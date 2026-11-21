// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import com.example.winavf.UbuntuLiveSessionMedia.Profile;
import com.example.winavf.UbuntuWorkspaceMedia.Installer;
import com.example.winavf.UbuntuWorkspaceMedia.Request;
import com.example.winavf.runtime.RuntimeTransition;
import com.example.winavf.runtime.VmLifecycle;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Prepares and boots installed and temporary Ubuntu sessions with the shared AVF configuration. */
final class UbuntuLaunchFlow {
    interface Host {
        boolean running();
        boolean mayBeRunning();
        void reconnect();
        void beginBoot();
        void resetFrame();
        void showBootStatus(String message);
        void updateBootText(String message);
        void showLiveFailure();
        void showHandoffFailure(String message);
        Object launch(Object configuration, String consoleLog) throws Exception;
        void attachDisplay(Object vm);
        void requestRepair();
        void show(String message);
    }

    private final Activity activity;
    private final SharedPreferences preferences;
    private final VmLifecycle lifecycle;
    private final RuntimeTransition transition;
    private final UbuntuWorkspaceBoot workspace;
    private final UbuntuLiveSessionMedia live;
    private final DisplayConnection displayConnection;
    private final Host host;
    private final DisplaySettings displaySettings;
    private volatile int guestRevision;

    UbuntuLaunchFlow(Activity activity, VmLifecycle lifecycle, RuntimeTransition transition,
                     UbuntuWorkspaceStorage storage, GuestMediaFiles files, DisplayConnection displayConnection, Host host) {
        this.activity = activity;
        this.preferences = activity.getPreferences(0);
        this.lifecycle = lifecycle;
        this.transition = transition;
        this.workspace = new UbuntuWorkspaceBoot(activity, preferences, storage, files);
        this.live = new UbuntuLiveSessionMedia(activity, preferences, files);
        this.displayConnection = displayConnection;
        this.host = host;
        this.displaySettings = new DisplaySettings(preferences);
    }

    void resumeOrRepair() {
        if (host.running() || !lifecycle.beginStorageMutation()) {
            host.show("Stop the active VM before inspecting the installed Ubuntu workspace.");
            return;
        }
        boolean installed;
        try {
            installed = workspace.inspectInstalledForResume().candidateFound();
        } catch (Exception failure) {
            host.show("Could not verify the installed Ubuntu handoff: " + IoUtil.rootMessage(failure));
            return;
        } finally {
            lifecycle.endStorageMutation();
        }
        if (installed) {
            host.show("Found the completed Ubuntu boot handoff; starting the persistent installation.");
            startPersistent(Request.FIRST_BOOT, Installer.DEFAULT);
        } else {
            activity.runOnUiThread(host::requestRepair);
        }
    }

    void startPersistent(Request request, Installer installer) {
        if (VmRuntimeService.runningVm() != null) {
            host.reconnect();
            return;
        }
        if (host.running()) {
            host.show("Stop the running VM before opening the Ubuntu installer or installed system.");
            return;
        }
        if (!lifecycle.beginStart()) return;
        var plan = workspace.plan(request, installer);
        if (plan.installing()) lifecycle.install(VmLifecycle.Install.PREPARING);
        else if (plan.firstBoot()) lifecycle.install(VmLifecycle.Install.FIRST_BOOT);
        host.beginBoot();
        host.resetFrame();
        try {
            startPreparedWorkspace(plan);
        } catch (Exception failure) {
            if (plan.installing() || plan.firstBoot()) lifecycle.install(VmLifecycle.Install.FAILED);
            failBoot("Ubuntu could not start: ", failure);
        }
    }

    private void startPreparedWorkspace(UbuntuWorkspaceBoot.Plan plan) throws Exception {
        var prepared = workspace.prepare(plan, host.running());
        int refreshHz = requestedRefreshHz();
        var media = prepared.media();
        Object configuration = configuration(media.kernel(), media.disk(), false, true, prepared.mavk(), refreshHz);
        recordDisplayBoot(prepared.installed(), false, true, prepared.mavk(), refreshHz);
        if (prepared.handoff()) workspace.markHandoffPending();
        guestRevision = 0;
        Object vm = host.launch(configuration, "ubuntu-persistent-serial.log");
        probe(vm, prepared.installed());
        host.attachDisplay(vm);
        workspace.recordLaunch(plan, prepared);
        if (prepared.handoff()) scheduleInstaller(vm);
        host.showBootStatus(prepared.handoff() ? "Starting Ubuntu Setup…" : "Starting installed Ubuntu…");
        host.show(prepared.handoff()
                ? "Starting Ubuntu install / repair profile; Setup will open only after the Live profile is confirmed."
                : "Starting the installed Ubuntu system.");
    }

    void startLive(Profile profile, boolean useProductDefaults) {
        if (host.running() || !lifecycle.beginStart()) {
            host.show("The workspace is already starting, running or stopping.");
            return;
        }
        host.beginBoot();
        host.resetFrame();
        host.showBootStatus("Preparing Ubuntu live session…");
        try {
            startPreparedLive(profile, useProductDefaults);
        } catch (Exception failure) {
            String reason = setBootFailure(failure);
            activity.runOnUiThread(host::showLiveFailure);
            host.show("Ubuntu Live could not start: " + reason);
        }
    }

