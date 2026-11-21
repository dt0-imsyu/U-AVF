// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads the Ubuntu PipeWire monitor over AVF vsock and plays stereo PCM. */
final class UbuntuAudioBridge {
    private static final long PORT = 4053;
    private static final int SAMPLE_RATE = 48_000;
    private static final int FRAME_BYTES = 4;
    private static final long REPORT_INTERVAL_MS = 10_000;
    private Object activeVm;

    synchronized void start(Object vm, File reportDirectory) {
        if (activeVm == vm) return;
        activeVm = vm;
        File report = new File(reportDirectory, "ubuntu-gnome-audio-bridge-report.txt");
        new Thread(() -> play(vm, report), "WinAVF-ubuntu-audio-bridge").start();
    }

    private static void play(Object vm, File report) {
        try (PrintWriter log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("transport=AVF_CONNECT_VSOCK");
            log.println("guest=PIPEWIRE_NULL_SINK_MONITOR");
            log.println("port=" + PORT);
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(connect(vm))) {
                checkHeader(input);
                AudioTrack track = newTrack();
                try {
                    if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                        throw new IllegalStateException("Android AudioTrack initialization failed");
                    }
                    track.setBufferSizeInFrames(Math.min(track.getBufferCapacityInFrames(), 4096));
                    track.setVolume(1.0f);
                    track.play();
                    log.println("result=PCM_HEADER_PASS");
                    log.println("format=PCM_S16LE_48000_STEREO");
                    log.println("performance_mode=" + track.getPerformanceMode());
                    log.println("buffer_frames=" + track.getBufferSizeInFrames());
                    log.flush();
                    stream(input, track, log);
                } finally {
                    release(track);
                }
            }
        } catch (Exception error) {
            Log.w("U-AVF", "Ubuntu audio bridge failed", error);
            try (PrintWriter log = new PrintWriter(new FileOutputStream(report, true))) {
                log.println("result=AUDIO_BRIDGE_FAILED");
                log.println("error=" + IoUtil.rootMessage(error));
            } catch (Exception writeError) {
                Log.w("U-AVF", "Could not write Ubuntu audio report", writeError);
            }
        }
    }

    private static ParcelFileDescriptor connect(Object vm) throws Exception {
        Method connect = vm.getClass().getMethod("connectVsock", long.class);
        long deadline = SystemClock.elapsedRealtime() + 300_000;
        Exception lastFailure = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                ParcelFileDescriptor socket = (ParcelFileDescriptor) connect.invoke(vm, PORT);
                if (socket != null) return socket;
            } catch (ReflectiveOperationException error) {
                lastFailure = error;
            }
            Thread.sleep(1_000);
        }
        throw new IllegalStateException("vsock " + PORT + " did not become available", lastFailure);
    }

    private static void checkHeader(InputStream input) throws Exception {
        byte[] header = IoUtil.readExactly(input, 16);
        int rate = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(8);
        if (header[0] != 'U' || header[1] != 'A' || header[2] != 'V' || header[3] != 'F'
                || ByteCodec.u16(header, 4) != 1 || ByteCodec.u16(header, 6) != 2
                || rate != SAMPLE_RATE || ByteCodec.u16(header, 12) != 1) {
            throw new IllegalStateException("unexpected guest PCM header");
        }
    }

    private static AudioTrack newTrack() {
        int channels = AudioFormat.CHANNEL_OUT_STEREO;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, channels, encoding);
        if (minimum <= 0) throw new IllegalStateException("Android rejected 48 kHz stereo PCM");
        AudioFormat format = new AudioFormat.Builder().setEncoding(encoding)
                .setSampleRate(SAMPLE_RATE).setChannelMask(channels).build();
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        return new AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(minimum, 16_384)).setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
    }

    private static void stream(InputStream input, AudioTrack track, PrintWriter log) throws Exception {
        byte[] buffer = new byte[16_388];
        int carriedBytes = 0;
        long writtenBytes = 0;
        long previousBytes = 0;
        long reportedAt = SystemClock.elapsedRealtime();
        for (int count; (count = input.read(buffer, carriedBytes, buffer.length - carriedBytes)) >= 0;) {
            if (count == 0) continue;
            int available = carriedBytes + count;
            // Keep a partial stereo frame until the next socket read.
            int complete = available - available % FRAME_BYTES;
            writeFrames(track, buffer, complete);
            writtenBytes += complete;
            carriedBytes = available - complete;
            System.arraycopy(buffer, complete, buffer, 0, carriedBytes);
            long now = SystemClock.elapsedRealtime();
            if (now - reportedAt >= REPORT_INTERVAL_MS) {
                log.println("pcm_bytes=" + writtenBytes);
                log.println("pcm_bytes_per_s=" + (writtenBytes - previousBytes) * 1000 / (now - reportedAt));
                log.println("underruns=" + track.getUnderrunCount());
                log.println("buffer_frames=" + track.getBufferSizeInFrames());
                log.flush();
                reportedAt = now;
                previousBytes = writtenBytes;
            }
        }
        log.println("result=PCM_STREAM_ENDED");
        log.println("pcm_bytes=" + writtenBytes);
    }

    private static void writeFrames(AudioTrack track, byte[] buffer, int bytes) {
        for (int offset = 0; offset < bytes;) {
            int count = track.write(buffer, offset, bytes - offset, AudioTrack.WRITE_BLOCKING);
            if (count <= 0) throw new IllegalStateException("AudioTrack write failed: " + count);
            offset += count;
        }
    }

    private static void release(AudioTrack track) {
        try {
            if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                track.pause();
                track.flush();
            }
        } finally {
            track.release();
        }
    }
}
