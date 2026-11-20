# Gfxstream Vulkan memory-registration failure: controlled follow-up

Device: Samsung SM-X736B, stock Android16, GenieZone, Mali-G925.
Production VirGL/OpenGL works. This report concerns a separate Gfxstream test,
not a claim that the previously reported physical-timer issue causes Vulkan failure.

## Reproduction and observed boundary

1. Use the existing non-protected persistent Ubuntu VM and unchanged boot platform.
2. Select `backend=gfxstream`, context types `gfxstream-gles:gfxstream-vulkan:gfxstream-composer`,
   Vulkan/EGL/GLES enabled, and `renderer-features=ExternalBlob:disabled`.
3. Actual crosvm arguments and renderer logs confirm the override.
4. Run `vulkaninfo --summary` with a private Gfxstream ICD, without replacing
   system ICDs. All dynamic-library dependencies resolve. Guest capset mask is776.
5. Host renderer initializes and recognizes Mali. Guest ASG stream initialization
   fails; no Vulkan physical device can be enumerated.

Host, CID2321 / PID14513:

```text
Failed to map blob, resource id 10, offset 0
Failed to map blob, resource id 11, offset 1060864
Failed to map blob, resource id 12, offset 2121728
register_memory failed: Request failed: failed to add memory region
Bad address (os error 14)
```

Guest:

```text
mmap64 failed with (Invalid argument)
Failed to create virtgpu AddressSpaceStream
vkEnumeratePhysicalDevices failed with ERROR_INITIALIZATION_FAILED
```

The same host registration failure existed with ExternalBlob enabled and with
SystemBlob enabled. Disabling ExternalBlob does not resolve it. This is not
a successful hardware-Vulkan test.

## Precise investigation request

Please trace the failed dynamic registration at the crosvm/GZVM boundary:

- memory slot, GPA, host virtual address, length and flags;
- renderer blob ID, memory type, flags, actual backing and map/export route;
- return value of GZVM_SET_USER_MEMORY_REGION;
- which kernel or hypervisor check produces EFAULT (VMA lookup, purpose HVC,
  memregion HVC, or another vendor-specific branch).

Public Gfxstream sources initialize an ASG ring of12288+1048576 bytes
(0x103000), matching the offset spacing above. This supports a ring-mapping
hypothesis; the logs do not prove exact blob IDs or backing allocations.
Public GZVM sources have several EFAULT branches, so the error alone cannot
identify a unique hypervisor defect.

Shell/app access to `/dev/gzvm` is denied, despite its DAC mode. Kernel dmesg
is also denied. We have not bypassed SELinux, rooted, unlocked, or flashed.
An engineering trace or supported diagnostic build is needed to discriminate
the remaining branches rather than repeating graphics flags.

Evidence: `D:\UAVF-builds\vulkan-sigill-20261003\aligned-ring-final-host-logcat.txt`,
`aligned-ring-final-serial.log`, and `aligned-ring-result.json`.
JSON SHA256: `1D088E41C0BEFC22975B6219C297CD92C0A6E017018F8F826581F4E465E2AEFF`.
See the main audit for primary source links and production rollback verification.

## Trace access follow-up

An unchanged production VM was used for a three-second Perfetto capability
check, with no new graphics A/B. Scheduler tracing works (237 compact bundles),
but Perfetto marks printk/console unknown and both raw_syscalls entry/exit
events failed. Shell/run-as cannot read crosvm mappings, kernel log, GenieZone
event formats or kprobe controls. The needed trace cannot be collected through
the audited stock interfaces.

For an engineering build, two narrow instrumentation points are sufficient:

1. crosvm immediately before/after set_user_memory_region: log calling PID/TID,
   slot/GPA/HVA/rounded length/flags/backing, ioctl return and errno. Include the
   process that created the VM and the process that owns the backing mapping.
2. GZVM handler: label precheck failure, VMA lookup failure in gzvm->mm, VMA
   extent mismatch, purpose-HVC failure and memregion-HVC failure separately.
   Include VM owner/current PID identity and the VMA bounds when available.

Use a private engineering channel for raw addresses; public reports can redact
them after validating relationships/alignment. No need to boot the full GNOME
desktop: isolated Gfxstream ICD enumeration already reproduces the failure.
Once a failing branch is identified, repeat the exact single enumeration after
the targeted fix, then validate Vulkan workload and production VirGL separately.
