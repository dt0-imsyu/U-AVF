# Windows ARM64 GPU transport

This package tests Windows kernel access to the existing Mavk Bridge. It
contains a demand-start diagnostic driver, its control client, and a separate
standard D3D11 draw test for a future WDDM adapter.

## Build and run

Run `build-admission.ps1 -OutputDirectory <fresh-directory>` from PowerShell.
It uses the workspace's ARM64 WDK/LLVM and produces three ARM64 PE files
plus the `UavfMavkRender.lib` support library.
Pass `-MicrosoftLinker <path-to-link.exe>` for the WDK-style
`/DRIVER:WDM` image. The build does not sign the driver.

Run `sign-admission.ps1` to make a separate test package with an embedded
SHA-256 signature on the ARM64 driver. Copy `run-guest-admission.ps1` into it.
Run the script without switches for Secure Boot, Code Integrity, HVCI,
elevation, Viosock and BCD evidence. `-Run` uses a temporary demand-start
service on the verified disposable volume. `-RetainedDiagnosticGuest`
selects the older isolated Android-owner diagnostic volume by its own serial.
`-TrustTestCertificate` adds
temporary trust only when the live kernel already reports test signing.
The runner removes its service and added trust and leaves boot settings intact.

While the guest probe listens, start the diagnostic Android owner's Mavk
endpoint for the same VM. It connects to port 4057 over MVK1. The driver
accepts host CID 2 only. A passing run reports device identity, 256 compute
words after a native Vulkan fence and 3330 pixels from the trusted triangle.
Failure reports the stage and NTSTATUS, or the SCM error and Code Integrity log
when admission fails. The driver registers no display device.

`Mavk/icd/windows/build-codec-probe.ps1` builds the ARM64 and x64 consumers
of the pinned gfxstream transport. They query device identity and serialized
`vkEnumerateInstanceVersion`. Start the retained diagnostic owner with
`run-owner-admission.ps1 -Codec` to select the headless codec-3 decoder.
Build that APK with `-WddmCodecLibrary` and the existing narrow test bundle.
Without that opt-in the owner serves the original MVK1 diagnostic operations.
A codec version query exercises framing and identity only. GPU completion
and WDDM admission require their own evidence.
The kernel diagnostic client accepts `--codec` via its separate fixed
read-only IOCTL. The PowerShell runner selects it with `-Run -Codec`.

`d3d11-draw.exe` lists DXGI adapters and their LUIDs. Pass a single exact
adapter name to request D3D11 feature level 11_0 and run a shader/vertex-buffer
draw followed by staged readback. It rejects software and remote adapters.
Pair the output with Android Bridge submission and Mali identity evidence
to establish the rendering path.

## Reuse and remaining interfaces

- The ARM64 kernel transport now exposes `MavkWireExchange` with 64-bit
  request IDs and bounded variable-size payloads over the existing MVK1
  stream. `MavkWaitCodecFence` uses codec 3's pinned `vkWaitForFences`
  framing, reads the Vulkan result independently of the Bridge response
  status and bounds the wait. The experimental Android decoder can compile
  with a null Surface and skip AHB presenter allocation for this route.
- The render worker can now open its own MVK1 stream, submit one pre-recorded
  Vulkan command buffer with a host fence and report the result through the
  Dxgkrnl completion path. Queue, command and fence handles are expected
  from the same codec session. The current worker serializes one submission
  through fence completion and leaves session recovery to the adapter.
  A successful Viosock open assigns a generation. The worker rejects stale
  device, queue and command bindings before writing to a reopened stream.
- `render-completion.c` maps retired work to Dxgkrnl DMA completion or
  fault notifications. Bind the interface in StartDevice and verify
  interrupt synchronization on the worker after StartDevice has returned.
  The worker checks the actual callback IRQL before issuing its first GPU
  command. Failure reports the callback status, result and the interrupt
  count when the translated resource list describes one bus. The completion
  callback also checks IRQL before notifying Dxgkrnl. This path requires a
  live Dxgkrnl DPC callback.
