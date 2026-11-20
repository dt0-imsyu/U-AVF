# Windows ARM64 reassessment after Linux breakthrough — 2026-10-04

## Decision

Windows kernel entry and complete early interrupt initialization are now PROVEN
in the isolated diagnostic VM. The old 0x5C failure is localized and bypassed
by a platform-state fix: restore GICD_CTLR.DS after firmware GIC shutdown at EBS.
Control: kernel reads CTLR=0, GIC3 initialization fails C0000184 and causes
0x5C `0x200,0x05250631,2,0xffffffffc000000e`. Fixed: kernel reads CTLR=0x40,
GIC3 initialization returns SUCCESS and HalpInitializeInterrupts returns SUCCESS.
WinPE/Setup is NOT PASS. Do not claim a complete Windows repair or equivalence
to an unchanged production launch.
One new, instruction-backed MPAM hypothesis was tested in a diskless VM and
did not reproduce a fault. No Windows baseline, Ubuntu workspace, APK,
production graphics, firmware or ISO was modified; no commit/push.

The previous observer-only pause and UNKNOWN kernel-entry conclusion are
superseded. Kernel/HAL fault localization is now possible without vendor trace.

## Post-fix boundary — KD refresh is not a demonstrated hang

### Cache-path refinement (latest, supersedes the narrower guesses below)

`kd-native-cache-private-runtime` reads partition-mode byte c561e4 = 1.
CcCreatePrivateVolumeCacheMap allocations succeed; all guarded initialization
checkpoints pass, including the partition refcount atomic, physical-page query,
PsCreateSystemThread (status 0), NUMA initialization and quick-LWS setup.
CcInitializePrivateVolumeCacheMap returns Boolean 1 at 63f83c.
RX SHA256 `D24AC6064CB8A1D0586734DDA6A09906E360701B74AD9AB9D39CB68971F57F4D`.

`kd-native-cache-insert-runtime` passes the partition queued-spinlock acquisition
and CcInsertPrivateVolumeCacheMap. The list already contains a matching map;
the function returns Boolean 0 (existing-map case, NOT NTSTATUS failure).
Partition lock and global lock are zero afterwards. CcCreatePrivateVolumeCacheMap
then follows the cleanup of its unused newly-created map at 63f898/63f8a8.
Thus the bounded stop is NOT the allocation, initialization or insertion lock.
RX SHA256 `651882F2349AA9575B06B4343D2BA95A222C7B2AFE5E2C8AB1F2E9F62E333CB8`.
The focused final diagnostic observes CcDeletePrivateVolumeCacheMap, especially
its shutdown events and ZwWaitForSingleObject on the worker-thread handle.
No driver, CPU-feature or timer changes are justified by these observations.

`kd-native-cache-delete-runtime` is INVALID for the deletion boundary: the
observer attempted to dereference context X18 as PCR, but it was not a validated
kernel address. KD returned C0000001 on ReadVirtualMemory; the diagnostic VM
was stopped safely. This is an observer fault, not a Windows bugcheck. The
invalid read was removed before repeating the otherwise identical experiment.

`kd-native-cache-delete-validated-runtime` passes both partition lock operations,
enters CcDeletePrivateVolumeCacheMap, sets its two shutdown events (both signal
states 1), and returns from ZwWaitForSingleObject at 4c3604 with status 0.
ZwClose also succeeds, the worker handle becomes zero, and 4c361c is reached.
The map's QWORD reference count at +8 is 4 rather than the required 1.
Static code takes the loop at 4c362c calling KeDelayExecutionThread at 4c3638;
the after-delay checkpoint 4c363c is not observed within 30 seconds.
Worker entry/wait-return checkpoints also fire, but the observed entry has a
different map pointer; do NOT attribute those hits to this deleted map's worker.
The successful wait on this map's actual thread handle rules out waiting for
that worker's termination as the immediate stop. Worker-exit checkpoint is not
observed. No CLOCK_IRQ/BUGCHECK checkpoint was hit.
RX SHA256 `86D036A88E22DECF9853617DEAE134C11A66AB5BB3C26404BF988BEBE3C6D63A`.

Current remaining question is why that delay does not resume and outstanding
cache references do not drain. Timer expiry/interrupt delivery, scheduler
wait/wakeup and reference-release path must be distinguished; do not assert
the known CNTP bug is responsible from absence of these hits. The next useful
diagnostic is the exact KeDelay wait object/due time and Windows timer programming,
not disabling ReFS, faking the reference count, or changing unrelated ACPI flags.
No new platform fix is proven in these cache traces; WinPE still NOT PASS.

`kd-native-cache-delay-runtime` observes the exact requested interval: -50
100-nanosecond units = 5 microseconds, kernel mode, non-alertable. KiRcuCheckQuiescent
and KiBeginThreadWait return; thread lock is zero. KiCommitThreadWait returns
STATUS_TIMEOUT (102), then KeDelayExecutionThread returns SUCCESS at 4c363c.
The precomputed deadline was sampled before multiple debugger pauses, so this
is observer-perturbed expiry, NOT proof that timer IRQ or natural wakeup works.
No driver return/WinPE milestone follows. RX SHA256
`6D35815269E1AC255DE2786166519D2B06711EF7FB1599DCC583BDE7B657187F`.
The necessary control removes all in-delay stops and samples only after-return
at 4c363c, bounded to eight iterations. It must not be confused with the
detailed trace or described as a timer fix.

`kd-native-cache-delay-sparse-runtime` makes no stops inside KeDelayExecutionThread
and sets only its caller's after-return/cleanup-complete probes. Neither fires
within 30 seconds of the ReFSv1 entry. RX SHA256
`2783084B437C3F6CDCDAC09FD6FFB5E204E36CD58AF09A36865AEA8E811E670B`.
This contrasts with the internally paused trace: natural timed wait remains
unproven, and debugger-assisted timeout is not a repair. Next directly observe
the exact CNTV_CTL/CNTV_CVAL write paths and clock interrupt handler; no CNTP
workaround or CPU/ACPI flag sweep is warranted.

`kd-native-clock-program-runtime` is NOT a valid repeated-write/loop-count
measurement. The new repeated probe rearmed at the stopped PC before resume,
so it retrapped the same instruction rather than sampling distinct executions.
Its first pre-write intents and first delay-return hit are real, but eight
samples must NOT be called eight MSR writes or eight delay iterations. Fixed
by alternating pre/post-write checkpoints and caller loop/return checkpoints;
the corrected experiment uses a separate evidence directory.

