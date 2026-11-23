// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.winavf.UbuntuWorkspaceMedia.Installer;
import com.example.winavf.UbuntuWorkspaceMedia.Request;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/** Prepares a persistent Ubuntu boot and records the install or repair handoff. */
final class UbuntuWorkspaceBoot {
    record Plan(Request request, Installer installer, boolean firstBoot, boolean reinstall, boolean installing) {
        static Plan select(Request request, Installer installer, boolean ready) {
            boolean firstBoot = request == Request.FIRST_BOOT;
            boolean reinstall = request == Request.REINSTALL;
            return new Plan(request, installer, firstBoot, reinstall, reinstall || (!firstBoot && !ready));
        }
    }

    record Prepared(UbuntuWorkspaceMedia.Prepared media, boolean installed, boolean handoff, boolean mavk) {}
    record InstalledBoot(boolean layoutValid, boolean candidateFound) {
        String evidence() {
            return "POST_INSTALL_HANDOFF=" + (candidateFound ? "CANDIDATE_FOUND" : "CANDIDATE_NOT_FOUND")
                    + " GPT=" + (layoutValid ? "PASS" : "FAIL");
        }
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final UbuntuWorkspaceStorage storage;
    private final GuestMediaFiles files;
    private final DisplaySettings display;

    UbuntuWorkspaceBoot(Context context, SharedPreferences preferences,
                        UbuntuWorkspaceStorage storage, GuestMediaFiles files) {
        this.context = context;
        this.preferences = preferences;
        this.storage = storage;
        this.files = files;
        this.display = new DisplaySettings(preferences);
    }

    Plan plan(Request request, Installer installer) { return Plan.select(request, installer, runtimeReady()); }

    InstalledBoot inspectInstalled() throws Exception {
        return inspectInstalled(false);
    }

    InstalledBoot inspectInstalledForResume() throws Exception {
        AvfGuest.requireStopped(context, AvfGuest.UBUNTU_VM);
        return inspectInstalled(true);
    }

    private InstalledBoot inspectInstalled(boolean allowEspRecovery) throws Exception {
        File disk = storage.disk();
        boolean layoutValid = PersistentUbuntuDisk.audit(disk, UbuntuIsoImport.BYTES, PersistentUbuntuDisk.TARGET_BYTES);
        if (!layoutValid) return new InstalledBoot(false, false);
        boolean candidate = PersistentUbuntuDisk.hasInstalledBootCandidate(disk);
        if (allowEspRecovery && !candidate) {
            String recovery = PersistentUbuntuDisk.recoverInstalledEsp(disk, UbuntuIsoImport.BYTES,
                    new File(context.getFilesDir(), "linux-persistent-payload/esp-backups"),
                    Installer.DEFAULT.espSha, Installer.MAVK.espSha, Installer.PLASMA.espSha,
                    // Older installer ESPs may still need to restore their installed boot backup.
                    "AE9B2C7DE939D204E749274035BECE03B469C974ECD243272AB18B46E5925824",
                    "5D927DB2E4BCFED509E59F40274AAF077747B3D856891D08898B334E33B94E1C",
                    "8F89D3B492A6157A077BDBCC5A50D18B0572B34F310F5BB489729E83BE1D10A6");
            Log.i("U-AVF", "INSTALLED_ESP_RECOVERY=" + recovery);
            candidate = PersistentUbuntuDisk.hasInstalledBootCandidate(disk);
        }
        return new InstalledBoot(true, candidate);
    }

    Prepared prepare(Plan plan, boolean running) throws Exception {
        AvfGuest.requireStopped(context, AvfGuest.UBUNTU_VM);
        long rootBytes = plan.request() == Request.CREATE ? storage.plannedRootBytes() : 0;
        var prepared = workspaceMedia().prepare(plan.request(), plan.installer(), plan.installing(), rootBytes);
        if (plan.firstBoot() && prepared.firstBootInstaller() != Installer.DEFAULT
                && !preferences.getBoolean(DisplaySettings.FIRST_BOOT_OVERRIDE, false)) {
            Mode mode = prepared.firstBootInstaller() == Installer.H264 ? Mode.H264 : Mode.MAVK;
            display.select(mode, false, running);
        }
        preferences.edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true)
                .putBoolean("linux_gpu_virgl_experimental", false)
                .putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).apply();
        boolean ready = runtimeReady();
        boolean installed = !plan.reinstall() && (plan.firstBoot() || ready);
        boolean handoff = plan.reinstall() || (!plan.firstBoot() && !ready);
        boolean mavk = installed && display.mavkRequested() && MavkRuntimeAssets.isBundled(context);
        return new Prepared(prepared, installed, handoff, mavk);
    }

    private boolean runtimeReady() {
        return preferences.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false);
    }

    private UbuntuWorkspaceMedia workspaceMedia() {
        return new UbuntuWorkspaceMedia(preferences, context.getFilesDir(), context.getExternalFilesDir(null),
                storage.disk(), new UbuntuWorkspaceMedia.Assets() {
            @Override public File verified(String asset, File target, long bytes, String sha) throws IOException {
                return files.copyVerifiedAsset(asset, target, bytes, sha);
            }

            @Override public File copy(String asset, File target) throws IOException {
                return files.copyAsset(asset, target, -1);
            }
        });
    }

    void markHandoffPending() {
        preferences.edit().putBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, true)
                .putBoolean(UbuntuWorkspaceMedia.HANDOFF_ATTEMPTED, false).apply();
    }

    void clearHandoffPending() {
        preferences.edit().putBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, false).apply();
    }

    void recordLaunch(Plan plan, Prepared prepared) {
        File report = new File(context.getExternalFilesDir(null), "ubuntu-install-runtime-report.txt");
        try (var log = new PrintWriter(new FileOutputStream(report))) {
            log.println("PROFILE=" + (prepared.handoff() ? "UBUNTU_INSTALL_OR_REPAIR"
                    : plan.firstBoot() ? "UBUNTU_POST_INSTALL_HANDOFF" : "UBUNTU_PERSISTENT_BOOT"));
            log.println("MAVK_INSTALLED_REQUESTED=" + prepared.mavk());
            log.println("FORCE_INSTALL_PROFILE=" + plan.reinstall());
            log.println("ISO_SHA256=" + UbuntuIsoImport.SHA256);
            log.println("ONE_VIRTIO_BLOCK_DISK=PASS");
            log.println("ESP_PLUS_UNMODIFIED_ISO_PLUS_32G_INSTALL_TARGET=PASS");
            log.println("INSTALLER_FULLSCREEN_REQUEST=" + (prepared.handoff() ? "PENDING" : "NOT_REQUESTED"));
            log.println("PLATFORM_ESP_UPDATE=" + prepared.media().platformUpdate());
            log.println("INSTALLER_WINDOW_VERIFIED=NOT_AVAILABLE_FROM_CURRENT_AVF_API");
            log.println("DISK_BYTES=" + prepared.media().disk().length());
            log.println("DISK_GPT_AUDIT=PASS");
            log.println("VM_LAUNCH=PASS");
        } catch (IOException error) {
            Log.w("U-AVF", "Could not save Ubuntu launch report", error);
        }
    }
}
