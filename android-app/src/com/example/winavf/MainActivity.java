// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import static com.example.winavf.IoUtil.rootMessage;

import com.example.winavf.runtime.RuntimeTransition;
import com.example.winavf.GuestVmSession.PowerAction;

import com.example.winavf.runtime.VmLifecycle;

import com.example.winavf.UbuntuWorkspaceMedia.Installer;
import com.example.winavf.UbuntuWorkspaceMedia.Request;
import com.example.winavf.navigation.ProductNavigation;
import com.example.winavf.navigation.ProductNavigation.Location;
import com.example.winavf.navigation.ProductScreen;
import com.example.winavf.UbuntuLiveSessionMedia.Profile;
import com.example.winavf.workspace.DisplaySettings;
import com.example.winavf.workspace.DisplaySettings.Mode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Build.VERSION;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets.Type;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
   private volatile String plasmaInstallState = "unavailable";
   private static final String UBUNTU_PERSISTENT_REPORT_NAME = "ubuntu-install-runtime-report.txt";
   private TextView status;
   private FrameSurfaceView frameSurface;
   private GuestDisplaySurfaces displaySurfaces;
   private SessionControls sessionControls;
   private UbuntuDisplaySession ubuntuDisplay;
   private UbuntuLaunchFlow ubuntuLaunch;
   private GuestVmSession guestSession;
   private UbuntuGuestServices ubuntuServices;
   private WindowsImagePatches imagePatches;
   private UbuntuSettingsTabs ubuntuSettingsTabs;
   private final VmLifecycle lifecycle = new VmLifecycle();
   private final RuntimeTransition transition = new RuntimeTransition();
   private RuntimeTransitionView transitionView;
   private RuntimeDiagnostics diagnostics;
   private UbuntuWorkspaceStorage ubuntuStorage;
   private UbuntuWorkspaceActions ubuntuActions;
   private WorkspaceScreens workspaceScreens;
   private GuestMediaFiles guestMediaFiles;
   private ConsoleLoopbackProbe consoleLoopback;
   private AvfDebugDiagnostics avfDebugDiagnostics;
   private WindowsBootProbes windowsBootProbes;
   private WindowsDiagnosticDiskReports windowsDiskReports;
   private UbuntuBlockIoAudit ubuntuBlockIoAudit;
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
   private volatile boolean activityDestroyed;
   private DiagnosticVmLaunches diagnosticLaunches;
   private DiagnosticCommands diagnosticCommands;
   private FrameLayout page;
   private FrameLayout homePanel;
   private final ProductTabs productTabs = new ProductTabs();
   private FigmaWorkspaceSettings workspaceSettingsView;
   private boolean awaitingKeyboardCapture;
   private TextView productStatus;
   private ConsoleFrameDecoder frameDecoder;
   private boolean frameVisible;
   private final DiagnosticSerialCapture diagnosticSerial = new DiagnosticSerialCapture();
   private volatile int observedFrameCount;
   private final AtomicBoolean frameRevealPostQueued = new AtomicBoolean();
   private ProductKdBridge kdBridge;
   private LaunchProfile selectedProfile;
   private static final int REQUEST_UBUNTU_ISO = 21825;
   private static final int REQUEST_UBUNTU_LIVE_ISO = 21829;
   private static final int REQUEST_WINDOWS_INSTALL_ISO = 21827;
   private static final int REQUEST_WINDOWS_WINPE = 21828;
   private WindowsSetupFlow windowsSetup;
   private ProductScreen productScreen;
   private final ProductNavigation navigation = new ProductNavigation();
   private boolean returningBack;
   private CompatibilitySession compatibility;
   private DiagnosticsScreens diagnosticScreens;
   private FigmaCompatibilityView compatibilityView;
   private boolean compatibilityViewRunning;
   private boolean welcomeFirstRun;
   private String createWorkspaceOs;
   private String draftUbuntuTitle="";
   private String workspaceSettingsTab;
   private int workspaceSettingsPreviousTab=-1;
   private boolean wizardSettingsActive;
   private final SessionEdgeGesture edgeGesture = new SessionEdgeGesture();
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

   @Override public void onCreate(Bundle savedState) {
      super.onCreate(savedState);
      this.initializeControllers();
      this.initializeWindow();
      this.initializeViewport();
      this.addProductControls();
      this.initializeFrameDecoder();
      this.setContentView(this.page);
      this.page.setFocusableInTouchMode(true);
      this.page.requestFocus();
      this.diagnosticCommands.handle(this.getIntent());
      this.restoreProductNavigation(savedState);
      guestSession.reconnectRunningFrameVmIfPresent();
   }

   private void initializeControllers() {
      this.uiHandler = new Handler(this.getMainLooper());
      this.ubuntuServices = this.createUbuntuGuestServices();
      this.guestSession = this.createGuestVmSession();
      this.imagePatches = new WindowsImagePatches(this, this::show);
      this.diagnostics = new RuntimeDiagnostics(this.getPreferences(0));
      this.guestMediaFiles = new GuestMediaFiles(this.getAssets());
      this.consoleLoopback = new ConsoleLoopbackProbe(this, this.guestMediaFiles, this::show);
      this.avfDebugDiagnostics = new AvfDebugDiagnostics(this, this.guestMediaFiles, this::show);
      this.windowsBootProbes = new WindowsBootProbes(this.getExternalFilesDir(null), this::show);
      this.windowsDiskReports = new WindowsDiagnosticDiskReports(this, this::show);
      this.ubuntuBlockIoAudit = new UbuntuBlockIoAudit(this, this::show);
      this.ubuntuStorage = new UbuntuWorkspaceStorage(this, this.getPreferences(0), this.lifecycle,
            () -> this.isManagedVmRunning() || this.guestSession.hasFrameGuest());
      this.ubuntuActions = new UbuntuWorkspaceActions(this, this.ubuntuStorage, this.guestMediaFiles,
            () -> this.isManagedVmRunning() || this.guestSession.hasFrameGuest(), () -> this.draftUbuntuTitle,
            this::openUbuntuIsoPicker, this::startPersistentUbuntu, this::renderProductScreen, this::show);
      this.windowsSetup = new WindowsSetupFlow(this,
            () -> !this.activityDestroyed && this.productScreen == ProductScreen.WINDOWS_HOME,
            this::isManagedVmRunning,
            () -> this.showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME),
            document -> this.openInstallDocumentPicker(
                  document == WindowsWorkspaceMedia.Document.ISO ? REQUEST_WINDOWS_INSTALL_ISO : REQUEST_WINDOWS_WINPE, true),
            this::startPreparedWindowsSetup);
      this.workspaceScreens = this.createWorkspaceScreens();
      this.ubuntuSettingsTabs = this.createUbuntuSettingsTabs();
      this.compatibility = new CompatibilitySession(this, this.uiHandler, this.lifecycle, this.diagnostics,
            this::isManagedVmRunning, this::hasAvfPermissions, this::releaseVersionLabel, this::onCompatibilityChanged);
      this.diagnosticScreens = this.createDiagnosticsScreens();
      this.ubuntuLaunch = this.createUbuntuLaunchFlow();
      this.initializeDebugCommands();
   }

   private UbuntuGuestServices createUbuntuGuestServices() {
      return new UbuntuGuestServices(this, lifecycle, transition, new UbuntuGuestServices.Host() {
         public boolean installedDiskExists() { return persistentUbuntuDiskFile().isFile(); }
         public void resetPlasmaState() { plasmaInstallState = "unavailable"; }
         public String plasmaState() { return plasmaInstallState; }
         public void updatePlasmaState(String state) { MainActivity.this.updatePlasmaInstallState(state); }
         public void displayGuestReady(boolean ready) { ubuntuDisplay.guestReady(ready); }
         public void startDisplay(Object vm) { startUbuntuFrameBridge(vm); }
         public void refreshTransition() { if (transitionView != null) transitionView.refresh(); }
         public void show(String message) { MainActivity.this.show(message); }
      });
   }

   private GuestVmSession createGuestVmSession() {
      return new GuestVmSession(this, uiHandler, lifecycle, transition, diagnosticSerial, new GuestVmSession.Host() {
         public void attachWindows(WindowsSetupRuntime setup) { windowsSession.attach(setup); }
         public void selectUbuntu() { selectedProfile = LaunchProfile.LINUX; }
         public GuestVmSession.GuestOs selectedOs() {
            return selectedProfile == LaunchProfile.LINUX ? GuestVmSession.GuestOs.UBUNTU : GuestVmSession.GuestOs.WINDOWS;
         }
         public boolean destroyed() { return activityDestroyed; }
         public boolean frameVisible() { return frameVisible; }
         public boolean isManagedVmRunning() { return MainActivity.this.isManagedVmRunning(); }
         public File persistentUbuntuDiskFile() { return MainActivity.this.persistentUbuntuDiskFile(); }
         public ConsoleFrameDecoder frameDecoder() { return frameDecoder; }
         public void startUbuntuFrameBridge(Object vm) { MainActivity.this.startUbuntuFrameBridge(vm); }
         public void startPersistentUbuntu(Request request) { MainActivity.this.startPersistentUbuntu(request); }
         public void startUbuntuLive() { MainActivity.this.startUbuntuLive(); }
         public void finishHandoff() { ubuntuLaunch.finishHandoff(); }
         public void beginRuntimeTransition(RuntimeTransition.Mode mode) { MainActivity.this.beginRuntimeTransition(mode); }
         public void refreshTransition() { transitionTick.run(); }
         public void refreshTransitionView() { if (transitionView != null) transitionView.refresh(); }
         public GuestControl control() { return ubuntuServices.control(); }
         public void disconnectGuestControls(String detail) { ubuntuServices.disconnect(detail); }
         public void resetGuestFrameSurface() { MainActivity.this.resetGuestFrameSurface(); }
         public void hideStoppedGuestControls() { MainActivity.this.hideStoppedGuestControls(); }
         public void showStoppedWorkspace(boolean temporary) { renderProductScreen(stoppedWorkspaceScreen(temporary)); }
         public void showInstallHandoff() {
            MainActivity.this.hideStoppedGuestControls();
            status.setVisibility(View.VISIBLE);
            status.setText("Preparing the installed Ubuntu system…");
            show("Install session ended; checking the persistent boot handoff.");
         }
         public void show(String message) { MainActivity.this.show(message); }
      });
   }

   private void initializeDebugCommands() {
      this.diagnosticLaunches = new DiagnosticVmLaunches(this, this.guestMediaFiles, this.windowsBootProbes,
            this.diagnosticSerial, new DiagnosticVmLaunches.Host() {
         public boolean skipValidation() { return skipDiskValidation(); }
         public void resetFrames(boolean hide) {
            if (hide) frameVisible = false;
            observedFrameCount = 0;
         }
         public int observedFrames() { return observedFrameCount; }
         public void applyStagedPatch(File staging, File disk) throws Exception { imagePatches.applyStaged(staging, disk); }
         public void attachAndRun(Object vm, String consoleLog) throws Exception {
            guestSession.attachAndRunDiagnostic(vm, consoleLog);
         }
         public void probeUbuntu(Object vm) { ubuntuLaunch.probe(vm, false); }
         public void attachUbuntuDisplay(Object vm) { startUbuntuFrameBridge(vm); }
         public void show(String message) { MainActivity.this.show(message); }
      });
      this.diagnosticCommands = new DiagnosticCommands(this, this.diagnosticLaunches, this.ubuntuBlockIoAudit,
            this.avfDebugDiagnostics, this.consoleLoopback, this.windowsDiskReports, this.displayConnection,
            new DiagnosticCommands.Host() {
         public void showWelcome() { renderProductScreen(ProductScreen.ONBOARDING); }
         public boolean running() { return isManagedVmRunning(); }
         public VmLifecycle.State lifecycleState() { return lifecycle.state(); }
         public void selectUbuntu(boolean save) {
            if (save) selectProfile(LaunchProfile.LINUX);
            else selectedProfile = LaunchProfile.LINUX;
         }
         public void startLive() { startUbuntuLive(); }
         public void startLive(Profile profile) { startUbuntuLive(profile); }
         public void stop() { stopSelectedProfile(); }
         public void rollback() { imagePatches.rollback(); }
         public void startDebugger(String token) { startProductKdBridge(token); }

         public void stopDebugger() { stopProductKdBridge(); }
         public Object ubuntuGuest() { return ubuntuDisplay.guest(); }
         public void attachDisplay(Object vm) { startUbuntuFrameBridge(vm); }
         public void feedFrame(byte[] frame) { frameDecoder.feed(frame, 0, frame.length); }
         public void show(String message) { MainActivity.this.show(message); }
      });
   }

   private void startProductKdBridge(String token) {
      kdBridge().serve(token);
   }

   private void stopProductKdBridge() {
      kdBridge().stop();
   }

   /** The KD bridge is rebuilt per session so a failed run leaves no stale state. */
   private ProductKdBridge kdBridge() {
      ProductKdBridge bridge = this.kdBridge;
      if (bridge == null) {
         bridge = new ProductKdBridge(this, this.getExternalFilesDir((String) null),
               this.getFilesDir(), this.guestMediaFiles, this.frameDecoder,
               this.imagePatches::applyStaged, this::copyFile, this::show);
         this.kdBridge = bridge;
      }
      return bridge;
   }

   private void initializeWindow() {
      UiLanguage.load(this);
      // Android's gesture navigation and the Back key share the same VM-preserving route.
      this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT, this.navigationBackCallback);
      this.getWindow().setDecorFitsSystemWindows(false);
      this.hideSystemBars();
   }

   private void initializeViewport() {
      this.page = new FrameLayout(this);
      this.page.setBackgroundColor(0xff000000);
      this.frameSurface = new FrameSurfaceView(this);
      this.frameSurface.setInputListener(this.createViewportInput());
      this.page.addView(this.frameSurface, new FrameLayout.LayoutParams(-1, -1));
      this.displaySurfaces = new GuestDisplaySurfaces(this.page, this.frameSurface);
      this.ubuntuDisplay = this.createUbuntuDisplaySession();
      this.windowsSession = this.createWindowsSessionViewport();
      this.status = this.createViewportStatus();
      this.page.addView(this.status, new FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM));
   }

   private TextView createViewportStatus() {
      TextView statusView = new TextView(this);
      statusView.setText("Ready");
      statusView.setTextColor(0xffcccccc);
      statusView.setTextSize(12.0f);
      statusView.setSingleLine(true);
      statusView.setEllipsize(TruncateAt.END);
      statusView.setPadding(16, 8, 16, 8);
      statusView.setBackgroundColor(0x99000000);
      return statusView;
   }

   private FrameSurfaceView.InputListener createViewportInput() {
      return new FrameSurfaceView.InputListener() {
         @Override public void onPointer(int x, int y, int action) {
            revealRunningControls();
            onGuestPointer(x, y, action);
         }

         @Override public void onMouseButton(int button, boolean pressed) {
            revealRunningControls();
            displayConnection.send(GuestInput.BUTTON, pressed ? 1 : 0, button, 0);
         }

         @Override public void onScroll(int horizontal, int vertical) {
            revealRunningControls();
            displayConnection.send(GuestInput.SCROLL, 0, horizontal, vertical);
         }

         @Override public void onRelativePointer(int dx, int dy) {
            onGuestRelativePointer(dx, dy);
         }

         @Override public void onPointerCaptureChanged(boolean captured) {
            show(captured ? "Mouse captured. Press Ctrl + Alt + Shift + Esc to open controls and release it."
                  : "Mouse released.");
            if (sessionControls != null && sessionControls.isOpen()) sessionControls.populate();
         }
      };
   }

   private void initializeFrameDecoder() {
      this.frameDecoder = new ConsoleFrameDecoder(new ConsoleFrameDecoder.Listener() {
         @Override public void onFrame(ConsoleFrameDecoder.Frame frame) {
            observedFrameCount++;
            guestFrameWidth = frame.width;
            guestFrameHeight = frame.height;
            // Firmware UART frames are not proof that a Linux desktop exists.
            if (selectedProfile == LaunchProfile.LINUX && transition.visible()) return;
            queueFrameForDisplay(frame);
         }

         @Override public void onProtocolError(String message) {}
      });
   }

   @Override protected void onSaveInstanceState(Bundle out) {
      super.onSaveInstanceState(out);
      ProductScreen visibleScreen = this.homePanel.getVisibility() == View.VISIBLE ? this.productScreen : null;
      var state = new ProductNavigationState(visibleScreen, this.workspaceSettingsTab, this.wizardSettingsActive,
            this.createWorkspaceOs, this.draftUbuntuTitle, this.awaitingKeyboardCapture, this.navigation.snapshot());
      state.save(out);
   }

   private void restoreProductNavigation(Bundle savedState) {
      var state = ProductNavigationState.restore(savedState);
      if (state == null) return;
      this.workspaceSettingsTab = state.tab();
      this.wizardSettingsActive = state.wizard();
      this.createWorkspaceOs = state.draftOs();
      this.draftUbuntuTitle = state.draftTitle();
      this.awaitingKeyboardCapture = state.keyboardCapturePending();
      this.navigation.restore(state.history());
      this.renderBackDestination(state.screen());
   }

   private void hideSystemBars() {
      WindowInsetsController insets = this.getWindow().getDecorView().getWindowInsetsController();
      if (insets == null) return;
      insets.hide(Type.statusBars() | Type.navigationBars());
      insets.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
   }

   @Override public void onWindowFocusChanged(boolean focused) {
      super.onWindowFocusChanged(focused);
      this.ubuntuServices.focusClipboard(focused);
      if (focused) this.hideSystemBars();
      else this.guestInput.captureEnded();
   }

   @Override protected void onResume() {
      super.onResume();
      VmKeyboardAccessibilityService.foreground(this);
      if(this.page!=null) this.page.post(() -> {
         this.displaySurfaces.resumeDecoder();
         if(this.awaitingKeyboardCapture) {
            this.awaitingKeyboardCapture=false;
            this.getPreferences(0).edit().putBoolean("linux_system_keyboard_capture",this.keyboardServiceEnabled()).apply();
         }
         if(this.workspaceSettingsView!=null && this.workspaceSettingsView.getParent()==this.homePanel)
            this.workspaceSettingsView.refreshToggles();
      });
   }

   @Override protected void onPause() {
      this.displaySurfaces.suspendDecoder();
      VmKeyboardAccessibilityService.background(this);
      this.guestInput.releaseKeys();
      super.onPause();
   }

   @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
      super.onConfigurationChanged(configuration);
      this.hideSystemBars();
      if(this.page!=null) this.page.post(() -> {
         this.displaySurfaces.fitMavk();
         this.displaySurfaces.fitEncoded();
         this.updateRuntimeEdgeExclusion();
         this.displaySurfaces.resumeDecoder();
      });
   }

   private boolean desktopHasFocus() {
      return this.frameVisible && this.homePanel.getVisibility() != View.VISIBLE;
   }

   private boolean guestKeyboardAvailable() {
      return this.desktopHasFocus() && this.displayConnection.hasInput()
            && !this.sessionControls.isOpen();
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

   @Override protected void onDestroy() {
      this.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(this.navigationBackCallback);
      this.activityDestroyed = true;
      if (this.diagnosticCommands != null) this.diagnosticCommands.close();
      if (this.windowsSession != null) this.windowsSession.close();
      guestSession.detachUbuntuConsole();
      if (this.ubuntuDisplay != null) this.ubuntuDisplay.dismissFallback();
      if (this.displaySurfaces != null) this.displaySurfaces.close();
      if (this.ubuntuServices != null) this.ubuntuServices.close();
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
      this.sessionControls.toggle();
   }

   private void onGuestPointer(int x, int y, int action) {
      long interval = this.displaySurfaces.renderer() != null ? 8 : 33;
      this.guestInput.pointer(x, y, action, SystemClock.uptimeMillis(), interval);
   }

   private void onGuestRelativePointer(int dx, int dy) {
      if (this.frameVisible) this.guestInput.relativePointer(dx, dy, this.guestFrameWidth, this.guestFrameHeight);
   }

   private void updateRuntimeEdgeExclusion() {
      if (VERSION.SDK_INT < 29 || this.page == null) return;
      this.edgeGesture.updateExclusion(this.page, this.desktopHasFocus() && this.edgeGestureEnabled());
   }

   private boolean edgeGestureEnabled() {
      return this.getPreferences(0).getBoolean("runtime_edge_gesture", false);
   }

   @Override public boolean dispatchTouchEvent(MotionEvent event) {
      boolean canStart = event.getActionMasked() == MotionEvent.ACTION_DOWN && this.desktopHasFocus()
            && !this.sessionControls.isOpen() && this.edgeGestureEnabled();
      if (this.edgeGesture.onTouch(event, this.page, canStart, this::openDrawerFromEdge)) return true;
      return super.dispatchTouchEvent(event);
   }

   private void openDrawerFromEdge() {
      this.guestInput.releaseKeys();
      this.sessionControls.open();
   }

   /** Shows a decoded frame, revealing the desktop on the first one after a transition. */
   private void queueFrameForDisplay(ConsoleFrameDecoder.Frame frame) {
      if (!this.transition.acceptsDesktop()) return;
      FrameSurfaceView surface = this.frameSurface;
      if (surface != null) {
         surface.present(frame);
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
      this.ubuntuDisplay.dismissFallback();
      this.displayConnection.begin();
      this.displaySurfaces.close();
      this.ubuntuDisplay.guestReady(false);
      this.frameVisible = false;
      this.guestFrameWidth = 0;
      this.guestFrameHeight = 0;
      this.guestInput.resetPointer();
      this.frameRevealPostQueued.set(false);
      FrameSurfaceView surface = this.frameSurface;
      if (surface != null) surface.clearFrame();
   }

   public void onBackPressed() {
      this.handleBackAction();
   }

   private void handleBackAction() {
      if (this.sessionControls.isOpen()) {
         this.sessionControls.close();
      } else if(this.homePanel.getVisibility()==View.VISIBLE) this.navigateBack();
      else if(!this.isManagedVmRunning())this.finish();
   }

   private void navigateBack() {
      Location previous=this.navigation.back(this.frameVisible);
      if(previous==null) {
         if(mainTab(this.productScreen)>0 && this.homePanel.getVisibility()==View.VISIBLE) {
            this.renderBackDestination(ProductScreen.MODE_SELECTION);
            return;
         }
         if(!this.isManagedVmRunning())this.finish();
         return;
      }
      if(previous.screen()==null) {
         this.showRunningDesktop();
         if(previous.drawer())this.sessionControls.open();
         return;
      }
      this.workspaceSettingsTab=previous.settingsTab();
      this.wizardSettingsActive=previous.wizard();
      this.renderBackDestination(previous.screen());
   }

   private void renderBackDestination(ProductScreen screen) {
      this.returningBack = true;
      try {
         this.renderProductScreen(screen);
      } finally {
         this.returningBack = false;
      }
   }

   private boolean isManagedVmRunning() {
      return this.lifecycle.busy() || guestSession.mayBeRunning() || VmRuntimeService.windowsSetupBusy();
   }

   private void addProductControls() {
      this.homePanel = new FrameLayout(this);
      this.homeWallpaper=FigmaCanvas.wallpaper(this);
      this.homePanel.setBackground(this.homeWallpaper);
      this.page.addView(this.homePanel, new FrameLayout.LayoutParams(-1, -1));
      this.sessionControls = this.createSessionControls();
      this.restoreProfile();
   }

   private SessionControls createSessionControls() {
      return new SessionControls(this, this.page, this.frameSurface, this.displaySurfaces, this.displayConnection,
            this.guestInput, new SessionControls.Host() {
         @Override public boolean frameVisible() { return MainActivity.this.frameVisible; }
         @Override public boolean homeVisible() { return homePanel.getVisibility() == View.VISIBLE; }
         @Override public boolean windows() { return selectedProfile == LaunchProfile.WINDOWS; }
         @Override public boolean vmRunning() { return isManagedVmRunning(); }
         @Override public void preferences(String tab) { openSessionPreferences(tab); }
         @Override public void home() { renderProductScreen(ProductScreen.MODE_SELECTION); }
         @Override public void shutdown() { confirmStopVm(); }
         @Override public void reboot() { guestSession.requestGuestPower(PowerAction.REBOOT); }
      });
   }

   private void saveDisplayMode(Mode mode, boolean userChoice) {
      this.displaySettings().select(mode, userChoice, this.isManagedVmRunning());
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
         if(this.windowsSession.viewing(setup)) {
            this.beginRuntimeTransition(RuntimeTransition.Mode.BOOT);
            this.windowsSession.retryDisplay(setup);
         }
         else this.windowsSession.attach(setup);
         return;
      }
      if (!this.frameVisible) {
         if (this.isManagedVmRunning()) {
            if(this.transition.visible())this.transitionTick.run();
            else guestSession.reconnectRunningFrameVmIfPresent();
         } else {
            this.show("There is no running virtual machine to return to.");
         }

      } else {
         this.showRunningDesktop();
      }
   }

   private android.graphics.drawable.Drawable homeWallpaper;

   private void renderProductScreen(ProductScreen screen) {
      ProductScreen from=this.productScreen;
      boolean wasVisible=this.homePanel.getVisibility()==View.VISIBLE;
      this.renderProductContent(screen);
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

   private void renderProductContent(ProductScreen screen) {
      this.sessionControls.dismissIme();
      if (!this.returningBack) {
         this.navigation.opening(screen,
               new Location(this.productScreen, this.workspaceSettingsTab,
                     this.wizardSettingsActive, this.sessionControls.isOpen()),
               this.homePanel.getVisibility()==View.VISIBLE, this.homePanel.getChildCount()>0,
               this.frameVisible && !this.transition.visible());
      }
      switch (screen) {
         case DIAGNOSTICS, DIAGNOSTIC_LOG, DIAGNOSTIC_RUNS -> this.showDiagnosticsScreen(screen);
         case MODE_SELECTION, SYSTEMS, APP_SETTINGS -> this.showMainTabs(screen);
         case ONBOARDING, COMPATIBILITY -> this.showFigmaOnboarding(screen);
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
               this.showFigmaWorkspaceScreen(screen);
      }
   }

   private void enterProductScreen(ProductScreen screen, boolean replaceContent) {
      this.productScreen = screen;
      this.sessionControls.closeImmediately();
      this.status.setVisibility(View.GONE);
      this.sessionControls.hideButton();
      if (replaceContent) this.homePanel.removeAllViews();
      this.homePanel.setVisibility(View.VISIBLE);
   }

   private void showMainTabs(ProductScreen screen) {
      int previous = mainTab(this.productScreen);
      this.enterProductScreen(screen, false);
      this.productStatus = this.productTabs.show(this.homePanel, previous, mainTab(screen),
            this::renderProductScreen, this.workspaceScreens::mainPageFactory);
   }

   private static int mainTab(ProductScreen screen) {
      return screen == null ? -1 : screen.mainTab();
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
            },disk?this.ubuntuActions::confirmReinstall:()->this.renderProductScreen(ProductScreen.CREATE_OS),
            this.ubuntuActions::confirmDelete);
      this.productStatus=recovery.notice;
      this.homePanel.addView(recovery,new FrameLayout.LayoutParams(-1,-1));
   }

   private void showFigmaWorkspaceScreen(ProductScreen screen) {
      this.enterProductScreen(screen, true);
      if (screen == ProductScreen.WINDOWS_HOME || screen == ProductScreen.LINUX_HOME ||
            screen == ProductScreen.CREATE_STORAGE || screen == ProductScreen.CREATE_REVIEW) {
         this.wizardSettingsActive = false;
      }
      FigmaCanvas canvas = this.workspaceScreens.create(screen);
      this.productStatus = canvas.notice;
      this.productStatus.setText("");
      this.homePanel.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
   }

   private WorkspaceScreens createWorkspaceScreens() {
      return new WorkspaceScreens(this, this.ubuntuStorage, this.ubuntuActions, this.windowsSetup, new WorkspaceScreens.Host() {
         public boolean vmRunning() { return isManagedVmRunning(); }
         public boolean windowsSelected() { return selectedProfile == LaunchProfile.WINDOWS; }
         public void selectWindows(boolean windows) { selectProfile(windows ? LaunchProfile.WINDOWS : LaunchProfile.LINUX); }
         public void returnToVm() { returnToRunningVm(); }
         public void launchSelected() { launchSelectedProfile(); }
         public void startUbuntu() { startPersistentUbuntu(Request.START_EXISTING); }
         public void resumeUbuntu() { resumeInstalledUbuntuOrOfferRepair(); }
         public WorkspaceConfig config() { return workspaceConfig(); }
         public String displayLabel() { return configuredDisplayLabel(); }
         public String draftTitle() { return draftUbuntuTitle; }
         public void setDraftTitle(String title) { draftUbuntuTitle = title; }
         public void navigate(ProductScreen screen) { renderProductScreen(screen); }
         public void refreshWorkspace(ProductScreen screen) { showFigmaWorkspaceScreen(screen); }
         public boolean currentWindowsPage(View view) {
            return !activityDestroyed && productScreen == ProductScreen.WINDOWS_HOME && view.getParent() == homePanel;
         }
         public void back() { navigateBack(); }
         public void settings(String tab) { openSessionPreferences(tab); }
         public void wizardSettings() {
            wizardSettingsActive = true;
            workspaceSettingsTab = "Performance";
            renderProductScreen(ProductScreen.VM_SETTINGS);
         }
         public void pickWindowsIso() { openWindowsIsoPicker(); }
         public void pickLiveIso() { openInstallDocumentPicker(REQUEST_UBUNTU_LIVE_ISO, false); }
         public void openUbuntuStorage() { MainActivity.this.openUbuntuStorage(); }
         public void refreshAppSettings() { showMainTabs(ProductScreen.APP_SETTINGS); }
         public String releaseVersion() { return releaseVersionLabel(); }
         public void showLegal(String name) { showLegalDocument(name); }
         public void show(String message) { MainActivity.this.show(message); }
      });
   }

   private void beginCompatibilityCheck() {
      if (this.compatibility.running()) {
         this.renderProductScreen(ProductScreen.COMPATIBILITY);
         return;
      }
      if (this.isManagedVmRunning() || VmLifecycle.compatibilityProbeActive()) {
         ProductDialog.notice(this, "Проверка устройства", "Сначала завершите текущий сеанс VM.");
         return;
      }
      if (this.productScreen == ProductScreen.ONBOARDING && !this.compatibility.sharingDecided()) {
         this.compatibility.confirmFirstRun();
      } else {
         this.renderProductScreen(ProductScreen.COMPATIBILITY);
      }
   }

   private void onCompatibilityChanged() {
      if (this.productScreen == ProductScreen.COMPATIBILITY ||
            this.productScreen == ProductScreen.ONBOARDING && this.compatibility.running()) {
         this.renderProductScreen(ProductScreen.COMPATIBILITY);
      }
   }

   private void showFigmaOnboarding(ProductScreen screen) {
      boolean pageChanged=this.productScreen!=screen;
      if(!pageChanged && screen==ProductScreen.COMPATIBILITY && this.compatibility.running()
            && this.compatibilityViewRunning && this.compatibilityView!=null && this.compatibilityView.getParent()==this.homePanel) {
         this.compatibility.bind(this.compatibilityView);
         return;
      }
      this.enterProductScreen(screen, true);
      FigmaCanvas canvas;
      if(screen==ProductScreen.ONBOARDING) {
         canvas=new FigmaWelcomeView(this,this::beginCompatibilityCheck,
            ()->this.showLegalDocument("PRIVACY.md"));
      } else {
         this.compatibilityViewRunning=this.compatibility.running();
         this.compatibilityView=new FigmaCompatibilityView(this,this.compatibility.running(),this.compatibility.status(),
               this.hasAvfPermissions(), this.compatibility.sharing(), this.compatibility::setSharing, this.compatibility::start,
               ()->this.renderProductScreen(ProductScreen.MODE_SELECTION),
               this::navigateBack,this::skipCompatibilityCheck,()->this.renderProductScreen(ProductScreen.VM_PERMISSION),
               ()->this.renderProductScreen(ProductScreen.DIAGNOSTICS));
         this.compatibility.bind(this.compatibilityView);
         canvas=this.compatibilityView;
      }
      this.productStatus=canvas.notice;this.homePanel.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
   }

   private void skipCompatibilityCheck() {
      if(this.compatibility.running())return;
      ProductDialog.show(this,"Skip compatibility test?",
         "This device will remain unverified. You can open the dashboard and repeat the test from Diagnostics. Required VM permissions and image integrity checks still apply.",
         "Continue unverified",false,()-> {
            this.getPreferences(0).edit().putBoolean("first_run_complete",true).apply();
            this.renderProductScreen(ProductScreen.MODE_SELECTION);
         });
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

   private void resumeInstalledUbuntuOrOfferRepair() { this.ubuntuLaunch.resumeOrRepair(); }

   private File persistentUbuntuDiskFile() {
      return this.ubuntuStorage.disk();
   }

   private void startPersistentUbuntu(Request request) { this.ubuntuLaunch.startPersistent(request, Installer.DEFAULT); }

   private void startPersistentUbuntu(Request request, Installer installer) { this.ubuntuLaunch.startPersistent(request, installer); }

   private UbuntuLaunchFlow createUbuntuLaunchFlow() {
      return new UbuntuLaunchFlow(this, this.lifecycle, this.transition, this.ubuntuStorage, this.guestMediaFiles,
            this.displayConnection, new UbuntuLaunchFlow.Host() {
         public boolean running() { return isManagedVmRunning(); }
         public boolean mayBeRunning() { return guestSession.mayBeRunning(); }
         public void reconnect() { guestSession.reconnectRunningFrameVmIfPresent(); }
         public void beginBoot() { beginRuntimeTransition(RuntimeTransition.Mode.BOOT); }
         public void resetFrame() { resetGuestFrameSurface(); }
         public void showBootStatus(String message) {
            runOnUiThread(() -> {
               homePanel.setVisibility(View.GONE);
               status.setVisibility(View.VISIBLE);
               status.setText(message);
            });
         }
         public void updateBootText(String message) { status.setText(message); }
         public void showLiveFailure() {
            renderProductScreen(productScreen == ProductScreen.SYSTEMS ? ProductScreen.SYSTEMS
                  : isoPresent() ? ProductScreen.LINUX_HOME : ProductScreen.LINUX_SETUP);
         }
         public void showHandoffFailure(String message) {
            renderProductScreen(ProductScreen.LINUX_HOME);
            MainActivity.this.show(message);
         }
         public Object launch(Object configuration, String consoleLog) throws Exception {
            return guestSession.launchUbuntuVm(configuration, consoleLog);
         }
         public void attachDisplay(Object vm) { startUbuntuFrameBridge(vm); }
         public void requestRepair() { ubuntuActions.confirmInstall(true); }
         public void show(String message) { MainActivity.this.show(message); }
      });
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
      if (!windows) {
         this.ubuntuSettingsTabs.bind(view, tab, this.persistentUbuntuDiskFile(), this.availablePlasmaInstallState());
      }
   }

   private String availablePlasmaInstallState() {
      boolean canInstall = this.isManagedVmRunning() && !this.wizardSettingsActive
            && this.getPreferences(0).getBoolean("linux_current_boot_installed", false)
            && !"unavailable".equals(this.plasmaInstallState);
      return canInstall ? this.plasmaInstallState : null;
   }

   private UbuntuSettingsTabs createUbuntuSettingsTabs() {
      return new UbuntuSettingsTabs(this, new UbuntuSettingsTabs.Host() {
         @Override public boolean running() { return isManagedVmRunning(); }

         @Override public boolean keyboardCaptureEnabled() { return MainActivity.this.keyboardCaptureEnabled(); }

         @Override public void toggleKeyboardCapture() { toggleSystemKeyboardCapture(); }

         @Override public void clipboardPreferenceChanged() { ubuntuServices.syncClipboard(); }

         @Override public void showDisplayTab() { openWorkspaceSettingsTab("Display"); }

         @Override public void refresh(FigmaWorkspaceSettings view) {
            if (view == workspaceSettingsView && view.getParent() == homePanel
                  && productScreen == ProductScreen.VM_SETTINGS) showFigmaWorkspaceSettings();
         }

         @Override public void installPlasma() { ubuntuServices.requestPlasmaInstall(); }

         @Override public void expandStorage() { ubuntuActions.showExpansion(); }

         @Override public void showStorageRecovery() { renderProductScreen(ProductScreen.INSTALL_RECOVERY); }

         @Override public void show(String message) { MainActivity.this.show(message); }
      });
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

   private void showDiagnosticsScreen(ProductScreen screen) {
      this.enterProductScreen(screen, true);
      var canvas = this.diagnosticScreens.create(screen);
      this.productStatus = canvas.notice;
      this.homePanel.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
   }

   private void recordObservedRuntimeEvents() {
      this.diagnostics.observe(this.transition, this.selectedProfile == LaunchProfile.WINDOWS);
   }

   private DiagnosticsScreens createDiagnosticsScreens() {
      return new DiagnosticsScreens(this, this.diagnostics, this.compatibility, this.ubuntuStorage,
            this.windowsSetup, new DiagnosticsScreens.Host() {
         public void observeRuntime() { recordObservedRuntimeEvents(); }
         public boolean hasPermissions() { return hasAvfPermissions(); }
         public boolean guestConnected() {
            return selectedProfile != LaunchProfile.WINDOWS && ubuntuServices.connected();
         }
         public boolean frameVisible() { return MainActivity.this.frameVisible; }
         public String version() { return releaseVersionLabel(); }
         public String displayLabel() { return configuredDisplayLabel(); }
         public void checkCompatibility() { beginCompatibilityCheck(); }
         public void back() { navigateBack(); }
         public void navigate(ProductScreen screen) { renderProductScreen(screen); }
         public void desktop() { showRunningDesktop(); }
         public void show(String message) { MainActivity.this.show(message); }
      });
   }

   private boolean isoPresent() {
      return UbuntuIsoImport.present(this.getExternalFilesDir(null));
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
         this.sessionControls.closeImmediately();
         this.guestInput.releaseKeys();
         this.homePanel.setVisibility(8);
         this.sessionControls.hideButton();
         this.status.setVisibility(8);
         if (this.transitionView==null) {
            this.transitionView=new RuntimeTransitionView(this,this.transition,
                  ()->this.selectedProfile==LaunchProfile.WINDOWS,
                  ()->guestSession.confirmForceStop(this.transition.operation() == RuntimeTransition.Mode.REBOOT
                        ? PowerAction.REBOOT : PowerAction.SHUTDOWN),
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
      this.sessionControls.closeImmediately();
      this.homePanel.setVisibility(8);
      this.status.setVisibility(8);
      this.updateRuntimeEdgeExclusion();
      this.sessionControls.revealButton();
   }

   private void completeDesktopTransition() {
      if(!this.transition.desktop(android.os.SystemClock.elapsedRealtime())) return;
      this.runOnUiThread(this::recordObservedRuntimeEvents);
      String sessionOs=this.selectedProfile==LaunchProfile.WINDOWS?"windows":"ubuntu";
      android.content.SharedPreferences.Editor history=this.getPreferences(0).edit()
            .putLong(this.transition.events().containsKey("Windows setup frame")?"last_setup_windows":
                  "windows".equals(sessionOs)?"last_display_windows":"last_desktop_ubuntu",System.currentTimeMillis());
      if("ubuntu".equals(sessionOs))history.putString("last_desktop_graphics_ubuntu",
            this.displaySurfaces.renderer()!=null?"Mavk · кадр показан":
                  this.displaySurfaces.decoder()!=null?"H.264 · кадр показан":"Кадр показан");
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
      this.sessionControls.revealButton();
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
         this.sessionControls.closeImmediately();
         (new Thread(this::stopSelectedProfile, "U-AVF-stop-confirmed")).start();
      });
   }

   private void openUbuntuIsoPicker() {
      this.openInstallDocumentPicker(REQUEST_UBUNTU_ISO,false);
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent result) {
      super.onActivityResult(requestCode, resultCode, result);
      if (resultCode != RESULT_OK || result == null || result.getData() == null) return;

      Uri selected = result.getData();
      switch (requestCode) {
         case REQUEST_WINDOWS_INSTALL_ISO -> this.windowsSetup.selectIso(selected);
         case REQUEST_WINDOWS_WINPE -> this.windowsSetup.selectWinpe(selected);
         case DiagnosticsScreens.EXPORT_REQUEST -> this.diagnosticScreens.export(selected);
         case REQUEST_UBUNTU_ISO, REQUEST_UBUNTU_LIVE_ISO -> {
            boolean live = requestCode == REQUEST_UBUNTU_LIVE_ISO;
            new Thread(() -> this.importUbuntuIso(selected, live), "U-AVF-import-ubuntu-iso").start();
         }
         default -> {}
      }
   }

   private void openWindowsIsoPicker() {
      this.openInstallDocumentPicker(REQUEST_WINDOWS_INSTALL_ISO, true);
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

   private WindowsSessionViewport windowsSession;

   private void startPreparedWindowsSetup(WindowsSetupRuntime.Boot boot) {
      if(this.isManagedVmRunning() || VmLifecycle.compatibilityProbeActive())return;
      if(!this.hasAvfPermissions()) {
         this.show(UiLanguage.text("Для запуска нужны разрешения AVF."));
         return;
      }
      try {
         if(VmRuntimeService.startWindowsSetup(this,boot))this.windowsSession.attach(VmRuntimeService.windowsSetup());
      } catch(RuntimeException error) {
         ProductDialog.notice(this,"Windows Setup",rootMessage(error));
      }
   }

   private WindowsSessionViewport createWindowsSessionViewport() {
      return new WindowsSessionViewport(this, this.uiHandler, this.transition, this.displaySurfaces,
            this.frameSurface, this.displayConnection, this.guestInput, new WindowsSessionViewport.Host() {
         @Override public void selectWindows() { selectedProfile = LaunchProfile.WINDOWS; }
         @Override public void begin(RuntimeTransition.Mode mode) { beginRuntimeTransition(mode); }
         @Override public void refreshTransition() { transitionTick.run(); }
         @Override public void showWorkspace() {
            if (transitionView != null) transitionView.setVisibility(View.GONE);
            showFigmaWorkspaceScreen(ProductScreen.WINDOWS_HOME);
         }
         @Override public boolean frameVisible() { return MainActivity.this.frameVisible; }
         @Override public void frameLost() { frameVisible = false; }
         @Override public void geometry(int width, int height) {
            guestFrameWidth = width;
            guestFrameHeight = height;
         }
         @Override public void present(int width, int height) {
            frameSurface.setEncodedMode(true, width, height);
            frameVisible = true;
            completeDesktopTransition();
            showRunningDesktop();
         }
      });
   }

   private void onUbuntuIsoImported(boolean live) {
      this.runOnUiThread(()->{
         if(this.activityDestroyed)return;
         if(live) {
            if(this.productScreen==ProductScreen.SYSTEMS && !this.isManagedVmRunning())
               this.workspaceScreens.showLiveSession();
         } else if(!this.isManagedVmRunning()) {
            this.selectProfile(LaunchProfile.LINUX);
            this.renderProductScreen(this.productScreen==ProductScreen.CREATE_IMAGE?ProductScreen.CREATE_IMAGE:ProductScreen.LINUX_HOME);
         }
      });
   }

   private void importUbuntuIso(Uri source, boolean live) {
      try {
         this.show("Checking the selected official Ubuntu image…");
         boolean alreadyStaged = UbuntuIsoImport.stage(this.getContentResolver(), source,
               this.getExternalFilesDir(null),
               bytes -> this.show("Importing Ubuntu image: " + (bytes >> 20) + " MiB"));
         this.onUbuntuIsoImported(live);
         this.show(alreadyStaged ? "The selected Ubuntu image matches the copy already staged in U-AVF."
               : "Official Ubuntu 24.04.5 ARM64 image verified and ready.");
      } catch (Exception error) {
         this.show("Ubuntu image was not accepted: " + rootMessage(error));
      }
   }

   /** Converts density-independent pixels to physical pixels. */
   private int dp(int value) {
      return (int)((float)value * this.getResources().getDisplayMetrics().density + 0.5F);
   }

   private void restoreProfile() {
      var preferences = this.getPreferences(0);
      boolean previouslySelected = preferences.contains("launch_profile");
      String storedProfile = preferences.getString("launch_profile", LaunchProfile.LINUX.name());
      this.selectedProfile = LaunchProfile.WINDOWS.name().equals(storedProfile)
            ? LaunchProfile.WINDOWS : LaunchProfile.LINUX;

      this.compatibility.restore();
      if (previouslySelected) preferences.edit().putBoolean("first_run_complete", true).apply();
      this.renderProductScreen(previouslySelected ? ProductScreen.MODE_SELECTION : ProductScreen.ONBOARDING);
   }

   private void selectProfile(LaunchProfile profile) {
      this.selectedProfile = profile;
      this.getPreferences(0).edit().putString("launch_profile", profile.name()).apply();
      this.show(profile == LaunchProfile.WINDOWS ? "Windows mode selected." : "Linux mode selected.");
   }

   private void launchSelectedProfile() {
      if (guestSession.mayBeRunning() || this.isManagedVmRunning()) {
         this.show("A VM is already running. Stop it before launching another profile.");
         return;
      }
      if (this.selectedProfile == LaunchProfile.WINDOWS) {
         new Thread(() -> this.diagnosticLaunches.startWindows(DiagnosticVmLaunches.WindowsProbe.BOOT), "WinAVF-ui-windows-start").start();
         return;
      }
      if (!this.isoPresent()) {
         this.show("Choose the official Ubuntu 24.04.5 ARM64 image first.");
         this.openUbuntuIsoPicker();
         return;
      }
      new Thread(this::startUbuntuLive, "WinAVF-ui-linux-start").start();
   }

   private void stopSelectedProfile() {
      guestSession.requestGuestPower(PowerAction.SHUTDOWN);
   }

   private boolean skipDiskValidation() {
      return this.getPreferences(0).getBoolean("skip_disk_validation", false);
   }

   @Override public void onNewIntent(Intent intent) {
      super.onNewIntent(intent);
      this.setIntent(intent);
      Log.i("UAVF-H264", "New intent: encoded=" + intent.getBooleanExtra("encoded_display_probe", false));
      this.diagnosticCommands.handle(intent);
   }

   private void startUbuntuLive() { this.ubuntuLaunch.startLive(Profile.VIRGL_LINUX_FIRST, true); }

   private void startUbuntuLive(Profile profile) { this.ubuntuLaunch.startLive(profile, false); }


   private UbuntuDisplaySession createUbuntuDisplaySession() {
      return new UbuntuDisplaySession(this, this.uiHandler, this.lifecycle, this.transition, this.displaySurfaces,
            this.frameSurface, this.displayConnection, this.guestInput, new UbuntuDisplaySession.Host() {
         @Override public void connectServices(Object guest) {
            ubuntuServices.connect(guest);
         }

         @Override public boolean reconnectAllowed() {
            return !activityDestroyed && guestSession.mayBeRunning() && !guestSession.stopRequested();
         }

         @Override public boolean frameVisible() { return MainActivity.this.frameVisible; }

         @Override public boolean displayFocused() {
            return desktopHasFocus() && !sessionControls.isOpen() && !transition.visible();
         }

         @Override public void presentSurface(int width, int height, Runnable fit) {
            presentUbuntuSurface(width, height, fit);
         }

         @Override public void presentRawFrame(ConsoleFrameDecoder.Frame frame) {
            guestFrameWidth = frame.width;
            guestFrameHeight = frame.height;
            queueFrameForDisplay(frame);
         }

         @Override public void mavkDisconnected(Object guest) {
            onMavkDisplayDisconnected(guest);
         }

         @Override public void show(String message) { MainActivity.this.show(message); }
      });
   }

   private void startUbuntuFrameBridge(Object vm) {
      if (!this.activityDestroyed) this.ubuntuDisplay.start(vm);
   }

   private void onMavkDisplayDisconnected(Object vm) {
      boolean wasVisible = this.frameVisible;
      this.frameVisible = false;
      if (!guestSession.mayBeRunning() || !this.lifecycle.owns(vm) || !this.transition.acceptsDesktop()) return;
      if (wasVisible || !this.transition.visible()) this.beginRuntimeTransition(RuntimeTransition.Mode.DISPLAY_RECONNECT);
      this.transition.failure(UiLanguage.text("Сеанс Ubuntu всё ещё работает. Mavk потерял вывод изображения."),
            SystemClock.elapsedRealtime());
      this.transitionTick.run();
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

   private void hideStoppedGuestControls() {
      this.sessionControls.closeImmediately();
      this.sessionControls.hideButton();
   }

   private ProductScreen stoppedWorkspaceScreen(boolean temporary) {
      if (this.selectedProfile == LaunchProfile.WINDOWS) return ProductScreen.WINDOWS_HOME;
      if (temporary) return ProductScreen.SYSTEMS;
      return this.persistentUbuntuDiskFile().isFile() ? ProductScreen.LINUX_HOME : ProductScreen.LINUX_SETUP;
   }

   private File copyFile(File source, File target, long expectedBytes, boolean force) throws IOException {
      return GuestMediaFiles.copy(source, target, expectedBytes, force,
            () -> this.show("Copying Windows setup medium into app-private storage…"));
   }

   /** Surfaces a status line. Serial noise is recorded but never shown. */
   private void show(String message) {
      this.runOnUiThread(() -> {
         this.currentNotice = message;
         if(message.startsWith("SERIAL:") || message.startsWith("Writing complete VM serial log") || message.startsWith("Console closed:")) {
            return;
         }
         if (this.productStatus != null && this.homePanel != null && this.homePanel.getVisibility() == 0) {
            this.productStatus.setText(UiLanguage.text(message));
         }

         this.status.setText(message);
         if (!this.frameVisible) {
            this.status.setVisibility(0);
         }


      });
   }

   private enum LaunchProfile {
      WINDOWS,
      LINUX
   }
}
