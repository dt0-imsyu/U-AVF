# Windows ARM64 product target

Priority update2026-10-15: pursue GPU through the new sibling Mavk project
(../../Mavk/docs/ROADMAP.md). Native Android proof/core first; Windows guest
ICD and WDDM are separate gates. Capture FPS tuning is no longer the next task.
The full Windows product objective below is retained, NOT achieved or narrowed.

Full user objective remains: installed Windows Desktop, stable native1920x1200
  approximately60 actualFPS, mouse/touch/keyboard, audio, network, clipboard,
lifecycle, existing U-AVF UI integration, independent Windows/Ubuntu workspaces
that coexist without deleting each other. No commit/push/publication.

## Current verified/incomplete state

Authoritative latest checkpoint: STATE.md, Current exact x264 delivery.
IddCx pipe→x264→UVF1/4052→existing Android MediaCodec is guest-proven at
~42.91 encodedFPS over25s, receive/decode/render callbacks~41–45/s,pending0.
x264 selected1/selftest0 and4.4–4.7ms/frame: source availability dominates.
Winlogon/secondary-pattern context is NOT user Desktop or physical present PASS.
Driver/trust,3 owned guest payloads,both helpers and separate codec are restored.
Exact recovery ALL112 original blocks readback PASS; no VM currently running.
Next is reversible real Desktop/primary-Idd topology, not another synthetic run.
Historical admission/preparation entries below are superseded by this paragraph.

Authoritative2026-10-14 correction: IddCx ARM64 guest admission and BasicDisplay
coexistence ARE proven (PnP started, OS system buffers native1920x1200).
GDI full-motion source48.4984/s; WARP flip44.4899/s; NOT Android60.
Scoped test driver/trust removed after each completed run. No hardware3D.
Opt-in bounded pipe source/receiver implemented and host-tested. Current
guest pipe retry91791 / clock2 awaits fresh transfer evidence, after two
pre-admission fixture refusals (calendar then own certificate-path typo).
Full Windows audio/network/clipboard/lifecycle/UI/workspace parity remains
required; do not redefine product completion as driver admission or source FPS.

- Latest source-only WGC probe completed from actual non-SYSTEM user:
  1920x1200/30s,29.200 readFPS,Map18.050ms. Below58FPS gate, no WGC tuning.
  Next official ARM64 IddCx proof needs actual loaded monitor device; guest
  admission/BasicDisplay coexistence not yet proven. No driver installation
  in current working system. docs/WINDOWS_IDDCX_ADMISSION_2026-10-13.md.

- Installed Windows Desktop/app-owned AVF/8CPU/nativeGOP1200p proven; retain
  existing timer/PSCI/PMU contract. No repeated boot/CPU/ACPI flag sweeps.
- H264/app-owned vsock/common Android MediaCodec/Surface proven. Best tested
  full-repaint actual present~26FPS, NOT60. RAM reader branch stopped at unproven
  safe mapping gate; retain display fallback, no repeated closed capture tests.
  See WINDOWS_RAM_READER_ADMISSION_2026-10-12.md.
- Diagnostic input path has keyboard/mouse interaction evidence. Complete
  product capture/release, touch semantics and UI input regression remain.
- Clipboard candidate opt-in, source/protocol tests exist; actual guest
  roundtrip and installed/live bootstrap integration not yet product PASS.
  Optional server retry fixed/built; bounded guest roundtrip did not confirm
  success, all four cold rollbacks PASS. Pending-TEXT/REQUEST ordering defect
  fixed with PC checks; diagnostic failure categories installed and tested
  once: CONNECT/SEND succeeded, READ returned IO error,
  no roundtrip PASS. Next narrow gate is response/worker failure localization:
  WINDOWS_CLIPBOARD_RECOVERY_2026-10-12.md.
- Windows audio/network and full lifecycle are not release-verified.
- Windows currently isolated diagnostic path; production UI/profile integration
  and persistent independent workspace coexistence remain required.
- Microsoft Basic Display/Render path is software; do not claim Mali3D in
  Windows or infer compositor60FPS from WM_PAINT/nominal refresh.

## Evidence-first sequence

2026-10-13 priority override: clipboard frozen, RAM stopped. First test WGC
as the alternative frame source, then official IddCx only if WGC fails the
source gate. See WINDOWS_WGC_SOURCE_2026-10-13.md. No repeated DXGI/GDI tests.

1. WGC source branch closed at29.2FPS. IddCx system-buffer prototype and
   unique unsigned catalog package built; guest load/source/FPS unproven.
   A bounded ARM64 software-device helper is ready but not run. Next is
   disposable guest admission, then >=58 unique sourceFPS before existing
   encoder/transport integration. Clipboard remains frozen; RAM remains NO-GO.
   Do not repeat closed capture tests or claim overall goal complete/blocked
   solely from the failure of a particular source branch.
2. Preserve best verified display variant; integrate only demonstrated changes
   into common Windows runtime, not experimental defaults.
3. Verify input/release/reconnect, then network/audio/clipboard and lifecycle
   with actual end-to-end evidence, not compilation/nominal capability alone.
4. Integrate existing UI and independent persistent Windows/Ubuntu profile
   storage/launch; verify coexistence, no profile-data loss and isolation.
5. Regression/release audit against every requirement above. No completion
   claim while60FPS or any other required capability is unverified.

Protected: working Ubuntu/P7/official ISO/production display and user data;
Windows isolated disk only. No root/unlock/flash/SELinux or host firmware
changes. Keep approximately50GiB reserve on tablet/C. D remains failing;
new replacement E is approved for artifacts under E:/U-AVF/artifacts.
Obsolete Ubuntu workspace deletion was separately authorized and completed,
not a future requirement for running Windows.

Latest measured display checkpoint: WINDOWS_PERFORMANCE_CURRENT.md and its
linked reports. Historical checklist remains WINDOWS_MILESTONE_CHECKLIST.md.

## Current isolation/product integration boundary

Read-only check2026-10-13: production MainActivity dashboard/storage remains
single-Ubuntu; working Windows is in a separate diagnostic APK/UID. There is
no demonstrated multi-workspace product flow or safe cross-UID disk adoption.
Do not merely expose a Windows card that launches an unrelated legacy path.
Keep Linux storage path intact during future explicit profile integration.

Tablet free57336804KiB =54.68GiB, only4.68GiB above the50GiB reserve. A clean
Windows guest cannot be assumed to fit without reclamation. C free56.50GiB,
E free910.53GiB; new E artifacts remain approved. Battery71%, USB charging.
No fresh disk created, no data removed, no VM running. Outstanding choice:
separate disposable environment versus revised working-Windows driver-install
authority. Neither a compiler PASS nor SwDeviceClose provides disk isolation.
