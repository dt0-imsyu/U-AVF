// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.input.GuestInput;

import com.example.winavf.ui.UiLanguage;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;
import com.example.winavf.runtime.RuntimeTransition;
import com.example.winavf.runtime.VmLifecycle;
import com.example.winavf.workspace.DisplaySettings;
import java.io.File;

/** Selects the Ubuntu display transport and owns retries for one Android viewport. */
final class UbuntuDisplaySession {
    interface Host {
        void connectServices(Object guest);
        boolean reconnectAllowed();
        boolean frameVisible();
        boolean displayFocused();
        void presentSurface(int width, int height, Runnable fit);
        void presentRawFrame(ConsoleFrameDecoder.Frame frame);
        void mavkDisconnected(Object guest);
        void show(String message);
    }

    private static final int ENCODED_RETRIES = 2;
    private static final long ENCODED_READY_MS = 25_000;
    private static final long ENCODED_HANDOFF_MS = 10_000;
    private static final long RAW_READY_MS = 35_000;
    private final Activity activity;
    private final Handler ui;
    private final SharedPreferences preferences;
    private final VmLifecycle lifecycle;
    private final RuntimeTransition transition;
    private final GuestDisplaySurfaces surfaces;
    private final FrameSurfaceView frameView;
    private final DisplayConnection connection;
    private final GuestInput input;
    private final Host host;
    private volatile Object guest;
    private boolean mavkGuestReady;
    private int retries;
    private boolean rawFallback;
    private boolean encodedPresented;
    private long encodedHandoffDeadline;
    private long initialFallbackAt;

    UbuntuDisplaySession(Activity activity, Handler ui, VmLifecycle lifecycle, RuntimeTransition transition,
                         GuestDisplaySurfaces surfaces, FrameSurfaceView frameView,
                         DisplayConnection connection, GuestInput input, Host host) {
        this.activity = activity;
        this.ui = ui;
        this.preferences = activity.getPreferences(0);
        this.lifecycle = lifecycle;
        this.transition = transition;
        this.surfaces = surfaces;
        this.frameView = frameView;
        this.connection = connection;
        this.input = input;
        this.host = host;
    }

    Object guest() { return guest; }

    void start(Object vm) {
        start(vm, false);
    }

