# Isolated Windows app-owner boot regression

Status: unresolved; CPU/PMU diagnostics stopped by user after bounded A/B.
Do not turn diagnostic harness recovery into the product objective.

## Current decision — bounded A/B closed

Verified directly in the two retained `events.jsonl` files:
`kd-winpe-owned-platform-pmu-readonly-20261007-runtime` event367 and
`kd-winpe-shell-platform-pmu-readonly-20261007-runtime` event376.
DFR0=0x100f11f310305719, PerfMon=7, PMUSERENR=0, PMCR=0xf8c1 match.
MIDR differs (app0x410fd821; shell0x410fd851), but both observed runs fail;
there is no evidence-backed CPU/PMU delta to fix. This observation is at one
EL1 checkpoint, not proof that all EL0/PMU state is equivalent throughout boot.
The instrumented shell is NOT the known-good control.

No further CPU/PMU, UART pacing, balloon or display-order A/B is authorized by
this task. Do not patch SMSS or guess a CPU/PMU feature workaround. Keep this
app-owned boot failure as an independent unresolved regression.

Practical path: retain proven shell-owned WinPE boot and delegate its formal
AVF connectVsock PFD to the existing Android decoder. The latest shell-owned
run reached Setup again without the PMU probe or SMSS patch. Binder PFD unit
test passes and an actual4055 PFD was returned, but no Android frame has yet
been received/presented: this is NOT a working tablet display milestone.
Fresh guest evidence `winpe-display-evidenceAVFClientResult/vsock-report.txt`
contains LISTEN_READY4055/CID2100 only; no accept/clock-ack/frame-send result.
Next narrowly scoped transport test should distinguish select -> accept ->
clock handshake in our own guest helper, without touching boot/CPU/firmware.
Old signed viosock/select behavior is a hypothesis, NOT an established cause.
Preserve A40B candidate and existing evidence; do not silently overwrite them.

## Proven good control

Qualification 2026-10-08: new shell-owned direct-accept candidate run
kd-winpe-shell-direct-accept-validated-20261008-runtime failed C000001D in
wininit.exe BEFORE our helper executes, without a new PMU probe or SMSS patch.
Actual crosvm arguments match prior successful shell-owned CID2100 run aside
from process/CID identities. This is not evidence that helper accept logic
caused the early exception, nor proof that failure is exclusive to APK owner.
Original fault PC/instruction remains unknown (retained PC is a debugger
breakpoint); do not infer PMCCNTR. Do not reopen the stopped CPU/PMU sweep.
Diagnostic VM stopped; production Ubuntu untouched; no stream/input PASS.

`kd-winpe-shell-parity-control-20261007-runtime` with original paced shell owner,
pristine A40B5E60...06D7ACCA5 disk, exact wrapper93EDA7C4...95F234 and existing
guarded CAM/periodic CNTV workarounds reached real Setup. Fresh retained
`winpe-display-evidenceShellParity`: nonblack640x480 screenshots;30 captured,
encoded and locally decoded UVF1 frames; listener4055/CID2092. No SMSS patch.

## Differences audited

Same kernel/wrapper/disk identities, oneCPU4GiB, noGPU, noNetwork, same firmware,
same CAM geometry/thunk and timer workaround order. Initial APK balloon=false
was changed only for diagnostic shell parity; actual crosvm CPU/SVE/no-pmu,
PCI/memory/serial/balloon/disk arguments match shell, aside from identity/fds/CID.
Balloon parity did not fix SIGILL. Deferred display connections did not fix it.
Java8byte/1ms pacing stopped in boot debugger and does not prove a PMU fix.

## Terminal read-only A/B

| Launch | MIDR | ID_AA64DFR0 | PerfMon | PMUSERENR | PMCR | Outcome |
|---|---|---|---|---|---|---|
| APK owner | 410fd821 | 100f11f310305719 | 7 | 0 | f8c1 | SMSS C000001D |
| Shell with same observation | 410fd851 | 100f11f310305719 | 7 | 0 | f8c1 | SMSS C000001D |

Read-only MRS probe at IO return used exact assembler-verified instructions,
then restored original kernel16bytes/full context. Still, sampled shell did NOT
reproduce the known-good outcome. No conclusion that CPU/PMU is causally ruled
out at all times: this is one checkpoint, not an EL0 transition trace. No observed
PMU exposure delta explains app failure; both MIDRs can fail. Avoid speculation.

SMSS exact failing instruction: d53b9d08, MRS x8,PMCCNTR_EL0, RVA1253c.
No signed userland file or RAM instruction was patched in these parity tests.

## Next / boundaries

Stop further PMU/CPU/timing experiments now. Keep signed WinPE/Setup as product
target, shared UVF1 H.264 decoder, and existing AVF ownership/permission model.
Look for supported VM-owner connectVsock / ParcelFileDescriptor transfer to
Android viewport; never retry denied shell AF_VSOCK or change SELinux.
No production Ubuntu mutation, commit, push or publication.
