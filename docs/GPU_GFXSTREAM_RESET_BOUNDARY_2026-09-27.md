# U-AVF GPU worker boundary — 2026-09-27

## Result

The exact working P7 Ubuntu initrd (`1A9D831567B8A7AD2DE10B413166DA96B9756525F59CB72A76FE77B05B18E2DC`) was used to build the disposable GPU diagnostic initrd (`8A527C2950F649E818E09A7EA0D6657F5BE152088EB443C3867BFEB1BBA10C0A`). The P7 launcher and Linux kernel were byte-identical in the source and output ESP. The stock Ubuntu ISO was not changed. The disposable one-disk image passed its full expected SHA-256 check (`5F357BA8855CB4733F23302FB0358E7B99F198368C7369180031B6F7E91D5A63`).

Two app-owned GPU configurations were run with that same guest disk: `HostComposition:disabled`, then `VulkanAllocateHostMemory:enabled`. The latter flag was confirmed active in the host log. Both reached Linux/GNOME and returned non-black 1920×1080 frames through the existing 4052/WAVF/Android path. Both failed guest Vulkan enumeration:

```text
virtio_gpu_get_capsets: timed out waiting for cap set 0
LV:GFXSTREAM_GUEST_ICD=INSTALLED
LV:XVNC_ROOT_COPY=YES
LV:XVNC_UNIT=CONFIGURED
LV:VK_ENUMERATE_RESULT=-3 DEVICE_COUNT=0
LV:VK_DEVICE=FAIL_FALLBACK_SOFTPIPE
LV:XVNC_SOCKET=PASS
```

`-3` is `VK_ERROR_INITIALIZATION_FAILED`. The defensive preflight prevented the earlier GNOME “Oh no” crash; it did **not** establish hardware acceleration. The host-memory flag did not change this result. The host selected the physical `Mali-G925-Immortalis MC12` Vulkan device and initialized Gfxstream, so neither an absent host Vulkan loader nor an omitted guest ICD explains the current failure.

## New actionable boundary

The host-memory run's crosvm log has this ordering:

```text
01:58:24  Gfxstream initialized successfully
01:58:28  Failed to create Vk ColorBuffer: format:32993 not compatible (twice)
02:02:04  Gfxstream shutting down; shut down completed
02:02:04  pcivirtio-gpu activate failed: worker thread missing on activate?
guest     virtio_gpu_get_capsets: timed out waiting for cap set 0
```

`32993` is hexadecimal `0x80E1` (`GL_BGRA_EXT`). The format error is real but did not immediately kill the renderer: it remained initialized until the later GPU-device transition. Therefore **do not claim** the color-buffer error alone caused the worker disappearance.

The checked-out P7 EDK2 firmware includes `OvmfPkg/VirtioGpuDxe/VirtioGpu.inf`, initializes the virtio-GPU for GOP before Linux, and intentionally preserves its scanout across `ExitBootServices()` in `OvmfPkg/VirtioGpuDxe/Commands.c`. Linux then probes the same virtio-GPU. Upstream crosvm GPU code starts the GPU worker before the guest runs, `reset()` stops the worker, and `activate()` requires its sender/receiver, returning the **exact** `worker thread missing on activate?` error if absent. The shipped Samsung crosvm source revision has not been established, so this is a strong source-and-log match, not proof of its exact internal call stack.

The shortest discriminating A/B is a *disposable, headless firmware clone* that omits only `VirtioGpuDxe` from `ArmVirtKvmTool.fdf` while preserving the physical virtio-GPU device, Linux kernel, P7 bootloader, initrd, ISO and Android GPU settings. Serial autoboot must be validated offline first. If Linux's first GPU bind now receives a capset and enumerates Vulkan, the UEFI→Linux virtio-GPU reset/re-activate boundary was causal. If not, inspect the host's first-activation error; do not start changing additional flags. This experiment necessarily loses pre-EBS GOP graphics in the diagnostic clone and is **not** a product firmware change. Original P7/GOLDEN must remain untouched.

## Final frame-bridge control before GPU A/B

