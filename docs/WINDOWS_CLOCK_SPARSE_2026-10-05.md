# Windows sparse clock observation — 2026-10-05

## Purpose

Separate the heavily instrumented CNTV-write run's HAL 0x5C/0x110 failure from a genuine absence of guest timer interrupt delivery. Preserve the validated Windows-only GAS8/quiet RuntimeDxe/GICD_DS-at-EBS candidate and all production Linux files.

## Preparation

The staged disposable disk initially differed from exact candidate SHA. The observer refused to start; FAT/shim blocks and firmware range matched. The changed bytes elsewhere were not localized or silently accepted. Only `/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img` was replaced from the existing known candidate. Full staging SHA guard then passed. No baseline/user disk was replaced.

## Sparse experiment

Exact kernel SHA C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734.

One-shot guarded probes:

- RVA 0x49fe60: temporary HAL interrupt self-test callback.
- RVA 0x487fdc: successful self-test exit.
- RVA 0x25c650: internal bugcheck.
- RVA 0xb27fa8: IoInitSystem return.
- RVA 0x4c363c: cache-map cleanup delay return.

No driver entry/return stop loop and no CNTV MSR stop/rearm loop. Breakpoints restored original instructions before continuing; no image patch.

## Result

- Kernel entry again verified.
- TIMER_TEST_ISR hit at t=634.561; TIMER_TEST_SUCCESS at t=634.705.
- Timer object advertised IRQ 0x1b (PPI27); callback increments timer+0x40 from 0 to 1. IRQ delivery/self-test PASS for this runtime.
- No 0x110 bugcheck observed. Prior heavy trace is not evidence of a fundamental virtual-timer failure.
- Driver/module load stream continues, then KD refresh at t=637.388.
- No delay-return or IoInitSystem-return probe. One break-in at t=667.332 has no complete KD response by t=672.377. This does not identify the stopped PC.
- Diagnostic VM stopped by scoped cleanup. WinPE/Setup remain NOT PASS.

Evidence: `build-logs/windows-post-linux-reassessment-20261004/kd-native-clock-sparse-20261005-runtime/`.

## Next discriminating action

Capture context/PSTATE once at the already localized cache cleanup delay call; arm the normal clock handler only there. Distinguish normal clock/wait wakeup from interrupt-disabled or stalled execution, without repeating timer self-test/MSR instrumentation, disabling ReFS or modifying refcounts. Do not assert CNTP or vendor-only cause from UART silence.

## Completed wait follow-ups

Three bounded runs, same guarded candidate, no production changes:

1. `kd-native-wait-clock-20261005-runtime`: at cache cleanup delay call, CPSR 0x20001044 has I=0, interval=-50 (5 microseconds), map refcount=4. Normal clock handlers and caller return not observed after resume.
2. `kd-native-wait-program-20261005-runtime`: reaches actual dispatcher call RVA 0x2597d0. Return at 0x2597d4 reports STATUS_TIMEOUT, but x19 differs from the originally captured thread. This is NOT proof the original cache cleanup thread resumed.
3. `kd-native-original-waiter-20261005-runtime`: reads original thread while stopped in another thread's timeout return. Original thread `0xffff8881a96af080`; return thread `0xffff8881a97c8080`; cache map refcount is now **1**. Cache references do drain. Original cleanup return remains absent.

Original snapshot timer base is thread+0xf8 (confirmed by KiCommitThreadWait disassembly), due-time field thread+0x110=`0x493033`. Raw bytes and context are retained, not rewritten. The initial observer called the x1 pointer at thread+0x138 a `timer`; it is a wait block, not the KTIMER base. New output distinguishes these. Do not infer timer insertion/expiration solely from an unvalidated struct layout.

No normal clock handler or post-call timer-write breakpoint observed; generic waits in other threads do return and shared-time samples advance across debug stops. This does not establish normal clock cadence, nor a globally broken scheduler. No original waiter wakeup / IoInitSystem completion / WinPE PASS.

The next useful localization is the original KTIMER expiration and waiter-ready path. Do not disable ReFS, force refcount=1 (already achieved naturally), or repeat HAL self-test / early MSR loops. Each run ended with one unsuccessful UART break-in and scoped cleanup.

## Validated timer layout and wheel observation

Microsoft public `ntkrnlmp.pdb` downloaded to the isolated evidence directory.
GUID matches image RSDS: 36D33E64-9D94-FF98-D448-13A0F326BB4F. **Age differs:
public stripped PDB 6, image RSDS 1**. Do not call this a full GUID/age match.
Timer/PRCB offsets used below are also corroborated by exact-image disassembly
(KiInsertTimerTable and KiTimerExpiration), not accepted solely from the PDB.

