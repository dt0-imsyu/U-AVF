# Prepared candidate: U-AVF 1.0-rc.5

Status: local candidate prepared; public distribution HOLD. No commit/push/tag.

APK: U-AVF 1.0.apk, package com.example.winavf, versionCode13, AndroidAPI36+ ARM64.
SHA256 7104BA1221455B6F2E8098298E25C875C7D3F813FFAD7B3F07139EBEBD8B0295.

RC5 changes Firebase-cloud consent/privacy-purpose text, not runtime algorithms.
RC4 remains running on the tablet; RC5 UI device acceptance is not yet performed.

## Completed

- Signed release build, non-debuggable, backup disabled, diagnostic launch extras
  disabled, 16KiB ZIP alignment and signature verification PASS.
- Six Java suites PASS against current RC5 classes; account guard,
  installer static safety and shell parsing PASS.
- 43 runtime assets match the audited updated base; six packaged legal documents.
  Installer authentication choices preserved; session audio/clipboard discover
  the real account. Encoded EOF handoff retries are bounded; capture/encoder,
  early CPIO, kernel, launcher and official ISO preserved.
- Removed shared installer password. Interactive account required; bootstrap
  does not mark a target ready without an unlocked password account.
- Fixed report version identification and added legal/privacy screen in settings.
- Bilingual usage/release notes, scoped license with earlier Apache/third-party
  rights preserved, checksums, private source checkpoint and source inventory.
- Rebuilt wrapper byte-identical; local U-Boot source/config checkpoint retained.
- RC1 installed in place; legal viewer and AVF permissions verified. Acceptance
  exposed an ESP update bug when installed handoff existed but ready preference
  was absent. RC2 preserves that handoff and offers verified backup recovery.
- Disposable sparse recovery test PASS: unknown ESP refused, root/ISO/GPT intact,
  marker across chunk boundary detected and repeated recovery idempotent.
- RC2 installed in place. Actual old ESP restored with exact SHA readback:
  E034AAA119F9F14F69969079DFDB365C4F4806B336D0F18DA6FC9A3AD5973A28.

## Not represented as completed

RC3 fixed readiness pacing/elapsed-time bounds; RC4 fixes account policy and
encoded session handoff. Separate QA clean install with custom qauser and
password-required login completed; normal Restart now automatically booted
installed Ubuntu. Real GDM accepted the password; authenticated GNOME, Firefox
Snap, session audio and clipboard socket work. Repeat login with APK-only EOF
reconnect retained hardware encoded1920x1200 output. See DEVICE_ACCEPTANCE_2026-10-04.md.
No renewed active-workload FPS or human audio-latency claim. Metered connection
prevented the installer security-update substep; copying/bootstrap completed.

- Recovery boot acceptance PASS: installed GNOME and LVH1 revision2 ready; keyboard
  shortcut opens terminal, hardware Surface output and PCM stream alive, normal
  guest shutdown completes. Production root was not formatted. Audible
  audio and broad regression acceptance remain pending; no renewed FPS benchmark.
- Clipboard hot toggle/bidirectional copy and real partition/ext4 expansion
  acceptance waived by owner on2026-10-04; not marked PASS. Final lifecycle/input
  acceptance remains listed in ACCEPTANCE.md. No disk resized for this waiver.
- Complete third-party corresponding source/build/relink obligations.
- Owner-approved privacy contact, retention/deletion and live backend controls.
- Portable secure signing-key recovery and publication approval.

## Accepted known issues / limitations

Installer flicker, slow App Center and installed-state recognition after first
reboot were intentionally not fixed. Hardware OpenGL is established on the test
device; hardware Vulkan remains blocked. Windows boot remains research-only.
1920x1200/60 display capability is not a guarantee for every application/device.

Only candidate/ is the proposed distribution set, and only after gates close.
internal/ includes private provenance/test material and must never be uploaded.
Rebuilding/source auditing tools never grant permission to publish the result.
