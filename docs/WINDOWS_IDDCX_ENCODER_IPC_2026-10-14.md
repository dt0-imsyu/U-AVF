# Isolated IddCx to encoder IPC

## Same-worker reconnect: actual guest PASS

2026-10-14 finite SYSTEM headless test, unchanged baseline encoder/8CPU/native
GOP. New42F1D3F3 driver admitted (STATUS0x180600a, PROBLEM0). PID4416 served
three consecutive connections and twelve complete1920x1200 BGRA frames, each
9216000B, distinct CRC32, sequence strictly increasing7..37. Exact final marker:
`GUEST_PIPE_RECONNECT_RESULT=0 CONNECTIONS=3 SAMPLES_PER_CONNECTION=4`.
This proves repair of the dropped-client endpoint in the actual UMDF worker;
it is NOT user Desktop or sustained FPS proof. No authentication automated.
Driver/test trust removed, three temporary own C payloads restored, cold owned
socket rollback/readback PASS. No encoder/codec/firmware/Ubuntu/main APK changes.
Evidence: `E:/U-AVF/artifacts/idd-headless-reconnect-cold-20261014`, bounded
observer `kd-windows-idd-headless-reconnect-20261014`; runner exit0, VM stopped.

## First encoded stream — important confound

Native1920x1200/8CPU, same driver/receiver source, source_only_probe OFF;
no competing probe client. Android existing c2.mtk.avc.decoder received,
decoded and emitted1025 render callbacks. First six5s guest intervals:
1023/30.078s=34.0116 capture/encode FPS, source1288/30.003744s=42.928/s.
Source publish/copy callback1.3144ms. Android pending0/1, callback delay6–9ms.
Momentary46/s is not sustained. Callbacks are not physical display fences;
SurfaceFlinger latency command returned only current idle refresh period.
Screenshot after stimulus/window shutdown is black, not proof of desktop.

Crucially, codec selected=0/load_error2/selftest-1: separate UAVF8.DLL missing.
Actual codec original OpenH264 fallback,16–19ms/frame. These metrics do NOT
test x264. New opt-in IDD build fails explicitly if exact x264 not admitted.
Build00BC84EDC80ACB0D6C1A7366939023B81B8B6085C26F4ADD51B195EB7939413A
passed UVF1/conversion tests; not deployed. Do not optimize other layers first.

Evidence: E:/U-AVF/artifacts/idd-encoded-run-20261014/android-display-live.txt
(preserved before reconnect reset), idd-encoded-cold-20261014 own source logs.
Guest driver/trust/3 package files restored, observer43573 terminal,
encoder4161f240/socketce89182f cold rollback/readbacks PASS. Ubuntu unchanged.

Correct next gate: deliver same previously benchmarked843264B x2640463F4...
using fresh exact native plan. Read-only plan795C7E46... at
E:/U-AVF/artifacts/idd-x264-fat-readonly-plan3-20261014, zero guest writes.
Native EFI10752B/two-cluster[994732,994747]/sha359DED... is preserved; old
single-cluster plan assumptions rejected before writes. Root slot9978624,
free103-cluster set differs from old plan. Extend typed transaction with a
separately pinned native profile/negative tests before apply. Old plan must
not be reused blindly. selected1/selftest0 required before repeat metrics.

## Existing encoder integration (host-built, not guest deployed)

`windows-idd-source.cpp/.h` supplies a C ABI to the existing C encoder.
SYSTEM/token and exact admitted WUDFHost/image/loaded-driver SHA gates reused.
Own9.216MB buffer returned only after complete bounded validated read; lifetime
ends on next call/close. No driver-owned pointer crosses processes.
`build-windows-encoder.ps1 -ExperimentalX264 -IddPipeCapture` is mutually
exclusive with old capture A/B flags. Live mode uses this source only and
does not open GDI/DXGI; non-live codec fixture remains unchanged. Existing
BGRA→I420/x264/UVF1/stdout path retained. Android/vsock unchanged.
Idle poll iterations do not consume UVF frame sequence numbers. Source labels
correctly report IDD_LOCAL_PIPE; failure is explicit, not hidden fallback.

Reader now preserves connection on timeout only at an empty packet boundary
after proven cancellation completion. A completion that raced cancellation
consumes actual bytes instead of losing its header. Partial-frame failure
still closes; normal static desktop can resume without a new swapchain.
Actual host full-frame→idle timeout→next-full-frame same-channel PASS, with
pixel equality; previous PID/image/cancellation/backpressure tests PASS.

ARM64 encoder64000B fits existing79872B slot, build/link SHA
43A112BACB1B6D900B53BFDEDED4AD99D188348FF4E06331A2F2616C44ADF1F6,
E:/U-AVF/artifacts/idd-x264-encoder2-20261014. No-IDD x264 rebuild and real
UVF1 diagnostic ordering/BGRA-pitch conversion tests PASS. NOT guest deployed,
not encode performance/Android-frame PASS. No VM restarted for host checks.

