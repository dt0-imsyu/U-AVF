# Windows display producer: actual accept failure

Authoritative pre-fix run: `kd-winpe-producer-fault-20261008-runtime`, CID2108.
Image D05B10B95A533EF7D6CD62FBA4DD46D8EB610980A4DF12F723E563F78DEFBB5D.
Read-only evidence: workspace-parent
`build-logs/windows-post-linux-reassessment-20261004/winpe-display-evidenceProducerFault2108`.

Own first-chance logger captured:
- exception C0000005, read at address0 (info0=0/info1=0);
- module X:\Windows\System32\viosocklib.dll;
- PC=module+0x31d0, LR=module+0x22c8;
- instruction b94002a5: LDR W5,[X21]; next comparison is length>=12;
- supervisor exit255; stderr empty; no Android frames;
- Setup/SetupPrep/SetupHost active again, signed drivers problem0, 30 local AVC
  packets valid. Previous2107 Setup failure is not current authoritative state.

Shipped w11/ARM64 DLL SHA256:
4843B4A205EE197F3D72FAC31E64ABA415098A322A448F8FB935FCC67A70B475.
Path: handoff-compact-2026-08-23/handoff-compact-2026-08-23/windows-headless-media/
virtio-attestation-full/w11/arm64/viosock/viosocklib.dll.
Its disassembly matches the captured32bytes at0x31d0. At0x22c4 it calls0x3160,
returning to0x22c8. This shipped provider unconditionally invokes its peer-name
path, which unconditionally dereferences addrlen. Our accept(NULL,NULL) is the
trigger; upstream master accepting optional outputs did not prove shipped binary
equivalence. Earlier source-only dismissal of this branch is superseded.

Minimal own-helper fix: stack-allocated12-byte VsockAddress and int length=12
passed as both accept outputs. No signed DLL/driver edits, no instruction patch,
no CPU/PMU, timer/GIC, firmware or storage change. Preserve first-chance logging.

Prepared control image1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE.
Six-block delta39AF8E5EDD692B1BD11C046C1AD413EA7CE338646F76F25F982DC9B4B2625FD0.
Run `kd-winpe-producer-accept-fix-20261008-runtime` is in progress at this entry;
do not call runtime transport/display PASS until fresh clock/frame/Surface evidence.
Still a30-frame replay proof, not stable live stream. Next after proof: live
capture/reconnect/input using existing common UVF1/MediaCodec path.

## First corrected run, external interruption

CID2109 lost ADB at driver-load stage before the viosock display-ready host
trigger. Local observer became terminal (ADB runner closed), but owner PID1501 /
VM CID2109 were verified still alive after reconnect; did not infer VM death.
Cooperative stop marker then VM[] verification, followed by read-only extraction
`winpe-display-evidenceAcceptFixDisconnected2109`.

Fresh guest logs: no first-chance exception; listener enters accept and reaches
our45000ms deadline, supervisor exit4 instead of255. Capture/local codec30frames
still valid, real Setup page. Thus null-read regression did not recur, but no
Android channel was activated: actual transport/display not yet proved.
The exact same1E9D candidate is retried as
`kd-winpe-producer-accept-fix-recovered-20261008-runtime`, without global termination
tracing. This retry is justified by observed USB interruption, not silent polling.
