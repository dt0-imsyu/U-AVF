# Windows eight-CPU boundary — 2026-10-11

## Proven result

Latest override: explicit-affinity IPI A/B `kd-windows-explicit-ipi-ab-20261011`
TERMINAL PASS. Native KeStartAllProcessors returned8/barrier0; actual own
broker2896 reported eight active processors seven times over~91s. Real Desktop
and app-owned H264/Surface stream survived. Original own EFI rollback PASS.
The earlier oneCPU-only/pending statements below are historical.
The remaining display goal is native1920x1200/cap60, not another SMP experiment.

Installed Windows remains a one-CPU working baseline; eight-CPU Desktop is NOT
PASS. The narrow eight-CPU launcher changes only four bytes in two original
EFI MOVK GICR addresses (3ffd -> 3fef), preserving every other byte. SHA256
365B733271DA6DEEB5EEA2C262041F6E1793D6DA3C21ECE077BA15FE84B1A865.
Explicit diagnostic admission requires eight available host CPUs/MATCH_HOST;
it cannot be used with ONE_CPU. No production Ubuntu/ISO/APK changes.

CID2209 reached kernel, with effective `--cpus num-cores=8`, then reported
A0(BEEF,8003,C4000003,FFFFFFFFFFFFFFFE). Matching kernel/PDB locates this at
HalpPowerPsciFatalErrorHandler+28, RVA49a868: CPU_ON64 returned -2. This is a
concrete secondary CPU start failure, not display loss or an EBS hang.

## Bounded observations and A/B

| Run | Observation | Result |
| --- | --- | --- |
| CID2210 | Initial generic HVC probe hit PSCI_VERSION, not CPU_ON | Observer scope assertion, no Windows regression claim |
| CID2211 | Correct CPU_ON-only dispatch RVA4aaf1c; HVC handler RVA49a870 | affinity1, entry1048d3010, context8000c000; -2 |
| CID2212 | Same eight-CPU platform, RAM4GiB -> 2GiB, exact operands | entry8ced3010 below4GiB; same -2. Above4GiB alone does not explain failure |
| CID2213 | Read only 48B around exact CPU entry | Entry stub ADR/LDR/BR; page prefix contains live metadata, NOT empty padding |
| CID2214 | One CPU_ON HVC reserved-x7 A/B, original stack-save guard and next LDR restoration | x7 pointer ->0, x0..x6 unchanged; direct post-HVC result still -2; pointer restored |

All runs are under build-logs/windows-post-linux-reassessment-20261004/
kd-windows-{legacy-eight-native-metadata,eight-psci-operands,
eight-psci-typed-operands,eight-psci-low-address-ab,eight-psci-entry-prefix,
eight-psci-reserved-x7-ab}-20261011. Each failed isolated VM stopped; no Ubuntu
or installed user files rewritten. Own helper CPU metadata remains unchanged.

## Interpretation

Working Linux serial records CPU affinities1..7 and all8 CPUs, so generic
GenieZone SMP impossibility is disproven. Windows asks for affinity1, not an
invented physical core ID. Actual conduit is HVC #0; no SMC substitution.
Both upper/lower entry addresses are valid word-aligned Windows RAM addresses.
The reserved x7 difference also did not explain the failure.

Remaining focused question is the exact CPU_ON entry/context contract accepted
by GenieZone: Windows uses a dynamically placed +0x10 entry and nonzero context,
whereas Linux uses its secondary_entry and context0. Page-alignment/context
restriction is a HYPOTHESIS, not a proven vendor limitation. Do NOT overwrite
the first16B before the Windows entry: captured bytes are live metadata.
No blind alignment rounding, context zeroing or fatal-handler bypass.

Next useful proof needs an owned, explicitly reserved secondary-entry trampoline
that preserves the original Windows entry/context and allows these two operand
differences to be isolated. Prepare/validate it offline before another boot;
otherwise retain oneCPU baseline. No further RAM-size/x7/CPU identity sweeps.

psci-entry-trampoline.py now has an independently decoded 32B LDR/literal
template test (LDR x0 original context, LDR x16 original entry, BR x16).
Offline positive/range/recursive-page guards PASS; NOT allocated/deployed.
Allocation must be an explicitly owned executable EFI reserved/NVS region,
reported and verified in the actual memory map, never guessed free memory.

