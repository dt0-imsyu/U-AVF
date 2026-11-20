# Windows ARM64 practical runtime checklist — 2026-10-08

Full product objective/incomplete capability ladder: [WINDOWS_PRODUCT_TARGET.md](WINDOWS_PRODUCT_TARGET.md).

## Current checkpoint — 2026-10-11 (supersedes pending runs below)

- Latest10-12 priority: RAM reader ownership/cache/signing BEFORE benchmark.
  WINDOWS_RAM_READER_ADMISSION_2026-10-12.md: documented mapping candidate,
  exact Windows memory classification and admitted reader UNPROVEN. Repo
  ARM64 WDK available. Read-only owner inventory host tests PASS, not guest
  proof. No new VM boot/policy/disk change. Supersedes next-test bullets below.

- Current exact next test: ram-fb-readonly, at most two32-byte physical
  framebuffer observations separated by resumed guest execution, using fresh
  EBS geometry and actual Default/full-repaint report. Not a60FPS benchmark.
  Module mock/range/third-read rejection and observer compile PASS. Preserve
  original cadence source,8CPU,x264 and common transport. Require four cold
  rollback/readbacks after runtime. No new driver/policy/platform changes.
  Actual fixed repeat NOW PASS: all8 post-login samples match active workload
  and change4880b7ff->95806aff between resumed executions. No60FPS claim.
  Next gate is legitimate guest reader/admission + coherent frame access.
  Four cold rollback/readbacks ALL PASS; runner exit0, VM[]. No live test.

- Shared-primary admission query completed on actual Default: all three
  calls SUCCESS, shared handle0; source0/LUID00000000:000047d9. NO-GO for
  that existing public route, not a query permission error. No new resource/
  mode/driver/policy changes. All four cold rollback/readbacks PASS; runner
  exit0, VM[]. No active experiment. Prepared async-worker is next available
  bounded scheduling A/B; RAM reader needs its own legitimate admission gate.
  WINDOWS_SHARED_PRIMARY_ACCESS_2026-10-12.md contains exact evidence.

- 2026-10-12 latest: release-phase control22.957 actualFPS; ReleaseFrame
  only33.110ms total/551 calls, not the bottleneck. Found cap60 applied to
  poll attempts rather than sent frames. Opt-in frame-pacer/ownership tests
  PASS; first run22.5-24.2 capturedFPS but lacked Surface fence sample.
  All four cold rollbacks PASS. Exact repeat frame-pacing-present failed
  before Windows (U-Boot synchronous abort at EDK2 handoff), no FPS result.
  Repeat all four cold rollbacks PASS; VM[]. No active measurement remains.
  Details WINDOWS_FRAME_PACING_2026-10-12.md; no default promotion.
  Authoritative ledger: WINDOWS_PERFORMANCE_CURRENT.md. No60FPS claim.

- Private timer lowers actual guest pause14.271->2.284ms, but Android21.533FPS;
  no40-45/60 PASS. All four rollback/readbacks PASS. Poll audit prior0x101
  and USB-interrupted rollback recovered ALL112 original blocks. Next missing
  ReleaseFrame/COM release phase timings; details WINDOWS_PRIVATE_POLL_TIMER_2026-10-12.md.

- Async two-slot/DO_NOT_WAIT24.902 actualFPS; no40-45/60 PASS. Map blocking
  removed, but ready age35.3ms /686 not-ready calls. No queue growth. Next
  measure actual polling Sleep(1) wall cadence before any local wait change.
  Original DXGI stable default; details WINDOWS_DXGI_ASYNC_STAGING_2026-10-12.md.
  All four cold rollback/readbacks PASS; VM stopped. Poll timing candidate
  compiled/selftested separately, not deployed yet.

- Staging-readback follow-up23.134 actualFPS: no gain; Map completion still
  15-20ms despite quick asynchronous CopyResource submission. Original DXGI
  preserved. Next bounded asynchronous readiness hypothesis only, not more
  CPU/platform/codec permutations. Details WINDOWS_DXGI_STAGING_2026-10-12.md.
  All four cold rollback/readbacks PASS; VM stopped.

- 2026-10-12 cadence audit supersedes the old next-step bullets below:
  actual Android25.798FPS; DXGI accumulated45.95/s, acquired27.71/s;
  MapDesktopSurface15.75ms wall/frame, capture-thread CPU nearly0.
  DWM advertised64Hz is not actual FPS (display counters0). Next only
  staging CopyResource/Map A/B; no GDI/codec/platform sweep. See
  docs/WINDOWS_DXGI_CADENCE_2026-10-12.md. All four cold rollback/readbacks
  PASS; VM stopped. No production default change.

- Actual full-frame synthetic measurements, NOT60FPS: serial x26424.756
  physical Android FPS/20s; overlapped DXGI26.008/20s, source Paint~62/s,
  queue dropped0. Four rollback/readbacks PASS for both. Next one existing
  GDI CAPTUREBLT+x264 comparison; no CPU/platform/codec/Android mutations.
  Capture sample-color counter excludes repeated full-repaint snapshots;
  don't promote paint/callback/nominal cap as actual desktop performance.

- Source-ownership A/B late-release NOT faster (16.95-17.49FPS), four
  rollback/readbacks PASS. Next native30s full-repaint +20s actual Android
  present fences, ORIGINAL capture contract. No60FPS claim or default change.

- controlled-window x264: real30s big Notepad drag17.4-18.54FPS, reverse
  20s22.2-23.2FPS. Encode~8ms improved from38-39; capture30-32ms dominates,
  Android pending0/1. All four cold rollback/readbacks PASS, VM[]. Next
  one source-ownership A/B (late ReleaseFrame), default preserved.

- 2026-10-12 x264 actual ARM64 admission PASS, same8CPU/native1200p,
  UVF1/MediaCodec. Four cold rollback/readbacks PASS. Sustained workload
  comparison still missing; late desktop-selection sample NOT60FPS.
  Next bounded test repeats ordinary-window motion, no platform mutation.

- 2026-10-12: user authorized deleting old Ubuntu workspace. Release app's
  own deletion succeeded, retained official ISO and Windows disk. ~20GiB
  reclaimed, ~69GiB available. Storage blocker below superseded. Current
  runtime-after-cleanup tests eight-thread codec on proven native1200p/8CPU;
  sustained active desktop FPS remains to measure, not yet60FPS PASS.

- Optional guest4054 sidecar implemented in SAME own winpe-vsock binary;
  no new file allocation/driver/provider registration. ARM64 raw30720B fits
  existing81408B slot; cold retained original exact/prepared only, writes0.
 68 real localhost socket/pipe/owned-child tests PASS (NOT guest viosock/clip).
  Runner explicit WindowsClipboard flag/parser PASS; encoder RunTag permits
  NEW bounded evidence without overwriting failed run. No activation/install.
- Runtime gate: at least3 consecutive goal turns below52GiB tablet reserve
  (~48.95GiB); VM[]. Safe offline components now prepared, next meaningful
  validation requires actual VM. No safe expendable >=3.05GiB artifact found
  outside protected data/official ISO. Do not weaken guard or build more
  untested features instead. Free~4GB or identify expendable file; resume
  codec/activeFPS test first, then clipboard. Full Windows goal NOT achieved.

- Optional Windows clipboard Android integration now uses SAME GuestClipboard
  and UCIP, defaultOFF, per-ready/installed-profile opt-in4054 with existing
  own Binder UID/token checks.52 admission tests PASS; diagnostic APK strict
  Java/D8/sign verify PASS in NEW C output, not installed. Focus/lifecycle close
  wired; Ubuntu default constructor/label preserved. Native guest4054 listener
  still missing: don't claim copy/paste PASS or enable flag. Final rebuild2
  includes volatile flag/signature verify, APK F4A0ED45...52CED8A4. No VM/
  profile/storage/installed APK changes.

- Windows clipboard source prepared while tablet runtime reserve blocks boot:
  standalone ARM64 public-API UCIP worker compiled;47 actual native wire/
  Unicode/session/pipe checks PASS, same packet Android+Linux interop PASS.
  No host clipboard accessed, no deployment/VM/APK mutation. NOT guest
  clipboard PASS.4054 listener/own Binder admission, opt-in common client,
  reversible placement and real app copy/paste still required. See
  docs/WINDOWS_CLIPBOARD_2026-10-12.md. Don't replace performance goal with
  protocol-only success; resume8-thread active desktop test after reserve.

- Experimental8-thread loader+ARM64 encoder compiled; host actual hash/size/
  file-lock and reusable12frame codec tests PASS. Single runtime attempt
  rejected by owner reserve BEFORE VM creation (~48.95GiB available vs52GiB).
  NOT codec/FPS runtime result. Actual separate-file transaction APPLY and
  ROLLBACK full readback PASS; encoder/socket/GOP recovery PASS; VM[] confirmed.
  Wrapper admission moved before writes; don't repeat until reserve restored.
  Keep official ISO/production disks untouched. Exact next test remains ARM64
  selftest/selection via UVF1 followed by active Desktop encode performance.

- Separate eight-thread codec typed transaction:465 actual offline verifier/
  apply/rollback partial-failure tests PASS; real tablet typed dry-run PASS,
  zero writes.112 original blocks/GPT/EFI remain exact. Explicit verified
  experimental loader + ARM64 selftest next, then one controlled active
  desktop performance run. No new VM start or60FPS claim.

- 2026-10-12 separate eight-thread DLL read-only staging plan PASS:103
  verified free isolated FAT clusters, GOP reserved cluster excluded, GPT/
  EFI/mirror/directory/FSInfo guards, all preimages cold rechecked; zero
  device writes. Six allocator tests + actual manifest invariants PASS.
  Typed transaction failure/rollback tests and explicit ARM64 codec loader
  still required before guest deployment. Not runtime/FPS PASS.

- Codec native-host sibling ABI/thread test PASS:8threads/8slices,12 changing
  1920x1200 encode+decode/stride/luma checks. ARM64 test compiled only.
  Cold current DLL original800856B/F5976... confirmed. Final -O2 candidate
  843264B cannot fit; no deployment/truncation. Stop size sweeps, stage separate
  experimental codec with guarded transaction before ARM64 performance test.
  Current DXGI/H264/CPU8/Ubuntu/data untouched. Not60FPS PASS.

- 2026-10-12: official2.5.1 source proves encoder thread clamp4. Isolated
  constant8 ARM64/NEON DLL compiled/export/import audited, not deployed.
  909824B exceeds old800856B DLL: do not truncate or use fixed encoder writer.
  Runtime codec/thread test + controlled staging next. No60FPS proof;
  preserved working DXGI checkpoint/Ubuntu/data unchanged, no new VM launch.

- Direct DXGI mapped capture NORMAL runtime; Winlogon -> Default/Desktop
  reconnect, native1920x1200 H264 Surface. User confirms improvement.
  Best candidate safely retained in C-only safe-checkpoints/windows-dxgi-
  1920x1200-20261011 (~606KiB, SHA manifest, no disk clone/private tokens).
- Not60FPS: sparse Desktop interval2.783 capture/2.261 encodedFPS; combined
  acquire/map/convert27.564ms, encode40.436ms, Androidpending0/1/~7-9ms.
  Next accurate separated timing + active Desktop workload, then encode/
  capture overlap if indicated. No new CPU/GOP mutations.
- All own encoder/socket/EFI/FAT rollback readbacks PASS, VMs[]. Android
  fullscreen system-bars lifecycle fix built/verified/installed; runtime
  immersive regression pending. Production Ubuntu untouched.

- LIVE metadata now reliable via existing shared UVF1/app-owned channel; real
  DXGI DuplicateOutput +Acquire1920x1200 PASS, system-memory desktop=1.
- Actual OpenH264 threads4 (requested8); GDI32-36ms +conversion4ms +encode21-24ms,
  ~16-17fps typical. Next direct MapDesktopSurface capture A/B, same encoder.
  No more GDI flag/CPU/GOP changes. Not60FPS/hardware GPU proof.
- All encoder/own socket/EFI/FAT rollback readbacks PASS, no VM retained.

- Latest threads/DXGI A/B terminal normal, Desktop1920x1200 PixelCopy verified;
  Android12-14fps, pending0/1. Both encoder andEFI original rollback PASS.
  Requested low-complexity/8thread settings compile; effective runtime settings
  and DXGI HRESULT unknown because exact producer directories not persisted
  for non-coherent cold reader. Next coherent metrics collection before another
  FPS A/B. Do not infer failure/unsupported from missing logs.

- Native1920x1200 Winlogon live stream PASS: exact EFI359DED.../APKA7D123...,
  real mode enumerated by guest, lock-screen PixelCopy,1544 matched decoder/
  render callbacks, native startup8/barrier0. OriginalEFI/FAT rollback PASS.
- 60FPS NOT PASS: capture15-18fps, BitBlt33-40ms +conversion4.3ms +encode23-25ms;
  pipe~0.1ms and Androidcallbacks6-9ms/pending0. One transient~4fps window.
  Next native faster capture feasibility +encoder threading, common pipeline.
  No further CPU/GOP boot experiments without new evidence; Desktop motion
  and actual physical presentation still require verification.

- Devicepath candidate7E609... runtime A/B TERMINAL: native startup8/barrier0,
  drivers loading, no Surface within300s. Observer response bound is not proof
  of hang. Exact EFI/FAT rollback PASS; no VM retained. Next loader GOP selection
  /boot framebuffer evidence, not another blind retry/CPU flag change.

- Latest RAM-GOP A/B terminal: eight CPU/userspace PASS, EFI GOP success;
  fresh producer still ROOT\\BasicDisplay640x480. Decoder bounds rejection,
  NOT native1200p PASS. Exact originalEFI/FAT rollback PASS, no VM retained.
- Explicit display BCD audit across17 objects found no restrictions. Independent
  display handle + vendor/ADR devicepath candidate built and host-tested with
  original/fixed-eight byte parity PASS; NOT deployed. Next typed admission,
  fresh reversible preflight and one runtime A/B. No CPU flag experiments.
- D/E both faulty: new important files only C; bounded artifacts/no full clones.
  Cleanup deferred by user; 50GiB reserve still a requirement, not currently met.

- Latest display work: cap60 installed and actually exercised, eight CPU retained.
  No sustained60FPS/native1200p PASS. First real Desktop source~13FPS,
  GDI66-71ms, encode5-8ms, pipe<0.2ms. SRCCOPY-only A/B no improvement,
  rolled back. Read-only adapter-audit run terminal, Surface~13FPS,
  decoder pending0/1, native source still640x480. Exact EFI rollback PASS
  after all three runs. Installed own encoder4161f240...a00b84 (cap60,
  CAPTUREBLT retained, bounded native adapter/mode/timing diagnostics).
- Offline-only native1920x1200 RAM-GOP candidate built and bounds/layout/
  refusal/rollback tests PASS, control EFI893... and fixed8EFI365... byte
  parity PASS. Candidate1643E97C...42C9A3 is10240B, NOT deployed: existing
  own EFI file8192B consumes exactly one8192B cluster. Never truncate/pad
  this candidate into that slot. Next requires guarded own-file FAT extension
  / exact reversible metadata plan plus typed APK admission before one A/B.
  See WINDOWS_DISPLAY_CAP60_2026-10-11.md. No signed Windows/Ubuntu changes.
- Own EFI growth read-only preflight PASS, device_writes0: free cluster994747,
  mirrored FAT match, exactly five retained blocks. Report:
  smp-ram-gop-file-preflight-20261011/report.json. Next implement typed
  reversible transaction / APK admission, then one native1200p GOP A/B.

- AUTHORITATIVE: explicit IPI A/B session53829 TERMINAL PASS. Native
  KeStartAllProcessors returned8/barrier0; own broker2896 independently
  reported processors8 seven times over~91s. Real Desktop and live H264
  640x480~14-15FPS/cap15 reached Android Surface. Exact original EFI rollback
  PASS; no VM retained. Evidence: kd-windows-explicit-ipi-ab-20261011,
  smp-explicit-ipi-live-userspace-second-20261011/runtime-cpu.log.
- Next user priority: raise Windows encoder cap60 and native resolution toward
  1920x1200, preserving this exact eight-CPU path. Current640x480 disabled
  Settings selector is a real source-mode restriction, not Android scaling.
  Native1200p and sustained60FPS are NOT yet PASS. Ubuntu unchanged.

- Latest completed: scheduling initialization returned; native time-sync IPI
  send completed, pending8 at initial and first-loop observations. Every stop
  in this region costs42-44s. GIC physical reads unavailableC0000001.
  No all8 Desktop proof; exact original rollback PASS after both IPI trials.
  Minimal-observer600s A/B session89380 terminal: native flags1..7 PASS,
  no startup return/Surface. Original rollback PASS. Current explicit native
  IPI affinity A/B session53829; one guarded transient capability82b->80b,
  unchanged native target set/waits, exact same candidate/APK. Not a PASS yet.

- Latest sequential PPM trial terminal: native starts1..7 PASS,
  KeNumberProcessors8, PpmInitHeteroEngine return0, architectural efficiency
  update completed. No all8 Desktop yet. OriginalEFI rollback PASS; VMs[].
  Next same-candidate scheduling/HAL phase1 call-return ladder; adjacent
  probes must be sequential because KD removes its forward-window breakpoints.

- New native startup proof: actual Windows flags1..4=1 and successful native
  wait result4 in CID2235. CPU5 flag/ownmarker0 through10000 iterations,
  then native result2; KeStall does return. CPU3 is not a fixed blocker.
- Same candidate can fall back to actual one processor (CID2234 broker2632
  samples0..7), not an all8 PASS. Completed tests originalEFI rollback PASS.
- Native PoC-publication A/B terminal, session1718/CID2236: helpers all7
  executed; native Windows flags1..7=1 and all waits result4. No later stage
  or Surface by210s. Original893... rollback PASS. Actual8/Desktop unproven.
  Last-common KeStartAllProcessors snapshot session72063 terminal: all7 starts
  repeat; native handshake field0 and b360b0 reached. AP1..6 PCs are normal
  debugger-freeze function, CPU7 context unavailable (zeros); no guest-fault
  inference. Original rollback PASS. Next SAME-candidate PPM/caller-return
  probe running session4412, no new CPU/PMU config.
- Exact native PoC-publication configuration:
  candidate0347CB7B...8CC9007/APK4A1D2488...9BC06DD. Own helper only;
  full CPU_ON context restoration tested all7/28 bad contexts. Await terminal
  evidence; cache-publication cause not yet proven. Full objective unchanged.

- OWN AP markers runtime PASS for CPU1/2. CPU3 immediate marker0 is UNKNOWN;
  no later readback, no4th call/Surface. All-pre-start/BSP-only observer still
  reaches call3 then silence, excluding immediate HVC-return/AP-PMU stops as
  sole cause. Stop repeating those exact probes. Details in SMP boundary doc.
- Cold rollback original893... PASS, VMs[]. Active BCD read-only17-object
  audit: no explicit CPU limits anywhere, including inherited objects. No BCD
  mutation. Diagnostic APK6DB6052C...9283253C; production Ubuntu unchanged.

- Offline OWN AP-entry marker candidate built8192B SHA597AC056...120EBE29,
  NOT deployed/admitted.64B template stores ownmarker then restores exact
  Windows entry/x0; only x0/x16 scratch, no UART or Windows memory writes.
  Independent assembler-byte/literal-bounds/final-ABI oracle and map tests
  PASS, both original controls byte-identical. Device originalEFI, VMs[].
  Next separate APK/apply hash gate and version2 observer readback guard;
  do NOT reuse old runtime candidate authorization for this new marker.

