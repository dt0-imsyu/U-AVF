# VM manager implementation plan

Display performance is frozen. Stable RFB/WAVF stays default; H.264 is internal
experimental only. Do not rewrite installation/storage/platform.

## Audit

- App is framework Java, one large MainActivity, custom FrameSurfaceView;
  no Compose/Gradle rewrite planned. VarNN identifiers and recovered-style code
  indicate restored/decompiled ancestry. Exact recovery APK is not established
  from checked-in metadata; do not pretend provenance is fully verified.
  Preserve current source plus installed APK before changing it.
- Existing genuine flows: official ISO selection/download/hash, one persistent
  disk, protected vda3 reinstall, late bootstrap/handoff, stable display/audio,
  stateful key relay and optional UTF-8 clipboard on4054. Keep these.
- Defects: Stop directly calls VM.stop (forceful); running state is local bool;
  Activity reconnect does not attach lifecycle callback. Resource controls are
  currently info cards; launch uses 4GiB and MATCH_HOST. Do not expose arbitrary
  CPU counts until Samsung builder API is verified.
- Live Figma page74:10 now IS accessible, contrary to older September29 audit.
  57 frames cover onboarding/create/install/workspace/settings/runtime/recovery.
  Dashboard294:99 high-fidelity context + screenshot read directly via MCP.
  Design styling must come from requested frame context, not a new design.
- Unknown availability must remain Unknown, not green PASS. Preferences are
  configured intent, NOT runtime network/audio/GPU health.

## Sequence / gates

1. Separate real lifecycle model, reconcile AVF state, callback reattachment,
   stale-callback protection and duplicate-start prevention. Unit tests/build.
2. Add bounded/versioned vsock4056 control agent/client. Graceful shutdown/reboot,
   timeout15s -> Wait/Force; force separately warned. Guest power events verified
   on installed test workspace. No UART reliance in product.
3. Additive agent installation/upgrade, then automatic install payload inclusion
   using existing bootstrap. Do not modify firmware/storage or rewrite installer.
4. Persistent RAM/CPU/resolution settings only for verified builder/runtime
   APIs; stopped-only mutation and next-launch effective-config diagnostics.
5. Retain proven keyboard/clipboard; test combinations and echo-loop bounds.
6. Configurable toolbar mode/shortcut, touch fallback, pointer reacquisition.
7. Figma screen-by-screen functional wiring + truthful diagnostics; each block
   must build/test before proceeding. No unsupported multi-workspace controls.
8. Full lifecycle/install/UI regression and acceptance matrix; no commit/release.

## Current status

Lifecycle/control foundation builds and host tests PASS. Guest socket activated
on4056; Android HELLO/SESSION_INFO connected. Android Shutdown -> guest systemd
unmounts -> reboot: Power down -> no crosvm process -> workspace screen PASS.
On-device testing exposed old getState() reflection bug: public AVF has
getStatus()/STATUS_*; fixed using reflected named constants, not guessed numbers.
Restart -> automatic persistent boot -> desktop ->4056 reconnect PASS.
Automatic bootstrap inclusion offline audits PASS; clean-install acceptance
still PENDING. Kernel/BOOTAA64/GPT unchanged; only initrd control payload added.
Resource choices implemented next:2/3/4/6GiB, ONE_CPU/MATCH_HOST only,
1280x800/1920x1080/1920x1200. Defaults unchanged. Host validation PASS;
on-device effective-resource/resolution acceptance PENDING.
Figma visuals not yet ported in this stage. Do not claim release readiness.
Evidence/backup/builds: D:\UAVF-builds\vm-manager-20261001.

## Remaining lifecycle caveats

- Configuration changes no longer destroy Activity; reconnect uses getStatus
  and reattaches callbacks. Full process-death and Activity recreation tests
  remain pending (including audio/console consumer cleanup).
- Installed workspace control agent was deployed additively for this test.
  Bootstrap now packages it for new installs. Automatic upgrade of an existing
  guest runtime without manual guest interaction is not yet implemented.
- Timeout/Force stop path exists but has not been runtime-accepted. Do not
  claim graceful ACK proves completion or that power loss is data-safe.
