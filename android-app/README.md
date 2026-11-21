# Android application source

`android-app` is the source tree for the primary U-AVF APK. The release entry point is `build-release.ps1`. Start changes here.

## Where to make a change

| Area | Source | Responsibility |
| --- | --- | --- |
| Screen history | `src/com/example/winavf/navigation/` | Stable route IDs, Back history and return to the running desktop. The Activity owns Android view transitions. |
| Android screen state | `ProductNavigationState.java`, `ProductTabs.java` | Android saved-state serialization and the main pager with its tab indicator. |
| VM state | `src/com/example/winavf/runtime/` | VM lifecycle guards, owner identity, observed start and stop progress. Android callbacks and VM service binding live at the app boundary. |
| Ubuntu serial console | `runtime/VmConsole.java`, `VmRuntimeService.java` | One reader and log per managed VM. Recreating the Activity replaces its observer while the reader keeps draining. |
| Ubuntu Live media | `src/com/example/winavf/storage/UbuntuLiveMedia.java` | Select a boot media profile, verify the ISO and prepare the temporary image with its original GPT layout. The caller must exclude a running VM before writing. |
| Diagnostic history | `src/com/example/winavf/diagnostics/` | Persisted row format, bounded history and report records. Android preferences and document export are app responsibilities. |
| Diagnostic workflow | `RuntimeDiagnostics.java`, `CompatibilityAudit.java` | Collect VM lifecycle events, persist compatibility runs, and supply the local history and report views. |
| Display preferences | `src/com/example/winavf/workspace/DisplaySettings.java` | Android storage adapter for display mode, resolution per mode and the running session's transport preference. It reads and writes the existing keys. |
| Android AVF | `src/com/example/winavf/AvfGuest.java` | VM manager and callback adapter for the Java AVF runtime. Guest lifecycle and UI reactions consume named events. |
| Guest media | `UbuntuWorkspaceMedia.java`, `WindowsWorkspaceMedia.java` | Ubuntu installer integrity profiles and disk preparation. Windows ISO/WinPE import, persisted URI access and explicit boot choices. |
| ISO selection and installation | `UbuntuIsoImport.java`, `WindowsSetupFlow.java` | Verified Ubuntu ISO staging, Windows setup dialogs and progress. The Activity owns document picker dispatch and VM startup. |
| Ubuntu AVF configuration | `UbuntuVmConfig.java` | Named boot media and configuration options, AVF display, GPU and audio builders. |
| Workspace disk | `UbuntuWorkspaceStorage.java` | Paths, allocation limits, checked disk changes and workspace preferences. |
| Guest media files | `GuestMediaFiles.java` | Copies app assets into staging and publishes verified files only after the size and digest match. |
| Guest readiness and audio | `UbuntuReadinessProbe.java`, `UbuntuAudioBridge.java` | LVH1 readiness, PCM header validation, stereo playback and vsock reports. |
| Windows session | `WindowsSetupSession.java` | Follow the runtime service across display retry and installation reboot. |
| Debug boot probes | `ConsoleLoopbackProbe.java`, `WindowsBootProbes.java` | Discrete firmware console and guest-agent probes routed by debug builds. |
| Viewport connection | `DisplayConnection.java`, `RawDisplayReceiver.java`, `GuestInput.java`, `SessionEdgeGesture.java` | Session lifetime, fallback frame transport, ordered UIN1 input, Android key/pointer mapping and the session drawer gesture. |

`compile-java.ps1` compiles navigation, runtime state, storage and diagnostic history with an empty application classpath, then compiles the Android integration against them. The APK, Android component names and JNI package are shared with the existing application. Keep Android and generated APK resources out of those four packages.

The Activity binds the screens and chooses the next step for each guest. Ubuntu Live and installed Ubuntu use one VM launch path; their media preparation is separate. The Windows setup service still owns the experimental Windows guest. `WindowsSetupRuntime`, `PersistentUbuntuDisk`, `EncodedDisplayProbe`, and `MavkDisplayRuntime` are the implementation entry points for those subsystems. Historical diagnostic intent handlers remain in the Activity.

## Build

Use PowerShell on Windows, JDK 17 or newer, Android platform 36, Build Tools 36.0.0 and Python 3. The release script accepts `-JdkDir`, `-SdkDir` and `-Python`. It also reads `JAVA_HOME` and `ANDROID_HOME`. The existing release assembly requires `-BaseApk` with the compatible platform payloads and a fresh `-OutputDir`. Signing uses the local U-AVF release keystore.

For source changes, `compile-java.ps1` can use the generated Java files from a matching release build and a fresh classes directory. This checks the application without copying the base APK. The Java tests in `tests/` use a separate output directory and a regular JDK.

When the base APK carries the old UDF reader, run `native/windows-iso/build.ps1` with the pinned libudfread tarball. Pass its two outputs to the release build using `-WindowsIsoLibrary <libuavf_windows_iso.so>` and `-UdfreadLibrary <libudfread.so>`. The old reader rejects the UDF partition layout used by the verified Windows 11 ARM64 25H2 ISO.

`ProjectBoundariesTest` also accepts the official Ubuntu 24.04.5 ARM64 Desktop ISO, the app assets directory and a scratch directory as three optional arguments. This exercises live disk assembly and prefix conversion using real payloads. It creates and removes a temporary image of about 4 GiB inside the scratch directory.