### Diagnostic-channel recovery (current)

The independent `trace-kd-boot-drivers.py` now negotiates bootloader-to-kernel
KD epochs without DbgEng. `kd-native-driver-validated-parser-runtime` proves
GetVersion in ntoskrnl and five guarded software probes installed; the first
probe at `HalpInitializeInterrupts+0x474` fires. This is observer recovery,
not a new milestone beyond the already-proven interrupt initialization.

An observer stop on RestoreBreakpoint C0000001 was localized by the matching
kernel: `KdpSetCommonState` (RVA 4f8168) snapshots up to 16 bytes at PC and calls
`KdpDeleteBreakpointRange` (aa19a8), removing the hit probe before reporting
the exception. The client verifies original instruction and zero flags
in the exact `KdpBreakpointTable` entry, rather than treating repeat deletion
as a guest failure. Guarded correction PASS in `kd-native-driver-autoremove-runtime`:
52 DriverEntry calls, 51 returns. ACPI, PCI, NTFS, ReFS and DXGKrnl all return
SUCCESS. sacdrv returns C000025E and boot continues; this is not the stop.
The latest unreturned call is ReFSv1 at its exact image entry RVA ea000.
No return/bugcheck/IoInitSystem completion is observed within 30 seconds.
This is not yet a demonstrated ReFS defect. One targeted checkpoint trace
inside that signed, unchanged driver is the next diagnostic.

RX evidence SHA256:
`FE671F3826239B678D8855204F03818298E362E44FEE533C65F0E328B573E2C9`.
The run proves actual DriverEntry return statuses beyond the previous KD-refresh
boundary, rather than merely module notifications. No WinPE desktop is proven.

### ReFSv1 boundary refinement

`kd-native-refsv1-checkpoints-runtime`: all six checkpoints fire through ea094,
ea248, ea350, ea9d8, eab78, eac40. `kd-native-refsv1-tail-runtime`: allocation
returns at eac58, but RefsInitialize does not reach eae04. One normal KD break-in
after 30 seconds receives no state within five seconds; this does not prove a
crash or identify a blocked PC. RX SHA256s respectively
`549A7A9057D332019D2454FDEA9AEECEF7F2A0B603AF33D116238BF7691D8941` and
`351E6A8D8341D1128E0E733F70FCAF97AFBA44834EE5E0127E8201D2F6A6E690`.

`kd-native-refsv1-library-runtime` passes all three checksum LoadLibrary calls
(56d58/56d8c/56dbc, status0), CmsKeyRules and later library setup. Last checkpoint
is 2f618, immediately before MspInitializeLazyWriter (2ff80); 2f628 after it is
not hit in 30 seconds. The localized remaining interval is that function, not
CRC/ISA, DXGKrnl, PCI DriverEntry or the original GIC discovery failure.
Do not claim scheduler/interrupt failure yet: creation of its worker thread and
its following callback must be distinguished. The same disk/platform/one-CPU
contract is unchanged across these narrowing traces; only guarded KD probes differ.

`kd-native-refsv1-lazy-runtime` further proves PsCreateSystemThread SUCCESS at
300b0 and ZwClose SUCCESS at 300c4. The next indirect call at 300e0 targets
the matching kernel's `CcRegisterExternalCache` (RVA 4c1160); no return at
30104 is observed. Thus this is NOT a demonstrated thread-creation failure.
RX SHA256 `61822D0493407677ABDCD5CD8693066A98C5E86CAB132C1A6BFB82AF597BD44A`.
Current focused diagnostic observes cache initialization/lock state and direct
KeBugCheck2, which earlier KeBugCheckEx-only observation could miss. Do not
disable ReFS or modify CPU/timer flags on this interval alone.

`kd-native-cache-runtime` reaches CcRegisterExternalCache+14 and +48.
Cc initialization DWORD at c561b4 is 1, external-cache spinlock at dd28e0
is zero, and pool allocation has returned a non-null address. The following
checkpoint before CcAddExternalCacheInternal (4c11f8) is NOT observed.
No direct KeBugCheck2 hit or HalpTimerClockInterrupt hit is observed in this
bounded trace; absence is not proof of either no bugcheck or no timer delivery.
The most useful next step is the c561e4 branch flag and guarded checkpoints
in CcCreatePrivateVolumeCacheMap (22a9c0), the alternate call between those
two confirmed PCs. This is a narrowed cache/memory initialization interval,
not proof of a damaged ReFS filesystem or unavailable privileged tracing.
RX 298522 bytes, SHA256
`53E0A6FD936CDFD202D1E2AFF605D368062785A4A9B8D8B1B17CF0E6FECEAD70`.
All diagnostic VMs are stopped. Original candidate rehashes to 87CD1D44...DB35;
Ubuntu, P7, production APK/display/storage, Windows baseline remain untouched.

Additional observer defects corrected: firmware ASCII `0000` must not be
accepted as a KD header without kind/size/packet-ID validation; bootloader
PEER_SYNC must negotiate a fresh packet epoch; KD file-override requests
receive the retained debugger's normal not-found reply, keeping guest files.
`kd-native-driver-sync-runtime` is INVALID: staging overlapped its start and
U-Boot could not read the partition. An exact staged SHA barrier now prevents
starting a VM on an unfinished/modified candidate. Do not count it as a
Windows failure or repeat that experiment.

Pacing 8 bytes / 1 ms did not resolve initial DbgEng synchronization. The
device-side decoded TX snapshot is byte-identical to all 8957 host TX bytes;
ADB/Base64 corruption is excluded for that snapshot, not for the UART endpoint.
`uart-module-break-runtime` (-d rather than -b) also failed before kernel entry.

The small independent `probe-kd-version.py` succeeds without DbgEng: a single
GetVersion (0x3146) receives SUCCESS; then ReadVirtualMemory (0x3130) at the
reported PsLoadedModuleList receives SUCCESS with exactly 1 and 16 data bytes.
Evidence: `kd-minimal-version-runtime` and `kd-minimal-read-runtime`.
This excludes an inherently unreadable module-list address and demonstrates
functional bidirectional KD. It does not prove sustained DbgEng reliability.

