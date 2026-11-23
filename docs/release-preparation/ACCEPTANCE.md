# Device acceptance

## U-AVF 1.2 integration candidate

Run these checks against one signed APK. Record the APK hash, tablet model and
Android build with the results. The architecture candidates use the same
package ID and release certificate as 1.2 RC7. Test the update while all VMs
are stopped. Use separate disposable workspaces for installation tests.

| Area | Pass evidence |
| --- | --- |
| Update and UI | Existing workspace cards and disks survive an in-place update. Check all six languages, Back and main-tab swipes, setup/settings menus and diagnostics. Rotate or reopen the app during a VM transition and verify that real progress and controls recover. |
| Compatibility and consent | Run the disposable AVF probe. Check actual boot, guest memory, writable storage and vsock outcomes. Repeat with sharing disabled and check that the new local report is present. On a consented run, match its receipt to the manufacturer → model → configuration report. Search for a probe detail in Test runs, copy the result, and open the English export. |
| Ubuntu | From the official ISO, verify GNOME X11 H.264 and Mavk and the KDE Plasma X11 Mavk installer choice on disposable workspaces. Check first reboot and the installed session. Verify that an installed workspace can switch to H.264 and back without reinstalling. |
| Graphics and input | Check 1280×800, 1920×1080 and 1920×1200 where offered. Confirm guest geometry, full Android surface redraw and aligned input. Exercise keyboard, mouse and touch, H.264, Mavk and fallback. With Mavk, background and resume the same Activity and verify a moving desktop on the same VM. Test Activity destruction separately and record whether a new surface receives a frame. |
| Windows ARM64 Experimental | Select the supported Windows 11 ARM64 ISO in Android's picker. Confirm disk preparation, first Setup frame, automatic installation, first installed frame and a normal restart. Verify that U-AVF prepares WinPE from that ISO without requesting a separate template. Confirm that mismatched `install.wim`, `boot.wim` or BCD media is rejected at selection and preserves the previous ISO selection. Check that the workspace and data survive return to Android and a repeat boot. On a disposable workspace, check manual Setup and read-only Setup independently. Record whether manual Setup can hand off to an installed boot. |
| Mavk timing | Use the same moving window at 1920×1200 for GNOME X11 and KDE Plasma X11. Track visible position updates and p50/p95 intervals. Record the actual guest and display modes. A GNOME 90 FPS measurement requires a guest mode above 60 Hz. Compare KDE with its recorded 29–30 updates/s and the 60 FPS target. Count visible motion separately from AHB presentations. |

Reference evidence and prior candidate limitations are in
`../UAVF_1_2_CHECKPOINT.md`. The recorded GNOME guest mode was 1920×1200
at 60 Hz, with about 60 updates/s during short drags. The KDE retest was
about 30 updates/s. Preserve those results as reference measurements.

For the 90 Hz diagnostic launch, `encoded_guest_refresh_hz=90` requests the
same rate in AVF and on the Mavk surface. Confirm the actual XRandR mode
inside Ubuntu, the Android mode logged at desktop readiness and the visible
frame rate. The two refresh requests alone establish neither a 90 Hz guest
mode nor 90 FPS.

## 1.0-rc.5/code13 acceptance

Never replace a running VM or erase the working user workspace for acceptance.
Use a separate disposable workspace for destructive installation/storage tests.
Stop VM from U-AVF before updating APK; same release certificate preserves data.

Owner waiver2026-10-04: skip step4 (clipboard hot-toggle/bidirectional acceptance)
and the real disk/partition/ext4 growth portion of step8 this cycle. They are not
required release gates and are not represented as PASS. Other error/data-retention
checks remain applicable; no disk should be resized solely for these waived tests.

1. Update in place, verify app version/code13 and both AVF permissions.
2. Cold app start: existing workspace remains and boots installed Ubuntu, not ISO.
3. Settings → Licenses and privacy: all documents open and long text scrolls.
4. Clipboard starts off by default on clean app preferences. Enable during VM:
   Android→Ubuntu and Ubuntu→Android text work. Disable: no further synchronization.
5. On disposable workspace choose official Ubuntu24.04.5 ARM64 ISO and32/48GiB root.
   Guided installer opens automatically. Select a user and password (no shared
   default password). Installer formats only vda3; ESP and ISO remain untouched.
6. Complete installation, late-command patches runtime; normal Restart now triggers next boot, which
   reaches installed GNOME with normal login, Snap, VirGL/Mali, display, audio,
   keyboard/mouse/touch and network. Clipboard socket exists and opt-in works.
7. Save a file; shutdown/reboot/start preserves it. Background/rotation restores
   encoded display rather than an unexplained fallback/frozen old frame.
8. Increase disposable root storage: both partition and ext4 size increase after
   boot; file remains intact. Low-space/invalid ISO/incomplete install show errors
   without deleting another workspace or bypassing integrity checks.
9. Optional report sharing off: no upload/auth. If owner approves final privacy,
   sharing on sends correct app version; live create-only rules must be verified.

Record actual outcomes, not assumed PASS. Offline account/geometry tests are not
a replacement for installer acceptance. Installer flicker, App Center slowness
and installed-state recognition after first reboot remain accepted known issues.

## Publication-only gates

- Complete exact GPL/LGPL corresponding sources/configs/relink requirements.
- Final privacy contact, retention/deletion and live backend abuse protection.
- Owner-controlled portable encrypted signing-key recovery.
- Owner approval to publish/commit after acceptance; not granted in this task.
