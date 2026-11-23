// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.example.winavf.UbuntuLiveSessionMedia.Profile;
import com.example.winavf.runtime.VmLifecycle;
import java.util.zip.CRC32;

/** Routes the historical test intents in debuggable builds. */
final class DiagnosticCommands {
   interface Host {
      void showWelcome();
      boolean running();
      VmLifecycle.State lifecycleState();
      void selectUbuntu(boolean save);
      void startLive();
      void startLive(Profile profile);
      void stop();
      void rollback();
      void startDebugger(String token);
      void stopDebugger();
      Object ubuntuGuest();
      void attachDisplay(Object vm);
      void feedFrame(byte[] frame);
      void show(String message);
      /** Shows the failure card for one platform without starting a VM. QA only. */
      void previewBootFailure(String os, boolean windows);
   }

   private final Activity activity;
   private final DiagnosticVmLaunches launches;
   private final UbuntuBlockIoAudit blockIo;
   private final AvfDebugDiagnostics avf;
   private final ConsoleLoopbackProbe loopback;
   private final WindowsDiagnosticDiskReports diskReports;
   private final DisplayConnection connection;
   private final Host host;
   private EncodedDisplayProbe encodedDisplayProbe;
   private int encodedProbeGeneration;

   DiagnosticCommands(Activity activity, DiagnosticVmLaunches launches, UbuntuBlockIoAudit blockIo,
                      AvfDebugDiagnostics avf, ConsoleLoopbackProbe loopback, WindowsDiagnosticDiskReports diskReports,
                      DisplayConnection connection, Host host) {
      this.activity = activity;
      this.launches = launches;
      this.blockIo = blockIo;
      this.avf = avf;
      this.loopback = loopback;
      this.diskReports = diskReports;
      this.connection = connection;
      this.host = host;
   }

   void handle(Intent intent) {
      // Only debug builds expose the historical test actions.
      if ((activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
         intent.replaceExtras((Bundle) null);
         return;
      }
      if (debugAction(intent, "show_welcome")) {
         host.showWelcome();
         intent.removeExtra("show_welcome");
      }
      if (debugAction(intent, "encoded_display_probe")) this.openEncodedDisplayProof(intent);
      this.handleDebugBootActions(intent);
      this.handleDebugGraphicsActions(intent);
      this.handleDebugStorageAndGuestActions(intent);
   }

   private void handleDebugBootActions(Intent intent) {
      debugWorker(intent, "start", "WinAVF-start", () -> launches.startWindows(DiagnosticVmLaunches.WindowsProbe.BOOT));
      debugWorker(intent, "ubuntu_gnome", "WinAVF-ubuntu-gnome", () -> launches.startGnome(false, false));
      debugWorker(intent, "ubuntu_gnome_frame", "WinAVF-ubuntu-gnome-frame", () -> launches.startGnome(false, true));
      debugWorker(intent, "ubuntu_gnome_queue_depth_1", "WinAVF-ubuntu-gnome-q1", () -> launches.startGnome(true, false));
      if (debugAction(intent, "v12_recovery")) {
         Log.e("WinAVF", "V12_INTENT_RECEIVED");
         Log.e("WinAVF", "V12_PROFILE_SELECTED");
         new Thread(launches::startRecovery, "WinAVF-v12-recovery").start();
      }
      debugWorker(intent, "generic_ubuntu", "WinAVF-generic-ubuntu", () -> launches.startGeneric(UbuntuGenericBootMedia.Layout.SEPARATE_ISO));
      debugWorker(intent, "generic_ubuntu_no_iso", "WinAVF-generic-ubuntu-no-iso",
            () -> launches.startGeneric(UbuntuGenericBootMedia.Layout.PLATFORM_ONLY));
      debugWorker(intent, "generic_ubuntu_second_esp", "WinAVF-generic-ubuntu-second-esp",
            () -> launches.startGeneric(UbuntuGenericBootMedia.Layout.EXTRA_ESP));
      debugWorker(intent, "generic_ubuntu_combined", "WinAVF-generic-ubuntu-combined",
            () -> launches.startGeneric(UbuntuGenericBootMedia.Layout.COMBINED));
      if (debugAction(intent, "generic_ubuntu_frame_bridge_test")) {
         if (!host.running()) host.selectUbuntu(true);
         new Thread(host::startLive, "WinAVF-generic-frame-bridge-test").start();
      }
      if (debugAction(intent, "stop_frame_bridge_test")) {
         host.selectUbuntu(false);
         new Thread(host::stop, "WinAVF-stop-frame-bridge-test").start();
      }
   }

   private void handleDebugGraphicsActions(Intent intent) {
      if (debugAction(intent, "linux_gpu_virgl_gbm_test")) {
         this.startDebugVirgl(Profile.VIRGL, "WinAVF-linux-virgl-gbm-test");
      }
      if (debugAction(intent, "linux_gpu_virgl_gbm_linux_first_test")) {
         this.startDebugVirgl(Profile.VIRGL_LINUX_FIRST, "WinAVF-linux-virgl-gbm-linux-first-test");
      }
      debugWorker(intent, "linux_first_gpu_test", "WinAVF-linux-first-gpu-test",
            () -> host.startLive(Profile.GFXSTREAM));
      debugWorker(intent, "linux_first_gpu_no_udmabuf_test", "WinAVF-linux-first-gpu-no-udmabuf",
            () -> host.startLive(Profile.GFXSTREAM_NO_UDMABUF));
      if (debugAction(intent, "linux_first_gpu_system_blob_test")) {
         activity.getPreferences(0).edit().putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", true)
               .putBoolean("linux_gpu_gfxstream_system_blob_experimental", true)
               .putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false).apply();
         host.selectUbuntu(false);
         new Thread(() -> host.startLive(Profile.GFXSTREAM), "WinAVF-linux-first-gpu-system-blob").start();
      }
      debugWorker(intent, "linux_first_gpu_virgl_test", "WinAVF-linux-first-gpu-virgl",
            () -> host.startLive(Profile.GFXSTREAM_VIRGL));
      debugWorker(intent, "retry_frame_bridge_test", "WinAVF-frame-bridge-retry", this::retryDisplay);
   }

