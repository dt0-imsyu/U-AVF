// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** Stages the existing GNOME and V12 debug fixtures in their own app-private directories. */
final class UbuntuDiagnosticMedia {
    private static final String KERNEL = "u-boot-wrapper-v24.Image";
    private static final String GNOME_DISK = "ubuntu-gnome-24.04.5-v21-vsock-listener.img";
    private static final String GNOME_PATCH = "ubuntu-gnome-v21-vsock-firmware.patch";
    private static final String RECOVERY_DISK = "ubuntu-gnome-v12-recovery-candidate.img";
    private static final String RECOVERY_PATCH = "ubuntu-gnome-v12-recovery-firmware.patch";
    private static final long DISK_BYTES = 9_126_805_504L;
    private static final long PATCH_BYTES = 4_194_436L;
    private final Context context;
    private final GuestMediaFiles files;
    private final Consumer<String> show;
    private final boolean skipValidation;

    record Gnome(File kernel, File disk, File patch) {}
    record Recovery(File kernel, File disk) {}

    UbuntuDiagnosticMedia(Context context, GuestMediaFiles files, boolean skipValidation, Consumer<String> show) {
        this.context = context;
        this.files = files;
        this.skipValidation = skipValidation;
        this.show = show;
    }

    Gnome gnome() throws IOException {
        File staging = staging("ubuntu-gnome-payload");
        File kernel = copyKernel(staging);
        File disk = new File(staging, GNOME_DISK);
        String digest = "F3AAC176C600BFC3D3EA0BE5DA4EFF2F03DE7B27E3DED26A0E9D58D21B975FAB";
        if (disk.exists()) {
            verify(disk, DISK_BYTES, digest, "private Ubuntu medium");
            show.accept("Using preloaded app-private Ubuntu GNOME medium.");
        } else {
            File source = external(GNOME_DISK);
            verify(source, DISK_BYTES, digest, "Ubuntu GNOME staging medium");
            stage(source, staging, DISK_BYTES);
        }
        verify(disk, DISK_BYTES, digest, "private Ubuntu medium");
        File patch = external(GNOME_PATCH);
        verify(patch, PATCH_BYTES, "936B0E8106806E5E4AC99C4C8E9FD9394F34747992E7E44BBFF885A8AD50553A",
                "Ubuntu-only firmware patch");
        return new Gnome(kernel, disk, stage(patch, staging, PATCH_BYTES));
    }

    Recovery recovery() throws IOException {
        File staging = staging("v12-recovery-payload");
        File kernel = copyKernel(staging);
        File disk = external(RECOVERY_DISK);
        verify(disk, DISK_BYTES, "C9D80A9CABB14C6E1D0F4F3180923146E354CC916020FB55BB2608FDC3110EA8",
                "V12 recovery carrier");
        verify(external(RECOVERY_PATCH), PATCH_BYTES,
                "9C109F3EB95D1B6F27929D970152725E0F2328A25FC3C847D8ED1603D86EDC55", "V12 recovery patch");
        Log.e("WinAVF", "V12_MEDIA_GATE_PASS");
        return new Recovery(kernel, stage(disk, staging, DISK_BYTES));
    }

    private File staging(String name) throws IOException {
        File folder = new File(context.getFilesDir(), name);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create staging directory: " + name);
        return folder;
    }

    private File copyKernel(File staging) throws IOException {
        return files.copyAsset(KERNEL, new File(staging, KERNEL), -1);
    }

    private File external(String name) { return new File(context.getExternalFilesDir(null), name); }

    private File stage(File source, File staging, long bytes) throws IOException {
        return GuestMediaFiles.copy(source, new File(staging, source.getName()), skipValidation ? -1 : bytes, false,
                () -> show.accept("Copying Ubuntu boot media into app-private storage…"));
    }

    private void verify(File file, long bytes, String hash, String label) throws IOException {
        GuestMediaFiles.verifyDisk(file, bytes, hash, label, skipValidation, show);
    }
}
