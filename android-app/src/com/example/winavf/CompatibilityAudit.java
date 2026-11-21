// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import com.example.winavf.diagnostics.CompatibilityHistory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import static com.example.winavf.AvfCompatibilityProbe.Status;

/** Checks the local AVF prerequisites, runs the VM proof, and records its result. */
final class CompatibilityAudit {
    static final int SCHEMA = 3;
    private final Context context;
    private final SharedPreferences preferences;
    private final BooleanSupplier permissions;

    record Result(String id, Status status, String[] checks, long guestMemoryBytes) {}
    private record Evidence(Status status, String details, String report, long guestMemoryBytes) {}
    CompatibilityAudit(Context context, SharedPreferences preferences, BooleanSupplier permissions) {
        this.context = context;
        this.preferences = preferences;
        this.permissions = permissions;
    }

    Result run(BiConsumer<Integer, String> progress) {
        String[] checks = new String[FigmaCompatibilityView.CHECKS.length];
        Arrays.fill(checks, "Not checked");
        Evidence evidence;
        try {
            evidence = collectEvidence(checks, progress);
        } catch (Exception error) {
            evidence = unavailable(IoUtil.rootMessage(error));
        }
        String readable = formatReport(checks, evidence);
        writeReport("uavf-avf-compatibility.txt", readable);
        String id = UUID.randomUUID().toString().replace("-", "");
        save(id, checks, evidence.status(), readable);
        return new Result(id, evidence.status(), checks, evidence.guestMemoryBytes());
    }

    private Evidence collectEvidence(String[] checks, BiConsumer<Integer, String> progress) {
        boolean arm64 = Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
        setCheck(checks, progress, 0, arm64 ? "Passed · Android " + Build.VERSION.RELEASE + " / ARM64"
                : "Failed · ARM64 required");
        String managerError = managerError();
        setCheck(checks, progress, 1, managerError.isEmpty() ? "Passed · AVF API available"
                : "Failed · AVF manager unavailable");
        boolean permissionGranted = permissions.getAsBoolean();
        setCheck(checks, progress, 2, permissionGranted ? "Passed · granted" : "Failed · permission required");
        String platformError = platformError();
        setCheck(checks, progress, 3, platformError.isEmpty() ? "Passed · SHA-256 verified" : platformError);

        if (!arm64) return new Evidence(Status.INCOMPATIBLE, "The Android device does not expose an ARM64 ABI.", "", 0);
        if (!permissionGranted) return unavailable("Custom VM permission was not granted.");
        if (!platformError.isEmpty()) return unavailable(platformError);
        if (!managerError.isEmpty()) return unavailable(managerError);
        return runVmProof(checks);
    }

    private String managerError() {
        try {
            Class<?> type = Class.forName("android.system.virtualmachine.VirtualMachineManager");
            return context.getSystemService(type) != null ? "" : "Android did not provide a virtualization manager.";
        } catch (Exception error) {
            return IoUtil.rootMessage(error);
        }
    }

    private String platformError() {
        try {
            for (String asset : BundledPlatformAssets.NAMES) {
                verifyAsset(asset);
            }
            return "";
        } catch (Exception error) {
            return "Failed · " + IoUtil.rootMessage(error);
        }
    }

    private void verifyAsset(String asset) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long bytes = 0;
        byte[] buffer = new byte[1 << 20];
        try (InputStream input = context.getAssets().open(asset)) {
            for (int count; (count = input.read(buffer)) != -1;) {
                bytes += count;
                digest.update(buffer, 0, count);
            }
        }
        if (bytes != BundledPlatformAssets.size(asset)
                || !BundledPlatformAssets.sha256(asset).equals(Digest.hex(digest.digest()))) {
            throw new SecurityException("Bundled platform asset failed verification: " + asset);
        }
    }

    private Evidence runVmProof(String[] checks) {
        var probe = new AvfCompatibilityProbe(context);
        var result = probe.run();
        checks[4] = result.created() ? "Passed" : "Failed · VM creation was not confirmed";
        checks[5] = proof(result.booted(), result.created(), "Kernel marker absent");
        checks[6] = proof(result.memory(), result.booted(), "Memory proof absent");
        checks[7] = proof(result.storage(), result.vsock(), "Disk write/read proof absent");
        checks[8] = proof(result.vsock(), result.booted(), "Vsock proof absent");
        writeReport("uavf-avf-compatibility-console.txt", probe.consoleTail());
        return new Evidence(result.status(), result.details(), result.report(), result.guestMemoryBytes());
    }

    private void save(String id, String[] checks, Status outcome, String report) {
        String history = preferences.getString("compatibility_history_v1", "");
        if (history.isEmpty()) history = migratePreviousRun();
        history = CompatibilityHistory.append(history, new CompatibilityHistory.Run(Instant.now(), id, outcome.name(), report));
        var saved = preferences.edit().putString("compatibility_checked_build", Build.FINGERPRINT)
                .putInt("compatibility_check_schema", SCHEMA)
                .putString("compatibility_avf_status", outcome.name())
                .putString("compatibility_run_id", id)
                .putString("compatibility_history_v1", history)
                .putString("compatibility_execution_report", report);
        for (int index = 0; index < checks.length; index++) saved.putString("compatibility_result_" + index, checks[index]);
        saved.apply();
    }

    private String migratePreviousRun() {
        String id = preferences.getString("compatibility_run_id", "");
        String report = preferences.getString("compatibility_execution_report", "");
        if (!id.matches("[0-9a-f]{32}") || report.isBlank()) return "";
        return CompatibilityHistory.append("", new CompatibilityHistory.Run(null, id,
                preferences.getString("compatibility_avf_status", Status.INSUFFICIENT_DATA.name()), report));
    }

    private static String formatReport(String[] checks, Evidence evidence) {
        StringBuilder report = new StringBuilder("U-AVF local compatibility\n");
        report.append("DEVICE=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\nANDROID_SDK=").append(Build.VERSION.SDK_INT).append('\n');
        for (int index = 0; index < checks.length; index++) {
            report.append(UiLanguage.english(FigmaCompatibilityView.CHECKS[index])).append(" = ")
                    .append(checks[index]).append('\n');
        }
        if (evidence.report().isEmpty()) {
            report.append("AVF_STATUS=").append(evidence.status()).append("\nDETAILS=").append(evidence.details()).append('\n');
        } else report.append(evidence.report());
        return report.append("UBUNTU_WINDOWS_MAVK=SEPARATE_CHECKS\n").toString();
    }

    private void writeReport(String filename, String content) {
        File directory = context.getExternalFilesDir(null);
        if (directory == null) return;
        try {
            Files.writeString(new File(directory, filename).toPath(), content);
        } catch (IOException error) {
            Log.w("UAVF-Compat", "Could not save " + filename, error);
        }
    }

    private static void setCheck(String[] checks, BiConsumer<Integer, String> progress, int index, String result) {
        checks[index] = result;
        progress.accept(index, result);
    }

    private static String proof(boolean passed, boolean attempted, String failure) {
        if (passed) return "Passed";
        return attempted ? "Failed · " + failure : "Not checked";
    }

    private static Evidence unavailable(String reason) {
        return new Evidence(Status.INSUFFICIENT_DATA, reason, "", 0);
    }
}
