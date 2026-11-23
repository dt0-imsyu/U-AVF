// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.input;

import android.graphics.Rect;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

/** Reserves the side of the desktop for the optional hold-and-swipe session shortcut. */
public final class SessionEdgeGesture {
    private boolean tracking;
    private boolean triggered;
    private long pressedAt;
    private float startX;
    private float startY;

    public void updateExclusion(View viewport, boolean enabled) {
        if (!enabled) {
            viewport.setSystemGestureExclusionRects(List.of());
            return;
        }
        int right = viewport.getWidth();
        int height = viewport.getHeight();
        int middle = height / 2;
        int halfHeight = dp(viewport, 100);
        viewport.setSystemGestureExclusionRects(List.of(new Rect(Math.max(0, right - dp(viewport, 24)),
                Math.max(0, middle - halfHeight), right, Math.min(height, middle + halfHeight))));
    }

    public boolean onTouch(MotionEvent event, View viewport, boolean enabled, Runnable openDrawer) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) begin(event, viewport, enabled);
        if (!tracking) return false;

        if (action == MotionEvent.ACTION_MOVE && !triggered && event.getEventTime() - pressedAt >= 500
                && startX - event.getX() >= dp(viewport, 72) && Math.abs(event.getY() - startY) < dp(viewport, 80)) {
            triggered = true;
            openDrawer.run();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) tracking = false;
        return true;
    }

    private void begin(MotionEvent event, View viewport, boolean enabled) {
        tracking = enabled && !event.isFromSource(InputDevice.SOURCE_MOUSE)
                && event.getX() >= viewport.getWidth() - dp(viewport, 24)
                && Math.abs(event.getY() - viewport.getHeight() / 2f) <= dp(viewport, 100);
        if (!tracking) return;
        triggered = false;
        pressedAt = event.getEventTime();
        startX = event.getX();
        startY = event.getY();
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