## Baseline recovery

### Owned allocation/entry proof — CID2216–2218

New EFI builds reproduce both893... original and365... fixed8 controls exactly.
Only the isolated reserve variant compacts diagnostic labels to keep8192B;
all original safety conditions and DS/CAM/EBS behavior remain compiled.
Owned NVS pages8 atFFFD0000 are validated against the actual EFI memory map,
below4GiB and executable. Header/template/range guards passed native selftests.
No Windows metadata pages were borrowed; no persistent user files changed.

CID2216/2217 EFI allocation/map marker PASS and kernel entry PASS. However
kernel KD physical reads of NVS returnC0000001 with no bytes. Physical header
runtime readback is NOT PASS. Both controls stopped at observer guard, so
neither is evidence of a new Windows crash or successful secondary execution.

CID2218 exact static first-CPU trampoline candidate8192B SHA
36943A414743D10960C445102BE9EFCA4C2ADCAA46DAEB144CF7AA4F0E11620A;
diagnostic APK492289F4...D714E8B5. One CPU1 call only, guard original
context8000c000, actual HVC dispatcher. CPU_ON arguments changed:
entry105cd3010 -> owned pageFFFD1000; context8000c000 -> original entry105cd3010.
Static own code MOV x16,x0 / MOVZ+MOVK x0=8000c000 / BR x16 preserves the
Windows startup ABI if executed; independent immediate decoding/selftest PASS.
No physical writes and no Windows signed-image writes. Original x7 retained.
Direct native HVC return remains-2; subsequent A0 unchanged. This does NOT
prove trampoline execution or eightCPU PASS. It weakens entry-location-only
explanations, but context remains NONZERO, so context0 is NOT tested.

Next bounded hypothesis: context0 like Linux, while restoring original Windows
entry/context through owned memory. NVS cannot currently be read/written via KD;
do not bypass that guard or zero the Windows context without a restore path.
Consider an owned EFI runtime-code allocation with an explicit virtual-address
change event/ConvertPointer mapping proof, permitting virtual KD readback of
only owned literals. Audit/prepare offline before another launch; no speculative
address aliases or MmMapIoSpace calls before confirming initialization/IRQL.
This is still a local possibility, NOT a demonstrated privileged-trace blocker.

Original893... EFI restored/readback PASS after CID2218, evidence
smp-psci-first-cpu-final-rollback-20261011; actualVMs[]. No identical baseline
boot repeated (CID2215 already timed out). No active Windows Desktop currently.

Exact original EFI893B7859...402E8B restored via cold fixed-file rollback/readback,
evidence smp-legacy-eight-rollback-20261011. Normal oneCPU/4GiB control dispatched
kd-windows-original-restored-after-psci-20261011, no PSCI/clock experiments.
CID2215 is now TERMINAL: first winload notification followed by KD RESEND,
no complete response/state within210s, no Surface. Scoped owner stopped,
actual VMs[]. Rollback bytes PASS, runtime recovery NOT PASS. No repeat of
the same cold run without a new evidence-backed diagnostic reason.

