// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import static com.example.winavf.IoUtil.rootMessage;

import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import com.example.winavf.runtime.RuntimeTransition;
import com.example.winavf.runtime.VmConsole;
import com.example.winavf.runtime.VmLifecycle;
import com.example.winavf.UbuntuWorkspaceMedia.Request;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

final class GuestVmSession {
   enum GuestOs { UBUNTU, WINDOWS }

   enum PowerAction {
      SHUTDOWN, REBOOT;

      RuntimeTransition.Mode transitionMode() {
         return this == REBOOT ? RuntimeTransition.Mode.REBOOT : RuntimeTransition.Mode.SHUTDOWN;
      }
   }

   interface Host {
      void attachWindows(WindowsSetupRuntime setup);
      void selectUbuntu();
      GuestOs selectedOs();
      GuestControl control();
      boolean destroyed();
      boolean frameVisible();
      boolean isManagedVmRunning();
      File persistentUbuntuDiskFile();
      ConsoleFrameDecoder frameDecoder();
      void startUbuntuFrameBridge(Object vm);
      void startPersistentUbuntu(Request request);
      void startUbuntuLive();
      void finishHandoff();
      void beginRuntimeTransition(RuntimeTransition.Mode mode);
      void refreshTransition();
      void refreshTransitionView();
      void disconnectGuestControls(String detail);
      void resetGuestFrameSurface();
      void hideStoppedGuestControls();
      void showStoppedWorkspace(boolean temporary);
      void showInstallHandoff();
      void show(String message);
   }

   private final Activity activity;
   private final Handler uiHandler;
   private final VmLifecycle lifecycle;
   private final RuntimeTransition transition;
   private final DiagnosticSerialCapture diagnosticSerial;
   private final Host host;
   private final AtomicBoolean restoringDisplay = new AtomicBoolean();
   private final Object consoleObserverLock = new Object();
   private volatile VmConsole.Observer ubuntuConsoleObserver;
   private volatile boolean ubuntuFrameSession;
   private volatile boolean vmMayBeRunning;
   private volatile boolean userRequestedVmStop;
   private volatile boolean rebootRequested;

   GuestVmSession(Activity activity, Handler uiHandler, VmLifecycle lifecycle, RuntimeTransition transition,
                  DiagnosticSerialCapture diagnosticSerial, Host host) {
      this.activity = activity;
      this.uiHandler = uiHandler;
      this.lifecycle = lifecycle;
      this.transition = transition;
      this.diagnosticSerial = diagnosticSerial;
      this.host = host;
   }

   boolean mayBeRunning() { return vmMayBeRunning; }
   boolean hasFrameGuest() { return ubuntuFrameSession; }
   boolean stopRequested() { return userRequestedVmStop; }

   void attachAndRunDiagnostic(Object vm, String consoleLog) throws Exception {
      attachCallback(vm);
      startConsoleReader(AvfGuest.consoleOutput(vm), consoleLog);
      AvfGuest.run(vm);
      vmMayBeRunning = true;
   }

   private void clearGuestRuntime() {
      vmMayBeRunning = false;
      ubuntuFrameSession = false;
      host.resetGuestFrameSurface();
   }

   void reconnectRunningFrameVmIfPresent() {
      if (host.destroyed()) return;
      WindowsSetupRuntime setup = VmRuntimeService.windowsSetup();
      if (setup != null && (setup.active() || setup.continueInstalled())) {
         host.attachWindows(setup);
         return;
      }
      if (!restoringDisplay.compareAndSet(false, true)) return;
      new Thread(() -> {
         try {
            Object guest = VmRuntimeService.runningVm();
            if (guest == null) guest = AvfGuest.find(activity, AvfGuest.UBUNTU_VM);
            if (host.destroyed()) return;
            if (guest == null) {
               this.lifecycle.reconcile(null, "STOPPED", host.persistentUbuntuDiskFile().isFile());
               return;
            }

            String state = AvfGuest.state(guest);
            this.lifecycle.reconcile(guest, state, host.persistentUbuntuDiskFile().isFile());
            if (!state.contains("RUN") && !state.contains("START")) {
               return;
            }

            this.vmMayBeRunning = true;
            this.ubuntuFrameSession = true;
            host.selectUbuntu();
            this.attachCallback(guest);
            activity.runOnUiThread(() -> {
               if (!host.destroyed() && !host.frameVisible() && !transition.visible()) {
                  host.beginRuntimeTransition(RuntimeTransition.Mode.DISPLAY_RECONNECT);
               }
            });
            boolean temporary = activity.getPreferences(0).getBoolean("linux_current_boot_temporary", false);
            attachUbuntuConsole(guest, temporary ? "frame-bridge-test-serial.log" : "ubuntu-persistent-serial.log");
            host.startUbuntuFrameBridge(guest);
         } catch (Throwable error) {
            Log.i("U-AVF", "No reconnectable Linux session", error);
         } finally {
            restoringDisplay.set(false);
         }

      }, "U-AVF-reconnect").start();
   }

