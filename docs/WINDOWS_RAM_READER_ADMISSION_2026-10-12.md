# RAM framebuffer: ownership and reader admission

Latest user priority: legitimate read-only RAM access BEFORE another capture
API or prepared async-worker A/B. No full-frame benchmark/producer, VM restart,
driver installation, BCD change or disk mutation in this audit.

## Proven vs unknown

- Allocation before EBS: own GOP launcher, EfiReservedMemoryType, 2250 pages.
  Observed FF700000..FFFCA000 exclusive, 9216000 bytes, BGRA8888/pitch7680.
- Current Windows output is present at sampled locations; current adapter
  is BasicDisplay/BasicRender. Microsoft documents inherited UEFI framebuffer.
- Exact Windows loader/PFN classification, translated resource ownership,
  current mapping/cache attributes and lifetime coordination: UNKNOWN.
  UEFI reserved type is NOT a Windows MM classification or sharing contract.
- Prior64-byte total observation proves activity, not coherent full frames,
  read throughput or60 unique updates/s.

## Documented candidate, not yet safe reader

Microsoft's KMDOD sample maps its owned framebuffer with MmMapIoSpaceEx;
PAGE_READONLY is a documented protection. Installing that sample replaces
the display driver, so it is NOT the requested access experiment.
An independent reader must first validate the whole reserved/locked range,
owner/lifetime and cache attributes compatible with the existing writer.
Do not guess WC/noncached or copy the sample's fallback into a second alias.

MmCopyMemory(PHYSICAL) accepts ordinary OS-controlled memory, not I/O space
(including firmware tables). Our reserved GOP range has NOT been proven to
belong to that valid class. No forged MDLs, arbitrary physical IOCTLs,
vulnerable driver, or kernel-call injection via KD. A read-only mapping still
does not provide an atomic snapshot/fence; tearing is a separate criterion.

## Build vs load

Machine-wide SDK lacks kernel headers/libs, but repository NuGet tools DO
contain ARM64 WDK10.0.26100.6584: km/ntddk.h and km/ARM64/ntoskrnl.lib.
Missing WDK is therefore NOT a confirmed build blocker; no download needed.
No signed ARM64 U-AVF reader or Microsoft signing route identified.
Microsoft documents Dev Portal signing for new kernel drivers. September
secure-boot/no-testsigning evidence is historical, not proof of today's
installed guest CI policy. Do not claim custom drivers inherently impossible;
also do not disable CI/testsign or bypass admission to fake a valid benchmark.

Fresh offline signature check of the retained WinAvfBootProbe.sys:
SHA256 7F8E75C440048B60F0E10AFD0BF6DC0330616682A2D252C84FE3387BDE0D5B1B,
signer AND issuer CN=WinAVF Boot-Start Diagnostic; host trust UnknownError.
This is self-signed historical diagnostic code, not a Microsoft-admitted
reader. Do not reuse its certificate as proof that this guest accepts a new
kernel module. Signed VioGpuDod/viosock packages do not expose the required
framebuffer reader interface and cannot transfer their signature to new code.
Current device check: connected, battery87%, vm list empty; C56.31GiB and
tablet about69.36GiB available. No live session to inventory without a boot.

## Prepared bounded inventory

tools/windows-reassessment/collect-windows-framebuffer-ownership.ps1 takes
THIS boot's EBS base/length and emits JSON with CIM display identity,
overlapping Win32_DeviceMemoryAddress ranges and PnP owner associations.
No mapping/driver/BCD writes. Windows PowerShell self-test PASS: six overlap/
whole-coverage cases, three malformed-range rejections. NOT run in guest yet.
Additional real mocked-inventory test PASS: correct owner/whole-frame coverage,
association failure and CIM failure; errors never grant mapping authorization.
Missing PnP ranges are inconclusive for inherited firmware framebuffer;
CIM cannot establish PFN/cache attributes. These stay explicitly UNKNOWN.
Use an existing safe diagnostic opportunity rather than an extra cold boot
for partial ownership evidence while reader admission is absent.

## Full-frame proof only after admission