Next exact bounded A/B: signed-driver finite stage without competing probe
client; source_only_probe OFF so existing Android H264 client connects; cold
own encoder backup/readback/rollback. Keep cert clock fixture and new once/
unique backup suffix. Real desktop must then be shown on Idd monitor, not
present secondary test stimulus as product completion. Source60 still open.

## Guest channel PASS — 2026-10-14

Evidence `E:/U-AVF/artifacts/idd-pipe-clock2-cold-20261014` (actual stopped VM
bounded own-file read, NOT a claim of crash-consistent NTFS). Same live result
already read in observe3. Own source/probe/cleanup records recovered.

SYSTEM receiver verifies actual WUDFHost4156, exact image and loaded B15D...
DLL file SHA.1275 packets/389963879 QPCticks at13MHz =29.997221s,42.5039/s.
19source sequence gaps,1274sample changes, native RESULT0. Samples are NOT
whole-image uniqueness. No encoder/vsock/Android client in this source-only test.
Event1 actual1296 presentation numbers/30.010894s=43.1843/s, status0,
native1920x1200/pitch7680, publish/copy callback1.9687ms. WARP stimulus1711
Presents/30078ms=56.886/s, HRESULT0. Static trailing3frames not throughput.
Source58FPS gate still failed; no Android60 claim.

First fixture attempt had wrong root CER path; corrected to same
X509Certificate2/package path as tested admission script, parser/path parity
gate added before delivery. Actual calendar2519-09-13->2026-10-14, no native
counter/timer/CPU edits. Root clock issue remains open; fixture diagnostic only.
PnP status0x180600a/problem0. Guest driver/trust removed, three exact own
payloads restored, VM stopped, observer91791 terminal, socket cold rollback/
readback ce89182f PASS. Ubuntu/firmware/8CPU/Android/production H264 unchanged.

Next: C ABI source adapter from verified receiver to existing C x264 encoder
and unchanged UVF1/4052/MediaCodec. Real desktop must be on this admitted
monitor, not claim synthetic secondary monitor as production desktop. Compare
source/encode/send/Android actual presents, and reconnect/input geometry.

## Latest guest result (supersedes preparation state below)

Newest OFFLINE fix, no fresh guest result: pipe had permanent endpoint loss on
encoder disconnect. Old same-worker reproducer failed exit5; corrected rearm
passes4 real host reconnects, full pixels, monotonic sequences and mid-pending
9MB disconnect. Same first-instance handle/DACL/PID retained; no cached fake
updates; unknown failures close. Existing reader/provenance/ownership tests PASS.
This follows the documented server reconnect lifetime:
https://learn.microsoft.com/en-us/windows/win32/api/namedpipeapi/nf-namedpipeapi-connectnamedpipe

New driver42F1D3F3...22528B fits existing24576B slot, structural CFG/ASLR/NX
PASS. Own test catalogA484F851...signed without host trust imports; INFunchanged.
Matched encoder7BF1C22D... pins42F1, primary-source lifecycle22 tests PASS;
launcher67B32816... and stage26B89490... ready, NOT staged/guest-admitted.
Last real-Desktop gate NOT_RUN because locked; fallback lock screen visually
works. VM stopped, both own helpers and exact codec ALL112 blocks restored.
No additional locked boot repeated, no Android/Ubuntu/platform modification.

Corrected x264 run idd-x264-run-20261014: selected1/load_error0/selftest0,
8 threads; first25.031s1074 encoded frames=42.9068/s, encode4.389–4.703ms.
Android~41–45 receive/decode/render callbacks/s,pending0. Winlogon context,
late grey screenshot: neither real Desktop nor physical present/60FPS proof.
All scoped guest payloads/helpers restored; exact temporary codec recovery
ALL112 original-block readback PASS (94 writes), original EFI/GPT/NTFS guards.
Normal rollback refused two Windows FAT metadata updates; special recovery
admits only their exact sector hashes,1025 corruption tests/225 prefix tests PASS.
Next: user Default desktop on temporary admitted Idd topology; preserve source
and restore topology. No repeated codec/Android/capture API optimization.

Original26112B DLL rejected by cold updater BEFORE writes. Opt-in ThinLTO/Oz
now22528B fits exact original24576B file allocation; final CFG/AA64/export
verification PASS. Frame/source Event1 retained; detailed timingEvent2 and
IPCEvent3 omitted only for this transfer build. Receiver reports transfers.
DLL B15D9F4C840F3EB8569DAA6181136BAB37BB3F270933822E83F9920B4F0AFF5A;
catalog4B649830BF0B0A44CB55547DAEAA38948829142FECDE49BDA778E884A48E7707;
reader3775F62B007BB61AF8CF70F1D6D9EA981A803870AAD342BDD802E3A2AB7B721C.
Exact loaded-module hash guard updated. Cold delivery/readbacks PASS,
no allocations/platform mutations; source-only diagnostic app unchanged.

