# Product/UI phase — source mapping and current boundary

Figma file `6Z1PAzbSipIcdU1bHvRr98`, page `74:10` (03 Screens & Prototype).
Read directly using Figma MCP. A page is not a valid get_design_context screen.
No commit/publication. Hardware Vulkan R&D is suspended.

## Mapping

| Design | Existing real backend | Remaining implementation |
| --- | --- | --- |
| 369:123 / 173 / 223 / 273 runtime transitions | AVF callbacks, serial stream, guest power4056, MediaCodec Surface | New evidence model/view; device regression in progress |
| 294:99 dashboard;307:59/91/127 menu/delete/empty | One persistent Ubuntu, existing launch/stop/delete | Independent workspace registry + per-workspace boot/storage/config routing; do not claim multi-workspace yet |
| 294:127/162/200/248 create wizard | Official ISO selection/verification, persistent disk builder, safe root-only reinstall | Associate each newly created disk/config with workspace ID |
| 294:311 home | Actual disk presence/size, ready witness, managed VM state | Workspace-specific resources, measured stats and session evidence |
| 294:461 diagnostics;307:790/837 export/logs | Host preflight, real reports/logs, received/rendered display counters | Structured checks with provenance; missing guest metrics stay unavailable |
| 294:384;307:370/405/440/475/510/545 settings | RAM2/3/4/6GiB; one/match-host topology; input/audio/network flags | Per-workspace scope; actual next-boot semantics, no arbitrary vCPU slider |
| 294:336;303:125;307:244/288/328 controls | Power4056, capture/release, clipboard, keyboard shortcut | Layout reconciliation; shutdown/reboot distinct from destructive force-stop |
| 342:2/28/43/76/109/140 install/recovery | Verified installer/bootstrap, root-only reinstall, installed witness | Present actual stages, don't recreate Ubuntu installer forms |
| 294:508;307:890/928/966/1004/1042 app settings | Local appearance/developer options and about | Telemetry/update channels must stay hidden/disabled without backend |
| 294:419 and input variants | Native keyboard/mouse/touch, accessibility service | Samsung-specific routing regressions; do not promise intercepted OS shortcuts |

## Stage1 implementation facts

- New `RuntimeTransition` is independent from screen navigation and VM ownership.
- Events: app request, AVF VM handle / onStopped / onError, fresh owner-matched
  serial UEFI/Linux/systemd/GDM/shutdown/sync/powerdown markers, validated
  Surface frame. Each event retains provenance. Missing earlier milestones are
  NOT inferred from a later milestone.
- NetworkManager started is NOT proof of connectivity. Even network-online
  target is labelled as a target rather than an internet test.
- First encoded frame uses one small PixelCopy readback; all-black samples do
  not reveal desktop. This readback stops after startup; no hot-path copies.
- Shutdown/reboot reject late desktop callbacks and cover the last frame.
- Inactivity produces non-modal elapsed/Keep waiting/confirmed Force stop.
  Elapsed is not reset by Keep waiting. VM responsiveness is not fabricated.
- Native layout follows Figma shell/events/telemetry colors, radii and hierarchy;
  native scroll handles smaller viewport. System font fallback currently used
  (Lexend Deca is not bundled). Orbital vector geometry read directly from
  Figma369:130; rotation is its actual four-second linear loop, disabled when
  Android animations are disabled. Full screenshot/rotation QA remains required.
- `RuntimeTransitionTest` checks split/ANSI serial, cross-line false positives,
  stale generation, late shutdown frame, reboot frame gate, elapsed/error states.
- UI-only builder hashes every unchanged APK entry against GOOD baseline:
  assets, native libraries, resources and manifest must remain byte-identical.

## Preserved baseline

GOOD APK: `D:\UAVF-builds\vulkan-sigill-20261003\safe-jit-apk\WinAVF-test.apk`.
No guest disk/ISO/platform mutation is required for Stage1.
Before APK replacement, guest4056 graceful shutdown completed with serial
`reboot: Power down`, and no crosvm process remained.

## Installed-device checkpoint

`D:\UAVF-builds\product-ui-20261003\transition-r2\WinAVF-test.apk`
SHA256 `2026084E9C162453AFD09290E8CC27F8C40E8AE1F612C015FF9C07856B04BA5A`.
Host model test PASS; preserved32 APK entries PASS; signing and install PASS.
Cold launch and reboot both reach visible installed GNOME through the unchanged
1920x1200 MediaCodec stream. Reboot cover captured before guest stop; actual
STOP_REASON_REBOOT received and workspace starts automatically. Initial black
frames on the second boot correctly retain the transition until nonblack output.
Shutdown cover captured immediately. Keyboard/touch exercised through terminal.
Screenshots: `boot.png`, `desktop.png`, `reboot.png`, `reboot-wait.png`,
`reboot-result.png`, `shutdown.png`, `stopped.png` in the build folder above.

This is connectivity/lifecycle acceptance, not a sustained FPS benchmark or the
complete release regression matrix. Fresh `glxinfo` cannot run: mesa-utils is
not installed, matching the prior audit. No packages installed for this check.
No new hardware-Vulkan work or guest graphics changes.

