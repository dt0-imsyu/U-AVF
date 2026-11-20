# Isolated Windows PMCCNTR contract — 2026-10-08

## Evidence and decision

Human explicitly reopened this narrow investigation to obtain Setup inside APK.
Production Ubuntu, original Windows media, launch args and signed smss bytes
remain unchanged. No commit/push.

Exact smss SHA B2129DA7BCE9CB1175E498EC270C4CA4532052A51098250F5AEA32CEDA03694D:
RVA1253c reads PMCCNTR directly when private cookie seed RVA2f1e0 is zero.
The zero-result fallback calls imported NtQueryInformationProcess, class36.
There is no CPU-feature predicate at this instruction. This is not evidence for
changing QPC/BCD settings blindly.

Exact kernel SHA C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734:
HalpCycleCounterInitialize RVA4a20e0 explicitly ORs PMUSERENR.CR (bit2).
ArmInitializeProfilingHardware RVA49e768 clears EN/SW, sets ER, preserves CR.
These public symbols were checked against local PDB and actual instructions.

First native A/B: `kd-winpe-app-pmu-cr-native-20261008-runtime`.
At established driver-entry checkpoint: PMUSERENR before0, requested4, after4,
PMCRf8c1. The write genuinely persists immediately; it is NOT RAZ/WI here.
Native stack/GPR restoration and original checkpoint restoration succeeded;
no large SetContext write. Later real smss fatal c000021a/c000001d remains,
PC7ff620d6253c. Early permission alone is NOT a fix. This does not distinguish
later permission restoration from additional EL2 access trapping.

Upstream crosvm GenieZone reports ArmPmuV3=false and init_pmu as unsupported:
https://android.googlesource.com/platform/external/crosvm/+/853aaa9bdb28774fd82e7cfa4c910c95b0acaa5d/hypervisor/src/geniezone/mod.rs
This source is architectural context, not proof of every Samsung implementation.

## Native fallback proved through smss; next boundary localized

`pmu-counter-emulation.S` is an isolated RAM-only adapter at exact
KiSynchronousException RVA2ba430. It recognizes ONLY an EL0 trapped MRS
PMCCNTR_EL0 and supplies CNTVCT as a monotonic fallback.
This avoids modifying signed Windows userland or exposing broad PMU writes.
It is NOT a working PMU or correct CPU-cycle profiling claim.

Other opcodes, exception classes, EL1 callers and unreadable user instructions
return to the original handler. User code is read
through exact fault-safe KiEmulatedReadDword RVA620850. Trap Pc/Cpsr/Esr and
all destination register layouts verified against native KiEmulateReadRegister:
X0..17 at trap+a0; X18 at trap+130 for EL0; X19..28 in exception frame;
X29 at trap+140, X30 at trap+138; XZR discards the value.

Thunk is independently assembled, branch-relocated and bounded to unused final
executable page zero-fill tail at RVA665c40, separate from CAM/timer thunks.
This is beyond section raw bytes but inside the same executable mapped final
page; actual mapped bytes must all read zero before installation.
Prologue, cave, helper and write readbacks are guarded. Native PAC/stack/GPR
unwinding; no full SetContext. One breakpoint only on the first handled read
records actual trap/opcode destination/counter/PMUSERENR, then is removed.
The original user instruction remains intact; saved PC advances exactly4.

Offline predicate/relocation tests PASS, but are NOT runtime PASS.
Test: `kd-winpe-app-pmcc-trap-adapter-20261008-runtime`.
First attempt stopped at an offline cave-range guard before installing the
adapter (NOT a Windows regression). Cave moved into file-backed padding;
corrected test: `kd-winpe-app-pmcc-trap-adapter-bounded-20261008-runtime`.
This test DOES prove native smss recovery: PC7ff6e2e4253c, ESR6230e51b,
EL0 CPSR80001040, Rt8, PMUSERENR0, CNTVCT12bd7763 written into saved X8.
Windows then progresses to wininit.exe. New native exception is again PMCCNTR,
bytes179d3bd5 (MRS X23), PC7ff76e6dfc58. The first adapter deliberately covered
only X0..17, so this is a measured coverage gap, not another launch regression.

Exact register mapping was expanded per the already disassembled kernel
accessor, without modifying userland or adding CPU/config flags.
Full register test: `kd-winpe-app-pmcc-all-registers-20261008-runtime`.
Adapter installed/read back, but KD stops in a LOAD_STATE/RESEND transaction
for Null.SYS before any user emulation hit. Censored by diagnostic connection,
NOT proof that expanded adapter failed or Windows hung.

Next: `kd-winpe-app-pmcc-display-quiet-20261008-runtime`. Same complete adapter,
same launch/disk bytes, no emulation observation breakpoint. Exact exported
DbgLoadImageSymbols RVA2fbfa0 (void diagnostic notification only) returns without
the repeated KD symbol-load stop. Kernel driver initialization is not patched.
The app's existing own connectVsock/MediaCodec client starts after guarded kernel
patches and retries until the actual guest listener, independent of KD module
notifications. No shell socket or AVF FD delegation. Runtime pending.
On first proven app-owned Setup frame: stop PMU diagnosis and proceed to shared
live H.264, input and safe isolated installation.
