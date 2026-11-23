# U-AVF 1.2 checkpoint

## Windows 26H2 release target

The English ARM64 ISO on Tab S11 has SHA-256
`8992F2D2CFD2FA647E579E9EB31FC21F34DA8333C29B6170FDE17D2ED6623E9C`,
matching the English download listed by Microsoft for Windows 11 26H2.
Its WinPE kernel is `DB0C8B367C1927EB4D39D922CB54DA4621E9B3C241E2655D33C5ECD46719AA13`.
The PE export table puts `DbgLoadImageSymbols` at `0x2f7b50`. A unique raw
24-byte context from the corresponding 25H2 entry instead matches `0x498420`.
That mismatch demonstrates that byte uniqueness alone cannot identify a safe
patch location across servicing builds. The timer and cache-table instruction
matches found by this scan are candidates. They need semantic validation,
and the PMU helper targets and secondary-CPU bootstrap need separate checks.

The kernel's CodeView record identifies `ntkrnlmp.pdb`
`7A9F162A39E89C67CD02CF2126D51A651`. The matching Microsoft symbols identify
`HalpPciAccessMmConfigSpace` at `0x497b78`, `HalpGitAcknowledgeInterrupt` at
`0x214920`, `KiSynchronousException` at `0x2b69a0`, and `KiEmulatedReadDword`
at `0x62de20`. The eight-vCPU startup path also changes: for example,
`KeStartAllProcessors` moves to `0xb53e90` and `KeNumberProcessors` to
`0xe09a80`. Function-relative offsets and register assumptions are still
subject to verification before these locations can be used to boot a guest.

The 26H2 image contains live instructions at all three 25H2 trampoline
locations (`0x665b00`, `0x665b20`, `0x665c40`). Its PCI device and bus
shift bytes are at `0xc03c38` and `0xc03c39`, with initial values 15 and
20. In the PCI access routine, the bounds check and the page-relative
load start at `0x497b9c` and `0x497bbc`. The timer branch at `0x214998`
still targets the corresponding instruction at offset `0x20` in its
routine.

Disassembly puts the startup handshake probes in `KeStartAllProcessors`
at `0xb54140` (compare the startup result), `0xb5416c` (load the
callback argument), and `0xb54484` (return). Adding the old offsets to
the new symbol would instead probe a call, a conditional branch, and an
epilogue register restore. These results are static evidence. Enabling
this ISO requires verified executable space for the relocated trampolines,
validation of the PMU and GIC runtime state, and an eight-vCPU WinPE boot
on Tab S11.

An offline relocation check found file-backed zero ranges in executable
`.text` at `0x62e760`, `0x62e780` and `0x62e800` large enough for the
timer, PCI and PMU trampolines respectively. Their relocated branches and
the PCI page-relative address encode within ARM64 immediate bounds. This
does not establish that the ranges remain available in the loaded kernel
or that the 26H2 PMU trap context matches the 25H2 layout.

The Java KD runtime now selects both the kernel patches and the SMP
breakpoints from the same kernel identity. The 26H2 candidate matched
28 original byte ranges in the extracted kernel, including the empty
trampoline ranges and the SMP instruction checks. Java compiled and
18 local tests passed (5 skipped). The ISO import gate still limits new
workspaces to the verified 25H2 profile pending the 26H2 hardware test.

QA `1.2-anyiso.5` upgraded the isolated package in place on Tab S11 and
booted the previously prepared English 26H2 disk. KD reported
`WINDOWS_SMP_ONLINE=8`, and the guest displayed Windows Setup. The stock
manual Setup opened its media-driver prompt. The automatic installation
route was unavailable because Android reported 15,316,052 KiB free
against a 15 GiB requirement. Automatic installation and the first
installed boot remain unverified.

## Read-only Windows ISO regression on Tab S11, 2026-11-22

The ARM64 WIM library was run against `sources/boot.wim` from both ISOs on
the tablet. The Russian 25H2 ISO (`26200.6584`) returned verdict `1` (supported
WinPE build). The English `26300.9457` ISO returned verdict `2` (ARM64 Setup
without a matching boot profile). The scanner identified both files as Setup
media independently of language. This was a native library test. It did not
run the Android picker, prepare a workspace disk, or start WinPE.

## RC16 update on Tab S11, 2026-11-21

The installed RC15 (version code 45) was used as the payload base. RC16
(code 46, SHA-256 `7A0F09C165D11D48F07F322CB5EE4F542FC2BACB01861EEEE2EC4DA2F869F024`)
installed over RC15 with the same package and signing certificate. All 47
guest asset and library entries matched in size and CRC. Installer profile
and Windows JNI/UDF checks passed at build time.

The existing Ubuntu workspace still showed its 36 GB disk. On the tablet,
the settings menu switched through Russian, Spanish, German, Simplified
Chinese, Japanese and English. English was restored. Diagnostics and its
journal opened, the export action was visible, and search showed the
expected empty result for a nonexistent event. This check did not repeat
VM installation, AVF telemetry upload, or desktop performance measurement.

The subsequent RC16 on-device compatibility check returned `COMPATIBLE`.
It ran a disposable VM with sharing disabled. VM creation, kernel boot,
guest memory (4,084,658,176 bytes observed), disk write/readback and vsock
all passed. The application saved the full English report through Android's
document picker. The exported file was read back and contained the new
compatibility result and its start/end events (15:35:43–15:36:18 UTC).

### Firestore verification

On 2026-11-21, the active default database rules were read back from the
Firebase Rules API and matched `backend/firebase/firestore.rules`. The
active release was updated at 2026-11-15 15:08:41 UTC and includes the
direct manufacturer/model path. The local Auth and Firestore emulator
suite passed for grouped create, consent, deduplication, receipts and
legacy compatibility. This verification made no production writes.

## Mavk release checks

- Installed GNOME on Tab S11 remains on the tested logical-upload encoder
  and D24S8 reuse. The previous Overview A/B reached 77.43 ms for the
  first host reply and 23.07 ms to the successor. Opening physical p95
  is still around 25 ms. See `Mavk/docs/LOGICAL_UPLOAD_2026-11-09.md`.
- On the current guest, XRandR reports 1920×1200 at 60.00 Hz as its only
  mode at that resolution. Android reports active 120 Hz. There is no
  ready 90 FPS guest mode, so the optional GNOME 90 FPS run is deferred.
- KDE X11 retest on the same installed disk and encoder (CID 2143).
  Four 1.2-second drags give 30.01, 29.82, 29.35 and 29.97 visible
  position updates/s. Visible p50/p95 is 33.355/42.045 ms. Android receives
  29.36–30.10 present requests/s during the same gestures, with 2.47–2.70 ms
  median dispatch. That limits the next investigation to frame production
  upstream of Android presentation. No quick 60 FPS fix was established.
  The GNOME session selection was restored with AccountsService and the
  guest powered off cleanly.
