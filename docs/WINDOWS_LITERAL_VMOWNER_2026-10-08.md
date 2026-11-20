# Literal shell launcher port into APK :vmowner

User-approved route: preserve working shell VM construction/start, only
Context/UID/private paths/service lifecycle/necessary app FDs change.
Isolation: separate existing Windows diagnostic APK; production U-AVF APK,
Ubuntu and Windows original baseline were not changed or published.

## Implemented

- Foreground Service `WindowsVmOwnerService`, process `:vmowner`, same APK UID
  as diagnostic viewport. Explicit token and same-UID Binder checks.
- Literal custom/config builder sequence -> toVsRawConfig -> inputDevices=[]
  -> rawConfig union -> VirtualizationService -> IVirtualizationService.createVm
  -> IVirtualMachine.start. No Manager.create/run/debug/capture flags.
- Static normalized source block comparison against AvfOwnerBridge PASS.
  Reference source SHA C6FF99BBEF420B450F69E260AE43DCD87C48A1A47C3330EBE78527212516474C.
- Verified exact private Windows candidate1E9D...8E4BE and wrapper93ED...95F234.
  Private copy overwrites only disposable never-installed Windows test image.
- Existing observer applies the same CAM/subpage/periodic CNTV fixes in same
  order. No signed Windows userland patch, CPU/PMU/PCI/timer new flag.
- Driver-load readiness gates same-UID owner connectVsock4055; main process
  retains shared EncodedDisplayProbe / MediaCodec / Surface / UIN1 input.
  This future path is implemented but NOT yet exercised at WinPE.
- Exact shell native ingress/tailer binaries bundled as executable native APK
  libraries, no recompilation or alternative pacing. TCP adapter only converts
  Base64 records. App service retains VM across viewport lifetime; shutdown on
  authenticated observer EOF/destroy during these bounded tests.

## Observed stages

1. Raw createVm initially returned RPC DEAD_OBJECT before any VM/Windows.
   Log FD was the remaining external-storage descriptor; after moving it to
   private storage, creation/start succeeded. No claim of exact denied rule.
2. CID2114 with Java UART adapter reached kernel but lost complete KD responses
   at FLTMGR load. Stopped; no WinPE PASS.
3. CID2115 with exact native helpers reached kernel and verified previous
   guarded fixes, then no complete KD response after probe installation.
   Own idle break-in got repeated ntoskrnl load state, not SIGILL evidence.

Current APK SHA042E72A334AC469056679FE64944EBBBEE42BA56F33A736D20C9747CD9A24720.
Reports/raw config/events retained in both runtime directories under parent
build-logs/windows-post-linux-reassessment-20261004.

Machine-readable `shell-app-effective-argv-diff.json` beside CID2115 events:
expanded arguments equal after ignoring only PID/PPID/CID; FD indices match;
same workaround order. This is NOT proof of every live CPU register/mapping.
Pre-start raw JSON covers public fields but opaque FD/union values were `{}`;
do not misrepresent it as a fully decoded semantic snapshot of every object.
Kernel/wrapper/disk identities are independently checked, CPU/PMU/PCI/balloon/
serial expansion present in exact crosvm command.

Independent byte comparisons before observer shutdown:
host TX22458bytes == native decoded ingress22458bytes;
host RX72691bytes == serial file72691bytes. No byte loss detected.
Native helper stderr empty. CID2115 did NOT reach viosock readiness, so no
encoded-display report or Surface image exists for this attempt. Old report
was renamed before launch, not counted as fresh data.

## Current boundary

App-owned create/start/kernel now works, but WinPE/Setup and app-owned AVF
stream remain unproved. Do not declare SIGILL fixed merely because this attempt
stalled earlier. Current evidence cannot distinguish early kernel execution
stall from KD state-machine/liveness failure. No semantic argv delta exists to
fix, and no random UART/CPU/platform sweep should follow this result.
Both observers terminal; VM[] verified; no installation performed.
Future permitted work must identify a concrete state/implementation difference
from retained evidence, not repeat these launches blindly. Goal remains full
Setup/display/input/installation, not success at createVm.

## Retained wire boundary audit (no new VM)

`audit-kd-boundary.py` checks actual KD leader/length/trailer/checksum and decodes
the final records, not just text events. CID2115 TX22458bytes/473packets and
RX72691bytes/474packets. Final host command at TX offset22369 is Continue
API0x3136 / status0x10002 / packetID0x80800001; guest control ACK with the same
packetID occurs at RX offset72675. Previous Continue at TX22279 used ID0; guest
then reported/repeated a LoadSymbols state (0x3031) twice with ID1, and host
acknowledged it and issued the final Continue1. No valid later exception or
WinPE frame is present. Thus the final Continue was NOT simply missing from
the wire: Windows acknowledged it. This narrows the boundary beyond transport
delivery but does not prove whether kernel execution or subsequent KD state
handling stalled. Do not respond with more UART pacing/encoding experiments.
