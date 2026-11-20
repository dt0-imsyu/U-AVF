// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.workspace;

import android.content.SharedPreferences;
import java.util.function.BooleanSupplier;

/** Saved Ubuntu display preferences and the legacy keys shared with guest boot. */
public final class DisplaySettings {
    public static final int DEFAULT_REFRESH_HZ = 60;
    public static final String RUNNING_REFRESH_HZ = "linux_current_boot_display_refresh_hz";

    public static int requireDiagnosticRefresh(int refreshHz) {
        return switch (refreshHz) {
            case 60, 90, 120 -> refreshHz;
            default -> throw new IllegalArgumentException("Diagnostic guest refresh must be 60, 90 or 120 Hz");
        };
    }
    public static final String FIRST_BOOT_MODE = "ubuntu_first_boot_display_mode";
    public static final String FIRST_BOOT_OVERRIDE = "ubuntu_first_boot_display_override";
    private static final String ENCODED_ENABLED = "linux_installed_encoded_display";
    private static final String MAVK_ENABLED = "linux_mavk_experimental";
    private static final String RUNNING_RECEIVES_ENCODED = "linux_current_boot_receive_encoded";

    public enum Mode {
        FALLBACK("linux_resolution", "1280x800"),
        H264("linux_resolution_h264", "1920x1200"),
        MAVK("linux_resolution_mavk", "1920x1200");

        private final String resolutionKey;
        private final String defaultResolution;

        Mode(String resolutionKey, String defaultResolution) {
            this.resolutionKey = resolutionKey;
            this.defaultResolution = defaultResolution;
        }
    }

    private final SharedPreferences prefs;

    public DisplaySettings(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public Mode selected(BooleanSupplier mavkAvailable) {
        if (mavkRequested() && mavkAvailable.getAsBoolean()) return Mode.MAVK;
        return encodedEnabled() ? Mode.H264 : Mode.FALLBACK;
    }

    public boolean mavkRequested() {
        return prefs.getBoolean(MAVK_ENABLED, false);
    }

    public boolean encodedEnabled() {
        return prefs.getBoolean(ENCODED_ENABLED, true);
    }

    public boolean runningReceivesEncoded() {
        return prefs.getBoolean(RUNNING_RECEIVES_ENCODED, encodedEnabled());
    }

    public String resolution(Mode mode) {
        return prefs.getString(mode.resolutionKey, mode.defaultResolution);
    }

    public boolean hasResolution(Mode mode) {
        return prefs.contains(mode.resolutionKey);
    }

    public void setResolution(Mode mode, String resolution) {
        prefs.edit().putString(mode.resolutionKey, resolution).apply();
    }

    public void select(Mode mode, boolean userChoice, boolean running) {
        SharedPreferences.Editor change = prefs.edit();
        if (userChoice && prefs.contains(FIRST_BOOT_MODE)) {
            change.putBoolean(FIRST_BOOT_OVERRIDE, true);
        }
        if (running && !prefs.contains(RUNNING_RECEIVES_ENCODED)) {
            change.putBoolean(RUNNING_RECEIVES_ENCODED, encodedEnabled());
        }
        change.putBoolean(ENCODED_ENABLED, mode != Mode.FALLBACK)
                .putBoolean(MAVK_ENABLED, mode == Mode.MAVK).apply();
    }
}
