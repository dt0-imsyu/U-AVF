// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;

/** Reports which historical ext4 fixtures are staged for the block I/O experiments. */
final class UbuntuBlockIoAudit {
    private static final String CONTROL_IMAGE = "ubuntu-gnome-v22-ext4-control.img";
    private static final long CONTROL_BYTES = 9_126_805_504L;
    private static final String CONTROL_HASH = "F3C9498D0E4EFFAB2E38C75E523492A8CDD CFA9DC6CB3DDF1B48632E26C57D2C".replace(" ", "");
    private final Context context;
    private final Consumer<String> show;

    UbuntuBlockIoAudit(Context context, Consumer<String> show) {
        this.context = context;
        this.show = show;
    }

    void stageControl() {
        File folder = context.getExternalFilesDir(null);
        File report = new File(folder, "ubuntu-gnome-v22-ext4-control-report.txt");
        try {
            File source = new File(folder, CONTROL_IMAGE);
            if (!hasControlSize(source)) throw new IOException("Missing V22 control image: " + source);
            String hash = sha(source);
            if (!CONTROL_HASH.equals(hash)) throw new SecurityException("V22 control image hash mismatch: " + hash);
            File staging = new File(context.getFilesDir(), "v22-ext4-control-payload");
            if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Cannot create V22 payload directory");

            File target = GuestMediaFiles.copy(source, new File(staging, CONTROL_IMAGE), CONTROL_BYTES, false,
                    () -> show.accept("Copying Windows setup medium into app-private storage…"));
            writeControlReport(report, sha(target));
            show.accept("V22 control image staged; launch blocked pending Ubuntu root and guest protocol.");
        } catch (Exception error) {
            writeFailure(report, "V22_EXT4_CONTROL", "EXT4_BLOCK_IO_CONTROL", error);
            show.accept("V22 control path blocked: " + IoUtil.rootMessage(error));
        }
    }

    private static void writeControlReport(File report, String hash) throws IOException {
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("PROFILE=V22_EXT4_CONTROL");
            log.println("VM_NAME=winavf-ubuntu-gnome-24045-v22-ext4-control");
            log.println("MEDIA_NAME=" + CONTROL_IMAGE);
            log.println("MEDIA_LABEL=V22_EXT4_CONTROL");
            log.println("MEDIA_BYTES=" + CONTROL_BYTES);
            log.println("MEDIA_SHA256=" + hash);
            log.println("CONTROL_IMAGE_STAGED=PASS");
            log.println("BOOTABLE_UBUNTU_ROOT=BLOCKED");
            log.println("BOOTABLE_UBUNTU_ROOT_REASON=No guest root filesystem or EFI/initrd boot contract is present; control image must not be used as root disk.");
            log.println("EXT4_BLOCK_IO_CONTROL=BLOCKED");
            log.println("EXT4_BLOCK_IO_CONTROL_REASON=Guest-side EXT4_BLOCK_IO_CONTROL protocol and runnable Ubuntu payload are unconfirmed.");
            log.println("VM_LAUNCH=NOT_ATTEMPTED");
        }
    }

    void inspectBlockIo() {
        File folder = context.getExternalFilesDir(null);
        File report = new File(folder, "ubuntu-gnome-v23-ext4-block-io-report.txt");
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("PROFILE=V23_EXT4_BLOCK_IO");
            log.println("VM_NAME=winavf-ubuntu-gnome-24045-v23-ext4-block-io");
            log.println("AVF_DISK_API=Disk.RODisk(String)+Builder.addDisk(Disk)");
            inspectFixtures(log, new File(folder, CONTROL_IMAGE), new File(folder, "ubuntu-gnome-v21-v23-disposable-carrier.img"));
            show.accept("V23 two-disk API boundary audited; guest launch remains gated by verified boot carrier.");
        } catch (Exception error) {
            writeFailure(report, "V23_EXT4_BLOCK_IO", "EXT4_BLOCK_IO", error);
            show.accept("V23 block-I/O path blocked: " + IoUtil.rootMessage(error));
        }
    }

    private static void inspectFixtures(PrintWriter log, File control, File carrier) throws IOException {
        log.println("CONTROL_DISK_PRESENT=" + control.isFile());
        log.println("BOOT_CARRIER_PRESENT=" + carrier.isFile());
        if (!hasControlSize(control)) {
            blocked(log, "Verified V22 control disk is not staged");
            return;
        }
        String hash = sha(control);
        if (!CONTROL_HASH.equals(hash)) {
            blocked(log, "V22 control disk hash mismatch");
        } else if (!carrier.isFile() || carrier.length() == 0) {
            blocked(log, "Verified V21 boot carrier is not staged; VM launch not attempted");
        } else {
            log.println("CONTROL_DISK_SHA256=" + hash);
            log.println("BOOT_CARRIER_BYTES=" + carrier.length());
            log.println("EXT4_BLOCK_IO=API_READY_GUEST_CARRIER_PENDING_HASH");
            log.println("VM_LAUNCH=NOT_ATTEMPTED");
        }
    }

    private static void blocked(PrintWriter log, String reason) {
        log.println("EXT4_BLOCK_IO=BLOCKED");
        log.println("REASON=" + reason);
    }

    private static boolean hasControlSize(File file) { return file.isFile() && file.length() == CONTROL_BYTES; }

    private static String sha(File file) throws IOException { return Digest.hex(Digest.sha256File(file)); }

    private static void writeFailure(File report, String profile, String check, Exception error) {
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("PROFILE=" + profile);
            log.println(check + "=BLOCKED");
            log.println("ERROR=" + IoUtil.rootMessage(error));
        } catch (IOException ignored) {
            // The caller still reports the failure to the user.
        }
    }
}