- Common-PMU31->20 allocation A/B now exercised on BSP31: actual descriptor
 20, native AP1 comparison PASS, CPU_ON1/2 return0. Next natural20 run
  accepts CPU_ON1/2/3, no later stage. Late own KD break gets no response;
  no CPU snapshot, no all8 proof, no new Surface. Keep these limits explicit.
- Both runs terminal, isolated owner stopped. Original893... EFI restored,
  exact readback PASS. Next local observation should be OWN AP-entry marker
  to distinguish accepted HVC from actual secondary execution, not repeating
  late break/PMU toggles. Current common-PMU logic remains diagnostic only.

- PMU root cause READBACK PASS: BSP MIDR410fd821/N31 allocates31, actualCPU1
  MIDR410fd811/N20 fails20<31, then5C. Fixed debugger CPU routing (previously
  all GET/SET context requests selected CPU0 even at AP stop). Actual AP PC
  now matches stopped-state PC; routing/recovery offline tests PASS.
- Allocation request31->20 A/B prepared, not yet exercised: next BSP was
  naturally MIDR410fd811/N20, original allocation20 preserved. CID2227 live
  Surface640x480~13FPS PASS at210s, but broker2628 seq0..3 processors1;
  no actual8-core success. Script stopped owner, VMs[], original EFI893...
  restored/readback PASS. Next prove targeted allocation A/B on BSP31, then
  inspect remaining AP-online boundary rather than claiming CPU_ON0 is SMP.

- NEW: owned runtime virtual mapping/header/template readback PASS at loader
  and kernel. One CPU1 context0 trampoline A/B returned native HVC0 instead
  of-2; later unmodified call still failedA0. This is first-secondary acceptance,
  NOT a completed multi-core Desktop. See kd-windows-runtime-context0-ab.
- All-secondary8-page trial kd-windows-runtime-all-context0-20261011 reached
  CPU1 HVC0, then HAL5C(9000,1,14,0) BEFORE CPU2 probe. Exact kernel code
 49fd34..49fe24 reads MIDR/PMCR.N, compares CPU's counter count against
  allocated counter-set total, and constructs this bugcheck. Strong remaining
  hypothesis: heterogeneous PMU count mismatch. Read actual BSP/AP counts
  and flags at this boundary next; no blind PMU masking or counter bypass.
- Runtime callbackUART diagnostic AV and pre-kernel observer base=None error
  fixed; own68B PIC callback guard/launch/map tests PASS. Original EFI893...
  restored/readback PASS after all-CPU trial; VMs[]. No Ubuntu/D/commits.

### Earlier checkpoints (superseded by runtime mapping/context0 evidence)

- Owned reservation milestone: EFI NVS allocation/map validated, both original
  controls byte-identical, range/template selftests PASS. CID2216/2217 Windows
  kernel reached, but KD physical NVS reads failC0000001; no header runtime PASS.
- CID2218 firstCPU own aligned static trampoline returned-2 directly fromHVC.
  Windows context restored in own code if executed; no execution/SMP PASS.
  Context0 remains untested. OriginalEFI restored/readback, VMs[]. Next offline
  EFI runtime virtual mapping proof for safe owned-literal context0 A/B,
  not arbitrary Windows padding or CPU flags. Details in SMP boundary doc.

- SMP result recorded in WINDOWS_SMP_PSCI_BOUNDARY_2026-10-11.md. CPU_ON64
  affinity1/context8000c000 returns-2 at real HVC. 2GiB RAM/below4GiB entry
  and one saved/restored reserved-x7=0 A/B both fail identically. No repeats.
  Entry page-prefix is live Windows metadata, not safe padding. Offline own
  trampoline template/selftest PASS (NOT allocated/deployed). Need explicitly
  reserved executable EFI page/memory-map proof before next alignment/context
  test; no fatal-handler bypass or blind entry rounding.
  OriginalEFI rolled back/readback PASS. Normal1CPU4GiB recovery CID2215 is
  TERMINAL: pre-kernel KD/RESEND, no complete state by210s, no frame; VMs[].
  Runtime recovery NOT PASS; no identical reboot without a new reason.

- CID2209 fixed8 narrow GIC trial TERMINAL: --cpus num-cores=8 actually passed
  to crosvm, kernel connected and prior CAM/timer/PMCC adapters installed.
  New precise failure: A0(BEEF,8003,C4000003,FFFFFFFFFFFFFFFE), matched PDB
  HalpPowerPsciFatalErrorHandler+28 (RVA49a868). CPU_ON64 returned -2.
  No Surface/user-mode processor_count8 PASS. Isolated VM stopped, VMs[].
  Next ONE restored breakpoint at exact HVC wrapper RVA49a8b0 to observe
  typed CPU_ON operands; no flags/register mutations or Linux changes.
  EFI365B7332...B1A865 differs only4 bytes in original two GIC constants;
  cold/readback PASS. Generic dynamic EFI was rolled back after CID2208
  pre-kernel timeout, NOT admitted as a working SMP platform.

- CID2207 originalEFI/newhelper regression PASS: live frame/input retention;
  cold API samples processor_count1 (7 fresh samples broker2548). Helper alone
  verified. Scoped Windows stopped afterward; no Ubuntu change.
- Exact dynamic-GIC EFI nowE550...1FDC47 applied/readback; original893... backup
  retained in smp-gic-with-cpu-metadata-applied-20261011. OneCPU control dispatched
  (kd-windows-dynamic-gic-native-metadata-onecpu-20261011). No SMP PASS/flag yet.
- CID2206 retained after normal bound PASS; Enter after host observer exit
  changes real login screen and restores~13FPS. Static/idle0FPS was not a hang.
  Serial/RX and TX/decoded/written parity exact; no new KD packet after detach.
  Stopped only isolated Windows for cold own helper update. OriginalEFI retained.
- CPU metadata helper now71168B SHA731ed372...f0b01 applied/readback PASS;
  GetActiveProcessorCount logged for ACTUAL Windows SMP proof, not launch JSON.
  One originalEFI/ONE_CPU regression next, then GIC sameCPU, then MATCH_HOST.
- CID2206/owner18227/session65553 terminal after retention/input PASS: clean2026 persisted base, no
  calendar correction. Typed b17f78 delta1,230,117,077/freq222,389,687 (~5.53s),
  both counters near2^64, current>saved, predicteddate2026. No huge offset here.
  First invalid offset source still unresolved; no unproven kernel arithmetic
  clamp/CPU flags. Re-poll current owner before another test; reserve watcher18227.
- CID2205 repeated calendar-only A/B PASS: live lockscreenOct11, H264~13FPS,
  late CPU30.448→11.35%, LSASS0%. Cold bootstat2026/CRC PASS. Temporary mitigation
  reproduced, permanent clock fix NOT established. VMs[] after test deadline.
  Next existing b17f78 typed counter-delta probe from clean2026 persisted base;
  no calendar correction/CPU/EFI change. New retention gate permits completed
  clock scope and bounded≤2 in-flight frames (actual tests PASS, runtime pending).
- CID2204 typed loader-time source PASS: selected persisted2212 vs EFI2026,
  exact timestamp matches cold bootstat millisecond conversion. Persistence
  proven, original bad-date creation still unresolved. See clock-source doc.
  Runtime session35009 active at last observation, no clock/CPU mutation.
- CID2201 Desktop/input/live640x480 H264 PASS; no dump-unload recovery triggered.
  Test deadline incorrectly force-stopped usable VM. Diagnostic-only normal
  bound fresh-Surface retention gate now offline PASS (same owner/queue0/new
  receive+decode+callback totals over2s); runtime proof still pending. VMs[].
- Clock NOT fixed: latest cold bootstat CRC-valid2212-11-02, not2026.
  CPU broker2588 LSASS0..1.134%, late totalCPU95..100%. Next exact ONE loader
  final-time-selection probe reads only typed EFI/Bl/boot-status calendars.
  Exact loaderSHA/instruction/stack bounds and actual branch tests PASS.
  No new CPU/GIC/timer flags; SMP deferred until clock source separated.
- D rescue: README768B and runtime zip180370B verified on E (small manifest).
  Prefix/APK larger reads stalled, own rescue processes canceled; partial E
  copies UNVERIFIED, originals D intact. No further blind large reads/writes D.
- Reverse control CID2200/owner7259/session11502 TERMINAL: original EFI also
  stops at dump_viostor.sys unload/CONTINUE with2 target RESEND, no frame,
  actualVMs[]. Dynamic-GIC regression NOT established. Exact packet evidence
  motivates ONE reset-only wire-epoch recovery at this bounded state; offline
  guards PASS. Original EFI/ONE_CPU retained for next single control; no new
  clock/CPU/memory changes. SMP remains deferred until reliable sameCPU control.
- D GOOD rescue job18051 confirmed live pwsh24856/24980; only specifically
  named0.5GiB backup copied to E, no D writes/deletions. Currently partial;
  E preallocated file size is NOT SHA256 completion proof. No large raw copies.
- STORAGE INVARIANT: user reports D failing; never use D for new builds/backups.
  Disabled old archive-to-D helper. Important GOOD backup rescue to E pending
  session18051; originals retained. No verified-rescue claim until manifest PASS.
- CID2199 dynamic-GIC ONE_CPU trial TERMINAL, observer71884 handle gone, VMs[].
  Exact EFI/app gates PASS; serial count1/bootbank3ffd0000/MPIDR80000000,
  DSreadback50 restored52. Kernel+timer/PMCC adapters installed, then KD timeout.
  Effective config machine diff vs DesktopCID2198: ZERO differences.
  No Surface, no freshCPU (logSHAfc1ffc... unchanged). No GIC regression claim.
  Original EFI restored cold/readback893B...; candidate retained off-device.
  Cold bootstat after CID2199 remains CRC-valid2026 (134361455662492723),
  SHA961861ea...10cd5; no renewed centuries-date evidence in that record.
  Diagnostic APK A853AB5353A42DE6B6C963D2AEF01609DB842030D34B75F3892CC37C3A5EAF29
  only, original APK CA52... retained smp-apk-control-backup-20261011.
  Actual SMP admission assertions: positive +15 negative cases PASS.
  Next localize stalled boot observer boundary from saved packets before deciding
  one minimal control; do not change CPU until adequate sameCPU control evidence.

- SMP preparation IMPLEMENTED, OFFLINE VERIFIED, NOT DEPLOYED. Optional
  GIC_DYNAMIC selector validates checksum/bounded MADT GICC/GICD/GICR, matches
  boot MPIDR affinity against GICR_TYPER, checks LAST/stride/unique enabled CPUs.
  Active-PPI guard covers ALL discovered redistributors before global DS write.
  No IAR read, no speculative MMIO outside known crosvm window. Default mode
  reproduces frozen EFI893B7859...02E8B exactly; old runtime untouched.
- Fifteen host selector checks PASS (1/8CPU, affinity permutation, absent boot
  CPU, duplicate affinities, malformed/checksum/range/early-LAST/missing-LAST,
  VLPIS stride bound and wrong GIC version); builder parser/legacy tests PASS.
  Optional SMP EFI fits existing8192B slot after dropping only redundant
  diagnostic dumps, SHA E550FD640FC3864305A6F6F0A794B582A5DB43D7F0556FA69FFD11BE5C1FDC47.
  Reproduced final build smp-dynamic-gic-ready-20261011. NO disk layout change.
  Next: exact-hash isolated candidate gates/APK support + cold backup/readback,
  ONE same-CPU control of dynamic selector; only then match-host SMP test.
  Existing observer's one-CPU physical samples must NOT be used as SMP evidence.

- CID2198 CONTROL RESULT: real Desktop, app-owned keyboard Ctrl+Shift+Esc /
  Task Manager column navigation and guest-native Alt+F4 shutdown PASS. H264
  receive/decode/render callbacks~13..15FPS at640x480/cap15, queue0,
  receive-to-render~7..8ms. Reconnect during login/session change observed.
  No RAM calendar correction this boot; persisted boot-status date was2026.
  Login was manually completed by user (no password/auth automation).
- Fresh CPU log115648B SHAfc1ffc7698c6353eab8a5ad8c4e4166ef3dc1d34836ebf9c5fac20ab15abc125.
  Eight samples, broker2564; seven
  ~15s intervals, LSASS CPU0.198/0/0/0/1.036/0.398/0.303%, not old busy loop.
  Overall boot/login CPU7.7..77.8%; settled Task Manager snapshots28/30%.
  Evidence persisted-calendar-cpu-cold-20261011/cpu-summary.json and actual
  tablet Desktop/Task Manager captures in kd-windows-persisted-calendar-control.
- Windows shutdown removed CID2198, actualVMs[]. Observer1612 was deliberately
  terminated ONLY AFTER guest shutdown to end unnecessary remaining timeout;
  wrapper exit1 therefore expected host termination, not Windows boot failure.
  All controls terminal. Next: retain clock/CPU/Desktop checkpoint; audit boot
  GIC redistributor selection before SMP, then resolution/FPS/input/lifecycle.
  Clock source/persistence cause and accurate host-calendar sync still open;
  do not call temporary calendar scalar patch a production permanent fix.

- Across-boot invalid calendar variation is proven: initial installation2247,
  later3316 and3503. This does NOT yet prove nondeterminism or a counter fault;
  persisted/accumulated calendar bias remains possible. Within the measured
  interval QPC and calendar deltas agree; CID2194 loader calendar was already
  wrong before a normal ~1.2s counter delta. LSASS causal CPU proof remains open.
- Exact winload static audit: BlGetTime1a7938 calls EfiGetTime472e0.
  OslSetSystemTimeParameters2e1b0 can replace that date with a later CRC-valid
  boot-status time record. Caller10e20 receives that record from
  OslProcessBootStatusData23a58, field7, offset16/size32; calendar record+16,
  CRC32 record+24. Registry fallback identified: root ControlSetOverride KEY
  LastWriteTime (+4 in CM_KEY_NODE), not W32Time value data. Exact
  OslSetControlSet8550 stores its cell at hive-table+12e8; fallback reads2e3b0.
  Named metadata-only audit of in-memory log-recovered SYSTEM: key missing.
- Cold READ-ONLY bootstat evidence boot-time-state-cold-20261011:
  67584B SHA363f7f13...55a3e5; current field7 calendar134361452702395218
  =2026-10-10T22:34:30.239521Z, CRCb144eb06 PASS. This is AFTER calendar A/B;
  not evidence of the old bad value or proof of its original source.
  No bootstat/registry/disk reset performed. Next: actual EFI date at loader
  call vs cached boot-status candidate; prior early EFI2026 alone is insufficient.
- CID2197 notify-readback control TERMINAL: KD response/state timeout before
  clock correction/display; wrapper stopped, actualVMs[]. No clock regression
  inferred. Cold CID2196 UART audit: host TX=owner decoded=owner written bytes,
  host RX=owner serial bytes. Does not prove guest UART consumption.
- Host recovery change is narrowly scoped to exact third notification RET write
  at2fbfa0 after original-prologue readback, not a general retry-bound increase.
  Unit PASS; CID2197 did NOT reach it, runtime unverified. No unchanged retry.
- Own CPU-log rollover helper NOW installed cold helper-only, 44sectors/3ranges,
  exact binary readback PASS; retained originals for rollback. No hive/metadata
  writes. Completed control CID2198/owner8821/session89377: preserved current
  CRC-valid bootstat2026, NO calendar correction and NO late KD stops. Fresh CPU
  log + Desktop PASS above. Reserve guard used actualowner8821.
  SMP/1200p60 remain open. Ubuntu untouched.

## Current checkpoint — 2026-10-09 (latest verified state)

- CLOCK A/B CID2195 result: typed invalid loader-calendar8B write/readback PASS,
  old600240386053810000 -> trustedUTC134361452583397508; current kernel date
  134361453758089261 is2026, coherent lock15256726, uptime110.5469s /13MHzQPC.
  Existing H264/MediaCodec restored real stream~13..15FPS (cap15), queue0,
  callback~7.4..7.9ms, >700 frames. First actual Surface is Windows "Please wait",
  NOT Desktop PASS. Late KD process reads/context resume then failed; wrapper
  stopped diagnostic (observer79753 terminal, VMs[]). This is a diagnostic-channel
  failure, not Windows clock failure. LSASS CPU drop still NOT measured.
  Own CPU log cold backup calendar-ab-cpu-cold-20261011 SHA68dc1ce2...1c368 is
  capped526349B and ends with older year3053 process creation timestamps, so
  cannot support currentCPU claim. No dump/credentials examined.
  Next control kd-windows-calendar-light-control-20261011 observer60024:
  same calendar guard, NO late/hot break/inventory; let real display/user session
  run and use app-owned input/Task Manager for CPU instead of repeated KD stops.
  No new firmware/disk/source image, no production integration until proof.
  CONTROL TERMINAL: CID2196/owner3398 failed BEFORE calendar probe or display,
  third ambiguous boot-adapter write exceeded existing two-distinct readback
  recovery bound. Two earlier ranges were read back ORIGINAL (proved not applied)
  and then repaired once: CAMbytec03c38 and own thunk665b20. Do NOT relax/write
  recovery bounds, sweep flags, or infer a Windows clock failure from this.
  Wrapper stopped VM, observer60024 exit0 wrapper transport, actualVMs[].
  Calendar A/B proof remains2026 + stream; Desktop/LSASS CPU/SMP still open.
  Next control must address observed boot-adapter transport reliability with
  evidence, not repeat unchanged launch or keep failed guest frozen.

- CID2194 boot-delta runtime PASS: loader calendar600240385940110000 (3503),
  savedCounter18446744071865197586,current18446744073196804916,
  returnedFrequency1109808934, delta1331607330 (~1.2s), NOT backwards.
  Therefore this observed handoff subtraction does NOT explain centuries:
  input calendar is already wrong. Do not mask CPU/counter flags from this.
  Diagnostic stopped after result, actualVMs[]; observer41644 terminal.
  Prepared MINIMAL clock A/B at exact initializer LDRb17f14: if loader calendar
  outside2020..2100, replace ONLY8B calendar+a48 with trusted host UTC, verify
  readback; plausible calendar no-op. No context/register/counter/RTC/file write
  by helper; no signed userland patch. Windows may subsequently normally set
  its RTC/calendar. Unit guards/readback/no-op/bad-pointer/target PASS.
  CurrentCID2195/owner509/observer79753, kd-windows-boot-calendar-ab-20261011:
  same launch plus temporary calendar scalar correction and existing late120s
  public clock/LSASS observation. No runtime correction/CPU/frame PASS yet.
  Reserve guard actualowner509. Original EFI/Ubuntu/storage untouched.

- Latest2026-10-11 FIRST CLOCK SEED runtime PASS: CID2193/owner30389,
  kd-windows-first-clock-seed-20261011. One original HAL writer48a8c0 probe,
  removed before typed read, then guest resumed (subsequent driver loads).
  Actual caller47c974=HalSetRealTimeClock, explicit calendar600240385862130000
  =3503-02-02T16:03:06.213Z. Windows supplies bad date BEFORE writer/QPC extrapolation.
  This does not prove its earlier caller yet. Stopped diagnostic after evidence;
  observer53273 exited, actualVMs[] before next run. No userland/disk patch.
  Static initializerb17ee8 uses loader-extension calendar+a48/counter+a50;
  siteb17f78 subtracts saved counter from current, unsigned scaling atb17f90,
  then adds base calendar and calls HalSetRealTimeClock atb17fe8.
  Prepared three-read40B exact-site boot_clock_delta_evidence(), unit PASS;
  reads only typed initial calendar/counter + returned frequency, no stack dump.
  Current ONE next run CID2194/owner31370/observer41644,
  kd-windows-boot-clock-delta-20261011. Same launch, single earlier arithmetic
  observation, no late/hot inventory, no new timer/CPU flags; result pending.
  Reserve guard actualowner31370. Hypothesis: counter origin/backwards unsigned
  subtraction at handoff; NOT proven until actual operands obtained.

