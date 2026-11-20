# Windows ARM64 practical runtime goal — 2026-10-07

Active user objective supersedes screenshot-only work: advance autonomously to
installed Windows OOBE/desktop with stable common display, input and network
while a safe evidence-backed local path exists. No commit/push/publication.

## Active priority / latest evidence

AUTHORITATIVE MEDIA CORRECTION (direct FAT reads, 2026-10-08): A40B media DOES
have INSTALL.SWM3975906780bytes +INSTALL2.SWM2348011684bytes, but BOTH first48bytes
are all zero (no MSWIM header/GUID/image count). They are INVALID install
placeholders, not a usable payload. Earlier "absent" wording meant no usable
payload; filename existence alone is not a valid test. FAT free1884241920bytes.
GPT has one ESP-type partition LBA2048..17825758 named Windows Setup; no separate
installation target. Do not format that media partition as a Windows root.

Valid ISO payload now independently extracted/split/fully wimlib-verified on C:
install-payload-udf-20261008/payload-report.json PASS, ARM64 build26200.6584.
Local ISO SHA56E38532...90CA9EE; install.wim SHA293DFA07...537131. ISO unchanged.
Preparing real-setup clone from exact DDC0 lifetime image, replacing ONLY invalid
SWM entries with valid parts, retaining EFI/platform/boot WIM. Handle68873 live;
await disk audit before staging/runtime. This is a concrete media-source fix,
not a new CPU/platform experiment and not yet a Setup/display/input PASS.

Host E: is disk1 WDC WD10EZEX-22MFCA0; System Disk Event153 I/O retries observed
16:11..16:12. Stop heavy writes to E, do not assume Healthy label negates retries.
ISO mount was dismounted. Partial E artifacts not valid. Own E extraction process
ended. Scratch moved to separate C:disk2. Removed only our rebuildable88EA
direct-accept raw image and temporary extracted install.wim AFTER SWM verify;
baseline, candidate WIMs, source ISO, parts/manifests/logs remain preserved.

Install source audit 2026-10-08: local win11-iso-work/Win11_25H2_ARM64_ru-ru.iso
contains /sources/install.wim6347029986bytes; wimlib reports3 ARM64 editions,
build26200.6584. Original diagnostic media lacks install.wim/SWM and install
target; historical driver/source discovery screen is NOT completed Setup.
Official ISO origin/checksum has not been reverified against Microsoft; record
local checksum separately, do not call this a newly downloaded verified ISO.
Mounted ISO read-only for metadata, then dismounted (Attached:false confirmed).
Initial mounted split was interrupted and is NOT valid; retain partial artifacts
under E:/UAVF-Windows-install-payload-20261008, never use them as install media.
Direct UDF preparation uses pinned local pycdlib1.14.0 tool and no mount/global
Python changes. Current preparation handle45858 extracts to separate
E:/UAVF-Windows-install-payload-udf-20261008; require SWM verify + manifest before
use. No VM started in this turn and no Ubuntu or Windows baseline disk modified.

Latest 2026-10-08: shell-lifetime candidateDDC0E4C2...7140363C passes disk/WIM/
paired EFI audit. Startup lifetime fix compiled; no signed userland patch.
kd-winpe-shell-lifetime-20261008-runtime stalled in BOOT DEBUGGER before kernel:
last file request netio.sys after bounded own break-in, then no complete KD state.
Observer ended and owner cleanup stopped VM. This is observation/boot failure,
NOT proof that Windows kernel hung, and NOT a lifecycle/display test result.
Do not spend another series on UART/timing/CPU/balloon/order flags.
Lifetime fix and direct-accept helpers remain unverified in a completed runtime.
No actual tablet Setup stream/input/installation milestone achieved today.

Follow-up kd-winpe-shell-native-fault-record-20261008-runtime reached userspace
and viostor/viosock without C000001D; native original exception record reader
was added read-only (no new CPU probe or userland patch), but no exception occurred.
Fresh readback winpe-display-evidenceDirectAccept2102 confirms own server reached
ACCEPT_BEGIN DIRECT=1 GENERATION=1, never ACCEPT_END/clock handshake. Guest then
requested Hal reboot reason3; process inventory had no Setup/own shell helpers.
All3 GDI captures are purple WinPE background, not usable Setup; nonblack pixels
alone must NOT be called Setup PASS. Android0frames. Diagnostic VM cleaned up.

