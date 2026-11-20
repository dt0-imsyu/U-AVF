// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Small stream and file helpers used by the media and probe paths.
 *
 * Extracted from MainActivity. Every method is stateless; the waiting helpers
 * take the monitor object explicitly so callers keep control of their own
 * locking.
 */
final class IoUtil {
    private static final int TEXT_PROBE_BYTES = 128;

    private IoUtil() {}

    /** Reads exactly {@code length} bytes or fails; a short read is never silent. */
    static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        for (int read = 0; read < length; ) {
            int count = input.read(buffer, read, length - read);
            if (count < 0) throw new EOFException("short bridge capability token");
            read += count;
        }
        return buffer;
    }

    /** Fills {@code target} completely from {@code input}; a short read is never silent. */
    static void readExactly(InputStream input, byte[] target) throws IOException {
        for (int read = 0; read < target.length; ) {
            int count = input.read(target, read, target.length - read);
            if (count < 0) throw new IllegalStateException("short guest PCM header");
            read += count;
        }
    }

    /** Reads the head of a small text file, or the empty string when unreadable. */
    static String readText(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), TEXT_PROBE_BYTES)];
            int count = input.read(buffer);
            return count > 0 ? new String(buffer, 0, count, StandardCharsets.US_ASCII) : "";
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
    }

    static void writeText(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }

    /** Reads up to {@code maximumBytes} from the end of a file as UTF-8. */
    static String readTail(File file, int maximumBytes) {
        if (!file.isFile()) return "";
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long from = Math.max(0L, input.length() - (long) maximumBytes);
            input.seek(from);
            byte[] buffer = new byte[(int) (input.length() - from)];
            input.readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException unreadable) {
            return "";
        }
    }

    /**
     * Waits until the collected bytes contain {@code expected}.
     *
     * @return the offset of the match, or -1 on timeout
     */
    static int waitForBytes(ByteArrayOutputStream collected, Object monitor,
                            byte[] expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (monitor) {
            while (true) {
                byte[] data = collected.toByteArray();
                int found = indexOf(data, expected);
                if (found >= 0) return found;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) return -1;
                monitor.wait(remaining);
            }
        }
    }

    /** Waits until at least {@code length} bytes have been collected. */
    static boolean waitForLength(ByteArrayOutputStream collected, Object monitor,
                                 int length, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (monitor) {
            while (collected.size() < length) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) return false;
                monitor.wait(remaining);
            }
            return true;
        }
    }

    static void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException | RuntimeException ignored) {
            // Best effort only: the caller is already tearing the session down.
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        if (needle.length == 0) return 0;
        for (int at = 0; at <= haystack.length - needle.length; at++) {
            int offset = 0;
            while (offset < needle.length && haystack[at + offset] == needle[offset]) offset++;
            if (offset == needle.length) return at;
        }
        return -1;
    }
}