Code audit found lossy startup gating: host KD bytes were discarded before the
Windows marker and the marker-containing RX chunk was discarded in full.
The bridge now waits rather than drops TX, and forwards bytes after the marker
even when a KD packet shares that chunk. C# compilation PASS; current
`uart-lossless-gate-runtime` verification with the known chunked ingress and
unchanged DS/EBS candidate still failed to read PsLoadedModuleList before kernel
commands. The marker/suffix unit test passes for splits 1/5/12/26; this is a
valid bridge defect fix, NOT a demonstrated fix for DbgEng synchronization.
All diagnostic VMs/observers from these tests are stopped. No new progress past
the already-proven Phase1/IoInitSystem entry was achieved in this batch.

Next narrow path: use the independent KD client (GetVersion + virtual reads
already working) to follow load-state/continue exchanges and install exact
kernel boot-driver entry/return probes, bypassing unstable DbgEng initialization.
Do not repeat pacing/-b/-d tests or declare privileged vendor trace necessary.

`uart-kd-refresh-runtime/kd.log.txt` repeats the HAL completion PASS and proves
`Phase1Initialization` and `IoInitSystem` entry on a system thread. An exact
string xref in the matching kernel maps "KDTARGET: Refreshing KD connection"
to `KdRefreshDebuggerNotPresent` (RVA 0x4f7f40). Live entry/return breakpoints
show its caller is `watchdog!WdIsDebuggerPresent` from `dxgkrnl!TdrInit`, during
`dxgkrnl!DriverEntry`. The function returns x0=0; subsequent silence does NOT
prove a KD-refresh deadlock, a bugcheck, or a dropped serial connection.

Final serial: `kd-refresh-final-serial.bin`, 533917 bytes, SHA256
`236AACFEEC8F1DB5CB0B32DD96C82D82A4ACE21C3F9501DC7889AC0ED03EF8B6`.
The same disposable candidate/FD/BCD/kernel is used; Windows and production
Ubuntu files are not patched. Next diagnostic: exact boot-driver entry/return
and IoInitSystem completion, rather than changing platform flags.

The first boot-driver observer attempt (`uart-boot-driver-runtime`) is INVALID
for this comparison: KD reported Unable to read PsLoadedModuleList and entered
RESEND/RESET cycling at winload, before kernel commands ran. Its separate serial
is preserved as `boot-driver-invalid-observer-serial.bin`. The verified shell
VM requester was terminated; this is an observer failure, not a Windows result.

One retry (`uart-boot-driver-retry-runtime`) reproduced that same initial
observer failure. No driver-entry breakpoint ran in either attempt. Stop
repeating Windows boots for this script until KD transport is corrected:
validated host RestoreBreakpoint (0x3135) packets are followed by target RESEND (5),
host/target RESET (6), and repeated winload state 0x3031; this differs from the
previous successful final Continue/ACK sequence. Host-side packet integrity
alone does not establish byte-exact guest reception. The next required step is
diagnostic-channel recovery, not ACPI/CPU/timer changes or a vendor-only claim.
Both invalid observers and their shell-owned VMs have been stopped. No WinPE
desktop, completed dxgkrnl DriverEntry, or completed IoInitSystem is proven.

Important protocol correction: 0x3135 is RestoreBreakpoint, NOT GetVersion;
GetVersion is 0x3146 (also present in the earlier successful trace). The resend
loop may be downstream of initial debugger setup failure. Do not misidentify
the failing exchange as a kernel version query.

## Latest A/B outcome — actual local fix verified

`gas8-gic-ds-ebs` candidate SHA
`87CD1D4486EEB0C29AB6FBA691F73B3B4C8AE5E0538532BDE14364311A34DB35`.
Shim SHA `58DADE952C7DA4D65ADD4516B37F68C5F7288A0B3A35222C1F8F3F5FB115DEBD`.
Same r11 FD, signed Windows, BCD, one CPU and 4 GiB as controls. The difference
from early-DS candidate is the EBS callback, not ACPI/CPU/timer flags.

Kernel debugger proves, in this order:

1. `HalpGic3ValidateIoUnit`: x0=0 (SUCCESS).
2. `HalpGic3Discover`: x0=0 (SUCCESS).
3. `HalpGic3InitializeIoUnit+0x2c`: actual CTLR in x19=0x40 (was 0).
4. `HalpGic3InitializeIoUnit+0x28c`: status in x21=0 (was C0000184).
5. `HalpInitializeInterrupts+0x474`: returned x0=0 (SUCCESS).

The old 0x5C boundary is passed, not just debugger/kernel entry. Subsequent KD
load-symbol notifications include ramdisk.sys, mup.sys, disk.sys, CLASSPNP.SYS.
These notifications prove progress/load notification, not device driver success
or a usable desktop. No new fatal error was captured before bounded timeout.
KD reports refreshing connection after this point; the next instruction/blocked
stage is not yet localized. A single standard KD break-in byte was delivered
to the verified disposable VM stdin pipe; no new debugger stop was observed.
Do not call the timeout a demonstrated guest crash or a vendor-only blocker.

Evidence: `uart-hal-ds-ebs-runtime/kd.log.txt`,
`hal-ds-ebs-final-serial.bin` (489004 bytes, SHA256
`EA76FD7AFA833A56ECFF4B37D815C430D90E0C3447D51E4EC0D2DDCB2ADA2D08`),
`hal-ds-ebs-final-packets.json`. Remote VM timeout=124, host observer timeout
closed its open stdin pipe; no diagnostic VM remains running.

Keep this branch open: a concrete local platform fix exists. Next work should
localize the post-interrupt-init boundary with the current debugger, not repeat
the old GIC/timer/CPU experiments. Do not integrate the diagnostic UART prints,
probe-only profiles or Windows KD settings into the production Ubuntu flow.

## Kernel breakthrough — authoritative latest result

- Existing r11 FD / signed Windows / BCD / Boot.WIM unchanged on disk; only
  disposable shim GAS width and one RAM-only RuntimeDxe diagnostic helper fix.
- `resets=0` alone test stalled at initial PsLoadedModuleList; post-EBS effect
  not tested. A new ingress variant flushes all decoded bytes from each input
  read together instead of individual 3-byte writes. Standalone tests prove
  ABC delivered with stdin open in 65 ms and fragmented padded 4096 bytes exact.
  Raw Windows adb stdout expands LF to CRLF: the byte-exact test's return leg
  must itself be base64-armored. Both old/new decoders pass the corrected test.
- Combined chunked-ingress + resets=0 run restores post-EBS Winload debugging,
  then connects to **Windows 10 Kernel 26100 MP (1 processor) ARM64**, not the
  Boot Debugger. This one run does not isolate which observer change matters.
