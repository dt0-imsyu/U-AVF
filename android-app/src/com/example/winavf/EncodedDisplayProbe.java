package com.example.winavf;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Isolated UVF1/4055 proof. Never changes the normal WAVF receiver/VM disk. */
final class EncodedDisplayProbe {
    private final Activity activity;
    private final Object vm;
    private final Runnable restoreFallback;
    private final float requestedDisplayHz;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong received = new AtomicLong(), decoded = new AtomicLong(), rendered = new AtomicLong(), bytes = new AtomicLong();
    private final AtomicLong receiveRenderNs = new AtomicLong();
    private final ConcurrentHashMap<Long, Long> arrival = new ConcurrentHashMap<>();
    private volatile boolean stopping;
    private volatile ParcelFileDescriptor fd;
    private volatile MediaCodec codec;
    private Dialog dialog;
    private TextView status;
    private Thread outputThread;
    private PrintWriter report;
    private long lastTime, lastReceived, lastDecoded, lastRendered, lastBytes;
    private String codecName = "connecting";
    private boolean fallbackRestored;
    private volatile boolean sessionStreamEnded;
    boolean retryableSessionEnd() { return sessionStreamEnded; }
    private boolean embedded, firstFrame;
    private java.util.function.Consumer<java.io.OutputStream> inputReady;
    private Runnable desktopReady;
    private boolean frameValidationPending;
    private long lastValidationMs;
    /** One small readback during startup only, never a per-frame production copy. */
    private void validateFirstDesktopFrame() {
        if (!embedded || firstFrame || frameValidationPending || disposed || stopping || embeddedSurface==null) return;
        long now=SystemClock.elapsedRealtime();
        if (now-lastValidationMs<250 || !embeddedSurface.getHolder().getSurface().isValid()) return;
        lastValidationMs=now; frameValidationPending=true;
        final android.graphics.Bitmap sample=android.graphics.Bitmap.createBitmap(64,40,android.graphics.Bitmap.Config.ARGB_8888);
        try {
            android.view.PixelCopy.request(embeddedSurface,sample,result->{
                frameValidationPending=false;
                boolean nonBlack=false;
                if (result==android.view.PixelCopy.SUCCESS && !disposed && !stopping) {
                    int[] pixels=new int[64*40];sample.getPixels(pixels,0,64,0,0,64,40);
                    int lit=0;for(int pixel:pixels) if((pixel&0x00ffffff)!=0) lit++;
                    nonBlack=lit>=8;
                }
                sample.recycle();
                if(nonBlack && !firstFrame) {firstFrame=true;desktopReady.run();}
            },main);
        } catch (RuntimeException failure) {sample.recycle();frameValidationPending=false;}
    }
    private volatile boolean suppressFallback;
    private volatile boolean disposed;
    private SurfaceView embeddedSurface;
    private SurfaceHolder.Callback embeddedCallback;
    private Runnable recreateSurface;
    private final java.util.concurrent.atomic.AtomicBoolean reconnectPosted=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean receiveStarted=new java.util.concurrent.atomic.AtomicBoolean();
    private void startEmbeddedReceive(SurfaceHolder holder) {
        if(!stopping && receiveStarted.compareAndSet(false,true))
            new Thread(()->receive(holder),"UAVF-H264-installed").start();
    }
    void closeSilently() {
        disposed=true; suppressFallback=true;
        if(embeddedSurface!=null && embeddedCallback!=null)
            embeddedSurface.getHolder().removeCallback(embeddedCallback);
        close();
    }
    void resumeSurface() {
        if(!disposed && stopping && suppressFallback && embeddedSurface!=null &&
                embeddedSurface.getHolder().getSurface().isValid() &&
                reconnectPosted.compareAndSet(false,true)) main.post(recreateSurface);
    }

    void suspendSurface() {
        // Set suppression before Android invalidates Surface or codec reports EOF.
        // A foreground reconnect uses a fresh receiver/codec, not the raw backend.
        if(disposed || !embedded) return;
        suppressFallback=true;
        if(inputReady!=null) inputReady.accept(null);
        stop();
    }