- KTIMER Header.Inserted: byte 3 bit 6; Expired: byte 3 bit 7. Original timer
  `08 00 12 40` means inserted=true, expired=false, not an uninserted timer.
- KTHREAD.Timer=+0xf8, State=+0x17c (5/Waiting), WaitReason=+0x2a3
  (4/DelayExecution), TimerActive=ThreadFlags(+0x6c) bit 9.
- PRCB TimerTable=+0x1b80; entries +0x200; two sets of 256 buckets, stride 0x20.
  TimerType at KTIMER+0x3a selects the set; omitting it reads the wrong bucket.
- `kd-native-timer-wheel-20261005-runtime`: original timer due=0x49f7f5,
  type=1, bucket=18; computed bucket head exactly matches both KTIMER links.
  Bucket lock=0, sole timer is this original timer, bucket due matches its due.
  Cache map refcount again naturally falls to 1.
- KiProcessExpiredTimerList fires, called from **KiRetireDpcList** at RVA 0x2a22b0.
  It is NOT proof KiTimerExpirationDpc/0x5101b0 ran; these one-shot probes did
  not fire. The retiring DPC code contains an inline timer-processing path.
- Retained processing-time register x10=0x49f7e3 is 18 units before this timer's
  due. Shared interrupt time later reads 0x1b4c0f1 while original timer remains
  queued. Debug stops skew these observations: do not assert an expiration bug
  solely by comparing saved processing time with later shared time.
- No original cache delay return or IoInitSystem completion. One bounded idle
  break-in failed; diagnostic VM stopped; production untouched.

Next discriminating test: only after the other thread's timeout return, arm
one-shot idle-call/return and alternate always-on clock/work handlers. Determine
whether execution reaches or returns from the CPU idle transition, without
changing timer values, CPU flags, guest image or platform contract.

## Idle follow-up result

`kd-native-wait-idle-20261005-runtime`, same exact guarded disk/kernel:

- After another thread returns STATUS_TIMEOUT, arm one-shot idle call at
  RVA 0x50eeb4 and return at 0x50eeb8, HalpTimerAlwaysOnClockInterrupt at
  0x212f30 and HalpTimerClockInterruptWork at 0x20a870.
- **Both idle call and return hit**. First PoIdle transition does not hang.
  Return x0=C0000225. This is a captured return value, not a demonstrated
  causal error. Exact code reenables IRQ immediately after return; I=1 at these
  two probes is expected, not proof of permanently masked interrupts.
- Original timer due=0x449669, bucket=17/type=1, inserted/active, thread waiting.
  Correct computed bucket head matches timer links; bucket lock=0.
- ClockActive remains 1. Stored clock timer deadline changes across the idle
  call. No new normal/alternate clock-handler hit or original waiter return is
  observed after resuming. No bugcheck or IoInitSystem completion observed.
- One 30-second idle bound, one unanswered UART break-in, scoped stop. Running
  VMs empty afterward. Production Linux, APK, ISO and baseline untouched.

This narrows the next question to timer rearm/idle fallback after the first
returned PoIdle call. It does not justify forcing expiration, disabling ReFS,
changing CPU identity, or declaring GenieZone a vendor-only blocker yet.
Only observer code was expanded; no new platform/kernel/Windows fix this step.

## Architectural idle follow-up — 2026-10-07

Same exact guarded candidate, `kd-native-wait-wfi-20261007-runtime`.
One-shot WFI entry at RVA 0x4841f8 armed only AFTER the proved PoIdle return;
return at 0x4841fc armed only after entry, avoiding adjacent-probe deletion.
Exact HalProcessorIdle code: helper call, DSB SY, ISB, WFI, return.

Both WFI_ENTER (t approximately 703.64) and WFI_RETURN (t approximately 703.93) observed, in addition to
PoIdle entry/return and another thread's timeout return. Thus this observed
architectural wait also returns; do not claim a WFI/CPU-idle hang. Original
waiter remains waiting with inserted timer. No original cache return,
IoInitSystem completion or WinPE success. No kernel/firmware/image fix made.
Next question is repeat clock delivery/rearm after this returned idle, not
forcing expiration or replacing ReFS. Ubuntu release-upgrade investigation is
separate and paused at the user's request.

## GIC observation access audit — 2026-10-07

Three distinct access methods, same guarded Windows-only candidate:

- `kd-native-wait-gic-20261007-runtime`: KD physical reads at validated
  distributor/redistributor physical addresses all return C0000001, no data.
