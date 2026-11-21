// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.SystemClock;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Opens the verified Live installer's UI when the guest advertises the install profile. */
final class UbuntuInstallerAutostart {
    private static final long WAIT_MS = 300_000;
    private static final int ALT_LEFT = 0xffe9;
    private static final int F10 = 0xffc7;
    private static final int F11 = 0xffc8;

    private final DisplayConnection display;
    private final BooleanSupplier active;
    private final IntSupplier revision;
    private final Runnable installing;
    private final Consumer<String> report;
    private final Runnable reveal;

    UbuntuInstallerAutostart(DisplayConnection display, BooleanSupplier active, IntSupplier revision,
                            Runnable installing, Consumer<String> report, Runnable reveal) {
        this.display = display;
        this.active = active;
        this.revision = revision;
        this.installing = installing;
        this.report = report;
        this.reveal = reveal;
    }

    void start() {
        new Thread(this::run, "U-AVF-ubuntu-installer-autostart").start();
    }

    private void run() {
        try {
            waitFor(() -> revision.getAsInt() != 0);
            int guestRevision = revision.getAsInt();
            if (!active.getAsBoolean() || guestRevision != 1) {
                report.accept("INSTALLER_AUTOSTART=SKIPPED_NOT_LIVE_INSTALL_PROFILE guest_revision=" + guestRevision);
                return;
            }
            waitFor(display::hasInput);
            OutputStream input = display.input();
            if (!active.getAsBoolean() || input == null) {
                report.accept("INSTALLER_AUTOSTART=NO_FRAME_INPUT_TIMEOUT");
                return;
            }
            requestInstaller(input);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            report.accept("INSTALLER_AUTOSTART=FAILED " + IoUtil.rootMessage(error));
        }
    }

    private void requestInstaller(OutputStream input) throws Exception {
        Thread.sleep(2500);
        if (!active.getAsBoolean()) return;
        display.sendAndWait(input, GuestInput.START_INSTALLER, 1, 1, 0, true);
        installing.run();
        report.accept("INSTALLER_AUTOSTART=ALLOWLISTED_REQUEST_SENT");
        Thread.sleep(8000);
        if (!active.getAsBoolean()) return;
        key(input, ALT_LEFT, true);
        key(input, F10, true);
        key(input, F10, false);
        key(input, ALT_LEFT, false);
        Thread.sleep(250);
        key(input, F11, true);
        key(input, F11, false);
        display.drain();
        report.accept("INSTALLER_FULLSCREEN_REQUEST=ALT_F10_AND_F11_SENT");
        report.accept("INSTALLER_WINDOW_VERIFIED=NOT_AVAILABLE_FROM_CURRENT_AVF_API");
        reveal.run();
    }

    private void key(OutputStream input, int symbol, boolean pressed) throws Exception {
        display.sendAndWait(input, GuestInput.KEY, pressed ? 1 : 0, symbol, 0, false);
    }

    private void waitFor(BooleanSupplier ready) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + WAIT_MS;
        while (active.getAsBoolean() && !ready.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(250);
        }
    }
}
