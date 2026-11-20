// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.navigation;

/** Names are persisted in Activity state. Keep existing identifiers stable. */
public enum ProductScreen {
    ONBOARDING,
    COMPATIBILITY,
    VM_PERMISSION,
    MODE_SELECTION,
    CREATE_OS,
    CREATE_IMAGE,
    CREATE_STORAGE,
    CREATE_REVIEW,
    INSTALL_RECOVERY,
    INPUT_SETTINGS,
    LINUX_SETUP,
    LINUX_HOME,
    WINDOWS_HOME,
    VM_SETTINGS,
    APP_SETTINGS,
    DIAGNOSTICS,
    INSTALL_STATUS,
    SYSTEMS,
    DIAGNOSTIC_LOG,
    DIAGNOSTIC_RUNS;

    public int mainTab() {
        return switch (this) {
            case MODE_SELECTION -> 0;
            case SYSTEMS -> 1;
            case APP_SETTINGS -> 2;
            default -> -1;
        };
    }
}
