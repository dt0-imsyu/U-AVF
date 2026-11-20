# Windows capture alternatives: bounded feasibility audit

Priority from the user's attachment: RAM framebuffer -> asynchronous staging
ring -> IddCx. Preserve native1920x1200,8CPU,x264,common UVF1/vsock/Android,
Ubuntu/P7/data. No driver installation, firmware change or policy weakening.

## RAM framebuffer

Actual own source: `tools/windows-reassessment/windows-ram-gop.h` allocates
2250 pages of EfiReservedMemoryType, BGRA8888, pitch1920,9216000 bytes.
`AllocateMaxAddress` selects the address; FF700000 is an OBSERVED address,
not a safe universal hardcoded address across future firmware/boots.
End observed range FFFCA000. Saved EBS audit has baseFF700000,1920x1200,
3 nonzero sparse samples, query/set/blt counters0. Direct writes bypass the
callbacks, so zero callbacks cannot establish inactivity.

Actual Desktop source inventories prove ROOT\\BasicDisplay/BasicRender,
native1920x1200/32bpp/advertised64Hz. Microsoft documents that BasicDisplay
inherits the UEFI linear framebuffer and writes output there. This makes
ongoing framebuffer activity a strong hypothesis, subsequently confirmed by
the bounded device observation below. Unique framebuffer update rate and
whole-frame coherence remain unmeasured.

Current encoder is a user-mode executable. It has no trusted physical-memory
mapping helper, no framebuffer reader device/IOCTL, and no AVF app API exposing
guest RAM. Microsoft restricts opening Device\\PhysicalMemory to kernel-mode
drivers. Adding a GPA cast or administrator elevation is not a valid reader.
KD physical reads in prior8CPU runs DID work for CPU-entry RAM, so bounded
read-only framebuffer observation through KD is plausible. MMIO read failures
from old GIC investigations must NOT be generalized to RAM.

Decision:
- GO for a small read-only observation, when preparing the next isolated boot:
  derive base/size/format from its exact fresh EBS marker; confirm Default
  Desktop from current encoder report; sample fixed bounded tile locations;
  resume normal guest execution between two observations while moving a real
  window; correlate tile changes with displayed content. Each KD stop pauses
  Windows, so these samples cannot measure sustained60FPS or production speed.
- NO-GO for immediately replacing the current user-mode capture with RAM:
  the required sanctioned mapping path is absent. If samples prove current
  pixels, evaluate a narrow signed ARM64 guest reader with validated framebuffer
  ownership/range, read-only ACL-restricted access and proper cache attributes.
  No arbitrary physical-memory IOCTL or vulnerable-driver workaround.
- Remaining correctness risk: a live scanout buffer is not a coherent frame
  snapshot. Concurrent DWM writes can tear; format, generation and synchronization
  must be established before streaming. RAM availability alone does not prove60.

## Staging ring

Already physically tested two reusable staging textures, DO_NOT_WAIT,
CopyResource+Flush and bounded ownership. Map blocking removed, actual
Android24.902FPS, not40-45/60. Private-timer/release-phase controls also did
not meet target. Capture attempts incorrectly consumed cap60; opt-in corrected
frame pacer exists, but first warm capture22.5-24.2FPS did not prove a gain.
See WINDOWS_FRAME_PACING_2026-10-12.md.

The next distinct implementation is capture on its own thread while prior
I420 is encoded, using existing bounded latest-frame queue. Prepared on PC
only, NOT staged: `build-logs/windows-async-worker-verified-20261012`.
Raw binary BB6E74164BCB24FA45CFB942D618BEEDF7F1EA8ED2923DA72EA94246BA5F5E76.
Strict ARM64 build PASS; actual host queue/worker join+ownership/error tests,
async staging mocks, frame-pacer and UVF1/color tests PASS. No combined device
performance proof yet. Snapshot publication prevents main-thread reads of
live capture state; pacing has a separately owned timer. This is not approval
to promote the candidate or infer60FPS. The old overlap experiment used the
old blocking source/cap; do not repeat it verbatim.

## IddCx

Microsoft's UMDF sample gives an actual compositor swapchain and new-frame
event: SetDevice -> ReleaseAndAcquireBuffer -> process -> FinishedProcessingFrame.
This avoids Desktop Duplication acquisition, but still delivers a DirectX
surface. CPU x264 can still require staging/readback, so eliminating Map wait
is NOT guaranteed. SharedTexture is not shared Windows/Android memory.

