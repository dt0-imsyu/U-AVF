# Windows runtime milestone A/B — 2026-10-08

Scope: one late logical checkpoint per identical cold boot, existing guarded
CAM and periodic CNTV workaround unchanged; no CPU/PMU/UART/balloon sweep,
no smss patch, no production Ubuntu change, no commit/push.

## Cold-boot identity

Both disposable Windows copies were restored to
`1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE`.
App-private disk cloned BEFORE shell control started, with full SHA verification.
No production disk was copied over or formatted.

## Results

| Checkpoint | Shell observation | APK observation |
| --- | --- | --- |
| IoInitSystem return b27fa8 | Reached; later native PMCCNTR SIGILL, not a fresh WinPE control | Boot-driver load observed; checkpoint not confirmed before KD silence |
| Driver-entry b2a1b0 | Reached; read-only register snapshot restored; CID2118 real Setup + 30 captured/encoded/locally decoded UVF1 AVC packets | KD stopped responding inside winload file requests, before kernel connection/checkpoint |
| Interrupt-init b1bf14 | Reached; snapshot restored; later c000021a/c000001d at smss+1253c, not WinPE PASS | KD stopped responding inside winload file requests, before kernel connection/checkpoint |

The shell control is not uniformly successful: native SIGILL also occurs in
shell-owned runs. Thus ownership is not proved the unique cause of every
failure, and a missing KD event is NOT proof of a Windows hang.

## Actual shell state

| Register/state | Driver-entry (fresh WinPE PASS) | Interrupt-init |
| --- | --- | --- |
| CNTV_CTL | 1: enabled/unmasked/not pending | 2: disabled/masked/not pending |
| CNTFRQ | 13000000 | 13000000 |
| DAIF | 40 | c0 |
| MIDR | 410fd851 | 410fd851 |
| ICC_CTLR | 88400 | 88400 |
| ICC_PMR | f0 | f0 |
| ICC_IGRPEN1 | 1 | 1 |

Different timer/DAIF values at DIFFERENT phases are not an A/B discrepancy.
Actual CAM and periodic thunk bytes were read and matched the installed fixes.
All physical GIC KD ReadIOExtended requests returned c0000001: unknown, not zero.

Existing independent firmware serial markers match through StartImage:
GICD_CTLR=12, GICD_TYPER=50, GICR_WAKER=0, MPIDR=80000000,
DS probe readback=50, restored CTLR=52, EBS callback registered.
Shell additionally emits EBS_GICD_CTLR=40 and EBS_DS=PASS. App does not emit
these markers in the last two runs. This brackets OBSERVABLE progress, not
a proven instruction-level divergent boundary.

## Independent evidence / wire audit

Driver-entry app: host TX and native ingress log SHA equal
9D64D2230C22F36504E6A70347EDAE57B474BF9F146473C5071DD21793010FC9.
Host RX and native serial file SHA equal
EB894614CE25FF9C13CB5F9F21CE788125EE92BE1C0D1DB419B090DBAFF769CC.
Equality proves byte delivery to the helper/log, not necessarily UART consumption
by Windows. No timing/pacing change was made.
Interrupt app last packet is KD RESEND followed by retransmission of the last
host-file reply; no later complete target packet observed.
Read-only stopped private-disk inspection found NO UAVF-DISPLAY directory:
no independent evidence that the WinPE producer executed in that run.
This absence does not distinguish bootloader wait from early-kernel failure.

## Evidence locations

Under `build-logs/windows-post-linux-reassessment-20261004/`:

- `kd-winpe-single-driver-state-shell-20261008-runtime/`
- `kd-winpe-single-driver-state-app-20261008-runtime/`
- `kd-winpe-single-interrupt-state-shell-20261008-runtime/`
- `kd-winpe-single-interrupt-state-app-20261008-runtime/`
- `winpe-display-evidenceSingleDriverShell20261008/`
- `winpe-display-evidenceSingleInterruptApp20261008/`

Both machine-readable runtime comparisons mark `comparable=false`, not
"runtime equal". Failed/absent observations are explicit. No semantic fix is
justified by this data. App-owned WinPE/Surface remains unproved.

## Next precise boundary

Need independent app-owned progress observation between the last winload file
request and the EBS callback BEFORE another HAL/driver checkpoint can be useful.
Do not repeat these late probes or infer a GIC/timer mismatch from shell-only
samples. Vendor-only cause has NOT been established. Do not patch smss or sweep
launch flags to compensate for missing observability.
All diagnostic VMs stopped; authoritative Running VMs: [].

## Resumed pre-EBS observation

Downloaded official Microsoft `winload_prod.pdb` using the PE CodeView GUID
D17823BF-65E9-9F0E-1895-4689D54842D4 and age2 symbol-server key. Returned PDB
GUID matches but PDB age4 vs PE age2: do NOT claim exact type-layout parity.
Public function addresses independently corroborated against PE exports/code:
BlBdStart213f20; BlBdPullRemoteFile214150 constructs FileIO API3430/type11 and
calls BdSendPacket at2142cc / BdReceivePacket at21431c. Kernel transfer1080
changes stack, calls architecture setup and branches to x20 at109c.
Retained app RESEND/silence belongs to loader debug file transactions, not a
confirmed kernel/HAL exception.

Next pair uses `--loader-state`: seven read-only GIC queries at the EXISTING
natural winload module notification. No additional breakpoint, instruction,
register, pacing, feature or boot-parameter changes. No later HAL/IO probes;
unchanged proven CAM/timer RAM fixes still apply on kernel connection. This
tests actual earliest shared GIC state with less instrumentation; not a
production workaround or WinPE PASS until real evidence exists.