- Completed bounded run2026-10-11: CID2192/actualowner27293/observer83287,
  kd-windows-hal-clock-baseline-20261011. Same frozen installed launch; original
  EFI, no restore/preparation/storage/CPU/timer changes. New reader only7 fixed
  reads88B with exact original/runtime HAL code guards; unit PASS. Requests
  KUSER clock then HAL QPC/calendar baseline/mode/error fields at existing late
  break. Runtime HAL metadata PASS: coherent referenceQPC121350015,
  referenceCalendar541490910014448004 (already3316), mode0/errorFlags0.
  KUSER raw541490911136777079, QPCbaseline1580377807/frequency13MHz,
  interruptTime5.4535266s, coherent even lock157734, signed bias-5156703029.
  HAL reference is already wrong BEFORE extrapolation; no proof of branch
  execution or initialization source yet. Note uptime5.45s despite late120s
  observer schedule: KD/driver-load stops make host elapsed NOT guest uptime.
  After reads, hot-process identity unavailable (LSASS not yet present), then
  resume KD response failed; OBSERVER_STOP. Wrapper stopped diagnostic VM,
  actualVMs[]. No Surface/CPU fix PASS. Do not repeat unchanged run or treat
  debugger resume loss as Windows clock root cause. Reserve guard uses
  actualowner27293, not wrapperPID39101. Battery100%, tablet56GiB free.
  C unexpectedly14.6GiB vs prior51; D57.7/E82.3. No large new artifacts/clones
  or unverified cleanup; host free-space requirement currently unmet.
  Next static discriminator: writer/initializer of HAL baseline blocke05160
  and loader/EFI time handoff. Same3316 baseline across latest boots narrows
  earlier cross-boot variation; does not establish random counter drift.
  Exact measured delta: raw - HAL calendar112.2329075s; KUSER baselineQPC -
  HAL referenceQPC /13MHz112.232907077s (agreement <1 microsecond).
  Therefore this extrapolation is numerically correct; wrong BASE date persists.
  Offline exact-kernel reference scan finds reader48a804 and writer48a928.
  Writer function48a8c0 takes explicit calendar pointerX0 (read at48a8f0),
  or ifX0==0/mode0 calls system-time reader2fb068, then stores calendar at
  baseline+12 and QPC at+0/+24. Thus HAL baseline alone is NOT independent RTC
  evidence. Next highest-gain early observation: first call to writer48a8c0,
  exact kernel code + LR + typed calendar argument only, before any LSASS reads;
  determine whether far-future date is supplied by caller or current-system-time
  fallback. No stack/process data, signed-userland patch, or timer sweep.

- 2026-10-11 cross-boot CLOCK evidence: first installed boot recorded
  2247-02-24 in clean-installed-firstboot-offline-evidence-20261009/
  ProgramData/UAVF/firstboot-state.txt and Windows/Panther/setupact.log;
  latest CID2191 raw kernel SystemTime is3316-12-01. Calendar varies across
  boots, but RANDOM/nondeterministic counter offset is NOT proven: accumulated
  or persisted initialization bias remains possible. Saved slow-run log
  source-observation-default-stall-20261010/LIVE-SERVER-2156-656703/LIVE-1/
  live-encode-report.txt has19 interval samples: Tick149.062s,
  QPC149.057641615s at13MHz, calendar149.0575501s, no backwards QPC interval.
  This rejects sustained gross clock acceleration DURING THAT149s sample;
  it does not prove cold-boot QPC initialization or all boots correct.
  Next discriminator remains Windows initialization/persisted time source,
  not blind timer/CPU changes. No guest mutation/restart for this audit.

- Cold time-state audit2026-10-11: actualVMs[]; isolated SYSTEM8650752B
  SHA1420921d...1f044 extracted read-only. Header607/606 is DIRTY, so base-only
  observations are insufficient. SYSTEM.LOG1 empty; LOG2 SHA5e5ec71a...78081
  replayed IN MEMORY using RegistryLog to matching608/608. Named time values
  still: SecureTimeEstimated/High/Low and LastKnownGoodTime2015-08-01;
  ShutdownTime2247-02-24T12:46:31; timezone Russian Standard Time/UTC+3.
  These inspected persisted values do NOT supply year3316. Do not disable
  Secure Time Seeding without new evidence. No device/registry writes.
  Tools inspect-system-time-state.py (named keys only, optional memory replay),
  extract-installed-setup-evidence.py --time-hive-only; evidence directories
  clock-state-cold-20261011 and clock-state-transactions-20261011.
  Next: exact kernel/loader time initialization and EFI-to-kernel conversion.
  Actual3316 SFAPM arithmetic OVERFLOWS but wraps to POSITIVE5611129356604909468ns,
  not a negative deadline; prior3053 negative case cannot be assumed for3316.
  CPU root cause remains unproven. No restart/flags/userland patch for audit.
  Exact ntos static next discriminator: HalQueryRealTimeClock RVA47c760
  first tries time-provider path666760, else fallback48a7c8. Fallback computes
  baseline calendar + (currentQPC - baselineQPC)*10000000/frequency, reading
  shared baseline block RVAe05160. Literal47c8b8 identifies the floor as CURRENT
  KUSER SystemTime, with one-day threshold864000000000, not independent persisted
  minimum time. Thus pre-EBS EFI GetTime2026 does NOT
  prove Windows later uses that calendar unchanged. Next observation should
  distinguish provider/fallback and actual baseline/floor, not change RTC flags.
  Static paths only; no live branch/baseline proof yet.

- Current CID2191/owner23307/observer73449 (raw-clock-discriminator-20261011)
  FIRST public clock sample runtime PASS, same guest resumed after inventory:
  rawSystemTime541490911030240127 =3316-12-01T14:11:43Z; interruptTime115.6566401s;
  QPCfrequency13MHz, baseline1562647139, increment14189803133622732012, shift0,
  bypass1, bias-135491269, equal EVEN TimeUpdateLock1491118. This proves the
  ordinary Windows kernel calendar is already far-future; not merely a user
  precise-time projection artifact. Original UEFI calendar2026 independently
  proven in CID2190. Hot LSASS740/TID780 kernel724/user4184ticks remains.
  No CPU fix/SMP/display PASS. Do not repeat RTC correction or assume QPC flags
  are wrong: frequency/increment/uptime are individually plausible.
  Next: inspect explicit Windows time initialization/persisted-time state READ
  ONLY (not SAM/SECURITY/credentials or whole-hive rollback). Microsoft KB3160312
  documents stale Secure Time Seeding registry data as a possible override of
  administrator/RTC time, but this VM has NOT yet proven that cause. Compare
  actual named time values before any mutation/disable/reset. Then one narrow
  correction A/B only if exact invalid source is demonstrated.
  Reserve guard freshowner23307. Original EFI restored before this boot;
  Ubuntu/storage/user data unchanged. After decisive clock sample, deliberately
  stopped CID2191 for cold read-only clock-state inspection; actualVMs[].

- Latest2026-10-11 RTC A/B COMPLETED: isolated EFI8192B write/readback and
  diagnostic APKCA52AC10...79A9 installed; production APK untouched. First launch
  observer initialization logging failed before receiving serial; corrected
  logging order, not a Windows result. Actual CID2190/owner20872 then proved:
  RTC_BEFORE_STATUS0, RTC_BEFORE_YEAR07EA=2026, RTC_ALREADY_PLAUSIBLE=NO_CHANGE.
  Therefore invalid initial EFI calendar is NOT the demonstrated cause; candidate
  intentionally did not SetTime. Same hot LSASS740/TID780 persists, kernel779 /
  user4092ticks at first observation; no desktop frame / CPU fix PASS. Resumed
  successfully after inspection. Stopped diagnostic; source EFI restored exactly
  SHA893B7859...02E8B, readback and GPT/FAT metadata unchanged, actualVMs[].
  Backups/reports rtc-device-preflight/apply/rollback-20261011; previous APK saved
  rtc-cam-guarded-build-20261011/previous-diagnostic.apk. All installed data intact.
  Next discriminator: Windows raw KUSER SystemTime vs precise-time/QPC projection.
  Prepared kernel_clock_metadata() FIRST in late observation, before expensive
  inventory: ten fixed PUBLIC kernel clock reads, coherent high halves/seqlock;
  no process/user data or hardware mutation. Unit PASS; not yet runtime proven.
  Raw future date implies Windows clock initialization/persisted-time path;
  raw2026 vs precise future implies counter/projection inconsistency. Do NOT
  assume either, repeat RTC variants, or change counter/timer flags blindly.

- Latest2026-10-11: actualVMs[], deviceonline. CID2188 scheduled observation
  reached the hot thread (LSASS732/TID776, RtlGetSystemTimePrecise again), then
  failed in debugger context transfer BEFORE kernel return probe. No new date /
  timeout result; do not repeat that diagnostic launch unchanged.
  Prepared next minimal CLOCK A/B instead of further saved-PC sampling:
  build-logs/windows-post-linux-reassessment-20261004/rtc-cam-guarded-build-20261011.
  Recompiled unmodified control exactly SHA893B7859...02E8B (byte parity PASS),
  including frozen CAM SSDT3BB6E30F...9C68 and SAME GIC/loader mode. RTC candidate
  AAD53298...46F5D is SAME8192-byte size. Standard EFI GetTime/SetTime only;
  preserve already-plausible date, correct invalid/far-future date using trusted
  host UTC, require actual GetTime readback or abort clearly. No RTC pointer
  hooks, timer/CPU mutations or signed-userland changes. Mocked RTC tests and
  original GAS selftest PASS. Candidate NOT deployed; installed disk/APK intact.
  Next: back up exact current isolated BOOTAA64 bytes; prepare scoped diagnostic
  owner acceptance of ONLY this candidate hash, fixed-size same-file write +
  readback/rollback. One boot must prove EFI clock marker, actual Windows date,
  LSASS CPU delta and restored desktop; no CPU fix claim until runtime A/B.
  Do NOT use the earlier7168-byte non-CAM preflight shim (it lacks paired CAM).
  C50.94/D57.71/E82.30GiB free. Ubuntu unchanged, no commit/push. SMP remains
  mandatory after CPU cause is fixed; all other Windows goal milestones open.

- CID2186 one return probe reached NtWaitForAlertByThreadId8f7d74 but observer
  refused EL1t (it incorrectly required EL1h). Retained checksum-validated wire
  proves PSTATE82001144, exact hot LSASS736/TID784/thread identity and returned
  NTSTATUS102 = TIMEOUT. Only this typed W0 extracted, no general GPR dump.
  Timeout/date had not yet been read, so immediate-wait/clock cause NOT proven.
  Corrected guard to permit real EL1t OR EL1h, still reject EL0/EL2/other sites;
  tests PASS. Wrapper stopped the diagnostic correctly after refused observation.
  Current CID2187/owner9898/observer7061, evidence
  kd-windows-lsass-el1t-wait-return-20261010, repeats ONLY this newly corrected
  typed observation; first120sec point can be delayed by real loader activity.
  Same frozen boot adaptations/config/data. Reserve guard freshowner9898.
  Exact-source offline windows-time-deadline-audit.py verifies SFAPM opcodes /
  UTC100ns*100 arithmetic; observed previousFILETIME458317211425910348
  (3053-05-08) yields signed negative deadline. Still needs actual current date
  and typed wait duration, then ONE clock correction A/B before claiming CPU fix.

- CID2185 actual runtime PASS for staged SetContextEx: two inventories completed,
  full context readback proved PC+4 with unchanged registers, same guest resumed
  both times. This fixes debugger-induced frozen-test state, NOT the CPU cause.
  Hot LSASS748/TID788 still busy: original ntdll!RtlGetSystemTimePrecise+0xb0
  called by kernelbase!GetSystemTimePreciseAsFileTime, then sfapm+1b120.
  SFAPM's observed worker uses msvcp_win condition variables / _Mtx_unlock.
  Original SFAPM19ef8 calls _Xtime_get_ticks (UTC100ns since1970), then19f24
  computes signed64 nanoseconds via multiply100/add duration before _Cnd_timedwait.
  Year3053 overflows this to a NEGATIVE deadline (static arithmetic proof), a
  strong link between observed far-future guest date and immediate-wait spin.
  Runtime current date + exact wait status remain required; do NOT call root
  cause confirmed or disable SFAPM. No signed-userland/security changes.
  CID2185 stopped deliberately after two successful resumes, actualVMs[].
  Current CID2186/owner7912/observer3882, evidence
  kd-windows-lsass-one-wait-return-20261010, performs the ONE prepared kernel
  return observation after180sec; boot adapters PASS, late point may wait for
  loader-message silence. No further random saved-PC or CPU flag sweep.
  Reserve guard attached to freshowner7912. If time-overflow evidence confirms,
  next minimal A/B should correct invalid isolated Windows RTC before boot,
  not patch authentication code. SMP remains required AFTER CPU root fix.

- Current CID2185/owner1592, observer61481, evidence
  kd-windows-lsass-two-range-resume-20261010: kernel connected and existing
  CAM/timer/PMCC adapters completed; same VM now awaits its scheduled late
  inspection. No CPU/config/disk/broker change. CID2184 had two distinct lost
  write transactions, so scoped recovery now permits TWO distinct ranges max,
  once per range, each needing exact pending packet + actual original/completed
  byte proof. Partial/other mismatches and context/continue retries still denied.
  Unit guards PASS. PC-only-semantic staged context restore awaits runtime proof.
  If saved context again misses the wait result, prepared ONE deterministic
  NtWaitForAlertByThreadId return probe (8f7d74); guard exact hot PID/TID/thread,
  original instruction/accessors, EL1 and bounded typed kernel SP+10 timeout.
  No user stack, credentials, signed-userland or wait-result mutation. First
  unrelated return reports unavailable, no rearm;60sec bound stops diagnostic.
  Offline test PASS; flag UAVF_KD_WAIT_RETURN=1 is NOT enabled in current CID2185.
  Prefer this precise result over another chain of random saved-PC snapshots.

- Latest checkpoint, 2026-10-10: diagnostic Windows VM is stopped; actual
  `vm list` reports `Running VMs: []`. Installed disk/data and Ubuntu unchanged.
  CID2183's three bounded same-VM snapshots put hot LSASS PID740/TID780 in
  ntdll!RtlQueryPerformanceCounter (called by msvcp_win!_Query_perf_counter),
  RtlSleepConditionVariableSRW lock reacquisition, then sfapm+119e8/LR+11fb8.
  Exact original code windows match. These are saved contexts, NOT continuous
  sampled PCs; no precise hot instruction or timeout root cause is proven.
  Lead: SFAP authentication worker / chrono / condition-variable cycle.
  Do not disable authentication providers or inspect credential caches.
  Third observation failed restoring the full912-byte context; historical
  Surface PASS incorrectly kept the debugger-halted guest. Stopped it explicitly.
  Prepared PC-only semantic update via staged32-byte SetContextEx packets,
  full unchanged-register readback and fail-closed retention
  on OBSERVER_STOP; unit tests/parser PASS, runtime verification still pending.
  Corrected the typed result reader: BaseSetLastNTError returns a converted
  Win32 error, not NTSTATUS (ERROR_TIMEOUT1460); no runtime result collected yet.
  Next: localize the worker's actual wait/deadline result without more blind
  cold boots; prove debugger resume before retaining a diagnostic guest.
  Mandatory next performance milestone AFTER root fix: enable and prove SMP.
  One vCPU is diagnostic only, never the production Windows acceptance target.

- Completed controlled run: CID2184/owner32247, observer30461, evidence
  kd-windows-lsass-resume-staging-20261010. Purpose: verify debugger resume
  after the existing late inspection, not a CPU flag or display experiment.
  Static exact-kernel audit KdpSetContextEx RVA4f8240 shows its third input
  field is total context size; offset+count==total triggers conversion/commit.
  Corrected draft packet schema before runtime (zero third field was invalid).
  Full912-byte original context with onlyPC+4 changed is staged in32-byte
  packets, then full readback; no ambiguous mutation retry. Guards PASS.
  Reserve guard attached to freshowner32247; C51/D57/E82/tablet55GiB free.
  Result: reached kernel but failed BEFORE runtime inspection, while staging
  unchanged PMCC adapter. One missing write at665c80 was proven not applied
  and recovered; another transaction hit the once-per-boot recovery limit.
  No SetContextEx runtime result, no LSASS/performance result. Wrapper stopped
  isolated VM correctly (actualVMs[]). Do not repeat this boot unchanged or
  count it as a CPU/SMP experiment; preserve evidence and resolve the bounded
  debugger transport prerequisite before the next runtime observation.

- 2026-10-10 20:51: CID2182 completed its single late inventory and resumed.
  LSASS PID728/TID772 running, kernel4379/user31875 ticks; current-process
  ownership verified. Saved PC kernelbase+1c7250 = BaseSetLastNTError+28,
  LR kernelbase+10cb0c = SleepConditionVariableSRW+3c. Original official index3
  kernelbase866361...F142 code window matches; public PDB GUID matches (server
  stripped age2 vs PE age1, do not claim exact age equality). Imports independently
  prove RtlNtStatusToDosError/RtlSetLastWin32Error. This and earlier ntdll wait
  snapshot point to the SAME condition-variable wait/result cycle, not a new
  unrelated hot function. Initial syscall-only timeout guard correctly refused
  to read another context. CPU return status/timeout still unknown; no frame yet.
  Extended exact-source guard now supports this observed result epilogue:
  read typed Win32 error4, timeout8 and one saved CODE return pointer only.
  No credentials/general stack/register dump. Offline guards PASS. Next one
  boot starts observation at360sec (hot CPU onset already proven), then allows
  at most2 explicit same-VM snapshots60sec apart, replacing repeated cold boots.
  Exact-schema/sequence/bound/replacement-owner tests PASS. Do not change CPU,
  codec, signed Windows files or Ubuntu before the typed result is observed.

- SMP readiness (read-only audit while CID2182 runs): ACPI GICC/GICR entries
  are dynamically derived from FDT, so no static one-CPU MADT rebuild is needed.
  BUT uart-gas-shim.c GIC audit/DS active-PPI guard hard-codes0x3ffd0000/
  0x3ffe0300, i.e. the one-core redistributor/SGI bank. Eight-core range starts
  0x3fef0000; those constants then refer to the last CPU, not boot CPU. Before
  SMP test, derive the boot CPU redistributor from actual MADT/FDT + MPIDR and
  preserve the existing no-active-interrupt guard. Do not blindly change only
  CPU_TOPOLOGY_ONE_CPU. No current launcher/firmware/APK was changed or deployed.

