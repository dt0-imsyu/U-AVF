// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.example.winavf.navigation.ProductScreen;
import com.example.winavf.UbuntuWorkspaceMedia.Installer;
import com.example.winavf.UbuntuWorkspaceMedia.Request;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Presents the Ubuntu installer and the confirmed workspace disk operations. */
final class UbuntuWorkspaceActions {
    private static final long GIB = UbuntuWorkspaceStorage.GIB;
    private static final long[] ROOT_SIZES = {32, 48, 64, 96, 128, 256};
    private final Activity activity;
    private final SharedPreferences preferences;
    private final UbuntuWorkspaceStorage storage;
    private final GuestMediaFiles files;
    private final BooleanSupplier blockedByVm;
    private final Supplier<String> draftTitle;
    private final Runnable pickIso;
    private final BiConsumer<Request, Installer> start;
    private final Consumer<ProductScreen> navigate;
    private final Consumer<String> show;
    private final AtomicBoolean storageBusy = new AtomicBoolean();

    UbuntuWorkspaceActions(Activity activity, UbuntuWorkspaceStorage storage, GuestMediaFiles files,
                           BooleanSupplier blockedByVm, Supplier<String> draftTitle, Runnable pickIso,
                           BiConsumer<Request, Installer> start, Consumer<ProductScreen> navigate, Consumer<String> show) {
        this.activity = activity;
        this.preferences = activity.getPreferences(0);
        this.storage = storage;
        this.files = files;
        this.blockedByVm = blockedByVm;
        this.draftTitle = draftTitle;
        this.pickIso = pickIso;
        this.start = start;
        this.navigate = navigate;
        this.show = show;
    }

    void chooseIso() {
        ProductDialog.menu(activity, "Установочный ISO", new String[]{"Выбрать файл…", "Сайт Ubuntu ↗"}, index -> {
            if (index == 1) openOfficialDownload();
            else if (blockedByVm.getAsBoolean()) show.accept("Завершите Ubuntu перед сменой образа.");
            else pickIso.run();
        });
    }