- Kernel base `0xfffff80334600000`, PsLoadedModuleList `0xfffff8033536a5a0`;
  PC `nt!DebugService2+8 = 0xfffff803348013c8`, normal initial debug stop.
  Stack contains `nt!KiSystemStartup+0x184 -> nt!KdInitSystem+0x690`.
- After g: fatal 0x5C, parameters above. The same-candidate automatic stack
  follow-up stalled in initial KD synchronization and was stopped; it is not
  evidence of a new Windows failure. Offline localization below supersedes it.
- Full raw serial 255442 bytes, SHA256
  `BA7803813BF4CC1AEBAD4A284DD8DAE5A73D577A04A4F105E927083370F49A86`.
  Evidence: `uart-gas8-chunked-runtime/kd.log.txt`,
  `gas8-chunked-final-serial.bin`, `gas8-chunked-final-packets.json`.
- Windows does jump into kernel; prior “no kernel transfer” is disproven for
  this diagnostic contract. Do not infer the HAL's missing device from status
  C000000E alone, nor blame CNTP/MIDR without the actual failure stack.

## Exact HAL localization from matching kernel and Microsoft symbols

The retained ntoskrnl.exe SHA256 is
`C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734`.
Its RSDS identity matches the runtime-cached ntkrnlmp.pdb:
`36D33E649D94FF98D44813A0F326BB4F1`.
Read-only tools `find-hal-failure-literal.py` and `pdb-near-rva.py` map the
observed discriminator to actual PDB public symbols (not misleading PE exports).

- RVA `0xb1baa0`: `HalpInitializeInterrupts`.
- RVA `0xb1bb40..0xb1bb5c`: exact 0x5C call, P1=0x200, P2 loaded from
  literal RVA `0xb1bf38` = 0x05250631, P3=`HalpInterruptLastProblem`,
  P4=sign-extended failed NTSTATUS.
- RVA `0x48d410`: `HalpInterruptSelectController`; tries registered controller
  type 2 then type 0 via `HalpFindInterruptController` and initialization.
- Its failure branch RVA `0x48d484..0x48d4a0` sets LastProblem=2 and returns
  exactly C000000E. This explains the observed parameter pair, rather than
  guessing a missing PCI device from NO_SUCH_DEVICE alone.
- Selection is called by `HalpInitializeInterrupts` at RVA `0xb1bdf0`.
  Failure jumps to the exact observed bugcheck. This localizes the current
  blocker to no usable interrupt controller at selection; it does not yet
  distinguish failed discovery from failed controller/local-unit initialization.
- `HalpInterruptSocEntry` calls `HalpGic3Discover` first, then legacy GIC.
  `HalpGic3ValidateIoUnit` checks MADT GICD version/base and reads actual
  GICD_PIDR2 at +0xffe8 (architecture >=3) and GICD_TYPER at +4.
  Linux FDT success does not prove these Windows-specific validation checks pass.

Read-only EFI GIC register audit completed in the disposable one-vCPU r11 VM,
before Windows. No GIC writes, interrupt acknowledgements, ACPI changes,
CPU masking or Windows patches. The probe deliberately returns before Windows.
GICD_CTLR=0x12, GICD_TYPER=0x50, GICD_PIDR2=0x3b;
GICR_TYPER=0x10, GICR_WAKER=0, GICR_PIDR2=0x3b;
MPIDR_EL1=0x80000000. The exact basic ValidateIoUnit PIDR/version checks are
consistent with these values; mapping/allocation/kernel-time state is not tested.
The probe's later TABLE=FAIL is expected fail-closed behavior if UEFI retries
the shim after it already changed GAS32 to GAS8, not a Windows failure.
Evidence: `gas8-gic-audit-serial.bin`. Candidate then restored byte-for-byte to
the proven kernel-entry SHA DA7C75AF...AEB33. Next KD trace targets
GIC3 discovery/validation return values and InterruptSetProblemEx arguments.

## Current observer breakthrough (supersedes earlier research-only status)

### Controller trace — newer than offline localization

Same restored candidate, fresh successful KD connection and software breakpoints:
`uart-hal-controller-runtime/kd.log.txt`.
`HalpGic3ValidateIoUnit` returns SUCCESS (0), then `HalpGic3Discover` returns
SUCCESS (0). The controller is discovered; this is NOT absent ACPI/GIC discovery.
`HalpInterruptSetProblemEx` next reports controller pointer nonzero,
problem=5, underlying status=C0000184, caller
`HalpInterruptInitializeController+0xdc`; callback is
`HalpGic3InitializeIoUnit`. Selection then overwrites that detail with problem=2,
C000000E and the observed fatal 0x5C. Full fatal HAL stack was captured too.

The specific initialization function's direct C0000184 branch is RVA
`0x4ae03c`: when SecurityExtn is absent (flag set in successful validation)
and GICD_CTLR.DS bit6 is zero. The preboot read-only probe matches this
combination: TYPER=0x50 (SecurityExtn=0), CTLR=0x12 (DS=0).
This is a strong concrete lead, not a timer/MIDR/PCI hypothesis. A further
callback could theoretically return the same status; exact branch-hit trace
is still desirable before labeling the direct branch unconditionally proven.

Arm's GICv3/v4 overview, section 4.4.2, distinguishes the single-security-state
model using DS=1:
https://developer.arm.com/-/media/Arm%20Developer%20Community/PDF/Learn%20the%20Architecture/GICv3_v4_overview.pdf?revision=65f91645-cd52-4795-952b-f01095ff5ef8
Next local feasibility test: ONLY this disposable VM, mask IRQ/FIQ, refuse
active interrupts or SecurityExtn, disable delivery, write DS, read back,
restore delivery/masks, return before Windows. If ignored, do not keep
retrying firmware flags: the bit is supplied by the virtual GIC implementation.

