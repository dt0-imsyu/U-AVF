// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

/**
 * Tracks which guest frames are still relevant for the visible desktop.
 *
 * Two problems are solved here, both of which used to be handled ad hoc in
 * MainActivity:
 *
 * 1. Stale frames. The decoder emits a monotonically increasing sequence per
 *    connection, but nothing consumed it, so a frame queued just before a
 *    reconnect could be presented after the new session already drew. The
 *    sequence is now checked and late frames are dropped.
 *
 * 2. Frame geometry ownership. Frame size is needed by the pointer mapping and
 *    by the surface sizing, and was written from three different call sites.
 *    It now has one owner that also resets on disconnect.
 *
 * The class is deliberately free of Android types so the ordering rules can be
 * tested on the host.
 */
final class GuestFrameSession {
    /** Result of offering a frame to the visible desktop. */
    enum Accept {
        /** The frame is newer than anything presented and should be shown. */
        PRESENT,
        /** The frame belongs to a previous connection and must be ignored. */
        STALE,
        /** The desktop is not accepting frames yet. */
        DEFERRED
    }

    private long connection;
    private int lastSequence = -1;
    private int width;
    private int height;
    private boolean visible;
    private boolean desktopReady;

    /**
     * Starts a new connection. Frames from the previous one are rejected even
     * if their sequence numbers are higher, because sequence numbers restart.
     */
    synchronized void reconnect() {
        connection++;
        lastSequence = -1;
        width = 0;
        height = 0;
        visible = false;
        desktopReady = false;
    }

    /** Marks the transition finished so frames may be presented. */
    synchronized void desktopReady() {
        desktopReady = true;
    }

    /** Clears the ready flag when the guest stops or the surface is torn down. */
    synchronized void reset() {
        desktopReady = false;
        lastSequence = -1;
        width = 0;
        height = 0;
        visible = false;
    }

    boolean visible() {
        return visible;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    long connection() {
        return connection;
    }

    /** Records the geometry reported by the decoder, independent of presentation. */
    synchronized void observeGeometry(int frameWidth, int frameHeight) {
        if (frameWidth > 0 && frameHeight > 0) {
            width = frameWidth;
            height = frameHeight;
        }
    }

    /**
     * Decides what to do with an arriving frame.
     *
     * @param expectedConnection the connection the frame belongs to
     * @param sequence           the frame's sequence within that connection
     */
    synchronized Accept offer(long expectedConnection, int sequence) {
        if (expectedConnection != connection) return Accept.STALE;
        if (sequence <= lastSequence) return Accept.STALE;
        if (!desktopReady) {
            // Geometry still matters: the pointer mapping needs it as soon as
            // the first frame of a connection arrives.
            return Accept.DEFERRED;
        }
        lastSequence = sequence;
        return Accept.PRESENT;
    }

    /** Marks the desktop visible after the first accepted frame was presented. */
    synchronized void markVisible() {
        visible = true;
    }
}
