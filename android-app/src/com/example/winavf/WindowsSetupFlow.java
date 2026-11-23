// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.ui.ProductDialog;
import com.example.winavf.ui.UiLanguage;
import com.example.winavf.runtime.VmLifecycle;
import android.content.Intent;
import android.content.ActivityNotFoundException;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns document selection, disk preparation, and the Windows boot decision. */
final class WindowsSetupFlow {
    record Workspace(boolean isoAvailable, boolean setupAvailable, boolean isoSelected, boolean diskExists,
                     boolean diskReady, Progress progress) {
        boolean canChooseMedia(boolean vmRunning) {
            return isoAvailable && !diskExists && progress.phase() == null && !vmRunning;
        }

        String diskLabel() {
            if (diskReady) return IoUtil.formatBytes(WindowsSetupDisk.TARGET_BYTES);
            return diskExists ? "Настройка не завершена" : "Диск ещё не создан";
        }

        String status() {
            if (progress.phase() != null || setupAvailable) return progress.label(diskReady);
            return isoSelected ? "ISO проверен · Experimental" : "Экспериментальный запуск Windows";
        }

        String action(boolean chooseIso) {
            if (chooseIso) return "Выбрать ISO…";
            if (setupAvailable) return diskExists ? "Продолжить" : "Установка";
            return isoAvailable ? "Проверить ISO" : "Запустить";
        }
    }

    record Progress(WindowsSetupDisk.Phase phase, String error) {
        String label(boolean diskReady) {
            if (diskReady) return "Диск Windows сохранён";
            if (phase == null) {
                return error.isEmpty() ? "Экспериментальная установка Windows" : "Не удалось подготовить диск";
            }
            return switch (phase) {
                case CHECKING_ISO -> "Проверка Windows ISO…";
                case CHECKING -> "Проверка установочных файлов…";
                case COPYING -> "Подготовка загрузки Windows…";
                case FINALIZING -> "Проверка разметки диска…";
            };
        }
    }

    // The workspace screen reads process-wide progress after Activity recreation.
    private static volatile Progress progress = new Progress(null, "");
    private static volatile WindowsSetupRuntime.Boot preparedBoot;
    private final Activity activity;
    private final WindowsWorkspaceMedia media;
    private final BooleanSupplier screenActive;
    private final BooleanSupplier vmRunning;
    private final Runnable refresh;
    private final Runnable pickIso;
    private final Consumer<WindowsSetupRuntime.Boot> boot;
    private boolean inspectingDisk;

    WindowsSetupFlow(Activity activity, BooleanSupplier screenActive, BooleanSupplier vmRunning,
                     Runnable refresh, Runnable pickIso,
                     Consumer<WindowsSetupRuntime.Boot> boot) {
        this.activity = activity;
        this.media = new WindowsWorkspaceMedia(activity.getApplicationContext(), activity.getPreferences(0));
        this.screenActive = screenActive;
        this.vmRunning = vmRunning;
        this.refresh = refresh;
        this.pickIso = pickIso;
        this.boot = boot;
    }

    Progress progress() { return progress; }

    boolean diskReady() {
        var disk = WindowsSetupDisk.workspace(activity).toFile();
        return disk.isFile() && disk.length() == WindowsSetupDisk.TARGET_BYTES;
    }

    boolean isoSelected() {
        return media.selectedIso() != null;
    }

    Workspace workspace() {
        // Capture progress before reading the disk so a concurrent publish
        // still causes a refresh even if this snapshot misses the new file.
        Progress current = progress;
        boolean isoAvailable = WindowsInstallIso.available();
        boolean diskExists = Files.exists(WindowsSetupDisk.workspace(activity), LinkOption.NOFOLLOW_LINKS);
        boolean setupAvailable = diskExists ? WindowsSetupDisk.available() : WindowsSetupDisk.isoSetupAvailable(activity);
        return new Workspace(isoAvailable, setupAvailable, isoAvailable && isoSelected(), diskExists,
                setupAvailable && diskReady(), current);
    }

