// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/** In-process KD streams backed by the proven paced UART ingress and regular-file tailer. */
final class WindowsKdUart implements Closeable {
    // The app-local WinPE loader can remain active beyond the old three-minute bound.
    static final long BOOT_TIMEOUT_MS = 360_000;
    private static final String INGRESS = "libuavf_ingress.so";
    private static final String TAIL = "libuavf_console_tail.so";
    private static final int WRITE_FILE = ParcelFileDescriptor.MODE_CREATE
            | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY;

    private ParcelFileDescriptor serialOut;
    private ParcelFileDescriptor serialIn;
    private ParcelFileDescriptor crosvmLog;
    private Process ingress;
    private Process tailer;

    static boolean available(File nativeDir) {
        return new File(nativeDir, INGRESS).canExecute() && new File(nativeDir, TAIL).canExecute();
    }

    WindowsKdUart(File nativeDir, File directory) throws IOException {
        if (!available(nativeDir)) throw new IOException("Windows UART helpers are unavailable");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create UART log directory");
        File serial = new File(directory, "serial.bin");
        try {
            serialOut = ParcelFileDescriptor.open(serial, WRITE_FILE);
            crosvmLog = ParcelFileDescriptor.open(new File(directory, "crosvm.log"), WRITE_FILE);
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            serialIn = pipe[0];
            try (ParcelFileDescriptor write = pipe[1]) {
                ingress = new ProcessBuilder(new File(nativeDir, INGRESS).getAbsolutePath(), "--paced",
                        "--log", new File(directory, "decoded-tx.bin").getAbsolutePath(),
                        "--written-log", new File(directory, "written-tx.bin").getAbsolutePath())
                        .redirectOutput(new File("/proc/self/fd/" + write.getFd()))
                        .redirectError(new File(directory, "ingress-stderr.txt")).start();
            }
            tailer = new ProcessBuilder(new File(nativeDir, TAIL).getAbsolutePath(), serial.getAbsolutePath())
                    .redirectError(new File(directory, "tailer-stderr.txt")).start();
        } catch (IOException | RuntimeException failure) {
            close();
            throw failure;
        }
    }

    Object createVm(WindowsAvfOwner owner) throws ReflectiveOperationException {
        return owner.create(serialOut, serialIn, crosvmLog);
    }

    /** Reads one guest UART session. Closing this transport unblocks an active reader. */
    void serve(Runnable kernelResumed) throws IOException {
        serve(kernelResumed, false);
    }

    void serve(Runnable kernelResumed, boolean matchHostCpus) throws IOException {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                tailer.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream uart = ingress.getOutputStream()) {
            InputStream rx = new InputStream() {
                private ByteArrayInputStream chunk = new ByteArrayInputStream(new byte[0]);

                private boolean fill() throws IOException {
                    while (chunk.available() == 0) {
                        long deadline = System.nanoTime() + 1_000_000_000L;
                        while (!lines.ready() && tailer.isAlive()) {
                            if (System.nanoTime() >= deadline) throw new SocketTimeoutException("Windows UART idle");
                            try { Thread.sleep(5); }
                            catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IOException("Windows UART interrupted", interrupted);
                            }
                        }
                        String line = lines.readLine();
                        if (line == null) return false;
                        if (line.startsWith("O:")) {
                            chunk = new ByteArrayInputStream(Base64.decode(line.substring(2), Base64.DEFAULT));
                        }
                    }
                    return true;
                }

                @Override public int read() throws IOException { return fill() ? chunk.read() : -1; }

                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    if (length == 0) return 0;
                    return fill() ? chunk.read(bytes, offset, length) : -1;
                }
            };
            OutputStream tx = new OutputStream() {
                @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}); }

                @Override public void write(byte[] bytes) throws IOException {
                    uart.write(Base64.encode(bytes, Base64.NO_WRAP));
                    uart.write('\n');
                    uart.flush();
                }
            };
            new WindowsKdSession(rx, tx, matchHostCpus).serve(kernelResumed);
        }
    }

    @Override public synchronized void close() {
        if (tailer != null) tailer.destroy();
        if (ingress != null) ingress.destroy();
        for (ParcelFileDescriptor fd : new ParcelFileDescriptor[]{serialOut, serialIn, crosvmLog}) {
            if (fd != null) try { fd.close(); } catch (IOException ignored) {}
        }
    }
}
