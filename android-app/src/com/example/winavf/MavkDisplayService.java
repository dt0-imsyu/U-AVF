package com.example.winavf;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import java.io.File;
import java.io.IOException;

/** Same-UID renderer process. Never creates, reconfigures or stops a VM. */
public final class MavkDisplayService extends Service {
    static final String INTERFACE="com.example.winavf.MavkDisplayService.v2";
    static final int START=IBinder.FIRST_CALL_TRANSACTION,STOP=START+1,STATUS=START+2,FPS=START+3;
    private ParcelFileDescriptor channel;
    private Surface surface;
    private boolean running;
    private long owner;
    private volatile boolean nativeLoaded;
    private volatile File activeReport;
    private long legacyExtent;
    private static final java.util.regex.Pattern PRESENT_EXTENT=java.util.regex.Pattern.compile(
            "(?m)^MAVK_LEASE present=\\d+ extent=(\\d{1,4})x(\\d{1,4}) slot=");
    private static native int runNative(String report,int channel,Surface surface);
    private static native boolean hasPresented();
    private static native long getPresentedExtent();
    private static native int getPresentedFps();
    private static native void resetPresented();

    private final Binder binder=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)
                throws android.os.RemoteException {
            if(code==INTERFACE_TRANSACTION){reply.writeString(INTERFACE);return true;}
            if(Binder.getCallingUid()!=android.os.Process.myUid())throw new SecurityException("Same UID required");
            if(code!=START&&code!=STOP&&code!=STATUS&&code!=FPS)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(INTERFACE);
            long requestedOwner=data.readLong();
            if(code!=START)data.enforceNoDataAvail();
            if(code==STOP){stopChannel(requestedOwner);reply.writeNoException();return true;}
            if(code==FPS){
                int fps=-1;
                synchronized(MavkDisplayService.this){
                    if(running && owner==requestedOwner && nativeLoaded){
                        try{fps=getPresentedFps();}catch(UnsatisfiedLinkError ignored){}
                    }
                }
                reply.writeNoException();reply.writeInt(fps);return true;
            }
            if(code==STATUS){
                int state;
                long extent=0;
                synchronized(MavkDisplayService.this){
                    state=!running || owner!=requestedOwner?-1:nativeLoaded&&hasPresented()?1:0;
                    if(state==1) {
                        try{extent=getPresentedExtent();}
                        catch(UnsatisfiedLinkError legacy){extent=legacyPresentedExtent();}
                    }
                }
                reply.writeNoException();reply.writeInt(state);reply.writeLong(extent);return true;
            }
            ParcelFileDescriptor fd=data.readTypedObject(ParcelFileDescriptor.CREATOR);
            Surface target=data.readTypedObject(Surface.CREATOR);
            data.enforceNoDataAvail();
            synchronized(MavkDisplayService.this){
                if(running||requestedOwner==0||fd==null||target==null||!target.isValid()){
                    close(fd);if(target!=null)target.release();
                    throw new IllegalStateException("Renderer already active or invalid channel/Surface");
                }
                running=true;owner=requestedOwner;nativeLoaded=false;legacyExtent=0;activeReport=null;channel=fd;surface=target;
            }
            new Thread(()->{
                int status=90;
                File reports=getExternalFilesDir(null);
                if(reports==null)reports=getFilesDir();
                File report=new File(reports,"mavk-runtime-report.txt");
                try{
                    System.loadLibrary("mavk_runtime_probe");
                    resetPresented();
                    activeReport=report;
                    nativeLoaded=true;
                    status=runNative(report.getAbsolutePath(),fd.getFd(),target);
                }catch(Throwable error){
                    android.util.Log.e("UAVF-Mavk","Renderer stopped",error);
                }finally{
                    synchronized(MavkDisplayService.this){
                        close(channel);channel=null;surface=null;owner=0;
                        target.release();running=false;
                    }
                    try{java.nio.file.Files.writeString(new File(report.getParentFile(),"mavk-runtime-exit.txt").toPath(),Integer.toString(status));}
                    catch(IOException ignored){}
                    stopSelf();
                }
            },"UAVF-Mavk-renderer").start();
            reply.writeNoException();return true;
        }
    };
    /** Older bundled renderers publish the extent of submitted buffers in the
     * current report. Consult it only after their presentation fence signals. */
    private synchronized long legacyPresentedExtent() {
        if(legacyExtent!=0)return legacyExtent;
        File report=activeReport;
        if(report==null)return 0;
        try(var input=new java.io.FileInputStream(report)) {
            String text=new String(input.readNBytes(32768),java.nio.charset.StandardCharsets.US_ASCII);
            var frames=PRESENT_EXTENT.matcher(text);
            while(frames.find()) {
                int width=Integer.parseInt(frames.group(1)),height=Integer.parseInt(frames.group(2));
                if(width>=320 && width<=4096 && height>=200 && height<=4096)
                    legacyExtent=((long)width<<32)|height;
            }
        } catch(IOException ignored){}
        return legacyExtent;
    }
    private static void close(ParcelFileDescriptor fd){if(fd!=null)try{fd.close();}catch(IOException ignored){}}
    private synchronized void stopChannel(){
        if(channel!=null)try{android.system.Os.shutdown(channel.getFileDescriptor(),android.system.OsConstants.SHUT_RDWR);}catch(Exception ignored){}
        // The native worker owns the descriptor until its blocking read returns.
    }
    private synchronized void stopChannel(long requestedOwner){
        if(running && owner==requestedOwner)stopChannel();
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public void onDestroy(){stopChannel();super.onDestroy();}
}
