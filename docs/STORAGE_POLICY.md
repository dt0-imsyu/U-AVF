# Local storage reserve

User requirement (2026-10-08): retain at least 50 GiB free on C:, D: and E:.
User requirement (2026-10-09): retain approximately 50 GiB free on the tablet's
shared /data filesystem too. This reserve is not available for Windows installation.
Before staging or installation, budget physical installation writes and temporary
copies in addition to the reserve. Do not start a storage-consuming test below it.
Before large builds, extraction, image clones or transfers, check free space
including peak temporary allocation; do not count sparse logical length as
physical allocation. Target at least 55 GiB after cleanup for modest headroom.

Do not automatically delete personal data, GOLDEN, source, keys, official ISOs,
production/persistent disks, current diagnostic candidates or their required
inputs. Delete only enumerated obsolete/rebuildable project outputs. Preserve
small logs, manifests and extracted evidence. No recursive broad-root cleanup.

Use one disposable working disk and small audited forward/reverse deltas where
possible rather than retaining full clones for every experiment. Remove APK
asset staging copies and unsigned/aligned intermediate APKs after successful
builds; keep signed deliverables and source assets. Preserve the current known-good
and next candidate. Check the reserve again after long operations.

E: has observed Disk Event153 retries; avoid heavy writes even when space is free.

Cleanup 2026-10-08: 242 explicitly checked files removed, approximately 113.4 GiB
physically reclaimed across C:/D:. Free at verification: C:57.3 GiB, D:59.5 GiB,
E:82.9 GiB. No E: deletion. Current Windows/Ubuntu and GOLDEN preserved.
Detailed manifest: workspace-parent `build-logs/storage-cleanup-20261008.json`.
Deleted test outputs are not in the recycle bin; regenerate from retained sources.
This is an operational reserve policy, not an OS-wide quota or background cleaner.

2026-10-09 10:16 reserve recheck: C:54.1GiB, D:59.5GiB, E:82.9GiB;
tablet /data59,981,120KiB (57.2GiB) after full Windows image apply and first
installed boot. No cleanup required now. Current diagnostic owner PID15266 has
the detached52GiB stop guard; refresh exact-PID guard on each new cold launch.

Tablet cleanup 2026-10-09: two explicitly verified obsolete Windows diagnostic
copies removed, approximately 17 GiB reclaimed. Retained PC inputs allow rebuilding
them; they are not in a recycle bin. Manifest: workspace-parent
`build-logs/windows-post-linux-reassessment-20261004/obsolete-device-windows-cleanup-20261009.json`.
After cleanup /data still has approximately 40 GiB available, below the new reserve.
Further safe cleanup is required before staging/installing Windows. Ubuntu,
production persistent storage, GOLDEN and personal files must remain untouched.

Further cleanup 2026-10-09: SHA-verified legacy diagnostic windows.img and
prepared shell windows-kd.img removed (another17GiB), exact full PC images
retained. Private Windows working disk unchanged. Free /data now59,798,152KiB
(~57.0GiB); reserve restored, installation headroom only7GiB. Cold restore from
prepared shell scratch now requires restaging; do not assume that copy exists.
Manifest: workspace-parent `build-logs/windows-post-linux-reassessment-20261004/redundant-device-windows-staging-cleanup-20261009.json`.

Latest verified tablet state 2026-10-09: obsolete real-setup copy removed only
after the complete reverse-delta recovery SHA was verified. Total obsolete
Windows diagnostic copies reclaimed: 42.5 GiB. Manifest:
`obsolete-real-setup-cleanup-20261009.json` in the same evidence directory.
Current /data available: 68,684,440 KiB (65.5 GiB); only 15.5 GiB above reserve.
The Windows target is sparse: 72.5 GiB logical size is NOT physical free space.
Preserve at least 50 GiB during Windows apply, not only before/after the test.
Do not restore, truncate or replay image deltas against the running mutable disk.

Isolated Windows apply guard: `tools/windows-reassessment/android-owner/
watch-install-reserve.sh` runs detached on tablet, checks /data every second,
and force-stops ONLY the disposable `com.example.uavfwinpetest` at52GiB free.
This uses a2GiB safety margin over the50GiB reserve and ends when the exact
owner PID ends. It survives loss of ADB. Syntax/check mode verified; low-space
stop has not been triggered as a test. A forced stop may interrupt/corrupt the
disposable installation, which is preferable to consuming the user's reserve.
It is not a global Android quota and cannot prevent unrelated user apps writing.