Isolated profile; validate fresh range; signed/admitted read-only fixed-range
reader with restricted access, no arbitrary user-provided PA. Copy full frame
into owned memory; measure QPC duration/transferred bytes without DXGI/KD.
Correlate CRC/tiles with changing output, measure unique frames and tearing
separately. Repeated reads are not FPS. Preserve x264/vsock/MediaCodec; only
then implement a producer.

## Subsequent physical diagnostic launch: admission assumption corrected

One named ram-owner-inventory run uses the already proven original x264/8CPU
launch contract (no new mapping, driver or capture candidate). Fresh EBS again
reports FF700000/native1920x1200; actual Android screenshot shows Windows
Desktop AND the Windows Test Mode watermark. Therefore the September
no-testsigning assumption cannot be carried over to this installed guest.
The watermark is not a driver load PASS or an exact BCD/CI dump.

Microsoft additionally documents that an active kernel debugger can permit
test-driver loading; this diagnostic launch already uses KD. An isolated
documented development load may thus be feasible without changing BCD, but
ARM64/HVCI/actual driver admission remain to be tested. Production still needs
proper signing; neither KD nor Test Mode is a production solution.
This supersedes treating absent production signing as a proven impossibility
for the requested isolated feasibility test. Memory ownership/cache gates
remain mandatory before mapping.

Evidence: runtime-ram-owner-inventory/current-screen.png and fresh owner serial
markers; corresponding kd-windows-direct-x264-20261012-ram-owner-inventory
events/report. Win+R input channel writes acknowledged, but guest command
execution and CIM output NOT obtained. Stream then stopped delivering updates;
do not classify that alone as kernel hang or report a CIM owner result.
Observer reached its600s bound; vm list empty afterward. Interruption left
codec rollback at19 original/93 candidate blocks. Exact bounded recovery
restored only93 admitted candidate blocks; final ALL112 ORIGINAL READBACK PASS.
GOP, own socket and encoder cold rollback/readbacks also PASS. No active VM.

## Bounded RAM gate result after the latest explicit user request

NO-GO for independent RAM mapping in the current attempt: actual existing
cache mapping and reader ownership contract remain unproven. Not a finding
that all RAM capture is impossible, not a signature-load failure. No new
driver loaded and no full-frame copy benchmark, because the safe mapping gate
must precede either. Do not repeat DXGI/GDI/staging or clone this installation.

One cold read-only extraction of the CURRENT installed BasicDisplay succeeded:
DriverStore basicdisplay.inf_arm64_6b150b5dc035d612, WOF XPRESS8K, decoded67072
bytes/9 chunks; PE checksum matches, SHA2569B22C3D74A2B63F20533A976ACD563FA0A44C5D3863B2252DB71F190535FAB9A.
IAT RVA90c0=MmMapIoSpaceEx; call at117bc with0x404 (RW|WC), fallback call117dc
with0x204 (RW|NC). Actual successful branch NOT observed. Thus taking the
sample/default WC flag is a guess, not validated cache-compatible access.
Only1.25MiB read from device, no changes. Artifacts on new healthy E:
E:/U-AVF/artifacts/windows-ram-reader-20261012/installed-basicdisplay/.
No full-frame coherence/unique cadence/60 actualSurfaceFPS evidence.
Branch stopped at the user's safety gate; preserve current display fallback.

Additional references:
- https://learn.microsoft.com/en-us/windows-hardware/drivers/install/test-signing
- https://learn.microsoft.com/en-us/windows-hardware/drivers/install/code-integrity-event-log-messages

## Primary references

- https://learn.microsoft.com/en-us/windows-hardware/drivers/display/microsoft-basic-display-driver
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-mmmapiospaceex
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-mmmapiospace
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/ntddk/nf-ntddk-mmcopymemory
- https://raw.githubusercontent.com/microsoft/Windows-driver-samples/main/video/KMDOD/bdd_util.cxx
- https://learn.microsoft.com/en-us/windows-hardware/drivers/install/kernel-mode-code-signing-policy--windows-vista-and-later-
