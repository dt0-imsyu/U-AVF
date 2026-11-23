// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.ui.UiLanguage;

import android.content.SharedPreferences;
import com.example.winavf.workspace.DisplaySettings;
import java.io.File;

/** Owns persistent Ubuntu disk preparation. Call only after acquiring the VM start guard. */
final class UbuntuWorkspaceMedia {
    private static final long ISO_BYTES = 3_967_463_424L;
    private static final long INSTALL_ROOT_BYTES = 34_359_738_368L;
    private static final long PREFIX_BYTES = 128L << 20;
    private static final long STORAGE_RESERVE = 2L << 30;
    private static final String ISO_SHA = "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14";
    static final String INSTALLED_READY = "ubuntu_installed_runtime_ready";
    static final String HANDOFF_PENDING = "ubuntu_install_handoff_pending";
    static final String HANDOFF_ATTEMPTED = "ubuntu_install_handoff_attempted";
    static final String PLASMA_PENDING = "ubuntu_plasma_install_pending";
    private static final String GROWTH_VERIFIED = "ubuntu_growth_helper_verified";
    private static final String PLATFORM_SHA = "ubuntu_install_platform_prefix_sha256";

    private static final String UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET = "uavf-install-platform-prefix-gdm-share.img";
    private static final String UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256 = "E2555776EDE54AD8C9DC38B8E1225C6673931F07522167464691D4064484AA2D";
    private static final String UBUNTU_INSTALL_MAVK_PREFIX = "uavf-install-platform-mavk-r2.img";
    private static final String UBUNTU_INSTALL_MAVK_PREFIX_SHA256 = "E527AA3ED0F6A59570C8F623152E806ADE852D31F72FC7C3E3CAED00DCC81383";
    private static final String UBUNTU_INSTALL_MAVK_ESP_SHA256 = "5D927DB2E4BCFED509E59F40274AAF077747B3D856891D08898B334E33B94E1C";
    private static final String UBUNTU_INSTALL_PLASMA_PREFIX = "uavf-install-platform-plasma-r2.img";
    private static final String UBUNTU_INSTALL_PLASMA_PREFIX_SHA256 = "A98B4C127B7BE9666BB400DD65AD7EC202D7B7F9E3026F7310C5DC759D2D959D";
    private static final String UBUNTU_INSTALL_PLASMA_ESP_SHA256 = "8F89D3B492A6157A077BDBCC5A50D18B0572B34F310F5BB489729E83BE1D10A6";
    private static final String UBUNTU_INSTALL_PLATFORM_ESP_SHA256 = "AE9B2C7DE939D204E749274035BECE03B469C974ECD243272AB18B46E5925824";

    enum Installer {
        DEFAULT(0, UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET, UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256,
                UBUNTU_INSTALL_PLATFORM_ESP_SHA256),
        H264(1, UBUNTU_INSTALL_MAVK_PREFIX, UBUNTU_INSTALL_MAVK_PREFIX_SHA256, UBUNTU_INSTALL_MAVK_ESP_SHA256),
        MAVK(2, UBUNTU_INSTALL_MAVK_PREFIX, UBUNTU_INSTALL_MAVK_PREFIX_SHA256, UBUNTU_INSTALL_MAVK_ESP_SHA256),
        PLASMA(3, UBUNTU_INSTALL_PLASMA_PREFIX, UBUNTU_INSTALL_PLASMA_PREFIX_SHA256, UBUNTU_INSTALL_PLASMA_ESP_SHA256);

        final int storedValue;
        final String asset;
        final String prefixSha;
        final String espSha;

        Installer(int storedValue, String asset, String prefixSha, String espSha) {
            this.storedValue = storedValue;
            this.asset = asset;
            this.prefixSha = prefixSha;
            this.espSha = espSha;
        }

        static Installer fromStored(int value) {
            return switch (value) {
                case 1 -> H264;
                case 2 -> MAVK;
                case 3 -> PLASMA;
                default -> DEFAULT;
            };
        }
    }

    enum Request { START_EXISTING, CREATE, REINSTALL, FIRST_BOOT }
    record Prepared(File disk, File kernel, String platformUpdate, Installer firstBootInstaller) {}

    interface Assets {
        File verified(String asset, File destination, long bytes, String sha) throws Exception;
        File copy(String asset, File destination) throws Exception;
    }

    private final SharedPreferences prefs;
    private final File files;
    private final File external;
    private final File disk;
    private final Assets assets;

    UbuntuWorkspaceMedia(SharedPreferences prefs, File files, File external, File disk, Assets assets) {
        this.prefs = prefs;
        this.files = files;
        this.external = external;
        this.disk = disk;
        this.assets = assets;
    }

