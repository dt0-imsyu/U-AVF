// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.diagnostics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Codec for the persisted diagnostic history shared by the UI and text export. */
public final class DiagnosticJournal {
    private static final int MAX_ENTRIES = 128;

    public record Entry(String time, String component, String level, String event) {
        public String line() {
            return time + "  " + component + "  ·  " + level + "  ·  " + event;
        }
    }

    private final UnaryOperator<String> english;

    public DiagnosticJournal(UnaryOperator<String> english) {
        this.english = english;
    }

    public String append(String history, Instant time, String component, String level, String event) {
        String[] rows = history.isEmpty() ? new String[0] : history.split("\n");
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, rows.length - MAX_ENTRIES + 1); i < rows.length; i++) {
            result.append(rows[i]).append('\n');
        }
        return result.append(time).append('\t')
                .append(english.apply(component)).append('\t')
                .append(english.apply(level)).append('\t')
                .append(english.apply(event).replace('\r', ' ').replace('\n', ' ').replace('\t', ' '))
                .toString();
    }

    public List<Entry> newestFirst(String history) {
        String[] rows = history.split("\n");
        List<Entry> entries = new ArrayList<>();
        for (int i = rows.length - 1; i >= 0; i--) {
            String[] parts = rows[i].split("\t", 4);
            if (parts.length != 4) continue;
            String component = english.apply(parts[1]);
            String event = english.apply(parts[3]);
            if (component.equals("AVF") && event.startsWith("Compatibility result: ")) component = "Check";
            entries.add(new Entry(parts[0], component, english.apply(parts[2]), event));
        }
        return entries;
    }
}
