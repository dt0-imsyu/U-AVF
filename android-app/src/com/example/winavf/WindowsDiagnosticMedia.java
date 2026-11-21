// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** Stages the preserved Windows boot diagnostic independently of the ISO installer. */
final class WindowsDiagnosticMedia {
    private static final String KERNEL = "u-boot-wrapper-v24.Image";
    static final String DISK = "win11-gop-ebs-r1.img";
    static final long DISK_BYTES = 9_126_805_504L;

    record Prepared(File staging, File kernel, File disk) {}

    private WindowsDiagnosticMedia() {}

    static Prepared prepare(Context context, GuestMediaFiles assets, boolean skipValidation, Consumer<String> show)
            throws IOException {
        File staging = new File(context.getFilesDir(), "payload");
        if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Cannot create payload directory");
        File kernel = assets.copyAsset(KERNEL, new File(staging, KERNEL), -1);
        File external = context.getExternalFilesDir(null);
        File marker = new File(staging, "u-boot-wrapper-start-marker.Image");
        if (marker.isFile()) {
            GuestMediaFiles.copy(marker, new File(external, "initial-working-u-boot-wrapper.Image"), -1, true, () -> {});
        }
        File source = new File(external, DISK);
        GuestMediaFiles.verifyDisk(source, DISK_BYTES, null, "Windows boot medium", skipValidation, show);
        File disk = GuestMediaFiles.copy(source, new File(staging, DISK), skipValidation ? -1 : DISK_BYTES, false,
                () -> show.accept("Copying Windows setup medium into app-private storage…"));
        return new Prepared(staging, kernel, disk);
    }
}