- 2026-10-10 20:34: CID2182/owner19403, observer16923 remains live. New
  guarded recovery runtime PASS: own665c40 chunk read back original zeros,
  ONE fresh synchronized write then exact target readback succeeded; full
  PMCC adapter3fb18c...d9d and existing CAM/timer/quiet-symbol guards finished.
  Kernel runtime continued in same VM. No more reboot: wait for the already
  scheduled ONE600sec LSASS timeout observation (runtime start~20:32:26).
  Native service/frame/CPU evidence for this boot still pending. This proves
  transport recovery, NOT the LSASS root cause or Windows performance fix.
  Battery100%/USBpowered, C51GiB/D57GiB/E82GiB/tablet55GiB reserve remains.

- 2026-10-10 20:29: CID2181 readback recovery produced hard new evidence:
  after RESET, requested own PMCC padding chunk665ce0 was still32 exact zero
  bytes, matching the guarded original; write was NOT applied. Failed closed
  and stopped isolated VM. Added ONE fresh synchronized write only when that
  exact original-byte readback resolves ambiguity; partial/other mismatch still
  fails closed, no generic mutation replay. Positive completed/not-applied and
  negative scope/API/address/once/partial tests PASS. Next run uses this proven
  distinction to finish the unchanged boot adaptations then inspect LSASS wait.
  CPU cause/performance still unproven; do not count these transport boots as
  LSASS experiments. No changes to Windows disk, signed userland or Ubuntu.

- 2026-10-10 20:20: identical retry CID2180 also ended before the timeout
  observation: previous CAM-byte READ succeeded, next WRITE got only RESEND.
  No more identical retries. Added one tightly guarded protocol recovery:
  RESET + READBACK ONLY for existing exact CAM bytes/own adapter padding.
  Never replay WRITE/context/continue after reset. Only actual matching target
  bytes can confirm a write whose response was lost; mismatch fails closed.
  Offline pending-API/address/data/scope/once/mismatch guards PASS; existing
  read-only recovery/negative mutation tests remain PASS. Next single launch
  tests this new recovery AND the already prepared wait-timeout observation.
  No UART/CPU/disk/guest-security change; no performance success claim.

- 2026-10-10 20:17: timeout observation run CID2179 ended BEFORE LSASS.
  RX/TX wire audit: previous32-byte READ at PMCC cave0x665c40 succeeded;
  next32-byte WRITE at0x665c60 received only RESEND, no matching WRITE response.
  Observer correctly failed closed on ambiguous mutation; wrapper stopped only
  the isolated diagnostic VM (VMs[]). Not a timeout/CPU test result. One
  identical retry is allowed for this evidenced transient KD transport failure;
  no UART/CPU flags, disk/bootstrap, signed files or Ubuntu changes.

- 2026-10-10 20:12: same CID2178 late native snapshot persisted LSASS counters
  and source timing; display still severely slow, no decoder backlog. Input
  writes reached app channel; password field then returned to lock screen,
  but this does NOT establish successful authentication. Prepared ONE next
  purpose-built observation of the exact condition-variable wait: typed8-byte
  timeout plus kernel timer/shared-time metadata, only for verified current
  running hot thread, matching original ntdll code+PC+LR and bounded pointer.
  Offline relative/absolute/immediate/infinite and negative address/code/current
  thread guards PASS; no arbitrary LSASS memory/stack/register dump or writes.
  One diagnostic restart justified by new timeout evidence, not flag/codec
  changes. Preserve installed disk/account; no reapply/reinstall/format.

- 2026-10-10 20:05: CID2178 reached the installed lock screen and the single
  late read-only observation succeeded; guest resumed in the SAME VM. Native
  counters isolate LSASS PID744/TID784. Current-process ownership and same-boot
  module map verified: saved PC = ntdll!ZwWaitForAlertByThreadId+4 (RVA0x2e74),
  saved LR = ntdll!RtlSleepConditionVariableSRW+0x1e8 (RVA0x6c208). The exact
  48-byte source instruction window matches the original signed ntdll.
  This is last saved syscall context, NOT continuous hot-PC sampling. It
  narrows the next investigation to wait/timeout correctness; do not infer
  crypto, entropy or a current instruction from it. No user stack, credentials,
  general LSASS register dump or signed-userland patch. WPR yielded no sampled
  PCs and must not be repeated for that purpose. Severe low FPS persists.
  One vCPU is ONLY diagnostic: after the CPU/wait root fix, enable and verify
  SMP before performance acceptance. Ubuntu and installed user data unchanged.

- 2026-10-10 19:48: prior run CID2177 ended after USB disconnect; now VMs[].
  Cold read-only extraction preserved broker slot58f843...dbe2 and private
  WPR ETL2,621,440B (etw-broker3228-cold-profile-1944). START/STOP both0,
  eventsLost0, but ZERO sampled-PC events; event catalogue confirms only
  loader/thread/process/config metadata, so no sampling/performance PASS.
  Do not repeat WPR looking for CPU samples without a new sampling source.
  Native8snapshots isolate LSASS PID732 hot TID776/base-priority9; others mostly
  idle. ETW DCStop rundown decoded (not covered by StartGroup): TID776 entry
  ucrtbase.dll+0x2afe0, a generic thread entry, NOT the hot code. LSASS module
  map contains no viosock provider; discard that speculative explanation.
  Earlier180sec KD inventory proved smss/winlogon/services/LSASS, but current
  thread owner read unavailable. Kernel-only metadata now tolerates that exact
  unavailable status, disables ALL user-address reads; unreadable sleeping
  thread stacks preserve counters/unknown PC. Offline owner/no-user-read tests
  PASS. Next single observation AFTER native CPU workload exists (~600sec):
  LSASS saved-PC/LR/counters, correlate with SAME-BOOT module metadata only.
  Do not reuse CID2177 ASLR addresses in another boot. No CPU/codec/security
  mutation, reinstall, Ubuntu changes, commit/push. SMP follows CPU root fix.

- 2026-10-10 16:26: CID2175 did reach kernel, but debugger exchange stalled
  during readback of the first CAM byte, before desktop/service execution.
  Captured valid RX/TX show cached READ responses/RESENDs; this run is NOT an
  LSASS/WPR result. Wrapper stopped isolated VM (VMs[]). Added one bounded
  transport epoch recovery for idempotent stopped READ/context/version queries
  only; writes/continue/context mutations still fail closed on timeout.
  Actual-method offline recovery and negative mutation fixtures PASS, existing
  ACK/address/idle/write-segmentation tests PASS. Same guest/platform/broker
  launched as kd-windows-read-epoch-recovery-20261010, exec52021. No disk rewrite.
  Await fresh broker/thread/profile evidence; no hot-PC or performance PASS yet.
  CID2176 then stayed silent after winload unload while crosvm used~96% of
  one host CPU. One bounded KD break byte sent through existing same-UID
  diagnostic ingress pipe (no socket/SELinux change) returned the SAME winload
  unload state at0x809862f8; observer resumed it. This localizes that silence to
  a boot-debug exchange, not evidence of LSASS or display failure. No reboot.
  Standard transport RESET then immediately restored kernel entry in SAME VM;
  bounded READ recovery allowed all guarded CAM/timer/PMCC adaptations to finish.
  No fresh service/listener after~8min. A single diagnostic break resolved to
  KdCheckForDebugBreak/KeAccumulateTicks/KiUpdateRunTime/KiUpdateTime, i.e. clock
  interrupt path, not a sampled LSASS hot PC. VM stopped, data preserved.
  Automatic exact unload+CONTINUE transport recovery now one-per-boot, negative
  mutation/other-state fixtures PASS. Next run recovered-runtime-boundary with
  ONE180sec process/code observation uses this proven read recovery (not a
  repetition with unchanged failed transport). No new image, flags or codec.
  After CPU root fix: enable/prove SMP; one vCPU is diagnostic, not production.

- 2026-10-10 16:05: CID2173 broker2392 normal-priority WPR startup timed out1460;
  process CPU samples show recorder almost unscheduled while LSASS monopolizes
  vCPU. No ETL/hot PC PASS. Correct password accepted/Welcome, then diagnostic
  Stop/VMs[] verified (not clean guest shutdown). Own recorder start/stop children
  now HIGH_PRIORITY_CLASS only; recorder exits before steady20sec sample window,
  LSASS/user app priorities unchanged. Exact own-instance cancellation after
  timeout; explicit --cpu-profile entry for future tests without reboot.
  Added limited LSASS thread CPU/start-address queries, no suspend/context,
  LSASS memory reads or SeDebugPrivilege. Strict ARM64 build4677FF76...BD062A1F
  PASS; owned broker-only cold49sectors/full readback PASS new slot58f84338623e732a7ee2253dcef415b900e8b9ab8389486cd688e5ba0c93dbe2.
  Original6d1a...d003 retained runtime-etw-priority-cold-backup-20261010.
  Current boot kd-windows-lsass-etw-priority-runtime-20261010 has no late KD break.
  Goal still incomplete. No blanket priority/timer/CPU flag changes.
  Disk reserve recovered: three obsolete intermediate WIMs copied/hash-verified
  to D:\\UAVF-archived-staging-20261010 then only their C staging sources removed;
  archive manifest has recovery paths/hashes. C51.17/D57.71/E82.30GiB.
  Initial E archive copy was stalled/unverified; helper cancellation requested,
  never use that copy as backup proof. Current media/baselines/Ubuntu unchanged.

- 2026-10-10 15:46: bounded late KD read on CID2172 reached native break but
  process inventory/resume failed in RESEND/ACK exchange. NO hot-thread PC or
  LSASS root cause obtained; do not repeat this same late KD experiment.
  Earlier late-inventory branch consumed the hot-process option; routing fixed
  offline, not a runtime PASS. Exact diagnostic Stop/VMs[] verified.
  Prepared short native ETW observation: actual embedded WPR XML parser PASS,
  only ProcessThread/Loader/SampledProfile, 4MiB circular MEMORY buffers, no
  stack/heap/credential dump or signed code/security mutation. Own named WPR
  instance, one20sec recording per service boot, own ETL/log preserved locally.
  Strict native ARM64 compile PASS raw9DFDA90F...54C7E014; guarded broker-only
  cold update46sectors/full readback PASS, new slot6d1a95a533824f3e3ea1bf3e54286c71fa82a6cefbacfb5eaffe0c8b82b8d003.
  Fresh old broker backup runtime-etw-cold-backup-20261010. New unchanged-platform
  boot kd-windows-lsass-etw-runtime-20261010 has NO late KD break; native WPR
  startup/results/ETL still pending. Source code from exact original index3
  extracted on PC only (8MiB), symbols for lsasrv/ntdll downloaded from Microsoft.
  Live NTFS signed-code extraction was not reconstructed PE (sparse/WOF or
  noncoherent); never use those bytes as executable identity or corruption proof.
  Production Ubuntu/data untouched; no commit/push. C50.91GiB/tablet56.14GiB.

- 2026-10-10 15:22: CID2171 fresh native CPU log persisted and read-only
  snapshot PASS: cpu-observation-live-1522. Seven bounded intervals show idle0,
  lsass.exe PID728 accounting for about76–112% of one CPU in guest counters;
  encoder about0.09–0.18%. Over100% reflects non-atomic process/system sampling,
  not extra cores. This localizes scheduling starvation to LSASS workload,
  not a saturated video encoder or Android queue. Do NOT kill LSASS, disable
  authentication/security or change CPU/timer flags. Next bounded observation:
  hot thread saved instruction PC/module, code metadata only, no credential dump.
  Login field input is delayed; no repeated blind password submissions.

- 2026-10-10 15:30: CID2171 accepted the correct password/Welcome; fresh
  Default producer5112/encoder3168 started, still about0.4FPS and near-zero
  encoder CPU. Guest shutdown command attempted, completion NOT confirmed;
  diagnostic Stop then VMs[] verified, observer26283 ended on channel EOF.
  Prepared one evidence-driven cold boot with a single late LSASS code
  snapshot480sec after kernel runtime start (same launch/platform/fixes).
  Exact public PDB offsets verified; actual-function fixture guards PASS,
  Python/PowerShell parser PASS. Snapshot only PID/thread CPU/saved PC/LR/code
  and loader names, no user stack/credentials/dump; no LSASS kill/priority or
  signed userland mutation. Evidence kd-windows-lsass-code-observation-20261010.


- 2026-10-10 15:07: same CID2171/exec26283 passed kernel entry; guarded CAM,
  periodic IMASK and exact EL0 PMCC trap adapter installed/verified, then fresh
  win32k/cdd load events. Native CPU samples still pending service startup.
  No second restart, unchanged platform. Reserve58986448KiB remains above guard.

- 2026-10-10 15:05: CID2171/owner12070 and observer exec26283 verified live;
  current boot still serving winload boot-file requests, new events continue.
  Runtime kernel adaptations/source/CPU-service evidence not fresh yet; do not
  confuse missing frames at this phase with another display failure. Retain
  same run, no extra restart. CPU summary utility selftest PASS (kernel includes
  idle subtraction, process deltas, PID reuse and broker boot boundary). It
  explicitly labels unaccounted time unknown, not a proven interrupt storm.

- 2026-10-10 15:00: actual complete frame stall localized separately: CID2170
  observer ended on native user first-chance breakpoint0x80000003 at
  0x7ffaf79554cc and did not resume the target. Offline RX packet confirms
  state240bytes/FirstChance1 at offset184. This explains zero frames after
  observer exit, NOT preceding boot-wide slowdown or0.3FPS. Corrected hold-runtime
  observer narrowly passes first-chance user breakpoints with
  DBG_EXCEPTION_NOT_HANDLED0x80010001, no PC/code writes; actual guard6cases PASS.
  Kernel/second-chance/SIGILL still investigated, never blindly swallowed.
  CID2170 stopped through diagnostic UI, VMs[] verified (not clean guest shutdown).
  Own broker-only CPU observation cold update/readback PASS, originalc739...add6
  backed up, new slotb7ae075930f595bf844109959c6e2166c79cceb7a8d294467ebd9f156d621b0e.
  CPU sampler8bounded15sec snapshots: system idle/kernel/user + process CPU/time,
  basename only, no priorities/privileges/command lines/user-file changes.
  Fresh boot CID2171 owner12070 exec26283 / kd-windows-cpu-observation-runtime-20261010.
  Same platform/CPU/kernel/timer/config, existing encoder/protocol. Exact-owner
  reserve guard active; no Ubuntu mutation/commit/push. Await fresh CPU evidence.

- 2026-10-10 14:53: same CID2170 reached real Winlogon/H.264 Surface readback
  PASS. Fresh broker2736 started335093guest-ms; agent1044/Winlogon producer1172.
  New encoder2900 confirms session1/WinSta0/Winlogon. Captured0.37–0.80FPS;
  GDI about65–72ms (one397ms interval), I4200.36–0.45ms, encode834–1691ms,
  pipe-write0.02–0.08ms; Android queue0/~11ms callback. Crucially process total
  CPU grew only~109ms over~52guest-seconds, so encode wall duration does NOT
  prove CPU-saturated encoder. Investigate scheduling/waits/background load,
  retaining guest-clock caveat. Evidence source-observation-winlogon-metrics-20261010.
  Correct known password accepted after selecting ENG (first attempt used RUS).
  Welcome appeared; Default producer2156 started630062guest-ms, CID2170 fresh
  listener/ACK8 confirmed. Default encoder metrics not yet present. CtrlShiftEsc
  channel write attempted, Task Manager UI not yet confirmed. No further reboot.

- 2026-10-10: tablet reauthorized, battery100%, initial VMs[]. Prepared strict
  ARM64 encoder observation build A1F5048CF1668327015BED0E55345C5D2418CB2B2EBDFAB1A9941305D93F6E5A.
  Adds GDI/conversion split, QPC/tick/wall correlation and process CPU times;
  original15FPS/UVF1/codec/source unchanged. Encoder-only cold update/readback
  PASS, slot79872bytes SHA b26fcfb5496cf32fd5f74db2829f68a0628b54409e0ade19b56cb5c42c97805e.
  Fresh original backup in encoder-observation-prepared-20261010; no hive writes.
  One unchanged-platform boot running CID2170 owner32099, exec13429, evidence
  kd-windows-source-observation-runtime-20261010. Boot loaded win32k/cdd; fresh
  source metrics not yet available. On-device52GiB guard started for this owner.
  C51.44/D59.50/E82.90GiB; tablet56.18GiB free. No commit/push/Ubuntu changes.
  Follow-up14:49: same CID2170/owner32099/exec13429 verified live; new load events
  progressed through appid, SMB, mmcss, network policy and wtd. Native
  SystemEventsBroker timeout repeated (51703guest-ms) before fresh encoder
  metrics or service marker. Android received0frames so far; old persisted
  broker entries are NOT evidence of current service startup. No reboot.

- 22:39: battery4%, tablet tests stopped at user limit. CID2168 eventually
  displayed installed Explorer desktop22:34 and Task Manager window22:36,
  so black source was temporary; extreme startup/performance slowdown remains
  unresolved. No causal attribution to observer detach. Diagnostic UI stop
  confirmed VMs[] (app-side stop, not proven clean guest shutdown). Root/account
  and Ubuntu untouched. Source-observation encoder still NOT staged; next test
  after charging must observe actual process/source/clock state.

- 22:30: CID2168 legacy Run retirement PASS; password accepted and Default
  producer4832 started, but current Desktop capture is black/slow, NOT Desktop
  regression PASS. Native source report capture1591.816ms/encode2170.227ms on
  guest clock; Android queue0. Viewer-only reconnect and post-boot observer
  detach did not initially restore good frames; no VM config/platform change.
  exec68229 now terminal (explicit observer detach), VM retained by app service.
  Source inventory encoder ARM64 build PASS but NOT staged. See
  WINDOWS_DISPLAY_FOLLOWUP_20261009.md for next exact observation and evidence.
  Battery5%; user explicitly requested stopping tablet tests at3–4%.

- 22:00: CID2168 / owner5997 / exec68229 cold boot reached native Windows
  lock/login screen with live app-owned H.264/MediaCodec frames. Fresh own log
  confirms SERVICE_STARTED -> LEGACY_RUN_RETIRED value=0 -> session agent ->
  Winlogon producer. Exact-owned Run value only retired by native SCM service;
  absent/custom/type/truncation preservation selftest PASS. No SYSTEM hive,
  Windows kernel, firmware, layout, user-account or Ubuntu changes.
- Previous CID2167 native shutdown command reached shutdown screen, VMs[],
  and SERVICE_STOPPED. Old observer explicitly retired only after real VM exit;
  its resulting runner exit1 is not a Windows crash.
- Own broker-only cold update/readback PASS: padded helper SHA256
  c73980449c6b246d5f4e86fbad51a4c3f1166ba93ec7087b756fd55b9f77add6.
  Evidence broker-retirement-applied-20261009 and
  broker-retirement-live-markers-second-20261009. Current login regression in
  progress. IDD virtual-monitor path only researched, not installed/proven.
  Tablet battery9%, USB still discharging; avoid long blind runtime waits.

- CURRENT21:38: CID2167 owner28113 / exec32488 remains live. Diagnostic APK
  SHA02564AB2754023CA50F4386A32AACC178D5DD9B389F43AA6010FC2175EF617E4.
  Minimal known-good observer passed guarded CAM/CNTV/PMCC adaptations; fresh
  SCM log adds service2552 / session agent2944 / Winlogon producer3036.
  App-owned native login -> accepted existing password -> Explorer Desktop PASS,
  live stream ~12–13FPS640x480; producer/MediaCodec generation changes across
  login without VM restart. repeated-installed-desktop.png preserved. Current
  stream continuously responsive to Win+R, Alt+Tab, Enter, Unicode text, touch
  focus and PageDown. Full input/lifecycle/performance parity NOT yet achieved.
