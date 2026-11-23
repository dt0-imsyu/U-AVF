# Android application source

`android-app` is the source tree for the primary U-AVF APK. The release entry point is `build-release.ps1`. Start changes here.

## Where to make a change

| Area | Source | Responsibility |
| --- | --- | --- |
| Screen history | `src/com/example/winavf/navigation/` | Stable route IDs, Back history and return to the running desktop. The Activity owns Android view transitions. |
| Android screen state | `ProductNavigationState.java`, `ui/ProductTabs.java` | Android saved-state serialization and the main pager with its tab indicator. |
| Screens and widgets | `src/com/example/winavf/ui/` | Dashboard, installation screens, workspace settings, session drawer, dialogs and localization. The launcher and in-app brand mark share `res/drawable/ic_uavf_brand.png`. The dashboard wallpaper is `assets/product-ui/dashboard-wallpaper.png`. |
| Guest input | `src/com/example/winavf/input/` | Translate Android input to guest key and pointer events, manage the keyboard and edge gesture, and encode clipboard packets. |
| Home and workspace screens | `WorkspaceScreens.java` | Compose home tabs, workspace cards, installer review and Live media choices from the current state. |
| Ubuntu settings tabs | `UbuntuSettingsTabs.java` | Bind memory, CPU, graphics, input, guest services and storage controls to workspace preferences and Activity actions. |
| VM state | `src/com/example/winavf/runtime/` | VM lifecycle guards, owner identity, observed start and stop progress. Android callbacks and VM service binding live at the app boundary. |
| Ubuntu serial console | `runtime/VmConsole.java`, `VmRuntimeService.java` | One reader and log per managed VM. Recreating the Activity replaces its observer while the reader keeps draining. |
| Ubuntu Live media | `src/com/example/winavf/storage/UbuntuLiveMedia.java` | Select a boot media profile, verify the ISO and prepare the temporary image with its original GPT layout. The caller must exclude a running VM before writing. |
| Ubuntu boot preparation | `UbuntuLiveSessionMedia.java`, `UbuntuWorkspaceBoot.java` | Select temporary and persistent boot media, preserve per-session display choices and record the boot and installer handoff. |
| Ubuntu launch flow | `UbuntuLaunchFlow.java` | Coordinate AVF boot, readiness, Live versus installed settings and installer handoff. |
| Diagnostic history | `src/com/example/winavf/diagnostics/` | Persisted row format, bounded history and report records. Android preferences and document export are app responsibilities. |
| Diagnostic workflow | `RuntimeDiagnostics.java`, `CompatibilityAudit.java` | Collect VM lifecycle events, persist compatibility runs, and supply the local history and report views. |
| Diagnostic screens | `DiagnosticsScreens.java` | Bind diagnostics and history views, clipboard copies and document-picker export. |
| Compatibility session | `CompatibilitySession.java` | Own compatibility progress, restore the current result and submit selected fields only after consent. |
| Display preferences | `src/com/example/winavf/workspace/DisplaySettings.java` | Android storage adapter for display mode, resolution per mode and the running session's transport preference. It reads and writes the existing keys. |
| Android AVF | `src/com/example/winavf/AvfGuest.java` | VM manager and callback adapter for the Java AVF runtime. Guest lifecycle and UI reactions consume named events. |
| Guest media | `UbuntuWorkspaceMedia.java`, `WindowsWorkspaceMedia.java` | Installer integrity profiles, disk preparation, persisted ISO access and boot choices. |
| ISO selection and installation | `GuestIsoPicker.java`, `UbuntuIsoImport.java`, `WindowsSetupFlow.java` | Routes Android document results, stages verified Ubuntu media, and manages Windows setup dialogs and progress. |
| Ubuntu AVF configuration | `UbuntuVmConfig.java` | Named boot media and configuration options, AVF display, GPU and audio builders. |
| Workspace disk | `UbuntuWorkspaceStorage.java` | Paths, allocation limits, checked disk changes and workspace preferences. |
| Workspace actions | `UbuntuWorkspaceActions.java` | Ubuntu ISO selection, installation profiles and confirmed resize or deletion. |
| Guest media files | `GuestMediaFiles.java` | Copies app assets into staging and publishes verified files only after the size and digest match. |
| Guest readiness and audio | `UbuntuReadinessProbe.java`, `UbuntuAudioBridge.java` | LVH1 readiness, PCM header validation, stereo playback and vsock reports. |
| Windows session | `WindowsSetupSession.java`, `WindowsSessionViewport.java` | Follow the runtime service and own viewport attachment, display retry and installation reboot. |
| Debug boot probes | `AvfDebugDiagnostics.java`, `ConsoleLoopbackProbe.java`, `WindowsBootProbes.java` | Framework capability reports, firmware console and guest-agent probes routed by debug builds. |
| Debug Ubuntu media | `UbuntuGenericBootMedia.java`, `UbuntuDiagnosticMedia.java`, `UbuntuBlockIoAudit.java` | Fixed staging layouts, checked fixtures and reports for the older debug boot and block I/O scenarios. |
| Debug VM configuration | `DiagnosticVmConfig.java` | AVF config for the historical single-disk Windows, GNOME and recovery profiles. |
| Debug commands | `DiagnosticCommands.java`, `DiagnosticVmLaunches.java` | Route test actions in debuggable builds and start the existing diagnostic media through AVF. |
| Debug Windows media | `WindowsDiagnosticMedia.java`, `WindowsDiagnosticDiskReports.java` | Stage the legacy boot fixture, inspect its ESP and manage disposable witness files separately from the Windows ISO installer. |
| Debug serial capture | `DiagnosticSerialCapture.java` | Record the historical VM console and detect firmware markers across reads. |
| Viewport connection | `DisplayConnection.java`, `RawDisplayReceiver.java`, `GuestFrameSession.java` | Session lifetime, fallback frame transport, visible-frame state and ordered UIN1 input. |
| Launch and power overlay | `TransitionController.java`, `ui/RuntimeTransitionView.java` | The controller owns progress scheduling on the main thread. The view displays observed boot and power events. |
| Embedded guest surfaces | `GuestDisplaySurfaces.java` | Own the Windows and Ubuntu decoder surface, Mavk surface, viewport fit and teardown. |
| Session controls | `SessionControls.java` | Desktop menu, pointer capture, panel animation and display preferences during a VM session. |
| Ubuntu display session | `UbuntuDisplaySession.java` | Select Mavk, H.264 or raw display, track the active guest and scope retries to the active viewport connection. |

