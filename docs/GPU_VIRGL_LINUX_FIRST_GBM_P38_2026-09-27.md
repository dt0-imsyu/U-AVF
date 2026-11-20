# P38: Linux-first VirGL + TigerVNC GBM, 2026-09-27

## Result

The user reports visibly smoother GNOME animations. The tablet screenshot shows a live, non-black GNOME Settings window, and the existing Xvnc/RFB → WAVF → vsock 4052 → Android path remains active. The VM was left running for the user.

This is a positive display milestone, **not yet proof that GNOME's OpenGL renderer string is `virgl`**. No `glxinfo -B` result was collected from the running guest. Do not label 30 FPS or hardware-rendered GNOME as confirmed until that direct measurement exists.

## Controlled A/B

- P36 stock P7 firmware + VirGL GBM initrd: GPU worker activation failed; guest had no VirGL capset.
- P38a: same P36 initrd and host `virglrenderer`, but disposable Linux-first firmware omits early `VirtioGpuDxe` initialization. Guest reached GNOME and reported `[drm] features: +virgl +edid +resource_blob +host_visible`. However Xvnc used the llvmpipe branch because its mode marker remained in initrd and was not copied to the live root.
- P38b: same Linux-first firmware and host backend; init-bottom now copies only `winavf-virgl-gbm-mode` into the live root. Serial confirms `LV:VIRGL_GBM_MARKER_COPY=YES` and `LV:VIRGL_GBM_XVNC=REQUESTED`. Xvnc listened on `:1` / TCP 5901 and GNOME reached a visible desktop. `CRASH_FILES=0` at the 79-second diagnostic checkpoint.

The P38b guest still logs a virtio-gpu error response `0x1200` for command `0x203`, and the host logs two `CtxDetachResource/InvalidContextId` errors. These did not prevent the visible GNOME session. Investigate their impact only if a concrete regression appears.

## Identity and preservation

- Stock P36 VirGL GBM prefix: `C7AE4D1F9BBDBFFA2CD27063A9FF59E483CC60F828CB3D9BFB66B65572C663DB`.
- Linux-first FD: `2FC8B2D2781AC215CF7A14DE8219E51B4554CAF8AF163703FCC4DF18BA3F37A7`.
- P38a prefix (firmware-only difference from P36): `0FFF5C99A563F379377C056C8E9BC2A7C21B31A211805C226CFA335DBF853C17`.
- P38b initrd: `B0B422FB7E9A268558ADB4FE5E2C75BAB307A4FFE59A5BC51CCC96FCC9D3F6AD`; source P7/P33 initrd: `1A9D831567B8A7AD2DE10B413166DA96B9756525F59CB72A76FE77B05B18E2DC`.
- P38b prefix: `DE0E7075989D2FDA564B0A55047571B3ACC0C87FDD64A535104176CD383D351B`.
- APK signed with v3 scheme, SHA-256 `FC5B2665C552AD594C4C2D0BEBE6A5BE33FF452CFE7D9264DE1355853ED881B9`.
- Official Ubuntu ISO, GOLDEN, Windows baseline, and original P7 prefix were not modified. The temporary app-owned disk was changed only by a verified prefix swap; ISO/trailer component hashes were checked. No Git commit.

## Evidence

Directory: `C:\Users\denis\MainProjects\win11ontab\build-logs\gpu-virgl-headless-p38-20260927`.

- `runtime-p38b-serial.log`: `E84AC6D197203C94098BFFADD15F7F8E2F56F7CD20717A7749ED68A82A085755`.
- `runtime-p38b-frame-report.txt`: `5796BD22050A789551FF8832FF75AF89023E4355C6FE34FBC5ED0D15FDEF9A53`.
- `runtime-p38b-host-logcat.txt`: `0EBB2414321EE1A2AD15E1202FA3DE81F08030D34C57614F4920F8FB09340154`.
- `runtime-p38b-screen.png`: `359FD6421A610036A0C63768FAE9D50591F0CB34364849A2225D7336CB7617B5`.

Android frame report recorded non-black frames and a peak measured interval of 5.75 drawn FPS. The desktop was mostly static during these intervals; this is **not** an active-motion 30 FPS benchmark. User-observed smoothness is valuable but does not replace a timed animation measurement.

## Next exact check, without reboot

In the running GNOME `:1` session, execute `DISPLAY=:1 glxinfo -B` (install `mesa-utils` only if already available through the live session) and preserve `OpenGL renderer string`. Also check `glxinfo` direct-rendering status. If the renderer is `virgl`, hardware-rendered GNOME can be marked PASS. If it is llvmpipe/softpipe, the VirGL capset and GBM Xvnc path are real but GNOME still falls back to software; debug that one boundary, not Gfxstream.
