// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.view.MotionEvent;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class GuestDisplaySessionTest {
    public static void main(String[] args) throws Exception {
        staleStreamsCannotReplaceTheActiveInput();
        pointerKeepsItsPositionAndBounds();
        System.out.println("GUEST_DISPLAY_SESSION_TEST=PASS");
    }

    private static void staleStreamsCannotReplaceTheActiveInput() throws Exception {
        var old = new ByteArrayOutputStream();
        var active = new ByteArrayOutputStream();
        try (var connection = new DisplayConnection()) {
            int first = connection.begin();
            check(connection.publishInput(first, old), "Initial stream");
            int next = connection.begin();
            check(!connection.publishInput(first, old), "Previous epoch cannot replace input");
            check(connection.publishInput(next, active), "Current epoch publishes input");
            connection.sendAndWait(old, GuestInput.KEY, 1, 65, 0, false);
            connection.send(GuestInput.POINTER, 0, 1280, 800);
            connection.sendAndWait(active, GuestInput.BUTTON, 1, 0, 0, true);
            check(old.size() == 0, "Stale event must not reach the guest");
            ByteBuffer wire = ByteBuffer.wrap(active.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
            check(wire.remaining() == 32, "Two ordered UIN1 records");
            check(wire.getInt() == 0x314e4955 && wire.get() == 1 && wire.get() == 0 && wire.getShort() == 0
                    && wire.getInt() == 1280 && wire.getInt() == 800, "Pointer packet");
            check(wire.getInt() == 0x314e4955 && wire.get() == 2 && wire.get() == 1, "Button follows pointer");
        }
    }

    private static void pointerKeepsItsPositionAndBounds() {
        List<String> events = new ArrayList<>();
        var input = new GuestInput((kind, state, x, y) -> events.add(kind + ":" + state + ":" + x + ":" + y));
        input.pointer(12, 13, MotionEvent.ACTION_DOWN, 50, 8);
        input.pointer(20, 20, MotionEvent.ACTION_MOVE, 52, 8);
        input.pointer(24, 24, MotionEvent.ACTION_MOVE, 55, 8);
        input.pointer(28, 28, MotionEvent.ACTION_CANCEL, 56, 8);
        input.relativePointer(100, -100, 64, 64);
        check(events.equals(List.of("1:0:12:13", "2:1:0:0", "1:0:20:20", "1:0:28:28", "2:0:0:0", "1:0:63:0")),
                "Pointer ordering, throttling, cancel and relative clamp: " + events);
    }

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
