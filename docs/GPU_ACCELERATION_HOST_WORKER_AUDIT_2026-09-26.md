# U-AVF GPU acceleration investigation — 2026-09-26

## Current milestone

The user confirmed Linux is visible in the U-AVF app. The running VM is left intact.

```text
LINUX_DESKTOP_VISIBLE_IN_APP       = PASS
WAVF_4052_FRAME_TRANSPORT          = PASS
WAVF_1920x1080_NONBLACK_FRAME       = PASS
ANDROID_HOST_GFXSTREAM_GLES_ON_MALI = PASS
LINUX_GUEST_VIRTIO_GPU_3D           = NOT_WORKING
GNOME_RENDERER                     = SOFTPIPE
```

The GPU distinction matters: the AVF/crosvm host successfully initialized
Gfxstream GLES on the tablet's Mali-G925, but Linux GNOME still runs through
the existing Xvnc/software-rendering path. Host EGL initialization is not
evidence of guest 3D acceleration.

## Gfxstream GLES run

The app's experimental setting produced this crosvm configuration:

```text
--gpu=backend=gfxstream,context-types=gfxstream-gles,
      egl=true,gles=true,surfaceless=true
```

Android logcat confirms:

```text
Gfxstream initialized successfully!
Graphics Adapter ... Mali-G925-Immortalis MC12
Graphics API Version ... OpenGL ES 3.2 ... Mali G925
```

The same run's guest serial still reports:

```text
virtio_gpu_get_capsets: timed out waiting for cap set 0
LV:SOFTPIPE=ENABLED
No virgl contexts available on host
```

The desktop nevertheless reached GNOME and was sent to Android through the
already-established WAVF/vsock 4052 pipeline. Captured WAVF packets passed
CRC and pixel checks at 1920x1080. The frame-rate report is variable and does
not establish stable 30 FPS.

Artifacts are preserved in `build-logs/gpu-hardware-audit-20260926/`, notably
`gfxstream-gles-host-logcat.txt`, `gfxstream-gles-serial.log`,
`gfxstream-gles-wavf.raw`, `gfxstream-gles-vsock4052-report.txt`, and
`gfxstream-gles-frame-report.txt`.

## Other host-backend attempts

### VirGL

The Samsung crosvm accepted the configuration sufficiently to attempt
initialization, but logs from that attempt showed EGL context failure and
`pcivirtio-gpu activate failed: worker thread missing on activate?`; guest
capset discovery timed out. Do not infer VirGL works from advertised guest
features. `virgl-run-serial.log` preserves the guest side. No system crosvm,
Android system/vendor files, permissions, or SELinux policy were changed.

### Gfxstream Vulkan

One attempt using the AOSP-style `gfxstream-vulkan:gfxstream-composer`
contexts with Vulkan enabled aborted crosvm during Android color-buffer
creation:

```text
Failed to find memory type for creating buffer
Failed to create Vk ColorBuffer ... format:32993
Failed to create ColorBuffer ... 640x480
SIGABRT in VirtioGpuResource::Create
```

This is a distinct host-side allocation failure. Do not repeat the identical
configuration. Preserve the host log in the audit artifact directory.

### Prepared, not installed: host-memory Vulkan diagnostic

The app source now has a separate default-off toggle,
`Try Gfxstream Vulkan host-memory path`. It selects the AOSP-style Vulkan
contexts and passes the documented Gfxstream renderer override
`VulkanAllocateHostMemory:enabled`, targeting the prior allocation failure.
The app APK builds and verifies successfully:

```text
SHA-256: 65B5A2E84076F50392264529AA81588399C4E14B405B001C6D513F1130101D3C
Size:   135181414 bytes
```

This APK is **not installed**. The active Linux VM was left running. This
configuration would only re-test host renderer allocation; it cannot by
itself enable GNOME 3D until a matching ARM64 guest Vulkan ICD/Zink path is
available.

## Correct next direction

The current GLES-only configuration is not sufficient to accelerate a normal
Linux Mesa desktop: the guest has no successful 3D capset and continues to
select softpipe. Upstream crosvm's documented Linux Gfxstream path instead
uses a **Gfxstream guest Vulkan ICD + Mesa Zink**. The documented flow builds
`gfxstream-vk`, sets `MESA_LOADER_DRIVER_OVERRIDE=zink` and
`VK_ICD_FILENAMES`, then starts a DRM compositor. Its example ICD is
x86_64-specific; this project needs a Linux ARM64 guest build/artifact and an
app-owned disposable way to expose it without modifying the official ISO.

Before another runtime:

1. Establish whether the current upstream guest Vulkan client builds for
   Linux AArch64 and list its exact runtime dependencies/ICD JSON. The
   checked-out current upstream tree no longer contains the older standalone
   Meson guest build described in the older crosvm documentation; fetching
   that historical commit failed with HTTP 500, and its archive URL returned
   404. Do not assume the old x86_64 sample can be used as an ARM64 binary.
2. Determine whether the shipped Samsung Gfxstream host Vulkan path can be
   made to initialize by changing a documented AVF GPU config parameter,
   based on the recorded color-buffer allocation failure. Do not guess at
   guest ACPI/platform settings.
3. Prepare a reversible app-owned guest overlay for the driver and Zink
   selection; leave stock Ubuntu ISO, P7 firmware/launcher, GOLDEN, and
   Windows baseline untouched.
4. Only after those checks, schedule one bounded runtime with the existing
   4052/WAVF presentation path and require a guest renderer report identifying
   Gfxstream/Zink (not softpipe/llvmpipe) before calling guest acceleration
   PASS.

## Guardrails

- Do not stop or restart the user's currently visible Linux VM without a
  specific planned test.
- Do not modify the proven P7 platform, GOLDEN, official Ubuntu ISO, Windows
  baseline, Android system/vendor, SELinux, or device privilege state.
- Do not claim GPU acceleration from the Mali host-adapter log alone.
- Keep existing WAVF -> vsock 4052 -> Android decoder/SurfaceView path intact.

## Primary references

- AOSP crosvm Rutabaga/Gfxstream documentation, including Linux guest
  `gfxstream-vk` + Zink flow:
  https://android.googlesource.com/platform/external/crosvm/+/eb4de73dc9459714b00e3a32e183fd4914676839/docs/book/src/appendix/rutabaga_gfx.md
- AOSP crosvm Gfxstream renderer flag definitions:
  https://android.googlesource.com/platform/external/crosvm/+/293913c01a306a99ff0540bf7c13fad9f0fe931b/rutabaga_gfx/src/rutabaga_utils.rs