- Windows Settings actually opened via app-owned UIN1; Display resolution
  shows640x480 and selector disabled. This is source-side mode limitation, not
  merely Android viewport sizing. Need bounded mode inventory/GOP investigation
  before any platform change. No1920x1200/60FPS claim.
- New observation: legacy HKLM Run display CMD visibly floods Access denied
  after login. Source installed-display.cmd writes LocalSystem-created
  ProgramData folder and competes with SCM broker. Source fix prepared: exit
  if broker RUNNING; otherwise standard-user LOCALAPPDATA diagnostics. NOT yet
  applied to running Windows disk; preserve current SCM stream while integrating.
- Native-debugger branch frozen as R&D: packet batching restores many memory
  exchanges but boot-debugger ReadControlSpace0x3135 returnsC0000001 repeatedly.
  No native RAM adapter PASS. Do not spend further boots on DbgEng initialization.
  New native TX batching unit verifies exact byte identity including 1-byte
  fragmentation. Host journals now persistent handles, not open/close per byte.
- UART trace/fix: successful pipe writes +FIONREAD now observed. Native debugger
  emits1-byte fragments; old sleep-per-fragment imposed1ms/byte instead of
  nominal8bytes/ms. Byte-credit pacing now spans input read boundaries; actual
  compiled helper selftest, exact synthetic output/post-write logs paced/unpaced
  PASS. Trace queued0/1bytes shows downstream draining (NOT guest-consumption
  proof). No VM config/kernel/disk/CPU changes; prior APK saved for recovery.
  Reserve C50.48GiB/D59.50GiB/E82.90GiB/tablet~56.35GiB, guard owner28113 active.

- LATEST21:17: native-debugger series ended; isolated CID2163 explicitly stopped.
  No new native-kernel or desktop PASS. Preserve the earlier installed Desktop
  proof/root/account; do not reinstall or restore old hives to fix debugger I/O.
  CID2162 read Winload module list/headers then stalled READ64-after-response.
  ACK budget corrected to reset only on a genuinely new target response, not
  alternating ID; duplicate cannot renew budget. Unit PASS.
  CID2163 still stalled GetVersion0x3146 after Winload state PC809862f8.
  Evidence: kd-windows-native-per-exchange-ack-20261009-runtime (exec51045).
  It never reached the native RAM adapter, so v4 script runtime remains untested.
- Native v4 exact module-base parser and .load JsProvider correction offline
  PASS; startup now has one guarded command-file replay at observed second
  boot-debugger reconnect (runtime untested). Same existing RAM bytes/guards.
  Do NOT repeat another ordinary boot or sweep pacing/CPU/timer flags next.
  Next information gap: bytes accepted by Android TCP/Base64 != bytes actually
  consumed by guest UART. Need bounded positive UART-ingress/write evidence or
  a demonstrated alternative for applying the already-proven RAM adapters,
  without guest disk patch, production-platform mutation or privileged access.

- 21:12: CID2160 native KD reproduces target RESEND on winload READ16
  at80c5cdf8, before kernel scripts. Host/Android decoded TX3235bytes identical
  SHA DB03D58CD5E15D336CCF8235C705FC1E496CD6AC4036F51B60136CA986C8CF30.
  No source corruption through Base64/TCP proven; UART guest acceptance unknown.
  Stopped isolated diagnostic run, retained installed root/account/broker.
- CID2161 owner22940 / exec78254: bounded control-only replay of last validated
  native ACK on explicit target RESEND permits full native Winload debugger
  initialization and startup commands. This is a workaround, not UART root cause.
  Offline fragmented/control-only/reset/max3 replay tests PASS. Native script
  used C++ expression nt incorrectly; correction uses exact lm m nt module base.
  Provider .load quoted Windows path loses backslashes; .scriptload nevertheless
  succeeds through auto-provider; generator now uses .load JsProvider.
  Still no native-kernel-adapters/display PASS; no disk/platform/userland change.

- 21:06 Moscow: native Microsoft KD A/B CID2159 stopped before RAM adapters.
  Host duplex pipe used a synchronous handle: read can serialize the write.
  Replaced host-only bridge with independent overlapped operations/events;
  real Win32 simultaneous-read/write loopback PASS. No Windows disk/config change.
- Control CID2160 owner21818 / exec73266: native KD now establishes ARM64 boot
  debugger connection (previous run could not). Guarded identical RAM adapters
  still pending; do not claim kernel/display PASS yet. Tablet reserve59086700KiB.
  Evidence: kd-windows-native-overlapped-pipe-20261009-runtime.
  Native command/JS preflight, exact mapped-image patch and fail-closed guard
  tests PASS; no new kernel workaround, no smss/userland patch.

- CURRENT terminal checkpoint20:42 Moscow: CID2157 reached kernel adapters but
  no Surface/listener for8minutes; explicit isolated diagnostic stop, VMs[] and
  exec64475 terminal. Own live log snapshot is read-only/non-coherent, includes
  only previous persisted broker markers; cannot prove current service absence.
- One new observation-enabled run exec59300,
  kd-windows-installed-late-process-boundary-20261009-runtime, failed before
  late inspection: target repeatedly RESENDed after successful cave WRITE16
  response, while host attempted verification READ16 at kernel+665b20.
  Kernel basefffff80012a00000; target reply addressfffff80013065b20.
  VM/runner terminal. ACK replay is NOT a complete transport fix. Do not keep
  repeating these boots or call this a service/root/SIGILL regression.
- Added optional one-shot late KD process inventory (120..1800s, only retained
  runtime, skipped if current Surface PASS). Exact native break guard, process
  list bounded256, resume attempted even if inventory read incomplete. Source
  compiles; runtime UNTESTED because early exchange failed. No guest disk write.
- NEXT: existing Microsoft kd.exe installed at WinDbg1.2606.22001.0/amd64.
  Audit/reuse its mature KD transport over existing app-owned host TCP39101
  via host-only named pipe. Do NOT invoke old production-image/product-APK
  launch scripts: they mutate unrelated paths. Keep identical isolated VM,
  native kernel and exact guarded RAM adaptations; no new platform flags.
  Need guarded native-debugger adapter/command preflight before another boot.
  Objective remains full1920x1200~60FPS/input/network/audio/clipboard/lifecycle
  and multi-workspace parity; current login/desktop proof alone is incomplete.

- LATEST 20:28 Moscow: previous CID2156 reached visible native Winlogon login,
  authenticated existing user via UIN1, then real Explorer desktop. Screenshot
  verified-user-login-screen.png retained. Service log independently proves
  SERVICE_STARTED(Session0), console agent(Session1), Winlogon producer3608,
  Default producer2648, later Winlogon/Default transitions. Own service source
  is now runtime-proven for pre-login capture/login transition, NOT yet sustained
  desktop reliability. Pre-login captured/encoded1659frames over135656ms,
  GDI+conversion73.298ms/frame, encode7.508ms/frame (old15FPS encoder).
- CID2156 became terminal (owner Socket closed, host observer EOF); storage
  reserve remained59086624KiB, no reserve-triggered stop shown. Do not infer
  Windows crash from this terminal host channel. Source logs cold-read under
  prelogin-service-actual-runtime-evidence-20261009-direct-index; some newer
  directory entries unavailable via current cold NTFS index, not proof corruption.
- CURRENT: CID2157 / owner15065 / exec64475,
  kd-windows-login-desktop-reconnect-control-20261009-runtime. Purpose: second
  installed cold boot/login and sustained desktop/reconnect evidence, no platform
  changes. CAM/timer/PMCC adaptations verified again; no frames yet. Reserve
  guard59086572KiB PASS. Broker stays installed; never restore old whole hive/root.
- Runner removes only stale isolated first-Surface screenshot before a new test;
  prior runs retain host evidence. Owned private crosvm export now chmod644 so
  ADB can retrieve the diagnostic file (no SELinux or system policy changes).

- CURRENT 17:20: CID2156 / owner17913 / exec8899,
  kd-windows-prelogin-service-ack-recovery-20261009-runtime. Same audited broker
  applied/readback PASS (prelogin-service-ack-repair-applied-20261009), now testing
  host KD ACK replay before retransmitting pending RPC. Unit tests of actual
  methods PASS; not a CPU/UART/platform flag change. Current cold boot passed
  the previous failed exchange and verified CAM/timer/PMCC adaptations. One
  pass supports recovery hypothesis but is NOT yet proof of boot reproducibility.
  Waiting actual SCM/pre-login producer; no frames yet. Guard59098136KiB PASS.

- LATEST: recovered service apply full readback PASS; one boot CID2155,
  owner10379 / exec78987 reached KERNEL_CONNECTED, but KD timed out BEFORE
  finishing CAM adaptations (before SCM/service). No service runtime verdict.
  Evidence kd-windows-prelogin-service-recovered-20261009-runtime. Terminal
  runner/VMs[] verified. Guard reserve59097872KiB passed.
- Wire audit localizes the failed exchange: KD write4bytes at kernel+49bacc,
  actual VAfffff800c949bacc, repeated target RESEND, no write response. Earlier
  reads/writes at the same stop succeeded. Host tx.bin and Android native
  owner-decoded-host-tx.bin SHA both8BEC3ADE...99882, bytes35438: app ingress
  corruption is excluded; actual UART/target acceptance remains unproven.
  kd-control-audit now exposes memory address/count/status. Observer additionally
  rejects stale same-API memory responses for another address/count (read-only
  retry bounded; writes never silently treated as successful). Compile PASS.
- Preserve fresh user/root state: exact guarded broker rollback exec23575
  completed DISPLAY_SERVICE_COLD_ROLLBACK_READBACK_PASS,
  prelogin-service-recovered-rollback-20261009. Prepared broker/recovered
  committed-hive artifact retained. No VM currently running.
  Unit test of extracted actual KD.rpc: stale-address read retries bounded;
  stale-address write refused without replay PASS.
  Do not repeat identical service boot; resolve this specific KD exchange or
  use independent safe observability first. No production/Linux modifications.

- LATEST superseding checkpoint, 17:06 Moscow: original-hive control CID2154
  reached kernel, verified CAM/timer/PMCC RAM adaptations, and live H.264:
  860 decoded/render callbacks at640x480, initially~4FPS, then0FPS. Tablet
  screenshot shows Windows blue background only; not verified login/desktop.
  Diagnostic Enter RPC confirmed channel write only. Saved control-display-report
  and control-background-frame in its runtime evidence. Forced isolated stop
  after saving stalled source evidence; authoritative VMs[] and observer terminal.
- New cold extraction prelogin-control-capture-evidence-20261009 includes bounded
  owned producer logs. Actual current producer7144, CID2154, listener4055,
  handshake8bytes; OpenH264 reported~3.9FPS. No completed encoder report/producer
  exit/exception: do not claim precise hang or desktop-switch cause yet.
- SYSTEM grew natively to8650752bytes, raw sequence364/363, SHA9377165a...989a.
  Retained matching transaction logs recover locally through sequence385;
  contiguous validated logs375..382 and383..385, same allocation. Native
  Offline Registry preflight adds only six service values, preserves existing
  values/security relative to recovered committed state. Raw original retained
  for exact guarded apply/rollback. Prepared22435998...54f3 in
  prelogin-service-recovered-preflight-20261009. Device apply exec4913 pending
  FULL binary readback; no boot until PASS. Current BCD temporary policy remains.
- Runner now verifies an actual live isolated VM before reporting retained PASS;
  historical Surface readback alone no longer implies VM retention. Parser PASS.

- LATEST superseding checkpoint, 16:58 Moscow: original-hive control is running
  CID2154 / owner32378 / exec33637, evidence
  kd-windows-prelogin-original-hive-control-20261009-runtime. KD now reports
  installed-system driver loads (including condrv.sys/WdNisDrv.sys); this is
  real progress beyond loader, NOT yet proof of login/desktop. Black display
  alone cannot distinguish pre-login capture absence from a guest failure.
  Reserve check59140440KiB PASS; guard attached to exact owner32378.
- Broker cold apply/readback succeeded, but its runtime was NOT proven:
  first boot failed in KD response before required kernel adaptations; retry
  explicitly hit repeated-boot failure policy. Temporary BCD IgnoreAllFailures
  (only element250000e0=1) was applied with semantic/security diff/readback PASS.
  Subsequent broker boot still failed before service startup.
- Exact broker rollback/readback PASS restores original SYSTEM4623ab43...5639e
  and own winpe-screen.exe2bbf8193...418b. Service source/preflight/artifacts
  retained separately. Current control uses restored originals, same APK and
  temporary BCD policy. Remove only temporary policy after clean boot proof;
  do not blindly restore a stale whole BCD. User/account/root preserved.
- Current likely display boundary remains user-selection/login before HKLM
  Run producer starts. User correctly flagged this; do not rearm OOBE, bypass
  login, or treat missing4055 as proof Windows has not booted.

- Repeat-boot display bootstrap repair in progress. CID2150 had no4055 listener;
  explicitly stopped ONLY isolated diagnostic package for cold evidence; VMs[]
  verified, exact observer ended. This was a forced diagnostic stop because
  native UI/channel was unavailable, NOT a clean guest shutdown proof.
- Cold SYSTEM is clean363/363, 8MiB SHA4623ab43...5639e, Select.Current1;
  SetupType/OOBEInProgress/SystemSetupInProgress ALL0, CmdLine empty.
  Thus native Setup completed; do not rearm/restart OOBE. HKLM Run starts only
  after login; pre-login capture missing remains the strongest local hypothesis,
  not proof of current guest execution/milestone.
- windows-display-service.c compiled ARM64 PASS, stripped17920bytes. Proper SCM
  service -> windowless console-session agent -> existing producer on active
  Default/Winlogon desktop. Own job objects bound child lifetimes; no desktop
  ACL changes, login bypass, new codec or signed Windows patches. Runtime UNPROVEN.
- Microsoft's signed C:\Windows\System32\offreg.dll prepared one added service
  with whole-hive value AND existing-key security diff PASS. RegLoadAppKey
  rejected SYSTEM (1009/uniform-security API constraint); failed scratch preserved,
  no guest writes from that attempt. Native Offline Registry path succeeded.
- Fixed-file device preflight PASS: only existing OWN unused winpe-screen.exe
  slot (same71168bytes; broker padded) and SYSTEM (same8MiB). No NTFS metadata/
  allocation/GPT/FAT/ISO changes. Full originals retained. Apply exec1253 currently
  writing bounded backed ranges, SYSTEM header last; must verify full binary
  readback/report before any boot. Rollback built in. Evidence
  prelogin-display-service-security-preflight-20261009 and
  prelogin-display-service-applied-20261009. No production Ubuntu/APK changes.

- CURRENT: CID2150 / owner15900 / exec35435,
  kd-windows-desktop-display-followup-20261009-runtime. Same frozen one-vCPU
  platform, kernel CAM/timer/PMCC adaptations verified in events. Existing
  installed root/user preserved. After ordinary boot, vsock4055 not yet listening;
  AVF remains live. Do NOT infer Windows hang from connect failures/black Surface.
- Most likely next boundary: ordinary boot reaches user selection BEFORE
  HKLM Run UAVFDisplay can start. Prior Desktop stream may still have been the
  SYSTEM firstboot producer surviving OOBE, so actual user-Run producer was NOT
  proven. Need pre-login capture bootstrap or independent process evidence,
  not repeated FPS tuning/reinstallation. User explicitly identifies user-choice
  screen as likely current state. Never bypass login/forge Setup completion.
- Prior CID2149 native Start -> Shutdown accepted, authoritative VMs[] verified
  before stopping exact observer5148/updating isolated APK. New APK SHA
  9564B67D2B624927441F76FF64920AE9A2C7728CA6D277AD77C5F1308C22660E
  installed successfully. Adds Surface-recreate fix/configurable dimensions,
  shell-UID + owner-token gated diagnostic UIN1 input via existing app-owned
  channel (layout-independent text, no contents/token logs). Not a second
  decoder/SELinux/socket bypass. Input RPC runtime test pending listener.
- Reserve guard attached to exact15900, 52GiB stop margin, available59569308KiB.
  Paths /data/local/tmp/uavf-windows-display-followup-reserve.sh and .log.
- Win/escape combinations tested through Android unexpectedly backgrounded
  viewer/returned host UI; avoid them for diagnostic commands until routing
  verified. Ordinary mouse taps/typed letters operated real Start/layout menu.
  Current account available; never change it. Password provided privately by
  user, not stored in checklist. Priority remains real1920x1200/~60FPS once
  repeatable pre-login -> Desktop capture is restored.

- Foreground-return regression localized: common EncodedDisplayProbe stops its
  codec when Surface is destroyed and invokes its recreate callback on return.
  Windows diagnostic callback only logged SURFACE_RECREATED, so black viewport
  remained despite same live CID2149. Recreated ONLY Activity and rebound same
  existing owner; no VM reboot. H.264 readback PASS, actual Windows lock screen
  visible (native idle lock), confirming producer/transport still healthy.
- Source fix isolated to WindowsDiagnosticActivity: callback replaces stopped
  receiver/codec; onPause releases keys/suspends receiver; onResume resumes
  Surface. No AVF owner/storage/shared Linux receiver changes. Java build PASS.
  New APK SHA22056C26C5C78CAEBA3F9C7BB4F3DD834AA80F4B6F826349E77F706238EEB80C
  built, NOT installed while owner VM live. Exact Desktop-good APK3C792012...
  retained in current evidence as U-AVF-Windows-desktop-good.apk.
- Native mode inventory execution waiting for current account unlock/password;
  asked user, never changed account/login state or tried guessed credentials.

- FIRST INSTALLED DESKTOP PASS (15:36–15:41): user completed native OOBE
  and confirmed desktop working. Same app-owned CID2149 / owner32530 stayed
  live through OOBE -> real Explorer desktop; no reinstall or VM restart.
  User-created account/settings must be preserved; credentials not changed.
- H.264 stream reconnected after the OOBE/user-session transition. Fresh
  Android report reached 3811 received/decoded/render callbacks, pending0,
  ~8–13FPS at640x480, ~7–8ms receive-to-render callback. This proves sustained
  transport/reconnect, NOT final1200p60 performance or display-fence latency.
  Evidence: kd-native-offline-oobe-20261009-runtime/first-installed-desktop.png,
  desktop-encoded-display-report.txt, desktop-owner-report.txt.
- Next: preserve this desktop baseline; inventory native display modes and
  actual user-session producer, then improve isolated Windows resolution/input
  and network. Audio/clipboard/product lifecycle remain unproven. Guest clock
 2247 and temporary kernel RAM fixes remain blockers for practical parity.
  Do not repeat early boot/OOBE experiments or mutate this user root blindly.
  Ubuntu production remains untouched. No commit/push.
- Display follow-up source audit: winpe-encode.c explicitly caps live cadence
  and OpenH264 at15FPS. Diagnostic Activity also pins decoder/readback/input
  coordinates to640x480. Raising native resolution alone would currently
  break bounds/input; fix these coherently AFTER actual mode enumeration.
- Read-only windows-display-inventory.c prepared and built ARM64 with
  -Wall -Wextra -Werror PASS. Enumerates current/registry/supported modes,
  adapter IDs, native dimensions and user/session; never sets a mode/registry.
  Guest execution pending: tablet currently foregrounded main Ubuntu menu,
  not Windows diagnostic viewer. Attempted Win+R did not reveal guest CMD;
  no further text/commands injected and no production Ubuntu actions taken.
