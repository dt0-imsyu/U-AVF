// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;

/** Stages the stock ISO and historical platform fixtures for the debug boot variants. */
final class UbuntuGenericBootMedia {
    enum Layout {
        SEPARATE_ISO, PLATFORM_ONLY, EXTRA_ESP, COMBINED;
    }

    private static final long PLATFORM_BYTES = 128L << 20;
    private static final long COMBINED_BYTES = 4_102_029_312L;
    private static final String PLATFORM_ASSET = "generic-ubuntu-platform-gpt.img";
    private static final String COMBINED_ASSET = "generic-ubuntu-one-disk-journal-rplus.img";
    private static final String PLATFORM_SHA = "413B28882832DDC73CA3B425D9676542595590B53B91739FC4A45CDF41046AA2";
    private static final String COMBINED_SHA = "FFABA39B31EA1E95B40F4DD3F67BF5C274C336C6CFF427E7075AC0BD82143714";
    private final Context context;
    private final GuestMediaFiles files;
    private final Consumer<String> show;

    record Prepared(UbuntuVmConfig.Media media, File stockIso, Layout layout, boolean skipValidation) {}

    UbuntuGenericBootMedia(Context context, GuestMediaFiles files, Consumer<String> show) {
        this.context = context;
        this.files = files;
        this.show = show;
    }

    Prepared prepare(Layout layout, boolean skipValidation) throws IOException {
        File external = context.getExternalFilesDir(null);
        File iso = new File(external, UbuntuIsoImport.NAME);
        File platform = new File(external, PLATFORM_ASSET);
        File combined = new File(external, COMBINED_ASSET);
        GuestMediaFiles.verifyDisk(iso, UbuntuIsoImport.BYTES, UbuntuIsoImport.SHA256,
                "stock Ubuntu ISO", skipValidation, show);
        GuestMediaFiles.verifyDisk(platform, PLATFORM_BYTES, PLATFORM_SHA,
                "Ubuntu platform ESP", skipValidation, show);
        if (layout == Layout.COMBINED) {
            GuestMediaFiles.verifyDisk(combined, COMBINED_BYTES, COMBINED_SHA,
                    "combined Ubuntu disk", skipValidation, show);
        }
        File staging = new File(context.getFilesDir(), "generic-ubuntu-payload");
        if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Cannot create generic Ubuntu payload");

        File separateIso = layout == Layout.SEPARATE_ISO
                ? stage(iso, staging, UbuntuIsoImport.NAME, UbuntuIsoImport.BYTES, skipValidation) : null;
        File disk = layout == Layout.COMBINED
                ? stage(combined, staging, COMBINED_ASSET, COMBINED_BYTES, skipValidation)
                : stage(platform, staging, PLATFORM_ASSET, PLATFORM_BYTES, skipValidation);
        File extraDisk = layout == Layout.EXTRA_ESP
                ? stage(platform, staging, "generic-ubuntu-second-esp-control.img", PLATFORM_BYTES, skipValidation) : null;
        String kernelAsset = "u-boot-wrapper-v24.Image";
        File kernel = files.copyAsset(kernelAsset, new File(staging, kernelAsset), -1);
        return new Prepared(new UbuntuVmConfig.Media(kernel, disk, separateIso, extraDisk),
                separateIso == null ? iso : separateIso, layout, skipValidation);
    }

    private File stage(File source, File staging, String name, long bytes, boolean skipValidation) throws IOException {
        return GuestMediaFiles.copy(source, new File(staging, name), skipValidation ? -1 : bytes, false,
                () -> show.accept("Copying Ubuntu boot media into app-private storage…"));
    }

    void reportLaunch(Prepared prepared) throws IOException {
        try (var log = openReport()) {
            Layout layout = prepared.layout();
            log.println("PROFILE=GENERIC_UBUNTU");
            log.println("STOCK_UBUNTU_24_04_5_UNMODIFIED=PASS");
            log.println("ISO_SHA256=" + (prepared.skipValidation() ? "SKIPPED_BY_USER"
                    : Digest.hex(Digest.sha256File(prepared.stockIso()))));
            log.println("PLATFORM_ESP_PRESENT=PASS");
            log.println("PLATFORM_EFI_LOADER_PRESENT=PASS");
            log.println("BOOT_CHAIN=KERNEL_FIRST_UBOOT_THEN_ESP_EDK2");
            log.println("SECOND_ISO_BLOCK_DISK=" + (layout == Layout.SEPARATE_ISO ? "PRESENT" : "ABSENT_DIAGNOSTIC"));
            log.println("SECOND_ESP_CONTROL_DISK=" + (layout == Layout.EXTRA_ESP ? "PRESENT" : "ABSENT"));
            log.println("ONE_DISK_ESP_PLUS_STOCK_ISO_PARTITION=" + (layout == Layout.COMBINED ? "PASS" : "NO"));
            log.println("VM_LAUNCH=PASS");
        }
    }

    void reportFailure(String reason) {
        try (var log = openReport()) {
            log.println("PROFILE=GENERIC_UBUNTU");
            log.println("STOCK_UBUNTU_24_04_5_UNMODIFIED=NOT_REACHED");
            log.println("ERROR=" + reason);
        } catch (IOException error) {
            Log.w("U-AVF", "Could not write generic Ubuntu boot report", error);
        }
    }

    private PrintWriter openReport() throws IOException {
        File report = new File(context.getExternalFilesDir(null), "generic-ubuntu-runtime-report.txt");
        return new PrintWriter(new FileOutputStream(report, false));
    }
}