Own startup previously returned after WaitForSingleObject(Setup launcher);
winpeshl shell lifetime must not be tied to an exiting Setup launcher. Prepared
minimal own-bootstrap fix retaining WinPE lifetime and saving launcher exit code
and available Panther/wpeinit logs. No signed Windows change. Lifetime helper
SHA362E5880...011A6834; separate shell-lifetime candidate preparing. Next one test:
does retained shell allow bounded accept and actual UVF stream, and what is the
launcher exit/log evidence? This is not yet a display fix or installation PASS.

2026-10-08 bounded transport A/B prepared: own winpe-vsock binary compiled with
UAVF_DIRECT_ACCEPT=1, bypassing only select before accept, with stage markers and
45s accept deadline terminating only the helper. Separate candidate88EADE49...
EF2F6A7 built from exact A40B baseline; BOOTAA64.EFI remains893B7859...02E8B.
WIM update replaces only /Windows/System32/winpe-vsock.exe in boot index2.
New runner accepts an explicit guarded candidate SHA; all boot/CPU/KD settings
unchanged. Artifact: direct-accept-disk-report.json. Initial runner identity gate
rejected the new SHA before VM creation; fixed by checking the variant manifest's
exact base SHA/size/paired EFI, not disabling checks. Baseline files preserved.

Actual run kd-winpe-shell-direct-accept-validated-20261008-runtime booted kernel
but stopped with unhandled C000001D in wininit.exe before guest viosock server
or viewer connection. No new PMU probe or SMSS patch. Therefore transport A/B
was NOT executed and gives no verdict on select/accept; no Android stream PASS.
Actual crosvm config matches prior CID2100 Setup run except PID/CID (now2101).
Fault instruction is NOT localized: retained PC is the debugger breakpoint,
not necessarily the original C000001D fault PC. Do not label it PMCCNTR.
VM cleanup confirmed Running VMs:[]. No blind retry or CPU/PMU branch reopened.
This also qualifies prior owner diagnosis: shell owner is not guaranteed boot
reliable merely because historical Setup runs passed. Preserve the good runs.

- CPU/PMU A/B is CLOSED without a causal fix: identical sampled PMU state;
  different MIDRs, but both instrumented runs failed. Do not repeat CPU/PMU,
  UART/timing/balloon/order experiments or patch SMSS. App-owned boot is a
  separate unresolved regression (see WINDOWS_APP_OWNER_REGRESSION_2026-10-07).
- Shell-owned WinPE/Setup remains proven. Current practical route preserves
  that owner and delegates the supported AVF PFD to the shared Android decoder.
  No raw shell socket, privilege/policy bypass or second decoder.
- Latest test: kd-winpe-shell-avf-delegation-client-result-20261007-runtime.
  WinPE Setup, signed viostor/viosock problem0, local H.264 proof PASS.
  Formal AVF4055 PFD returned; Android received/decoded/presented0 frames.
  Guest report stops at LISTEN_READY4055/CID2100. NOT live-stream/input PASS.
- Next: one guest-helper transport boundary test (select/accept/clock handshake),
  retaining the exact good boot contract; no new CPU/PMU investigation.
- Ubuntu production/data/ISO untouched. All diagnostic VMs currently stopped.

Entries below preserve the historical milestone evidence; older "current" or
"awaiting" notes do not override the active priority above.

- [x] WinPE userspace: active winpeshl/SetupHost/SetupPrep/setup from RAM disk.
- [x] Isolated guarded periodic CNTV RAM workaround passes cold boot.
- [x] Real WinPE frame: three nonblack GDI captures at 640x480.
  Latest GUI run hides diagnostic console and shows real Windows Setup.
  GUI snapshots stable, NOT a video-stream or interactive-install PASS yet.
- [ ] Stable stream, resolution, reconnect; prefer shared production H.264 ->
  current UVF1 vsock4055 -> MediaCodec -> Surface (4052 is raw), no Windows-only transport.
- [ ] Keyboard/mouse and basic touch where supported.
- [ ] Setup genuinely interactive.
- [ ] Installation started onto disposable Windows target only.
- [ ] First reboot and boot installed disk.
- [ ] OOBE/desktop.
- [ ] Network.
- [ ] Stable runtime/regression.
- [ ] Explain recurrent CNTV mask and prove minimal permanent isolated fix;
  RAM workaround is temporary, never production integration without regression.

Current experiment: kd-winpe-gdi-capture-20261007-runtime,
display candidate SHA D1F0CA6F2EE10931AF719C49F0B1F65C0DD0E4725C89314B72B297C37359DB14.
Automatic Setup + three GDI screenshots onto uniquely marked disposable FAT.
This is source validation, NOT a stable-stream milestone.