Public contract references:
[Linux CPU_ON call](https://github.com/torvalds/linux/blob/master/drivers/firmware/psci/psci.c)
uses target/entry/context0; [crosvm MPIDR default](https://chromium.googlesource.com/crosvm/crosvm/+/072558a6e5a21c2481c624f5f6cf0a7bbd5d66d4/hypervisor/src/aarch64.rs)
uses affinity=vcpu ID. These are supporting references, not a trace of Samsung's
private hypervisor implementation.
# Prepared owned runtime mapping control

Candidate: `smp-psci-runtime-map-slot-build-20261011`, EFI8192B,
SHA256 `2C64EB289A63E7A193CBA17CB709ADECF39C7F7C85EB816E82F9E546DC4F9EFC`.
The original and fixed8 controls still reproduce exactly. Map/template tests
pass for both NVS and RuntimeServicesCode. The copied VA-change callback has
no COFF relocations and no branches/data references outside its own208B;
ConvertPointer is the only external call and is captured before VA conversion.
The earlier8704B artifact is superseded, not deployed. The8192B control omits
redundant successful CAM/SPCR/DBG2 and DS register dumps only; all validation,
DS restoration, failure paths and final DS/EFI load markers remain.

This is a read-only runtime mapping control, not a context0 CPU_ON test.
Require a real VA event, exact owned header and safe trampoline through KD
virtual reads before allowing a later bounded literals-only write/A/B.
No inferred kernel VA, no Windows page writes, no storage geometry changes.

# First secondary accepted with context0

`kd-windows-runtime-map-control-20261011`: first callback failed on UART MMIO
inside SetVirtualAddressMap; this was an OWN diagnostic error, not a Windows
regression. Original EFI restored exactly. UART removed; own68B callback now
stores ConvertPointer result and uses one guarded own BRK for observation.
`kd-windows-runtime-map-kd-control-20261011` exposed pre-kernel observer
restore_context `base=None`; guard fixed without guest changes.
`kd-windows-runtime-map-kd-observer-fix-20261011`: loader checkpoint and kernel
virtual header/template reads PASS. Original CPU_ON still returned-2.

`kd-windows-runtime-context0-ab-20261011`: own16B literals read/write/readback
PASS. Original affinity1, entry1178d3010, Windows context8000c000; HVC redirected
to FFFD1000/context0. Own LDR/LDR/BR restores exact entry/x0. Direct HVC result
**0** (previous original and nonzero-context own-entry variants returned-2).
Next unmodified secondary call still caused A0/-2. This proves acceptance of
the first secondary, not actual8CPU/Desktop or general-context root cause.

First runtime EFI SHA20A6364A39A5B58888923DDE027975A4B15771D6F652614397C48C872C8F4AF4
rolled back exact893... before next trial. Next bounded candidate allocates8
owned runtime pages, one per CPU, avoiding literal reuse/races. Candidate
SHA7F33A06FEE99DD373AB2CAB99A6CFABB8E883342882EC3F79FB6BA238363EA9E;
offline map/templates/PIC/launch-gates PASS. Current trial
`kd-windows-runtime-all-context0-20261011` must prove all native returns and
then actual Windows processor count/Desktop; results pending.

# New boundary: secondary PMU counter-set initialization

`kd-windows-runtime-all-context0-20261011` terminated: CPU1 HVC returned0,
then **5C(9000,1,14,0)** before the rearmed CPU2 observation. Not all seven
calls accepted; no actual processors8/Desktop proof. Original8192B EFI restored
SHA893... and readback PASS, VMs[]. Installed diagnostic APK8C76A217...
F9D5FB10A; only isolated own EFI/helper scope, no Ubuntu/D/commit changes.

Exact installed kernel disassembly narrows this new failure:

- RVA49fd34 reads MIDR;49fd38 PMCR;49fd3c extracts N[15:11] into w19.
- CPU index arrives in w20; primary initializes counter sets at49fd70.
-49fda4..49fe00 sums four counter-set count fields of primary PRCB+730.
-49fe04 compares hardware N against sum; if N is smaller,49fe0c..49fe24
  creates bugcheck5C with9000,CPU index,N,0.

Observed9000/1/20 therefore localizes to CPU1's hardware-counter count being
smaller than allocated total, subject to a direct exact-branch readback next.
This is **not a timer/display failure**, and should not be treated as a reason
to alter unrelated GIC/timer flags. Heterogeneous PMU exposure is the strongest
next hypothesis. Need a narrow read-only BSP/AP MIDR/PMCR/counter-set snapshot;
do not blindly clamp counters, skip fatal handler, or modify CPU model.

# Actual heterogeneous PMU mismatch and current runtime state

`kd-windows-runtime-pmu-count-boundary-20261011` read BSP N31/count31, then
exposed a KD observer bug: GET/SET_CONTEXT headers always selected CPU0.
At an AP breakpoint this returned frozen BSP context. Observer now routes
requests using the stopped-state Processor and verifies actual context PC.
Offline actual-method processor-routing and prior response-recovery tests PASS.

`kd-windows-runtime-pmu-cpu-routing-20261011` proved:

| Processor | MIDR | PMCR | N | Allocated counts | Compare |
|---|---|---|---|---|---|
| BSP0 |410fd821|f8c1|31|31,0,0,0|31>=31|
| AP1 |410fd811|a0c1|20|31,0,0,0|20<31 ->5C|

Common allocation A/B changes only one BSP allocation argument31->20,
not MIDR/PMCR/CPU features or the native secondary validation. Admission
requires the exact already-observed MIDR/N pairs and current owned runtime
profile. `kd-windows-runtime-common-pmu-ab-20261011` stopped before mutation
because BSP was naturally the known N20 core. Guard now handles BOTH proven
pairs: N20 keeps original allocation; N31 receives the one allocation A/B.

`kd-windows-runtime-common-pmu-heterogeneous-20261011`, CID2227: BSP naturally
N20, no allocation mutation. First CPU_ON accepted0, but no later AP/CPU2
milestone. Live H264640x480~13FPS Surface persisted to210s; own broker2628
snapshots seq0..3 report **processors1**. Therefore neither8-core online nor
the31->20 fix is proven. This positive live run does not replace the full goal.
Next targeted test must exercise actual N31 allocation, then localize AP-online
if necessary. Do not rerun natural20 blindly or infer8CPUs from crosvm args.

Script stopped owner at finalization, VMs[]. Original own EFI893... restored
with exact readback in `smp-common20-final-rollback-20261011`. No Ubuntu,
Windows user data, D writes, commits or publishing.

# Common allocation exercised; next boundary is secondary startup

`kd-windows-common-pmu-confirm-20261011`: BSP MIDR410fd821/N31,
one allocation argument31->20 actually applied. Native counter-set readback
is[20,0,0,0]; AP1 hardware N31 passes native comparison unchanged. CPU_ON1
and2 return0. No next PMU/CPU3/Surface milestone before210s; scoped owner
stopped at observer bound. The cap is diagnostic only, not production yet.

`kd-windows-smp-start-contexts-20261011`: BSP naturally N20, no allocation
mutation; CPU_ON1,2,3 return0. Late silence prompted exactly one own debugger
break, which produced no stopped state within10s. No CPU context snapshot
exists, and silence is NOT a proved guest crash. Owner stopped by existing
bounded finalization, VMs[]. No identical late-break retry planned.

Original EFI893... restored/readback PASS in
`smp-common-pmu-confirm-rollback-20261011`. InstalledWindows/Ubuntu/D intact.
Next locally testable observation: OWN AP-entry execution marker inside the
already-owned perCPU trampoline. No UART output (avoid corrupting shared KD),
no Windows entry-page overwrite, no random CPU/timer/GIC flags. Distinguish
PSCI acceptance from actual trampoline and native secondary entry progress.

# Offline OWN AP execution marker (not runtime proof)

`smp-own-entry-marker-build-20261011` EFI8192B SHA256
`597AC0561E32A1210BC030B1B4A4BC432E2AAB1BBACE945862D25368120EBE29`.
Separate builder switch requires runtime8-page variant. Header version2.
Each trampoline: MOV w0,1; ADR x16,ownmarker; STR x0; DSB SY;
LDR original x0; LDR original entry into x16; BR x16; NOP.
Offsets: context32, entry40, execution marker48, safe WFE56.
All accesses remain inside that CPU's own page. No UART interleaving, no
Windows code/data writes, no deliberate AP exception, no CPU/feature mask.
Final x0/x16 match old ABI and every other register is untouched.

Independent assembler produces exactly the C64B instruction/data template;
PC-relative targets and final-register oracle PASS. Runtime map/template
selftests PASS, original893... and fixed8 365... controls byte-identical.
Candidate is **not deployed or authorized by existing APK/apply gates**.
Next add separate exact hash/flag and version2 observer template guards.
Marker1 proves actual execution; marker0 at immediate HVC return remains
unknown because the secondary may not have started yet. Never equate0 with
failure without a later bounded observation. Current tablet originalEFI/VMs[].

# Execution marker runtime and observer-footprint differential

Version2 candidate597AC056...120EBE29 has separate exact-hash APK/apply gates,
header/template and changed literal-offset guards. Diagnostic APK SHA
6DB6052CF917B6FBD02DEA23BA687C8F99AEDBAC2FE8EA35682185E99283253C.

- `kd-windows-owned-ap-entry-marker-20261011`: native HVC returns0 for1/2/3.
  Own markers1/2 become1; marker3 is0 at immediate return. This proves execution
  for1/2 only. Marker3 remains unknown without later readback. No new stage or
  Surface by210s, scoped owner stopped.
- `kd-windows-owned-ap-deferred-return-20261011`: only call1 reached, later
  Surface live at210s. CPU3 differential ineligible; no claim this fixes SMP.
- `kd-windows-owned-pre-start-only-20261011`, CID2232: same candidate/platform,
  actual BSP allocation31->20; all secondary observations before HVC, NO
  immediate return or AP PMU breakpoint. A distinct earlier function-entry
  breakpoint avoids rearming the currently stopped callsite. Later call2 sees
  marker1=1; later call3 sees1=1/2=1. After submission3 no call4/Surface before
 210s. Post-start/AP PMU stops therefore do not alone explain this boundary.
  This does NOT prove CPU3 fails before own entry or that vendor trace is the
  only route. Do not repeat identical post-start/late-break diagnostics.

Original EFI893... restored/readback PASS, VMs[]. Read-only BCD extraction
SHA C1360923959FEDE56D680BCE73AC5BD98F1B70A6632F199430B1C12C78314CD6.
`audit-bcd-cpu-options.py` inspects17objects, active loader86df...; no explicit
UseBootProcessorOnly/NumberOfProcessors/ForceMaximumProcessors/config/group
limits anywhere. No BCD writes. Element definitions verified against
[Microsoft documentation](https://learn.microsoft.com/en-us/previous-versions/windows/desktop/bcd/bcdosloaderelementtypes).
All8 CPUs/1200p60 still unproven. Ubuntu/D/storage architecture unchanged.

# Mode naming and public-source audit

The app exposes one CPU or MATCH_HOST (all eight), not a two-CPU profile.
CPU1/CPU2/CPU3 in these observations are secondary affinity indices within
the same eight-vCPU launch. Accepted CPU_ON calls are not an online CPU count.

Read-only upstream audit, 2026-10-11: AOSP crosvm android15-qpr2-s4-release
GenieZone init_pmu is a no-op; get_system_regs is unsupported. The inspected
common-kernel GZVM driver rejects GET_ONE_REG with EOPNOTSUPP and delegates
guest hypercalls to its architecture handler. These public revisions do NOT
establish the exact Samsung implementation or explain the CPU3 boundary.
They do not justify another PMU flag sweep, a generic "SMP unsupported"
verdict, or claiming a host register snapshot is available through AVF.

Sources:
- https://android.googlesource.com/platform/external/crosvm/+/refs/heads/android15-qpr2-s4-release/hypervisor/src/geniezone/mod.rs
- https://android.googlesource.com/kernel/common/+/5727772a3852ca071768d686c334d96bfdc2cc72/drivers/virt/geniezone/gzvm_vcpu.c
- https://android.googlesource.com/kernel/common/+/5727772a3852ca071768d686c334d96bfdc2cc72/drivers/virt/geniezone/gzvm_exception.c

No runtime, firmware, CPU configuration, or Windows/Ubuntu data mutation
in this audit. Next useful evidence remains execution after CPU3 submission,
not a fresh launch-argument comparison or a two-core workaround.

# Native startup wait: CPU3 actually reached Windows startup flag

`kd-windows-third-secondary-wait-20261011`, CID2233, same marker EFI/APK:
BSP N31->20 allocation applied. One exact BSP wait observation after CPU_ON3
found own markers1/2/3 all1 AND native Windows startup flag1. Native
HalpInterruptWaitForProcessorStartUp returned4 with zero polling iterations;
next native CPU_ON4 was submitted. Thus CPU3 is NOT a fixed nonexecution
boundary. No stage after4/Surface by210s. Observer terminal; owner stopped;
original EFI893... exact rollback PASS (`smp-startup-wait-rollback-20261011`).

Exact image/pdb identify startup wait RVA481e50: byte flag read at481e84,
1ms KeStallExecutionProcessor call481e90, first return481e94, result481ebc.
mode0 permits10000 iterations; nonzero mode skips the finite bound. Probe
reads only that native flag, loop/result registers and OWN execution markers;
it never changes timeout/flag/state. Independent actual-handler offline tests
cover all7 affinities, three phases, read-only access and AP-context rejection.

Second run `kd-windows-secondary-startup-waits-20261011` extends wait
observation3..7 on the same candidate. BSP naturallyN20 (no mutation), but
only CPU_ON1 submitted before normal driver initialization resumes; no CPU3
wait comparison is eligible. Terminal with live Surface; own broker2632
samples0..7 report processors1, not all8. Late live filesystem snapshot lost
owner at its final check; treat it as partial. OriginalEFI rollback PASS.
This variability means next observation must also cover the first native
wait, not assume every cold boot reaches CPU3. Existing all1..7 handler
prepared offline; don't repeat an unobservable third-only wait blindly.

`kd-windows-first-and-next-startup-waits-20261011`, CID2235: all1..7 waits
observed, actual BSP31->20. Native startup flags for1/2/3/4 are1; each returns4
with zero polling iterations and own entry markers1. CPU5 wait flag0, own
marker5 remains0 at first1ms stall return AND after10000 native iterations
(~14.5s measured interval), result2. This rules out KeStall never returning
as the reason for this wait. No later stage/Surface by210s. OriginalEFI
893... restored exactly; VMs[]. A fixed CPU3-only blocker is disproved.

# Prepared PoC-publication A/B (not runtime proof)

Own adapter currently writes literals through BSP kernel VA, verifies them
through that same VA, then starts a secondary at the physical entry. There
is no explicit native PoC clean after these writes; initial EFI reserve uses
DC CVAU (PoU), not DC CVAC (PoC). This is a concrete publication gap, NOT yet
the demonstrated cause of CPU5 nonexecution. Do not claim stale literals are
observed: marker5=0 does not show what the AP fetched.

Separate candidate8192B SHA0347CB7BC2BB33C34C8940392EACB62795CDBE66D77FB081EEBFA29A88CC9007:
initial OWN pages clean to PoC; OWN40B static helper at firstpage+512 executes
four DC CVAC at own trampoline VA offsets0/32/64/96, DSB SY, ISB, own BRK.
Observer must restore the entire saved Windows CPU_ON context after helper,
not merely advance PC into unrelated code. Only OWN128B cache lines touched;
no Windows file writes, CPU feature/timer/GIC changes or user data reads.
Independent assembler/PIC/opcode bounds PASS; actual-return-handler all7
full-context restorations and28 bad-context rejections PASS. Original893...
and fixed8 365... EFI controls reproduced byte-identically. New explicit
hash/flag APK/apply/observer gates required; no implicit production enable.

Linux's documented MMU-off entry contract requires PoC publication and coherent
CPU entry; this motivates the isolated hypothesis, not a claim that the Linux
boot ABI is Windows' ABI:
https://docs.kernel.org/arch/arm64/booting.html

`kd-windows-native-poc-publication-20261011`, CID2236, actual BSP31->20:
all7 native publication helpers executed and saved Windows ABI restored.
All7 native startup flags1 and wait result4; final own markers1..7 all1.
This is first ALL-secondary execution/native-HAL-start proof. At immediate
CPU3/4 wait flag1 could coexist with ownmarker0, later markers1; do not treat
an immediate cached marker0 as proof of nonexecution. No later kernel stage
or Surface by210s. Original893... restored exactly; VMs[]. APK4A1D2488...
9BC06DD retained; previous6DB... APK preserved verified on healthy E.

Next same-candidate observation at KeStartAllProcessors RVA b36084 after
last successful HalStartNextProcessor result4. Public matching PDB confirms
KeStartAllProcessors start b35dd8. Native code polls [*[x26]+88] before
b360b0; read only that boot field and all8 CPU PC/SP/PSTATE at the LAST KNOWN
common milestone, not a late unresponsive break. No stacks/credentials,
no new timer/GIC/CPU flags. Actual-handler all8 read-only/selection-restore
offline test and explicit admission gates PASS. Runtime pending in
kd-windows-final-native-start-state-20261011, session72063.

That run TERMINAL, CID2237: all7 native starts repeat, loader native boot
field+88=0; b360b0 reached. SnapshotCPU1..6 PC27b218 resolves by exact public
PDB to KiFreezeTargetExecution+118: expected debugger stop, not a stuck
Windows initialization function. CPU7 all-zero PC/SP/PSTATE is unavailable
context, not evidence that CPU7 executed address0. No later stage/Surface
by210s; original893... rollback PASS, VMs[]. Next same-candidate probe
at KeStartAllProcessors b362dc (PpmInitHeteroEngine call), b363bc return.
Read only exact matching-PDB KeNumberProcessors e08a80 / KiBarrierWait e08c38;
offline actual-handler bounds/read-only PASS. Current run session4412,
kd-windows-post-start-ppm-boundary-20261011.

## Sequential post-start ladder (authoritative latest)

PPM sequential run terminal: all7 starts, KeNumberProcessors8/barrier1.
PpmInitHeteroEngine returned0; PpmUpdateArchitecturalEfficiencyClass returned.
The preceding simultaneous b362dc/b362e8 probes were INELIGIBLE: KD removes
breakpoints in its forward instruction snapshot. Use sequential adjacent
probes and32-byte active spacing. No PPM-hang conclusion from those old probes.

kd-windows-scheduling-hal-boundary-20261011 terminal: same exact candidate,
all7 starts; thread-cycle allocation and KiConfigureAllSchedulingInformation
completed. b36370 native target x15=kernel+4cdb50. Exact public PDB identifies
this as xHalTscSynchronization, NOT generic HAL initialization. Its tail calls
KeAdjustInterruptTime(0,1), which invokes KeIpiGenericCall with
KiCalibrateTimeAdjustment callback a982e0. Native b36374 return/Surface not
observed by210s. OriginalEFI893... rollback/readback PASS. No CPU feature,
timer, disk data, or signed Windows image changes in this observation.

Next unchanged-candidate observation: native time IPI call4f895c -> post-send
wait275f3c -> return4f8960. Read bounded pending count and CPU PC/PSTATE only.
No barrier/count writes or instruction bypass. Actual-handler offline test
PASS. Runtime evidence kd-windows-time-ipi-boundary-20261011; terminal result
must be recorded before treating this as a proven IPI blocker.

Both IPI trials terminal and original rollback PASS. Post-send count8 then
first native-loop iteration count8; no native IPI return/Surface within210s.
Stops take42-44s, so first-loop count is not sustained execution telemetry.
Physical ReadPhysical GIC samples returnedC0000001 (UNAVAILABLE, not zero).
Current observer-only minimal A/B600s retains only startup return after native
flags1..7 PASS; no PPM/IPI stops, identical adapters/EFI/CPU config.

Narrow candidate if minimal A/B fails: native Windows all-others capability
optimization vs Linux explicit affinity. Exact kernel 484d98 tests capability
bit5; its unmodified fallback 484dd0 constructs all-minus-self affinity.
HalpGic3RequestInterrupt 4af0fc..4af108 implements broadcast IRM, while Linux
gic_ipi_send_mask/gic_send_sgi builds explicit target lists:
https://raw.githubusercontent.com/torvalds/linux/master/drivers/irqchip/irq-gic-v3.c
This is a hypothesis, NOT a proven Samsung/GZVM bug. Separate opt-in A/B is
prepared: guarded native controller flags82b->80b only after actual all8
GIC metadata/affinities0..7 are read. No IPI/count/barrier bypass, CPU model or
timer change, no signed Windows files. One transient capability bit selects
existing native explicit-address implementation. Unknown flags fail closed.
Actual one-bit/negative guards, mapped GIC bounds/ordering tests PASS.
Not yet deployed; original rollback remains mandatory after each trial.

## Explicit-affinity A/B breakthrough

Minimal-observer600s A/B TERMINAL: no startup return/Surface; original rollback
PASS. Thus adjacent PPM/IPI stop sequence is not sole cause.

kd-windows-explicit-ipi-ab-20261011 RUNNING session53829,300s bound.
Native controller actual flags82b; native GIC context count8 and virtual
MPIDRs0..7, physical redistributors3fef0000..3ffd0000. One guarded runtime
capability82b->80b readback PASS selects existing explicit all-minus-self
affinity, not a target/count/barrier bypass. Time IPI call22109.933,
capability change22112.189, native time IPI return22112.519,
KeStartAllProcessors return22112.885: KeNumberProcessors8/KiBarrierWait0.
This is first successful native all8 start/barrier-release proof. Subsequent
normal Windows driver loads observed. User-mode processor count and live
Surface still pending; do NOT claim eight-core Desktop before those checks.

Mapped KD reads of redistributor SGI registers returned all zeros even for
known-active BSP; these snapshots are not sufficient hardware-register
truth and must not be used to invent a Group/enable fix. Exact native
controller metadata and return checkpoints are decisive positive evidence.

