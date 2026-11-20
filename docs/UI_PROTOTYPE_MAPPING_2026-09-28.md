# U-AVF UI prototype mapping

Updated: 2026-09-28  
Figma file: `6Z1PAzbSipIcdU1bHvRr98`, page `03 Screens & Prototype`, entry node `74:10`.

The prototype and its connected screen/state frames were inspected through the Figma integration (design context and rendered node screenshots), including onboarding, permissions, compatibility, dashboard, workspace creation, image/storage/review, setup/recovery, runtime controls, input, settings, diagnostics, and empty/error/confirmation states.

## Current app mapping

| Prototype area | Status in U-AVF | Implementation / product decision |
|---|---|---|
| Onboarding and AVF permission | Works / aligned | Existing onboarding and permission request remain the entry flow. Permission status is checked rather than simulated. |
| Compatibility check | Works / aligned | Calls the actual device-readiness preflight and reports its result. It is a preflight, not a promise of full hardware certification. |
| Dashboard and workspace return | Works / aligned | Shows the single persistent Ubuntu workspace, real install state, verified ISO status, running-VM return, diagnostics and app settings. |
| Create workspace: OS choice | Works, with a limitation | Ubuntu is the supported install path. Windows ARM remains marked experimental and does not imply post-boot support. |
| Create workspace: image | Works / aligned | Official Ubuntu ARM64 ISO can be selected/downloaded and is checksum-verified; the source ISO is retained unmodified. |
| Storage and install review | Works / intentionally safer than mockup | Geometry is managed by U-AVF. Setup is told to format only Ubuntu root `vda3`; ESP `vda1` and ISO `vda2` are preserved. Unsupported CPU/RAM/disk-size sliders are omitted. |
| Installation and first boot | User-confirmed end-to-end | User reports a complete install cycle and automatic transition to installed Ubuntu after reboot. The UI exposes continue, reinstall, live-session and delete/recovery actions. |
| Workspace runtime controls | Works / aligned | Existing session drawer exposes return, settings, diagnostics, input capture and stop. `Ctrl+Alt+Shift+Esc` toggles it and releases pointer capture; the extra Shift modifier avoids the common Ctrl+Alt+Esc guest shortcut. |
| Mouse, keyboard and touch | Implemented; device acceptance pending | Touch/pointer and key forwarding use the existing input protocol; Android pointer capture sends relative deltas. The release shortcut is documented in-app. No unsupported pinch/three-finger claims. |
| Workspace settings | Partial, truthful | Tabs cover general, performance, display, input, audio, network, storage and advanced. Only supported next-launch audio/network toggles and pointer capture act as controls; other values are informational. |
| Diagnostics and export | Works / aligned | Preflight calls the real readiness routine, technical logs use the existing log view, and export writes a local report plus available log tails to `Downloads/U-AVF`. |
| Delete workspace | Implemented; device acceptance pending | Confirmation explicitly preserves ISO and app data; operation is restricted to the named persistent Ubuntu disk and refuses while a VM is running. |
| Multiple workspaces | Not implemented | Dashboard states only one persistent workspace is supported; no fake add-workspace action is shown. |
| Resource tuning, arbitrary resolution/FPS, gestures, telemetry/update channel | Not implemented / removed from actionable UI | Current AVF launch path does not expose validated controls for these. Do not present prototype sliders/toggles as functional. |
| GPU status | Experimental | VirGL has worked on the validated path but is device/runtime-sensitive; diagnostics label it experimental, not universally guaranteed. |

## Release acceptance still required

- Install and run the candidate APK on the tablet; review tablet-size and portrait layouts.
- Exercise the create/install/reboot/launch/recovery/delete paths without deleting the known-good installed workspace until the candidate test is planned.
- Test keyboard shortcuts and pointer capture/release with a physical keyboard/mouse, plus touch. Runtime controls use `Ctrl+Alt+Shift+Esc`.
- Verify the export file exists and contains the report/log sections on-device.
- Complete release hardening and known-issue review before any public release.

No Figma source was edited during this pass. The implemented app intentionally removes prototype controls that have no real backing behavior; any subsequent Figma edits should mirror the verified app behavior rather than reintroduce unsupported promises.

## Live reference recheck — 2026-09-29

The supplied Figma file was queried again through MCP before further UI implementation. Its only top-level page currently resolves as `05 Archive / Old — Login`; the requested entry node `74:10` and page `03 Screens & Prototype` are not present in live metadata. The accessible `74:12` frame is an old Honor 200 onboarding/login screen, not the U-AVF dashboard/workspace prototype. Therefore the 2026-09-28 mapping above is retained as historical context, but cannot be treated as a current pixel-accurate target until the intended prototype is restored/shared.

Source audit found two concrete UI mismatches: app Appearance was only an info card, and closing the runtime drawer released mouse capture without restoring it. Both are now implemented in a development APK build (persistent Dark / Standard AVF selector with wallpaper and light-theme contrast; conditional pointer-capture restore). Device acceptance is still pending. Multiple persistent workspaces remain unsupported by the current single-disk/single-VM model; do not show a create action that silently overwrites the installed system.
