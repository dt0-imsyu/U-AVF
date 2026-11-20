// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** Owns display connection epochs and the ordered UIN1 writer for one viewport. */
final class DisplayConnection implements AutoCloseable {
    private static final byte[] MAGIC = {'U', 'I', 'N', '1'};
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private int epoch;
    private boolean closed;
    private ParcelFileDescriptor rawSocket;
    private volatile OutputStream input;

    synchronized int begin() {
        ++epoch;
        input = null;
        ParcelFileDescriptor previous = rawSocket;
        rawSocket = null;
        if (previous != null) {
            // Its reader closes the descriptor after shutdown wakes the blocked read.
            try {
                Os.shutdown(previous.getFileDescriptor(), OsConstants.SHUT_RDWR);
            } catch (Exception ignored) {}
        }
        return epoch;
    }

    synchronized boolean current(int candidate) { return !closed && candidate == epoch; }

    synchronized boolean trackRawSocket(int candidate, ParcelFileDescriptor socket) {
        if (!current(candidate)) return false;
        rawSocket = socket;
        return true;
    }

    synchronized void forgetRawSocket(ParcelFileDescriptor socket) {
        if (rawSocket != socket) return;
        input = null;
        rawSocket = null;
    }

    synchronized boolean publishInput(int candidate, OutputStream stream) {
        if (!current(candidate)) return false;
        input = stream;
        return true;
    }

    OutputStream input() { return input; }
    boolean hasInput() { return input != null; }

    void send(int kind, int state, int first, int second) {
        OutputStream stream = input;
        if (stream == null) return;
        byte[] packet = packet(kind, state, first, second);
        try {
            writer.execute(() -> {
                try {
                    writeIfCurrent(stream, packet, false);
                } catch (IOException error) {
                    Log.w("U-AVF", "Guest input write failed", error);
                }
            });
        } catch (RejectedExecutionException closedWhileDispatching) {
            // A viewport can disappear while a surface callback is in flight.
        }
    }

    void sendAndWait(OutputStream stream, int kind, int state, int first, int second, boolean required)
            throws Exception {
        byte[] packet = packet(kind, state, first, second);
        writer.submit(() -> {
            writeIfCurrent(stream, packet, required);
            return null;
        }).get(5, TimeUnit.SECONDS);
    }

    void drain() throws Exception {
        writer.submit(() -> {}).get(5, TimeUnit.SECONDS);
    }

    private void writeIfCurrent(OutputStream stream, byte[] packet, boolean required) throws IOException {
        synchronized (stream) {
            if (input != stream) {
                if (required) throw new IOException("Installer input channel is no longer active");
                return;
            }
            stream.write(packet);
            stream.flush();
        }
    }

    private static byte[] packet(int kind, int state, int first, int second) {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).put(MAGIC)
                .put((byte) kind).put((byte) state).putShort((short) 0).putInt(first).putInt(second).array();
    }

    @Override public synchronized void close() {
        closed = true;
        begin();
        writer.shutdownNow();
    }
}
