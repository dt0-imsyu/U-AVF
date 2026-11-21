// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import static com.example.winavf.WindowsDiagnosticMedia.DISK;
import static com.example.winavf.WindowsDiagnosticMedia.DISK_BYTES;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.function.Consumer;

/** Inspects the legacy Windows boot fixture without opening the user's Windows workspace disk. */
final class WindowsDiagnosticDiskReports {
    private static final String BASELINE_SHA = "2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7";
    private static final int MAX_SETUP_LOG_BYTES = 16 << 20;
    private static final String OFFLINE_SCOPE = "POST_STOP_OFFLINE_DISPOSABLE_DISK_INSPECTION";
    private static final String BCD_SCOPE = "READ_ONLY_PRODUCT_BASELINE_BCD_EXPORT";
    private final Context context;
    private final File external;
    private final Consumer<String> show;

    WindowsDiagnosticDiskReports(Context context, Consumer<String> show) {
        this.context = context;
        this.external = context.getExternalFilesDir(null);
        this.show = show;
    }

    void exportBcd() {
        File report = new File(external, "product-bcd-readonly-report.txt");
        try {
            File baseline = new File(external, DISK);
            byte[] bcd;
            try (var volume = Fat32Volume.open(baseline)) {
                bcd = volume.readFile("EFI", "MICROSOFT", "BOOT", "BCD");
            }
            if (bcd == null) throw new IOException("FAT entry not found: EFI/MICROSOFT/BOOT/BCD");
            try (var output = new FileOutputStream(new File(external, "product-bcd-readonly.bin"))) {
                output.write(bcd);
                output.getFD().sync();
            }
            try (var log = report(report)) {
                log.println("scope=" + BCD_SCOPE);
                log.println("imageBytes=" + baseline.length());
                log.println("bcdBytes=" + bcd.length);
                log.println("bcdSha256=" + Digest.hex(Digest.sha256(bcd)));
                log.println("result=PASS");
            }
            show.accept("Read-only product BCD export complete.");
        } catch (Exception error) {
            fail(report, BCD_SCOPE, "Read-only product BCD export failed: ", error);
        }
    }

    void auditWitness() {
        File report = new File(external, "persistent-boot-witness-report.txt");
        try {
            AvfGuest.requireStopped(context, DiagnosticVmConfig.WINDOWS_VM);
            File disk = new File(privatePayload(), DISK);
            if (!hasExpectedSize(disk)) throw new IOException("No disposable persistent-witness disk is available");
            try (var volume = Fat32Volume.open(disk); var log = report(report)) {
                log.println("scope=" + OFFLINE_SCOPE);
                log.println("diskBytes=" + disk.length());
                log.println("diskSha256=" + Digest.hex(Digest.sha256File(disk)));
                boolean setupActivity = exportSetupLog(volume, "SETUPACT", "persistent-witness-setupact.log", log);
                boolean setupError = exportSetupLog(volume, "SETUPERR", "persistent-witness-setuperr.log", log);
                String result = setupActivity || setupError ? "PASS" : "NOT_OBSERVED";
                log.println("PERSISTENT_WINDOWS_SETUP_WITNESS=" + result);
                log.println("WINDOWS_KERNEL_EXECUTION_AFTER_EBS=" + result);
                log.println("NEGATIVE_INTERPRETATION=NO_PERSISTENT_ARTIFACT_DOES_NOT_PROVE_NO_KERNEL_EXECUTION");
            }
            show.accept("Persistent witness offline audit complete");
        } catch (Exception error) {
            fail(report, OFFLINE_SCOPE, "Persistent witness audit failed: ", error);
        }
    }

    private boolean exportSetupLog(Fat32Volume volume, String name, String filename, PrintWriter log) throws IOException {
        byte[] content = volume.readRootFile(name, "LOG", MAX_SETUP_LOG_BYTES);
        if (content == null || content.length == 0) return false;
        Files.write(new File(external, filename).toPath(), content);
        log.println(name + "_LOG_BYTES=" + content.length);
        log.println(name + "_LOG_SHA256=" + Digest.hex(Digest.sha256(content)));
        return true;
    }

    void cleanupWitness() {
        File report = new File(external, "persistent-witness-cleanup-report.txt");
        try {
            AvfGuest.requireStopped(context, DiagnosticVmConfig.WINDOWS_VM);
            File baseline = new File(external, DISK);
            if (!hasExpectedSize(baseline)) throw new IOException("Immutable baseline is unavailable");
            String hash = Digest.hex(Digest.sha256File(baseline));
            if (!BASELINE_SHA.equals(hash)) throw new IOException("Immutable baseline hash mismatch");
            AvfGuest.removeStopped(context, DiagnosticVmConfig.WINDOWS_VM);
            if (AvfGuest.find(context, DiagnosticVmConfig.WINDOWS_VM) != null) {
                throw new IOException("Could not delete stopped diagnostic VM record");
            }
            File staging = privatePayload();
            Files.deleteIfExists(new File(staging, DISK).toPath());
            Files.deleteIfExists(new File(staging, "active-image-patch.bin").toPath());
            Files.deleteIfExists(new File(external, "winavf-image-patch.bin").toPath());
            try (var log = report(report)) {
                log.println("DISPOSABLE_PRIVATE_CLONE_DELETED=PASS");
                log.println("IMMUTABLE_BASELINE_SHA256=" + hash);
                log.println("RESULT=PASS");
            }
            show.accept("Disposable persistent-witness disk deleted; baseline verified");
        } catch (Exception error) {
            fail(report, "DISPOSABLE_PERSISTENT_WITNESS_CLEANUP", "Persistent witness cleanup failed: ", error);
        }
    }

    private File privatePayload() { return new File(context.getFilesDir(), "payload"); }

    private static boolean hasExpectedSize(File file) { return file.isFile() && file.length() == DISK_BYTES; }

    private static PrintWriter report(File file) throws IOException {
        return new PrintWriter(new FileOutputStream(file, false));
    }

    private void fail(File file, String scope, String message, Exception failure) {
        String reason = IoUtil.rootMessage(failure);
        try (var log = report(file)) {
            log.println("scope=" + scope);
            log.println("state=FAILED");
            log.println("error=" + reason);
        } catch (IOException error) {
            Log.w("U-AVF", "Could not write Windows diagnostic failure report", error);
        }
        show.accept(message + reason);
    }
}