Capture run passed WinPE again, automatic winpe-display-startup and SetupHost
active at bounded KD inventory. BasicDisplay and FastFAT load confirmed.
Disk retrieval in progress; no screenshot verdict yet. No production VM running
during this isolated test, cleanup reports Running VMs: [].

Protocol audit: repository production EncodedDisplayProbe currently connects
UVF1 H.264 on **4055**, while WAVF/raw uses 4052; user's conceptual 4052 encoded
path must not cause blind port/protocol mismatch. Preserve common implementation,
not introduce a second Windows codec transport. WinPE WIM currently has no
Media Foundation encoder libraries (mf/mfplat/mfreadwrite/msmpeg2venc).
Guest ARM64 software encoder needs explicit feasibility after real frame proof.

2026-10-07 next blocker localized at PnP, not timer/display transport:
kd-winpe-storage-pnp-harderror-20261007-runtime active diagnostic process
`drv00000000.exe` proves drvload return 0; `nofat.exe` proves no marked FAT
volume discovered. Bounded reciprocal PnP tree has 18 nodes including ACPI HAL,
CPU, RAM disk, BasicDisplay, but NO ACPI PCI root/PCI devices/virtio-blk/vsock.
This corroborates existing omitted PCI SSDT/MCFG due CAM-vs-ECAM parser gap.
Do not keep installing drivers onto a nonexistent bus.

One preceding same-candidate run hit 0x4C/C000021A before helper; next run passed
to Setup again. Transient not root-caused; do not blame telemetry or timer yet.
Observer now collects processes/PnP/hard-error memory on future bugchecks.

New local lever: exact HAL HalpPciGetMmConfigPhysicalAddress uses global
HalpPciMcfgFunShift/DevShift/BusShift (defaults 12/15/20), not immutable shifts.
Static audit guards PASS; CAM geometry would be 8/11/16. This is NOT runtime
permission to mutate constants blindly. Next audit initializer/all consumers,
obtain true host FDT ranges/IRQ map, then one paired Windows-only PCI CAM adapter
A/B (root description + validated access geometry) if supported by evidence.
Do not fabricate ordinary ECAM MCFG without a working translation.

### Confirmed CAM adapter / storage breakthrough

Read-only host-fdt-probe (12s, same devices/4GiB/one vCPU, disk read-only)
captured actual FDT: CAM 0x2e000000/16MiB, bus0 only; MMIO32
0x2c000000/32MiB; prefetch64 0x180800000..0xffffffffff;
slots1..8 INTA -> GIC SPI4..11 -> GSI36..43. Strict parser saved DTB/JSON.

Isolated uart-windows-cam EFI adds compiled PCI SSDT/MCFG only together with
guarded HAL CAM adapter; exact paired firmware SHA enforced. Default ECAM
shifts changed in RAM to 8/11/16. First test enumerated duplicate adjacent
devices: reusable HAL mapper drops PA low12 bits. Exact remap code 0x4840bc
and native access routine prove missing subpage offset for CAM.

RAM-only 16-byte thunk (0x665b20 padding, source0x49baec) adds PA low12 to
access offset, replays original ADRP, branches back; config limit256. Compiled
reference matches emitted bytes; x16 scratch, flags/SP preserved. No disk kernel
patch. Current template disk SHA 92AFF5C81D229E78C3C92A85BDBF38D0CFE774DEC62992927BF752B978DB36F2
(next GUID capture helper revision will change disk SHA).

kd-winpe-storage-entry-reset-20261007-runtime now proves unique correct PCI
devices, including block1042, vsock1053. Signed viostor DriverEntry returns0,
device problem0; SCSI Red Hat VirtIO disk problem0; STORAGE Volume problem0.
WinPE/Setup remains alive at ~60s and ~120s observer bound. No reset this run.
Other virtio nodes have code28 (drivers not installed), NOT hardware failure.

Capture storage blocker now understood: sole FAT GPT partition is EFI System
Partition c12a7328-f81f-11d2-ba4b-00a0c93ec93b, so GetLogicalDrives misses it.
Next helper uses FindFirstVolume/Volume GUID with bounded wait and unique marker;
does NOT change partition type or assign letters. Candidate preparation running.

