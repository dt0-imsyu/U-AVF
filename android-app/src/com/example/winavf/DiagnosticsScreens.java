// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.ui.DiagnosticsScreen;
import com.example.winavf.ui.UiLanguage;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import com.example.winavf.navigation.ProductScreen;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

/** Presents diagnostics, history search, clipboard copies and report export. */
final class DiagnosticsScreens {
    static final int EXPORT_REQUEST = 21826;

    interface Host {
        void observeRuntime();
        boolean hasPermissions();
        boolean guestConnected();
        boolean frameVisible();
        String version();
        String displayLabel();
        void checkCompatibility();
        void back();
        void navigate(ProductScreen screen);
        void desktop();
        void show(String message);
    }

    private final Activity activity;
    private final SharedPreferences preferences;
    private final RuntimeDiagnostics diagnostics;
    private final CompatibilitySession compatibility;
    private final UbuntuWorkspaceStorage storage;
    private final WindowsSetupFlow windows;
    private final Host host;

    DiagnosticsScreens(Activity activity, RuntimeDiagnostics diagnostics, CompatibilitySession compatibility,
                       UbuntuWorkspaceStorage storage, WindowsSetupFlow windows, Host host) {
        this.activity = activity;
        this.preferences = activity.getPreferences(0);
        this.diagnostics = diagnostics;
        this.compatibility = compatibility;
        this.storage = storage;
        this.windows = windows;
        this.host = host;
    }

    DiagnosticsScreen create(ProductScreen screen) {
        host.observeRuntime();
        return switch (screen) {
            case DIAGNOSTICS -> new DiagnosticsScreen(activity, deviceStatus(), guestStatus(), host.frameVisible(),
                    host::back, host::checkCompatibility, () -> host.navigate(ProductScreen.DIAGNOSTIC_RUNS),
                    () -> host.navigate(ProductScreen.DIAGNOSTIC_LOG), this::requestExport, host::desktop);
            case DIAGNOSTIC_RUNS -> new DiagnosticsScreen(activity, diagnostics.compatibilitySearch(),
                    host::back, this::copy, this::copySummary, this::requestExport);
            case DIAGNOSTIC_LOG -> new DiagnosticsScreen(activity, diagnostics.entries(),
                    host::back, this::copy, this::copySummary, this::requestExport);
            default -> throw new IllegalArgumentException("Unknown diagnostic screen " + screen);
        };
    }

    private String[] deviceStatus() {
        boolean arm64 = Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
        String avf = compatibility.check(1);
        String platform = compatibility.check(3);
        return new String[]{
                arm64 ? "Android " + Build.VERSION.RELEASE + " · ARM64" : "ARM64 отсутствует",
                avf.startsWith("Passed") ? "API доступен" : avf.startsWith("Failed") ? "API недоступен" : "Не проверен",
                host.hasPermissions() ? "Предоставлены" : "Нет доступа",
                platform.startsWith("Passed") ? "Проверена" : platform.startsWith("Failed") ? "Есть ошибка" : "Не проверена"
        };
    }

    private String[] guestStatus() {
        boolean desktop = preferences.getLong("last_desktop_ubuntu", 0) > 0;
        boolean connected = host.guestConnected();
        String boot = desktop ? (connected ? "Подтверждены" : "Загрузка подтверждена")
                : connected ? "vsock подключён" : "Не проверены";
        return new String[]{boot, preferences.getString("last_desktop_graphics_ubuntu", "Не проверена")};
    }

    private String summary() {
        host.observeRuntime();
        String[] device = deviceStatus();
        String[] guest = guestStatus();
        var disk = storage.disk();
        var report = new StringBuilder("U-AVF diagnostics\nVersion: ").append(host.version())
                .append("\nDevice: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nAndroid: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
                .append("\nUbuntu configuration: ").append(preferences.getInt("linux_ram_gib", 4))
                .append(" GiB RAM · ").append(host.displayLabel())
                .append("\nUbuntu disk: ").append(disk.isFile() ? IoUtil.formatBytes(disk.length()) : "Not created")
                .append("\nAVF: ").append(UiLanguage.english(device[1]))
                .append("\nPermissions: ").append(UiLanguage.english(device[2]))
                .append("\nPlatform: ").append(UiLanguage.english(device[3]))
                .append("\nBoot and vsock: ").append(UiLanguage.english(guest[0]))
                .append("\nGraphics: ").append(UiLanguage.english(guest[1]));
        diagnostics.appendToReport(report, windows.diskReady());
        return report.toString();
    }

    private void copySummary() { copy(summary()); }

    private void copy(String text) {
        if (text.isBlank()) return;
        ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("U-AVF", text));
    }

    private void requestExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "U-AVF-diagnostics-" + LocalDate.now() + ".txt");
        activity.startActivityForResult(intent, EXPORT_REQUEST);
    }

    void export(Uri destination) {
        byte[] report = summary().getBytes(StandardCharsets.UTF_8);
        new Thread(() -> {
            try (var file = activity.getContentResolver().openOutputStream(destination, "wt")) {
                if (file == null) throw new IOException("No output stream");
                file.write(report);
            } catch (Exception error) {
                host.show("Не удалось сохранить отчёт.");
                return;
            }
            host.show("Диагностический отчёт сохранён.");
        }, "U-AVF-diagnostic-export").start();
    }
}
