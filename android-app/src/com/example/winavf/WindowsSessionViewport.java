// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;
import android.view.SurfaceView;
import com.example.winavf.runtime.RuntimeTransition;

/** Connects the Windows Setup lifecycle to the embedded H.264 viewport. */
final class WindowsSessionViewport implements WindowsSetupSession.View {
    interface Host {
        void selectWindows();
        void begin(RuntimeTransition.Mode mode);
        void refreshTransition();
        void showWorkspace();
        boolean frameVisible();
        void frameLost();
        void geometry(int width, int height);
        void present(int width, int height);
    }

    private final Activity activity;
    private final Handler ui;
    private final RuntimeTransition transition;
    private final GuestDisplaySurfaces surfaces;
    private final FrameSurfaceView frame;
    private final DisplayConnection connection;
    private final GuestInput input;
    private final Host host;
    private final WindowsSetupSession session;

    WindowsSessionViewport(Activity activity, Handler ui, RuntimeTransition transition, GuestDisplaySurfaces surfaces,
                           FrameSurfaceView frame, DisplayConnection connection, GuestInput input, Host host) {
        this.activity = activity;
        this.ui = ui;
        this.transition = transition;
        this.surfaces = surfaces;
        this.frame = frame;
        this.connection = connection;
        this.input = input;
        this.host = host;
        this.session = new WindowsSetupSession(ui, this);
    }

    void attach(WindowsSetupRuntime setup) { session.attach(setup); }
    boolean viewing(WindowsSetupRuntime setup) { return session.viewing(setup); }
    void close() { session.close(); }

    @Override public void attached(WindowsSetupRuntime setup) {
        host.selectWindows();
        if (setup.rebootSuccessor()) showReboot();
        else host.begin(RuntimeTransition.Mode.BOOT);
    }

    @Override public void observed(WindowsSetupRuntime setup) {
        if (transition.visible()) describe(setup, false);
    }

    @Override public void phaseChanged(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase phase) {
        switch (phase) {
            case BOOTING -> event("Virtual machine created", "Windows Setup AVF owner");
            case WAITING_DISPLAY, DISPLAYING -> {
                event("Windows kernel", "KD readback and continue");
                if (surfaces.decoder() == null) retryDisplay(setup);
            }
            case STOPPING -> {
                if (!setup.restarting()) return;
                detachDisplay();
                host.begin(RuntimeTransition.Mode.REBOOT);
                showReboot();
            }
            default -> {}
        }
    }

    private void showReboot() {
        if (transition.mode() != RuntimeTransition.Mode.REBOOT) host.begin(RuntimeTransition.Mode.REBOOT);
        event("VM stopped", "AVF reboot callback");
    }

    private void describe(WindowsSetupRuntime setup, boolean active) {
        if (setup.installing()) event("Windows installation", active ? "Guided install active" : "Guided install requested");
        else if (setup.setupEnvironment()) event("Windows setup", active ? "WinPE active" : "WinPE requested");
    }

    @Override public void finished(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase phase) {
        if (phase == WindowsSetupRuntime.Phase.FAILED) {
            transition.failure(setup.error(), SystemClock.elapsedRealtime());
            host.refreshTransition();
        } else {
            transition.dismiss();
            host.showWorkspace();
        }
    }

    @Override public void detachDisplay() {
        input.releaseKeys();
        connection.begin();
        surfaces.closeEncoded();
        frame.setEncodedMode(false, 0, 0);
        host.frameLost();
    }

    @Override public void retryDisplay(WindowsSetupRuntime setup) {
        if (!session.viewing(setup) || (setup.phase() != WindowsSetupRuntime.Phase.WAITING_DISPLAY
                && setup.phase() != WindowsSetupRuntime.Phase.DISPLAYING)) return;
        int epoch = connection.begin();
        SurfaceView surface = surfaces.openEncoded();
        surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> surfaces.fitEncoded());
        var decoder = new EncodedDisplayProbe(activity, setup, 60f, () -> displayEnded(setup, surface, epoch), 640, 480);
        decoder.acceptNativeWindowsMode();
        decoder.onStreamGeometry(() -> updateGeometry(surface, decoder));
        surfaces.useDecoder(decoder);
        decoder.startEmbedded(surface, stream -> {
            if (surfaces.ownsEncoded(surface)) connection.publishInput(epoch, stream);
        }, () -> firstFrame(setup, surface, decoder), () -> {
            if (owns(surface)) retryDisplay(setup);
        });
    }

    private boolean owns(SurfaceView surface) { return surfaces.ownsEncoded(surface) && !activity.isDestroyed(); }

    private void updateGeometry(SurfaceView surface, EncodedDisplayProbe decoder) {
        if (!owns(surface)) return;
        host.geometry(decoder.streamWidth(), decoder.streamHeight());
        surfaces.fitEncoded();
    }

    private void firstFrame(WindowsSetupRuntime setup, SurfaceView surface, EncodedDisplayProbe decoder) {
        if (!owns(surface) || !setup.displayReady()) return;
        updateGeometry(surface, decoder);
        if (setup.setupEnvironment()) event("Windows setup frame", "Validated WinPE Surface frame");
        host.present(decoder.streamWidth(), decoder.streamHeight());
    }

    private void displayEnded(WindowsSetupRuntime setup, SurfaceView surface, int epoch) {
        if (!owns(surface) || !setup.active() || !connection.publishInput(epoch, null)) return;
        host.frameLost();
        setup.displayFailed();
        // Sign-in can replace the capture stream briefly. Keep the guest alive while it reconnects.
        ui.postDelayed(() -> {
            if (!session.viewing(setup) || !setup.active() || host.frameVisible()
                    || transition.mode() == RuntimeTransition.Mode.REBOOT) return;
            if (!transition.visible()) host.begin(RuntimeTransition.Mode.BOOT);
            describe(setup, true);
            event("Windows display reconnecting", "Waiting for replacement stream");
        }, 2500);
    }

    private void event(String name, String detail) {
        transition.event(name, detail, SystemClock.elapsedRealtime());
    }
}
