# Windows producer exit localization

Run: `kd-winpe-producer-exit-20261008-runtime`, CID2105.
Image SHA256: 0AEE1BF9E2876DA898C55368943CAC0D7E51959CE4DF21A7AE57FDBF143F10EA.
Own supervisor only changed from prior real-source candidate; same platform.

Evidence: workspace-parent
`build-logs/windows-post-linux-reassessment-20261004/winpe-display-evidenceProducerExit2105`.

- WinPE/Setup userspace reached, Setup/SetupPrep/SetupHost remained active.
- Signed viostor/viosock PnP problem0.
- 30 GDI/AVC/local-decode proof packets, 640x480; source screen still the Setup
  driver-discovery page, not interactive normal install flow.
- Guest listener AF40/CID2105 reaches ACCEPT_BEGIN, then no accept-end.
- Parent supervisor records PRODUCER_EXIT=0x000000ff, WAIT=0, QUERIED=1.
- Android received/decoded/presented0/0/0. No Surface readback exists.
- Observer own idle break was resumed; later bounded observation timeout.
- Owner cleanup verified `Running VMs: []`; read-only remote FAT extraction then
  recovered reports/screens without a full9GB image pull.

Interpretation: process termination is now proven, not merely a missing process
inventory entry.255 is not any explicit helper return or watchdog exit4.
Exact cause remains unknown: do not call it SIGILL, invalid parameter, provider
crash, or a vendor defect without fault/runtime diagnostics.

Upstream VIOSockAccept checks optional addr/addrlen and permits NULL/NULL:
https://github.com/virtio-win/kvm-guest-drivers-windows/blob/master/viosock/lib/viosocklib.c
This source audit rules out assuming that NULL/NULL is intrinsically invalid;
it does not establish equivalence with the shipped signed binary.

Prepared minimal observation improvement: own bootstrap redirects producer
stdout/stderr to vsock-stderr.txt via dedicated inherited handles; own producer
routes CRT error mode to stderr, avoiding lost modal runtime diagnostics.
Both compile successfully; not deployed, not a claimed fix. Preserve production
decoder, platform, timers, signed drivers and Setup. No new full raw clone.

Next: include these two own helpers in a compact audited BOOT.WIM block delta,
one bounded run, read stderr alongside exit report. Once socket termination is
localized/fixed, deploy the already prepared live encoder/input path. Actual
tablet stream, input, installation and installed boot remain incomplete.

## Stderr control completed

`kd-winpe-producer-stderr-20261008-runtime`, CID2106.
Image SHA680B2A20078273444A3B69881859A7F51EB45A3564E53BDDD698F584C3BD2720.
Six1MiB changed blocks, forward/reverse deltas retained; original0AEE can be
restored using reverse delta. Current PC scratch was renamed/updated, not cloned.
Own two helper replacements only, paired EFI/GPT/install SWMs unchanged.
Host manifest/Android full image SHA guards passed before VM creation.

Fresh evidence folder `winpe-display-evidenceProducerStderr2106`:
vsock-stderr.txt exists but is empty; exit255 repeats at ACCEPT_BEGIN.
30 captured/encoded/locally decoded AVC packets remain valid, no Android frames.
Setup/SetupPrep/SetupHost and signed viosockwspsvc are active in native inventory;
own winpe-vsock process is absent. No proof of faulting PC or terminate caller.
Cleanup VM[] verified before read-only extraction. C/D/E still >50GiB free.

Do not repeat this stderr test or label the cause CRT/PMU/vendor. Next obtain a
bounded termination/fault record for the own helper. Existing matching kernel
PDB public NtTerminateProcess: section9 PAGE offset2662304, RVA0x8effa0.
Original disassembly: d503237f PACIBSP, a9ba7bfd STP. Kernel SHA remains exact
C667739004D1186DACA2FD3FE7CCDE61394BC068F385B4BB8CD5364D3005A734.
No new termination breakpoint has been installed yet; process identity, syscall
status and user caller/trap context must be observed, not altered.

## Bounded termination observer completed (supersedes prior next step)

Same stderr image680B..., CID2107, run
`kd-winpe-producer-termination-20261008-runtime`. After signed viosock load,
NtTerminateProcess entry/entry+16 checkpoints alternated, restoring each original
instruction before resume; <=24 calls allowed. No exit code/register changes.
Actual kernel image hash guard unchanged. PDB GUID matches PE RSDS GUID
36D33E64-9D94-FF98-D448-13A0F326BB4F (PDB info age6 vs PE age1 noted; function
entry bytes and process accessor disassembly independently checked).

Captured: WerFault.exe NtTerminateProcess(handle0x154,status0xff). There was NO
own-process self-exit call hit. This points toward WER-mediated crash handling,
but the target handle was not resolved: do not claim exact target or fault code.
Producer's durable supervisor independently again records exit255 at accept.
Stderr remains empty, Android0frames. Extraction evidence:
`winpe-display-evidenceProducerTermination2107`.

Important separate regression: Setup launcher exited C190011F in this run,
final inventory has no Setup/SetupHost, screenshots are purple WinPE desktop.
No proof that termination instrumentation caused it; prior same image run2106
retained Setup. Setup stability/source flow still unproven. No install occurred.

Owner initially appeared in cleanup listing, then authoritative recheck showed
VM[] and owner PID29496 gone; only then remote read-only extraction performed.
Do not treat the transient cleanup listing as a stopped VM.

Next prepared minimal observer: own winpe-vsock AddVectoredExceptionHandler
records original first-chance code, PC/module/RVA, instruction bytes and SP/LR
using native write-through file IO, returns CONTINUE_SEARCH without changing any
instruction or exception outcome. Compiles PASS; NOT deployed/runtime tested.
One own-helper WIM delta should replace further global termination sweeps.
