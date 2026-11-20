// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.navigation;

import java.util.ArrayDeque;
import java.util.List;

/** Owns Back history for product screens and the running desktop. */
public final class ProductNavigation {
    private static final int MAX_HISTORY = 32;
    private final ArrayDeque<Location> history = new ArrayDeque<>();

    // The desktop is outside the product screen stack and has no ProductScreen.
    public record Location(ProductScreen screen, String settingsTab, boolean wizard, boolean drawer) {}

    public void opening(ProductScreen next, Location current, boolean productVisible,
                        boolean productHasContent, boolean liveDesktop) {
        if (productVisible && productHasContent && shouldKeep(current.screen(), next)) {
            push(current);
        } else if (!productVisible && liveDesktop) {
            push(new Location(null, "", false, current.drawer()));
        }
    }

    private static boolean shouldKeep(ProductScreen current, ProductScreen next) {
        if (current == next || (current.mainTab() >= 0 && next.mainTab() >= 0)) return false;
        return current != ProductScreen.VM_SETTINGS || next != ProductScreen.INPUT_SETTINGS;
    }

    public Location back(boolean desktopAvailable) {
        Location previous;
        do {
            previous = history.pollLast();
        } while (previous != null && previous.screen() == null && !desktopAvailable);
        return previous;
    }

    public List<Location> snapshot() {
        return List.copyOf(history);
    }

    public void restore(List<Location> locations) {
        history.clear();
        locations.forEach(this::push);
    }

    private void push(Location location) {
        if (history.size() == MAX_HISTORY) history.removeFirst();
        history.addLast(location);
    }
}