Previous subpage run exited with guest-requested reboot (crosvm reset/exit0),
not hypervisor crash. Following same-profile run stayed alive; caller reset probe
not hit. Transient C000021A hard-error text identifies Session Manager illegal
instruction C000001D, faultPC 0x7ff6ccc6253c in one run; exact instruction not
localized yet. Future bugcheck reader captures parameter array and user fault
bytes if address-space context permits. Do not mask CPU features blindly.

Known storage constraint: current diagnostic image intentionally omits Windows
installation WIM/SWM payload. Do not interpret resulting Setup source error as
a boot regression; prepare explicit isolated installer/target layout afterward.

Boundaries: production Ubuntu/data/disk/display, P7 platform and official ISO
unchanged. No root/unlock/flash/SELinux changes. Stop at required privileged
vendor trace, production-data risk, or only random unsupported mutations left.
Do not equate loss of KD/display with guest hang. Update after major evidence.

Goal tool limitation: active goal objective cannot be edited by available API;
this checklist records expanded authoritative objective until previous WinPE
screen subgoal is achieved and successor tool goal can be created honestly.

2026-10-07 current tool goal now explicitly covers the full user objective:
app-owned usable Setup -> installation -> first installed boot/OOBE/desktop.
Previous goal-tool limitation is historical, no longer a current blocker.

### Real Setup source and encoder proof — 2026-10-07

GUI capture run `kd-winpe-gui-capture-20261007-runtime` shows actual Windows
Setup (driver/source discovery page), not the diagnostic console. Three matching
640x480 images preserved in `winpe-display-evidenceGUI`. No install payload is
present yet; do not call this interactive Setup/storage installation PASS.

`kd-winpe-encode-proof-20261007-runtime` passes official Cisco OpenH264 ARM64
inside WinPE: 30 real captured frames -> 30 UVF1 H.264 packets -> 30 locally
decoded frames. Mean capture+I420 conversion 3.194 ms, encode 12.493 ms;
mean luma absolute error 0.413. Bounded 10Hz source proof, NOT sustained FPS
benchmark or Android display PASS. Evidence `winpe-display-evidenceEncode1`.

Next isolated test loads signed viosock and tries bounded 4055 UVF1 replay /
reconnect, then live producer + common Android decoder. Exact native frame size
640x480 must be supported explicitly; production Linux decoder currently guards
1920x1200. Never fake an upscaled resolution or silently break Linux bounds.
Current replay candidate SHA A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5.

Signed VirtioSocket binding is now runtime PASS (PCI1053, problem0), WSP service
alive. Guest report proves AF40, correct CID2085, **LISTEN_READY=4055** in WinPE.
Evidence: `kd-winpe-vsock-replay-valid-20261007-runtime`,
`winpe-display-evidenceVsock1/vsock-report.txt`. No client connected: stock
Android shell AF_VSOCK socket() returns EACCES. This does NOT disprove guest
transport; use public AVF connectVsock via the VM owner, not SELinux/root bypass.
`uvf-vsock-receive1/received.uvf` is a preserved text permission error, NOT video.
First replay wrapper run used unsupported timeout240 and never created a VM;
discard as wrapper validation error, not runtime. Valid run used timeout300.

### App-owned channel preparation

Separate diagnostic APK `com.example.uavfwinpetest` installed; production APK
unchanged. Exact Windows disk and wrapper staged only in its external sandbox.
Uses shared EncodedDisplayProbe with explicit640x480 constructor; existing Linux
constructor still defaults/guards1920x1200 unchanged. No second decoder.
Authenticated TCP KD owner transport added to existing guarded observer.

First app-owned attempt did NOT start VM: getConsoleInput is filtered TestApi.
Old docs already prove temporary hidden_api_policy1 exposes it. A second
attempt was blocked by secure keyguard/screen-off/background frozen activity;
host handshake EOF handling fixed (previously spun on empty recv). No Windows
regression inferred. Temporary hidden_api_policy restored to absent/null.
Next device test needs user to unlock tablet; never request/bypass device PIN.
Harness now defers when keyguard locked and keeps screen awake while foreground.

Diagnostic owner APK with locked-device guard rebuilt (SHA
0132A135CC6DA790206A92C62FD781E47E385935FA8385AB47444AFE985C9D63).
Next exact test after unlock: app-owned candidate A40B..., authenticated39101
raw KD observer with existing CAM/timer guards -> public connectVsock4055 ->
shared640x480 decoder -> nonblack PixelCopy + tablet screenshot. Only after
this source-to-Surface proof replace bounded recording replay with live capture
and wire existing UIN1 input to Windows SendInput. No fresh platform A/B needed.

### Prepared while tablet remains securely locked (no runtime claims)

