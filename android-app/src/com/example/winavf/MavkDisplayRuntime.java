package com.example.winavf;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicLong;

/** App-owned AVF channel -> common Mavk/AHB renderer, without a video codec.
 * VM creation remains in the existing runtime. Switching guest driver modes
 * requires a fresh session; a driver failure is never disguised as raw frames.
 */
final class MavkDisplayRuntime implements AutoCloseable,SurfaceHolder.Callback {
    private static final AtomicLong nextOwner=new AtomicLong(android.os.SystemClock.elapsedRealtimeNanos());
    private final long owner=nextOwner.incrementAndGet();
    private final Object channelLock=new Object();
    private final Activity activity;
    private final Object vm;
    private final int refreshHz;
    private final Consumer<OutputStream> inputReady;
    private final Runnable desktopReady;
    private final Consumer<String> failure;
    private final BooleanSupplier viewportVisible;
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private SurfaceView surface;
    private TextView fpsLabel;
    private int lastFps=-1;
    private int frameWidth=1920,frameHeight=1200;
    private IBinder renderer;
    private ParcelFileDescriptor graphics,input;
    private boolean rendererStarted;
    private volatile boolean closed;
    private boolean bound,firstFrame,sessionReady,notified;
    private final long started=android.os.SystemClock.elapsedRealtime();

