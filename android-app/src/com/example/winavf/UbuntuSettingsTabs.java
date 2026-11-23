// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.ui.ProductDialog;
import com.example.winavf.ui.WorkspaceSettingsScreen;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;
import java.io.File;
import java.util.Arrays;

/** Binds the Ubuntu settings controls to the saved workspace preferences. */
final class UbuntuSettingsTabs {
    interface Host {
        boolean running();
        boolean keyboardCaptureEnabled();
        void toggleKeyboardCapture();
        void clipboardPreferenceChanged();
        void showDisplayTab();
        void refresh(WorkspaceSettingsScreen view);
        void installPlasma();
        void expandStorage();
        void showStorageRecovery();
        void show(String message);
    }

    private static final long GIB = 1L << 30;
    private static final int[] MEMORY_GIB = {2, 3, 4, 6};
    private static final String[] LABELS = {"Резервный", "H.264 + OpenGL", "Mavk"};
    private static final String[] RESOLUTIONS = {"1280x800", "1920x1080", "1920x1200"};
    private final Activity activity;
    private final SharedPreferences preferences;
    private final DisplaySettings display;
    private final Host host;

    UbuntuSettingsTabs(Activity activity, Host host) {
        this.activity = activity;
        this.preferences = activity.getPreferences(0);
        this.display = new DisplaySettings(preferences);
        this.host = host;
    }

    static String displayLabel(Mode mode) { return LABELS[mode.ordinal()]; }

    static String[] displayLabels(boolean mavkAvailable) {
        return Arrays.copyOf(LABELS, mavkAvailable ? LABELS.length : 2);
    }

    void bind(WorkspaceSettingsScreen view, int tab, File disk, String plasmaState) {
        switch (tab) {
            case 0 -> performance(view);
            case 1 -> display(view, plasmaState);
            case 2 -> input(view);
            case 3 -> services(view);
            case 4 -> storage(view, disk);
            default -> throw new IllegalArgumentException("Unknown Ubuntu settings tab: " + tab);
        }
    }

    private void performance(WorkspaceSettingsScreen view) {
        int selected = Arrays.binarySearch(MEMORY_GIB, preferences.getInt("linux_ram_gib", 4));
        view.card(476, 612);
        view.segments(520, "Память", new String[]{"2 ГБ", "3 ГБ", "4 ГБ", "6 ГБ"},
                selected < 0 ? 2 : selected, index -> selectMemory(MEMORY_GIB[index]));
        view.segments(736, "Виртуальные процессоры", new String[]{"Все ядра", "1 vCPU"},
                preferences.getBoolean("linux_one_cpu", false) ? 1 : 0, index -> {
                    if (host.running()) {
                        ProductDialog.notice(activity, "Процессор", "Завершите Ubuntu перед изменением процессора.");
                        return false;
                    }
                    preferences.edit().putBoolean("linux_one_cpu", index == 1).apply();
                    return true;
                });
        view.option(952, "aea17", "Графический вывод", displayLabel(selectedMode()), host::showDisplayTab);
    }

    private boolean selectMemory(int memoryGiB) {
        if (host.running()) {
            ProductDialog.notice(activity, "Память", "Завершите Ubuntu перед изменением памяти.");
            return false;
        }
        var memory = new ActivityManager.MemoryInfo();
        activity.getSystemService(ActivityManager.class).getMemoryInfo(memory);
        if (memoryGiB * GIB > memory.totalMem - 3 * GIB) {
            ProductDialog.notice(activity, "Память", "Оставьте для Android не менее 3 ГБ.");
            return false;
        }
        preferences.edit().putInt("linux_ram_gib", memoryGiB).apply();
        return true;
    }

    private void display(WorkspaceSettingsScreen view, String plasmaState) {
        Mode mode = selectedMode();
        boolean running = host.running();
        view.card(476, !running ? 450 : plasmaState != null ? 506 : 332);
        view.displaySelector(516, displayLabels(MavkRuntimeAssets.isBundled(activity)), mode.ordinal(),
                index -> selectDisplay(view, Mode.values()[index]), () -> host.refresh(view));
        view.note(702, switch (mode) {
            case FALLBACK -> "Резервный вывод. Ниже частота кадров, можно выбрать разрешение.";
            case H264 -> "Аппаратный OpenGL (VirGL) и передача видео H.264.";
            case MAVK -> "GPU Mali и прямой вывод на экран без видеокодека. Экспериментально.";
        });
        if (!running) resolution(view, mode);
        else view.note(756, "Завершите Ubuntu перед сменой режима вывода.");
        if (plasmaState != null) plasma(view, plasmaState);
    }