`compile-java.ps1` compiles navigation, runtime state, storage and diagnostic history with an empty application classpath, then compiles the Android integration against them. The APK, Android component names and JNI package are shared with the existing application. Keep Android and generated APK resources out of those four packages.

The Activity binds the screens and chooses the next step for each guest. Ubuntu Live and installed Ubuntu use one VM launch path; their media preparation is separate. The Windows setup service still owns the experimental Windows guest. `WindowsSetupRuntime`, `PersistentUbuntuDisk`, `EncodedDisplayProbe`, and `MavkDisplayRuntime` are the implementation entry points for those subsystems. In debuggable builds, the Activity delegates historical diagnostic intents to `DiagnosticCommands`.

## Build

Use PowerShell on Windows, JDK 17 or newer, Android platform 36, Build Tools 36.0.0 and Python 3. The release script accepts `-JdkDir`, `-SdkDir` and `-Python`. It also reads `JAVA_HOME` and `ANDROID_HOME`. The existing release assembly requires `-BaseApk` with the compatible platform payloads and a fresh `-OutputDir`. Signing uses the local U-AVF release keystore.

For source changes, `compile-java.ps1` can use the generated Java files from a matching release build and a fresh classes directory. This checks the application without copying the base APK. The Java tests in `tests/` use a separate output directory and a regular JDK.

Run the tests from the repository root. The APK supplies the platform manifest
used to generate the test classes.

```powershell
python tools/release/run-java-tests.py --apk $baseApk
```