- `kd-native-wait-gic-map-20261007-runtime`: capture exact
  HalpGic3InitializeLocalUnit x0 context, then read mapping pointers after WFI.
  Mapping offsets independently checked in exact HalpGic3QueryLineState code:
  distributor=context+8, CPU0 redistributor=context+0xb0.
  Virtual read succeeds: GICD_CTLR=0x52, GICR WAKER/group/enable/pending/active/
  timer-priority samples all zero. **Not yet a proved disabled IRQ:** debugger
  memory-copy access width must be validated before interpreting MMIO zeros.
- `kd-native-wait-gic-io-20261007-runtime`: width-4 KD extended IO reads
  (Internal bus 0, memory space 0) all return C0000001. This is an unavailable
  observer operation, not proof GenieZone itself rejected a timer operation.

WFI entry/return still observed. No original waiter wakeup/IoInitSystem/WinPE.
No IRQ state writes, guest image patch, production mutation or commit/push.
Do not repeat these KD physical/IO access variants. Remaining local option is
validated native-width guest register observation using guarded reversible RAM
instrumentation, or observing the exact HAL register-load instructions. Do not
label this vendor-only while that option remains unexplored.

## Native register sampling — 2026-10-07

`kd-native-live-cntv-20261007-runtime`: an exact-code-guarded, reversible
four-instruction MRS sample temporarily replaces the HalProcessorIdle epilogue
AFTER WFI returned. Its separate breakpoint captures x0..x3, then restores all
16 original bytes and the complete 912-byte ARM64 context including original
PC. Readback and register/PC rollback guards PASS. No Windows file is modified.
Instructions compiled/audited in timer-register-sample.S/.o.

Native result VALID:

- CNTV_CTL=7: enabled, masked, ISTATUS set.
- CNTV_CVAL=0x8e5134b; CNTVCT=0xd53fa38; CNTFRQ=13,000,000.
- Deadline delta=-74,376,941 ticks (already past).

This is the first direct current-register evidence, not a descriptor inference.
It does NOT yet prove the mask is wrong: sample is still inside HalProcessorIdle
return, before caller idle/IRQ restoration. Next compare after PoIdle return and
the caller's DAIFClr (RVA 0x50eebc). Never blindly clear the mask from this result.
Exact kernel only writes CNTV_CTL through HalpGitWriteControlRegister at
0x4a1fe8; HalpGitStop requests control=2. No further normal post-wait write
probe seen. No original waiter/IoInitSystem completion. VM cleaned up.

Late comparison `kd-native-late-cntv-20261007-runtime` executes at RVA
0x50eebc AFTER PoIdle return and caller DAIFClr. IRQ-unmasked context guard PASS.
Native result remains CNTV_CTL=7, CVAL=0x8f6d438, counter=0xd717627,
frequency=13MHz, delta=-75,145,711. Code/full-context rollback PASS.
No original wakeup. This strengthens the remaining mask hypothesis, but does
not yet locate its writer or exclude debugger effects.

Next prepared A/B (`--test-timer-unmask`) requires a validated late sample with
exact CTL=7, deadline already passed, IRQ mask clear. It performs one MRS/BIC
IMASK/MSR/ISB sequence, restores original 16 code bytes and all context, but
deliberately leaves ONLY the timer IMASK bit cleared in this disposable VM.
No comparator/frequency/CPU/ACPI mutation. Observe actual clock IRQ, original
cache return and IoInitSystem before drawing a causal conclusion. Not a
production workaround. Source assembly and instruction bytes compiled/audited.

