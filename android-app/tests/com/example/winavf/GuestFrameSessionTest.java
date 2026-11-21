// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

/**
 * Checks the frame ordering rules without a device.
 *
 * These rules decide whether a decoded frame reaches the visible desktop, so a
 * mistake shows up as a frozen or flickering guest, or as a frame from a dead
 * connection painted over a fresh one.
 */
public final class GuestFrameSessionTest {
    public static void main(String[] args) {
        ordering();
        reconnectDropsOldConnection();
        geometrySurvivesDeferral();
        resetClearsState();
        System.out.println("GUEST_FRAME_SESSION_TEST=PASS");
    }

    private static void ordering() {
        GuestFrameSession session = new GuestFrameSession();
        check(session.offer(session.connection(), 1) == GuestFrameSession.Accept.DEFERRED,
                "first frame is deferred until the transition finishes");
        session.desktopReady();

        check(session.offer(session.connection(), 2) == GuestFrameSession.Accept.PRESENT,
                "newer sequence is presented");
        check(session.offer(session.connection(), 2) == GuestFrameSession.Accept.STALE,
                "duplicate sequence is stale");
        check(session.offer(session.connection(), 1) == GuestFrameSession.Accept.STALE,
                "older sequence is stale");
        check(session.offer(session.connection(), 3) == GuestFrameSession.Accept.PRESENT,
                "later sequence is presented");
    }

    private static void reconnectDropsOldConnection() {
        GuestFrameSession session = new GuestFrameSession();
        session.desktopReady();
        long oldConnection = session.connection();
        session.offer(oldConnection, 10);
        check(session.visible() == false, "not visible until markVisible");

        session.reconnect();
        session.desktopReady();
        // A high sequence from the dead connection must not win over the new one.
        check(session.offer(oldConnection, 99) == GuestFrameSession.Accept.STALE,
                "frame from the previous connection is stale");
        check(session.offer(session.connection(), 1) == GuestFrameSession.Accept.PRESENT,
                "first frame of the new connection is presented even though its sequence restarted");
        check(session.connection() != oldConnection, "connection id advanced");
    }

    private static void geometrySurvivesDeferral() {
        GuestFrameSession session = new GuestFrameSession();
        // Geometry must be remembered before the desktop is ready: the pointer
        // mapping needs it as soon as the first frame arrives.
        session.observeGeometry(1920, 1200);
        check(session.width() == 1920 && session.height() == 1200,
                "geometry recorded while deferred");
        session.offer(session.connection(), 1);
        check(session.width() == 1920, "geometry unchanged by a deferred frame");

        session.observeGeometry(0, 0);
        check(session.width() == 1920, "zero geometry is ignored");
    }

    private static void resetClearsState() {
        GuestFrameSession session = new GuestFrameSession();
        session.desktopReady();
        session.observeGeometry(1280, 800);
        session.offer(session.connection(), 5);
        session.markVisible();
        check(session.visible(), "visible after markVisible");

        session.reset();
        check(!session.visible(), "reset clears visibility");
        check(session.width() == 0 && session.height() == 0, "reset clears geometry");
        check(session.offer(session.connection(), 6) == GuestFrameSession.Accept.DEFERRED,
                "after reset frames are deferred again");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError("Failed: " + what);
    }
}
