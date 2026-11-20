// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.SharedPreferences;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Verifies saved keys and the distinction between next boot and the running display. */
public final class DisplaySettingsTest {
    public static void main(String[] args) {
        Map<String, Object> keys = new HashMap<>();
        var settings = new DisplaySettings(preferences(keys));
        check(settings.selected(() -> { throw new AssertionError("Probe should be lazy"); }) == Mode.H264);
        check(settings.resolution(Mode.FALLBACK).equals("1280x800"));
        check(settings.resolution(Mode.H264).equals("1920x1200"));
        check(settings.resolution(Mode.MAVK).equals("1920x1200"));

        keys.put(DisplaySettings.FIRST_BOOT_MODE, 2);
        settings.select(Mode.FALLBACK, true, true);
        check(settings.selected(() -> false) == Mode.FALLBACK);
        check(settings.runningReceivesEncoded());
        check(Boolean.TRUE.equals(keys.get(DisplaySettings.FIRST_BOOT_OVERRIDE)));

        settings.setResolution(Mode.MAVK, "1920x1080");
        settings.select(Mode.MAVK, true, true);
        check(settings.resolution(Mode.MAVK).equals("1920x1080"));
        check(settings.resolution(Mode.H264).equals("1920x1200"));
        check(settings.selected(() -> false) == Mode.H264);
        check(settings.selected(() -> true) == Mode.MAVK);
        for (int hz : new int[]{60, 90, 120}) check(DisplaySettings.requireDiagnosticRefresh(hz) == hz);
        try {
            DisplaySettings.requireDiagnosticRefresh(144);
            throw new AssertionError("Unsupported diagnostic rate accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("DISPLAY_SETTINGS_TEST=PASS");
    }

    private static SharedPreferences preferences(Map<String, Object> keys) {
        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBoolean", "getString" -> keys.getOrDefault(args[0], args[1]);
                    case "contains" -> keys.containsKey(args[0]);
                    case "edit" -> editor(keys);
                    default -> throw new AssertionError(method);
                });
    }

    private static SharedPreferences.Editor editor(Map<String, Object> keys) {
        Map<String, Object> pending = new HashMap<>();
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "putBoolean", "putString" -> {
                        pending.put((String) args[0], args[1]);
                        yield proxy;
                    }
                    case "apply" -> {
                        keys.putAll(pending);
                        yield null;
                    }
                    default -> throw new AssertionError(method);
                });
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