- Recording, position timestamps, host timestamps and XRandR screenshot
  are in `Mavk/artifacts-local/uavf12/`. The FPS tuning time box is closed
  for this release plan.

### Native revision carried by RC7

The RC7 `lib/arm64-v8a/libmavk_runtime_probe.so` matches the retained
`frame-credits/libmavk_runtime_probe.so` by size (5,090,584 bytes) and SHA-256
`D2AC2A29B6913E9DDE026B01552F1C7D90E10272A4811D2AD5A53964B54A730B`.
The matching artifact, its `*.candidate` source snapshots and
`host-build-final.log` are under
`Mavk/artifacts-local/gfxstream-app-20261027/live-runtime1/apk-window-shaders/replay/live-io-candidate/lazy-before-barrier/frame-credits/`.
The current top-level `host-bootstrap.cpp` and `bridge_presenter.cpp`
have different hashes from those snapshots. Start any native surface
reattachment build from the retained source set and verify guest protocol
compatibility before replacing the packaged library. The Android-only
integration candidates retain this RC7 binary.

An isolated ARM64 rebuild succeeded on 2026-11-20 in
`D:/UAVF-builds/mavk-rc7-reconstruction-20261120/`. It uses the retained
gfxstream source archive, current shared Mavk sources and the nine
`frame-credits` host overlays, including both decoder-state headers.
CMake used NDK 28.2.13676358, Android API 31 and the
`mavk_runtime_probe` target in Release mode. The rebuilt ELF exports the
same six JNI entry points. Its hash differs from RC7, so this is a
buildable investigation baseline. Device validation remains required
before substituting it in U-AVF.

## Native UI

The r12 candidate is `Mavk/artifacts-local/uavf12/ui-build-r12/U-AVF-mavk-input120.apk`,
SHA-256 `D88B3A935506671B0DDD07BA7900D36FE7A0BA0F736389924F639E98B060BFD6`.
The build compares all 60 original non-DEX entries against the signed
installed-controls APK, including both video paths, and verifies 76 local
Figma images. The original rollback has SHA-256
`922deb2a5b65c5540dd3b4cba6106abb65b6ebe0cef818430eac1a239b122045`.

The Figma 439:2 migration covers the dashboard, system list and details,
settings, workspace creation, installation status and recovery, diagnostics,
AVF permission help, and the Ubuntu boot and power controls. An installed
Ubuntu card is driven by observed runtime readiness. The existing Windows
loader remains experimental and is not counted as a newly installed workspace.

- Back uses app navigation history. Both the visible button and Android Back
  returned to the actual diagnostics entry point in the tablet smoke tests.
- Russian, English and Spanish switch without restarting. Journal event
  payloads are kept verbatim. Language and journal filters are anchored menus
  on r12, as are supported resolution and shortcut choices.
- One selector maps fallback, H.264 with VirGL, and Mavk to the existing launch
  preferences. Changing mode requires a stopped VM. Only fallback exposes the
  configured resolution control. System shortcuts can be enabled after Android
  grants access to the optional keyboard service.
- Confirmed bright-yellow Android selector on r9 (sample RGB 254,187,34);
  the same long hold on r10 gave RGB 42,49,58. On r10 a series of 12 quick
  tab switches recorded 498 Android UI frames, one janky frame, p95 5 ms.
  This sample measures app UI rendering, independent of guest FPS.
- Boot and installation progress report real events with an indeterminate
  indicator. Existing JVM transition and lifecycle tests pass. The original
  r5 UI was tested through Ubuntu boot, AHB desktop and clean guest shutdown.

Open work for the release includes installer choices for KDE/Mavk, a safe
Windows ISO installer, real AVF compatibility execution and Firestore migration,
and verification of Activity recreation during an in-flight VM operation.
No VM disks, firmware, H.264 assets or Mavk renderer binaries were replaced
for this UI batch.\n

The r12 APK installed on the Tab S11 with no running VMs. The anchored
language popup and the fallback display page with all three choices and an
editable 1920 × 1080 resolution were observed. The full automated r12 sequence
did not finish because the active screen changed while it was running.
The earlier r9 tablet pass exercised Russian → English → Spanish → Russian.
Do not repeat or overwrite user-selected display preferences during live review.

## DeepSeek integration, 2026-11-11

Signed candidate: `Mavk/artifacts-local/uavf12/deepseek-integrated-r4/U-AVF-mavk-input120.apk`
(SHA-256 `94DB3382041DFFE3831EE3FBDDDDA81A403B6D82303C352F045B3901ED06E80B`).
Tab S11 accepted the in-place update. All 136 non-DEX package entries match
the signed r13 by SHA-256. R4 adds one verified H.264 Live-only prefix asset.
Keep r13 as the APK rollback and
`Mavk/artifacts-local/uavf12/deepseek-integration-preimage` as the Java
rollback. The isolated DeepSeek package, firmware, ISO and installed guest
runtime were kept intact.

- Selective integration preserved the app owner, installed disk and display
  backends. English, Russian, Spanish, German, Simplified Chinese and Japanese
  settings were reached on the tablet. The diagnostic journal stayed English.
- The session menu showed the live mode and opened its dropdown immediately
  below its own row. Selecting another mode prompted for confirmation and
  took effect after a clean Ubuntu shutdown and the next launch.
- Mavk presented actual 1280×800 AHBs on the installed Ubuntu desktop.
  The explicit raw fallback booted at 1920×1080 according to the guest's
  geometry log and produced a visible desktop. These tests used normal
  app-owned VM start and shutdown.
- H.264 decoded and displayed 1920×1200 through MediaCodec. The host now
  sets the Surface buffer size and waits for its confirmation before decoder
  startup, then maps input to the displayed stream geometry. On the
  installed producer a 1920×1080 request reached X11, then the legacy encoder
  changed the desktop back to 1920×1200. The host reported
  `guest_negotiation=false` and retained H.264. The requested 1280×800 case
  also produced a 1920×1200 stream on r2. R3 verified a native 1920×1200
  H.264 boot and a guest-initiated shutdown without a fallback modal.

The source producer and GLX capture payload support UVF1 size negotiation.
Local wire tests covered all three dimensions and legacy UIN1 preservation.
In isolated Ubuntu Live, Tab S11 confirmed negotiated streams and decoder
Surfaces at 1280×800 and 1920×1080. At 1280×800 the installer window moved
with touch input and the newly exposed area updated. At 1920×1080 the
guest geometry matched the encoded stream and the centered viewport.
Reports and screen captures are under
`Mavk/artifacts-local/uavf12/deepseek-live-encoded-check`.

R4 ships that payload exclusively for H.264 Live. To rebuild it from r13,
extract `assets/uavf-live-encoded-geometry-prefix.img` from R4 and pass it
to `build-mavk-input.ps1 -EncodedLivePrefix`. The prefix replaced only the two encoded
Python files in CASPER/INITRD and verified the original GPT, EFI loader
and kernel bytes. The installed Ubuntu producer needs a separate safe
upgrade. The APK leaves it in place and reports any size mismatch. The
complete mode-by-resolution matrix still needs a regression pass.

