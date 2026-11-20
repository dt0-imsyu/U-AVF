// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import com.example.winavf.runtime.RuntimeTransition;
import com.example.winavf.runtime.VmConsole;

import com.example.winavf.runtime.VmLifecycle;

import com.example.winavf.diagnostics.DiagnosticJournal;
import com.example.winavf.diagnostics.CompatibilityHistory;
import com.example.winavf.UbuntuWorkspaceMedia.Installer;
import com.example.winavf.UbuntuWorkspaceMedia.Request;
import com.example.winavf.navigation.ProductNavigation;
import com.example.winavf.navigation.ProductNavigation.Location;
import com.example.winavf.navigation.ProductScreen;
import com.example.winavf.storage.UbuntuLiveMedia;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PermissionInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.system.Os;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets.Type;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

public final class MainActivity extends Activity {
   private static final String VM_NAME = "winavf-gop-ebs-r1";
   private static final String CONSOLE_INPUT_PROBE_VM_NAME = "winavf-console-input-probe-20260909";
   private static final String CONSOLE_BINARY_ECHO_VM_NAME = "winavf-console-binary-echo-20260909";
   private static final byte[] CONSOLE_BINARY_ECHO_READY;
   private static final int CONSOLE_BINARY_ECHO_BYTES = 4096;
   private static final int KD_BRIDGE_PORT = 39100;
   private static final long WINDOWS_TARGET_SIZE = 68719476736L;
   private static final String HEADLESS_SETUP_MEDIA_NAME = "win11-headless-installer-10g.img";
   private static final long HEADLESS_SETUP_MEDIA_MIN_SIZE = 7516192768L;
   private static final String HEADLESS_SETUP_MEDIA_REVISION = "r3-efi-gpt";
   private static final String HEADLESS_BOOT_MEDIA_NAME = "win11-gop-ebs-r1.img";
   private static final long HEADLESS_BOOT_MEDIA_SIZE = 9126805504L;
   private static final String HEADLESS_BOOT_MEDIA_SHA256 = "2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7";
   private static final String UBUNTU_GNOME_VM_NAME = "winavf-ubuntu-gnome-24045-v21";
   private static final String UBUNTU_GNOME_QUEUE_DEPTH_1_VM_NAME = "winavf-ubuntu-gnome-24045-v21-q1";
   private static final String UBUNTU_GNOME_MEDIA_NAME = "ubuntu-gnome-24.04.5-v21-vsock-listener.img";
   private static final long UBUNTU_GNOME_MEDIA_SIZE = 9126805504L;
   private static final String UBUNTU_GNOME_MEDIA_SHA256 = "F3AAC176C600BFC3D3EA0BE5DA4EFF2F03DE7B27E3DED26A0E9D58D21B975FAB";
   private static final String UBUNTU_GNOME_FIRMWARE_PATCH_NAME = "ubuntu-gnome-v21-vsock-firmware.patch";
   private static final long UBUNTU_GNOME_FIRMWARE_PATCH_SIZE = 4194436L;
   private static final String UBUNTU_GNOME_FIRMWARE_PATCH_SHA256 = "936B0E8106806E5E4AC99C4C8E9FD9394F34747992E7E44BBFF885A8AD50553A";
   private static final String V12_RECOVERY_VM_NAME = "winavf-ubuntu-gnome-v12-recovery";
   private static final String V12_RECOVERY_MEDIA_NAME = "ubuntu-gnome-v12-recovery-candidate.img";
   private static final long V12_RECOVERY_MEDIA_SIZE = 9126805504L;
   private static final String V12_RECOVERY_MEDIA_SHA256 = "C9D80A9CABB14C6E1D0F4F3180923146E354CC916020FB55BB2608FDC3110EA8";
   private static final String V12_RECOVERY_PATCH_NAME = "ubuntu-gnome-v12-recovery-firmware.patch";
   private static final long V12_RECOVERY_PATCH_SIZE = 4194436L;
   private static final String V12_RECOVERY_PATCH_SHA256 = "9C109F3EB95D1B6F27929D970152725E0F2328A25FC3C847D8ED1603D86EDC55";
   private static final String IMAGE_PATCH_STAGING_NAME = "winavf-image-patch.bin";
   private static final String IMAGE_PATCH_ACTIVE_NAME = "active-image-patch.bin";
   private static final String GENERIC_UBUNTU_VM_NAME = "winavf-generic-ubuntu-24045";
   private static final String GENERIC_UBUNTU_ISO_NAME = "ubuntu-24.04.5-desktop-arm64.iso";
   private static final long GENERIC_UBUNTU_ISO_SIZE = 3967463424L;
   private static final String GENERIC_UBUNTU_ISO_SHA256 = "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14";
   private static final String GENERIC_UBUNTU_PLATFORM_ESP_NAME = "generic-ubuntu-platform-gpt.img";
   private static final long GENERIC_UBUNTU_PLATFORM_ESP_SIZE = 134217728L;
   private static final String GENERIC_UBUNTU_PLATFORM_ESP_SHA256 = "413B28882832DDC73CA3B425D9676542595590B53B91739FC4A45CDF41046AA2";
   private static final String GENERIC_UBUNTU_COMBINED_DISK_NAME = "generic-ubuntu-one-disk-journal-rplus.img";
   private static final long GENERIC_UBUNTU_COMBINED_DISK_SIZE = 4102029312L;
   private static final String GENERIC_UBUNTU_COMBINED_DISK_SHA256 = "FFABA39B31EA1E95B40F4DD3F67BF5C274C336C6CFF427E7075AC0BD82143714";
   private static final String GENERIC_UBUNTU_STOCK_KERNEL_NAME = "generic-ubuntu-stock-kernel.efi";
   private static final String GENERIC_UBUNTU_REPORT_NAME = "generic-ubuntu-runtime-report.txt";
   private static final String FRAME_BRIDGE_TEST_VM_NAME = "winavf-frame-bridge-test-ubuntu-24045";
   private static final long FRAME_BRIDGE_TEST_DISK_SIZE = 4102029312L;
   private static final String FRAME_BRIDGE_TEST_DISK_SHA256 = "68582C7187EB83BEEE9E2D994BE4952FC5DE772BF2273AD1ECBCE6621B59BB47";
   private static final String FRAME_BRIDGE_TEST_REPORT_NAME = "frame-bridge-test-runtime-report.txt";
   private static final String FRAME_BRIDGE_PLATFORM_PREFIX_ASSET = "p33-platform-prefix-128m.img";
   private static final long FRAME_BRIDGE_PLATFORM_PREFIX_SIZE = 134217728L;
   private static final String FRAME_BRIDGE_PLATFORM_PREFIX_SHA256 = "012942E1A9203E659D43A1CEB5AB2DD70AEF6D01A8F4C4FDEA22B146DC088CB1";
   private static final String FRAME_BRIDGE_GPT_TRAILER_ASSET = "p33-gpt-trailer.bin";
   private static final long FRAME_BRIDGE_GPT_TRAILER_SIZE = 16896L;
   private static final String FRAME_BRIDGE_GPT_TRAILER_SHA256 = "A0F9379853E2BD1CD9FF2854113CCB971B8A2A236C7C7A0736983EAA427C861B";
   private static final String FRAME_BRIDGE_RUNTIME_DISK_NAME = "winavf-P35-rfb-incremental.img";
   private static final String FRAME_BRIDGE_RUNTIME_STAMP_NAME = "uavf-ubuntu-live-runtime.ready";
   private static final String FRAME_BRIDGE_ZINK_PREFIX_ASSET = "p7-platform-prefix-gfxstream-zink.img";
   private static final long FRAME_BRIDGE_ZINK_PREFIX_SIZE = 134217728L;
   private static final String FRAME_BRIDGE_ZINK_PREFIX_SHA256 = "24C0C1D7A5D5E070F3D61A87EDE1691525B9ECBCA7BEDBCAD182B5366CC9EE27";
   private static final String FRAME_BRIDGE_ZINK_DISK_NAME = "winavf-P7-gfxstream-zink-experimental.img";
   private static final String FRAME_BRIDGE_ZINK_DISK_SHA256 = "D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F";
   private static final String FRAME_BRIDGE_ZINK_STAMP_NAME = "uavf-ubuntu-zink-runtime.ready";
   private static final String FRAME_BRIDGE_VIRGL_GBM_PREFIX_ASSET = "p7-platform-prefix-virgl-gbm.img";
   private static final long FRAME_BRIDGE_VIRGL_GBM_PREFIX_SIZE = 134217728L;
   private static final String FRAME_BRIDGE_VIRGL_GBM_PREFIX_SHA256 = "C7AE4D1F9BBDBFFA2CD27063A9FF59E483CC60F828CB3D9BFB66B65572C663DB";
   private volatile String plasmaInstallState = "unavailable";
   private static final String FRAME_BRIDGE_VIRGL_GBM_DISK_NAME = "winavf-P7-virgl-gbm-runtime.img";
   private static final String FRAME_BRIDGE_VIRGL_GBM_STAMP_NAME = "uavf-ubuntu-virgl-gbm-runtime.ready";
   private static final String UBUNTU_PERSISTENT_DISK_NAME = "uavf-ubuntu-persistent-one-disk.raw";
   private static final String UBUNTU_PERSISTENT_VM_NAME = "winavf-frame-bridge-test-ubuntu-24045";
   private static final String UBUNTU_PERSISTENT_REPORT_NAME = "ubuntu-install-runtime-report.txt";
   private static final String UBUNTU_INSTALL_HANDOFF_PENDING_PREF = "ubuntu_install_handoff_pending";
   private static final String UBUNTU_INSTALL_HANDOFF_ATTEMPTED_PREF = "ubuntu_install_handoff_attempted";
   private static final long UBUNTU_PERSISTENT_MIN_FREE_BYTES = 17179869184L;
   private static final String LINUX_FIRST_GPU_PREFIX_ASSET = "p7-platform-prefix-linux-first-gpu.img";
   private static final String LINUX_FIRST_GPU_PREFIX_SHA256 = "E2336A9161C31CC2BBB386C77BFE3459640573DC29F5B8AA7DA61E4270AF95B5";
   private static final String LINUX_FIRST_GPU_DISK_SHA256 = "C2C670C75BC0534706078898A82DD683B87A491D320D153C44F2F0BB4D81A764";
   private static final String V22_EXT4_CONTROL_VM_NAME = "winavf-ubuntu-gnome-24045-v22-ext4-control";
   private static final String V22_EXT4_CONTROL_MEDIA_NAME = "ubuntu-gnome-v22-ext4-control.img";
   private static final long V22_EXT4_CONTROL_MEDIA_SIZE = 9126805504L;
   private static final String V22_EXT4_CONTROL_MEDIA_SHA256;
   private static final String V22_EXT4_CONTROL_LABEL = "V22_EXT4_CONTROL";
   private static final String V22_EXT4_CONTROL_REPORT_NAME = "ubuntu-gnome-v22-ext4-control-report.txt";
   private static final String V23_EXT4_BLOCK_IO_VM_NAME = "winavf-ubuntu-gnome-24045-v23-ext4-block-io";
   private static final String V23_BOOT_CARRIER_NAME = "ubuntu-gnome-v21-v23-disposable-carrier.img";
   private static final String V23_EXT4_BLOCK_IO_REPORT_NAME = "ubuntu-gnome-v23-ext4-block-io-report.txt";
   private TextView status;
   private FrameSurfaceView frameSurface;
   private android.view.SurfaceView installedEncodedSurface;
   private android.app.Dialog encodedFallbackDialog;
   private EncodedDisplayProbe installedEncodedDisplay;
   private MavkDisplayRuntime mavkDisplay;
   private android.view.SurfaceView mavkSurface;
   private volatile boolean mavkSessionReady;
   private int encodedSessionReconnects;
   private long encodedSessionReconnectWindowMs;
   private volatile int ubuntuGuestRuntimeRevision;
   private volatile String activeFrameVmName;
   private volatile boolean vmMayBeRunning;
   private volatile boolean userRequestedVmStop;
   private final VmLifecycle lifecycle = new VmLifecycle();
   private final RuntimeTransition transition = new RuntimeTransition();
   private RuntimeTransitionView transitionView;
   private final DiagnosticJournal journal = new DiagnosticJournal(UiLanguage::english);
   private int journalGeneration=-1;
   private final java.util.Set<String> journalSeen=new java.util.HashSet<>();
   private final Runnable transitionTick = new Runnable() {
      public void run() {
         if (activityDestroyed) return;
         recordObservedRuntimeEvents();
         if (transitionView != null) {
            transitionView.allowForceStop(isManagedVmRunning());
            transitionView.refresh();
         }
         if (transition.visible()) uiHandler.postDelayed(this,1000);
      }
   };
   private volatile Object lifecycleVm;
   private GuestControl guestControl;
   private volatile Object controlVm;
   private volatile String guestControlStatus = "Unavailable / not connected";
   private volatile boolean rebootRequested;
   private volatile boolean activityDestroyed;
   private volatile Object activeAudioBridgeVm;
   private volatile Object encodedDisplayVm;
   private EncodedDisplayProbe encodedDisplayProbe;
   private int encodedProbeGeneration;
   private FrameLayout page;
   private FrameLayout homePanel;
   private FrameLayout tabsHost;
   private FigmaMainTabs.Pager tabsPages;
   private FigmaMainTabs.Navigation tabsNavigation;
   private int tabsLanguage=-1;
   private LinearLayout toolbar;
   private GuestIme guestIme;
   private FigmaWorkspaceSettings workspaceSettingsView;
   private boolean awaitingKeyboardCapture;
   private Button menuButton;
   private View drawerScrim;
   private TextView profileLabel;
   private TextView profileDetails;
   private Button windowsProfileButton;
   private Button linuxProfileButton;
   private Button chooseUbuntuIsoButton;
   private TextView ubuntuIsoStatus;
   private TextView homeSetupStatus;
   private TextView productStatus;
   private Button v22ProfileButton;
   private CheckBox skipDiskValidationToggle;
   private ConsoleFrameDecoder frameDecoder;
   private boolean frameVisible;
   private volatile boolean bootOptionsPromptSeen;
   private volatile boolean serialInputWindowSeen;
   private volatile boolean serialEscapeAcknowledged;
   private volatile int observedFrameCount;
   private boolean restorePointerCaptureAfterDrawer;
   private final AtomicBoolean frameRevealPostQueued = new AtomicBoolean();
   private final StringBuilder serialProbeTail = new StringBuilder();
   private volatile Socket activeKdBridgeSocket;
   private volatile Object activeKdBridgeVm;
   private LaunchProfile selectedProfile;
   private static final int REQUEST_UBUNTU_ISO = 21825;
   private static final int REQUEST_UBUNTU_LIVE_ISO = 21829;
   private static final int REQUEST_DIAGNOSTIC_EXPORT = 21826;
   private static final int REQUEST_WINDOWS_INSTALL_ISO = 21827;
   private static final int REQUEST_WINDOWS_WINPE = 21828;
   private record WindowsSetupProgress(WindowsSetupDisk.Phase phase, String error) {}
   private static volatile WindowsSetupProgress windowsSetupProgress = new WindowsSetupProgress(null, "");
   private ProductScreen productScreen;
   private final ProductNavigation navigation = new ProductNavigation();
   private boolean returningBack;
   private boolean compatibilityRunning;
   private static final int COMPATIBILITY_SCHEMA=3;
   private final String[] readinessResults=new String[FigmaCompatibilityView.CHECKS.length];
   { Arrays.fill(this.readinessResults,"Not checked"); }
   private AvfCompatibilityProbe.Status compatibilityStatus=AvfCompatibilityProbe.Status.INSUFFICIENT_DATA;
   private int readinessCompleted;
   private boolean compatibilityPassed;
   private FigmaCompatibilityView compatibilityView;
   private boolean compatibilityViewRunning;
   private boolean welcomeFirstRun;
   private String createWorkspaceOs;
   private String draftUbuntuTitle="";
   private String workspaceSettingsTab;
   private int workspaceSettingsPreviousTab=-1;
   private boolean wizardSettingsActive;
   private long edgeHoldStarted;
   private float edgeHoldX,edgeHoldY;
   private boolean edgeGestureTracking;
   private int guestFrameWidth;
   private int guestFrameHeight;
   private String currentNotice;
   private Handler uiHandler;

   public MainActivity() {
      this.selectedProfile = MainActivity.LaunchProfile.WINDOWS;
      this.productScreen = ProductScreen.ONBOARDING;
      this.createWorkspaceOs = "ubuntu";
      this.workspaceSettingsTab = "Performance";
      this.guestFrameWidth = 1280;
      this.guestFrameHeight = 800;
      this.currentNotice = "";
   }

