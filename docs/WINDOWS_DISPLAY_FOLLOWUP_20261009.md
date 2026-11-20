# Installed Windows display follow-up — 2026-10-09

## Proven, not inferred

- Final update, 22:39: battery reached4%; tablet testing stopped as requested.
  CID2168 did eventually show the real installed Explorer/bamboo desktop at
  22:34, then a Task Manager window at22:36–22:39 (content still unpainted).
  Thus earlier black frames were temporary during exceptionally slow startup,
  not proof of permanently broken GDI capture. No demonstrated cause/fix for
  this slowdown; recovery cannot be attributed to observer detach.
  Diagnostic Stop Windows VM button was used; authoritative VM list became[].
  This was an app-side stop, NOT a proven clean guest shutdown. Installed disk
  retained, no reinstall, driver installation, account or Ubuntu changes.
  Next charged-device test: bounded source/process/clock observation, not another
  blind display-backend change. Prepared encoder remains unstaged.

- CID2167: native login/Explorer desktop, roughly12–13FPS640x480, working
  Win+R/Alt+Tab/touch/keyboard. Native shutdown reached VMs[] and SCM stop.
- CID2168: same platform/CPU/disk except own broker executable update. Native
  SCM log confirms exact legacy UAVFDisplay Run value retired successfully.
  Winlogon H.264 frames rendered in the shared Android receiver.
- Password accepted using ordinary Android keyboard events forwarded by the
  existing guest adapter; Welcome appeared and SCM created Default producer4832.
  Diagnostic Unicode text submission did not reliably fill the password field;
  channel_written is not proof of delivered text. Do not change the password.
- Actual Default stream produced black640x480 frames. Guest report for PID4832
  generation1:118captures/117encoded/443813guest-ms, capture+conversion1591.816ms,
  encode2170.227ms. Vsock report:116sent frames, INPUT_OK6, INPUT_REJECTED0.
  Android queue0, receive-to-render callback roughly10–16ms. These clocks are
  guest clocks, not trusted wall-clock latency. Old prior Desktop PASS remains
  valid; desktop later appeared, but cold-boot performance regression is NOT fixed.
- This boot was slow before broker startup (SCM360843guest-ms versus41500 in
  prior boot). Windows logged SystemEventsBroker timeout and later WER/CrashAPI
  ReadProcessMemory/PEB errors. Do not assume the broker Run removal caused all
  slowdown, or that WER identifies the crashing application/instruction.

## Two bounded same-VM checks

1. Recreated only diagnostic Activity/decoder and explicitly requested display
   start; CID2168 and owner5997 unchanged. New vsock generation connected;
   black/slow output persisted. No VM reboot, config, disk or production change.
2. Detached only the exact known observer Python process after current PixelCopy
   retention guard passed. Diagnostic service retained VM; runner exit1 is the
   explicit observer termination, not Windows exit. Initial follow-up still
   slow/black. No strong evidence that idle host observer explains the regression.

## Prepared next observation, not installed

### 2026-10-10 continuation

The 10 October observation revision is now installed, unlike the older build
below. Strict ARM64 build PASS; raw binary SHA256
A1F5048CF1668327015BED0E55345C5D2418CB2B2EBDFAB1A9941305D93F6E5A.
Adds separate GDI and I420 conversion timings, QPC counter, wall FILETIME and
GetProcessTimes kernel/user CPU totals. These are guest measurements, not
independent proof of correct guest clock speed; correlate with host timestamps.
Default cap15, GDI source, OpenH264 and UVF1/vsock protocol unchanged.

Cold read backed up the exact existing own encoder before preparing a same-size
79872byte slot. Original SHA256
9ec8018fe3bec5ea04382f99d3ad59d9af3f69d52b74bc0b75b53b8fd5804ca1;
new padded SHA256 b26fcfb5496cf32fd5f74db2829f68a0628b54409e0ade19b56cb5c42c97805e.
Guarded encoder-only apply/full readback PASS (156 changed sectors, two ranges).
No NTFS metadata, registry, kernel, platform or user data writes. Original
backup and rollback inputs retained in encoder-observation-prepared-20261010
and encoder-observation-applied-20261010.

Battery100%, VM initial state[]; new single boot CID2170 owner32099.
Observer exec13429 is live and new driver events are arriving after win32k/cdd.
No fresh source metrics/desktop proof yet. Do not restart on display connection
errors while native service has not started. Evidence directory:
kd-windows-source-observation-runtime-20261010. On-device reserve guard52GiB
targets exact owner32099; production Ubuntu remains untouched.

- winpe-encode.c now reports actual session/desktop/window station/window count,
  QPC frequency and stage before encoder init/after init/GDI ready/5s intervals.
  No titles, input contents or credentials. Existing15FPS default, UVF1,
  OpenH264/vsock/Android architecture unchanged. Strict ARM64 compilation PASS.
- Binary display-source-observation-20261009/winpe-encode-observation.exe SHA256
  D6FB4330A0B2F0F627B703030ED5598AF814204B0840E87F49089F5F8ED40384.
  NOT staged to installed root. Next use a guarded own encoder-only slot update,
  after cold shutdown and backup/readback, not a Windows reinstall/hive rollback.
- Read-only snapshot helper now supports an exact observed producer PID and
  bounded WER summaries. Live NTFS reads are non-coherent; absent summaries are
  not proof that no crash occurred. PID can repeat across boots: trust matching
  CID and fresh markers, not folder name alone.

## 1200p candidate preflight only

- Actual Settings resolution640x480 disabled. Effective current VM has no host
  GPU/display config. Need native adapter/mode inventory before choosing change.
- Downloaded author release ARM64 virtual monitor package24.12.24 on PC only:
  ZIP SHA2561D3744E474C7513B24B465B5D10FB39BACB8023B6252F9E90921DCB8AB8A0C99;
  DLL machineAA64, INF NTARM64/UMDF/IddCx. Catalog Authenticode and /pa catalog
  membership checks PASS (SignPath Foundation). /kp fails Microsoft-root chain.
  Guest install/trust policy and renderer compatibility NOT yet proven. No
  certificate import, test-signing, policy weakening or driver installation.
- Primary references: [author release](https://github.com/VirtualDrivers/Virtual-Display-Driver/releases/tag/24.12.24),
  [Microsoft IDD sample](https://github.com/microsoft/Windows-driver-samples/blob/main/video/IndirectDisplay/IddSampleDriver/Driver.cpp).
  IDD is a possible monitor/mode solution, not proof of Mali acceleration/60FPS.

## Remaining focus

Investigate the severe startup/runtime slowdown using prepared native source
inventory and process/clock evidence; real desktop capture eventually works. No
random CPU/PMU/timer flags or new display architecture. Preserve earlier good
Desktop artifacts, installed account/root and all Ubuntu data. No commit/push.
User requested ending tablet tests at3–4% battery; at22:30battery5%, still draining.
