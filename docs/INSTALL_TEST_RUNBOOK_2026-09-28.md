# U-AVF guided-install test — 2026-09-28

This is the next user-run test. It does not alter GOLDEN, the official Ubuntu ISO, or the Windows baseline. Do not run it against a workspace containing Ubuntu data you need: reinstall intentionally reformats only the dedicated Ubuntu root partition.

## Candidate

- Current APK (installed on tablet): `C:\Users\denis\MainProjects\win11ontab\Non-root-Windows-VM\android-app\out\WinAVF-test.apk`.
- Current APK SHA-256: `F53C4D19B2A3A970B40B2D29B0D628953ECE7DADCDCCF8CFE5976282428AB827`.
- Corrected APK size: 269,411,452 bytes; v3 signature verification passes.
- Previous APK SHA-256: `1B39E1D014B992EB9D8A0BC67268E5F4658848D884A1CC570A8B0CF1E54D0544` (contains the preflight bug; do not use for another install).
- P40-derived install platform prefix SHA-256: `817A07876629FC8C788D21E7689B5AD9DF9502AB0584A15AA1DE3B9E0A7F6D64`.
- Embedded install initrd SHA-256: `4A17A7BC439D2C4A54ADB4A7669C6013586724BEC80768073C1A98B854221BDA`.
- Expected disk topology: one 36 GiB virtual disk; vda1 U-AVF ESP, vda2 unchanged official Ubuntu 24.04.5 ISO, vda3 dedicated 32 GiB Ubuntu root.

The new `Reinstall Ubuntu` action is explicit and warns that Ubuntu files on vda3 will be erased. Before starting, the app audits the GPT, makes a verified rollback copy of the ESP, and updates only the ESP boot files. The installer's preflight then rejects any unexpected disk size, partition size/label, or filesystem before installation begins. Autoinstall preserves the GPT, vda1, and vda2; only vda3 gets an ext4 filesystem.

## Test procedure

1. Install/update from the APK above. Confirm its SHA-256 first if copying it between devices.
2. Open Linux workspace and select **Reinstall Ubuntu**. Read the warning; cancel if this is not the disposable test installation.
3. In Ubuntu Setup, complete only the requested locale, keyboard, and account screens. Use username `uavf`, choose a private test password, and save it. Timezone is currently set to UTC because Subiquity 24.04 does not expose timezone as an interactive autoinstall section. Do not change storage layout or choose any disk erase/format option. If a storage editor or whole-disk confirmation appears, stop and capture the screen instead of proceeding. If Subiquity asks to confirm that it may use the autoinstall configuration, continue only when it is clearly the ordinary autoinstall confirmation; capture it for the report.
4. Confirm Setup is foreground/full-screen without opening the regular desktop first.
5. Wait for the install to finish. The intended flow is: successful installer late-command stages U-AVF runtime → installer powers off the Live VM → app detects the ESP `installed.ready` marker → app starts the installed system once.
6. Report whether you reach installed GNOME and whether login works. Do not manually run a shell command or select Temporary Live Session during this test.

## Evidence to return

From `Android/data/com.example.winavf/files/` (or app diagnostics export), preserve:

- `ubuntu-install-runtime-report.txt`
- `ubuntu-persistent-serial.log`
- `ubuntu-gnome-vsock-report.txt`
- a screenshot if Setup stops at a warning, storage screen, completion screen, or login loop.

Expected serial markers include `LV:AUTOINSTALL_CONFIG=READY`, `LV:INSTALL_RUNTIME_TRIGGER=LATE_COMMAND_ONLY`, `UAVF_INSTALL_PREFLIGHT=PASS_EXACT_GPT_GEOMETRY_AND_PROTECTED_MEDIA`, `UAVF_INSTALL_RUNTIME=RUNTIME_STAGED`, and then the installed boot's `UAVF_FIRSTBOOT=RUNTIME_ACTIVE`. Missing markers are diagnostic; do not repeat installation until the report and serial log are saved.

## What is verified before this test

- Static autoinstall audit passes: GPT type, exact partition starts/lengths, labels, and filesystems are checked; only vda3 is formatted; vda1/vda2 are preserved; runtime staging is gated on the installer's late-command completion.
- Tablet attempt 1 stopped before language/storage because the preflight requested an `lsblk` column unavailable in Ubuntu 24.04; it did not reach partition formatting.
- Tablet attempt 2 booted Live Ubuntu and auto-started Setup, then stopped in early-commands with `UAVF_INSTALL_PREFLIGHT=FAIL ESP_LABEL_MISMATCH`. The GPT name is correct; `lsblk -r` escaped spaces in raw PARTLABEL output. The check is now fixed. This failure also occurred before storage actions and did not format vda3.
- The installed APK did reach the on-device guided-install path: preflight passed, then Subiquity rejected storage conversion with `autoinstall config did not create needed bootloader partition`. This happens before curtin formats vda3. Root cause: the preserved ESP action lacked `grub_device: true` despite `flag: boot` and `/boot/efi` mount. That marker is now added to the source YAML and required by the static audit, but a new initrd/prefix/APK still needs to be built and tested; the APK SHA above is the previous failing build, not the next test candidate.
- The new initrd preserves the early CPIO byte-for-byte; its main archive and extracted tree pass offline audit. The P40 launcher and kernel in the platform prefix are unchanged.
- The previous APK was runtime-tested and failed before language/storage. The corrected APK is built and signature-verified; it still requires installation and one controlled runtime test.

## Known release caveats

- Fullscreen is requested by the app but cannot be independently acknowledged by the current AVF API.
- The autoinstall identity screen is interactive. The YAML contains a temporary example password hash as a fallback; the test user should set a private password in Setup. Replace this mechanism with per-install identity handling before public release.
- This runbook validates the first automated installation cycle only. A second clean cycle and persistence/relaunch pass are still required.
