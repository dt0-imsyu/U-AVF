// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** UCTL v1, separate4056. No unbounded queues or automatic power-command retry. */
final class GuestControl implements AutoCloseable {
    interface Listener { void status(boolean connected,String detail,JSONObject session); }
    private final Object vm;
    private final Listener listener;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed,connected;
    private volatile ParcelFileDescriptor fd;
    private InputStream input; private OutputStream output; private int id;
    GuestControl(Object vm,Listener listener) {
        this.vm=vm; this.listener=listener;
        Thread t=new Thread(this::loop,"U-AVF-control"); t.setDaemon(true); t.start();
    }
    boolean connected() { return connected; }
    private void loop() {
        while(!closed) {
            try {
                ParcelFileDescriptor opened=(ParcelFileDescriptor)vm.getClass().getMethod("connectVsock",long.class).invoke(vm,4056L);
                synchronized(this) {
                    if(closed) { closeSocket(opened); return; }
                    fd=opened;
                    input=new FileInputStream(opened.getFileDescriptor()); output=new FileOutputStream(opened.getFileDescriptor());
                }
                JSONObject hello=exchange("HELLO");
                if(!hello.optBoolean("ok")) throw new IOException("Guest control HELLO rejected");
                connected=true;
                while(!closed) {
                    JSONObject info=exchange("SESSION_INFO"); listener.status(true,"Connected",info);
                    Thread.sleep(3000);
                }
            } catch(Exception e) { if(!closed) listener.status(false,"Unavailable: "+e.getClass().getSimpleName(),null); }
            finally { disconnect(); }
            try { Thread.sleep(3000); } catch(InterruptedException e) { return; }
        }
    }
    synchronized JSONObject exchange(String type) throws Exception {
        if(closed || fd==null) throw new IOException("Guest control unavailable");
        final ParcelFileDescriptor connection=fd;
        ScheduledFuture<?> timeout=timer.schedule(()->closeSocket(connection),3,TimeUnit.SECONDS);
        try {
            int request=++id;
            byte[] data=new JSONObject().put("version",1).put("id",request).put("type",type).toString().getBytes(StandardCharsets.UTF_8);
            ByteBuffer h=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            h.putInt(0x4c544355).putShort((short)1).putShort((short)0).putInt(data.length);
            output.write(h.array()); output.write(data); output.flush();
            byte[] header=exact(input,12); ByteBuffer r=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if(r.getInt()!=0x4c544355 || r.getShort()!=1 || r.getShort()!=0) throw new IOException("Bad control header");
            int size=r.getInt(); if(size<2 || size>262144) throw new IOException("Control size rejected");
            JSONObject response=new JSONObject(new String(exact(input,size),StandardCharsets.UTF_8));
            if(response.optInt("version")!=1 || response.optInt("id")!=request || !type.equals(response.optString("type")))
                throw new IOException("Control response mismatch");
            return response;
        } finally { timeout.cancel(false); }
    }
    void power(boolean reboot,java.util.function.Consumer<String> result) {
        Thread t=new Thread(()->{
            try { JSONObject r=exchange(reboot?"REBOOT":"SHUTDOWN"); result.accept(r.optBoolean("ok")?"ACK":r.optString("error","Rejected")); }
            catch(Exception e) { result.accept("Unavailable: "+e.getClass().getSimpleName()); }
        },"U-AVF-power-request"); t.setDaemon(true); t.start();
    }
    private static byte[] exact(InputStream in,int size) throws IOException {
        byte[] b=new byte[size]; int at=0; while(at<size) { int n=in.read(b,at,size-at); if(n<0) throw new EOFException(); at+=n; } return b;
    }
    private void disconnect() {
        connected=false; ParcelFileDescriptor p=fd; fd=null;
        if(p!=null) closeSocket(p);
    }
    private static void closeSocket(ParcelFileDescriptor p) {
        try { android.system.Os.shutdown(p.getFileDescriptor(),android.system.OsConstants.SHUT_RDWR); } catch(Exception ignored) {}
        try { p.close(); } catch(IOException ignored) {}
    }
    public void close() { closed=true; disconnect(); timer.shutdownNow(); }
}