On the main package, Recovery → Temporary Live session selected the new
prefix with the official ISO and no persistent guest disk. The 1280×800
request matched the X11 log, UVF1 stream, MediaCodec Surface and screenshot.
Evidence is in `Mavk/artifacts-local/uavf12/deepseek-integrated-r4`.
R4 then shut down normally. The original 1920×1200 H.264 preference was
restored. A 12-transition tab sample recorded 1 janky frame out of 1112
and a 6 ms p95 Android UI frame time. Export through the Android file picker
produced the English report saved in the same evidence directory.

The next release stages are real device compatibility and consent-based
Firebase history, Mavk stability and verified X11/Wayland performance,
automatic Ubuntu installation and graphics selection, the experimental
Windows ARM64 ISO installer, then a full release regression. Complete the
current display integration before switching to compatibility.
The existing compatibility result uses schema 2 and records
`VM_BOOT_TEST=NOT_RUN_BY_PREFLIGHT`. Keep those historical reports and
invalidate their positive status for the new real-boot assessment.

### Installed H.264 maintenance

`deepseek-integrated-r4/installed-h264-geometry-update.zip` contains the
same producer and capture code tested in Live, plus the installed-system
updater. SHA-256
`b37ce2f2efa97dbde15a8e9d6668ebcf32261745fd4366ca90b50f8c891058dd`.
The existing UCTL protocol has no file-update operation. Use the archive
inside a managed Ubuntu 24.04 guest only, with its Ubuntu administrator.
Extract it and run `sudo python3 update-uavf-encoded-geometry.py apply`.
Reconnect H.264 or restart Ubuntu. The same bundle with `rollback` restores
the original producer. The update preserves systemd units, the raw bridge,
the current capture for existing connections and user data. It activates
the new entrypoint only after saving the old one and verifying the versioned
capture. Four filesystem tests passed, including an interrupted activation
and refusal to roll back over a later installation. This package has not
been applied to the installed Ubuntu on Tab S11.

### Real base AVF compatibility

The compatibility screen now runs a separate app-owned VM. It allocates
4 GiB through AVF, boots an 839168-byte initrd with the proven Ubuntu kernel,
and checks a 64 MiB memory region. A fresh nonce over vsock authorizes a
write only to the gap between the ESP and backup GPT of its temporary image.
Android reads back the witness after AVF stops the VM. Test media uses no
workspace disk, ISO or networking.

On Tab S11, the first small launch failed in the existing U-Boot wrapper
at 512 MiB. At 4 GiB, the guest reached Linux init. The missing AF_VSOCK
family was traced to three modules omitted from the small initrd. The
final image includes the exact ARM64 modules from Ubuntu's matching
`linux-modules-7.0.0-31-generic_7.0.0-31.31~24.04.1` package (archive
SHA-256 `f3580aee9bda3223ef6558cd435132ffebe6ce1fb342af1497ff6b00320ce85d`).
The signed-off test disk asset is
`assets/uavf-compat-boot.img` in `deepseek-integrated-r4/compat-qa-final/U-AVF-QA-compat.apk`,
SHA-256 `b2bec4208f5d5e02f42ace1a36a18c08c363ccb1b2c5af9887da85af8fabb082`.
Build with `-AvfCompatibilityMedia` pointing at that extracted asset.

The main compatibility screen was exercised in the isolated QA package
with all four packaged preflight assets and the real probe. All nine
checks passed on Tab S11. The UI showed “Совместимо”, AVF's running VM list
was empty afterward, and the English export recorded separate scope for
Ubuntu, Windows and Mavk. Evidence is under
`deepseek-integrated-r4/compat-product-ui`. Schema 3 invalidates preflight-only
positive results while keeping their historical local report.

### Compatibility statistics and consent

The signed R6 candidate is
`Mavk/artifacts-local/uavf12/compatibility-r6/U-AVF-mavk-input120.apk`.
SHA-256 `967CFFD382A3CE675AA599A0D01751A49C65A52157CBE6DB53389DD404FCD9CA`.
The build verified all 138 unchanged non-DEX entries and replaced the
privacy notice. R6 installed in place on Tab S11.

The R6 app requested permission before each check. Choosing “Continue without
sharing” started the same local test. Current builds ask on first run and use
the saved sharing switch for later checks. The result screen uses a green check,
a muted red cross or a neutral mark, with a text status for each stage.
The test on the signed R6 used the local-only option and all nine stages
passed. The associated screenshots and English report are in the R6 directory.
After the compatibility test, the existing Ubuntu disk booted from the normal
dashboard into H.264 at 1920×1200. The app then received AVF
`STOP_REASON_SHUTDOWN` after the normal session stop action.
On the same Tab S11, an isolated build of the Android client sent a
synthetic report to the loopback emulators only after consent. Repeating
the run ID returned its receipt and left the slot count at one. The
no-consent call created no Auth credentials. The fixture and temporary
ADB forwarding were removed from the tablet afterward.

The v2 client and rules group results by manufacturer, model and configuration.
One atomic commit reserves a per-account slot and creates the report and its
receipt. Emulator tests passed for v2, repeat rejection, consent enforcement
and the v1 write path. Production deployment was initially blocked by CLI
authentication, then completed on 2026-11-15. The direct model layout and the
previous routes are both present in the verified active rules.
See `backend/firebase/README.md` for the local validation command and schema.

### Ubuntu installer GNOME graphics candidate

The signed candidate is
`Mavk/artifacts-local/uavf12/installer-mavk-r1/apk/U-AVF-mavk-input120.apk`.
SHA-256 `F545E5313A34EB61691CFCF2073700ABE2CBA6AFA46E69C740BA0DCAD7FA6C62`.
The build retained all 139 non-DEX entries from compatibility R6 and added
the independently hashed Mavk installer prefix. The chooser appears for a
new workspace on builds with that prefix and the matching Android renderer.
The GNOME X11 H.264 option selects the existing installer prefix. The Mavk
option verifies the add-on before partitioning, installs it in the audited
Ubuntu target before the ready marker and carries the requested graphics mode
into the existing UCTL first-boot handoff. H.264 remains selectable in the app.

The package uses the device-tested logical-upload encoder and D24S8 Mesa
build. It includes the KWin X11 unit wrapper, installed only when the target
already contains the Plasma X11 unit. Installing Plasma packages and selecting
the Plasma session in the new workspace remain separate unfinished work.

The prefix audit passed for the package manifest and each extracted member,
the retained kernel and bootloader, and the existing encoded runtime. Four
Linux staging tests covered the successful call and repeat, corrupt archive,
wrong partition, and target-install failure. The installer layout, account,
graphics boot gate, Android lifecycle, and transition regressions passed.
The staging tests use disposable paths and a stand-in for Curtin. The
candidate still needs an end-to-end ISO install and first Mavk boot on the
tablet. It has not been installed over the running baseline. The tablet
reported 46 GiB free and no running VMs at the last check, below the requested
roughly 50 GiB reserve for starting another installation.

