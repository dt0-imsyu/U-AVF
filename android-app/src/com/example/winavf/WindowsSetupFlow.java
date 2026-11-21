// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
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
            if (setupAvailable) return progress.label(diskReady);
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
                case COPYING -> "Копирование шаблона WinPE…";
                case FINALIZING -> "Проверка разметки диска…";
            };
        }
    }

    // Disk preparation outlives an Activity recreation.
    private static volatile Progress progress = new Progress(null, "");
    private final Activity activity;
    private final WindowsWorkspaceMedia media;
    private final BooleanSupplier screenActive;
    private final BooleanSupplier vmRunning;
    private final Runnable refresh;
    private final Consumer<WindowsWorkspaceMedia.Document> pickDocument;
    private final Consumer<WindowsSetupRuntime.Boot> boot;
    private boolean inspectingDisk;

    WindowsSetupFlow(Activity activity, BooleanSupplier screenActive, BooleanSupplier vmRunning,
                     Runnable refresh, Consumer<WindowsWorkspaceMedia.Document> pickDocument,
                     Consumer<WindowsSetupRuntime.Boot> boot) {
        this.activity = activity;
        this.media = new WindowsWorkspaceMedia(activity.getApplicationContext(), activity.getPreferences(0));
        this.screenActive = screenActive;
        this.vmRunning = vmRunning;
        this.refresh = refresh;
        this.pickDocument = pickDocument;
        this.boot = boot;
    }

    Progress progress() { return progress; }

    boolean diskReady() {
        var disk = WindowsSetupDisk.workspace(activity).toFile();
        return disk.isFile() && disk.length() == WindowsSetupDisk.TARGET_BYTES;
    }

    boolean isoSelected() {
        return media.selected(WindowsWorkspaceMedia.Document.ISO) != null;
    }

    Workspace workspace() {
        boolean isoAvailable = WindowsInstallIso.available();
        boolean setupAvailable = isoAvailable && WindowsSetupDisk.available();
        boolean diskExists = Files.exists(WindowsSetupDisk.workspace(activity), LinkOption.NOFOLLOW_LINKS);
        return new Workspace(isoAvailable, setupAvailable, isoAvailable && isoSelected(), diskExists,
                setupAvailable && diskReady(), progress);
    }

    void open() {
        Progress current = progress;
        if (current.phase() != null) {
            notice(current.label(false));
            return;
        }
        Path workspace = WindowsSetupDisk.workspace(activity);
        if (Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            inspectDisk(workspace);
        } else {
            chooseMedia(current.error());
        }
    }

    private void chooseMedia(String failure) {
        Uri iso = media.selected(WindowsWorkspaceMedia.Document.ISO);
        Uri winpe = media.selected(WindowsWorkspaceMedia.Document.WINPE);
        String requirements = UiLanguage.format(
                "Нужны Windows 11 ARM64 25H2 (ru-RU), сборка %s, и соответствующий шаблон WinPE.",
                WindowsSetupDisk.INSTALL_BUILD)
                + "\n" + WindowsSetupDisk.REFERENCE_TEMPLATE
                + "\n\n" + UiLanguage.format("Свободно %s · требуется %s",
                        IoUtil.formatBytes(activity.getFilesDir().getUsableSpace()),
                        IoUtil.formatBytes(WindowsSetupDisk.requiredFreeBytes()));
        if (!failure.isEmpty()) requirements += "\n\n" + failure;
        String[] options = {iso == null ? "Выбрать ISO…" : "Заменить ISO…",
                winpe == null ? "Выбрать шаблон WinPE…" : "Заменить шаблон WinPE…", "Подготовить диск"};
        ProductDialog.choose(activity, "Установка Windows ARM64", requirements,
                iso != null && winpe != null ? options : Arrays.copyOf(options, 2), -1, choice -> {
                    switch (choice) {
                        case 0 -> pickDocument.accept(WindowsWorkspaceMedia.Document.ISO);
                        case 1 -> pickDocument.accept(WindowsWorkspaceMedia.Document.WINPE);
                        case 2 -> prepare(iso, winpe);
                        default -> {}
                    }
                });
    }

    void selectWinpe(Uri source) {
        new Thread(() -> {
            try {
                media.selectWinpe(source);
                activity.runOnUiThread(() -> {
                    if (screenActive.getAsBoolean()) open();
                });
            } catch (IOException | SecurityException error) {
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed()) notice(IoUtil.rootMessage(error));
                });
            }
        }, "U-AVF-windows-winpe").start();
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
                if (WindowsSetupDisk.available()) open();
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

    private void prepare(Uri iso, Uri winpe) {
        if (iso == null || winpe == null || progress.phase() != null || vmRunning.getAsBoolean()) return;
        progress = new Progress(WindowsSetupDisk.Phase.CHECKING, "");
        refresh.run();
        new Thread(() -> {
            try {
                media.prepare(iso, winpe, phase -> progress = new Progress(phase, ""));
                progress = new Progress(null, "");
            } catch (IOException | RuntimeException | LinkageError error) {
                Log.w("UAVF-Windows-Setup", "Preparation failed", error);
                progress = new Progress(null, IoUtil.rootMessage(error));
            }
        }, "U-AVF-windows-prepare").start();
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
            case PENDING_INSTALL -> ProductDialog.notice(activity, "Диск Windows сохранён",
                    "Найден раздел NTFS. Завершение установки не подтверждено. Повторный запуск пока недоступен.");
            case PARTITIONED -> ProductDialog.notice(activity, "Диск Windows сохранён",
                    "Диск уже размечен. Повторный запуск пока недоступен.");
            case UNAVAILABLE -> ProductDialog.notice(activity, "Диск Windows подготовлен",
                    "Диск сохранён в U-AVF. Запуск Windows Setup с этого диска пока недоступен.");
        }
    }

    private void offerSetup(boolean guided) {
        long required = WindowsSetupDisk.requiredInstallFreeBytes();
        long available = activity.getFilesDir().getUsableSpace();
        if (guided && available >= required) {
            ProductDialog.choose(activity, "Windows Setup",
                    "Установка Windows 11 Pro начнётся автоматически после подтверждения.",
                    new String[]{"Открыть WinPE", "Установить Windows"}, -1, choice -> {
                        if (choice == 0) boot.accept(WindowsSetupRuntime.Boot.PREVIEW);
                        else confirmInstall();
                    });
            return;
        }
        String description = guided
                ? UiLanguage.format("Для автоматической установки нужно %1$s свободного места. Сейчас доступно %2$s.",
                        IoUtil.formatBytes(required), IoUtil.formatBytes(available))
                : UiLanguage.text("Можно открыть WinPE и проверить экран и ввод. Автоматическая установка пока недоступна в этой сборке.");
        ProductDialog.show(activity, "Windows Setup", description, "Открыть WinPE", false,
                () -> boot.accept(WindowsSetupRuntime.Boot.PREVIEW));
    }

    private void confirmInstall() {
        ProductDialog.show(activity, "Установить Windows",
                "U-AVF создаст разделы на подготовленном виртуальном диске и начнёт установку Windows 11 Pro.",
                "Установить", false, () -> boot.accept(WindowsSetupRuntime.Boot.INSTALL));
    }

    private void notice(String message) {
        ProductDialog.notice(activity, "Windows ARM64", message);
    }
}