    private void startPreparedLive(Profile profile, boolean useProductDefaults) throws Exception {
        if (host.mayBeRunning()) throw new IllegalStateException("Stop the VM before preparing Live media");
        boolean skipDigest = preferences.getBoolean("skip_disk_validation", false);
        if (skipDigest) host.show("Disk verification skipped for official Ubuntu ISO.");
        var prepared = live.prepare(profile, useProductDefaults, displaySettings, skipDigest);
        int refreshHz = requestedRefreshHz();
        boolean mavk = prepared.mavk() != null;
        Object configuration = configuration(prepared.kernel(), prepared.disk(),
                profile.disableUdmabuf, profile.hostVirgl, mavk, refreshHz);
        prepared.invalidateReadyMarker();
        recordDisplayBoot(false, true, profile.supportsEncodedDisplay(), mavk, refreshHz);
        Object vm = host.launch(configuration, "frame-bridge-test-serial.log");
        probe(vm, false);
        host.attachDisplay(vm);
        live.recordBoot(profile, prepared);
        host.show("Ubuntu Live started; waiting for the guest display.");
    }

    private Object configuration(File kernel, File disk, boolean disableUdmabuf,
                                 boolean hostVirgl, boolean mavk, int refreshHz) throws Exception {
        var resources = new WorkspaceConfig(preferences.getInt("linux_ram_gib", 4),
                preferences.getBoolean("linux_one_cpu", false), displaySettings.resolution(Mode.FALLBACK));
        var viewport = resources;
        if (mavk || displaySettings.encodedEnabled() && displaySettings.hasResolution(Mode.H264)) {
            viewport = new WorkspaceConfig(resources.ramGiB, resources.oneCpu,
                    displaySettings.resolution(mavk ? Mode.MAVK : Mode.H264));
        }
        var media = new UbuntuVmConfig.Media(kernel, disk, null, null);
        var options = new UbuntuVmConfig.Options(AvfGuest.UBUNTU_VM, resources.memoryBytes(), resources.topology(),
                viewport.width, viewport.height, refreshHz, disableUdmabuf, hostVirgl);
        Object config = UbuntuVmConfig.build(activity, preferences, activity.getIntent(), media, options);
        appendReport("EFFECTIVE_LAUNCH_CONFIG RAM_BYTES=" + resources.memoryBytes() + " CPU=" + resources.topology()
                + " DISPLAY=" + resources.resolution() + " AUTO_BALLOON=false");
        return config;
    }

    private void recordDisplayBoot(boolean installed, boolean temporary, boolean encoded, boolean mavk, int refreshHz) {
        preferences.edit().putBoolean("linux_current_boot_installed", installed)
                .putBoolean("linux_current_boot_temporary", temporary)
                .putBoolean("linux_current_boot_encoded", encoded).putBoolean("linux_current_boot_mavk", mavk)
                .putInt(DisplaySettings.RUNNING_REFRESH_HZ, refreshHz)
                .putBoolean("linux_current_boot_receive_encoded", displaySettings.encodedEnabled())
                .putString("linux_current_boot_h264_resolution", displaySettings.resolution(Mode.H264)).apply();
    }

    private int requestedRefreshHz() {
        int fallback = DisplaySettings.DEFAULT_REFRESH_HZ;
        var intent = activity.getIntent();
        return DisplaySettings.requireDiagnosticRefresh(intent == null ? fallback
                : intent.getIntExtra("encoded_guest_refresh_hz", fallback));
    }

    private void failBoot(String prefix, Exception failure) { host.show(prefix + setBootFailure(failure)); }

    private String setBootFailure(Exception failure) {
        String reason = IoUtil.rootMessage(failure);
        lifecycle.failStart(reason);
        transition.failure(reason, SystemClock.elapsedRealtime());
        return reason;
    }

    void probe(Object vm, boolean installed) {
        UbuntuReadinessProbe.start(vm, activity.getExternalFilesDir(null), installed,
                () -> !activity.isDestroyed() && lifecycle.owns(vm) && lifecycle.busy(),
                revision -> guestRevision = revision, this::markReady, host::show);
    }

    private void markReady() {
        preferences.edit().putBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, true)
                .remove(DisplaySettings.FIRST_BOOT_MODE).remove(DisplaySettings.FIRST_BOOT_OVERRIDE).apply();
        lifecycle.install(VmLifecycle.Install.READY);
    }

    private void scheduleInstaller(Object vm) {
        new UbuntuInstallerAutostart(displayConnection, () -> host.mayBeRunning() && lifecycle.owns(vm),
                () -> guestRevision, () -> lifecycle.install(VmLifecycle.Install.INSTALLING), this::appendReport,
                () -> activity.runOnUiThread(() -> host.updateBootText("Ubuntu Setup is opening full-screen…"))).start();
    }

    void finishHandoff() {
        try {
            var installed = workspace.inspectInstalled();
            appendReport(installed.evidence());
            if (!installed.candidateFound()) {
                failHandoff("No installed Ubuntu boot marker was found; the persistent disk was left untouched.");
                return;
            }
            long deadline = SystemClock.elapsedRealtime() + 15_000;
            while (host.running() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(300);
            workspace.clearHandoffPending();
            activity.runOnUiThread(() -> host.updateBootText("Starting installed Ubuntu for the first time…"));
            startPersistent(Request.FIRST_BOOT, Installer.DEFAULT);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            String reason = IoUtil.rootMessage(error);
            appendReport("POST_INSTALL_HANDOFF=FAILED " + reason);
            failHandoff("Could not start the installed Ubuntu handoff: " + reason);
        }
    }

    private void failHandoff(String message) {
        lifecycle.install(VmLifecycle.Install.FAILED);
        workspace.clearHandoffPending();
        activity.runOnUiThread(() -> host.showHandoffFailure(message));
    }

    private void appendReport(String message) {
        File report = new File(activity.getExternalFilesDir(null), "ubuntu-install-runtime-report.txt");
        try (var output = new FileOutputStream(report, true)) {
            output.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            Log.w("U-AVF", "Cannot append installer autostart report", error);
        }
    }
}