### Ubuntu desktop options and deferred Plasma installation

Candidate R2 supersedes the GNOME-only installer R1. Signed APK at
`Mavk/artifacts-local/uavf12/installer-desktops-r2/apk/U-AVF-mavk-input120.apk`,
SHA-256 `EC602FE0304C49030CC5AA1CBF9F0FB9331F43EA79CD4C84C81FF8B596C9EA30`.
All 139 R6 non-DEX entries were preserved. The two new verified prefixes
carry the same guest Mavk package. GNOME supports H.264/OpenGL and Mavk
without downloading Plasma. The KDE choice requests Ubuntu's
`plasma-desktop` and `kwin-x11` packages in the target and sets the Setup
account's Plasma X11 session while keeping GDM and the original login policy.

For R2-managed GNOME installs, the Display settings can request a fixed
Plasma provisioning job while the installed VM is running. The guest
reports pending, installed or failed status through UCTL. Once successful,
the app selects Mavk for the next boot. H.264 and the original GDM session
picker remain available. Older guest installations lack the provisioning
unit and don't expose the on-demand action. They receive no automatic
filesystem changes from an APK update.

The two prefixes passed source, embedded archive and protected boot content
checks. Linux tests covered package ordering, keeping GDM, preserving account
fields, rejecting locked accounts, invoking only the fixed provisioning
unit, and refusing a normal UCTL reboot during installation. The package
download, real systemd job, and clean GNOME/Plasma installs are still pending
on hardware. The tablet was at 46 GiB free with no running VM. R2 was not
installed on the tablet.

The combined signed Windows QA APK also contains all three Ubuntu installer
profiles. The release assembler now validates the assembled package against
the full prefix and 126 MiB ESP hashes for every included profile.
`-RequireUbuntuDesktopChoices` makes all three mandatory. The existing signed
combined APK passed that check, and substitution of an incorrect ESP hash
failed independently for each profile. The legacy H.264-only APK still
passes in legacy mode and fails the three-profile check.

New app source preserves an explicit user selection of H.264 or another
display mode if the first installed boot has to be retried. The Plasma
install controls explain the Ubuntu internet requirement before submission.
Installed ESP recovery now accepts the exact verified ESP of any of the
three profiles with the existing single-backup and readback checks. The
sparse host fixture verified restoration, refusal of unknown ESPs and
preservation of GPT, ISO and root. The Android source build and five staging
tests plus the account/session test passed. These changes are awaiting a new
APK/device pass. The tablet has 24 GiB free, below the current 38 GiB
admission for the minimum 32 GiB Ubuntu target plus media and reserve.

### Windows ARM64 ISO intake

The signed intake candidate is
`build-logs/uavf12-windows-iso/apk/U-AVF-mavk-input120.apk`,
SHA-256 `DD09527218A1716F72B53929FF5CEC6DF61253419778D0389D9F12CF5AB55370`.
The candidate preserves all 141 existing non-DEX entries from desktop R2.
The Windows workspace can inspect a selected UDF ISO in place through the
Android document picker. It checks the ARM64 EFI machine and the WIM headers
for boot and install images, including an ESD install image. The original
experimental boot action remains in the workspace menu.

The ARM64 reader ran on SM-X736B and passed both positive fixtures and the
x64 EFI, invalid boot WIM and missing install image rejection cases. The
fixtures exercise format detection, not full WIM integrity or Setup. The
APK was built and signed locally. The Windows wizard and a real Microsoft
ISO have not been exercised on the tablet with this candidate.

The next installation gate is assembling a product boot medium from the
selected ISO and signed guest drivers, then using the verified one-disk
geometry in a fresh Windows workspace. The previous diagnostic boot relied
on host KD to apply the proven Windows runtime adaptations. A standalone
app-owned boot still needs those adaptations and the Windows display service
under the product lifecycle. No user disk or active app was changed.

The local `tools/windows-reassessment/stream-setup-disk.py` assembles the
known 25H2 Setup fixture from the retained WinPE template and two split WIM
parts. It reads all inputs in place, verifies their digests, and can publish
only a new disk after verifying the combined Setup bytes. An output disk
keeps the Setup partition and adds a sparse 64 GiB unallocated install area.
The output path enforces the 50 GiB free-space reserve and refuses to replace
an existing disk.

Read-only assembly of the full retained fixture reproduced the earlier
Setup SHA-256 `03E3DB8CBD9B3B7B16DD0AE22C356DDB68E9B4B5847FDD305A7E2692DABB8EE5`.
The four planned GPT writes were byte-identical to the retained geometry
plan. A small Windows filesystem test exercised sparse output, GPT readback,
and rejection of existing output and incorrect media hashes. The real disk
creation request was rejected by the storage reserve. This is an off-device
assembler for the retained fixture. Wiring user ISO extraction, disk assembly
and the app-owned Setup/installed boot lifecycle remains open.

### Windows ISO to split WIM on Android

`WindowsInstallIso.openPayload` exposes a WIM inside UDF as a read-only
seekable descriptor. On SM-X736B, direct positional reads of both boot and
install WIMs succeeded after closing the original ISO descriptor. Android
denied reopening that proxy via `/proc/self/fd`. The wimlib 1.14.5 adapter
uses the existing descriptor and keeps filename-based overwrite unavailable.

On Tab S11, the adapter read a 1,850,275-byte synthetic XPRESS WIM straight
from its UDF image and wrote three SWM parts, with no intermediate WIM copy.
Host wimlib verified the parts, then extracted both files with SHA-256 matching
the original fixtures. A clean `build-wim.ps1` rebuild also passed the device
test. The isolated test app was removed. This verifies the
conversion on Android. It still needs to feed the installation disk builder,
and real Microsoft ISO scale, solid ESD conversion and Setup boot remain
unverified. The production APK and guest disks were not changed.

### Android Windows Setup disk assembly

`WindowsSetupDisk.prepare` connects the ISO descriptor, SWM conversion, FAT32
write and GPT construction. This experimental profile requires the exact
retained Windows 11 ARM64 25H2 ISO and matching WinPE template. It stages a
new disk privately, verifies every SWM after writing, creates the sparse
64 GiB target area, then publishes the disk with `RENAME_NOREPLACE`. Admission
enforces the 50 GiB reserve with space for both split parts and the media.

On Tab S11, the isolated test converted a fixture ISO and wrote all three
resulting SWM parts into a FAT32 test disk. An independent host reader matched
all parts and confirmed the EFI fixture and outer disk prefix were unchanged.
Android GPT expansion matched all four archived writes and reached
77,846,282,240 logical bytes without allocating the target area. Publication
refused an existing file and succeeded for a fresh path. The test package
was removed. The real ISO preparation and product VM launch remain open.

