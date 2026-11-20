# Vulkan/Venus and SIGILL audit — 2026-10-03

## Preservation and result

Production 1920×1200 encoded display is frozen. No ISO, firmware, kernel,
storage geometry or CPU-feature changes. No commits/publication.

`STOCK_VENUS_HOST_RENDERER = BLOCKED_BUILD_CONFIGURATION`

`HARDWARE_VULKAN = NOT_PASS`

This is not a claim that the Mali GPU cannot run Vulkan. Previous Gfxstream
logs selected Mali Vulkan successfully but failed GZVM memory registration.

## Exact current contract

Guest Mesa is 25.2.8-0ubuntu0.24.04.3. `virtio_icd.json` and
`libvulkan_virtio.so` are present, alongside gfxstream and lavapipe ICDs.
Default vulkaninfo enumerates only CPU llvmpipe/LLVM20.1.2. Forcing the Venus
ICD alone returns ERROR_INITIALIZATION_FAILED; no virtual hardware GPU.

Read-only DRM GETPARAM on renderD128:

- 3D_FEATURES, CAPSET_QUERY_FIX, RESOURCE_BLOB, HOST_VISIBLE,
  CROSS_DEVICE, CONTEXT_INIT: all 1.
- SUPPORTED_CAPSET_IDS: 4 (`1 << 2`), **VirGL2 only**, not Venus ID4.
- BLOB_ALIGNMENT query: EINVAL (optional newer parameter, not a GPU verdict).

Actual production host argument: `backend=virglrenderer,context-types=virgl2,
egl=true,gles=true,surfaceless=true`; Vulkan is explicitly disabled in APK.

## One controlled A/B

Diagnostic APK SHA256
`57B745FC44AE7B2DB55DB017815A797E10B0E84BC6A46CBAF77E93DC6D6DDA6F`.
Transient intent `venus_gpu_probe=true` adds only Venus/Vulkan to the existing
VirGL branch. Ordinary launches remain unchanged. No persistent GPU preference.

Host accepted exact arguments `backend=virglrenderer,
context-types=virgl2:venus,...,vulkan=true` (CID2310).

At 00:20:49.459 local host log:

```
Vulkan support was not enabled in virglrenderer
error initializing gpu backend=virglrenderer, falling back to 2d.
```

This directly localizes stock Venus failure to host renderer initialization,
before guest ICD selection or a Vulkan application. No vkcube PASS claimed.
The failed A/B remained in UEFI; app force-stop ended it before Linux mounted
root. Normal launch CID2311 restored `context-types=virgl2` without Vulkan.
Normal preceding boot was shut down through the guest agent with Power down.

## Architecture/source evidence