    MavkDisplayRuntime(Activity activity,Object vm,int refreshHz,Consumer<OutputStream> inputReady,
            Runnable desktopReady,Consumer<String> failure,BooleanSupplier viewportVisible){
        this.activity=activity;this.vm=vm;this.refreshHz=refreshHz;this.inputReady=inputReady;
        this.desktopReady=desktopReady;this.failure=failure;this.viewportVisible=viewportVisible;
    }
    void start(SurfaceView view){
        // The decoder binds its GPU resources to this presentation layer.
        // Keep the layer while the view is attached but temporarily hidden.
        surface=view;
        view.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
        SurfaceHolder holder=view.getHolder();
        holder.addCallback(this);
        if(holder.getSurface().isValid())surfaceCreated(holder);
    }
    void setGuestSessionReady(boolean ready){main.post(()->{sessionReady=ready;notifyDesktop();});}
    int frameWidth(){return frameWidth;}
    int frameHeight(){return frameHeight;}
    private void notifyDesktop(){
        if(closed||notified||!firstFrame||!sessionReady)return;
        notified=true;
        var display=surface.getDisplay();
        android.util.Log.i("UAVF-Mavk","Surface refresh request="+refreshHz+
                " Hz; Android display mode at desktop ready="+(display==null?"unknown":display.getMode().getRefreshRate()+" Hz"));
        desktopReady.run();
        if(!closed)showFps();
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private void showFps(){
        if(!(surface.getParent() instanceof FrameLayout parent))return;
        fpsLabel=new TextView(activity);
        fpsLabel.setTextColor(Color.WHITE);
        fpsLabel.setTextSize(11);
        fpsLabel.setTypeface(Typeface.MONOSPACE);
        fpsLabel.setPadding(dp(7),dp(3),dp(7),dp(3));
        fpsLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        fpsLabel.setVisibility(View.GONE);
        GradientDrawable background=new GradientDrawable();
        background.setColor(0xBF101820);
        background.setCornerRadius(dp(5));
        fpsLabel.setBackground(background);
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM|Gravity.RIGHT);
        position.setMargins(dp(10),dp(10),dp(10),dp(10));
        parent.addView(fpsLabel,parent.indexOfChild(surface)+1,position);
        main.post(sampleFps);
    }
    private final Runnable sampleFps=new Runnable(){public void run(){
        if(closed||fpsLabel==null||renderer==null)return;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        int fps=-1;
        try{
            data.writeInterfaceToken(MavkDisplayService.INTERFACE);
            data.writeLong(owner);
            if(renderer.transact(MavkDisplayService.FPS,data,reply,0)){
                reply.readException();
                fps=reply.readInt();
            }
        }catch(android.os.RemoteException ignored){}
        finally{data.recycle();reply.recycle();}
        if(fps!=lastFps){
            lastFps=fps;
            if(fps>=0)fpsLabel.setText(fps+" FPS");
        }
        fpsLabel.setVisibility(fps>=0 && viewportVisible.getAsBoolean()?View.VISIBLE:View.GONE);
        main.postDelayed(this,1000);
    }};
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            if(closed)return;renderer=binder;
            new Thread(()->{
                try{
                    ParcelFileDescriptor channel=connect(4057,150000);
                    if(!ownChannel(channel,true))return;
                    Parcel data=Parcel.obtain(),reply=Parcel.obtain();
                    try{
                        data.writeInterfaceToken(MavkDisplayService.INTERFACE);
                        data.writeLong(owner);
                        data.writeTypedObject(channel,0);
                        data.writeTypedObject(surface.getHolder().getSurface(),0);
                        if(!binder.transact(MavkDisplayService.START,data,reply,0))throw new java.io.IOException("Renderer transaction unavailable");
                        reply.readException();
                    }finally{data.recycle();reply.recycle();}
                    synchronized(channelLock){
                        if(!closed)rendererStarted=true;
                    }
                    if(closed){stopRenderer(binder);return;}
                    main.post(checkFrame);
                    ParcelFileDescriptor controls=connect(4052,20000);
                    if(!ownChannel(controls,false))return;
                    main.post(()->{
                        if(!closed)inputReady.accept(new FileOutputStream(controls.getFileDescriptor()));
                    });
                }catch(Exception error){fail("Mavk connection: "+error.getClass().getSimpleName());}
            },"UAVF-Mavk-connect").start();
        }
        public void onServiceDisconnected(ComponentName name){if(!closed)fail("Mavk renderer process ended");}
    };
    private boolean ownChannel(ParcelFileDescriptor channel,boolean render){
        synchronized(channelLock){
            if(!closed){
                if(render)graphics=channel;else input=channel;
                return true;
            }
        }
        closeChannel(channel);
        return false;
    }
    private ParcelFileDescriptor connect(long port,long timeout)throws Exception{
        long until=android.os.SystemClock.elapsedRealtime()+timeout;
        Exception last=null;
        while(!closed&&android.os.SystemClock.elapsedRealtime()<until){
            try{return (ParcelFileDescriptor)vm.getClass().getMethod("connectVsock",long.class).invoke(vm,port);}
            catch(Exception error){last=error;Thread.sleep(200);}
        }
        throw new java.io.IOException("App-owned channel "+port+" unavailable",last);
    }
    @Override public void surfaceCreated(SurfaceHolder holder){
        if(closed||bound)return;
        holder.getSurface().setFrameRate(refreshHz,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        bound=activity.bindService(new Intent(activity,MavkDisplayService.class),connection,Context.BIND_AUTO_CREATE);
        if(!bound)fail("Mavk renderer service unavailable");
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){
        if(!closed)fail("Mavk Surface changed; reconnect the display session");
    }
    // SurfaceControl children are not the SurfaceView BufferQueue: PixelCopy
    // of that queue cannot validate AHB presentation. Ask the shared presenter
    // for an actual signalled display fence instead. No pixels are copied.
    private final Runnable checkFrame=new Runnable(){public void run(){
        if(closed||notified)return;
        if(android.os.SystemClock.elapsedRealtime()-started>180000){fail("No completed Mavk frame");return;}
        IBinder binder=renderer;if(binder==null)return;
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(MavkDisplayService.INTERFACE);
            data.writeLong(owner);
            if(!binder.transact(MavkDisplayService.STATUS,data,reply,0))throw new java.io.IOException("Renderer status unavailable");
            reply.readException();int state=reply.readInt();
            if(state<0){fail("Mavk render session ended");return;}
            if(state==1){
                long extent=reply.dataAvail()>=Long.BYTES?reply.readLong():0;
                int width=(int)(extent>>>32),height=(int)extent;
                if(width>=320 && height>=200 && width<=4096 && height<=4096){
                    frameWidth=width;
                    frameHeight=height;
                    android.util.Log.i("UAVF-Mavk","PRESENTED_EXTENT="+width+"x"+height);
                }
                firstFrame=true;notifyDesktop();if(notified)return;
            }
        }catch(Exception error){fail("Mavk renderer status failed");return;}
        finally{data.recycle();reply.recycle();}
        if(!closed)main.postDelayed(this,200);
    }};
    private void fail(String reason){main.post(()->{if(!closed){close();failure.accept(reason);}});}
    private static void closeChannel(ParcelFileDescriptor channel){
        if(channel==null)return;
        try{android.system.Os.shutdown(channel.getFileDescriptor(),android.system.OsConstants.SHUT_RDWR);}catch(Exception ignored){}
        try{channel.close();}catch(Exception ignored){}
    }
    @Override public void close(){
        ParcelFileDescriptor oldInput,oldGraphics;
        boolean stop;
        synchronized(channelLock){
            if(closed)return;
            closed=true;
            oldInput=input;oldGraphics=graphics;input=null;graphics=null;
            stop=rendererStarted;rendererStarted=false;
        }
        main.removeCallbacks(checkFrame);main.removeCallbacks(sampleFps);inputReady.accept(null);
        if(fpsLabel!=null&&fpsLabel.getParent() instanceof FrameLayout parent)parent.removeView(fpsLabel);
        fpsLabel=null;
        if(surface!=null)surface.getHolder().removeCallback(this);
        closeChannel(oldInput);closeChannel(oldGraphics);
        if(stop && renderer!=null)stopRenderer(renderer);
        renderer=null;
        if(bound){try{activity.unbindService(connection);}catch(IllegalArgumentException ignored){}bound=false;}
    }

    private void stopRenderer(IBinder binder){
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken(MavkDisplayService.INTERFACE);
            data.writeLong(owner);
            if(binder.transact(MavkDisplayService.STOP,data,reply,0))reply.readException();
        }catch(Exception ignored){}
        finally{data.recycle();reply.recycle();}
    }
}