### Windows Setup preparation in the workspace UI

The signed candidate is `build-logs/uavf12-windows-iso/apk-setup/U-AVF-mavk-input120.apk`,
SHA-256 `6955A7E1C3B76ECCBDD89F52AD55A7616622207212DF746669968DC5E854D284`.
The Windows workspace opens the document pickers for the retained ISO and
matching WinPE template, reports available and required space, and runs
the preparation job off the UI thread. The workspace displays actual job
phases with an indeterminate progress bar and prevents duplicate starts.
Both source files are read in place. The previous Windows boot action is
still available in the workspace menu.

The build preserved all 141 non-DEX entries from desktop R2 and added
the ARM64 ISO reader, WIM converter and FAT writer. A separate tablet test
used sparse descriptors of the exact input sizes and proved that the
storage guard stops before preparation writes and clears interrupted private
staging. The test APK was removed. The signed candidate is local and was not
installed over the baseline. On this tablet, 46 GiB is available versus
the current 67 GiB preparation threshold. The real prepared-disk Setup and
installed Windows launch are still open.

### Windows first-boot admission and live-display template

The current source uses `windows-display-live-name-fix-20261008.img`,
SHA-256 `A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A`.
This is the retained WinPE image that produced the app-owned Setup display.
Its boot WIM contains the verified capture executable name. The older
`apk-setup` candidate still points to the shell-lifetime template.

Before publishing a new disk, the builder now checks the exact fresh GPT
layout, both GPT table copies, the ARM64 EFI and boot WIM hashes, and the
complete SWM sequence. The FAT writer admits the existing source set in the
hash-pinned live template and rejects overwriting other populated SWMs.
Host read-only hashing confirmed the real template and boot WIM identities.
On Tab S11, isolated fixtures verified both FAT variants, read-only boot
hashing, GPT acceptance and corrupted-table rejection. The test package
was removed. These changes are source-only for the product APK at this
checkpoint. The proven boot still requires the external KD observer and
its in-memory adaptations, so standalone Setup is not enabled.

### App-side Windows KD bootstrap building blocks

`WindowsKdWire` implements the loader gate and bounded serial packet framing.
`WindowsKdBootProfile` checks the pinned ARM64 PE identity, the CAM anchors
and every preimage before applying the eleven recorded in-memory edits. It
checks each write immediately and the complete changed range afterward.

`WindowsKdWireTest` replays the retained app-owned WinPE PASS UART capture
with reads limited to seven bytes. It decodes 636 RX and 638 TX packets,
checks the exact kernel base, the retransmitted write and readback for all
eleven edits. The guarded profile matches the captured bytes on a mapped
copy of the retained kernel. It refuses an incorrect preimage and a loader
stop before writing, and aborts on a simulated lost write. The profile
uses eighteen writes of at most 32 bytes, following the current Python
observer's bounded write strategy.

`WindowsKdSession` now handles the loader/kernel handoff, packet epochs,
duplicate file requests and stopped-memory RPCs. Its session test uses the
captured UART prefix through the version reply, then an in-process target
backed by the retained kernel image for the newly issued memory requests.
It reaches the kernel-continued callback after verified writes. Lost writes
and a reset during a write produce errors without sending a kernel continue.

The isolated diagnostic owner accepts `embedded_kd=true` only for the
hash-pinned disposable single-CPU WinPE image. In this mode it runs KD over
the same native UART helpers without the host TCP listener. Its existing
viewer starts the app-owned vsock connection after the kernel is resumed
and waits for an actual decoded frame. Legacy external-KD launch remains
the default. The owner and viewer compile against Android API 36. The
embedded mode still needs a device boot and first-frame check before it can
be used by the product workspace. No APK was installed, and no guest disk
or firmware was changed.

### Isolated embedded KD device check

The diagnostic package currently has a grown 77,846,282,240-byte Windows
disk. The embedded KD option now uses `files/windows-embedded-kd.img`
instead, so the installed disk is excluded from this check.
`run-embedded-kd-test.ps1` validates the installed diagnostic APK and
the WinPE image, stages a fresh disposable copy in bounded chunks, launches
without host KD forwarding and saves the Surface readback with owner
logs. It cleans up its own media and does not change Android settings.

The first-frame check can fit on the tablet with approximately 59.5 GiB
available. The script's PC preflight stopped before staging because C:
has approximately 3 GiB free, below the 50 GiB reserve. No device boot
was attempted. This check is separate from full ISO preparation and
installation, which still requires its own storage budget and validation.

The Windows AVF creation path now lives in `WindowsAvfOwner` under the
main Android sources. The diagnostic service uses it for both KD modes,
including the existing raw configuration, serial descriptors, VM start,
stop and vsock connection. The API 36 compilation and recorded KD
regression passed. This does not yet wire the prepared workspace to launch
or validate the underlying AVF calls on the tablet.

Removed ten intermediate alignment and signing APKs from five September
build directories. All five retained signed APKs passed signature
verification, and each removed intermediate contained the same SHA-256
for every non-signature ZIP member as its retained signed APK. Two
archived test images were compressed in place with unchanged SHA-256.
C: now has approximately 5.5 GiB free. The device test still requires
the configured host reserve.

### Windows Setup preparation without a second payload copy

The two existing SWMs in the hash-pinned live WinPE template were read
directly from FAT and matched the full SHA-256 values in the retained
25H2 ISO conversion report. The product preparation path still requires
the exact ISO and WinPE hashes. It now copies the template once and checks
the full copy before expanding and auditing both GPT copies. It no longer
creates and reinserts identical SWMs for this specific supported pair.
The preparation threshold is now 58.5625 GiB, including the 50 GiB reserve
and 64 MiB of headroom. This threshold covers preparation only, and assumes
both input files are already accessible. The expanded GPT identity was
reproduced from the real template without creating another disk image.

The diagnostic owner uses the shared `WindowsKdUart` transport. The
optional product packaging path includes the exact pinned ARM64 ingress
and console tailer when requested with the Windows Setup disk components.
An APK fixture verified both packaged binaries and preserved its existing
asset. The same binaries installed in the diagnostic package passed a
16-byte paced-ingress and console-tail smoke test on Tab S11. The Android
product and diagnostic Java sources compile against API 36. A full Setup
boot and installed-system restart remain unverified with this path.

### Windows Setup launch from the workspace

The product workspace offers first boot from its own prepared Windows disk
when the optional KD runtime is packaged. `VmRuntimeService` retains the
owner independently of Activity and Surface recreation. The new runtime
checks the fresh GPT and boot media, uses the hash-pinned wrapper and
one-CPU WinPE config, and exposes only the app-owned display vsock.
The existing H.264 receiver and input path attach after the KD kernel
continue. The progress screen waits for a validated Surface frame. The
Windows stop control explicitly warns that it cuts VM power.

