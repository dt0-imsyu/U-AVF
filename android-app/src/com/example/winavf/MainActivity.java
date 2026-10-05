// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import android.app.Activity;
import android.content.ContentValues;
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
import android.provider.MediaStore.Downloads;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;
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
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
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
   private static final byte[] IMAGE_PATCH_MAGIC;
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
   private static final String FRAME_BRIDGE_VIRGL_GBM_LINUX_FIRST_PREFIX_ASSET = "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img";
   private static final String FRAME_BRIDGE_VIRGL_GBM_LINUX_FIRST_PREFIX_SHA256 = "FE451CA13834DC5EDE3096EDC3AF1EF2894BFDECAA7801CED9E69EDB07447858";
   private static final String FRAME_BRIDGE_VIRGL_GBM_LINUX_FIRST_PREVIOUS_PREFIX_ASSET = "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-real-gnome.img";
   private static final String FRAME_BRIDGE_VIRGL_GBM_LINUX_FIRST_PREVIOUS_PREFIX_SHA256 = "FDD4F255DA989845A5F8F346BCABD6246AB966E2279898590BF083A328C2247E";
   private static final String UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET = "uavf-install-platform-prefix-gdm-share.img";
   private static final String UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256 = "E2555776EDE54AD8C9DC38B8E1225C6673931F07522167464691D4064484AA2D";
   private static final String UBUNTU_INSTALL_PLATFORM_ESP_SHA256 = "AE9B2C7DE939D204E749274035BECE03B469C974ECD243272AB18B46E5925824";
   private static final String UBUNTU_INSTALL_PLATFORM_PREFIX_APPLIED_PREF = "ubuntu_install_platform_prefix_sha256";
   private static final String FRAME_BRIDGE_VIRGL_GBM_DISK_NAME = "winavf-P7-virgl-gbm-runtime.img";
   private static final String FRAME_BRIDGE_VIRGL_GBM_STAMP_NAME = "uavf-ubuntu-virgl-gbm-runtime.ready";
   private static final String UBUNTU_PERSISTENT_DISK_NAME = "uavf-ubuntu-persistent-one-disk.raw";
   private static final String UBUNTU_PERSISTENT_VM_NAME = "winavf-frame-bridge-test-ubuntu-24045";
   private static final String UBUNTU_PERSISTENT_REPORT_NAME = "ubuntu-install-runtime-report.txt";
   private static final String UBUNTU_INSTALLED_RUNTIME_READY_PREF = "ubuntu_installed_runtime_ready";
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
   private TextView logText;
   private FrameSurfaceView frameSurface;
   private android.view.SurfaceView installedEncodedSurface;
   private EncodedDisplayProbe installedEncodedDisplay;
   private int encodedSessionReconnects;
   private long encodedSessionReconnectWindowMs;
   private volatile OutputStream activeFrameInput;
   private final Object frameConnectionLock = new Object();
   private int frameConnectionGeneration;
   private ParcelFileDescriptor activeFrameSocket;
   private volatile int ubuntuGuestRuntimeRevision;
   private volatile String activeFrameVmName;
   private volatile boolean vmMayBeRunning;
   private volatile boolean userRequestedVmStop;
   private final VmLifecycle lifecycle = new VmLifecycle();
   private final RuntimeTransition transition = new RuntimeTransition();
   private RuntimeTransitionView transitionView;
   private final Runnable transitionTick = new Runnable() {
      public void run() {
         if (activityDestroyed) return;
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
   private final ExecutorService frameInputWriter = Executors.newSingleThreadExecutor();
   private volatile long lastPointerMoveMs;
   private FrameLayout page;
   private FrameLayout homePanel;
   private LinearLayout toolbar;
   private LinearLayout drawerContent;
   private LinearLayout settingsPanel;
   private ScrollView logPanel;
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
   private LinearLayout productBody;
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
   private final StringBuilder uiLog = new StringBuilder();
   private volatile Socket activeKdBridgeSocket;
   private volatile Object activeKdBridgeVm;
   private LaunchProfile selectedProfile;
   private static final int REQUEST_UBUNTU_ISO = 21825;
   private ProductScreen productScreen;
   private boolean compatibilityRunning;
   private final String[] readinessResults={"Not checked","Not checked","Not checked","Not checked"};
   private int readinessCompleted;
   private boolean compatibilityPassed;
   private boolean diagnosticsReturnAfterReadiness;
   private boolean welcomeFirstRun;
   private String createWorkspaceOs;
   private String workspaceSettingsTab;
   private boolean wizardSettingsActive;
   private boolean runtimeShortcutConsumed;
   private int runtimeShortcutKey=KeyEvent.KEYCODE_ESCAPE;
   private long edgeHoldStarted;
   private float edgeHoldX,edgeHoldY;
   private boolean edgeGestureTracking;
   private boolean physicalCtrlDown;
   private boolean physicalAltDown;
   private boolean physicalShiftDown;
   private int guestFrameWidth;
   private int guestFrameHeight;
   private int guestPointerX;
   private int guestPointerY;
   private String currentNotice;
   private Handler uiHandler;

   public MainActivity() {
      this.selectedProfile = MainActivity.LaunchProfile.WINDOWS;
      this.productScreen = MainActivity.ProductScreen.ONBOARDING;
      this.createWorkspaceOs = "ubuntu";
      this.workspaceSettingsTab = "General";
      this.guestFrameWidth = 1280;
      this.guestFrameHeight = 800;
      this.guestPointerX = -1;
      this.guestPointerY = -1;
      this.currentNotice = "";
   }

   public void onCreate(Bundle var1) {
      super.onCreate(var1);
      // Android 13+ can dispatch navigation Back without dispatchKeyEvent or
      // onBackPressed. Keep both paths under the same VM-preserving policy.
      this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
         OnBackInvokedDispatcher.PRIORITY_DEFAULT, this.navigationBackCallback);
      this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::handleBackAction);
      this.getWindow().setDecorFitsSystemWindows(false);
      this.hideSystemBars();
      this.page = new FrameLayout(this);
      this.page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> this.fitEncodedSurface());
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
            MainActivity.this.sendGuestInput(2, var2 ? 1 : 0, var1, 0);
         }

         public void onScroll(int var1, int var2) {
            MainActivity.this.revealRunningControls();
            MainActivity.this.sendGuestInput(4, 0, var1, var2);
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
      this.reconnectRunningFrameVmIfPresent();
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
         this.guestKeyboard.releaseAll();
         this.physicalCtrlDown = this.physicalAltDown = this.physicalShiftDown = false;
      }

   }

   @Override protected void onResume() {
      super.onResume();
      VmKeyboardAccessibilityService.foreground(this);
      if(this.page!=null) this.page.post(() -> {
         if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.resumeSurface();
      });
   }

   @Override protected void onPause() {
      if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.suspendSurface();
      VmKeyboardAccessibilityService.background(this);
      this.guestKeyboard.releaseAll();
      super.onPause();
   }

   @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
      super.onConfigurationChanged(configuration);
      this.hideSystemBars();
      if(this.page!=null) this.page.post(() -> {
         this.fitEncodedSurface();
         if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.resumeSurface();
      });
   }

   private void fitEncodedSurface() {
      android.view.SurfaceView surface=this.installedEncodedSurface;
      if(surface==null || this.page==null) return;
      int pw=this.page.getWidth(),ph=this.page.getHeight();
      int w=Math.min(pw,ph*1920/1200),h=w*1200/1920;
      FrameLayout.LayoutParams fit=(FrameLayout.LayoutParams)surface.getLayoutParams();
      if(w>0 && h>0 && (fit.width!=w || fit.height!=h)) {
         fit.width=w;fit.height=h;surface.setLayoutParams(fit);
      }
   }

   boolean systemKeyboardCaptureActive() {
      return !this.activityDestroyed && this.hasWindowFocus() && this.frameVisible &&
         this.activeFrameInput != null &&
         this.getPreferences(0).getBoolean("linux_system_keyboard_capture",false) &&
         (this.toolbar == null || this.toolbar.getVisibility() != View.VISIBLE);
   }

   boolean runtimePanelShortcut(KeyEvent event) {
      if(this.activityDestroyed || !this.hasWindowFocus() || !this.frameVisible ||
         !this.getPreferences(0).getBoolean("linux_system_keyboard_capture",false)) return false;
      int code=event.getKeyCode();
      int chosen=this.getPreferences(0).getInt("runtime_panel_shortcut",KeyEvent.KEYCODE_ESCAPE);
      return (code==KeyEvent.KEYCODE_ESCAPE || code==chosen) &&
         (event.isCtrlPressed() || this.physicalCtrlDown) &&
         (event.isAltPressed() || this.physicalAltDown) &&
         (event.isShiftPressed() || this.physicalShiftDown);
   }

   void finishCapturedAccessibilityKey(KeyEvent event) {
      if (this.systemKeyboardCaptureActive()) {
         this.dispatchKeyEvent(event);
      } else {
         // Opening controls disables capture before modifier key-up arrives.
         // Clear local shortcut state without forwarding to another app/session.
         int code=event.getKeyCode();
         if (code==KeyEvent.KEYCODE_CTRL_LEFT || code==KeyEvent.KEYCODE_CTRL_RIGHT) this.physicalCtrlDown=false;
         if (code==KeyEvent.KEYCODE_ALT_LEFT || code==KeyEvent.KEYCODE_ALT_RIGHT) this.physicalAltDown=false;
         if (code==KeyEvent.KEYCODE_SHIFT_LEFT || code==KeyEvent.KEYCODE_SHIFT_RIGHT) this.physicalShiftDown=false;
         if (code==this.runtimeShortcutKey) this.runtimeShortcutConsumed=false;
         this.guestKeyboard.releaseAll();
      }
   }

   void releaseSystemKeyboardCapture() {
      this.guestKeyboard.releaseAll();
      this.physicalCtrlDown=this.physicalAltDown=this.physicalShiftDown=false;
      this.runtimeShortcutConsumed=false;
   }

   private void addSystemKeyboardCaptureControls(LinearLayout panel) {
      this.addWorkspaceToggle(panel,"linux_system_keyboard_capture","Capture system keyboard shortcuts",false,
         "Optional Accessibility routing for Cmd/Super and Alt+Tab, only while the VM viewport is focused. No screen/text retrieval. Ctrl+Alt+Shift+Esc releases capture. Enable the U-AVF VM keyboard service below; another key mapper may conflict.");
      this.addSecondaryButton(panel,VmKeyboardAccessibilityService.connected() ?
         "Keyboard service enabled · manage" : "Enable U-AVF VM keyboard service",()-> {
            this.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
         });
   }

   private void sendGuestInput(int var1, int var2, int var3, int var4) {
      OutputStream var5 = this.activeFrameInput;
      if (var5 != null) {
         byte[] var6 = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{85, 73, 78, 49}).put((byte)var1).put((byte)var2).putShort((short)0).putInt(var3).putInt(var4).array();
         this.frameInputWriter.execute(() -> {
            try {
               synchronized(var5) {
                  if (this.activeFrameInput == var5) {
                     var5.write(var6);
                     var5.flush();
                  }
               }
            } catch (Exception var6x) {
               Log.w("U-AVF", "Guest input write failed", var6x);
            }

         });
      }
   }

   private final GuestKeyboard guestKeyboard = new GuestKeyboard(
      (keysym, down) -> this.sendGuestInput(3, down ? 1 : 0, keysym, 0));
   private final OnBackInvokedCallback navigationBackCallback = this::handleBackAction;
   private GuestClipboard guestClipboard;
   private Object clipboardVm;

   @Override protected void onDestroy() {
      this.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(this.navigationBackCallback);
      this.activityDestroyed = true;
      if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.closeSilently();
      if (this.guestControl != null) this.guestControl.close();
      if (this.guestClipboard != null) this.guestClipboard.close();
      this.guestKeyboard.releaseAll();
      synchronized(this.frameConnectionLock) {
         ++this.frameConnectionGeneration;
         if(this.activeFrameSocket != null) {
            try { Os.shutdown(this.activeFrameSocket.getFileDescriptor(), android.system.OsConstants.SHUT_RDWR); } catch(Exception ignored) {}
            try { this.activeFrameSocket.close(); } catch(Exception ignored) {}
            this.activeFrameSocket = null;
         }
         this.activeFrameInput = null;
      }
      super.onDestroy();
   }

   @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
      if (this.frameVisible && this.activeFrameInput != null &&
          (this.toolbar == null || this.toolbar.getVisibility() != View.VISIBLE)) {
         return this.dispatchKeyEvent(event);
      }
      return super.dispatchKeyShortcutEvent(event);
   }

   private void onGuestPointer(int var1, int var2, int var3) {
      this.guestPointerX = var1;
      this.guestPointerY = var2;
      if (var3 == 2) {
         long var4 = SystemClock.uptimeMillis();
         if (var4 - this.lastPointerMoveMs < 33L) {
            return;
         }

         this.lastPointerMoveMs = var4;
      }

      this.sendGuestInput(1, 0, var1, var2);
      if (var3 == 0) {
         this.sendGuestInput(2, 1, 0, 0);
      }

      if (var3 == 1 || var3 == 3) {
         this.sendGuestInput(2, 0, 0, 0);
      }

   }

   public boolean dispatchKeyEvent(KeyEvent var1) {
      int var2 = var1.getKeyCode();
      boolean var3 = var1.getAction() == 0;
      boolean guestFocused = this.frameVisible && this.activeFrameInput != null &&
         (this.toolbar == null || this.toolbar.getVisibility() != View.VISIBLE);
      if (var2 == KeyEvent.KEYCODE_BACK || var2 == KeyEvent.KEYCODE_META_LEFT ||
          var2 == KeyEvent.KEYCODE_META_RIGHT) {
         // No text/characters: diagnose Samsung event routing, not user typing.
         Log.d("U-AVF", "Key route code=" + var2 + " action=" + var1.getAction() +
            " scan=" + var1.getScanCode() + " source=" + var1.getSource() +
            " device=" + var1.getDeviceId() + " flags=" + var1.getFlags() +
            " guest=" + guestFocused);
      }
      if (var2 == KeyEvent.KEYCODE_BACK && guestFocused && isPhysicalKeyboardBack(var1)) {
         if (var1.getAction() == KeyEvent.ACTION_DOWN || var1.getAction() == KeyEvent.ACTION_UP) {
            this.guestKeyboard.handle(var2, var3, var1.getMetaState(), 0);
         }
         return true;
      }
      if (var2 == 113 || var2 == 114) {
         this.physicalCtrlDown = var3;
      }

      if (var2 == 57 || var2 == 58) {
         this.physicalAltDown = var3;
      }

      if (var2 == 59 || var2 == 60) {
         this.physicalShiftDown = var3;
      }

      int selectedShortcut=this.getPreferences(0).getInt("runtime_panel_shortcut",KeyEvent.KEYCODE_ESCAPE);
      boolean runtimeShortcut = (var2 == KeyEvent.KEYCODE_ESCAPE || var2 == selectedShortcut) &&
         (var1.isCtrlPressed() || this.physicalCtrlDown) &&
         (var1.isAltPressed() || this.physicalAltDown) &&
         (var1.isShiftPressed() || this.physicalShiftDown);
      if (this.frameVisible && this.activeFrameInput != null && !runtimeShortcut &&
          !(var2 == this.runtimeShortcutKey && this.runtimeShortcutConsumed) &&
          var2 != KeyEvent.KEYCODE_BACK &&
          (this.toolbar == null || this.toolbar.getVisibility() != View.VISIBLE) &&
          (var1.getAction() == KeyEvent.ACTION_DOWN || var1.getAction() == KeyEvent.ACTION_UP)) {
         int textMeta = var1.getMetaState() &
            ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK | KeyEvent.META_META_MASK);
         if (this.guestKeyboard.handle(var2, var3, var1.getMetaState(), var1.getUnicodeChar(textMeta))) return true;
      }

      if (var3 && this.frameVisible && runtimeShortcut) {
         if (var1.getRepeatCount() == 0) {
            this.runtimeShortcutConsumed = true;
            this.runtimeShortcutKey=var2;
            this.guestKeyboard.releaseAll();
            if (this.toolbar != null && this.toolbar.getVisibility() == 0) {
               this.closeSessionDrawer();
            } else {
               this.openSessionDrawer();
            }
         }

         return true;
      } else if (var2 == this.runtimeShortcutKey && var1.getAction() == 1 && this.runtimeShortcutConsumed) {
         this.runtimeShortcutConsumed = false;
         return true;
      } else if (var1.getKeyCode() == 4) {
         if (var1.getAction() == 1) {
            this.handleBackAction();
         }

         return true;
      } else {
         if (this.frameVisible && this.activeFrameInput != null &&
             (this.toolbar == null || this.toolbar.getVisibility() != View.VISIBLE) &&
             (var1.getAction() == 0 || var1.getAction() == 1)) {
            int var4;
            switch (var1.getKeyCode()) {
               case 19:
                  var4 = 65362;
                  break;
               case 20:
                  var4 = 65364;
                  break;
               case 21:
                  var4 = 65361;
                  break;
               case 22:
                  var4 = 65363;
                  break;
               case 23:
               case 24:
               case 25:
               case 26:
               case 27:
               case 28:
               case 29:
               case 30:
               case 31:
               case 32:
               case 33:
               case 34:
               case 35:
               case 36:
               case 37:
               case 38:
               case 39:
               case 40:
               case 41:
               case 42:
               case 43:
               case 44:
               case 45:
               case 46:
               case 47:
               case 48:
               case 49:
               case 50:
               case 51:
               case 52:
               case 53:
               case 54:
               case 55:
               case 56:
               case 62:
               case 63:
               case 64:
               case 65:
               case 68:
               case 69:
               case 70:
               case 71:
               case 72:
               case 73:
               case 74:
               case 75:
               case 76:
               case 77:
               case 78:
               case 79:
               case 80:
               case 81:
               case 82:
               case 83:
               case 84:
               case 85:
               case 86:
               case 87:
               case 88:
               case 89:
               case 90:
               case 91:
               case 94:
               case 95:
               case 96:
               case 97:
               case 98:
               case 99:
               case 100:
               case 101:
               case 102:
               case 103:
               case 104:
               case 105:
               case 106:
               case 107:
               case 108:
               case 109:
               case 110:
               case 115:
               case 116:
               case 117:
               case 118:
               case 119:
               case 120:
               case 121:
               case 125:
               case 126:
               case 127:
               case 128:
               case 129:
               case 130:
               default:
                  var4 = var1.getUnicodeChar();
                  break;
               case 57:
                  var4 = 65513;
                  break;
               case 58:
                  var4 = 65514;
                  break;
               case 59:
                  var4 = 65505;
                  break;
               case 60:
                  var4 = 65506;
                  break;
               case 61:
                  var4 = 65289;
                  break;
               case 66:
                  var4 = 65293;
                  break;
               case 67:
                  var4 = 65288;
                  break;
               case 92:
                  var4 = 65365;
                  break;
               case 93:
                  var4 = 65366;
                  break;
               case 111:
                  var4 = 65307;
                  break;
               case 112:
                  var4 = 65535;
                  break;
               case 113:
                  var4 = 65507;
                  break;
               case 114:
                  var4 = 65508;
                  break;
               case 122:
                  var4 = 65360;
                  break;
               case 123:
                  var4 = 65367;
                  break;
               case 124:
                  var4 = 65379;
                  break;
               case 131:
                  var4 = 65470;
                  break;
               case 132:
                  var4 = 65471;
                  break;
               case 133:
                  var4 = 65472;
                  break;
               case 134:
                  var4 = 65473;
                  break;
               case 135:
                  var4 = 65474;
                  break;
               case 136:
                  var4 = 65475;
                  break;
               case 137:
                  var4 = 65476;
                  break;
               case 138:
                  var4 = 65477;
                  break;
               case 139:
                  var4 = 65478;
                  break;
               case 140:
                  var4 = 65479;
                  break;
               case 141:
                  var4 = 65480;
                  break;
               case 142:
                  var4 = 65481;
            }

            if (var4 > 0) {
               this.sendGuestInput(3, var1.getAction() == 0 ? 1 : 0, var4, 0);
               return true;
            }
         }

         return super.dispatchKeyEvent(var1);
      }
   }

   private void onGuestRelativePointer(int var1, int var2) {
      if (this.frameVisible && this.guestFrameWidth > 0 && this.guestFrameHeight > 0) {
         if (this.guestPointerX < 0 || this.guestPointerY < 0) {
            this.guestPointerX = this.guestFrameWidth / 2;
            this.guestPointerY = this.guestFrameHeight / 2;
         }

         this.guestPointerX = Math.max(0, Math.min(this.guestFrameWidth - 1, this.guestPointerX + var1));
         this.guestPointerY = Math.max(0, Math.min(this.guestFrameHeight - 1, this.guestPointerY + var2));
         this.sendGuestInput(1, 0, this.guestPointerX, this.guestPointerY);
      }
   }

   private int runtimeButtonVisibility() {
      return this.getPreferences(0).getBoolean("hide_runtime_panel_button",false)?View.GONE:View.VISIBLE;
   }

   private String controlsShortcutLabel() {
      return "Ctrl + Alt + Shift + "+(this.getPreferences(0).getInt("runtime_panel_shortcut",KeyEvent.KEYCODE_ESCAPE)==KeyEvent.KEYCODE_F12?"F12":"Esc");
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
            this.guestKeyboard.releaseAll();this.openSessionDrawer();
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
      if(this.installedEncodedDisplay!=null) { this.installedEncodedDisplay.closeSilently(); this.installedEncodedDisplay=null; }
      if(this.installedEncodedSurface!=null) { this.page.removeView(this.installedEncodedSurface); this.installedEncodedSurface=null; }
      if(this.frameSurface!=null) this.frameSurface.setEncodedMode(false,0,0);
      this.frameVisible = false;
      this.guestFrameWidth = 0;
      this.guestFrameHeight = 0;
      this.guestPointerX = -1;
      this.guestPointerY = -1;
      this.frameRevealPostQueued.set(false);
      FrameSurfaceView surface = this.frameSurface;
      if (surface != null) surface.clearFrame();
   }

   public void onBackPressed() {
      this.handleBackAction();
   }

   private static boolean isPhysicalKeyboardBack(KeyEvent event) {
      InputDevice device = event.getDevice();
      return device != null && !device.isVirtual() && device.isExternal() &&
         device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
         event.isFromSource(InputDevice.SOURCE_KEYBOARD);
   }

   private void handleBackAction() {
      if (this.toolbar != null && this.toolbar.getVisibility() == 0) {
         this.closeSessionDrawer();
      } else if (this.logPanel != null && this.logPanel.getVisibility() == 0) {
         this.logPanel.setVisibility(8);
         this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS);
      } else if (!this.frameVisible && !this.isManagedVmRunning()) {
         if (this.productScreen != MainActivity.ProductScreen.ONBOARDING && this.productScreen != MainActivity.ProductScreen.MODE_SELECTION) {
            this.renderProductScreen(this.backDestination());
         } else {
            if (this.productScreen == MainActivity.ProductScreen.MODE_SELECTION) {
               this.renderProductScreen(MainActivity.ProductScreen.ONBOARDING);
            } else {
               this.finish();
            }

         }
      } else {
         this.show("VM is still running. Use Stop to power it off; Back will keep it running.");
      }
   }

   private boolean isManagedVmRunning() {
      return this.lifecycle.busy() || this.vmMayBeRunning;
   }

   private void addProductControls() {
      this.homePanel = new FrameLayout(this);
      this.homePanel.setBackgroundColor(-234156008);
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
      this.toolbar.setPadding(this.dp(14), this.dp(14), this.dp(14), this.dp(14));
      this.toolbar.setBackground(this.panelBackground(-266854357));
      this.toolbar.setElevation((float)this.dp(14));
      FrameLayout.LayoutParams var2 = new FrameLayout.LayoutParams(Math.min(this.dp(420), this.getResources().getDisplayMetrics().widthPixels - this.dp(24)), Math.min(this.dp(560), this.getResources().getDisplayMetrics().heightPixels - this.dp(24)), 8388661);
      var2.setMargins(0, this.dp(8), this.dp(8), this.dp(8));
      this.page.addView(this.toolbar, var2);
      this.toolbar.setVisibility(8);
      this.drawerScrim = new View(this);
      this.drawerScrim.setBackgroundColor(1996488704);
      this.drawerScrim.setVisibility(8);
      this.drawerScrim.setOnClickListener((var1x) -> this.closeSessionDrawer());
      this.page.addView(this.drawerScrim, 1, new FrameLayout.LayoutParams(-1, -1));
      this.settingsPanel = new LinearLayout(this);
      this.logPanel = new ScrollView(this);
      this.logText = new TextView(this);
      this.logPanel.addView(this.logText, new FrameLayout.LayoutParams(-1, -2));
      this.settingsPanel.setVisibility(8);
      this.logPanel.setVisibility(8);
      this.page.addView(this.logPanel, new FrameLayout.LayoutParams(-1, -1));
      this.restoreProfile();
   }

   private void openSessionDrawer() {
      if (this.frameVisible && this.toolbar != null) {
         this.guestKeyboard.releaseAll();
         this.restorePointerCaptureAfterDrawer = this.frameSurface != null && this.frameSurface.isPointerCaptured();
         if (this.restorePointerCaptureAfterDrawer) {
            this.frameSurface.releaseCapturedPointer();
         }

         this.populateSessionDrawer();
         this.drawerScrim.animate().cancel();
         this.drawerScrim.setAlpha(0.0F);
         this.drawerScrim.setVisibility(0);
         this.drawerScrim.animate().alpha(0.28F).setDuration(170L).start();
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
      this.toolbar.setPadding(this.dp(18), this.dp(16), this.dp(18), this.dp(18));
      LinearLayout var1 = new LinearLayout(this);
      var1.setGravity(16);
      TextView var2 = this.text("Control center", 21, -1);
      var2.setTypeface(Typeface.DEFAULT, 1);
      var1.addView(var2, new LinearLayout.LayoutParams(0, this.dp(42), 1.0F));
      Button var3 = this.button("×");
      var3.setTextSize(22.0F);
      var3.setContentDescription("Close session controls");
      var1.addView(var3, new LinearLayout.LayoutParams(this.dp(40), this.dp(40)));
      var3.setOnClickListener((var1x) -> this.closeSessionDrawer());
      this.toolbar.addView(var1);
      ScrollView var4 = new ScrollView(this);
      var4.setFillViewport(false);
      this.drawerContent = new LinearLayout(this);
      this.drawerContent.setOrientation(1);
      var4.addView(this.drawerContent, new FrameLayout.LayoutParams(-1, -2));
      LinearLayout.LayoutParams var5 = new LinearLayout.LayoutParams(-1, 0, 1.0F);
      var5.topMargin = this.dp(6);
      this.toolbar.addView(var4, var5);
      String var6 = this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? "Windows workspace" : "Ubuntu workspace";
      TextView session=this.text(var6 + " · " + this.lifecycle.state().name().toLowerCase(java.util.Locale.ROOT),14,-5326393);
      this.drawerContent.addView(session,this.spaced(new LinearLayout.LayoutParams(-1,-2),12));
      this.drawerContent.addView(this.text("Guest control: " + this.guestControlStatus,12,-6313286),this.spaced(new LinearLayout.LayoutParams(-1,-2),6));
      this.addDrawerSection("INPUT");
      boolean mouseWasCaptured = this.restorePointerCaptureAfterDrawer ||
         (this.frameSurface != null && this.frameSurface.isPointerCaptured());
      this.addControlPair(mouseWasCaptured ? "Release mouse" : "Capture mouse",() -> {
         // Opening the drawer temporarily releases capture. Preserve that
         // intent in the label and never toggle it back on for Release.
         this.restorePointerCaptureAfterDrawer=false;
         if (mouseWasCaptured && this.frameSurface != null) this.frameSurface.releaseCapturedPointer();
         this.closeSessionDrawer();
         if (!mouseWasCaptured) this.page.postDelayed(this::togglePointerCapture,170);
      },"Input settings",() -> this.renderProductScreen(ProductScreen.INPUT_SETTINGS));
      this.addDrawerSection("WORKSPACE");
      this.addControlPair("Settings",() -> this.renderProductScreen(ProductScreen.VM_SETTINGS),"Diagnostics",() -> this.renderProductScreen(ProductScreen.DIAGNOSTICS));
      this.addControlPair("Workspace home",() -> this.renderProductScreen(this.selectedProfile == LaunchProfile.WINDOWS ? ProductScreen.WINDOWS_HOME : ProductScreen.LINUX_HOME),"Return to VM",this::closeSessionDrawer);
      this.addDrawerSection("POWER");
      this.addControlPair("Restart…",() -> {
         if(this.selectedProfile != LaunchProfile.LINUX) {this.show("Guest restart is available for Ubuntu only.");return;}
         ProductDialog.show(this,"Restart Ubuntu?","Save your work first. Request a normal guest reboot and reconnect this workspace.","Restart",false,()->this.requestGuestPower(true));
      },"Shut down…",this::confirmStopVm);
      TextView force=this.text("Force stop…",12,Color.rgb(235,139,128));
      force.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
      force.setPadding(0,this.dp(12),0,this.dp(12));
      force.setOnClickListener(v -> this.confirmForceStop(false));
      this.drawerContent.addView(force,new LinearLayout.LayoutParams(-1,this.dp(48)));
      this.toolbar.addView(this.text(this.controlsShortcutLabel()+" · open / close",11,-6313286),new LinearLayout.LayoutParams(-1,-2));
   }

   private void addControlPair(String left,Runnable leftAction,String right,Runnable rightAction) {
      LinearLayout row=new LinearLayout(this);
      String[] labels={left,right};
      Runnable[] actions={leftAction,rightAction};
      for(int i=0;i<2;i++) {
         final Runnable action=actions[i];
         Button tile=this.button(labels[i]);
         tile.setTextSize(14);
         tile.setAllCaps(false);
         tile.setPadding(this.dp(8),this.dp(8),this.dp(8),this.dp(8));
         tile.setBackground(this.panelBackground(Color.argb(24,255,255,255)));
         tile.setOnClickListener(v -> action.run());
         LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,this.dp(56),1f);
         if(i==1)cell.leftMargin=this.dp(12);
         row.addView(tile,cell);
      }
      this.drawerContent.addView(row,this.spaced(new LinearLayout.LayoutParams(-1,-2),12));
   }

   private void togglePointerCapture() {
      if (this.frameSurface != null && this.frameVisible) {
         if (this.frameSurface.isPointerCaptured()) {
            this.frameSurface.releaseCapturedPointer();
            if (this.toolbar != null && this.toolbar.getVisibility() == 0) {
               this.restorePointerCaptureAfterDrawer = false;
            }
         } else {
            this.frameSurface.requestFocus();
            this.frameSurface.capturePointer();
         }

      } else {
         this.show("Start a VM before capturing the mouse.");
      }
   }

   private void showSessionLogsInDrawer() {
      this.refreshLogPanel();
      CharSequence var1 = this.logText.getText();
      this.toolbar.removeAllViews();
      LinearLayout var2 = new LinearLayout(this);
      var2.setGravity(16);
      Button var3 = this.button("‹");
      var3.setTextSize(20.0F);
      var3.setContentDescription("Back to session controls");
      var2.addView(var3, new LinearLayout.LayoutParams(this.dp(40), this.dp(40)));
      TextView var4 = this.text("Session logs", 18, -1);
      var4.setTypeface(this.productTypeface(), Typeface.BOLD);
      LinearLayout.LayoutParams var5 = new LinearLayout.LayoutParams(0, -1, 1.0F);
      var5.leftMargin = this.dp(8);
      var2.addView(var4, var5);
      Button var6 = this.button("×");
      var6.setTextSize(22.0F);
      var2.addView(var6, new LinearLayout.LayoutParams(this.dp(40), this.dp(40)));
      this.toolbar.addView(var2);
      var3.setOnClickListener((var1x) -> this.populateSessionDrawer());
      var6.setOnClickListener((var1x) -> this.closeSessionDrawer());
      TextView var7 = this.text(var1 == null ? "No session logs yet." : var1.toString(), 10, -2828067);
      var7.setGravity(51);
      var7.setTypeface(Typeface.MONOSPACE);
      var7.setTextIsSelectable(true);
      var7.setPadding(this.dp(10), this.dp(12), this.dp(10), this.dp(12));
      ScrollView var8 = new ScrollView(this);
      var8.setFillViewport(false);
      var8.addView(var7, new FrameLayout.LayoutParams(-1, -2));
      LinearLayout.LayoutParams var9 = new LinearLayout.LayoutParams(-1, 0, 1.0F);
      var9.topMargin = this.dp(10);
      this.toolbar.addView(var8, var9);
   }

   private void addDrawerSection(String var1) {
      TextView var2 = this.text(var1, 10, -7233099);
      var2.setTypeface(Typeface.DEFAULT, 1);
      LinearLayout.LayoutParams var3 = new LinearLayout.LayoutParams(-1, this.dp(30));
      var3.topMargin = this.dp(16);
      (this.drawerContent == null ? this.toolbar : this.drawerContent).addView(var2, var3);
   }

   private void addDrawerLabel(String var1, String var2) {
      LinearLayout var3 = new LinearLayout(this);
      var3.setOrientation(1);
      var3.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
      var3.setBackground(this.panelBackground(-14669257));
      TextView var4 = this.text(var1, 14, -1);
      var4.setTypeface(Typeface.DEFAULT, 1);
      var3.addView(var4);
      TextView var5 = this.text(var2, 11, -5194292);
      LinearLayout.LayoutParams var6 = new LinearLayout.LayoutParams(-1, -2);
      var6.topMargin = this.dp(3);
      var3.addView(var5, var6);
      (this.drawerContent == null ? this.toolbar : this.drawerContent).addView(var3, this.spaced(new LinearLayout.LayoutParams(-1, -2), 12));
   }

   private void returnToRunningVm() {
      if (!this.frameVisible && this.activeFrameVmName == null) {
         if (this.isManagedVmRunning()) {
            this.show("Reconnecting to the active Linux session…");
            this.reconnectRunningFrameVmIfPresent();
         } else {
            this.show("There is no running virtual machine to return to.");
         }

      } else {
         this.frameVisible = true;
         this.showRunningDesktop();
      }
   }

   private void addWelcomePanel() {
      this.renderProductScreen(MainActivity.ProductScreen.ONBOARDING);
   }

   private void renderProductScreen(ProductScreen var1) {
      if(var1==ProductScreen.ONBOARDING || var1==ProductScreen.COMPATIBILITY) {this.showFigmaOnboarding(var1);return;}
      if(var1==ProductScreen.INSTALL_STATUS) {this.showFigmaSetupStatus();return;}
      if(var1==ProductScreen.LINUX_HOME || var1==ProductScreen.CREATE_OS || var1==ProductScreen.CREATE_IMAGE || var1==ProductScreen.LINUX_SETUP || var1==ProductScreen.CREATE_STORAGE || var1==ProductScreen.CREATE_REVIEW) {
         this.showFigmaWorkspaceScreen(var1);return;
      }
      if (!this.frameVisible || var1 == MainActivity.ProductScreen.INSTALL_STATUS || var1 == MainActivity.ProductScreen.DIAGNOSTICS || var1 == MainActivity.ProductScreen.VM_SETTINGS || var1 == MainActivity.ProductScreen.APP_SETTINGS || var1 == MainActivity.ProductScreen.MODE_SELECTION || var1 == MainActivity.ProductScreen.LINUX_HOME || var1 == MainActivity.ProductScreen.WINDOWS_HOME || var1 == MainActivity.ProductScreen.COMPATIBILITY || var1 == MainActivity.ProductScreen.INPUT_SETTINGS || var1 == MainActivity.ProductScreen.INSTALL_RECOVERY) {
         this.productScreen = var1;
         this.closeSessionDrawerImmediately();
         this.settingsPanel.setVisibility(8);
         this.logPanel.setVisibility(8);
         this.status.setVisibility(8);
         this.menuButton.setText("⋯");
         this.menuButton.setContentDescription("Open session controls");
         this.menuButton.setVisibility(8);
         this.homePanel.setVisibility(0);
         this.homePanel.removeAllViews();
         this.homePanel.addView(new FigmaProductBackdrop(this),new FrameLayout.LayoutParams(-1,-1));
         LinearLayout var2 = new LinearLayout(this);
         var2.setOrientation(1);
         var2.setBackgroundColor(Color.TRANSPARENT);
         this.homePanel.addView(var2, new FrameLayout.LayoutParams(-1, -1));
         LinearLayout var3 = new LinearLayout(this);
         var3.setGravity(16);
         var3.setPadding(this.dp(24), this.dp(10), this.dp(80), this.dp(10));
         var3.setBackgroundColor(0xf509121a);
         ImageView var4 = new ImageView(this);
         var4.setImageResource(2130771969);
         var4.setScaleType(ScaleType.FIT_CENTER);
         var3.addView(var4, new LinearLayout.LayoutParams(this.dp(48), this.dp(48)));
         TextView var5 = this.text("U-AVF", 20, -657414);
         var5.setTypeface(this.productTypeface(), Typeface.BOLD);
         LinearLayout.LayoutParams var6 = new LinearLayout.LayoutParams(-2, -2);
         var6.leftMargin = this.dp(12);
         var3.addView(var5, var6);
         TextView var7 = this.text(this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? "WINDOWS" : "LINUX", 11, -6378304);
         var7.setGravity(21);
         var3.addView(var7, new LinearLayout.LayoutParams(0, -1, 1.0F));
         var2.addView(var3, new LinearLayout.LayoutParams(-1, this.dp(68)));
         ScrollView var8 = new ScrollView(this);
         var8.setFillViewport(true);
         var8.setClipToPadding(false);
         var8.setPadding(this.dp(20),this.dp(24),this.dp(20),this.dp(20));
         LinearLayout var9 = new LinearLayout(this);
         var9.setOrientation(1);
         var9.setPadding(this.dp(24), this.dp(12), this.dp(24), this.dp(28));
         var9.setGravity(1);
         var8.addView(var9);
         var2.addView(var8, new LinearLayout.LayoutParams(-1, 0, 1.0F));
         this.productBody = var9;
         this.productStatus = this.text("", 12, -4733481);
         this.productStatus.setPadding(this.dp(24), this.dp(8), this.dp(24), this.dp(10));
         var2.addView(this.productStatus, new LinearLayout.LayoutParams(-1, -2));
         switch (var1.ordinal()) {
            case 0 -> this.renderOnboarding(var9);
            case 1 -> this.renderCompatibility(var9);
            case 2 -> this.renderPermissionHelp(var9);
            case 3 -> this.renderModeSelection(var9);
            case 4 -> this.renderCreateOs(var9);
            case 5 -> this.renderCreateImage(var9);
            case 6 -> this.renderCreateStorage(var9);
            case 7 -> this.renderCreateReview(var9);
            case 8 -> this.renderInstallRecovery(var9);
            case 9 -> this.renderInputSettings(var9);
            case 10 -> this.renderLinuxSetup(var9);
            case 11 -> this.renderLinuxHome(var9);
            case 12 -> this.renderWindowsHome(var9);
            case 13 -> this.renderVmSettings(var9);
            case 14 -> this.renderAppSettings(var9);
            case 15 -> this.renderDiagnostics(var9);
            case 16 -> this.renderInstallationStatus(var9);
         }

         this.homePanel.animate().cancel();
         this.homePanel.setAlpha(1.0F);
         this.homePanel.setTranslationY((float)this.dp(7));
         this.homePanel.animate().translationY(0.0F).setDuration(220L).setInterpolator(new DecelerateInterpolator()).start();
      }
   }

   private ProductScreen backDestination() {
      if(this.productScreen==ProductScreen.VM_SETTINGS && this.wizardSettingsActive)return ProductScreen.CREATE_STORAGE;
      if(this.productScreen==ProductScreen.INSTALL_STATUS)return ProductScreen.INSTALL_RECOVERY;
      if (this.productScreen == MainActivity.ProductScreen.VM_PERMISSION) {
         return MainActivity.ProductScreen.COMPATIBILITY;
      } else if (this.productScreen != MainActivity.ProductScreen.CREATE_OS && this.productScreen != MainActivity.ProductScreen.LINUX_SETUP) {
         if (this.productScreen == MainActivity.ProductScreen.CREATE_IMAGE) {
            return MainActivity.ProductScreen.CREATE_OS;
         } else if (this.productScreen == MainActivity.ProductScreen.CREATE_STORAGE) {
            return MainActivity.ProductScreen.CREATE_IMAGE;
         } else if (this.productScreen == MainActivity.ProductScreen.CREATE_REVIEW) {
            return MainActivity.ProductScreen.CREATE_STORAGE;
         } else if (this.productScreen != MainActivity.ProductScreen.INSTALL_RECOVERY && this.productScreen != MainActivity.ProductScreen.INPUT_SETTINGS) {
            if (this.productScreen != MainActivity.ProductScreen.WINDOWS_HOME && this.productScreen != MainActivity.ProductScreen.LINUX_HOME) {
               if (this.productScreen == MainActivity.ProductScreen.APP_SETTINGS) {
                  return MainActivity.ProductScreen.MODE_SELECTION;
               } else if (this.productScreen != MainActivity.ProductScreen.VM_SETTINGS && this.productScreen != MainActivity.ProductScreen.DIAGNOSTICS) {
                  return MainActivity.ProductScreen.MODE_SELECTION;
               } else {
                  return this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? MainActivity.ProductScreen.WINDOWS_HOME : MainActivity.ProductScreen.LINUX_HOME;
               }
            } else {
               return MainActivity.ProductScreen.MODE_SELECTION;
            }
         } else {
            return MainActivity.ProductScreen.LINUX_HOME;
         }
      } else {
         return MainActivity.ProductScreen.MODE_SELECTION;
      }
   }

   private void renderOnboarding(LinearLayout var1) {
      ImageView var2 = new ImageView(this);
      var2.setImageResource(2130771969);
      var2.setScaleType(ScaleType.FIT_CENTER);
      var1.addView(var2, new LinearLayout.LayoutParams(this.dp(132), this.dp(132)));
      this.addTitle(var1, "Your virtual workspace", "Run a Linux desktop or explore experimental Windows ARM virtualisation on this tablet.");
      this.addInfoCard(var1, "Private by design", "U-AVF uses Android's built-in virtualization. It does not require root or an unlocked bootloader.");
      this.addPrimaryButton(var1, "Check device compatibility", () -> {
         this.compatibilityRunning = true;
         this.renderProductScreen(MainActivity.ProductScreen.COMPATIBILITY);
         (new Thread(this::checkDeviceReadiness, "U-AVF-readiness-check")).start();
      });
      this.addSecondaryButton(var1, "I already checked this device", () -> this.renderProductScreen(MainActivity.ProductScreen.MODE_SELECTION));
   }

   private void renderCompatibility(LinearLayout var1) {
      this.addTitle(var1, this.compatibilityRunning ? "Checking this device…" : (this.compatibilityPassed ? "Prerequisites ready" : "Device compatibility"), "Real, local checks. Guest boot and GPU acceleration are confirmed separately by a running Ubuntu session.");
      String[] names={"Android / ARM64","Virtualization Framework","Custom VM permissions","Bundled platform integrity"};
      for(int i=0;i<names.length;i++) {
         LinearLayout row=new LinearLayout(this);
         row.setGravity(android.view.Gravity.CENTER_VERTICAL);
         row.setPadding(this.dp(18),this.dp(14),this.dp(18),this.dp(14));
         row.setBackground(this.panelBackground(Color.argb(18,255,255,255)));
         row.addView(this.text(names[i],15,-1),new LinearLayout.LayoutParams(0,-2,1f));
         String result=this.readinessResults[i];
         boolean checking=this.compatibilityRunning && i==this.readinessCompleted;
         if(checking) {
            android.widget.ProgressBar spinner=new android.widget.ProgressBar(this);
            spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.rgb(233,84,32)));
            LinearLayout.LayoutParams indicator=new LinearLayout.LayoutParams(this.dp(24),this.dp(24));
            indicator.rightMargin=this.dp(12);row.addView(spinner,indicator);
            result="Checking…";
         }
         int color=result.startsWith("Passed") ? Color.rgb(54,194,120) : result.startsWith("Failed") ? Color.rgb(244,137,122) : -5326393;
         TextView status=this.text(result,12,color);
         status.setGravity(android.view.Gravity.END);
         row.addView(status,new LinearLayout.LayoutParams(this.dp(220),-2));
         this.addBounded(var1,row,12);
      }
      android.widget.ProgressBar progress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
      progress.setMax(this.readinessResults.length);
      progress.setProgress(this.readinessCompleted,true);
      progress.setProgressTintList(ColorStateList.valueOf(Color.rgb(233,84,32)));
      this.addBounded(var1,progress,20,this.dp(8));
      if(this.compatibilityRunning) {
         android.widget.ProgressBar activity=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
         activity.setIndeterminate(true);
         activity.setIndeterminateTintList(ColorStateList.valueOf(Color.rgb(233,84,32)));
         this.addBounded(var1,activity,8,this.dp(4));
      }
      this.addSmallNote(var1,this.compatibilityRunning ? "Checking files and permissions… Keep using the app; no VM is started." : "Boot, vsock and graphics: not tested by this preflight. Results are saved locally in Diagnostics.");
      if (!this.compatibilityRunning) {
         if(this.compatibilityPassed) this.addPrimaryButton(var1,"Continue to dashboard",() -> this.renderProductScreen(ProductScreen.MODE_SELECTION));
         this.addSecondaryButton(var1,"Run compatibility check", () -> {
            if (this.hasAvfPermissions()) {
               this.compatibilityRunning = true;
               this.renderProductScreen(MainActivity.ProductScreen.COMPATIBILITY);
               (new Thread(this::checkDeviceReadiness, "U-AVF-readiness-check")).start();
            } else {
               this.renderProductScreen(MainActivity.ProductScreen.VM_PERMISSION);
            }

         });
      }
      this.addFooterAction(var1,"Dashboard",() -> this.renderProductScreen(ProductScreen.MODE_SELECTION));
   }

   private void readinessResult(int index,String result) {
      this.runOnUiThread(() -> {
         this.readinessResults[index]=result;
         this.readinessCompleted=index+1;
         if(this.productScreen==ProductScreen.COMPATIBILITY)this.renderProductScreen(ProductScreen.COMPATIBILITY);
      });
   }

   private void renderPermissionHelp(LinearLayout var1) {
      this.addTitle(var1, "Virtual machine access", "U-AVF needs both Android virtualization permissions.");
      this.addInfoCard(var1, "Next step", "Enable USB debugging, connect the tablet and accept the computer's authorization prompt. Run both commands below. No output normally means success. Then check permissions to restart the device compatibility test.");
      this.addInfoCard(var1,"Permission status",
         "Manage virtual machines: "+(this.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE")==0?"Granted":"Missing")+
         "\nCustom virtual machines: "+(this.checkSelfPermission("android.permission.USE_CUSTOM_VIRTUAL_MACHINE")==0?"Granted":"Missing"));
      TextView var2 = this.text("adb shell pm grant " + this.getPackageName() + " android.permission.MANAGE_VIRTUAL_MACHINE\n\nadb shell pm grant " + this.getPackageName() + " android.permission.USE_CUSTOM_VIRTUAL_MACHINE", 12, -4466689);
      var2.setTypeface(Typeface.MONOSPACE);
      var2.setTextIsSelectable(true);
      var2.setPadding(this.dp(14), this.dp(14), this.dp(14), this.dp(14));
      var2.setBackground(this.panelBackground(-15656411));
      this.addBounded(var1, var2, 14);
      this.addPrimaryButton(var1, "Check permission again", () -> {
         if (this.hasAvfPermissions()) {
            this.diagnosticsReturnAfterReadiness=false;
            this.beginCompatibilityCheck();
         } else {
            this.renderProductScreen(ProductScreen.VM_PERMISSION);
            this.show("The required Android virtualization permission is still unavailable.");
         }

      });
   }

   private void renderModeSelection(LinearLayout var1) {
      this.addTitle(var1, "Dashboard", "Your workspaces");
      File var2 = this.persistentUbuntuDiskFile();
      boolean var3 = var2.isFile();
      boolean var4 = var3 && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false);
      if(var3) {
      String var5 = this.isManagedVmRunning() ? this.lifecycle.state().name().toLowerCase(Locale.ROOT) : var4 ? "Installed · ready" : "Setup needs attention";
      String var6 = var4 ? "Persistent Ubuntu · " + formatBytes(var2.length()) : "Persistent workspace found · continue setup or repair";
      String var7 = "Open workspace";
      this.addWorkspaceHeroCard(var1, "Ubuntu 24.04 LTS", var5, var6, var7, () -> {
         this.selectProfile(MainActivity.LaunchProfile.LINUX);
         this.renderProductScreen(MainActivity.ProductScreen.LINUX_HOME);

      });
      }
      if (this.vmMayBeRunning || this.activeFrameVmName != null || this.isManagedVmRunning()) {
         this.addSecondaryButton(var1, "Return to running workspace", this::returnToRunningVm);
      }

      if (!var3) {
         this.addInfoCard(var1,"No workspaces yet","Create your first workspace to get started.");
         this.addPrimaryButton(var1, "＋   Create workspace", () -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_OS));
      } else {
         this.addInfoCard(var1,"Workspace support","This version supports one Ubuntu workspace. Windows installation and additional workspaces are not available yet.");
      }

      String var10003 = Build.MODEL;
      this.addInfoCard(var1, "Device", var10003 + " · Android " + VERSION.RELEASE + " · " + (this.hasAvfPermissions() ? "Virtualization access ready" : "Virtualization permission needed"));
      this.addFooterAction(var1, "Diagnostics and compatibility", () -> this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS));
      this.addFooterAction(var1, "U-AVF settings", () -> this.renderProductScreen(MainActivity.ProductScreen.APP_SETTINGS));
   }

   private void renderLinuxSetup(LinearLayout var1) {
      this.renderCreateImage(var1);
   }

   private void renderLinuxHome(LinearLayout var1) {
      File var2 = this.persistentUbuntuDiskFile();
      boolean var3 = var2.isFile();
      boolean var4 = var3 && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false);
      this.addTitle(var1, "Ubuntu 24.04 LTS", var4 ? "Installed · ready" : "Ubuntu workspace");
      this.addWorkspaceHeroCard(var1, "Ubuntu Desktop ARM64", this.isManagedVmRunning() ? this.lifecycle.state().name().toLowerCase(Locale.ROOT) : var4 ? "Stopped" : "Setup required", var3 ? "Persistent workspace · " + formatBytes(var2.length()) : "The official Ubuntu image is preserved separately", this.isManagedVmRunning() ? "Return to Ubuntu" : var4 ? "Launch Ubuntu" : (var3 ? "Continue setup" : "Create workspace"), () -> {
         if(this.isManagedVmRunning()) {this.returnToRunningVm();return;}
         if (var4) {
            (new Thread(() -> this.startPersistentUbuntu(true), "U-AVF-persistent-ubuntu")).start();
         } else if (var3) {
            (new Thread(this::resumeInstalledUbuntuOrOfferRepair, "U-AVF-resume-installed-ubuntu")).start();
         } else {
            this.renderProductScreen(this.isoPresent() ? MainActivity.ProductScreen.CREATE_STORAGE : MainActivity.ProductScreen.CREATE_OS);
         }

      });
      if (this.frameVisible || this.isManagedVmRunning()) {
         this.addSecondaryButton(var1, "Return to running Ubuntu", this::showRunningDesktop);
      }

      LinearLayout var5 = new LinearLayout(this);
      var5.setOrientation(0);
      this.addActionTile(var5, "Settings", "Performance, display, input", () -> this.renderProductScreen(MainActivity.ProductScreen.VM_SETTINGS));
      this.addActionTile(var5, "Diagnostics", "Device checks and logs", () -> this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS));
      this.addBounded(var1, var5, 14, this.dp(96));
      this.addSecondaryButton(var1, "Install & recovery", () -> this.renderProductScreen(MainActivity.ProductScreen.INSTALL_RECOVERY));
      this.addSmallNote(var1, "Reinstall replaces Ubuntu Root (vda3) only. U-AVF platform/EFI and the verified ISO stay intact.");
      this.addFooterAction(var1, "← Dashboard", () -> this.renderProductScreen(MainActivity.ProductScreen.MODE_SELECTION));
   }

   private void renderCreateOs(LinearLayout var1) {
      this.addTitle(var1, "Create workspace", "Choose an operating system.");
      this.addWizardProgress(var1, 1);
      this.addModeCard(var1, "Ubuntu", "Recommended · official ARM64 GNOME desktop", 2130771970, -952298, () -> {
         this.createWorkspaceOs = "ubuntu";
         this.renderProductScreen(MainActivity.ProductScreen.CREATE_IMAGE);
      });
      this.addModeCard(var1, "Windows ARM", "Experimental · existing verified boot media only", 2130771972, -13203242, () -> {
         this.createWorkspaceOs = "windows";
         this.selectProfile(MainActivity.LaunchProfile.WINDOWS);
         this.renderProductScreen(MainActivity.ProductScreen.WINDOWS_HOME);
      });
      this.addSmallNote(var1, "U-AVF currently supports one persistent Ubuntu workspace. Windows remains an experimental boot path.");
      this.addFooterAction(var1, "← Dashboard", () -> this.renderProductScreen(MainActivity.ProductScreen.MODE_SELECTION));
   }

   private void renderCreateImage(LinearLayout var1) {
      this.addTitle(var1, "Create workspace", "Choose where Ubuntu comes from.");
      this.addWizardProgress(var1, 2);
      this.addInfoCard(var1, "Ubuntu Desktop 24.04.5 · ARM64", "Only the official, checksum-verified image is accepted. The source ISO is never modified.");
      this.addInfoCard(var1, "Image status", this.isoPresent() ? "Verified · SHA-256 matches Canonical image" : "Not selected yet · about 3.7 GiB download");
      this.addPrimaryButton(var1, "Open Ubuntu website", this::openOfficialUbuntuDownloadPage);
      this.addSecondaryButton(var1, "Choose ISO from this device", this::openUbuntuIsoPicker);
      Button var2 = this.button("Continue to storage");
      var2.setTextSize(15.0F);
      var2.setTypeface(Typeface.DEFAULT, 1);
      var2.setBackground(this.panelBackground(this.isoPresent() ? -13922972 : -13354946));
      var2.setEnabled(this.isoPresent());
      var2.setOnClickListener((var1x) -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_STORAGE));
      this.addBounded(var1, var2, 12, this.dp(54));
      this.addFooterAction(var1, "← Back", () -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_OS));
   }

   private void renderCreateStorage(LinearLayout var1) {
      this.addTitle(var1, "Create workspace", "U-AVF prepares a persistent disk with protected boot media.");
      this.addWizardProgress(var1, 3);
      this.addInfoCard(var1, "Storage location", "U-AVF private workspace storage · managed automatically");
      File var2 = this.persistentUbuntuDiskFile();
      this.addInfoCard(var1, "Persistent disk", var2.isFile() ? formatBytes(var2.length()) + " · existing workspace" : "Created automatically · includes a dedicated Ubuntu system partition");
      this.addInfoCard(var1, "Safe install layout", "Ubuntu Root (vda3) is the only partition formatted by Setup. U-AVF EFI (vda1) and the verified official ISO (vda2) are preserved.");
      this.addSmallNote(var1, "CPU, memory, display size and storage geometry use the validated device profile; this screen does not offer unsupported tuning controls.");
      this.addPrimaryButton(var1, "Review workspace", () -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_REVIEW));
      this.addFooterAction(var1, "← Back", () -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_IMAGE));
   }

   private void renderCreateReview(LinearLayout var1) {
      this.addTitle(var1, "Review workspace", "The workspace is created first. Ubuntu Setup opens automatically in Installation mode.");
      this.addWizardProgress(var1, 4);
      this.addInfoCard(var1, "Operating system", "Ubuntu 24.04.5 LTS · official ARM64 image · " + (this.isoPresent() ? "verified" : "image missing"));
      File var2 = this.persistentUbuntuDiskFile();
      this.addInfoCard(var1, "Storage", var2.isFile() ? "Existing persistent workspace · " + formatBytes(var2.length()) : "Managed U-AVF disk · protected boot + ISO + Ubuntu Root");
      this.addInfoCard(var1, "Install safety", "Before boot, U-AVF verifies GPT and partition geometry. Subiquity is configured to format Ubuntu Root (vda3), not EFI or ISO.");
      if (var2.isFile()) {
         boolean var3 = this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false);
         this.addPrimaryButton(var1, var3 ? "Open existing Ubuntu workspace" : "Continue existing installation", () -> {
            this.selectProfile(MainActivity.LaunchProfile.LINUX);
            if (var3) {
               this.renderProductScreen(MainActivity.ProductScreen.LINUX_HOME);
            } else {
               (new Thread(this::resumeInstalledUbuntuOrOfferRepair, "U-AVF-create-resume")).start();
            }

         });
         this.addSecondaryButton(var1, "Reinstall Ubuntu Root…", this::confirmUbuntuReinstallProfile);
      } else {
         this.addPrimaryButton(var1, "Create and install workspace", () -> this.confirmUbuntuInstallProfile(false));
      }

      this.addFooterAction(var1, "← Back", () -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_STORAGE));
   }

   private void renderInstallRecovery(LinearLayout var1) {
      this.addTitle(var1, "Installation & recovery", "Manage the persistent Ubuntu install and verified source image.");
      File var2 = this.persistentUbuntuDiskFile();
      boolean var3 = var2.isFile() && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false);
      String var10003 = var3 ? "Installed" : (var2.isFile() ? "Setup incomplete" : "Not installed");
      this.addInfoCard(var1, "Ubuntu 24.04.5 LTS", var10003 + (var2.isFile() ? " · " + formatBytes(var2.length()) : ""));
      this.addInfoCard(var1, "Verified Ubuntu ISO", this.isoPresent() ? "Ubuntu 24.04.5 ARM64 · verified · retained" : "No verified ISO is currently available");
      this.addSecondaryButton(var1, "Setup status & protected storage", () -> this.renderProductScreen(ProductScreen.INSTALL_STATUS));
      if (var2.isFile()) {
         this.addSecondaryButton(var1, "Reinstall Ubuntu", this::confirmUbuntuReinstallProfile);
         this.addSecondaryButton(var1, "Temporary live session", () -> {
            if (this.isoPresent()) {
               this.launchSelectedProfile();
            } else {
               this.renderProductScreen(MainActivity.ProductScreen.CREATE_IMAGE);
            }

         });
         Button var4 = this.button("Delete Ubuntu workspace…");
         var4.setTextColor(-23388);
         var4.setBackground(this.panelBackground(-13295837));
         var4.setOnClickListener((var1x) -> this.confirmDeleteUbuntuWorkspace());
         this.addBounded(var1, var4, 12, this.dp(50));
         this.addSmallNote(var1, "Deleting removes only the persistent Ubuntu workspace disk. The downloaded official ISO is kept.");
      } else {
         this.addPrimaryButton(var1, "Create Ubuntu workspace", () -> this.renderProductScreen(MainActivity.ProductScreen.CREATE_OS));
      }

      this.addFooterAction(var1, "← Workspace", () -> this.renderProductScreen(MainActivity.ProductScreen.LINUX_HOME));
   }

   private void showFigmaWorkspaceScreen(ProductScreen screen) {
      this.productScreen=screen;this.productBody=null;this.closeSessionDrawerImmediately();
      this.settingsPanel.setVisibility(View.GONE);this.logPanel.setVisibility(View.GONE);this.status.setVisibility(View.GONE);
      this.menuButton.setVisibility(View.GONE);this.homePanel.setVisibility(View.VISIBLE);this.homePanel.removeAllViews();
      FigmaCanvas canvas;
      if(screen==ProductScreen.LINUX_HOME) {
         this.wizardSettingsActive=false;
         File disk=this.persistentUbuntuDiskFile();boolean exists=disk.isFile();
         boolean ready=exists && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false);
         boolean running=this.isManagedVmRunning();WorkspaceConfig config=this.workspaceConfig();
         String state=running?"Running":ready?"Stopped":"Setup required";
         String resources=(config.oneCpu?"1 vCPU":"Match-host vCPU")+" · "+config.ramGiB+" GiB RAM · "
               +(exists?formatBytes(disk.length()):"No disk")+" · "+this.configuredDisplayLabel();
         canvas=new FigmaWorkspaceHome(this,state,ready?"Installed · ready":"Ubuntu workspace",resources,
               running?"Return to Ubuntu":ready?"Launch Ubuntu":exists?"Continue setup":"Create workspace",() -> {
                  if(this.isManagedVmRunning()) {this.returnToRunningVm();return;}
                  if(ready)new Thread(()->this.startPersistentUbuntu(true),"U-AVF-persistent-ubuntu").start();
                  else if(exists)new Thread(this::resumeInstalledUbuntuOrOfferRepair,"U-AVF-resume-installed-ubuntu").start();
                  else this.renderProductScreen(this.isoPresent()?ProductScreen.CREATE_STORAGE:ProductScreen.CREATE_OS);
               },()->this.renderProductScreen(ProductScreen.MODE_SELECTION),()->this.renderProductScreen(ProductScreen.VM_SETTINGS),
               ()->this.renderProductScreen(ProductScreen.DIAGNOSTICS),()->this.renderProductScreen(ProductScreen.INSTALL_RECOVERY));
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
               },()->this.renderProductScreen(review?ProductScreen.CREATE_STORAGE:ProductScreen.CREATE_IMAGE),()-> {
                  this.wizardSettingsActive=true;this.workspaceSettingsTab="Performance";this.renderProductScreen(ProductScreen.VM_SETTINGS);
               },this.workspaceRootSizeLabel(),disk.isFile()?null:this::chooseNewWorkspaceRootSize);
      } else {
         boolean image=screen!=ProductScreen.CREATE_OS;
         canvas=new FigmaCreateView(this,image,this.isoPresent(),this.createWorkspaceOs,selected -> {
            this.createWorkspaceOs=selected;this.renderProductScreen(ProductScreen.CREATE_OS);
         },()-> {
            if(image) {if(this.isoPresent())this.renderProductScreen(ProductScreen.CREATE_STORAGE);return;}
            if("windows".equals(this.createWorkspaceOs)) {this.selectProfile(LaunchProfile.WINDOWS);this.renderProductScreen(ProductScreen.WINDOWS_HOME);}
            else this.renderProductScreen(ProductScreen.CREATE_IMAGE);
         },()->this.renderProductScreen(image?ProductScreen.CREATE_OS:ProductScreen.MODE_SELECTION),
         this::openOfficialUbuntuDownloadPage,
         () -> {if(this.isManagedVmRunning()) {this.show("Stop Ubuntu before changing boot media.");return;}this.openUbuntuIsoPicker();});
      }
      this.productStatus=canvas.notice;this.productStatus.setText("");
      this.homePanel.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
      if(android.animation.ValueAnimator.areAnimatorsEnabled()) {canvas.setTranslationY(this.dp(12));canvas.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();}
   }

   private void beginCompatibilityCheck() {
      if(this.compatibilityRunning)return;
      this.compatibilityRunning=true;this.compatibilityPassed=false;this.readinessCompleted=0;
      Arrays.fill(this.readinessResults,"Waiting");
      this.renderProductScreen(ProductScreen.COMPATIBILITY);
      new Thread(this::checkDeviceReadiness,"U-AVF-readiness-check").start();
   }

   private void showFigmaOnboarding(ProductScreen screen) {
      boolean pageChanged=this.productScreen!=screen;
      this.productScreen=screen;this.productBody=null;this.closeSessionDrawerImmediately();
      this.settingsPanel.setVisibility(View.GONE);this.logPanel.setVisibility(View.GONE);
      this.status.setVisibility(View.GONE);this.menuButton.setVisibility(View.GONE);
      this.homePanel.setVisibility(View.VISIBLE);this.homePanel.removeAllViews();
      FigmaCanvas canvas;
      if(screen==ProductScreen.ONBOARDING) {
         canvas=new FigmaWelcomeView(this,this.getPreferences(0).getBoolean("share_compatibility_report",false),
            value->this.getPreferences(0).edit().putBoolean("share_compatibility_report",value).apply(),this::beginCompatibilityCheck,
            ()->this.showLegalDocument("PRIVACY.md"));
      } else {
         boolean runtimeProof=Build.FINGERPRINT.equals(this.getPreferences(0).getString("runtime_verified_build",""));
         String boot=runtimeProof?"Verified · last boot":"Not tested";
         String vsock=this.guestControl!=null && this.guestControl.connected()?"Verified · connected":runtimeProof?"Verified · last boot":"Not tested";
         String graphics=this.graphicsEvidence();
         canvas=new FigmaCompatibilityView(this,this.compatibilityRunning,this.compatibilityPassed,
            this.readinessCompleted,this.readinessResults.clone(),boot,vsock,graphics,
            this::beginCompatibilityCheck,()->this.renderProductScreen(ProductScreen.MODE_SELECTION),
            ()->this.renderProductScreen(ProductScreen.ONBOARDING),this::shareCompatibilityReport,this::skipCompatibilityCheck);
      }
      this.productStatus=canvas.notice;this.homePanel.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
      if(pageChanged && android.animation.ValueAnimator.areAnimatorsEnabled()) {
         canvas.setTranslationY(this.dp(12));canvas.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
      }
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

   private void shareCompatibilityReport() {
      if(!this.getPreferences(0).getBoolean("share_compatibility_report",false)) {this.show("Enable report sharing on the Welcome screen first.");return;}
      this.uploadCompatibilityReport();
   }

   private volatile boolean compatibilityUploading;
   private volatile String compatibilityUploadStatus="";
   private void uploadCompatibilityReport() {
      if(this.compatibilityUploading)return;
      this.compatibilityUploading=true;
      this.compatibilityUploadStatus="Sending compatibility report…";
      if(this.productStatus!=null)this.productStatus.setText(this.compatibilityUploadStatus);
      final String[] results=this.readinessResults.clone();
      final boolean proof=Build.FINGERPRINT.equals(this.getPreferences(0).getString("runtime_verified_build",""));
      final boolean vsock=this.guestControl!=null && this.guestControl.connected();
      final boolean hardware=this.graphicsEvidence().startsWith("Verified");
      new Thread(()->{
         try {
            org.json.JSONObject fields=new org.json.JSONObject().put("schemaVersion",new org.json.JSONObject().put("integerValue","1"))
               .put("consent",new org.json.JSONObject().put("booleanValue",true))
               .put("appVersion",CompatibilityUpload.value(this.releaseVersionLabel()))
               .put("androidApi",new org.json.JSONObject().put("integerValue",Integer.toString(VERSION.SDK_INT)))
               .put("androidRelease",CompatibilityUpload.value(boundedReportText(VERSION.RELEASE,32)))
               .put("manufacturer",CompatibilityUpload.value(boundedReportText(Build.MANUFACTURER,48)))
               .put("model",CompatibilityUpload.value(boundedReportText(Build.MODEL,64)));
            org.json.JSONObject checks=new org.json.JSONObject();String[] keys={"arm64","avf","permissions","platform"};
            for(int i=0;i<keys.length;i++)checks.put(keys[i],CompatibilityUpload.value(results[i].startsWith("Passed")?"pass":results[i].startsWith("Failed")?"fail":"not_tested"));
            // Keep the existing upload schema without treating workspace prerequisites as device tests.
            checks.put("storage",CompatibilityUpload.value("not_tested"));
            checks.put("iso",CompatibilityUpload.value("not_tested"));
            fields.put("results",CompatibilityUpload.map(checks));
            fields.put("runtime",CompatibilityUpload.map(new org.json.JSONObject()
               .put("boot",CompatibilityUpload.value(proof?"pass":"not_tested"))
               .put("vsock",CompatibilityUpload.value(vsock||proof?"pass":"not_tested"))
               .put("openGL",CompatibilityUpload.value(hardware?"pass":"not_tested"))));
            this.compatibilityUploadStatus=CompatibilityUpload.send(this,this.getPreferences(0),fields);
         } catch(Exception error) {this.compatibilityUploadStatus="Report not sent · "+error.getMessage()+". Tap Share report to retry.";}
         finally {this.compatibilityUploading=false;}
         this.runOnUiThread(()->{
            if(this.productScreen==ProductScreen.COMPATIBILITY && this.productStatus!=null)this.productStatus.setText(this.compatibilityUploadStatus);
            android.util.Log.i("U-AVF",this.compatibilityUploadStatus);
         });
      },"U-AVF-compatibility-upload").start();
   }

   private static String boundedReportText(String value,int max) {return value==null?"":value.substring(0,Math.min(max,value.length()));}
   private void shareCompatibilityReportManually() {
      StringBuilder report=new StringBuilder("U-AVF compatibility report\n");
      report.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
         .append("\nAndroid: ").append(VERSION.RELEASE).append(" / API ").append(VERSION.SDK_INT).append('\n');
      String[] names={"ARM64","AVF","VM permissions","Platform integrity"};
      for(int i=0;i<names.length;i++)report.append(names[i]).append(": ").append(this.readinessResults[i]).append('\n');
      report.append("OpenGL: ").append(this.graphicsEvidence()).append('\n');
      report.append("VM currently running: ").append(this.isManagedVmRunning()).append('\n');
      report.append("Control vsock connected: ").append(this.guestControl!=null && this.guestControl.connected()).append('\n');
      Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain")
         .putExtra(Intent.EXTRA_SUBJECT,"U-AVF compatibility report").putExtra(Intent.EXTRA_TEXT,report.toString());
      this.startActivity(Intent.createChooser(send,"Review and share with developers"));
   }

   private void showFigmaSetupStatus() {
      this.productScreen=ProductScreen.INSTALL_STATUS;this.productBody=null;
      this.closeSessionDrawerImmediately();this.settingsPanel.setVisibility(View.GONE);this.logPanel.setVisibility(View.GONE);
      this.status.setVisibility(View.GONE);this.menuButton.setVisibility(View.GONE);this.homePanel.setVisibility(View.VISIBLE);this.homePanel.removeAllViews();
      boolean running=this.isManagedVmRunning();
      boolean installed=this.persistentUbuntuDiskFile().isFile() && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false);
      FigmaSetupView setup=new FigmaSetupView(this,installed,running,this.isoPresent(),() -> {
         if(this.isManagedVmRunning())this.returnToRunningVm();
         else this.renderProductScreen(installed?ProductScreen.LINUX_HOME:ProductScreen.CREATE_IMAGE);
      },() -> this.renderProductScreen(ProductScreen.INSTALL_RECOVERY),() -> this.renderProductScreen(ProductScreen.DIAGNOSTICS));
      this.homePanel.addView(setup,new FrameLayout.LayoutParams(-1,-1));
      new Thread(() -> {
         File report=new File(this.getExternalFilesDir(null),UBUNTU_PERSISTENT_REPORT_NAME);String value=readTail(report,16384);
         String result=value.contains("DISK_GPT_AUDIT=PASS")?"✓ Last launch: partition geometry verified. ":"Partition audit not yet recorded. ";
         result+=installed?"✓ Installed runtime confirmed.":"Installed runtime not confirmed.";
         final String observed=result;
         this.runOnUiThread(() -> {if(this.productScreen==ProductScreen.INSTALL_STATUS && setup.getParent()==this.homePanel)setup.recordedState(observed);});
      },"U-AVF-figma-setup-status").start();
   }

   /** Legacy summary retained until native Figma screen regression passes. */
   private void renderInstallationStatus(LinearLayout body) {
      boolean running=this.isManagedVmRunning();
      boolean installed=this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false)
            && this.persistentUbuntuDiskFile().isFile();
      this.addTitle(body,"Ubuntu Setup",installed?"Installed runtime confirmed":"Installation mode · protected workspace storage");
      this.addSetupStatusRow(body,"Install target","Ubuntu Root · vda3","Reinstall target",false);
      this.addSetupStatusRow(body,"Platform / EFI","U-AVF · vda1","Preserved",true);
      this.addSetupStatusRow(body,"Official Ubuntu ISO","Source · vda2",this.isoPresent()?"Verified":"Not verified",this.isoPresent());
      this.addSmallNote(body,"Setup formats Ubuntu Root only. Do not change its prepared storage layout. The official ISO is never modified.");
      LinearLayout evidence=new LinearLayout(this);evidence.setOrientation(1);
      this.addBounded(body,evidence,12,-2);
      TextView pending=this.text("Reading the last setup report…",13,0xff91a2b4);evidence.addView(pending);
      // Disk/report reads stay off the UI thread. Historical evidence is labelled as such.
      new Thread(() -> {
         File report=new File(this.getExternalFilesDir(null),UBUNTU_PERSISTENT_REPORT_NAME);
         String value=readTail(report,16384);long updated=report.lastModified();
         this.runOnUiThread(() -> {
            if(this.productScreen!=ProductScreen.INSTALL_STATUS || this.productBody!=body)return;
            evidence.removeAllViews();
            this.addSetupStatusRow(evidence,"Partition audit","Last launch",value.contains("DISK_GPT_AUDIT=PASS")?"✓ Verified":"Unobserved",value.contains("DISK_GPT_AUDIT=PASS"));
            String request=value.contains("INSTALLER_AUTOSTART=ALLOWLISTED_REQUEST_SENT")?"Request sent":value.contains("INSTALLER_AUTOSTART=FAILED")?"Request failed":"Not recorded";
            this.addSmallNote(evidence,"Installer: "+request+" · Runtime: "+(installed?"✓ Installed HELLO confirmed":"Not confirmed")+". Installer window visibility is not independently verified.");
            this.addSmallNote(evidence,updated>0?"Report saved: "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new java.util.Date(updated))
                  +". These are recorded checks, not a live progress estimate.":"No setup report yet. Ubuntu Setup remains the real installation interface.");
         });
      },"U-AVF-setup-summary").start();
      if(running)this.addPrimaryButton(body,"Continue in running Ubuntu / Setup",this::returnToRunningVm);
      else if(installed)this.addPrimaryButton(body,"Open installed workspace",() -> this.renderProductScreen(ProductScreen.LINUX_HOME));
      else this.addPrimaryButton(body,"Prepare Ubuntu Setup",() -> this.renderProductScreen(ProductScreen.CREATE_IMAGE));
      this.addSecondaryButton(body,"Diagnostics & report",() -> this.renderProductScreen(ProductScreen.DIAGNOSTICS));
      this.addFooterAction(body,"← Installation & recovery",() -> this.renderProductScreen(ProductScreen.INSTALL_RECOVERY));
   }

   private void addSetupStatusRow(LinearLayout parent,String label,String detail,String state,boolean positive) {
      LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
      row.setPadding(this.dp(18),this.dp(10),this.dp(18),this.dp(10));row.setBackground(this.panelBackground(0xff0c121a));
      TextView name=this.text(label,14,0xffabbacc),description=this.text(detail,14,0xffabbacc),statusLabel=this.text(state,13,positive?0xff38d185:0xffabbacc);
      name.setMaxLines(2);description.setMaxLines(2);statusLabel.setMaxLines(2);statusLabel.setGravity(android.view.Gravity.END);
      row.addView(name,new LinearLayout.LayoutParams(0,-2,1.2f));row.addView(description,new LinearLayout.LayoutParams(0,-2,1.1f));row.addView(statusLabel,new LinearLayout.LayoutParams(0,-2,1));
      this.addBounded(parent,row,10,this.dp(62));
   }

   private void renderInputSettings(LinearLayout var1) {
      this.addTitle(var1, "Input & gestures", "Controls supported by the current Android-to-guest input bridge.");
      this.addInfoCard(var1, "Touch", "Tap, drag and touch scrolling are sent through the existing guest input bridge.");
      this.addInfoCard(var1, "Keyboard", "Hardware keyboard keys and common combinations are forwarded to Ubuntu while the VM is in front.");
      this.addSystemKeyboardCaptureControls(var1);
      this.addInfoCard(var1, "Mouse capture", this.frameSurface != null && this.frameSurface.isPointerCaptured() ? "Captured · movement is relative and stays inside the VM until released" : "Not captured · mouse movement follows the pointer inside the viewport");
      if (this.frameVisible) {
         this.addSecondaryButton(var1, this.frameSurface.isPointerCaptured() ? "Release mouse" : "Capture mouse", this::togglePointerCapture);
      }

      this.addInfoCard(var1, "Runtime controls shortcut", "Ctrl + Alt + Shift + Esc opens or closes the controls panel and releases captured mouse input.");
      this.addSmallNote(var1, "Pinch-to-zoom and three-finger workspace gestures are not advertised until the guest bridge implements them.");
      this.addFooterAction(var1, "← Workspace settings", () -> this.renderProductScreen(MainActivity.ProductScreen.VM_SETTINGS));
   }

   private void addWizardProgress(LinearLayout var1, int var2) {
      LinearLayout var3 = new LinearLayout(this);
      var3.setGravity(16);
      String[] var4 = new String[]{"System", "Image", "Storage", "Review"};

      for(int var5 = 0; var5 < var4.length; ++var5) {
         TextView var6 = this.text(var5 + 1 + "  " + var4[var5], 12, var5 + 1 == var2 ? -952298 : (var5 + 1 < var2 ? -9317724 : -7761766));
         var6.setGravity(17);
         var6.setBackground(this.panelBackground(var5 + 1 == var2 ? -13622243 : -15263460));
         LinearLayout.LayoutParams var7 = new LinearLayout.LayoutParams(0, this.dp(38), 1.0F);
         if (var5 > 0) {
            var7.leftMargin = this.dp(6);
         }

         var3.addView(var6, var7);
      }

      this.addBounded(var1, var3, 16, this.dp(38));
   }

   private void addWorkspaceHeroCard(LinearLayout var1, String var2, String var3, String var4, String var5, Runnable var6) {
      LinearLayout var7 = new LinearLayout(this);
      var7.setGravity(16);
      var7.setPadding(this.dp(18), this.dp(16), this.dp(18), this.dp(16));
      var7.setBackground(this.panelBackground(0xf7101923));
      ImageView var8 = new ImageView(this);
      var8.setImageResource(2130771970);
      var8.setScaleType(ScaleType.FIT_CENTER);
      var7.addView(var8, new LinearLayout.LayoutParams(this.dp(68), this.dp(68)));
      LinearLayout var9 = new LinearLayout(this);
      var9.setOrientation(1);
      var9.setPadding(this.dp(18), 0, this.dp(12), 0);
      TextView var10 = this.text(var2, 20, -1);
      var10.setTypeface(this.productTypeface(), Typeface.BOLD);
      var9.addView(var10);
      var9.addView(this.text(var3, 12, -8858718));
      TextView var11 = this.text(var4, 12, -6184026);
      var11.setPadding(0, this.dp(4), 0, 0);
      var9.addView(var11);
      var7.addView(var9, new LinearLayout.LayoutParams(0, -2, 1.0F));
      Button var12 = this.button(var5);
      var12.setTextSize(14.0F);
      var12.setTypeface(this.productTypeface());
      var12.setTextColor(0xff05140f);
      var12.setBackground(this.panelBackground(0xf538d185));
      var12.setOnClickListener((var1x) -> var6.run());
      // Dashboard cards open management, never implicitly boot a guest.
      if ("Open workspace".equals(var5)) {
         var7.setOnClickListener((view) -> var6.run());
         var7.setContentDescription(var2 + ", open workspace management");
      }
      var7.addView(var12, new LinearLayout.LayoutParams(this.dp(190), this.dp(52)));
      this.addBounded(var1, var7, 20, this.dp(116));
   }

   private void addActionTile(LinearLayout var1, String var2, String var3, Runnable var4) {
      LinearLayout var5 = new LinearLayout(this);
      var5.setOrientation(1);
      var5.setGravity(16);
      var5.setPadding(this.dp(14), this.dp(10), this.dp(12), this.dp(10));
      var5.setBackground(this.panelBackground(-15263461));
      TextView var6 = this.text(var2, 15, -1);
      var6.setTypeface(Typeface.DEFAULT, 1);
      var5.addView(var6);
      var5.addView(this.text(var3, 11, -6578784));
      var5.setOnClickListener((var1x) -> var4.run());
      LinearLayout.LayoutParams var7 = new LinearLayout.LayoutParams(0, -1, 1.0F);
      if (var1.getChildCount() > 0) {
         var7.leftMargin = this.dp(10);
      }

      var1.addView(var5, var7);
   }

   private void resumeInstalledUbuntuOrOfferRepair() {
      try {
         File var1 = this.persistentUbuntuDiskFile();
         boolean var2 = PersistentUbuntuDisk.audit(var1, 3967463424L, 34359738368L);
         if (var2 && !PersistentUbuntuDisk.hasInstalledBootCandidate(var1)) {
            String recovery = PersistentUbuntuDisk.recoverInstalledEsp(var1, 3967463424L,
                  UBUNTU_INSTALL_PLATFORM_ESP_SHA256,
                  new File(this.getFilesDir(), "linux-persistent-payload/esp-backups"));
            Log.i("U-AVF", "INSTALLED_ESP_RECOVERY=" + recovery);
         }
         boolean var3 = var2 && PersistentUbuntuDisk.hasInstalledBootCandidate(var1);
         if (var3) {
            this.show("Found the completed Ubuntu boot handoff; starting the persistent installation.");
            this.startPersistentUbuntu(true, true);
            return;
         }

         this.runOnUiThread(() -> this.confirmUbuntuInstallProfile(true));
      } catch (Throwable var4) {
         this.show("Could not verify the installed Ubuntu handoff: " + rootMessage(var4));
      }

   }

   private void confirmUbuntuInstallProfile(boolean var1) {
      String var2 = var1 ? "U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. Back up any files on the existing Ubuntu system first. Do not change the storage layout in Ubuntu Setup." : "U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. You will choose your language, keyboard and account in Ubuntu Setup; do not change its storage layout.";
      ProductDialog.show(this,var1 ? "Repair or reinstall Ubuntu?" : "Install Ubuntu safely",var2,"Continue to installer",var1,() -> (new Thread(() -> this.startPersistentUbuntu(var1), "U-AVF-persistent-ubuntu")).start());
   }

   private void confirmUbuntuReinstallProfile() {
      ProductDialog.show(this,"Reinstall Ubuntu?","This replaces the Ubuntu system on its dedicated vda3 partition. Personal files and settings on that Ubuntu partition will be erased. The official installer image on vda2 and U-AVF boot partition on vda1 are preserved. The app verifies the disk layout before it starts.","Reinstall Ubuntu",true,() -> (new Thread(() -> this.startPersistentUbuntu(true, false, true), "U-AVF-ubuntu-reinstall")).start());
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

   private void startPersistentUbuntu(boolean var1) {
      this.startPersistentUbuntu(var1, false);
   }

   private void startPersistentUbuntu(boolean var1, boolean var2) {
      this.startPersistentUbuntu(var1, var2, false);
   }

   private void startPersistentUbuntu(boolean var1, boolean var2, boolean var3) {
      if (!this.vmMayBeRunning && !this.isManagedVmRunning()) {
         if (!this.lifecycle.beginStart()) return;
         this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
         this.resetGuestFrameSurface();
         File var4 = this.persistentUbuntuDiskFile();
         File var5 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
         File var6 = new File(this.getFilesDir(), "linux-persistent-payload");

         try {
            String var7 = "NOT_REQUIRED";
            if (!var5.isFile() || var5.length() != 3967463424L) {
               throw new IllegalStateException("Choose the verified Ubuntu 24.04.5 ARM64 ISO first.");
            }

            if (var1) {
               File growthJournal=new File(var4.getParentFile(),"root-growth.intent");
               if(growthJournal.isFile())PersistentUbuntuDisk.completeGrow(var4,3967463424L,growthJournal);
               if (!PersistentUbuntuDisk.audit(var4, 3967463424L, 34359738368L)) {
                  throw new SecurityException("Persistent Ubuntu disk layout failed its GPT check; it was left untouched.");
               }

               if (var3) {
                  this.getPreferences(0).edit().putBoolean("ubuntu_growth_helper_verified",false).apply();
                  this.getPreferences(0).edit().putBoolean("ubuntu_installed_runtime_ready", false).putBoolean("ubuntu_install_handoff_pending", false).putBoolean("ubuntu_install_handoff_attempted", false).apply();
               }

               String var8 = this.getPreferences(0).getString("ubuntu_install_platform_prefix_sha256", "");
               if (var3 || !UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256.equalsIgnoreCase(var8)) {
                  if (!var3 && (var2 || this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false)
                        || PersistentUbuntuDisk.hasInstalledBootCandidate(var4))) {
                     var7 = "SKIPPED_INSTALLED_SYSTEM";
                  } else {
                     if (!var6.isDirectory() && !var6.mkdirs()) {
                        throw new IllegalStateException("Cannot create Ubuntu platform update staging directory.");
                     }

                     File var9 = this.copyVerifiedAsset(UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET, new File(var6, UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET), 134217728L, UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256);
                     var7 = PersistentUbuntuDisk.updatePlatformEsp(var4, var9, 3967463424L, UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256, UBUNTU_INSTALL_PLATFORM_ESP_SHA256, new File(var6, "esp-backups"));
                     this.getPreferences(0).edit().putString("ubuntu_install_platform_prefix_sha256", UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256).apply();
                     Log.i("U-AVF", "PLATFORM_ESP_UPDATE=" + var7);
                  }
               }
            } else {
               if (var4.exists()) {
                  throw new IllegalStateException("An installation disk already exists; use Start installed Ubuntu.");
               }

               if (this.getFilesDir().getUsableSpace() < PersistentUbuntuDisk.expectedDiskBytes(3967463424L,this.newWorkspaceRootBytes())+2L*1024*1024*1024) {
                  throw new IllegalStateException("Not enough Android storage for the selected Ubuntu disk and 2 GiB reserve.");
               }

               if (!var6.isDirectory() && !var6.mkdirs()) {
                  throw new IllegalStateException("Cannot create Ubuntu install staging directory.");
               }

               File var23 = this.copyVerifiedAsset(UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET, new File(var6, UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET), 134217728L, UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256);
               PersistentUbuntuDisk.create(var23, var5, var4, UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256, "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14",this.newWorkspaceRootBytes());
               this.getPreferences(0).edit().putBoolean("ubuntu_growth_helper_verified",false).apply();
               this.getPreferences(0).edit().putBoolean("ubuntu_installed_runtime_ready", false).putString("ubuntu_install_platform_prefix_sha256", UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256).apply();
            }

            if (!var6.isDirectory() && !var6.mkdirs()) {
               throw new IllegalStateException("Cannot create Ubuntu install staging directory.");
            }

            File var24 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var6, "u-boot-wrapper-v24.Image"), -1L);
            this.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true).putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).apply();
            Object var25 = this.buildGenericUbuntuConfig(var24, var4, (File)null, (File)null, false, "winavf-frame-bridge-test-ubuntu-24045", false, true);
            Object var10 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

            try {
               Object var11 = var10.getClass().getMethod("get", String.class).invoke(var10, "winavf-frame-bridge-test-ubuntu-24045");
               if (var11 != null) {
                  var10.getClass().getMethod("delete", String.class).invoke(var10, "winavf-frame-bridge-test-ubuntu-24045");
               }
            } catch (Exception var21) {
            }

            Object var26 = var10.getClass().getMethod("create", String.class, var25.getClass()).invoke(var10, "winavf-frame-bridge-test-ubuntu-24045", var25);
            this.attachCallback(var26);
            this.ubuntuGuestRuntimeRevision = 0;
            this.userRequestedVmStop = false;
            InputStream var12 = (InputStream)var26.getClass().getMethod("getConsoleOutput").invoke(var26);
            this.startConsoleReader(var12, "ubuntu-persistent-serial.log");
            var26.getClass().getMethod("run").invoke(var26);
            this.vmMayBeRunning = true;
            this.activeFrameVmName = "winavf-frame-bridge-test-ubuntu-24045";
            boolean var13 = !var3 && (var2 || this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false));
            this.getPreferences(0).edit().putBoolean("linux_current_boot_installed",var13)
                  .putBoolean("linux_current_boot_encoded",true).apply();
            this.startUbuntuVsockHelloProbe(var26, var13);
            this.startUbuntuFrameBridge(var26);
            boolean var14 = var3 || !var2 && !this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false);
            if (var14) {
               this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", true).putBoolean("ubuntu_install_handoff_attempted", false).apply();
            }

            File var15 = new File(this.getExternalFilesDir((String)null), "ubuntu-install-runtime-report.txt");
            PrintWriter var16 = new PrintWriter(new FileOutputStream(var15, false));

            try {
               var16.println("PROFILE=" + (var14 ? "UBUNTU_INSTALL_OR_REPAIR" : (var2 ? "UBUNTU_POST_INSTALL_HANDOFF" : "UBUNTU_PERSISTENT_BOOT")));
               var16.println("FORCE_INSTALL_PROFILE=" + var3);
               var16.println("ISO_SHA256=2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14");
               var16.println("ONE_VIRTIO_BLOCK_DISK=PASS");
               var16.println("ESP_PLUS_UNMODIFIED_ISO_PLUS_32G_INSTALL_TARGET=PASS");
               var16.println("INSTALLER_FULLSCREEN_REQUEST=" + (var14 ? "PENDING" : "NOT_REQUESTED"));
               var16.println("PLATFORM_ESP_UPDATE=" + var7);
               var16.println("INSTALLER_WINDOW_VERIFIED=NOT_AVAILABLE_FROM_CURRENT_AVF_API");
               var16.println("DISK_BYTES=" + var4.length());
               var16.println("DISK_GPT_AUDIT=PASS");
               var16.println("VM_LAUNCH=PASS");
            } catch (Throwable var20) {
               try {
                  var16.close();
               } catch (Throwable var19) {
                  var20.addSuppressed(var19);
               }

               throw var20;
            }

            var16.close();
            if (var14) {
               this.scheduleUbuntuInstallerAutostart();
            }

            this.runOnUiThread(() -> {
               this.homePanel.setVisibility(8);
               this.status.setVisibility(0);
               this.status.setText(var14 ? "Starting Ubuntu Setup…" : "Starting installed Ubuntu…");
            });
            this.show(var14 ? "Starting Ubuntu install / repair profile; Setup will open only after the Live profile is confirmed." : "Starting the installed Ubuntu system.");
         } catch (Throwable var22) {
            this.lifecycle.failStart(rootMessage(var22));
            this.transition.failure(rootMessage(var22),android.os.SystemClock.elapsedRealtime());
            this.show("Ubuntu installation could not start: " + rootMessage(var22));
         }

      } else {
         this.show("Stop the running VM before opening the Ubuntu installer or installed system.");
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

            while(this.vmMayBeRunning && this.activeFrameInput == null && System.currentTimeMillis() < var1) {
               try {
                  Thread.sleep(250L);
               } catch (InterruptedException var6) {
                  Thread.currentThread().interrupt();
                  return;
               }
            }

            OutputStream var3 = this.activeFrameInput;
            if (this.vmMayBeRunning && var3 != null) {
               try {
                  Thread.sleep(2500L);
                  this.sendInstallerLaunch(var3);
                  this.appendUbuntuInstallerAutostartReport("INSTALLER_AUTOSTART=ALLOWLISTED_REQUEST_SENT");
                  Thread.sleep(8000L);
                  this.sendInstallerKey(var3, 65513, true);
                  this.sendInstallerKey(var3, 65479, true);
                  this.sendInstallerKey(var3, 65479, false);
                  this.sendInstallerKey(var3, 65513, false);
                  Thread.sleep(250L);
                  this.sendInstallerKey(var3, 65480, true);
                  this.sendInstallerKey(var3, 65480, false);
                  this.frameInputWriter.submit(() -> {
                  }).get(5L, TimeUnit.SECONDS);
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

   private void sendInstallerLaunch(OutputStream var1) throws Exception {
      byte[] var2 = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{85, 73, 78, 49}).put((byte)5).put((byte)1).putShort((short)0).putInt(1).putInt(0).array();
      this.frameInputWriter.submit(() -> {
         synchronized(var1) {
            if (this.activeFrameInput == var1) {
               try {
                  var1.write(var2);
                  var1.flush();
               } catch (IOException var6) {
                  throw new UncheckedIOException(var6);
               }

            } else {
               throw new IllegalStateException("Installer input channel is no longer active");
            }
         }
      }).get(5L, TimeUnit.SECONDS);
   }

   private void sendInstallerKey(OutputStream var1, int var2, boolean var3) throws Exception {
      byte[] var4 = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).put(new byte[]{85, 73, 78, 49}).put((byte)3).put((byte)(var3 ? 1 : 0)).putShort((short)0).putInt(var2).putInt(0).array();
      this.frameInputWriter.submit(() -> {
         synchronized(var1) {
            if (this.activeFrameInput == var1) {
               try {
                  var1.write(var4);
                  var1.flush();
               } catch (IOException var6) {
                  throw new UncheckedIOException(var6);
               }
            }

         }
      }).get(5L, TimeUnit.SECONDS);
   }

   private void sendInstallerText(OutputStream var1, String var2) throws Exception {
      for(int var3 = 0; var3 < var2.length(); ++var3) {
         char var4 = var2.charAt(var3);
         boolean var5 = Character.isUpperCase(var4) || var4 == '_';
         char var6 = var4;
         if (Character.isUpperCase(var4)) {
            var6 = Character.toLowerCase(var4);
         } else if (var4 == '_') {
            var6 = '-';
         }

         if (var5) {
            this.sendInstallerKey(var1, 65505, true);
         }

         this.sendInstallerKey(var1, var6, true);
         this.sendInstallerKey(var1, var6, false);
         if (var5) {
            this.sendInstallerKey(var1, 65505, false);
         }

         Thread.sleep(12L);
      }

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

   private void renderWindowsHome(LinearLayout var1) {
      this.addTitle(var1, "Windows workspace", "Windows 11 ARM · experimental");
      if (this.frameVisible) {
         this.addSecondaryButton(var1, "Return to running VM", this::showRunningDesktop);
      }

      this.addInfoCard(var1, "Status", "Boots to the Windows loader on this device; post-boot Windows setup is not yet confirmed.");
      this.addInfoCard(var1, "Media", "Uses the existing verified Windows boot image. This does not modify the source image.");
      this.addPrimaryButton(var1, "Start Windows", this::launchSelectedProfile);
      this.addSecondaryButton(var1, "Switch workspace", () -> this.renderProductScreen(MainActivity.ProductScreen.MODE_SELECTION));
      this.addFooterAction(var1, "Virtual machine settings", () -> this.renderProductScreen(MainActivity.ProductScreen.VM_SETTINGS));
      this.addFooterAction(var1, "Diagnostics", () -> this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS));
   }

   private void renderVmSettings(LinearLayout var1) {
      this.addTitle(var1, "Workspace settings", this.selectedProfile == MainActivity.LaunchProfile.LINUX ? "Ubuntu workspace" : "Windows ARM workspace · experimental");
      if (this.frameVisible) {
         this.addSecondaryButton(var1, "Return to running session", this::showRunningDesktop);
      }

      LinearLayout var2 = new LinearLayout(this);
      var2.setGravity(48);
      var2.setOrientation(0);
      var2.setPadding(this.dp(10), this.dp(10), this.dp(10), this.dp(10));
      var2.setBackground(this.panelBackground(-15395304));
      LinearLayout var3 = new LinearLayout(this);
      var3.setOrientation(1);
      String[] var4 = new String[]{"General", "Performance", "Display", "Input", "Audio", "Network", "Storage", "Advanced"};

      for(String var8 : var4) {
         Button var9 = this.button(var8.equals("Input") ? "Input & gestures" : var8);
         var9.setGravity(19);
         var9.setTextSize(12.0F);
         var9.setTextColor(this.isLightAppearance() ? (this.workspaceSettingsTab.equals(var8) ? -14669773 : -10918539) : (this.workspaceSettingsTab.equals(var8) ? -657933 : -4999752));
         GradientDrawable tabBackground=new GradientDrawable();
         tabBackground.setCornerRadius(this.dp(10));
         tabBackground.setColor(this.workspaceSettingsTab.equals(var8) ? Color.argb(30,255,255,255) : Color.TRANSPARENT);
         var9.setBackground(tabBackground);
         var9.setOnClickListener((var2x) -> {
            this.workspaceSettingsTab = var8;
            this.renderProductScreen(MainActivity.ProductScreen.VM_SETTINGS);
         });
         LinearLayout.LayoutParams var10 = new LinearLayout.LayoutParams(this.dp(154), this.dp(42));
         var10.topMargin = this.dp(8);
         var3.addView(var9, var10);
      }

      var2.addView(var3);
      LinearLayout var11 = new LinearLayout(this);
      var11.setOrientation(1);
      var11.setPadding(this.dp(18), this.dp(10), this.dp(10), this.dp(12));
      var2.addView(var11, new LinearLayout.LayoutParams(0, -2, 1.0F));
      this.renderWorkspaceSettingsContent(var11);
      LinearLayout.LayoutParams var12 = new LinearLayout.LayoutParams(Math.min(this.getResources().getDisplayMetrics().widthPixels - this.dp(48), this.dp(820)), -2);
      var12.topMargin = this.dp(14);
      var1.addView(var2, var12);
      this.addFooterAction(var1, this.wizardSettingsActive?"← Storage":"← Workspace", () -> this.renderProductScreen(this.wizardSettingsActive?ProductScreen.CREATE_STORAGE:this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? MainActivity.ProductScreen.WINDOWS_HOME : MainActivity.ProductScreen.LINUX_HOME));
   }

   private void renderWorkspaceSettingsContent(LinearLayout var1) {
      TextView var2 = this.text(this.workspaceSettingsTab.equals("Input") ? "Input & gestures" : this.workspaceSettingsTab, 20, -1);
      var2.setTypeface(Typeface.DEFAULT, 1);
      var1.addView(var2, new LinearLayout.LayoutParams(-1, this.dp(36)));
      if (this.selectedProfile != MainActivity.LaunchProfile.LINUX) {
         var1.addView(this.text("Windows workspace controls are limited to the verified experimental boot profile.", 13, -5197131));
      } else {
         switch (this.workspaceSettingsTab) {
            case "General":
               this.addInfoCard(var1, "Workspace", "Ubuntu 24.04.5 LTS · ARM64");
               this.addInfoCard(var1, "Official image", this.isoPresent() ? "Verified" : "Select an Ubuntu ISO");
               this.addSecondaryButton(var1,"Choose official Ubuntu ISO…",() -> {
                  if(this.isManagedVmRunning()) {this.show("Stop Ubuntu before changing boot media.");return;}
                  this.openUbuntuIsoPicker();
               });
               break;
            case "Performance":
               WorkspaceConfig resources=this.workspaceConfig();
               this.addResourceChoice(var1,"Memory",new String[]{"2 GiB","3 GiB","4 GiB","6 GiB"}, resources.ramGiB == 2 ? 0 : resources.ramGiB == 3 ? 1 : resources.ramGiB == 6 ? 3 : 2, choice -> {
                  int gib=new int[]{2,3,4,6}[choice];
                  android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();
                  ((android.app.ActivityManager)this.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
                  if((long)gib*1073741824L > info.totalMem-3L*1073741824L) {
                     this.show("Leave at least 3 GiB for Android. This memory choice is too large for the device."); return;
                  }
                  this.getPreferences(0).edit().putInt("linux_ram_gib",gib).apply();
               });
               this.addResourceChoice(var1,"CPU topology",new String[]{"Match host","1 vCPU"}, resources.oneCpu ? 1 : 0, choice -> {
                  this.getPreferences(0).edit().putBoolean("linux_one_cpu",choice==1).apply();
               });
               this.addInfoCard(var1, "Graphics", "Hardware acceleration · VirGL");
               this.addSmallNote(var1, this.isManagedVmRunning()?"Stop Ubuntu to change CPU or memory.":"Applies on next start.");
               break;
            case "Display":
               this.addWorkspaceToggle(var1,"linux_installed_encoded_display","Fast display",true,"Applies on next start.");
               this.addInfoCard(var1, "Resolution", this.configuredDisplayLabel());
               if(this.frameVisible && this.isManagedVmRunning()) {
                  String received=this.guestFrameWidth+"×"+this.guestFrameHeight;
                  if(!received.equals(this.configuredDisplayLabel())) this.addInfoCard(var1,"Active session",received);
               }
               WorkspaceConfig displayConfig=this.workspaceConfig();
               if(!this.getPreferences(0).getBoolean("linux_installed_encoded_display",true)) this.addResourceChoice(var1,"Fallback resolution",new String[]{"1280×800","1920×1080","1920×1200"}, "1920x1080".equals(displayConfig.resolution()) ? 1 : "1920x1200".equals(displayConfig.resolution()) ? 2 : 0, choice -> {
                  this.getPreferences(0).edit().putString("linux_resolution",new String[]{"1280x800","1920x1080","1920x1200"}[choice]).apply();
               });
               break;
            case "Input":
               this.addWorkspaceToggle(var1,"hide_runtime_panel_button","Hide controls button",false,"");
               this.addWorkspaceToggle(var1,"runtime_edge_gesture","Hold right middle edge, then swipe left",false,"");
               this.addResourceChoice(var1,"Controls shortcut",new String[]{"Ctrl+Alt+Shift+Esc","Ctrl+Alt+Shift+F12"},this.getPreferences(0).getInt("runtime_panel_shortcut",KeyEvent.KEYCODE_ESCAPE)==KeyEvent.KEYCODE_F12?1:0,choice->{
                  this.getPreferences(0).edit().putInt("runtime_panel_shortcut",choice==1?KeyEvent.KEYCODE_F12:KeyEvent.KEYCODE_ESCAPE).apply();
               });
               this.addFooterAction(var1,"Keyboard permissions → App settings",()->this.renderProductScreen(ProductScreen.APP_SETTINGS));
               this.addInfoCard(var1, "Pointer capture", this.frameSurface != null && this.frameSurface.isPointerCaptured() ? "Active" : "Off");
               if (this.frameVisible) {
                  this.addSecondaryButton(var1, this.frameSurface.isPointerCaptured() ? "Release mouse" : "Capture mouse", this::togglePointerCapture);
               }

               this.addWorkspaceToggle(var1, "linux_text_clipboard_enabled", "Share text clipboard (experimental)", false,
                  "Text only · changes apply immediately.");
               break;
            case "Audio":
               this.addWorkspaceToggle(var1, "linux_audio_output_enabled", "Audio output", true, "Changes apply the next time Ubuntu starts.");
               this.addSmallNote(var1,"Microphone · Coming later");
               break;
            case "Network":
               this.addWorkspaceToggle(var1, "linux_internet_enabled", "Internet access", true, "Changes apply the next time Ubuntu starts.");
               break;
            case "Storage":
               File var5 = this.persistentUbuntuDiskFile();
               this.addInfoCard(var1, "Persistent workspace", var5.isFile() ? formatBytes(var5.length()) + " · app-managed disk" : "No persistent disk created");
               this.addSecondaryButton(var1,"Expand Ubuntu storage",this::showStorageExpansion);
               this.addInfoCard(var1, "Reinstall", "Erases Ubuntu files. Keeps the installer image.");
               this.addSecondaryButton(var1, "Installation & recovery", () -> this.renderProductScreen(MainActivity.ProductScreen.INSTALL_RECOVERY));
               break;
            default:
               this.addSecondaryButton(var1, "Open app settings", () -> this.renderProductScreen(MainActivity.ProductScreen.APP_SETTINGS));
         }

      }
   }

   private void renderAppSettings(LinearLayout var1) {
      this.addTitle(var1, "U-AVF settings", "Application preferences");
      this.addSecondaryButton(var1,"Welcome screen",()->this.renderProductScreen(ProductScreen.ONBOARDING));
      this.addSystemKeyboardCaptureControls(var1);
      CheckBox var2 = new CheckBox(this);
      var2.setText("Skip disk verification before launch (advanced)");
      var2.setTextColor(this.resolveAppearanceTextColor(-1840913));
      var2.setChecked(this.getPreferences(0).getBoolean("skip_disk_validation", false));
      var2.setOnCheckedChangeListener((var1x, var2x) -> this.getPreferences(0).edit().putBoolean("skip_disk_validation", var2x).apply());
      this.addBounded(var1, var2, 10);
      this.addSmallNote(var1, "When enabled, size/SHA checks are skipped. This can launch a modified or incomplete image; leave off for normal use.");
      this.addFooterAction(var1, "Compatibility and diagnostics", () -> this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS));
      this.addSecondaryButton(var1, "Licenses and privacy", () -> ProductDialog.choose(this,
         "Licenses and privacy", "U-AVF " + this.releaseVersionLabel(),
         new String[]{"Privacy", "U-AVF license", "License scope", "Apache 2.0", "Third-party notices"},
         -1, index -> this.showLegalDocument(new String[]{"PRIVACY.md", "LICENSE-UAVF.txt", "LICENSE_SCOPE.md", "LICENSE-APACHE-2.0.txt", "THIRD_PARTY_NOTICES.txt"}[index])));
      this.addFooterAction(var1, "← Dashboard", () -> this.renderProductScreen(MainActivity.ProductScreen.MODE_SELECTION));
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
         this.getPreferences(0).getString("linux_resolution","1280x800"));
   }

   private String configuredDisplayLabel() {
      return this.getPreferences(0).getBoolean("linux_installed_encoded_display",true)?"1920×1200":this.workspaceConfig().resolution().replace('x','×');
   }

   private void addResourceChoice(LinearLayout parent,String label,String[] choices,int selected,java.util.function.IntConsumer save) {
      LinearLayout group=new LinearLayout(this);
      group.setOrientation(LinearLayout.VERTICAL);
      TextView title=this.text(label,14,-5326393);
      group.addView(title,new LinearLayout.LayoutParams(-1,-2));
      LinearLayout segments=new LinearLayout(this);
      segments.setPadding(this.dp(5),this.dp(5),this.dp(5),this.dp(5));
      segments.setBackground(this.panelBackground(Color.argb(36,255,255,255)));
      boolean editable="Controls shortcut".equals(label) || !this.isManagedVmRunning();
      for(int i=0;i<choices.length;i++) {
         final int index=i;
         boolean active=i==selected;
         TextView option=this.text(choices[i],14,active ? Color.WHITE : -5326393);
         option.setGravity(android.view.Gravity.CENTER);
         option.setTypeface(Typeface.DEFAULT,active ? Typeface.BOLD : Typeface.NORMAL);
         option.setPadding(this.dp(6),this.dp(10),this.dp(6),this.dp(10));
         option.setMinHeight(this.dp(48));
         option.setBackground(this.panelBackground(active ? Color.rgb(210,76,29) : Color.TRANSPARENT));
         option.setAlpha(editable ? 1f : 0.5f);
         option.setEnabled(editable);
         option.setContentDescription(label + ": " + choices[i] + (active ? ", selected" : "") + (editable ? "" : ", stop VM to change"));
         option.setOnClickListener(v -> {
            if(!"Controls shortcut".equals(label) && this.isManagedVmRunning()) {this.show("Stop Ubuntu before changing resources.");return;}
            if(index==selected) return;
            save.accept(index);
            this.renderProductScreen(ProductScreen.VM_SETTINGS);
         });
         LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1f);
         if(i>0)cell.leftMargin=this.dp(4);
         segments.addView(option,cell);
      }
      LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);
      row.topMargin=this.dp(10);
      group.addView(segments,row);
      this.addBounded(parent,group,20);
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

   private void addWorkspaceToggle(LinearLayout var1, String var2, String var3, boolean var4, String var5) {
      android.widget.Switch var6 = new android.widget.Switch(this);
      var6.setText(var3);
      var6.setTextColor(this.resolveAppearanceTextColor(-1840913));
      var6.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.rgb(233,84,32),Color.rgb(164,166,169)}));
      var6.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.rgb(101,49,31),Color.rgb(63,65,68)}));
      var6.setSwitchPadding(this.dp(20));
      var6.setPadding(0,this.dp(12),0,this.dp(12));
      var6.setChecked(this.getPreferences(0).getBoolean(var2, var4));
      var6.setOnCheckedChangeListener((var3x, var4x) -> {
         this.getPreferences(0).edit().putBoolean(var2, var4x).apply();
         if ("linux_text_clipboard_enabled".equals(var2)) {
            this.syncGuestClipboard();
            this.show(var4x ? "Text clipboard sharing enabled." : "Text clipboard sharing disabled.");
            return;
         }
         this.show(var3 + (var4x ? " enabled" : " disabled") + " for the next Linux launch.");
         if("linux_installed_encoded_display".equals(var2)) this.renderProductScreen(ProductScreen.VM_SETTINGS);
      });
      this.addBounded(var1, var6, 4);
      if(!var5.isEmpty()) this.addSmallNote(var1, var5);
   }

   private void renderDiagnostics(LinearLayout var1) {
      this.addTitle(var1, "Diagnostics", "Device checks, runtime health and logs.");
      if (this.frameVisible) {
         this.addSecondaryButton(var1, "Return to running session", this::showRunningDesktop);
      }

      File var2 = new File(this.getExternalFilesDir((String)null), "uavf-device-readiness.txt");
      File var3 = this.persistentUbuntuDiskFile();
      boolean var4 = var3.isFile() && this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false);
      this.addDiagnosticRow(var1, "AVF access", this.hasAvfPermissions() ? "Passed" : "Needs permission", this.hasAvfPermissions());
      this.addDiagnosticRow(var1, "Official Ubuntu image", this.isoPresent() ? "Verified" : "Not ready", this.isoPresent());
      this.addDiagnosticRow(var1, "Persistent workspace", var4 ? "Installed" : (var3.isFile() ? "Setup incomplete" : "Not created"), var4);
      this.addDiagnosticRow(var1, "Current VM", this.isManagedVmRunning() ? "Running" : "Stopped", this.isManagedVmRunning());
      this.addDiagnosticRow(var1, "Text clipboard",
         !this.getPreferences(0).getBoolean("linux_text_clipboard_enabled", false) ? "Sharing disabled" :
         (this.guestClipboard == null ? "Waiting for VM" : this.guestClipboard.status()),
         this.guestClipboard != null && "Channel connected".equals(this.guestClipboard.status()));
      this.addDiagnosticRow(var1, "Device preflight", var2.isFile() ? "Report available" : "Not run yet", var2.isFile());
      String graphics=this.graphicsEvidence();
      this.addDiagnosticRow(var1,"OpenGL graphics",graphics,graphics.startsWith("Verified"));
      for(int i=0;i<this.readinessResults.length;i++) {
         String result=this.readinessResults[i];
         if(!"Not checked".equals(result) && !"Waiting".equals(result)) {
            String[] names={"Android / ARM64","Virtualization Framework","VM permissions","Platform integrity"};
            this.addDiagnosticRow(var1,names[i],result,result.startsWith("Passed"));
         }
      }
      this.addPrimaryButton(var1, "Run device preflight", () -> {
         this.diagnosticsReturnAfterReadiness = false;
         this.compatibilityRunning = true;
         this.renderProductScreen(MainActivity.ProductScreen.COMPATIBILITY);
         (new Thread(this::checkDeviceReadiness, "U-AVF-diagnostics-preflight")).start();
      });
      this.addSecondaryButton(var1, "Export diagnostics report", this::exportDiagnosticsReport);
      this.addSecondaryButton(var1, "View technical logs", () -> {
         this.refreshLogPanel();
         this.homePanel.setVisibility(8);
         this.logPanel.setVisibility(0);
         this.logText.setTextColor(-2828067);
         this.logText.setTextSize(12.0F);
         this.logText.setTypeface(Typeface.MONOSPACE);
         this.logText.setPadding(this.dp(18), this.dp(18), this.dp(18), this.dp(18));
         this.logPanel.setBackgroundColor(-16052200);
         this.logPanel.bringToFront();
         this.menuButton.setText("‹");
         this.menuButton.setContentDescription("Back to diagnostics");
         this.menuButton.setVisibility(this.runtimeButtonVisibility());
         this.menuButton.setOnClickListener((ignoredView) -> {
            this.logPanel.setVisibility(8);
            this.menuButton.setText("⋯");
            this.menuButton.setVisibility(8);
            this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS);
         });
         this.menuButton.bringToFront();
      });
      this.addFooterAction(var1, "← Workspace", () -> this.renderProductScreen(this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? MainActivity.ProductScreen.WINDOWS_HOME : MainActivity.ProductScreen.LINUX_HOME));
   }

   private void addDiagnosticRow(LinearLayout var1, String var2, String var3, boolean var4) {
      LinearLayout var5 = new LinearLayout(this);
      var5.setGravity(16);
      var5.setPadding(this.dp(14), this.dp(8), this.dp(14), this.dp(8));
      var5.setBackground(this.panelBackground(-15132132));
      View var6 = new View(this);
      GradientDrawable var7 = new GradientDrawable();
      var7.setShape(1);
      int resultColor=var4 ? -13252733 : var3.startsWith("Failed") || var3.contains("Needs permission") ? -19912 : -5326393;
      var7.setColor(resultColor);
      var6.setBackground(var7);
      var5.addView(var6, new LinearLayout.LayoutParams(this.dp(12), this.dp(12)));
      TextView var8 = this.text(var2, 14, -1513240);
      LinearLayout.LayoutParams var9 = new LinearLayout.LayoutParams(0, this.dp(38), 1.0F);
      var9.leftMargin = this.dp(12);
      var5.addView(var8, var9);
      TextView var10 = this.text(var3, 12, resultColor);
      var5.addView(var10);
      this.addBounded(var1, var5, 5, this.dp(52));
   }

   private String graphicsEvidence() {
      File directory=this.getExternalFilesDir(null);
      for(String name:new String[]{"ubuntu-persistent-serial.log","frame-bridge-test-serial.log"}) {
         String log=readTail(new File(directory,name),131072);
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

   private void exportDiagnosticsReport() {
      try {
         StringBuilder var1 = new StringBuilder(32768);
         var1.append("U-AVF diagnostics report\n").append("Generated: ").append(Instant.now()).append('\n').append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n').append("Android: ").append(VERSION.RELEASE).append(" (API ").append(VERSION.SDK_INT).append(")\n").append("Selected profile: ").append(this.selectedProfile).append('\n').append("Compatibility preflight: ").append(this.compatibilityPassed ? "PASS" : "NOT_PASSED").append(" (this is not a full hardware certification)\n").append("VM tracked running: ").append(this.isManagedVmRunning()).append('\n').append("Official Ubuntu ISO: ").append(this.isoPresent() ? "size/hash verified" : "missing or unverified").append('\n').append("Persistent workspace disk: ").append(this.persistentUbuntuDiskFile().isFile() ? formatBytes(this.persistentUbuntuDiskFile().length()) : "not present").append('\n').append("Installed runtime preference: ").append(this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready", false)).append('\n').append("Internet next launch: ").append(this.getPreferences(0).getBoolean("linux_internet_enabled", true)).append('\n').append("Audio next launch: ").append(this.getPreferences(0).getBoolean("linux_audio_output_enabled", true)).append("\n\n");
         File var2 = this.getExternalFilesDir((String)null);
         this.appendDiagnosticsFile(var1, "Device readiness", new File(var2, "uavf-device-readiness.txt"), 24576);
         this.appendDiagnosticsFile(var1, "Install/runtime report", new File(var2, "ubuntu-install-runtime-report.txt"), 16384);
         this.appendDiagnosticsFile(var1, "Persistent Ubuntu serial", new File(var2, "ubuntu-persistent-serial.log"), 49152);
         this.appendDiagnosticsFile(var1, "Linux frame serial", new File(var2, "frame-bridge-test-serial.log"), 32768);
         this.appendDiagnosticsFile(var1, "Frame bridge report", new File(var2, "frame-bridge-test-runtime-report.txt"), 16384);
         this.appendDiagnosticsFile(var1, "Audio report", new File(var2, "ubuntu-audio-report.txt"), 12288);
         String var3 = "U-AVF-diagnostics-" + String.valueOf(LocalDate.now()) + ".txt";
         ContentValues var4 = new ContentValues();
         var4.put("_display_name", var3);
         var4.put("mime_type", "text/plain");
         var4.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/U-AVF");
         var4.put("is_pending", 1);
         Uri var5 = this.getContentResolver().insert(Downloads.EXTERNAL_CONTENT_URI, var4);
         if (var5 == null) {
            throw new IllegalStateException("Android could not create the report in Downloads/U-AVF");
         }

         OutputStream var6 = this.getContentResolver().openOutputStream(var5, "w");

         try {
            if (var6 == null) {
               throw new IllegalStateException("Could not open diagnostics report for writing");
            }

            var6.write(var1.toString().getBytes(StandardCharsets.UTF_8));
            var6.flush();
         } catch (Throwable var10) {
            if (var6 != null) {
               try {
                  var6.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (var6 != null) {
            var6.close();
         }

         ContentValues var12 = new ContentValues();
         var12.put("is_pending", 0);
         this.getContentResolver().update(var5, var12, (String)null, (String[])null);
         this.show("Diagnostics saved to Downloads/U-AVF/" + var3);
      } catch (Throwable var11) {
         this.show("Could not export diagnostics: " + rootMessage(var11));
      }

   }

   private void appendDiagnosticsFile(StringBuilder var1, String var2, File var3, int var4) {
      var1.append("--- ").append(var2).append(" ---\n");
      String var5 = readTail(var3, var4);
      var1.append(var5.isEmpty() ? "Not available\n" : var5).append("\n\n");
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
         this.runOnUiThread(() -> this.renderProductScreen(MainActivity.ProductScreen.MODE_SELECTION));
         this.show("Ubuntu workspace deleted. The verified source ISO was kept.");
      } catch (Throwable var8) {
         this.show("Workspace was not deleted: " + rootMessage(var8));
      }

   }

   private void addModeCard(LinearLayout var1, String var2, String var3, int var4, int var5, Runnable var6) {
      LinearLayout var7 = new LinearLayout(this);
      var7.setGravity(16);
      var7.setPadding(this.dp(18), this.dp(14), this.dp(18), this.dp(14));
      var7.setBackground(this.panelBackground(-15393496));
      ImageView var8 = new ImageView(this);
      var8.setImageResource(var4);
      var8.setScaleType(ScaleType.FIT_CENTER);
      var7.addView(var8, new LinearLayout.LayoutParams(this.dp(72), this.dp(72)));
      LinearLayout var9 = new LinearLayout(this);
      var9.setOrientation(1);
      var9.setPadding(this.dp(16), 0, 0, 0);
      TextView var10 = this.text(var2, 20, -1);
      var10.setTypeface(Typeface.DEFAULT, 1);
      var9.addView(var10);
      var9.addView(this.text(var3, 13, -5392185));
      var7.addView(var9, new LinearLayout.LayoutParams(0, -2, 1.0F));
      TextView var11 = this.text("›", 30, var5);
      var7.addView(var11);
      var7.setOnClickListener((var1x) -> var6.run());
      this.addBounded(var1, var7, 12);
   }

   private void addTitle(LinearLayout var1, String var2, String var3) {
      TextView var4 = this.text(var2, 28, -657414);
      var4.setTypeface(Typeface.DEFAULT, 1);
      var4.setPadding(0, this.dp(12), 0, this.dp(6));
      this.addBounded(var1, var4, 2);
      TextView var5 = this.text(var3, 15, -5260600);
      var5.setLineSpacing((float)this.dp(3), 1.0F);
      this.addBounded(var1, var5, 18);
   }

   private void addInfoCard(LinearLayout var1, String var2, String var3) {
      LinearLayout var4 = new LinearLayout(this);
      var4.setOrientation(1);
      var4.setPadding(this.dp(20), this.dp(18), this.dp(20), this.dp(18));
      var4.setBackground(this.panelBackground(0xf50c121a));
      TextView var5 = this.text(var2, 14, -1577483);
      var5.setTypeface(this.productTypeface(), Typeface.BOLD);
      var4.addView(var5);
      TextView var6 = this.text(var3, 13, -5326393);
      var6.setPadding(0, this.dp(4), 0, 0);
      var4.addView(var6);
      this.addBounded(var1, var4, 12);
   }

   private void addPrimaryButton(LinearLayout var1, String var2, Runnable var3) {
      Button var4 = this.button(var2);
      var4.setTextSize(15.0F);
      var4.setTypeface(this.productTypeface(), Typeface.NORMAL);
      var4.setTextColor(0xff05140f);
      var4.setBackground(this.panelBackground(0xf538d185));
      var4.setOnClickListener((var1x) -> var3.run());
      this.addBounded(var1, var4, 12, this.dp(54));
   }

   private void addSecondaryButton(LinearLayout var1, String var2, Runnable var3) {
      Button var4 = this.button(var2);
      var4.setTextSize(14.0F);
      var4.setBackground(this.panelBackground(0xf5151e28));
      var4.setOnClickListener((var1x) -> var3.run());
      this.addBounded(var1, var4, 12, this.dp(54));
   }

   private void addFooterAction(LinearLayout var1, String var2, Runnable var3) {
      Button var4 = this.button(var2);
      var4.setGravity(19);
      var4.setBackgroundColor(0);
      var4.setOnClickListener((var1x) -> var3.run());
      this.addBounded(var1, var4, 12, this.dp(48));
   }

   private void addSmallNote(LinearLayout var1, String var2) {
      TextView var3 = this.text(var2, 12, -7365718);
      var3.setPadding(0, this.dp(8), 0, this.dp(10));
      this.addBounded(var1, var3, 4);
   }

   private TextView text(String var1, int var2, int var3) {
      TextView var4 = new TextView(this);
      var4.setText(var1);
      var4.setTextSize((float)var2);
      var4.setTypeface(this.productTypeface());
      var4.setTextColor(this.resolveAppearanceTextColor(var3));
      var4.setGravity(16);
      return var4;
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

   private void applyProductBackground(LinearLayout var1) {
      if (this.isLightAppearance()) {
         var1.setBackground(new GradientDrawable(Orientation.TL_BR, new int[]{-460036, -1511949, -2300950}));
      } else {
         Drawable var2 = this.getDrawable(2130771971);
         ColorDrawable var3 = new ColorDrawable(-988736487);
         var1.setBackground(new LayerDrawable(new Drawable[]{var2, var3}));
      }
   }

   private void addAppearanceSelector(LinearLayout var1) {
      this.addSmallNote(var1, "Appearance");
      boolean var2 = this.isLightAppearance();
      LinearLayout var3 = new LinearLayout(this);
      var3.setOrientation(0);
      Button var4 = this.button("Dark");
      Button var5 = this.button("Standard AVF");
      var4.setTextColor(var2 ? -14274243 : -1);
      var5.setTextColor(var2 ? -1 : -14274243);
      var4.setBackground(this.panelBackground(var2 ? -14341583 : -12815176));
      var5.setBackground(this.panelBackground(var2 ? -12815176 : -920587));
      LinearLayout.LayoutParams var6 = new LinearLayout.LayoutParams(0, this.dp(48), 1.0F);
      var6.rightMargin = this.dp(6);
      var3.addView(var4, var6);
      LinearLayout.LayoutParams var7 = new LinearLayout.LayoutParams(0, this.dp(48), 1.0F);
      var7.leftMargin = this.dp(6);
      var3.addView(var5, var7);
      var4.setOnClickListener((var1x) -> this.setAppearance("dark"));
      var5.setOnClickListener((var1x) -> this.setAppearance("light"));
      this.addBounded(var1, var3, 4, this.dp(54));
      this.addSmallNote(var1, var2 ? "Standard AVF theme is active." : "Dark theme is active.");
   }

   private void setAppearance(String var1) {
      if (!var1.equals(this.getPreferences(0).getString("appearance_mode", "dark"))) {
         this.getPreferences(0).edit().putString("appearance_mode", var1).apply();
         this.renderProductScreen(this.productScreen);
      }
   }

   private void addBounded(LinearLayout var1, View var2, int var3) {
      this.addBounded(var1, var2, var3, -2);
   }

   private void addBounded(LinearLayout var1, View var2, int var3, int var4) {
      int var5 = var1 == this.productBody ? Math.max(this.dp(160),Math.min(this.getResources().getDisplayMetrics().widthPixels - this.dp(136), this.dp(820))) : -1;
      LinearLayout.LayoutParams var6 = new LinearLayout.LayoutParams(var5, var4);
      var6.topMargin = this.dp(var3);
      var1.addView(var2, var6);
   }

   private LinearLayout.LayoutParams spaced(LinearLayout.LayoutParams var1, int var2) {
      var1.topMargin = this.dp(var2);
      return var1;
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
         this.guestKeyboard.releaseAll();
         this.homePanel.setVisibility(8);
         this.menuButton.setVisibility(8);
         this.status.setVisibility(8);
         if (this.transitionView==null) {
            this.transitionView=new RuntimeTransitionView(this,this.transition,
                  ()->this.confirmForceStop(this.transition.operation()==RuntimeTransition.Mode.REBOOT),
                  ()->{this.transition.dismiss();this.transitionView.setVisibility(8);
                     this.renderProductScreen(ProductScreen.LINUX_HOME);});
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
      ProductDialog.show(this,"Shut down Ubuntu?","Request a normal guest shutdown. The app keeps waiting for real guest events; recovery is offered only after at least one minute.","Shut down",false,() -> {
         this.closeSessionDrawerImmediately();
         (new Thread(this::stopSelectedProfile, "U-AVF-stop-confirmed")).start();
      });
   }

   private void reconnectRunningFrameVmIfPresent() {
      (new Thread(() -> {
         try {
            Object var1 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
            Object var2 = var1.getClass().getMethod("get", String.class).invoke(var1, "winavf-frame-bridge-test-ubuntu-24045");
            if (var2 == null) {
               this.lifecycle.reconcile(null, "STOPPED", this.persistentUbuntuDiskFile().isFile());
               return;
            }

            String var3 = this.readAvfState(var2);
            this.lifecycle.reconcile(var2, var3, this.persistentUbuntuDiskFile().isFile());
            if (!var3.contains("RUN") && !var3.contains("START")) {
               return;
            }

            this.vmMayBeRunning = true;
            this.activeFrameVmName = "winavf-frame-bridge-test-ubuntu-24045";
            this.selectedProfile = MainActivity.LaunchProfile.LINUX;
            this.attachCallback(var2);
            this.runOnUiThread(() -> {
               this.frameVisible = true;
               this.showRunningDesktop();
               this.show("Reconnected to the running Linux session.");
            });
            InputStream var4 = (InputStream)var2.getClass().getMethod("getConsoleOutput").invoke(var2);
            this.startConsoleReader(var4, "frame-bridge-test-serial.log");
            this.startUbuntuFrameBridge(var2);
         } catch (Throwable var5) {
            Log.i("U-AVF", "No reconnectable Linux session", var5);
         }

      }, "U-AVF-reconnect")).start();
   }

   private void openUbuntuIsoPicker() {
      Intent var1 = new Intent("android.intent.action.OPEN_DOCUMENT");
      var1.addCategory("android.intent.category.OPENABLE");
      var1.setType("*/*");
      var1.putExtra("android.intent.extra.TITLE", "ubuntu-24.04.5-desktop-arm64.iso");

      try {
         this.startActivityForResult(var1, 21825);
      } catch (Throwable var3) {
         this.show("Could not open image picker: " + rootMessage(var3));
      }

   }

   protected void onActivityResult(int var1, int var2, Intent var3) {
      super.onActivityResult(var1, var2, var3);
      if (var1 == 21825 && var2 == -1 && var3 != null && var3.getData() != null) {
         Uri var4 = var3.getData();
         (new Thread(() -> this.importUbuntuIso(var4), "U-AVF-import-ubuntu-iso")).start();
      }
   }

   private void importUbuntuIso(Uri var1) {
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
         if (var2.isFile() && var2.length() == 3967463424L && "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(hex(sha256File(var2)))) {
            this.runOnUiThread(this::refreshUbuntuIsoStatus);
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

         String var24 = hex(var5.digest());
         if (var6 != 3967463424L || !"2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(var24)) {
            var3.delete();
            throw new SecurityException("This is not the verified Ubuntu 24.04.5 ARM64 desktop ISO (size/hash mismatch)");
         }

         try {
            Files.move(var3.toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var19) {
            Files.move(var3.toPath(), var2.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         this.runOnUiThread(() -> {
            this.refreshUbuntuIsoStatus();
            this.selectProfile(MainActivity.LaunchProfile.LINUX);
            this.renderProductScreen(this.productScreen == MainActivity.ProductScreen.CREATE_IMAGE ? MainActivity.ProductScreen.CREATE_IMAGE : MainActivity.ProductScreen.LINUX_HOME);
         });
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

   private void downloadOfficialUbuntuIso() {
      File var1 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
      File var2 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso.download");
      HttpURLConnection var3 = null;

      try {
         if (!var1.isFile() || var1.length() != 3967463424L || !"2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(hex(sha256File(var1)))) {
            if (var1.getParentFile().getUsableSpace() < 4034572288L) {
               throw new IllegalStateException("Free at least 3.8 GiB of shared app storage before downloading.");
            }

            this.show("Connecting to Ubuntu servers…");
            URL var4 = new URL("https://cdimage.ubuntu.com/ubuntu/releases/24.04.5/release/ubuntu-24.04.5-desktop-arm64.iso");
            var3 = (HttpURLConnection)var4.openConnection();
            var3.setConnectTimeout(30000);
            var3.setReadTimeout(60000);
            var3.setInstanceFollowRedirects(true);
            var3.setRequestProperty("User-Agent", "U-AVF/1.0");
            int var5 = var3.getResponseCode();
            if (var5 != 200) {
               throw new IllegalStateException("Ubuntu download returned HTTP " + var5);
            }

            if (!"https".equalsIgnoreCase(var3.getURL().getProtocol())) {
               throw new SecurityException("Ubuntu download did not remain on HTTPS.");
            }

            long var6 = var3.getContentLengthLong();
            if (var6 > 0L && var6 != 3967463424L) {
               throw new IllegalArgumentException("Ubuntu server returned an unexpected image size.");
            }

            MessageDigest var8 = MessageDigest.getInstance("SHA-256");
            long var9 = 0L;
            long var11 = 0L;
            BufferedInputStream var13 = new BufferedInputStream(var3.getInputStream());

            try {
               FileOutputStream var14 = new FileOutputStream(var2, false);

               try {
                  byte[] var15 = new byte[1048576];

                  int var16;
                  while((var16 = ((InputStream)var13).read(var15)) >= 0) {
                     if (var16 != 0) {
                        var9 += (long)var16;
                        if (var9 > 3967463424L) {
                           throw new IllegalArgumentException("Ubuntu image exceeded expected size.");
                        }

                        var8.update(var15, 0, var16);
                        var14.write(var15, 0, var16);
                        if (var9 - var11 >= 134217728L) {
                           var11 = var9;
                            long progressBytes = var9;
                            this.runOnUiThread(() -> this.show("Downloading official Ubuntu image: " + progressBytes / 1048576L + " MiB"));
                        }
                     }
                  }

                  var14.getFD().sync();
               } catch (Throwable var32) {
                  try {
                     var14.close();
                  } catch (Throwable var30) {
                     var32.addSuppressed(var30);
                  }

                  throw var32;
               }

               var14.close();
            } catch (Throwable var33) {
               try {
                  ((InputStream)var13).close();
               } catch (Throwable var29) {
                  var33.addSuppressed(var29);
               }

               throw var33;
            }

            ((InputStream)var13).close();
            String var36 = hex(var8.digest());
            if (var9 == 3967463424L && "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(var36)) {
               try {
                  Files.move(var2.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
               } catch (AtomicMoveNotSupportedException var31) {
                  Files.move(var2.toPath(), var1.toPath(), StandardCopyOption.REPLACE_EXISTING);
               }

                this.runOnUiThread(() -> {
                  this.selectProfile(MainActivity.LaunchProfile.LINUX);
                  this.refreshUbuntuIsoStatus();
                  this.renderProductScreen(this.productScreen == MainActivity.ProductScreen.CREATE_IMAGE ? MainActivity.ProductScreen.CREATE_IMAGE : MainActivity.ProductScreen.LINUX_HOME);
               });
               this.show("Official Ubuntu image downloaded and verified.");
               return;
            }

            throw new SecurityException("Downloaded image failed Canonical's pinned size/SHA-256 check.");
         }

       this.runOnUiThread(() -> {
            this.selectProfile(MainActivity.LaunchProfile.LINUX);
            this.renderProductScreen(this.productScreen == MainActivity.ProductScreen.CREATE_IMAGE ? MainActivity.ProductScreen.CREATE_IMAGE : MainActivity.ProductScreen.LINUX_HOME);
         });
         this.show("The verified Ubuntu image is already ready.");
      } catch (Throwable var34) {
         try {
            var2.delete();
         } catch (Throwable var28) {
         }

         this.show("Ubuntu download failed: " + rootMessage(var34));
         return;
      } finally {
         if (var3 != null) {
            var3.disconnect();
         }

      }

   }

   private void checkDeviceReadiness() {
      this.runOnUiThread(() -> {
         Arrays.fill(this.readinessResults,"Waiting");
         this.readinessCompleted=0;
         if(this.productScreen==ProductScreen.COMPATIBILITY)this.renderProductScreen(ProductScreen.COMPATIBILITY);
      });
      File var1 = new File(this.getExternalFilesDir((String)null), "uavf-device-readiness.txt");
      boolean var2 = Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a");
      this.readinessResult(0,var2 ? "Passed · Android " + VERSION.RELEASE + " / ARM64" : "Failed · ARM64 required");
      boolean var3 = false;

      String var4;
      try {
         Class var5 = Class.forName("android.system.virtualmachine.VirtualMachineManager");
         Object var6 = this.getSystemService(var5);
         var3 = var6 != null;
         var4 = var3 ? "VirtualizationManager available" : "VirtualizationManager unavailable";
      } catch (Throwable var22) {
         var4 = rootMessage(var22);
      }
      this.readinessResult(1,var3 ? "Passed · API available" : "Failed · AVF unavailable");
      boolean permissionsReady=this.hasAvfPermissions();
      this.readinessResult(2,permissionsReady ? "Passed · granted" : "Failed · permission required");

      boolean var11 = true;
      String var12 = "PASS";

      try {
         for(String asset:BundledPlatformAssets.NAMES) {
            this.verifyBundledAsset(asset,BundledPlatformAssets.size(asset),BundledPlatformAssets.sha256(asset));
         }
      } catch (Throwable var20) {
         var11 = false;
         var12 = rootMessage(var20);
      }
      this.readinessResult(3,var11 ? "Passed · SHA-256 verified" : "Failed · platform integrity");

      boolean var13 = var2 && var3 && permissionsReady && var11;

      try {
         PrintWriter var14 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var14.println("RESULT=" + (var13 ? "PREREQUISITES_READY" : "NOT_READY"));
            String var10001 = Build.MODEL;
            var14.println("DEVICE=" + var10001);
            int var25 = VERSION.SDK_INT;
            var14.println("ANDROID_SDK=" + var25);
            var14.println("ARM64_ABI=" + (var2 ? "PASS" : "FAIL"));
            var14.println("CUSTOM_VM_PERMISSIONS=" + (permissionsReady ? "PASS" : "FAIL"));
            var14.println("AVF_MANAGER=" + (var3 ? "PASS" : "FAIL") + " (" + var4 + ")");
            var14.println("BUNDLED_PLATFORM_ASSETS=" + (var11 ? "PASS" : "FAIL") + " (" + var12 + ")");
            var14.println("VM_BOOT_TEST=NOT_RUN_BY_PREFLIGHT");
            var14.println("NOTE=This check verifies launch prerequisites only; a real VM launch is required for full device compatibility.");
         } catch (Throwable var18) {
            try {
          var14.close();
            } catch (Throwable var17) {
               var18.addSuppressed(var17);
            }

            throw var18;
         }

         var14.close();
      } catch (Exception var19) {
         this.runOnUiThread(() -> {
            this.compatibilityRunning=false;
            this.compatibilityPassed=false;
            this.renderProductScreen(ProductScreen.COMPATIBILITY);
            this.show("Could not save readiness report: " + rootMessage(var19));
         });
         return;
      }

       this.runOnUiThread(() -> {
         this.compatibilityRunning = false;
         this.compatibilityPassed = var13;
         android.content.SharedPreferences.Editor verified=this.getPreferences(0).edit().putBoolean("compatibility_passed",var13)
            .putString("compatibility_checked_build",Build.FINGERPRINT).putInt("compatibility_check_schema",2);
         for(int i=0;i<this.readinessResults.length;i++)verified.putString("compatibility_result_"+i,this.readinessResults[i]);
         verified.apply();
         if (this.diagnosticsReturnAfterReadiness) {
            this.diagnosticsReturnAfterReadiness = false;
            this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS);
         } else if (!this.hasAvfPermissions()) {
            this.renderProductScreen(MainActivity.ProductScreen.VM_PERMISSION);
         } else {
            this.renderProductScreen(MainActivity.ProductScreen.COMPATIBILITY);
         }

         this.show(var13 ? "Device prerequisites look good. A real launch confirms full compatibility." : "Some prerequisites are missing. Open Diagnostics for the local report.");
         if(this.getPreferences(0).getBoolean("share_compatibility_report",false))this.shareCompatibilityReport();
      });
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

      if (var6 != var2 || !var4.equals(hex(var5.digest()))) {
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
      return hex(var2.digest());
   }

   private Button button(String var1) {
      Button var2 = new Button(this);
      var2.setText(var1);
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

   private void toggleSettings() {
      this.renderProductScreen(MainActivity.ProductScreen.VM_SETTINGS);
   }

   private void toggleLogs() {
      this.renderProductScreen(MainActivity.ProductScreen.DIAGNOSTICS);
   }

   private void showScrim() {
      this.drawerScrim.animate().cancel();
      this.drawerScrim.setVisibility(0);
      this.drawerScrim.animate().alpha(1.0F).setDuration(180L).start();
   }

   private void hideScrim() {
      if (this.settingsPanel.getVisibility() != 0 && this.logPanel.getVisibility() != 0) {
         this.drawerScrim.animate().alpha(0.0F).setDuration(160L).withEndAction(() -> this.drawerScrim.setVisibility(8)).start();
      }
   }

   private void closeOverlays() {
      this.hideSettingsDrawer();
      this.hideLogDrawer();
   }

   private void hideSettingsDrawer() {
      if (this.settingsPanel.getVisibility() == 0) {
         this.settingsPanel.animate().alpha(0.0F).translationX((float)this.dp(380)).setDuration(180L).withEndAction(() -> {
            this.settingsPanel.setVisibility(8);
            this.hideScrim();
         }).start();
      }
   }

   private void hideLogDrawer() {
      if (this.logPanel.getVisibility() == 0) {
         this.logPanel.animate().alpha(0.0F).translationY((float)this.dp(360)).setDuration(180L).withEndAction(() -> {
            this.logPanel.setVisibility(8);
            this.hideScrim();
         }).start();
      }
   }

   private void hideSettingsImmediately() {
      this.settingsPanel.animate().cancel();
      this.settingsPanel.setVisibility(8);
      this.settingsPanel.setAlpha(1.0F);
      this.settingsPanel.setTranslationX(0.0F);
   }

   private void hideLogPanelImmediately() {
      this.logPanel.animate().cancel();
      this.logPanel.setVisibility(8);
      this.logPanel.setAlpha(1.0F);
      this.logPanel.setTranslationY(0.0F);
   }

   private void refreshLogPanel() {
      String var1 = this.selectedProfile != MainActivity.LaunchProfile.P33_FRAME_BRIDGE && this.selectedProfile != MainActivity.LaunchProfile.LINUX ? "serial.log" : "frame-bridge-test-serial.log";
      String var2 = readTail(new File(this.getExternalFilesDir((String)null), var1), 16384);
      TextView var10000 = this.logText;
      String var10001 = this.selectedProfile.name();
      var10000.setText("U-AVF EVENT LOG — " + var10001 + "\n" + String.valueOf(this.uiLog) + (var2.isEmpty() ? "\nNo serial output has been captured for this profile." : "\nRAW SERIAL TAIL\n" + var2));
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

      this.compatibilityPassed = this.getPreferences(0).getInt("compatibility_check_schema",0)==2
         && Build.FINGERPRINT.equals(this.getPreferences(0).getString("compatibility_checked_build",""))
         && this.getPreferences(0).getBoolean("compatibility_passed", false);
      if(this.getPreferences(0).getInt("compatibility_check_schema",0)==2 && Build.FINGERPRINT.equals(this.getPreferences(0).getString("compatibility_checked_build",""))) {
         for(int i=0;i<this.readinessResults.length;i++)this.readinessResults[i]=this.getPreferences(0).getString("compatibility_result_"+i,"Not checked");
         this.readinessCompleted=this.readinessResults.length;
      }
      if (var1) {
         this.getPreferences(0).edit().putBoolean("first_run_complete", true).apply();
      }

      this.updateProfileUi();
      if (var1) {
         this.renderProductScreen(this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? MainActivity.ProductScreen.WINDOWS_HOME : (this.isoPresent() ? MainActivity.ProductScreen.LINUX_HOME : MainActivity.ProductScreen.LINUX_SETUP));
      } else {
         this.renderProductScreen(MainActivity.ProductScreen.ONBOARDING);
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

            (new Thread(this::startFrameBridgeTest, "WinAVF-ui-linux-start")).start();
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
      String var1 = this.activeFrameVmName != null ? this.activeFrameVmName : (this.selectedProfile == MainActivity.LaunchProfile.P33_FRAME_BRIDGE ? "winavf-frame-bridge-test-ubuntu-24045" : (this.selectedProfile == MainActivity.LaunchProfile.LINUX ? "winavf-frame-bridge-test-ubuntu-24045" : (this.selectedProfile == MainActivity.LaunchProfile.V22_EXT4_CONTROL ? "winavf-ubuntu-gnome-24045-v22-ext4-control" : "winavf-gop-ebs-r1")));

      try {
         Object var2 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
         Object var3 = var2.getClass().getMethod("get", String.class).invoke(var2, var1);
         if (var3 == null) {
            this.show("No running " + this.selectedProfile.name().toLowerCase() + " VM.");
            return;
         }

         this.userRequestedVmStop = true;
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
         } else if (var4 != null && !var4.equals(hex(sha256File(var1)))) {
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
      return this.skipDiskValidation() ? "SKIPPED_BY_USER" : hex(sha256File(var1));
   }

   private static String readTail(File var0, int var1) {
      if (!var0.isFile()) {
         return "";
      } else {
         try {
            RandomAccessFile var2 = new RandomAccessFile(var0, "r");

            String var6;
            try {
               long var3 = Math.max(0L, var2.length() - (long)var1);
               var2.seek(var3);
               byte[] var5 = new byte[(int)(var2.length() - var3)];
               var2.readFully(var5);
               var6 = new String(var5, StandardCharsets.UTF_8);
            } catch (Throwable var8) {
               try {
                  var2.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            var2.close();
            return var6;
         } catch (Throwable var9) {
            return "";
         }
      }
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
            synchronized (this.frameConnectionLock) {
               ++this.frameConnectionGeneration;
               if (this.activeFrameSocket != null) {
                  try { Os.shutdown(this.activeFrameSocket.getFileDescriptor(), android.system.OsConstants.SHUT_RDWR); } catch (Exception ignored) { }
                  try { this.activeFrameSocket.close(); } catch (Exception ignored) { }
                  this.activeFrameSocket = null;
               }
               this.activeFrameInput = null;
            }
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
         (new Thread(this::startFrameBridgeTest, "WinAVF-generic-frame-bridge-test")).start();
      }

      if (var1.getBooleanExtra("stop_frame_bridge_test", false)) {
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(this::stopSelectedProfile, "WinAVF-stop-frame-bridge-test")).start();
      }

      if (var1.getBooleanExtra("linux_gpu_virgl_gbm_test", false)) {
         this.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true).putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).apply();
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(() -> this.startFrameBridgeTest(false, false, true), "WinAVF-linux-virgl-gbm-test")).start();
      }

      if (var1.getBooleanExtra("linux_gpu_virgl_gbm_linux_first_test", false)) {
         this.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true).putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).putBoolean("linux_gpu_gfxstream_system_blob_experimental", false).apply();
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(() -> this.startFrameBridgeTest(false, false, true, true), "WinAVF-linux-virgl-gbm-linux-first-test")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_test", false)) {
         (new Thread(() -> this.startFrameBridgeTest(true), "WinAVF-linux-first-gpu-test")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_no_udmabuf_test", false)) {
         (new Thread(() -> this.startFrameBridgeTest(true, true), "WinAVF-linux-first-gpu-no-udmabuf")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_system_blob_test", false)) {
         this.getPreferences(0).edit().putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", true).putBoolean("linux_gpu_gfxstream_system_blob_experimental", true).putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false).apply();
         this.selectedProfile = MainActivity.LaunchProfile.LINUX;
         (new Thread(() -> this.startFrameBridgeTest(true), "WinAVF-linux-first-gpu-system-blob")).start();
      }

      if (var1.getBooleanExtra("linux_first_gpu_virgl_test", false)) {
         (new Thread(() -> this.startFrameBridgeTest(true, false, true), "WinAVF-linux-first-gpu-virgl")).start();
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
      putLe16(var1, 8, 320);
      putLe16(var1, 10, 200);
      putLe32(var1, 12, 1398359601);
      putLe32(var1, 16, 256000);

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
      putLe32(var1, 20, (int)var5.getValue());
      this.frameDecoder.feed(var1, 0, var1.length);
      this.show("Synthetic WAVF surface audit frame submitted.");
   }

   private static void putLe16(byte[] var0, int var1, int var2) {
      var0[var1] = (byte)var2;
      var0[var1 + 1] = (byte)(var2 >>> 8);
   }

   private static void putLe32(byte[] var0, int var1, int var2) {
      var0[var1] = (byte)var2;
      var0[var1 + 1] = (byte)(var2 >>> 8);
      var0[var1 + 2] = (byte)(var2 >>> 16);
      var0[var1 + 3] = (byte)(var2 >>> 24);
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
            var62.println("patternSha256=" + hex(sha256(var63)));
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
            int var18 = waitForBytes(var8, var7, CONSOLE_BINARY_ECHO_READY, 8000L);
            if (var18 < 0) {
               throw new IllegalStateException("Guest readiness marker not observed");
            }

            var62.println("readyMarkerOffset=" + var18);
            var6.write(var63);
            var6.flush();
            var62.println("patternTransmitted=true");
            if (!waitForLength(var8, var7, var18 + CONSOLE_BINARY_ECHO_READY.length + var63.length, 8000L)) {
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
            var62.println("echoedSha256=" + hex(sha256(var20)));
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

   private static boolean waitForLength(ByteArrayOutputStream var0, Object var1, int var2, long var3) throws InterruptedException {
      long var5 = System.currentTimeMillis() + var3;
      synchronized(var1) {
         while(var0.size() < var2) {
            long var8 = var5 - System.currentTimeMillis();
            if (var8 <= 0L) {
               return false;
            }

            var1.wait(var8);
         }

         return true;
      }
   }

   private static int waitForBytes(ByteArrayOutputStream var0, Object var1, byte[] var2, long var3) throws InterruptedException {
      long var5 = System.currentTimeMillis() + var3;
      synchronized(var1) {
         while(true) {
            byte[] var8 = var0.toByteArray();

            for(int var9 = 0; var9 <= var8.length - var2.length; ++var9) {
               boolean var10 = true;

               for(int var11 = 0; var11 < var2.length; ++var11) {
                  if (var8[var9 + var11] != var2[var11]) {
                     var10 = false;
                     break;
                  }
               }

               if (var10) {
                  return var9;
               }
            }

            long var14 = var5 - System.currentTimeMillis();
            if (var14 <= 0L) {
               return -1;
            }

            var1.wait(var14);
         }
      }
   }

   private static String hex(byte[] var0) {
      return HexFormat.of().formatHex(var0).toUpperCase(Locale.ROOT);
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
         applyPatch(var7, var5, false);
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
         Object var1 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));
         Object var2 = var1.getClass().getMethod("get", String.class).invoke(var1, "winavf-frame-bridge-test-ubuntu-24045");
         if (var2 == null) {
            throw new IllegalStateException("FRAME_BRIDGE_TEST VM not found");
         }

         this.startUbuntuFrameBridge(var2);
      } catch (Throwable var3) {
         this.show("Frame bridge retry failed: " + rootMessage(var3));
      }

   }

   private void startFrameBridgeTest() {
      this.getPreferences(0).edit().putBoolean("linux_gpu_virgl_gbm_xvnc_experimental", true).putBoolean("linux_gpu_virgl_experimental", false).putBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false).putBoolean("linux_gpu_gfxstream_system_blob_experimental", false).apply();
      this.startFrameBridgeTest(false, false, true, true);
   }

   private void startFrameBridgeTest(boolean var1) {
      this.startFrameBridgeTest(var1, false);
   }

   private void startFrameBridgeTest(boolean var1, boolean var2) {
      this.startFrameBridgeTest(var1, var2, false);
   }

   private void startFrameBridgeTest(boolean var1, boolean var2, boolean var3) {
      this.startFrameBridgeTest(var1, var2, var3, false);
   }

   private void startFrameBridgeTest(boolean var1, boolean var2, boolean var3, boolean var4) {
      if (!this.lifecycle.beginStart()) {
         this.show("The workspace is already starting, running or stopping.");
         return;
      }
      this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
      File var5 = new File(this.getExternalFilesDir((String)null), "frame-bridge-test-runtime-report.txt");

      try {
         this.frameVisible = false;
         this.runOnUiThread(() -> {
            this.homePanel.setVisibility(8);
            this.status.setVisibility(0);
            this.status.setText("Preparing Ubuntu live session…");
         });
         File var26 = new File(this.getExternalFilesDir((String)null), "ubuntu-24.04.5-desktop-arm64.iso");
         if (!var26.isFile()) {
            throw new IllegalStateException("Choose the official Ubuntu ISO first.");
         }

         File var27 = new File(this.getFilesDir(), "frame-bridge-test-payload");
         if (!var27.exists() && !var27.mkdirs()) {
            throw new IllegalStateException("Cannot create frame bridge payload");
         }

         File var8 = this.buildFrameBridgeLiveDisk(var26, var27, var1, var4);
         File var9 = this.copyAsset("u-boot-wrapper-v24.Image", new File(var27, "u-boot-wrapper-v24.Image"), -1L);
         Object var10 = this.buildGenericUbuntuConfig(var9, var8, (File)null, (File)null, false, "winavf-frame-bridge-test-ubuntu-24045", var2, var3);
         Object var11 = this.getSystemService(Class.forName("android.system.virtualmachine.VirtualMachineManager"));

         try {
            Object var12 = var11.getClass().getMethod("get", String.class).invoke(var11, "winavf-frame-bridge-test-ubuntu-24045");
            if (var12 != null) {
               var11.getClass().getMethod("delete", String.class).invoke(var11, "winavf-frame-bridge-test-ubuntu-24045");
            }
         } catch (Exception var23) {
         }

         Object var28 = var11.getClass().getMethod("create", String.class, var10.getClass()).invoke(var11, "winavf-frame-bridge-test-ubuntu-24045", var10);
         this.attachCallback(var28);
         InputStream var13 = (InputStream)var28.getClass().getMethod("getConsoleOutput").invoke(var28);
         this.startConsoleReader(var13, "frame-bridge-test-serial.log");
         File var14 = new File(var27, "uavf-ubuntu-live-runtime.ready");
         if (var14.exists() && !var14.delete()) {
            throw new IllegalStateException("Cannot invalidate temporary-media cache before launch");
         }

         var28.getClass().getMethod("run").invoke(var28);
         this.vmMayBeRunning = true;
         this.activeFrameVmName = "winavf-frame-bridge-test-ubuntu-24045";
         this.getPreferences(0).edit().putBoolean("linux_current_boot_installed",false)
               .putBoolean("linux_current_boot_encoded",var4).apply();
         this.startUbuntuVsockHelloProbe(var28);
         PrintWriter var15 = new PrintWriter(new FileOutputStream(var5, false));

         try {
            var15.println("PROFILE=LINUX_LIVE_FRAME_BRIDGE");
            var15.println("SELECTED_OFFICIAL_ISO_SHA256=2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14");
            boolean var16 = this.getPreferences(0).getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false);
            boolean var17 = this.getPreferences(0).getBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false) && !var16;
            boolean var18 = this.getPreferences(0).getBoolean("linux_gpu_gfxstream_system_blob_experimental", false);
            var15.println("RENDERER_EXPERIMENT=" + (var4 ? "VIRGL_GBM_XVNC_115_LINUX_FIRST" : (var17 ? (var3 ? "VIRGL_GBM_XVNC_115_HOST_VIRGL" : "VIRGL_GBM_XVNC_115") : (var3 ? "LINUX_FIRST_GPU_VIRGL" : (var18 && var1 ? "LINUX_FIRST_GPU_SYSTEM_BLOB" : (var2 ? "LINUX_FIRST_GPU_NO_UDMABUF" : (var1 ? "LINUX_FIRST_GPU_BIND" : (var16 ? "GFXSTREAM_VULKAN_MESA_ZINK" : "SOFTWARE_BASELINE"))))))));
            var15.println("RUNTIME_DISK_EXPECTED_SHA256=" + (var17 ? "COMPONENT_HASHES" : (var1 ? "C2C670C75BC0534706078898A82DD683B87A491D320D153C44F2F0BB4D81A764" : (var16 ? "D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F" : "68582C7187EB83BEEE9E2D994BE4952FC5DE772BF2273AD1ECBCE6621B59BB47"))));
            var15.println("RUNTIME_DISK_SHA256=" + hex(sha256File(var8)));
            var15.println("RUNTIME_DISK_BYTES=4102029312");
            var15.println("RUNTIME_DISK_SHA256_VERIFIED=PASS");
            var15.println("PLATFORM_PREFIX_SHA256=" + (var4 ? FRAME_BRIDGE_VIRGL_GBM_LINUX_FIRST_PREFIX_SHA256 : (var17 ? "C7AE4D1F9BBDBFFA2CD27063A9FF59E483CC60F828CB3D9BFB66B65572C663DB" : (var1 ? "E2336A9161C31CC2BBB386C77BFE3459640573DC29F5B8AA7DA61E4270AF95B5" : (var16 ? "24C0C1D7A5D5E070F3D61A87EDE1691525B9ECBCA7BEDBCAD182B5366CC9EE27" : "012942E1A9203E659D43A1CEB5AB2DD70AEF6D01A8F4C4FDEA22B146DC088CB1")))));
            var15.println("GPT_TRAILER_SHA256=A0F9379853E2BD1CD9FF2854113CCB971B8A2A236C7C7A0736983EAA427C861B");
            var15.println("STOCK_ISO_SHA256=2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14");
            var15.println("ONE_DISK_ESP_PLUS_STOCK_ISO_PARTITION=PASS");
            var15.println("FRAME_BRIDGE_PLATFORM_ASSETS_PACKAGED_IN_APK=PASS");
            var15.println("CPU_TOPOLOGY_REQUEST=MATCH_HOST_AB");
            var15.println("OFFICIAL_ISO_MUTATED=NO");
            var15.println("PERSISTENT_GUEST_DISK=NO_LIVE_SESSION");
            var15.println("VM_LAUNCH=PASS");
         } catch (Throwable var24) {
            try {
               var15.close();
            } catch (Throwable var22) {
               var24.addSuppressed(var22);
            }

            throw var24;
         }

         var15.close();
         this.startUbuntuFrameBridge(var28);
         this.show("Ubuntu Live launched. This session is temporary; use Stop to power off.");
      } catch (Throwable var25) {
         if (!this.vmMayBeRunning) this.lifecycle.failStart(rootMessage(var25));
         Throwable var6 = var25;

         try {
            PrintWriter var7 = new PrintWriter(new FileOutputStream(var5, false));

            try {
               var7.println("PROFILE=FRAME_BRIDGE_TEST");
               var7.println("ERROR=" + rootMessage(var6));
            } catch (Throwable var20) {
               try {
                  var7.close();
               } catch (Throwable var19) {
                  var20.addSuppressed(var19);
               }

               throw var20;
            }

            var7.close();
         } catch (Throwable var21) {
         }

         this.runOnUiThread(() -> {
            this.frameVisible = false;
            this.status.setVisibility(8);
            this.renderProductScreen(this.isoPresent() ? MainActivity.ProductScreen.LINUX_HOME : MainActivity.ProductScreen.LINUX_SETUP);
         });
         this.show("FRAME_BRIDGE_TEST launch failed: " + rootMessage(var25));
         this.transition.failure(rootMessage(var25),android.os.SystemClock.elapsedRealtime());
      }

   }

   private File buildFrameBridgeLiveDisk(File var1, File var2, boolean var3, boolean var4) throws Exception {
      boolean var5 = this.getPreferences(0).getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false);
      if (var3 && !var5) {
         throw new IllegalStateException("Enable the existing Gfxstream/Vulkan experiment before Linux-first-GPU A/B");
      } else {
         boolean var6 = this.getPreferences(0).getBoolean("linux_gpu_virgl_gbm_xvnc_experimental", false) && !var5 && !var3;
         if (var4 && !var6) {
            throw new IllegalStateException("Linux-first VirGL requires the GBM Xvnc profile");
         } else {
            File var14;
            File var15;
            String var16;
            label562: {
               String var7 = var4 ? "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img" : (var3 ? "p7-platform-prefix-linux-first-gpu.img" : (var5 ? "p7-platform-prefix-gfxstream-zink.img" : (var6 ? "p7-platform-prefix-virgl-gbm.img" : "p33-platform-prefix-128m.img")));
               long var8 = var6 ? 134217728L : (var5 ? 134217728L : 134217728L);
               String var10 = var4 ? FRAME_BRIDGE_VIRGL_GBM_LINUX_FIRST_PREFIX_SHA256 : (var3 ? "E2336A9161C31CC2BBB386C77BFE3459640573DC29F5B8AA7DA61E4270AF95B5" : (var5 ? "24C0C1D7A5D5E070F3D61A87EDE1691525B9ECBCA7BEDBCAD182B5366CC9EE27" : (var6 ? "C7AE4D1F9BBDBFFA2CD27063A9FF59E483CC60F828CB3D9BFB66B65572C663DB" : "012942E1A9203E659D43A1CEB5AB2DD70AEF6D01A8F4C4FDEA22B146DC088CB1")));
               String var11 = var6 ? "" : (var3 ? "C2C670C75BC0534706078898A82DD683B87A491D320D153C44F2F0BB4D81A764" : (var5 ? "D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F" : "68582C7187EB83BEEE9E2D994BE4952FC5DE772BF2273AD1ECBCE6621B59BB47"));
               String var12 = var6 ? "winavf-P7-virgl-gbm-runtime.img" : (var5 ? "winavf-P7-gfxstream-zink-experimental.img" : "winavf-P35-rfb-incremental.img");
               String var13 = var6 ? "uavf-ubuntu-virgl-gbm-runtime.ready" : (var5 ? "uavf-ubuntu-zink-runtime.ready" : "uavf-ubuntu-live-runtime.ready");
               var14 = new File(var2, var12);
               var15 = new File(var2, var13);
               var16 = "ISO_SHA256=2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14\nPREFIX_SHA256=" + var10 + "\nTRAILER_SHA256=A0F9379853E2BD1CD9FF2854113CCB971B8A2A236C7C7A0736983EAA427C861B\nDISK_SHA256=" + (var11.isEmpty() ? "COMPONENT_HASHES" : var11) + "\nBYTES=4102029312\n";
               this.requireDisk(var1, 3967463424L, "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14", "official Ubuntu ISO");
               if (var14.isFile() && var14.length() == 4102029312L && var10.equals(hex(sha256FilePrefix(var14, var8)))) {
                  if (var6) {
                     if (this.matchesVirglGbmDiskParts(var14, var8)) {
                        break label562;
                     }
                  } else if (var11.equals(hex(sha256File(var14)))) {
                     break label562;
                  }
               }

               if (var6 && var14.isFile() && var14.length() == 4102029312L) {
                  String var17 = var4 ? BundledPlatformAssets.sha256("p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-real-gnome.img") : "925979C0DDF0A36CFC8BC756844D2CA341E4ED8B2B30906B0ED8ADCEE90CD978";
                  String var18 = var4 ? "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-real-gnome.img" : "p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img";
                  if (var17.equals(hex(sha256FilePrefix(var14, var8))) && this.matchesVirglGbmDiskParts(var14, var8)) {
                     try {
                        RandomAccessFile var63 = new RandomAccessFile(var14, "rw");

                        try {
                           this.copyVerifiedAssetToDisk(var7, var63, 0L, var8, var10, "VirGL firmware A/B prefix");
                           var63.getFD().sync();
                        } catch (Throwable var41) {
                           try {
                              var63.close();
                           } catch (Throwable var37) {
                              var41.addSuppressed(var37);
                           }

                           throw var41;
                        }

                        var63.close();
                        if (var10.equals(hex(sha256FilePrefix(var14, var8))) && this.matchesVirglGbmDiskParts(var14, var8)) {
                           FileOutputStream var64 = new FileOutputStream(var15, false);

                           try {
                              var64.write(var16.getBytes(StandardCharsets.US_ASCII));
                              var64.getFD().sync();
                           } catch (Throwable var40) {
                              try {
                                 var64.close();
                              } catch (Throwable var36) {
                                 var40.addSuppressed(var36);
                              }

                              throw var40;
                           }

                           var64.close();
                           return var14;
                        }

                        throw new SecurityException("VirGL firmware A/B disk verification failed");
                     } catch (Exception var42) {
                        RandomAccessFile var68 = new RandomAccessFile(var14, "rw");

                        try {
                           this.copyVerifiedAssetToDisk(var18, var68, 0L, var8, var17, "VirGL firmware A/B rollback");
                           var68.getFD().sync();
                        } catch (Throwable var35) {
                           try {
                              var68.close();
                           } catch (Throwable var34) {
                              var35.addSuppressed(var34);
                           }

                           throw var35;
                        }

                        var68.close();
                        if (var17.equals(hex(sha256FilePrefix(var14, var8))) && this.matchesVirglGbmDiskParts(var14, var8)) {
                           throw var42;
                        }

                        throw new SecurityException("Could not restore pre-A/B VirGL disk", var42);
                     }
                  }
               }

               if (var5 && var14.isFile() && var14.length() == 4102029312L) {
                  String var52 = var3 ? "24C0C1D7A5D5E070F3D61A87EDE1691525B9ECBCA7BEDBCAD182B5366CC9EE27" : "E2336A9161C31CC2BBB386C77BFE3459640573DC29F5B8AA7DA61E4270AF95B5";
                  String var57 = var3 ? "D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F" : "C2C670C75BC0534706078898A82DD683B87A491D320D153C44F2F0BB4D81A764";
                  String var19 = var3 ? "p7-platform-prefix-gfxstream-zink.img" : "p7-platform-prefix-linux-first-gpu.img";
                  if (var52.equals(hex(sha256FilePrefix(var14, var8))) && var57.equals(hex(sha256File(var14)))) {
                     try {
                        RandomAccessFile var66 = new RandomAccessFile(var14, "rw");

                        try {
                           this.copyVerifiedAssetToDisk(var7, var66, 0L, var8, var10, "Linux-first-GPU A/B prefix");
                           var66.getFD().sync();
                        } catch (Throwable var44) {
                           try {
                              var66.close();
                           } catch (Throwable var33) {
                              var44.addSuppressed(var33);
                           }

                           throw var44;
                        }

                        var66.close();
                        if (!var11.equals(hex(sha256File(var14)))) {
                           throw new SecurityException("Linux-first-GPU converted disk SHA-256 mismatch");
                        }

                        FileOutputStream var67 = new FileOutputStream(var15, false);

                        try {
                           var67.write(var16.getBytes(StandardCharsets.US_ASCII));
                           var67.getFD().sync();
                        } catch (Throwable var43) {
                           try {
                              var67.close();
                           } catch (Throwable var32) {
                              var43.addSuppressed(var32);
                           }

                           throw var43;
                        }

                        var67.close();
                        return var14;
                     } catch (Exception var45) {
                        RandomAccessFile var69 = new RandomAccessFile(var14, "rw");

                        try {
                           this.copyVerifiedAssetToDisk(var19, var69, 0L, var8, var52, "A/B rollback prefix");
                           var69.getFD().sync();
                        } catch (Throwable var31) {
                           try {
                              var69.close();
                           } catch (Throwable var30) {
                              var31.addSuppressed(var30);
                           }

                           throw var31;
                        }

                        var69.close();
                        if (!var57.equals(hex(sha256File(var14)))) {
                           throw new SecurityException("Could not restore exact pre-A/B disk", var45);
                        }

                        throw var45;
                     }
                  }
               }

               if (var5 && !var14.exists()) {
                  File var53 = new File(var2, "winavf-P35-rfb-incremental.img");
                  if (var53.isFile() && var53.length() == 4102029312L && "012942E1A9203E659D43A1CEB5AB2DD70AEF6D01A8F4C4FDEA22B146DC088CB1".equals(hex(sha256FilePrefix(var53, 134217728L))) && "68582C7187EB83BEEE9E2D994BE4952FC5DE772BF2273AD1ECBCE6621B59BB47".equals(hex(sha256File(var53)))) {
                     boolean var59 = false;

                     try {
                        RandomAccessFile var61 = new RandomAccessFile(var53, "rw");

                        try {
                           this.copyVerifiedAssetToDisk("p7-platform-prefix-gfxstream-zink.img", var61, 0L, 134217728L, "24C0C1D7A5D5E070F3D61A87EDE1691525B9ECBCA7BEDBCAD182B5366CC9EE27", "P7 Gfxstream/Zink test prefix");
                           var61.getFD().sync();
                        } catch (Throwable var47) {
                           try {
                              var61.close();
                           } catch (Throwable var29) {
                              var47.addSuppressed(var29);
                           }

                           throw var47;
                        }

                        var61.close();
                        String var62 = hex(sha256File(var53));
                        if (!"D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F".equals(var62)) {
                           throw new SecurityException("Converted Zink disk SHA-256 mismatch: " + var62);
                        }

                        if (!var53.renameTo(var14)) {
                           throw new IllegalStateException("Could not rename the verified Zink test disk.");
                        }

                        var59 = true;
                        FileOutputStream var65 = new FileOutputStream(var15, false);

                        try {
                           var65.write(var16.getBytes(StandardCharsets.US_ASCII));
                           var65.getFD().sync();
                        } catch (Throwable var46) {
                           try {
                              var65.close();
                           } catch (Throwable var28) {
                              var46.addSuppressed(var28);
                           }

                           throw var46;
                        }

                        var65.close();
                        return var14;
                     } catch (Exception var51) {
                        File var20 = var59 ? var14 : var53;
                        if (var20.isFile()) {
                           RandomAccessFile var21 = new RandomAccessFile(var20, "rw");

                           try {
                              this.copyVerifiedAssetToDisk("p33-platform-prefix-128m.img", var21, 0L, 134217728L, "012942E1A9203E659D43A1CEB5AB2DD70AEF6D01A8F4C4FDEA22B146DC088CB1", "P7 rollback prefix");
                              var21.getFD().sync();
                           } catch (Throwable var27) {
                              try {
                                 var21.close();
                              } catch (Throwable var26) {
                                 var27.addSuppressed(var26);
                              }

                              throw var27;
                           }

                           var21.close();
                           if (!"68582C7187EB83BEEE9E2D994BE4952FC5DE772BF2273AD1ECBCE6621B59BB47".equals(hex(sha256File(var20)))) {
                              throw new SecurityException("Could not restore the verified P7 temporary disk after Zink conversion failed.", var51);
                           }

                           if (var59 && !var14.renameTo(var53)) {
                              throw new SecurityException("Restored P7 disk, but could not return it to its baseline filename.", var51);
                           }
                        }

                        if (var15.exists()) {
                           var15.delete();
                        }

                        throw var51;
                     }
                  }
               }

               if (var1.length() != 3967463424L) {
                  throw new IllegalArgumentException("Select the official Ubuntu 24.04.5 ARM64 Desktop ISO.");
               }

               if (!var14.exists() && var2.getUsableSpace() < 4370464768L) {
                  throw new IllegalStateException("Free at least 4.1 GiB in app storage to prepare the temporary Linux boot disk.");
               }

               if (var15.exists() && !var15.delete()) {
                  throw new IllegalStateException("Cannot invalidate temporary-media cache before rebuild");
               }

               try {
                  RandomAccessFile var54 = new RandomAccessFile(var14, "rw");

                  try {
                     var54.setLength(0L);
                     var54.setLength(4102029312L);
                     this.copyVerifiedAssetToDisk(var7, var54, 0L, var8, var10, var5 ? "P7 Gfxstream/Zink test prefix" : "P7 platform prefix");
                     this.copyVerifiedFileToDisk(var1, var54, 134217728L, 3967463424L, "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14", "Ubuntu ISO");
                     this.copyVerifiedAssetToDisk("p33-gpt-trailer.bin", var54, 4102012416L, 16896L, "A0F9379853E2BD1CD9FF2854113CCB971B8A2A236C7C7A0736983EAA427C861B", "backup GPT trailer");
                     var54.getFD().sync();
                  } catch (Throwable var49) {
                     try {
                        var54.close();
                     } catch (Throwable var25) {
                        var49.addSuppressed(var25);
                     }

                     throw var49;
                  }

                  var54.close();
               } catch (Exception var50) {
                  throw var50;
               }

               if (var14.length() != 4102029312L) {
                  var14.delete();
                  throw new IllegalStateException("Prepared Linux disk has the wrong size");
               }

               String var55 = hex(sha256File(var14));
               if ((var6 || var11.equals(var55)) && (!var6 || var10.equals(hex(sha256FilePrefix(var14, var8))))) {
                  FileOutputStream var58 = new FileOutputStream(var15, false);

                  try {
                     var58.write(var16.getBytes(StandardCharsets.US_ASCII));
                     var58.getFD().sync();
                  } catch (Throwable var48) {
                     try {
                        var58.close();
                     } catch (Throwable var24) {
                        var48.addSuppressed(var24);
                     }

                     throw var48;
                  }

                  var58.close();
                  return var14;
               }

               var14.delete();
               throw new SecurityException("Prepared Linux disk integrity check failed: " + var55);
            }

            FileOutputStream var56 = new FileOutputStream(var15, false);

            try {
               var56.write(var16.getBytes(StandardCharsets.US_ASCII));
               var56.getFD().sync();
            } catch (Throwable var39) {
               try {
                  var56.close();
               } catch (Throwable var38) {
                  var39.addSuppressed(var38);
               }

               throw var39;
            }

            var56.close();
            return var14;
         }
      }
   }

   private boolean matchesVirglGbmDiskParts(File var1, long var2) throws Exception {
      if (var1.length() != 4102029312L) {
         return false;
      } else {
         return !"2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14".equals(hex(sha256FileRange(var1, var2, 3967463424L))) ? false : "A0F9379853E2BD1CD9FF2854113CCB971B8A2A236C7C7A0736983EAA427C861B".equals(hex(sha256FileRange(var1, 4102012416L, 16896L)));
      }
   }

   private void copyVerifiedAssetToDisk(String var1, RandomAccessFile var2, long var3, long var5, String var7, String var8) throws Exception {
      InputStream var9 = this.getAssets().open(var1);

      try {
         this.copyVerifiedStreamToDisk(var9, var2, var3, var5, var7, var8);
      } catch (Throwable var13) {
         if (var9 != null) {
            try {
               var9.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }
         }

         throw var13;
      }

      if (var9 != null) {
         var9.close();
      }

   }

   private void copyVerifiedFileToDisk(File var1, RandomAccessFile var2, long var3, long var5, String var7, String var8) throws Exception {
      BufferedInputStream var9 = new BufferedInputStream(new FileInputStream(var1));

      try {
         this.copyVerifiedStreamToDisk(var9, var2, var3, var5, var7, var8);
      } catch (Throwable var13) {
         try {
            ((InputStream)var9).close();
         } catch (Throwable var12) {
            var13.addSuppressed(var12);
         }

         throw var13;
      }

      ((InputStream)var9).close();
   }

   private void copyVerifiedStreamToDisk(InputStream var1, RandomAccessFile var2, long var3, long var5, String var7, String var8) throws Exception {
      MessageDigest var9 = MessageDigest.getInstance("SHA-256");
      byte[] var10 = new byte[1048576];
      long var11 = 0L;
      var2.seek(var3);

      int var13;
      while((var13 = var1.read(var10)) >= 0) {
         if (var13 != 0) {
            var11 += (long)var13;
            if (var11 > var5) {
               throw new IllegalArgumentException(var8 + " is larger than expected");
            }

            var9.update(var10, 0, var13);
            var2.write(var10, 0, var13);
         }
      }

      if (var11 != var5 || !var7.equals(hex(var9.digest()))) {
         throw new SecurityException(var8 + " size/SHA-256 verification failed");
      }
   }

   private static String readSmallText(File var0) {
      try {
         FileInputStream var1 = new FileInputStream(var0);

         String var5;
         label45: {
            String var9;
            try {
               ByteArrayOutputStream var2 = new ByteArrayOutputStream();
                byte[] var3 = new byte[1024];

                int var4;
                while((var4 = ((InputStream)var1).read(var3)) >= 0) {
                  if (var4 != 0) {
                     if (var2.size() + var4 > 8192) {
                        var5 = "";
                        break label45;
                     }

                     var2.write(var3, 0, var4);
                  }
               }

               var9 = var2.toString(StandardCharsets.US_ASCII);
            } catch (Throwable var7) {
               try {
                  ((InputStream)var1).close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }

               throw var7;
            }

            ((InputStream)var1).close();
            return var9;
         }

         ((InputStream)var1).close();
         return var5;
      } catch (Throwable var8) {
         return "";
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
      call(var26, "setWidth", Integer.TYPE, managedWorkspace ? workspace.width : 1280);
      call(var26, "setHeight", Integer.TYPE, managedWorkspace ? workspace.height : 800);
      call(var26, "setHorizontalDpi", Integer.TYPE, 160);
      call(var26, "setVerticalDpi", Integer.TYPE, 160);
      int guestRefreshHz = managedWorkspace && getIntent() != null
            ? getIntent().getIntExtra("encoded_guest_refresh_hz", 60) : 60;
      if (guestRefreshHz != 60 && guestRefreshHz != 90 && guestRefreshHz != 120) {
         throw new IllegalArgumentException("Diagnostic guest refresh must be 60, 90 or 120 Hz");
      }
      call(var26, "setRefreshRate", Integer.TYPE, guestRefreshHz);
      Log.i("UAVF-H264", "Guest virtual display refresh request=" + guestRefreshHz);
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

         String var17 = hex(sha256File(var16));
         if (!V22_EXT4_CONTROL_MEDIA_SHA256.equals(var17)) {
            throw new SecurityException("V22 control image hash mismatch: " + var17);
         }

         File var4 = new File(this.getFilesDir(), "v22-ext4-control-payload");
         if (!var4.exists() && !var4.mkdirs()) {
            throw new IllegalStateException("Cannot create V22 payload directory");
         }

         File var5 = this.copyFile(var16, new File(var4, "ubuntu-gnome-v22-ext4-control.img"), 9126805504L, false);
         String var6 = hex(sha256File(var5));
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
               if (!V22_EXT4_CONTROL_MEDIA_SHA256.equals(hex(sha256File(var14)))) {
                  var4.println("EXT4_BLOCK_IO=BLOCKED");
                  var4.println("REASON=V22 control disk hash mismatch");
               } else if (var13.isFile() && var13.length() != 0L) {
                  var4.println("CONTROL_DISK_SHA256=" + hex(sha256File(var14)));
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
                  byte[] var6 = readExactly(var5.getInputStream(), var1.length());
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

   private static byte[] readExactly(InputStream var0, int var1) throws Exception {
      byte[] var2 = new byte[var1];

      int var4;
      for(int var3 = 0; var3 < var1; var3 += var4) {
         var4 = var0.read(var2, var3, var1 - var3);
         if (var4 < 0) {
            throw new EOFException("short bridge capability token");
         }
      }

      return var2;
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
            closeQuietly(var3);
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
            closeQuietly(var3);
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

      closeQuietly(this.activeKdBridgeSocket);
      this.show("Product KD bridge stop requested.");
   }

   private static void closeQuietly(Socket var0) {
      if (var0 != null) {
         try {
            var0.close();
         } catch (Throwable var2) {
         }

      }
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
         byte[] var4 = readFat32File(var3, "EFI", "MICROSOFT", "BOOT", "BCD");
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
            var13.println("bcdSha256=" + hex(sha256(var4)));
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

   private static byte[] readFat32File(File var0, String... var1) throws Exception {
      RandomAccessFile var2 = new RandomAccessFile(var0, "r");

      byte[] var20;
      try {
         var2.seek(1048576L);
         byte[] var3 = new byte[512];
         var2.readFully(var3);
         if (var3[82] != 70 || var3[83] != 65 || var3[84] != 84 || var3[85] != 51 || var3[86] != 50) {
            throw new IllegalStateException("Expected FAT32 partition");
         }

         int var4 = u16(var3, 11);
         int var5 = var3[13] & 255;
         int var6 = u16(var3, 14);
         int var7 = var3[16] & 255;
         long var8 = u32(var3, 36);
         long var10 = u32(var3, 44);
         long var12 = (long)var4 * (long)var5;
         long var14 = 1048576L + (long)var6 * (long)var4;
         long var16 = 1048576L + ((long)var6 + (long)var7 * var8) * (long)var4;
         int var18 = 0;

         while(true) {
            if (var18 >= var1.length) {
               throw new IllegalStateException("Empty FAT path");
            }

            FatEntry var19 = findFatEntry(var2, var14, var16, var12, var10, var1[var18]);
            if (var19 == null) {
               throw new IllegalStateException("FAT entry not found: " + var1[var18]);
            }

            if (var18 == var1.length - 1) {
               var20 = readFatFile(var2, var14, var16, var12, var19.cluster, var19.size);
               break;
            }

            if ((var19.attributes & 16) == 0) {
               throw new IllegalStateException("Expected directory: " + var1[var18]);
            }

            var10 = var19.cluster;
            ++var18;
         }
      } catch (Throwable var22) {
         try {
            var2.close();
         } catch (Throwable var21) {
            var22.addSuppressed(var21);
         }

         throw var22;
      }

      var2.close();
      return var20;
   }

   private static FatEntry findFatEntry(RandomAccessFile var0, long var1, long var3, long var5, long var7, String var9) throws Exception {
      for(long var13 : walkFatChain(var0, var1, var7)) {
         byte[] var15 = new byte[(int)var5];
         var0.seek(var3 + (var13 - 2L) * var5);
         var0.readFully(var15);
         String[] var16 = new String[21];

         for(int var17 = 0; var17 < var15.length; var17 += 32) {
            int var18 = var15[var17] & 255;
            if (var18 == 0) {
               return null;
            }

            if (var18 == 229) {
               Arrays.fill(var16, (Object)null);
            } else if ((var15[var17 + 11] & 255) == 15) {
               int var19 = var15[var17] & 31;
               if (var19 > 0 && var19 < var16.length) {
                  var16[var19] = decodeFatLongNamePart(var15, var17);
               }
            } else {
               String var26 = (new String(var15, var17, 8, StandardCharsets.US_ASCII)).trim();
               String var20 = (new String(var15, var17 + 8, 3, StandardCharsets.US_ASCII)).trim();
               String var21 = var20.isEmpty() ? var26 : var26 + "." + var20;
               StringBuilder var22 = new StringBuilder();

               for(int var23 = 1; var23 < var16.length; ++var23) {
                  if (var16[var23] != null) {
                     var22.append(var16[var23]);
                  }
               }

               String var27 = var22.length() == 0 ? var21 : var22.toString();
               Arrays.fill(var16, (Object)null);
               if (var9.equalsIgnoreCase(var27) || var9.equalsIgnoreCase(var21)) {
                  long var24 = (long)u16(var15, var17 + 20) << 16 | (long)u16(var15, var17 + 26);
                  return new FatEntry(var24, u32(var15, var17 + 28), var15[var17 + 11] & 255);
               }
            }
         }
      }

      return null;
   }

   private static String decodeFatLongNamePart(byte[] var0, int var1) {
      int[] var2 = new int[]{1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30};
      StringBuilder var3 = new StringBuilder();

      for(int var7 : var2) {
         int var8 = u16(var0, var1 + var7);
         if (var8 == 0 || var8 == 65535) {
            break;
         }

         var3.append((char)var8);
      }

      return var3.toString();
   }

   private static long[] walkFatChain(RandomAccessFile var0, long var1, long var3) throws Exception {
       ArrayList<Long> var5 = new ArrayList<>();
      HashSet var6 = new HashSet();

      byte[] var9;
      for(long var7 = var3; var7 >= 2L && var7 < 268435448L; var7 = u32(var9, 0) & 268435455L) {
         if (!var6.add(var7)) {
            throw new IllegalStateException("FAT loop at " + var7);
         }

         var5.add(var7);
         var0.seek(var1 + var7 * 4L);
         var9 = new byte[4];
         var0.readFully(var9);
      }

      return var5.stream().mapToLong(Long::longValue).toArray();
   }

   private static byte[] readFatFile(RandomAccessFile var0, long var1, long var3, long var5, long var7, long var9) throws Exception {
      if (var9 > 2147483647L) {
         throw new IllegalStateException("Read-only export is bounded to 2 GiB");
      } else {
         byte[] var11 = new byte[(int)var9];
         int var12 = 0;

         for(long var16 : walkFatChain(var0, var1, var7)) {
            int var18 = (int)Math.min(var5, var9 - (long)var12);
            if (var18 <= 0) {
               break;
            }

            var0.seek(var3 + (var16 - 2L) * var5);
            var0.readFully(var11, var12, var18);
            var12 += var18;
         }

         if ((long)var12 != var9) {
            throw new IllegalStateException("FAT chain shorter than file size");
         } else {
            return var11;
         }
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
                                             this.getPreferences(0).edit().putBoolean("ubuntu_installed_runtime_ready", true).apply();
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

   private void startUbuntuFrameBridge(Object var1) {
      this.startUbuntuFrameBridge(var1,false);
   }

   private void startUbuntuFrameBridge(Object var1, boolean rawOnly) {
      if(this.installedEncodedDisplay!=null) { this.installedEncodedDisplay.closeSilently(); this.installedEncodedDisplay=null; }
      try {
         this.lifecycle.reconcile(var1,this.readAvfState(var1),this.persistentUbuntuDiskFile().isFile());
      } catch(Exception e) { this.lifecycle.failed(var1,"AVF state unavailable: " + rootMessage(e)); }
      if (this.controlVm != var1 || this.guestControl == null) {
         if(this.guestControl != null) this.guestControl.close();
         this.controlVm=var1;
         this.guestControl=new GuestControl(var1,(connected,detail,session)->{
            if(!this.activityDestroyed && this.lifecycleVm==var1) {
               if(!detail.equals(this.guestControlStatus)) Log.i("U-AVF-control",detail);
               this.guestControlStatus=detail;
               if(connected && session!=null) {
                  if(session.optBoolean("growth_supported") && session.optInt("growth_version")==1) {
                     android.content.SharedPreferences.Editor growth=this.getPreferences(0).edit().putBoolean("ubuntu_growth_helper_verified",true);
                     long pending=this.getPreferences(0).getLong("ubuntu_growth_pending_bytes",0);
                     if(pending>0 && session.optLong("filesystem_bytes")>=pending)growth.remove("ubuntu_growth_pending_bytes");
                     growth.apply();
                  }
                  if(session.optBoolean("ok") && session.optBoolean("sessions_available"))
                     this.getPreferences(0).edit().putString("runtime_verified_build",Build.FINGERPRINT).apply();
                  this.transition.guestReady(session.optBoolean("sessions_available"),android.os.SystemClock.elapsedRealtime());
                  this.runOnUiThread(()->{if(this.transitionView!=null)this.transitionView.refresh();});
               }
            }
         });
      }
      if(this.encodedDisplayVm!=var1) {
         this.encodedSessionReconnects=0;
         this.encodedSessionReconnectWindowMs=0;
      }
      this.encodedDisplayVm = var1;
      final int connectionGeneration;
      synchronized (this.frameConnectionLock) {
         connectionGeneration = ++this.frameConnectionGeneration;
         if (this.activeFrameSocket != null) {
            try { Os.shutdown(this.activeFrameSocket.getFileDescriptor(), android.system.OsConstants.SHUT_RDWR); }
            catch (Exception ignored) { }
         }
         this.activeFrameInput = null;
      }
      this.startUbuntuAudioBridge(var1);
      this.runOnUiThread(() -> {
         if (this.isFinishing() || this.activityDestroyed) return;
         if (this.clipboardVm != var1) {
            if (this.guestClipboard != null) this.guestClipboard.close();
            this.guestClipboard = null;
            this.clipboardVm = var1;
         }
         this.syncGuestClipboard();
      });
      if(!rawOnly && this.getPreferences(0).getBoolean("linux_current_boot_encoded",
            this.getPreferences(0).getBoolean("linux_current_boot_installed",false))
            && this.getPreferences(0).getBoolean("linux_installed_encoded_display",true)) {
         this.runOnUiThread(()-> {
            if(this.activityDestroyed || this.encodedDisplayVm!=var1) return;
            if(this.installedEncodedDisplay!=null) this.installedEncodedDisplay.closeSilently();
            if(this.installedEncodedSurface!=null) this.page.removeView(this.installedEncodedSurface);
            android.view.SurfaceView surface=new android.view.SurfaceView(this);
            this.installedEncodedSurface=surface;
            FrameLayout.LayoutParams bounds=new FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER);
            this.page.addView(surface,0,bounds);
            surface.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)-> {
               int pw=this.page.getWidth(), ph=this.page.getHeight();
               int w=Math.min(pw,ph*1920/1200), h=w*1200/1920;
               FrameLayout.LayoutParams fit=(FrameLayout.LayoutParams)surface.getLayoutParams();
               if(w>0 && h>0 && (fit.width!=w || fit.height!=h)) { fit.width=w;fit.height=h;surface.setLayoutParams(fit); }
               // Hide the known one-source-pixel left-edge artifact without
               // scaling/translation: the input viewport coordinates stay exact.
               if(r>l && b>t) surface.setClipBounds(new android.graphics.Rect(
                     Math.max(1,((r-l)+1919)/1920),0,r-l,b-t));
            });
            final EncodedDisplayProbe receiver=new EncodedDisplayProbe(this,var1,60,()-> {
               if(this.activityDestroyed || this.installedEncodedSurface!=surface) return;
               long reconnectNow=android.os.SystemClock.elapsedRealtime();
               if(reconnectNow-this.encodedSessionReconnectWindowMs>60000) {
                  this.encodedSessionReconnectWindowMs=reconnectNow;
                  this.encodedSessionReconnects=0;
               }
               boolean retrySession=this.installedEncodedDisplay!=null &&
                     this.installedEncodedDisplay.retryableSessionEnd() &&
                     this.encodedSessionReconnects<3 && this.isManagedVmRunning();
               this.page.removeView(surface); this.installedEncodedSurface=null;
               this.installedEncodedDisplay=null;
               this.frameSurface.setEncodedMode(false,0,0);
               this.activeFrameInput=null;
               if(retrySession) {
                  this.encodedSessionReconnects++;
                  this.guestKeyboard.releaseAll();
                  Log.i("UAVF-H264","Reconnecting after guest X11 session handoff");
                  this.uiHandler.postDelayed(()-> {
                     if(!this.activityDestroyed && this.encodedDisplayVm==var1 &&
                           this.isManagedVmRunning()) this.startUbuntuFrameBridge(var1);
                  },1000);
                  return;
               }
               this.show("Encoded display unavailable. Restoring stable display.");
               this.startUbuntuFrameBridge(var1,true);
            });
            this.installedEncodedDisplay=receiver;
            receiver.startEmbedded(surface,input-> {
               synchronized(this.frameConnectionLock) {
                  if(connectionGeneration==this.frameConnectionGeneration) {
                     this.activeFrameInput=input;
                     this.transition.event("Display connected","Encoded vsock stream",android.os.SystemClock.elapsedRealtime());
                  }
               }
            },()-> {
               if(this.installedEncodedSurface!=surface) return;
               if(!this.transition.acceptsDesktop()) return;
               this.completeDesktopTransition();
               this.guestFrameWidth=1920; this.guestFrameHeight=1200;
               this.frameSurface.setEncodedMode(true,1920,1200);
               if(!this.frameVisible) { this.frameVisible=true; this.showRunningDesktop(); }
            },()-> {
               if(!this.activityDestroyed && this.installedEncodedSurface==surface &&
                  this.encodedDisplayVm==var1) {
                  this.guestKeyboard.releaseAll();
                  this.startUbuntuFrameBridge(var1);
               }
            });
         });
         return;
      }
      if(this.installedEncodedSurface!=null) {
         this.page.removeView(this.installedEncodedSurface);this.installedEncodedSurface=null;
         this.frameSurface.setEncodedMode(false,0,0);
      }
      (new Thread(() -> {
         File var2 = new File(this.getExternalFilesDir((String)null), "ubuntu-gnome-frame-bridge-report.txt");
         final AtomicInteger var3 = new AtomicInteger();
         final AtomicInteger var4 = new AtomicInteger();
         final AtomicInteger var5 = new AtomicInteger();
         final AtomicBoolean var6 = new AtomicBoolean();
         AtomicLong var7 = new AtomicLong();
         ConsoleFrameDecoder var8 = new ConsoleFrameDecoder(new ConsoleFrameDecoder.Listener() {
            public void onFrame(ConsoleFrameDecoder.Frame var1) {
               synchronized (MainActivity.this.frameConnectionLock) {
                  if (connectionGeneration != MainActivity.this.frameConnectionGeneration) return;
               }
               var3.incrementAndGet();
               boolean var2 = var6.get();
               if (!var2) {
                  short var3x = 256;

                  for(int var4x = 0; var4x + 2 < var1.bgra.length; var4x += var3x) {
                     if (var1.bgra[var4x] != 0 || var1.bgra[var4x + 1] != 0 || var1.bgra[var4x + 2] != 0) {
                        var2 = true;
                        break;
                     }
                  }
               }

               if (!var2) {
                  var4.incrementAndGet();
               } else {
                  var5.incrementAndGet();
                  var6.set(true);
                  MainActivity.this.guestFrameWidth = var1.width;
                  MainActivity.this.guestFrameHeight = var1.height;
                  MainActivity.this.queueFrameForDisplay(var1);
               }
            }

            public void onProtocolError(String var1) {
               Log.w("U-AVF", "Frame bridge WAVF: " + var1);
            }
         });

         try {
            synchronized (this.frameConnectionLock) {
               if (connectionGeneration != this.frameConnectionGeneration) return;
            }
            PrintWriter var67 = new PrintWriter(new FileOutputStream(var2, false));

            try {
               var67.println("transport=AVF_CONNECT_VSOCK");
               var67.println("guest=UBUNTU_XVNC_X11_CAPTURE");
               var67.println("port=4052");
               var67.println("lz4Decoder=" + (WavfNative.AVAILABLE ? "NATIVE_BOUNDED_PARITY_PASS" : "JAVA_FALLBACK"));
               Method var68 = var1.getClass().getMethod("connectVsock", Long.TYPE);
               ParcelFileDescriptor var11 = null;
               Throwable var12 = null;
               long var13 = System.currentTimeMillis() + 300000L;

               while(System.currentTimeMillis() < var13) {
                  synchronized (this.frameConnectionLock) {
                     if (connectionGeneration != this.frameConnectionGeneration) return;
                  }
                  try {
                     var11 = (ParcelFileDescriptor)var68.invoke(var1, 4052L);
                     synchronized (this.frameConnectionLock) {
                        if (connectionGeneration != this.frameConnectionGeneration) {
                           var11.close();
                           return;
                        }
                        this.activeFrameSocket = var11;
                     }
                     break;
                  } catch (Throwable var64) {
                     var12 = var64;
                     Thread.sleep(1000L);
                  }
               }

               if (var11 == null) {
                  throw new IllegalStateException("vsock 4052 listener did not become available", var12);
               }

               try {
                  FileInputStream var15 = new FileInputStream(var11.getFileDescriptor());

                  try {
                     FileOutputStream var16 = new FileOutputStream(var11.getFileDescriptor());

                     try {
                        this.activeFrameInput = var16;
                        byte[] var17 = new byte[65536];
                        boolean var18 = false;
                        long var19 = System.currentTimeMillis();
                        int var21 = 0;
                        long var22 = 0L;
                        long[] var24 = this.frameSurface.performanceSnapshot();
                        long decodeFeedNanos = 0L;
                        long reportedDecodeFeedNanos = 0L;
                        long[] reportedDecoderCosts = var8.costSnapshot();

                        while(true) {
                           int var25 = var15.read(var17);
                           synchronized (this.frameConnectionLock) {
                              if (connectionGeneration != this.frameConnectionGeneration) return;
                           }
                           if (var25 < 0) {
                              var67.println("streamClosed=YES");
                              var67.println("bridgeFrameCount=" + var3.get());
                              var67.println("blackFrameCount=" + var4.get());
                              var67.println("nonBlackFrameCount=" + var5.get());
                              var67.println("rawFrameArchive=DISABLED_NORMAL_RUNTIME");
                              break;
                           }

                           if (var25 != 0) {
                              var7.addAndGet((long)var25);
                              long decodeFeedStarted = System.nanoTime();
                              var8.feed(var17, 0, var25);
                              decodeFeedNanos += System.nanoTime() - decodeFeedStarted;
                              if (!var18 && var5.get() > 0) {
                                 for(int var26 = 0; var26 < 20 && !var6.get(); ++var26) {
                                    Thread.sleep(10L);
                                 }

                                 String var10001 = var6.get() ? "FRAME_TRANSPORT_TO_ANDROID_PASS" : "FRAME_DECODED_PRESENTATION_NOT_CONFIRMED";
                                 var67.println("result=" + var10001);
                                 var10001 = var6.get() ? "GNOME_VISIBLE_IN_APP_PASS" : "DECODED_NOT_PRESENTED";
                                 var67.println("firstFrame=" + var10001);
                                 var67.println("stream=CONTINUOUS_UNTIL_VM_STOP");
                                 var67.flush();
                                 this.show(var6.get() ? "Real Ubuntu GNOME frame presented in Android." : "Ubuntu frame decoded but presentation not confirmed.");
                                 var18 = true;
                              }

                              long var69 = System.currentTimeMillis();
                              if (var69 - var19 >= 10000L) {
                                 long[] var28 = this.frameSurface.performanceSnapshot();
                                 long var29 = var69 - var19;
                                 long var31 = var28[0] - var24[0];
                                 long var33 = var28[1] - var24[1];
                                 long var35 = var28[2] - var24[2];
                                 long var37 = var28[3] - var24[3];
                                 int var39 = var3.get();
                                 long var40 = var7.get();
                                 var67.println(String.format(Locale.US, "PERF interval_ms=%d decoded_fps=%.2f prepared_fps=%.2f drawn_fps=%.2f vsock_bytes_per_s=%.0f prepare_ms=%.2f draw_call_ms=%.2f", var29, (double)1000.0F * (double)(var39 - var21) / (double)var29, (double)1000.0F * (double)var31 / (double)var29, (double)1000.0F * (double)var33 / (double)var29, (double)1000.0F * (double)(var40 - var22) / (double)var29, (double)var35 / ((double)1000000.0F * (double)Math.max(1L, var31)), (double)var37 / ((double)1000000.0F * (double)Math.max(1L, var33))));
                                 var67.println("frameCount=" + var3.get());
                                 var67.println(String.format(Locale.US, "DECODE feed_including_prepare_ms=%.2f", (decodeFeedNanos - reportedDecodeFeedNanos) / (1000000.0 * Math.max(1, var39 - var21))));
                                 reportedDecodeFeedNanos = decodeFeedNanos;
                                 long[] decoderCosts = var8.costSnapshot();
                                 double decoderFrames = Math.max(1, var39 - var21);
                                 var67.println(String.format(Locale.US,
                                       "DECODE_COST crc_ms=%.2f lz4_ms=%.2f pixel_copy=%s",
                                       (decoderCosts[0] - reportedDecoderCosts[0]) / (1000000.0 * decoderFrames),
                                       (decoderCosts[1] - reportedDecoderCosts[1]) / (1000000.0 * decoderFrames),
                                       WavfNative.AVAILABLE ? "NATIVE_NUMERIC_ARGB" : "JAVA"));
                                 reportedDecoderCosts = decoderCosts;
                                 var67.println("nonBlackFrameCount=" + var5.get());
                                 var67.println("rawFrameArchive=DISABLED_NORMAL_RUNTIME");
                                 var67.flush();
                                 var21 = var39;
                                 var22 = var40;
                                 var24 = var28;
                                 var19 = var69;
                              }
                           }
                        }
                     } catch (Throwable var61) {
                        try {
                           var16.close();
                        } catch (Throwable var58) {
                           var61.addSuppressed(var58);
                        }

                        throw var61;
                     }

                     var16.close();
                  } catch (Throwable var62) {
                     try {
                        var15.close();
                     } catch (Throwable var57) {
                        var62.addSuppressed(var57);
                     }

                     throw var62;
                  }

                  var15.close();
               } finally {
                  synchronized (this.frameConnectionLock) {
                     if (this.activeFrameSocket == var11) {
                        this.activeFrameInput = null;
                        this.activeFrameSocket = null;
                     }
                  }
                  var11.close();
               }
            } catch (Throwable var65) {
               try {
                  var67.close();
               } catch (Throwable var56) {
                  var65.addSuppressed(var56);
               }

               throw var65;
            }

            var67.close();
         } catch (Throwable var66) {
            synchronized (this.frameConnectionLock) {
               if (connectionGeneration != this.frameConnectionGeneration) return;
            }
            Throwable var9 = var66;

            try {
               PrintWriter var10 = new PrintWriter(new FileOutputStream(var2, true));

               try {
                  var10.println("streamEnd=" + rootMessage(var9));
                  var10.println("bridgeFrameCount=" + var3.get());
                  var10.println("nonBlackFrameCount=" + var5.get());
                  if (!var6.get()) {
                     var10.println("result=FRAME_TRANSPORT_TO_ANDROID_ERROR");
                  }
               } catch (Throwable var59) {
                  try {
                     var10.close();
                  } catch (Throwable var55) {
                     var59.addSuppressed(var55);
                  }

                  throw var59;
               }

               var10.close();
            } catch (Throwable var60) {
            }

            if (!var6.get()) {
               this.show("Ubuntu frame bridge failed: " + rootMessage(var66));
            }
         }

      }, "WinAVF-ubuntu-frame-bridge")).start();
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
                        readExactly(var10, var11);
                        ByteBuffer var12 = ByteBuffer.wrap(var11).order(ByteOrder.LITTLE_ENDIAN);
                        if (var11[0] != 85 || var11[1] != 65 || var11[2] != 86 || var11[3] != 70 || u16(var11, 4) != 1 || u16(var11, 6) != 2 || var12.getInt(8) != 48000 || u16(var11, 12) != 1) {
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

   private static void readExactly(InputStream var0, byte[] var1) throws Exception {
      int var3;
      for(int var2 = 0; var2 < var1.length; var2 += var3) {
         var3 = var0.read(var1, var2, var1.length - var2);
         if (var3 < 0) {
            throw new IllegalStateException("short guest PCM header");
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
            if (!patchTargetsBaseline(var4, var2)) {
               throw new IllegalStateException("An earlier image patch is still active; rollback it before applying another patch");
            }

            if (!var4.delete()) {
               throw new IllegalStateException("Could not discard the unapplied image patch record");
            }

            this.show("Discarded a patch record that was never applied to the baseline image.");
         }

         this.copyFile(var3, var4, -1L, true);

         try {
            applyPatch(var4, var2, false);
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

         applyPatch(var4, var15, true);
         if (!var4.delete()) {
            throw new IllegalStateException("Rollback passed but the active patch record could not be removed");
         }

         PrintWriter var5 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var5.println("RESULT=PASS");
            var5.println("BASELINE_SHA256=" + hex(sha256File(var15)));
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

   private static int readLittleEndianInt(RandomAccessFile var0) throws Exception {
      int var1 = var0.readUnsignedByte();
      int var2 = var0.readUnsignedByte();
      int var3 = var0.readUnsignedByte();
      int var4 = var0.readUnsignedByte();
      return var1 | var2 << 8 | var3 << 16 | var4 << 24;
   }

   private static long readLittleEndianLong(RandomAccessFile var0) throws Exception {
      return (long)readLittleEndianInt(var0) & 4294967295L | (long)readLittleEndianInt(var0) << 32;
   }

   private static boolean patchTargetsBaseline(File var0, File var1) throws Exception {
      RandomAccessFile var2 = new RandomAccessFile(var0, "r");

      boolean var9;
      label41: {
         label40: {
            boolean var5;
            try {
               byte[] var3 = new byte[IMAGE_PATCH_MAGIC.length];
               var2.readFully(var3);
               if (!Arrays.equals(var3, IMAGE_PATCH_MAGIC) || readLittleEndianInt(var2) != 1) {
                  var9 = false;
                  break label41;
               }

               if (readLittleEndianLong(var2) != var1.length()) {
                  var9 = false;
                  break label40;
               }

               byte[] var4 = new byte[32];
               var2.readFully(var4);
               var5 = Arrays.equals(var4, sha256File(var1));
            } catch (Throwable var7) {
               try {
                  var2.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }

               throw var7;
            }

            var2.close();
            return var5;
         }

         var2.close();
         return var9;
      }

      var2.close();
      return var9;
   }

   private static void applyPatch(File var0, File var1, boolean var2) throws Exception {
      RandomAccessFile var3 = new RandomAccessFile(var0, "r");

      try {
         byte[] var4 = new byte[IMAGE_PATCH_MAGIC.length];
         var3.readFully(var4);
         if (!Arrays.equals(var4, IMAGE_PATCH_MAGIC)) {
            throw new IllegalStateException("Unsupported image patch magic");
         }

         if (readLittleEndianInt(var3) != 1) {
            throw new IllegalStateException("Unsupported image patch version");
         }

         long var5 = readLittleEndianLong(var3);
         if (var5 != var1.length()) {
            throw new IllegalStateException("Patch targets a different runtime image size");
         }

         byte[] var7 = new byte[32];
         var3.readFully(var7);
         int var8 = readLittleEndianInt(var3);
         if (var8 < 1 || var8 > 4096) {
            throw new IllegalStateException("Invalid image patch range count");
         }

         PatchRange[] var9 = new PatchRange[var8];

         for(int var10 = 0; var10 < var8; ++var10) {
            long var11 = readLittleEndianLong(var3);
            int var13 = readLittleEndianInt(var3);
            if (var11 < 0L || var13 < 1 || var13 > 67108864 || var11 > var5 - (long)var13) {
               throw new IllegalStateException("Invalid image patch range");
            }

            byte[] var14 = new byte[32];
            byte[] var15 = new byte[32];
            var3.readFully(var14);
            var3.readFully(var15);
            long var16 = var3.getFilePointer();
            var3.seek(var16 + (long)var13);
            long var18 = var3.getFilePointer();
            var3.seek(var18 + (long)var13);
            var9[var10] = new PatchRange(var11, var13, var14, var15, var16, var18);
         }

         if (var3.getFilePointer() != var3.length()) {
            throw new IllegalStateException("Trailing data in image patch");
         }

         if (!var2 && !Arrays.equals(var7, sha256File(var1))) {
            throw new IllegalStateException("Runtime image does not match the patch baseline");
         }

         RandomAccessFile var25 = new RandomAccessFile(var1, "rw");

         try {
            for(PatchRange var28 : var9) {
               byte[] var29 = var2 ? var28.newHash : var28.oldHash;
               byte[] var30 = var2 ? var28.oldHash : var28.newHash;
               byte[] var17 = new byte[var28.length];
               var25.seek(var28.offset);
               var25.readFully(var17);
               if (!Arrays.equals(var29, sha256(var17))) {
                  throw new IllegalStateException("Patch range state hash mismatch at " + var28.offset);
               }

               long var31 = var2 ? var28.oldDataOffset : var28.newDataOffset;
               byte[] var20 = new byte[var28.length];
               var3.seek(var31);
               var3.readFully(var20);
               if (!Arrays.equals(var30, sha256(var20))) {
                  throw new IllegalStateException("Patch payload hash mismatch at " + var28.offset);
               }

               var25.seek(var28.offset);
               var25.write(var20);
               var25.getFD().sync();
               var25.seek(var28.offset);
               var25.readFully(var17);
               if (!Arrays.equals(var30, sha256(var17))) {
                  throw new IllegalStateException("Patch read-back verification failed at " + var28.offset);
               }
            }
         } catch (Throwable var23) {
            try {
               var25.close();
            } catch (Throwable var22) {
               var23.addSuppressed(var22);
            }

            throw var23;
         }

         var25.close();
         if (var2 && !Arrays.equals(var7, sha256File(var1))) {
            throw new IllegalStateException("Rollback did not restore the baseline hash");
         }
      } catch (Throwable var24) {
         try {
            var3.close();
         } catch (Throwable var21) {
            var24.addSuppressed(var21);
         }

         throw var24;
      }

      var3.close();
   }

   private static byte[] sha256(byte[] var0) throws Exception {
      return MessageDigest.getInstance("SHA-256").digest(var0);
   }

   private static byte[] sha256File(File var0) throws Exception {
      MessageDigest var1 = MessageDigest.getInstance("SHA-256");
      byte[] var2 = new byte[1048576];
      BufferedInputStream var3 = new BufferedInputStream(new FileInputStream(var0));

      int var4;
      try {
         while((var4 = ((InputStream)var3).read(var2)) >= 0) {
            var1.update(var2, 0, var4);
         }
      } catch (Throwable var7) {
         try {
            ((InputStream)var3).close();
         } catch (Throwable var6) {
            var7.addSuppressed(var6);
         }

         throw var7;
      }

      ((InputStream)var3).close();
      return var1.digest();
   }

   private static byte[] sha256FilePrefix(File var0, long var1) throws Exception {
      MessageDigest var3 = MessageDigest.getInstance("SHA-256");
      byte[] var4 = new byte[1048576];
      BufferedInputStream var5 = new BufferedInputStream(new FileInputStream(var0));

      int var8;
      try {
         for(long var6 = var1; var6 > 0L; var6 -= (long)var8) {
            var8 = ((InputStream)var5).read(var4, 0, (int)Math.min((long)var4.length, var6));
            if (var8 < 0) {
               throw new EOFException("Disk is shorter than its platform prefix");
            }

            var3.update(var4, 0, var8);
         }
      } catch (Throwable var10) {
         try {
            ((InputStream)var5).close();
         } catch (Throwable var9) {
            var10.addSuppressed(var9);
         }

         throw var10;
      }

      ((InputStream)var5).close();
      return var3.digest();
   }

   private static byte[] sha256FileRange(File var0, long var1, long var3) throws Exception {
      MessageDigest var5 = MessageDigest.getInstance("SHA-256");
      byte[] var6 = new byte[1048576];
      BufferedInputStream var7 = new BufferedInputStream(new FileInputStream(var0));

      try {
         long var10;
         for(long var8 = 0L; var8 < var1; var8 += var10) {
            var10 = ((InputStream)var7).skip(var1 - var8);
            if (var10 <= 0L) {
               if (((InputStream)var7).read() < 0) {
                  throw new EOFException("Disk is shorter than the requested range");
               }

               var10 = 1L;
            }
         }

         int var12;
         for(long var15 = var3; var15 > 0L; var15 -= (long)var12) {
            var12 = ((InputStream)var7).read(var6, 0, (int)Math.min((long)var6.length, var15));
            if (var12 < 0) {
               throw new EOFException("Disk is shorter than the requested range");
            }

            var5.update(var6, 0, var12);
         }
      } catch (Throwable var14) {
         try {
            ((InputStream)var7).close();
         } catch (Throwable var13) {
            var14.addSuppressed(var13);
         }

         throw var14;
      }

      ((InputStream)var7).close();
      return var5.digest();
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

   private void attachCallback(Object var1) throws Exception {
      this.lifecycleVm = var1;
      this.lifecycle.attach(var1);
      this.transition.event("Virtual machine created","AVF VM handle",android.os.SystemClock.elapsedRealtime());
      this.runOnUiThread(() -> {
         if(!this.activityDestroyed) {
            try {VmRuntimeService.keepAlive(this,var1);}
            catch(Exception error) {Log.w("U-AVF-runtime","Foreground runtime could not start",error);}
         }
      });
      Class var2 = Class.forName("android.system.virtualmachine.VirtualMachineCallback");
      Object var3 = Proxy.newProxyInstance(this.getClassLoader(), new Class[]{var2}, (var1x, var2x, var3x) -> {
         if (this.activityDestroyed || !this.lifecycle.owns(var1)) return null;
         if (var2x.getName().equals("onStopped")) {
            VmRuntimeService.ended(this,var1);
            String reason = Arrays.toString(var3x);
            if (var3x != null && var3x.length > 1 && var3x[1] instanceof Integer) {
               for (Field field : var2.getFields()) {
                  if (field.getName().startsWith("STOP_REASON_") && field.getType() == Integer.TYPE && field.getInt(null) == ((Integer)var3x[1]).intValue()) {
                     reason=field.getName(); break;
                  }
               }
            }
            this.lifecycle.stopped(var1, reason);
            this.transition.event("VM stopped","AVF onStopped: "+reason,android.os.SystemClock.elapsedRealtime());
            Log.i("U-AVF-lifecycle","Stopped: " + reason);
            if (this.guestControl != null) this.guestControl.close();
            this.guestControl=null;
            this.controlVm=null;
            this.guestControlStatus="Disconnected: " + reason;
            boolean restart = this.rebootRequested || reason.equals("STOP_REASON_REBOOT");
            if(restart && this.transition.mode()!=RuntimeTransition.Mode.REBOOT) {
               this.beginRuntimeTransition(RuntimeTransition.Mode.REBOOT);
               this.transition.event("VM stopped","AVF onStopped: "+reason,android.os.SystemClock.elapsedRealtime());
            }
            this.rebootRequested=false;
            String var4 = this.activeFrameVmName;
            boolean var5 = "winavf-frame-bridge-test-ubuntu-24045".equals(var4) && !this.userRequestedVmStop && this.getPreferences(0).getBoolean("ubuntu_install_handoff_pending", false) && !this.getPreferences(0).getBoolean("ubuntu_install_handoff_attempted", false);
            this.vmMayBeRunning = false;
            this.activeFrameVmName = null;
            this.resetGuestFrameSurface();
            if (restart && !var5 && "winavf-frame-bridge-test-ubuntu-24045".equals(var4)) {
               this.show("Ubuntu reboot: reconnecting the workspace…");
               this.uiHandler.postDelayed(() -> new Thread(() -> {
                  if (this.getPreferences(0).getBoolean("ubuntu_installed_runtime_ready",false)) this.startPersistentUbuntu(true);
                  else this.startFrameBridgeTest();
               },"U-AVF-reboot-handoff").start(),500);
            } else if (var5) {
               this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_attempted", true).apply();
               this.runOnUiThread(() -> {
                  this.closeSessionDrawerImmediately();
                  this.toolbar.setVisibility(8);
                  this.drawerScrim.setVisibility(8);
                  this.menuButton.setVisibility(8);
                  this.status.setVisibility(0);
                  this.status.setText("Preparing the installed Ubuntu system…");
                  this.show("Install session ended; checking the persistent boot handoff.");
               });
               (new Thread(() -> this.finishUbuntuInstallHandoff(), "U-AVF-install-handoff")).start();
            } else {
               final int stoppedGeneration=this.transition.generation();
               this.runOnUiThread(() -> {
                  this.closeSessionDrawerImmediately();
                  this.toolbar.setVisibility(8);
                  this.drawerScrim.setVisibility(8);
                  this.menuButton.setVisibility(8);
                  if(this.transitionView!=null)this.transitionView.refresh();
                  this.uiHandler.postDelayed(()->{
                     if(this.activityDestroyed || this.transition.generation()!=stoppedGeneration)return;
                     this.transition.dismiss();
                     if(this.transitionView!=null)this.transitionView.refresh();
                     this.renderProductScreen(this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? MainActivity.ProductScreen.WINDOWS_HOME : (this.isoPresent() ? MainActivity.ProductScreen.LINUX_HOME : MainActivity.ProductScreen.LINUX_SETUP));
                  },450);
               });
               this.show("VM stopped.");
            }
         } else if (var2x.getName().equals("onError")) {
            this.lifecycle.failed(var1, Arrays.toString(var3x));
            this.transition.failure(Arrays.toString(var3x),android.os.SystemClock.elapsedRealtime());
            if (this.guestControl != null) this.guestControl.close();
            this.guestControl=null;
            this.controlVm=null;
            this.guestControlStatus="Disconnected: VM error";
            this.vmMayBeRunning = false;
            this.activeFrameVmName = null;
            this.resetGuestFrameSurface();
            this.runOnUiThread(() -> {
               this.closeSessionDrawerImmediately();
               this.toolbar.setVisibility(8);
               this.drawerScrim.setVisibility(8);
               this.menuButton.setVisibility(8);
               this.renderProductScreen(this.selectedProfile == MainActivity.LaunchProfile.WINDOWS ? MainActivity.ProductScreen.WINDOWS_HOME : (this.isoPresent() ? MainActivity.ProductScreen.LINUX_HOME : MainActivity.ProductScreen.LINUX_SETUP));
            });
            this.show("VM error: " + Arrays.toString(var3x));
            this.runOnUiThread(this.transitionTick);
         }

         return null;
      });
      var1.getClass().getMethod("setCallback", Executor.class, var2).invoke(var1, ForkJoinPool.commonPool(), var3);
   }

   private String readAvfState(Object vm) throws Exception {
      int value=((Number)vm.getClass().getMethod("getStatus").invoke(vm)).intValue();
      for(String name : new String[]{"STATUS_RUNNING","STATUS_STOPPED","STATUS_DELETED"}) {
         if(vm.getClass().getField(name).getInt(null)==value) return name.substring(7);
      }
      throw new IllegalStateException("Unknown AVF status: " + value);
   }

   private void finishUbuntuInstallHandoff() {
      File var1 = this.persistentUbuntuDiskFile();

      try {
         boolean var2 = PersistentUbuntuDisk.audit(var1, 3967463424L, 34359738368L);
         boolean var3 = var2 && PersistentUbuntuDisk.hasInstalledBootCandidate(var1);
         this.appendUbuntuInstallerAutostartReport("POST_INSTALL_HANDOFF=" + (var3 ? "CANDIDATE_FOUND" : "CANDIDATE_NOT_FOUND") + " GPT=" + (var2 ? "PASS" : "FAIL"));
         if (!var3) {
            this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", false).apply();
            this.runOnUiThread(() -> {
               this.renderProductScreen(MainActivity.ProductScreen.LINUX_HOME);
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
         this.startPersistentUbuntu(true, true);
      } catch (Throwable var6) {
         this.getPreferences(0).edit().putBoolean("ubuntu_install_handoff_pending", false).apply();
         this.appendUbuntuInstallerAutostartReport("POST_INSTALL_HANDOFF=FAILED " + rootMessage(var6));
         this.runOnUiThread(() -> {
            this.renderProductScreen(MainActivity.ProductScreen.LINUX_HOME);
            this.show("Could not start the installed Ubuntu handoff: " + rootMessage(var6));
         });
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

   private static int u16(byte[] var0, int var1) {
      return var0[var1] & 255 | (var0[var1 + 1] & 255) << 8;
   }

   private static long u32(byte[] var0, int var1) {
      return (long)u16(var0, var1) | (long)u16(var0, var1 + 2) << 16;
   }

   private static String readWinpeMarker(File var0) throws Exception {
      RandomAccessFile var1 = new RandomAccessFile(var0, "r");

      Object var21;
      label96: {
         Object var34;
         label95: {
            String var36;
            label94: {
               Object var32;
               try {
                  byte[] var2 = new byte[512];
                  var1.seek(1048576L);
                  var1.readFully(var2);
                  int var3 = u16(var2, 11);
                  int var4 = var2[13] & 255;
                  int var5 = u16(var2, 14);
                  int var6 = var2[16] & 255;
                  long var7 = u32(var2, 36);
                  long var9 = u32(var2, 44);
                  long var11 = 1048576L + (long)var5 * (long)var3;
                  long var13 = 1048576L + ((long)var5 + (long)var6 * var7) * (long)var3;
                  int var15 = var3 * var4;
                  long var16 = var9;

                  for(int var18 = 0; var18 < 1024 && var16 >= 2L && var16 < 268435448L; ++var18) {
                     byte[] var19 = new byte[var15];
                     var1.seek(var13 + (var16 - 2L) * (long)var15);
                     var1.readFully(var19);

                     for(int var20 = 0; var20 + 32 <= var19.length; var20 += 32) {
                        if (var19[var20] == 0) {
                           var21 = null;
                           break label96;
                        }

                        if ((var19[var20] & 255) != 229 && (var19[var20 + 11] & 255) != 15) {
                           new String(var19, var20, 8, StandardCharsets.US_ASCII);
                           String var22 = new String(var19, var20 + 8, 3, StandardCharsets.US_ASCII);
                           if (var22.equals("TXT")) {
                              long var23 = (long)u16(var19, var20 + 20) << 16 | (long)u16(var19, var20 + 26);
                              int var25 = (int)u32(var19, var20 + 28);
                              if (var25 <= 0 || var25 > 4096) {
                                 var34 = null;
                                 break label95;
                              }

                              byte[] var26 = new byte[var25];

                              byte[] var29 = new byte[4];
                              for(int var27 = 0; var23 >= 2L && var23 < 268435448L && var27 < var25; var23 = u32(var29, 0) & 268435455L) {
                                 int var28 = Math.min(var15, var25 - var27);
                                 var1.seek(var13 + (var23 - 2L) * (long)var15);
                                 var1.readFully(var26, var27, var28);
                                 var27 += var28;
                                 var29 = new byte[4];
                                 var1.seek(var11 + var23 * 4L);
                                 var1.readFully(var29);
                              }

                              String var35 = new String(var26, StandardCharsets.US_ASCII);
                              if (var35.contains("stage=WINPE_USERLAND_")) {
                                 var36 = var35;
                                 break label94;
                              }
                           }
                        }
                     }

                     byte[] var33 = new byte[4];
                     var1.seek(var11 + var16 * 4L);
                     var1.readFully(var33);
                     var16 = u32(var33, 0) & 268435455L;
                  }

                  var32 = null;
               } catch (Throwable var31) {
                  try {
                     var1.close();
                  } catch (Throwable var30) {
                     var31.addSuppressed(var30);
                  }

                  throw var31;
               }

               var1.close();
               return (String)var32;
            }

            var1.close();
            return var36;
         }

         var1.close();
         return (String)var34;
      }

      var1.close();
      return (String)var21;
   }

   private void auditPersistentBootWitness() {
      File var1 = new File(this.getExternalFilesDir((String)null), "persistent-boot-witness-report.txt");

      try {
         File var20 = new File(this.getFilesDir(), "payload");
         File var3 = new File(var20, "win11-gop-ebs-r1.img");
         if (!var3.isFile() || var3.length() != 9126805504L) {
            throw new IllegalStateException("No disposable persistent-witness disk is available");
         }

         byte[] var4 = readFatRootFile(var3, "SETUPACT", "LOG", 16777216);
         byte[] var5 = readFatRootFile(var3, "SETUPERR", "LOG", 16777216);
         PrintWriter var6 = new PrintWriter(new FileOutputStream(var1, false));

         try {
            var6.println("scope=POST_STOP_OFFLINE_DISPOSABLE_DISK_INSPECTION");
            var6.println("diskBytes=" + var3.length());
            var6.println("diskSha256=" + hex(sha256File(var3)));
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
               String var10001 = hex(MessageDigest.getInstance("SHA-256").digest(var4));
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
               String var23 = hex(MessageDigest.getInstance("SHA-256").digest(var5));
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

         String var9 = hex(sha256File(var8));
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

   private static byte[] readFatRootFile(File var0, String var1, String var2, int var3) throws Exception {
      RandomAccessFile var4 = new RandomAccessFile(var0, "r");

      Object var39;
      label109: {
         byte[] var40;
         label108: {
            Object var37;
            try {
               byte[] var5 = new byte[512];
               var4.seek(1048576L);
               var4.readFully(var5);
               int var6 = u16(var5, 11);
               int var7 = var5[13] & 255;
               int var8 = u16(var5, 14);
               int var9 = var5[16] & 255;
               long var10 = u32(var5, 36);
               long var12 = u32(var5, 44);
               if (var6 != 512 || var7 == 0 || var9 != 2 || var12 < 2L) {
                  throw new IllegalStateException("Unexpected FAT32 geometry");
               }

               long var14 = 1048576L + (long)var8 * (long)var6;
               long var16 = 1048576L + ((long)var8 + (long)var9 * var10) * (long)var6;
               int var18 = var6 * var7;
               long var19 = var12;

               for(int var21 = 0; var21 < 1024 && var19 >= 2L && var19 < 268435448L; ++var21) {
                  byte[] var22 = new byte[var18];
                  var4.seek(var16 + (var19 - 2L) * (long)var18);
                  var4.readFully(var22);

                  for(int var23 = 0; var23 + 32 <= var22.length; var23 += 32) {
                     if (var22[var23] == 0) {
                        var39 = null;
                        break label109;
                     }

                     if ((var22[var23] & 255) != 229 && (var22[var23 + 11] & 255) != 15 && (var22[var23 + 11] & 16) == 0) {
                        String var24 = (new String(var22, var23, 8, StandardCharsets.US_ASCII)).trim();
                        String var25 = (new String(var22, var23 + 8, 3, StandardCharsets.US_ASCII)).trim();
                        if (var1.equals(var24) && var2.equals(var25)) {
                           long var26 = (long)u16(var22, var23 + 20) << 16 | (long)u16(var22, var23 + 26);
                           long var28 = u32(var22, var23 + 28);
                           if (var28 > 0L && var28 <= (long)var3) {
                              byte[] var30 = new byte[(int)var28];
                              int var31 = 0;

                              for(int var32 = 0; var32 < 4096 && var26 >= 2L && var26 < 268435448L && (long)var31 < var28; ++var32) {
                                 int var33 = Math.min(var18, (int)var28 - var31);
                                 var4.seek(var16 + (var26 - 2L) * (long)var18);
                                 var4.readFully(var30, var31, var33);
                                 var31 += var33;
                                 byte[] var34 = new byte[4];
                                 var4.seek(var14 + var26 * 4L);
                                 var4.readFully(var34);
                                 var26 = u32(var34, 0) & 268435455L;
                              }

                              if ((long)var31 != var28) {
                                 throw new IllegalStateException("Witness FAT chain ended before file size");
                              }

                              var40 = var30;
                              break label108;
                           }

                           throw new IllegalStateException("Witness log has invalid or excessive size: " + var28);
                        }
                     }
                  }

                  byte[] var38 = new byte[4];
                  var4.seek(var14 + var19 * 4L);
                  var4.readFully(var38);
                  var19 = u32(var38, 0) & 268435455L;
               }

               var37 = null;
            } catch (Throwable var36) {
               try {
                  var4.close();
               } catch (Throwable var35) {
                  var36.addSuppressed(var35);
               }

               throw var36;
            }

            var4.close();
            return (byte[])var37;
         }

         var4.close();
         return var40;
      }

      var4.close();
      return (byte[])var39;
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
      if (var2.isFile() && var2.length() == var3 && var5.equalsIgnoreCase(hex(sha256File(var2)))) {
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

               if (var7.length() == var3 && var5.equalsIgnoreCase(hex(sha256File(var7)))) {
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

   private static String readText(File var0) {
      try {
         FileInputStream var1 = new FileInputStream(var0);

         String var4;
         try {
            byte[] var2 = new byte[(int)Math.min(var0.length(), 128L)];
            int var3 = ((InputStream)var1).read(var2);
            var4 = var3 > 0 ? new String(var2, 0, var3, StandardCharsets.US_ASCII) : "";
         } catch (Throwable var6) {
            try {
               ((InputStream)var1).close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         ((InputStream)var1).close();
         return var4;
      } catch (Exception var7) {
         return "";
      }
   }

   private static void writeText(File var0, String var1) throws Exception {
      FileOutputStream var2 = new FileOutputStream(var0, false);

      try {
         var2.write(var1.getBytes(StandardCharsets.US_ASCII));
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
         String var2 = (new SimpleDateFormat("HH:mm:ss", Locale.ROOT)).format(new Date());
         this.uiLog.append(var2).append("  ").append(var1).append('\n');
         if (this.uiLog.length() > 24576) {
            this.uiLog.delete(0, this.uiLog.length() - 24576);
         }

         this.currentNotice = var1;
         if(var1.startsWith("SERIAL:") || var1.startsWith("Writing complete VM serial log") || var1.startsWith("Console closed:")) {
            if(this.logPanel != null && this.logPanel.getVisibility()==0) this.refreshLogPanel();
            return;
         }
         if (this.productStatus != null && this.homePanel != null && this.homePanel.getVisibility() == 0) {
            this.productStatus.setText(var1);
         }

         this.status.setText(var1);
         if (!this.frameVisible) {
            this.status.setVisibility(0);
         }

         if (this.logPanel != null && this.logPanel.getVisibility() == 0) {
            this.refreshLogPanel();
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
      IMAGE_PATCH_MAGIC = "WAVFPAT1".getBytes(StandardCharsets.US_ASCII);
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

   private static enum ProductScreen {
      ONBOARDING,
      COMPATIBILITY,
      VM_PERMISSION,
      MODE_SELECTION,
      CREATE_OS,
      CREATE_IMAGE,
      CREATE_STORAGE,
      CREATE_REVIEW,
      INSTALL_RECOVERY,
      INPUT_SETTINGS,
      LINUX_SETUP,
      LINUX_HOME,
      WINDOWS_HOME,
      VM_SETTINGS,
      APP_SETTINGS,
      DIAGNOSTICS,
      INSTALL_STATUS;

      // $FF: synthetic method
      private static ProductScreen[] $values() {
         return new ProductScreen[]{ONBOARDING, COMPATIBILITY, VM_PERMISSION, MODE_SELECTION, CREATE_OS, CREATE_IMAGE, CREATE_STORAGE, CREATE_REVIEW, INSTALL_RECOVERY, INPUT_SETTINGS, LINUX_SETUP, LINUX_HOME, WINDOWS_HOME, VM_SETTINGS, APP_SETTINGS, DIAGNOSTICS, INSTALL_STATUS};
      }
   }

   private static final class FatEntry {
      final long cluster;
      final long size;
      final int attributes;

      FatEntry(long var1, long var3, int var5) {
         this.cluster = var1;
         this.size = var3;
         this.attributes = var5;
      }
   }

   private static final class PatchRange {
      final long offset;
      final long oldDataOffset;
      final long newDataOffset;
      final int length;
      final byte[] oldHash;
      final byte[] newHash;

      PatchRange(long var1, int var3, byte[] var4, byte[] var5, long var6, long var8) {
         this.offset = var1;
         this.length = var3;
         this.oldHash = var4;
         this.newHash = var5;
         this.oldDataOffset = var6;
         this.newDataOffset = var8;
      }
   }
}
