# Active display task: WGC source feasibility

User priority supersedes clipboard work. Clipboard frozen; RAM remains NO-GO
at safe mapping gate. No more GDI/Desktop Duplication/staging tuning. Full
Windows product goal remains incomplete; native1200p/8CPU display~26FPS is
the existing fallback, not60FPS.

Prior clipboard VM stopped. All four cold readback rollbacks PASS, including
112 codec blocks; previous diagnostic APK restored. No VM currently running.

Prepared official-ABI ARM64 C probe, no new driver or DLL dependency. Built
with Windows SDK WinRT headers and existing LLVM toolchain, fits own encoder
slot after stripping. Source: tools/windows-reassessment/windows-wgc-probe.c.
Reproducible builder: build-windows-wgc-probe.ps1. New large artifacts on E.

Probe: IsSupported -> CreateForMonitor -> D3D11/WinRT device ->
CreateFreeThreaded(3 buffers) -> FrameArrived -> bounded latest-frame mailbox.
Thirty-second existing fullscreen moving workload; measures event arrivals,
distinct SystemRelativeTime/deltas, readback frames, full BGRA copy cost,
coalesced frames and synthetic center-color changes. Pixel sample is explicitly
NOT a full-frame hash. No x264/vsock/Android FPS PASS until an actual stream
is integrated and measured. Device format/resolution checked before copying.
HARDWARE device creation failure permits WARP device only for WGC feasibility;
this never implies Windows GPU acceleration or changes its display driver.

Next: embed this bounded probe ahead of existing diagnostic encoder startup,
save own report rather than putting text into UVF1 stdout; preserve normal
encoder after probe. One current8CPU launch, source-only gate first. If >=58-60
real image updates and readback budget allow, connect WGC frames to existing
x264/common Android path. If unavailable or~25-30FPS, stop WGC tuning and
prepare isolated official IddCx feasibility/admission (no unapproved driver
installation). Swapchain runtime frames cannot be proven solely by building
an uninstalled IddCx sample.

## First runtime result

One current8CPU/native1200p app-owned launch (host CID2281) reached Desktop.
Actual current producer6712 / LIVE-SERVER-6712-128406 header CID2281 confirmed
artifact attribution. Own WGC source report: WGC_SUPPORT hr=80070424 supported=0.
No capture frames/30-second performance result; this is NOT a30FPS ceiling.
Artifact: E:/U-AVF/artifacts/windows-wgc-encoder-20261013/live-current-producer6712/LIVE-SERVER-6712-128406/LIVE-1/wgc-source-report.txt.

Read-only guest console checks executed: sc qc CaptureService succeeds (type60,
DEMAND_START, RpcSs), and sc query lists CaptureService_3bbd8. Therefore missing
service HRESULT must not be interpreted as absent service template globally.
Evidence screenshot: capture-service-query.png in the same E build directory.
Source windows-display-service.c duplicates its SYSTEM process token and changes
TokenSessionId to1 for the agent; child producer inherits it. This is not the
normal logged-on user's token. Strong next narrow hypothesis: per-user service
activation under that synthetic SYSTEM/session1 context.

Prepared explicit --wgc-probe-only mode for the same owned encoder, executed
from real user's console into a fresh own TEMP directory: no codec, UVF1 or
vsock writes. Reports token category/authentication LUID and activation HRESULT.
Normal-user test not executed yet: first attempt hit the600s observer deadline
and input channel was already closed. No command execution claim.
All four cold rollback/readbacks then completed PASS; observer/runner exit0,
VM list empty. Corrected caller probe built on E in windows-wgc-caller-probe-20261013,
not staged on the tablet.
Next one corrected-context launch, not a platform/driver experiment; if WGC is
then unavailable or below the source gate, proceed to isolated IddCx admission.

Corrected-context run started: runtime-wgc-user / kd-windows-direct-x264-20261012-wgc-user,
900s observer bound (stop earlier once the report is obtained). Explicit-only
candidate does NOT invoke WGC from normal display startup; original display
loop remains intact. Source-only command helper prepared ahead of time:
start-installed-windows-wgc-user-probe.ps1 (requires visibly open Run dialog).
It starts only our helper from authenticated user's shell, holds the process
handle, waits at most40s, kills only that process on timeout and displays the
actual report. Input-written acknowledgement alone is NOT a test PASS.
Candidate E:/U-AVF/artifacts/windows-wgc-user-only-20261013/; own-slot padded
SHA5FB45EB555E815947E03266ED4158C265806961D1A7D0044A9C15DD1DE16C282.
No normal-user runtime result yet. Always await four cold rollbacks after run.

Corrected-context CID2282 completed: actual password accepted and Desktop/Run
visible. The long EncodedCommand printed PowerShell help, NOT a WGC report.
Input channel-written ACK is not guest execution. Subsequent short console
command was not visually confirmed before the900s observer closed the VM.
No normal-user WGC conclusion or FPS inferred. All four cold rollbacks and
readbacks PASS, runner exit0, VM[]. Exact own TEMP report cold lookup hit
BrokenIndexError; this is unavailable evidence, not a negative WGC result.
Run helper now uses a fresh own TEMP directory and a <255-character cmd
command, avoiding the long Run payload. One delivery-corrected run wgc-short
started; same candidate/platform, no new capture/backend tuning.

Primary sources:
- https://learn.microsoft.com/en-us/windows/apps/develop/media-authoring-processing/screen-capture
- https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createformonitor

## Final actual normal-user source result — CID2283

Executed from visibly focused uavf console: winpe-encode.exe . --wgc-probe-only,
then type wgc-source-report.txt. Complex Unicode punctuation/digits in earlier
console command produced malformed VT text; earlier help was not reliable
evidence of a Run-length limit. Simple source-only command actually completed.
Own report captured on screen and recovered by bounded cold read:
E:/U-AVF/artifacts/windows-wgc-user-only-20261013/cold-user-result-2283/wgc-user-source-report.txt.
Screenshot: user-wgc-result-2283.png in the same artifact root.

- Caller local_system=0, identity/statistics valid, authentication LUID6cc85.
- Activation00000000, IsSupported true, D3D11 creation00000000. This does NOT
  prove GPU acceleration: installed adapter remains BasicDisplay/BasicRender.
- Native1920x1200, CreateForMonitor/CreateFreeThreaded3 buffers/FrameArrived.
- elapsed30000ms;883 arrivals and883 distinct SystemRelativeTime values.
-876 reads=29.200FPS;846 center-color changes;7 coalesced frames.
- Map18.050ms/frame; row copy1.342ms/frame.
- source timestamp delta mean33.943ms, min15.625ms, max3031.250ms.
- workload1790 paints~59.67/s; paint callbacks are NOT unique DWM frames.
- Center sample changes are NOT whole-image hash or Android present FPS.

Decision: source gate FAIL for >=58FPS. No WGC->encoder/Android FPS claim;
do not optimize this branch further. SYSTEM activation failure was caller
context, not absent WGC support. Source readback has similar cost to DXGI,
without asserting VSync or a permanent architectural ceiling.
Next gate: official ARM64 IddCx swapchain/admission proof, not generic VDD
installation or a fourth Desktop Duplication/readback variation.