Live producer source compiled separately: public GDI->I420->OpenH264->UVF1
anonymous pipe,15Hz paced,900 frames bound; no replay file or local decoder in
live hot path. Existing vsock listener has optional --live and UIN1->SendInput
adapter. Child restarts from IDR per connection; bounded sends / pipe closure.
No candidate WIM/disk updated yet: preserve first app-owned replay proof A40B.

Diagnostic APK also compiles shared GuestKeyboard + UIN1 touch/mouse wiring,
native4:3 viewport letterbox. These inputs are NOT runtime tested; current A40B
guest still has original replay listener without the new input worker. APK SHA
DDBC0F229F22815020C08CDFB04263DC23980DF04E2E0237937D22DCA9D1B7A3.
Safe run-test.ps1 gates on unlocked/no-active-VM and restores original hidden
API setting plus diagnostic ownership in finally. Never bypass keyguard.

### App owner / PMU boundary localized

User unlocked tablet. External FUSE kernel/disk paths caused DeadObject in
createVm before crosvm. Byte-identical **private** sandbox copy passed to real
Windows kernel/KD (`kd-winpe-owned-private-ready-20261007-runtime`). Production
APK/data untouched. Original external A40B copy stays immutable; private copy is
guest-written and must be explicitly restored only while disposable/preinstall.

Repeated smss fatal C000001D now has exact native bytes: PC imageRVA1253c,
instruction d53b9d08 = **MRS x8,PMCCNTR_EL0**. Source smss SHA B2129DA7...03694D,
64-byte window byte-identical in memory. NOT SVE, not random memory corruption.
Read-only `smss-pmu-static-audit.json` records code and KD flag consumers.

KdIgnoreUmExceptions was already0 at IO return and did not expose first chance;
that observation branch is closed, not repeated. Historical attempted A/B used guarded
PspUserThreadStartup8e2af0 + exported native process accessors to identify
smss/PEB/image before EL0, then replaces only its proven PMCCNTR instruction in
RAM with same-register CNTVCT read. It never reached the guarded image write:
native currentThread/process identity was proven, but PEB/user page is unreadable
at preentry. No signed userland instruction was patched. Do not repeat that probe.

### Launch parity restored first — user priority, 2026-10-07

SMSS RAM patch investigation deferred. Kernel/wrapper/candidate identities and
CAM/timer patch bytes/order match earlier successful launch. Found actual
differences: shell balloon=true vs APK=false (PCI slot changes), shell osName
empty vs custom APK name, and shell KD ingress paced8bytes/1ms vs burst APK.
An isolated APK shell-config parity run still hit exact PMCCNTR SIGILL; balloon
alone is NOT a demonstrated fix. Actual framework RawConfig now retained.

Exact former shell path re-run from pristine A40B disk:
`kd-winpe-shell-parity-control-20261007-runtime`. Reached actual Setup again:
`winpe-display-evidenceShellParity`: nonblack640x480 screenshots,30 UVF1 packets,
30 local decodes, encode10.318ms, listener4055/CID2092. No SMSS patch, CPU model
change, or firmware change. Idle KD inventory failed; that does not erase fresh
guest-produced screenshot/encoder/listener evidence.

Current controlled APK A/B: `kd-winpe-owned-paced-parity-20261007-runtime`,
same shell-compatible config plus8byte/1ms UART ingress. Sole remaining tested
difference vs previous APK run is sender pacing. Await runtime outcome before
claiming cause. Production Ubuntu unchanged; only disposable diagnostic Windows
private copy restored. Goal remains app-owned live Setup + input + installation,
not a PMU research detour.

Paced APK result: stopped inside boot debugger after netio file request; own
break-in yielded one further NDIS request, then KD timeout. NOT WinPE PASS and
NOT evidence that pacing fixes PMU. No userland RAM patch was enabled.
Actual running crosvm arguments matched shell CPU/SVE/no-pmu/memory/PCI/balloon/
serial/disk backend (identity name/CID and fd numbers differ as expected).

Next isolated parity control `kd-winpe-owned-deferred-display-parity-20261007-runtime`:
unpaced bridge, same shell-compatible config, but no early display connectVsock
loop. Shared decoder is explicitly activated only after native viosock driver
LOAD_STATE, with authenticated singleTop diagnostic intent. No fixed boot delay.
This tests the outstanding concurrent-client difference before any PMU mutation.

