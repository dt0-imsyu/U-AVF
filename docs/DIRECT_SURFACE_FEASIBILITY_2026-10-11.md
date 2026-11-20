# Direct VM -> Android Surface: quick feasibility audit

Date: 2026-10-11. Stock SM-X736B / Android16 / app-owned AVF.
Verdict: **no supported integration point for U-AVF on the current device**.
This is an API/ownership boundary, not a measured GPU bandwidth limit or a
claim that crosvm universally lacks Android display support.

Revalidated read-only on 2026-10-12: connected SM-X736B has the same build
fingerprint and both exact APEX Java jar hashes below; SELinux remains
Enforcing, GenieZone is reported, assignable devices are empty, and host
DRI still exposes only system-owned card0. The September22 upstream commit
was checked again. No VM start, installation, profile or disk mutation was
performed for this revalidation. Exact shipped crosvm build support remains
unestablished; the absence of a supported app Surface/export endpoint is
the actionable blocker, irrespective of whether the backend is compiled in.

## Fresh device evidence

- Fingerprint samsung/gts11xx/gts11:16/BP4A.251205.006/X736BXXS9BZI1_OXM9BZI1:user/release-keys;
  security patch2026-09-05; GenieZone; SELinux Enforcing.
- Shell cannot execute/read the APEX crosvm; `--version` / `run --help`
  return inaccessible/not found. Therefore its exact commit and inclusion
  of the September22 change cannot be established by this audit.
- `vm info`: no KVM/VFIO, no assignable devices. Shell feature queries
  custom_vm/paravirtualized_devices/console_input return disabled. These
  queries DO NOT negate proven custom VM/VirGL runtime behavior.
- U-AVF process is untrusted_app; virtmgr has its own virtualizationmanager
  domain. App ownership of VM does not make APK the renderer process.
- android.system.virtualizationservice is labeled virtualization_service.
  /data/misc/virtualizationservice is permission denied; no app-accessible
  GPU/display/control socket was found in the readable Unix socket listing.
  Non-discovery is not proof there are no private sockets.
- /dev/dri exposes card0 system:system0660, not a readable renderD node;
  /dev/mali0 exists, mode0666/gpu_device. DMA heaps are system-owned,
  mainly0444/dmabuf_* labels. Existence/mode of a device is NOT proof of
  untrusted_app ioctl access or access to crosvm's resource handles.

Current APEX Java jars match the already audited September26 bytes exactly:

| Jar | SHA256 |
| --- | --- |
| framework-virtualization.jar | 8635B9E08B233EC977CDAB9A80707AFB4BEC361C86CAF6A7FA3E928B6F6DD322 |
| service-virtualization.jar | B492F4891A0CF2E1784252EFCEC68102D40B059F51E729C6040B1B31ED9F09F8 |

Fresh local DEX structural inspection of those exact framework bytes:
VirtualMachine / Config.Builder / CustomImageConfig.Builder have no Surface,
HardwareBuffer, DMA-BUF export, external GPU socket/backend executable,
or shared framebuffer mapping setter/return API. DisplayConfig/GpuConfig
exist; surfaceless means EGL rendering mode, NOT Android Surface attachment.
Raw AIDL contains CustomMemoryBackingFile/customMemoryBackingFiles, but the
supported app Builders do not expose them. Directly constructing private raw
Binder requests is outside this task; field existence is not an app API.
Extracted small DEX: build-logs/direct-display-audit-20261011/.

Prior actual app access probe remains relevant because interface bytes match:
ServiceManager lookup NULL, internal display AIDL classes absent; no guessed
Binder transactions. See HIDDEN_API_RX_AND_NATIVE_DISPLAY_PROBE_2026-09-09.md.
No new private Binder invocation or hidden API/SELinux policy change was made.

## September architecture vs this app