- Prepared source-only display follow-up (NOT deployed into current VM/APK):
  encoder accepts bounded UAVF_WINDOWS_FPS1..120, default15 unchanged,
  logs five-second capture/encode FPS and conversion/encode/pipe-write cost.
  Pipe-write timing is NOT a vsock-send or capture-to-screen latency claim.
  Isolated Activity now accepts validated even display_width/display_height
  and consistently uses them for decoder bounds, aspect ratio, readback and
  touch/mouse coordinates; default640x480 unchanged. Common Linux decoder
  and production app unchanged. Native ARM64 compilation PASS.
  All isolated owner Java sources + unchanged shared decoder/keyboard compile
  PASS (existing deprecation/toolchain warning only), in separate evidence
  output. Current installed diagnostic APK and live VM were not replaced.

- 15:26 CID2148 native shutdown verified AVF VMs[]; only then exact observer
 29908 stopped and exec31436 closed. Runner reports observer failure due to
  operator termination, NOT Windows fault; guest already terminal and prior
  OOBE/display/input evidence retained. Native next launch started exec3256,
  kd-native-offline-oobe-20261009-runtime, installed disk, same platform/fixes.
  Purpose: one supported offline-OOBE test, not another early-boot experiment.

- 15:24 native offline OOBE preparation accepted. Current UI reached network
  requirement (VM network deliberately false). Staged493byte ARM64 Unattend
  with documented HideWirelessSetupInOOBE/HideOnlineAccountScreens only;
  no SkipMachineOOBE, no injected account or completion flags. Native
  sysprep /oobe /quiet /quit /unattend:C:\UAVF\oobe-offline.xml returned0;
  GeneralizationState stayed7, CleanupState2. Cached Panther XML readback
  contains both settings. Native CONX apphelp warning remains, not terminal.
- Updated firstboot2317 CRLF bytes copied to actual System32, native fc /b
  PASS; prior own script retained C:\UAVF\firstboot-before-v2.cmd. CmdLine
  own wrapper rearmed through native reg, query PASS. Evidence screenshots
  native-oobe-unattend-sysprep-exit0.png, firstboot-v2-copy-fc-pass.png,
  oobe-offline-cached-and-capture-rearmed.png. Requested native shutdown,
  verify authoritative VMs[] before next installed boot. No reinstall.
- send-winpe-file.ps1 now has explicit -InstalledSession mode limited to
  C:\UAVF\*.cmd/txt/xml; existing X: mode retained. Both plan checks PASS,
  actual installed staging XML493/helper2317 passed. It only stages own
  files; does not execute, auto-reboot or modify Setup state itself.

- 15:10 OOBE input PASS: actual Android tap accepted native Russia button
  and advanced to Russian keyboard page (oobe-input-region-advanced.png).
  Shift+F10 opens actual installed CMD; RU keyboard -> Alt+Shift -> English
  read-only registry probe executed, exit returns to OOBE. Source/Windows
  input remains common UIN1 path, no separate Windows Android decoder.
- Native online reg add/query UAVFDisplay HKLM\...\CurrentVersion\Run PASS
  (user-display-autostart-readback.png). Only our previously-absent value,
  to start same producer minimized in real user session after OOBE/login.
  Base provisioning source now includes guarded SOFTWARE autostart setup;
  source ASCII/label check PASS, fresh-install offline branch not yet tested.
  Current guest user-logon autostart has NOT executed/been proven yet.

- 15:06 OOBE SCREEN PASS: actual native region-selection page (Russia)
  through app-owned H.264/MediaCodec/Surface after installed-disk boot.
  CID2148 same live owner23776. Evidence oobe-region-screen.png; moving
  native wait-screen indicator preceded it, continuously decoded frames
  >1100, pending0, ~13-15FPS640x480. Not final resolution/performance proof.
  Next exercise actual OOBE input and finish account/desktop. No completion
  flags changed; Windows owns its successful specialize/OOBE transition.

- CURRENT OOBE boot: CID2148 / owner23776 / exec31436,
  kd-clean-native-oobe-capture-20261009-runtime. Exact installed winload80983000;
  kernel connected basefffff801c4200000. Reserve guard exact23776 active.
  Own CmdLine cold recovery readback PASS (one value, original hive retained);
  fresh post-specialize BCD restored readback SHA64AEF178...006F0.
  No OOBE/display success claim until actual native screen and stream evidence.

- BREAKTHROUGH: fresh clean installed native specialize PASS. Cold read-only
  extraction of actual Panther/UnattendGC and firstboot logs proves:
  Specialize return0, phase4 completed, setup.exe exit0, windeploy exit0;
  native requested reboot BEFORE OOBE. CID2145 was NOT a setup failure.
  Prior SpBcd system-store error absent; BCD System1/TreatAsSystem1 present.
  Evidence clean-installed-firstboot-offline-evidence-20261009 (~1.4MiB read).
- Actual post-boot SYSTEM independently extracted read-only (~9.4MiB total
  device reads): GeneralizationState7, OOBEInProgress1, SetupType2,
  SystemSetupInProgress0. OOBE is PENDING, not complete. Guest clock2247
  remains a separate compatibility issue, not the old specialize blocker.
- Next boundary is capture lifecycle across native Setup reboot. Existing
  wrapper restores stock windeploy CmdLine on entry, so next OOBE launch
  would lack own capture. Source installed-firstboot.cmd now rearms only
  own CmdLine AFTER native exit0 while OOBEInProgress1; no success flags.
  Updated source has NOT been copied into current guest yet.
- Isolated one-shot recovery preflight validates clean7.5MiB SYSTEM SHA
  0582a0d254dacef5021b93e7e7edd39631b1cdad169108084ab30093aa28b3e4;
  full registry inventory differs ONLY Setup\CmdLine. Existing cell capacity,
  NTFS backed-run mapping, exact root serial and rollback bytes checked.
  rearm-isolated-oobe-capture.py changes only our own launch command, never
  signed Windows code/OOBE completion/SystemSetup status. Application running
  exec33825; check report/readback before new boot. Restore CURRENT post-Setup
  BCD default from retained32KiB store, never old pre-specialize BCD.
- Both recovery WinPE attempts ended on KD response bound, without WinPE
  PASS; no more identical boot retries. Alternative cold NTFS evidence reader
  succeeded without guest reboot or full-image copy; Ubuntu/data untouched.

- CURRENT recovery retry CID2147 / owner19691 / exec80487;
  kd-clean-firstboot-evidence-winpe-retry-20261009-runtime.
  First recovery CID2146 ended on bounded KD response failure and runner
  force-stopped it without a WinPE PASS. One identical retry only; currently
  loader file requests continue, no kernel/WinPE proof yet. No config change.
  Reserve guard updated to exact19691 at firstboot-evidence-retry-reserve.log.
  collect-installed-firstboot-evidence.cmd prepared1251 CRLF bytes,8 staged
  input lines, NOT staged/run yet. Require actual idle English WinPE prompt;
  mount rootW:(serial3EF6-69B7) and sourceS:, then collect fresh logs.

- 14:52 CID2145 is TERMINAL: authoritative AVF VMs[]; logcat unregistered
  at14:48:12. Do not treat the visible old CMD as a current live frame.
  Display report now connecting/0FPS; attempted read-only input was NOT
  delivered. Setup completion vs failure is still unknown; no OOBE claim.
  Stopped exact observer2280 only after VMs[]; did not stop a live guest.
- Cold CURRENT BCD extracted32KiB SHA64AEF17812984115E2EC1A7C57C3C5430109B6A136DA87FD1DD0FF849E0006F0.
  Recovery changes only default GUID86df57c0-9fb3-11f1-a311-83a31eab37aa
  to preserved WinPE;25byte changes, complete hive inventory checked,
  device replacement readback PASS. Original fresh BCD retained.
  Next WinPE read firstboot-state/firstboot/Panther from existing NTFS,
  preserve evidence, then choose next installed boot from actual outcome.
  Recovery run kd-clean-firstboot-evidence-winpe-20261009-runtime, exec34760.
  No root formatting, no image restore, no Ubuntu or production changes.
- Final objective remains full Windows profile at Ubuntu parity, not merely
  first Setup/desktop: stable display/input/audio/network/clipboard/lifecycle
  and independent persistent Windows/Ubuntu workspaces. User reconfirmed.

- 14:39 FIRST CLEAN INSTALLED BOOT live CID2145/ownerPID14749, exec41345,
  kd-clean-installed-system-role-20261009-runtime. Prior WinPE shutdown VMs[]
  proved before launch. Same guarded mutable disk/no restore; Winload80983000
  and installed Windows paths, not WinPE ramdisk. Reserve guard exactPID14749
  at uavf-windows-clean-installed-reserve.log (~57GiB). Observer freshly applies
  proven RAM fixes; no second observer. Wait for real firstboot/Setup evidence.

- 14:37 boot preparation PASS: BASE_EXIT0, BOOT_PREP_EXIT0; source template
  BCD now has Description\System DWORD1 BEFORE first clean installed boot.
  Default readback points to partitionW:\Windows\system32\winload.efi,
  Windows11, NOT ramdisk. Frozen custom EFI/WINORIG fc checks passed in batch.
  Actual normalized helper bytes: firstboot1725,display567,bootprep4044;
  base3721. Updated firstboot records clock/state, still invokes signed stock
  windeploy and does NOT fake Setup success. Evidence clean-bootprep-system-role-
  pass.png / clean-installed-default-bcd.png in CID2144 run.
- 14:38 requested normal wpeutil shutdown for cold installed boot. Confirm
  authoritative VMs[] before next run. Earlier observer expired at3600s, but
  service retained VM with working capture; expiration was NOT guest shutdown.
  Free tablet~57.1GiB; host C53.9/D59.5/E82.9GiB. No source/Ubuntu restoration.
  WinPE clock24.08.2026 15:04 vs host09.10.2026 14:37, recorded not changed.

- 12:16 CLEAN APPLY PASS: actual100%, native success and APPLY_EXIT=0 from
  the batch's immediate errorlevel capture; clean-apply-pass.png saved.
  Same live CID2144/owner15926, no new VM. New root serial3EF6-69B7 after
  explicitly authorized quick format. First-attempt evidence remains under
  S:\UAVFRECOVERY\before-clean-specialize-20261009; source/EFI unchanged.
  Next CURRENT base/bootstrap/BCD scripts, then one installed boot. This clean
  installation has NOT yet specialized or reached OOBE; do not claim desktop.
- 12:13 secondary read-only CMD proved DISM1788 still alive; current log had
  no error, result marker absent then. W:\Windows\System32\tasklist.exe works
  in WinPE but slow; native WinPE tasklist absent. Closed only that secondary
  CMD, returned to main apply, then98%->100%. Never interrupted DISM.

- 11:40 LIVE: CID2144 / ownerPID15926, observer exec80283, evidence
  kd-winpe-recovery-kd-retry-20261009-runtime. The one identical retry passed
  WinPE live H.264 Surface (~15FPS, no backlog) and native Shift+F10 input.
  The prior recovery failure was an incomplete KD exchange during CAM writes,
  NOT evidence of a new Windows kernel failure; no config flags changed.
- Use adb input keycombination59 140 for Shift+F10,57 59 for Alt+Shift.
  keyevent59 140 sends separate presses, NOT a chord. English layout actually
  verified; DiskPart mounted exact NTFS volume0 W:, serialA8E6-FF6E,63GiB,
  source FAT32 volume1 S:, labelWINSETUP. No partition topology changes.
- preserve-install-evidence.cmd runtime PASS:36 UAVF files,12 Panther files,
  13 runtime files copied to S:\UAVFRECOVERY\before-clean-specialize-20261009.
  Original SYSTEM backup and Panther setupact/setuperr compared byte-for-byte;
  preserved.txt records success. Official SWM/ISO/EFI bytes not altered.
- clean-specialize-apply.cmd guards exact old root label+serial, source tag,
  both SWMs and preservation marker. TRIM disabled/read back before format.
  Disposable Windows root ONLY quick-formatted successfully; previous installed
  test OS removed, logs/backups remain in the source recovery folder. DISM
  apply is the next/current operation: do not type into its foreground CMD.
  This is one clean BCD-before-first-specialize test, not more Setup-state hacks.
  Guard exactPID15926 continues at52GiB; ~57.1GiB available before apply.
- After apply exits, inspect W:\uavf-apply-exit.txt. Then stage CURRENT base,
  firstboot/display and prepare-installed-boot scripts, including System1 BCD
  role BEFORE first installed boot. Do not use the old staged bootprep version.
  Need verify actual clock before stock windeploy; year2587 cause unresolved.
- 11:42 actual foreground DISM20%, CID2144 still live. H.264 counter7630
  frames, receive/decode/render callback14.98FPS, pending0, ~7ms receiver to
  callback. clean-apply-20-percent.png and fresh encoded-display-report.txt
  saved in this run's evidence.52GiB guard remains live;57.1GiB free. No
  user input/staging in occupied DISM CMD until the script exits.
- 11:48 verified wait: same CID2144/owner15926, foreground DISM36%, no
  failure dialog, reserve guard still active (~57.1GiB). Do not restart.
  installed-firstboot.cmd now records native date/time, Setup/SysprepStatus
  and loaded BCD Description BEFORE stock windeploy (observation only; no
  clock or completion-flag edits). NOT staged during DISM; stage CURRENT
  version after apply exit together with other helpers. ASCII/input-plan
  validated; runtime evidence for the new firstboot logging still pending.
- 12:03 same live CID2144, actual foreground DISM88% (clean-apply-88-percent.png).
  Reserve guard remains exactPID15926,59,858,540KiB available. Observer
  heartbeat alive. Apply not terminal; no restart or guest typing permitted yet.
  Planned staging normalized bytes: base3721, bootprep4044, firstboot1725,
  display567. Updated bootprep includes BCD System1; updated firstboot includes
  pre-windeploy clock/state logging. Never substitute old staged versions.

### Earlier state (superseded by CID2144 above)

- User reconfirms final objective: Windows must reach Ubuntu's practical
  workspace level after first boot; Setup/desktop alone is not completion.
  Follow the product parity ladder under Objective and invariants below.
- Latest read-only device check: Running VMs: []; diagnostic owner process
  absent. Earlier LIVE NOW entries below are historical, NOT current state.
- CID2142 ended after terminating the failed Setup child: native shutdown,
  not evidence of kernel hang. Guest year2587 was confirmed and corrected to
  2026; a successful clock-corrected Setup retry has NOT been demonstrated.
- Cold recovery changed only the isolated ESP BCD default to preserved WinPE.
  Original28KiB BCD retained;27 payload bytes changed, full hive inventory
  checked, device readback PASS, frozen EFI unchanged. No root reformat.
  Tools: isolated-device-bcd.py and prepare-winpe-bcd-recovery.py.
- Recovery run evidence: kd-winpe-install-recovery-20261009-runtime.
  Reports do not establish recovery WinPE PASS; no live owner remains now.
  Next inspect that run's terminal evidence before another purposeful launch.
  Preserve C:\UAVF/Panther evidence before any disposable root reinstall;
  apply proven BCD System1 fix before a fresh first specialize.

## Historical checkpoint — 2026-10-09 10:59

- LIVE NOW: CID2142, ownerPID29599, exec49184 observer3600s, evidence
  kd-installed-specialize-retry-20261009-runtime; English Shift+F10 CMD active.
  Installed Windows/C: boots, app-owned H.264/input work. OOBE NOT passed.
- BCD System1 fix remains proven after cold boot and integrated into isolated
  boot provisioning; source/EFI/Ubuntu untouched. After exact failed-state
  rollback windeploy now gets past state6 rejection and launches stock
  C:\Windows\System32\oobe\setup.exe, whose dialog says cannot start setup.
  Do not repeat cold boots or alter more Setup flags without its specific error.
- Native stock RespecializeCmdLine is Sysprep\sysprep.exe /respecialize /quiet,
  proved in original SYSTEM backup/current registry. One live invocation after
  tasklist confirmed no sysprep process: Panther proves PnP respecialize succeeds,
  no errors, but it does NOT execute SpBcd/full specialize; GeneralizationState4
  remains. No full Setup/OOBE success claim. Current SetupType2/SetupPhase4.
- PowerShell Get-Content did not return; use native findstr/cscript instead.
  C:\UAVF\tail-log.js reads last25 lines, UTF8 Russian text is mojibake but
  English diagnostics intact. Old error dialog overlays center of screenshot.
- wevtutil Application query initially RPC unavailable because EventLog stopped;
  RpcSs separately proves RUNNING. sc start EventLog accepted START_PENDING;
  latest command queries EventLog and writes recent errors to
  C:\UAVF\application-errors-after-eventlog.txt. NEXT inspect that result,
  then stock oobe/setup diagnostics; no guessed specialize switch (argument
  table contains RESPECIALIZE, not SPECIALIZE). Preserve logs before any action.
- 11:02 EventLog verified RUNNING. Application error query succeeds;356byte
  report contains only old SecurityCenter event16, not a Setup fault. Thus no
  crash diagnosis from Application log. Current next step is stock OOBE/setup
  failure-specific diagnostics (not another CPU/platform sweep or blind reboot).
  If failed installation cannot be retried normally, prefer one clean isolated
  root reinstall with BCD role fixed BEFORE first specialize rather than a long
  chain of manual status edits. Preserve C:\UAVF/Panther evidence before removing
  any disposable Windows root; source ESP, Ubuntu and reserve remain invariant.
- Guard exactPID29599 retained; tablet59,898,476KiB (~57.1GiB), host C54.1/
  D59.5/E82.9GiB. No root/source formatting/reapply. Windows clock year2587 noted.

- CID2140 / PID15266: installed Windows boot + live H.264 + Shift+F10 input PASS.
  Current signed specialize stopped in SpBcd, NOT a platform/CPU hang:
  Panther reports "BCD: File is not system store", 0xC0000098 / 0x3ee.
- Exact isolated ESP BCD Description lacked System. /sysstore S: accepted but
  default bcdedit /enum still failed. Backup C:\UAVF\BCD-before-system-flag;
  one live A/B added Description\System DWORD1, unloaded hive, then default
  /enum succeeded exit0. bcd-system-flag-pass.png preserves readback. This is
  BCD access PASS only; specialize/OOBE still pending. No signed userland patch.
- Next: inspect actual Setup state; retry signed specialize normally after BCD
  fix, preserve capture on any required reboot. Integrate proven BCD fix into
  boot provisioning if specialize advances. Do not reinstall/reapply image.
- Tablet available59,981,120KiB (~57.2GiB), isolated52GiB guard retained.
  Host observer7890 remains bounded; no second patching observer over live VM.
- Normal shutdown after preserving Panther/Setup evidence: authoritative VMs[]
  confirmed. Observer7890 stopped ONLY after guest exited, runner finally ran;
  forced observer exit1 is host lifecycle, not a new Windows failure.
- Controlled BCD retry: CID2141 ownerPID23282, observer exec11596, evidence
  kd-installed-bcd-system-retry-20261009-runtime. Same guarded kernel fixes,
  existing installed disk; hook rearmed ONLY to preserve capture while retrying
  original signed windeploy. SetupType/ChildCompletion/OOBE flags unchanged.
  Detached52GiB guard uavf-windows-bcd-retry-reserve.log checks exactPID23282.
  Prior CID2140 entries below are historical, not the current owner.