The corrected initrd selected the overlay bridge (`LV:FRAME_BRIDGE_SOURCE=INITRD_OVERLAY`). The live guest reached Xvnc and Android received non-black GNOME frames. After more than three minutes of Linux runtime the crosvm process and vsock stream were still alive, with no `streamClosed` or late fatal 180-second timeout. The static desktop correctly produced only occasional delta frames; this is not a 30-FPS claim. The display transport is now frozen as a working diagnostic baseline. The only earlier bridge messages were a startup `Connection refused` before Xvnc and one 3-second `timed out` before the stable connection.

Evidence: `build-logs/gfxstream-zink-20260926/runtime-safe/serial-stable2-final.bin`, `frame-report-stable2.txt`, `runtime-report-stable2.txt`, and `uavf-stable2.png`. The screenshot visibly shows GNOME in the Android app. The exact runtime disk hash was `D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F`.

The diagnostic FDF `firmware-work/edk2/ArmVirtPkg/ArmVirtKvmToolLinuxFirstGpu.fdf` differs from the stock FDF only by omitting the single `VirtioGpuDxe` inclusion. The incremental packaging script is `Non-root-Windows-VM/tools/linux-gnome/build-linux-first-gpu-fd.ps1`. It backs up and restores the generated stock P7 FD, then checks its exact SHA `7162202A2ED14C6BE3433915CB5786D9A41E3722647E40AA3CFEF29720D14963`. A separate-output clean build failed on the known generated `SsdtPcieOscTemplate.c` host toolchain issue; the incremental packaging completed `- Done -`.

## Linux-first virtio-GPU runtime: positive boundary and next failure

The headless diagnostic FD built successfully from the stock tree: `2FC8B2D2781AC215CF7A14DE8219E51B4554CAF8AF163703FCC4DF18BA3F37A7`, exactly 2 MiB. The existing P7 FD was restored and SHA-verified unchanged. `replace-p7-firmware.py` produced a prefix with only `/EFI/EDK2/QEMU_EFI.fd` changed; all three other ESP files and pre-ESP/GPT bytes were content-identical. Prefix SHA `E2336A9161C31CC2BBB386C77BFE3459640573DC29F5B8AA7DA61E4270AF95B5`; computed whole one-disk SHA `C2C670C75BC0534706078898A82DD683B87A491D320D153C44F2F0BB4D81A764`. Android verified that full SHA before launching. Stock Ubuntu ISO hash remained `2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14`.

The headless firmware autobooted Linux successfully (`AVF_GOP_FRAME status=Unsupported`, then `Linux version 7.0.0-31-generic`). Linux initialized virtio-GPU at `0000:00:01.0`. The prior `virtio_gpu_get_capsets: timed out waiting for cap set 0` was absent; the host proceeded to publish format support and process 3D/blob commands. This strongly supports the pre-boot UEFI virtio-GPU reset/reactivation hypothesis as the cause of the earlier capset stall. It does **not** prove a fully working 3D device or the exact Samsung crosvm reset implementation.

The new first concrete failure was `ResourceMapBlob: ErrUnspec`. The detailed crosvm log says `Failed to map blob ... failed to add the memory mapping: register_memory failed ... Bad address (os error 14)` for multiple resource IDs. Guest Mesa saw `mmap64 failed with (Invalid argument)` and `LV:VK_ENUMERATE_RESULT=-3 DEVICE_COUNT=0`. Gfxstream also reported incompatible ColorBuffer format `32993` and `CtxDetachResource: InvalidContextId`; these are separate errors whose causal order relative to Vulkan creation is not yet proven. GNOME remained visible through the software fallback and 4052/WAVF.

One targeted memory-mode A/B used the **same** FD, disk and guest, overriding Gfxstream `VulkanAllocateHostVisibleAsUdmabuf:disabled`. Host log confirmed the override, while `VulkanAllocateHostMemory` reverted to its default disabled setting. The exact same `register_memory ... Bad address (os error 14)` and `VK_ERROR_INITIALIZATION_FAILED` remained. Thus the UDMABUF override did not repair the mapping. Because two Gfxstream feature values differ, this A/B does not isolate either one's individual contribution; it does rule out this practical one-flag setting as a fix.

After evidence capture, the app converted the disposable disk back from SHA `C2C670...1A764` to the exact normal P7/Gfxstream disk SHA `D16EE353C8D0B5AC3262073D76552ED62135A4169798EF6F43D47BCF5F7D208F` and relaunched the normal GNOME fallback. The restored run reached `LV:XVNC_SOCKET=PASS` and `GNOME_VISIBLE_IN_APP_PASS` with non-black frames. P7 FD, GOLDEN, Windows baseline and stock ISO were untouched. No git commit was made.