    void open() {
        Progress current = progress;
        if (current.phase() != null) {
            notice(current.label(false));
            return;
        }
        if (launchPreparedIfReady()) return;
        Path workspace = WindowsSetupDisk.workspace(activity);
        if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            inspectDisk(workspace);
        } else {
            chooseMedia(current.error());
        }
    }

    void confirmReset(boolean reinstall) {
        if (vmRunning.getAsBoolean() || VmLifecycle.compatibilityProbeActive() || progress.phase() != null) {
            notice("Сначала завершите текущий сеанс VM.");
            return;
        }
        ProductDialog.show(activity, reinstall ? "Начать заново" : "Удалить воркспейс",
                "Виртуальный диск Windows и все данные на нём будут удалены. Исходный ISO сохранится.",
                reinstall ? "Начать заново и отформатировать" : "Удалить", true, () -> reset(reinstall));
    }

    private void reset(boolean reinstall) {
        if (vmRunning.getAsBoolean() || VmLifecycle.compatibilityProbeActive() || progress.phase() != null) return;
        progress = new Progress(WindowsSetupDisk.Phase.FINALIZING, "");
        refresh.run();
        new Thread(() -> {
            String failure = "";
            try {
                Path disk = WindowsSetupDisk.workspace(activity);
                if (Files.exists(disk, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isRegularFile(disk, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unexpected Windows workspace path");
                }
                Files.deleteIfExists(disk);
                // The runtime directory holds the extracted wrapper, the UART helper
                // and the KD logs. It survived deletion and kept the space allocated.
                File root = activity.getFilesDir();
                IoUtil.deleteTree(root, new File(root, "windows-setup-runtime"));
                IoUtil.deleteTree(root, new File(root, "payload"));
                preparedBoot = null;
                if (!reinstall) {
                    media.forgetIso();
                    activity.getPreferences(0).edit().remove("windows_workspace_title")
                            .remove("last_display_windows").apply();
                }
            } catch (IOException error) {
                failure = IoUtil.rootMessage(error);
            }
            String result = failure;
            progress = new Progress(null, result);
            activity.runOnUiThread(() -> {
                if (!screenActive.getAsBoolean()) return;
                refresh.run();
                if (!result.isEmpty()) notice(result);
                else if (reinstall) chooseMedia("");
            });
        }, "U-AVF-windows-reset").start();
    }

    private void chooseMedia(String failure) {
        Uri iso = media.selectedIso();
        boolean canPrepare = iso != null && WindowsSetupDisk.isoSetupAvailable(activity);
        String requirements = UiLanguage.text(
                "Поддерживаются: 26H2 en-US ARM64 (сейчас на сайте Microsoft) и 25H2 ru-RU ARM64 (26200.6584).")
                + "\n\n" + UiLanguage.format("Свободно %s · требуется %s",
                        IoUtil.formatBytes(activity.getFilesDir().getUsableSpace()),
                        IoUtil.formatBytes(WindowsSetupDisk.requiredFreeBytes()));
        if (!failure.isEmpty()) requirements += "\n\n" + failure;
        String select = iso == null ? "Выбрать ISO…" : "Заменить ISO…";
        if (!canPrepare) {
            ProductDialog.choose(activity, "Установка Windows ARM64", requirements,
                    new String[]{select, "Скачать ISO ↗"}, -1, choice -> {
                        if (choice == 0) pickIso.run();
                        else openOfficialDownload();
                    });
            return;
        }
        chooseSetupMode(requirements, true, () -> prepare(iso, WindowsSetupRuntime.Boot.INSTALL),
                () -> prepare(iso, WindowsSetupRuntime.Boot.PREVIEW),
                () -> prepare(iso, WindowsSetupRuntime.Boot.READ_ONLY_SETUP), pickIso);
    }

    private void openOfficialDownload() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.microsoft.com/en-us/software-download/windows11arm64")));
        } catch (ActivityNotFoundException error) {
            notice("Не удалось открыть ссылку.");
        }
    }

    void selectIso(Uri source) {
        if (progress.phase() != null) {
            Log.w("UAVF-Windows-ISO", "Selection ignored while " + progress.phase() + " is active");
            notice("Подготовка Windows уже выполняется. Дождитесь завершения.");
            return;
        }
        progress = new Progress(WindowsSetupDisk.Phase.CHECKING_ISO, "");
        if (screenActive.getAsBoolean()) refresh.run();
        new Thread(() -> inspectIso(source), "U-AVF-windows-iso").start();
    }

    private void inspectIso(Uri source) {
        try {
            var result = media.selectIso(source);
            progress = new Progress(null, "");
            Log.i("UAVF-Windows-ISO", "ARM64_EFI=PASS BOOT_WIM_BYTES=" + result.bootWimBytes()
                    + " INSTALL_IMAGE=" + (result.esd() ? "ESD" : "WIM") + " INSTALL_BYTES=" + result.installBytes());
            activity.runOnUiThread(() -> {
                if (!screenActive.getAsBoolean()) return;
                refresh.run();
                if (WindowsSetupDisk.isoSetupAvailable(activity)) open();
                else notice("Образ содержит загрузчик ARM64 и файлы установки. Загрузка Setup из ISO пока недоступна.");
            });
        } catch (IOException | RuntimeException | LinkageError error) {
            String detail = IoUtil.rootMessage(error);
            progress = new Progress(null, detail);
            Log.w("UAVF-Windows-ISO", "ISO check failed: " + error.getClass().getSimpleName() + ": " + detail, error);
            activity.runOnUiThread(() -> {
                if (!screenActive.getAsBoolean()) return;
                refresh.run();
                notice(detail);
            });
        }
    }

    private void prepare(Uri iso, WindowsSetupRuntime.Boot selectedBoot) {
        if (iso == null || progress.phase() != null || vmRunning.getAsBoolean()) return;
        progress = new Progress(WindowsSetupDisk.Phase.CHECKING, "");
        refresh.run();
        startDiskPreparation(media, iso, selectedBoot);
    }

    private static void startDiskPreparation(WindowsWorkspaceMedia media, Uri iso, WindowsSetupRuntime.Boot selectedBoot) {
        // Only application-scoped storage survives on this worker. The visible
        // workspace observes progress and refreshes when the operation completes.
        new Thread(() -> {
            try {
                media.prepare(iso, phase -> progress = new Progress(phase, ""));
                preparedBoot = selectedBoot;
                progress = new Progress(null, "");
            } catch (IOException | RuntimeException | LinkageError error) {
                Log.w("UAVF-Windows-Setup", "Preparation failed", error);
                progress = new Progress(null, IoUtil.rootMessage(error));
            }
        }, "U-AVF-windows-prepare").start();
    }

    boolean launchPreparedIfReady() {
        if (!screenActive.getAsBoolean() || vmRunning.getAsBoolean() || progress.phase() != null) return false;
        WindowsSetupRuntime.Boot selectedBoot = preparedBoot;
        if (selectedBoot == null) return false;
        preparedBoot = null;
        boot.accept(selectedBoot);
        return true;
    }

    private void inspectDisk(Path disk) {
        if (inspectingDisk) return;
        inspectingDisk = true;
        new Thread(() -> {
            WindowsWorkspaceMedia.BootOptions options = null;
            try {
                options = media.inspect(disk);
            } catch (IOException | LinkageError error) {
                Log.w("UAVF-Windows-Setup", "Windows disk layout check failed", error);
            }
            var result = options;
            activity.runOnUiThread(() -> showBootOptions(result));
        }, "U-AVF-Windows-disk-layout").start();
    }

    private void showBootOptions(WindowsWorkspaceMedia.BootOptions options) {
        inspectingDisk = false;
        if (!screenActive.getAsBoolean() || vmRunning.getAsBoolean()) return;
        if (options == null) {
            ProductDialog.notice(activity, "Windows Setup",
                    "Не удалось проверить разметку диска Windows. Подробности доступны в журнале.");
            return;
        }
        switch (options) {
            case INSTALLED -> ProductDialog.show(activity, "Windows ARM64",
                    "Загрузчик установленной Windows проверен. При первом запуске продолжится настройка системы.",
                    "Запустить", false, () -> boot.accept(WindowsSetupRuntime.Boot.INSTALLED));
            case PREVIEW_AND_INSTALL -> offerSetup(true);
            case PREVIEW -> offerSetup(false);
            case PENDING_INSTALL -> ProductDialog.show(activity, "Диск Windows сохранён",
                    "Найден раздел NTFS. Завершение установки не подтверждено. Если установка прервалась, её можно начать заново.",
                    "Начать заново", false, () -> confirmReset(true));
            case PARTITIONED -> ProductDialog.show(activity, "Диск Windows сохранён",
                    "Диск уже размечен. Если установка прервалась, её можно начать заново.",
                    "Начать заново", false, () -> confirmReset(true));
            case UNAVAILABLE -> ProductDialog.notice(activity, "Диск Windows подготовлен",
                    "Диск сохранён в U-AVF. Запуск Windows Setup с этого диска пока недоступен.");
        }
    }

    private void offerSetup(boolean guided) {
        long required = WindowsSetupDisk.requiredInstallFreeBytes();
        long available = activity.getFilesDir().getUsableSpace();
        boolean canInstall = guided && available >= required;
        String description = canInstall ? "" : guided
                ? UiLanguage.format("Для автоматической установки освободите ещё %d МиБ.",
                        (required - available + (1L << 20) - 1) >> 20)
                : UiLanguage.text("Автоматическая установка недоступна для этого диска.");
        chooseSetupMode(description, canInstall, () -> boot.accept(WindowsSetupRuntime.Boot.INSTALL),
                () -> boot.accept(WindowsSetupRuntime.Boot.PREVIEW),
                () -> boot.accept(WindowsSetupRuntime.Boot.READ_ONLY_SETUP), null);
    }

    private void chooseSetupMode(String description, boolean canInstall, Runnable automatic,
                                 Runnable manual, Runnable readOnly, Runnable replace) {
        var labels = new java.util.ArrayList<String>();
        var actions = new java.util.ArrayList<Runnable>();
        if (canInstall) {
            labels.add("Автоматическая установка");
            actions.add(() -> ProductDialog.show(activity, "Установить Windows",
                    "U-AVF создаст разделы на подготовленном виртуальном диске и начнёт установку Windows 11 Pro.",
                    "Установить", false, automatic));
        }
        labels.add("Свой сценарий установки");
        actions.add(() -> ProductDialog.show(activity, "Свой сценарий установки",
                "Выберите параметры установки в Windows Setup. Виртуальный диск доступен для записи.",
                "Открыть установщик", false, manual));
        labels.add("Открыть установщик без изменения ISO");
        actions.add(readOnly);
        if (replace != null) {
            labels.add("Заменить ISO…");
            actions.add(replace);
        }
        String note = UiLanguage.text("Исходный ISO сохраняется. В режиме просмотра виртуальный диск доступен только для чтения.");
        String message = description.isEmpty() ? note : description + "\n\n" + note;
        ProductDialog.choosePrimary(activity, "Установка Windows ARM64", message,
                labels.toArray(String[]::new), canInstall ? 0 : -1, false, index -> actions.get(index).run());
    }

    private void notice(String message) {
        ProductDialog.notice(activity, "Windows ARM64", message);
    }
}
