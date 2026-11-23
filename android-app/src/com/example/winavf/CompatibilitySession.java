// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.diagnostics.CompatibilityChecks;
import com.example.winavf.diagnostics.CompatibilityStatus;
import com.example.winavf.ui.CompatibilityScreen;
import com.example.winavf.ui.ProductDialog;
import com.example.winavf.ui.UiLanguage;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.example.winavf.runtime.VmLifecycle;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.json.JSONObject;

/** Owns a local compatibility run, its visible progress and its optional report. */
final class CompatibilitySession {
    private static final String SHARING = "compatibility_share_results";
    private final Activity activity;
    private final Handler ui;
    private final SharedPreferences preferences;
    private final VmLifecycle lifecycle;
    private final RuntimeDiagnostics diagnostics;
    private final BooleanSupplier vmRunning;
    private final BooleanSupplier hasPermissions;
    private final Supplier<String> appVersion;
    private final Runnable update;
    private final Runnable permissionFailure;

    private final String[] checks = new String[CompatibilityChecks.LABELS.size()];
    private CompatibilityStatus status = CompatibilityStatus.INSUFFICIENT_DATA;
    private boolean running;
    private int completed;
    private static final String[] REPORT_CHECKS = {"arm64", "avf", "permissions", "platform", "created", "boot", "memory", "storage", "vsock"};

    CompatibilitySession(Activity activity, Handler ui, VmLifecycle lifecycle, RuntimeDiagnostics diagnostics,
                         BooleanSupplier vmRunning, BooleanSupplier hasPermissions, Supplier<String> appVersion,
                         Runnable update, Runnable permissionFailure) {
        this.activity = activity;
        this.ui = ui;
        this.preferences = activity.getPreferences(0);
        this.lifecycle = lifecycle;
        this.diagnostics = diagnostics;
        this.vmRunning = vmRunning;
        this.hasPermissions = hasPermissions;
        this.appVersion = appVersion;
        this.update = update;
        this.permissionFailure = permissionFailure;
        Arrays.fill(checks, "Not checked");
    }

    boolean running() { return running; }
    CompatibilityStatus status() { return status; }
    String check(int index) { return checks[index]; }
    boolean sharingDecided() { return preferences.contains(SHARING); }
    boolean sharing() { return preferences.getBoolean(SHARING, false); }
    void setSharing(boolean share) { preferences.edit().putBoolean(SHARING, share).apply(); }

    void bind(CompatibilityScreen view) { view.results(running, completed, checks); }

    void confirmFirstRun() {
        ProductDialog.choosePrimary(activity, "Статистика совместимости",
                "Разрешите отправить результат этого теста в Firebase. Это поможет нам расширить список поддерживаемых устройств. Передаются модель, конфигурация и результаты проверки AVF.",
                new String[]{"Разрешить и начать", "Продолжить без отправки"}, 0, true, index -> {
                    setSharing(index == 0);
                    start();
                });
    }

    void start() {
        if (running) return;
        if (vmRunning.getAsBoolean() || !lifecycle.beginCompatibilityProbe()) {
            ProductDialog.notice(activity, "Проверка устройства", "Сначала завершите текущий сеанс VM.");
            return;
        }
        if (!hasPermissions.getAsBoolean()) {
            preferences.edit().putBoolean(CompatibilityAudit.WAITING_FOR_PERMISSIONS, true).apply();
        }
        prepareProgress();
        boolean shareRun = sharing();
        diagnostics.append("Проверка", "Событие", "Запущена проверка требований устройства");
        update.run();
        new Thread(() -> runProbe(shareRun), "U-AVF-readiness-check").start();
    }

    void restore() {
        readResult();
        if (VmLifecycle.compatibilityProbeActive()) {
            prepareProgress();
            ui.postDelayed(this::observeRestoredRun, 350);
        }
    }

    boolean waitingForPermissions() {
        return preferences.getBoolean(CompatibilityAudit.WAITING_FOR_PERMISSIONS,
                CompatibilityAudit.PERMISSION_REQUIRED.equals(checks[CompatibilityChecks.AVF_PERMISSIONS]));
    }

    void clearPermissionRetry() {
        preferences.edit().putBoolean(CompatibilityAudit.WAITING_FOR_PERMISSIONS, false).apply();
    }

