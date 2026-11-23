// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.ui.RuntimeTransitionView;

import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import com.example.winavf.runtime.RuntimeTransition;

/**
 * Drives the boot/shutdown/reboot transition overlay.
 *
 * The overlay is a mode machine: it appears when a launch begins, shows what the
 * guest is doing, and gives way to the desktop once a real frame arrives. Three
 * things used to be tangled in MainActivity for this: a repeating tick, the
 * per-activity handler, and the destroyed flag. They are kept together here so
 * the ordering rules are visible in one place:
 *
 *   - the tick stops as soon as the transition is no longer visible;
 *   - a completion from an older generation cannot hide a newer overlay;
 *   - nothing is scheduled after the activity is destroyed.
 */
final class TransitionController {
    /** What the surrounding screen needs to do when the overlay changes state. */
    interface Host {
        /** True once a guest VM exists that the user may force-stop. */
        boolean canForceStop();

        /** Runs when the user asks to force-stop from the overlay. */
        void forceStop(boolean forReboot);
        void stopWindowsLaunch();
        void stopUbuntuBoot();
        void recoverWindows();

        /** Hides the overlay and returns to the mode-selection screen. */
        void dismissed();

        /** Records observed runtime events; called on every tick. */
        void recordEvents();

        /** Reveals the running desktop once the overlay is gone. */
        void desktopRevealed();

        /** True when the active profile is Windows; the overlay wording differs. */
        boolean isWindows();
    }

    private static final long TICK_INTERVAL_MS = 1000L;
    private static final long DESKTOP_SETTLE_MS = 450L;

    private final Activity activity;
    private final RuntimeTransition transition;
    private final Handler handler;
    private final Host host;

    private RuntimeTransitionView view;
    private volatile boolean destroyed;
    private volatile boolean transitionWindows;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            host.recordEvents();
            if (view != null) {
                view.allowForceStop(host.canForceStop());
                view.refresh();
            }
            if (transition.visible()) handler.postDelayed(this, TICK_INTERVAL_MS);
        }
    };

    TransitionController(Activity activity, RuntimeTransition transition, Handler handler, Host host) {
        this.activity = activity;
        this.transition = transition;
        this.handler = handler;
        this.host = host;
    }

    /** Stops all future ticks. Called from onDestroy. */
    void destroyed() {
        destroyed = true;
        handler.removeCallbacks(tick);
    }

    /** Shows the overlay for a new launch, replacing any previous one. */
    void begin(RuntimeTransition.Mode mode) {
        /*
         * The profile is refreshed on every begin, including the reboot-to-boot
         * handoff. Otherwise a guest that rebooted while the other profile was
         * selected keeps showing that profile's name: a later Ubuntu launch
         * would still say "Запускаем Windows", because the reboot guard below
         * preserves the transition state but not the identity behind it.
         */
        transitionWindows = host.isWindows();
        // Keep reboot evidence visible until the restarted VM produces a frame.
        if (!(mode == RuntimeTransition.Mode.BOOT
                && transition.mode() == RuntimeTransition.Mode.REBOOT)) {
            transition.begin(mode, SystemClock.elapsedRealtime());
        }
        activity.runOnUiThread(() -> {
            if (destroyed) return;
            if (view == null) {
                view = createView();
                activity.addContentView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            handler.removeCallbacks(tick);
            tick.run();
        });
    }

    private RuntimeTransitionView createView() {
        return new RuntimeTransitionView(activity, transition, () -> transitionWindows,
                () -> host.forceStop(transition.operation() == RuntimeTransition.Mode.REBOOT),
                () -> {
                    if (transitionWindows) host.stopWindowsLaunch();
                    else host.stopUbuntuBoot();
                }, () -> dismiss(host::recoverWindows),
                () -> dismiss(host::dismissed));
    }

    private void dismiss(Runnable destination) {
        transition.dismiss();
        destination.run();
        view.hide();
    }

    /** True while the overlay should still be on screen. */
    boolean visible() {
        return transition.visible();
    }

    /** Restarts the tick, used when returning to a transition that is still up. */
    void resume() {
        activity.runOnUiThread(() -> {
            handler.removeCallbacks(tick);
            tick.run();
        });
    }

    /** Restarts the tick so the overlay redraws with current state. */
    void refresh() {
        resume();
    }

    /** Hides the overlay without dismissing the transition state. */
    void hideOverlay() {
        activity.runOnUiThread(() -> {
            if (view != null) view.hide();
        });
    }

    /**
     * Marks the desktop as reached and hides the overlay shortly after.
     *
     * Returns true only for the frame that actually completed the transition,
     * so callers can record history once and repeated frames stay cheap.
     * The generation check prevents a late completion from a previous launch
     * from hiding an overlay that belongs to a newer one.
     */
    boolean desktopReached() {
        if (!transition.desktop(SystemClock.elapsedRealtime())) return false;
        activity.runOnUiThread(host::recordEvents);
        int completedGeneration = transition.generation();
        activity.runOnUiThread(() -> {
            if (view != null) view.refresh();
            handler.postDelayed(() -> {
                if (destroyed) return;
                if (transition.finishDesktop(completedGeneration)) host.desktopRevealed();
            }, DESKTOP_SETTLE_MS);
        });
        return true;
    }
}