DS write feasibility is **PASS** (not Windows boot PASS): new disposable probe
reads CTLR=0x12, masks IRQ/FIQ, verifies no active interrupts, disables group
delivery, writes DS, reads **0x50**, restores delivery and reads **0x52**.
`WG:DS_PROBE=WRITABLE`. Guest-visible DS is locally writable, so vendor-only
blocker is NOT established. Evidence: `gas8-gic-ds-probe-serial.bin`.
Probe candidate SHA D72EA217...46D22F0. Windows A/B fix candidate then changes
only the shim to continue into signed Windows after this verified operation,
SHA `12FA3178BAD882E39A6649BEF2B41D8392ECAF0EF7E9703D5649421E4E885ADC`.
Exact r11 FD, BootMgr and BCD stay unchanged. Early-DS Windows test still
fails, but adds decisive evidence: `GIC3_INIT_CTLR` reads x19=0 in
HalpGic3InitializeIoUnit; return x21=C0000184. Source of the retained EDK2
ArmGicCommonDxe.c `ArmGicDisableDistributor` writes 0 at EBS, called by
GicV3ExitBootServicesEvent. Thus a preboot DS write is erased at handoff.
Evidence: `uart-hal-ds-fix-runtime/kd.log.txt`,
`hal-ds-early-final-serial.bin`.

Next minimal fix candidate adds a Windows-shim-only EBS callback at TPL_CALLBACK,
below the existing GIC shutdown TPL_NOTIFY. It checks delivery is disabled and
SecurityExtn absent, restores only DS without enabling interrupts, and reports
readback. No firmware rebuild or Windows binary patch. The next KD trace checks
kernel-visible CTLR and complete interrupt initialization, not merely EFI PASS.

- Disposable shell-owned WinPE VM; no APK/app changes. The greatly changed
  Android UI/runtime architecture is not involved in this diagnostic runner.
- GAS32 control: Windows Loading files, no validated KD data packets.
- GAS8 (SPCR + DBG2 width, same byte AccessSize/address): Windows emits valid
  KD packets and reports Boot Debugger Initialized. The source boot manager,
  BCD, r11 FD, disk geometry and Boot.WIM are unchanged.
- Both variants use one WinPE-only candidate: original geometry/allocated
  clusters preserved except unused INSTALL.SWM/INSTALL2.SWM payload bytes.
  Not an installation disk; the original source and Windows baseline are untouched.
- A separate no-VM test proves stock Android `base64 -di` buffers short ingress
  until EOF. A small read/write-based decoder passes short open-stream input
  in 59 ms and byte-exact padded-record testing (1024 bytes).
- The host bridge also needs complete padded records (no one/two-byte carry)
  and a `WG:LOAD=PASS,START_WINDOWS` transmit gate. Without the gate, prompt
  KD synchronization interrupts U-Boot autoboot. These are observer fixes,
  not guest CPU/platform changes.
- Full KD connection and commands are proven in `uart-gas8-gated-runtime`
  and `uart-gas8-observer-runtime`: Windows Boot Debugger 26100 AArch64,
  winload symbols/registers, `OslMain -> BlInitializeLibrary -> BlBdInitialize`.
- Continuing (`g`) loads drivers, then KD reports Shutdown before the firmware
  EBS marker. This is a debug lifecycle transition, not VM shutdown evidence.
- Validated packets report first-chance access violation `C0000005`, PC
  `0x17f3b00ac`, read address `0x3fd`. Reported instruction bytes decode as
  `ldrb w3,[x2]; tbz w3,#5,...; mov x1,#0x3f8; strb w0,[x1]`.
  It is UART polling, not evidence of a timer/SVE/kernel-HAL fault.
  The exact module/function and relation to the old non-debug stall remain
  unproven; firmware diagnostic output is the next narrow hypothesis.
- A subsequent BlBdStop breakpoint attempt connected but stalled reading
  PsLoadedModuleList with repeated packets; the breakpoint was NOT confirmed
  installed/reached. Do not repeat it blindly or mark its intended trace PASS.
- BdsDxe-only match failed closed; no patch was applied. Offline extraction of
  the exact SHA-verified r11 LZMA FV identifies one matching instruction stream
  in RuntimeDxe FFS `B601F8C4-43B7-4784-95B1-F4226CB40CEE`, not DXE Core.
  Source identifies it as the added bounded `WinAvfAuditPutc` diagnostic.
  The complete 40-byte function has a bounded counter loop, unlike the first
  proposed matcher; the incorrect matcher was not allowed to patch anything.
- A first RuntimeDxe-targeted test identifies entry `0x17f3b00a4`, but writing
  the RET traps on an EFI read-only code page BEFORE Windows. This test is not
  evidence about Windows progression. The next revision uses the UEFI memory
  attribute protocol to clear RO on one page, patch only the exact unique void
  helper, then restore and verify original protections before Windows starts.
  No CoreExitBootServices/Windows/on-disk FD patch; fail closed on any mismatch.
- `gas8-runtime-quiet-ro` proves exact RuntimeDxe base `0x17f3a0000`, helper
  entry `0x17f3b00a4`, initial RO attribute `0x20000`, helper disable PASS and
  original page-protection restoration. Full Winload KD reconnects; continuing
  closes boot debugging. The previous UART C0000005 is absent. Kernel KD/WinPE
  are still NOT observed. Removing this instrumentation fault is therefore
  proven, but fixing the original Windows stall is NOT proven. Next one narrow
  trace uses software breakpoints on BlBdStop and OslArm64TransferToKernel,
  with explicit breakpoint-list evidence before continuing (not flag sweeps).
- `uart-gas8-boundary-runtime/kd.log.txt`: both breakpoints installed (explicit
  `bl` output). BlBdStop breakpoint HIT at `0xa62ccc88`; register and stack
  commands succeed: `OslMain+0x324 -> OslFwKernelSetup+0x258 -> BlBdStop`.
  Disassembly confirms the next sequence: EfiGetMemoryMap; firmware Boot
  Services +0xe8 call (ExitBootServices); EfiGetNtStatusCode; BlBdStart; then
  subsequent kernel setup. This replaces guesses about the KD Shutdown event:
  it occurs before the EBS call, not proof of kernel entry or VM shutdown.
- OslArm64TransferToKernel symbol resolves to `0xa60ba080`; it switches stack,
  calls OslUnmapFirmwareTemporaryRanges, and branches through x20. Its code
  was read, but this breakpoint was NOT observed hit. A later breakpoint
  after BlBdStart also was NOT observed hit. Continued serial contains repeat
  boot-debug initialization/unload traffic, so reconnect synchronization is
  still an observer blocker, not proven CPU/HAL failure.
