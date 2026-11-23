// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Receives fallback WAVF frames. The viewport decides when they may be displayed. */
final class RawDisplayReceiver implements ConsoleFrameDecoder.Listener {
    private static final long PORT = 4052;
    private static final long CONNECT_TIMEOUT_MS = 300_000;
    private static final long PERF_INTERVAL_MS = 10_000;
    private final Object vm;
    private final DisplayConnection connection;
    private final int epoch;
    private final File report;
    private final Supplier<long[]> surfaceCounters;
    private final Consumer<ConsoleFrameDecoder.Frame> onFrame;
    private final Consumer<String> warning;
    private final Runnable ended;
    private final ConsoleFrameDecoder decoder = new ConsoleFrameDecoder(this);
    private long frames, blackFrames, usableFrames;
    private long bytes, decodeNanos;
    private Snapshot previous;

    RawDisplayReceiver(Object vm, DisplayConnection connection, int epoch, File report,
                       Supplier<long[]> surfaceCounters, Consumer<ConsoleFrameDecoder.Frame> onFrame,
                       Consumer<String> warning, Runnable ended) {
        this.vm = vm;
        this.connection = connection;
        this.epoch = epoch;
        this.report = report;
        this.surfaceCounters = surfaceCounters;
        this.onFrame = onFrame;
        this.warning = warning;
        this.ended = ended;
    }

    void start() { new Thread(this::receive, "WinAVF-ubuntu-frame-bridge").start(); }