The product Java compile passed. The candidate base APK's wrapper matches
the launcher's pinned digest, and the new UI text has six locale entries.
This launch path is for the fresh Setup disk. Installed Windows reboot,
actual VM creation, and display/input on Tab S11 still need validation
with the complete Windows candidate.

### Windows disk state and installed boot evidence

The workspace now checks both GPT headers and tables read-only before
offering first boot. A disk with a target partition keeps its existing
state. An NTFS boot sector is shown as an observation, without implying
that Setup has completed. A damaged or mismatched GPT is reported and
never admitted as fresh media.

The layout test used the original WinPE geometry and 34 KiB of metadata
read from the retained installed test disk on Tab S11. It passed the fresh
GPT, installed partition and NTFS checks, and rejected a mismatched table,
bad checksum and overlapping partition. The app Java compile passed.

Two recorded installed boots report KD build 26100 and machine 0xaa64,
matching the pinned session. The earlier installed kernel extraction
matches the WinPE SHA-256. The remaining handoff is guest provisioning
and the installed BCD selection. The successful historical boot used
offline driver and display staging, `bcdboot`, serial debug settings and
the BCD system-store role before first boot. An NTFS partition by itself
provides none of those guarantees.

### Windows guest provisioning from the prepared disk

The optional Windows KD package now carries the five ASCII/CRLF install
commands. The disk builder adds them in a new `UAVF` FAT directory on its
unpublished staging image and verifies each byte. First boot audits the
commands read-only against the signed app assets.

`INSTALL.CMD` is the WinPE entry point. It checks the source volume, the
pinned virtual disk GUID, the free target drive letter and the absence
of a prior attempt. After explicit confirmation it creates the two target
partitions in the previously unallocated area, applies image index 3,
calls the retained driver/display and BCD preparation scripts, and writes
`BOOTRDY.TAG` only after successful boot preparation. That tag means
prepared for first installed boot, not completed Windows Setup or OOBE.

On Tab S11 an isolated FAT fixture staged and verified all five commands,
rejected a second write and preserved the EFI and WIM bytes. The package
was removed. Android product compilation and packaging verification
passed. DiskPart, DISM, BCD execution and consumption of the completion
marker still require integration and a real Windows installation run.

### Installed Windows launch admission

The workspace now offers the installed boot after read-only checks of the
target's NTFS geometry, the WinPE preparation marker and the actual BCD
store. The BCD check follows Boot Manager's selected default and verifies
the system-store role, Windows loader path, Windows root, debugger flags
and both device records against the GPT disk and NTFS partition IDs. The
runtime repeats admission immediately before creating AVF and verifies
the retained EFI/WIM and bundled command files.

On Tab S11 an isolated sparse fixture combined the recorded installed
BCD with read-only GPT metadata from the retained Windows test disk.
The Android reader accepted the matching pair and rejected a missing
marker and a wrong partition ID. The product compile and APK packaging
fixture passed. This does not confirm a real VM start, cold restart,
OOBE, or display after installed-system boot.

### Windows session exit and display recovery

The Windows owner now observes the shipped AVF VM state off the UI thread.
Only the terminal `DEAD` state ends the session via this monitor. Display
EOF clears the viewer and makes reconnection available from the workspace.
An AVF exit after a validated frame returns to the workspace so a newly
prepared installed disk can be selected on the next cold start.

The tablet framework exposed `IVirtualMachine.getState()` and its
`VirtualMachineState.DEAD` constant. An isolated raw AVF test created and
started a VM on Tab S11 and observed `DEAD` after explicitly stopping its
own VM. The Linux fixture did not reach its guest-ready marker under the
Windows raw configuration, so this is a VM-state check only. The fixture
was uninstalled and the VM list was empty. Natural Windows shutdown and
the new screen-reconnect path still need a live Windows run.

### Embedded KD hardware run and guided installation

On SM-X736B the isolated diagnostic APK staged the exact 8.5 GiB WinPE
disk directly from the host stream into its private storage. No second
host image or tablet transfer file was required. After ADB server
disconnects, recovery compared the full received prefix before resuming
in bounded chunks. Subsequent boots restored only the differing disposable
64 MiB block and checked the full source digest.

The live runs reached the loader and then the actual kernel load stop
with the embedded observer. The first run stalled on an unacknowledged
loader file reply. Bounded retransmission of the same packet advanced
the loader. The kernel exchange revealed a reset during GET_VERSION.
The session now accepts an identical stopped-state replay only for
read-only queries and still rejects resets during writes. The captured
KD regression covers both cases.

The ARM64 WinPE launcher now has an optional guided-install mode. It
checks the one-shot approval file on the unique WinPE source volume,
maps the verified source to S, and starts the existing installer script
without typed commands. The script checks the disk GUID, consumes the
approval and refuses a second attempt on a started target. The new
launcher was compiled separately at that stage. The guided packaging and
Android approval flow are recorded below.

No fresh Setup Surface frame was produced by the embedded KD runs. The
fourth run exhausted the retry bound on a loader file exchange. Captures
and the transport scripts are in `build-logs/uavf12-windows-iso/embedded-winpe-live`.
The test VM is stopped. Its separate disk is retained for a later retry
without another full transfer. The measured free space with that disk
present is 51.47 GiB. The previous installed test image allocated 9.77 GiB
more than this WinPE image, leaving an estimated 8.29 GiB shortfall with
the 50 GiB reserve. A new installation was not started.

### Guided WinPE packaging and installed-system handoff

The ARM64 launcher with the approval gate was embedded into index 2 of a
separately extracted boot WIM and verified with wimlib. The only replacement
is the U-AVF launcher selected by `winpeshl.ini`. The build produced a six-block
FAT delta against the retained WinPE template. The wizard applies it only to
the new private staging disk and checks the full post-update SHA-256 before
adding commands or publishing the disk. This avoids a second multi-gigabyte
template. The source image is opened read-only by the delta generator.

The signed guided candidate with the KD timeout recovery is
`build-logs/uavf12-windows-iso/apk-guided-v3/U-AVF-mavk-input120.apk`.
SHA-256 `543A221CEB506CD0876D704D016997B4DCF8F9F5C2056D9F7E41CCA5A676999E`.
It passed APK verification and preservation checks for all 141 non-DEX
entries from desktop R2. The candidate has not been installed over the main
application. Its WinPE bootstrap keeps transient capture data on X: so that
a preview cannot prevent the next approved boot from starting the display.

On Tab S11 the v8 diagnostic owner booted the original WinPE, verified the
kernel adaptations by readback, opened app-owned vsock, and produced a 640×480
Surface capture of the actual Windows 11 Setup driver dialog. No external KD
observer was used. The picture and UART traces are in
`build-logs/uavf12-windows-iso/embedded-winpe-live/attempt-8`.
The original guided WIM also reached the same dialog without an approval
marker. That capture is in `embedded-winpe-live/guided-preview-pass`.
The follow-up run with repeat-safe display storage (CID 2186) timed out on
an early kernel header read in embedded KD, before the new bootstrap could
execute. Its boot and display therefore still need a hardware PASS.
The isolated Android FAT test also passed approval creation, duplicate
rejection and clearing. The exact 25H2 ARM64 ru-RU ISO was found on the tablet
and its SHA-256 matches the supported source profile.

