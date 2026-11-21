// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.winavf.storage.UbuntuLiveMedia;
import com.example.winavf.workspace.DisplaySettings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/** Selects and prepares the disposable Ubuntu workspace boot media. */
final class UbuntuLiveSessionMedia {
    enum Profile {
        VIRGL(false, false, true), VIRGL_LINUX_FIRST(false, false, true),
        GFXSTREAM(true, false, false), GFXSTREAM_NO_UDMABUF(true, true, false),
        GFXSTREAM_VIRGL(true, false, true);

        final boolean linuxFirstGpu;
        final boolean disableUdmabuf;
        final boolean hostVirgl;

        Profile(boolean linuxFirstGpu, boolean disableUdmabuf, boolean hostVirgl) {
            this.linuxFirstGpu = linuxFirstGpu;
            this.disableUdmabuf = disableUdmabuf;
            this.hostVirgl = hostVirgl;
        }

        boolean supportsEncodedDisplay() { return this == VIRGL_LINUX_FIRST; }
    }

    record Prepared(File kernel, File disk, File readyMarker, UbuntuLiveMedia.Profile layout,
                    MavkRuntimeAssets mavk, boolean encodedGeometry) {
        void invalidateReadyMarker() throws IOException {
            if (readyMarker.exists() && !readyMarker.delete()) {
                throw new IOException("Cannot invalidate temporary-media cache before launch");
            }
        }
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final GuestMediaFiles files;

    UbuntuLiveSessionMedia(Context context, SharedPreferences preferences, GuestMediaFiles files) {
        this.context = context;
        this.preferences = preferences;
        this.files = files;
    }

    Prepared prepare(Profile profile, boolean useProductDefaults, DisplaySettings display, boolean skipIsoDigest)
            throws Exception {
        AvfGuest.requireStopped(context, AvfGuest.UBUNTU_VM);
        if (useProductDefaults) selectProductRenderer();
        File source = new File(context.getExternalFilesDir(null), UbuntuIsoImport.NAME);
        if (!source.isFile()) throw new IllegalStateException("Choose the official Ubuntu ISO first.");
        File staging = new File(context.getFilesDir(), "frame-bridge-test-payload");
        if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Cannot create Ubuntu Live staging directory");

        MavkLiveMedia.restore(context, staging);
        boolean mavkRequested = profile.supportsEncodedDisplay() && display.mavkRequested();
        MavkRuntimeAssets mavk = mavkRequested ? MavkRuntimeAssets.load(context) : null;
        boolean encodedGeometry = profile.supportsEncodedDisplay() && !mavkRequested && display.encodedEnabled()
                && files.hasAsset(UbuntuLiveMedia.Profile.ENCODED.asset());
        var layout = UbuntuLiveMedia.Profile.select(
                preferences.getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false),
                preferences.getBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false),
                profile.linuxFirstGpu, profile.supportsEncodedDisplay(), encodedGeometry);
        var media = new UbuntuLiveMedia(context.getAssets()::open,
                () -> BundledPlatformAssets.sha256(UbuntuLiveMedia.previousVirglAsset()));
        File disk = media.prepare(source, staging, layout, skipIsoDigest);
        if (mavkRequested) MavkLiveMedia.enable(context, staging, disk, mavk);
        String kernelAsset = "u-boot-wrapper-v24.Image";
        File kernel = files.copyAsset(kernelAsset, new File(staging, kernelAsset), -1);
        return new Prepared(kernel, disk, new File(staging, "uavf-ubuntu-live-runtime.ready"),
                layout, mavk, encodedGeometry);
    }

    private void selectProductRenderer() {
        preferences.edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true)
                .putBoolean("linux_gpu_virgl_experimental", false)
                .putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false)
                .putBoolean("linux_gpu_gfxstream_system_blob_experimental", false).apply();
    }

    void recordBoot(Profile profile, Prepared prepared) {
        File report = new File(context.getExternalFilesDir(null), "frame-bridge-test-runtime-report.txt");
        try (var log = new PrintWriter(new FileOutputStream(report))) {
            log.println("PROFILE=LINUX_LIVE_FRAME_BRIDGE");
            log.println("ENCODED_GEOMETRY_LIVE=" + prepared.encodedGeometry());
            log.println("MAVK_LIVE_ENABLED=" + (prepared.mavk() != null));
            log.println("RENDERER_EXPERIMENT=" + profile.name());
            log.println("RUNTIME_DISK_EXPECTED_SHA256=" + prepared.layout().expectedDiskDigest());
            log.println("RUNTIME_DISK_BYTES=" + prepared.disk().length());
            log.println("PLATFORM_PREFIX_SHA256="
                    + (prepared.mavk() == null ? prepared.layout().prefixSha() : prepared.mavk().sha256));
            log.println("ONE_DISK_ESP_PLUS_STOCK_ISO_PARTITION=PASS");
            log.println("OFFICIAL_ISO_MUTATED=NO");
            log.println("PERSISTENT_GUEST_DISK=NO_LIVE_SESSION");
            log.println("VM_LAUNCH=PASS");
        } catch (IOException error) {
            Log.w("U-AVF", "Could not save Ubuntu Live launch report", error);
        }
    }
}