    private void prepareProgress() {
        running = true;
        completed = 0;
        status = CompatibilityStatus.INSUFFICIENT_DATA;
        Arrays.fill(checks, "Waiting");
    }

    private void runProbe(boolean shareRun) {
        try {
            var result = new CompatibilityAudit(activity, preferences, hasPermissions).run(this::progress);
            if (result.permissionGranted() && shareRun && sharing()) {
                new Thread(() -> upload(result), "U-AVF-compatibility-upload").start();
            }
        } finally {
            VmLifecycle.endCompatibilityProbe();
            ui.post(this::finish);
        }
    }

    private void progress(int index, String result) {
        ui.post(() -> {
            if (activity.isDestroyed() || !running) return;
            checks[index] = result;
            completed = index + 1;
            update.run();
        });
    }

    private void observeRestoredRun() {
        if (activity.isDestroyed()) return;
        if (VmLifecycle.compatibilityProbeActive()) ui.postDelayed(this::observeRestoredRun, 350);
        else finish();
    }

    private void finish() {
        if (activity.isDestroyed()) return;
        running = false;
        readResult();
        diagnostics.append("Check", "Event", "Compatibility result: " + status);
        update.run();
        if (waitingForPermissions()) permissionFailure.run();
    }

    private void readResult() {
        boolean current = preferences.getInt("compatibility_check_schema", 0) == CompatibilityAudit.SCHEMA
                && Build.FINGERPRINT.equals(preferences.getString("compatibility_checked_build", ""));
        status = CompatibilityStatus.INSUFFICIENT_DATA;
        if (current) {
            try {
                status = CompatibilityStatus.valueOf(
                        preferences.getString("compatibility_avf_status", "INSUFFICIENT_DATA"));
            } catch (IllegalArgumentException ignored) {}
        }
        for (int index = 0; index < checks.length; index++) {
            checks[index] = current ? preferences.getString("compatibility_result_" + index, "Not checked") : "Not checked";
        }
        completed = current ? checks.length : 0;
    }

    private void upload(CompatibilityAudit.Result result) {
        if (!sharing()) return;
        String message;
        try {
            var memory = new ActivityManager.MemoryInfo();
            activity.getSystemService(ActivityManager.class).getMemoryInfo(memory);
            var group = CompatibilityUpload.Group.forDevice(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT,
                    memory.totalMem, Runtime.getRuntime().availableProcessors());
            var fields = reportFields(result, group);
            if (!sharing()) return;
            message = CompatibilityUpload.send(activity.getApplicationContext(), true, result.id(), group, fields);
        } catch (Exception error) {
            message = "Could not send the compatibility report";
            Log.w("UAVF-Compat", "Compatibility submission failed", error);
        }
        String notification = message;
        ui.post(() -> {
            if (!activity.isDestroyed()) Toast.makeText(activity, UiLanguage.text(notification), Toast.LENGTH_LONG).show();
            Log.i("UAVF-Compat", notification);
        });
    }

    private JSONObject reportFields(CompatibilityAudit.Result result, CompatibilityUpload.Group group) throws Exception {
        var fields = new JSONObject().put("schemaVersion", CompatibilityUpload.integer(2))
                .put("consent", new JSONObject().put("booleanValue", true))
                .put("appVersion", CompatibilityUpload.value(bounded(appVersion.get(), 64)))
                .put("androidRelease", CompatibilityUpload.value(bounded(Build.VERSION.RELEASE, 32)))
                .put("status", CompatibilityUpload.value(result.status().name()));
        var reportChecks = new JSONObject();
        for (int index = 0; index < REPORT_CHECKS.length; index++) {
            String check = result.checks()[index];
            reportChecks.put(REPORT_CHECKS[index], CompatibilityUpload.value(
                    check.startsWith("Passed") ? "pass" : check.startsWith("Failed") ? "fail" : "not_tested"));
        }
        return fields.put("checks", CompatibilityUpload.map(reportChecks))
                .put("device", CompatibilityUpload.map(group.deviceFields()))
                .put("avf", CompatibilityUpload.map(new JSONObject()
                        .put("guestMemoryMiB", CompatibilityUpload.integer(result.guestMemoryBytes() / (1L << 20)))));
    }

    private static String bounded(String value, int length) {
        return value == null ? "" : value.substring(0, Math.min(length, value.length()));
    }
}