Confirmed automatic install starts by creating a one-shot approval file.
WinPE consumes it before partitioning. The script requests normal WinPE
shutdown after writing the boot-preparation marker. Android can cold-start
the installed target only after AVF exits and the installed BCD verifies.
The user can cancel the pending handoff before it starts. These are
implemented transitions. A full installation and first boot remain unverified.
The initial diagnostic used a 50 GiB tablet reserve. After the user's
clarification, the Windows wizard uses a 5 GiB Android reserve plus 10 GiB
of installation headroom after disk preparation. The PC storage policy is
independent. The disposable tablet workspace was expanded in place with a
sparse target, preserving the existing Windows and Ubuntu disks.

The isolated build of the product Activity and service launched WinPE from
the visible wizard confirmation (CID 2187) and rendered to the product
Surface. The install job exited before creating partitions. A second run
(CID 2188) persisted the actual DiskPart output, which reported the exact
expected disk GUID. WinPE accepted the approval and launched the installer,
but the batch's redundant `findstr` check returned exit 23 with the correct
approval file still present. The batch now compares the first line using
cmd's built-in read and retains the full byte-for-byte check in the launcher.

CID 2189 accepted the approval, created both target partitions and completed
DISM image application. The console closed with installer exit 31. Cold
readback confirmed the target kernel and the completed DISM session, plus an
empty `uavf-apply-exit.txt`. On Windows cmd, the expanded trailing zero in
`echo APPLY_EXIT=0>` is parsed as a file descriptor. Moving redirection before
`echo` produced the exact expected marker in a host reproduction. The
installer now uses that form for both the DISM result and the boot marker.

The next run (CID 2190) completed DISM with `APPLY_EXIT=0`, provisioned
the drivers and produced the installed BCD and `BOOTRDY.TAG`. WinPE
shut down, Android observed AVF DEAD, and the wizard started the installed
disk automatically as CID 2191. The installed boot reached native Windows
specialization, recorded the first-boot hook, and delivered a live H.264
frame from the installed system to Android Surface. The capture at
`build-logs/uavf12-windows-iso/wizard-device/installed-2191-visible.png`
shows the first-boot console. OOBE and desktop require separate confirmation.

Keep guest graphics additions within driver and display provisioning,
after image application and before boot preparation. Future Mavk or WDDM
packages can extend that stage without touching approval, partitioning,
image application or BCD preparation.

### Windows resolution, session handoff and SMP

The isolated wizard installation reached Windows after OOBE. Its native GOP
and Android AVC stream both reported 1920×1200 in CID 2194. The user reproduced
a stream interruption after dismissing the lock screen while crosvm was still
running. The retained installer profile used the interactive capture launch,
whereas the earlier Windows prototype used the broker that starts capture on
each active input desktop. The wizard now supports staging that broker as
`windows-setup/BROKER.EXE` alongside the provisioning scripts. Build it with
`tools/windows-reassessment/build-windows-display-broker.ps1` and package
`winpe-screen-service.exe` under that asset name. The service invocation used
by the wizard disables the diagnostic CPU sampling. An already prepared disk
needs separate migration of its guest service. Android retries the display
independently of the VM, and extended waits expand the current Windows card.
The expanded card and cancellation were exercised on the tablet. The AVF
callback delivered reason 1 for an explicit stop in CID 2197.

On the isolated installed disk, the broker and service registration were
applied with full binary readback. Offline recovery used the single nonempty
SYSTEM log. The registry comparison after recovery found exactly one new
service and no change to existing values or key security. From Winlogon to
the actual desktop in CID 2198, the stream closed at 18:17:43.876 and Android
recorded ten decoded and rendered frames from the replacement stream at
18:17:45.188. The desktop remained 1920×1200 and Start opened by touch.
Restart through the guest's Start menu reported AVF reason 5, showed the
restart card and launched CID 2199 with the same disk. The first 1920×1200
render callbacks appeared around 58 seconds after AVF reached DEAD, and
the lock screen was visible. Approximately 15 render callbacks/s on the
desktop are decoder telemetry, not a benchmark of unique displayed frames.

The release assembler accepts the ARM64 broker and matching FAT JNI as a
pair and updates only the Windows provisioning assets. Packaging and
Ubuntu installer payload integrity passed. A clean install with those
assets has not yet been run. Evidence is under
`build-logs/uavf12-windows-iso/wizard-device` in the parent workspace.

The 8-vCPU GOP EFI hash is verified before selecting MATCH_HOST. A test of that
profile in CID 2195 reached native 1920×1200 GOP but the embedded KD session
rejected stop 0x3030. The previous 8-vCPU PASS used
`trace-kd-boot-drivers.py` to use the common 20-counter PMU allocation, adapt
all seven PSCI CPU_ON calls and configure explicit-affinity IPIs in the
verified GIC context. The wizard must reproduce
that startup contract before enabling 8-vCPU media by default. The isolated
disk's previous EFI was restored with readback and GPT parity.

Follow-up: the embedded KD owner now performs those guarded SMP steps.
CID 2204 booted the existing wizard test disk with the retained SMP EFI,
reported `WINDOWS_SMP_ONLINE=8`, and rendered the Windows lock screen
at native 1920×1200 on Tab S11. Test APK `test-smp-runtime5.apk`, SHA-256
`FB275D7F45FD74799F5C07C35770DA31F4B1EEC90990936C79CB8D7B32CC68CB`.
After ordinary sign-in, the broker replaced the stream and the desktop was
visible at 1920×1200. Restart from Windows produced AVF reason 5, launched
CID 2205, and again reached `WINDOWS_SMP_ONLINE=8` and a live 1920×1200
Surface. Evidence: `smp-2204-2205-logcat.txt`, `smp-2204-desktop.png` and
`smp-2205-visible.png` in the wizard-device log directory. A fresh WinPE
installation with the SMP profile is still pending.

The guided SMP payload was built by layering the verified 8-vCPU EFI over
the retained guided delta. The builder verified the frozen image, retained
delta, WIM, and new EFI and emitted nine 1 MiB blocks without cloning the
source image. The signed test package `test-smp-guided.apk` includes that
delta and the broker. SHA-256
`7560755492132A7E0EF4755D45ED9AE9B455318D6B648F6634B154E39D3367DD`.
Use the delta directory with `-WindowsGuidedProfile` when assembling from
the retained product APK. This test package was verified offline and is
separate from the installed package that completed the SMP reboot test.

