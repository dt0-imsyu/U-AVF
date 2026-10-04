// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.util.Objects;

/** Optional text-only side channel; queues at most the latest clipboard value. */
final class GuestClipboard implements AutoCloseable {
    private final Activity activity;
    private final Object vm;
    private final ClipboardManager clipboard;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private volatile boolean closed, foreground;
    private volatile ParcelFileDescriptor socket;
    private volatile String status = "Connecting";
    private String pendingText, lastAndroidText;
    private boolean request;
    private final ClipboardManager.OnPrimaryClipChangedListener changed = this::schedulePublish;
    private final Runnable publish = () -> publishLocal(false);

    GuestClipboard(Activity activity, Object vm) {
        this.activity = activity;
        this.vm = vm;
        clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        foreground = activity.hasWindowFocus();
        clipboard.addPrimaryClipChangedListener(changed);
        Thread thread = new Thread(this::connectLoop, "U-AVF-clipboard");
        thread.setDaemon(true);
        thread.start();
    }
    void focus(boolean focused) {
        foreground = focused;
        if (focused) publishLocal(true);
    }
    String status() { return status; }
    private void schedulePublish() {
        if (!closed && foreground) {
            main.removeCallbacks(publish);
            main.postDelayed(publish, 200);
        }
    }
    private void publishLocal(boolean queryIfUnchanged) {
        if (closed || !foreground) return;
        ClipData data = clipboard.getPrimaryClip();
        CharSequence raw = data != null && data.getItemCount() > 0 ? data.getItemAt(0).getText() : null;
        if (raw == null) {
            if (queryIfUnchanged) enqueueRequest();
            return; // Never resolve URIs, files or implicit content-provider text.
        }
        if (raw.length() > ClipboardWire.MAX_BYTES) return;
        String text = raw.toString();
        if (Objects.equals(text, lastAndroidText)) {
            if (queryIfUnchanged) enqueueRequest();
            return;
        }
        try { ClipboardWire.encode(ClipboardWire.TEXT, text); }
        catch (IOException oversized) { return; }
        lastAndroidText = text;
        synchronized (lock) { pendingText = text; lock.notifyAll(); }
    }
    private void enqueueRequest() {
        synchronized (lock) { request = true; lock.notifyAll(); }
    }
    private void connectLoop() {
        while (!closed) {
            ParcelFileDescriptor descriptor = null;
            try {
                descriptor = (ParcelFileDescriptor) vm.getClass().getMethod("connectVsock", long.class)
                    .invoke(vm, 4054L);
                socket = descriptor;
                FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
                FileOutputStream output = new FileOutputStream(descriptor.getFileDescriptor());
                final ParcelFileDescriptor connection = descriptor;
                main.post(() -> { if (!closed) { lastAndroidText = null; publishLocal(true); } });
                Thread writer = new Thread(() -> writeLoop(connection, output), "U-AVF-clipboard-send");
                writer.setDaemon(true);
                writer.start();
                Log.i("U-AVF", "Clipboard 4054 connected (text only)");
                status = "Channel connected";
                while (!closed && socket == descriptor) {
                    ClipboardWire.Message message = ClipboardWire.read(input);
                    if (message.type == ClipboardWire.TEXT) main.post(() -> {
                        if (!closed && foreground && socket == connection &&
                            !Objects.equals(message.text, lastAndroidText)) {
                            lastAndroidText = message.text;
                            clipboard.setPrimaryClip(ClipData.newPlainText("Ubuntu", message.text));
                        }
                    });
                }
            } catch (Exception unavailable) {
                // Old guest runtimes legitimately do not expose this optional port.
                // Never fail the VM, display or audio because clipboard is unavailable.
                status = "Waiting for guest channel";
                if (!closed) Log.i("U-AVF", "Clipboard 4054 retry: " + unavailable.getClass().getSimpleName());
            } finally {
                if (socket == descriptor) socket = null;
                if (descriptor != null) try { descriptor.close(); } catch (IOException ignored) { }
                synchronized (lock) { lock.notifyAll(); }
            }
            try { Thread.sleep(5000); } catch (InterruptedException stopped) { return; }
        }
    }
    private void writeLoop(ParcelFileDescriptor connection, OutputStream output) {
        try {
            while (!closed && socket == connection) {
                String text;
                boolean query;
                synchronized (lock) {
                    while (!closed && socket == connection && pendingText == null && !request) lock.wait();
                    if (closed || socket != connection) return;
                    text = pendingText; pendingText = null;
                    query = request; request = false;
                }
                if (text != null) output.write(ClipboardWire.encode(ClipboardWire.TEXT, text));
                else if (query) output.write(ClipboardWire.encode(ClipboardWire.REQUEST, ""));
                output.flush();
            }
        } catch (Exception error) {
            try { connection.close(); } catch (IOException ignored) { }
        }
    }
    @Override public void close() {
        closed = true;
        status = "Disabled";
        main.removeCallbacks(publish);
        clipboard.removePrimaryClipChangedListener(changed);
        ParcelFileDescriptor connection = socket;
        socket = null;
        if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        synchronized (lock) { pendingText = null; lock.notifyAll(); }
    }
}