- Final quiet trace: 95,467 bytes, SHA256
  `111149A40AFAD72798AAEF68B84E7C207E4A1E726D4E9328B080352EC640BAA1`;
  validated exception list empty. Final boundary serial: 97,452 bytes retained
  separately. No next platform flag test is justified. Remaining local work:
  recover debugger synchronization across BlBdStop/BlBdStart, or install a
  minimal firmware-owned EBS status observer without raw physical UART at
  unsafe runtime-map points. Do not mark Windows kernel entry PASS yet.

Microsoft's documented virtual-COM debugger target includes `resets=0,reconnect`:
https://learn.microsoft.com/en-us/windows-hardware/drivers/debugger/attaching-to-a-virtual-machine--kernel-mode-
The current bridge did not use these settings; a narrowly scoped observer-only
follow-up must account for named-pipe reconnection before assuming a guest hang.

Evidence root: `build-logs/windows-post-linux-reassessment-20261004/`.
`boot-wim-equality.json` proves source/candidate Boot.WIM equality:
`A0D1106F85ED9CF449182ED220BCC8480DDC645791D136D9C6AA4880177D48C4`.
The GAS8 prelaunch disk SHA is
`14FB1E8CE344B124473008346AF61B250F1911E1CE08F0DCB138F0451E1E8A94`.
The original control SHA is
`E3CC6A75F2C2295D7A5F11A0ADF1745E6FDD5A27693A76BC3153B9CC29F3EF33`.
Exported FAT/prefix and BOOT directory blocks are byte-identical; only the
shim cluster differs. Full debugger connection additionally required the
observer transport fixes above; do not attribute all of it to GAS width alone.

## Corrected four-way differential

| Run | Description/handoff | Observed result | Meaning |
| --- | --- | --- | --- |
| Product Windows 26100.6584 | UEFI/ACPI, host-derived CPU, CAM PCI backing | winload and original EBS return; no later Windows witness | Kernel transfer, exception and early HAL stages remain indistinguishable. |
| Old Debian GRUB / direct stub v2 | ACPI with `earlycon=uart8250,mmio32,0x3f8` | Serial silence after ER | Invalid basis for saying all ACPI kernels hang. |
| Direct Debian stub v3 | Same platform, only early console changed to 8-bit `mmio` | ACPI enumeration, PSCI HVC, GICv3, CNTV and Debian Installer | ACPI early bring-up works. This installer is initramfs based, not proof of disk access. |
| Ubuntu ACPI | ACPI without PCI root/MCFG | Kernel/initramfs/Casper reached; live medium not found | Real device-discovery gap, not loss of all post-EBS execution. |
| Working Ubuntu FDT | FDT selected during DXE; generic CAM driver | PCI/virtio disk, systemd, GNOME, later production VirGL/display | Positive platform/device proof, not equivalence to Windows ACPI contract. |
| QEMU Windows | TCG Cortex-A76, QEMU ACPI/PCI machine | Historical Setup/install/OOBE | Independent viable Windows image; not identical CPU or hardware backing. |

Retained decisive logs:

- `build-logs/linux-efi-stub-runtime-v3-mmio8-20260911-202844/raw-serial.log`:
  ACPI RSDP/XSDT/FACP/MADT/GTDT, PSCIv1.0, 13 MHz virtual timer, installer.
- `build-logs/runtime-va-r9-20260906/runtime-r9-raw-serial.log`:
  firmware enumerates PCI, but installed XSDT has only FACP/GTDT/APIC/SPCR/
  three SSDTs/DBG2; no MCFG/IORT/PCI SSDT.
- `build-logs/generic-ubuntu-frame-bridge/runtime-clean-P9-serial-captured.log`
  versus `runtime-control-P7-platform-P9-initrd-serial.log`: same clean P9
  initrd, different platform, panic versus userspace. This does NOT isolate
  ACPI alone: launcher and FD also differ. Old invalid P9 is not evidence.

The previous blanket statement “Linux ACPI also hangs after EBS” is superseded
by the existing v3 runtime. The `nr_free_zone_pages` panic cannot be projected
onto Windows: Windows fault PC is unknown and the Linux comparison changes
multiple platform variables.

## Ranked hypotheses (only two remaining classes)

### 1. Windows-specific CPU/EL1 contract consumption

New Linux evidence proves a real *userspace* incompatibility: LLVM20's X925
target emits SVE despite guest HWCAP lacking SVE. It raises the relevance of
CPU identity versus exposed features, but does not establish the same bug in
Windows kernel code. Windows early boot is not this Mesa/LLVM JIT.

Fresh read-only scans revalidate the exact Windows files:

- winload SHA `F7747F4AC18CCD66EBF6A043D979CF30C7731114C27C33FF2FC3C0610F491D31`;
  7 MIDR read sites, PE entry preferred VA `0x180005fa0`.
- kernel SHA `C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734`;
  15 MIDR read sites, PE entry preferred VA `0x140a87ba0`.
- Kernel SVE feature paths explicitly read PFR0[35:32], e.g.
  `0x14053dbcc..0x14053dbe4` and `0x140730b90..0x140730ba8`.
  The retained product PFR0 `0x1201011023111111` has SVE=0. Presence of SVE
  helper code is NOT proof Windows executes it. Linear disassembly also
  decodes literal data; raw SVE matches must not be treated as crash sites.
- Kernel early init at `0x140a89bf0..0x140a89c08` tests MPAM capability in
  PFR0/PFR1 and reads MPAM1_EL1. PFR0[43:40]=1 in the product capture.

The MPAM read was chosen over arbitrary feature masking: an exact conditional
Windows register access, advertised capability, independently trap-observable
test, no disk, no Microsoft binary patch.

Across-launch MIDR differences (`0x410fd821` versus `0x410fd851`) do not prove
within-vCPU migration. This investigation's A720-labelled boot reports the
same PFR0 as the old X925-labelled probe. Linux EL0 sanitized ID registers
are not an interchangeable observation of Windows EL1 register semantics.

### 2. Incomplete Windows-visible ACPI PCI contract

This is the strongest concrete platform-description gap, but its causal
relationship to the FIRST silent Windows boundary remains UNKNOWN.

- AVF reference `crosvm.rs` hardcodes `cam=[start=0x2e000000,size=0x1000000]`;
  historical real host launch args agree on that backing.
- Local EDK2 `BaseCachingPciExpressLib/GetAvfPciCamAddress` translates its PCI
  addresses to CAM, so successful pre-EBS enumeration is not a Windows PCI proof.
- Dynamic ACPI `PciConfigSpaceParser` recognizes ECAM only. ConfigurationManager
  removes the PCI tables when no config-space object exists. Runtime confirms
  their absence. Intentional omission avoids lying but does not provide a
  complete Windows device-discovery contract.