Fresh-install QA uses a separate app package, `com.example.winavf.smpfreshqa`,
and its own private disk. The test streams the verified guided WinPE media
directly to the isolated package and then uses the app's storage validation,
installer staging, partition geometry and confirmation dialog. This setup
bypasses the two document pickers and full template copy. The Windows wizard
still requires the pinned ISO and WinPE template for ordinary preparation.
On Tab S11, WinPE CID 2206 reached eight online CPUs and a 1920×1200
Android Surface. DISM completed, both signed guest driver packages were
installed, and crosvm reported a guest shutdown with exit status zero
and raw AVF reason 3. The app validated BCD and automatically started
CID 2207 from the same disk. This boot reached eight online CPUs, then
showed native Windows first-boot preparation at 1920×1200. See
`dism-complete.png`, `install-handoff-logcat.txt` and
`installed-2207-first-visible.png` in the isolated QA directory.
First boot then requested an ordinary restart (raw AVF reason 5).
The app showed the Windows restart card and launched CID 2208.
It again reached eight online CPUs and displayed Windows OOBE at
1920×1200. See `installed-2208-restart-logcat.txt`,
`installed-2208-restart-card.png` and `installed-2208-oobe.png`.

The Windows transition card now distinguishes WinPE, active installation,
installed-system output and display reconnection. Late frame callbacks are
rejected after the AVF owner enters shutdown. English diagnostic exports
record the last WinPE and installed-system frames separately. A frame alone
does not establish that OOBE has finished or that a user desktop is visible.

The follow-up build stages the optional offline OOBE answer file beside
the display broker. The installer copies it into Panther with readback
before preparing BCD, and refuses to overwrite an existing answer file.
The isolated FAT fixture on Tab S11 passed byte readback of all seven
payloads, duplicate rejection and boot-file preservation. This fixture
does not establish that Windows consumes the answer file during first boot.

The full isolated candidate `U-AVF-1.2-windows-qa2-release.apk` is in
`build-logs/uavf12-windows-iso/wizard-device/smp-fresh-qa/release-full`.
Its SHA-256 is
`84A81326559BB8EF53350C6EB234A7ABC433E176C52AEBF27F56286F3426E282`.
The release signature, APK alignment, stable resource IDs, 134 retained
payloads and Ubuntu installer ESP digest were verified. The lifecycle,
transition, Windows GPT and captured KD replay checks passed with the
latest app source.

WDDM R&D can proceed independently of Ubuntu desktop integration. The
retained Windows Vulkan ICD and Mavk Bridge provide guest-to-Mali rendering.
The missing part is a signed ARM64 Windows graphics adapter and the D3D UMD
integration with Windows allocations, fences and presentation. Yttrium's
ARM64 port is a concrete reference for the Windows side. Its VirtIO GPU/Venus
host contract needs replacing with a transport available in the app-owned
AVF VM. The first end-to-end gate is D3D11CreateDevice on the experimental
adapter and a verified draw on Mali. DWM and Android-owned AHB presentation
follow that test.

## Main 1.2 RC5 and isolated Tab S11 checks

- Main APK: `D:/UAVF-builds/uavf12-main-diagnostics-rc5-20261115/U-AVF-1.2-rc.5-release.apk`,
  SHA-256 `29314A975BB6AF1C165AA89AE75B5760678A6FF8FE5667ACD93069374B7BED2C`.
  Package `com.example.winavf`, version code 29, normal launcher, existing
  release certificate. After isolated QA, the main APK was installed in
  place on Tab S11 and its launcher opened successfully. No guest was started.
- Isolated `com.example.winavf.releaseqa` proved all nine local AVF checks
  on Tab S11, including guest memory, write/readback and the vsock challenge.
  The test used Continue without sharing. The result appeared in Test Runs,
  search filtered it correctly, and Export Report opened Android's picker.
  Windows ISO and Ubuntu temporary-session ISO both opened the system picker;
  Cancel returned to their respective screens. No ISO was selected during
  this navigation check. The disposable package was removed afterward.
  Reports: `D:/UAVF-builds/uavf12-navigation-qa-20261115/tab-s11-compatibility.txt`
  and `tab-s11-test-runs.txt` in the same directory.
- RC5 additionally centers the ISO button's icon and label. This was
  checked in the main app on Tab S11, with a capture at
  `D:/UAVF-builds/uavf12-main-diagnostics-rc5-20261115/windows-iso-button-tab-s11.png`.
  Firestore grouped writes passed
  the emulator suite. Production readback matched the new rules and a
  synthetic grouped report, consent denial, receipt and duplicate rejection
  passed. The synthetic documents and account were removed. The previous
  rules release is saved in `D:/UAVF-builds/uavf12-firestore-live-20261115/rules-before.json`.
  WDDM remains paused.

## Windows from one ISO and clean source check

- The Windows wizard takes the supported Windows 11 ARM64 25H2 ru-RU ISO
  (26200.6584). It stages the guest UEFI, WinPE additions and Setup files on a
  new workspace disk. The ISO is opened read-only.
- Tab S11, isolated QA version 1.2-iso-qa.3. The wizard selected the ISO in
  Android, prepared the disk, started WinPE at 1920×1200 with eight vCPUs and
  applied the Windows image after user confirmation and verified installed
  boot after WinPE shut down. The first installed boot reached
  `WINDOWS_SMP_ONLINE=8`. After the next guest reboot the installed boot
  files verified again, but the Android owner stopped at the secondary-vCPU
  check. The saved KD capture shows native startup-wait result 4 for CPU 6
  and a zero word in its U-AVF entry marker. The earlier CPUs passed the
  same check. A prior instrumented boot also observed a zero marker after
  native success that became one by the final handshake. The runtime now
  retries zero markers at the end of the eight-vCPU handshake, while requiring
  native success at each startup wait. A host-side simulation passed for a
  delayed marker, a missing marker and a failed native wait. One subsequent
  manual boot on the same isolated disk with QA.5 logged `WINDOWS_SMP_ONLINE=8`
  and delivered rendered H.264 callbacks at 1920×1200. The log does not record
  whether a marker was deferred in that run. Cancel in the stop confirmation
  left the boot running. The main package and disk remain in place.
- Main APK 1.2-rc.17 (version code 50) passed signature, alignment, JNI and
  platform profile checks. It was built locally and has not been installed.
- A separate source snapshot excluded ignored files and build outputs. The
  Java suite reported 18 passes and five fixture-dependent skips. All four
  Windows native libraries matched the APK byte for byte. The WinPE ZIP
  builder produced identical output on two runs, and its entries matched
  the device-tested bundle. The APK build still requires a compatible
  platform base and the inputs described in `android-app/README.md`.
- The release build also completed from a clean copy of the Git-eligible
  working-tree files, with the platform APK, native libraries and WinPE ZIP
  supplied explicitly. Main APK 1.2-rc.18 (version code 51) passed signature,
  alignment, platform profile, JNI and WinPE bundle checks. It has not been
  installed. SHA-256:
  `9A7262832099D589F682B254843FC3DDF83B67F0E80AE9D969EBFED29C3161F0`.