    private Mode selectedMode() { return display.selected(() -> MavkRuntimeAssets.isBundled(activity)); }

    private boolean selectDisplay(WorkspaceSettingsScreen view, Mode requested) {
        if (requested == selectedMode()) return false;
        if (host.running()) {
            ProductDialog.notice(activity, "Графический вывод", "Завершите Ubuntu перед сменой режима вывода.");
            return false;
        }
        display.select(requested, true, false);
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        return true;
    }

    private void resolution(WorkspaceSettingsScreen view, Mode mode) {
        int selected = Arrays.asList(RESOLUTIONS).indexOf(display.resolution(mode));
        view.choice(780, "aea17", "Разрешение", new String[]{"1280 × 800", "1920 × 1080", "1920 × 1200"},
                selected, index -> {
                    if (host.running()) return;
                    display.setResolution(mode, RESOLUTIONS[index]);
                    host.refresh(view);
                });
    }

    private void plasma(WorkspaceSettingsScreen view, String state) {
        String label = switch (state) {
            case "installing" -> "Установка идёт…";
            case "ready" -> "Установлена";
            case "failed" -> "Повторить установку";
            default -> "Установить";
        };
        view.option(840, "aea17", "KDE Plasma X11 · Mavk", label,
                "ready".equals(state) || "installing".equals(state) ? null : host::installPlasma);
    }

    private void input(WorkspaceSettingsScreen view) {
        view.card(476, 704);
        toggle(view, 476, "1bf09", "Общий буфер обмена", "linux_text_clipboard_enabled", false, "");
        toggle(view, 604, "12205", "Жест от правого края", "runtime_edge_gesture", false, "");
        toggle(view, 732, "836de", "Скрывать кнопку панели", "hide_runtime_panel_button", false, "");
        boolean f12 = preferences.getInt("runtime_panel_shortcut", KeyEvent.KEYCODE_ESCAPE) == KeyEvent.KEYCODE_F12;
        view.choice(860, "5312a", "Открыть панель VM", new String[]{"Ctrl+Alt+Shift+Esc", "Ctrl+Alt+Shift+F12"},
                f12 ? 1 : 0, index -> {
                    preferences.edit().putInt("runtime_panel_shortcut",
                            index == 1 ? KeyEvent.KEYCODE_F12 : KeyEvent.KEYCODE_ESCAPE).apply();
                    host.refresh(view);
                });
        view.toggle(988, "5312a", "Перехват системных сочетаний", host::keyboardCaptureEnabled, host::toggleKeyboardCapture);
        view.note(1112, "Передаёт системные сочетания в VM. Для этого нужна служба U-AVF.");
    }

    private void services(WorkspaceSettingsScreen view) {
        String onRestart = host.running() ? "Изменение вступит в силу после перезапуска Ubuntu."
                : "Настройка применяется при запуске Ubuntu.";
        view.card(476, 256);
        toggle(view, 476, "4f479", "Звук гостевой системы", "linux_audio_output_enabled", true, onRestart);
        toggle(view, 604, "ff92e", "Интернет", "linux_internet_enabled", true, onRestart);
    }

    private void storage(WorkspaceSettingsScreen view, File disk) {
        boolean exists = disk.isFile();
        view.storage(exists ? IoUtil.formatBytes(disk.length()) + " · диск U-AVF" : "Диск ещё не создан",
                exists ? host::expandStorage : null, host::showStorageRecovery);
    }

    private void toggle(WorkspaceSettingsScreen view, int y, String icon, String title, String key,
                        boolean defaultValue, String note) {
        view.toggle(y, icon, title, () -> preferences.getBoolean(key, defaultValue), () -> {
            boolean previous = preferences.getBoolean(key, defaultValue);
            var edit = preferences.edit().putBoolean(key, !previous);
            if ("hide_runtime_panel_button".equals(key) && !previous) edit.putBoolean("runtime_edge_gesture", true);
            if ("runtime_edge_gesture".equals(key) && previous) edit.putBoolean("hide_runtime_panel_button", false);
            edit.apply();
            if ("linux_text_clipboard_enabled".equals(key)) host.clipboardPreferenceChanged();
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (!note.isEmpty()) host.show(note);
        });
    }
}
