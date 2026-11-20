# GNOME 1080p acceleration: evidence and next experiment

Date: 2026-09-26. No Git commit or release. This document does not claim 1080p30.

## Current measured bottleneck

P31 drag: ~18–24 Android-presented updates/s; guest producer ~25.8–28.2
captures/s. The guest spent ~20–22 ms in full-screen XGetImage and ~14–16
ms in full-frame comparison/compression per attempted frame, versus ~0.08 ms
writing vsock. Eight vCPUs were already requested. Thus more vCPUs alone
cannot remove the serial capture/encode critical path.

The current Python bridge calls XOpenDisplay/XCloseDisplay and XGetImage for
each 1920x1080 frame, then compares against the previous full 8 MiB BGRA
buffer and compresses changed 64-row bands. This happens even for a tiny
cursor or window change. See `tools/linux-gnome/guest/winavf-frame-bridge.py`.

Separately, GNOME uses Mesa softpipe. A P34 llvmpipe test with
`LP_NATIVE_VECTOR_WIDTH=128` and `arm64.nosve` reached GNOME but crashed with
SIGILL at `index z1.s, #0, #1` (SVE). These settings do not make llvmpipe safe
on this guest. The working P33 APK was restored on the tablet; P34 is not a
product candidate. Preserve its raw serial as evidence.

The restored P33 runtime reached `graphical.target`, the 4052 bridge produced
non-black frames, and an Android screenshot again showed the GNOME overview.
The VM was left running for user inspection. The P34 experimental source/assets
still exist in the workspace; do not rebuild/install them as a working release.

## Ranked options

1. **Use Xvnc's local incremental RFB updates as the capture source.**
   TigerVNC already tracks changed regions, filters unchanged pixels and
   supports changed/copy rectangles. The RFB protocol allows an incremental
   FramebufferUpdateRequest and returns only updates. Translate raw RFB
   rectangles into the existing WAVF v1 delta records on vsock 4052. Keep
   Android FrameSurfaceView/decoder and input path unchanged. Current Xvnc
   uses `-rfbport -1`, so first enable a listener bound to loopback only and
   prove within the guest that an RFB 3.8 client sees one full frame and
   bounded incremental rectangles. Do not expose VNC beyond guest loopback.
   This eliminates repeated full-screen XGetImage and most full-frame diff
   work. It may not accelerate GNOME's own software rendering.

2. **Persistent X11 + XDamage/XShm.** Fewer changes to source, but XDamage
   support and MIT-SHM behavior on this exact Xvnc build need a guest proof.
   A persistent X connection is a cheap preparatory optimization but does
   not remove full-screen XGetImage by itself. Use this fallback if local RFB
   is unavailable or fails to provide bounded incremental updates.

3. **Safe multithreaded software rendering.** Mesa llvmpipe could use more
   vCPUs, but P25 and P34 show the current guest encounters unsupported SVE.
   Do not repeat a broad llvmpipe A/B based only on `arm64.nosve` or vector
   width. This requires a specific, tested CPU-feature-safe Mesa/LLVM build
   or a confirmed hypervisor CPU-feature fix. It is independent of capture.

4. **GPU acceleration.** Potentially the biggest final improvement, but the
   present stock AVF app/virtio-gpu path has no proved usable 3D scanout.
   Do not destabilize P7/P33 or claim this as a near-term 30 FPS fix.

## One next bounded test

Create a disposable P35 from the exact working P33 initrd and P7 platform.
Change only the guest frame-bridge implementation and local-only Xvnc RFB
listener. First, an offline RFB parser test using deterministic rectangles
must verify dimensions, pixel format, sequence, changed-rectangle placement,
CRC, and no regression to WAVF/Android. Then perform one runtime with a
continuous-motion window and measure separately: Xvnc update rectangles/s,
RFB receive/decode time, WAVF packets/s, Android decoded updates/s, Android
presented frames/s, GNOME interaction latency. Compare against P31/P33.
Success is sustained >=30 Android-presented frames/s **under movement**, not
merely capture attempts or a static desktop. Restore P33 if P35 fails.

## Sources

- TigerVNC server change tracking and frame clock:
  https://github.com/TigerVNC/tigervnc/blob/master/common/rfb/VNCServerST.cxx
- RFB incremental update request and rectangle semantics:
  https://github.com/svn2github/tigervnc/blob/master/rfbproto/rfbproto.rst
- LLVM issue documenting ARMv9/SVE instruction generation in virtual guests:
  https://github.com/llvm/llvm-project/issues/114987
