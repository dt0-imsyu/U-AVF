// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import static com.example.winavf.IoUtil.rootMessage;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import com.example.winavf.runtime.RuntimeTransition;
import com.example.winavf.runtime.VmLifecycle;
import org.json.JSONObject;

/** Owns activity-scoped connections to the running Ubuntu guest. */
final class UbuntuGuestServices implements AutoCloseable {
   interface Host {
      boolean installedDiskExists();
      void resetPlasmaState();
      String plasmaState();
      void updatePlasmaState(String state);
      void displayGuestReady(boolean ready);
      void startDisplay(Object vm);
      void refreshTransition();
      void show(String message);
   }

   private final Activity activity;
   private final VmLifecycle lifecycle;
   private final RuntimeTransition transition;
   private final Host host;
   private final UbuntuAudioBridge ubuntuAudio = new UbuntuAudioBridge();
   private volatile GuestControl guestControl;
   private volatile Object controlVm;
   private volatile String guestControlStatus = "Unavailable / not connected";
   private volatile boolean disposed;
   private GuestClipboard guestClipboard;
   private Object clipboardVm;

   UbuntuGuestServices(Activity activity, VmLifecycle lifecycle, RuntimeTransition transition, Host host) {
      this.activity = activity;
      this.lifecycle = lifecycle;
      this.transition = transition;
      this.host = host;
   }

   GuestControl control() { return guestControl; }

   boolean connected() {
      GuestControl control = guestControl;
      return control != null && control.connected();
   }

   void focusClipboard(boolean focused) {
      if (guestClipboard != null) guestClipboard.focus(focused);
   }

   @Override public void close() {
      disposed = true;
      disconnect("Disconnected: activity closed");
      closeClipboard();
   }

   private void closeClipboard() {
      if (guestClipboard == null) return;
      guestClipboard.close();
      guestClipboard = null;
   }

   void connect(Object vm) {
      if (disposed) return;
      try {
         this.lifecycle.reconcile(vm, AvfGuest.state(vm), host.installedDiskExists());
      } catch (Exception error) {
         this.lifecycle.failed(vm, "AVF state unavailable: " + rootMessage(error));
      }
      if (this.controlVm != vm || this.guestControl == null) {
         if (this.guestControl != null) this.guestControl.close();
         this.controlVm = vm;
         host.resetPlasmaState();
         var prefs = activity.getPreferences(0);
         Boolean installedMavk = prefs.getBoolean("linux_current_boot_installed", false)
               ? prefs.getBoolean("linux_current_boot_mavk", false) : null;
         this.guestControl = new GuestControl(vm, installedMavk, (connected, detail, session) -> {
            activity.runOnUiThread(() -> this.onUbuntuControlUpdate(vm, connected, detail, session));
         });
      }
      this.startUbuntuAudioBridge(vm);
      if (this.clipboardVm != vm) {
         closeClipboard();
         this.clipboardVm = vm;
      }
      syncClipboard();
   }

   private void onUbuntuControlUpdate(Object vm, boolean connected, String detail, JSONObject session) {
      if (disposed || !lifecycle.owns(vm) || this.controlVm != vm) return;
      if (!detail.equals(this.guestControlStatus)) Log.i("U-AVF-control", detail);
      this.guestControlStatus = detail;
      boolean guestReady = connected && session != null && session.optBoolean("ok")
            && session.optBoolean("sessions_available");
      host.displayGuestReady(guestReady);
      if (!connected || session == null) return;

      host.updatePlasmaState(session.optString("plasma_x11", "unavailable"));
      var prefs = activity.getPreferences(0);
      if (session.has("mavk_selected") && !session.optBoolean("mavk_selected")
            && prefs.getBoolean("linux_current_boot_mavk", false)) {
         prefs.edit().putBoolean("linux_current_boot_mavk", false).apply();
         Log.w("UAVF-Mavk", "Using stable display: " + session.optString("mavk_mode_error"));
         host.startDisplay(vm);
      }
      if (session.optBoolean("growth_supported") && session.optInt("growth_version") == 1) {
         var growth = prefs.edit().putBoolean("ubuntu_growth_helper_verified", true);
         long pending = prefs.getLong("ubuntu_growth_pending_bytes", 0);
         if (pending > 0 && session.optLong("filesystem_bytes") >= pending) growth.remove("ubuntu_growth_pending_bytes");
         growth.apply();
      }
      if (guestReady) prefs.edit().putString("runtime_verified_build", Build.FINGERPRINT).apply();
      this.transition.guestReady(session.optBoolean("sessions_available"), SystemClock.elapsedRealtime());
      host.refreshTransition();
   }

   void syncClipboard() {
      boolean enabled = activity.getPreferences(0).getBoolean("linux_text_clipboard_enabled", false);
      if (!enabled || this.clipboardVm == null) {
         closeClipboard();
      } else if (this.guestClipboard == null && !disposed && !activity.isFinishing()) {
         this.guestClipboard = new GuestClipboard(activity, this.clipboardVm);
      }
   }

   private void startUbuntuAudioBridge(Object vm) {
      if (activity.getPreferences(0).getBoolean("linux_audio_output_enabled", true)) {
         this.ubuntuAudio.start(vm, activity.getExternalFilesDir(null));
      }
   }

   void disconnect(String status) {
      GuestControl control = this.guestControl;
      this.guestControl = null;
      this.controlVm = null;
      if (control != null) control.close();
      this.guestControlStatus = status;
   }

   void requestPlasmaInstall() {
      if(!activity.getPreferences(0).getBoolean("linux_internet_enabled",true)) {
         ProductDialog.notice(activity,"KDE Plasma X11",
               "Для загрузки Plasma нужен интернет в Ubuntu.");
         return;
      }
      ProductDialog.show(activity,"KDE Plasma X11 · Mavk",
            "Ubuntu скачает Plasma из своих репозиториев. После установки перезапустите Ubuntu.",
            "Установить",false,()->{
         GuestControl control=this.guestControl;
         if(control==null || !control.connected() || "installing".equals(host.plasmaState()))return;
         host.updatePlasmaState("installing");
         control.installPlasma(ok->activity.runOnUiThread(()->{
            if(disposed || this.guestControl!=control)return;
            if(ok)activity.getPreferences(0).edit().putBoolean(UbuntuWorkspaceMedia.PLASMA_PENDING,true).apply();
            else {
               host.updatePlasmaState("failed");
               host.show("Could not start Plasma installation.");
            }
         }));
      });
   }

}