- Linux FDT supports CAM explicitly (bus shift16), unlike ECAM (bus shift20).
  The generic Linux helper's log says “ECAM” even when the selected ops are CAM;
  that string alone is not evidence of an ECAM backing.

Offline address comparison, config register0:

| Device | Actual CAM address | Same-base ECAM/MCFG address |
| --- | --- | --- |
| 00:00.0 | 0x2e000000 | 0x2e000000 |
| 00:01.0 | 0x2e000800 | 0x2e008000 |
| 00:05.0 | 0x2e002800 | 0x2e028000 |

Merely publishing MCFG at the CAM base or renaming FDT compatible would make
Windows access the wrong devices/functions. The available app API has no
evidenced host ECAM-selection control. A truthful solution needs a real ECAM
aperture or a suitable Windows-specific CAM access implementation; neither
is a demonstrated firmware-only fix. Do not invent an ITS to populate IORT.

Old audits were right that an absent IORT-to-ITS reference cannot be followed.
They went too far when treating absent PCI tables as sufficient to eliminate
the entire missing-device-contract hypothesis. Conversely, missing PCI does
not prove a pre-HAL crash, particularly for a ramdisk WinPE boot.

## One bounded runtime: exact MPAM instruction availability

Diskless shell-owned AVF VM, 128 MiB, one vCPU, same GenieZone. ARM64 Image
contains only UART output, private exception vectors, architectural ID reads
and the exact MPAM1_EL1 opcode (`d538a509`, same instruction as Windows with
destination x9). No OS disk attached. One 12-second bounded run, CID2072.

```text
B0000000000000004       CurrentEL = EL1
M00000000410FD821       MIDR
P1201011023111111       PFR0
Q0000000000010021       PFR1
TR8000000000030002      MPAM read started, returned value
D                      probe completed
```

No X/ESR exception marker. The asserted candidate “advertised MPAM1 read traps
on GenieZone” did not reproduce. This is NOT an EBS or Windows runtime A/B:
the small probe bypasses firmware and cannot exclude EBS-dependent effects,
later MPAM writes or Windows-specific configuration. No such broader claim.
The bounded runner ended and `vm list` returned empty. Evidence retained in
`build-logs/windows-post-linux-reassessment-20261004/` (serial/crosvm log,
Image/ELF and static-contract.json). No guest or Android data removed.

## Precise blocker and stop decision

```text
CURRENT_WINPE_CNTP_DIRECT_BLOCKER      = REFUTED_BY_EXACT_BINARY_AUDIT
GLOBAL_ACPI_POST_EBS_FAILURE           = REFUTED_BY_DEBIAN_V3
ACPI_PCI_DISCOVERY_CONTRACT_GAP        = CONFIRMED
ACPI_PCI_AS_CAUSE_OF_WINDOWS_STALL     = UNPROVEN
MIDR_TO_WINDOWS_ISA_MISMATCH           = UNPROVEN
MPAM1_READ_FAULT_ON_DISKLESS_PROBE     = NOT_REPRODUCED
WINDOWS_KERNEL_ENTRY                  = UNKNOWN
NEW_WINDOWS_POST_EBS_PROGRESS         = NONE_OBSERVED
```

The public device configuration/observer audit from October3 confirms the
same captured framework API. It supplies neither CPU model/feature masking,
vCPU PC/ESR/FAR/ELR access, nor a supported ECAM override. Existing GDB/KD and
vendor trace negative evidence was not repeated. Firmware EBS wrappers do not
observe Windows' later branch or its newly installed exception vectors.

Highest-information next action is one vendor observer capture of failing
Windows immediately after EBS: guest PC, EL, ESR/FAR/ELR, PFR/MIDR and relevant
translation state. It distinguishes loader stall, kernel-entry exception and
early HAL/device wait. Until that or a real platform control exists, do not
run another cosmetic ACPI/timer/CPU A/B. Cause remains unlocalized, not fixed.

## Follow-up: observer feasibility, 2026-10-04

User requested continued Windows work. Reopened the specific observer question,
not the closed timer/BCD/CPU flag experiments. Read-only source audit and device
access checks only; no VM launched, no APK installed, no disk/firmware changed.

Two distinct barriers must not be conflated:

1. Retained device runtime rejects raw GDB creation as non-debuggable; the local
   AVF `crosvm.rs/add_gdb_arg` reference rejects DebugLevel NONE. Raw custom-image
   API has no supported GDB setter. `--debug full` did not overcome this guard.
2. Public GenieZone crosvm implementation at commit
   `853aaa9bdb28774fd82e7cfa4c910c95b0acaa5d`,
   `hypervisor/src/geniezone/mod.rs:432-436,467-471`, returns EINVAL for
   `get_max_hw_bps` and `set_guest_debug`. ARM64 GDB breakpoint/single-step
   operations call these methods (`aarch64/src/lib.rs:1298-1313`). Merely exposing
   a GDB port would not supply those capabilities in this implementation.

Important limits on these findings:

- This public source is NOT proven byte-identical to Samsung's installed binary.
  A read-only attempt to inspect `/apex/com.android.virt/bin/crosvm` on the tablet
  returned Permission denied, including for strings. No policy change attempted.
- Newer AOSP VirtMgr is not interchangeable with the retained Samsung behavior.
  At AVF commit `5f8e75df64e6f6cb64b7e10930e98784327a20a4`, vmconfig returns None
  for RawConfig debug level; aidl/check_gdb_allowed rejects Some(NONE), while
  DebugConfig substitutes NONE. Do not claim that every upstream raw VM is
  rejected by that particular aidl check, or that an upstream update is a fix.
- GenieZone is NOT devoid of register-reading machinery. `get_one_reg` uses
  `GZVM_GET_ONE_REG` for PC/general/system registers in the public backend.
  What is missing locally is an app-accessible observer of the AVF-owned vCPU,
  not proof that a vendor observer is impossible.
- AppConfig.CustomConfig is not a demonstrated Windows-compatible replacement
  for the custom-image RawConfig boot path; it is not a safe justified A/B here.

Device remained connected; final `vm list`: `Running VMs: []`.
No new Windows post-EBS progress. No justified firmware/Windows fix selected.
Stop remains appropriate under the requested privileged-observer stop condition.

