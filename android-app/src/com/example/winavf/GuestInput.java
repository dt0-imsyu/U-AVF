// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Translates Android input into guest events. Android views own focus and hit testing. */
final class GuestInput {
    static final int POINTER = 1;
    static final int BUTTON = 2;
    static final int KEY = 3;
    static final int SCROLL = 4;
    static final int START_INSTALLER = 5;

    interface Sink { void send(int kind, int state, int first, int second); }
    record Focus(boolean keyboard, boolean shortcut, boolean transitioning) {}

    private final Sink sink;
    private final GuestKeyboard keys;
    private boolean ctrl, alt, shift;
    private int consumedShortcut = KeyEvent.KEYCODE_UNKNOWN;
    private int x = -1, y = -1;
    private long lastMove;

    GuestInput(Sink sink) {
        this.sink = sink;
        keys = new GuestKeyboard((keysym, down) -> sink.send(KEY, down ? 1 : 0, keysym, 0));
    }

    boolean isPanelShortcut(KeyEvent event, int preferredKey) {
        int key = event.getKeyCode();
        return (key == KeyEvent.KEYCODE_ESCAPE || key == preferredKey)
                && (ctrl || event.isCtrlPressed()) && (alt || event.isAltPressed())
                && (shift || event.isShiftPressed());
    }

    boolean handleKey(KeyEvent event, Focus focus, int preferredKey, Runnable togglePanel) {
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        if (!down && !up) return false;
        updateModifiers(key, down);

        if (up && consumedShortcut == key) {
            consumedShortcut = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK) {
            if (!focus.keyboard() || focus.transitioning() || !isPhysicalKeyboardBack(event)) return false;
            return keys.handle(key, down, event.getMetaState(), 0);
        }
        if (focus.shortcut() && !focus.transitioning() && down && isPanelShortcut(event, preferredKey)) {
            if (event.getRepeatCount() == 0) {
                consumedShortcut = key;
                releaseKeys();
                togglePanel.run();
            }
            return true;
        }
        if (!focus.keyboard() || isPanelShortcut(event, preferredKey)) return false;

        int meta = event.getMetaState();
        int textMeta = meta & ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK);
        if (keys.handle(key, down, meta, event.getUnicodeChar(textMeta))) return true;
        // Some layouts only supply a character when the full modifier state is used.
        int fallback = event.getUnicodeChar();
        if (fallback <= 0) return false;
        sink.send(KEY, down ? 1 : 0, fallback, 0);
        return true;
    }

    void accessibilityKeyReleased(int key) {
        updateModifiers(key, false);
        if (consumedShortcut == key) consumedShortcut = KeyEvent.KEYCODE_UNKNOWN;
        releaseKeys();
    }

    private void updateModifiers(int key, boolean down) {
        switch (key) {
            case KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ctrl = down;
            case KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> alt = down;
            case KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shift = down;
        }
    }

    private static boolean isPhysicalKeyboardBack(KeyEvent event) {
        InputDevice device = event.getDevice();
        return device != null && !device.isVirtual() && device.isExternal()
                && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                && event.isFromSource(InputDevice.SOURCE_KEYBOARD);
    }

    void releaseKeys() { keys.releaseAll(); }

    void captureEnded() {
        releaseKeys();
        ctrl = alt = shift = false;
        consumedShortcut = KeyEvent.KEYCODE_UNKNOWN;
    }

    void resetPointer() { x = y = -1; }

    void pointer(int x, int y, int action, long now, long moveInterval) {
        this.x = x;
        this.y = y;
        if (action == MotionEvent.ACTION_MOVE) {
            if (now - lastMove < moveInterval) return;
            lastMove = now;
        }
        sink.send(POINTER, 0, x, y);
        if (action == MotionEvent.ACTION_DOWN) sink.send(BUTTON, 1, 0, 0);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) sink.send(BUTTON, 0, 0, 0);
    }

    void relativePointer(int dx, int dy, int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (x < 0 || y < 0) {
            x = width / 2;
            y = height / 2;
        }
        x = Math.max(0, Math.min(width - 1, x + dx));
        y = Math.max(0, Math.min(height - 1, y + dy));
        sink.send(POINTER, 0, x, y);
    }
}