Deferred-client result: same exact SMSS PMCCNTR C000001D before viosock load,
without any connectVsock/display loop. Early decoder connection is NOT the cause.
Current `kd-winpe-owned-platform-pmu-readonly-20261007-runtime` observes MIDR,
ID_AA64DFR0,PMUSERENR,PMCR at existing IO return checkpoint. Exact MRS words
assembled independently; original kernel16bytes + full context restored after
read. No PMU register writes, feature masks, signed userland RAM patch or firmware
mutation. Compare against an identical old shell observer before choosing a fix.

### CPU/PMU A/B terminal result — diagnostic STOP per user

App `kd-winpe-owned-platform-pmu-readonly-20261007-runtime`:
MIDR410fd821; ID_AA64DFR0100f11f310305719; PerfMon7;
PMUSERENR0; PMCRf8c1. Exact PMCCNTR SMSS SIGILL still occurs.
Shell `kd-winpe-shell-platform-pmu-readonly-20261007-runtime`:
MIDR410fd851; same DFR0/PerfMon7/PMUSERENR0/PMCRf8c1.
This instrumented shell also hit fatal SMSS C000001D with fault PC ending253c.
Thus MIDR difference is real but NOT an explanatory core-specific discriminator;
no specific PMU state delta demonstrated. Do not label instrumented shell GOOD.
Last uninstrumented former shell WinPE PASS remains evidenceShellParity.

STOP this diagnostic branch: no further UART/timing/balloon/order experiments,
no SMSS RAM patch, no guessed PMUSERENR enable. All temporary diagnostic VMs
stopped; hidden_api_policy restorednull. Production Ubuntu unchanged.
Track app-owned boot reliability as a separate regression. Product next step:
shortest supported AVF-owned connection/FD handoff from existing good shell
launch to shared Android MediaCodec; no raw shell AF_VSOCK/SELinux bypass,
no second decoder. Then prepared live source/UIN1 input and isolated Setup media.

### Supported owner channel delegation proof in progress

Shipped shell API audit (UID2000, no VM started by audit) confirms formal
IVirtualizationService.createVm and IVirtualMachine.connectVsock(int)->PFD.
Framework documents VM-owner connection transfer to another process. Prepared
diagnostic shell AVF owner using same exact raw config/stdio ingress and an
authenticated Binder delegate restricted to diagnostic APK UID + token +4055.
APK provider accepts only shellUID2000 and matching live viewer token. Shared
EncodedDisplayProbe is unchanged: delegate object provides connectVsock(long).
No raw shell socket, SELinux policy change, root or additional decoder.

`kd-winpe-shell-avf-delegation-20261007-runtime` is the first actual channel test;
not PASS until fresh receive/decode/Surface evidence. Production APK unchanged.

Shell owner bridge now reproduces real WinPE again:
`kd-winpe-shell-avf-delegation-provider-ready-20261007-runtime` proves live
smss/csrss/SetupHost/SetupPrep/Setup processes; viostor +VirtioSocket problem0.
Fresh `winpe-display-evidenceDelegatedChannel`: real640x480 Setup screenshots,
30captured/encoded/local-decoded UVF1 frames, listener4055/CID2098. No CPU/PMU
probe, userland patch or platform changes. Formal AVF Binder owner published
successfully and viewer accepted it, but receive/decode/present stayed0.
This is NOT end-to-end display PASS yet.

Ordinary Android IPC startup errors fixed before more guest tests: Looper init,
non-null inputDevices array (same step as framework VirtualMachine.run), and
real shell attribution using AOSP shell-provider acquisition. Preflight without
creating VM proves provider access and rejects invalid token. It does not weaken
permissions or impersonate another UID. App-owned boot regression remains separate.

Current `kd-winpe-shell-avf-delegation-channel-20261007-runtime` logs first exact
owner connectVsock result. Delegation restores owner Binder identity only after
clientUID/token/port checks, then restores calling identity in finally. No raw
AF_VSOCK syscall, second decoder or SELinux mutation. Await actual data/Surface.

Owner API actually returned a4055 descriptor in the channel run, but Android
continued connecting with0frames. Do not conflate fd-return with usable stream.
First subsequent service result was Failed to connect. Added exact first
client-side exception capture rather than changing transport/platform flags.
An independent Binder PFD roundtrip unit test passes (ordinary/dev/null FD,
NO vsock/video/guest claim), and invalid owner token is rejected. Current
`kd-winpe-shell-avf-delegation-client-result-20261007-runtime` gathers the actual
client result using unchanged Windows candidate/boot contract. CPU/PMU branch
remains stopped.
