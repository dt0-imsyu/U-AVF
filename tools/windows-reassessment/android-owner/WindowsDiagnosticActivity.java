package com.example.winavf;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Exact isolated Windows contract; no production app or Ubuntu paths. */
public final class WindowsDiagnosticActivity extends Activity {
    private static final String DISK_SHA="A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5";
    private static final String NAME="uavf-windows-cam-owned-20261007";
    private Object vm,manager; private Socket host; private EncodedDisplayProbe display;
    private SurfaceView surface; private TextView status;
    // Frozen for a viewer instance; default preserves the proven Setup mode.
    private int displayWidth=640,displayHeight=480;
    private PrintWriter report;
    private volatile OutputStream guestInput;
    private String ownerToken;
    private android.os.IBinder serviceOwner;
    private android.content.ServiceConnection ownerConnection;
    private GuestClipboard guestClipboard;
    private static java.lang.ref.WeakReference<WindowsDiagnosticActivity> externalViewer=new java.lang.ref.WeakReference<>(null);
    static boolean diagnosticMavkProbe(String token)throws Exception {
        WindowsDiagnosticActivity activity=externalViewer.get();
        if(activity==null||token==null||!token.equals(activity.ownerToken)||activity.isDestroyed()||activity.serviceOwner==null||!activity.getIntent().getBooleanExtra("mavk_bridge_enabled",false))throw new SecurityException("Explicit Mavk owner probe required");
        android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
        try{data.writeInterfaceToken(AvfOwnerBridge.INTERFACE);data.writeString(token);if(!activity.serviceOwner.transact(4,data,reply,0))throw new IOException("Mavk owner unavailable");reply.readException();return reply.readInt()==1;}finally{data.recycle();reply.recycle();}
    }
    static boolean diagnosticClipboardProbe(String token) throws Exception {
        WindowsDiagnosticActivity activity=externalViewer.get();
        if(activity==null||token==null||!token.equals(activity.ownerToken)||activity.isDestroyed()
            ||!activity.getIntent().getBooleanExtra("clipboard_enabled",false)
            ||!activity.getIntent().getBooleanExtra("clipboard_probe",false)
            ||activity.guestClipboard!=null||activity.serviceOwner==null||activity.guestInput==null)
            throw new SecurityException("Explicit isolated clipboard probe not ready");
        synchronized(activity) {
            boolean passed;
            try {
                passed=WindowsClipboardProbe.run(new DelegatedAvfChannel(activity.serviceOwner,token,true),activity::note);
            } catch(Exception error) {
                // Only a fixed class category: never exception messages, tokens
                // or clipboard values. Preserve the last completed stage.
                activity.note("CLIPBOARD_PROBE_ERROR="+(error instanceof java.io.EOFException?"EOF":
                    error instanceof java.io.InterruptedIOException?"TIMEOUT":error instanceof java.io.IOException?"IO":"CHANNEL"));
                throw error;
            }
            activity.note("SYNTHETIC_WINDOWS_CLIPBOARD_TRANSPORT_ROUNDTRIP="+(passed?"PASS":"FAIL")
                +"; NOT_ANDROID_SYSTEM_CLIPBOARD_PASS=1");
            return passed;
        }
    }
    static boolean diagnosticInput(String token, String text, int symbol, int state) {
        WindowsDiagnosticActivity activity=externalViewer.get();
        if(activity==null||token==null||!token.equals(activity.ownerToken)
                ||activity.guestInput==null||activity.isDestroyed())return false;
        synchronized(activity) {
            if(text!=null) {
                if(text.length()>2048)throw new IllegalArgumentException("Diagnostic text bound");
                for(int i=0;i<text.length();i++) {
                    char c=text.charAt(i);
                    if(c<32||c==127)throw new IllegalArgumentException("Use explicit keys for control characters");
                }
                // Shared UIN1 Unicode keysyms: layout-independent text, unlike
                // Android shortcut injection. Never log credentials/content.
                for(int i=0;i<text.length()&&activity.guestInput!=null;i++) {
                    int sym=0x01000000|text.charAt(i);
                    activity.input(3,1,sym,0);activity.input(3,0,sym,0);
                      // Diagnostic bulk typing must not outrun a newly opened
                      // guest console/PSReadLine. Physical keyboard is unchanged.
                      try { Thread.sleep(10); }
                      catch(InterruptedException e) { Thread.currentThread().interrupt(); return false; }
                }
            }else {
                if(symbol<1||symbol>0xffff||(state!=0&&state!=1))
                    throw new IllegalArgumentException("Diagnostic key bound");
                activity.input(3,state,symbol,0);
            }
            return activity.guestInput!=null;
        }
    }
    public static boolean attachExternalOwner(String token,android.os.IBinder owner) {
        WindowsDiagnosticActivity activity=externalViewer.get();
        if(activity==null||owner==null||token==null||!token.equals(activity.ownerToken))return false;
        activity.runOnUiThread(()->{activity.vm=new DelegatedAvfChannel(owner,token);activity.note("AVF_CHANNEL_DELEGATED_FROM_SHELL_OWNER");activity.startDisplay();});
        return true;
    }
    public static final class DelegatedAvfChannel {
        private final android.os.IBinder owner;private final String token;
        private final boolean clipboardEnabled;
        private boolean failureRecorded;
        private final java.util.HashSet<String> distinctFailures=new java.util.HashSet<>();
        DelegatedAvfChannel(android.os.IBinder owner,String token){this(owner,token,false);}
        DelegatedAvfChannel(android.os.IBinder owner,String token,boolean clipboardEnabled){this.owner=owner;this.token=token;this.clipboardEnabled=clipboardEnabled;}
        public android.os.ParcelFileDescriptor connectVsock(long port)throws Exception {
            if(!WindowsChannelPolicy.allowed(port,true,clipboardEnabled))throw new SecurityException("Only explicitly enabled isolated Windows channels");
            android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
            try {
                data.writeInterfaceToken("com.example.uavfwinpetest.AvfDisplayOwner");data.writeString(token);data.writeInt((int)port);
                if(!owner.transact(1,data,reply,0))throw new java.io.IOException("AVF owner unavailable");
                reply.readException();
                android.os.ParcelFileDescriptor descriptor=android.os.ParcelFileDescriptor.CREATOR.createFromParcel(reply);
                WindowsDiagnosticActivity activity=externalViewer.get();
                if(activity!=null)activity.note("DELEGATED_FD_RECEIVED valid="+descriptor.getFileDescriptor().valid()+" fd="+descriptor.getFd());
                return descriptor;
            }catch(Exception e){
                Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
                String fingerprint=cause.getClass().getName()+": "+cause.getMessage();
                if(distinctFailures.size()<6&&distinctFailures.add(fingerprint)){
                    WindowsDiagnosticActivity activity=externalViewer.get();
                    if(activity!=null){activity.note("DELEGATED_CHANNEL_DISTINCT_ERROR="+fingerprint);e.printStackTrace(activity.report);activity.report.flush();}
                }
                if(!failureRecorded){failureRecorded=true;android.util.Log.e("UAVF-AVF-channel","delegated descriptor",e);
                    WindowsDiagnosticActivity activity=externalViewer.get();if(activity!=null)activity.note("DELEGATED_CHANNEL_FIRST_ERROR="+e);}
                throw e;
            }finally{data.recycle();reply.recycle();}
        }
    }
    private void startDisplay() {
        if(vm==null || display!=null)return;
        if(getIntent().getBooleanExtra("source_only_probe",false)) {
            note("PRIMARY_CAPTURE_SUPPRESSED_FOR_SOURCE_ONLY_PROBE; VM_OWNER_RETAINED");
            return;
        }
        note("DISPLAY_START_REQUESTED");
        display=new EncodedDisplayProbe(this,vm,60f,()->{
            note("DISPLAY_ENDED");display=null;guestInput=null;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                if(vm!=null&&!isFinishing()&&!isDestroyed()&&serviceOwner!=null&&serviceOwner.isBinderAlive())startDisplay();
            },500);
        },displayWidth,displayHeight);
        display.startEmbedded(surface,o->{guestInput=o;},()->{
            note("VALID_SETUP_FRAME_PRESENTED");
            if(guestClipboard==null&&vm!=null&&!isFinishing()&&!isDestroyed()&&getIntent().getBooleanExtra("clipboard_enabled",false)
                &&!getIntent().getBooleanExtra("clipboard_probe",false)) {
                guestClipboard=new GuestClipboard(this,vm,"Windows");
                guestClipboard.focus(hasWindowFocus());
                note("OPTIONAL_WINDOWS_CLIPBOARD_CLIENT_STARTED; NOT_GUEST_CLIPBOARD_PASS");
            }
            android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(displayWidth,displayHeight,android.graphics.Bitmap.Config.ARGB_8888);
            android.view.PixelCopy.request(surface,pixels,result->{
                if(result==android.view.PixelCopy.SUCCESS)try(FileOutputStream f=new FileOutputStream(new File(getExternalFilesDir(null),"android-surface-first.png"))){
                    pixels.compress(android.graphics.Bitmap.CompressFormat.PNG,100,f);note("ANDROID_SURFACE_READBACK=PASS");
                }catch(IOException e){note("ANDROID_SURFACE_READBACK="+e);}
                else note("ANDROID_SURFACE_READBACK_ERROR="+result);
                pixels.recycle();
            },new android.os.Handler(android.os.Looper.getMainLooper()));
        },this::recreateDisplaySurface);
    }
    private void awaitEmbeddedKernel(android.os.IBinder owner, long deadline) {
        if(isFinishing()||isDestroyed()||serviceOwner!=owner||!owner.isBinderAlive())return;
        android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
        try{
            data.writeInterfaceToken(AvfOwnerBridge.INTERFACE);data.writeString(ownerToken);
            if(!owner.transact(5,data,reply,0))throw new IOException("Embedded KD status unavailable");
            reply.readException();
            if(reply.readInt()!=0){startDisplay();return;}
        }catch(Exception error){note("EMBEDDED_KD_STATUS_ERROR="+error);return;}
        finally{data.recycle();reply.recycle();}
        if(android.os.SystemClock.elapsedRealtime()<deadline){
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    ()->awaitEmbeddedKernel(owner,deadline),500);
        }else note("EMBEDDED_KD_VIEWER_TIMEOUT");
    }
    private void recreateDisplaySurface() {
        if(isFinishing()||isDestroyed()||vm==null||surface==null
                ||!surface.getHolder().getSurface().isValid())return;
        note("SURFACE_RECREATED");
        // Match the common receiver's lifecycle contract: a stopped MediaCodec
        // cannot be reused on Android's replacement Surface. Keep the AVF
        // owner/guest alive and replace only this viewer's channel and codec.
        EncodedDisplayProbe previous=display;
        display=null;guestInput=null;
        if(previous!=null)previous.closeSilently();
        startDisplay();
    }
    @Override protected void onPause() {
        keyboard.releaseAll();
        if(guestClipboard!=null)guestClipboard.focus(false);
        if(display!=null)display.suspendSurface();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(guestClipboard!=null)guestClipboard.focus(hasWindowFocus());
        if(display!=null)display.resumeSurface();
        getWindow().getDecorView().post(this::hideSystemBars);
    }
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if(ownerToken!=null && ownerToken.equals(intent.getStringExtra("token"))
                && intent.getBooleanExtra("start_display",false)){
            if(serviceOwner!=null){
                android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
                try{data.writeInterfaceToken(AvfOwnerBridge.INTERFACE);data.writeString(ownerToken);if(!serviceOwner.transact(2,data,reply,0))throw new IOException("Owner readiness unavailable");reply.readException();}
                catch(Exception e){note("OWNER_READY_ERROR="+e);return;}
                finally{data.recycle();reply.recycle();}
            }
            if(display!=null)display.resumeSurface();else startDisplay();
        }
    }
    private final GuestKeyboard keyboard=new GuestKeyboard((sym,down)->input(3,down?1:0,sym,0));
    private synchronized void input(int kind,int value,int x,int y) {
        OutputStream out=guestInput;if(out==null)return;
        byte[] packet=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .put(new byte[]{'U','I','N','1'}).put((byte)kind).put((byte)value).putShort((short)0).putInt(x).putInt(y).array();
        try{out.write(packet);out.flush();}catch(IOException e){guestInput=null;note("INPUT_STREAM="+e);}
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if(guestInput!=null && event.getKeyCode()!=android.view.KeyEvent.KEYCODE_BACK
                && (event.getAction()==android.view.KeyEvent.ACTION_DOWN||event.getAction()==android.view.KeyEvent.ACTION_UP)
                && keyboard.handle(event.getKeyCode(),event.getAction()==android.view.KeyEvent.ACTION_DOWN,
                    event.getMetaState(),event.getUnicodeChar(event.getMetaState())))return true;
        return super.dispatchKeyEvent(event);
    }
    private static Object call(Object o,String n,Class<?> t,Object arg)throws Exception {
        return o.getClass().getMethod(n,t).invoke(o,arg);
    }
    private static Object call(Object o,String n)throws Exception{return o.getClass().getMethod(n).invoke(o);}
    private void hideSystemBars() {
        getWindow().setDecorFitsSystemWindows(false);
        android.view.WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null) {
            controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(android.view.WindowInsets.Type.systemBars());
        }
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if(guestClipboard!=null)guestClipboard.focus(focused);
        if(focused)hideSystemBars();
    }
    private void note(String s){if(report!=null){report.println(s);report.flush();}runOnUiThread(()->status.setText(s));}
    private static String sha(File f)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[1048576];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:hash.digest())s.append(String.format("%02X",b));return s.toString();
    }
    private File privateCopy(File source,String name,String expected)throws Exception {
        File target=new File(getFilesDir(),name);
        if(target.exists() && name.equals("windows-diagnostic.img") && getIntent().getBooleanExtra("restore_private",false)) {
            if(target.length()!=9126805504L)throw new SecurityException("Unexpected private diagnostic disk size");
            note("Restoring only disposable Windows diagnostic copy; no installation has been performed");
            if(!target.delete())throw new IOException("Cannot restore disposable private copy");
        }
        if(!target.exists()) {
            if(!target.createNewFile())throw new IOException("Cannot reserve isolated private copy");
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)) {
                byte[] bytes=new byte[1048576];long total=0;int n;
                while((n=in.read(bytes))!=-1){out.write(bytes,0,n);total+=n;if(total%(256*1048576L)==0)note("PRIVATE_COPY_BYTES="+total);}
            }
        }
        if(target.length()!=source.length()||!expected.equals(sha(target)))throw new SecurityException("Private diagnostic copy differs: "+name);
        return target;
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        displayWidth=getIntent().getIntExtra("display_width",640);
        displayHeight=getIntent().getIntExtra("display_height",480);
        if(displayWidth<2||displayHeight<2||displayWidth>4096||displayHeight>4096
                ||(displayWidth&1)!=0||(displayHeight&1)!=0) {
            throw new IllegalArgumentException("Isolated Windows display dimensions");
        }
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout layout=new FrameLayout(this);surface=new SurfaceView(this);layout.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        layout.addOnLayoutChangeListener((v,l,t,r,bottom,ol,ot,orr,ob)->{
            int width=r-l,height=bottom-t;if(width<=0||height<=0)return;
            int sw=Math.min(width,(int)((long)height*displayWidth/displayHeight));
            int sh=(int)((long)sw*displayHeight/displayWidth);
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)surface.getLayoutParams();
            if(lp.width!=sw||lp.height!=sh){lp.width=sw;lp.height=sh;lp.gravity=android.view.Gravity.CENTER;surface.setLayoutParams(lp);}
        });
        surface.setFocusableInTouchMode(true);surface.requestFocus();
        surface.setOnTouchListener((v,e)->{
            if(guestInput==null)return false;
            int x=Math.max(0,Math.min(displayWidth-1,(int)(e.getX()*displayWidth/Math.max(1,v.getWidth()))));
            int y=Math.max(0,Math.min(displayHeight-1,(int)(e.getY()*displayHeight/Math.max(1,v.getHeight()))));
            input(1,0,x,y);
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)input(2,1,1,0);
            else if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP||e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)input(2,0,1,0);
            return true;
        });
        surface.setOnGenericMotionListener((v,e)->{
            if(guestInput==null)return false;
            int x=Math.max(0,Math.min(displayWidth-1,(int)(e.getX()*displayWidth/Math.max(1,v.getWidth()))));
            int y=Math.max(0,Math.min(displayHeight-1,(int)(e.getY()*displayHeight/Math.max(1,v.getHeight()))));
            input(1,0,x,y);
            if(e.getActionMasked()==android.view.MotionEvent.ACTION_SCROLL){
                int dx=(int)e.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL),dy=(int)e.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);
                if(dx!=0)input(4,0,Math.max(-32,Math.min(32,dx)),0);
                if(dy!=0)input(4,0,0,Math.max(-32,Math.min(32,dy)));
            }else if(e.getActionMasked()==android.view.MotionEvent.ACTION_BUTTON_PRESS||e.getActionMasked()==android.view.MotionEvent.ACTION_BUTTON_RELEASE){
                int button=e.getActionButton();int mapped=button==android.view.MotionEvent.BUTTON_SECONDARY?3:button==android.view.MotionEvent.BUTTON_TERTIARY?2:1;
                input(2,e.getActionMasked()==android.view.MotionEvent.ACTION_BUTTON_PRESS?1:0,mapped,0);
            }
            return true;
        });
        status=new TextView(this);status.setTextColor(0xffffffff);status.setBackgroundColor(0xaa000000);
        layout.addView(status,new FrameLayout.LayoutParams(-1,-2));setContentView(layout);
        hideSystemBars();
        android.widget.Button stop=new android.widget.Button(this);stop.setText("Stop Windows VM");
        FrameLayout.LayoutParams stopLayout=new FrameLayout.LayoutParams(-2,-2,android.view.Gravity.BOTTOM|android.view.Gravity.END);
        stopLayout.setMargins(16,16,16,16);layout.addView(stop,stopLayout);
        stop.setOnClickListener(v->{
            if(serviceOwner==null||ownerToken==null)return;
            android.os.Parcel data=android.os.Parcel.obtain(),reply=android.os.Parcel.obtain();
            try{data.writeInterfaceToken(AvfOwnerBridge.INTERFACE);data.writeString(ownerToken);
                if(!serviceOwner.transact(3,data,reply,0))throw new IOException("Owner stop unavailable");reply.readException();
                vm=null;stopClipboard();if(display!=null)display.closeSilently();surface.setVisibility(android.view.View.GONE);status.setText("Windows VM stopped");
            }catch(Exception error){note("OWNER_STOP_ERROR="+error);}finally{data.recycle();reply.recycle();}
        });
        String token=getIntent().getStringExtra("token");
        if(token==null||!token.matches("[0-9A-Fa-f]{32,128}")){status.setText("Missing diagnostic token");return;}
        ownerToken=token;
        if(getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()) {
            status.setText("Unlock the tablet, then restart this diagnostic activity.");return;
        }
        if(getIntent().getBooleanExtra("external_owner",false)) {
            try{report=new PrintWriter(new FileOutputStream(new File(getExternalFilesDir(null),"owner-report.txt")));}
            catch(IOException e){throw new RuntimeException(e);}
            File priorDisplay=new File(getExternalFilesDir(null),"encoded-display-report.txt");
            if(priorDisplay.exists()&&!priorDisplay.renameTo(new File(getExternalFilesDir(null),"encoded-display-previous-"+System.currentTimeMillis()+".txt")))
                throw new IllegalStateException("Cannot preserve previous display evidence");
            externalViewer=new java.lang.ref.WeakReference<>(this);note("WAITING_FOR_DELEGATED_AVF_OWNER");return;
        }
        if(getIntent().getBooleanExtra("service_owner",false)) {
            try{report=new PrintWriter(new FileOutputStream(new File(getExternalFilesDir(null),"owner-report.txt")));}catch(IOException e){throw new RuntimeException(e);}
            File priorDisplay=new File(getExternalFilesDir(null),"encoded-display-report.txt");
            if(priorDisplay.exists()&&!priorDisplay.renameTo(new File(getExternalFilesDir(null),"encoded-display-before-service-"+System.currentTimeMillis()+".txt")))throw new IllegalStateException("Preserve prior display evidence");
            externalViewer=new java.lang.ref.WeakReference<>(this);
            android.content.Intent ownerIntent=new android.content.Intent(this,WindowsVmOwnerService.class).putExtra("token",token);
            boolean embeddedKd=getIntent().getBooleanExtra("embedded_kd",false);
            ownerIntent.putExtra("embedded_kd",embeddedKd);
            if(getIntent().hasExtra("candidate_sha"))ownerIntent.putExtra("candidate_sha",getIntent().getStringExtra("candidate_sha"));
            ownerIntent.putExtra("prepare_install_target",getIntent().getBooleanExtra("prepare_install_target",false));
            ownerIntent.putExtra("resume_prepared_install_target",getIntent().getBooleanExtra("resume_prepared_install_target",false));
            ownerIntent.putExtra("resume_windows_install_target",getIntent().getBooleanExtra("resume_windows_install_target",false));
            ownerIntent.putExtra("idd_disposable_bootstrap",getIntent().getBooleanExtra("idd_disposable_bootstrap",false));
            ownerIntent.putExtra("clipboard_enabled",getIntent().getBooleanExtra("clipboard_enabled",false));
            ownerIntent.putExtra("mavk_bridge_enabled",getIntent().getBooleanExtra("mavk_bridge_enabled",false));
            ownerIntent.putExtra("wddm_admission",getIntent().getBooleanExtra("wddm_admission",false));
            ownerIntent.putExtra("wddm_codec",getIntent().getBooleanExtra("wddm_codec",false));
            ownerIntent.putExtra("wddm_pnp",getIntent().getBooleanExtra("wddm_pnp",false));
            ownerIntent.putExtra("mavk_validation",getIntent().getBooleanExtra("mavk_validation",false));
            ownerIntent.putExtra("rtc_clock_shim",getIntent().getBooleanExtra("rtc_clock_shim",false));
            ownerIntent.putExtra("dynamic_gic",getIntent().getBooleanExtra("dynamic_gic",false));
            ownerIntent.putExtra("fixed_eight_cpu_gic",getIntent().getBooleanExtra("fixed_eight_cpu_gic",false));
            ownerIntent.putExtra("psci_low_memory_ab",getIntent().getBooleanExtra("psci_low_memory_ab",false));
            ownerIntent.putExtra("psci_owned_reserve",getIntent().getBooleanExtra("psci_owned_reserve",false));
            ownerIntent.putExtra("psci_first_cpu_entry",getIntent().getBooleanExtra("psci_first_cpu_entry",false));
            ownerIntent.putExtra("psci_runtime_reserve",getIntent().getBooleanExtra("psci_runtime_reserve",false));
            ownerIntent.putExtra("psci_runtime_all_cpus",getIntent().getBooleanExtra("psci_runtime_all_cpus",false));
            ownerIntent.putExtra("psci_entry_marker",getIntent().getBooleanExtra("psci_entry_marker",false));
            ownerIntent.putExtra("psci_cache_publish",getIntent().getBooleanExtra("psci_cache_publish",false));
            ownerIntent.putExtra("windows_ram_gop",getIntent().getBooleanExtra("windows_ram_gop",false));
            ownerIntent.putExtra("match_host_cpus",getIntent().getBooleanExtra("match_host_cpus",false));
            ownerConnection=new android.content.ServiceConnection(){
                public void onServiceConnected(android.content.ComponentName name,android.os.IBinder binder){
                    serviceOwner=binder;
                    vm=new DelegatedAvfChannel(binder,ownerToken,getIntent().getBooleanExtra("clipboard_enabled",false));
                    note("PERSISTENT_APP_OWNER_BOUND_WAITING_FOR_WINPE");
                    if(embeddedKd)awaitEmbeddedKernel(binder,android.os.SystemClock.elapsedRealtime()
                            +WindowsKdUart.BOOT_TIMEOUT_MS+30000);
                }
                public void onServiceDisconnected(android.content.ComponentName name){stopClipboard();note("PERSISTENT_APP_OWNER_DISCONNECTED");}
            };
            startForegroundService(ownerIntent);bindService(ownerIntent,ownerConnection,Context.BIND_AUTO_CREATE);return;
        }
        new Thread(()->start(token),"Windows-owner-KD").start();
    }
    @Override protected void onDestroy() {
        stopClipboard();
        if(ownerConnection!=null){unbindService(ownerConnection);ownerConnection=null;}
        if(externalViewer.get()==this) {
            externalViewer.clear();keyboard.releaseAll();guestInput=null;
            if(display!=null)display.closeSilently();if(report!=null)report.close();
        }
        super.onDestroy();
    }
    private void stopClipboard() {
        if(guestClipboard!=null){guestClipboard.close();guestClipboard=null;}
    }
    private void start(String token){
        try {
            File dir=getExternalFilesDir(null);report=new PrintWriter(new FileOutputStream(new File(dir,"owner-report.txt")));
            File disk=new File(dir,"windows.img"),kernel=new File(dir,"u-boot-wrapper-v24.Image");
            if(disk.length()!=9126805504L||!DISK_SHA.equals(sha(disk)))throw new SecurityException("Disposable Windows disk identity failed");
            if(kernel.length()!=1430912L||!"93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234".equals(sha(kernel)))throw new SecurityException("Exact wrapper identity failed");
            if(getIntent().getBooleanExtra("private_storage",false)) {
                note("Preparing byte-identical isolated private storage copy");
                disk=privateCopy(disk,"windows-diagnostic.img",DISK_SHA);
                kernel=privateCopy(kernel,"u-boot-wrapper-v24.Image","93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234");
                note("PRIVATE_STORAGE_IDENTITY=PASS");
            }
            note("DISK_IDENTITY=PASS; waiting for authenticated KD");
            try(ServerSocket server=new ServerSocket(39101,1,InetAddress.getLoopbackAddress())) {
                note("KD_LISTEN="+server.getLocalSocketAddress());
                server.setSoTimeout(120000);host=server.accept();host.setTcpNoDelay(true);host.setSoTimeout(10000);
                note("KD_ACCEPTED");
                byte[] got=host.getInputStream().readNBytes(token.length());
                if(!token.equals(new String(got,StandardCharsets.US_ASCII)))throw new SecurityException("KD token mismatch");
                note("KD_AUTHENTICATED");
                host.getOutputStream().write("UAVF_WINDOWS_OWNER_OK\n".getBytes(StandardCharsets.US_ASCII));host.getOutputStream().flush();host.setSoTimeout(0);
                Class<?> custom=Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
                Object cb=Class.forName(custom.getName()+"$Builder").getConstructor().newInstance();
                boolean parity=getIntent().getBooleanExtra("shell_parity",false);
                call(cb,"setName",String.class,NAME);call(cb,"setOsName",String.class,parity?"":"uavf-windows-isolated");
                call(cb,"setKernelPath",String.class,kernel.getAbsolutePath());
                call(cb,"useNetwork",boolean.class,false);call(cb,"useAutoMemoryBalloon",boolean.class,parity);
                note("SHELL_PARITY="+parity+" balloon="+parity+"; isolated diagnostic only");
                Class<?> dc=Class.forName(custom.getName()+"$Disk");
                call(cb,"addDisk",dc,dc.getMethod("RWDisk",String.class).invoke(null,disk.getAbsolutePath()));
                Object ci=call(cb,"build");
                Class<?> config=Class.forName("android.system.virtualmachine.VirtualMachineConfig");
                Object builder=Class.forName(config.getName()+"$Builder").getConstructor(Context.class).newInstance(this);
                call(builder,"setProtectedVm",boolean.class,false);call(builder,"setMemoryBytes",long.class,4294967296L);
                call(builder,"setCpuTopology",int.class,config.getField("CPU_TOPOLOGY_ONE_CPU").getInt(null));
                call(builder,"setDebugLevel",int.class,1);call(builder,"setConsoleInputDevice",String.class,"ttyS0");
                call(builder,"setVmOutputCaptured",boolean.class,true);call(builder,"setVmConsoleInputSupported",boolean.class,true);
                call(builder,"setCustomImageConfig",custom,ci);Object cfg=call(builder,"build");
                // Retain actual framework conversion, not just requested Builder values.
                java.lang.reflect.Method rawMethod=config.getDeclaredMethod("toVsRawConfig");
                rawMethod.setAccessible(true);Object raw=rawMethod.invoke(cfg);
                for(java.lang.reflect.Field field:raw.getClass().getFields()) {
                    if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                    Object value=field.get(raw);
                    note("RAW_CONFIG "+field.getName()+"="+String.valueOf(value));
                }
                manager=getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
                Object prior=manager.getClass().getMethod("get",String.class).invoke(manager,NAME);
                if(prior!=null)manager.getClass().getMethod("delete",String.class).invoke(manager,NAME); // Only disposable diagnostic name.
                vm=manager.getClass().getMethod("create",String.class,config).invoke(manager,NAME,cfg);
                note("VM_DESCRIPTOR_CREATED");
                try {
                    InputStream logs=(InputStream)call(vm,"getLogOutput");
                    new Thread(()->{try(FileOutputStream f=new FileOutputStream(new File(dir,"virtmgr-log.txt"))){logs.transferTo(f);}catch(Exception e){note("LOG_CAPTURE="+e);}},"Windows-owner-log").start();
                    note("LOG_CAPTURE_READY");
                }catch(Exception e){note("LOG_CAPTURE_API="+e);}
                InputStream serial=(InputStream)call(vm,"getConsoleOutput");OutputStream input=(OutputStream)call(vm,"getConsoleInput");
                Socket connection=host;
                Thread rx=new Thread(()->{try{serial.transferTo(connection.getOutputStream());}catch(Exception e){note("SERIAL_RX="+e);}},"Windows-KD-rx");rx.start();
                note("RUN_REQUESTED");call(vm,"run");note("VM_RUNNING exact oneCPU/4GiB/noGPU/one Windows disk");
                if(getIntent().getBooleanExtra("defer_display",false))note("DISPLAY_DEFERRED_UNTIL_GUEST_VIOSOCK_LOAD");
                else runOnUiThread(this::startDisplay);
                boolean paced=getIntent().getBooleanExtra("paced_uart",false);
                note("KD_UART_PACING="+(paced?"8 bytes / 1 ms (known-good ingress parity)":"unpaced"));
                byte[] data=new byte[4096];int n;
                while((n=host.getInputStream().read(data))!=-1){
                    for(int offset=0;offset<n;) {
                        int count=paced?Math.min(8,n-offset):n-offset;
                        input.write(data,offset,count);input.flush();offset+=count;
                        if(paced)Thread.sleep(1);
                    }
                }
            }
        }catch(Throwable e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();note("OWNER_ERROR="+e+" ROOT="+cause);android.util.Log.e("UAVF-Windows-owner","diagnostic",e);}
        finally {
            runOnUiThread(this::stopClipboard);
            keyboard.releaseAll();guestInput=null;
            if(display!=null)display.closeSilently();
            if(vm!=null)try{call(vm,"stop");}catch(Exception e){note("STOP="+e);}
            if(host!=null)try{host.close();}catch(Exception ignored){}
            if(report!=null)report.close();
        }
    }
}