[Mesa Venus requirements](https://docs.mesa3d.org/drivers/venus.html): guest
requires blob, host-visible and context initialization. Android host needs
Vulkan1.1 and external dma-buf/image-modifier/foreign-queue support. Host-visible
memory relies on exported, mappable GPU memory and hypervisor registration.
Thus Venus does not inherently eliminate the class of the previous gfxstream
`register_memory EFAULT`; but this A/B fails earlier and does not test that stage.

[Rutabaga source](https://android.googlesource.com/platform/external/crosvm/+/beb03577b/rutabaga_gfx/src/rutabaga_core.rs)
maps `venus` to VirglRenderer, distinct from gfxstream-vulkan, and maps Vulkan
enablement to `use_venus` for that backend. Explicit context types filter capsets.
This is upstream architectural evidence, not proof of Samsung's exact revision.

[Linux UAPI](https://raw.githubusercontent.com/torvalds/linux/master/include/uapi/drm/virtgpu_drm.h)
defines the queried params, capset bitmask and Venus ID4.

Shell/run-as cannot execute/read Samsung's crosvm binary; exact shipped version
is unavailable through that endpoint. Runtime arguments and renderer's own
diagnostic are stronger evidence than assuming upstream compile options.

## Consequence

An Ubuntu Mesa update alone cannot add a missing host Venus renderer. Stock
APK cannot replace the privileged VirtualizationService's renderer binary.
Vendor enabling Venus is needed for this particular standard path; Gfxstream's
separate EFAULT remains unresolved. No production GPU switch or speculative
sequence of feature flags is justified by this evidence.

## SIGILL investigation

Existing systemd cores: snap-store revision1428 (PID4809 among several),
vkmark PID6542. GDB15.1 installed. Guest exposes A720/X4/X925 topology;
current AT_HWCAP=0xefbfffff, AT_HWCAP2=0x801ae181, no SVE flag in cpuinfo.
Core inspection now identifies the instruction, not merely a GPU correlation.

| Evidence | snap-store PID4809 | vkmark PID6542 |
|---|---|---|
| PC | 0xf98a78a89038 | 0xf4a14e831038 |
| Opcode (little-endian bytes) | 04 40 a1 04 | 03 40 a1 04 |
| Disassembly | INDEX z4.s,#0,#1 | INDEX z3.s,#0,#1 |
| Required ISA | SVE | SVE |
| Core AT_HWCAP | 0xefbfffff, SVE absent | same |
| Core AT_HWCAP2 | 0x801ae181 | same |
| PC region | anonymous RX 0xf98a78a89000..0xf98a78a8a000 | anonymous RX 0xf4a14e831000..0xf4a14e832000 |

File-backed NT_FILE mappings do not contain either PC; core PT_LOAD includes
each 4 KiB RX region. This supports generated/JIT code, not an instruction
inside the main ELF executable. Both executables are ELF64 little-endian,
EM_AARCH64=183, ET_DYN; GNU build-id/ABI notes are present, no architecture
attributes section. Lack of such attributes alone does not prove an ISA level.
Backtraces stop after frame0 (identical previous frame); Snap namespace library
paths are partly unavailable. Exact JIT caller is not symbolically recovered.

Standalone exact snap-store opcode + RET, affinity pinned separately to all
8 vCPUs, returned SIGILL on all 8. No core files created by these children.
Android host cpuinfo advertises SVE/SVE2; guest does not. Physical silicon
support therefore does not establish virtualized guest availability.

Both installed LLVM20.1 libraries (system and mesa-2404 snap) return:
`LLVMGetHostCPUName=cortex-x925`, features `+fp-armv8,+sha2,+lse,+neon,+crc,+aes`.
The feature list omits SVE, but does not explicitly disable it. Emitting identical
fixed-width vector IR with this target generates SVE INDEX, including z4.s in
the 16-element case. Appending `-sve,-sve2` emits NEON/literal loads, no SVE.
This reproduces the CPU-name/default-features mismatch independently of a GPU
driver, without executing compiler output or changing VM CPU configuration.

[LLVM20 Host.cpp](https://github.com/llvm/llvm-project/blob/llvmorg-20.1.2/llvm/lib/TargetParser/Host.cpp)
reads CPU part identifiers and maps 0xd85 to cortex-x925; Linux feature detection
does not supply a negative SVE flag when absent. Its LLVM_CPUINFO override is
available for isolated diagnostic processes.
[LLVM20 CPU model](https://github.com/llvm/llvm-project/blob/llvmorg-20.1.2/llvm/lib/Target/AArch64/AArch64Processors.td)
includes SVE/SVE2 in X925 defaults.

Bounded app-only LLVM_CPUINFO diagnostic retaining real feature flags but using
generic model did NOT fix either app on its first run (both SIGILL). This is not
a production workaround by itself. A cache-disabled run with the same fixture
then ran vkmark workloads (bounded score239 on lavapipe) and snap-store survived the
20-second window, deliberately terminated afterwards. GALLIVM_DEBUG=ir was also
enabled for that run. Clean confirmation WITHOUT GALLIVM_DEBUG also passes:
vkmark rendered both vertex workloads through the 15-second window and exited
0 on the harness's graceful termination (bounded score243); snap-store remained
alive for20 seconds, then deliberately received SIGTERM (-15, not SIGILL).
This is not completion of every vkmark benchmark or a long-term stability test.
snap-store logged ratings.ubuntu.com connection-refused errors, a separate
network/service issue, not a CPU crash. No hardware Vulkan claim.
No old cache/user data was deleted: MESA_SHADER_CACHE_DISABLE is process-local.
This is a demonstrated scoped candidate, not yet an integrated runtime fix.

Recommended implementation after clean confirmation: versioned application
JIT policy for this exact restricted-SVE/LLVM combination, plus a fresh shader
cache namespace or targeted invalidation when policy changes. Do not reuse
machine-code caches from the previous X925/SVE JIT target. Do not change CPU
MIDR/HWCAP for the whole VM, replace VirGL, or globally weaken Snap confinement.
Ensure each Snap namespace can read the policy fixture; the current fixture is
inside snap-store's own common directory, not a cross-application global policy.

Kernel journal read succeeds with guest sudo. Available recent kernel evidence
contains Snap desktop-integration AppArmor device-info denials, not a recovered
instruction fault trace for the historical crashes. Those denials do not explain
the demonstrated INDEX SIGILL. Original coredump metadata/disassembly retained.

## Bundled host renderer feasibility

Runtime process labels prove separate domains: APK untrusted_app, virtmgr
virtualizationmanager, VM crosvm. Sharing the Android UID is not renderer ownership.
Shipped GPU builder reflection (`build-logs/gpu-hardware-audit-20260926/
avf-capability-audit.txt`, lines128..185) exposes backend, context types, flags,
PCI address and renderer-features only. No library path, environment injection,
render-server executable/socket or vhost-user GPU selector.

Local preserved AVF source crosvm.rs uses `/apex/com.android.virt/bin/crosvm`
and maps those fields to GPU options. It is reference source, not a claim that
Samsung's unavailable binary matches every line. Runtime exact arguments and
missing-build-support message independently agree with renderer ownership.

[Upstream crosvm GPU parameters](https://crosvm.dev/doc/src/devices/virtio/gpu/parameters.rs.html)
and GPU render-server exist, but that does not expose them through Samsung's
AVF configuration. Merely packaging a Venus-capable .so in APK will not cause
the system crosvm to load it. No supported app-level substitution endpoint is
currently evidenced. No speculative build/LD_PRELOAD/security-policy change.
Verdict: BUNDLED_VENUS_DROP_IN=NO_EXPOSED_INTEGRATION_POINT, not universal
impossibility of a future custom remoting architecture.

Evidence directory: `D:\UAVF-builds\vulkan-sigill-20261003`:
production-guest-audit.json, venus-host-logcat.txt, venus-serial.log,
sigill-existing-cores.json, sigill-core-details.json, sve-llvm-audit.json,
llvm-target-audit.json, sigill-scoped-jit-test.json.
Cache-disabled diagnostic and clean confirmations:
sigill-scoped-jit-no-cache-test.json, sigill-scoped-jit-clean-test.json.

Normal restored display report: MediaCodec c2.mtk.avc.decoder,1920x1200,
11806 received/decoded/rendered callbacks, pending0. Static GNOME naturally
has lower dirty-update rate. No new60 FPS benchmark or display optimization.

## Production SIGILL integration

`uavf-safe-jit.py` gates on AArch64, missing HWCAP_SVE and LLVM20 CPU name
cortex-x925. Only snap-store/vkmark are routed; actual feature flags remain.
Shader caches are enabled in a new llvm20-nosve-v1 namespace; old caches/data
remain untouched. App Center gets a user desktop override preserving official
arguments; unknown user overrides are not overwritten. Runtime autostart creates
this entry. Direct bypass launches are not claimed fixed.

Installed cold/warm bounded tests pass for both apps without disabling caches.
Final regression records VirGL/Mali-G925, Firefox alive, VS Code launch exit0.
VS Code single-instance exit0 alone is not proof of a newly created window.
Reports: production-policy-regression.json and production-final-regression.json.
Installer/live source-exact prefix audits pass; kernel/launcher/pre-ESP/early CPIO
unchanged. APK SHA256 82A0B238DC2A9E8B84D71C2E7286EBB994CEA1C054E9906A4308A83EF936ED52.

## gfxstream memory-registration source audit

Historical runtime resource IDs4/6 fail at aperture offset0, IDs5/7 at1060864
(0x103000). Offset is not size or absolute GPA. Logs do NOT include backing FD,
host pointer, region length, blob_mem/blob_flags or absolute GPA. Do not invent
these or infer length from the gap between offsets.

Reference path: ResourceMapBlob -> rutabaga export/map -> VmMemorySource
(Vulkan/Descriptor/ExternalMapping) -> mapper.add_mapping -> RegisterMemory
-> source.map -> destination allocation -> GeniezoneVm.add_memory_region
-> GZVM_SET_USER_MEMORY_REGION. Historical error context matches the last
add-memory-region stage, not Vulkan instance creation or source.map failure.

Primary references (not a claim of exact Samsung binary revision):
- https://github.com/google/crosvm/blob/main/devices/src/virtio/gpu/virtio_gpu.rs
- https://github.com/google/crosvm/blob/main/vm_control/src/lib.rs
- https://android.googlesource.com/platform/external/crosvm/+/refs/heads/android15-qpr2-s4-release/hypervisor/src/geniezone/mod.rs
- https://android.googlesource.com/kernel/common/+/refs/heads/android16-6.12/drivers/virt/geniezone/gzvm_vm.c

Reference GZVM dynamic add rounds size to host page size, flags0, checks RAM
overlap (ENOSPC), then issues ioctl using mapped host pointer and allocated GPA.
Reference kernel can return EFAULT for missing VMA, memory-purpose HVC failure,
memregion HVC rejection, or copy_from_user. Thus EFAULT does not uniquely prove
GPU DMA-buffer pinning failure or a hypervisor bug. Vendor code may differ.

Memory alternatives exist internally, but Samsung app API has no per-blob
backing selector. Merely substituting anonymous/memfd memory is not an app flag
and would require preserving renderer resource semantics/coherency. Existing
VulkanAllocateHostMemory and UDMABUF experiments must not be repeated blindly.
Minimal direct ioctl reproduction requires VM fd/host mappings, not available
to this app/shell. A guest DRM blob probe can request registration, but cannot
observe exact host pointer/backing or distinguish the kernel EFAULT branches.
Next useful evidence is a narrowly instrumented host register-memory trace
(slot/GPA/pointer/length/flags + failing kernel/HVC branch), not another full
GNOME gfxstream reboot. Hardware Vulkan remains BLOCKED, not PASS.

## Follow-up: ASG ring backing isolation

Old P37 evidence was recovered, not repeated: SystemBlob enabled at host log
line26259, EFAULT still at30855 onward. This option is not an untested fix.
Read-only native open probe of /dev/gzvm returns EACCES both as shell and
run-as com.example.winavf. No VM creation/ioctl performed. DAC0666 is insufficient.

Reference guest ASG stream creates initial blob_id0, HOST3D, MAPPABLE, size
ALIGN(ringSize+bufferSize,blobAlignment). Host capsets use ringSize12288 and
bufferSize1048576: total1060864=0x103000, matching historical aperture spacing.
This is a strong ring-initialization hypothesis, not proof historical resource
IDs4/5 have this exact blob_id (old logs lack create-blob details).
Reference createRingBlob allocates SharedMemory with ExternalBlob enabled;
otherwise aligned process memory. Thus an early EFAULT need not involve Mali
VkDeviceMemory import/pinning at all. Historical no-UDMABUF run still had
ExternalBlob enabled. One transient ExternalBlob:disabled A/B is now prepared
and started CID2317; actual host log confirms override. Outcome pending.

Sources:
- https://android.googlesource.com/platform/hardware/google/gfxstream/+/refs/heads/emu-35-1-release/guest/GoldfishAddressSpace/VirtioGpuAddressSpaceStream.cpp
- https://android.googlesource.com/platform/hardware/google/gfxstream/+/refs/heads/emu-35-1-release/host/virtio-gpu-gfxstream-renderer.cpp
- https://android.googlesource.com/platform/hardware/google/gfxstream/+/refs/heads/emu-35-1-release/guest/vulkan_enc/ResourceTracker.cpp

Guest ResourceTracker usingDirectMapping() returns true in inspected Linux
reference; disabling coherent memory flags is not proven to remove blob maps.
No coherent-emulation runtime was executed on that unsupported assumption.

## Completed ExternalBlob-disabled A/B — CID2321

APK candidate SHA256 C1005BCF569A310BD82DDCECDF6D04772F77783FDCF7C0F53620F4A2B2E4D95A.
Actual crosvm args and renderer startup both confirm ExternalBlob disabled.
Temporary isolated probe used SupplementaryGroups=render (unit only, no account
group or permission changes). Previous headless attempt lacked render access;
its PermissionError is an instrumentation failure, not a Vulkan verdict.

Guest GETPARAM7 capset mask776 (bits3/8/9). All isolated ICD ldd dependencies
resolve. vulkaninfo --summary exits1: mmap64 EINVAL, Failed to create virtgpu
AddressSpaceStream, vkEnumeratePhysicalDevices ERROR_INITIALIZATION_FAILED.
No global ICD installed or changed. Missing DISPLAY affects surface queries,
not an explanation for the preceding ASG memory-map failure.

Host PID14513 concurrently reports ResourceMapBlob register_memory EFAULT,
including resource10 offset0, resource11 offset1060864 and resource12
offset2121728. ASG ring mapping remains the strongest source-supported path;
actual host pointer/length/blob_id/backing remain unlogged, so do not claim
exact allocation type or exact failing kernel/HVC branch as experimentally proven.

Evidence directory D:\UAVF-builds\vulkan-sigill-20261003:
- aligned-ring-result.json SHA256 1D088E41C0BEFC22975B6219C297CD92C0A6E017018F8F826581F4E465E2AEFF
- aligned-ring-final-host-logcat.txt
- aligned-ring-final-serial.log
- aligned-ring-service-permission-failure.txt (instrumentation only)

Verdict: ExternalBlob-disabled does not bypass the existing memory-registration
blocker. Hardware Vulkan NOT PASS. This does not prove all future Vulkan paths
impossible or identify EFAULT uniquely as a GenieZone hypervisor defect.
Next useful test requires a host registration trace (slot/GPA/HVA/size/flags,
source backing, ioctl and failing kernel/HVC branch), or a supported host build
with an instrumentable renderer/VMM. App flags alone cannot provide that trace;
direct /dev/gzvm access is denied to shell and app UID. Do not repeat SystemBlob,
UDMABUF or ExternalBlob toggles without new evidence.

Rollback: exact production safe-jit APK82A0B238...936ED52 reinstalled after
confirmed Power down. CID2322 host args backend=virglrenderer/context=virgl2.
Installed GNOME visible, H2641920x1200 receive/decode/render active, accepted
input events; no sustained-FPS rebenchmark performed. Temporary diagnostic
unit and enable symlink removed; systemctl is-enabled returns4/not-found.
Private diagnostic files retained. glxinfo is not installed, so cleanup's
optional glxinfo check did not run; do not claim a fresh glxinfo renderer query.
Evidence: aligned-ring-rollback-regression.json and ring-cleanup-and-gl-regression.png
(filename does not imply a completed glxinfo check).

## Fast trace feasibility — unchanged production CID2322

Stock fingerprint samsung/gts11xx/gts11:16/BP4A.251205.006/X736BXXS8BZH4_OXM8BZH4:user/release-keys.
Kernel6.6.102-android15-8-abogkiX736BXXS8BZH4-4k; ro.debuggable0.
No GPU config changes, VM restarts, app updates or guest changes in this audit.

Shell can enumerate trace groups and read raw_syscalls format. This alone is
not trace permission. A three-second official Perfetto capture has237 compact
scheduler bundles, but FtraceStats lists printk/console as unknown_ftrace_events
and raw_syscalls/sys_enter + sys_exit as failed_ftrace_events. No relevant
console/syscall records. Do not reinterpret these as successfully enabled events.
The initial /data/local/tmp config path was denied to Perfetto; placing the
same config in its documented /data/misc/perfetto-configs path fixed delivery,
not permissions to the requested events.

Direct GenieZone event formats and kprobe_events deny reads; dmesg denies.
crosvm PID15506 maps denied under run-as despite same UID. Actual SELinux
domains differ: crosvm versus runas_app. No ptrace or policy bypass attempted.
Existing September GenieZone captures already emitted no useful vendor events;
they were not repeated.

Evidence: memory-trace-capability.pftrace (742223bytes),
memory-trace-capability-audit.json. Reproducible config/parser in diagnostics:
gzvm-memory-trace-capability.pbtxt and audit-memory-trace.py. Parser counts
known Perfetto protobuf fields and handles compact scheduler bundles; it is
not a general Trace Processor replacement.

Source discriminants: vma_lookup(gzvm->mm, userspace_addr) failure gives EFAULT;
extent mismatch gives EINVAL in inspected kernel reference. Purpose/memregion
HVC failures also give EFAULT. Basic page/GPA precheck gives EINVAL, RAM overlap
gives ENOSPC in inspected crosvm. These narrow hypotheses only if vendor code
matches; they are not runtime proof of the branch. A recent upstream crosvm
remote ExternalMapping guard gives EPERM, not the observed nested EFAULT;
do not claim that security change is this device's root cause.

Primary references:
- https://perfetto.dev/docs/data-sources/syscalls
- https://github.com/google/perfetto/blob/main/protos/perfetto/trace/ftrace/ftrace_stats.proto
- https://chromium.googlesource.com/crosvm/crosvm/+/59a43fcd80b77d7a7383f526b8fefed28500d28e

Decision: required trace is blocked on audited stock interfaces. No further
gfxstream flag A/B without an accessible new observer. Next is a vendor-host
trace with the parameters and branch labels specified in the addendum. This
does not prove the root cause is vendor-only or that all Vulkan solutions are
impossible; it prevents an unsupported local fix claim.

## Own VMM / external renderer final feasibility recheck

Current device framework SHA2568635B9E08B233EC977CDAB9A80707AFB4BEC361C86CAF6A7FA3E928B6F6DD322
matches the captured framework/API exactly. Existing on-device reflection and
preserved decompilation show backend/context/PCI/features and rendering flags,
not renderer library path, render-server executable or GPU socket handoff.
The jar contains Android DEX; javap class lookup is not a valid fresh API audit.
No framework replacement or APK changes made.

Upstream crosvm supports external GPU/render-server configurations, but the
Samsung AVF API does not expose that configuration or raw VM/vCPU/RAM/MMIO/IRQ
handles. AVF is not a public KVM-provider API for an app-bundled crosvm. Merely
running a renderer process does not connect it to the existing virtio-gpu queues.
The current supported attachment route remains absent, not universally impossible.

Custom Vulkan remoting over vsock would instead require a guest ICD/transport
and host Vulkan execution server with resource/synchronization/presentation
semantics. This is a new project, not proven by the current graphics experiment.
No implementation-time estimate or performance/compatibility claim is established.
Do not start a speculative full VMM build without a real attachment endpoint.
