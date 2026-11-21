// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.Handler;

/** Follows the service-owned Windows guest across Activity attachment and AVF reboot. */
final class WindowsSetupSession {
    interface View {
        void attached(WindowsSetupRuntime setup);
        void observed(WindowsSetupRuntime setup);
        void phaseChanged(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase phase);
        void retryDisplay(WindowsSetupRuntime setup);
        void detachDisplay();
        void finished(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase phase);
    }

    private static final long POLL_MS = 500;
    private final Handler ui;
    private final View view;
    private WindowsSetupRuntime current;
    private boolean closed;

    WindowsSetupSession(Handler ui, View view) {
        this.ui = ui;
        this.view = view;
    }

    boolean viewing(WindowsSetupRuntime setup) { return current == setup; }

    void attach(WindowsSetupRuntime setup) {
        if (closed || current == setup || setup == null) return;
        current = setup;
        view.attached(setup);
        poll(setup, null);
    }

    private void poll(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase previous) {
        if (closed || current != setup) return;
        var phase = setup.phase();
        view.observed(setup);
        if (phase != previous) view.phaseChanged(setup, phase);
        if (phase == WindowsSetupRuntime.Phase.DISPLAY_UNAVAILABLE && setup.retryDisplay()) {
            view.retryDisplay(setup);
        }
        if (setup.active()) {
            schedulePoll(setup, phase);
        } else if (setup.continueInstalled()) {
            attachSuccessor(setup, phase);
        } else {
            current = null;
            view.detachDisplay();
            view.finished(setup, phase);
        }
    }

    private void schedulePoll(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase phase) {
        ui.postDelayed(() -> poll(setup, phase), POLL_MS);
    }

    private void attachSuccessor(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase phase) {
        WindowsSetupRuntime successor = VmRuntimeService.windowsSetup();
        if (successor == setup) schedulePoll(setup, phase);
        else if (successor != null) {
            view.detachDisplay();
            attach(successor);
        }
    }

    void close() {
        closed = true;
        current = null;
    }
}