- The render support library provides the KMD's StartDevice, DPC and
  QueryCurrentFence callbacks and a registration routine that calls
  `DxgkInitialize` once the driver supplies its remaining DDIs.
  A single adapter-owned worker opens the codec stream and retires GPU
  work in scheduler order. Render must prepare a nonpaged submission
  record with pinned context and allocation references before the
  scheduler asks SubmitCommand to queue it. The queue links that record
  directly at DISPATCH_LEVEL without allocating memory. The worker returns
  the record for release after completion or for recovery after a failure.
  Session setup requests Vulkan 1.1, selects a graphics queue on the single
  physical device exposed by the Mali endpoint, and gives each scheduler
  context a native command pool. Adapter teardown refuses to close a session
  while context or allocation records are still live. This path has passed
  ARM64 compilation and local codec request/cleanup checks. It has yet to
  run from the installed miniport in the Windows guest.
  Preparation acquires rundown protection for the context, the recorded
  command and each referenced allocation. The command must belong to the
  context, and every allocation must belong to its adapter, Vulkan device
  and codec connection generation. Partial preparation unwinds the
  acquired references. The codec buffer path creates the native Vulkan
  buffer, checks its memory and dedicated allocation requirements, chooses
  a compatible memory type and binds it. Teardown destroys the buffer
  before freeing memory. It rejects required dedicated allocations and
  skips native destruction for handles in a retired connection generation.
  VidMm creation, opening, residency and destruction callbacks still need
  to route their allocation records through this path.
  Paging, cancellation, preemption and timeout recovery must be wired
  before exposing the SubmitCommand callback. Dxgkrnl treats an error
  returned from the physical submission DDI as a system scheduler fault.
- Yttrium's `VioGpu3DStartDevice` and `HWInit` consume translated PCI
  resources. `VioGpu3DSubmitCommand` uses the VirtIO control queue, and
  `VioGpu3DInterruptRoutine` handles that device's interrupts. The guest
  profile that passed the kernel codec test has `gpuConfig=null` and its
  AVF owner configures no new render device. A Viosock connection cannot
  serve as the display adapter's interrupt resource. Microsoft's VM sample
  enumerates a separate ACPI node with an interrupt and uses
  `DxgkCbSynchronizeExecution` to notify completion from worker-driven
  rendering. The isolated Mavk ACPI device now enumerates in Windows.
  The combined graphics package still needs a verified scheduler
  notification route, GPU object provisioning, paging, preemption and reset.
- Keep the Yttrium Mesa D3D10/11 DDI frontend, shader translation and Gallium.
  Review the DDI files and winsys together, including DXGI present and shared
  allocation callbacks. Mesa files retain their individual notices. The
  Yttrium KMD and the upstream Viosock interfaces are BSD-3-Clause. This
  package uses the Viosock ABI and contains no imported Yttrium or Mesa code.
- Yttrium's current ARM64 INF binds PCI VEN_1AF4/DEV_1050. Its adapter uses
  device feature negotiation, capsets, contexts, blob allocations and scanout.
  For U-AVF keep its DDI behavior as a reference and replace the PCI/Venus
  backend. Start with a render-only adapter and preserved H.264 output.
  Candidate UMD backend is Gallium/Zink with the generated Mavk Vulkan codec.
  The limited Windows ICD and MVK1 diagnostic draw are insufficient as the
  UMD's rendering implementation. Keep the D3DKMT adapter LUID matched to
  the exposed Vulkan device and report only verified feature/format support.
- For WDDM submission, perform Viosock I/O in a PASSIVE_LEVEL worker with
  per-context ownership. The OS scheduler needs an ordered completion or
  failure for each submission, including on disconnect. A Bridge request ACK
  cannot certify GPU completion. Track allocation references until GPU
  retirement and coordinate device removal, pending submissions and reset.
  The diagnostic driver's blocking IOCTL is intentionally outside the WDDM
  scheduling path. `render-queue.h` implements worker-side ordering and
  reference retirement, with a local test for two queues, duplicate and
  out-of-order completions, wrapping WDDM fence IDs, capacity and disconnect.
  Dxgk callbacks and the Android completion feed still need to be connected.
- Keep MVK1 request IDs scoped to each stream. Route a completion by both
  context ID and submission serial. Each new connection gets a fresh context
  ID and private resource table. A shared resource requires an explicit
  export/import and a second owned reference. Wait for the host fence before
  reporting success. On connection loss, use the WDDM fault/reset path and
  retire guest references in order. Host resources remain owned by the
  decoder until its GPU work retires. Apply these rules before routing any
  DWM shared allocation through the codec.
- The Linux codec already batches ordered recording, groups confirmed native
  creation, retains some resource storage/views, and keeps logical uploads
  together. The main remaining WDDM-specific gap is multiple simultaneous
  processes and shared allocations. The single guest listener/decoder loop
  and connection-local handles cannot implement cross-process DWM sharing.
  The present target is also currently tied to one Android Surface/pool.
  Map shared allocations explicitly in the KMD/host service and keep GPU
  release and Android release fences separate before enabling AHB reuse.

## Evidence

- The opt-in `-WddmPnp` guest EFI adds a 77-byte SSDT declaring
  `\_SB.MVKG` with hardware ID `UAV0001`. The original build was
  reproduced byte-for-byte before creating this candidate. On Tab S11,
  the EFI serial log reported `WG:WDDM_PNP_SSDT=INSTALLED`, the isolated
  Windows ARM64 guest booted, and PnPUtil reported connected instance
  `ACPI\UAV0001\0` with hardware ID `ACPI\UAV0001` and problem code
  28. The test used no IRQ declaration or bound driver. PnPUtil confirmed
  removal of the test devnode. The next package should bind this hardware ID,
  reach StartDevice, bring up the existing Mavk transport and submit real
  GPU work with confirmed scheduler completion in one run.