    private void receive() {
        if (!connection.current(epoch)) return;
        try (PrintWriter log = new PrintWriter(new FileOutputStream(report))) {
            log.println("transport=AVF_CONNECT_VSOCK");
            log.println("guest=UBUNTU_XVNC_X11_CAPTURE");
            log.println("port=" + PORT);
            log.println("lz4Decoder=" + (WavfNative.AVAILABLE ? "NATIVE_BOUNDED_PARITY_PASS" : "JAVA_FALLBACK"));
            log.flush();
            try (ParcelFileDescriptor socket = connect()) {
                if (socket == null || !connection.trackRawSocket(epoch, socket)) return;
                try (InputStream framesIn = new ParcelFileDescriptor.AutoCloseInputStream(
                             ParcelFileDescriptor.dup(socket.getFileDescriptor()));
                     OutputStream guestInput = new ParcelFileDescriptor.AutoCloseOutputStream(
                             ParcelFileDescriptor.dup(socket.getFileDescriptor()))) {
                    if (connection.publishInput(epoch, guestInput)) readFrames(framesIn, log);
                } finally {
                    connection.forgetRawSocket(socket);
                }
            } catch (Exception failure) {
                if (connection.current(epoch)) {
                    log.println("streamEnd=" + failure);
                    log.println("bridgeFrameCount=" + frames);
                    log.println("nonBlackFrameCount=" + usableFrames);
                    if (usableFrames == 0) {
                        log.println("result=FRAME_TRANSPORT_TO_ANDROID_ERROR");
                        warning.accept("Ubuntu frame bridge failed: " + failure.getMessage());
                    }
                }
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        } catch (IOException failure) {
            if (connection.current(epoch)) warning.accept("Could not write frame bridge report: " + failure.getMessage());
        } finally {
            if (connection.current(epoch)) ended.run();
        }
    }

    private ParcelFileDescriptor connect() throws Exception {
        Method connectVsock = vm.getClass().getMethod("connectVsock", long.class);
        long deadline = SystemClock.elapsedRealtime() + CONNECT_TIMEOUT_MS;
        Throwable lastFailure = null;
        while (connection.current(epoch) && SystemClock.elapsedRealtime() < deadline) {
            try {
                return (ParcelFileDescriptor) connectVsock.invoke(vm, PORT);
            } catch (InvocationTargetException error) {
                lastFailure = error.getCause();
                Thread.sleep(1000);
            }
        }
        if (!connection.current(epoch)) return null;
        throw new IOException("vsock " + PORT + " listener did not become available", lastFailure);
    }

    private void readFrames(InputStream input, PrintWriter log) throws IOException {
        byte[] block = new byte[65536];
        previous = snapshot();
        boolean firstFrameReported = false;
        while (connection.current(epoch)) {
            int count = input.read(block);
            if (!connection.current(epoch)) return;
            if (count < 0) {
                log.println("streamClosed=YES");
                log.println("bridgeFrameCount=" + frames);
                log.println("blackFrameCount=" + blackFrames);
                log.println("nonBlackFrameCount=" + usableFrames);
                log.println("rawFrameArchive=DISABLED_NORMAL_RUNTIME");
                return;
            }
            if (count == 0) continue;
            bytes += count;
            long started = System.nanoTime();
            decoder.feed(block, 0, count);
            decodeNanos += System.nanoTime() - started;
            if (!firstFrameReported && usableFrames > 0) {
                log.println("result=FRAME_TRANSPORT_TO_ANDROID_PASS");
                log.println("firstFrame=DECODED_FOR_PRESENTATION");
                log.println("stream=CONTINUOUS_UNTIL_VM_STOP");
                log.flush();
                firstFrameReported = true;
            }
            if (SystemClock.elapsedRealtime() - previous.ms >= PERF_INTERVAL_MS) reportPerformance(log);
        }
    }

    @Override public void onFrame(ConsoleFrameDecoder.Frame frame) {
        if (!connection.current(epoch)) return;
        frames++;
        if (usableFrames == 0 && isBlack(frame.bgra)) {
            blackFrames++;
            return;
        }
        usableFrames++;
        onFrame.accept(frame);
    }

    @Override public void onProtocolError(String error) {
        Log.w("U-AVF", "Frame bridge WAVF: " + error);
    }

    private static boolean isBlack(byte[] pixels) {
        for (int offset = 0; offset + 2 < pixels.length; offset += 256) {
            if (pixels[offset] != 0 || pixels[offset + 1] != 0 || pixels[offset + 2] != 0) return false;
        }
        return true;
    }

    private record Snapshot(long ms, long frames, long bytes, long decode, long[] surface, long[] costs) {}

    private Snapshot snapshot() {
        return new Snapshot(SystemClock.elapsedRealtime(), frames, bytes, decodeNanos,
                surfaceCounters.get(), decoder.costSnapshot());
    }

    private void reportPerformance(PrintWriter log) {
        Snapshot now = snapshot();
        long interval = now.ms - previous.ms;
        long decoded = now.frames - previous.frames;
        long prepared = now.surface[0] - previous.surface[0];
        long drawn = now.surface[1] - previous.surface[1];
        double seconds = interval / 1000.0;
        log.println(String.format(Locale.US,
                "PERF interval_ms=%d decoded_fps=%.2f prepared_fps=%.2f drawn_fps=%.2f vsock_bytes_per_s=%.0f prepare_ms=%.2f draw_call_ms=%.2f",
                interval, decoded / seconds, prepared / seconds, drawn / seconds,
                (now.bytes - previous.bytes) / seconds, averageMs(now.surface[2] - previous.surface[2], prepared),
                averageMs(now.surface[3] - previous.surface[3], drawn)));
        log.println("frameCount=" + frames);
        log.println(String.format(Locale.US, "DECODE feed_including_prepare_ms=%.2f",
                averageMs(now.decode - previous.decode, decoded)));
        log.println(String.format(Locale.US, "DECODE_COST crc_ms=%.2f lz4_ms=%.2f pixel_copy=%s",
                averageMs(now.costs[0] - previous.costs[0], decoded),
                averageMs(now.costs[1] - previous.costs[1], decoded),
                WavfNative.AVAILABLE ? "NATIVE_NUMERIC_ARGB" : "JAVA"));
        log.println("nonBlackFrameCount=" + usableFrames);
        log.println("rawFrameArchive=DISABLED_NORMAL_RUNTIME");
        log.flush();
        previous = now;
    }

    private static double averageMs(long nanos, long frames) { return nanos / (1_000_000.0 * Math.max(1, frames)); }
}