   private void startDebugVirgl(Profile profile, String workerName) {
      var preferences = activity.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true)
            .putBoolean("linux_gpu_virgl_experimental", false)
            .putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false);
      if (profile == Profile.VIRGL_LINUX_FIRST) {
         preferences.putBoolean("linux_gpu_gfxstream_system_blob_experimental", false);
      }
      preferences.apply();
      host.selectUbuntu(false);
      new Thread(() -> host.startLive(profile), workerName).start();
   }

   private void handleDebugStorageAndGuestActions(Intent intent) {
      debugWorker(intent, "ubuntu_gnome_cleanup", "WinAVF-ubuntu-gnome-cleanup", launches::cleanupGnome);
      debugWorker(intent, "v22_ext4_control", "WinAVF-v22-ext4-control", blockIo::stageControl);
      debugWorker(intent, "v23_ext4_block_io", "WinAVF-v23-ext4-block-io", blockIo::inspectBlockIo);
      debugWorker(intent, "uefi_input_probe", "WinAVF-uefi-input", () -> launches.startWindows(DiagnosticVmLaunches.WindowsProbe.UEFI_KEYBOARD));
      debugWorker(intent, "uefi_serial_escape_probe", "WinAVF-uefi-serial-escape", () -> launches.startWindows(DiagnosticVmLaunches.WindowsProbe.UEFI_SERIAL_ESCAPE));
      debugWorker(intent, "vsock_hello_probe", "WinAVF-vsock-hello", () -> launches.startWindows(DiagnosticVmLaunches.WindowsProbe.WINPE_HELLO));
      debugWorker(intent, "rollback", "WinAVF-rollback", host::rollback);
      debugWorker(intent, "audit", "WinAVF-audit", avf::auditCapabilities);
      if (debugAction(intent, "synthetic_frame")) this.emitSyntheticFrameForSurfaceAudit();
      debugWorker(intent, "display_host_audit", "WinAVF-display-audit", avf::auditDisplayAccess);
      debugWorker(intent, "console_input_api_probe", "WinAVF-console-input-api", avf::probeConsoleInput);
      debugWorker(intent, "console_binary_loopback", "WinAVF-console-binary-loopback", loopback::run);
      debugWorker(intent, "cleanup_console_binary_loopback", "WinAVF-console-binary-cleanup", loopback::cleanup);
      if (debugAction(intent, "kd_bridge")) {
         String token = intent.getStringExtra("kd_token");
         new Thread(() -> host.startDebugger(token), "WinAVF-product-kd-bridge").start();
      }
      debugWorker(intent, "kd_bridge_stop", "WinAVF-product-kd-stop", host::stopDebugger);
      debugWorker(intent, "export_product_bcd", "WinAVF-product-bcd-audit", diskReports::exportBcd);
      debugWorker(intent, "persistent_witness_audit", "WinAVF-persistent-witness-audit", diskReports::auditWitness);
      debugWorker(intent, "persistent_witness_cleanup", "WinAVF-persistent-witness-cleanup", diskReports::cleanupWitness);
      if (debugAction(intent, "preview_boot_failure")) {
         // Renders the failure card on the UI thread without touching any VM or disk.
         String os = intent.getStringExtra("preview_os");
         activity.runOnUiThread(() -> host.previewBootFailure(os == null ? "Ubuntu" : os,
               intent.getBooleanExtra("preview_windows", false)));
      }
   }

   private void openEncodedDisplayProof(Intent intent) {
      Log.i("UAVF-H264", "Probe request: vm=" + (host.ubuntuGuest() != null)
            + " running=" + host.running() + " lifecycle=" + host.lifecycleState());
      if (host.ubuntuGuest() == null || !host.running()) {
         host.show("Start installed Ubuntu before the isolated encoded display proof.");
         return;
      }
      int generation = ++this.encodedProbeGeneration;
      if (this.encodedDisplayProbe != null) this.encodedDisplayProbe.close();
      Object vm = host.ubuntuGuest();
      connection.begin();
      this.encodedDisplayProbe = new EncodedDisplayProbe(activity, vm,
            intent.getIntExtra("encoded_display_refresh_hz", 60), () -> {
               if (generation == this.encodedProbeGeneration && host.running()) host.attachDisplay(vm);
            });
      this.encodedDisplayProbe.show();
   }

   private static boolean debugAction(Intent intent, String action) {
      return intent.getBooleanExtra(action, false);
   }

   private static void debugWorker(Intent intent, String action, String threadName, Runnable worker) {
      if (debugAction(intent, action)) new Thread(worker, threadName).start();
   }

   private void emitSyntheticFrameForSurfaceAudit() {
      int width = 320;
      int height = 200;
      int header = 28;
      int bytes = width * height * 4;
      byte[] frame = new byte[header + bytes];
      frame[0] = 'W';
      frame[1] = 'A';
      frame[2] = 'V';
      frame[3] = 'F';
      frame[4] = 1;
      frame[5] = 1;
      ByteCodec.putLe16(frame, 8, width);
      ByteCodec.putLe16(frame, 10, height);
      ByteCodec.putLe32(frame, 12, 1398359601);
      ByteCodec.putLe32(frame, 16, bytes);
      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int pixel = header + (y * width + x) * 4;
            frame[pixel] = (byte) (x * 255 / (width - 1));
            frame[pixel + 1] = (byte) (y * 255 / (height - 1));
            frame[pixel + 2] = (byte) 128;
            frame[pixel + 3] = (byte) 255;
         }
      }
      var crc = new CRC32();
      crc.update(frame, header, bytes);
      ByteCodec.putLe32(frame, 20, (int) crc.getValue());
      host.feedFrame(frame);
      host.show("Synthetic WAVF surface audit frame submitted.");
   }

   private void retryDisplay() {
      try {
         Object vm = AvfGuest.find(activity, AvfGuest.UBUNTU_VM);
         if (vm == null) throw new IllegalStateException("Ubuntu VM not found");
         host.attachDisplay(vm);
      } catch (Exception error) {
         host.show("Frame bridge retry failed: " + IoUtil.rootMessage(error));
      }
   }

   void close() {
      ++encodedProbeGeneration;
      if (encodedDisplayProbe != null) encodedDisplayProbe.close();
      encodedDisplayProbe = null;
   }
}