- ARM64 driver and both clients build reproducibly. On the Windows PC the
  x64 D3D11 control passed on the GTX 1080 Ti, 3330 pixels. The same test
  is built for the guest. A separate existing Android Bridge regression
  passed on Mali-G925, one indexed submit, 3330 pixels and clean teardown.
- Two fresh reads from the disposable disk confirmed TESTSIGNING absent.
  Two fresh reads from the older separate diagnostic disk found TESTSIGNING
  enabled on the installed loader. This provides an existing test candidate
  without a BCD edit. SignTool found the embedded signature and rejected its
  self-signed root under the build host's kernel signing policy. Guest Secure
  Boot and HVCI remain unmeasured for this package.
  The BCD was left intact. The test package was staged on the isolated ESP
  with a sector journal and exact readback. The first run reached Test Mode
  and an elevated session. The guest's PowerShell 5.1 rejected the original
  script syntax. On the follow-up native SCM test the service started with
  Win32 error 3 from the temporary ESP drive path. After copying the driver
  to the guest system volume and updating the service path, StartService
  returned Win32 487 (ERROR_INVALID_ADDRESS). The exact kernel failure
  site is unknown. The probe never reached Viosock. The temporary service
  was deleted. The runner now stages the driver on the guest system volume.
  The comparison build from the same objects and Microsoft LINK has the
  PE WDM-driver bit set. The kernel service reached RUNNING and the app
  connected to the accepted Viosock peer. The first MVK1 write returned
  STATUS_INVALID_PARAMETER and the Android endpoint received zero bytes.
  Supplying an explicit offset advanced the first exchange to STATUS_CANT_WAIT.
  The next build preserves the accepted socket settings and handles that
  would-block status with bounded polling. The upstream Viosock implementation
  sets the nonblocking flag even when asked to clear it with FIONBIO=0.
  That build passed on Tab S11: the Windows kernel verified 256 compute
  words and 3330 pixels. The Android log confirmed seven commands, two
  Mali-G925 submissions, clean EOF and no live Core resources. The package
  loaded with the guest's existing Test Mode without importing the test
  certificate. SCM stop returned 1052, service deletion succeeded, and
  the binary remained mapped until the isolated guest shut down. This
  establishes the kernel transport and GPU completion path.
- The opt-in diagnostic APK allows 8 GiB headroom on the retained diagnostic
  volume only, after the same disk and boot file checks. Installation paths
  still require 52 GiB. The APK was verified and the baseline APK backed up.
- On Tab S11 the ARM64 user-mode client reached the headless codec through
  app-owned AVF on port 4057. The original Winsock `select` path failed before
  sending a request. With direct nonblocking polling, Windows reported Vulkan
  1.3.0 and Mali-G925 Immortalis MC12, and the Android decoder recorded
  three requests, opcode 20137 once, and clean EOF.
- The signed ARM64 kernel codec package passed the same query on Tab S11.
  The retained diagnostic guest loaded the demand-start service using its
  existing test signing configuration. Windows reported `MAVK_KERNEL_CODEC_PASS`
  with API 1.3.0 and vendor 0x13b5. Android recorded three requests, opcode
  20137 once, and clean EOF. PowerShell file execution was denied by the
  existing guest policy. The test used the native SCM commands after checking
  the isolated volume serial and byte-comparing the staged binary.
  This query did not create a Vulkan device, submit work, or register a
  WDDM adapter.
- The pinned Mesa guest encoder, resource tracker and stream sources compile
  into ARM64 Windows COFF objects with their generated Mesa headers. The
  resource tracker still references `VirtGpuDevice::getInstance` and the
  coherent-memory mapping classes. The Windows DLL and the UMD have not been
  linked. Use the Bridge's explicit upload and readback when adapting the
  Windows memory path, with its own capability checks.

Sources: [Yttrium KMD](https://github.com/arehnman/yttrium-virtio-gpu/tree/e9917ae25b2597306d483e1063dba08658139bc9/viogpu/viogpu3d),
[Mesa UMD](https://github.com/arehnman/virtio-win-mesa/tree/76c095bbbd875fda21e55c69d9e1eed56980d956/src/gallium/frontends/d3d10umd),
[VirtIO Viosock WSK](https://github.com/virtio-win/kvm-guest-drivers-windows/tree/master/viosock/wsk),
[WDDM driver caps](https://learn.microsoft.com/en-us/windows-hardware/drivers/display/wddm-driver-and-feature-caps).
