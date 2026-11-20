// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.diagnostics;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Bounded local records of completed AVF checks. Independent of cloud delivery. */
public final class CompatibilityHistory {
    private static final int MAX_RUNS = 12;
    private static final int MAX_REPORT_LENGTH = 8192;

    public record Run(Instant completed, String id, String status, String report) {
        public String text() {
            return (completed == null ? status : completed + "  ·  " + status) + "\n" + report.stripTrailing();
        }
    }

    public static String append(String history, Run run) {
        StringBuilder saved = new StringBuilder();
        write(saved, run);
        int retained = 1;
        for (Run previous : read(history)) {
            if (previous.id().equals(run.id())) continue;
            write(saved, previous);
            if (++retained == MAX_RUNS) break;
        }
        return saved.toString();
    }

    public static List<Run> read(String history) {
        List<Run> runs = new ArrayList<>();
        for (String line : history.split("\n")) {
            if (runs.size() == MAX_RUNS) break;
            String[] columns = line.split("\t", 4);
            if (columns.length != 4 || !columns[1].matches("[0-9a-f]{32}")
                    || columns[3].length() > MAX_REPORT_LENGTH * 6) continue;
            try {
                Instant completed = columns[0].equals("-") ? null : Instant.parse(columns[0]);
                String report = new String(Base64.getDecoder().decode(columns[3]), StandardCharsets.UTF_8);
                runs.add(new Run(completed, columns[1], columns[2], report));
            } catch (IllegalArgumentException | java.time.DateTimeException malformed) {
                // Preserve the other runs if a single stored row cannot be read.
            }
        }
        return List.copyOf(runs);
    }

    public static String search(List<Run> runs, String query, String legacyReport) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (runs.isEmpty()) return matches(legacyReport, needle) ? legacyReport : "";
        StringBuilder result = new StringBuilder();
        for (Run run : runs) {
            String report = run.text();
            if (!matches(report, needle)) continue;
            if (result.length() > 0) result.append("\n\n");
            result.append(report);
        }
        return result.toString();
    }

    private static boolean matches(String text, String needle) {
        return !text.isBlank() && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void write(StringBuilder saved, Run run) {
        String report = run.report();
        if (report.length() > MAX_REPORT_LENGTH) report = report.substring(0, MAX_REPORT_LENGTH);
        saved.append(run.completed() == null ? "-" : run.completed()).append('\t')
                .append(run.id()).append('\t').append(run.status()).append('\t')
                .append(Base64.getEncoder().encodeToString(report.getBytes(StandardCharsets.UTF_8))).append('\n');
    }

    private CompatibilityHistory() {}
}