First intended A/B `kd-native-imask-ab-20261007-runtime` did **not** reach
WAIT_CALL/sample/unmask. Therefore neither positive nor negative A/B evidence.
Own bounded UART break-in succeeded, stopping in DbgBreakPointWithStatus
(RVA 0x201370, BRK #f000), stack through KdCheckForDebugBreak and
MiWalkPageTables/MiWalkPageTablesRecursivelyNoSynch. Observer previously
classified this as unexpected and stopped. New handling for this exact own
break (A/B mode only) guards the original BRK, advances PC to its RET, restores
context and resumes once within the overall bound. Never generalize this to
unknown breakpoints/faults; no claim of a kernel crash from this own break.

## IMASK A/B executes and advances Windows

`kd-native-imask-ab-resume-20261007-runtime` reaches the required stage:
native late CTL=7, CVAL=0xa06e911, counter=0xe4bf584, 13MHz, past deadline.
Guarded A/B clears only IMASK, executing control=5. Original code and complete
context rollback PASS. No timer comparator, frequency, ACPI or CPU change.

- Cache caller return RVA 0x4c363c observed at t=2438.715, status=0.
  x19 map=0xffff858c23bb08a0 matches original WAIT_CALL map exactly;
  original cleanup return is now proved (not another generic waiting thread).
- IoInitSystem return probe RVA 0xb27fa8 hit at t=2440.36, x0=0.
  Record exact return value; do not infer its semantic success solely from zero.
- Subsequent new load notifications: mouclass, HIDPARSE, win32kbase,
  win32kfull, win32kbase_rs, dxgmms2 and cdd.dll. Real progress beyond the old
  cache cleanup boundary. **WinPE/Setup still NOT PASS**.
- Later stream quiets after cdd.dll; one break-in produces no full response;
  diagnostic VM stopped. No normal clock handler probe observed: progress
  after unmask alone does not prove which interrupt handler woke the waiter.

New priority: find why normal timer remains masked, without treating one manual
unmask as a production fix. Exact retained earlier MSR trace's control=3 writer
was HalpGitInitialize (call at 0x4a1c08), called from
HalpTimerInitializeProfiling (return near 0x48a1c0c in that run).
HalpGitArmTimer explicitly writes control=1 after comparator at 0x2139f8.
Inspect selected HalpProfileTimer (global e06308), HalpClockTimer (e06428),
HalpAlwaysOnTimer (e06440), their private generic-register state and last
remask writer. Alias/shared-register conflict is a hypothesis, not yet proved.
Observer now records selected objects at WAIT_CALL in A/B mode and exact map
identity at cache return. Do not repeat unsuccessful KD physical/IO methods.

## Selected timers and repeated mask — continuation

`kd-native-selected-timers-20261007-runtime` repeats original waiter return
with same_map=true, refcount=1, and IoInitSystem return. HalpProfileTimer=0,
HalpAlwaysOnTimer=0; only HalpClockTimer is selected. The e06320 periodic
global is a state structure, not a timer pointer (observed first qword
0x40720008); do not dereference it as one. Clock/profile alias is not supported.

Correction: retained old control=3 trace LR=base+**0x4a1c0c**, inside
HalpGitInitialize. Earlier note attributing that LR to profiling initialization
(0x48a1c0c) was incorrect address arithmetic; no profiling writer proven.

After first unmask, native periodic compare write at 0x213964 and arm compare
at 0x213b24 both execute; control write at 0x4a1fe8 requests **1**.
Nevertheless native sample at IoInitSystem return has CTL=7, CVAL=0xea54f69,
counter=0xffb3dd8, 13MHz, expired again. One-shot control probe cannot exclude
later writes: do not yet call this spontaneous vendor remasking proved.

Exact HalpGitAcknowledgeInterrupt periodic branch updates CNTV_CVAL at
0x213964 then branches directly to epilogue (0x213968 -> 0x213910), without
rewriting control. This differs from HalpGitArmTimer, which writes control=1.

Next isolated workaround candidate: replace ONLY that guarded periodic return
branch with a 24-byte RAM thunk in .text last-page zero padding at 0x665b00.
MRS x16/conditional ENABLE check/BIC IMASK/MSR/ISB/branch original epilogue.
Preserves flags/SP/LR and other registers; x16 is ABI scratch. Applied after
kernel connect, before first normal periodic IRQ, without manual late unmask.
Kernel SHA, source word, zero padding, executable section/page extent and all
RAM readbacks guarded. No Windows disk/image/firmware mutation. This is a
candidate A/B for sustained rearm, not a published production fix or vendor
causality claim. Source compiled/audited in periodic-timer-unmask.S/.o.

## Cold boot and WinPE userspace proof — 2026-10-07

The periodic IMASK workaround passed cold boot without manual late unmask in
kd-native-periodic-imask-20261007-runtime. Repeat verification in
kd-native-winpe-processes-20261007-runtime passed IoInitSystem, native CTL=1
with future deadline, then loaded condrv/srvnet/srv2/mpsdrv.

At a single bounded debugger break, active process inventory proved:
winpeshl.exe PID828/2 threads; setup.exe PID820/3 threads;
SetupPrep.exe PID924/3 threads; SetupHost.exe PID888/13 threads.
All had ExitStatus=0x103 (STATUS_PENDING), nonzero PEB and WinPE Ramdisk paths.
System services, csrss, wininit, winlogon and lsass were also active.
Process links validated reciprocally, bounded to 64 entries; PID/image offsets
validated against exact native accessor instructions. Public stripped PDB age
caveat remains; do not claim GUI verification from this inventory.

Verdict: WinPE userspace and Setup process chain reached; former post-EBS and
timer-delayed I/O boundary genuinely passed. Own debugger break was resumed
at guarded RET; subsequent quiet KD timeout is not a guest crash verdict.
Desktop pixels, input, installer responsiveness and completed installation are
not tested. Workaround is still isolated RAM-only/version guarded, not a shipped
APK feature or durable vendor fix. Remasking origin and long-term PatchGuard
compatibility remain unproved. Production Ubuntu, ISO, storage and Windows
baseline were not changed; diagnostic candidate alone was restored/tested.
