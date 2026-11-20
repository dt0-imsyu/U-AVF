# Generic Ubuntu: interactive P28 and the 1080p/consumer path

## Proven runtime (installed P28, not the newly built APK)

- P7 launcher, firmware FD and kernel remain the proven platform contract.
- The official Ubuntu 24.04.5 ARM64 ISO is byte-exact and unmodified.
- P27 Android touch opened the real GNOME app grid through the existing
  WAVF/vsock 4052 session and XTest input. P28 touch and basic keyboard input
  also reached GNOME; typing `gl` appeared in GNOME search.
- P28 capture measured 1.98679 FPS against its **2 FPS** target at 1024x768.
  The live report subsequently reached 1,765 frames, including 1,740
  nonblack frames, without a stream disconnect. These counters establish
  continuity, not 30 FPS or full keyboard/mouse compatibility.
- P28 disk SHA-256:
  `7153A965F282CE9233469EAC5B7EEFE01AEBA17B3F34B0E278D9FE01E98F9FA3`.
  P28 initrd SHA-256:
  `BA78B93CF26077F4D7DFB1C5C1C6B1BE2119DACD5835DA3C81BD6A67A42609D0`.

Artifacts: `C:\Users\denis\MainProjects\win11ontab\build-logs\generic-ubuntu-frame-bridge\runtime-P28-android-screen.png` and
`runtime-P28-serial-after-gnome.log` in the same directory. The P28 raw
archive contains only its first 12 **black** startup frames; do not use that
archive as visual evidence. The screenshot and live app report establish
nonblack output. The archive selection is fixed in the uninstalled next APK.

## Current source changes: offline only

The host-built APK at `android-app/out/WinAVF-test.apk` has SHA-256
`CBB41C38E0AAD36568C6315514D7E5276D878AC65FE479F2FC62D5169B334C39`.
It was **not** installed over the live P28 VM. The guest bridge source was
repacked into the offline-only P29 initrd, but not run on the tablet. The
APK's diagnostic disk name and SHA gate now select the audited P29 disk.
Changes:

- WAVF v1 keyframes use zlib; subsequent frames send only changed 64x64
  tiles. The Android decoder accepts a bounded 16 MiB payload, enough for
  an incompressible 1920x1080 BGRA keyframe. Unchanged frames emit nothing.
- Host-side `test-wavf-delta.py` at 1920x1080: PASS, synthetic keyframe
  84,430 bytes; three changed pixels across tiles: 591 bytes. The Android
  high-resolution decoder test: PASS, including a compressed edge tile and a
  deliberately high-entropy 8,296,959-byte keyframe beyond the old 4 MiB limit.
  These are correctness checks, not performance measurements on GenieZone.
- Physical mouse hover, left/middle/right buttons and wheel messages are
  mapped onto the existing 16-byte UIN1 protocol. More physical keys map to
  X11 keysyms. Tablet runtime remains NOT_TESTED for these additions.
- Stop now tracks the frame-test VM from launch, including before the vsock
  connection is established. Evidence capture starts at the first nonblack
  frame instead of consuming its quota during black startup.

The P29 diagnostic initrd is
`C:\Users\denis\MainProjects\win11ontab\build-logs\generic-ubuntu-frame-bridge\initrd-P29-delta-input`
(82,523,393 bytes; SHA-256
`BD8C61BFFBB4C440868650B15ACF18055586ADBD41419882910B9F51D6B254BD`).
Its early CPIO is byte-identical to P28 (63,149,056 bytes); decompressed
main CPIO differs in exactly one file,
`usr/local/sbin/winavf-frame-bridge`, matching the current source bytes.
The disposable P29 disk was then materialized offline at
`E:\winavf-P29-P7-platform-gnome-delta-input.img` (4,102,029,312 bytes;
SHA-256 `2280A81E1738303AF8DF21288EC4AAD01FCF3C21E22846CB3DDBA8790B04E6D0`).
The replacement tool verified unchanged P7 launcher, FD and kernel. The
independent disk audit confirmed both GPT headers, exact official ISO
partition hash `2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14`
and ISO9660 PVD; FAT1 and FAT2 match. Full-disk SHA was independently
recomputed. This disk has **not** been copied to Android or run.

## 1080p30 reality check

Full raw 1920x1080 BGRA at 30 FPS is 248,832,000 bytes/s before framing.
That is not a reasonable target for the existing full-frame Python path.
The delta encoder is a prerequisite, but 30 FPS is not proven: XGetImage,
CPU tile comparison/deflation, vsock throughput, Android decode/copy and
SurfaceView composition must all fit a 33.3 ms interval. A host-only
unchanged-frame comparison was 0.74 ms, but it excludes capture and tablet
CPU. Do not extrapolate that number to a device FPS claim.

The next runtime should use the audited P29 disposable P7-platform disk,
changing only the bridge to delta packets, first at the proven 1024x768/2 FPS.
Install the updated APK **after P28 is stopped by
the app**. Verify first visible raw WAVF records, physical mouse/keyboard,
and bounded frame timing. Only after that set Xvnc to 1920x1080 and measure
2, 10, then up to 30 FPS on the actual tablet. If software Xvnc/softpipe
cannot sustain 30, use the measured bottleneck to choose a faster capture or
encoder rather than asserting the target is reached.

## Single-official-ISO consumer flow

The current Linux Launch button still expects a separately supplied
platform/combined disk; the P28 desktop is an explicit diagnostic image.
The product goal is: user selects the official Ubuntu ARM64 ISO in the app;
the app checks exact size/SHA, generates an app-private one-disk GPT image
with the audited 128 MiB platform ESP and byte-identical ISO partition,
launches it with the P7 kernel-first config, and exposes display/input
settings and logs. The official ISO must never be mutated. All derived
platform assets and guest hooks need an explicit redistribution/license
review before bundling into a release APK; changing the project license
does not override their upstream licenses. No consumer one-ISO claim yet.

## Operating guardrails

P28 is still running at this checkpoint. Do not install the new APK or
delete/mutate its device media while it is running. Do not modify GOLDEN,
the Windows baseline or the official ISO. Do not add a second virtio-blk or
auto-memory-balloon. Do not commit/push or change license until the user
authorizes the final release after stable output/input.