Existing PC-only ARM64 MttVDD24.12.24 package was already audited10-09:
AA64 DLL/NTARM64 UMDF/IddCx; Authenticode/catalog membership PASS, /kp
Microsoft-root check FAIL. Guest UMDF trust/admission remains UNKNOWN; /kp
alone is not a conclusive UMDF installation verdict. Binary package also does
not expose our custom x264 frame handoff. A generic virtual-monitor install
would still leave capture integration unresolved. No certificate imports,
test-signing, driver install or monitor replacement performed.

Decision: architectural GO for later isolated prototype, NO-GO as today's
drop-in60FPS fix without exact guest admission and CPU frame access proof.
No large driver implementation before those gates.

## Frequency / interpretation

64Hz advertised mode is not unique presented-image throughput. Warm paint
callbacks around62/s do not prove62 complete DWM frames. The cadence control
observed DXGI accumulated45.95/s and unique acquisitions27.71/s; DWM displayed
counters stayed0. Actual unique Windows Present rate remains UNPROVEN.
Do not relabel callback/nominal-refresh metrics as real frames or VSync cause.

## Primary sources

- https://learn.microsoft.com/en-us/windows-hardware/drivers/display/microsoft-basic-display-driver
- https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/security-issues-for-section-objects-and-views
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-mmmapiospace
- https://learn.microsoft.com/en-us/windows-hardware/drivers/display/indirect-display-driver-model-overview
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/iddcx/nf-iddcx-iddcxswapchainreleaseandacquirebuffer
- https://raw.githubusercontent.com/microsoft/Windows-driver-samples/main/video/IndirectDisplay/IddSampleDriver/Driver.cpp

Read-only observer now prepared/selftested: `ram-framebuffer-audit.py` accepts
only one fresh EBS1920x1200/mode0/aligned-range marker, samples eight pixels
(32B) at most twice, and rejects changed geometry or third observations.
Actual mock tests and observer Python compile PASS. It uses the existing exact
own debugger break with verified resume; no framebuffer/memory writes.
First runtime `ram-fb-readonly` reached8CPU/barrier0 but observer's temporary
`module` name collided with an existing PE-address integer before any pixel
read. Fixed to distinct `ram_module`; actual AST-init/helper test now proves
it survives an integer named `module`, and73 admission cases PASS. All four
cold rollback/readbacks PASS. Repeat `ram-fb-readonly-fixed` subsequently
completed PASS as recorded below. Uses original
native cadence capture/codec control, not the prepared worker prototype.
After observation all existing four cold rollbacks must complete before boot.

## Actual fresh device result — RAM pixel activity PASS

`kd-windows-direct-x264-20261012-ram-fb-readonly-fixed/events.jsonl` proves
native startup8/barrier0, followed by current source Default/WinSta0 with
active bounded full repaint. Two exact KD break observations used fresh
EBS baseFF700000. At host monotonic39771.267345 eight BGRA samples were
4880b7ff; at39776.3759781 all eight were95806aff. Each sample matched the
actual workload color invariant (green128, red+blue255), and all8 changed
between resumed executions. Actual RAM read status0 for every4-byte read.
Only64 bytes total, no framebuffer writes. Both own break contexts resumed
with verified PC-only advance of the exact debugger BRK.

This confirms that the reserved UEFI RAM framebuffer contains current
post-login Windows output at the sampled locations. It is not stale firmware
pixels. It does NOT prove complete-frame coherence,60 uniqueFPS, bandwidth,
an app/user-mode mapping or a working replacement capture backend.

Decision updated: GO to investigate a narrow sanctioned guest reader/access
path and its cost. Do not implement streaming through KD, arbitrary RAM
mapping IOCTLs, vulnerable drivers or security-policy bypasses. Direct
physical user-mode access is still absent; a properly admitted signed guest
helper would be a separate implementation/admission gate. The common x264/
vsock/MediaCodec/Surface path stays intact.

Runtime completed its two observations and stopped normally. GOP/socket/
encoder/final112-block codec cold rollback/readbacks ALL PASS, runner terminal
exit0, VM list empty. Stable disk files restored. No active experiment left.

Latest reader ownership/admission gate:
WINDOWS_RAM_READER_ADMISSION_2026-10-12.md. Documented mapping alone is not
an admitted/safe reader; Windows type/cache/signing remain unproven.