    void startEmbedded(SurfaceView surface,
            java.util.function.Consumer<java.io.OutputStream> inputReady, Runnable desktopReady,
            Runnable surfaceRecreated) {
        this.embedded=true; this.inputReady=inputReady; this.desktopReady=desktopReady;
        this.embeddedSurface=surface; this.recreateSurface=surfaceRecreated;
        status=new TextView(activity); // Metrics stay in diagnostics, not over the desktop.
        lastTime=SystemClock.elapsedRealtimeNanos();
        main.postDelayed(metrics,1000);
        embeddedCallback=new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                holder.getSurface().setFrameRate(60f,android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
                // A destroyed Surface cannot retain its MediaCodec. Reconnect
                // the production stream when Android creates its replacement;
                // temporary backgrounding is NOT a backend failure.
                if (stopping && suppressFallback) resumeSurface();
                else startEmbeddedReceive(holder);
            }
            public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) {}
            public void surfaceDestroyed(SurfaceHolder holder) {
                suppressFallback=true;
                inputReady.accept(null);
                stop();
            }
        };
        surface.getHolder().addCallback(embeddedCallback);
        if(surface.getHolder().getSurface().isValid())
            startEmbeddedReceive(surface.getHolder());
    }
    private volatile long windowStart, windowEnd;
    private final AtomicLong windowReceived=new AtomicLong(), windowDecoded=new AtomicLong(), windowRendered=new AtomicLong();
    private boolean inWindow(long at) { return windowStart>0 && at>=windowStart && at<windowEnd; }

    EncodedDisplayProbe(Activity activity, Object vm, float requestedDisplayHz, Runnable restoreFallback) {
        this.activity = activity; this.vm = vm; this.restoreFallback = restoreFallback;
        this.requestedDisplayHz = Math.max(60f, Math.min(120f, requestedDisplayHz));
    }
    void close() { if (dialog != null) dialog.dismiss(); stop(); }

    void show() {
        dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout layout = new FrameLayout(activity);
        layout.setBackgroundColor(Color.BLACK);
        SurfaceView surface = new SurfaceView(activity);
        layout.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(activity);
        status.setTextColor(Color.WHITE); status.setBackgroundColor(0x99000000);
        status.setText("H.264 Surface proof — port 4055");
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(-2, -2);
        textParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.LEFT;
        layout.addView(status, textParams);
        Button exit = new Button(activity); exit.setText("Return to normal display");
        FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(-2, -2);
        exitParams.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
        layout.addView(exit, exitParams);
        exit.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(layout);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.view.Display display = activity.getDisplay();
        if (display != null && requestedDisplayHz > 60f) {
            android.view.Display.Mode current = display.getMode();
            android.view.Display.Mode best = null;
            for (android.view.Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() <= this.requestedDisplayHz + 0.1f
                        && (best == null || mode.getRefreshRate() > best.getRefreshRate())) best = mode;
            }
            if (best != null) {
                WindowManager.LayoutParams attrs = dialog.getWindow().getAttributes();
                attrs.preferredDisplayModeId = best.getModeId();
                dialog.getWindow().setAttributes(attrs);
            }
        }
        dialog.setOnDismissListener(d -> stop());
        surface.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                holder.getSurface().setFrameRate(requestedDisplayHz, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
                new Thread(() -> receive(holder), "UAVF-H264-receive").start();
            }
            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}
            public void surfaceDestroyed(SurfaceHolder holder) { stop(); }
        });
        dialog.show();
        lastTime = SystemClock.elapsedRealtimeNanos();
        main.postDelayed(metrics, 1000);
    }

    private final Runnable metrics = new Runnable() {
        public void run() {
            long now = SystemClock.elapsedRealtimeNanos();
            double seconds = (now - lastTime) / 1e9;
            long r = received.get(), d = decoded.get(), p = rendered.get(), b = bytes.get();
            long count = p - lastRendered;
            String line = String.format(java.util.Locale.US,
                "H264 Surface 1920x1200 codec=%s receive=%.2f decode=%.2f rendered_callback=%.2f Mbps=%.2f pending=%d receive_to_render_ms=%.2f total=%d/%d/%d",
                codecName, (r-lastReceived)/seconds, (d-lastDecoded)/seconds, count/seconds,
                (b-lastBytes)*8/seconds/1e6, arrival.size(), count==0?0:receiveRenderNs.getAndSet(0)/1e6/count, r,d,p);
            status.setText(line);
            synchronized (EncodedDisplayProbe.this) { if (report != null) { report.println(line); report.flush(); } }
            android.util.Log.i("UAVF-H264", line);
            lastTime=now; lastReceived=r; lastDecoded=d; lastRendered=p; lastBytes=b;
            if (!stopping) main.postDelayed(this, 1000);
        }
    };

    private void receive(SurfaceHolder holder) {
        try {
            synchronized (this) {
                report = new PrintWriter(new FileOutputStream(new File(activity.getExternalFilesDir(null), "encoded-display-report.txt")));
                report.println("UVF1 AVC -> MediaCodec -> Surface; callbacks are not display fences"); report.flush();
            }
            Throwable last = null;
            for (int attempt=0; attempt<(embedded?300:30) && !stopping; attempt++) {
                try { fd=(ParcelFileDescriptor)vm.getClass().getMethod("connectVsock",long.class).invoke(vm,4055L); break; }
                catch (Throwable e) { last=e; Thread.sleep(1000); }
            }
            if (fd==null || stopping) throw new IllegalStateException("4055 unavailable",last);
            try (FileInputStream input=new FileInputStream(fd.getFileDescriptor())) {
                byte[] header=new byte[40];
                int generation=-1, lastSequence=-1;
                while (!stopping) {
                    readExactly(input,header);
                    ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                    if(h.getInt(0)!=0x31465655 || h.getShort(4)!=1 || h.getInt(8)!=1) throw new IllegalArgumentException("Bad UVF1 AVC header");
                    int type=h.getShort(6)&65535, gen=h.getInt(12), seq=h.getInt(16);
                    int w=h.getShort(20)&65535, height=h.getShort(22)&65535, length=h.getInt(32);
                    long pts=h.getLong(24);
                    if(type==4 && length==0) {
                        try {
                            FileOutputStream output=new FileOutputStream(fd.getFileDescriptor());
                            output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(System.nanoTime()).array());
                            if(embedded) inputReady.accept(output);
                        }
                        catch(java.io.IOException e) { throw e; }
                        continue;
                    }
                    if(type==5 && length==16) {
                        byte[] bounds=new byte[16]; readExactly(input,bounds);
                        ByteBuffer boundsBuffer=ByteBuffer.wrap(bounds).order(ByteOrder.LITTLE_ENDIAN);
                        windowStart=boundsBuffer.getLong(); windowEnd=boundsBuffer.getLong();
                        synchronized(this) { report.println("FIXED_WINDOW start_ns="+windowStart+" end_ns="+windowEnd); report.flush(); }
                        continue;
                    }
                    if(w!=1920 || height!=1200 || length<0 || length>4*1024*1024 || (type!=2 && type!=3)) throw new IllegalArgumentException("UVF1 bounds/type rejected");
                    if(generation==-1) generation=gen;
                    if(gen!=generation || seq<=lastSequence) throw new IllegalArgumentException("UVF1 generation/order rejected");
                    lastSequence=seq;
                    if(type==3) { if(length!=0) throw new IllegalArgumentException("EOS payload"); break; }
                    if(length==0) throw new IllegalArgumentException("Empty AU");
                    byte[] payload=new byte[length]; readExactly(input,payload);
                    if(codec==null) {
                        MediaFormat format=MediaFormat.createVideoFormat("video/avc",w,height);
                        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4*1024*1024);
                        format.setInteger(MediaFormat.KEY_LOW_LATENCY,1);
                        format.setInteger(MediaFormat.KEY_FRAME_RATE,60);
                        codec=embedded ? MediaCodec.createDecoderByType("video/avc") : MediaCodec.createByCodecName("c2.mtk.avc.decoder");
                        codecName=codec.getName();
                        codec.setOnFrameRenderedListener((c,presentationTimeUs,nanoTime)-> {
                            Long at=arrival.remove(presentationTimeUs);
                            if(at!=null) receiveRenderNs.addAndGet(Math.max(0,nanoTime-at));
                            rendered.incrementAndGet();
                            validateFirstDesktopFrame();
                            // Vendor callback timestamp may not use Android's
                            // monotonic domain. Count callback delivery separately.
                            if(inWindow(System.nanoTime())) windowRendered.incrementAndGet();
                        },main);
                        codec.configure(format,holder.getSurface(),null,0); codec.start();
                        outputThread=new Thread(this::drain,"UAVF-H264-output"); outputThread.start();
                    }
                    long deadline=SystemClock.elapsedRealtime()+2000;
                    int index;
                    do {
                        if(stopping || SystemClock.elapsedRealtime()>deadline) throw new IllegalStateException("Decoder input backpressure exceeded 2 seconds");
                        index=codec.dequeueInputBuffer(10000);
                    } while(index<0);
                    ByteBuffer target=codec.getInputBuffer(index);
                    if(target==null || target.capacity()<length) throw new IllegalArgumentException("AU exceeds codec input");
                    target.clear(); target.put(payload);
                    if(arrival.size()>120) throw new IllegalStateException("Decoder pending frames exceeded bound");
                    // Codec callback nanoTime uses System.nanoTime, not BOOTTIME.
                    arrival.put(pts,System.nanoTime());
                    codec.queueInputBuffer(index,0,length,pts,0);
                    received.incrementAndGet(); bytes.addAndGet(length);
                    if(inWindow(System.nanoTime())) windowReceived.incrementAndGet();
                }
                Thread.sleep(1000);
            }
        } catch(Throwable e) {
            // GDM's X server closes on login/logout. A previously validated
            // stream ending cleanly is a session handoff, not codec corruption.
            sessionStreamEnded=embedded && firstFrame && !stopping && e instanceof java.io.EOFException;
            synchronized(this) { if(report!=null) { report.println("ERROR="+e); report.flush(); } }
            if (!stopping) {
                android.util.Log.e("UAVF-H264","encoded probe",e);
                main.post(()->status.setText("Encoded probe ended: "+e));
            }
        } finally {
            stop();
        }
    }

    private void drain() {
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        MediaCodec drainingCodec=codec;
        try {
            while(!stopping) {
                int index=drainingCodec.dequeueOutputBuffer(info,10000);
                if(index>=0) { decoded.incrementAndGet(); if(inWindow(System.nanoTime())) windowDecoded.incrementAndGet();
                    // Guest capture PTS is NOT an Android Surface clock. Render
                    // immediately using the local monotonic timestamp instead.
                    drainingCodec.releaseOutputBuffer(index,System.nanoTime()); }
            }
        } catch(Throwable e) { if(!stopping) { android.util.Log.e("UAVF-H264","output",e); stop(); } }
    }
    private static void readExactly(FileInputStream in, byte[] b) throws java.io.IOException {
        int offset=0;
        while(offset<b.length) { int n=in.read(b,offset,b.length-offset); if(n<0) throw new java.io.EOFException(); offset+=n; }
    }
    synchronized void stop() {
        stopping=true;
        main.removeCallbacks(metrics);
        if(fd!=null) {
            try { android.system.Os.shutdown(fd.getFileDescriptor(),android.system.OsConstants.SHUT_RDWR); } catch(Exception ignored) {}
            try { fd.close(); } catch(Exception ignored) {} fd=null;
        }
        // Codec is torn down only after its output worker has exited.
        MediaCodec current=codec;
        if(current!=null) {
            codec=null;
            new Thread(()-> {
                try { if(outputThread!=null) outputThread.join(1500); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
                try { current.stop(); } catch(Exception ignored) {}
                current.release();
            },"UAVF-H264-close").start();
        }
        if(report!=null) { report.println("FIXED_WINDOW seconds=20 received="+windowReceived+" decoded="+windowDecoded+" rendered_callbacks="+windowRendered); report.println("END received="+received+" decoded="+decoded+" rendered_callbacks="+rendered); report.close(); report=null; }
        if (!fallbackRestored) {
            fallbackRestored=true;
            main.post(() -> {
                // An ended probe must not leave its last/black Surface above
                // the restored desktop. Dismiss also on EOF and connect error.
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
                if(!suppressFallback) restoreFallback.run();
            });
        }
    }
}
