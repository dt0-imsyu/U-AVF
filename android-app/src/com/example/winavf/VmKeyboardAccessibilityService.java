package com.example.winavf;

import android.accessibilityservice.AccessibilityService;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/** Optional, user-enabled keyboard routing. No screen/text retrieval or storage. */
public final class VmKeyboardAccessibilityService extends AccessibilityService {
    private static WeakReference<MainActivity> foreground = new WeakReference<>(null);
    private static volatile boolean connected;
    private final Set<Integer> captured = new HashSet<>();

    static void foreground(MainActivity activity) { foreground = new WeakReference<>(activity); }
    static void background(MainActivity activity) {
        if (foreground.get() == activity) foreground.clear();
    }
    static boolean connected() { return connected; }
    @Override protected void onServiceConnected() {
        connected = true;
        // The system's explicit user enablement is the opt-in. Respect any
        // previously saved OFF setting; never enable this service ourselves.
        android.content.SharedPreferences prefs = getSharedPreferences("MainActivity", MODE_PRIVATE);
        if (!prefs.contains("linux_system_keyboard_capture"))
            prefs.edit().putBoolean("linux_system_keyboard_capture", true).apply();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Deliberately do not retrieve nodes, text, screenshots or other apps.
    }
    @Override public void onInterrupt() {
        captured.clear();
        MainActivity activity = foreground.get();
        if (activity != null) activity.releaseSystemKeyboardCapture();
    }
    @Override public void onDestroy() {
        connected = false;
        captured.clear();
        MainActivity activity = foreground.get();
        if (activity != null) activity.releaseSystemKeyboardCapture();
        super.onDestroy();
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        MainActivity activity = foreground.get();
        InputDevice device = event.getDevice();
        boolean physical = device != null && !device.isVirtual() && device.isExternal() &&
            device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            event.isFromSource(InputDevice.SOURCE_KEYBOARD);
        int code = event.getKeyCode();
        boolean wasCaptured = captured.contains(code);
        if (event.getAction() == KeyEvent.ACTION_UP && wasCaptured) {
            captured.remove(code);
            if (activity != null) activity.finishCapturedAccessibilityKey(event);
            // Never leak an orphan key-up to One UI after focus loss.
            return true;
        }
        if (physical && activity != null && event.getAction()==KeyEvent.ACTION_DOWN &&
                activity.runtimePanelShortcut(event)) {
            if(activity.dispatchKeyEvent(event)) {captured.add(code);return true;}
        }
        if (!physical || activity == null || !activity.systemKeyboardCaptureActive() ||
            event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int textMeta = event.getMetaState() &
            ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK);
        boolean supported = GuestKeyboard.isModifier(code) ||
            GuestKeyboard.keysym(code, event.getUnicodeChar(textMeta)) != 0;
        if (!supported) return false; // Volume/power and unmapped system controls remain Android's.
        if (activity.dispatchKeyEvent(event)) {
            captured.add(code);
            return true;
        }
        return false;
    }
}
