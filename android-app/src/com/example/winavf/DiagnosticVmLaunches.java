// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.util.Log;
import com.example.winavf.workspace.DisplaySettings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;

/** Starts the retained diagnostic fixtures through the existing AVF runtime. */
final class DiagnosticVmLaunches {
    enum WindowsProbe { BOOT, UEFI_KEYBOARD, UEFI_SERIAL_ESCAPE, WINPE_HELLO }

    interface Host {
        boolean skipValidation();
        void resetFrames(boolean hide);
        int observedFrames();
        void applyStagedPatch(File staging, File disk) throws Exception;
        void attachAndRun(Object vm, String consoleLog) throws Exception;
        void probeUbuntu(Object vm);
        void attachUbuntuDisplay(Object vm);
        void show(String message);
    }

    private static final String GNOME = "winavf-ubuntu-gnome-24045-v21";
    private static final String GNOME_QUEUE_ONE = GNOME + "-q1";
    private final Activity activity;
    private final GuestMediaFiles files;
    private final WindowsBootProbes probes;
    private final DiagnosticSerialCapture serial;
    private final Host host;

    DiagnosticVmLaunches(Activity activity, GuestMediaFiles files, WindowsBootProbes probes,
                         DiagnosticSerialCapture serial, Host host) {
        this.activity = activity;
        this.files = files;
        this.probes = probes;
        this.serial = serial;
        this.host = host;
    }

    void startWindows(WindowsProbe probe) {
        try {
            String name = DiagnosticVmConfig.WINDOWS_VM;
            AvfGuest.requireStopped(activity, name);
            host.resetFrames(false);
            serial.resetMarkers();
            host.show("Preparing app-private kernel-style loader…");
            var media = WindowsDiagnosticMedia.prepare(activity, files, host.skipValidation(), host::show);
            host.applyStagedPatch(media.staging(), media.disk());
            host.show("Using the preserved Windows Boot Manager milestone medium only…");
            host.show("Creating custom AVF VM through Android API…");
            Object config = DiagnosticVmConfig.build(activity, media.kernel(), media.disk(),
                    probe == WindowsProbe.UEFI_KEYBOARD);
            Object vm = AvfGuest.replaceStopped(activity, name, config);
            host.attachAndRun(vm, "serial.log");
            switch (probe) {
                case UEFI_KEYBOARD -> probes.startKeyboardEscape(vm);
                case UEFI_SERIAL_ESCAPE -> probes.startSerialEscape(vm, serial::bootOptionsPrompt,
                        serial::escapeAcknowledged, host::observedFrames);
                case WINPE_HELLO -> probes.startHello(vm);
                case BOOT -> {}
            }
            probes.startMarkerReport(media.disk());
            host.show("VM launched. Waiting for kernel-first serial output…");
        } catch (Exception error) {
            host.show("FAILED: " + IoUtil.rootMessage(error));
        }
    }

    void startGnome(boolean queueDepthOne, boolean showDisplay) {
        try {
            AvfGuest.requireStopped(activity, GNOME);
            AvfGuest.requireStopped(activity, GNOME_QUEUE_ONE);
            host.resetFrames(true);
            var media = ubuntuMedia().gnome();
            ImagePatch.apply(media.patch(), media.disk(), false);
            String name = queueDepthOne ? GNOME_QUEUE_ONE : GNOME;
            String parameters = queueDepthOne ? "mem=2G virtio_blk.queue_depth=1" : "mem=2G";
            Object config = DiagnosticVmConfig.build(activity, name, media.kernel(), media.disk(), false, parameters);
            Object vm = AvfGuest.replaceStopped(activity, name, config);
            host.attachAndRun(vm, queueDepthOne ? "ubuntu-gnome-q1-serial.log" : "ubuntu-gnome-serial.log");
            host.probeUbuntu(vm);
            if (showDisplay) host.attachUbuntuDisplay(vm);
            host.show(queueDepthOne ? "Ubuntu GNOME queue_depth=1 diagnostic launched; capturing its complete serial log."
                    : "Ubuntu GNOME live profile launched; capturing its complete serial log.");
        } catch (Exception error) {
            host.show("Ubuntu GNOME launch failed: " + IoUtil.rootMessage(error));
        }
    }

