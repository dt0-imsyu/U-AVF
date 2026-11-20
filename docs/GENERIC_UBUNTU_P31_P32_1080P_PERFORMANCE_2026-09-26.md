# P31/P32: 1080p motion performance and P32 diagnostic-window failure

Date: 2026-09-25/26. This is **not** a 1080p30 milestone. No commit or release
was made. The exact P7 launcher, firmware, kernel, one-disk layout and stock
Ubuntu ISO were preserved. GOLDEN and the Windows baseline were not modified.

## Controlled evidence

- P31 with eight host-matched vCPUs displayed real GNOME at 1920x1080.
  Android decoded, prepared and drew essentially every received packet.
  During a real small-window drag, the final windows were roughly 18-24
  drawn updates/s, not a stable 30. Guest capture ranged about 25.8-28.2/s;
  compute was 34-37 ms/capture, split into about 20-22 ms X11 capture and
  14-16 ms compare/compress. Vsock write was ~0.08 ms. This is a serial
  guest-producer bottleneck, not evidence that more vCPUs alone will help.
- P32 changed only the motion-pattern advance from sent-packet sequence to
  attempted-capture count. Its initrd differs from P31 in exactly one file,
  `usr/local/sbin/winavf-frame-bridge`; early CPIO is unchanged. P32 initrd
  SHA-256: `6D854004625A4DDEF66CDE0460CF4827FB5EBA4DE280B8B386163B3766ABCCD0`.
  P32 full disk SHA-256: `B23BBEB2F7BCB41220645BBE1646D9057964E28D1F64EFF4CD19F6EC945208AF`.
  Both FAT copies passed read-only fsck; GPT, partition ISO and payload hashes
  passed. Device-side staged SHA matched host before launch.
- Installed APK SHA-256:
  `24D51D90EF8997253A3B74DD650522E499034DE182CC81A62F6F9262DAAF1A8D`.
  It reserves a 32dp black control strip so the menu no longer covers guest
  pixels, restores immersive system-bar hiding on focus, and retains the
  frame/Android FPS counters. This does **not** add accessibility key filtering.
- P32 reached GNOME, the real Xvnc frame traversed vsock 4052 and was drawn
  in Android. Before failure, one Android 10-second window reported 20.85
  drawn/s; the guest's last 5-second window reported 26.81 captures/s and
  21.85 packets/s. Stable 30 presented FPS was **not** attained.

## Why the display froze

At guest time 85.77s, the user closed the temporary white motion-pattern
window. At 85.80s the bridge process logged `XIO: fatal IO error 0 (Success)
on X server ":1"`, followed by a closed 4052 stream. The crosvm process
remained alive. The pattern window is created by the same Python process
that owns the capture/vsock bridge, and it does not register the X11
`WM_DELETE_WINDOW` protocol. Under ICCCM, closing such a window may cause
the window manager to disconnect that X client. This precisely matches the
observed timing, but the exact server-side action was not traced. It is a
diagnostic-window lifecycle bug, not proof of a GNOME or VM crash.

The existing app-only `retry_frame_bridge_test` intent was attempted without
restarting the VM. No new 4052 report was produced. No VM rerun, APK reinstall,
disk deletion or media replacement followed the P32 launch. The P32 VM was
left running as requested, though its last decoded frame is stale; do not
represent the current session as usable graphics.

## Exact retained evidence

- `build-logs/generic-ubuntu-frame-bridge/runtime-P31-drag-report.txt`
- `build-logs/generic-ubuntu-frame-bridge/runtime-P31-drag-serial.log`
- `build-logs/generic-ubuntu-frame-bridge/runtime-P31-before-drag.png`
- `build-logs/generic-ubuntu-frame-bridge/runtime-P31-after-drag.png`
- `build-logs/generic-ubuntu-frame-bridge/runtime-P32-serial-final.log`
- `build-logs/generic-ubuntu-frame-bridge/runtime-P32-report-progress.txt`
- `build-logs/generic-ubuntu-frame-bridge/runtime-P32-after-retry.png`
- Host P32 disk: `D:\winavf-P32-P7-platform-gnome-motion-attempt-counter-z22.img`
- Tablet P32 staged disk: `/sdcard/Android/data/com.example.winavf/files/winavf-P32-P7-platform-gnome-motion-attempt-counter-z22.img`
- Installed app package: `com.example.winavf`; active VM name:
  `winavf-frame-bridge-test-ubuntu-24045`.

P31's **tablet-side** disposable staged and private disk copies were removed
after exact SHA verification and stopping P31, before the user requested no
further deletions. P31's host image and all P32 assets remain. Do not delete
anything else without a new request.

## Next engineering change, not yet run

Remove the synthetic motion window from a user-facing build or register
`WM_DELETE_WINDOW` and handle its ClientMessage so closing it cannot kill
the capture/vsock process. Add bridge/socket restart or reconnect recovery.
Then optimize the producer's two expensive stages: keep the X11 connection
open across captures, evaluate XShm if Xvnc supports MIT-SHM, and replace
full-width 64-row compression with bounded dirty rectangles or a native
capture/compression path. Measure drawn frames during a large-window drag;
do not infer 30 FPS from capture count alone. This requires a future runtime
only if the user authorizes it.
