// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.SharedPreferences;
import com.example.winavf.diagnostics.CompatibilityHistory;
import com.example.winavf.diagnostics.DiagnosticJournal;
import com.example.winavf.runtime.RuntimeTransition;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Stores observed VM events and supplies the same history to search and export. */
final class RuntimeDiagnostics {
    private static final String EVENTS = "diagnostic_events_v1";
    private static final String RUNS = "compatibility_history_v1";
    private static final String LEGACY_REPORT = "compatibility_execution_report";
    private final SharedPreferences preferences;
    private final DiagnosticJournal journal = new DiagnosticJournal(UiLanguage::english);
    private final Set<String> observedEvents = new HashSet<>();
    private int generation = -1;

    RuntimeDiagnostics(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    void observe(RuntimeTransition transition, boolean windows) {
        if (!transition.visible()) return;
        if (generation != transition.generation()) {
            generation = transition.generation();
            observedEvents.clear();
        }
        String guest = windows ? "Windows" : "Ubuntu";
        boolean winpe = transition.events().containsKey("Windows setup frame");
        for (String event : transition.events().keySet()) {
            if (!observedEvents.add(event)) continue;
            String message = message(event, windows, winpe);
            if (message != null) {
                boolean avfEvent = event.equals("Virtual machine created") || event.equals("VM stopped");
                append(avfEvent ? "AVF" : guest, "Событие", message);
            }
        }
        if (transition.mode() == RuntimeTransition.Mode.ERROR && observedEvents.add("error")) {
            append(guest, "Ошибка", failureMessage(transition, windows));
        }
    }

    private static String message(String event, boolean windows, boolean winpe) {
        return switch (event) {
            case "Start requested" -> "Запрошен запуск";
            case "Display reconnect requested" -> "Display reconnection requested";
            case "Virtual machine created" -> "Виртуальная машина создана";
            case "UEFI started" -> "Запущен UEFI";
            case "Linux kernel" -> "Подтверждена загрузка ядра Linux";
            case "Windows kernel" -> "Windows kernel resumed after bootstrap";
            case "Windows setup frame" -> "First Windows Setup frame presented";
            case "Windows display reconnecting" -> "Reconnecting to the Windows display";
            case "systemd userspace" -> "Запущены службы systemd";
            case "GDM started" -> "Запущен дисплейный менеджер";
            case "Display connected" -> "Подключён вывод изображения";
            case "Desktop presented" -> winpe ? null : windows
                    ? "First installed Windows frame presented" : "Показан первый кадр рабочего стола";
            case "Shutdown requested" -> "Запрошено завершение";
            case "Restart requested" -> "Запрошен перезапуск";
            case "Power request accepted" -> "Гость принял запрос питания";
            case "Filesystem sync" -> "Подтверждена синхронизация файлов";
            case "VM stopped" -> "Виртуальная машина остановлена";
            default -> null;
        };
    }

    private static String failureMessage(RuntimeTransition transition, boolean windows) {
        if (transition.operation() == RuntimeTransition.Mode.DISPLAY_RECONNECT) {
            return "Display reconnection failed";
        }
        if (windows && !transition.detail().isBlank()) return transition.detail();
        return "Операция виртуальной машины завершилась ошибкой";
    }

    void append(String component, String level, String message) {
        String history = preferences.getString(EVENTS, "");
        preferences.edit().putString(EVENTS, journal.append(history, Instant.now(), component, level, message)).apply();
    }

    List<DiagnosticJournal.Entry> entries() {
        return journal.newestFirst(preferences.getString(EVENTS, ""));
    }

    Function<String, String> compatibilitySearch() {
        var history = CompatibilityHistory.read(preferences.getString(RUNS, ""));
        String legacy = preferences.getString(LEGACY_REPORT, "");
        return query -> CompatibilityHistory.search(history, query, legacy);
    }

    void appendToReport(StringBuilder report, boolean windowsDiskReady) {
        report.append("\nWindows disk: ").append(windowsDiskReady ? "Prepared workspace" : "Not prepared");
        appendTime(report, "\nLast Windows Setup display: ", "last_setup_windows");
        appendTime(report, "\nLast installed Windows display: ", "last_display_windows");
        report.append("\n\nObserved events\n");
        for (var entry : entries()) report.append(entry.line()).append('\n');
        String compatibility = compatibilitySearch().apply("");
        if (!compatibility.isEmpty()) report.append("\nCompatibility reports\n").append(compatibility);
    }

    private void appendTime(StringBuilder report, String label, String preference) {
        long time = preferences.getLong(preference, 0);
        if (time > 0) report.append(label).append(Instant.ofEpochMilli(time));
    }
}