- 10:24 retry result: firstboot hook ran and app-owned H.264 reconnected with
  real Windows frames. Signed Setup now shows "could not complete installation";
  setuperr.log still contains only the earlier12:03 SpBcd failure. Do NOT claim
  specialize PASS or change ChildCompletion to fake completion. Next inspect
  fresh setupact tail / status for retained failure vs new failure; original
  Panther logs and Setup registry export are preserved in C:\UAVF.
  Shift+F10 admin CMD works, English layout set by verified Alt+Shift after
  Cyrillic readback. Current foreground PowerShell -NoProfile Get-Content
  setupact.log -Tail25 has not returned yet; wait/inspect instead of duplicating.
  Source worker started by original hook; blind early commands were Cyrillic
  and did not execute. Tablet free59,896,972KiB (~57.1GiB).
- 10:35 exact next cause: UnattendGC setupact says windeploy refuses failed
  GeneralizationState6, 0x8030000C, before new specialize. Source SYSTEM backup
  loaded/read/unloaded proves original GeneralizationState4 and no setup.exe
  ChildCompletion value. Setup exported before scoped retry rollback; restored
  ONLY6->4 and removed introduced ChildCompletion setup.exe1, never set3/success.
  BCD default enumeration after cold boot proves installed {current} C:\Windows
  and system store usable. Hook rearmed to retain interactive capture; requested
  normal shutdown for a deliberate full specialize retry. No image reapply.
  Evidence retained-failed-generalization-state6.png,
  baseline-generalization-state4.png, retry-state4-and-installed-current-bcd.png.
  Guest UnattendGC log reports year2587 (clock issue noted, not diagnosed).
- 10:37 CID2141 normal shutdown -> VMs[]; observer11596 stopped after guest
  terminal, finally executed. New full specialize retry CID2142 ownerPID29599,
  exec49184, kd-installed-specialize-retry-20261009-runtime. Guard exactPID29599
  uavf-windows-specialize-retry-reserve.log. Same platform/guarded RAM fixes and
  installed disk, no new flags. BCD system-role fix integrated into isolated
  prepare-installed-boot.cmd with KeyName/readback/unload/backup rollback guards;
  system-store access proven across reboot, full specialize still pending.

- App-owned CID2139 / owner PID26809 remains RUNNING; live H.264 Setup/CMD
  display and keyboard/mouse are proven. Ubuntu and production APK untouched.
- Two previous Compact image applies failed at 1%; the observed fatal D1 names
  viostor.sys (read0x848, IRQL11, fault PC ending4254). Shipped driver disassembly
  locates this in DISCARD/UNMAP list processing; exact corruption origin unproven.
- Single scoped A/B: NTFS DisableDeleteNotify changed0->1, queried/read back.
  Same image/driver/platform now completes100% apply, reports success and captured
  untouched DISM exit0. APPLY_IMAGE PASS, NOT installed boot/OOBE PASS.
  Source: kd-winpe-ntfs-trim-off-20261009-runtime. Observer session17090 ended
  normally at its1800s bound; authoritative AVF list still proves CID2139 alive.
- Separate W: NTFS root, S: original source ESP. No source/Ubuntu formatting.
  Tablet free57.8GiB; detached guard stops Windows at52GiB.
- Next: actual DISM success -> signed storage/vsock driver provisioning ->
  inspect offline Setup hive -> same guest capture/input runtime in interactive
  OOBE/session -> preserve custom EFI shim while provisioning installed BCD ->
  first installed boot with already-proven guarded kernel RAM workarounds.
- Installed OOBE/desktop, reconnect after install and network NOT proven.
  Do not restart the live apply or restore whole-image deltas over this disk.
- Base provisioning PASS: exact kernel fc/binary equality; DISM reports both
  signed viosock and viostor installed; helpers copied and binary compared;
  SYSTEM backup retained, Select Current1 read, actual DisableDeleteNotification1
  persisted in ControlSet001, hive unloaded; BASE_EXIT0 and marker read back.
  Screenshot base-success-setup-trim-readback.png. Full goal still incomplete.
- Boot provisioning PASS: prepare-installed-boot.cmd exit0 and marker read back.
  Windows BCD default is partition=W:, winload.efi, NOT ramdisk/WinPE. Original
  EFI BOOTAA64/WINORIG compared byte-identical after BCDBoot; source WIM/SWM
  untouched. Old WinPE BCD and EFI files backed up inside isolated W:\UAVF.
  Serial debug1/115200, bootdebug/debug match proven WinPE contract. Own Setup
  hook CmdLine read back, original saved; hook restores signed windeploy first.
  Actual OOBE hook/session/capture behavior remains unverified until next boot.
  boot-prepared-exit0.png and W:\UAVF\bcd-installed-readback.txt preserve proof.
- wpeutil shutdown reached known HalReturnToFirmware address0xfffff80156c85590,
  but halted at stale observer breakpoint1 after earlier observer expiration.
  Raw serial valid state-change repeats PC/address and breakpoint80000003;
  not a Windows/storage hang. Own bounded runner now omits --break-on-reboot
  in HoldRuntime; CAM/timer/PMCC workarounds unchanged. Parser PASS. CID2139 then
  stopped through isolated package only; VMs[] proved before cold launch.
- FIRST INSTALLED BOOT RUNNING: CID2140 ownerPID15266, host exec session7890,
  evidence kd-installed-firstboot-20261009-runtime. Kernel connected, installed
  driver loading seen (NOT WinPE ramdisk). FIRST INSTALLED LIVE FRAME PASS:
  firstboot cmd wrapper visible with Windows11 chrome, live app-owned vsock and
  existing H.264/MediaCodec Surface. installed-firstboot-live-frame.png; original
  Setup CmdLine restore reports success. This is not yet OOBE/desktop proof.
  Windows windeploy remains active; no reboot or input into its busy CMD.
  New detached reserve guard for exactPID15266: uavf-windows-firstboot-reserve.log.
  Launch uses existing run-service-test.ps1 -ResumeWindowsInstallTarget
  -Candidate live-name-fix -EmulateUserPmcc -QuietDriverNotifications -HoldRuntime
  -ObserverSeconds3600 -KeepRunning. No new platform/CPU/display flags.
- Prepared: installed-firstboot.cmd,
  installed-display.cmd. Base requires first-command DISM exit0 capture, exact
  WinPE/installed kernel equality and approved volume label before any write.
  WinPE WIM inventory lacks fc/findstr; base uses built-in find and installed fc.
  Firstboot hook may be provisioned ONLY after actual offline CmdLine is verified
  exactly oobe\windeploy.exe; it restores original before executing signed OOBE.
  Its interactive session/desktop behavior remains a runtime hypothesis.
  Exact offline Setup CmdLine is REG_SZ oobe\windeploy.exe; SetupType1. This
  satisfies the hook's frozen original-command guard. Hook/worker staging next.
  send-winpe-file.ps1 actual chunked staging PASS (3721bytes exact count); first
  multi-kilobyte input lost tail, no script created/executed. Corrected to320char
  payload chunks and <=700char paced lines; no new transport or reboot.
- Observer session17090 ended at its1800s total bound (~09:25). Expiration is NOT
  guest hang or grounds to restart; VM KeepRunning/space guard
  remain active independently. Do not run a second patching observer over live
  already-modified kernel merely to extend the observation bound.
- DISM completed, its first idle-prompt command captured exit0 in
  W:\uavf-apply-exit.txt. Screenshot apply-success-exit0.png preserves both
  success text and actual0 readback. send-winpe-file stages
  small ASCII files through proven cscript/FSO, not4GiB image transfers; its
  actual base script execution has passed. No boot/disk restore yet.
- Exact WinPE fsutil.exe binary strings and actual live registry query prove
  Control\FileSystem\DisableDeleteNotification REG_DWORD1. NOT the fsutil label
  NtfsDisableDeleteNotify. Offline provisioning corrected BEFORE execution and
  requires matching actual live readback. Source WIM lacks fc/findstr; installed
  index3 includes fc.exe. Use exact packaged viosocklib.dll/viosockwspsvc.exe.

## Objective and invariants

### Final product objective — user extension 2026-10-09

First installed desktop is an intermediate milestone, NOT completion. After
Windows boots, bring its workspace to the practical user-facing level of the
existing Ubuntu workspace, without regressing Ubuntu or changing its data:

- Reproducible installation and automatic boot from persistent Windows disk.
- Stable common H.264/vsock/MediaCodec display, reconnect/background/rotation;
  pursue the Ubuntu1920x1200/60FPS level only after desktop stability, and report
  actual measured performance rather than claiming parity in advance.
- Keyboard/modifiers, captured mouse/buttons/cursor, touch and release shortcut.
- Network, low-latency audio output, clipboard, shared-folder integration and
  disk growth where locally feasible; validate each in installed Windows.
- Common workspace UI/settings, real boot/reboot/shutdown state, diagnostics,
  recovery and data persistence across VM/application restarts and APK updates.
- Multiple independent persistent VM workspaces: Windows and Ubuntu must
  coexist without deleting one another. Separate per-workspace storage/runtime
  state and isolation; verify switching and simultaneous runtime where device
  resources permit, without damaging Ubuntu/P7. Do not narrow this to one
  replaceable diagnostic disk as the final product.
- Integrate proven Windows-specific capture/input/runtime bootstrap into the
  normal APK flow; retain shared transport/decoder instead of a parallel stack.
- OpenGL/Vulkan/GPU acceleration is a separate capability gate: Windows hardware
  driver availability is not implied by Linux VirGL. Record concrete blockers
  and experimental limitations; do not sacrifice a working desktop to chase
  unsupported parity, or claim unproven hardware acceleration.

Order: installed boot -> OOBE/desktop -> stable display/input -> network/audio
-> desktop integrations -> main APK/product lifecycle -> regression/hardening.
Existing safety/stop conditions remain: isolated Windows disk,50GiB reserves,
no root/unlock/flash/SELinux changes, no Ubuntu mutation, no commit/push/release.

CURRENT WORK: APP-OWNED LIVE SETUP + INPUT PASS. PMU diagnosis finished;
continue safe isolated installation, not more launch parity experiments.
2026-10-09 revalidation: Running VMs[] (terminal, no live observer session).
Retained Android stream total33075 received/decoded/render callbacks before
ECONNRESET. Live ~15FPS measured, keyboard Shift+F10/text/Enter and touch opening
Setup Browse dialog visually proved. No raw fallback or signed smss patch.
VM ownership UID10042/CID2133 persisted after KD observer disconnected.
Current next boundary: installation disk geometry/source mount. DiskPart proved
Disk0 online8GiB GPT with no free space. Host verified sole partition is EFI
System Partition spanning all media. Hidden ESP has no auto drive letter;
there is no safe Windows root target yet. Preserve source, add separate target
region to this isolated Windows disk, never format partition1 or Ubuntu.
Full Windows Setup/install/desktop objective unchanged and incomplete.

2026-10-09 storage milestone: removed two additional SHA-verified redundant
Android Windows staging copies (17GiB), retaining exact full PC backups.
Tablet free space now 59,798,152KiB (~57.0GiB); reserve50GiB restored, only7GiB
installation headroom remains. Do not apply Windows until its physical footprint
fits above the reserve. Original baseline/Ubuntu/private VM untouched.
Manifest: redundant-device-windows-staging-cleanup-20261009.json.
prepare-install-geometry.py audited exact A1DE candidate and primary GPT CRC.
Source backup GPT is entirely zero/missing. Generated small plan creates valid
backup GPT and adds64GiB unallocated target, preserving source partition bytes
and LBA2048..17825758. Plan NOT applied, installation NOT started.
Next: guarded sparse growth/staging and persistent identity validation, source
mount + /InstallFrom, physical-space preflight; no additional boot diagnosis.

Further2026-10-09: old03E3 Android media removed only after full virtual
reconstruction through all six reverse deltas matched exact03E3 SHA; no9GiB PC
clone needed. Recovery/cleanup manifests retained. Expected free after restoring
private media~65.5GiB, not all available for Windows writes (reserve50GiB).
Diagnostic APK FB8D34332254F6E178B77EC9E7F88C119BE146D01B40221133DF5AAFB2782757
contains explicit prepare_install_target mode: exact9GiB source SHA before sparse
growth, journal/readback/rollback, physical reserve check; normal launch unchanged.
Four offline GPT guard tests PASS; runtime geometry PASS still pending.
PC adb36.0.2 exec-in hit host std::bad_alloc/0xc0000409 before VM creation.
Cold transfer now uses256MiB sync chunks plus same-UID assembly, verified prefix
resume and final full SHA. Partial/empty media is not bootable, never claim PASS.
Frozen installed-image extract proves ntoskrnl.exe SHA C6677390...D3005A734 and
winload.efi SHA F7747F4A...F491D31 equal known WinPE files despite image metadata
build26200.6584. No unknown installed-kernel offsets assumed.

NEW MILESTONE: kd-winpe-app-pmcc-display-quiet-20261008-runtime proves actual
Windows Setup in diagnostic APK Surface. Owner UID10042/package owns VM and
connectVsock FD. UVF1 AVC -> existing c2.mtk.avc.decoder -> Surface: 30 received,
30 decoded/render callbacks, ~8.5ms receive-to-render. PixelCopy readback PASS;
android-surface-first-confirmed.png visually verified genuine Setup driver page.
This is a 30-frame REPLAY, NOT yet persistent live capture or usable input proof.
Root cause/workaround: native EL0 PMCC trap adapter gets smss and wininit through
without signed userland changes; suppress DbgLoadImageSymbols diagnostic stops
to avoid established KD symbol-notification RESEND stalls. Same launch args.
Later live milestone supersedes this initial replay result: see
WINDOWS_APP_OWNED_LIVE_SETUP_2026-10-09.md. No more PMU sweeps.

Latest targeted experiment: PMUSERENR.CR native A/B genuinely reads back4,
but early permission write does not prevent smss SIGILL. Next single bounded
test is exact-kernel EL0 PMCC trap adapter with CNTVCT fallback, NOT a smss patch
or PMU support claim. Native smss recovery is now PROVEN; next fault is wininit
reading the same PMCC into X23. Expand only verified exception-frame register
coverage, one corrected run pending. Offline bounds/relocations PASS.
See WINDOWS_PMU_COUNTER_ADAPTER_2026-10-08.md. No CPU/argument sweeps.

Historical runtime milestone search:
One late observation point per identical cold boot, shell control vs APK
:vmowner, no argument/CPU/PMU/UART/balloon sweep. First point IoInitSystem return
RVA b27fa8, already reached in retained shell WinPE PASS. New observer mode
forbids extra post-IO/PMU/reboot/termination probes; proven CAM/timer fixes stay.
Private APK candidate restored from SHA-verified pristine shell scratch before
each control. Completed pair results and current boundary are recorded below.

Latest exact driver-entry pair (2026-10-08): shell CID2118 reached b2a1b0,
snapshot/restoration PASS, then REAL Setup screenshot and 30 UVF1 AVC packets
captured/encoded/locally decoded PASS (not a FPS benchmark, not tablet stream).
CNTV_CTL=1, CNTFRQ=13000000, DAIF=40, ICC_CTLR=88400, ICC_PMR=f0,
ICC_IGRPEN1=1, MIDR=410fd851. Physical KD GIC reads returned c0000001;
GICD/DS remain UNKNOWN. Matching app cold boot stopped returning KD packets
inside winload host-file requests, BEFORE kernel connection or configured
checkpoint. Host TX equals native ingress log; host RX equals serial file,
full SHA equality both directions. No dropped bytes established, no proof of
guest hang or GIC state difference. shell-app-runtime-diff.json explicitly
marks comparable=false. Next bounded pair moves checkpoint earlier to
interrupt-init b1bf14; no CPU/PMU/config sweeps, no smss patch.

Interrupt-init pair completed: shell snapshot PASS (CNTV_CTL2/DAIFc0,
ICC_CTLR88400/PMRf0/IGRPEN1=1/MIDR410fd851), then native smss SIGILL:
not a WinPE PASS control. App KD again stopped in winload file requests before
kernel/checkpoint; last target packet RESEND. Independent stopped app disk has
no UAVF-DISPLAY directory. Both runtime diffs comparable=false. Existing
firmware state matches through StartImage; EBS_DS=PASS/CTL40 appears only in
shell. This is the observable bracket, NOT proof of a GIC mismatch/hang.
No fix justified; no more repeated late probes. Next needed observation is
winload last file request -> EBS callback, independent of later KD milestones.
See WINDOWS_RUNTIME_MILESTONE_AB_2026-10-08.md. VMs[], Ubuntu untouched.

Pre-EBS natural-notification read pair completed: BOTH shell and APK reach
winload notification; GIC virtual reads unsupported c0000001 in both, actual
state unknown. BOTH then lose KD before EBS, not an app-only boundary.
Observer idle-timer defect fixed/tested offline: FILE_IO/PRINT activity now
extends inactivity window, global deadline retained. Corrected APK still
silent60s after FILE_IO/RESEND; WinPE not reached. Fix is diagnostic-only,
not Windows PASS. Official winload symbols/code locate remote-file transaction
at214150/send2142cc/receive21431c and kernel transfer1080. No flags, smss,
production APK or Ubuntu mutations. No live VM remains.

ACK reliability observer correction (offline PASS) allowed new APK run to
reach driver-entry and capture native state. Matches retained shell WinPE
CNTV_CTL1/freq13MHz/DAIF40/ICC_CTLR88400/PMRf0/group1enabled. Only MIDR differs
410fd851 vs410fd821; no causality or CPU-model fix inferred. Post-snapshot
failure specifically KD SetContext3133/985-byte wire packet; rollback not
verified, so boot after checkpoint is censored by observer, not Windows FAIL.
Next cold APK test uses same single driver-entry point WITHOUT MRS/context
write; proven CAM/periodic fixes unchanged. No extra platform/CPU flags.

Sparse no-context-write APK run completed: kernel + driver-entry PASS; one
breakpoint restored, no MRS/SetContext snapshot. Real smss c000021a/c000001d,
user PC7ff61c6c253c (consistent old1253c site; no fresh instruction read).
WinPE/producer not reached. This is no longer an inferred post-EBS hang: actual
APK post-EBS/driver execution proved. Shared sampled timer/GIC CPU state matches
retained shell WinPE. MIDR821 vs851 was ALREADY seen in closed prior PMU A/B,
not a new causality proof. Do NOT repeat closed CPU/PMU/preentry-smss experiments
or patch signed userland. Any PMU-scope reopening requires explicit user direction.
All VMs[], no production change; objective still incomplete.

While waiting for explicit CPU/PMU-scope direction, prepared/compiled live-only
Windows source fixes: removed900-frame encoder and2-client/45sec server limits,
unique live reports, partial pipe writes, resolution-change reconnect signal,
own encoder-child cleanup. Shared UVF1/UIN1/Android decoder unchanged.
See WINDOWS_LIVE_DISPLAY_PREPARED_2026-10-08.md and prepared-manifest.json.
PREPARED ONLY: no device staging/new VM/WinPE/live Surface PASS. Working image
and APK unchanged; source integration must wait for reliable app-owned WinPE.

Runtime binary search update: single io-complete shell reached b27fa8,
CPSR60000044 (IRQ unmasked), CAM/periodic thunk bytes intact, but later original
user fault C000001D/PMCCNTR instruction d53b9d08. It is milestone PASS, NOT fresh
shell WinPE PASS. Same single-point APK run reached boot-driver loading but no
confirmed IO-return checkpoint before KD timeout. Missing KD is NOT a proven
guest hang or a proven first divergent function.
Next identical cold pair selects earlier driver-entry b2a1b0. Runtime snapshot
uses read-only MRS within the SAME logical checkpoint, one active trap at a
time, guarded code/full context rollback, plus width4 GIC MMIO reads. Records
CNTV_CTL/CVAL/CNTVCT/CNTFRQ, MIDR, DAIF, ICC_CTLR/PMR/IGRPEN1; no feature/register
writes, no smss patch. Assembler/objdump-verified boot-runtime-state.S retained.