   void requestGuestPower(PowerAction action) {
      if (!host.isManagedVmRunning()) {
         host.show("No running VM.");
         return;
      }
      userRequestedVmStop = true;
      rebootRequested = action == PowerAction.REBOOT;
      lifecycle.stopping();
      host.beginRuntimeTransition(action.transitionMode());
      host.show(rebootRequested ? "Restart requested; waiting for Ubuntu…" : "Stopping: waiting for Ubuntu shutdown…");

      Object owner = lifecycle.owner();
      int generation = transition.generation();
      GuestControl control = host.control();
      if (control != null && control.connected()) {
         control.power(rebootRequested, result ->
               activity.runOnUiThread(() -> onPowerResult(owner, generation, result)));
      } else {
         host.show("Guest control unavailable. No hard stop was performed; waiting before offering recovery.");
      }
      uiHandler.postDelayed(() -> {
         if (ownsTransition(owner, generation) && lifecycle.state() == VmLifecycle.State.STOPPING) {
            host.refreshTransition();
         }
      }, 60_000);
   }

   private boolean ownsTransition(Object owner, int generation) {
      return !host.destroyed() && lifecycle.owns(owner) && transition.generation() == generation;
   }

   private void onPowerResult(Object owner, int generation, String result) {
      if (!ownsTransition(owner, generation) || transition.events().containsKey("VM stopped")) return;
      host.show("Guest power request: " + result);
      long now = SystemClock.elapsedRealtime();
      if ("ACK".equals(result)) {
         transition.event("Power request accepted", "Guest UCTL ACK", now);
      } else {
         transition.failure("Ubuntu did not accept the power request: " + result + ". No forced stop was performed.", now);
      }
      host.refreshTransition();
   }

   void confirmForceStop(PowerAction action) {
      String title = action == PowerAction.REBOOT ? "Force restart VM?" : "Force stop VM?";
      ProductDialog.show(activity, title,
            "This cuts power without a guest shutdown. Unsaved data may be lost and the guest filesystem may be damaged.",
            "Force", true, () -> {
               rebootRequested = action == PowerAction.REBOOT;
               new Thread(this::forceStopSelectedProfile, "U-AVF-force-stop").start();
            });
   }