Minimal actionable vendor observation request: suspend the failing AVF-owned
Windows vCPU after EBS and return PC/PSTATE plus ESR_EL1/FAR_EL1/ELR_EL1,
VBAR_EL1, SCTLR_EL1/TCR_EL1/TTBR0_EL1/TTBR1_EL1 and MIDR/PFR0/PFR1. Report
availability of each register, not invented zero values. This separates loader
stall, kernel entry and early HAL without requiring hardware breakpoints.

Sources:

- [GenieZone backend](https://android.googlesource.com/platform/external/crosvm/+/853aaa9bdb28774fd82e7cfa4c910c95b0acaa5d/hypervisor/src/geniezone/mod.rs)
- [ARM64 debug operations](https://android.googlesource.com/platform/external/crosvm/+/853aaa9bdb28774fd82e7cfa4c910c95b0acaa5d/aarch64/src/lib.rs)
- [AVF GDB authorization](https://android.googlesource.com/platform/packages/modules/Virtualization/+/5f8e75df64e6f6cb64b7e10930e98784327a20a4/android/virtmgr/src/aidl.rs)
- [AVF raw/AppConfig debug distinction](https://android.googlesource.com/platform/packages/modules/Virtualization/+/5f8e75df64e6f6cb64b7e10930e98784327a20a4/libs/vmconfig/src/lib.rs)

## Internet follow-up: concrete UART stride discrepancy

New primary-source finding changes the next-experiment decision. The old
source-only UART PASS did not validate the meaning of RegisterBitWidth for
DBG2 subtype 0x0012. Microsoft specifies that it encodes register stride,
separately from AccessSize. Do not conflate a byte access with byte spacing.

Retained r11 installed-table evidence:

```text
DG=8000,0012,00,20,00,01,00000000000003F8,N=\_SB_.COM0?
SP=00,20,00,01,00000000000003F8,08,00000020,07
```

GAS RegisterBitWidth = 0x20 (32 bits), AccessSize = 1 (byte).
The retained U-Boot UART audit reports width 1, shift 0: adjacent byte
registers. crosvm's public serial device likewise places LSR at offset 5,
LCR at 3, SCR at 7 and handles byte-sized accesses. The correct byte-spaced
GAS width would be 8, not 32. Interpreting 32 as stride gives four-byte
spacing, e.g. LSR at 0x40c instead of the actual 0x3fd.

Local generator corroboration: AcpiDbg2Lib/Dbg2Generator.c uses ARM_GAS32
in its template (line 144). BuildAcpiDbg2Table updates base and AccessSize
but leaves that width in place for subtype 16550_WITH_GAS. SpcrGenerator.c
also starts from ARM_GAS32 (line 75). This is an actual retained description
discrepancy, not an invented UART address or a new BCD permutation.

What this does NOT prove: that the exact Windows kdcom transport consumed
this field, that its polling loop stalled, or that it caused the normal
non-debug Windows post-EBS stall. Those require runtime evidence.

Highest-information next local A/B is now justified: on a disposable
Windows KD configuration with the already-correct 0x3f8 address and the
same signed Windows/BCD, correct only subtype-appropriate serial GAS stride
from 32 to 8 in the Windows diagnostic firmware. Validate installed table
bytes and checksums, then use the existing byte-exact serial/KD bridge for
one bounded run. Do not edit generic PL011 templates or the proven Ubuntu
firmware, and do not mutate the immutable Windows disk. Inspect remaining
staged/local diagnostic media before choosing a runner. This research turn
does not build/apply a firmware patch and does not claim a KD handshake.

The earlier 'no justified observer experiment' decision is superseded by
this concrete serial-contract discrepancy. It is a route to possibly
restoring local Windows observability, not a demonstrated Windows fix.

Other researched alternatives did not establish an immediate local fix:

- Arm DEN0115 defines PCI configuration access through SMC/HVC as an ECAM
  alternative, but implementing its handler requires EL3/EL2; guest EDK2
  runs at EL1 here. No evidence Windows uses this ABI on this configuration.
- A fabricated MCFG does not translate CAM to ECAM. Microsoft's generic
  configuration-space documentation does not provide a firmware CAM switch.
- HyperUEFI is QEMU/KVM on Raspberry Pi OS, not a guest-EL1 workaround for
  GenieZone. Its README requires privileged Linux host operation.
- UTM issue 7850 describes silence at firmware starting BootXXXX, with no
  demonstrated successful EBS; not equivalent to this exact Windows boundary
  and not a proven fix.

Primary references for this finding:

- [Microsoft DBG2 subtype/GAS semantics](https://learn.microsoft.com/en-us/windows-hardware/drivers/bringup/acpi-debug-port-table)
- [crosvm serial implementation](https://github.com/google/crosvm/blob/main/devices/src/serial.rs)
- [Arm PCI configuration firmware ABI](https://documentation-service.arm.com/static/5fdb5be3d68fe412ba0410bc)
- [Microsoft PCI configuration access](https://learn.microsoft.com/en-us/windows-hardware/drivers/pci/accessing-pci-device-configuration-space)
- [HyperUEFI implementation/requirements](https://github.com/codeeleven0/hyperuefi)

Local evidence: ARM64_KD_SERIAL_PLATFORM_CONTRACT_AUDIT_2026-09-07.md,
R11_DEBUG_UART_ALIGNMENT_RUNTIME_2026-09-08.md and
SHELL_WINDOWS_KD_R11_ALIGNMENT_RUNTIME_2026-09-08.md. The r11 KD run did not
observe EBS and cannot settle the later boundary.

## Original reassessment references

- [Microsoft ACPI platform tables](https://learn.microsoft.com/en-us/windows-hardware/drivers/bringup/acpi-system-description-tables)
- [Linux generic PCI CAM/ECAM operations](https://kernel.googlesource.com/pub/scm/linux/kernel/git/torvalds/linux.git/+/55aa45154fe48b7d8e96bc031ad27d5b0edfcdb8/drivers/pci/controller/pci-host-generic.c)
- [Linux EL0 CPU ID sanitization and MIDR limitations](https://www.kernel.org/doc/html/v5.19/arm64/cpu-feature-registers.html)
- Project: `LINUX_EFI_STUB_V3_MMIO8_RUNTIME_2026-09-11.md`,
  `WINDOWS_ARM64_TIMER_INSTRUCTION_AUDIT_2026-09-13.md`,
  `VULKAN_VENUS_SIGILL_AUDIT_2026-10-03.md`.