New evidence under `build-logs/gfxstream-zink-20260926/linux-first-gpu/`:

- `firmware-build.log`, `KVMTOOL_EFI.linux-first-gpu.candidate.fd`, `KVMTOOL_EFI.p7-stock.backup.fd`, `p7-platform-prefix-linux-first-gpu.img`;
- `runtime-report.txt`, `runtime-serial.bin`, `runtime-host-logcat.txt`, `runtime-frame-report.txt`;
- matching `runtime-no-udmabuf-*` captures.

The next engineering decision should focus on crosvm/GZVM GPU blob-memory registration, not another frame-bridge, WIM, ACPI or random renderer-flags experiment. The public crosvm source identifies `resource_map_blob` as the operation that maps a host GPU memory source into guest memory, but the exact rejected Samsung/GZVM memory source is not known from current logs. Without a public way for an unprivileged APK to control the mapping source or a vendor fix, **hardware Vulkan acceleration remains blocked at this precise boundary**. Keep the existing software GNOME mode as the working product fallback.

## Evidence

- `build-logs/gfxstream-zink-20260926/runtime-safe/serial-p7-correct.bin` — first correct-P7 run, SHA-256 `14DBC7B3B4304BAE87A55EC423DC35C88B56289E4229D3BC656765D5F2A96060` at capture.
- `build-logs/gfxstream-zink-20260926/runtime-safe/serial-hostmem.bin` — host-memory A/B, SHA-256 `2FDCDA7822AA961A47BD9062E9602E937DE1DBD0F4A54EC3FB59576127DC1D2B` at capture.
- `build-logs/gfxstream-zink-20260926/runtime-safe/hostmem-crosvm-logcat.txt` — host log, SHA-256 `B7557C022CDB0F16681D1F7FAB227F5318D03B9B12CE05A5DD2645DD4D8E4DB9`.
- `build-logs/gfxstream-zink-20260926/runtime-safe/frame-report-hostmem.txt` — Android reports 62 non-black frames. This confirms presentation, not 30 FPS or 3D acceleration.
- `build-logs/gfxstream-zink-20260926/runtime-safe/runtime-report-hostmem.txt` — runtime disk and original-ISO hash checks.

Source references: [upstream crosvm GPU worker/activate/reset](https://chromium.googlesource.com/crosvm/crosvm/+/5afeaecc52e76b38a1689920ce4adffa009b912e/devices/src/virtio/gpu/mod.rs), [upstream crosvm blob mapping](https://chromium.googlesource.com/crosvm/crosvm/+/4c372205c6256e6c51d5a8ee86d5751da79fc1aa/devices/src/virtio/gpu/virtio_gpu.rs), [Linux virtio-GPU command/response IDs](https://github.com/torvalds/linux/blob/master/include/uapi/linux/virtio_gpu.h), [AOSP Gfxstream feature override parser](https://android.googlesource.com/platform/hardware/google/gfxstream/+/refs/heads/main/host/virtio-gpu-gfxstream-renderer.cpp), [AOSP documented Linux gfxstream-vk/Zink path](https://android.googlesource.com/platform/external/crosvm/+/eb4de73dc9459714b00e3a32e183fd4914676839/docs/book/src/appendix/rutabaga_gfx.md).

```text
FRAME_BRIDGE_STABLE_GT_180S       = PASS
LINUX_GNOME_VISIBLE_IN_APP        = PASS
GUEST_GFXSTREAM_ICD_INSTALLED     = PASS
HOST_MALI_VULKAN_SELECTED         = PASS
LINUX_FIRST_GPU_BIND              = PASS
VIRTIO_GPU_CAPSET_TIMEOUT         = ELIMINATED_BY_FIRST_BIND_AB
GPU_BLOB_REGISTER_MEMORY          = FAIL_EFAULT
NO_UDMABUF_FLAG_AB                = NO_IMPROVEMENT
VULKAN_DEVICE_ENUMERATION         = FAIL
HARDWARE_ACCELERATION             = NOT_WORKING
```