    void startRecovery() {
        try {
            String name = "winavf-ubuntu-gnome-v12-recovery";
            AvfGuest.requireStopped(activity, name);
            var media = ubuntuMedia().recovery();
            Object config = DiagnosticVmConfig.build(activity, name, media.kernel(), media.disk(), false, "mem=2G");
            Log.e("WinAVF", "V12_VM_CREATE_BEGIN");
            Object vm = AvfGuest.replaceStopped(activity, name, config);
            host.attachAndRun(vm, "v12-recovery-serial.log");
            host.show("V12_RECOVERY launched; capturing serial output.");
        } catch (Exception error) {
            String message = "V12_RECOVERY launch failed: " + IoUtil.rootMessage(error);
            Log.e("WinAVF", message, error);
            host.show(message);
        }
    }

    void startGeneric(UbuntuGenericBootMedia.Layout layout) {
        var staging = new UbuntuGenericBootMedia(activity, files, host::show);
        try {
            var prepared = staging.prepare(layout, host.skipValidation());
            var options = new UbuntuVmConfig.Options(UbuntuVmConfig.GENERIC_VM, 4L << 30,
                    "CPU_TOPOLOGY_ONE_CPU", 1280, 800, DisplaySettings.DEFAULT_REFRESH_HZ, false, false);
            Object config = UbuntuVmConfig.build(activity, activity.getPreferences(0), activity.getIntent(),
                    prepared.media(), options);
            Object vm = AvfGuest.replaceStopped(activity, options.name(), config);
            host.attachAndRun(vm, "generic-ubuntu-serial.log");
            staging.reportLaunch(prepared);
            host.show("GENERIC_UBUNTU launched with untouched stock ISO.");
        } catch (Exception failure) {
            String reason = IoUtil.rootMessage(failure);
            staging.reportFailure(reason);
            host.show("GENERIC_UBUNTU launch failed: " + reason);
        }
    }

    void cleanupGnome() {
        File report = new File(activity.getExternalFilesDir(null), "ubuntu-gnome-cleanup-report.txt");
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            AvfGuest.requireStopped(activity, GNOME_QUEUE_ONE);
            Object guest = AvfGuest.find(activity, GNOME);
            if (guest != null && !"STOPPED".equals(AvfGuest.state(guest))) AvfGuest.stop(guest);
            AvfGuest.removeStopped(activity, GNOME);
            AvfGuest.removeStopped(activity, GNOME_QUEUE_ONE);
            File staging = new File(activity.getFilesDir(), "ubuntu-gnome-payload");
            for (String name : new String[]{"ubuntu-gnome-24.04.5-v21-vsock-listener.img",
                    "u-boot-wrapper-v24.Image", "ubuntu-gnome-v21-vsock-firmware.patch"}) {
                Files.deleteIfExists(new File(staging, name).toPath());
            }
            Files.deleteIfExists(staging.toPath());
            log.println("RESULT=PASS");
            log.println("SCOPE=ubuntu-gnome-payload-only");
            log.println("WINDOWS_PAYLOAD_UNTOUCHED=true");
            host.show("Disposed the separate Ubuntu GNOME profile; Windows media was untouched.");
        } catch (Exception error) {
            String reason = IoUtil.rootMessage(error);
            try (var log = new PrintWriter(new FileOutputStream(report, false))) {
                log.println("RESULT=FAIL");
                log.println("ERROR=" + reason);
            } catch (Exception writeError) {
                Log.w("U-AVF", "Could not write GNOME cleanup report", writeError);
            }
            host.show("Ubuntu GNOME cleanup failed: " + reason);
        }
    }

    private UbuntuDiagnosticMedia ubuntuMedia() {
        return new UbuntuDiagnosticMedia(activity, files, host.skipValidation(), host::show);
    }
}
