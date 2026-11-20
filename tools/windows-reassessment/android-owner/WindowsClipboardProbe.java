package com.example.winavf;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.*;

/** Explicit opt-in diagnostic only; never returns/logs existing clipboard text. */
final class WindowsClipboardProbe {
    static final String FIXTURE="UAVF Windows clipboard roundtrip — Привет 😄";
    static boolean run(WindowsDiagnosticActivity.DelegatedAvfChannel channel,java.util.function.Consumer<String> status) throws Exception {
        status.accept("CLIPBOARD_PROBE_STAGE=CONNECT");
        try(ParcelFileDescriptor fd=channel.connectVsock(4054L)) {
            long deadline=SystemClock.elapsedRealtime()+5000;
            FileInputStream source=new FileInputStream(fd.getFileDescriptor());
            InputStream bounded=new InputStream() {
                @Override public int read() throws IOException {
                    byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;
                }
                @Override public int read(byte[] bytes,int offset,int count) throws IOException {
                    if(count==0)return 0;
                    long remaining=deadline-SystemClock.elapsedRealtime();
                    if(remaining<=0)throw new InterruptedIOException("Clipboard probe deadline");
                    StructPollfd poll=new StructPollfd();poll.fd=fd.getFileDescriptor();poll.events=(short)OsConstants.POLLIN;
                    try {
                        if(Os.poll(new StructPollfd[]{poll},(int)remaining)!=1)
                            throw new InterruptedIOException("Clipboard probe timeout");
                    } catch(android.system.ErrnoException error) {throw new IOException("Clipboard probe poll",error);}
                    return source.read(bytes,offset,count);
                }
            };
            FileOutputStream output=new FileOutputStream(fd.getFileDescriptor());
            status.accept("CLIPBOARD_PROBE_STAGE=SEND");
            output.write(ClipboardWire.encode(ClipboardWire.TEXT,FIXTURE));
            output.write(ClipboardWire.encode(ClipboardWire.REQUEST,""));output.flush();
            status.accept("CLIPBOARD_PROBE_STAGE=READ");
            // Ignore at most a few in-flight notifications; content never escapes.
            for(int i=0;i<4;i++) {
                ClipboardWire.Message message=ClipboardWire.read(bounded);
                if(message.type==ClipboardWire.TEXT&&FIXTURE.equals(message.text)) {
                    status.accept("CLIPBOARD_PROBE_STAGE=MATCH");return true;
                }
            }
            status.accept("CLIPBOARD_PROBE_STAGE=UNMATCHED");
            return false;
        }
    }
}