## Next gate

Current implementation wave: native workspace home294:311 and wizard OS294:127,
Image294:162, Storage294:200, Review294:248, read via design context + screenshot.
Shared FigmaCanvas reuses bundled Lexend and individual original logo/icon,
background, glow and step assets. User-visible reference values are replaced by
actual config/disk/ISO state. No invented session duration/FPS history.
Storage location/size remain managed/fixed and unsupported tuning is explicitly
In development rather than a fake slider. CPU/RAM customization uses real
settings and returns to the wizard. Review reuses existing workspace without
formatting; destructive reinstall remains explicit recovery confirmation.
Device navigation checked: OS -> Image -> Storage -> Review; CPU/RAM settings
return to Storage. Review on the existing installed disk says Open existing
workspace, not Create/Format. Ubuntu/Windows use original transparent raster
assets; logo/step SVGs rasterized without changing artwork or intrinsic sizes.
Home and Storage fit the tablet without scrolling; remaining screens still
need visual acceptance. No multi-workspace support is implied by this wizard.

Loading follow-up: recovery requires >=60 seconds total and >=30 seconds without
fresh evidence. Silence is not labeled a hang. Removed synthetic backdating of
shutdown inactivity after 15 seconds. Restart includes UEFI; an explicit power
ACK/rejection is recorded, and errors retain the original operation/rows.
Power confirmations use native dark product dialogs. Host lifecycle tests PASS;
new device power/regression acceptance remains pending, not inferred from build.

Final UI/lifecycle APK: F:\dt\UAVF-builds\product-ui-20261003\workspace-lifecycle-final\WinAVF-test.apk
SHA256: 5AA092B23FC81C8F40FE87D9255A28E7AF7F2E48D0DE99CB16225367AA3A60B3.
Signature/build verified; all32 baseline resources and16 new UI assets verified.
No ISO, firmware, guest runtime or user disk was changed by this build.

Follow-up build states-input-final (F:\dt\UAVF-builds\product-ui-20261003):
APK SHA3872E7AE18031694C675EB16F0564A2613BB1875E0D18D809C8B9FEAEB7048F6,
installed after a verified graceful STOP_REASON_SHUTDOWN. All32 baseline entries
and16 UI assets preserved. Copy cleanup and effective-display labeling included.
Host tests and actual installed serial replay PASS: UEFI/kernel/systemd/GDM.
Linux/systemd may be confirmed from their required managed-service prerequisite;
the event stores that provenance, not a fictional earlier timestamp.
Last desktop and shutdown completion now stay visible450ms with generation guard.
UI runtime controls have hide-button + configurable Esc/F12 chord and optional
middle-left edge hold/swipe. Original Esc combination remains a rescue path.
Other-version ISO support remains blocked by boot/media preparation, not only
SHA policy. Do not implement a cosmetic warning/Continue over hard-coded media.

Visual acceptance (user clarification): implement the actual Figma design, not
an approximate redesign. Preserve typography, composition, colors, component
geometry and assets; correct accidental misalignment and adapt to viewport.
Setup342:2 now has a native scaled-canvas implementation with bundled Lexend
Deca (OFL included) and the three exported individual Figma glow assets in their
original slots. No screenshot of a whole screen is used as an implementation.
Native buttons route to existing workspace/Setup/diagnostics. Dynamic labels
replace unproven "Formatted" claims. Installed-device Setup screenshot checked:
all controls fit without scrolling; original slot geometry/typeface/assets used.
APK: D:\UAVF-builds\product-ui-20261003\figma-setup-native\WinAVF-test.apk
SHA256: 971C91CEEBBD5CDEABB0295DC116F7D35CE71D2E881EFF62920B8CFC18D19DAE.
All32 baseline non-code APK entries preserved. New UI-only assets added; no
guest/firmware/ISO/disk mutation. Other screens are NOT yet visually accepted
against Figma; do not present the legacy UI as a finished design transfer.

Runtime transition layout now has no ScrollView: lifecycle rows share remaining
viewport height. Observed rows receive a green border/background and explicit
check mark; absent markers remain unobserved. Setup summary added under Install
& recovery, using recorded launch report and confirmed installed HELLO state.
Report reads are bounded and asynchronous; historical checks carry a timestamp.
Continue in Setup returns to the existing VM, never starts another installer.
This is the Setup summary component, not completion of all Figma flows.

User-requested motion adjustment: stationary central cube and accent dot; only
the outer dashed ring rotates clockwise (4 seconds) and the inner ring rotates
counterclockwise (6.5 seconds). Motion stops when hidden/detached and respects
Android disabled animations. This intentionally replaces the original whole-icon
rotation without changing VM/runtime behavior.

Independent workspaces need more than dashboard cards: current disk path,
installation preferences, payload/witness cache and AVF VM name are shared.
Introduce a registry and explicitly scoped storage/config/boot ownership, keeping
the legacy workspace path and flags unchanged during import. Block switching
ownership while a VM is busy. Avoid a superficial multi-card UI over one disk.
Full GPU/app/network/audio, live/installer, rotation/background and waiting/error
device QA remain pending; do not call the entire product phase complete.