When the base APK carries the old UDF reader, run `native/windows-iso/build.ps1` with the pinned libudfread tarball. Pass its two outputs to the release build using `-WindowsIsoLibrary <libuavf_windows_iso.so>` and `-UdfreadLibrary <libudfread.so>`. The old reader rejects the UDF partition layout used by the verified Windows 11 ARM64 25H2 ISO.

The experimental Windows installer takes one Windows 11 ARM64 25H2 (ru-RU) ISO, build 26200.6584. U-AVF reads the ISO in place, adds the display and storage helpers to a private copy of WinPE, and assembles the workspace disk with the guest UEFI and the Windows installation files. The user selects only the Windows ISO.

For a release build with this installer, package the matching FAT and wimlib JNI libraries using `-WindowsSetupFatLibrary` and `-WindowsWimLibrary`. Supply the ZIP from `../tools/release/build-windows-iso-boot-runtime.py` with `-WindowsIsoBootRuntime`. The bundle contains the VM's EDK2 firmware and the WinPE add-ons. The stock Windows images come from the user's ISO.

Build the JNI libraries with `native/windows-iso/build-setup-fat.ps1` and `native/windows-iso/build-wim.ps1`. These scripts check the FatFs and wimlib source archive digests. The runtime bundle builder accepts the ARM64 WinPE overlay, EFI shim, [EDK2 r11 firmware](../docs/R11_DEBUG_UART_ALIGNMENT_RUNTIME_2026-09-08.md), and the stock and prepared BCD files. Those build inputs and the platform base APK must be obtained separately from the source checkout.

After building those libraries and the runtime ZIP, assemble the app from the
repository root. Set the path variables to the corresponding build outputs
and choose a version code higher than the installed release.

```powershell
./android-app/build-release.ps1 -BaseApk $baseApk -OutputDir $outputDir `
    -VersionName $versionName -VersionCode $versionCode -RequireUbuntuDesktopChoices `
    -WindowsIsoLibrary $isoLibrary -UdfreadLibrary $udfLibrary `
    -WindowsSetupFatLibrary $fatLibrary -WindowsWimLibrary $wimLibrary `
    -WindowsIsoBootRuntime $winpeRuntime
```

The release script checks Android resource IDs, Ubuntu platform profiles,
packaged JNI symbols, the WinPE bundle, and APK signing. The WinPE check uses
the Android loader's file list and firmware fingerprints.

The older `build.ps1` serves development configurations with local platform
images. Use `-PlatformAssetsDir` to select the matching platform images and
`-BootWrapper` to select the pinned U-Boot wrapper. Pass `-JdkDir` or `-SdkDir`
to override the default tool paths.

### Relink the Windows WIM adapter

`build-wim.ps1` builds a static ARM64 libwim archive and links it to two JNI
sources. To relink with a compatible libwim build, apply
`native/windows-iso/enable-wimlib-fd.py` before compiling libwim. Build the
archive with position-independent code for `aarch64-linux-android35` and
disable NTFS-3G and FUSE. The JNI source uses the file-descriptor API added by
the patch.

From the repository root, set `$clang` to the Android NDK compiler, `$wim` to
the libwim build directory, and `$output` to the destination
`libuavf_windows_wim.so` file. Pass that file to `-WindowsWimLibrary` when
building the APK.

```powershell
$source = 'android-app/native/windows-iso'
$link = @(
    '--target=aarch64-linux-android35', '-O2', '-fPIC', '-shared',
    '-Wall', '-Wextra', '-Werror',
    '-Wl,-z,relro,-z,now,-z,max-page-size=16384,--exclude-libs,ALL',
    '-Wl,-soname,libuavf_windows_wim.so', "-I$wim/include",
    "$source/windows_wim_split_jni.c", "$source/windows_boot_wim.c",
    "$wim/.libs/libwim.a", '-o', $output
)
& $clang @link
```

`ProjectBoundariesTest` also accepts the official Ubuntu 24.04.5 ARM64 Desktop ISO, the app assets directory and a scratch directory as three optional arguments. This exercises live disk assembly and prefix conversion using real payloads. It creates and removes a temporary image of about 4 GiB inside the scratch directory.