Result: `kd-winpe-loader-read-state-shell-20261008-runtime-valid` and
`kd-winpe-loader-read-state-app-20261008-runtime-idlefix` both reached the natural
winload stop. All seven GIC queries returned c0000001 in BOTH owners: no actual
register values, no GIC equality claim. Neither run produced EBS markers or
kernel connection. This regression boundary is not exclusive to app ownership.
Initial shell invocation without suffix valid failed argument parsing before
any VM creation (PowerShell single-element array became a string); corrected
with explicit string[] and observer exit-code guard. Do not count it as boot.

Found/verified observer defect: packet(timeout=60) counted total time serving
bootloader FILE_IO/PRINT as silence. Shell had actual new requests 24 seconds
apart yet was stopped only ~35 seconds after its last request. Changed only
the observer inactivity window to restart on complete FILE_IO/PRINT messages;
overall deadline still enforced. `test-kd-idle-bound.py` executes the real
KD.packet implementation with a virtual clock: activity through65s passes a
30s idle limit; true30s silence fails; absolute55s deadline still fails.
App using corrected observer still became genuinely silent for60s after
FILE_IO/RESEND at qcgpio_i.sys. Thus timeout correction is NOT a WinPE fix.
No new hardware/guest semantic difference proved; next action must account
for loader debug transaction stalls common to both owners, not blindly repeat
post-EBS probes. All VMs stopped; production disk and guest signed files intact.

## KD acknowledgement/replay audit and bounded correction

Retained audit `loader-file-wire-sequence-audit-20261008.json` covers fresh
shell WinPE PASS and three failing runs. No malformed/truncated/checksum-bad
host or target packets detected. Repeated target IDs contain IDENTICAL bodies,
not a new request discarded due to ID reuse. Do not change PID negotiation
based on speculation.
Exact winload BdComSendPacket21cb68 waits for ACK via BdReceivePacket at21cde8.
BdComReceivePacket21c5e8 checks ACK IDs at21c93c and explicitly requests RESEND
when data arrives while awaiting ACK (21c980..21c9b8); it also requests resend
on invalid/truncated/checksum-bad data at21cad8. Therefore RESEND does not by
itself identify which failure happened. It proves target protocol activity.

Host observer lacked bounded automatic retransmission of an unacknowledged
reply. Added pending packet/ID state: at most3 automatic byte-identical retries,
explicit target RESEND/duplicate FILE_IO replay, ACK/reset cancellation. New
target progress settles the prior exchange if ACK was lost. No new command,
CPU/GIC/timer/PMU flag, serial pacing change or Windows instruction patch.
Offline virtual-clock tests use the ACTUAL KD class: silence/absolute bound,
exact bytes/ID,3 automatic retries, explicit RESEND and duplicate handling,
ACK/RESET cancellation all PASS. Not proof that this fixes Windows.

Prepared a fresh identical pair for a single driver-entry snapshot, evidence
prefix `kd-winpe-single-driver-*-ack-replay-20261008-runtime`. Results pending.

ACK-replay result: shell CID2124 connected to kernel but stopped responding to
first kernel read; no driver-entry/WinPE control. APK reached driver-entry,
CAM and periodic fixes verified, and native read-only state captured:
CNTV_CTL1/frequency13000000/DAIF40/ICC_CTLR88400/PMRf0/IGRPEN1=1,
deadline delta25827. Against retained fresh shell WinPE driver-entry sample,
these match; MIDR differs (shell410fd851, APK410fd821). This is a reference
comparison, NOT same-run-pair causality; heterogeneous identity was already
known. Do not arbitrarily mask features or modify CPU model from this alone.

APK post-snapshot failure is precisely host SetContext API3133, body968 bytes
(912-byte saved context + KD header), total wire985 bytes, repeated on target
RESEND with no complete reply. Code restore precedes it, but full-context
rollback NOT verified. Diagnostic VM stopped immediately; cannot classify
subsequent Windows boot as FAIL from this censored run. Actual captured state
comparison retained as reference-shell-app-runtime-diff.json with rollback
caveat. No new proven GIC/timer runtime discrepancy.

Next isolated APK test retains ONE restored driver-entry breakpoint but drops
the optional MRS/SetContext snapshot, since native state is now captured and
the observer write is the first proved failing transaction. No VM parameters
or production files changed. Evidence:
`kd-winpe-single-driver-app-no-context-write-20261008-runtime` (pending).

No-context-write APK result: kernel connected, CAM/periodic fix verification
PASS, single driver-entry breakpoint reached/restored, CPSR60001044. No native
MRS/code substitution/SetContext request executed. Later real c000021a with
underlying c000001d in Session Manager, user PC7ff61c6c253c, context464b87ed60.
PC is consistent with the previously byte-verified smss1253c PMCCNTR site;
this run did NOT independently read the user instruction bytes, so do not claim
a new complete exception/core capture. Viosock/helper/Setup not reached.

This separates two failures: previous sample rollback transaction985bytes is a
diagnostic failure; the sparse run exhibits a real Windows userland SIGILL.
Post-EBS/kernel/boot-driver app-owned execution is now positively observed,
not inferred from launch arguments. Timer/GIC CPU-interface state at the shared
driver-entry sample matches retained shell WinPE. New MIDR difference is NOT
new project evidence: the same821 vs851 mismatch and equivalent PMU state were
already documented in WINDOWS_APP_OWNER_REGRESSION_2026-10-07.md. Do not reopen
the explicitly closed PMU/CPU/preentry-smss experiments automatically or relabel
them as a fresh discovery. No actual GICD late-register values were available.

Current boundary: app driver init -> real Session Manager c000001d. Further
low-level PMU investigation requires explicit reopening of that user-closed
scope or genuinely new supported evidence. No guessed GIC/timer/CPU fix is
justified. All diagnostic VMs stopped; goal remains incomplete, no commit/push.