First guest run stage17 refused before driver/trust install: calendar outside
own test certificate validity (`idd-pipe-observe4-20261014/job-error.txt`).
No fresh pipe result; previous source records must not be counted. Own3 files
restored in finally, VM stopped, observer36986 terminal, native socket cold
rollback/readback ce89182f PASS.

Prepared one necessary calendar fixture: read exact own certificate, verify
thumb/serial, only if calendar outside validity set known host UTC, log
before/after, verify, then unchanged admission. No certificate widening,
new key, CI/BCD change, timer/CPU/QPC mutation. Root clock issue remains open.
Stage once18/unique backups/parser PASS; not yet staged or rerun. Next exactly
one retry of same guest pipe experiment, then scoped cleanup/rollback.

Purpose: feed the already-admitted OS-owned IddCx system buffers into the
existing x264/UVF1/vsock/Android pipeline, rather than another capture API.
This does not enable hardware3D or solve the measured source60FPS gate.

Implementation is opt-in (`-FramePipe`, requires `-SystemBufferProof` and
complete link). Normal proof worker/production H264 behavior unchanged.

One allocated packet:64B UID1 header and1920x1200 BGRA pixels, packed pitch7680.
Driver copies each row before releasing the borrowed OS frame. Asynchronous
outbound byte pipe accepts only local SYSTEM clients, non-inherited handles,
first-instance guard. At most one write outstanding. Busy consumer means
drop that source update, not block composition or enqueue more memory.
Transfer cancellation completes before owned storage is freed.
Sequence/drop counts are source-local; not Android presents or image hashes.
The pipe exports desktop pixels only when explicitly enabled; no ETW pixels.

Host tests ran twice using real named pipes, fragmented4093B reads and all
9.216MB pixel comparisons, input-memory reuse, absent/slow consumer, drop
counts, subsequent packet, and cancel-close while transfer pending. PASS.
Host test DACL allows its own owner/SYSTEM; this is NOT proof of guest UMDF
creation rights or cross-session SYSTEM receipt. ARM64 test compiled only.

ARM64 WDF/IddCx DLL linked with actual CRT/CFG/ASLR/NX,26112B:
`E:/U-AVF/artifacts/idd-frame-pipe-linked-20261014/IddSampleDriver.dll`
SHA256 B80AB2B94B31F1CDEC9DE953ACCDBDEC0DE6BC741E0F8F3CD7032162E839CA60.
Unsigned, not staged, no guest writes. No FPS claim.

## Receiver and package preparation

`idd-frame-reader.h` verifies actual pipe server PID and exact process image,
then reads header/pixels with one shared deadline. Malformed geometry,
non-increasing sequence or frequency change fails closed. Pending read is
cancelled/completed before stack/buffer reuse; abnormal non-completing
cancellation terminates only the user-mode receiver, not the VM/driver.
Real host test PASS twice:9.216MB pixel equality, wrong PID/image rejection,
50ms timeout and safe cancellation. ARM64 compile is not guest proof.

Finite guest `idd-etw-read.exe --pipe-probe` requires SYSTEM and discovers a
unique WUDFHost with the loaded DLL whose on-disk SHA exactly matchesB80AB2...
Then validates pipe server PID and C:/Windows/System32/WUDFHost.exe.
50s admission ceiling,30s receive interval,2s read deadline. Sample changes
are explicitly not whole-frame image uniqueness or Android FPS.
Helper15872B SHA95CE41D9DCFD63C662354EE50B90A4F97BF6867BF606918DB5B7537B06483689,
in E:/U-AVF/artifacts/idd-frame-receiver-probe2-20261014.

Catalog generated/signability PASS (no errors/warnings), signed by existing
own test key80E30D...; no host trust imports or CI/boot edits. Catalog SHA
6E2A9240EADE930160EB4127A62E0328DE50469D23B217DC3FCEC30F4D76D266.
INF byte-identical10304CA4...; DLL stillB80AB2...
Package E:/U-AVF/artifacts/idd-frame-pipe-signed-20261014. NOT staged or
guest-admitted. No additional VM launch. Next prepare exact cold-slot delivery
and bounded wrapper invoking receiver alongside the30s motion source, with
scoped driver/trust cleanup and previous owned helper rollback.

Next gate: signed isolated admission plus bounded SYSTEM receiver. Validate
server identity as the admitted UMDF host, fixed header bounds, full pixels,
disconnect/timeout, endpoint cleanup and restoration. Then hook this source
into existing x264 conversion/UVF1 without changing vsock/MediaCodec.
Do not treat the fixed endpoint name or its DACL alone as server provenance.
Do not deploy to production until these checks and real end-to-end metrics pass.

Current source ceiling: GDI stimulus48.4984/s; WARP flip44.4899/s.
Driver copy below1ms, about93% waiting for OS frames. Source58 gate failed.
No further batching/GDI/DXGI/staging repetition; source availability remains
a separate blocker even if IPC/encoding succeeds. Ubuntu/P7/8CPU/firmware intact.