   public void onCreate(Bundle var1) {
      super.onCreate(var1);
      UiLanguage.load(this);
      // Android 13+ can dispatch navigation Back without dispatchKeyEvent or
      // onBackPressed. Keep both paths under the same VM-preserving policy.
      this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
         OnBackInvokedDispatcher.PRIORITY_DEFAULT, this.navigationBackCallback);
      this.getWindow().setDecorFitsSystemWindows(false);
      this.hideSystemBars();
      this.page = new FrameLayout(this);
      this.page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
         this.fitEncodedSurface();
         this.fitMavkSurface();
      });
      this.page.setBackgroundColor(-16777216);
      this.uiHandler = new Handler(this.getMainLooper());
      this.frameSurface = new FrameSurfaceView(this);
      this.frameSurface.setInputListener(new FrameSurfaceView.InputListener() {
         public void onPointer(int var1, int var2, int var3) {
            MainActivity.this.revealRunningControls();
            MainActivity.this.onGuestPointer(var1, var2, var3);
         }

         public void onMouseButton(int var1, boolean var2) {
            MainActivity.this.revealRunningControls();
            MainActivity.this.displayConnection.send(GuestInput.BUTTON, var2 ? 1 : 0, var1, 0);
         }

         public void onScroll(int var1, int var2) {
            MainActivity.this.revealRunningControls();
            MainActivity.this.displayConnection.send(GuestInput.SCROLL, 0, var1, var2);
         }

         public void onRelativePointer(int var1, int var2) {
            MainActivity.this.onGuestRelativePointer(var1, var2);
         }

         public void onPointerCaptureChanged(boolean var1) {
            MainActivity.this.show(var1 ? "Mouse captured. Press Ctrl + Alt + Shift + Esc to open controls and release it." : "Mouse released.");
            if (MainActivity.this.toolbar != null && MainActivity.this.toolbar.getVisibility() == 0) {
               MainActivity.this.populateSessionDrawer();
            }

         }
      });
      FrameLayout.LayoutParams var2 = new FrameLayout.LayoutParams(-1, -1);
      this.page.addView(this.frameSurface, var2);
      this.status = new TextView(this);
      this.status.setText("Ready");
      this.status.setTextColor(-3355444);
      this.status.setTextSize(12.0F);
      this.status.setSingleLine(true);
      this.status.setEllipsize(TruncateAt.END);
      this.status.setPadding(16, 8, 16, 8);
      this.status.setBackgroundColor(-1728053248);
      FrameLayout.LayoutParams var3 = new FrameLayout.LayoutParams(-1, -2, 80);
      this.page.addView(this.status, var3);
      this.addProductControls();
      this.frameDecoder = new ConsoleFrameDecoder(new ConsoleFrameDecoder.Listener() {
         public void onFrame(ConsoleFrameDecoder.Frame var1) {
            ++MainActivity.this.observedFrameCount;
            MainActivity.this.guestFrameWidth = var1.width;
            MainActivity.this.guestFrameHeight = var1.height;
            // Firmware UART frames are not proof that a Linux desktop exists.
            if(MainActivity.this.selectedProfile==LaunchProfile.LINUX && MainActivity.this.transition.visible()) return;
            MainActivity.this.queueFrameForDisplay(var1);
         }

         public void onProtocolError(String var1) {
         }
      });
      this.setContentView(this.page);
      this.page.setFocusableInTouchMode(true);
      this.page.requestFocus();
      this.handleIntentActions(this.getIntent());
      this.restoreProductNavigation(var1);
      this.reconnectRunningFrameVmIfPresent();
   }

   @Override protected void onSaveInstanceState(Bundle out) {
      super.onSaveInstanceState(out);
      if(this.homePanel.getVisibility()==View.VISIBLE)out.putString("product_screen",this.productScreen.name());
      out.putString("workspace_tab",this.workspaceSettingsTab);
      out.putBoolean("wizard_settings",this.wizardSettingsActive);
      out.putString("create_workspace_os",this.createWorkspaceOs);
      out.putString("draft_ubuntu_title",this.draftUbuntuTitle);
      out.putBoolean("keyboard_capture_pending",this.awaitingKeyboardCapture);
      java.util.ArrayList<Bundle> history=new java.util.ArrayList<>();
      for(Location location:this.navigation.snapshot()) {
         Bundle item=new Bundle();
         if(location.screen()!=null)item.putString("screen",location.screen().name());
         item.putString("tab",location.settingsTab());
         item.putBoolean("wizard",location.wizard());
         item.putBoolean("drawer",location.drawer());
         history.add(item);
      }
      out.putParcelableArrayList("product_history",history);
   }

   private void restoreProductNavigation(Bundle state) {
      if(state==null || !state.containsKey("product_screen"))return;
      this.workspaceSettingsTab=state.getString("workspace_tab","Performance");
      this.wizardSettingsActive=state.getBoolean("wizard_settings");
      this.createWorkspaceOs=state.getString("create_workspace_os","ubuntu");
      this.draftUbuntuTitle=state.getString("draft_ubuntu_title","");
      this.awaitingKeyboardCapture=state.getBoolean("keyboard_capture_pending");
      java.util.ArrayList<Bundle> history=state.getParcelableArrayList("product_history",Bundle.class);
      java.util.ArrayList<Location> locations=new java.util.ArrayList<>();
      if(history!=null)for(Bundle item:history) {
         String screen=item.getString("screen");
         locations.add(new Location(screen==null?null:ProductScreen.valueOf(screen),
               item.getString("tab","Performance"),item.getBoolean("wizard"),item.getBoolean("drawer")));
      }
      this.navigation.restore(locations);
      this.returningBack=true;
      try {this.renderProductScreen(ProductScreen.valueOf(state.getString("product_screen")));}
      finally {this.returningBack=false;}
   }

   private void hideSystemBars() {
      WindowInsetsController var1 = this.getWindow().getDecorView().getWindowInsetsController();
      if (var1 != null) {
         var1.hide(Type.statusBars() | Type.navigationBars());
         var1.setSystemBarsBehavior(2);
      }

   }

   public void onWindowFocusChanged(boolean var1) {
      super.onWindowFocusChanged(var1);
      if (this.guestClipboard != null) this.guestClipboard.focus(var1);
      if (var1) {
         this.hideSystemBars();
      } else {
         this.guestInput.captureEnded();
      }

   }

   @Override protected void onResume() {
      super.onResume();
      VmKeyboardAccessibilityService.foreground(this);
      if(this.page!=null) this.page.post(() -> {
         if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.resumeSurface();
         if(this.awaitingKeyboardCapture) {
            this.awaitingKeyboardCapture=false;
            this.getPreferences(0).edit().putBoolean("linux_system_keyboard_capture",this.keyboardServiceEnabled()).apply();
         }
         if(this.workspaceSettingsView!=null && this.workspaceSettingsView.getParent()==this.homePanel)
            this.workspaceSettingsView.refreshToggles();
      });
   }

   @Override protected void onPause() {
      if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.suspendSurface();
      VmKeyboardAccessibilityService.background(this);
      this.guestInput.releaseKeys();
      super.onPause();
   }

   @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
      super.onConfigurationChanged(configuration);
      this.hideSystemBars();
      if(this.page!=null) this.page.post(() -> {
         this.fitMavkSurface();
         this.fitEncodedSurface();
         this.updateRuntimeEdgeExclusion();
         if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.resumeSurface();
      });
   }

   private void fitEncodedSurface() {
      android.view.SurfaceView surface=this.installedEncodedSurface;
      if(surface==null || this.page==null) return;
      EncodedDisplayProbe decoder=this.installedEncodedDisplay;
      this.fitGuestSurface(surface,decoder==null?1920:decoder.streamWidth(),decoder==null?1200:decoder.streamHeight());
   }

   private void fitMavkSurface() {
      if(this.mavkSurface==null || this.page==null)return;
      MavkDisplayRuntime renderer=this.mavkDisplay;
      this.fitGuestSurface(this.mavkSurface,renderer==null?1920:renderer.frameWidth(),renderer==null?1200:renderer.frameHeight());
   }

   private void fitGuestSurface(android.view.SurfaceView surface,int width,int height) {
      int pw=this.page.getWidth(),ph=this.page.getHeight();
      int w=Math.min(pw,ph*width/height),h=w*height/width;
      FrameLayout.LayoutParams fit=(FrameLayout.LayoutParams)surface.getLayoutParams();
      if(w>0 && h>0 && (fit.width!=w || fit.height!=h)) {
         fit.width=w;fit.height=h;surface.setLayoutParams(fit);
      }
   }

   private boolean desktopHasFocus() {
      return this.frameVisible && this.homePanel.getVisibility() != View.VISIBLE;
   }

   private boolean guestKeyboardAvailable() {
      return this.desktopHasFocus() && this.displayConnection.hasInput()
            && (this.toolbar == null || this.toolbar.getVisibility() != View.VISIBLE);
   }

   private int panelShortcutKey() {
      return this.getPreferences(0).getInt("runtime_panel_shortcut", KeyEvent.KEYCODE_ESCAPE);
   }

   boolean systemKeyboardCaptureActive() {
      return !this.activityDestroyed && this.hasWindowFocus() && this.guestKeyboardAvailable()
            && !this.transition.visible()
            && this.getPreferences(0).getBoolean("linux_system_keyboard_capture", false);
   }

   boolean runtimePanelShortcut(KeyEvent event) {
      return !this.activityDestroyed && this.hasWindowFocus() && this.desktopHasFocus()
            && !this.transition.visible()
            && this.getPreferences(0).getBoolean("linux_system_keyboard_capture", false)
            && this.guestInput.isPanelShortcut(event, this.panelShortcutKey());
   }

   void finishCapturedAccessibilityKey(KeyEvent event) {
      if (this.systemKeyboardCaptureActive()) this.dispatchKeyEvent(event);
      else this.guestInput.accessibilityKeyReleased(event.getKeyCode());
   }

   void releaseSystemKeyboardCapture() {
      this.guestInput.captureEnded();
   }

   private final DisplayConnection displayConnection = new DisplayConnection();
   private final GuestInput guestInput = new GuestInput(this.displayConnection::send);
   private final OnBackInvokedCallback navigationBackCallback = this::handleBackAction;
   private GuestClipboard guestClipboard;
   private Object clipboardVm;

   @Override protected void onDestroy() {
      this.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(this.navigationBackCallback);
      this.activityDestroyed = true;
      this.detachUbuntuConsole();
      this.dismissEncodedFallback();
      if(this.mavkDisplay!=null){this.mavkDisplay.close();this.mavkDisplay=null;}
      if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.closeSilently();
      if (this.guestControl != null) this.guestControl.close();
      if (this.guestClipboard != null) this.guestClipboard.close();
      this.guestInput.releaseKeys();
      this.displayConnection.close();
      super.onDestroy();
   }

   @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
      return this.guestKeyboardAvailable() ? this.dispatchKeyEvent(event) : super.dispatchKeyShortcutEvent(event);
   }

   @Override public boolean dispatchKeyEvent(KeyEvent event) {
      var focus = new GuestInput.Focus(this.guestKeyboardAvailable(), this.desktopHasFocus(), this.transition.visible());
      if (this.guestInput.handleKey(event, focus, this.panelShortcutKey(), this::toggleSessionDrawer)) return true;
      return super.dispatchKeyEvent(event);
   }

   private void toggleSessionDrawer() {
      if (this.toolbar != null && this.toolbar.getVisibility() == View.VISIBLE) this.closeSessionDrawer();
      else this.openSessionDrawer();
   }

   private void onGuestPointer(int x, int y, int action) {
      long interval = this.mavkDisplay != null ? 8 : 33;
      this.guestInput.pointer(x, y, action, SystemClock.uptimeMillis(), interval);
   }

   private void onGuestRelativePointer(int dx, int dy) {
      if (this.frameVisible) this.guestInput.relativePointer(dx, dy, this.guestFrameWidth, this.guestFrameHeight);
   }

   private int runtimeButtonVisibility() {
      return this.getPreferences(0).getBoolean("hide_runtime_panel_button",false)?View.GONE:View.VISIBLE;
   }

   private void updateRuntimeEdgeExclusion() {
      if(VERSION.SDK_INT<29 || this.page==null)return;
      if(this.frameVisible && this.homePanel.getVisibility()!=View.VISIBLE && this.getPreferences(0).getBoolean("runtime_edge_gesture",false)) {
         int middle=this.page.getHeight()/2;
         int right=this.page.getWidth();
         this.page.setSystemGestureExclusionRects(java.util.Collections.singletonList(new android.graphics.Rect(Math.max(0,right-this.dp(24)),Math.max(0,middle-this.dp(100)),right,Math.min(this.page.getHeight(),middle+this.dp(100)))));
      } else this.page.setSystemGestureExclusionRects(java.util.Collections.emptyList());
   }

   @Override public boolean dispatchTouchEvent(MotionEvent event) {
      if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {
         this.edgeGestureTracking=this.frameVisible && this.homePanel.getVisibility()!=View.VISIBLE &&
            (this.toolbar==null || this.toolbar.getVisibility()!=View.VISIBLE) &&
            this.getPreferences(0).getBoolean("runtime_edge_gesture",false) &&
            !event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getX()>=this.page.getWidth()-this.dp(24) &&
            Math.abs(event.getY()-this.page.getHeight()/2f)<=this.dp(100);
         if(this.edgeGestureTracking) {this.edgeHoldStarted=event.getEventTime();this.edgeHoldX=event.getX();this.edgeHoldY=event.getY();}
      }
      if(this.edgeGestureTracking) {
         if(event.getActionMasked()==MotionEvent.ACTION_MOVE && this.edgeHoldStarted!=Long.MAX_VALUE && event.getEventTime()-this.edgeHoldStarted>=500 &&
            this.edgeHoldX-event.getX()>=this.dp(72) && Math.abs(event.getY()-this.edgeHoldY)<this.dp(80)) {
            this.edgeHoldStarted=Long.MAX_VALUE;
            this.guestInput.releaseKeys();this.openSessionDrawer();
         }
         if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL) this.edgeGestureTracking=false;
         return true;
      }
      return super.dispatchTouchEvent(event);
   }

   private void queueFrameForDisplay(ConsoleFrameDecoder.Frame var1) {
      if (!this.transition.acceptsDesktop()) return;
      FrameSurfaceView var2 = this.frameSurface;
      if (var2 != null) {
         var2.present(var1);
         if (!this.frameVisible && this.frameRevealPostQueued.compareAndSet(false, true)) {
            this.runOnUiThread(() -> {
               this.frameRevealPostQueued.set(false);
               if (!this.frameVisible) {
                  this.frameVisible = true;
                  this.completeDesktopTransition();
                  this.showRunningDesktop();
               }

            });
         }
      }
   }

   private void resetGuestFrameSurface() {
      if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) {
         this.runOnUiThread(this::resetGuestFrameSurface); return;
      }
      this.dismissEncodedFallback();
      this.displayConnection.begin();
      this.closeGuestDisplayViews();
      this.mavkSessionReady = false;
      this.frameVisible = false;
      this.guestFrameWidth = 0;
      this.guestFrameHeight = 0;
      this.guestInput.resetPointer();
      this.frameRevealPostQueued.set(false);
      FrameSurfaceView surface = this.frameSurface;
      if (surface != null) surface.clearFrame();
   }

   private void dismissEncodedFallback() {
      if(this.encodedFallbackDialog!=null) {
         this.encodedFallbackDialog.dismiss();
         this.encodedFallbackDialog=null;
      }
   }

   public void onBackPressed() {
      this.handleBackAction();
   }

   private void handleBackAction() {
      if (this.toolbar != null && this.toolbar.getVisibility() == 0) {
         this.closeSessionDrawer();
      } else if(this.homePanel.getVisibility()==View.VISIBLE) this.navigateBack();
      else if(!this.isManagedVmRunning())this.finish();
   }

   private void navigateBack() {
      Location previous=this.navigation.back(this.frameVisible);
      if(previous==null) {
         if(mainTab(this.productScreen)>0 && this.homePanel.getVisibility()==View.VISIBLE) {
            this.returningBack=true;
            try {this.renderProductScreen(ProductScreen.MODE_SELECTION);}
            finally {this.returningBack=false;}
            return;
         }
         if(!this.isManagedVmRunning())this.finish();
         return;
      }
      if(previous.screen()==null) {
         this.showRunningDesktop();
         if(previous.drawer())this.openSessionDrawer();
         return;
      }
      this.workspaceSettingsTab=previous.settingsTab();
      this.wizardSettingsActive=previous.wizard();
      this.returningBack=true;
      try { this.renderProductScreen(previous.screen()); }
      finally { this.returningBack=false; }
   }

   private boolean isManagedVmRunning() {
      return this.lifecycle.busy() || this.vmMayBeRunning || VmRuntimeService.windowsSetupBusy();
   }

   private void addProductControls() {
      this.homePanel = new FrameLayout(this);
      this.homeWallpaper=FigmaCanvas.wallpaper(this);
      this.homePanel.setBackground(this.homeWallpaper);
      this.page.addView(this.homePanel, new FrameLayout.LayoutParams(-1, -1));
      this.menuButton = this.button("⋯");
      this.menuButton.setTextSize(20.0F);
      this.menuButton.setContentDescription("Open session controls");
      this.menuButton.setOnClickListener((var1x) -> {
         if (this.frameVisible) {
            if (this.toolbar.getVisibility() == 0) {
               this.closeSessionDrawer();
            } else {
               this.openSessionDrawer();
            }

         }
      });
      FrameLayout.LayoutParams var1 = new FrameLayout.LayoutParams(this.dp(42), this.dp(42), 8388661);
      var1.setMargins(0, this.dp(12), this.dp(12), 0);
      this.page.addView(this.menuButton, var1);
      this.toolbar = new LinearLayout(this);
      this.toolbar.setOrientation(1);
      this.toolbar.setElevation((float)this.dp(14));
      this.page.addView(this.toolbar, new FrameLayout.LayoutParams(-1, -1));
      this.toolbar.setVisibility(8);
      this.drawerScrim = new View(this);
      this.drawerScrim.setBackgroundColor(1996488704);
      this.drawerScrim.setVisibility(8);
      this.drawerScrim.setOnClickListener((var1x) -> this.closeSessionDrawer());
      this.page.addView(this.drawerScrim, this.page.indexOfChild(this.toolbar), new FrameLayout.LayoutParams(-1, -1));
      this.guestIme = new GuestIme(this, key -> {
         if (key == 0 || this.homePanel.getVisibility() == View.VISIBLE || this.toolbar.getVisibility() == View.VISIBLE) return;
         this.displayConnection.send(GuestInput.KEY,1,key,0);
         this.displayConnection.send(GuestInput.KEY,0,key,0);
      });
      this.page.addView(this.guestIme, new FrameLayout.LayoutParams(1, 1));
      this.restoreProfile();
   }

   private void openSessionDrawer() {
      if (this.frameVisible && this.toolbar != null) {
         this.guestIme.dismiss();
         this.guestInput.releaseKeys();
         this.restorePointerCaptureAfterDrawer = this.frameSurface != null && this.frameSurface.isPointerCaptured();
         if (this.restorePointerCaptureAfterDrawer) {
            this.frameSurface.releaseCapturedPointer();
         }

         this.populateSessionDrawer();
         this.drawerScrim.animate().cancel();
         this.drawerScrim.setAlpha(0.0F);
         this.drawerScrim.setVisibility(0);
         this.drawerScrim.animate().alpha(0.58F).setDuration(170L).start();
         this.toolbar.animate().cancel();
         this.toolbar.setAlpha(0.0F);
         this.toolbar.setTranslationX((float)this.dp(22));
         this.toolbar.setVisibility(0);
         this.toolbar.animate().alpha(1.0F).translationX(0.0F).setDuration(190L).setInterpolator(new DecelerateInterpolator()).start();
         this.menuButton.setVisibility(8);
         this.menuButton.setContentDescription("Close session controls");
      }
   }

   private void closeSessionDrawer() {
      if (this.toolbar != null && this.toolbar.getVisibility() == 0) {
         this.toolbar.animate().cancel();
         this.toolbar.animate().alpha(0.0F).translationX((float)this.dp(22)).setDuration(150L).withEndAction(() -> {
            this.toolbar.setVisibility(8);
            this.toolbar.setAlpha(1.0F);
            this.toolbar.setTranslationX(0.0F);
            if (this.frameVisible) {
               this.menuButton.setVisibility(this.runtimeButtonVisibility());
               this.menuButton.bringToFront();
                if (this.restorePointerCaptureAfterDrawer && this.frameSurface != null && !this.frameSurface.isPointerCaptured()) {
                  this.frameSurface.requestFocus();
                  this.frameSurface.capturePointer();
               }
            }

            this.restorePointerCaptureAfterDrawer = false;
         }).start();
         this.drawerScrim.animate().alpha(0.0F).setDuration(150L).withEndAction(() -> this.drawerScrim.setVisibility(8)).start();
         this.menuButton.setContentDescription("Open session controls");
      }
   }

   private void closeSessionDrawerImmediately() {
      if (this.toolbar != null) {
         this.toolbar.animate().cancel();
         this.toolbar.setVisibility(8);
         this.toolbar.setAlpha(1.0F);
         this.toolbar.setTranslationX(0.0F);
      }

      if (this.drawerScrim != null) {
         this.drawerScrim.animate().cancel();
         this.drawerScrim.setVisibility(8);
         this.drawerScrim.setAlpha(1.0F);
      }

      if (this.menuButton != null && this.frameVisible) {
         this.menuButton.setVisibility(this.runtimeButtonVisibility());
      }

      this.restorePointerCaptureAfterDrawer = false;
   }

   private void populateSessionDrawer() {
      this.toolbar.removeAllViews();
      boolean windows=this.selectedProfile==LaunchProfile.WINDOWS;
      boolean captured=this.restorePointerCaptureAfterDrawer;
      String graphics=windows?"H.264":DISPLAY_MODES[this.mavkDisplay!=null?2:this.installedEncodedDisplay!=null?1:0];
      Runnable restart=windows?null:()->ProductDialog.show(this,"Перезагрузить Ubuntu?",
            "Сохраните работу в Ubuntu. U-AVF переподключится после перезагрузки.","Перезагрузить",
            false,()->this.requestGuestPower(true));
      FigmaSessionMenu controls=new FigmaSessionMenu(this,captured?"Захват мыши":"Мышь",
            graphics,this::closeSessionDrawer,()->ProductDialog.choose(this,"Мышь","Управление указателем",
                  new String[]{"Захватить указатель","Освободить указатель"},captured?0:1,index->{
                     this.restorePointerCaptureAfterDrawer=index==0;
                     this.closeSessionDrawer();
                  }),this::chooseSessionDisplayMode,()->this.openSessionPreferences("Performance"),
            ()->this.renderProductScreen(ProductScreen.MODE_SELECTION),this::confirmStopVm,restart);
      this.toolbar.addView(controls,new LinearLayout.LayoutParams(-1,-1));
   }

   private void chooseSessionDisplayMode(View anchor) {
      if(this.selectedProfile==LaunchProfile.WINDOWS) {
         this.openSessionPreferences("Display");
         return;
      }
      String[] modes=MavkRuntimeAssets.isBundled(this)?DISPLAY_MODES:Arrays.copyOf(DISPLAY_MODES,2);
      FigmaDropdown.show(anchor,modes,this.configuredDisplayMode().ordinal(),index->{
         Mode requested = Mode.values()[index];
         if(requested==this.configuredDisplayMode())return;
         ProductDialog.show(this,"Графический вывод",
               "Изменение вступит в силу после перезапуска Ubuntu.",
               "Сохранить",false,()->this.saveDisplayMode(requested,true));
      });
   }

   private void saveDisplayMode(Mode mode, boolean userChoice) {
      this.displaySettings().select(mode, userChoice, this.isManagedVmRunning());
   }

   private void requestPlasmaInstall() {
      if(!this.getPreferences(0).getBoolean("linux_internet_enabled",true)) {
         ProductDialog.notice(this,"KDE Plasma X11",
               "Для загрузки Plasma нужен интернет в Ubuntu.");
         return;
      }
      ProductDialog.show(this,"KDE Plasma X11 · Mavk",
            "Ubuntu скачает Plasma из своих репозиториев. После установки перезапустите Ubuntu.",
            "Установить",false,()->{
         GuestControl control=this.guestControl;
         if(control==null || !control.connected() || "installing".equals(this.plasmaInstallState))return;
         this.updatePlasmaInstallState("installing");
         control.installPlasma(ok->this.runOnUiThread(()->{
            if(this.activityDestroyed || this.guestControl!=control)return;
            if(ok)this.getPreferences(0).edit().putBoolean(UbuntuWorkspaceMedia.PLASMA_PENDING,true).apply();
            else {
               this.updatePlasmaInstallState("failed");
               this.show("Could not start Plasma installation.");
            }
         }));
      });
   }

   private void updatePlasmaInstallState(String next) {
      String previous=this.plasmaInstallState;
      this.plasmaInstallState=next;
      var prefs=this.getPreferences(0);
      boolean pending=prefs.getBoolean(UbuntuWorkspaceMedia.PLASMA_PENDING,false);
      if(previous.equals(next) && !(pending && ("ready".equals(next)||"failed".equals(next))))return;
      this.runOnUiThread(()->{
         if(this.activityDestroyed || !next.equals(this.plasmaInstallState))return;
         if(prefs.getBoolean(UbuntuWorkspaceMedia.PLASMA_PENDING,false)) {
            if("ready".equals(next)) {
               this.saveDisplayMode(Mode.MAVK, false);
               prefs.edit().remove(UbuntuWorkspaceMedia.PLASMA_PENDING).apply();
               android.widget.Toast.makeText(this,UiLanguage.text("Plasma установлена. Перезапустите Ubuntu."),
                     android.widget.Toast.LENGTH_LONG).show();
            } else if("failed".equals(next)) {
               prefs.edit().remove(UbuntuWorkspaceMedia.PLASMA_PENDING).apply();
               this.show("Plasma installation failed. See Ubuntu installer diagnostics.");
            }
         }
         if(!previous.equals(next) && this.homePanel.getVisibility()==View.VISIBLE &&
               this.productScreen==ProductScreen.VM_SETTINGS && "Display".equals(this.workspaceSettingsTab))
            this.showFigmaWorkspaceSettings();
      });
   }

   private void openSessionPreferences(String tab) {
      this.wizardSettingsActive=false;
      this.workspaceSettingsTab=tab;
      this.renderProductScreen(ProductScreen.VM_SETTINGS);
   }

   private void openUbuntuStorage() {
      if(this.isManagedVmRunning() && this.selectedProfile==LaunchProfile.WINDOWS) {
         this.show("Завершите сеанс Windows перед изменением ресурсов Ubuntu.");
         return;
      }
      if(!this.isManagedVmRunning())this.selectProfile(LaunchProfile.LINUX);
      this.openSessionPreferences("Storage");
   }

   private void returnToRunningVm() {
      WindowsSetupRuntime setup=VmRuntimeService.windowsSetup();
      if(setup!=null && setup.retryDisplay()) {
         if(this.windowsSetupViewer==setup) {
            this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
            this.startWindowsSetupDisplay(setup);
         }
         else this.attachWindowsSetup(setup);
         return;
      }
      if (!this.frameVisible) {
         if (this.isManagedVmRunning()) {
            if(this.transition.visible())this.transitionTick.run();
            else this.reconnectRunningFrameVmIfPresent();
         } else {
            this.show("There is no running virtual machine to return to.");
         }

      } else {
         this.showRunningDesktop();
      }
   }

   private android.graphics.drawable.Drawable homeWallpaper;

   private void renderProductScreen(ProductScreen var1) {
      ProductScreen from=this.productScreen;
      boolean wasVisible=this.homePanel.getVisibility()==View.VISIBLE;
      this.renderProductContent(var1);
      boolean flatBackground=switch(this.productScreen) {
         case COMPATIBILITY,VM_PERMISSION,DIAGNOSTICS,DIAGNOSTIC_LOG,DIAGNOSTIC_RUNS,INSTALL_STATUS -> true;
         default -> false;
      };
      if(flatBackground)this.homePanel.setBackgroundColor(0xff101113);
      else if(this.homePanel.getBackground()!=this.homeWallpaper)this.homePanel.setBackground(this.homeWallpaper);
      boolean betweenTabs=mainTab(from)>=0 && mainTab(this.productScreen)>=0;
      if((!wasVisible || from!=this.productScreen) && !betweenTabs &&
            this.homePanel.getVisibility()==View.VISIBLE && this.homePanel.getChildCount()>0 &&
            android.animation.ValueAnimator.areAnimatorsEnabled()) {
         View content=this.homePanel.getChildAt(this.homePanel.getChildCount()-1);
         content.setAlpha(.86f);
         content.setTranslationX(this.dp(this.returningBack?-10:10));
         content.animate().alpha(1f).translationX(0f).setDuration(180L)
               .setInterpolator(new DecelerateInterpolator(1.5f)).start();
      }
   }

   private void renderProductContent(ProductScreen var1) {
      if(this.guestIme!=null)this.guestIme.dismiss();
      if (!this.returningBack) {
         this.navigation.opening(var1,
               new Location(this.productScreen, this.workspaceSettingsTab,
                     this.wizardSettingsActive, this.toolbar.getVisibility()==View.VISIBLE),
               this.homePanel.getVisibility()==View.VISIBLE, this.homePanel.getChildCount()>0,
               this.frameVisible && !this.transition.visible());
      }
      switch (var1) {
         case DIAGNOSTICS, DIAGNOSTIC_LOG, DIAGNOSTIC_RUNS -> this.showDiagnosticsScreen(var1);
         case MODE_SELECTION, SYSTEMS, APP_SETTINGS -> this.showMainTabs(var1);
         case ONBOARDING, COMPATIBILITY -> this.showFigmaOnboarding(var1);
         case VM_PERMISSION -> this.showFigmaAvfPermission();
         case INSTALL_STATUS -> this.showFigmaSetupStatus();
         case INSTALL_RECOVERY -> this.showFigmaRecovery();
         case VM_SETTINGS -> this.showFigmaWorkspaceSettings();
         case INPUT_SETTINGS -> {
            this.wizardSettingsActive = false;
            this.workspaceSettingsTab = "Input";
            this.showFigmaWorkspaceSettings();
         }
         case LINUX_HOME, WINDOWS_HOME, CREATE_OS, CREATE_IMAGE, LINUX_SETUP, CREATE_STORAGE, CREATE_REVIEW ->
               this.showFigmaWorkspaceScreen(var1);
      }
   }

   private void enterProductScreen(ProductScreen screen, boolean replaceContent) {
      this.productScreen = screen;
      this.closeSessionDrawerImmediately();
      this.status.setVisibility(View.GONE);
      this.menuButton.setVisibility(View.GONE);
      if (replaceContent) this.homePanel.removeAllViews();
      this.homePanel.setVisibility(View.VISIBLE);
   }

   private void showMainTabs(ProductScreen screen) {
      int previous=mainTab(this.productScreen);
      int selected=mainTab(screen);
      boolean sameHost=this.tabsHost!=null && this.tabsHost.getParent()==this.homePanel
            && this.tabsLanguage==UiLanguage.selected();
      this.enterProductScreen(screen, false);
      if(!sameHost) {
         this.homePanel.removeAllViews();
         this.tabsHost=new FrameLayout(this);
         java.util.function.IntConsumer select=index->this.renderProductScreen(
               index==FigmaMainTabs.HOME?ProductScreen.MODE_SELECTION:
               index==FigmaMainTabs.SYSTEMS?ProductScreen.SYSTEMS:ProductScreen.APP_SETTINGS);
         this.tabsNavigation=new FigmaMainTabs.Navigation(this,selected,select);
         this.tabsPages=new FigmaMainTabs.Pager(this,selected,this.tabsNavigation,select);
         this.tabsHost.addView(this.tabsPages,new FrameLayout.LayoutParams(-1,-1));
         this.tabsHost.addView(this.tabsNavigation,new FrameLayout.LayoutParams(-1,-1));
         this.homePanel.addView(this.tabsHost,new FrameLayout.LayoutParams(-1,-1));
         this.tabsLanguage=UiLanguage.selected();
      } else if(previous!=selected && this.tabsPages.page(selected)!=null) {
         this.productStatus=this.tabsPages.page(selected).notice;
         this.tabsNavigation.select(selected);
         this.tabsPages.show(selected,true);
         return;
      }
      android.content.SharedPreferences prefs=this.getPreferences(0);
      File disk=this.persistentUbuntuDiskFile();
      boolean exists=disk.isFile();
      boolean ready=exists && prefs.getBoolean("ubuntu_installed_runtime_ready",false);
      boolean running=this.isManagedVmRunning();
      boolean windowsRunning=running && this.selectedProfile==LaunchProfile.WINDOWS;
      boolean ubuntuRunning=running && this.selectedProfile!=LaunchProfile.WINDOWS
            && !prefs.getBoolean("linux_current_boot_temporary",false);
      String ubuntuStatus=ubuntuRunning?"Работает":ready?"Выключена":exists?"Настройка не завершена":"Не установлена";
      Runnable ubuntu=()->{
         if(ubuntuRunning) {this.returnToRunningVm();return;}
         if(running) {
            ProductDialog.notice(this,"Ubuntu","Сначала завершите текущий сеанс VM.");
            return;
         }
         this.selectProfile(LaunchProfile.LINUX);
         this.renderProductScreen(ProductScreen.LINUX_HOME);
      };
      Runnable windows=()->{
         if(windowsRunning) {this.returnToRunningVm();return;}
         if(running) {
            ProductDialog.notice(this,"Windows ARM64","Завершите сеанс Ubuntu перед запуском Windows.");
            return;
         }
         this.selectProfile(LaunchProfile.WINDOWS);
         this.renderProductScreen(ProductScreen.WINDOWS_HOME);
      };
      Runnable create=()->this.renderProductScreen(ProductScreen.CREATE_OS);
      Runnable createUbuntu=()->{
         this.createWorkspaceOs="ubuntu";
         this.renderProductScreen(exists?ProductScreen.INSTALL_STATUS:ProductScreen.CREATE_IMAGE);
      };
      Runnable resources=()->{
         if(running && !ubuntuRunning) {
            ProductDialog.notice(this,"Ресурсы Ubuntu","Сначала завершите текущий сеанс VM.");
            return;
         }
         if(!running)this.selectProfile(LaunchProfile.LINUX);
         this.wizardSettingsActive=false;
         this.workspaceSettingsTab="Performance";
         this.renderProductScreen(ProductScreen.VM_SETTINGS);
      };
      Runnable launch=()->{
         if(running){ubuntu.run();return;}
         this.selectProfile(LaunchProfile.LINUX);
         if(ready)new Thread(()->this.startPersistentUbuntu(Request.START_EXISTING),"U-AVF-persistent-ubuntu").start();
         else if(exists)new Thread(this::resumeInstalledUbuntuOrOfferRepair,"U-AVF-resume-installed-ubuntu").start();
         else createUbuntu.run();
      };
      String launchLabel=ubuntuRunning?"Вернуться":ready?"Запустить":exists?"Продолжить":"Создать";
      String diskSize=exists?formatBytes(disk.length()):"—";
      boolean hasIso=this.isoPresent();
      String storage=exists?(hasIso?UiLanguage.text("1 образ")+" · ":"")+diskSize:hasIso?"1 проверенный образ":"Хранилище пусто";
      FigmaMainTabs.Workspace windowsWorkspace=null;
      if(windowsRunning || this.windowsSetupDiskReady()) {
         String status=windowsRunning?"Работает":"Диск Windows сохранён";
         windowsWorkspace=new FigmaMainTabs.Workspace(this.workspaceTitle(true),
               "Windows ARM64 · "+UiLanguage.text(status),status,"—","—","—",
               this.lastDesktopSession(prefs,"windows"),true,windows);
      }
      for(int tab=0;tab<3;tab++) {
         if(sameHost && tab!=selected)continue;
         FigmaMainTabs tabs=new FigmaMainTabs(this,tab,!ready&&!ubuntuRunning,exists&&!ready,
            new FigmaMainTabs.Workspace(this.workspaceTitle(false),"Ubuntu 24.04 · "+UiLanguage.text(ubuntuStatus),ubuntuStatus,
                  UiLanguage.format("%d ГБ",this.workspaceConfig().ramGiB),diskSize,this.configuredDisplayLabel(),
                  this.lastDesktopSession(prefs,"ubuntu"),false,ubuntu),
            windowsWorkspace,
            storage,launchLabel,launch,create,createUbuntu,windows,resources,this::openUbuntuStorage,this::showUbuntuLiveSession,
            ()->this.renderProductScreen(ProductScreen.DIAGNOSTICS),
            !prefs.getBoolean("skip_disk_validation",false),()->{
               prefs.edit().putBoolean("skip_disk_validation",!prefs.getBoolean("skip_disk_validation",false)).apply();
            },()->ProductDialog.notice(this,"U-AVF",this.releaseVersionLabel()),
            ()->this.showLegalDocument("PRIVACY.md"),()->ProductDialog.choose(this,"Лицензии",this.releaseVersionLabel(),
                  new String[]{"U-AVF","Область действия","Apache 2.0","Сторонние компоненты"},-1,index ->
                        this.showLegalDocument(new String[]{"LICENSE-UAVF.txt","LICENSE_SCOPE.md","LICENSE-APACHE-2.0.txt","THIRD_PARTY_NOTICES.txt"}[index])),
            index->{
               UiLanguage.select(this,index);
               this.showMainTabs(ProductScreen.APP_SETTINGS);
            });
         this.tabsPages.page(tab,tabs);
      }
      this.productStatus=this.tabsPages.page(selected).notice;
      this.tabsNavigation.select(selected);
      this.tabsPages.show(selected,false);
   }

   private static int mainTab(ProductScreen screen) {
      return screen == null ? -1 : screen.mainTab();
   }

   private String lastDesktopSession(android.content.SharedPreferences prefs,String os) {
      long time=prefs.getLong("windows".equals(os)?"last_display_windows":"last_desktop_ubuntu",0L);
      return time==0L?"":java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,UiLanguage.locale()).format(new java.util.Date(time));
   }

   private void readinessResult(int index,String result) {
      this.runOnUiThread(() -> {
         if(this.activityDestroyed)return;
         this.readinessResults[index]=result;
         this.readinessCompleted=index+1;
         if(this.productScreen==ProductScreen.COMPATIBILITY)this.renderProductScreen(ProductScreen.COMPATIBILITY);
      });
   }

   private void showFigmaAvfPermission() {
      this.enterProductScreen(ProductScreen.VM_PERMISSION, true);
      String prefix="adb shell pm grant "+this.getPackageName()+" android.permission.";
      String manage=prefix+"MANAGE_VIRTUAL_MACHINE", custom=prefix+"USE_CUSTOM_VIRTUAL_MACHINE";
      FigmaAvfPermission view=new FigmaAvfPermission(this,manage,custom,this.hasAvfPermissions(),
            this::navigateBack,()->{
               if(this.hasAvfPermissions()) {
                  this.navigateBack();
                  this.beginCompatibilityCheck();
               } else {
                  ProductDialog.notice(this,"Доступ к виртуализации","Android ещё не предоставил оба разрешения.");
               }
            },()->{
               this.getSystemService(android.content.ClipboardManager.class).setPrimaryClip(
                     android.content.ClipData.newPlainText("U-AVF",manage+"\n"+custom));
               ProductDialog.notice(this,"Команды скопированы","Выполните обе команды на компьютере, затем проверьте доступ.");
            });
      this.productStatus=view.notice;
      this.homePanel.addView(view,new FrameLayout.LayoutParams(-1,-1));
   }

   private void showFigmaRecovery() {
      this.enterProductScreen(ProductScreen.INSTALL_RECOVERY, true);
      boolean disk=this.persistentUbuntuDiskFile().isFile();
      FigmaRecovery recovery=new FigmaRecovery(this,disk,this.isManagedVmRunning(),this::navigateBack,
            ()->this.renderProductScreen(ProductScreen.INSTALL_STATUS),()->{
               if(this.isManagedVmRunning())return;
               this.selectProfile(LaunchProfile.LINUX);
               if(this.isoPresent())this.launchSelectedProfile();
               else this.renderProductScreen(ProductScreen.CREATE_IMAGE);
            },disk?this::confirmUbuntuReinstallProfile:()->this.renderProductScreen(ProductScreen.CREATE_OS),
            this::confirmDeleteUbuntuWorkspace);
      this.productStatus=recovery.notice;
      this.homePanel.addView(recovery,new FrameLayout.LayoutParams(-1,-1));
   }

   private void showFigmaWorkspaceScreen(ProductScreen screen) {
      this.enterProductScreen(screen, true);
      FigmaCanvas canvas;
      if(screen==ProductScreen.LINUX_HOME || screen==ProductScreen.WINDOWS_HOME) {
         this.wizardSettingsActive=false;
         boolean windows=screen==ProductScreen.WINDOWS_HOME;
         File disk=this.persistentUbuntuDiskFile();boolean exists=disk.isFile();
         boolean ready=exists && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false);
         boolean running=this.isManagedVmRunning() && (this.selectedProfile==LaunchProfile.WINDOWS)==windows
               && (windows || !this.getPreferences(0).getBoolean("linux_current_boot_temporary",false));
         boolean windowsIso=windows && WindowsInstallIso.available();
         boolean windowsSetup=windowsIso && WindowsSetupDisk.available();
         WindowsSetupProgress progress=windowsSetupProgress;
         boolean windowsDiskReady=windowsSetup && this.windowsSetupDiskReady();
         boolean windowsIsoSelected=windowsIso && this.windowsMedia().selected(WindowsWorkspaceMedia.Document.ISO)!=null;
         boolean hasWindowsDisk=windows && java.nio.file.Files.exists(WindowsSetupDisk.workspace(this),
               java.nio.file.LinkOption.NOFOLLOW_LINKS);
         boolean canChooseWindowsMedia=windowsIso && !hasWindowsDisk && progress.phase()==null && !this.isManagedVmRunning();
         boolean chooseWindowsIso=canChooseWindowsMedia && !windowsIsoSelected;
         String state=running?"Работает":windows?(windowsSetup?windowsSetupLabel(progress,windowsDiskReady):
               windowsIsoSelected?"ISO проверен · Experimental":"Экспериментальный запуск Windows"):ready?"Выключена":"Требуется настройка";
         String resources=windows?"Фиксированный профиль":
               UiLanguage.format("%d ГБ",this.workspaceConfig().ramGiB)+" · "+(exists?formatBytes(disk.length()):"—");
         FigmaWorkspaceHome.WindowsDetails windowsDetails=windows?new FigmaWorkspaceHome.WindowsDetails(
               windowsDiskReady?formatBytes(WindowsSetupDisk.TARGET_BYTES):
               hasWindowsDisk?"Настройка не завершена":"Диск ещё не создан",
               windowsIsoSelected?"Образ доступен":"Не выбран",canChooseWindowsMedia?()->{
                  if(this.isManagedVmRunning())return;
                  if(windowsSetup)this.openWindowsSetup();
                  else this.openWindowsIsoPicker();
               }:null):null;
         canvas=new FigmaWorkspaceHome(this,windows,this.workspaceTitle(windows),state,resources,
               windows?"—":this.configuredDisplayLabel(),running?"Вернуться":chooseWindowsIso?"Выбрать ISO…":windowsSetup?(hasWindowsDisk?"Продолжить":"Установка"):windowsIso?"Проверить ISO":windows||ready?"Запустить":exists?"Продолжить":"Создать",() -> {
                  if(running) {this.returnToRunningVm();return;}
                  if(this.isManagedVmRunning()) {this.show("Сначала завершите текущий сеанс VM.");return;}
                  this.selectProfile(windows?LaunchProfile.WINDOWS:LaunchProfile.LINUX);
                  if(windows) {
                     if(chooseWindowsIso)this.openWindowsIsoPicker();
                     else if(windowsSetup)this.openWindowsSetup();
                     else if(windowsIso)this.openWindowsIsoPicker();
                     else this.launchSelectedProfile();
                     return;
                  }
                  if(ready)new Thread(()->this.startPersistentUbuntu(Request.START_EXISTING),"U-AVF-persistent-ubuntu").start();
                  else if(exists)new Thread(this::resumeInstalledUbuntuOrOfferRepair,"U-AVF-resume-installed-ubuntu").start();
                  else this.renderProductScreen(this.isoPresent()?ProductScreen.CREATE_STORAGE:ProductScreen.CREATE_OS);
               },this::navigateBack,()->this.showWorkspaceActions(windows),
               tab->this.openSessionPreferences(FigmaWorkspaceSettings.TABS[tab]),windowsDetails);
         if(windowsSetup && progress.phase()!=null) {
            canvas.widget(new FigmaProgress(this),112,1370,1312,12);
            this.refreshWindowsSetupWhileActive(canvas,progress);
         }
      } else if(screen==ProductScreen.CREATE_STORAGE || screen==ProductScreen.CREATE_REVIEW) {
         this.wizardSettingsActive=false;
         boolean review=screen==ProductScreen.CREATE_REVIEW;File disk=this.persistentUbuntuDiskFile();WorkspaceConfig config=this.workspaceConfig();
         boolean ready=disk.isFile() && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false);
         canvas=new FigmaStorageReview(this,review,disk.isFile()?formatBytes(disk.length()):formatBytes(PersistentUbuntuDisk.expectedDiskBytes(3967463424L,this.newWorkspaceRootBytes())),config.oneCpu?"1 vCPU":"Match-host vCPU",config.ramGiB,this.isoPresent(),this.configuredDisplayLabel(),
               disk.isFile()?ready?"Open existing workspace":"Continue existing setup":"Create & install",()-> {
                  if(!review) {this.renderProductScreen(ProductScreen.CREATE_REVIEW);return;}
                  if(disk.isFile()) {
                     if(ready)this.renderProductScreen(ProductScreen.LINUX_HOME);
                     else if(this.isManagedVmRunning())this.returnToRunningVm();
                     else new Thread(this::resumeInstalledUbuntuOrOfferRepair,"U-AVF-create-resume").start();
                  } else this.confirmUbuntuInstallProfile(false);
               },this::navigateBack,()-> {
                  this.wizardSettingsActive=true;this.workspaceSettingsTab="Performance";this.renderProductScreen(ProductScreen.VM_SETTINGS);
               },this.workspaceRootSizeLabel(),disk.isFile()?null:this::chooseNewWorkspaceRootSize);
      } else if(screen==ProductScreen.CREATE_OS) {
         boolean exists=this.persistentUbuntuDiskFile().isFile();
         canvas=new FigmaCreateView(this,exists,this::navigateBack,()->{
            this.createWorkspaceOs="ubuntu";
            this.renderProductScreen(exists?ProductScreen.LINUX_HOME:ProductScreen.CREATE_IMAGE);
         },()->{
            if(this.isManagedVmRunning() && this.selectedProfile!=LaunchProfile.WINDOWS) {
               this.show("Сначала завершите текущий сеанс VM.");
               return;
            }
            this.selectProfile(LaunchProfile.WINDOWS);
            this.renderProductScreen(ProductScreen.WINDOWS_HOME);
         });
      } else {
         boolean hasDisk=this.persistentUbuntuDiskFile().isFile();
         String name=hasDisk?this.workspaceTitle(false):
               this.draftUbuntuTitle.isEmpty()?UiLanguage.text("Ubuntu · Новый"):this.draftUbuntuTitle;
         String resources=UiLanguage.format("%d ГБ",this.workspaceConfig().ramGiB)+" · "+
               (hasDisk?formatBytes(this.persistentUbuntuDiskFile().length()):this.workspaceRootSizeLabel());
         canvas=new FigmaCreateView(this,name,resources,this.isoPresent(),hasDisk,this::navigateBack,
               ()->ProductDialog.rename(this,name,next->{
                  if(hasDisk)this.getPreferences(0).edit().putString("ubuntu_workspace_title",next).apply();
                  else this.draftUbuntuTitle=next;
                  this.showFigmaWorkspaceScreen(screen);
               }),this::chooseUbuntuMedia,()->this.renderProductScreen(ProductScreen.CREATE_STORAGE),()->{
                  if(hasDisk) {this.renderProductScreen(ProductScreen.LINUX_HOME);return;}
                  if(this.isManagedVmRunning()) {this.show("Сначала завершите текущий сеанс VM.");return;}
                  this.selectProfile(LaunchProfile.LINUX);
                  this.confirmUbuntuInstallProfile(false);
               });
      }
      this.productStatus=canvas.notice;this.productStatus.setText("");
      this.homePanel.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
   }

   private String workspaceTitle(boolean windows) {
      return this.getPreferences(0).getString(windows?"windows_workspace_title":"ubuntu_workspace_title",windows?"Windows":"Ubuntu");
   }

   private void chooseUbuntuMedia() {
      ProductDialog.menu(this,"Установочный ISO",new String[]{"Выбрать файл…","Сайт Ubuntu ↗"},index->{
         if(index==1)this.openOfficialUbuntuDownloadPage();
         else if(this.isManagedVmRunning())this.show("Завершите Ubuntu перед сменой образа.");
         else this.openUbuntuIsoPicker();
      });
   }

   private void showUbuntuLiveSession() {
      if(this.isManagedVmRunning() && this.selectedProfile==LaunchProfile.LINUX
            && this.getPreferences(0).getBoolean("linux_current_boot_temporary",false)) {
         this.returnToRunningVm();
         return;
      }
      if(this.isManagedVmRunning() || VmLifecycle.compatibilityProbeActive()) {
         ProductDialog.notice(this,"Временная Live-сессия","Сначала завершите текущий сеанс VM.");
         return;
      }
      boolean ready=this.isoPresent();
      String[] media={"Выбрать ISO…","Сайт Ubuntu ↗"};
      String[] options=ready?new String[]{"Запустить",media[0],media[1]}:media;
      ProductDialog.choose(this,"Временная Live-сессия","Запуск Ubuntu с ISO без установки",options,-1,index->{
         if(ready && index==0) {
            this.selectProfile(LaunchProfile.LINUX);
            this.launchSelectedProfile();
         } else if(index==(ready?1:0)) {
            this.openInstallDocumentPicker(REQUEST_UBUNTU_LIVE_ISO,false);
         } else {
            this.openOfficialUbuntuDownloadPage();
         }
      });
   }

   private void showWorkspaceActions(boolean windows) {
      boolean canModify=!windows && this.persistentUbuntuDiskFile().isFile() && !this.isManagedVmRunning();
      String[] actions=canModify?new String[]{"Переименовать…","Параметры","Переустановить…","Удалить…"}:
            new String[]{"Переименовать…","Параметры","Диагностика"};
      if(windows && WindowsInstallIso.available() && !this.isManagedVmRunning())
         actions=new String[]{"Переименовать…","Параметры","Диагностика","Запустить прежний образ"};
      ProductDialog.menu(this,"Воркспейс",actions,index->{
         if(index==0)ProductDialog.rename(this,this.workspaceTitle(windows),name->{
            this.getPreferences(0).edit().putString(windows?"windows_workspace_title":"ubuntu_workspace_title",name).apply();
            this.showFigmaWorkspaceScreen(windows?ProductScreen.WINDOWS_HOME:ProductScreen.LINUX_HOME);
         });
         else if(index==1)this.openSessionPreferences("Performance");
         else if(windows && index==3)this.launchSelectedProfile();
         else if(!canModify)this.renderProductScreen(ProductScreen.DIAGNOSTICS);
         else if(index==2)this.confirmUbuntuReinstallProfile();
         else this.confirmDeleteUbuntuWorkspace();
      });
   }

   private void beginCompatibilityCheck() {
      if(this.compatibilityRunning) {
         this.renderProductScreen(ProductScreen.COMPATIBILITY);
         return;
      }
      if(this.isManagedVmRunning() || VmLifecycle.compatibilityProbeActive()) {
         ProductDialog.notice(this,"Проверка устройства","Сначала завершите текущий сеанс VM.");
         return;
      }
      if(this.productScreen!=ProductScreen.ONBOARDING || this.getPreferences(0).contains("compatibility_share_results")) {
         this.renderProductScreen(ProductScreen.COMPATIBILITY);
         return;
      }
      ProductDialog.choose(this,"Статистика совместимости",
            "Разрешите отправить результат этого теста в Firebase. Это поможет нам расширить список поддерживаемых устройств. Передаются модель, конфигурация и результаты проверки AVF.",
            new String[]{"Продолжить без отправки","Разрешить и начать"},-1,index->{
               boolean share=index==1;
               this.getPreferences(0).edit().putBoolean("compatibility_share_results",share).apply();
               this.startCompatibilityCheck(share);
            });
   }

   private void startCompatibilityCheck(boolean shareResult) {
      if(this.vmMayBeRunning || !this.lifecycle.beginCompatibilityProbe()) {
         ProductDialog.notice(this,"Проверка устройства","Сначала завершите текущий сеанс VM.");
         return;
      }
      this.compatibilityRunning=true;this.compatibilityPassed=false;this.readinessCompleted=0;
      this.compatibilityStatus=AvfCompatibilityProbe.Status.INSUFFICIENT_DATA;
      this.appendDiagnosticEvent("Проверка","Событие","Запущена проверка требований устройства");
      Arrays.fill(this.readinessResults,"Waiting");
      this.renderProductScreen(ProductScreen.COMPATIBILITY);
      new Thread(()->this.checkDeviceReadiness(shareResult),"U-AVF-readiness-check").start();
   }

   private void showFigmaOnboarding(ProductScreen screen) {
      boolean pageChanged=this.productScreen!=screen;
      if(!pageChanged && screen==ProductScreen.COMPATIBILITY && this.compatibilityRunning
            && this.compatibilityViewRunning && this.compatibilityView!=null && this.compatibilityView.getParent()==this.homePanel) {
         this.compatibilityView.results(true,this.readinessCompleted,this.readinessResults);
         return;
      }
      this.enterProductScreen(screen, true);
      FigmaCanvas canvas;
      if(screen==ProductScreen.ONBOARDING) {
         canvas=new FigmaWelcomeView(this,this::beginCompatibilityCheck,
            ()->this.showLegalDocument("PRIVACY.md"));
      } else {
         this.compatibilityViewRunning=this.compatibilityRunning;
         this.compatibilityView=new FigmaCompatibilityView(this,this.compatibilityRunning,this.compatibilityStatus,
               this.hasAvfPermissions(),this.getPreferences(0).getBoolean("compatibility_share_results",false),
               share->this.getPreferences(0).edit().putBoolean("compatibility_share_results",share).apply(),
               ()->this.startCompatibilityCheck(this.getPreferences(0).getBoolean("compatibility_share_results",false)),
               ()->this.renderProductScreen(ProductScreen.MODE_SELECTION),
               this::navigateBack,this::skipCompatibilityCheck,()->this.renderProductScreen(ProductScreen.VM_PERMISSION),
               ()->this.renderProductScreen(ProductScreen.DIAGNOSTICS));
         this.compatibilityView.results(this.compatibilityRunning,this.readinessCompleted,this.readinessResults);
         canvas=this.compatibilityView;
      }
      this.productStatus=canvas.notice;this.homePanel.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
   }

   private void skipCompatibilityCheck() {
      if(this.compatibilityRunning)return;
      ProductDialog.show(this,"Skip compatibility test?",
         "This device will remain unverified. You can open the dashboard and repeat the test from Diagnostics. Required VM permissions and image integrity checks still apply.",
         "Continue unverified",false,()-> {
            this.getPreferences(0).edit().putBoolean("first_run_complete",true).apply();
            this.renderProductScreen(ProductScreen.MODE_SELECTION);
         });
   }

   private void uploadCompatibilityReport(String run,CompatibilityUpload.Group group,
         String status,String[] results,String appVersion,String androidRelease,long guestMemoryBytes) {
      new Thread(()->{
         String uploadStatus;
         try {
            org.json.JSONObject fields=new org.json.JSONObject().put("schemaVersion",CompatibilityUpload.integer(2))
               .put("consent",new org.json.JSONObject().put("booleanValue",true))
               .put("appVersion",CompatibilityUpload.value(boundedReportText(appVersion,64)))
               .put("androidRelease",CompatibilityUpload.value(boundedReportText(androidRelease,32)))
               .put("status",CompatibilityUpload.value(status));
            org.json.JSONObject checks=new org.json.JSONObject();String[] keys={"arm64","avf","permissions","platform","created","boot","memory","storage","vsock"};
            for(int i=0;i<keys.length;i++)checks.put(keys[i],CompatibilityUpload.value(results[i].startsWith("Passed")?"pass":results[i].startsWith("Failed")?"fail":"not_tested"));
            fields.put("checks",CompatibilityUpload.map(checks));
            fields.put("device",CompatibilityUpload.map(group.deviceFields()));
            fields.put("avf",CompatibilityUpload.map(new org.json.JSONObject()
                  .put("guestMemoryMiB",CompatibilityUpload.integer(guestMemoryBytes/(1L<<20)))));
            uploadStatus=CompatibilityUpload.send(this,true,run,group,fields);
         } catch(Exception error) {
            uploadStatus="Could not send the compatibility report";
            Log.w("UAVF-Compat","Compatibility submission failed",error);
         }
         final String result=uploadStatus;
         this.runOnUiThread(()->{
            if(!this.activityDestroyed)
               android.widget.Toast.makeText(this,UiLanguage.text(result),android.widget.Toast.LENGTH_LONG).show();
            Log.i("UAVF-Compat",result);
         });
      },"U-AVF-compatibility-upload").start();
   }

   private static String boundedReportText(String value,int max) {return value==null?"":value.substring(0,Math.min(max,value.length()));}
   private void showFigmaSetupStatus() {
      this.enterProductScreen(ProductScreen.INSTALL_STATUS, true);
      FigmaSetupView setup=new FigmaSetupView(this,this.isoPresent(),() -> {
         if(this.frameVisible)this.returnToRunningVm();
         else this.renderProductScreen(this.persistentUbuntuDiskFile().isFile()
               ? ProductScreen.LINUX_HOME : ProductScreen.CREATE_IMAGE);
      },this::navigateBack,() -> this.renderProductScreen(ProductScreen.DIAGNOSTICS));
      this.homePanel.addView(setup,new FrameLayout.LayoutParams(-1,-1));
      this.productStatus=setup.notice;
      Runnable state=new Runnable() {
         @Override public void run() {
            if(activityDestroyed || productScreen!=ProductScreen.INSTALL_STATUS || setup.getParent()!=homePanel)return;
            boolean disk=persistentUbuntuDiskFile().isFile();
            setup.state(lifecycle.install(),disk,disk && getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false),frameVisible);
            setup.postDelayed(this,800);
         }
      };
      state.run();
      new Thread(() -> {
         File report=new File(this.getExternalFilesDir(null),UBUNTU_PERSISTENT_REPORT_NAME);String value=IoUtil.readTail(report,16384);
         this.runOnUiThread(() -> {if(this.productScreen==ProductScreen.INSTALL_STATUS && setup.getParent()==this.homePanel)
            setup.partitionAudit(this.persistentUbuntuDiskFile().isFile() && value.contains("DISK_GPT_AUDIT=PASS"));});
      },"U-AVF-figma-setup-status").start();
   }

   private void resumeInstalledUbuntuOrOfferRepair() {
      if (this.isManagedVmRunning() || !this.lifecycle.beginStorageMutation()) {
         this.show("Stop the active VM before inspecting the installed Ubuntu workspace.");
         return;
      }
      boolean installed;
      try {
         AvfGuest.requireStopped(this, AvfGuest.UBUNTU_VM);
         File disk = this.persistentUbuntuDiskFile();
         boolean layoutValid = PersistentUbuntuDisk.audit(disk, 3967463424L, 34359738368L);
         if (layoutValid && !PersistentUbuntuDisk.hasInstalledBootCandidate(disk)) {
            String recovery = PersistentUbuntuDisk.recoverInstalledEsp(disk, 3967463424L,
                  new File(this.getFilesDir(), "linux-persistent-payload/esp-backups"),
                  Installer.DEFAULT.espSha, Installer.MAVK.espSha, Installer.PLASMA.espSha);
            Log.i("U-AVF", "INSTALLED_ESP_RECOVERY=" + recovery);
         }
         installed = layoutValid && PersistentUbuntuDisk.hasInstalledBootCandidate(disk);
      } catch (Exception failure) {
         this.show("Could not verify the installed Ubuntu handoff: " + rootMessage(failure));
         return;
      } finally {
         this.lifecycle.endStorageMutation();
      }
      if (installed) {
         this.show("Found the completed Ubuntu boot handoff; starting the persistent installation.");
         this.startPersistentUbuntu(Request.FIRST_BOOT);
      } else {
         this.runOnUiThread(() -> this.confirmUbuntuInstallProfile(true));
      }
   }

   private void confirmUbuntuInstallProfile(boolean var1) {
      if(!this.isoPresent()) {
         ProductDialog.show(this,"Нужен образ Ubuntu",
               "Для установки нужен официальный образ Ubuntu 24.04.5 Desktop ARM64. Без него U-AVF не может создать систему, поэтому установка не начинается.",
               "Выбрать образ",false,this::chooseUbuntuMedia);
         return;
      }
      String var2 = var1 ? "U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. Back up any files on the existing Ubuntu system first. Do not change the storage layout in Ubuntu Setup." : "U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. You will choose your language, keyboard and account in Ubuntu Setup; do not change its storage layout.";
      ProductDialog.show(this,var1 ? "Repair or reinstall Ubuntu?" : "Install Ubuntu safely",var2,"Continue to installer",var1,() -> {
         if(!var1 && !this.persistentUbuntuDiskFile().exists() && MavkRuntimeAssets.isBundled(this)
               && this.hasInstallerPrefix(Installer.MAVK.asset)) {
            boolean plasma=this.hasInstallerPrefix(Installer.PLASMA.asset);
            String[] options={"GNOME X11 · H.264 / OpenGL","GNOME X11 · Mavk (экспериментально)","KDE Plasma X11 · Mavk"};
            ProductDialog.choose(this,"Рабочий стол Ubuntu","Выберите рабочий стол и графику. Для загрузки Plasma нужен интернет.",
                  plasma?options:Arrays.copyOf(options,2),0,index->this.launchUbuntuInstaller(false,index+1));
         } else this.launchUbuntuInstaller(var1,0);
      });
   }

   private boolean hasInstallerPrefix(String asset) {
      try(InputStream input=this.getAssets().open(asset)) {
         return input.read()!=-1;
      } catch(IOException absent) { return false; }
   }

   private void launchUbuntuInstaller(boolean existing,int installProfile) {
      if(installProfile==3 && !this.getPreferences(0).getBoolean("linux_internet_enabled",true)) {
         ProductDialog.notice(this,"KDE Plasma X11",
               "Включите интернет в настройках Ubuntu или выберите GNOME. Plasma загружается при установке.");
         return;
      }
      if(!existing && !this.persistentUbuntuDiskFile().exists() && !this.draftUbuntuTitle.isEmpty())
         this.getPreferences(0).edit().putString("ubuntu_workspace_title",this.draftUbuntuTitle).apply();
      new Thread(()->this.startPersistentUbuntu(existing ? Request.START_EXISTING : Request.CREATE,
            Installer.fromStored(installProfile)),"U-AVF-persistent-ubuntu").start();
   }

   private void confirmUbuntuReinstallProfile() {
      ProductDialog.show(this,"Reinstall Ubuntu?","This replaces the Ubuntu system on its dedicated vda3 partition. Personal files and settings on that Ubuntu partition will be erased. The official installer image on vda2 and U-AVF boot partition on vda1 are preserved. The app verifies the disk layout before it starts.","Reinstall Ubuntu",true,() -> (new Thread(() -> this.startPersistentUbuntu(Request.REINSTALL), "U-AVF-ubuntu-reinstall")).start());
   }

   private File persistentUbuntuDiskFile() {
      return new File(new File(this.getFilesDir(), "linux-persistent"), "uavf-ubuntu-persistent-one-disk.raw");
   }

   private volatile boolean storageExpanding;
   private long newWorkspaceRootBytes() {
      long bytes=this.getPreferences(0).getLong("new_workspace_root_bytes",PersistentUbuntuDisk.TARGET_BYTES);
      return bytes>=PersistentUbuntuDisk.TARGET_BYTES && bytes<=1024L*1024*1024*1024 && bytes%(1024*1024)==0?bytes:PersistentUbuntuDisk.TARGET_BYTES;
   }

   private String workspaceRootSizeLabel() {
      try {
         File disk=this.persistentUbuntuDiskFile();
         long bytes=disk.isFile()?PersistentUbuntuDisk.rootBytes(disk,3967463424L):this.newWorkspaceRootBytes();
         return (bytes/(1024*1024*1024))+" GiB";
      } catch(Exception error) {return "Unavailable — disk audit required";}
   }

   private void chooseNewWorkspaceRootSize() {
      if(this.persistentUbuntuDiskFile().isFile()) {this.show("Use Expand Ubuntu storage for an existing workspace.");return;}
      String[] labels={"32 GiB","48 GiB","64 GiB","96 GiB","128 GiB","256 GiB"};
      long[] sizes={32,48,64,96,128,256};
      int selected=-1;
      for(int i=0;i<sizes.length;i++)if(sizes[i]*1024*1024*1024==this.newWorkspaceRootBytes())selected=i;
      ProductDialog.choose(this,"Ubuntu system storage","Choose space for Ubuntu. Boot files and the ISO use additional space.",labels,selected,index->{
            long bytes=sizes[index]*1024*1024*1024;
            if(this.getFilesDir().getUsableSpace()<PersistentUbuntuDisk.expectedDiskBytes(3967463424L,bytes)+2L*1024*1024*1024) {
               this.show("Not enough Android storage for this size, ISO and 2 GiB reserve.");return;
            }
            this.getPreferences(0).edit().putLong("new_workspace_root_bytes",bytes).apply();
            this.renderProductScreen(ProductScreen.CREATE_STORAGE);
         });
   }

   private void showStorageExpansion() {
      if(this.storageExpanding){this.show("Storage operation is already running.");return;}
      if(this.vmMayBeRunning || this.isManagedVmRunning() || this.activeFrameVmName!=null) {
         this.show("Stop Ubuntu before expanding storage.");return;
      }
      if(!this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false)) {
         this.show("Finish Ubuntu installation first.");return;
      }
      if(this.getPreferences(0).getLong("ubuntu_growth_pending_bytes",0)>0) {
         this.show("Start Ubuntu to finish the previous filesystem expansion first.");return;
      }
      if(!this.getPreferences(0).getBoolean("ubuntu_growth_helper_verified",false)) {
         this.showProductNotice("Storage runtime update required","The installed guest has not confirmed automatic ext4 expansion support. Its disk will not be changed. A guest runtime update is required first.");return;
      }
      this.storageExpanding=true;
      new Thread(()->{
         try {
            final long current=PersistentUbuntuDisk.rootBytes(this.persistentUbuntuDiskFile(),3967463424L);
            final long free=this.getFilesDir().getUsableSpace();
            this.runOnUiThread(()->{
               this.storageExpanding=false;
               String[] choices={"Add 8 GiB","Add 16 GiB","Add 32 GiB","Add 64 GiB"};
               ProductDialog.choose(this,"Expand Ubuntu storage","Current Ubuntu storage: "+(current/(1024*1024*1024))+" GiB",choices,-1,index->{
                     long add=(8L<<index)*1024*1024*1024;
                     if(free<add+2L*1024*1024*1024){this.show("Not enough Android storage; keep at least 2 GiB free.");return;}
                     ProductDialog.show(this,"Expand root to "+((current+add)/(1024*1024*1024))+" GiB?",
                        "Ubuntu files, the boot partition and ISO are preserved. The partition grows now; Ubuntu expands ext4 on its next boot. Keep the tablet charged.",
                        "Expand",false,()->this.expandUbuntuStorage(current+add));
                  });
            });
         } catch(Exception error) {this.storageExpanding=false;this.show("Storage check failed: "+rootMessage(error));}
      },"U-AVF-storage-check").start();
   }

   private void showProductNotice(String title,String message) {
      this.runOnUiThread(()->ProductDialog.notice(this,title,message));
   }

   private void expandUbuntuStorage(long bytes) {
      if(this.storageExpanding)return;
      this.storageExpanding=true;
      new Thread(()->{
         try {
            if(!this.lifecycle.beginStorageMutation())throw new IllegalStateException("Stop VM before resizing");
            try {
               if(this.vmMayBeRunning || this.isManagedVmRunning() || this.activeFrameVmName!=null)throw new IllegalStateException("VM is still active");
               File disk=this.persistentUbuntuDiskFile();
               long current=PersistentUbuntuDisk.rootBytes(disk,3967463424L);
               if(this.getFilesDir().getUsableSpace()<bytes-current+2L*1024*1024*1024)throw new IllegalStateException("Insufficient free space");
               File journal=new File(disk.getParentFile(),"root-growth.intent");
               PersistentUbuntuDisk.grow(disk,3967463424L,bytes,journal);
               this.getPreferences(0).edit().putLong("ubuntu_growth_pending_bytes",bytes).apply();
               this.runOnUiThread(()->{this.renderProductScreen(ProductScreen.VM_SETTINGS);this.show("Partition expanded. Start Ubuntu to finish ext4 expansion.");});
            } finally {this.lifecycle.endStorageMutation();}
         } catch(Exception error) {this.show("Storage expansion not completed: "+rootMessage(error));}
         finally {this.storageExpanding=false;}
      },"U-AVF-storage-expand").start();
   }

   private void startPersistentUbuntu(Request request) {
      this.startPersistentUbuntu(request, Installer.DEFAULT);
   }

   private void startPersistentUbuntu(Request request, Installer installer) {
      if (VmRuntimeService.runningVm() != null) {
         this.reconnectRunningFrameVmIfPresent();
         return;
      }
      if (this.isManagedVmRunning()) {
         this.show("Stop the running VM before opening the Ubuntu installer or installed system.");
         return;
      }
      if (!this.lifecycle.beginStart()) return;

      var prefs = this.getPreferences(0);
      boolean firstBoot = request == Request.FIRST_BOOT;
      boolean reinstall = request == Request.REINSTALL;
      boolean installing = reinstall || (!firstBoot && !prefs.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false));
      if (installing) this.lifecycle.install(VmLifecycle.Install.PREPARING);
      else if (firstBoot) this.lifecycle.install(VmLifecycle.Install.FIRST_BOOT);
      this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
      this.resetGuestFrameSurface();

      try {
         AvfGuest.requireStopped(this, AvfGuest.UBUNTU_VM);
         var media = new UbuntuWorkspaceMedia(prefs, this.getFilesDir(), this.getExternalFilesDir(null),
               this.persistentUbuntuDiskFile(), new UbuntuWorkspaceMedia.Assets() {
            @Override public File verified(String asset, File target, long bytes, String sha) throws Exception {
               return MainActivity.this.copyVerifiedAsset(asset, target, bytes, sha);
            }
            @Override public File copy(String asset, File target) throws Exception {
               return MainActivity.this.copyAsset(asset, target, -1);
            }
         });
         long rootBytes = request == Request.CREATE ? this.newWorkspaceRootBytes() : 0;
         var prepared = media.prepare(request, installer, installing, rootBytes);
         if (firstBoot && prepared.firstBootInstaller() != Installer.DEFAULT
               && !prefs.getBoolean(DisplaySettings.FIRST_BOOT_OVERRIDE, false)) {
            this.saveDisplayMode(prepared.firstBootInstaller() == Installer.H264 ? Mode.H264 : Mode.MAVK, false);
         }
         prefs.edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true)
               .putBoolean("linux_gpu_virgl_experimental", false)
               .putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).apply();
         boolean installed = !reinstall && (firstBoot || prefs.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false));
         boolean mavk = installed && this.displaySettings().mavkRequested() && MavkRuntimeAssets.isBundled(this);
         int refreshHz = this.requestedUbuntuRefreshHz();
         Object config = this.buildGenericUbuntuConfig(prepared.kernel(), prepared.disk(), null, null,
               false, AvfGuest.UBUNTU_VM, false, true, mavk, refreshHz);
         boolean handoff = reinstall || (!firstBoot && !prefs.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false));
         this.recordUbuntuDisplayBoot(installed, false, true, mavk, refreshHz);
         if (handoff) prefs.edit().putBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, true)
               .putBoolean(UbuntuWorkspaceMedia.HANDOFF_ATTEMPTED, false).apply();
         this.ubuntuGuestRuntimeRevision = 0;
         Object vm = this.launchUbuntuVm(config, "ubuntu-persistent-serial.log");
         this.startUbuntuVsockHelloProbe(vm, installed);
         this.startUbuntuFrameBridge(vm);
         this.recordUbuntuBoot(prepared, handoff, firstBoot, reinstall, mavk);
         if (handoff) this.scheduleUbuntuInstallerAutostart();
         this.runOnUiThread(() -> {
            this.homePanel.setVisibility(View.GONE);
            this.status.setVisibility(View.VISIBLE);
            this.status.setText(handoff ? "Starting Ubuntu Setup…" : "Starting installed Ubuntu…");
         });
         this.show(handoff ? "Starting Ubuntu install / repair profile; Setup will open only after the Live profile is confirmed."
               : "Starting the installed Ubuntu system.");
      } catch (Exception failure) {
         String reason = rootMessage(failure);
         if (installing || firstBoot) this.lifecycle.install(VmLifecycle.Install.FAILED);
         this.lifecycle.failStart(reason);
         this.transition.failure(reason, SystemClock.elapsedRealtime());
         this.show("Ubuntu could not start: " + reason);
      }
   }

   private void recordUbuntuBoot(UbuntuWorkspaceMedia.Prepared media, boolean handoff, boolean firstBoot,
                                 boolean reinstall, boolean mavk) {
      File report = new File(this.getExternalFilesDir(null), "ubuntu-install-runtime-report.txt");
      try (PrintWriter log = new PrintWriter(new FileOutputStream(report))) {
         log.println("PROFILE=" + (handoff ? "UBUNTU_INSTALL_OR_REPAIR"
               : firstBoot ? "UBUNTU_POST_INSTALL_HANDOFF" : "UBUNTU_PERSISTENT_BOOT"));
         log.println("MAVK_INSTALLED_REQUESTED=" + mavk);
         log.println("FORCE_INSTALL_PROFILE=" + reinstall);
         log.println("ISO_SHA256=2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14");
         log.println("ONE_VIRTIO_BLOCK_DISK=PASS");
         log.println("ESP_PLUS_UNMODIFIED_ISO_PLUS_32G_INSTALL_TARGET=PASS");
         log.println("INSTALLER_FULLSCREEN_REQUEST=" + (handoff ? "PENDING" : "NOT_REQUESTED"));
         log.println("PLATFORM_ESP_UPDATE=" + media.platformUpdate());
         log.println("INSTALLER_WINDOW_VERIFIED=NOT_AVAILABLE_FROM_CURRENT_AVF_API");
         log.println("DISK_BYTES=" + media.disk().length());
         log.println("DISK_GPT_AUDIT=PASS");
         log.println("VM_LAUNCH=PASS");
      } catch (IOException error) {
         Log.w("U-AVF", "Could not save Ubuntu launch report", error);
      }
   }

   private void scheduleUbuntuInstallerAutostart() {
      (new Thread(() -> {
         long var1 = System.currentTimeMillis() + 300000L;

         while(this.vmMayBeRunning && this.ubuntuGuestRuntimeRevision == 0 && System.currentTimeMillis() < var1) {
            try {
               Thread.sleep(250L);
            } catch (InterruptedException var7) {
               Thread.currentThread().interrupt();
               return;
            }
         }

         if (this.vmMayBeRunning && this.ubuntuGuestRuntimeRevision == 1) {
            var1 = System.currentTimeMillis() + 300000L;

            while(this.vmMayBeRunning && !this.displayConnection.hasInput() && System.currentTimeMillis() < var1) {
               try {
                  Thread.sleep(250L);
               } catch (InterruptedException var6) {
                  Thread.currentThread().interrupt();
                  return;
               }
            }

            OutputStream var3 = this.displayConnection.input();
            if (this.vmMayBeRunning && var3 != null) {
               try {
                  Thread.sleep(2500L);
                  this.sendInstallerLaunch(var3);
                  this.lifecycle.install(VmLifecycle.Install.INSTALLING);
                  this.appendUbuntuInstallerAutostartReport("INSTALLER_AUTOSTART=ALLOWLISTED_REQUEST_SENT");
                  Thread.sleep(8000L);
                  this.sendInstallerKey(var3, 65513, true);
                  this.sendInstallerKey(var3, 65479, true);
                  this.sendInstallerKey(var3, 65479, false);
                  this.sendInstallerKey(var3, 65513, false);
                  Thread.sleep(250L);
                  this.sendInstallerKey(var3, 65480, true);
                  this.sendInstallerKey(var3, 65480, false);
                  this.displayConnection.drain();
                  this.appendUbuntuInstallerAutostartReport("INSTALLER_FULLSCREEN_REQUEST=ALT_F10_AND_F11_SENT");
                  this.appendUbuntuInstallerAutostartReport("INSTALLER_WINDOW_VERIFIED=NOT_AVAILABLE_FROM_CURRENT_AVF_API");
                  this.runOnUiThread(() -> this.status.setText("Ubuntu Setup is opening full-screen…"));
               } catch (Throwable var5) {
                  this.appendUbuntuInstallerAutostartReport("INSTALLER_AUTOSTART=FAILED " + rootMessage(var5));
               }

            } else {
               this.appendUbuntuInstallerAutostartReport("INSTALLER_AUTOSTART=NO_FRAME_INPUT_TIMEOUT");
            }
         } else {
            this.appendUbuntuInstallerAutostartReport("INSTALLER_AUTOSTART=SKIPPED_NOT_LIVE_INSTALL_PROFILE guest_revision=" + this.ubuntuGuestRuntimeRevision);
         }
      }, "U-AVF-ubuntu-installer-autostart")).start();
   }

   private void sendInstallerLaunch(OutputStream stream) throws Exception {
      this.displayConnection.sendAndWait(stream, GuestInput.START_INSTALLER, 1, 1, 0, true);
   }

   private void sendInstallerKey(OutputStream stream, int keysym, boolean down) throws Exception {
      this.displayConnection.sendAndWait(stream, GuestInput.KEY, down ? 1 : 0, keysym, 0, false);
   }

   private void appendUbuntuInstallerAutostartReport(String var1) {
      try {
         FileOutputStream var2 = new FileOutputStream(new File(this.getExternalFilesDir((String)null), "ubuntu-install-runtime-report.txt"), true);

         try {
            var2.write((var1 + "\n").getBytes(StandardCharsets.UTF_8));
         } catch (Throwable var6) {
            try {
               var2.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         var2.close();
      } catch (Exception var7) {
         Log.w("U-AVF", "Cannot append installer autostart report", var7);
      }

   }

   private static String formatBytes(long var0) {
      return String.format(Locale.ROOT, "%.0f GB", (double)var0 / (double)1.0737418E9F);
   }

   private void showFigmaWorkspaceSettings() {
      this.enterProductScreen(ProductScreen.VM_SETTINGS, true);
      this.homePanel.bringToFront();
      boolean windows=!this.wizardSettingsActive && this.selectedProfile==LaunchProfile.WINDOWS;
      int tab=Math.max(0,java.util.Arrays.asList(FigmaWorkspaceSettings.TABS).indexOf(this.workspaceSettingsTab));
      int previousTab=this.workspaceSettingsPreviousTab;
      this.workspaceSettingsPreviousTab=-1;
      FigmaWorkspaceSettings view=new FigmaWorkspaceSettings(this,windows,tab,previousTab,this::navigateBack,
            next->this.openWorkspaceSettingsTab(FigmaWorkspaceSettings.TABS[next]));
      this.workspaceSettingsView=view;
      this.productStatus=view.notice;
      this.homePanel.addView(view,new FrameLayout.LayoutParams(-1,-1));
      if(windows)return;
      android.content.SharedPreferences prefs=this.getPreferences(0);
      WorkspaceConfig config=this.workspaceConfig();
      Mode displayMode=this.configuredDisplayMode();
      String onRestart=this.isManagedVmRunning()?"Изменение вступит в силу после перезапуска Ubuntu.":"Настройка применяется при запуске Ubuntu.";
      switch(tab) {
         case 0 -> {
            int[] memory={2,3,4,6};
            int selected=config.ramGiB==2?0:config.ramGiB==3?1:config.ramGiB==6?3:2;
            view.card(476,612);
            view.segments(520,"Память",new String[]{"2 ГБ","3 ГБ","4 ГБ","6 ГБ"},selected,index->{
               if(this.isManagedVmRunning()) {ProductDialog.notice(this,"Память","Завершите Ubuntu перед изменением памяти.");return false;}
               android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();
               this.getSystemService(android.app.ActivityManager.class).getMemoryInfo(info);
               if(memory[index]*1073741824L > info.totalMem-3L*1073741824L) {
                  ProductDialog.notice(this,"Память","Оставьте для Android не менее 3 ГБ.");return false;
               }
               prefs.edit().putInt("linux_ram_gib",memory[index]).apply();
               return true;
            });
            view.segments(736,"Виртуальные процессоры",new String[]{"Все ядра","1 vCPU"},config.oneCpu?1:0,index->{
               if(this.isManagedVmRunning()) {ProductDialog.notice(this,"Процессор","Завершите Ubuntu перед изменением процессора.");return false;}
               prefs.edit().putBoolean("linux_one_cpu",index==1).apply();
               return true;
            });
            view.option(952,"aea17","Графический вывод",DISPLAY_MODES[displayMode.ordinal()],
                  ()->this.openWorkspaceSettingsTab("Display"));
         }
         case 1 -> {
            boolean running=this.isManagedVmRunning();
            boolean canResize=!running;
            boolean showPlasma=running && !this.wizardSettingsActive
                  && this.getPreferences(0).getBoolean("linux_current_boot_installed",false)
                  && !"unavailable".equals(this.plasmaInstallState);
            view.card(476,canResize?450:showPlasma?506:332);
            String[] modes=MavkRuntimeAssets.isBundled(this)?DISPLAY_MODES:Arrays.copyOf(DISPLAY_MODES,2);
            view.displaySelector(516,modes,displayMode.ordinal(),index->{
               Mode requested = Mode.values()[index];
               if(requested==this.configuredDisplayMode())return false;
               if(this.isManagedVmRunning()) {
                  ProductDialog.notice(this,"Графический вывод","Завершите Ubuntu перед сменой режима вывода.");
                  return false;
               }
               this.saveDisplayMode(requested,true);
               view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
               return true;
            },()->{
               if(view.getParent()==this.homePanel && this.productScreen==ProductScreen.VM_SETTINGS &&
                     "Display".equals(this.workspaceSettingsTab))this.showFigmaWorkspaceSettings();
            });
            view.note(702,DISPLAY_DESCRIPTIONS[displayMode.ordinal()]);
            if(canResize) {
               String[] resolutionModes={"1280x800","1920x1080","1920x1200"};
               int selected=java.util.Arrays.asList(resolutionModes).indexOf(this.displayConfig(displayMode).resolution());
               view.choice(780,"aea17","Разрешение",new String[]{"1280 × 800","1920 × 1080","1920 × 1200"},selected,index->{
                  if(this.isManagedVmRunning())return;
                  this.displaySettings().setResolution(displayMode,resolutionModes[index]);
                  this.showFigmaWorkspaceSettings();
               });
            } else view.note(756,"Завершите Ubuntu перед сменой режима вывода.");
            if(showPlasma) {
               String state=this.plasmaInstallState;
               String label=switch(state) {
                  case "installing" -> "Установка идёт…";
                  case "ready" -> "Установлена";
                  case "failed" -> "Повторить установку";
                  default -> "Установить";
               };
               view.option(840,"aea17","KDE Plasma X11 · Mavk",label,
                     "ready".equals(state)||"installing".equals(state)?null:this::requestPlasmaInstall);
            }
         }
         case 2 -> {
            view.card(476,704);
            this.workspaceToggle(view,476,"1bf09","Общий буфер обмена","linux_text_clipboard_enabled",false,"");
            this.workspaceToggle(view,604,"12205","Жест от правого края","runtime_edge_gesture",false,"");
            this.workspaceToggle(view,732,"836de","Скрывать кнопку панели","hide_runtime_panel_button",false,"");
            boolean f12=prefs.getInt("runtime_panel_shortcut",KeyEvent.KEYCODE_ESCAPE)==KeyEvent.KEYCODE_F12;
            view.choice(860,"5312a","Открыть панель VM",
                        new String[]{"Ctrl+Alt+Shift+Esc","Ctrl+Alt+Shift+F12"},f12?1:0,index->{
                           prefs.edit().putInt("runtime_panel_shortcut",index==1?KeyEvent.KEYCODE_F12:KeyEvent.KEYCODE_ESCAPE).apply();
                           this.showFigmaWorkspaceSettings();
                        });
            view.toggle(988,"5312a","Перехват системных сочетаний",this::keyboardCaptureEnabled,this::toggleSystemKeyboardCapture);
            view.note(1112,"Передаёт системные сочетания в VM. Для этого нужна служба U-AVF.");
         }
         case 3 -> {
            view.card(476,256);
            this.workspaceToggle(view,476,"4f479","Звук гостевой системы","linux_audio_output_enabled",true,onRestart);
            this.workspaceToggle(view,604,"ff92e","Интернет","linux_internet_enabled",true,onRestart);
         }
         case 4 -> {
            File disk=this.persistentUbuntuDiskFile();
            view.storage(disk.isFile()?formatBytes(disk.length())+" · диск U-AVF":"Диск ещё не создан",
                  disk.isFile()?this::showStorageExpansion:null,
                  ()->this.renderProductScreen(ProductScreen.INSTALL_RECOVERY));
         }
      }
   }

   private void openWorkspaceSettingsTab(String tab) {
      if(this.workspaceSettingsTab.equals(tab))return;
      this.workspaceSettingsPreviousTab=java.util.Arrays.asList(FigmaWorkspaceSettings.TABS).indexOf(this.workspaceSettingsTab);
      this.workspaceSettingsTab=tab;
      this.showFigmaWorkspaceSettings();
   }

   private boolean keyboardServiceEnabled() {
      var manager=this.getSystemService(android.view.accessibility.AccessibilityManager.class);
      if(manager==null)return false;
      android.content.ComponentName expected=new android.content.ComponentName(this,VmKeyboardAccessibilityService.class);
      for(var info:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
         var service=info.getResolveInfo().serviceInfo;
         if(expected.equals(new android.content.ComponentName(service.packageName,service.name)))return true;
      }
      return false;
   }

   private boolean keyboardCaptureEnabled() {
      return this.getPreferences(0).getBoolean("linux_system_keyboard_capture",false) && this.keyboardServiceEnabled();
   }

   private void toggleSystemKeyboardCapture() {
      if(this.keyboardCaptureEnabled()) {
         this.getPreferences(0).edit().putBoolean("linux_system_keyboard_capture",false).apply();
         this.releaseSystemKeyboardCapture();
      } else if(this.keyboardServiceEnabled()) {
         this.getPreferences(0).edit().putBoolean("linux_system_keyboard_capture",true).apply();
      } else {
         this.awaitingKeyboardCapture=true;
         this.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
      }
   }

   private void workspaceToggle(FigmaWorkspaceSettings view,int y,String icon,String title,String key,
         boolean defaultValue,String note) {
      android.content.SharedPreferences prefs=this.getPreferences(0);
      view.toggle(y,icon,title,()->prefs.getBoolean(key,defaultValue),()->{
         boolean checked=prefs.getBoolean(key,defaultValue);
         android.content.SharedPreferences.Editor edit=prefs.edit().putBoolean(key,!checked);
         if("hide_runtime_panel_button".equals(key) && !checked)edit.putBoolean("runtime_edge_gesture",true);
         if("runtime_edge_gesture".equals(key) && checked)edit.putBoolean("hide_runtime_panel_button",false);
         edit.apply();
         if("linux_text_clipboard_enabled".equals(key))this.syncGuestClipboard();
         view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
         if(!note.isEmpty())this.show(note);
      });
   }

   private String releaseVersionLabel() {
      try {
         android.content.pm.PackageInfo info = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
         return boundedReportText(info.versionName + "/" + info.getLongVersionCode(), 64);
      } catch (android.content.pm.PackageManager.NameNotFoundException error) {
         return "unknown";
      }
   }

   private void showLegalDocument(String name) {
      try (java.io.InputStream source = this.getAssets().open("legal/" + name)) {
         byte[] bytes = source.readNBytes(1048577);
         if (bytes.length > 1048576) throw new java.io.IOException("Document exceeds limit");
         ProductDialog.notice(this, name, new String(bytes, StandardCharsets.UTF_8));
      } catch (java.io.IOException error) {
         ProductDialog.notice(this, "Document unavailable", "Please use the legal documents included with the official APK distribution.");
      }
   }

   private WorkspaceConfig workspaceConfig() {
      return new WorkspaceConfig(this.getPreferences(0).getInt("linux_ram_gib",4),
         this.getPreferences(0).getBoolean("linux_one_cpu",false),
         this.displaySettings().resolution(Mode.FALLBACK));
   }

   private static final String[] DISPLAY_MODES={"Резервный","H.264 + OpenGL","Mavk"};
   private static final String[] DISPLAY_DESCRIPTIONS={
      "Резервный вывод. Ниже частота кадров, можно выбрать разрешение.",
      "Аппаратный OpenGL (VirGL) и передача видео H.264.",
      "GPU Mali и прямой вывод на экран без видеокодека. Экспериментально."
   };

   private DisplaySettings displaySettings() {
      return new DisplaySettings(this.getPreferences(0));
   }

   private Mode configuredDisplayMode() {
      return this.displaySettings().selected(() -> MavkRuntimeAssets.isBundled(this));
   }

   private String configuredDisplayLabel() {
      return this.displayConfig(this.configuredDisplayMode()).resolution().replace('x','×');
   }

   private WorkspaceConfig displayConfig(Mode mode) {
      if (mode == Mode.FALLBACK) return this.workspaceConfig();
      var prefs = this.getPreferences(0);
      return new WorkspaceConfig(prefs.getInt("linux_ram_gib", 4),
            prefs.getBoolean("linux_one_cpu", false), this.displaySettings().resolution(mode));
   }

   private void syncGuestClipboard() {
      boolean enabled = this.getPreferences(0).getBoolean("linux_text_clipboard_enabled", false);
      if (!enabled || this.clipboardVm == null) {
         if (this.guestClipboard != null) this.guestClipboard.close();
         this.guestClipboard = null;
      } else if (this.guestClipboard == null && !this.activityDestroyed && !this.isFinishing()) {
         this.guestClipboard = new GuestClipboard(this, this.clipboardVm);
      }
   }

   private void showDiagnosticsScreen(ProductScreen screen) {
      this.enterProductScreen(screen, true);
      this.recordObservedRuntimeEvents();
      FigmaDiagnostics canvas;
      if(screen==ProductScreen.DIAGNOSTICS) {
         canvas=new FigmaDiagnostics(this,this.diagnosticDeviceStatus(),this.diagnosticGuestStatus(),
               this.frameVisible,this::navigateBack,
               this::beginCompatibilityCheck,()->this.renderProductScreen(ProductScreen.DIAGNOSTIC_RUNS),
               ()->this.renderProductScreen(ProductScreen.DIAGNOSTIC_LOG),this::requestDiagnosticExport,this::showRunningDesktop);
      } else if (screen==ProductScreen.DIAGNOSTIC_RUNS) {
         var preferences=this.getPreferences(0);
         var runs=CompatibilityHistory.read(preferences.getString("compatibility_history_v1",""));
         String legacy=preferences.getString("compatibility_execution_report","");
         canvas=new FigmaDiagnostics(this,query->CompatibilityHistory.search(runs,query,legacy),
               this::navigateBack,this::copyDiagnosticText,
               ()->this.copyDiagnosticText(this.buildDiagnosticSummary()),this::requestDiagnosticExport);
      } else {
         canvas=new FigmaDiagnostics(this,this.diagnosticEntries(),
               this::navigateBack,
               this::copyDiagnosticText,()->this.copyDiagnosticText(this.buildDiagnosticSummary()),this::requestDiagnosticExport);
      }
      this.productStatus=canvas.notice;
      this.homePanel.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
   }

   private String[] diagnosticDeviceStatus() {
      return new String[]{
            java.util.Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a")?"Android "+VERSION.RELEASE+" · ARM64":"ARM64 отсутствует",
            this.readinessResults[1].startsWith("Passed")?"API доступен":
                  this.readinessResults[1].startsWith("Failed")?"API недоступен":"Не проверен",
            this.hasAvfPermissions()?"Предоставлены":"Нет доступа",
            this.readinessResults[3].startsWith("Passed")?"Проверена":
                  this.readinessResults[3].startsWith("Failed")?"Есть ошибка":"Не проверена"};
   }

   private String[] diagnosticGuestStatus() {
      boolean desktop=this.getPreferences(0).getLong("last_desktop_ubuntu",0)>0;
      boolean control=this.selectedProfile!=LaunchProfile.WINDOWS && this.guestControl!=null && this.guestControl.connected();
      String boot=desktop?(control?"Подтверждены":"Загрузка подтверждена"):
            control?"vsock подключён":"Не проверены";
      return new String[]{boot,this.getPreferences(0).getString("last_desktop_graphics_ubuntu","Не проверена")};
   }

   private void recordObservedRuntimeEvents() {
      if(!this.transition.visible())return;
      int generation=this.transition.generation();
      if(this.journalGeneration!=generation){this.journalGeneration=generation;this.journalSeen.clear();}
      boolean win=this.selectedProfile==LaunchProfile.WINDOWS;
      for(String key:this.transition.events().keySet()) {
         if(!this.journalSeen.add(key))continue;
         String label=switch(key) {
            case "Start requested" -> "Запрошен запуск";
            case "Display reconnect requested" -> "Display reconnection requested";
            case "Virtual machine created" -> "Виртуальная машина создана";
            case "UEFI started" -> "Запущен UEFI";
            case "Linux kernel" -> "Подтверждена загрузка ядра Linux";
            case "Windows kernel" -> "Windows kernel resumed after bootstrap";
            case "Windows setup frame" -> "First Windows Setup frame presented";
            case "Windows display reconnecting" -> "Reconnecting to the Windows display";
            case "systemd userspace" -> "Запущены службы systemd";
            case "GDM started" -> "Запущен дисплейный менеджер";
            case "Display connected" -> "Подключён вывод изображения";
            case "Desktop presented" -> this.transition.events().containsKey("Windows setup frame")
                  ?null:win?"First installed Windows frame presented":"Показан первый кадр рабочего стола";
            case "Shutdown requested" -> "Запрошено завершение";
            case "Restart requested" -> "Запрошен перезапуск";
            case "Power request accepted" -> "Гость принял запрос питания";
            case "Filesystem sync" -> "Подтверждена синхронизация файлов";
            case "VM stopped" -> "Виртуальная машина остановлена";
            default -> null;
         };
         if(label!=null)this.appendDiagnosticEvent(
               key.equals("Virtual machine created")||key.equals("VM stopped")?"AVF":win?"Windows":"Ubuntu",
               "Событие",label);
      }
      if(this.transition.mode()==RuntimeTransition.Mode.ERROR && this.journalSeen.add("error"))
         this.appendDiagnosticEvent(win?"Windows":"Ubuntu","Ошибка",
               this.transition.operation()==RuntimeTransition.Mode.DISPLAY_RECONNECT?"Display reconnection failed":
                     win && !this.transition.detail().isBlank()?this.transition.detail():
                     "Операция виртуальной машины завершилась ошибкой");
   }

   private void appendDiagnosticEvent(String component,String level,String event) {
      var prefs = this.getPreferences(0);
      String history = prefs.getString("diagnostic_events_v1", "");
      prefs.edit().putString("diagnostic_events_v1",
            this.journal.append(history, Instant.now(), component, level, event)).apply();
   }

   private java.util.List<DiagnosticJournal.Entry> diagnosticEntries() {
      return this.journal.newestFirst(this.getPreferences(0).getString("diagnostic_events_v1", ""));
   }

   private String buildDiagnosticSummary() {
      this.recordObservedRuntimeEvents();
      String[] device=this.diagnosticDeviceStatus(), guest=this.diagnosticGuestStatus();
      WorkspaceConfig config=this.workspaceConfig();
      File disk=this.persistentUbuntuDiskFile();
      StringBuilder report=new StringBuilder("U-AVF diagnostics\nVersion: ").append(this.releaseVersionLabel())
            .append("\nDevice: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append("\nAndroid: ").append(VERSION.RELEASE).append(" / API ").append(VERSION.SDK_INT)
            .append("\nUbuntu configuration: ").append(config.ramGiB).append(" GiB RAM · ").append(this.configuredDisplayLabel())
            .append("\nUbuntu disk: ").append(disk.isFile()?formatBytes(disk.length()):"Not created")
            .append("\nAVF: ").append(UiLanguage.english(device[1])).append("\nPermissions: ").append(UiLanguage.english(device[2]))
            .append("\nPlatform: ").append(UiLanguage.english(device[3])).append("\nBoot and vsock: ").append(UiLanguage.english(guest[0]))
            .append("\nGraphics: ").append(UiLanguage.english(guest[1]));
      var preferences=this.getPreferences(0);
      long windowsSetup=preferences.getLong("last_setup_windows",0);
      long windowsDisplay=preferences.getLong("last_display_windows",0);
      report.append("\nWindows disk: ").append(this.windowsSetupDiskReady()?"Prepared workspace":"Not prepared");
      if(windowsSetup>0)report.append("\nLast Windows Setup display: ").append(Instant.ofEpochMilli(windowsSetup));
      if(windowsDisplay>0)report.append("\nLast installed Windows display: ").append(Instant.ofEpochMilli(windowsDisplay));
      report.append("\n\nObserved events\n");
      for(DiagnosticJournal.Entry entry:this.diagnosticEntries())report.append(entry.line()).append('\n');
      String execution=CompatibilityHistory.search(
            CompatibilityHistory.read(preferences.getString("compatibility_history_v1","")), "",
            preferences.getString("compatibility_execution_report",""));
      if(!execution.isEmpty())report.append("\nCompatibility reports\n").append(execution);
      return report.toString();
   }

   private void copyDiagnosticText(String text) {
      if(text.isBlank())return;
      android.content.ClipboardManager clipboard=this.getSystemService(android.content.ClipboardManager.class);
      if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("U-AVF",text));
   }

   private void requestDiagnosticExport() {
      Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain").putExtra(Intent.EXTRA_TITLE,"U-AVF-diagnostics-"+LocalDate.now()+".txt");
      this.startActivityForResult(intent,REQUEST_DIAGNOSTIC_EXPORT);
   }

   private String graphicsEvidence() {
      File directory=this.getExternalFilesDir(null);
      for(String name:new String[]{"ubuntu-persistent-serial.log","frame-bridge-test-serial.log"}) {
         String log=IoUtil.readTail(new File(directory,name),131072);
         for(String line:log.split("\\r?\\n")) {
            String lower=line.toLowerCase(Locale.ROOT);
            boolean rendererLine=lower.contains("opengl renderer") || lower.contains("gl_renderer=") || lower.contains("renderer for virgl");
            if(rendererLine && lower.contains("virgl") && lower.contains("mali") &&
                  !lower.contains("llvmpipe") && !lower.contains("softpipe"))
               return "Verified · VirGL / Mali · last boot";
         }
      }
      boolean enabled=this.getPreferences(0).getBoolean("linux_gpu_virgl_gbm_xvnc_experimental",false) &&
         !this.getPreferences(0).getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental",false);
      return enabled?"VirGL enabled · renderer not recorded":"Renderer not checked";
   }

   private void confirmDeleteUbuntuWorkspace() {
      if (!this.isManagedVmRunning() && this.activeFrameVmName == null) {
         ProductDialog.show(this,"Delete Ubuntu workspace?","This permanently deletes U-AVF's persistent Ubuntu workspace disk and installed files. The downloaded official Ubuntu ISO and other app data are kept. This cannot be undone.","Delete workspace",true,() -> (new Thread(this::deleteUbuntuWorkspace, "U-AVF-delete-workspace")).start());
      } else {
         this.show("Stop the running VM before deleting its workspace.");
      }
   }

   private void deleteUbuntuWorkspace() {
      try {
         if(this.storageExpanding)throw new IllegalStateException("Wait for the storage operation before deleting this workspace.");
         if (this.isManagedVmRunning() || this.activeFrameVmName != null) {
            throw new IllegalStateException("The Ubuntu VM is running; it was not deleted.");
         }

         File var1 = this.persistentUbuntuDiskFile();
         String var10000 = this.getFilesDir().getCanonicalPath();
         String var2 = var10000 + File.separator;
         String var3 = var1.getCanonicalPath();
         if (!var3.startsWith(var2) || !var1.getName().equals("uavf-ubuntu-persistent-one-disk.raw")) {
            throw new SecurityException("Refusing to delete a file outside the named app-private workspace.");
         }

         try {
            Class var4 = Class.forName("android.system.virtualmachine.VirtualMachineManager");
            Object var5 = this.getSystemService(var4);
            Object var6 = var4.getMethod("get", String.class).invoke(var5, "winavf-frame-bridge-test-ubuntu-24045");
            if (var6 != null) {
               var4.getMethod("delete", String.class).invoke(var5, "winavf-frame-bridge-test-ubuntu-24045");
            }
         } catch (ClassNotFoundException var7) {
            throw new IllegalStateException("AVF VM record could not be checked; workspace was preserved.", var7);
         }

         if (var1.exists() && !var1.delete()) {
            throw new IllegalStateException("Could not delete the persistent Ubuntu disk.");
         }

         this.getPreferences(0).edit().putBoolean("ubuntu_installed_runtime_ready", false).putBoolean("ubuntu_install_handoff_pending", false).putBoolean("ubuntu_install_handoff_attempted", false).apply();
         this.runOnUiThread(() -> this.renderProductScreen(ProductScreen.MODE_SELECTION));
         this.show("Ubuntu workspace deleted. The verified source ISO was kept.");
      } catch (Throwable var8) {
         this.show("Workspace was not deleted: " + rootMessage(var8));
      }

   }

   private Typeface productFont;
   private Typeface productTypeface() {
      if(this.productFont==null) {
         try {this.productFont=Typeface.createFromAsset(this.getAssets(),"product-ui/LexendDeca.ttf");}
         catch(RuntimeException error) {
            Log.w("U-AVF","Product font unavailable; using system font",error);
            this.productFont=Typeface.create("sans-serif",Typeface.NORMAL);
         }
      }
      return this.productFont;
   }

   private boolean isLightAppearance() {
      return false;
   }

   private int resolveAppearanceTextColor(int var1) {
      if (!this.isLightAppearance()) {
         return var1;
      } else {
         int var2 = Color.red(var1);
         int var3 = Color.green(var1);
         int var4 = Color.blue(var1);
         double var5 = (0.2126 * (double)var2 + 0.7152 * (double)var3 + 0.0722 * (double)var4) / (double)255.0F;
         return !(var5 < 0.48) && !(var5 > 0.72) ? -10918539 : -14669773;
      }
   }

   private void setAppearance(String var1) {
      if (!var1.equals(this.getPreferences(0).getString("appearance_mode", "dark"))) {
         this.getPreferences(0).edit().putString("appearance_mode", var1).apply();
         this.renderProductScreen(this.productScreen);
      }
   }

   private boolean isoPresent() {
      File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
      return var1.isFile() && var1.length() == 3967463424L;
   }

   private boolean hasAvfPermissions() {
      return VERSION.SDK_INT < 31 || this.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE") == 0 && this.checkSelfPermission("android.permission.USE_CUSTOM_VIRTUAL_MACHINE") == 0;
   }

   private void beginRuntimeTransition(RuntimeTransition.Mode mode) {
      // Retain the reboot evidence until its newly started VM produces a frame.
      if (!(mode==RuntimeTransition.Mode.BOOT && this.transition.mode()==RuntimeTransition.Mode.REBOOT))
         this.transition.begin(mode,android.os.SystemClock.elapsedRealtime());
      this.runOnUiThread(() -> {
         if (this.activityDestroyed) return;
         this.closeSessionDrawerImmediately();
         this.guestInput.releaseKeys();
         this.homePanel.setVisibility(8);
         this.menuButton.setVisibility(8);
         this.status.setVisibility(8);
         if (this.transitionView==null) {
            this.transitionView=new RuntimeTransitionView(this,this.transition,
                  ()->this.selectedProfile==LaunchProfile.WINDOWS,
                  ()->this.confirmForceStop(this.transition.operation()==RuntimeTransition.Mode.REBOOT),
                  ()->{this.transition.dismiss();this.transitionView.setVisibility(8);
                     this.renderProductScreen(ProductScreen.MODE_SELECTION);});
            this.page.addView(this.transitionView,new FrameLayout.LayoutParams(-1,-1));
         }
         this.uiHandler.removeCallbacks(this.transitionTick);
         this.transitionTick.run();
      });
   }

   private void showRunningDesktop() {
      if (this.transition.visible()) {
         this.runOnUiThread(this.transitionTick);
         return;
      }
      if (this.transitionView != null) this.transitionView.setVisibility(8);
      this.closeSessionDrawerImmediately();
      this.homePanel.setVisibility(8);
      this.status.setVisibility(8);
      this.menuButton.setVisibility(this.runtimeButtonVisibility());
      this.updateRuntimeEdgeExclusion();
      this.menuButton.setAlpha(0.92F);
      this.menuButton.bringToFront();
      this.menuButton.setContentDescription("Open session controls");
   }

   private void completeDesktopTransition() {
      if(!this.transition.desktop(android.os.SystemClock.elapsedRealtime())) return;
      this.runOnUiThread(this::recordObservedRuntimeEvents);
      String sessionOs=this.selectedProfile==LaunchProfile.WINDOWS?"windows":"ubuntu";
      android.content.SharedPreferences.Editor history=this.getPreferences(0).edit()
            .putLong(this.transition.events().containsKey("Windows setup frame")?"last_setup_windows":
                  "windows".equals(sessionOs)?"last_display_windows":"last_desktop_ubuntu",System.currentTimeMillis());
      if("ubuntu".equals(sessionOs))history.putString("last_desktop_graphics_ubuntu",
            this.mavkDisplay!=null?"Mavk · кадр показан":
                  this.installedEncodedDisplay!=null?"H.264 · кадр показан":"Кадр показан");
      history.apply();
      final int completedGeneration=this.transition.generation();
      this.runOnUiThread(()->{
         if(this.transitionView!=null) this.transitionView.refresh();
         this.uiHandler.postDelayed(()->{
            if(!this.activityDestroyed && this.transition.finishDesktop(completedGeneration)) this.showRunningDesktop();
         },450);
      });
   }

   private void revealRunningControls() {
      if (this.frameVisible && this.menuButton != null) {
         this.menuButton.animate().cancel();
         this.menuButton.setVisibility(this.runtimeButtonVisibility());
         this.menuButton.setAlpha(0.92F);
      }
   }

   private void confirmStopVm() {
      WindowsSetupRuntime setup=VmRuntimeService.windowsSetup();
      if(setup!=null && setup.active()) {
         ProductDialog.show(this,"Windows Setup","Это отключит питание VM. По возможности сначала завершите работу в Windows.",
               "Force",true,()->new Thread(setup::stop,"U-AVF-Windows-stop").start());
         return;
      }
      String os=this.selectedProfile==LaunchProfile.WINDOWS?"Windows":"Ubuntu";
      ProductDialog.show(this,UiLanguage.format("Завершить работу %s?",os),"Сохраните открытые документы перед завершением сеанса.","Завершить",false,() -> {
         this.closeSessionDrawerImmediately();
         (new Thread(this::stopSelectedProfile, "U-AVF-stop-confirmed")).start();
      });
   }

   private final java.util.concurrent.atomic.AtomicBoolean restoringDisplay = new java.util.concurrent.atomic.AtomicBoolean();

   private void reconnectRunningFrameVmIfPresent() {
      WindowsSetupRuntime setup=VmRuntimeService.windowsSetup();
      if(setup!=null && (setup.active() || setup.continueInstalled())) {
         this.attachWindowsSetup(setup);
         return;
      }
      if(!this.restoringDisplay.compareAndSet(false,true))return;
      (new Thread(() -> {
         try {
            Object var1 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
            Object var2 = VmRuntimeService.runningVm();
            if(var2==null)var2=var1.getClass().getMethod("get", String.class).invoke(var1, "winavf-frame-bridge-test-ubuntu-24045");
            if (var2 == null) {
               this.lifecycle.reconcile(null, "STOPPED", this.persistentUbuntuDiskFile().isFile());
               return;
            }

            String var3 = AvfGuest.state(var2);
            this.lifecycle.reconcile(var2, var3, this.persistentUbuntuDiskFile().isFile());
            if (!var3.contains("RUN") && !var3.contains("START")) {
               return;
            }

            this.vmMayBeRunning = true;
            this.activeFrameVmName = "winavf-frame-bridge-test-ubuntu-24045";
            this.selectedProfile = MainActivity.LaunchProfile.LINUX;
            this.attachCallback(var2);
            this.runOnUiThread(() -> {
               if(!this.activityDestroyed && !this.frameVisible && !this.transition.visible())
                  this.beginRuntimeTransition(RuntimeTransition.Mode.DISPLAY_RECONNECT);
            });
            boolean temporary=this.getPreferences(0).getBoolean("linux_current_boot_temporary",false);
            this.attachUbuntuConsole(var2,temporary?"frame-bridge-test-serial.log":"ubuntu-persistent-serial.log");
            this.startUbuntuFrameBridge(var2);
         } catch (Throwable var5) {
            Log.i("U-AVF", "No reconnectable Linux session", var5);
         } finally {
            this.runOnUiThread(()->this.restoringDisplay.set(false));
         }

      }, "U-AVF-reconnect")).start();
   }

   private void openUbuntuIsoPicker() {
      this.openInstallDocumentPicker(REQUEST_UBUNTU_ISO,false);
   }

   protected void onActivityResult(int var1, int var2, Intent var3) {
      super.onActivityResult(var1, var2, var3);
      if(var1==REQUEST_WINDOWS_INSTALL_ISO && var2==RESULT_OK && var3!=null && var3.getData()!=null) {
         Uri source=var3.getData();
         this.startWindowsIsoInspection(source);
         return;
      }
      if(var1==REQUEST_WINDOWS_WINPE && var2==RESULT_OK && var3!=null && var3.getData()!=null) {
         this.selectWindowsWinpe(var3.getData());
         return;
      }
      if(var1==REQUEST_DIAGNOSTIC_EXPORT && var2==RESULT_OK && var3!=null && var3.getData()!=null) {
         Uri destination=var3.getData();
         byte[] report=this.buildDiagnosticSummary().getBytes(StandardCharsets.UTF_8);
         new Thread(()->{
            try(OutputStream file=this.getContentResolver().openOutputStream(destination,"wt")) {
               if(file==null)throw new java.io.IOException("No output stream");
               file.write(report);
            } catch(Exception error) {
               this.show("Не удалось сохранить отчёт.");
               return;
            }
            this.show("Диагностический отчёт сохранён.");
         },"U-AVF-diagnostic-export").start();
         return;
      }
      if ((var1==REQUEST_UBUNTU_ISO || var1==REQUEST_UBUNTU_LIVE_ISO) && var2==RESULT_OK
            && var3!=null && var3.getData()!=null) {
         Uri var4 = var3.getData();
         (new Thread(() -> this.importUbuntuIso(var4,var1==REQUEST_UBUNTU_LIVE_ISO), "U-AVF-import-ubuntu-iso")).start();
      }
   }

   private void openWindowsIsoPicker() {
      this.openWindowsDocumentPicker(REQUEST_WINDOWS_INSTALL_ISO);
   }

   private void openWindowsDocumentPicker(int request) {
      this.openInstallDocumentPicker(request,true);
   }

   private void openInstallDocumentPicker(int request,boolean persistent) {
      Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      if(persistent)intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      try {
         this.startActivityForResult(intent,request);
      } catch(android.content.ActivityNotFoundException|SecurityException error) {
         this.show("Could not open image picker: "+rootMessage(error));
      }
   }

   private boolean windowsSetupDiskReady() {
      File disk=WindowsSetupDisk.workspace(this).toFile();
      return disk.isFile() && disk.length()==WindowsSetupDisk.TARGET_BYTES;
   }

   private static String windowsSetupLabel(WindowsSetupProgress progress,boolean ready) {
      if(ready)return "Диск Windows сохранён";
      if(progress.phase()==null)return progress.error().isEmpty()?
            "Экспериментальная установка Windows":"Не удалось подготовить диск";
      return switch(progress.phase()) {
         case CHECKING_ISO -> "Проверка Windows ISO…";
         case CHECKING -> "Проверка установочных файлов…";
         case COPYING -> "Копирование шаблона WinPE…";
         case FINALIZING -> "Проверка разметки диска…";
      };
   }

   private void refreshWindowsSetupWhileActive(View screen,WindowsSetupProgress seen) {
      this.homePanel.postDelayed(()->{
         if(this.activityDestroyed || this.productScreen!=ProductScreen.WINDOWS_HOME ||
               screen.getParent()!=this.homePanel)return;
         if(windowsSetupProgress!=seen)this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
         else this.refreshWindowsSetupWhileActive(screen,seen);
      },700);
   }

   /** Reads a file from the FAT32 ESP that starts at byte 1 MiB of the image. */
   private static byte[] readEspFile(File disk, String... path) throws Exception {
      try (var volume = Fat32Volume.open(disk)) {
         byte[] data = volume.readFile(path);
         if (data == null) throw new IllegalStateException("FAT entry not found: " + String.join("/", path));
         return data;
      }
   }

   /** Reads an 8.3 named file from the FAT32 root directory. */
   private static byte[] readEspRootFile(File disk, String name, String extension, int maximumBytes)
         throws Exception {
      try (var volume = Fat32Volume.open(disk)) {
         return volume.readRootFile(name, extension, maximumBytes);
      }
   }

   private WindowsWorkspaceMedia windowsMedia() {
      return new WindowsWorkspaceMedia(this.getApplicationContext(), this.getPreferences(0));
   }

   private void openWindowsSetup() {
      if(windowsSetupProgress.phase()!=null) {
         ProductDialog.notice(this,"Windows ARM64",windowsSetupLabel(windowsSetupProgress,false));
         return;
      }
      java.nio.file.Path workspace=WindowsSetupDisk.workspace(this);
      if(java.nio.file.Files.exists(workspace,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
         this.inspectPreparedWindowsDisk(workspace);
         return;
      }
      Uri iso=this.windowsMedia().selected(WindowsWorkspaceMedia.Document.ISO);
      Uri winpe=this.windowsMedia().selected(WindowsWorkspaceMedia.Document.WINPE);
      long required=WindowsSetupDisk.requiredFreeBytes(),free=this.getFilesDir().getUsableSpace();
      String info=UiLanguage.format("Нужны Windows 11 ARM64 25H2 (ru-RU), сборка %s, и соответствующий шаблон WinPE.",
            WindowsSetupDisk.INSTALL_BUILD)
            +"\n"+WindowsSetupDisk.REFERENCE_TEMPLATE
            +"\n\n"+UiLanguage.format("Свободно %s · требуется %s",formatBytes(free),formatBytes(required));
      String failure=windowsSetupProgress.error();
      if(!failure.isEmpty())info+="\n\n"+failure;
      boolean ready=iso!=null && winpe!=null;
      String[] options={iso!=null?"Заменить ISO…":"Выбрать ISO…",
            winpe!=null?"Заменить шаблон WinPE…":"Выбрать шаблон WinPE…","Подготовить диск"};
      ProductDialog.choose(this,"Установка Windows ARM64",info,
            ready?options:Arrays.copyOf(options,2),-1,index->{
               if(index==0)this.openWindowsIsoPicker();
               else if(index==1)this.openWindowsDocumentPicker(REQUEST_WINDOWS_WINPE);
               else this.startWindowsSetupPreparation(iso,winpe);
            });
   }

   private WindowsSetupRuntime windowsSetupViewer;
   private boolean windowsSetupLayoutChecking;

   private void inspectPreparedWindowsDisk(java.nio.file.Path disk) {
      if (this.windowsSetupLayoutChecking) return;
      this.windowsSetupLayoutChecking = true;
      var media = this.windowsMedia();
      new Thread(() -> {
         WindowsWorkspaceMedia.BootOptions options = null;
         try {
            options = media.inspect(disk);
         } catch (IOException | LinkageError error) {
            Log.w("UAVF-Windows-Setup", "Windows disk layout check failed", error);
         }
         var result = options;
         this.runOnUiThread(() -> this.showWindowsBootOptions(result));
      }, "U-AVF-Windows-disk-layout").start();
   }

   private void showWindowsBootOptions(WindowsWorkspaceMedia.BootOptions options) {
      this.windowsSetupLayoutChecking = false;
      if (this.activityDestroyed || this.productScreen != ProductScreen.WINDOWS_HOME || this.isManagedVmRunning()) return;
      if (options == null) {
         ProductDialog.notice(this, "Windows Setup",
               "Не удалось проверить разметку диска Windows. Подробности доступны в журнале.");
         return;
      }
      switch (options) {
         case INSTALLED -> ProductDialog.show(this, "Windows ARM64",
               "Загрузчик установленной Windows проверен. При первом запуске продолжится настройка системы.",
               "Запустить", false, () -> this.startPreparedWindowsSetup(WindowsSetupRuntime.Boot.INSTALLED));
         case PREVIEW_AND_INSTALL -> this.offerWindowsSetupBoot(true);
         case PREVIEW -> this.offerWindowsSetupBoot(false);
         case PENDING_INSTALL -> ProductDialog.notice(this, "Диск Windows сохранён",
               "Найден раздел NTFS. Завершение установки не подтверждено. Повторный запуск пока недоступен.");
         case PARTITIONED -> ProductDialog.notice(this, "Диск Windows сохранён",
               "Диск уже размечен. Повторный запуск пока недоступен.");
         case UNAVAILABLE -> ProductDialog.notice(this, "Диск Windows подготовлен",
               "Диск сохранён в U-AVF. Запуск Windows Setup с этого диска пока недоступен.");
      }
   }

   private void offerWindowsSetupBoot(boolean guided) {
      long required=WindowsSetupDisk.requiredInstallFreeBytes(), available=this.getFilesDir().getUsableSpace();
      if(guided && available>=required) {
         ProductDialog.choose(this,"Windows Setup",
               "Установка Windows 11 Pro начнётся автоматически после подтверждения.",
               new String[]{"Открыть WinPE","Установить Windows"},-1,index->{
                  if(index==0)this.startPreparedWindowsSetup(WindowsSetupRuntime.Boot.PREVIEW);
                  else ProductDialog.show(this,"Установить Windows",
                        "U-AVF создаст разделы на подготовленном виртуальном диске и начнёт установку Windows 11 Pro.",
                        "Установить",false,()->this.startPreparedWindowsSetup(WindowsSetupRuntime.Boot.INSTALL));
               });
         return;
      }
      String description=guided?UiLanguage.format("Для автоматической установки нужно %1$s свободного места. Сейчас доступно %2$s.",
            formatBytes(required),formatBytes(available)):
            UiLanguage.text("Можно открыть WinPE и проверить экран и ввод. Автоматическая установка пока недоступна в этой сборке.");
      ProductDialog.show(this,"Windows Setup",description,"Открыть WinPE",false,
            ()->this.startPreparedWindowsSetup(WindowsSetupRuntime.Boot.PREVIEW));
   }

   private void startPreparedWindowsSetup(WindowsSetupRuntime.Boot boot) {
      if(this.isManagedVmRunning() || VmLifecycle.compatibilityProbeActive())return;
      if(!this.hasAvfPermissions()) {
         this.show(UiLanguage.text("Для запуска нужны разрешения AVF."));
         return;
      }
      try {
         if(VmRuntimeService.startWindowsSetup(this,boot))this.attachWindowsSetup(VmRuntimeService.windowsSetup());
      } catch(RuntimeException error) {
         ProductDialog.notice(this,"Windows Setup",rootMessage(error));
      }
   }

   private void attachWindowsSetup(WindowsSetupRuntime setup) {
      if(this.activityDestroyed || this.windowsSetupViewer==setup)return;
      this.windowsSetupViewer=setup;
      this.selectedProfile=LaunchProfile.WINDOWS;
      if(setup.rebootSuccessor()) {
         if(this.transition.mode()!=RuntimeTransition.Mode.REBOOT)
            this.beginRuntimeTransition(RuntimeTransition.Mode.REBOOT);
         this.transition.event("VM stopped","AVF reboot callback",android.os.SystemClock.elapsedRealtime());
      } else {
         this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
      }
      this.watchWindowsSetup(setup,null);
   }

   private void watchWindowsSetup(WindowsSetupRuntime setup, WindowsSetupRuntime.Phase previous) {
      if(this.activityDestroyed || this.windowsSetupViewer!=setup)return;
      WindowsSetupRuntime.Phase phase=setup.phase();
      long now=android.os.SystemClock.elapsedRealtime();
      if(this.transition.visible()) {
         if(setup.installing())this.transition.event("Windows installation","Guided install requested",now);
         else if(setup.setupEnvironment())this.transition.event("Windows setup","WinPE requested",now);
      }
      if(phase!=previous) {
         if(phase==WindowsSetupRuntime.Phase.BOOTING)
            this.transition.event("Virtual machine created","Windows Setup AVF owner",now);
         if(phase==WindowsSetupRuntime.Phase.WAITING_DISPLAY || phase==WindowsSetupRuntime.Phase.DISPLAYING) {
            this.transition.event("Windows kernel","KD readback and continue",now);
            if(this.installedEncodedDisplay==null)this.startWindowsSetupDisplay(setup);
         }
         if(phase==WindowsSetupRuntime.Phase.STOPPING && setup.restarting()) {
            this.detachWindowsSetupDisplay();
            this.beginRuntimeTransition(RuntimeTransition.Mode.REBOOT);
            this.transition.event("VM stopped","AVF reboot callback",now);
         }
      }
      if(phase==WindowsSetupRuntime.Phase.DISPLAY_UNAVAILABLE && setup.retryDisplay())
         this.startWindowsSetupDisplay(setup);
      if(setup.active()) {
         this.uiHandler.postDelayed(()->this.watchWindowsSetup(setup,phase),500);
         return;
      }
      if(setup.continueInstalled()) {
         WindowsSetupRuntime installed=VmRuntimeService.windowsSetup();
         if(installed==setup) {
            this.uiHandler.postDelayed(()->this.watchWindowsSetup(setup,phase),500);
         } else if(installed!=null) {
            this.detachWindowsSetupDisplay();
            this.attachWindowsSetup(installed);
         }
         return;
      }
      this.windowsSetupViewer=null;
      this.detachWindowsSetupDisplay();
      if(phase==WindowsSetupRuntime.Phase.FAILED) {
         this.transition.failure(setup.error(),now);
         this.transitionTick.run();
      } else {
         this.transition.dismiss();
         if(this.transitionView!=null)this.transitionView.setVisibility(View.GONE);
         this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
      }
   }

   private void detachWindowsSetupDisplay() {
      this.guestInput.releaseKeys();
      this.displayConnection.begin();
      if(this.installedEncodedDisplay!=null)this.installedEncodedDisplay.closeSilently();
      this.installedEncodedDisplay=null;
      if(this.installedEncodedSurface!=null)this.page.removeView(this.installedEncodedSurface);
      this.installedEncodedSurface=null;
      this.frameSurface.setEncodedMode(false,0,0);
      this.frameVisible=false;
   }

   private void startWindowsSetupDisplay(WindowsSetupRuntime setup) {
      if(this.windowsSetupViewer!=setup || (setup.phase()!=WindowsSetupRuntime.Phase.WAITING_DISPLAY
            && setup.phase()!=WindowsSetupRuntime.Phase.DISPLAYING))return;
      if(this.installedEncodedDisplay!=null)this.installedEncodedDisplay.closeSilently();
      if(this.installedEncodedSurface!=null)this.page.removeView(this.installedEncodedSurface);
      int connectionEpoch = this.displayConnection.begin();
      android.view.SurfaceView surface=new android.view.SurfaceView(this);
      this.installedEncodedSurface=surface;
      this.page.addView(surface,0,new FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER));
      surface.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->this.fitEncodedSurface());
      EncodedDisplayProbe receiver=new EncodedDisplayProbe(this,setup,60f,()->{
         if(this.installedEncodedSurface!=surface || this.activityDestroyed || !setup.active())return;
         this.displayConnection.publishInput(connectionEpoch, null);
         this.frameVisible=false;
         setup.displayFailed();
         // Windows can replace its capture session during sign-in. Give it time
         // to reconnect before taking over the screen with a status panel.
         this.uiHandler.postDelayed(()->{
            if(this.windowsSetupViewer!=setup || !setup.active() || this.frameVisible
                  || this.transition.mode()==RuntimeTransition.Mode.REBOOT)return;
            if(!this.transition.visible())this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
            if(setup.installing())this.transition.event("Windows installation","Guided install active",
                  android.os.SystemClock.elapsedRealtime());
            else if(setup.setupEnvironment())this.transition.event("Windows setup","WinPE active",
                  android.os.SystemClock.elapsedRealtime());
            this.transition.event("Windows display reconnecting","Waiting for replacement stream",
                  android.os.SystemClock.elapsedRealtime());
         },2500);
      },640,480);
      receiver.acceptNativeWindowsMode();
      receiver.onStreamGeometry(()->{
         if(this.installedEncodedSurface!=surface || this.activityDestroyed)return;
         this.guestFrameWidth=receiver.streamWidth();
         this.guestFrameHeight=receiver.streamHeight();
         this.fitEncodedSurface();
      });
      this.installedEncodedDisplay=receiver;
      receiver.startEmbedded(surface,input->{
         if(this.installedEncodedSurface==surface)this.displayConnection.publishInput(connectionEpoch, input);
      },()->{
         if(this.installedEncodedSurface!=surface || this.activityDestroyed || !setup.displayReady())return;
         this.guestFrameWidth=receiver.streamWidth();
         this.guestFrameHeight=receiver.streamHeight();
         this.fitEncodedSurface();
         this.frameSurface.setEncodedMode(true,this.guestFrameWidth,this.guestFrameHeight);
         this.frameVisible=true;
         if(setup.setupEnvironment())
            this.transition.event("Windows setup frame","Validated WinPE Surface frame",
                  android.os.SystemClock.elapsedRealtime());
         this.completeDesktopTransition();
         this.showRunningDesktop();
      },()->{
         if(this.installedEncodedSurface==surface && !this.activityDestroyed)
            this.startWindowsSetupDisplay(setup);
      });
   }

   private void selectWindowsWinpe(Uri source) {
      new Thread(()->{
         try {
            this.windowsMedia().selectWinpe(source);
            this.runOnUiThread(()->{
               if(!this.activityDestroyed && this.productScreen==ProductScreen.WINDOWS_HOME)this.openWindowsSetup();
            });
         } catch(IOException|SecurityException error) {
            this.runOnUiThread(()->{
               if(!this.activityDestroyed)ProductDialog.notice(this,"Windows ARM64",rootMessage(error));
            });
         }
      },"U-AVF-windows-winpe").start();
   }

   private void startWindowsSetupPreparation(Uri iso,Uri template) {
      if(iso==null || template==null || windowsSetupProgress.phase()!=null || this.isManagedVmRunning())return;
      var media = this.windowsMedia();
      windowsSetupProgress=new WindowsSetupProgress(WindowsSetupDisk.Phase.CHECKING,"");
      this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
      new Thread(()->{
         try {
            media.prepare(iso, template, phase -> windowsSetupProgress = new WindowsSetupProgress(phase, ""));
            windowsSetupProgress=new WindowsSetupProgress(null,"");
         } catch(IOException|RuntimeException|LinkageError error) {
            Log.w("UAVF-Windows-Setup","Preparation failed",error);
            windowsSetupProgress=new WindowsSetupProgress(null,rootMessage(error));
         }
      },"U-AVF-windows-prepare").start();
   }

   private void startWindowsIsoInspection(Uri source) {
      if(windowsSetupProgress.phase()!=null) {
         // A silent return made a stuck phase look like a dead button.
         Log.w("UAVF-Windows-ISO","Selection ignored: Windows step already running ("
               +windowsSetupProgress.phase()+")");
         this.show(UiLanguage.text("Подготовка Windows уже выполняется. Дождитесь завершения."));
         return;
      }
      windowsSetupProgress=new WindowsSetupProgress(WindowsSetupDisk.Phase.CHECKING_ISO,"");
      if(this.productScreen==ProductScreen.WINDOWS_HOME)this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
      new Thread(()->this.inspectWindowsIso(source),"U-AVF-windows-iso").start();
   }

   private void inspectWindowsIso(Uri source) {
      try {
         var result = this.windowsMedia().selectIso(source);
         windowsSetupProgress=new WindowsSetupProgress(null,"");
         Log.i("UAVF-Windows-ISO","ARM64_EFI=PASS BOOT_WIM_BYTES="+result.bootWimBytes()
               +" INSTALL_IMAGE="+(result.esd()?"ESD":"WIM")+" INSTALL_BYTES="+result.installBytes());
         this.runOnUiThread(()->{
            if(this.activityDestroyed || this.productScreen!=ProductScreen.WINDOWS_HOME)return;
            this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
            if(WindowsSetupDisk.available())this.openWindowsSetup();
            else ProductDialog.notice(this,"Windows ARM64","Образ содержит загрузчик ARM64 и файлы установки. Загрузка Setup из ISO пока недоступна.");
         });
      } catch(IOException|RuntimeException|LinkageError error) {
         String detail=rootMessage(error);
         windowsSetupProgress=new WindowsSetupProgress(null,detail);
         Log.w("UAVF-Windows-ISO","ISO check failed: "+error.getClass().getSimpleName()+": "+detail,error);
         this.runOnUiThread(()->{
            if(!this.activityDestroyed && this.productScreen==ProductScreen.WINDOWS_HOME) {
               this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
               ProductDialog.notice(this,"Windows ARM64",detail);
            }
         });
      }
   }

   private void onUbuntuIsoImported(boolean live) {
      this.runOnUiThread(()->{
         if(this.activityDestroyed)return;
         this.refreshUbuntuIsoStatus();
         if(live) {
            if(this.productScreen==ProductScreen.SYSTEMS && !this.isManagedVmRunning())
               this.showUbuntuLiveSession();
         } else if(!this.isManagedVmRunning()) {
            this.selectProfile(LaunchProfile.LINUX);
            this.renderProductScreen(this.productScreen==ProductScreen.CREATE_IMAGE?ProductScreen.CREATE_IMAGE:ProductScreen.LINUX_HOME);
         }
      });
   }

   private void importUbuntuIso(Uri var1, boolean live) {
      File var2 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
      File var3 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso.importing");

      try {
         this.show("Checking the selected official Ubuntu image…");
         String var4 = this.sha256Uri(var1);
         if (!"2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(var4)) {
            throw new SecurityException("This is not the verified Ubuntu 24.04.5 ARM64 desktop ISO.");
         }

         MessageDigest var5 = MessageDigest.getInstance("SHA-256");
         long var6 = 0L;
         if (var2.isFile() && var2.length() == 3967463424L && "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(Digest.hex(Digest.sha256File(var2)))) {
            this.onUbuntuIsoImported(live);
            this.show("The selected Ubuntu image matches the copy already staged in U-AVF.");
            return;
         }

         InputStream var8 = this.getContentResolver().openInputStream(var1);

         try {
            if (var8 == null) {
               throw new IllegalStateException("Could not read the selected file");
            }

            DigestInputStream var9 = new DigestInputStream(new BufferedInputStream(var8), var5);

            try {
               FileOutputStream var10 = new FileOutputStream(var3, false);

               try {
                  byte[] var11 = new byte[1048576];

                  int var12;
                  while((var12 = var9.read(var11)) >= 0) {
                     if (var12 != 0) {
                        var6 += (long)var12;
                        if (var6 > 3967463424L) {
                           throw new IllegalArgumentException("Selected image is larger than the supported Ubuntu ISO");
                        }

                        var10.write(var11, 0, var12);
                        if ((var6 & 536870911L) < (long)var12) {
                            long progressBytes = var6;
                            this.runOnUiThread(() -> this.show("Importing Ubuntu image: " + progressBytes / 1048576L + " MiB"));
                        }
                     }
                  }

                  var10.getFD().sync();
               } catch (Throwable var20) {
                  try {
                     var10.close();
                  } catch (Throwable var18) {
                     var20.addSuppressed(var18);
                  }

                  throw var20;
               }

               var10.close();
            } catch (Throwable var21) {
               try {
                  var9.close();
               } catch (Throwable var17) {
                  var21.addSuppressed(var17);
               }

               throw var21;
            }

            var9.close();
         } catch (Throwable var22) {
            if (var8 != null) {
               try {
                  var8.close();
               } catch (Throwable var16) {
                  var22.addSuppressed(var16);
               }
            }

            throw var22;
         }

         if (var8 != null) {
            var8.close();
         }

         String var24 = Digest.hex(var5.digest());
         if (var6 != 3967463424L || !"2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(var24)) {
            var3.delete();
            throw new SecurityException("This is not the verified Ubuntu 24.04.5 ARM64 desktop ISO (size/hash mismatch)");
         }

         try {
            Files.move(var3.toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var19) {
            Files.move(var3.toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         this.onUbuntuIsoImported(live);
         this.show("Official Ubuntu 24.04.5 ARM64 image verified and ready.");
      } catch (Throwable var23) {
         try {
            var3.delete();
         } catch (Throwable var15) {
         }

         this.show("Ubuntu image was not accepted: " + rootMessage(var23));
      }

   }

   private void openOfficialUbuntuDownloadPage() {
      try {
         this.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://cdimage.ubuntu.com/ubuntu/releases/24.04.5/release/")));
      } catch(android.content.ActivityNotFoundException error) {
         this.show("No browser available. Open cdimage.ubuntu.com to download Ubuntu Desktop ARM64.");
      }
   }

   private void checkDeviceReadiness(boolean shareResult) {
      String[] checks=new String[FigmaCompatibilityView.CHECKS.length];
      Arrays.fill(checks,"Not checked");
      AvfCompatibilityProbe.Status outcome=AvfCompatibilityProbe.Status.INSUFFICIENT_DATA;
      String report="";
      String reason="";
      long guestMemoryBytes=0;
      try {
         boolean arm64=Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
         checks[0]=arm64?"Passed · Android "+VERSION.RELEASE+" / ARM64":"Failed · ARM64 required";
         this.readinessResult(0,checks[0]);
         boolean managerAvailable=false;
         try {
            managerAvailable=this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"))!=null;
         } catch(Exception error) {
            reason=rootMessage(error);
         }
         checks[1]=managerAvailable?"Passed · AVF API available":"Failed · AVF manager unavailable";
         this.readinessResult(1,checks[1]);
         boolean permissions=this.hasAvfPermissions();
         checks[2]=permissions?"Passed · granted":"Failed · permission required";
         this.readinessResult(2,checks[2]);
         boolean platformVerified=false;
         try {
            for(String asset:BundledPlatformAssets.NAMES)
               this.verifyBundledAsset(asset,BundledPlatformAssets.size(asset),BundledPlatformAssets.sha256(asset));
            platformVerified=true;
            checks[3]="Passed · SHA-256 verified";
         } catch(Exception error) {
            checks[3]="Failed · "+rootMessage(error);
         }
         this.readinessResult(3,checks[3]);
         if(!arm64) {
            outcome=AvfCompatibilityProbe.Status.INCOMPATIBLE;
            reason="The Android device does not expose an ARM64 ABI.";
         } else if(permissions && platformVerified && managerAvailable) {
            AvfCompatibilityProbe probe=new AvfCompatibilityProbe(this);
            AvfCompatibilityProbe.Result result=probe.run();
            guestMemoryBytes=result.guestMemoryBytes();
            outcome=result.status();
            reason=result.details();
            checks[4]=result.created()?"Passed": "Failed · VM creation was not confirmed";
            checks[5]=result.booted()?"Passed":result.created()?"Failed · Kernel marker absent":"Not checked";
            checks[6]=result.memory()?"Passed":result.booted()?"Failed · Memory proof absent":"Not checked";
            checks[7]=result.storage()?"Passed":result.vsock()?"Failed · Disk write/read proof absent":"Not checked";
            checks[8]=result.vsock()?"Passed":result.booted()?"Failed · Vsock proof absent":"Not checked";
            report=result.report();
            File dir=this.getExternalFilesDir(null);
            if(dir!=null) {
               try {
                  java.nio.file.Files.writeString(new File(dir,"uavf-avf-compatibility-console.txt").toPath(),probe.consoleTail());
               } catch(IOException error) {
                  Log.w("UAVF-Compat","Could not save probe console",error);
               }
            }
         } else if(!permissions) reason="Custom VM permission was not granted.";
         else if(!platformVerified) reason=checks[3];
         else if(reason.isEmpty()) reason="Android did not provide a virtualization manager.";
      } catch(Exception error) {
         reason=rootMessage(error);
      } finally {
         try {
            String execution=report.isEmpty()?"AVF_STATUS="+outcome+"\nDETAILS="+reason+"\n":report;
            StringBuilder readable=new StringBuilder("U-AVF local compatibility\n");
            readable.append("DEVICE=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                  .append("\nANDROID_SDK=").append(VERSION.SDK_INT).append('\n');
            for(int i=0;i<checks.length;i++)
               readable.append(UiLanguage.english(FigmaCompatibilityView.CHECKS[i])).append(" = ").append(checks[i]).append('\n');
            readable.append(execution).append("UBUNTU_WINDOWS_MAVK=SEPARATE_CHECKS\n");
            File dir=this.getExternalFilesDir(null);
            if(dir!=null) {
               try {
                  java.nio.file.Files.writeString(new File(dir,"uavf-avf-compatibility.txt").toPath(),readable);
               } catch(IOException error) {
                  Log.w("UAVF-Compat","Could not save compatibility report",error);
               }
            }
            String run=java.util.UUID.randomUUID().toString().replace("-","");
            var preferences=this.getPreferences(0);
            String history=preferences.getString("compatibility_history_v1","");
            if(history.isEmpty()) {
               String previousId=preferences.getString("compatibility_run_id","");
               String previousReport=preferences.getString("compatibility_execution_report","");
               if(previousId.matches("[0-9a-f]{32}") && !previousReport.isBlank()) {
                  history=CompatibilityHistory.append("",new CompatibilityHistory.Run(null,previousId,
                        preferences.getString("compatibility_avf_status","INSUFFICIENT_DATA"),previousReport));
               }
            }
            history=CompatibilityHistory.append(history,
                  new CompatibilityHistory.Run(Instant.now(),run,outcome.name(),readable.toString()));
            var saved=preferences.edit().putString("compatibility_checked_build",Build.FINGERPRINT)
                  .putInt("compatibility_check_schema",COMPATIBILITY_SCHEMA)
                  .putString("compatibility_avf_status",outcome.name())
                  .putString("compatibility_run_id",run)
                  .putString("compatibility_history_v1",history)
                  .putString("compatibility_execution_report",readable.toString());
            for(int i=0;i<checks.length;i++)saved.putString("compatibility_result_"+i,checks[i]);
            saved.apply();
            if(shareResult) {
               var memory=new android.app.ActivityManager.MemoryInfo();
               this.getSystemService(android.app.ActivityManager.class).getMemoryInfo(memory);
               var group=CompatibilityUpload.Group.forDevice(Build.MANUFACTURER,Build.MODEL,VERSION.SDK_INT,
                     memory.totalMem,Runtime.getRuntime().availableProcessors());
               this.uploadCompatibilityReport(run,group,outcome.name(),checks,this.releaseVersionLabel(),
                     VERSION.RELEASE,guestMemoryBytes);
            }
         } finally {
            VmLifecycle.endCompatibilityProbe();
            this.runOnUiThread(this::finishCompatibilityCheck);
         }
      }
   }

   private void finishCompatibilityCheck() {
      if(this.activityDestroyed)return;
      this.compatibilityRunning=false;
      this.readCompatibilityResult();
      this.appendDiagnosticEvent("Check","Event","Compatibility result: "+this.compatibilityStatus);
      if(this.productScreen==ProductScreen.COMPATIBILITY) {
         this.renderProductScreen(ProductScreen.COMPATIBILITY);
      }
   }

   private void readCompatibilityResult() {
      var preferences=this.getPreferences(0);
      boolean current=preferences.getInt("compatibility_check_schema",0)==COMPATIBILITY_SCHEMA
            && Build.FINGERPRINT.equals(preferences.getString("compatibility_checked_build",""));
      this.compatibilityStatus=AvfCompatibilityProbe.Status.INSUFFICIENT_DATA;
      if(current) {
         try {
            this.compatibilityStatus=AvfCompatibilityProbe.Status.valueOf(
                  preferences.getString("compatibility_avf_status","INSUFFICIENT_DATA"));
         } catch(IllegalArgumentException ignored) {}
      }
      this.compatibilityPassed=this.compatibilityStatus==AvfCompatibilityProbe.Status.COMPATIBLE;
      for(int i=0;i<this.readinessResults.length;i++)
         this.readinessResults[i]=current?preferences.getString("compatibility_result_"+i,"Not checked"):"Not checked";
      this.readinessCompleted=current?this.readinessResults.length:0;
   }

   private void observeRecreatedCompatibilityCheck() {
      if(this.activityDestroyed)return;
      if(VmLifecycle.compatibilityProbeActive())this.uiHandler.postDelayed(this::observeRecreatedCompatibilityCheck,350);
      else this.finishCompatibilityCheck();
   }

   private void verifyBundledAsset(String var1, long var2, String var4) throws Exception {
      MessageDigest var5 = MessageDigest.getInstance("SHA-256");
      long var6 = 0L;
      InputStream var8 = this.getAssets().open(var1);

      try {
         byte[] var9 = new byte[1048576];

         int var10;
         while((var10 = var8.read(var9)) >= 0) {
            if (var10 != 0) {
               var6 += (long)var10;
               var5.update(var9, 0, var10);
            }
         }
      } catch (Throwable var12) {
         if (var8 != null) {
            try {
               var8.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }
         }

         throw var12;
      }

      if (var8 != null) {
         var8.close();
      }

      if (var6 != var2 || !var4.equals(Digest.hex(var5.digest()))) {
         throw new SecurityException("Bundled platform asset failed verification: " + var1);
      }
   }

   private void refreshUbuntuIsoStatus() {
      if (this.ubuntuIsoStatus != null) {
         File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
         boolean var2 = var1.isFile() && var1.length() == 3967463424L;
         this.ubuntuIsoStatus.setText(var2 ? "Official Ubuntu 24.04.5 selected · U-AVF can prepare a live session" : "No supported Ubuntu image selected yet.");
      }
   }

   private String sha256Uri(Uri var1) throws Exception {
      MessageDigest var2 = MessageDigest.getInstance("SHA-256");
      BufferedInputStream var3 = new BufferedInputStream(this.getContentResolver().openInputStream(var1));

      try {
         if (var3 == null) {
            throw new IllegalStateException("Could not read the selected file");
         }

         byte[] var4 = new byte[1048576];
         long var5 = 0L;

         int var7;
         while((var7 = ((InputStream)var3).read(var4)) >= 0) {
            if (var7 != 0) {
               var5 += (long)var7;
               if (var5 > 3967463424L) {
                  throw new IllegalArgumentException("Selected image is too large");
               }

               var2.update(var4, 0, var7);
            }
         }

         if (var5 != 3967463424L) {
            throw new IllegalArgumentException("Selected image has the wrong size");
         }
      } catch (Throwable var9) {
         try {
            ((InputStream)var3).close();
         } catch (Throwable var8) {
            var9.addSuppressed(var8);
         }

         throw var9;
      }

      ((InputStream)var3).close();
      return Digest.hex(var2.digest());
   }

   private Button button(String var1) {
      Button var2 = new Button(this);
      var2.setText(UiLanguage.text(var1));
      var2.setTextSize(13.0F);
      var2.setTextColor(this.isLightAppearance() ? -14274243 : -1);
      var2.setAllCaps(false);
      var2.setTypeface(this.productTypeface());
      var2.setBackground(this.panelBackground(-14406859));
      return var2;
   }

   private GradientDrawable panelBackground(int var1) {
      GradientDrawable var2 = new GradientDrawable();
      var2.setColor(this.resolveAppearancePanelColor(var1));
      var2.setCornerRadius((float)this.dp(12));
      var2.setStroke(this.dp(1), 0xcc293b4f);
      return var2;
   }

   private int resolveAppearancePanelColor(int var1) {
      if (!this.isLightAppearance()) {
         return var1;
      } else {
         int var2 = Color.red(var1);
         int var3 = Color.green(var1);
         int var4 = Color.blue(var1);
         boolean var5 = var2 < 72 && var3 < 82 && var4 < 96 && Math.abs(var2 - var3) < 36 && Math.abs(var3 - var4) < 40;
         if (!var5) {
            return var1;
         } else {
            return (var2 + var3 + var4) / 3 < 34 ? -920329 : -1774864;
         }
      }
   }

   private int dp(int var1) {
      return (int)((float)var1 * this.getResources().getDisplayMetrics().density + 0.5F);
   }

   private void restoreProfile() {
      boolean var1 = this.getPreferences(0).contains("launch_profile");
      String var2 = this.getPreferences(0).getString("launch_profile", MainActivity.LaunchProfile.LINUX.name());

      try {
         this.selectedProfile = MainActivity.LaunchProfile.valueOf(var2);
      } catch (IllegalArgumentException var4) {
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
      }

      if (this.selectedProfile == MainActivity.LaunchProfile.V22_EXT4_CONTROL || this.selectedProfile == MainActivity.LaunchProfile.V23_EXT4_BLOCK_IO) {
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
      }

      if (this.selectedProfile == MainActivity.LaunchProfile.P33_FRAME_BRIDGE) {
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
      }

      this.readCompatibilityResult();
      if(VmLifecycle.compatibilityProbeActive()) {
         this.compatibilityRunning=true;
         Arrays.fill(this.readinessResults,"Waiting");
         this.uiHandler.postDelayed(this::observeRecreatedCompatibilityCheck,350);
      }

      if (var1) {
         this.getPreferences(0).edit().putBoolean("first_run_complete", true).apply();
      }

      this.updateProfileUi();
      if (var1) {
         this.renderProductScreen(ProductScreen.MODE_SELECTION);
      } else {
         this.renderProductScreen(ProductScreen.ONBOARDING);
      }

   }

   private void selectProfile(LaunchProfile var1) {
      this.selectedProfile = var1;
      this.getPreferences(0).edit().putString("launch_profile", var1.name()).apply();
      this.updateProfileUi();
      this.show(var1 == MainActivity.LaunchProfile.WINDOWS ? "Windows mode selected." : "Linux mode selected.");
   }

   private void updateProfileUi() {
      if (this.profileLabel != null && this.profileDetails != null) {
         boolean var1 = this.selectedProfile == MainActivity.LaunchProfile.WINDOWS;
         boolean var2 = this.selectedProfile == MainActivity.LaunchProfile.V22_EXT4_CONTROL;
         boolean var3 = this.selectedProfile == MainActivity.LaunchProfile.LINUX || this.selectedProfile == MainActivity.LaunchProfile.P33_FRAME_BRIDGE;
         this.profileLabel.setText(var1 ? "WINDOWS" : (var2 ? "V22" : "LINUX"));
         this.windowsProfileButton.setBackground(this.panelBackground(var1 ? -15308873 : -14406859));
         this.linuxProfileButton.setBackground(this.panelBackground(var3 ? -13927861 : -14406859));
         this.v22ProfileButton.setBackground(this.panelBackground(var2 ? -7710186 : -14406859));
         this.profileDetails.setText(var1 ? "Windows ARM experimental" : "Ubuntu Linux desktop");
      }
   }

   private void launchSelectedProfile() {
      if (!this.vmMayBeRunning && !this.isManagedVmRunning()) {
         if (this.selectedProfile != MainActivity.LaunchProfile.LINUX && this.selectedProfile != MainActivity.LaunchProfile.P33_FRAME_BRIDGE) {
            if (this.selectedProfile == MainActivity.LaunchProfile.V22_EXT4_CONTROL) {
               (new Thread(this::startV22Ext4Control, "WinAVF-ui-v22-start")).start();
            } else {
               (new Thread(this::startTest, "WinAVF-ui-windows-start")).start();
            }
         } else {
            File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
            if (!var1.isFile() || var1.length() != 3967463424L) {
               this.show("Choose the official Ubuntu 24.04.5 ARM64 image first.");
               this.openUbuntuIsoPicker();
               return;
            }

            (new Thread(this::startUbuntuLive, "WinAVF-ui-linux-start")).start();
         }

      } else {
         this.show("A VM is already running. Stop it before launching another profile.");
      }
   }

   private void stopSelectedProfile() {
      this.requestGuestPower(false);
   }

   private void requestGuestPower(boolean reboot) {
      if (!this.isManagedVmRunning()) { this.show("No running VM."); return; }
      this.userRequestedVmStop = true;
      this.rebootRequested = reboot;
      this.lifecycle.stopping();
      this.beginRuntimeTransition(reboot ? RuntimeTransition.Mode.REBOOT : RuntimeTransition.Mode.SHUTDOWN);
      this.show(reboot ? "Restart requested; waiting for Ubuntu…" : "Stopping: waiting for Ubuntu shutdown…");
      GuestControl control = this.guestControl;
      final Object powerOwner=this.lifecycleVm;
      final int powerGeneration=this.transition.generation();
      if (control != null && control.connected()) {
         control.power(reboot, result -> {
            this.show("Guest power request: " + result);
            if(this.lifecycleVm!=powerOwner || this.transition.generation()!=powerGeneration || this.transition.events().containsKey("VM stopped"))return;
            if("ACK".equals(result)) this.transition.event("Power request accepted","Guest UCTL ACK",android.os.SystemClock.elapsedRealtime());
            else this.transition.failure("Ubuntu did not accept the power request: "+result+". No forced stop was performed.",android.os.SystemClock.elapsedRealtime());
            this.runOnUiThread(this.transitionTick);
         });
      } else {
         this.show("Guest control unavailable. No hard stop was performed; waiting before offering recovery.");
      }
      final Object expected = this.lifecycleVm;
      this.uiHandler.postDelayed(() -> this.offerPowerTimeout(expected, reboot), 60000);
   }

   private void offerPowerTimeout(Object expected, boolean reboot) {
      if (this.activityDestroyed || this.lifecycleVm != expected || this.lifecycle.state() != VmLifecycle.State.STOPPING) return;
      // The non-modal transition view owns waiting/recovery. Do not interrupt
      // the user repeatedly with a timeout dialog or claim the guest is hung.
      this.transitionTick.run();
   }

   private void confirmForceStop(boolean reboot) {
      ProductDialog.show(this,reboot ? "Force restart VM?" : "Force stop VM?",
         "This cuts power without a guest shutdown. Unsaved data may be lost and the guest filesystem may be damaged.","Force",true,()->{
            this.rebootRequested=reboot;
            new Thread(this::forceStopSelectedProfile,"U-AVF-force-stop").start();
         });
   }

   private void forceStopSelectedProfile() {
      WindowsSetupRuntime setup=VmRuntimeService.windowsSetup();
      if(setup!=null && setup.active()) {setup.stop();return;}
      String var1 = this.activeFrameVmName != null ? this.activeFrameVmName : (this.selectedProfile == MainActivity.LaunchProfile.P33_FRAME_BRIDGE ? "winavf-frame-bridge-test-ubuntu-24045" : (this.selectedProfile == MainActivity.LaunchProfile.LINUX ? "winavf-frame-bridge-test-ubuntu-24045" : (this.selectedProfile == MainActivity.LaunchProfile.V22_EXT4_CONTROL ? "winavf-ubuntu-gnome-24045-v22-ext4-control" : "winavf-gop-ebs-r1")));

      try {
         Object var2 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
         Object var3 = var2.getClass().getMethod("get", String.class).invoke(var2, var1);
         if (var3 == null) {
            this.show("No running " + this.selectedProfile.name().toLowerCase() + " VM.");
            return;
         }

         this.userRequestedVmStop = true;
         if(!this.getPreferences(0).getBoolean("linux_current_boot_temporary",false))
            this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", false).apply();
         this.lifecycle.stopping();
         var3.getClass().getMethod("stop").invoke(var3);
         // onStopped, not a successful stop() request, owns terminal state and
         // reboot routing. Keep the VM name until that callback is received.
         String var10001 = this.selectedProfile.name().substring(0, 1);
         this.show(var10001 + this.selectedProfile.name().substring(1).toLowerCase() + " VM stop requested.");
      } catch (Throwable var4) {
         this.show("Could not stop VM: " + rootMessage(var4));
      }

   }

   private boolean skipDiskValidation() {
      return this.getPreferences(0).getBoolean("skip_disk_validation", false);
   }

   private void requireDisk(File var1, long var2, String var4, String var5) throws Exception {
      if (var1.isFile() && var1.length() > 0L) {
         if (this.skipDiskValidation()) {
            this.show("Disk verification skipped for " + var5 + ".");
         } else if (var2 >= 0L && var1.length() != var2) {
            throw new IllegalStateException(var5 + " size mismatch");
         } else if (var4 != null && !var4.equals(Digest.hex(Digest.sha256File(var1)))) {
            throw new SecurityException(var5 + " SHA-256 mismatch");
         }
      } else {
         throw new IllegalStateException("Missing or empty " + var5 + ": " + String.valueOf(var1));
      }
   }

   private long checkedCopyLength(long var1) {
      return this.skipDiskValidation() ? -1L : var1;
   }

   private String diskShaForReport(File var1) throws Exception {
      return this.skipDiskValidation() ? "SKIPPED_BY_USER" : Digest.hex(Digest.sha256File(var1));
   }

   public void onNewIntent(Intent var1) {
      super.onNewIntent(var1);
      this.setIntent(var1);
      Log.i("UAVF-H264","New intent: encoded=" + var1.getBooleanExtra("encoded_display_probe",false));
      this.handleIntentActions(var1);
   }

   private void handleIntentActions(Intent var1) {
      // Release builds accept normal launcher navigation, not exported test commands.
      if ((this.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0) {
         var1.replaceExtras((Bundle)null);
         return;
      }
      if(var1.getBooleanExtra("show_welcome",false)) {
         this.renderProductScreen(ProductScreen.ONBOARDING);
         var1.removeExtra("show_welcome");
      }
      if (var1.getBooleanExtra("encoded_display_probe", false)) {
         Log.i("UAVF-H264","Probe request: vm=" + (this.encodedDisplayVm != null) + " running=" + this.isManagedVmRunning() + " lifecycle=" + this.lifecycle.state());
         if (this.encodedDisplayVm == null || !this.isManagedVmRunning()) {
            this.show("Start installed Ubuntu before the isolated encoded display proof.");
         } else {
            final int probeGeneration = ++this.encodedProbeGeneration;
            if (this.encodedDisplayProbe != null) this.encodedDisplayProbe.close();
            final Object probeVm = this.encodedDisplayVm;
            this.displayConnection.begin();
            this.encodedDisplayProbe = new EncodedDisplayProbe(this, probeVm,
                  var1.getIntExtra("encoded_display_refresh_hz", 60), () -> {
               if (probeGeneration == this.encodedProbeGeneration && this.isManagedVmRunning()) this.startUbuntuFrameBridge(probeVm);
            });
            this.encodedDisplayProbe.show();
         }
      }
      if (var1.getBooleanExtra("start", false)) {
         (new Thread(this::startTest, "WinAVF-start")).start();
      }

      if (var1.getBooleanExtra("ubuntu_gnome", false)) {
         (new Thread(() -> this.startUbuntuGnome(false, false), "WinAVF-ubuntu-gnome")).start();
      }

      if (var1.getBooleanExtra("ubuntu_gnome_frame", false)) {
         (new Thread(() -> this.startUbuntuGnome(false, true), "WinAVF-ubuntu-gnome-frame")).start();
      }

      if (var1.getBooleanExtra("ubuntu_gnome_queue_depth_1", false)) {
         (new Thread(() -> this.startUbuntuGnome(true), "WinAVF-ubuntu-gnome-q1")).start();
      }

      if (var1.getBooleanExtra("v12_recovery", false)) {
         Log.e("WinAVF", "V12_INTENT_RECEIVED");
         Log.e("WinAVF", "V12_PROFILE_SELECTED");
         (new Thread(this::startV12Recovery, "WinAVF-v12-recovery")).start();
      }

      if (var1.getBooleanExtra("generic_ubuntu", false)) {
         (new Thread(() -> this.startGenericUbuntu(true, false, false), "WinAVF-generic-ubuntu")).start();
      }

      if (var1.getBooleanExtra("generic_ubuntu_no_iso", false)) {
         (new Thread(() -> this.startGenericUbuntu(false, false, false), "WinAVF-generic-ubuntu-no-iso")).start();
      }

      if (var1.getBooleanExtra("generic_ubuntu_second_esp", false)) {
         (new Thread(() -> this.startGenericUbuntu(false, true, false), "WinAVF-generic-ubuntu-second-esp")).start();
      }

      if (var1.getBooleanExtra("generic_ubuntu_combined", false)) {
         (new Thread(() -> this.startGenericUbuntu(false, false, true), "WinAVF-generic-ubuntu-combined")).start();
      }

      if (var1.getBooleanExtra("generic_ubuntu_frame_bridge_test", false)) {
         if(!this.isManagedVmRunning()) this.selectProfile(LaunchProfile.LINUX);
         (new Thread(this::startUbuntuLive, "WinAVF-generic-frame-bridge-test")).start();
      }

      if (var1.getBooleanExtra("stop_frame_bridge_test", false)) {
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(this::stopSelectedProfile, "WinAVF-stop-frame-bridge-test")).start();
      }

      if (var1.getBooleanExtra("linux_gpu_virgl_gbm_test", false)) {
         this.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true).putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).apply();
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(() -> this.startUbuntuLive(LiveProfile.VIRGL), "WinAVF-linux-virgl-gbm-test")).start();
      }

      if (var1.getBooleanExtra("linux_gpu_virgl_gbm_linux_first_test", false)) {
         this.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true).putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).putBoolean("linux_gpu_gfxstream_system_blob_experimental", false).apply();
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(() -> this.startUbuntuLive(LiveProfile.VIRGL_LINUX_FIRST), "WinAVF-linux-virgl-gbm-linux-first-test")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_test", false)) {
         (new Thread(() -> this.startUbuntuLive(LiveProfile.GFXSTREAM), "WinAVF-linux-first-gpu-test")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_no_udmabuf_test", false)) {
         (new Thread(() -> this.startUbuntuLive(LiveProfile.GFXSTREAM_NO_UDMABUF), "WinAVF-linux-first-gpu-no-udmabuf")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_system_blob_test", false)) {
         this.getPreferences(0).edit().putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", true).putBoolean("linux_gpu_gfxstream_system_blob_experimental", true).putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false).apply();
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(() -> this.startUbuntuLive(LiveProfile.GFXSTREAM), "WinAVF-linux-first-gpu-system-blob")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_virgl_test", false)) {
         (new Thread(() -> this.startUbuntuLive(LiveProfile.GFXSTREAM_VIRGL), "WinAVF-linux-first-gpu-virgl")).start();
      }

      if (var1.getBooleanExtra("retry_frame_bridge_test", false)) {
         (new Thread(this::retryFrameBridgeTest, "WinAVF-frame-bridge-retry")).start();
      }

      if (var1.getBooleanExtra("ubuntu_gnome_cleanup", false)) {
         (new Thread(this::cleanupUbuntuGnome, "WinAVF-ubuntu-gnome-cleanup")).start();
      }

      if (var1.getBooleanExtra("v22_ext4_control", false)) {
         (new Thread(this::startV22Ext4Control, "WinAVF-v22-ext4-control")).start();
      }

      if (var1.getBooleanExtra("v23_ext4_block_io", false)) {
         (new Thread(this::startV23Ext4BlockIo, "WinAVF-v23-ext4-block-io")).start();
      }

      if (var1.getBooleanExtra("uefi_input_probe", false)) {
         (new Thread(this::startUefiInputProbe, "WinAVF-uefi-input")).start();
      }

      if (var1.getBooleanExtra("uefi_serial_escape_probe", false)) {
         (new Thread(this::startUefiSerialEscapeProbe, "WinAVF-uefi-serial-escape")).start();
      }

      if (var1.getBooleanExtra("vsock_hello_probe", false)) {
         (new Thread(this::startVsockHelloProbe, "WinAVF-vsock-hello")).start();
      }

      if (var1.getBooleanExtra("rollback", false)) {
         (new Thread(this::rollbackLastPatch, "WinAVF-rollback")).start();
      }

      if (var1.getBooleanExtra("audit", false)) {
         (new Thread(this::auditVirtualizationCapabilities, "WinAVF-audit")).start();
      }

      if (var1.getBooleanExtra("synthetic_frame", false)) {
         this.emitSyntheticFrameForSurfaceAudit();
      }

      if (var1.getBooleanExtra("display_host_audit", false)) {
         (new Thread(this::auditNativeAvfDisplayAccess, "WinAVF-display-audit")).start();
      }

      if (var1.getBooleanExtra("console_input_api_probe", false)) {
         (new Thread(this::probeConsoleInputApi, "WinAVF-console-input-api")).start();
      }

      if (var1.getBooleanExtra("console_binary_loopback", false)) {
         (new Thread(this::probeConsoleBinaryLoopback, "WinAVF-console-binary-loopback")).start();
      }

      if (var1.getBooleanExtra("cleanup_console_binary_loopback", false)) {
         (new Thread(this::cleanupConsoleBinaryLoopback, "WinAVF-console-binary-cleanup")).start();
      }

      if (var1.getBooleanExtra("kd_bridge", false)) {
         String var2 = var1.getStringExtra("kd_token");
         (new Thread(() -> this.startProductKdBridge(var2), "WinAVF-product-kd-bridge")).start();
      }

      if (var1.getBooleanExtra("kd_bridge_stop", false)) {
         (new Thread(this::stopProductKdBridge, "WinAVF-product-kd-stop")).start();
      }

      if (var1.getBooleanExtra("export_product_bcd", false)) {
         (new Thread(this::exportProductBcdReadOnly, "WinAVF-product-bcd-audit")).start();
      }

      if (var1.getBooleanExtra("persistent_witness_audit", false)) {
         (new Thread(this::auditPersistentBootWitness, "WinAVF-persistent-witness-audit")).start();
      }

      if (var1.getBooleanExtra("persistent_witness_cleanup", false)) {
         (new Thread(this::cleanupPersistentBootWitness, "WinAVF-persistent-witness-cleanup")).start();
      }

   }

   private void emitSyntheticFrameForSurfaceAudit() {
      byte[] var1 = new byte[256028];
      var1[0] = 87;
      var1[1] = 65;
      var1[2] = 86;
      var1[3] = 70;
      var1[4] = 1;
      var1[5] = 1;
      ByteCodec.putLe16(var1, 8, 320);
      ByteCodec.putLe16(var1, 10, 200);
      ByteCodec.putLe32(var1, 12, 1398359601);
      ByteCodec.putLe32(var1, 16, 256000);

      for(int var2 = 0; var2 < 200; ++var2) {
         for(int var3 = 0; var3 < 320; ++var3) {
            int var4 = 28 + (var2 * 320 + var3) * 4;
            var1[var4] = (byte)(var3 * 255 / 319);
            var1[var4 + 1] = (byte)(var2 * 255 / 199);
            var1[var4 + 2] = -128;
            var1[var4 + 3] = -1;
         }
      }

      CRC32 var5 = new CRC32();
      var5.update(var1, 28, 256000);
      ByteCodec.putLe32(var1, 20, (int)var5.getValue());
      this.frameDecoder.feed(var1, 0, var1.length);
      this.show("Synthetic WAVF surface audit frame submitted.");
   }

   private void auditVirtualizationCapabilities() {
      File var1 = new File(this.getExternalFilesDir((String)null), "avf-capability-audit.txt");
      String[] var2 = new String[]{"android.system.virtualmachine.VirtualMachineManager", "android.system.virtualmachine.VirtualMachine", "android.system.virtualmachine.VirtualMachineConfig", "android.system.virtualmachine.VirtualMachineConfig$Builder", "android.system.virtualmachine.VirtualMachineCustomImageConfig", "android.system.virtualmachine.VirtualMachineCustomImageConfig$Builder", "android.system.virtualmachine.VirtualMachineCustomImageConfig$Disk", "android.system.virtualmachine.VirtualMachineCustomImageConfig$DisplayConfig", "android.system.virtualmachine.VirtualMachineCustomImageConfig$DisplayConfig$Builder", "android.system.virtualmachine.VirtualMachineCustomImageConfig$GpuConfig", "android.system.virtualmachine.VirtualMachineCustomImageConfig$GpuConfig$Builder", "android.system.virtualmachine.VirtualMachineDescriptor", "android.system.virtualmachine.VirtualMachineCallback"};

      try {
         PrintWriter var3 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var3.println("fingerprint=" + Build.FINGERPRINT);
            var3.println("sdk=" + VERSION.SDK_INT);

            for(String var7 : var2) {
               try {
                  Class var8 = Class.forName(var7);
                  var3.println("CLASS " + var7 + " AVAILABLE");
                  Constructor[] var9 = var8.getDeclaredConstructors();
                  Arrays.sort(var9, (var0, var1x) -> var0.toString().compareTo(var1x.toString()));

                  for(Constructor var13 : var9) {
                     var3.println("  CONSTRUCTOR " + String.valueOf(var13));
                  }

                  Method[] var27 = var8.getDeclaredMethods();
                  Arrays.sort(var27, (var0, var1x) -> var0.toString().compareTo(var1x.toString()));

                  for(Method var14 : var27) {
                     String var15 = var14.toString();
                     if (var15.toLowerCase().matches(".*(disk|display|graphics|gpu|surface|console|input|keyboard|mouse|touch|virtio|socket|vsock).*")) {
                        var3.println("  METHOD " + var15);
                     }
                  }

                  Field[] var29 = var8.getDeclaredFields();
                  Arrays.sort(var29, (var0, var1x) -> var0.toString().compareTo(var1x.toString()));

                  for(Field var35 : var29) {
                     String var16 = var35.toString();
                     if (var16.toLowerCase().matches(".*(disk|display|graphics|gpu|surface|console|input|keyboard|mouse|touch|virtio|socket|vsock).*")) {
                        var3.println("  FIELD " + var16);
                     }
                  }
               } catch (Throwable var18) {
                  var3.println("CLASS " + var7 + " UNAVAILABLE " + rootMessage(var18));
               }
            }

            String[] var21 = new String[]{"android.permission.MANAGE_VIRTUAL_MACHINE", "android.permission.USE_CUSTOM_VIRTUAL_MACHINE", "android.permission.VIRTUAL_INPUT_DEVICE"};

            for(String var25 : var21) {
               PermissionInfo var26 = this.getPackageManager().getPermissionInfo(var25, 0);
               var3.println("PERMISSION " + var25 + " check=" + this.checkSelfPermission(var25) + " protection=" + var26.protectionLevel);
            }
         } catch (Throwable var19) {
            try {
               var3.close();
            } catch (Throwable var17) {
               var19.addSuppressed(var17);
            }

            throw var19;
         }

         var3.close();
      } catch (Throwable var20) {
         this.show("AVF capability audit failed: " + rootMessage(var20));
         return;
      }

      this.show("AVF capability audit saved to " + var1.getAbsolutePath());
   }

   private void auditNativeAvfDisplayAccess() {
      File var1 = new File(this.getExternalFilesDir((String)null), "native-avf-display-access-audit.txt");

      try {
         PrintWriter var2 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var2.println("fingerprint=" + Build.FINGERPRINT);
            var2.println("uid=" + Process.myUid());
            var2.println("permission.MANAGE_VIRTUAL_MACHINE=" + this.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE"));
            var2.println("permission.USE_CUSTOM_VIRTUAL_MACHINE=" + this.checkSelfPermission("android.permission.USE_CUSTOM_VIRTUAL_MACHINE"));
            Class var3 = Class.forName("android.os.ServiceManager");
            var2.println("CLASS android.os.ServiceManager=AVAILABLE");
            Method var4 = var3.getMethod("getService", String.class);
            var2.println("METHOD ServiceManager.getService=AVAILABLE");
            Object var5 = var4.invoke((Object)null, "android.system.virtualizationservice");
            String var10001 = var5 == null ? "NULL" : "AVAILABLE:" + var5.getClass().getName();
            var2.println("BINDER android.system.virtualizationservice=" + var10001);

            for(String var9 : new String[]{"android.system.virtualizationservice_internal.IVirtualizationServiceInternal", "android.system.virtualizationservice_internal.IVirtualizationServiceInternal$Stub", "android.crosvm.ICrosvmAndroidDisplayService", "android.crosvm.ICrosvmAndroidDisplayService$Stub"}) {
               try {
                  Class.forName(var9);
                  var2.println("CLASS " + var9 + "=AVAILABLE");
               } catch (Throwable var12) {
                  var2.println("CLASS " + var9 + "=UNAVAILABLE:" + rootMessage(var12));
               }
            }

            var2.println("RESULT=READ_ONLY_HOST_PATH_AUDIT_COMPLETE");
         } catch (Throwable var13) {
            try {
               var2.close();
            } catch (Throwable var11) {
               var13.addSuppressed(var11);
            }

            throw var13;
         }

         var2.close();
      } catch (Throwable var14) {
         this.show("Native AVF display access audit failed: " + rootMessage(var14));
         return;
      }

      this.show("Native AVF display access audit saved to " + var1.getAbsolutePath());
   }

   private void probeConsoleInputApi() {
      File var1 = new File(this.getExternalFilesDir((String)null), "console-input-api-probe.txt");
      Object var2 = null;
      boolean var3 = false;

      try {
         PrintWriter var40 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var40.println("scope=NO_GUEST_RUN_NO_DISK_NO_PRODUCT_MEDIA");
            Class var41 = Class.forName("android.system.virtualmachine.VirtualMachineConfig");
            Class var6 = Class.forName(var41.getName() + "$Builder");
            Object var7 = var6.getConstructor(Context.class).newInstance(this);
            call(var7, "setProtectedVm", Boolean.TYPE, false);
            call(var7, "setDebugLevel", Integer.TYPE, 1);
            call(var7, "setConsoleInputDevice", String.class, "ttyS0");
            call(var7, "setVmConsoleInputSupported", Boolean.TYPE, true);
            File var8 = new File(this.getFilesDir(), "payload");
            if (!var8.exists() && !var8.mkdirs()) {
               throw new IllegalStateException("Cannot create payload directory");
            }

            File var9 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var8, "u-boot-wrapper-v24.Image"), -1L);
            Class var10 = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
            Class var11 = Class.forName(var10.getName() + "$Builder");
            Object var12 = var11.getConstructor().newInstance();
            call(var12, "setName", String.class, "winavf-console-input-probe-20260909");
            call(var12, "setOsName", String.class, "winavf-console-input-probe");
            call(var12, "setKernelPath", String.class, var9.getAbsolutePath());
            Object var13 = call(var12, "build");
            call(var7, "setCustomImageConfig", var10, var13);
            Object var14 = call(var7, "build");
            boolean var15 = (Boolean)var14.getClass().getMethod("isVmConsoleInputSupported").invoke(var14);
            var40.println("config.isVmConsoleInputSupported=" + var15);
            if (!var15) {
               throw new IllegalStateException("Console-input flag was not persisted in config");
            }

            Class var16 = Class.forName("android.system.virtualmachine.VirtualMachineManager");
            var2 = this.getSystemService(var16);
            Object var17 = var2.getClass().getMethod("get", String.class).invoke(var2, "winavf-console-input-probe-20260909");
            if (var17 != null) {
               var2.getClass().getMethod("delete", String.class).invoke(var2, "winavf-console-input-probe-20260909");
            }

            Object var18 = var2.getClass().getMethod("create", String.class, var14.getClass()).invoke(var2, "winavf-console-input-probe-20260909", var14);
            var3 = true;
            OutputStream var19 = (OutputStream)var18.getClass().getMethod("getConsoleInput").invoke(var18);
            var19.write(new byte[0]);
            var19.flush();
            var19.close();
            var40.println("getConsoleInput=OUTPUT_STREAM_USABLE");
            var40.println("result=PASS");
            this.show("Console input API probe: OutputStream acquired.");
         } catch (Throwable var37) {
            try {
               var40.close();
            } catch (Throwable var36) {
               var37.addSuppressed(var36);
            }

            throw var37;
         }

         var40.close();
      } catch (Throwable var38) {
         Throwable var4 = var38;

         try {
            PrintWriter var5 = new PrintWriter(new FileOutputStream(var1, true));

            try {
               var5.println("result=FAIL");
               var5.println("error=" + rootMessage(var4));
            } catch (Throwable var34) {
               try {
                  var5.close();
               } catch (Throwable var33) {
                  var34.addSuppressed(var33);
               }

               throw var34;
            }

            var5.close();
         } catch (Throwable var35) {
         }

         this.show("Console input API probe failed: " + rootMessage(var38));
      } finally {
         if (var3 && var2 != null) {
            try {
               var2.getClass().getMethod("delete", String.class).invoke(var2, "winavf-console-input-probe-20260909");
            } catch (Throwable var32) {
            }
         }

      }

   }

   private void probeConsoleBinaryLoopback() {
      File var1 = new File(this.getExternalFilesDir((String)null), "console-binary-loopback-report.txt");
      File var2 = new File(this.getExternalFilesDir((String)null), "console-binary-loopback.raw");
      Object var3 = null;
      Object var4 = null;
      InputStream var5 = null;
      OutputStream var6 = null;
      Object var7 = new Object();
      ByteArrayOutputStream var8 = new ByteArrayOutputStream();

      try {
         PrintWriter var62 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var62.println("scope=DISPOSABLE_NO_DISK_NO_WINDOWS_NO_PRODUCT_MEDIA");
            byte[] var63 = new byte[4096];

            for(int var11 = 0; var11 < var63.length; ++var11) {
               var63[var11] = (byte)var11;
            }

            var62.println("patternBytes=" + var63.length);
            var62.println("patternSha256=" + Digest.hex(Digest.sha256(var63)));
            File var64 = new File(this.getFilesDir(), "payload");
            if (!var64.exists() && !var64.mkdirs()) {
               throw new IllegalStateException("Cannot create payload directory");
            }

            File var12 = this.copyAsset("console-binary-echo.Image", new File(var64, "console-binary-echo.Image"), -1L);
            Object var13 = this.buildConsoleBinaryEchoConfig(var12);
            if (!(Boolean)var13.getClass().getMethod("isVmConsoleInputSupported").invoke(var13)) {
               throw new IllegalStateException("Console input was not persisted in loopback config");
            }

            Class var14 = Class.forName("android.system.virtualmachine.VirtualMachineManager");
            var3 = this.getSystemService(var14);
            Object var15 = var3.getClass().getMethod("get", String.class).invoke(var3, "winavf-console-binary-echo-20260909");
            if (var15 != null) {
               var3.getClass().getMethod("delete", String.class).invoke(var3, "winavf-console-binary-echo-20260909");
            }

            var4 = var3.getClass().getMethod("create", String.class, var13.getClass()).invoke(var3, "winavf-console-binary-echo-20260909", var13);
            var5 = (InputStream)var4.getClass().getMethod("getConsoleOutput").invoke(var4);
            var6 = (OutputStream)var4.getClass().getMethod("getConsoleInput").invoke(var4);
            InputStream consoleReader = var5;
            Thread var17 = new Thread(() -> {
               byte[] buffer = new byte[512];

               int count;
               try {
                  while((count = consoleReader.read(buffer)) >= 0) {
                     synchronized(var7) {
                        var8.write(buffer, 0, count);
                        var7.notifyAll();
                     }
                  }
               } catch (Throwable var8x) {
               }

            }, "WinAVF-console-binary-reader");
            var17.setDaemon(true);
            var17.start();
            var4.getClass().getMethod("run").invoke(var4);
            int var18 = IoUtil.waitForBytes(var8, var7, CONSOLE_BINARY_ECHO_READY, 8000L);
            if (var18 < 0) {
               throw new IllegalStateException("Guest readiness marker not observed");
            }

            var62.println("readyMarkerOffset=" + var18);
            var6.write(var63);
            var6.flush();
            var62.println("patternTransmitted=true");
            if (!IoUtil.waitForLength(var8, var7, var18 + CONSOLE_BINARY_ECHO_READY.length + var63.length, 8000L)) {
               throw new IllegalStateException("Timed out waiting for echoed pattern");
            }

            byte[] var19;
            synchronized(var7) {
               var19 = var8.toByteArray();
            }

            byte[] var20 = Arrays.copyOfRange(var19, var18 + CONSOLE_BINARY_ECHO_READY.length, var18 + CONSOLE_BINARY_ECHO_READY.length + var63.length);
            FileOutputStream var21 = new FileOutputStream(var2, false);

            try {
               var21.write(var19);
            } catch (Throwable var57) {
               try {
                  var21.close();
               } catch (Throwable var56) {
                  var57.addSuppressed(var56);
               }

               throw var57;
            }

            var21.close();
            var62.println("echoedBytes=" + var20.length);
            var62.println("echoedSha256=" + Digest.hex(Digest.sha256(var20)));
            boolean var10001 = Arrays.equals(var63, var20);
            var62.println("byteExact=" + var10001);
            if (!Arrays.equals(var63, var20)) {
               throw new IllegalStateException("Echoed bytes differ from transmitted pattern");
            }

            var62.println("result=PASS");
            this.show("Console binary loopback PASS: 4096/4096 exact.");
         } catch (Throwable var59) {
            try {
               var62.close();
            } catch (Throwable var55) {
               var59.addSuppressed(var55);
            }

            throw var59;
         }

         var62.close();
      } catch (Throwable var60) {
         Throwable var9 = var60;

         try {
            PrintWriter var10 = new PrintWriter(new FileOutputStream(var1, true));

            try {
               var10.println("result=FAIL");
               var10.println("error=" + rootMessage(var9));
            } catch (Throwable var53) {
               try {
                  var10.close();
               } catch (Throwable var52) {
                  var53.addSuppressed(var52);
               }

               throw var53;
            }

            var10.close();
         } catch (Throwable var54) {
         }

         this.show("Console binary loopback failed: " + rootMessage(var60));
      } finally {
         try {
            if (var6 != null) {
               var6.close();
            }
         } catch (Throwable var51) {
         }

         try {
            if (var5 != null) {
               var5.close();
            }
         } catch (Throwable var50) {
         }

         if (var4 != null) {
            try {
               var4.getClass().getMethod("stop").invoke(var4);
            } catch (Throwable var49) {
            }
         }

         if (var3 != null) {
            try {
               var3.getClass().getMethod("delete", String.class).invoke(var3, "winavf-console-binary-echo-20260909");
            } catch (Throwable var48) {
            }
         }

      }

   }

   private void cleanupConsoleBinaryLoopback() {
      try {
         Class var1 = Class.forName("android.system.virtualmachine.VirtualMachineManager");
         Object var2 = this.getSystemService(var1);
         Object var3 = var2.getClass().getMethod("get", String.class).invoke(var2, "winavf-console-binary-echo-20260909");
         if (var3 != null) {
            try {
               var3.getClass().getMethod("stop").invoke(var3);
            } catch (Throwable var5) {
            }
         }

         var2.getClass().getMethod("delete", String.class).invoke(var2, "winavf-console-binary-echo-20260909");
         this.show("Console binary loopback VM removed.");
      } catch (Throwable var6) {
         this.show("Console binary loopback cleanup failed: " + rootMessage(var6));
      }

   }

   private Object buildConsoleBinaryEchoConfig(File var1) throws Exception {
      Class var2 = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
      Class var3 = Class.forName(var2.getName() + "$Builder");
      Object var4 = var3.getConstructor().newInstance();
      call(var4, "setName", String.class, "winavf-console-binary-echo-20260909");
      call(var4, "setOsName", String.class, "winavf-console-binary-echo");
      call(var4, "setKernelPath", String.class, var1.getAbsolutePath());
      Object var5 = call(var4, "build");
      Class var6 = Class.forName("android.system.virtualmachine.VirtualMachineConfig");
      Class var7 = Class.forName(var6.getName() + "$Builder");
      Object var8 = var7.getConstructor(Context.class).newInstance(this);
      call(var8, "setProtectedVm", Boolean.TYPE, false);
      call(var8, "setDebugLevel", Integer.TYPE, 1);
      call(var8, "setCpuTopology", Integer.TYPE, var6.getField("CPU_TOPOLOGY_ONE_CPU").getInt((Object)null));
      call(var8, "setMemoryBytes", Long.TYPE, 536870912L);
      call(var8, "setConsoleInputDevice", String.class, "ttyS0");
      call(var8, "setVmOutputCaptured", Boolean.TYPE, true);
      call(var8, "setVmConsoleInputSupported", Boolean.TYPE, true);
      call(var8, "setCustomImageConfig", var2, var5);
      return call(var8, "build");
   }

   private void startTest() {
      this.startTest(false, false, false);
   }

   private void startUefiInputProbe() {
      this.startTest(true, false, false);
   }

   private void startUefiSerialEscapeProbe() {
      this.startTest(false, false, true);
   }

   private void startVsockHelloProbe() {
      this.startTest(false, true, false);
   }

   private void startTest(boolean var1, boolean var2, boolean var3) {
      try {
         this.bootOptionsPromptSeen = false;
         this.serialInputWindowSeen = false;
         this.serialEscapeAcknowledged = false;
         this.observedFrameCount = 0;
         this.serialProbeTail.setLength(0);
         this.show("Preparing app-private kernel-style loader…");
         File var4 = new File(this.getFilesDir(), "payload");
         if (!var4.exists() && !var4.mkdirs()) {
            throw new IllegalStateException("Cannot create payload directory");
         }

         File var5 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var4, "u-boot-wrapper-v24.Image"), -1L);
         File var6 = new File(var4, "u-boot-wrapper-start-marker.Image");
         if (var6.isFile()) {
            File var7 = new File(this.getExternalFilesDir((String)null), "initial-working-u-boot-wrapper.Image");
            FileInputStream var8 = new FileInputStream(var6);

            try {
               FileOutputStream var9 = new FileOutputStream(var7);

               try {
                  byte[] var10 = new byte[1048576];

                  int var11;
                  while((var11 = ((InputStream)var8).read(var10)) >= 0) {
                     var9.write(var10, 0, var11);
                  }
               } catch (Throwable var17) {
                  try {
                     var9.close();
                  } catch (Throwable var15) {
                     var17.addSuppressed(var15);
                  }

                  throw var17;
               }

               var9.close();
            } catch (Throwable var18) {
               try {
                  ((InputStream)var8).close();
               } catch (Throwable var14) {
                  var18.addSuppressed(var14);
               }

               throw var18;
            }

            ((InputStream)var8).close();
         }

         File var20 = new File(this.getExternalFilesDir((String)null), "win11-gop-ebs-r1.img");
         this.requireDisk(var20, 9126805504L, (String)null, "Windows boot medium");
         File var21 = this.copyFile(var20, new File(var4, "win11-gop-ebs-r1.img"), this.checkedCopyLength(9126805504L), false);
         this.applyStagedImagePatch(var4, var21);
         this.show("Using the preserved Windows Boot Manager milestone medium only…");
         this.show("Creating custom AVF VM through Android API…");
         Object var22 = this.buildConfig(var5, var21, var1);
         Object var23 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

         try {
            Object var24 = var23.getClass().getMethod("get", String.class).invoke(var23, "winavf-gop-ebs-r1");
            if (var24 != null) {
               var23.getClass().getMethod("delete", String.class).invoke(var23, "winavf-gop-ebs-r1");
            }
         } catch (Exception var16) {
         }

         Method var25 = var23.getClass().getMethod("create", String.class, var22.getClass());
         Object var12 = var25.invoke(var23, "winavf-gop-ebs-r1", var22);
         this.attachCallback(var12);
         InputStream var13 = (InputStream)var12.getClass().getMethod("getConsoleOutput").invoke(var12);
         this.startConsoleReader(var13);
         var12.getClass().getMethod("run").invoke(var12);
         this.vmMayBeRunning = true;
         if (var1) {
            this.startEscInputProbe(var12);
         }

         if (var3) {
            this.startSerialEscapeInputProbe(var12);
         }

         if (var2) {
            this.startVsockHelloProbe(var12);
         }

         this.startWinpeMarkerReporter(var21);
         this.show("VM launched. Waiting for kernel-first serial output…");
      } catch (Throwable var19) {
         this.show("FAILED: " + rootMessage(var19));
      }

   }

   private void startUbuntuGnome(boolean var1) {
      this.startUbuntuGnome(var1, false);
   }

   private void startUbuntuGnome(boolean var1, boolean var2) {
      try {
         this.frameVisible = false;
         this.observedFrameCount = 0;
         File var3 = new File(this.getFilesDir(), "ubuntu-gnome-payload");
         if (!var3.exists() && !var3.mkdirs()) {
            throw new IllegalStateException("Cannot create Ubuntu payload directory");
         }

         File var4 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var3, "u-boot-wrapper-v24.Image"), -1L);
         File var5 = new File(var3, "ubuntu-gnome-24.04.5-v21-vsock-listener.img");
         if (var5.exists()) {
            this.requireDisk(var5, 9126805504L, "F3AAC176C600BFC3D3EA0BE5DA4EFF2F03DE7B27E3DED26A0E9D58D21B975FAB", "private Ubuntu medium");
            this.show("Using preloaded app-private Ubuntu GNOME medium.");
         } else {
            File var6 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-24.04.5-v21-vsock-listener.img");
            this.requireDisk(var6, 9126805504L, "F3AAC176C600BFC3D3EA0BE5DA4EFF2F03DE7B27E3DED26A0E9D58D21B975FAB", "Ubuntu GNOME staging medium");
            var5 = this.copyFile(var6, var5, this.checkedCopyLength(9126805504L), false);
         }

         this.requireDisk(var5, 9126805504L, "F3AAC176C600BFC3D3EA0BE5DA4EFF2F03DE7B27E3DED26A0E9D58D21B975FAB", "private Ubuntu medium");
         File var16 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v21-vsock-firmware.patch");
         this.requireDisk(var16, 4194436L, "936B0E8106806E5E4AC99C4C8E9FD9394F34747992E7E44BBFF885A8AD50553A", "Ubuntu-only firmware patch");
         File var7 = this.copyFile(var16, new File(var3, "ubuntu-gnome-v21-vsock-firmware.patch"), this.checkedCopyLength(4194436L), false);
         ImagePatch.apply(var7, var5, false);
         String var8 = var1 ? "winavf-ubuntu-gnome-24045-v21-q1" : "winavf-ubuntu-gnome-24045-v21";
         String var9 = var1 ? "mem=2G virtio_blk.queue_depth=1" : "mem=2G";
         Object var10 = this.buildConfig(var4, var5, false, var8, var9);
         Object var11 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

         try {
            Object var12 = var11.getClass().getMethod("get", String.class).invoke(var11, var8);
            if (var12 != null) {
               var11.getClass().getMethod("delete", String.class).invoke(var11, var8);
            }
         } catch (Exception var14) {
         }

         Object var17 = var11.getClass().getMethod("create", String.class, var10.getClass()).invoke(var11, var8, var10);
         this.attachCallback(var17);
         InputStream var13 = (InputStream)var17.getClass().getMethod("getConsoleOutput").invoke(var17);
         this.startConsoleReader(var13, var1 ? "ubuntu-gnome-q1-serial.log" : "ubuntu-gnome-serial.log");
         var17.getClass().getMethod("run").invoke(var17);
         this.vmMayBeRunning = true;
         this.startUbuntuVsockHelloProbe(var17);
         if (var2) {
            this.startUbuntuFrameBridge(var17);
         }

         this.show(var1 ? "Ubuntu GNOME queue_depth=1 diagnostic launched; capturing its complete serial log." : "Ubuntu GNOME live profile launched; capturing its complete serial log.");
      } catch (Throwable var15) {
         this.show("Ubuntu GNOME launch failed: " + rootMessage(var15));
      }

   }

   private void startV12Recovery() {
      try {
         File var1 = new File(this.getFilesDir(), "v12-recovery-payload");
         if (!var1.exists() && !var1.mkdirs()) {
            throw new IllegalStateException("Cannot create V12 payload directory");
         }

         File var12 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var1, "u-boot-wrapper-v24.Image"), -1L);
         File var3 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v12-recovery-candidate.img");
         this.requireDisk(var3, 9126805504L, "C9D80A9CABB14C6E1D0F4F3180923146E354CC916020FB55BB2608FDC3110EA8", "V12 recovery carrier");
         File var4 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v12-recovery-firmware.patch");
         this.requireDisk(var4, 4194436L, "9C109F3EB95D1B6F27929D970152725E0F2328A25FC3C847D8ED1603D86EDC55", "V12 recovery patch");
         Log.e("WinAVF", "V12_MEDIA_GATE_PASS");
         File var5 = this.copyFile(var3, new File(var1, "ubuntu-gnome-v12-recovery-candidate.img"), this.checkedCopyLength(9126805504L), false);
         Object var6 = this.buildConfig(var12, var5, false, "winavf-ubuntu-gnome-v12-recovery", "mem=2G");
         Object var7 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

         try {
            Object var8 = var7.getClass().getMethod("get", String.class).invoke(var7, "winavf-ubuntu-gnome-v12-recovery");
            if (var8 != null) {
               var7.getClass().getMethod("delete", String.class).invoke(var7, "winavf-ubuntu-gnome-v12-recovery");
            }
         } catch (Exception var10) {
         }

         Log.e("WinAVF", "V12_VM_CREATE_BEGIN");
         Object var13 = var7.getClass().getMethod("create", String.class, var6.getClass()).invoke(var7, "winavf-ubuntu-gnome-v12-recovery", var6);
         this.attachCallback(var13);
         InputStream var9 = (InputStream)var13.getClass().getMethod("getConsoleOutput").invoke(var13);
         Log.e("WinAVF", "V12_SERIAL_OPENED");
         this.startConsoleReader(var9, "v12-recovery-serial.log");
         Log.e("WinAVF", "V12_VM_RUN_BEGIN");
         var13.getClass().getMethod("run").invoke(var13);
         this.vmMayBeRunning = true;
         this.show("V12_RECOVERY launched; capturing serial output.");
      } catch (Throwable var11) {
         String var2 = "V12_RECOVERY launch failed: " + rootMessage(var11);
         Log.e("WinAVF", var2, var11);
         this.show(var2);
      }

   }

   private void startGenericUbuntu(boolean var1, boolean var2, boolean var3) {
      File var4 = new File(this.getExternalFilesDir((String)null), "generic-ubuntu-runtime-report.txt");

      try {
         File var27 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
         File var28 = new File(this.getExternalFilesDir((String)null), "generic-ubuntu-platform-gpt.img");
         this.requireDisk(var27, 3967463424L, "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14", "stock Ubuntu ISO");
         this.requireDisk(var28, 134217728L, "413B28882832DDC73CA3B425D9676542595590B53B91739FC4A45CDF41046AA2", "Ubuntu platform ESP");
         File var7 = new File(this.getExternalFilesDir((String)null), "generic-ubuntu-one-disk-journal-rplus.img");
         if (var3) {
            this.requireDisk(var7, 4102029312L, "FFABA39B31EA1E95B40F4DD3F67BF5C274C336C6CFF427E7075AC0BD82143714", "combined Ubuntu disk");
         }

         File var8 = new File(this.getFilesDir(), "generic-ubuntu-payload");
         if (!var8.exists() && !var8.mkdirs()) {
            throw new IllegalStateException("Cannot create generic Ubuntu payload");
         }

         File var9 = var1 ? this.copyFile(var27, new File(var8, "ubuntu-24.04.5-desktop-arm64.iso"), this.checkedCopyLength(3967463424L), false) : var27;
         File var10 = var3 ? this.copyFile(var7, new File(var8, "generic-ubuntu-one-disk-journal-rplus.img"), this.checkedCopyLength(4102029312L), false) : this.copyFile(var28, new File(var8, "generic-ubuntu-platform-gpt.img"), this.checkedCopyLength(134217728L), false);
         File var11 = var2 ? this.copyFile(var28, new File(var8, "generic-ubuntu-second-esp-control.img"), this.checkedCopyLength(134217728L), false) : null;
         File var12 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var8, "u-boot-wrapper-v24.Image"), -1L);
         Object var13 = this.buildGenericUbuntuConfig(var12, var10, var9, var11, var1);
         Object var14 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

         try {
            Object var15 = var14.getClass().getMethod("get", String.class).invoke(var14, "winavf-generic-ubuntu-24045");
            if (var15 != null) {
               var14.getClass().getMethod("delete", String.class).invoke(var14, "winavf-generic-ubuntu-24045");
            }
         } catch (Exception var25) {
         }

         Object var29 = var14.getClass().getMethod("create", String.class, var13.getClass()).invoke(var14, "winavf-generic-ubuntu-24045", var13);
         this.attachCallback(var29);
         InputStream var16 = (InputStream)var29.getClass().getMethod("getConsoleOutput").invoke(var29);
         this.startConsoleReader(var16, "generic-ubuntu-serial.log");
         var29.getClass().getMethod("run").invoke(var29);
         this.vmMayBeRunning = true;
         PrintWriter var17 = new PrintWriter(new FileOutputStream(var4, false));

         try {
            var17.println("PROFILE=GENERIC_UBUNTU");
            var17.println("STOCK_UBUNTU_24_04_5_UNMODIFIED=PASS");
            String var10001 = this.diskShaForReport(var9);
            var17.println("ISO_SHA256=" + var10001);
            var17.println("PLATFORM_ESP_PRESENT=PASS");
            var17.println("PLATFORM_EFI_LOADER_PRESENT=PASS");
            var17.println("BOOT_CHAIN=KERNEL_FIRST_UBOOT_THEN_ESP_EDK2");
            var17.println("SECOND_ISO_BLOCK_DISK=" + (var1 ? "PRESENT" : "ABSENT_DIAGNOSTIC"));
            var17.println("SECOND_ESP_CONTROL_DISK=" + (var2 ? "PRESENT" : "ABSENT"));
            var17.println("ONE_DISK_ESP_PLUS_STOCK_ISO_PARTITION=" + (var3 ? "PASS" : "NO"));
            var17.println("VM_LAUNCH=PASS");
         } catch (Throwable var24) {
            try {
               var17.close();
            } catch (Throwable var23) {
               var24.addSuppressed(var23);
            }

            throw var24;
         }

         var17.close();
         this.show("GENERIC_UBUNTU launched with untouched stock ISO.");
      } catch (Throwable var26) {
         Throwable var5 = var26;

         try {
            PrintWriter var6 = new PrintWriter(new FileOutputStream(var4, false));

            try {
               var6.println("PROFILE=GENERIC_UBUNTU");
               var6.println("STOCK_UBUNTU_24_04_5_UNMODIFIED=NOT_REACHED");
               var6.println("ERROR=" + rootMessage(var5));
            } catch (Throwable var21) {
               try {
                  var6.close();
               } catch (Throwable var20) {
                  var21.addSuppressed(var20);
               }

               throw var21;
            }

            var6.close();
         } catch (Throwable var22) {
         }

         this.show("GENERIC_UBUNTU launch failed: " + rootMessage(var26));
      }

   }

   private void retryFrameBridgeTest() {
      try {
         Object vm = AvfGuest.find(this, AvfGuest.UBUNTU_VM);
         if (vm == null) throw new IllegalStateException("Ubuntu VM not found");
         this.startUbuntuFrameBridge(vm);
      } catch (Exception error) {
         this.show("Frame bridge retry failed: " + rootMessage(error));
      }
   }

   private enum LiveProfile {
      VIRGL(false, false, true), VIRGL_LINUX_FIRST(false, false, true),
      GFXSTREAM(true, false, false), GFXSTREAM_NO_UDMABUF(true, true, false),
      GFXSTREAM_VIRGL(true, false, true);

      final boolean linuxFirstGpu, disableUdmabuf, hostVirgl;

      LiveProfile(boolean linuxFirstGpu, boolean disableUdmabuf, boolean hostVirgl) {
         this.linuxFirstGpu = linuxFirstGpu;
         this.disableUdmabuf = disableUdmabuf;
         this.hostVirgl = hostVirgl;
      }

      boolean supportsEncodedDisplay() { return this == VIRGL_LINUX_FIRST; }
   }

   private void startUbuntuLive() {
      this.startUbuntuLive(LiveProfile.VIRGL_LINUX_FIRST, true);
   }

   private void startUbuntuLive(LiveProfile profile) {
      this.startUbuntuLive(profile, false);
   }

   private void startUbuntuLive(LiveProfile profile, boolean useProductDefaults) {
      if (this.isManagedVmRunning() || !this.lifecycle.beginStart()) {
         this.show("The workspace is already starting, running or stopping.");
         return;
      }

      this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
      this.resetGuestFrameSurface();
      this.runOnUiThread(() -> {
         this.homePanel.setVisibility(View.GONE);
         this.status.setVisibility(View.VISIBLE);
         this.status.setText("Preparing Ubuntu live session…");
      });

      var prefs = this.getPreferences(0);
      try {
         if (this.vmMayBeRunning) throw new IllegalStateException("Stop the VM before preparing Live media");
         AvfGuest.requireStopped(this, AvfGuest.UBUNTU_VM);
         if (useProductDefaults) prefs.edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true)
               .putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false)
               .putBoolean("linux_gpu_gfxstream_system_blob_experimental", false).apply();
         File iso = new File(this.getExternalFilesDir(null), "ubuntu-24.04.5-desktop-arm64.iso");
         if (!iso.isFile()) throw new IllegalStateException("Choose the official Ubuntu ISO first.");
         File staging = new File(this.getFilesDir(), "frame-bridge-test-payload");
         if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Cannot create Ubuntu Live staging directory");

         MavkLiveMedia.restore(this, staging);
         var display = this.displaySettings();
         boolean mavk = profile.supportsEncodedDisplay() && display.mavkRequested();
         MavkRuntimeAssets mavkAssets = mavk ? MavkRuntimeAssets.load(this) : null;
         boolean encodedGeometry = profile.supportsEncodedDisplay() && !mavk && display.encodedEnabled()
               && this.hasInstallerPrefix(UbuntuLiveMedia.Profile.ENCODED.asset());
         var mediaProfile = UbuntuLiveMedia.Profile.select(
               prefs.getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false),
               prefs.getBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false),
               profile.linuxFirstGpu, profile.supportsEncodedDisplay(), encodedGeometry);
         var media = new UbuntuLiveMedia(this.getAssets()::open,
               () -> BundledPlatformAssets.sha256(UbuntuLiveMedia.previousVirglAsset()));
         boolean skipIsoDigest = this.skipDiskValidation();
         if (skipIsoDigest) this.show("Disk verification skipped for official Ubuntu ISO.");
         File disk = media.prepare(iso, staging, mediaProfile, skipIsoDigest);
         if (mavk) MavkLiveMedia.enable(this, staging, disk, mavkAssets);
         File kernel = this.copyAsset("u-boot-wrapper-v24.Image", new File(staging, "u-boot-wrapper-v24.Image"), -1);
         int refreshHz = this.requestedUbuntuRefreshHz();
         Object config = this.buildGenericUbuntuConfig(kernel, disk, null, null, false, AvfGuest.UBUNTU_VM,
               profile.disableUdmabuf, profile.hostVirgl, mavk, refreshHz);
         File ready = new File(staging, "uavf-ubuntu-live-runtime.ready");
         if (ready.exists() && !ready.delete()) throw new IOException("Cannot invalidate temporary-media cache before launch");

         this.recordUbuntuDisplayBoot(false, true, profile.supportsEncodedDisplay(), mavk, refreshHz);
         Object vm = this.launchUbuntuVm(config, "frame-bridge-test-serial.log");
         this.startUbuntuVsockHelloProbe(vm);
         this.startUbuntuFrameBridge(vm);
         this.recordLiveBoot(profile, mediaProfile, disk, mavkAssets, encodedGeometry);
         this.show("Ubuntu Live started; waiting for the guest display.");
      } catch (Exception failure) {
         String reason = rootMessage(failure);
         this.lifecycle.failStart(reason);
         this.transition.failure(reason, SystemClock.elapsedRealtime());
         this.runOnUiThread(() -> this.renderProductScreen(this.productScreen == ProductScreen.SYSTEMS
               ? ProductScreen.SYSTEMS : this.isoPresent() ? ProductScreen.LINUX_HOME : ProductScreen.LINUX_SETUP));
         this.show("Ubuntu Live could not start: " + reason);
      }
   }

   private void recordUbuntuDisplayBoot(boolean installed, boolean temporary, boolean encoded, boolean mavk, int refreshHz) {
      this.getPreferences(0).edit().putBoolean("linux_current_boot_installed", installed)
            .putBoolean("linux_current_boot_temporary", temporary)
            .putBoolean("linux_current_boot_encoded", encoded).putBoolean("linux_current_boot_mavk", mavk)
            .putInt(DisplaySettings.RUNNING_REFRESH_HZ, refreshHz)
            .putBoolean("linux_current_boot_receive_encoded", this.displaySettings().encodedEnabled())
            .putString("linux_current_boot_h264_resolution", this.displayConfig(Mode.H264).resolution()).apply();
   }

   private int requestedUbuntuRefreshHz() {
      int fallback=DisplaySettings.DEFAULT_REFRESH_HZ;
      Intent intent=this.getIntent();
      return DisplaySettings.requireDiagnosticRefresh(intent==null?fallback:intent.getIntExtra("encoded_guest_refresh_hz",fallback));
   }

   private Object launchUbuntuVm(Object config, String consoleLog) throws Exception {
      Object vm = AvfGuest.replaceStopped(this, AvfGuest.UBUNTU_VM, config);
      this.userRequestedVmStop = false;
      this.attachCallback(vm);
      this.attachUbuntuConsole(vm,consoleLog);
      this.vmMayBeRunning = true;
      this.activeFrameVmName = AvfGuest.UBUNTU_VM;
      try {
         AvfGuest.run(vm);
      } catch (Exception failure) {
         this.vmMayBeRunning = false;
         this.activeFrameVmName = null;
         throw failure;
      }
      return vm;
   }

   private void recordLiveBoot(LiveProfile profile, UbuntuLiveMedia.Profile media, File disk,
                               MavkRuntimeAssets mavk, boolean encodedGeometry) {
      File report = new File(this.getExternalFilesDir(null), "frame-bridge-test-runtime-report.txt");
      try (PrintWriter log = new PrintWriter(new FileOutputStream(report))) {
         log.println("PROFILE=LINUX_LIVE_FRAME_BRIDGE");
         log.println("ENCODED_GEOMETRY_LIVE=" + encodedGeometry);
         log.println("MAVK_LIVE_ENABLED=" + (mavk != null));
         log.println("RENDERER_EXPERIMENT=" + profile.name());
         log.println("RUNTIME_DISK_EXPECTED_SHA256=" + media.expectedDiskDigest());
         log.println("RUNTIME_DISK_BYTES=" + disk.length());
         log.println("PLATFORM_PREFIX_SHA256=" + (mavk == null ? media.prefixSha() : mavk.sha256));
         log.println("ONE_DISK_ESP_PLUS_STOCK_ISO_PARTITION=PASS");
         log.println("OFFICIAL_ISO_MUTATED=NO");
         log.println("PERSISTENT_GUEST_DISK=NO_LIVE_SESSION");
         log.println("VM_LAUNCH=PASS");
      } catch (IOException error) {
         Log.w("U-AVF", "Could not save Ubuntu Live launch report", error);
      }
   }

   private Object buildGenericUbuntuConfig(File var1, File var2, File var3, File var4, boolean var5) throws Exception {
      return this.buildGenericUbuntuConfig(var1, var2, var3, var4, var5, "winavf-generic-ubuntu-24045");
   }

   private Object buildGenericUbuntuConfig(File var1, File var2, File var3, File var4, boolean var5, String var6) throws Exception {
      return this.buildGenericUbuntuConfig(var1, var2, var3, var4, var5, var6, false);
   }

   private Object buildGenericUbuntuConfig(File var1, File var2, File var3, File var4, boolean var5, String var6, boolean var7) throws Exception {
      return this.buildGenericUbuntuConfig(var1, var2, var3, var4, var5, var6, var7, false);
   }

   private Object buildGenericUbuntuConfig(File var1, File var2, File var3, File var4, boolean var5, String var6, boolean var7, boolean var8) throws Exception {
      return this.buildGenericUbuntuConfig(var1,var2,var3,var4,var5,var6,var7,var8,false);
   }

   private Object buildGenericUbuntuConfig(File var1, File var2, File var3, File var4, boolean var5, String var6, boolean var7, boolean var8, boolean mavkLive) throws Exception {
      int refreshHz=AvfGuest.UBUNTU_VM.equals(var6)?this.requestedUbuntuRefreshHz():DisplaySettings.DEFAULT_REFRESH_HZ;
      return this.buildGenericUbuntuConfig(var1,var2,var3,var4,var5,var6,var7,var8,mavkLive,refreshHz);
   }

   private Object buildGenericUbuntuConfig(File var1, File var2, File var3, File var4, boolean var5, String var6, boolean var7, boolean var8, boolean mavkLive, int guestRefreshHz) throws Exception {
      Class var9 = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
      Object var10 = Class.forName(var9.getName() + "$Builder").getConstructor().newInstance();
      call(var10, "setName", String.class, var6);
      call(var10, "setOsName", String.class, "ubuntu-24.04.5-stock");
      call(var10, "setKernelPath", String.class, var1.getAbsolutePath());
      boolean var11 = this.getPreferences(0).getBoolean("linux_internet_enabled", true);
      boolean var12 = this.getPreferences(0).getBoolean("linux_audio_output_enabled", true);
      call(var10, "useNetwork", Boolean.TYPE, var11);
      if (var12 && this.getPreferences(0).getBoolean("linux_audio_avf_native_experimental", false)) {
         Class var13 = Class.forName(var9.getName() + "$AudioConfig$Builder");
         Object var14 = var13.getConstructor().newInstance();
         call(var14, "setUseSpeaker", Boolean.TYPE, true);
         call(var14, "setUseMicrophone", Boolean.TYPE, false);
         Object var15 = call(var14, "build");
         call(var10, "setAudioConfig", var15.getClass(), var15);
      }

      call(var10, "useAutoMemoryBalloon", Boolean.TYPE, false);
      Class var24 = Class.forName(var9.getName() + "$Disk");
      call(var10, "addDisk", var24, var24.getMethod("RWDisk", String.class).invoke((Object)null, var2.getAbsolutePath()));
      if (var5) {
         call(var10, "addDisk", var24, var24.getMethod("RODisk", String.class).invoke((Object)null, var3.getAbsolutePath()));
      }

      if (var4 != null) {
         call(var10, "addDisk", var24, var24.getMethod("RODisk", String.class).invoke((Object)null, var4.getAbsolutePath()));
      }

      Class var25 = Class.forName(var9.getName() + "$DisplayConfig$Builder");
      Object var26 = var25.getConstructor().newInstance();
      WorkspaceConfig workspace=this.workspaceConfig();
      boolean managedWorkspace="winavf-frame-bridge-test-ubuntu-24045".equals(var6);
      DisplaySettings settings = this.displaySettings();
      WorkspaceConfig display=mavkLive?this.displayConfig(Mode.MAVK):workspace;
      if(!mavkLive && managedWorkspace && settings.encodedEnabled()
            && settings.hasResolution(Mode.H264))display=this.displayConfig(Mode.H264);
      call(var26, "setWidth", Integer.TYPE, managedWorkspace ? display.width : 1280);
      call(var26, "setHeight", Integer.TYPE, managedWorkspace ? display.height : 800);
      call(var26, "setHorizontalDpi", Integer.TYPE, 160);
      call(var26, "setVerticalDpi", Integer.TYPE, 160);
      call(var26, "setRefreshRate", Integer.TYPE, guestRefreshHz);
      Log.i("UAVF-Display", "Guest virtual display refresh request=" + guestRefreshHz);
      Object var16 = call(var26, "build");
      call(var10, "setDisplayConfig", var16.getClass(), var16);
      Class var17 = Class.forName(var9.getName() + "$GpuConfig$Builder");
      Object var18 = var17.getConstructor().newInstance();
      if (var8 && this.getIntent().getBooleanExtra("gfxstream_aligned_ring_probe", false)) {
         // One transient memory-backing A/B; no persisted preference changes.
         // Gfxstream ring blob id0 uses aligned process memory when ExternalBlob
         // is disabled, instead of exporting a shared-memory descriptor.
         call(var18, "setBackend", String.class, "gfxstream");
         call(var18, "setRendererUseEgl", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGles", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGlx", Boolean.class, Boolean.FALSE);
         call(var18, "setRendererUseSurfaceless", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseVulkan", Boolean.class, Boolean.TRUE);
         call(var18, "setContextTypes", String[].class,
            new String[]{"gfxstream-gles", "gfxstream-vulkan", "gfxstream-composer"});
         call(var18, "setRendererFeatures", String.class, "ExternalBlob:disabled");
      } else if (var8) {
         // Explicit diagnostic-only A/B; ordinary launches remain VirGL2.
         boolean venusProbe=this.getIntent().getBooleanExtra("venus_gpu_probe",false);
         call(var18, "setBackend", String.class, "virglrenderer");
         call(var18, "setRendererUseEgl", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGles", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGlx", Boolean.class, Boolean.FALSE);
         call(var18, "setRendererUseSurfaceless", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseVulkan", Boolean.class, Boolean.valueOf(venusProbe));
         call(var18, "setContextTypes", String[].class, venusProbe ?
            new String[]{"virgl2","venus"} : new String[]{"virgl2"});
      } else if (this.getPreferences(0).getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false)) {
         call(var18, "setBackend", String.class, "gfxstream");
         call(var18, "setRendererUseEgl", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGles", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGlx", Boolean.class, Boolean.FALSE);
         call(var18, "setRendererUseSurfaceless", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseVulkan", Boolean.class, Boolean.TRUE);
         call(var18, "setContextTypes", String[].class, new String[]{"gfxstream-gles", "gfxstream-vulkan", "gfxstream-composer"});
         String var19 = this.getPreferences(0).getBoolean("linux_gpu_gfxstream_system_blob_experimental", false) ? "SystemBlob:enabled" : (var7 ? "VulkanAllocateHostVisibleAsUdmabuf:disabled" : "VulkanAllocateHostMemory:enabled");
         call(var18, "setRendererFeatures", String.class, var19);
      } else if (this.getPreferences(0).getBoolean("linux_gpu_gfxstream_vulkan_experimental", false)) {
         call(var18, "setBackend", String.class, "gfxstream");
         call(var18, "setRendererUseEgl", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGles", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGlx", Boolean.class, Boolean.FALSE);
         call(var18, "setRendererUseSurfaceless", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseVulkan", Boolean.class, Boolean.FALSE);
         call(var18, "setContextTypes", String[].class, new String[]{"gfxstream-gles"});
      } else if (this.getPreferences(0).getBoolean("linux_gpu_virgl_experimental", false)) {
         call(var18, "setBackend", String.class, "virglrenderer");
         call(var18, "setRendererUseEgl", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGles", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseGlx", Boolean.class, Boolean.FALSE);
         call(var18, "setRendererUseSurfaceless", Boolean.class, Boolean.TRUE);
         call(var18, "setRendererUseVulkan", Boolean.class, Boolean.FALSE);
         call(var18, "setContextTypes", String[].class, new String[]{"virgl2"});
      }

      Object var27 = call(var18, "build");
      call(var10, "setGpuConfig", var27.getClass(), var27);
      Object var20 = call(var10, "build");
      Class var21 = Class.forName("android.system.virtualmachine.VirtualMachineConfig");
      String var10000 = var21.getName();
      Object var22 = Class.forName(var10000 + "$Builder").getConstructor(Context.class).newInstance(this);
      call(var22, "setProtectedVm", Boolean.TYPE, false);
      call(var22, "setMemoryBytes", Long.TYPE, managedWorkspace ? workspace.memoryBytes() : 4294967296L);
      String var23 = managedWorkspace ? workspace.topology() : "CPU_TOPOLOGY_ONE_CPU";
      call(var22, "setCpuTopology", Integer.TYPE, var21.getField(var23).getInt((Object)null));
      call(var22, "setDebugLevel", Integer.TYPE, 1);
      call(var22, "setConsoleInputDevice", String.class, "ttyS0");
      call(var22, "setVmOutputCaptured", Boolean.TYPE, true);
      call(var22, "setVmConsoleInputSupported", Boolean.TYPE, true);
      call(var22, "setCustomImageConfig", var9, var20);
      if(managedWorkspace) this.appendUbuntuInstallerAutostartReport("EFFECTIVE_LAUNCH_CONFIG RAM_BYTES="+workspace.memoryBytes()+" CPU="+workspace.topology()+" DISPLAY="+workspace.resolution()+" AUTO_BALLOON=false");
      return call(var22, "build");
   }

   private void startV22Ext4Control() {
      File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v22-ext4-control-report.txt");

      try {
         File var16 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v22-ext4-control.img");
         if (!var16.isFile() || var16.length() != 9126805504L) {
            throw new IllegalStateException("Missing V22 control image: " + String.valueOf(var16));
         }

         String var17 = Digest.hex(Digest.sha256File(var16));
         if (!V22_EXT4_CONTROL_MEDIA_SHA256.equals(var17)) {
            throw new SecurityException("V22 control image hash mismatch: " + var17);
         }

         File var4 = new File(this.getFilesDir(), "v22-ext4-control-payload");
         if (!var4.exists() && !var4.mkdirs()) {
            throw new IllegalStateException("Cannot create V22 payload directory");
         }

         File var5 = this.copyFile(var16, new File(var4, "ubuntu-gnome-v22-ext4-control.img"), 9126805504L, false);
         String var6 = Digest.hex(Digest.sha256File(var5));
         PrintWriter var7 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var7.println("PROFILE=V22_EXT4_CONTROL");
            var7.println("VM_NAME=winavf-ubuntu-gnome-24045-v22-ext4-control");
            var7.println("MEDIA_NAME=ubuntu-gnome-v22-ext4-control.img");
            var7.println("MEDIA_LABEL=V22_EXT4_CONTROL");
            var7.println("MEDIA_BYTES=9126805504");
            var7.println("MEDIA_SHA256=" + var6);
            var7.println("CONTROL_IMAGE_STAGED=PASS");
            var7.println("BOOTABLE_UBUNTU_ROOT=BLOCKED");
            var7.println("BOOTABLE_UBUNTU_ROOT_REASON=No guest root filesystem or EFI/initrd boot contract is present; control image must not be used as root disk.");
            var7.println("EXT4_BLOCK_IO_CONTROL=BLOCKED");
            var7.println("EXT4_BLOCK_IO_CONTROL_REASON=Guest-side EXT4_BLOCK_IO_CONTROL protocol and runnable Ubuntu payload are unconfirmed.");
            var7.println("VM_LAUNCH=NOT_ATTEMPTED");
         } catch (Throwable var14) {
            try {
               var7.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }

            throw var14;
         }

         var7.close();
         this.show("V22 control image staged; launch blocked pending Ubuntu root and guest protocol.");
      } catch (Throwable var15) {
         Throwable var2 = var15;

         try {
            PrintWriter var3 = new PrintWriter(new FileOutputStream(var1, false));

            try {
               var3.println("PROFILE=V22_EXT4_CONTROL");
               var3.println("EXT4_BLOCK_IO_CONTROL=BLOCKED");
               var3.println("ERROR=" + rootMessage(var2));
            } catch (Throwable var11) {
               try {
                  var3.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }

               throw var11;
            }

            var3.close();
         } catch (Throwable var12) {
         }

         this.show("V22 control path blocked: " + rootMessage(var15));
      }

   }

   private void startV23Ext4BlockIo() {
      File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v23-ext4-block-io-report.txt");

      try {
         File var13 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v21-v23-disposable-carrier.img");
         File var14 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-v22-ext4-control.img");
         PrintWriter var4 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var4.println("PROFILE=V23_EXT4_BLOCK_IO");
            var4.println("VM_NAME=winavf-ubuntu-gnome-24045-v23-ext4-block-io");
            var4.println("AVF_DISK_API=Disk.RODisk(String)+Builder.addDisk(Disk)");
            var4.println("CONTROL_DISK_PRESENT=" + var14.isFile());
            var4.println("BOOT_CARRIER_PRESENT=" + var13.isFile());
            if (var14.isFile() && var14.length() == 9126805504L) {
               if (!V22_EXT4_CONTROL_MEDIA_SHA256.equals(Digest.hex(Digest.sha256File(var14)))) {
                  var4.println("EXT4_BLOCK_IO=BLOCKED");
                  var4.println("REASON=V22 control disk hash mismatch");
               } else if (var13.isFile() && var13.length() != 0L) {
                  var4.println("CONTROL_DISK_SHA256=" + Digest.hex(Digest.sha256File(var14)));
                  var4.println("BOOT_CARRIER_BYTES=" + var13.length());
                  var4.println("EXT4_BLOCK_IO=API_READY_GUEST_CARRIER_PENDING_HASH");
                  var4.println("VM_LAUNCH=NOT_ATTEMPTED");
               } else {
                  var4.println("EXT4_BLOCK_IO=BLOCKED");
                  var4.println("REASON=Verified V21 boot carrier is not staged; VM launch not attempted");
               }
            } else {
               var4.println("EXT4_BLOCK_IO=BLOCKED");
               var4.println("REASON=Verified V22 control disk is not staged");
            }
         } catch (Throwable var11) {
            try {
               var4.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }

            throw var11;
         }

         var4.close();
         this.show("V23 two-disk API boundary audited; guest launch remains gated by verified boot carrier.");
      } catch (Throwable var12) {
         Throwable var2 = var12;

         try {
            PrintWriter var3 = new PrintWriter(new FileOutputStream(var1, false));

            try {
               var3.println("PROFILE=V23_EXT4_BLOCK_IO");
               var3.println("EXT4_BLOCK_IO=BLOCKED");
               var3.println("ERROR=" + rootMessage(var2));
            } catch (Throwable var8) {
               try {
                  var3.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            var3.close();
         } catch (Throwable var9) {
         }

         this.show("V23 block-I/O path blocked: " + rootMessage(var12));
      }

   }

   private void cleanupUbuntuGnome() {
      File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-cleanup-report.txt");

      try {
         Object var18 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

         try {
            Object var19 = var18.getClass().getMethod("get", String.class).invoke(var18, "winavf-ubuntu-gnome-24045-v21");
            if (var19 != null) {
               try {
                  var19.getClass().getMethod("stop").invoke(var19);
               } catch (Throwable var15) {
               }

               var18.getClass().getMethod("delete", String.class).invoke(var18, "winavf-ubuntu-gnome-24045-v21");
            }
         } catch (Exception var16) {
         }

         File var20 = new File(this.getFilesDir(), "ubuntu-gnome-payload");
         File var4 = new File(var20, "ubuntu-gnome-24.04.5-v21-vsock-listener.img");
         if (var4.exists() && !var4.delete()) {
            throw new IllegalStateException("Could not delete disposable Ubuntu disk");
         }

         File var5 = new File(var20, "u-boot-wrapper-v24.Image");
         if (var5.exists() && !var5.delete()) {
            throw new IllegalStateException("Could not delete disposable Ubuntu loader copy");
         }

         File var6 = new File(var20, "ubuntu-gnome-v21-vsock-firmware.patch");
         if (var6.exists() && !var6.delete()) {
            throw new IllegalStateException("Could not delete disposable Ubuntu firmware patch");
         }

         if (var20.exists() && !var20.delete()) {
            throw new IllegalStateException("Could not delete empty Ubuntu payload directory");
         }

         PrintWriter var7 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var7.println("RESULT=PASS");
            var7.println("SCOPE=ubuntu-gnome-payload-only");
            var7.println("WINDOWS_PAYLOAD_UNTOUCHED=true");
         } catch (Throwable var14) {
            try {
               var7.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }

            throw var14;
         }

         var7.close();
         this.show("Disposed the separate Ubuntu GNOME profile; Windows media was untouched.");
      } catch (Throwable var17) {
         Throwable var2 = var17;

         try {
            PrintWriter var3 = new PrintWriter(new FileOutputStream(var1, false));

            try {
               var3.println("RESULT=FAIL");
               var3.println("ERROR=" + rootMessage(var2));
            } catch (Throwable var11) {
               try {
                  var3.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }

               throw var11;
            }

            var3.close();
         } catch (Throwable var12) {
         }

         this.show("Ubuntu GNOME cleanup failed: " + rootMessage(var17));
      }

   }

   private void startProductKdBridge(String var1) {
      File var2 = new File(this.getExternalFilesDir((String)null), "product-kd-bridge-report.txt");
      if (var1 != null && var1.matches("[0-9A-Fa-f]{32,128}")) {
         try {
            ServerSocket var3 = new ServerSocket(39100, 1, InetAddress.getLoopbackAddress());

            try {
               var3.setSoTimeout(45000);
               PrintWriter var4 = new PrintWriter(new FileOutputStream(var2, false));

               try {
                  var4.println("scope=APP_OWNED_PRODUCT_VM_RAW_KD_BRIDGE");
                  var4.println("listener=127.0.0.1:39100");
                  var4.println("state=LISTENING");
                  var4.flush();
                  this.show("KD bridge waiting for the host connection…");
                  Socket var5 = var3.accept();
                  var5.setTcpNoDelay(true);
                  var5.setSoTimeout(45000);
                  byte[] var6 = IoUtil.readExactly(var5.getInputStream(), var1.length());
                  if (!var1.equals(new String(var6, StandardCharsets.US_ASCII))) {
                     throw new SecurityException("KD bridge capability token mismatch");
                  }

                  var5.getOutputStream().write("WINAVF_KD_BRIDGE_OK\n".getBytes(StandardCharsets.US_ASCII));
                  var5.getOutputStream().flush();
                  var5.setSoTimeout(0);
                  this.activeKdBridgeSocket = var5;
                  var4.println("state=HOST_AUTHENTICATED");
                  var4.flush();
                  File var7 = new File(this.getFilesDir(), "payload");
                  if (!var7.exists() && !var7.mkdirs()) {
                     throw new IllegalStateException("Cannot create payload directory");
                  }

                  File var8 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var7, "u-boot-wrapper-v24.Image"), -1L);
                  File var9 = new File(this.getExternalFilesDir((String)null), "win11-gop-ebs-r1.img");
                  if (!var9.isFile() || var9.length() != 9126805504L) {
                     throw new IllegalStateException("Missing compact headless boot medium: " + String.valueOf(var9));
                  }

                  File var10 = this.copyFile(var9, new File(var7, "win11-gop-ebs-r1.img"), 9126805504L, false);
                  this.applyStagedImagePatch(var7, var10);
                  Object var11 = this.buildConfig(var8, var10, false);
                  Object var12 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

                  try {
                     Object var13 = var12.getClass().getMethod("get", String.class).invoke(var12, "winavf-gop-ebs-r1");
                     if (var13 != null) {
                        var12.getClass().getMethod("delete", String.class).invoke(var12, "winavf-gop-ebs-r1");
                     }
                  } catch (Exception var26) {
                  }

                  Object var31 = var12.getClass().getMethod("create", String.class, var11.getClass()).invoke(var12, "winavf-gop-ebs-r1", var11);
                  this.activeKdBridgeVm = var31;
                  this.attachCallback(var31);
                  InputStream var14 = (InputStream)var31.getClass().getMethod("getConsoleOutput").invoke(var31);
                  OutputStream var15 = (OutputStream)var31.getClass().getMethod("getConsoleInput").invoke(var31);
                  var4.println("state=VM_CREATED");
                  var4.flush();
                  this.startProductKdBridgePumps(var14, var15, var5);
                  var31.getClass().getMethod("run").invoke(var31);
                  var4.println("state=VM_RUNNING");
                  var4.flush();
                  this.show("Product KD bridge running on raw ttyS0.");

                  while(!var5.isClosed()) {
                     Thread.sleep(1000L);
                  }
               } catch (Throwable var27) {
                  try {
                     var4.close();
                  } catch (Throwable var25) {
                     var27.addSuppressed(var25);
                  }

                  throw var27;
               }

               var4.close();
            } catch (Throwable var28) {
               try {
                  var3.close();
               } catch (Throwable var24) {
                  var28.addSuppressed(var24);
               }

               throw var28;
            }

            var3.close();
         } catch (Throwable var29) {
            writeBridgeFailure(var2, rootMessage(var29));
            this.show("Product KD bridge failed: " + rootMessage(var29));
         } finally {
            this.activeKdBridgeSocket = null;
            this.activeKdBridgeVm = null;
         }

      } else {
         writeBridgeFailure(var2, "invalid diagnostic capability token");
      }
   }

   private void startProductKdBridgePumps(InputStream var1, OutputStream var2, Socket var3) {
      File var4 = new File(this.getExternalFilesDir((String)null), "product-kd-bridge-rx.bin");
      File var5 = new File(this.getExternalFilesDir((String)null), "product-kd-bridge-tx.bin");
      (new Thread(() -> {
         try {
            FileOutputStream var4x = new FileOutputStream(var4, false);

            try {
                OutputStream socketOutput = var3.getOutputStream();

               try {
                  byte[] var6 = new byte[4096];

                  int var7;
                  while((var7 = var1.read(var6)) >= 0) {
                     var4x.write(var6, 0, var7);
                     var4x.flush();
                      socketOutput.write(var6, 0, var7);
                      socketOutput.flush();
                     this.frameDecoder.feed(var6, 0, var7);
                  }
               } catch (Throwable var10) {
                   if (socketOutput != null) {
                     try {
                         socketOutput.close();
                     } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                     }
                  }

                  throw var10;
               }

                if (socketOutput != null) {
                   socketOutput.close();
               }
            } catch (Throwable var11) {
               try {
                  var4x.close();
               } catch (Throwable var8) {
                  var11.addSuppressed(var8);
               }

               throw var11;
            }

            var4x.close();
         } catch (Throwable var12) {
            IoUtil.closeQuietly(var3);
         }

      }, "WinAVF-product-kd-rx")).start();
      (new Thread(() -> {
         try {
            FileOutputStream var3x = new FileOutputStream(var5, false);

            try {
                InputStream socketInput = var3.getInputStream();

               try {
                  byte[] var5x = new byte[4096];

                  int var6;
                   while((var6 = socketInput.read(var5x)) >= 0) {
                     var3x.write(var5x, 0, var6);
                     var3x.flush();
                     var2.write(var5x, 0, var6);
                     var2.flush();
                  }
               } catch (Throwable var9) {
                   if (socketInput != null) {
                     try {
                         socketInput.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

                if (socketInput != null) {
                   socketInput.close();
               }
            } catch (Throwable var10) {
               try {
                  var3x.close();
               } catch (Throwable var7) {
                  var10.addSuppressed(var7);
               }

               throw var10;
            }

            var3x.close();
         } catch (Throwable var11) {
            IoUtil.closeQuietly(var3);
         }

      }, "WinAVF-product-kd-tx")).start();
   }

   private void stopProductKdBridge() {
      try {
         Object var1 = this.activeKdBridgeVm;
         if (var1 == null) {
            Object var2 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
            var1 = var2.getClass().getMethod("get", String.class).invoke(var2, "winavf-gop-ebs-r1");
         }

         if (var1 != null) {
            var1.getClass().getMethod("stop").invoke(var1);
         }
      } catch (Throwable var3) {
      }

      IoUtil.closeQuietly(this.activeKdBridgeSocket);
      this.show("Product KD bridge stop requested.");
   }

   private static void writeBridgeFailure(File var0, String var1) {
      try {
         PrintWriter var2 = new PrintWriter(new FileOutputStream(var0, false));

         try {
            var2.println("scope=APP_OWNED_PRODUCT_VM_RAW_KD_BRIDGE");
            var2.println("state=FAILED");
            var2.println("error=" + var1);
         } catch (Throwable var6) {
            try {
               var2.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         var2.close();
      } catch (Throwable var7) {
      }

   }

   private void exportProductBcdReadOnly() {
      File var1 = new File(this.getExternalFilesDir((String)null), "product-bcd-readonly-report.txt");
      File var2 = new File(this.getExternalFilesDir((String)null), "product-bcd-readonly.bin");

      try {
         File var3 = new File(this.getExternalFilesDir((String)null), "win11-gop-ebs-r1.img");
         byte[] var4 = readEspFile(var3, "EFI", "MICROSOFT", "BOOT", "BCD");
         FileOutputStream var5 = new FileOutputStream(var2, false);

         try {
            var5.write(var4);
            var5.getFD().sync();
         } catch (Throwable var11) {
            try {
               var5.close();
            } catch (Throwable var9) {
               var11.addSuppressed(var9);
            }

            throw var11;
         }

         var5.close();
         PrintWriter var13 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var13.println("scope=READ_ONLY_PRODUCT_BASELINE_BCD_EXPORT");
            var13.println("imageBytes=" + var3.length());
            var13.println("bcdBytes=" + var4.length);
            var13.println("bcdSha256=" + Digest.hex(Digest.sha256(var4)));
            var13.println("result=PASS");
         } catch (Throwable var10) {
            try {
               var13.close();
            } catch (Throwable var8) {
               var10.addSuppressed(var8);
            }

            throw var10;
         }

         var13.close();
         this.show("Read-only product BCD export complete.");
      } catch (Throwable var12) {
         writeBridgeFailure(var1, rootMessage(var12));
         this.show("Read-only product BCD export failed: " + rootMessage(var12));
      }

   }

   private void startVsockHelloProbe(Object var1) {
      (new Thread(() -> {
         File var2 = new File(this.getExternalFilesDir((String)null), "vsock-hello-probe-report.txt");

         try {
            PrintWriter var3 = new PrintWriter(new FileOutputStream(var2, false));

            label192: {
               try {
                  var3.println("transport=AVF_CONNECT_VSOCK");
                  var3.println("port=4050");
                  Throwable var4 = null;
                  int var5 = 1;

                  while(true) {
                     if (var5 > 60) {
                        var3.println("result=VSOCK_HELLO_NOT_OBSERVED");
                        String var10001 = var4 == null ? "none" : rootMessage(var4);
                        var3.println("lastError=" + var10001);
                        this.show("VSOCK HELLO was not observed.");
                        break label192;
                     }

                     try {
                        Method var6 = var1.getClass().getMethod("connectVsock", Long.TYPE);
                        ParcelFileDescriptor var7 = (ParcelFileDescriptor)var6.invoke(var1, 4050L);

                        try {
                           FileInputStream var8 = new FileInputStream(var7.getFileDescriptor());

                           try {
                              byte[] var9 = new byte[16];

                              int var11;
                              for(int var10 = 0; var10 < var9.length; var10 += var11) {
                                 var11 = var8.read(var9, var10, var9.length - var10);
                                 if (var11 < 0) {
                                    throw new IllegalStateException("short WVH1 reply");
                                 }
                              }

                              if (var9[0] != 87 || var9[1] != 86 || var9[2] != 72 || var9[3] != 49) {
                                 throw new IllegalStateException("unexpected vsock hello magic");
                              }

                              long var30 = (long)var9[8] & 255L | ((long)var9[9] & 255L) << 8 | ((long)var9[10] & 255L) << 16 | ((long)var9[11] & 255L) << 24;
                              long var13 = (long)var9[12] & 255L | ((long)var9[13] & 255L) << 8 | ((long)var9[14] & 255L) << 16 | ((long)var9[15] & 255L) << 24;
                              var3.println("attempt=" + var5);
                              var3.println("hello=WVH1");
                              var3.println("agentStatus=" + var30);
                              var3.println("capabilities=" + var13);
                              var3.println("result=VSOCK_HELLO_PASS");
                              this.show("VSOCK HELLO received from WinPE agent.");
                           } catch (Throwable var25) {
                              try {
                                 var8.close();
                              } catch (Throwable var24) {
                                 var25.addSuppressed(var24);
                              }

                              throw var25;
                           }

                           var8.close();
                           break;
                        } finally {
                           var7.close();
                        }
                     } catch (Throwable var27) {
                        var4 = var27;
                        Thread.sleep(1000L);
                        ++var5;
                     }
                  }
               } catch (Throwable var28) {
                  try {
                     var3.close();
                  } catch (Throwable var23) {
                     var28.addSuppressed(var23);
                  }

                  throw var28;
               }

               var3.close();
               return;
            }

            var3.close();
         } catch (Throwable var29) {
            this.show("VSOCK HELLO probe failed: " + rootMessage(var29));
         }

      }, "WinAVF-vsock-hello-probe")).start();
   }

   private void startUbuntuVsockHelloProbe(Object var1) {
      this.startUbuntuVsockHelloProbe(var1, false);
   }

   private void startUbuntuVsockHelloProbe(Object var1, boolean var2) {
      (new Thread(() -> {
         File var3 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-vsock-report.txt");

         try {
            PrintWriter var4 = new PrintWriter(new FileOutputStream(var3, false));

            label304: {
               label303: {
                  try {
                     var4.println("transport=AVF_CONNECT_VSOCK");
                     var4.println("guest=" + (var2 ? "UBUNTU_INSTALLED_SYSTEM" : "UBUNTU_LIVE_OR_INITRAMFS"));
                     var4.println("port=4051");
                     Throwable var5 = null;
                     int var6 = 0;
                     int var7 = 1;
                     // Readiness revision 1 is a valid "still preparing" reply,
                     // not a failed connection. Bound elapsed time, not replies:
                     // a fast socket must not exhaust the entire first-boot wait.
                     long readinessDeadline = android.os.SystemClock.elapsedRealtime()
                           + (var2 ? 300000L : 90000L);

                     while(true) {
                        if (this.activityDestroyed || !this.lifecycle.owns(var1)
                              || !this.lifecycle.busy()) {
                           var4.println("result=LINUX_VSOCK_PROBE_CANCELLED");
                           break label304;
                        }
                        if (android.os.SystemClock.elapsedRealtime() >= readinessDeadline) {
                           var4.println("result=" + (var2 && var6 == 1 ? "LINUX_INSTALLED_RUNTIME_NOT_READY" : "LINUX_VSOCK_HELLO_NOT_OBSERVED"));
                           var4.println("lastRevision=" + var6);
                           String var10001 = var5 == null ? "none" : rootMessage(var5);
                           var4.println("lastError=" + var10001);
                           this.show("Ubuntu vsock HELLO was not observed.");
                           break label304;
                        }

                        try {
                           Method var8 = var1.getClass().getMethod("connectVsock", Long.TYPE);
                           ParcelFileDescriptor var9 = (ParcelFileDescriptor)var8.invoke(var1, 4051L);

                           try {
                              label314: {
                                 FileInputStream var10 = new FileInputStream(var9.getFileDescriptor());

                                 label291: {
                                    label290: {
                                       try {
                                          byte[] var11 = new byte[16];

                                          int var13;
                                          for(int var12 = 0; var12 < var11.length; var12 += var13) {
                                             var13 = var10.read(var11, var12, var11.length - var12);
                                             if (var13 < 0) {
                                                throw new IllegalStateException("short LVH1 reply");
                                             }
                                          }

                                          if (var11[0] != 76 || var11[1] != 86 || var11[2] != 72 || var11[3] != 49) {
                                             throw new IllegalStateException("unexpected Linux vsock hello magic");
                                          }

                                          long var34 = (long)var11[4] & 255L | ((long)var11[5] & 255L) << 8 | ((long)var11[6] & 255L) << 16 | ((long)var11[7] & 255L) << 24;
                                          this.ubuntuGuestRuntimeRevision = (int)var34;
                                          var6 = (int)var34;
                                          long var15 = (long)var11[8] & 255L | ((long)var11[9] & 255L) << 8 | ((long)var11[10] & 255L) << 16 | ((long)var11[11] & 255L) << 24;
                                          long var17 = (long)var11[12] & 255L | ((long)var11[13] & 255L) << 8 | ((long)var11[14] & 255L) << 16 | ((long)var11[15] & 255L) << 24;
                                          if (var34 >= 2L && (var17 & 3L) == 3L) {
                                             this.getPreferences(0).edit().putBoolean("ubuntu_installed_runtime_ready", true)
                                                   .remove(DisplaySettings.FIRST_BOOT_MODE)
                                                   .remove(DisplaySettings.FIRST_BOOT_OVERRIDE).apply();
                                             this.lifecycle.install(VmLifecycle.Install.READY);
                                             var4.println("attempt=" + var7);
                                             var4.println("hello=LVH1");
                                             var4.println("revision=" + var34);
                                             var4.println("agentStatus=" + var15);
                                             var4.println("capabilities=" + var17);
                                             var4.println("audioSocketAdvertised=" + ((var17 & 4L) != 0L));
                                             var4.println("result=LINUX_INSTALLED_RUNTIME_READY");
                                             this.show("Installed Ubuntu runtime is ready.");
                                             break label291;
                                          }

                                          if (!var2) {
                                             var4.println("attempt=" + var7);
                                             var4.println("hello=LVH1");
                                             var4.println("revision=" + var34);
                                             var4.println("agentStatus=" + var15);
                                             var4.println("capabilities=" + var17);
                                             var4.println("audioSocketAdvertised=" + ((var17 & 4L) != 0L));
                                             var4.println("result=LINUX_VSOCK_HELLO_PASS");
                                             this.show("Ubuntu vsock HELLO received; Linux transport is live.");
                                             break label290;
                                          }

                                          var4.println("attempt=" + var7);
                                          var4.println("lastRevision=" + var34);
                                          var4.flush();
                                       } catch (Throwable var29) {
                                          try {
                                             var10.close();
                                          } catch (Throwable var28) {
                                             var29.addSuppressed(var28);
                                          }

                                          throw var29;
                                       }

                                       var10.close();
                                       break label314;
                                    }

                                    var10.close();
                                    break;
                                 }

                                 var10.close();
                                 break label303;
                              }
                           } finally {
                              var9.close();
                           }
                        } catch (Throwable var31) {
                           var5 = var31;
                        }

                        Thread.sleep(1000L);
                        ++var7;
                     }
                  } catch (Throwable var32) {
                     try {
                        var4.close();
                     } catch (Throwable var27) {
                        var32.addSuppressed(var27);
                     }

                     throw var32;
                  }

                  var4.close();
                  return;
               }

               var4.close();
               return;
            }

            var4.close();
         } catch (Throwable var33) {
            this.show("Ubuntu vsock probe failed: " + rootMessage(var33));
         }

      }, "WinAVF-ubuntu-vsock-hello")).start();
   }

   private enum DisplayFallback { ALLOW_ENCODED_PATHS, FORCE_RAW }

   private void startUbuntuFrameBridge(Object vm) {
      this.startUbuntuFrameBridge(vm, DisplayFallback.ALLOW_ENCODED_PATHS);
   }

   private void startUbuntuFrameBridge(Object vm, DisplayFallback fallback) {
      this.runOnUiThread(() -> {
         if (this.activityDestroyed || this.isFinishing() || !this.lifecycle.owns(vm)) return;
         int epoch = this.displayConnection.begin();
         this.closeGuestDisplayViews();
         this.connectUbuntuServices(vm);
         var prefs = this.getPreferences(0);
         if (fallback == DisplayFallback.FORCE_RAW) {
            this.startRawUbuntuDisplay(vm, epoch);
         } else if (prefs.getBoolean("linux_current_boot_mavk", false)) {
            this.startMavkUbuntuDisplay(vm, epoch);
         } else if (prefs.getBoolean("linux_current_boot_encoded",
               prefs.getBoolean("linux_current_boot_installed", false)) && this.displaySettings().runningReceivesEncoded()) {
            String requested = prefs.getString("linux_current_boot_h264_resolution", this.displayConfig(Mode.H264).resolution());
            this.startEncodedUbuntuDisplay(vm, epoch, requested);
         } else {
            this.startRawUbuntuDisplay(vm, epoch);
         }
      });
   }

   private void connectUbuntuServices(Object vm) {
      try {
         this.lifecycle.reconcile(vm, AvfGuest.state(vm), this.persistentUbuntuDiskFile().isFile());
      } catch (Exception error) {
         this.lifecycle.failed(vm, "AVF state unavailable: " + rootMessage(error));
      }
      if (this.controlVm != vm || this.guestControl == null) {
         if (this.guestControl != null) this.guestControl.close();
         this.controlVm = vm;
         this.plasmaInstallState = "unavailable";
         var prefs = this.getPreferences(0);
         Boolean installedMavk = prefs.getBoolean("linux_current_boot_installed", false)
               ? prefs.getBoolean("linux_current_boot_mavk", false) : null;
         this.guestControl = new GuestControl(vm, installedMavk, (connected, detail, session) -> {
            this.runOnUiThread(() -> this.onUbuntuControlUpdate(vm, connected, detail, session));
         });
      }
      if (this.encodedDisplayVm != vm) {
         this.encodedDisplayVm = vm;
         this.encodedSessionReconnects = 0;
         this.encodedSessionReconnectWindowMs = 0;
      }
      this.startUbuntuAudioBridge(vm);
      if (this.clipboardVm != vm) {
         if (this.guestClipboard != null) this.guestClipboard.close();
         this.guestClipboard = null;
         this.clipboardVm = vm;
      }
      this.syncGuestClipboard();
   }

   private void onUbuntuControlUpdate(Object vm, boolean connected, String detail, org.json.JSONObject session) {
      if (this.activityDestroyed || this.lifecycleVm != vm || this.controlVm != vm) return;
      if (!detail.equals(this.guestControlStatus)) Log.i("U-AVF-control", detail);
      this.guestControlStatus = detail;
      this.mavkSessionReady = connected && session != null && session.optBoolean("ok")
            && session.optBoolean("sessions_available");
      if (this.mavkDisplay != null) this.mavkDisplay.setGuestSessionReady(this.mavkSessionReady);
      if (!connected || session == null) return;

      this.updatePlasmaInstallState(session.optString("plasma_x11", "unavailable"));
      var prefs = this.getPreferences(0);
      if (session.has("mavk_selected") && !session.optBoolean("mavk_selected")
            && prefs.getBoolean("linux_current_boot_mavk", false)) {
         prefs.edit().putBoolean("linux_current_boot_mavk", false).apply();
         Log.w("UAVF-Mavk", "Using stable display: " + session.optString("mavk_mode_error"));
         this.startUbuntuFrameBridge(vm);
      }
      if (session.optBoolean("growth_supported") && session.optInt("growth_version") == 1) {
         var growth = prefs.edit().putBoolean("ubuntu_growth_helper_verified", true);
         long pending = prefs.getLong("ubuntu_growth_pending_bytes", 0);
         if (pending > 0 && session.optLong("filesystem_bytes") >= pending) growth.remove("ubuntu_growth_pending_bytes");
         growth.apply();
      }
      if (this.mavkSessionReady) prefs.edit().putString("runtime_verified_build", Build.FINGERPRINT).apply();
      this.transition.guestReady(session.optBoolean("sessions_available"), SystemClock.elapsedRealtime());
      if (this.transitionView != null) this.transitionView.refresh();
   }

   private void closeGuestDisplayViews() {
      if (this.mavkDisplay != null) {
         this.mavkDisplay.close();
         this.mavkDisplay = null;
      }
      if (this.mavkSurface != null) {
         this.page.removeView(this.mavkSurface);
         this.mavkSurface = null;
      }
      if (this.installedEncodedDisplay != null) {
         this.installedEncodedDisplay.closeSilently();
         this.installedEncodedDisplay = null;
      }
      if (this.installedEncodedSurface != null) {
         this.page.removeView(this.installedEncodedSurface);
         this.installedEncodedSurface = null;
      }
      if (this.frameSurface != null) this.frameSurface.setEncodedMode(false, 0, 0);
   }

   private android.view.SurfaceView addGuestDisplaySurface() {
      var surface = new android.view.SurfaceView(this);
      this.page.addView(surface, 0, new FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER));
      return surface;
   }

   private void startMavkUbuntuDisplay(Object vm, int epoch) {
      var surface = this.addGuestDisplaySurface();
      this.mavkSurface = surface;
      surface.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> this.fitMavkSurface());
      int refreshHz=this.getPreferences(0).getInt(DisplaySettings.RUNNING_REFRESH_HZ,DisplaySettings.DEFAULT_REFRESH_HZ);
      var renderer = new MavkDisplayRuntime(this, vm, refreshHz, input -> this.displayConnection.publishInput(epoch, input),
            () -> {
               if (this.mavkSurface != surface || !this.displayConnection.current(epoch)
                     || !this.transition.acceptsDesktop()) return;
               this.transition.event("Display connected", "Mavk AHB display fence", SystemClock.elapsedRealtime());
               this.presentUbuntuSurface(this.mavkDisplay.frameWidth(), this.mavkDisplay.frameHeight(), this::fitMavkSurface);
            }, reason -> {
               if (this.mavkSurface != surface || !this.displayConnection.publishInput(epoch, null)) return;
               boolean wasVisible = this.frameVisible;
               this.frameVisible = false;
               this.closeGuestDisplayViews();
               Log.e("UAVF-Mavk", reason);
               if(!this.vmMayBeRunning || !this.lifecycle.owns(vm) || !this.transition.acceptsDesktop())return;
               if(wasVisible || !this.transition.visible())
                  this.beginRuntimeTransition(RuntimeTransition.Mode.DISPLAY_RECONNECT);
               this.transition.failure(UiLanguage.text("Сеанс Ubuntu всё ещё работает. Mavk потерял вывод изображения."),
                     SystemClock.elapsedRealtime());
               this.transitionTick.run();
            }, () -> this.desktopHasFocus() && this.toolbar.getVisibility() != View.VISIBLE && !this.transition.visible());
      this.mavkDisplay = renderer;
      renderer.setGuestSessionReady(this.mavkSessionReady);
      renderer.start(surface);
   }

   private void startEncodedUbuntuDisplay(Object vm, int epoch, String requested) {
      var surface = this.addGuestDisplaySurface();
      this.installedEncodedSurface = surface;
      surface.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
         this.fitEncodedSurface();
         // Clip the known one-source-pixel edge artifact without moving the viewport.
         int width = this.installedEncodedDisplay == null ? 1920 : this.installedEncodedDisplay.streamWidth();
         if (r > l && b > t) surface.setClipBounds(new android.graphics.Rect(Math.max(1, (r - l + width - 1) / width), 0, r - l, b - t));
      });

      var receiver = new EncodedDisplayProbe(this, vm, 60, () -> this.uiHandler.postDelayed(
            () -> this.onEncodedUbuntuDisplayEnded(vm, epoch, surface, requested), 1200));
      receiver.onStreamGeometry(() -> {
         if (this.installedEncodedSurface == surface) this.fitEncodedSurface();
      });
      receiver.requestDesktopSize(new WorkspaceConfig(4, false, requested));
      this.installedEncodedDisplay = receiver;
      receiver.startEmbedded(surface, input -> {
         if (this.displayConnection.publishInput(epoch, input)) {
            this.transition.event("Display connected", "Encoded vsock stream", SystemClock.elapsedRealtime());
         }
      }, () -> {
         if (this.installedEncodedSurface != surface || !this.displayConnection.current(epoch)
               || !this.transition.acceptsDesktop()) return;
         boolean firstFrame = !this.frameVisible;
         this.presentUbuntuSurface(receiver.streamWidth(), receiver.streamHeight(), this::fitEncodedSurface);
         String actual = this.guestFrameWidth + "x" + this.guestFrameHeight;
         if (firstFrame && !requested.equals(actual)) this.reportDifferentDesktopSize(requested, actual);
      }, () -> {
         if (this.mayReconnectDisplay(vm, epoch) && this.installedEncodedSurface == surface) {
            this.guestInput.releaseKeys();
            this.startUbuntuFrameBridge(vm);
         }
      });
   }

   private void onEncodedUbuntuDisplayEnded(Object vm, int epoch, android.view.SurfaceView surface, String requested) {
      if (!this.mayReconnectDisplay(vm, epoch) || this.installedEncodedSurface != surface || this.userRequestedVmStop) return;
      long now = SystemClock.elapsedRealtime();
      if (now - this.encodedSessionReconnectWindowMs > 60000) {
         this.encodedSessionReconnectWindowMs = now;
         this.encodedSessionReconnects = 0;
      }
      boolean retry = this.installedEncodedDisplay != null && this.installedEncodedDisplay.retryableSessionEnd()
            && this.encodedSessionReconnects < 3;
      this.guestInput.releaseKeys();
      this.displayConnection.publishInput(epoch, null);
      this.closeGuestDisplayViews();
      if (retry) {
         this.encodedSessionReconnects++;
         Log.i("UAVF-H264", "Reconnecting after guest X11 session handoff");
         this.uiHandler.postDelayed(() -> {
            if (this.mayReconnectDisplay(vm, epoch)) this.startUbuntuFrameBridge(vm);
         }, 1000);
      } else if (!"1920x1200".equals(requested)) {
         Log.w("UAVF-H264", "Requested display " + requested + " failed; fallback requires confirmation");
         this.encodedFallbackDialog = ProductDialog.show(this, "Графический вывод",
               "Не удалось подключить видеопоток с выбранным разрешением.", "Резервный", false, () -> {
                  if (this.mayReconnectDisplay(vm, epoch)) this.startUbuntuFrameBridge(vm, DisplayFallback.FORCE_RAW);
               });
      } else {
         this.show("Encoded display unavailable. Restoring stable display.");
         this.startUbuntuFrameBridge(vm, DisplayFallback.FORCE_RAW);
      }
   }

   private boolean mayReconnectDisplay(Object vm, int epoch) {
      return !this.activityDestroyed && this.encodedDisplayVm == vm && this.lifecycle.owns(vm)
            && this.vmMayBeRunning && this.displayConnection.current(epoch);
   }

   private void reportDifferentDesktopSize(String requested, String actual) {
      Log.w("UAVF-H264", "Guest display size differs from requested size: " + requested + " -> " + actual);
      android.widget.Toast.makeText(this, UiLanguage.format("Ubuntu передаёт %1$s. Запрошено %2$s.",
            actual.replace('x','×'), requested.replace('x','×')), android.widget.Toast.LENGTH_LONG).show();
   }

   private void presentUbuntuSurface(int width, int height, Runnable fit) {
      this.completeDesktopTransition();
      this.guestFrameWidth = width;
      this.guestFrameHeight = height;
      fit.run();
      this.frameSurface.setEncodedMode(true, width, height);
      if (!this.frameVisible) {
         this.frameVisible = true;
         this.showRunningDesktop();
      }
   }

   private void startRawUbuntuDisplay(Object vm, int epoch) {
      File report = new File(this.getExternalFilesDir(null), "ubuntu-gnome-frame-bridge-report.txt");
      new RawDisplayReceiver(vm, this.displayConnection, epoch, report,
            this.frameSurface::performanceSnapshot, frame -> {
               this.guestFrameWidth = frame.width;
               this.guestFrameHeight = frame.height;
               this.queueFrameForDisplay(frame);
            }, this::show).start();
   }

   private void startUbuntuAudioBridge(Object var1) {
      if (this.getPreferences(0).getBoolean("linux_audio_output_enabled", true)) {
         if (this.activeAudioBridgeVm != var1) {
            this.activeAudioBridgeVm = var1;
            (new Thread(() -> {
               File var2 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-audio-bridge-report.txt");
               AudioTrack var3 = null;
               ParcelFileDescriptor var4 = null;

               try {
                  PrintWriter var72 = new PrintWriter(new FileOutputStream(var2, false));

                  try {
                     var72.println("transport=AVF_CONNECT_VSOCK");
                     var72.println("guest=PIPEWIRE_NULL_SINK_MONITOR");
                     var72.println("port=4053");
                     Throwable var73 = null;
                     Method var7 = var1.getClass().getMethod("connectVsock", Long.TYPE);
                     long var8 = System.currentTimeMillis() + 300000L;

                     while(System.currentTimeMillis() < var8) {
                        try {
                           var4 = (ParcelFileDescriptor)var7.invoke(var1, 4053L);
                           break;
                        } catch (Throwable var68) {
                           var73 = var68;
                           Thread.sleep(1000L);
                        }
                     }

                     if (var4 == null) {
                        throw new IllegalStateException("vsock 4053 did not become available", var73);
                     }

                     FileInputStream var10 = new FileInputStream(var4.getFileDescriptor());

                     try {
                        byte[] var11 = new byte[16];
                        IoUtil.readExactly(var10, var11);
                        ByteBuffer var12 = ByteBuffer.wrap(var11).order(ByteOrder.LITTLE_ENDIAN);
                        if (var11[0] != 85 || var11[1] != 65 || var11[2] != 86 || var11[3] != 70 || ByteCodec.u16(var11, 4) != 1 || ByteCodec.u16(var11, 6) != 2 || var12.getInt(8) != 48000 || ByteCodec.u16(var11, 12) != 1) {
                           throw new IllegalStateException("unexpected guest PCM header");
                        }

                        int var13 = AudioTrack.getMinBufferSize(48000, 12, 2);
                        if (var13 <= 0) {
                           throw new IllegalStateException("Android rejected 48 kHz stereo PCM");
                        }

                        int var14 = Math.max(var13, 16384);
                        AudioFormat var15 = (new AudioFormat.Builder()).setEncoding(2).setSampleRate(48000).setChannelMask(12).build();
                        AudioAttributes var16 = (new AudioAttributes.Builder()).setUsage(14).setContentType(2).build();
                        var3 = (new AudioTrack.Builder()).setAudioAttributes(var16).setAudioFormat(var15).setBufferSizeInBytes(var14).setTransferMode(1).setPerformanceMode(1).build();
                        if (var3.getState() != 1) {
                           throw new IllegalStateException("Android AudioTrack initialization failed");
                        }

                        int var17 = Math.min(var3.getBufferCapacityInFrames(), 4096);
                        var3.setBufferSizeInFrames(var17);
                        var3.setVolume(1.0F);
                        var3.play();
                        var72.println("result=PCM_HEADER_PASS");
                        var72.println("format=PCM_S16LE_48000_STEREO");
                        var72.println("performance_mode=" + var3.getPerformanceMode());
                        var72.println("buffer_frames=" + var3.getBufferSizeInFrames());
                        var72.flush();
                        byte[] var18 = new byte['耄'];
                        int var19 = 0;
                        long var20 = 0L;
                        long var22 = System.currentTimeMillis() + 10000L;
                        long var24 = var22 - 10000L;
                        long var26 = 0L;

                        while(true) {
                           int var28 = ((InputStream)var10).read(var18, var19, var18.length - var19);
                           if (var28 < 0) {
                              var72.println("result=PCM_STREAM_ENDED");
                              var72.println("pcm_bytes=" + var20);
                              var72.flush();
                              break;
                           }

                           if (var28 != 0) {
                              int var29 = var19 + var28;
                              int var30 = var29 & -4;

                              int var32;
                              for(int var31 = 0; var31 < var30; var31 += var32) {
                                 var32 = var3.write(var18, var31, var30 - var31, 0);
                                 if (var32 <= 0) {
                                    throw new IllegalStateException("AudioTrack write failed: " + var32);
                                 }
                              }

                              var20 += (long)var30;
                              var19 = var29 - var30;
                              if (var19 > 0) {
                                 System.arraycopy(var18, var30, var18, 0, var19);
                              }

                              long var74 = System.currentTimeMillis();
                              if (var74 >= var22) {
                                 var72.println("pcm_bytes=" + var20);
                                 var72.println("pcm_bytes_per_s=" + (var20 - var26) * 1000L / Math.max(1L, var74 - var24));
                                 var72.println("underruns=" + var3.getUnderrunCount());
                                 var72.println("buffer_frames=" + var3.getBufferSizeInFrames());
                                 var72.flush();
                                 var24 = var74;
                                 var26 = var20;
                                 var22 = var74 + 10000L;
                              }
                           }
                        }
                     } catch (Throwable var67) {
                        try {
                           ((InputStream)var10).close();
                        } catch (Throwable var66) {
                           var67.addSuppressed(var66);
                        }

                        throw var67;
                     }

                     ((InputStream)var10).close();
                  } catch (Throwable var69) {
                     try {
                        var72.close();
                     } catch (Throwable var65) {
                        var69.addSuppressed(var65);
                     }

                     throw var69;
                  }

                  var72.close();
               } catch (Throwable var70) {
                  Throwable var5 = var70;
                  Log.w("U-AVF", "Ubuntu audio bridge failed", var70);

                  try {
                     PrintWriter var6 = new PrintWriter(new FileOutputStream(var2, true));

                     try {
                        var6.println("result=AUDIO_BRIDGE_FAILED");
                        var6.println("error=" + rootMessage(var5));
                     } catch (Throwable var63) {
                        try {
                           var6.close();
                        } catch (Throwable var62) {
                           var63.addSuppressed(var62);
                        }

                        throw var63;
                     }

                     var6.close();
                  } catch (Exception var64) {
                  }
               } finally {
                  if (var3 != null) {
                     try {
                        var3.pause();
                     } catch (Exception var61) {
                     }

                     try {
                        var3.flush();
                     } catch (Exception var60) {
                     }

                     try {
                        var3.release();
                     } catch (Exception var59) {
                     }
                  }

                  if (var4 != null) {
                     try {
                        var4.close();
                     } catch (Exception var58) {
                     }
                  }

               }

            }, "WinAVF-ubuntu-audio-bridge")).start();
         }
      }
   }

   private void startEscInputProbe(Object var1) {
      (new Thread(() -> {
         File var2 = new File(this.getExternalFilesDir((String)null), "uefi-input-probe-report.txt");

         try {
            PrintWriter var13 = new PrintWriter(new FileOutputStream(var2, false));

            try {
               var13.println("transport=AVF_VIRTIO_KEYBOARD");
               var13.println("key=KEY_ESC(1)");
               Thread.sleep(1200L);
               Method var14 = var1.getClass().getMethod("sendKeyEvent", Short.TYPE, Boolean.TYPE);
               boolean var5 = (Boolean)var14.invoke(var1, Short.valueOf((short)1), true);
               Thread.sleep(40L);
               boolean var6 = (Boolean)var14.invoke(var1, Short.valueOf((short)1), false);
               var13.println("sendKeyEvent=RESOLVED");
               var13.println("pressAccepted=" + var5);
               var13.println("releaseAccepted=" + var6);
               var13.println("result=" + (var5 && var6 ? "HOST_INPUT_ACCEPTED" : "HOST_INPUT_REJECTED"));
               this.show("UEFI keyboard probe: press=" + var5 + " release=" + var6);
            } catch (Throwable var11) {
               try {
                  var13.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }

               throw var11;
            }

            var13.close();
         } catch (Throwable var12) {
            Throwable var3 = var12;

            try {
               PrintWriter var4 = new PrintWriter(new FileOutputStream(var2, false));

               try {
                  var4.println("transport=AVF_VIRTIO_KEYBOARD");
                  var4.println("result=HOST_INPUT_ERROR");
                  var4.println("error=" + rootMessage(var3));
               } catch (Throwable var8) {
                  try {
                     var4.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }

                  throw var8;
               }

               var4.close();
            } catch (Throwable var9) {
            }

            this.show("UEFI keyboard probe failed: " + rootMessage(var12));
         }

      }, "WinAVF-esc-input-probe")).start();
   }

   private void startSerialEscapeInputProbe(Object var1) {
      (new Thread(() -> {
         File var2 = new File(this.getExternalFilesDir((String)null), "uefi-serial-escape-probe-report.txt");

         try {
            PrintWriter var18 = new PrintWriter(new FileOutputStream(var2, false));

            label100: {
               try {
                  var18.println("transport=APP_CONSOLE_INPUT_TO_EDK2_SERIAL_CONIN");
                  var18.println("expectedPrompt=Press ESCAPE for boot options");
                  var18.println("mode=BOUNDED_PERIODIC_ESC_UNTIL_FIRMWARE_ACK");
                  long var19 = System.currentTimeMillis() + 15000L;

                  while(!this.bootOptionsPromptSeen && System.currentTimeMillis() < var19) {
                     Thread.sleep(25L);
                  }

                  var18.println("promptSeen=" + this.bootOptionsPromptSeen);
                  if (this.bootOptionsPromptSeen) {
                     int var6 = this.observedFrameCount;
                     OutputStream var7 = (OutputStream)var1.getClass().getMethod("getConsoleInput").invoke(var1);
                     var18.println("payloadHex=1B");
                     var18.println("framesBefore=" + var6);
                     int var8 = 0;
                     long var9 = System.currentTimeMillis() + 55000L;

                     while(!this.serialEscapeAcknowledged && System.currentTimeMillis() < var9) {
                        var7.write(27);
                        var7.flush();
                        ++var8;
                        Thread.sleep(250L);
                     }

                     var18.println("uartWrites=" + var8);
                     var18.println("uartWrite=" + (var8 > 0 ? "PASS" : "NOT_SENT"));
                     this.show("UEFI serial input probe: bounded ESC stream finished.");
                     Thread.sleep(3000L);
                     int var11 = this.observedFrameCount;
                     var18.println("framesAfter=" + var11);
                     var18.println("firmwareEscapeAcknowledged=" + this.serialEscapeAcknowledged);
                     String var10001 = this.serialEscapeAcknowledged && var11 > var6 ? "UEFI_SERIAL_INPUT_AND_GRAPHICS_RESPONSE_PASS" : (this.serialEscapeAcknowledged ? "UEFI_SERIAL_INPUT_PASS_FRAME_NOT_OBSERVED" : "UART_WRITE_PASS_RESPONSE_NOT_OBSERVED");
                     var18.println("result=" + var10001);
                     break label100;
                  }

                  var18.println("result=NOT_SENT_PROMPT_NOT_OBSERVED");
                  this.show("UEFI serial input probe: prompt was not observed; ESC was not sent.");
               } catch (Throwable var16) {
                  try {
                     var18.close();
                  } catch (Throwable var15) {
                     var16.addSuppressed(var15);
                  }

                  throw var16;
               }

               var18.close();
               return;
            }

            var18.close();
         } catch (Throwable var17) {
            Throwable var3 = var17;

            try {
               PrintWriter var4 = new PrintWriter(new FileOutputStream(var2, false));

               try {
                  var4.println("transport=APP_CONSOLE_INPUT_TO_EDK2_SERIAL_CONIN");
                  var4.println("result=ERROR");
                  var4.println("error=" + rootMessage(var3));
               } catch (Throwable var13) {
                  try {
                     var4.close();
                  } catch (Throwable var12) {
                     var13.addSuppressed(var12);
                  }

                  throw var13;
               }

               var4.close();
            } catch (Throwable var14) {
            }

            this.show("UEFI serial input probe failed: " + rootMessage(var17));
         }

      }, "WinAVF-serial-escape-probe")).start();
   }

   private void applyStagedImagePatch(File var1, File var2) throws Exception {
      File var3 = new File(this.getExternalFilesDir((String)null), "winavf-image-patch.bin");
      File var4 = new File(var1, "active-image-patch.bin");
      if (var3.isFile()) {
         if (var4.exists()) {
            if (!ImagePatch.targetsBaseline(var4, var2)) {
               throw new IllegalStateException("An earlier image patch is still active; rollback it before applying another patch");
            }

            if (!var4.delete()) {
               throw new IllegalStateException("Could not discard the unapplied image patch record");
            }

            this.show("Discarded a patch record that was never applied to the baseline image.");
         }

         this.copyFile(var3, var4, -1L, true);

         try {
            ImagePatch.apply(var4, var2, false);
            if (!var3.delete()) {
               this.show("Patch applied; external staging copy could not be deleted.");
            }

            this.show("Applied verified small image patch: " + var4.length() + " bytes");
         } catch (Throwable var6) {
            this.show("Patch was not accepted; active bundle is retained for inspection/rollback.");
            throw var6;
         }
      }
   }

   private void rollbackLastPatch() {
      File var1 = new File(this.getExternalFilesDir((String)null), "rollback-report.txt");

      try {
         File var14 = new File(this.getFilesDir(), "payload");
         File var15 = new File(var14, "win11-gop-ebs-r1.img");
         File var4 = new File(var14, "active-image-patch.bin");
         if (!var15.isFile()) {
            throw new IllegalStateException("No app-private runtime image exists yet");
         }

         if (!var4.isFile()) {
            throw new IllegalStateException("No active image patch exists to roll back");
         }

         ImagePatch.apply(var4, var15, true);
         if (!var4.delete()) {
            throw new IllegalStateException("Rollback passed but the active patch record could not be removed");
         }

         PrintWriter var5 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var5.println("RESULT=PASS");
            var5.println("BASELINE_SHA256=" + Digest.hex(Digest.sha256File(var15)));
         } catch (Throwable var12) {
            try {
               var5.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }

            throw var12;
         }

         var5.close();
         this.show("Small image patch rolled back and the runtime image was verified.");
      } catch (Throwable var13) {
         Throwable var2 = var13;

         try {
            PrintWriter var3 = new PrintWriter(new FileOutputStream(var1, false));

            try {
               var3.println("RESULT=FAIL");
               var3.println("ERROR=" + rootMessage(var2));
            } catch (Throwable var9) {
               try {
                  var3.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }

               throw var9;
            }

            var3.close();
         } catch (Throwable var10) {
         }

         this.show("ROLLBACK FAILED: " + rootMessage(var13));
      }

   }



   private void runBootflowProbe(Object var1) {
      (new Thread(() -> {
         try {
            Thread.sleep(350L);
            OutputStream var2 = (OutputStream)var1.getClass().getMethod("getConsoleInput").invoke(var1);
            var2.write(32);
            var2.flush();
            Thread.sleep(1200L);
            var2.write("bootflow scan -l\n".getBytes(StandardCharsets.US_ASCII));
            var2.flush();
            this.show("U-Boot bootflow probe sent.");
         } catch (Throwable var3) {
            this.show("Console-input probe unavailable: " + rootMessage(var3));
         }

      }, "WinAVF-bootflow-probe")).start();
   }

   private void startTargetUsageReporter(File var1) {
      (new Thread(() -> {
         File var2 = new File(this.getExternalFilesDir((String)null), "windows-target-usage.log");

         while(!Thread.currentThread().isInterrupted()) {
            try {
               PrintWriter var3 = new PrintWriter(new FileOutputStream(var2, false));

               try {
                  long var4 = Os.stat(var1.getAbsolutePath()).st_blocks * 512L;
                  var3.println("logical=" + var1.length());
                  var3.println("allocated=" + var4);
                  var3.println("timestamp=" + System.currentTimeMillis());
               } catch (Throwable var8) {
                  try {
                     var3.close();
                  } catch (Throwable var6) {
                     var8.addSuppressed(var6);
                  }

                  throw var8;
               }

               var3.close();
            } catch (Throwable var9) {
               return;
            }

            try {
               Thread.sleep(5000L);
            } catch (InterruptedException var7) {
               return;
            }
         }

      }, "WinAVF-target-usage")).start();
   }

   private Object buildConfig(File var1, File var2, boolean var3) throws Exception {
      return this.buildConfig(var1, var2, var3, "winavf-gop-ebs-r1", (String)null);
   }

   private Object buildConfig(File var1, File var2, boolean var3, String var4) throws Exception {
      return this.buildConfig(var1, var2, var3, var4, (String)null);
   }

   private Object buildConfig(File var1, File var2, boolean var3, String var4, String var5) throws Exception {
      Class var6 = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
      Class var7 = Class.forName(var6.getName() + "$Builder");
      Object var8 = var7.getConstructor().newInstance();
      call(var8, "setName", String.class, var4);
      call(var8, "setOsName", String.class, "winavf");
      call(var8, "setKernelPath", String.class, var1.getAbsolutePath());
      if (var5 != null && !var5.isEmpty()) {
         call(var8, "addParam", String.class, var5);
      }

      call(var8, "useNetwork", Boolean.TYPE, false);
      call(var8, "useAutoMemoryBalloon", Boolean.TYPE, true);
      Class var9 = Class.forName(var6.getName() + "$Disk");
      Object var10 = var9.getMethod("RWDisk", String.class).invoke((Object)null, var2.getAbsolutePath());
      call(var8, "addDisk", var9, var10);
      Class var11 = Class.forName(var6.getName() + "$DisplayConfig$Builder");
      Object var12 = var11.getConstructor().newInstance();
      call(var12, "setWidth", Integer.TYPE, 1280);
      call(var12, "setHeight", Integer.TYPE, 800);
      call(var12, "setHorizontalDpi", Integer.TYPE, 160);
      call(var12, "setVerticalDpi", Integer.TYPE, 160);
      call(var12, "setRefreshRate", Integer.TYPE, 60);
      Object var13 = call(var12, "build");
      call(var8, "setDisplayConfig", var13.getClass(), var13);
      if (var3) {
         call(var8, "useKeyboard", Boolean.TYPE, true);
      }

      Class var14 = Class.forName(var6.getName() + "$GpuConfig$Builder");
      Object var15 = call(var14.getConstructor().newInstance(), "build");
      call(var8, "setGpuConfig", var15.getClass(), var15);
      Object var16 = call(var8, "build");
      Class var17 = Class.forName("android.system.virtualmachine.VirtualMachineConfig");
      Class var18 = Class.forName(var17.getName() + "$Builder");
      Object var19 = var18.getConstructor(Context.class).newInstance(this);
      call(var19, "setProtectedVm", Boolean.TYPE, false);
      call(var19, "setMemoryBytes", Long.TYPE, 4294967296L);
      call(var19, "setCpuTopology", Integer.TYPE, var17.getField("CPU_TOPOLOGY_ONE_CPU").getInt((Object)null));
      call(var19, "setDebugLevel", Integer.TYPE, 1);
      call(var19, "setConsoleInputDevice", String.class, "ttyS0");
      call(var19, "setVmOutputCaptured", Boolean.TYPE, true);
      call(var19, "setVmConsoleInputSupported", Boolean.TYPE, true);
      call(var19, "setCustomImageConfig", var6, var16);
      return call(var19, "build");
   }

   private void attachCallback(Object vm) throws Exception {
      this.lifecycleVm = vm;
      this.lifecycle.attach(vm);
      this.transition.event("Virtual machine created", "AVF VM handle", SystemClock.elapsedRealtime());
      AvfGuest.observe(vm, ForkJoinPool.commonPool(),
            reason -> this.onGuestStopped(vm, reason), detail -> this.onGuestError(vm, detail));
      this.runOnUiThread(() -> {
         if (this.activityDestroyed || !this.lifecycle.owns(vm)) return;
         try {
            VmRuntimeService.keepAlive(this, vm);
         } catch (Exception error) {
            Log.w("U-AVF-runtime", "Foreground runtime could not start", error);
         }
      });
   }

   private void onGuestStopped(Object vm, String reason) {
      if (this.activityDestroyed || !this.lifecycle.stopped(vm, reason)) return;
      VmRuntimeService.ended(this, vm);
      String evidence = "AVF onStopped: " + reason;
      this.transition.event("VM stopped", evidence, SystemClock.elapsedRealtime());
      Log.i("U-AVF-lifecycle", "Stopped: " + reason);
      this.disconnectGuestControls("Disconnected: " + reason);
      boolean restart = this.rebootRequested || "STOP_REASON_REBOOT".equals(reason);
      if (restart && this.transition.mode() != RuntimeTransition.Mode.REBOOT) {
         this.beginRuntimeTransition(RuntimeTransition.Mode.REBOOT);
         this.transition.event("VM stopped", evidence, SystemClock.elapsedRealtime());
      }
      this.rebootRequested = false;

      var prefs = this.getPreferences(0);
      boolean temporary = prefs.getBoolean("linux_current_boot_temporary", false);
      boolean ubuntu = AvfGuest.UBUNTU_VM.equals(this.activeFrameVmName);
      boolean installing = ubuntu && !temporary && !this.userRequestedVmStop
            && prefs.getBoolean(UbuntuWorkspaceMedia.HANDOFF_PENDING, false)
            && !prefs.getBoolean(UbuntuWorkspaceMedia.HANDOFF_ATTEMPTED, false);
      this.clearGuestRuntime();
      if (installing) {
         this.lifecycle.install(VmLifecycle.Install.FINALIZING);
         prefs.edit().putBoolean(UbuntuWorkspaceMedia.HANDOFF_ATTEMPTED, true).apply();
         this.runOnUiThread(() -> {
            this.hideStoppedGuestControls();
            this.status.setVisibility(View.VISIBLE);
            this.status.setText("Preparing the installed Ubuntu system…");
            this.show("Install session ended; checking the persistent boot handoff.");
         });
         new Thread(this::finishUbuntuInstallHandoff, "U-AVF-install-handoff").start();
         return;
      }
      if (restart && ubuntu) {
         int generation = this.transition.generation();
         this.show("Ubuntu reboot: reconnecting the workspace…");
         this.uiHandler.postDelayed(() -> {
            if (this.activityDestroyed || this.transition.generation() != generation || this.isManagedVmRunning()) return;
            new Thread(() -> {
               if (!temporary && prefs.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false)) {
                  this.startPersistentUbuntu(Request.START_EXISTING);
               } else {
                  this.startUbuntuLive();
               }
            }, "U-AVF-reboot-handoff").start();
         }, 500);
         return;
      }

      int generation = this.transition.generation();
      this.runOnUiThread(() -> {
         this.hideStoppedGuestControls();
         if (this.transitionView != null) this.transitionView.refresh();
         this.uiHandler.postDelayed(() -> {
            if (this.activityDestroyed || this.transition.generation() != generation) return;
            this.transition.dismiss();
            if (this.transitionView != null) this.transitionView.refresh();
            this.renderProductScreen(this.stoppedWorkspaceScreen(temporary));
         }, 450);
      });
      this.show("VM stopped.");
   }

   private void onGuestError(Object vm, String detail) {
      if (this.activityDestroyed || !this.lifecycle.failed(vm, detail)) return;
      this.transition.failure(detail, SystemClock.elapsedRealtime());
      this.disconnectGuestControls("Disconnected: VM error");
      this.clearGuestRuntime();
      this.runOnUiThread(() -> {
         this.hideStoppedGuestControls();
         boolean temporary = this.getPreferences(0).getBoolean("linux_current_boot_temporary", false);
         this.renderProductScreen(this.stoppedWorkspaceScreen(temporary));
         this.transitionTick.run();
      });
      this.show("VM error: " + detail);
   }

   private void disconnectGuestControls(String status) {
      GuestControl control = this.guestControl;
      this.guestControl = null;
      this.controlVm = null;
      if (control != null) control.close();
      this.guestControlStatus = status;
   }

   private void clearGuestRuntime() {
      this.vmMayBeRunning = false;
      this.activeFrameVmName = null;
      this.resetGuestFrameSurface();
   }

   private void hideStoppedGuestControls() {
      this.closeSessionDrawerImmediately();
      this.toolbar.setVisibility(View.GONE);
      this.drawerScrim.setVisibility(View.GONE);
      this.menuButton.setVisibility(View.GONE);
   }

   private ProductScreen stoppedWorkspaceScreen(boolean temporary) {
      if (this.selectedProfile == LaunchProfile.WINDOWS) return ProductScreen.WINDOWS_HOME;
      if (temporary) return ProductScreen.SYSTEMS;
      return this.persistentUbuntuDiskFile().isFile() ? ProductScreen.LINUX_HOME : ProductScreen.LINUX_SETUP;
   }

   private void finishUbuntuInstallHandoff() {
      File var1 = this.persistentUbuntuDiskFile();

      try {
         boolean var2 = PersistentUbuntuDisk.audit(var1, 3967463424L, 34359738368L);
         boolean var3 = var2 && PersistentUbuntuDisk.hasInstalledBootCandidate(var1);
         this.appendUbuntuInstallerAutostartReport("POST_INSTALL_HANDOFF=" + (var3 ? "CANDIDATE_FOUND" : "CANDIDATE_NOT_FOUND") + " GPT=" + (var2 ? "PASS" : "FAIL"));
         if (!var3) {
            this.lifecycle.install(VmLifecycle.Install.FAILED);
            this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", false).apply();
            this.runOnUiThread(() -> {
               this.renderProductScreen(ProductScreen.LINUX_HOME);
               this.show("No installed Ubuntu boot marker was found; the persistent disk was left untouched.");
            });
            return;
         }

         long var4 = System.currentTimeMillis() + 15000L;

         while(this.isManagedVmRunning() && System.currentTimeMillis() < var4) {
            Thread.sleep(300L);
         }

         this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", false).apply();
         this.runOnUiThread(() -> this.status.setText("Starting installed Ubuntu for the first time…"));
         this.startPersistentUbuntu(Request.FIRST_BOOT);
      } catch (Throwable var6) {
         this.lifecycle.install(VmLifecycle.Install.FAILED);
         this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", false).apply();
         this.appendUbuntuInstallerAutostartReport("POST_INSTALL_HANDOFF=FAILED " + rootMessage(var6));
         this.runOnUiThread(() -> {
            this.renderProductScreen(ProductScreen.LINUX_HOME);
            this.show("Could not start the installed Ubuntu handoff: " + rootMessage(var6));
         });
      }

   }

   private final Object consoleObserverLock=new Object();
   private volatile VmConsole.Observer ubuntuConsoleObserver;

   private void attachUbuntuConsole(Object vm,String filename) throws Exception {
      synchronized(this.consoleObserverLock) {
         if(this.activityDestroyed)return;
         var observer=new VmConsole.Observer() {
            @Override public void onBytes(byte[] bytes,int length) {
               if(ubuntuConsoleObserver!=this || activityDestroyed || lifecycleVm!=vm)return;
               frameDecoder.feed(bytes,0,length);
               transition.serial(transition.generation(),new String(bytes,0,length,StandardCharsets.UTF_8),
                     SystemClock.elapsedRealtime());
            }
            @Override public void onFailure(Exception failure) {
               if(ubuntuConsoleObserver==this && !activityDestroyed)
                  Log.w("UAVF-Console","Managed Ubuntu console closed",failure);
            }
         };
         this.ubuntuConsoleObserver=observer;
         File reports=this.getExternalFilesDir(null);
         if(reports==null)reports=this.getFilesDir();
         VmRuntimeService.ubuntuConsole().attach(vm,()->AvfGuest.consoleOutput(vm),new File(reports,filename),observer);
      }
   }

   private void detachUbuntuConsole() {
      synchronized(this.consoleObserverLock) {
         VmRuntimeService.ubuntuConsole().detach(this.ubuntuConsoleObserver);
         this.ubuntuConsoleObserver=null;
      }
   }

   private void startConsoleReader(InputStream var1) {
      this.startConsoleReader(var1, "serial.log");
   }

   private void startConsoleReader(InputStream var1, String var2) {
      final Object consoleOwner=this.lifecycleVm;
      (new Thread(() -> {
         File var3 = new File(this.getExternalFilesDir((String)null), var2);
         this.show("Writing complete VM serial log to " + var3.getAbsolutePath());

         try {
            FileOutputStream var4 = new FileOutputStream(var3, false);

            try {
               byte[] var5 = new byte[256];

               int var6;
               while((var6 = var1.read(var5)) >= 0) {
                  var4.write(var5, 0, var6);
                  var4.flush();
                  this.frameDecoder.feed(var5, 0, var6);
                  String var7 = new String(var5, 0, var6, StandardCharsets.UTF_8);
                  if(this.lifecycleVm==consoleOwner)
                     this.transition.serial(this.transition.generation(),var7,android.os.SystemClock.elapsedRealtime());
                  this.observeUefiBootOptionsPrompt(var7);
                  if (var7.indexOf(10) >= 0) {
                     String var10001 = var7.replace("\r", "");
                     this.show("SERIAL: " + var10001.replace("\n", ""));
                  }
               }
            } catch (Throwable var9) {
               try {
                  var4.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }

               throw var9;
            }

            var4.close();
         } catch (Throwable var10) {
            this.show("Console closed: " + rootMessage(var10));
         }

      }, "WinAVF-console")).start();
   }

   private void observeUefiBootOptionsPrompt(String var1) {
      synchronized(this.serialProbeTail) {
         this.serialProbeTail.append(var1);
         if (this.serialProbeTail.length() > 256) {
            this.serialProbeTail.delete(0, this.serialProbeTail.length() - 256);
         }

         if (this.serialProbeTail.indexOf("Press ESCAPE for boot options") >= 0) {
            this.bootOptionsPromptSeen = true;
         }

         if (this.serialProbeTail.indexOf("AVF_UEFI_INPUT_WINDOW") >= 0) {
            this.serialInputWindowSeen = true;
         }

         if (this.serialProbeTail.indexOf("AVF_UEFI_ESC_RECEIVED") >= 0) {
            this.serialEscapeAcknowledged = true;
         }

      }
   }

   private void startWinpeMarkerReporter(File var1) {
      (new Thread(() -> {
         for(int var2 = 0; var2 < 360; ++var2) {
            try {
               String var3 = readWinpeMarker(var1);
               if (var3 != null) {
                  File var4 = new File(this.getExternalFilesDir((String)null), "winpe-userland-marker.txt");
                  FileOutputStream var5 = new FileOutputStream(var4, false);

                  try {
                     var5.write(var3.getBytes(StandardCharsets.US_ASCII));
                  } catch (Throwable var10) {
                     try {
                        var5.close();
                     } catch (Throwable var9) {
                        var10.addSuppressed(var9);
                     }

                     throw var10;
                  }

                  var5.close();
                  String var10001 = var3.replace("\r", " ");
                  this.show("WINPE_USERLAND marker found: " + var10001.replace("\n", " | "));
                  return;
               }
            } catch (Throwable var11) {
            }

            try {
               Thread.sleep(2000L);
            } catch (InterruptedException var8) {
               return;
            }
         }

      }, "WinAVF-winpe-marker")).start();
   }

   /** Returns the WinPE userland marker text from the ESP root, or null. */
   private static String readWinpeMarker(File disk) throws Exception {
      try (var volume = Fat32Volume.open(disk)) {
         for (String name : volume.rootFileNames("TXT")) {
            byte[] body = volume.readRootFileByExtension(name, "TXT", 4096);
            if (body == null) continue;
            String text = new String(body, StandardCharsets.US_ASCII);
            if (text.contains("stage=WINPE_USERLAND_")) return text;
         }
         return null;
      }
   }

   private void auditPersistentBootWitness() {
      File var1 = new File(this.getExternalFilesDir((String)null), "persistent-boot-witness-report.txt");

      try {
         File var20 = new File(this.getFilesDir(), "payload");
         File var3 = new File(var20, "win11-gop-ebs-r1.img");
         if (!var3.isFile() || var3.length() != 9126805504L) {
            throw new IllegalStateException("No disposable persistent-witness disk is available");
         }

         byte[] var4 = readEspRootFile(var3, "SETUPACT", "LOG", 16777216);
         byte[] var5 = readEspRootFile(var3, "SETUPERR", "LOG", 16777216);
         PrintWriter var6 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var6.println("scope=POST_STOP_OFFLINE_DISPOSABLE_DISK_INSPECTION");
            var6.println("diskBytes=" + var3.length());
            var6.println("diskSha256=" + Digest.hex(Digest.sha256File(var3)));
            boolean var7 = false;
            if (var4 != null && var4.length > 0) {
               File var8 = new File(this.getExternalFilesDir((String)null), "persistent-witness-setupact.log");
               FileOutputStream var9 = new FileOutputStream(var8, false);

               try {
                  var9.write(var4);
               } catch (Throwable var17) {
                  try {
                     var9.close();
                  } catch (Throwable var15) {
                     var17.addSuppressed(var15);
                  }

                  throw var17;
               }

               var9.close();
               var6.println("SETUPACT_LOG_BYTES=" + var4.length);
               String var10001 = Digest.hex(MessageDigest.getInstance("SHA-256").digest(var4));
               var6.println("SETUPACT_LOG_SHA256=" + var10001);
               var7 = true;
            }

            if (var5 != null && var5.length > 0) {
               File var21 = new File(this.getExternalFilesDir((String)null), "persistent-witness-setuperr.log");
               FileOutputStream var22 = new FileOutputStream(var21, false);

               try {
                  var22.write(var5);
               } catch (Throwable var16) {
                  try {
                     var22.close();
                  } catch (Throwable var14) {
                     var16.addSuppressed(var14);
                  }

                  throw var16;
               }

               var22.close();
               var6.println("SETUPERR_LOG_BYTES=" + var5.length);
               String var23 = Digest.hex(MessageDigest.getInstance("SHA-256").digest(var5));
               var6.println("SETUPERR_LOG_SHA256=" + var23);
               var7 = true;
            }

            var6.println("PERSISTENT_WINDOWS_SETUP_WITNESS=" + (var7 ? "PASS" : "NOT_OBSERVED"));
            var6.println("WINDOWS_KERNEL_EXECUTION_AFTER_EBS=" + (var7 ? "PASS" : "NOT_OBSERVED"));
            var6.println("NEGATIVE_INTERPRETATION=NO_PERSISTENT_ARTIFACT_DOES_NOT_PROVE_NO_KERNEL_EXECUTION");
         } catch (Throwable var18) {
            try {
               var6.close();
            } catch (Throwable var13) {
               var18.addSuppressed(var13);
            }

            throw var18;
         }

         var6.close();
         this.show("Persistent witness offline audit complete");
      } catch (Throwable var19) {
         Throwable var2 = var19;

         try {
            writeBridgeFailure(var1, rootMessage(var2));
         } catch (Throwable var12) {
         }

         this.show("Persistent witness audit failed: " + rootMessage(var19));
      }

   }

   private void cleanupPersistentBootWitness() {
      File var1 = new File(this.getExternalFilesDir((String)null), "persistent-witness-cleanup-report.txt");

      try {
         Object var17 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
         Object var3 = var17.getClass().getMethod("get", String.class).invoke(var17, "winavf-gop-ebs-r1");
         if (var3 != null) {
            var17.getClass().getMethod("delete", String.class).invoke(var17, "winavf-gop-ebs-r1");
            Object var4 = var17.getClass().getMethod("get", String.class).invoke(var17, "winavf-gop-ebs-r1");
            if (var4 != null) {
               throw new IllegalStateException("Could not delete stopped diagnostic VM record");
            }
         }

         File var18 = new File(this.getFilesDir(), "payload");
         File var5 = new File(var18, "win11-gop-ebs-r1.img");
         File var6 = new File(var18, "active-image-patch.bin");
         if (var5.exists() && !var5.delete()) {
            throw new IllegalStateException("Could not delete disposable private disk");
         }

         if (var6.exists() && !var6.delete()) {
            throw new IllegalStateException("Could not delete disposable active patch");
         }

         File var7 = new File(this.getExternalFilesDir((String)null), "winavf-image-patch.bin");
         if (var7.exists() && !var7.delete()) {
            throw new IllegalStateException("Could not delete external staged patch");
         }

         File var8 = new File(this.getExternalFilesDir((String)null), "win11-gop-ebs-r1.img");
         if (!var8.isFile() || var8.length() != 9126805504L) {
            throw new IllegalStateException("Immutable baseline is unavailable");
         }

         String var9 = Digest.hex(Digest.sha256File(var8));
         if (!"2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7".equals(var9)) {
            throw new IllegalStateException("Immutable baseline hash mismatch after cleanup");
         }

         PrintWriter var10 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var10.println("DISPOSABLE_PRIVATE_CLONE_DELETED=PASS");
            var10.println("IMMUTABLE_BASELINE_SHA256=" + var9);
            var10.println("RESULT=PASS");
         } catch (Throwable var15) {
            try {
               var10.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }

            throw var15;
         }

         var10.close();
         this.show("Disposable persistent-witness disk deleted; baseline verified");
      } catch (Throwable var16) {
         Throwable var2 = var16;

         try {
            writeBridgeFailure(var1, rootMessage(var2));
         } catch (Throwable var13) {
         }

         this.show("Persistent witness cleanup failed: " + rootMessage(var16));
      }

   }

   private File copyAsset(String var1, File var2, long var3) throws Exception {
      if (!var2.isFile() || var3 >= 0L && var2.length() != var3) {
         InputStream var5 = this.getAssets().open(var1);

         try {
            FileOutputStream var6 = new FileOutputStream(var2);

            try {
               byte[] var7 = new byte[1048576];

               int var8;
               while((var8 = var5.read(var7)) >= 0) {
                  var6.write(var7, 0, var8);
               }
            } catch (Throwable var11) {
               try {
                  var6.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }

               throw var11;
            }

            var6.close();
         } catch (Throwable var12) {
            if (var5 != null) {
               try {
                  var5.close();
               } catch (Throwable var9) {
                  var12.addSuppressed(var9);
               }
            }

            throw var12;
         }

         if (var5 != null) {
            var5.close();
         }

         return var2;
      } else {
         return var2;
      }
   }

   private File copyVerifiedAsset(String var1, File var2, long var3, String var5) throws Exception {
      if (var2.isFile() && var2.length() == var3 && var5.equalsIgnoreCase(Digest.hex(Digest.sha256File(var2)))) {
         return var2;
      } else {
         File var6 = var2.getParentFile();
         if (var6 != null && !var6.isDirectory() && !var6.mkdirs()) {
            throw new IllegalStateException("Cannot create verified asset staging directory.");
         } else {
            File var7 = new File(var6, var2.getName() + ".verified-tmp");
            if (var7.exists() && !var7.delete()) {
               throw new IllegalStateException("Cannot clear stale verified asset staging file.");
            } else {
               InputStream var8 = this.getAssets().open(var1);

               try {
                  FileOutputStream var9 = new FileOutputStream(var7);

                  try {
                     byte[] var10 = new byte[1048576];

                     int var11;
                     while((var11 = var8.read(var10)) >= 0) {
                        var9.write(var10, 0, var11);
                     }

                     var9.flush();
                     var9.getFD().sync();
                  } catch (Throwable var14) {
                     try {
                        var9.close();
                     } catch (Throwable var13) {
                        var14.addSuppressed(var13);
                     }

                     throw var14;
                  }

                  var9.close();
               } catch (Throwable var15) {
                  if (var8 != null) {
                     try {
                        var8.close();
                     } catch (Throwable var12) {
                        var15.addSuppressed(var12);
                     }
                  }

                  throw var15;
               }

               if (var8 != null) {
                  var8.close();
               }

               if (var7.length() == var3 && var5.equalsIgnoreCase(Digest.hex(Digest.sha256File(var7)))) {
                  if (var2.exists() && !var2.delete()) {
                     var7.delete();
                     throw new IllegalStateException("Cannot replace an old platform update payload.");
                  } else if (!var7.renameTo(var2)) {
                     var7.delete();
                     throw new IllegalStateException("Cannot finalize verified platform update payload.");
                  } else {
                     return var2;
                  }
               } else {
                  var7.delete();
                  throw new SecurityException("Bundled platform update failed its size or SHA-256 check.");
               }
            }
         }
      }
   }

   private File copyFile(File var1, File var2, long var3, boolean var5) throws Exception {
      long var6 = var3 >= 0L ? var3 : var1.length();
      if (!var5 && var2.isFile() && var2.length() == var6) {
         return var2;
      } else {
         this.show("Copying Windows setup medium into app-private storage…");
         FileInputStream var8 = new FileInputStream(var1);

         try {
            FileOutputStream var9 = new FileOutputStream(var2);

            try {
               byte[] var10 = new byte[1048576];

               int var11;
               while((var11 = ((InputStream)var8).read(var10)) >= 0) {
                  var9.write(var10, 0, var11);
               }
            } catch (Throwable var14) {
               try {
                  var9.close();
               } catch (Throwable var13) {
                  var14.addSuppressed(var13);
               }

               throw var14;
            }

            var9.close();
         } catch (Throwable var15) {
            try {
               ((InputStream)var8).close();
            } catch (Throwable var12) {
               var15.addSuppressed(var12);
            }

            throw var15;
         }

         ((InputStream)var8).close();
         if (var2.length() != var6) {
            throw new IllegalStateException("Setup-medium copy has unexpected size");
         } else {
            return var2;
         }
      }
   }

   private File ensureSparseTarget(File var1) throws Exception {
      if (!var1.isFile() || var1.length() != 68719476736L) {
         RandomAccessFile var2 = new RandomAccessFile(var1, "rw");

         try {
            var2.setLength(68719476736L);
         } catch (Throwable var6) {
            try {
               var2.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         var2.close();
      }

      long var7 = Os.stat(var1.getAbsolutePath()).st_blocks * 512L;
      long var10001 = var1.length();
      this.show("Sparse Windows target: logical=" + var10001 + " physical=" + var7 + " bytes");
      if (var7 > 16777216L) {
         throw new IllegalStateException("Sparse Windows target allocated too much: " + var7);
      } else {
         return var1;
      }
   }

   private static Object call(Object var0, String var1, Class<?> var2, Object var3) throws Exception {
      return var0.getClass().getMethod(var1, var2).invoke(var0, var3);
   }

   private static Object call(Object var0, String var1) throws Exception {
      return var0.getClass().getMethod(var1).invoke(var0);
   }

   private static Object newInstance(Class<?> var0) throws Exception {
      return var0.getConstructor().newInstance();
   }

   private void show(String var1) {
      this.runOnUiThread(() -> {
         this.currentNotice = var1;
         if(var1.startsWith("SERIAL:") || var1.startsWith("Writing complete VM serial log") || var1.startsWith("Console closed:")) {
            return;
         }
         if (this.productStatus != null && this.homePanel != null && this.homePanel.getVisibility() == 0) {
            this.productStatus.setText(UiLanguage.text(var1));
         }

         this.status.setText(var1);
         if (!this.frameVisible) {
            this.status.setVisibility(0);
         }


      });
   }

   private static String rootMessage(Throwable var0) {
      while(var0.getCause() != null) {
         var0 = var0.getCause();
      }

      String var10000 = var0.getClass().getSimpleName();
      return var10000 + ": " + var0.getMessage();
   }

   static {
      CONSOLE_BINARY_ECHO_READY = "WINAVF_ECHO_READY\n".getBytes(StandardCharsets.US_ASCII);
      V22_EXT4_CONTROL_MEDIA_SHA256 = "F3C9498D0E4EFFAB2E38C75E523492A8CDD CFA9DC6CB3DDF1B48632E26C57D2C".replace(" ", "");
   }

   private static enum LaunchProfile {
      WINDOWS,
      LINUX,
      P33_FRAME_BRIDGE,
      V22_EXT4_CONTROL,
      V23_EXT4_BLOCK_IO;

      // $FF: synthetic method
      private static LaunchProfile[] $values() {
         return new LaunchProfile[]{WINDOWS, LINUX, P33_FRAME_BRIDGE, V22_EXT4_CONTROL, V23_EXT4_BLOCK_IO};
      }
   }


}
