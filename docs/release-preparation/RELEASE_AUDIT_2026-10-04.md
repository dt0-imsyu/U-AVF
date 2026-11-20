# Release audit — 2026-10-04

Latest signed candidate RC5/code13:
7104BA1221455B6F2E8098298E25C875C7D3F813FFAD7B3F07139EBEBD8B0295.
Explicit Firebase cloud-consent and privacy-purpose text added; runtime unchanged.
Six Java suites and exact installer ESP regression check PASS against RC5.
RC4 remains the device-tested baseline; source/privacy/key recovery gates remain.

## Current RC4 state supersedes the historical RC8 audit below

Ordinary1.0-rc.4/code12 SHA256:
8C156D47A4BABCFF89C1B93AAE7B39A705904406BA199F5DA87E718B746D72F8.
Release signature/alignment, all43 runtime identities, package/ESP constants,
six Java suites and private kit checksums PASS. Installed in place; existing
production GNOME/hardware encoded1920x1200 and revision2 readiness observed.
Isolated clean custom-account install/password login and Firefox Snap PASS.
Shared fallback password removed, actual report version and in-app legal viewer
fixed. See DEVICE_ACCEPTANCE_2026-10-04.md for details, not the old RC8 statuses.
Clipboard and real disk-growth checks waived by owner; not PASS. Public HOLD
remains for complete source obligations, privacy decision, key recovery and
remaining lifecycle/input acceptance. No commit/push/tag/publication.

## Historical RC8 audit (not current release status)

Verdict: **local automated checks PASS; public release HOLD**.
No install, VM restart, data modification, commit or publication performed.

## Confirmed by this audit

- Exact RC8 APK SHA, ZIP CRC and all candidate SHA256SUMS verified.
- APK signature/16KiB alignment PASS; release certificate matches RC7.
- Actual packaged manifest: code8, version0.1.0-rc.8, minAPI36, debug/backup OFF.
- All43 assets/native payloads equal RC7; installer prefix and126MiB ESP SHA PASS.
  Deliberately wrong127MiB ESP SHA rejected. No signing credentials in APK.
- Packaged installer/display scripts equal current sources. Live single-GDM path
  and encoder-library hashes/notices PASS; no rewrite of working display runtime.
- Full current Java client compiles; guest Python syntax and installer shell syntax PASS.
- Eight Java suites PASS: clipboard wire, workspace config, VM lifecycle,
  transition markers, keyboard state, LZ4 block/WAVF, disk-size geometry.
- Python clipboard3/control4/storage5 unit tests PASS. Control test required a
  host-only fcntl stub on Windows; real Linux locking is NOT covered by that stub.
- Installer safety/static schema, root-only formatting and32..1024GiB size sync
  PASS. Clipboard initrd→Live block and all3 missing-file cases PASS.
- Old Apache text preserved; explicit19 new-file scope/headers and separate
  original-contribution scope in3 inherited files checked against local Git.
  These are provenance checks, not complete legal clearance.
- Connected tablet still RC7; both AVF permissions granted. Existing serial shows
  installed bootstrap and clipboard socket. Codec is c2.mtk.avc.decoder with
  pending0..1 and receive-to-render~8ms in the sampled static desktop. Observed
  ~5updates/s is NOT a new sustained-FPS test. Audio bytes continue advancing;
  reported underruns16 stayed constant in inspected tail. Audibility/latency were
  not independently measured here; user previously reported working audio.

## Actual unfinished release items

1. **Installer identity security:** sample fallback username/password hash remains
   in autoinstall. Identity is interactive, but this alone does not prove every
   unattended/error path must replace it. Review/remove fallback before public release.
2. **Statistics version:** MainActivity reports fixed dev-20261003-compatibility-v1,
   not package version. Confirmed observability/data-quality defect, not VM failure.
3. **Legal notices:** terms accompany the external kit but are not accessible in APK.
   Exact GPL/LGPL corresponding sources/build configs/relink obligations incomplete.
4. **Privacy/backend:** controller/retention/deletion remain draft. Local Firebase
   rules have strict owner/create-only checks, but32reports/UID is not global abuse
   protection. Live deployed rules and emulator attacks were NOT tested.
5. **Device acceptance:** RC8 not installed; hot clipboard and actual bidirectional
   copy/paste, pointer/focus/background/rotation, lifecycle and clean-user flow need
   RC8 smoke. No reinstall or destruction of the user's working system was done.
6. **Storage:** geometry and mocked ext4 growth PASS; actual expanded installed
   filesystem/data retention was not re-tested. A40GiB sparse host GPT fixture was
   not run: NTFS/sparse capability check denied, so no large file was blindly created.
7. **Signing recovery:** signing works, portable key/credential recovery unproven.

Known installer flicker/App Center slowdown/installed-status-after-first-reboot
remain accepted known issues, not fixed or disguised as passing tests.

## Private evidence

Kit: F:/dt/UAVF-release-prep/2026-10-04.
internal/release-audit-2026-10-04.json: mechanically checked candidate results.
internal/firebase-rules-audit-2026-10-04.json: local security assessment/coverage.
Neither internal/ nor private logs/source checkpoints are public upload assets.
APK identity remains CDDB146E422B4F687C2A5D8344E787B015C68C84942D08D05889699073501429.