    private void start(Object vm, boolean forceRaw) {
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed() || activity.isFinishing() || !lifecycle.owns(vm)) return;
            int epoch = connection.begin();
            surfaces.close();
            if (guest != vm) {
                guest = vm;
                retries = 0;
                rawFallback = false;
                encodedPresented = false;
                encodedHandoffDeadline = 0;
                initialFallbackAt = 0;
                mavkGuestReady = false;
            }
            host.connectServices(vm);
            rawFallback |= forceRaw;
            openTransport(vm, epoch, rawFallback);
        });
    }

    private void openTransport(Object vm, int epoch, boolean forceRaw) {
        if (!forceRaw && preferences.getBoolean("linux_current_boot_mavk", false)) {
            openMavk(vm, epoch);
            return;
        }
        var display = new DisplaySettings(preferences);
        boolean encoded = preferences.getBoolean("linux_current_boot_encoded",
                preferences.getBoolean("linux_current_boot_installed", false));
        if (!forceRaw && encoded && display.runningReceivesEncoded()) {
            String requested = preferences.getString("linux_current_boot_h264_resolution",
                    display.resolution(DisplaySettings.Mode.H264));
            openEncoded(vm, epoch, requested);
        } else {
            openRaw(vm, epoch);
        }
    }

    void guestReady(boolean ready) {
        mavkGuestReady = ready;
        MavkDisplayRuntime renderer = surfaces.renderer();
        if (renderer != null) renderer.setGuestSessionReady(ready);
    }

    private void openMavk(Object vm, int epoch) {
        SurfaceView surface = surfaces.openMavk();
        surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> surfaces.fitMavk());
        int refreshHz = preferences.getInt(DisplaySettings.RUNNING_REFRESH_HZ, DisplaySettings.DEFAULT_REFRESH_HZ);
        var renderer = new MavkDisplayRuntime(activity, vm, refreshHz, stream -> {
            if (surfaces.ownsMavk(surface)) connection.publishInput(epoch, stream);
        }, () -> presentMavk(surface, epoch), reason -> {
            if (!surfaces.ownsMavk(surface) || !connection.publishInput(epoch, null)) return;
            surfaces.close();
            Log.e("UAVF-Mavk", reason);
            host.mavkDisconnected(vm);
        }, host::displayFocused);
        surfaces.useRenderer(renderer);
        renderer.setGuestSessionReady(mavkGuestReady);
        renderer.start(surface);
    }

    private void presentMavk(SurfaceView surface, int epoch) {
        if (!surfaces.ownsMavk(surface) || !connection.current(epoch) || !transition.acceptsDesktop()) return;
        MavkDisplayRuntime renderer = surfaces.renderer();
        transition.event("Display connected", "Mavk AHB display fence", SystemClock.elapsedRealtime());
        host.presentSurface(renderer.frameWidth(), renderer.frameHeight(), surfaces::fitMavk);
    }

    private void openEncoded(Object vm, int epoch, String requested) {
        if (initialFallbackAt == 0) initialFallbackAt = SystemClock.elapsedRealtime() + ENCODED_HANDOFF_MS;
        SurfaceView surface = surfaces.openEncoded();
        surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitEncoded(surface, r - l, b - t));
        var decoder = new EncodedDisplayProbe(activity, vm, 60, () -> ui.postDelayed(
                () -> encodedEnded(vm, epoch, surface, false), 1200));
        long readyDelay = encodedHandoffDeadline == 0 ? ENCODED_READY_MS
                : Math.max(0, encodedHandoffDeadline - SystemClock.elapsedRealtime());
        ui.postDelayed(() -> {
            if (!decoder.hasDesktopFrame()) encodedEnded(vm, epoch, surface, true);
        }, readyDelay);
        decoder.onStreamGeometry(() -> {
            if (surfaces.ownsEncoded(surface)) surfaces.fitEncoded();
        });
        decoder.requestDesktopSize(new WorkspaceConfig(4, false, requested));
        surfaces.useDecoder(decoder);
        decoder.startEmbedded(surface, stream -> {
            if (surfaces.ownsEncoded(surface) && connection.publishInput(epoch, stream)) {
                transition.event("Display connected", "Encoded vsock stream", SystemClock.elapsedRealtime());
            }
        }, () -> presentEncoded(decoder, surface, epoch, requested), () -> {
            if (mayReconnect(vm, epoch) && surfaces.ownsEncoded(surface)) {
                input.releaseKeys();
                start(vm);
            }
        });
    }

    private void fitEncoded(SurfaceView surface, int width, int height) {
        if (!surfaces.ownsEncoded(surface)) return;
        surfaces.fitEncoded();
        var decoder = surfaces.decoder();
        int sourceWidth = decoder == null ? 1920 : Math.max(1, decoder.streamWidth());
        // Clip the known one-source-pixel edge artifact without moving the viewport.
        if (width > 0 && height > 0) {
            surface.setClipBounds(new Rect(Math.max(1, (width + sourceWidth - 1) / sourceWidth), 0, width, height));
        }
    }

    private void presentEncoded(EncodedDisplayProbe decoder, SurfaceView surface, int epoch, String requested) {
        if (!surfaces.ownsEncoded(surface) || !connection.current(epoch) || !transition.acceptsDesktop()) return;
        encodedPresented = true;
        encodedHandoffDeadline = 0;
        retries = 0;
        boolean firstFrame = !host.frameVisible();
        int width = decoder.streamWidth();
        int height = decoder.streamHeight();
        host.presentSurface(width, height, surfaces::fitEncoded);
        String actual = width + "x" + height;
        if (firstFrame && !requested.equals(actual)) {
            Log.w("UAVF-H264", "Guest display size differs from requested size: " + requested + " -> " + actual);
            Toast.makeText(activity, UiLanguage.format("Ubuntu передаёт %1$s. Запрошено %2$s.",
                    actual.replace('x', '×'), requested.replace('x', '×')), Toast.LENGTH_LONG).show();
        }
    }

    private void encodedEnded(Object vm, int epoch, SurfaceView surface, boolean timedOut) {
        if (!mayReconnect(vm, epoch) || !surfaces.ownsEncoded(surface)) return;
        long now = SystemClock.elapsedRealtime();
        if (encodedPresented && encodedHandoffDeadline == 0) {
            encodedHandoffDeadline = now + ENCODED_HANDOFF_MS;
        }
        var decoder = surfaces.decoder();
        boolean handoff = encodedHandoffDeadline != 0;
        boolean retry = handoff ? now < encodedHandoffDeadline
                : now < initialFallbackAt || (decoder != null
                        && (timedOut || decoder.retryableSessionEnd()) && retries < ENCODED_RETRIES);
        input.releaseKeys();
        connection.publishInput(epoch, null);
        surfaces.close();
        if (retry) {
            if (handoff) Log.i("UAVF-H264", "Reconnecting Ubuntu encoded display during login handoff");
            else {
                retries++;
                Log.i("UAVF-H264", "Retrying Ubuntu encoded display " + retries + "/" + ENCODED_RETRIES);
            }
            ui.postDelayed(() -> {
                if (mayReconnect(vm, epoch)) start(vm);
            }, handoff ? Math.min(1000, encodedHandoffDeadline - now) : 1000);
        } else {
            Log.w("UAVF-H264", "Encoded display unavailable; switching to raw for this Ubuntu session");
            Toast.makeText(activity, UiLanguage.text("Включён резервный вывод Ubuntu."), Toast.LENGTH_LONG).show();
            start(vm, true);
        }
    }

    private boolean mayReconnect(Object vm, int epoch) {
        return !activity.isDestroyed() && guest == vm && lifecycle.owns(vm)
                && host.reconnectAllowed() && connection.current(epoch);
    }

    private void openRaw(Object vm, int epoch) {
        long deadline = SystemClock.elapsedRealtime() + RAW_READY_MS;
        var ready = new java.util.concurrent.atomic.AtomicBoolean();
        connectRaw(vm, epoch, deadline, ready);
        ui.postDelayed(() -> {
            if (!mayReconnect(vm, epoch) || ready.get()) return;
            connection.begin();
            transition.failure("Ubuntu produced no frame on the fallback display.",
                    SystemClock.elapsedRealtime());
        }, RAW_READY_MS);
    }

    private void connectRaw(Object vm, int epoch, long deadline,
                            java.util.concurrent.atomic.AtomicBoolean ready) {
        File report = new File(activity.getExternalFilesDir(null), "ubuntu-gnome-frame-bridge-report.txt");
        new RawDisplayReceiver(vm, connection, epoch, report, frameView::performanceSnapshot, frame -> {
            ready.set(true);
            host.presentRawFrame(frame);
        }, host::show, () -> ui.postDelayed(() -> {
            if (!ready.get() && mayReconnect(vm, epoch) && SystemClock.elapsedRealtime() < deadline) {
                connectRaw(vm, epoch, deadline, ready);
            }
        }, 1000)).start();
    }
}
