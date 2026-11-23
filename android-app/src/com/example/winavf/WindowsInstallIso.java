// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.ProxyFileDescriptorCallback;
import android.os.storage.StorageManager;
import android.system.ErrnoException;
import android.system.OsConstants;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Reads a selected ISO in place. The Windows boot and disk paths remain separate. */
final class WindowsInstallIso {
    private static final boolean AVAILABLE = loadReader();

    record Inspection(long isoBytes, long bootWimBytes, long installBytes, boolean esd) {}

    static boolean available() { return AVAILABLE; }

    private static boolean loadReader() {
        try {
            System.loadLibrary("uavf_windows_iso");
            return true;
        } catch (UnsatisfiedLinkError unavailable) {
            return false;
        }
    }

    static Inspection inspect(ContentResolver resolver, Uri uri) throws IOException {
        try (ParcelFileDescriptor file = resolver.openFileDescriptor(uri, "r")) {
            if (file == null) throw new IOException("Unable to open the selected ISO.");
            return inspect(file);
        }
    }

    static Inspection inspect(ParcelFileDescriptor file) throws IOException {
        if (!available()) throw new IOException("Windows ISO reader is unavailable.");
        long[] result = probeFd(file.getFd());
        if (result == null || result.length != 5) throw new IOException("Windows ISO reader returned invalid data.");
        int status = (int) result[0];
        if (status != 0) throw statusFailure(status);
        return new Inspection(result[1], result[2], result[3], result[4] != 0);
    }

    /** Maps a native probe status to the message shown to the user. */
    static IOException statusFailure(int status) {
        return switch (status) {
            case 1 -> new IOException("Choose a local ISO file that supports random access.");
            case 2 -> new IOException("Could not read the UDF filesystem in this ISO.");
            case 3 -> new IOException("The ISO has no ARM64 EFI boot loader.");
            case 4 -> new IOException("The ISO has no readable sources/boot.wim.");
            case 5 -> new IOException("The ISO has no readable sources/install.wim or install.esd.");
            case 6 -> new IOException("The ISO file size is invalid: it must contain complete 2048-byte sectors.");
            default -> new IOException("Windows ISO inspection failed.");
        };
    }

    static String installDigest(ParcelFileDescriptor iso, long expectedSize) throws IOException {
        long handle = openPayloadFd(iso.getFd(), false);
        if (handle == 0) throw new IOException("Could not open the installation image inside the ISO.");
        try {
            if (payloadSize(handle) != expectedSize) throw new IOException("The installation image size changed.");
            return digestPayload(expectedSize, (offset, size, buffer) -> readPayload(handle, offset, size, buffer));
        } finally {
            closePayload(handle);
        }
    }

    @FunctionalInterface interface PayloadReader { int read(long offset, int size, byte[] buffer); }

    static String digestPayload(long expectedSize, PayloadReader reader) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
        byte[] buffer = new byte[1024 * 1024];
        for (long offset = 0; offset < expectedSize;) {
            int request = (int) Math.min((long) buffer.length, expectedSize - offset);
            int count = reader.read(offset, request, buffer);
            if (count <= 0 || count > request) throw new IOException("Could not read all of install.wim.");
            digest.update(buffer, 0, count);
            offset += count;
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static native long[] probeFd(int fd);

    /** Open boot.wim or the install WIM/ESD as a seekable read-only file without extracting it. */
    static ParcelFileDescriptor openPayload(Context context, ParcelFileDescriptor iso, boolean boot)
            throws IOException {
        if (!available()) throw new IOException("Windows ISO reader is unavailable.");
        return openProxy(context, openPayloadFd(iso.getFd(), boot));
    }

    /** Open an installation file within the selected ISO without a temporary copy. */
    static ParcelFileDescriptor openFile(Context context, ParcelFileDescriptor iso, String path)
            throws IOException {
        if (!available()) throw new IOException("Windows ISO reader is unavailable.");
        return openProxy(context, openFileFd(iso.getFd(), path));
    }

    private static ParcelFileDescriptor openProxy(Context context, long handle) throws IOException {
        if (handle == 0) throw new IOException("Could not open the requested file inside the ISO.");
        HandlerThread worker = new HandlerThread("U-AVF-iso-read");
        ProxyFileDescriptorCallback callback = new ProxyFileDescriptorCallback() {
            private boolean closed;

            @Override public synchronized long onGetSize() throws ErrnoException {
                if (closed) throw new ErrnoException("get ISO file size", OsConstants.EBADF);
                return payloadSize(handle);
            }

            @Override public synchronized int onRead(long offset, int size, byte[] data)
                    throws ErrnoException {
                if (closed) throw new ErrnoException("read ISO file", OsConstants.EBADF);
                int count = readPayload(handle, offset, size, data);
                if (count < 0) throw new ErrnoException("read ISO file", OsConstants.EIO);
                return count;
            }

            @Override public synchronized void onRelease() {
                if (closed) return;
                closed = true;
                closePayload(handle);
                worker.quitSafely();
            }
        };
        try {
            worker.start();
            return context.getSystemService(StorageManager.class).openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY, callback, new Handler(worker.getLooper()));
        } catch (IOException | RuntimeException error) {
            callback.onRelease();
            throw error;
        }
    }

    private static native long openPayloadFd(int fd, boolean boot);
    private static native long openFileFd(int fd, String path);
    private static native long payloadSize(long handle);
    private static native int readPayload(long handle, long offset, int size, byte[] data);
    private static native void closePayload(long handle);
}