Prior blocked audit after three resumed goal turns at the same
app-owned kernel/KD boundary. Authoritative recheck: CID2115 observer terminal,
expanded argv diff equal, final Continue acknowledged, viosock loads0, original
fault events0, Surface image absent, Running VMs[]. No active test to wait for.
Literal service implementation and evidence are preserved, not a WinPE PASS.
No new evidence-backed semantic fix is available within the user's prohibition
on CPU/PMU/UART/balloon sweeps. This does not establish Windows impossibility or
prove a vendor root cause. Resume requires new observable runtime evidence or
explicit direction for a distinct bounded investigation; no blind reboot.

Previous goal status: BLOCKED after three consecutive goal turns on the same ownership/
display boundary. Revalidated retained client errors and current VM[]; no live
test to wait for. Shell-owned AVF FD delegation has measured Binder EPERM;
supported live ownership reassignment was not found. APK-owned boot remains
unresolved, and the previous bounded CPU/PMU/timing diagnostic branch was
explicitly closed by the user. This does NOT prove Windows on this device is
impossible. Resume requires user direction to reopen a tightly scoped original
fault-context investigation (no random flags/signed-userland patch), or new
supported platform evidence. No success or installed Windows milestone claimed.

RESUMED by user: literal known-good shell raw createVm/start port to isolated
APK foreground service process :vmowner, same UID as viewport. Construction
source normalized comparison PASS through expanded raw inputDevices=[];
old Manager.create/run/debug/output-capture overrides are deliberately absent.
New diagnostic APK A9E0B5C7891CD218F6A9B4D5E8D04EFAADFA5698DAF0625209D1AB60A8C7771B
built/installed. Effective raw JSON captured before start, existing KD fixes
unchanged. Not runtime PASS yet: private disk preparation interrupted by USB;
no new VM created. Only disposable uninstalled private Windows copy may be
overwritten. Production APK/Ubuntu remain untouched during isolated proof.

Literal service result CID2114: raw createVm/start succeeded after log FD moved
from external storage to APK-private storage. Exact candidate1E9D...8E4BE and
wrapper verified. Machine-readable expanded argv diff saved under
kd-winpe-literal-service-private-fds-20261008-runtime/shell-app-effective-argv-diff.json:
equal after ignoring ONLY PID/PPID/CID (even FD indices match); same CAM/subpage/
periodic timer workaround order. No SIGILL observed, but KD desynchronized at
early kernel FLTMGR load; WinPE NOT reached. Observer terminal and VM[] checked.
Do not treat the old copied encoded-display-report as fresh evidence.

Current implementation replaces remaining Java UART pacing/file polling with
the SAME native ingress and console-tailer binaries packaged as executable
APK native libraries; TCP adapter only Base64-frames ingress/output. This is
literal I/O parity restoration, no alternate pacing/platform flags. APK
042E72A334AC469056679FE64944EBBBEE42BA56F33A736D20C9747CD9A24720 installed.
Ingress SHA0BF003F247F18ABBA44437B13A98E89A95BCB2714C3E8D85C6D5A6491CE78FBD;
tailer SHAC9A66DFD8DB9B3AA9EB167C1A58110F73D1112634967354AB212FB2D08D8E0C0.
Native helper control CID2115 completed after exact private restore:
kd-winpe-literal-service-native-uart-restored-20261008-runtime.
Same expanded crosvm argv (only PID/PPID/CID differ), machine-readable diff
saved beside events. Native decoded ingress matches host tx ALL22458bytes;
native serial file matches host rx ALL72691bytes. No byte loss/corruption and
no SIGILL observed. Windows kernel connected; CAM/periodic fixes verified;
after probe installation, KD stopped returning complete responses. Own bounded
break-in received repeated ntoskrnl load state, not original fault context.
WinPE/userspace/display/input NOT proven. Observer terminal, service stopped,
VM[] confirmed. No further UART/CPU/PMU/platform A/B without concrete difference.
Retained packet audit: final Continue0x3136/ID80800001 at TX22369 received guest
ACK80800001 at RX72675. Command delivery is proved; later WinPE/exception state
is absent. No justification for another UART transport tweak or blind launch.
Previous kernel run mutated private disk; identity guard correctly refused reuse.

Continuous real Windows Setup on tablet via shared UVF1/H.264/AVF/MediaCodec/
Surface, keyboard/mouse/touch, isolated installation, reboot, installed boot to
OOBE/desktop, stable display/input, network if available. Not screenshot-only.
No commit/push/publication, root/unlock/flash/SELinux changes. Never change
production Ubuntu/data/storage/P7/VirGL or original Windows baseline.
Encoded port4055; raw4052. Reuse EncodedDisplayProbe, not another decoder.

## Milestone ladder

### Current override — 2026-10-09 (older entries below are historical)

App-owned boot/display/input now PASS in the isolated diagnostic APK, CID2136.
Evidence: `kd-winpe-app-install-geometry-bounded-write-20261009-runtime`.
Kernel RAM fixes preserved byte-for-byte; bounded 32-byte KD writes and exact
read-only retransmission handling fixed observer delivery failures, not CPU flags.
Existing OpenH264/UVF1 -> app-owned vsock4055 -> EncodedDisplayProbe/MediaCodec
Surface shows real Setup continuously (~15FPS). Viewer reconnect on the same VM
is proved. Keyboard, pointer, WinPE CMD, DiskPart and WMI scripting work.

Sparse Windows disk: 77,846,282,240 logical bytes, validated primary/backup GPT;
original source ESP retained, separate64GiB unallocated install target visible.
S:\\sources\\install.swm and install2.swm lengths match verified inputs.
Fresh Setup launched with explicit InstallFrom, InstallDrivers and CompactOS.
Current screen: language selection; no root partition/apply started yet.

Follow-up same CID: language/keyboard/key-skip/Pro selection passed through
real displayed Setup. Explicit source fixed the previous driver/source page.
Setup then correctly blocks this diagnostic platform: 1vCPU, no TPM2/SecureBoot.
Read-only exact Setup DLL strings contain TPM/SecureBoot/RAM LabConfig bypasses,
not CPU bypass. Do not change CPU flags or patch signed Setup to hide this.
Use standard DISM split-image Compact apply to test installed Windows, not claim
that this is a supported Windows11 platform or completed GUI installer flow.

New empty partitions created on isolated disk0 ONLY: MSR16MiB offset8912896KiB,
NTFS `UAVFWindows` root65000MiB offset8929280KiB, letterW. Source ESP retained.
Evidence `target-created.png`; no existing partition deleted/formatted.
Compact /Verify /CheckIntegrity apply image3 command issued, completion pending.
Detached on-tablet `watch-install-reserve.sh` monitors ownerPID13142 each second;
stops ONLY com.example.uavfwinpetest at52GiB to protect50GiB reserve. Log:
`/data/local/tmp/uavf-windows-install-reserve-2136.log`. Guard check/syntax PASS;
actual low-space stop not triggered/tested. Root now mutable: old prepared-media
resume guard MUST NOT be used (it requires an unpartitioned target).

Latest boundary: Compact apply reached1%, then guest requested a REAL reset at
08:29:02; crosvm exited reset/status32, CID2136 unregistered. Not a decoder-only
freeze, storage guard, host OOM proof or installation completion. /data still
65.2GiB free. Private wire/ingress logs collected read-only in
`compact-apply-reset-2136-20261009`; serial SHA
EC237360FFAB3D201C0A4D67069BC4A7B964E1FCBC5AF0D83B94E259CC2A0936.
Host reset log retained. Final KD output repeats 'Refreshing KD connection';
the observer had ended before apply, so exact reset/bugcheck cause is NOT proved.
Do not claim apply PASS or reboot into installed Windows yet. Keep the partial
root/source; no whole-image restore. Next safe test requires a mutable-layout
identity guard, persistent apply log on W:, and live KD throughout apply so the
first exception/reset boundary is observable. No CPU/PMU/firmware sweep.

Follow-up `kd-winpe-mutable-install-observed-20261009-runtime`: separate mutable
target guard implemented and actually PASS before VM start. Verifies exact source
partition, primary/backup GPT CRCs, approved MSR/root offsets, NTFS signature,
full frozen EFI shim + boot.wim SHA and tablet reserve. Does NOT disable identity
or claim installed boot. APK F52C44746C7EF34D724552476141182286B10C773444B59612495F2F4A8799C9.
Current observer session14812 remains live,1800s overall bound; idle no longer
closes the ACK/FILE_IO channel after boot. Same kernel fixes; reset probe only.
OwnerPID22290, on-device reserve guard active. VM boot restarted without source
restore, preserving partial root. Cold source boot verified, no new apply yet.

Next immediate display issue: existing bootstrap uses CREATE_NEW-style
`CreateDirectoryW(volume+"UAVF-DISPLAY")`; if prior evidence folder exists it jumps
to keep_setup WITHOUT producer. The source disk is now intentionally persistent,
so this own bootstrap is not restart-safe. No RFB/raw or new decoder needed.
Archive/preserve that diagnostic directory safely during a cold boot, or make
own bootstrap choose a unique evidence directory; do not erase source or root.
After restoring live frames, rerun Compact apply with persistent W: log and KD
still attached; installed kernel boot and OOBE remain incomplete.

RESOLVED display restart: own cold LFN-only evidence archive implemented,
actual Java fixture tests rename/collision/corrupt-preimage PASS. Runtime
`kd-winpe-install-archive-reconnect-20261009-runtime`, CID2138, archives old folder
as UVFLOG000001 (files/FAT chains retained). EFI/WIM unchanged. Live real Setup
~15FPS, pending0; sourceS:/rootW: re-mounted with NO format. APK
3C7920122917C737E92E92DF5D1CEF92271942FBE9477AA2DC9C0AA4A644D1AD.

NEW REAL BLOCKER localized: second Compact apply also reaches1%, but live KD
catches fatal0xD1 (0x848, IRQL0xB, read0, PCfffff80e86c74254), viostor.sys.
Exact shipped driverSHA917BEBB136A2941EC50A5BD47764509B0B60A23C0696B922380E4043FD330790,
version100.102.104.29300. Static instruction at RVA4254: F9400128 `ldr x8,[x9]`;
preceding code reads list backlink and inserts an SRB into the processing list.
Function contains VIRTIO_BLK_F_DISCARD bit13 gate, UNMAP CDB descriptor parsing,
and VIRTIO_BLK_T_DISCARD value11: this is discard/UNMAP path, NOT ordinary
read/write, IRQ entry, CPU/PMU fault or decoder loss. Corruption's exact origin
(descriptor bounds vs queue/list state) not yet proved. Do not patch signed driver.
Next minimal A/B: disable NTFS delete notification/TRIM in isolated WinPE only
with supported fsutil behavior, query/readback, then same verified Compact apply.
Keep live KD and persistent W: log. If that passes, persist scoped installed-OS
policy and investigate exact viostor DISCARD bounds separately. Source/Ubuntu
unchanged; free /data65.15GiB and reserve guard never triggered.
Evidence: viostor-d1-unmap-2138-20261009 and retained events above.

TRIM A/B running: `kd-winpe-ntfs-trim-off-20261009-runtime` CID2139,
ownerPID26809, observer session17090 (live1800s bound). Same source/driver/VM,
no format/restore or signed patch. `fsutil behavior query DisableDeleteNotify`
visually proves NTFS0 -> set1 -> query1; ReFS remains0. Setting reports immediate
effect, no reboot needed. Screenshot ntfs-trim-readback.png retained. Same
Compact/Verify/CheckIntegrity image3 apply now passes old1% boundary, reached11%
with live frames and KD silence keepalive. Full apply PASS still pending.
Persistent log W:\\uavf-apply-trim-off.log; evidence W:\\uavf-trim-off.txt.
Guard still active, free /data~64.5GiB. Do not reboot until apply completes,
installed viostor/viosock/runtime and scoped NTFS policy are provisioned, and
BCD/custom EFI shim preservation are verified. No OOBE/desktop PASS yet.
Older collector host-log in viostor-d1-unmap-2138-20261009 had a hardcoded2136
time/CID filter; do NOT use that host log for2138. Corrected collector has explicit
--cid/--since; current KD fatal+wire evidence is independently valid.

Latest live recheck: same CID2139/session17090, apply24%, not1% or frozen replay.
Screenshot apply-trim-off-progress.png. Stream~15FPS, total3289/3289/3289,
pending0 and~7.6ms receive-to-render. Guard /data67,150,584KiB (~64.0GiB).
Keep this exact test running; no restart merely because observer output is quiet.
After completion, read actual DISM status/persistent log before claiming PASS.

Tablet free65.5GiB; >=50GiB must remain THROUGHOUT apply. Current headroom15.5GiB.
Do not overwrite/restore the running mutable disk. Installed boot identity guard,
guest capture/input provisioning and RAM-workaround reapplication after reboot
remain unfinished. Next: reach target selection, guard physical space, provision
installed runtime, then apply to the isolated root only and cold-boot installed OS.

- [x] WinPE userspace, real640x480 Setup screenshots.
- [x] Signed viostor/viosock PnP problem0.
- [x] GDI -> OpenH264 -> UVF1 recording/local decode proof.
- [x] Valid verified install SWMs in isolated media (not installation PASS).
- [x] Exact producer crash localized and own accept call fixed.
- [x] Formal shell AVF owner connects; guest accepts peerCID2 and sends clock.
- [ ] Actual AVF socket descriptor received/usable in diagnostic APK.
- [ ] Constant actual tablet Setup stream, proper resolution/reconnect.
- [ ] Keyboard/mouse/basic touch controlling Setup.
- [ ] Normal installer/source discovery and safe dedicated install target.
- [ ] Installation/files copied, first reboot, installed-disk boot.
- [ ] OOBE/desktop, stable display/input, network if available.
- [ ] Permanent timer fix proved/regressed; current guarded kernel RAM periodic
  CNTV workaround remains temporary, never silently production-integrate.

## Latest authoritative Windows result

`kd-winpe-duplex-proven-client-20261008-runtime`, CID2113 (reproduces CID2110).
Image1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE.
Evidence `winpe-display-evidenceDuplexClient2113` under workspace-parent
build-logs/windows-post-linux-reassessment-20261004.
Two accepts VALID=1/WSA0, peerCID2, peer length12. CLOCK_SEND_END OK1.
Both clock ACKs EOF/0bytes; sent frames0. Supervisor exit0, fault log empty.
Android received/decoded/presented0/0/0; no Surface PNG. FD return on host does
NOT prove APK receipt. Dev/null Binder FD preflight is not actual socket PASS.
30 local AVC proof packets valid. Setup image is driver-discovery page, not
normal install flow. Earlier CID2107 Setup C190011F remains a stability issue;
other runs retained Setup. No installation started.
VM[] verified before read-only remote extraction. No active VM at latest recheck.

## Proven producer fix

CID2108 VEH: C0000005 read0 at signed viosocklib.dll+31d0, instruction
LDR W5,[X21], LR+22c8. Shipped DLL always dereferences addrlen/GetPeerName;
accept(NULL,NULL) was the cause despite newer upstream permitting optional args.
Fixed own call with actual12-byte VsockAddress and int length, no signed changes.
See WINDOWS_VSOCK_NULL_ADDRESS_2026-10-08.md and fault/exit reports.
No more CRT/termination/CPU/GIC sweeps. Own fault logger preserved.

## Current blocker and exact next action

Latest runtime CID2112 (`kd-winpe-fixed-client-link-restored-20261008-runtime`)
held USB through boot but stopped before Setup: LSASS C000001D, context
0xF67E4FA7E0. Reported breakpoint PC is not original fault PC. No LSASS patch
or CPU/PMU sweep; this is an intermittent early-boot blocker, not display proof.
VM[] verified afterward. USB interruptions also occurred in preceding attempts;
runner rejects missing identity/state results instead of NULL hashes.

No-VM duplex socket unit PASS through exact DelegatedAvfChannel/Binder code:
shell writes challenge -> APK reads -> APK responds -> shell verifies.
5-second read bounds, explicit client UID/token checks; no raw AF_VSOCK,
no SELinux changes. This rules out generic socket FD marshalling failure, NOT
an AVF-specific denial. Actual AVF receipt still requires runtime evidence.
Diagnostic APK SHA9A9223F33088B4638E3B7C26EC96A6F4649FC11FCD9B617E8A425184F45DA840;
owner jar SHAD0BE8BC8DBA7B4C7248CEE14E43970447E0B9AFA7F1E1B6BB1A9138185A0CFDE.
Both installed/deployed; production APK untouched.

CID2113 control completed: Windows producer healthy, two accepts/clock sends,
ACK EOF; Android actual FD replies fail Binder EPERM / BR_FAILED_REPLY twice.
Owner process still alive; ordinary duplex socket transfer PASS. Actual AVF FD
transfer is therefore blocked by an Android security refusal, exact rule unknown
(no AVC available). See WINDOWS_AVF_FD_TRANSFER_2026-10-08.md. Do not repeat this
launch, proxy bytes as a policy workaround, or weaken SELinux. Decoder never
receives the socket; decoder/encoder tuning cannot fix it.

Shell FD delegation is closed. Supported production ownership is APK-created
VM/connectVsock, whose boot regression remains unresolved. No new CPU/PMU
difference established; previous user-closed sweeps must not be repeated.
Next work requires a new concrete app-owner boot hypothesis or supported
ownership capability, not random flags. Live producer/input/install stay pending.
Follow-up shipped API audit found no live owner reassignment; documented
toDescriptor/importFromDescriptor copies a STOPPED instance and still requires
APK-owned boot. claimVmInstance/createAccessorBinder are not a demonstrated
UVF stream ownership solution. No additional VM or ownership mutation made.
VM[] verified after reconnect and cooperative stop. Guest reports extracted;
fault log empty, producer exit0,30 local codec packets valid, Android0/0/0.

## Media and safety

A40B baseline retained; old named INSTALL.SWM/INSTALL2.SWM were zero-header
placeholders. Replaced with fully wimlib-verified pair from local read-only ISO
Win11_25H2_ARM64_ru-ru.iso SHA56E385327094DB839AD0DAECD43B192634C8C2B1ADAE0AAC1BF4FF02B90CA9EE.
Build26200.6584; Microsoft origin/checksum not independently reverified.
SWM manifest install-payload-udf-20261008/payload-report.json.
Original03E3 device media retained; PC current scratch updated in-place with
audited reversible deltas, WIMs/manifests retained. No repeated9GB PC clones.
Paired EFI893B7859...02E8B unchanged. GPT only ESP media, LBA2048..17825758:
no dedicated install target; never format this as Windows root.

User-closed CPU/PMU A/B stays closed; no SMSS signed userland RAM patch.
App-owned boot regression remains separate (WINDOWS_APP_OWNER_REGRESSION_2026-10-07.md).
Reserve >=50GiB each drive; latest C~55.5/D~59.5/E~82.9GiB. Follow STORAGE_POLICY.md.
E Disk Event153 retries: avoid heavy writes. Production/GOLDEN/ISOs preserved.
Historical details: WINDOWS_MILESTONE_HISTORY_2026-10-08.md and producer-exit docs.
