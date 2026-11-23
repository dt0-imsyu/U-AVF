// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.os.Bundle;
import com.example.winavf.navigation.ProductNavigation.Location;
import com.example.winavf.navigation.ProductScreen;
import java.util.ArrayList;
import java.util.List;

/** Android saved-state encoding for the current route, wizard and Back history. */
record ProductNavigationState(ProductScreen screen, String tab, boolean wizard, String draftOs,
                              String draftTitle, boolean keyboardCapturePending, List<Location> history) {
    void save(Bundle out) {
        if (screen != null) out.putString("product_screen", screen.name());
        out.putString("workspace_tab", tab);
        out.putBoolean("wizard_settings", wizard);
        out.putString("create_workspace_os", draftOs);
        out.putString("draft_ubuntu_title", draftTitle);
        out.putBoolean("keyboard_capture_pending", keyboardCapturePending);
        var savedHistory = new ArrayList<Bundle>();
        for (Location location : history) {
            Bundle entry = new Bundle();
            if (location.screen() != null) entry.putString("screen", location.screen().name());
            entry.putString("tab", location.settingsTab());
            entry.putBoolean("wizard", location.wizard());
            entry.putBoolean("drawer", location.drawer());
            savedHistory.add(entry);
        }
        out.putParcelableArrayList("product_history", savedHistory);
    }

    static ProductNavigationState restore(Bundle state) {
        if (state == null || !state.containsKey("product_screen")) return null;
        return new ProductNavigationState(decodeScreen(state.getString("product_screen")),
                state.getString("workspace_tab", "Performance"), state.getBoolean("wizard_settings"),
                state.getString("create_workspace_os", "ubuntu"), state.getString("draft_ubuntu_title", ""),
                state.getBoolean("keyboard_capture_pending"), restoreHistory(state));
    }

    private static List<Location> restoreHistory(Bundle state) {
        var history = new ArrayList<Location>();
        var entries = state.getParcelableArrayList("product_history", Bundle.class);
        if (entries == null) return history;
        for (Bundle entry : entries) {
            String screen = entry.getString("screen");
            history.add(new Location(screen == null ? null : decodeScreen(screen),
                    entry.getString("tab", "Performance"), entry.getBoolean("wizard"), entry.getBoolean("drawer")));
        }
        return history;
    }

    private static ProductScreen decodeScreen(String screen) {
        // Older 1.2 test builds stored About as a full-screen destination.
        return "ABOUT".equals(screen) ? ProductScreen.APP_SETTINGS : ProductScreen.valueOf(screen);
    }
}