    void openOfficialDownload() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://cdimage.ubuntu.com/ubuntu/releases/24.04.5/release/")));
        } catch (ActivityNotFoundException error) {
            show.accept("No browser available. Open cdimage.ubuntu.com to download Ubuntu Desktop ARM64.");
        }
    }

    void confirmInstall(boolean repair) {
        if (!UbuntuIsoImport.present(activity.getExternalFilesDir(null))) {
            ProductDialog.show(activity, "Нужен образ Ubuntu",
                    "Для установки нужен официальный образ Ubuntu 24.04.5 Desktop ARM64. Без него U-AVF не может создать систему, поэтому установка не начинается.",
                    "Выбрать образ", false, this::chooseIso);
            return;
        }
        String description = "U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. "
                + (repair ? "Back up any files on the existing Ubuntu system first. Do not change the storage layout in Ubuntu Setup."
                : "You will choose your language, keyboard and account in Ubuntu Setup; do not change its storage layout.");
        ProductDialog.show(activity, repair ? "Repair or reinstall Ubuntu?" : "Install Ubuntu safely",
                description, "Continue to installer", repair, () -> chooseInstaller(repair));
    }

    private void chooseInstaller(boolean existing) {
        if (existing || storage.disk().exists() || !MavkRuntimeAssets.isBundled(activity) || !files.hasAsset(Installer.MAVK.asset)) {
            launchInstaller(existing, Installer.DEFAULT);
            return;
        }
        boolean plasma = files.hasAsset(Installer.PLASMA.asset);
        String[] labels = {"GNOME X11 · H.264 / OpenGL", "GNOME X11 · Mavk (экспериментально)", "KDE Plasma X11 · Mavk"};
        Installer[] installers = {Installer.H264, Installer.MAVK, Installer.PLASMA};
        ProductDialog.choose(activity, "Рабочий стол Ubuntu", "Выберите рабочий стол и графику. Для загрузки Plasma нужен интернет.",
                plasma ? labels : Arrays.copyOf(labels, 2), 0, index -> launchInstaller(false, installers[index]));
    }

    private void launchInstaller(boolean existing, Installer installer) {
        if (installer == Installer.PLASMA && !preferences.getBoolean("linux_internet_enabled", true)) {
            ProductDialog.notice(activity, "KDE Plasma X11",
                    "Включите интернет в настройках Ubuntu или выберите GNOME. Plasma загружается при установке.");
            return;
        }
        String draft = draftTitle.get();
        if (!existing && !storage.disk().exists() && !draft.isEmpty()) {
            preferences.edit().putString("ubuntu_workspace_title", draft).apply();
        }
        new Thread(() -> start.accept(existing ? Request.START_EXISTING : Request.CREATE, installer),
                "U-AVF-persistent-ubuntu").start();
    }

    void confirmReinstall() {
        ProductDialog.show(activity, "Reinstall Ubuntu?",
                "This replaces the Ubuntu system on its dedicated vda3 partition. Personal files and settings on that Ubuntu partition will be erased. The official installer image on vda2 and U-AVF boot partition on vda1 are preserved. The app verifies the disk layout before it starts.",
                "Reinstall Ubuntu", true, () -> new Thread(() -> start.accept(Request.REINSTALL, Installer.DEFAULT),
                        "U-AVF-ubuntu-reinstall").start());
    }

    String rootSizeLabel() {
        try {
            long bytes = storage.disk().isFile() ? storage.installedRootBytes() : storage.plannedRootBytes();
            return bytes / GIB + " GiB";
        } catch (Exception error) {
            return "Unavailable — disk audit required";
        }
    }

    void chooseRootSize() {
        if (storage.disk().isFile()) {
            show.accept("Use Expand Ubuntu storage for an existing workspace.");
            return;
        }
        String[] labels = new String[ROOT_SIZES.length];
        long planned = storage.plannedRootBytes();
        int selected = -1;
        for (int index = 0; index < ROOT_SIZES.length; index++) {
            labels[index] = ROOT_SIZES[index] + " GiB";
            if (ROOT_SIZES[index] * GIB == planned) selected = index;
        }
        ProductDialog.choose(activity, "Ubuntu system storage",
                "Choose space for Ubuntu. Boot files and the ISO use additional space.", labels, selected, index -> {
                    try {
                        storage.planRoot(ROOT_SIZES[index] * GIB);
                        navigate.accept(ProductScreen.CREATE_STORAGE);
                    } catch (IllegalStateException error) {
                        show.accept(error.getMessage());
                    }
                });
    }

    void showExpansion() {
        if (storageBusy.get()) {
            show.accept("Storage operation is already running.");
            return;
        }
        String blocker = storage.expansionBlocker();
        if (!blocker.isEmpty()) {
            show.accept(blocker);
            return;
        }
        if (!storage.guestCanExpand()) {
            ProductDialog.notice(activity, "Storage runtime update required",
                    "The installed guest has not confirmed automatic ext4 expansion support. Its disk will not be changed. A guest runtime update is required first.");
            return;
        }
        workOnStorage("U-AVF-storage-check", "Storage check failed: ", () -> {
            long current = storage.installedRootBytes();
            long free = storage.freeBytes();
            onUi(() -> chooseExpansion(current, free));
        });
    }

    private void chooseExpansion(long current, long free) {
        String[] choices = {"Add 8 GiB", "Add 16 GiB", "Add 32 GiB", "Add 64 GiB"};
        ProductDialog.choose(activity, "Expand Ubuntu storage", "Current Ubuntu storage: " + current / GIB + " GiB",
                choices, -1, index -> {
                    long add = (8L << index) * GIB;
                    if (free < add + UbuntuWorkspaceStorage.RESERVE_BYTES) {
                        show.accept("Not enough Android storage; keep at least 2 GiB free.");
                        return;
                    }
                    long total = current + add;
                    ProductDialog.show(activity, "Expand root to " + total / GIB + " GiB?",
                            "Ubuntu files, the boot partition and ISO are preserved. The partition grows now; Ubuntu expands ext4 on its next boot. Keep the tablet charged.",
                            "Expand", false, () -> expand(total));
                });
    }

    private void expand(long total) {
        workOnStorage("U-AVF-storage-expand", "Storage expansion not completed: ", () -> {
            storage.expand(total);
            onUi(() -> navigate.accept(ProductScreen.VM_SETTINGS));
            show.accept("Partition expanded. Start Ubuntu to finish ext4 expansion.");
        });
    }

    void confirmDelete() {
        if (blockedByVm.getAsBoolean()) {
            show.accept("Stop the running VM before deleting its workspace.");
            return;
        }
        ProductDialog.show(activity, "Delete Ubuntu workspace?",
                "This permanently deletes U-AVF's persistent Ubuntu workspace disk and installed files. The downloaded official Ubuntu ISO and other app data are kept. This cannot be undone.",
                "Delete workspace", true, () -> workOnStorage("U-AVF-delete-workspace", "Workspace was not deleted: ", () -> {
                    storage.delete();
                    onUi(() -> navigate.accept(ProductScreen.MODE_SELECTION));
                    show.accept("Ubuntu workspace deleted. The verified source ISO was kept.");
                }));
    }

    @FunctionalInterface
    private interface StorageWork { void run() throws Exception; }

    private void workOnStorage(String worker, String errorPrefix, StorageWork work) {
        if (!storageBusy.compareAndSet(false, true)) {
            show.accept("Storage operation is already running.");
            return;
        }
        new Thread(() -> {
            try {
                work.run();
            } catch (Exception error) {
                show.accept(errorPrefix + IoUtil.rootMessage(error));
            } finally {
                storageBusy.set(false);
            }
        }, worker).start();
    }

    private void onUi(Runnable action) {
        activity.runOnUiThread(() -> {
            if (!activity.isDestroyed()) action.run();
        });
    }
}