   private void forceStopSelectedProfile() {
      WindowsSetupRuntime setup = VmRuntimeService.windowsSetup();
      if (setup != null && setup.active()) {
         setup.stop();
         return;
      }
      GuestOs profile = host.selectedOs();
      String name = ubuntuFrameSession || profile == GuestOs.UBUNTU
            ? AvfGuest.UBUNTU_VM : DiagnosticVmConfig.WINDOWS_VM;
      String label = profile == GuestOs.UBUNTU ? "Linux" : "Windows";
      try {
         Object vm = AvfGuest.find(activity, name);
         if (vm == null) {
            host.show("No running " + label.toLowerCase(Locale.ROOT) + " VM.");
            return;
         }
         this.userRequestedVmStop = true;
         var preferences = activity.getPreferences(0);
         if (!preferences.getBoolean("linux_current_boot_temporary", false)) {
            preferences.edit().putBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, false).apply();
         }
         this.lifecycle.stopping();
         AvfGuest.stop(vm);
         // The AVF callback owns terminal state and reboot routing.
         host.show(label + " VM stop requested.");
      } catch (Exception error) {
         host.show("Could not stop VM: " + rootMessage(error));
      }
   }

   Object launchUbuntuVm(Object config, String consoleLog) throws Exception {
      Object vm = AvfGuest.replaceStopped(activity, AvfGuest.UBUNTU_VM, config);
      this.userRequestedVmStop = false;
      this.attachCallback(vm);
      this.attachUbuntuConsole(vm,consoleLog);
      this.vmMayBeRunning = true;
      this.ubuntuFrameSession = true;
      try {
         AvfGuest.run(vm);
      } catch (Exception failure) {
         this.vmMayBeRunning = false;
         this.ubuntuFrameSession = false;
         throw failure;
      }
      return vm;
   }

   void attachCallback(Object vm) throws Exception {
      this.lifecycle.attach(vm);
      this.transition.event("Virtual machine created", "AVF VM handle", SystemClock.elapsedRealtime());
      AvfGuest.observe(vm, ForkJoinPool.commonPool(),
            reason -> this.onGuestStopped(vm, reason), detail -> this.onGuestError(vm, detail));
      activity.runOnUiThread(() -> {
         if (host.destroyed() || !this.lifecycle.owns(vm)) return;
         try {
            VmRuntimeService.keepAlive(activity, vm);
         } catch (Exception error) {
            Log.w("U-AVF-runtime", "Foreground runtime could not start", error);
         }
      });
   }

   private void onGuestStopped(Object vm, String reason) {
      if (host.destroyed() || !this.lifecycle.stopped(vm, reason)) return;
      VmRuntimeService.ended(activity, vm);
      String evidence = "AVF onStopped: " + reason;
      this.transition.event("VM stopped", evidence, SystemClock.elapsedRealtime());
      Log.i("U-AVF-lifecycle", "Stopped: " + reason);
      host.disconnectGuestControls("Disconnected: " + reason);
      boolean restart = this.rebootRequested || "STOP_REASON_REBOOT".equals(reason);
      if (restart && this.transition.mode() != RuntimeTransition.Mode.REBOOT) {
         host.beginRuntimeTransition(RuntimeTransition.Mode.REBOOT);
         this.transition.event("VM stopped", evidence, SystemClock.elapsedRealtime());
      }
      this.rebootRequested = false;

      var prefs = activity.getPreferences(0);
      boolean temporary = prefs.getBoolean("linux_current_boot_temporary", false);
      boolean ubuntu = ubuntuFrameSession;
      boolean installing = ubuntu && !temporary && !this.userRequestedVmStop
            && prefs.getBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, false)
            && !prefs.getBoolean(UbuntuWorkspaceMedia.HANDOFF_ATTEMPTED, false);
      this.clearGuestRuntime();
      if (installing) {
         this.lifecycle.install(VmLifecycle.Install.FINALIZING);
         prefs.edit().putBoolean(UbuntuWorkspaceMedia.HANDOFF_ATTEMPTED, true).apply();
         activity.runOnUiThread(() -> {
            host.showInstallHandoff();
            host.show("Install session ended; checking the persistent boot handoff.");
         });
         new Thread(host::finishHandoff, "U-AVF-install-handoff").start();
         return;
      }
      if (restart && ubuntu) {
         int generation = this.transition.generation();
         host.show("Ubuntu reboot: reconnecting the workspace…");
         this.uiHandler.postDelayed(() -> {
            if (host.destroyed() || this.transition.generation() != generation || host.isManagedVmRunning()) return;
            new Thread(() -> {
               if (!temporary && prefs.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false)) {
                  host.startPersistentUbuntu(Request.START_EXISTING);
               } else {
                  host.startUbuntuLive();
               }
            }, "U-AVF-reboot-handoff").start();
         }, 500);
         return;
      }

      int generation = this.transition.generation();
      activity.runOnUiThread(() -> {
         host.hideStoppedGuestControls();
         host.refreshTransitionView();
         this.uiHandler.postDelayed(() -> {
            if (host.destroyed() || this.transition.generation() != generation) return;
            this.transition.dismiss();
            host.refreshTransitionView();
            host.showStoppedWorkspace(temporary);
         }, 450);
      });
      host.show("VM stopped.");
   }

   private void onGuestError(Object vm, String detail) {
      if (host.destroyed() || !this.lifecycle.failed(vm, detail)) return;
      this.transition.failure(detail, SystemClock.elapsedRealtime());
      host.disconnectGuestControls("Disconnected: VM error");
      this.clearGuestRuntime();
      activity.runOnUiThread(() -> {
         host.hideStoppedGuestControls();
         boolean temporary = activity.getPreferences(0).getBoolean("linux_current_boot_temporary", false);
         host.showStoppedWorkspace(temporary);
         host.refreshTransition();
      });
      host.show("VM error: " + detail);
   }

   private void attachUbuntuConsole(Object vm,String filename) throws Exception {
      synchronized(this.consoleObserverLock) {
         if(host.destroyed())return;
         var observer=new VmConsole.Observer() {
            @Override public void onBytes(byte[] bytes,int length) {
               if(ubuntuConsoleObserver!=this || host.destroyed() || !lifecycle.owns(vm))return;
               host.frameDecoder().feed(bytes,0,length);
               transition.serial(transition.generation(),new String(bytes,0,length,StandardCharsets.UTF_8),
                     SystemClock.elapsedRealtime());
            }
            @Override public void onFailure(Exception failure) {
               if(ubuntuConsoleObserver==this && !host.destroyed())
                  Log.w("UAVF-Console","Managed Ubuntu console closed",failure);
            }
         };
         this.ubuntuConsoleObserver=observer;
         File reports=activity.getExternalFilesDir(null);
         if(reports==null)reports=activity.getFilesDir();
         VmRuntimeService.ubuntuConsole().attach(vm,()->AvfGuest.consoleOutput(vm),new File(reports,filename),observer);
      }
   }

   void detachUbuntuConsole() {
      synchronized(this.consoleObserverLock) {
         VmRuntimeService.ubuntuConsole().detach(this.ubuntuConsoleObserver);
         this.ubuntuConsoleObserver=null;
      }
   }

   void startConsoleReader(InputStream stream, String filename) {
      Object owner = lifecycle.owner();
      File log = new File(activity.getExternalFilesDir(null), filename);
      this.diagnosticSerial.start(stream, log, host.frameDecoder(), chunk -> {
         if (lifecycle.owns(owner)) {
            this.transition.serial(this.transition.generation(), chunk, SystemClock.elapsedRealtime());
         }
      }, host::show);
   }

}