[Upstream commit78c5df67](https://chromium.googlesource.com/crosvm/crosvm/+/78c5df67a6ff5548157d8d12618162b91ae12d47)
was committed September22. It adds Android-display selection to the standalone
vhost-user GPU, a GPU-process control socket, and mandatory external blob
exportability. It does not add a public AVF app Surface/export API, does not
grant SELinux access, and does not magically export guest RAM to APK.

[AOSP Terminal DisplayProvider](https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/heads/main/android/TerminalApp/java/com/android/virtualization/terminal/DisplayProvider.kt)
still obtains the internal virtualization service, waits for the crosvm
display Binder and attaches Surface there. Packaging its AIDL stubs is not
a permission grant. Current ordinary AVF grants are insufficient for that path.

| Candidate | Current feasibility |
| --- | --- |
| System crosvm Android display -> APK Surface | Internal service/Surface attachment boundary; no supported app endpoint |
| External vhost-user GPU in APK | No supported AVF selector/socket/FD handoff/control endpoint |
| external_blob / DMA-BUF import | No crosvm resource export into APK; external_blob alone does not supply it |
| Android HardwareBuffer | App can allocate/use its own buffers; no AVF bridge to the guest resource |
| File-backed shared framebuffer | Raw AIDL field exists, no supported app mapping API; not proven accessible |
| Raw vsock -> app buffer -> Surface | Possible CPU transport alternative, NOT zero-copy/shared VM memory; outside this quick audit |

[HardwareBuffer](https://developer.android.com/reference/android/hardware/HardwareBuffer)
is an Android buffer abstraction, not an AVF guest-memory discovery API.
Guest FD / DXGI HANDLE is not an Android DMA-BUF FD. A vsock byte stream
does not transfer host buffer ownership/fences by naming a guest handle.
Prior gfxstream GZVM register_memory EFAULT is a separate known issue; it
does not prove all VirGL2D/native scanout routes fail. This audit is blocked
earlier at supported app integration and therefore does not repeat blob flags.

## Windows applicability without a new guest driver

Latest actual Windows profile: ROOT\\BasicDisplay, Microsoft Basic Render
Driver, DXGI system-memory desktop1920x1200; GPU/display AVF config null.
This framebuffer is NOT a virtio-gpu scanout/resource/blob. Public
[Desktop Duplication](https://learn.microsoft.com/en-us/windows/win32/direct3ddxgi/desktop-dup-api)
provides the Windows capture resource, not a cross-hypervisor Android buffer.

Therefore the new virtio/vhost-user GPU architecture cannot directly consume
this existing DXGI framebuffer with only an APK flag. The normal GPU path
would require a compatible, trusted ARM64 WDDM/virtio-gpu guest display driver
and the host app-surface endpoint. No such accepted driver is proven here.
An alternative host bridge exporting/mirroring the existing guest framebuffer
could avoid a new guest driver, but requires new supported VMM/API functionality
absent from the current APK interface, plus synchronization; zero-copy is not
automatic. No driver/trust change was attempted.

Linux has a working virtio-gpu frontend/VirGL, so its guest prerequisite is
stronger; the same Android Surface/export API boundary still blocks U-AVF.
Xvnc itself is not automatically crosvm scanout: a future direct path would
need a compositor/X-server actually presenting to the virtio display.

## Decision / minimal experiment gate

Do not start a large direct backend implementation or another VM/flag sweep.
Reopen only after one concrete supported capability appears:
per-VM setSurface/export HardwareBuffer+fence API, or an OEM-supported broker
with app ownership checks and required SELinux grants, or an exposed external
GPU/backend FD handoff. Native backend build support must also be confirmed.
Under current no-system-modification constraints APK alone cannot add it.

Then one disposable Linux virtio-gpu scanout VM can render a moving glmark2
scene into the app Surface for30s at1920x1200; measure guest present timestamps,
host acquire/release fences, actual Android presentation, latency, CPU and
copy counts. Repeat after Surface replacement. Do not claim zero-copy from
FPS or DMA-BUF existence alone. Only after that test examine Windows driver
or supported framebuffer bridge; never mutate production disks.

No VM was launched for this audit; no APK installed, no profile/disk/ISO/GPU
configuration changed. Previously running timed DXGI test was already terminal
(ADB runner closed), all owned encoder/socket/EFI/FAT rollback readbacks PASS,
VM list empty. Its FPS task is not completed by this feasibility audit.