    Prepared prepare(Request request, Installer installer, boolean installing, long rootBytes) throws Exception {
        Installer firstBoot = Installer.fromStored(prefs.getInt(DisplaySettings.FIRST_BOOT_MODE, 0));
        Installer active = installing && request != Request.REINSTALL
                ? (request == Request.CREATE ? installer : firstBoot) : Installer.DEFAULT;
        if (active == Installer.PLASMA && !prefs.getBoolean("linux_internet_enabled", true)) {
            throw new IllegalStateException(UiLanguage.text("Для загрузки Plasma нужен интернет в Ubuntu."));
        }
        File staging = new File(files, "linux-persistent-payload");
        String platformUpdate = request == Request.CREATE ? create(staging, active, rootBytes)
                : prepareExisting(staging, active, request);
        ensureStaging(staging);
        File kernel = assets.copy("u-boot-wrapper-v24.Image", new File(staging, "u-boot-wrapper-v24.Image"));
        return new Prepared(disk, kernel, platformUpdate, firstBoot);
    }

    private String create(File staging, Installer installer, long rootBytes) throws Exception {
        if (disk.exists()) throw new IllegalStateException("An installation disk already exists; use Start installed Ubuntu.");
        File iso = external == null ? null : new File(external, "ubuntu-24.04.5-desktop-arm64.iso");
        if (iso == null || !iso.isFile() || iso.length() != ISO_BYTES) {
            throw new IllegalStateException("Choose the verified Ubuntu 24.04.5 ARM64 ISO first.");
        }
        if (files.getUsableSpace() < PersistentUbuntuDisk.expectedDiskBytes(ISO_BYTES, rootBytes) + STORAGE_RESERVE) {
            throw new IllegalStateException("Not enough Android storage for the selected Ubuntu disk and 2 GiB reserve.");
        }
        File prefix = stagePrefix(staging, installer);
        PersistentUbuntuDisk.create(prefix, iso, disk, installer.prefixSha, ISO_SHA, rootBytes);
        prefs.edit().putBoolean(GROWTH_VERIFIED, false).putBoolean(INSTALLED_READY, false)
                .putString(PLATFORM_SHA, installer.prefixSha)
                .putInt(DisplaySettings.FIRST_BOOT_MODE, installer.storedValue)
                .remove(DisplaySettings.FIRST_BOOT_OVERRIDE).remove(PLASMA_PENDING).apply();
        return "NOT_REQUIRED";
    }

    private String prepareExisting(File staging, Installer installer, Request request) throws Exception {
        File growthJournal = new File(disk.getParentFile(), "root-growth.intent");
        if (growthJournal.isFile()) PersistentUbuntuDisk.completeGrow(disk, ISO_BYTES, growthJournal);
        if (!PersistentUbuntuDisk.audit(disk, ISO_BYTES, INSTALL_ROOT_BYTES)) {
            throw new SecurityException("Persistent Ubuntu disk layout failed its GPT check; it was left untouched.");
        }
        if (request == Request.REINSTALL) {
            prefs.edit().putBoolean(GROWTH_VERIFIED, false).remove(DisplaySettings.FIRST_BOOT_MODE)
                    .remove(DisplaySettings.FIRST_BOOT_OVERRIDE).putBoolean(INSTALLED_READY, false)
                    .putBoolean(HANDOFF_PENDING, false).putBoolean(HANDOFF_ATTEMPTED, false).apply();
        } else {
            if (installer.prefixSha.equalsIgnoreCase(prefs.getString(PLATFORM_SHA, ""))) return "NOT_REQUIRED";
            if (request == Request.FIRST_BOOT || prefs.getBoolean(INSTALLED_READY, false)
                    || PersistentUbuntuDisk.hasInstalledBootCandidate(disk)) return "SKIPPED_INSTALLED_SYSTEM";
        }
        File prefix = stagePrefix(staging, installer);
        String result = PersistentUbuntuDisk.updatePlatformEsp(disk, prefix, ISO_BYTES, installer.prefixSha,
                installer.espSha, new File(staging, "esp-backups"));
        prefs.edit().putString(PLATFORM_SHA, installer.prefixSha).apply();
        return result;
    }

    private File stagePrefix(File staging, Installer installer) throws Exception {
        ensureStaging(staging);
        return assets.verified(installer.asset, new File(staging, installer.asset), PREFIX_BYTES, installer.prefixSha);
    }

    private static void ensureStaging(File staging) {
        if (!staging.isDirectory() && !staging.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu install staging directory.");
    }
}
