# Windows IddCx source gate

## D3D flip stimulus result — 2026-10-14

One bounded source-only A/B, same8CPU/native1920x1200/60Hz/signed timing
driver, Android primary capture suppressed. D3D11 WARP flip-discard renderer
1721 successful Presents/30062ms=57.248/s (NOT display FPS).
Idd1335 distinct presentation numbers/30.006814s=44.4899/s, copy0.6866ms,
acquire0.8442ms, finish0.0281ms, wait20.9070ms;93.015%waiting,27number gaps,
0repeats. Later static4frame interval excluded. Whole-image uniqueness and
Android present unverified; source58FPS gate failed. GDI control48.4984/s
is better; flip-model stimulus is not a fix. Remaining boundary measured
before encoding: OS composition/Idd buffer availability.
Evidence E:/U-AVF/artifacts/idd-d3d-flip-observe6-20261014.
Guest scoped driver/test trust removed; observer24501 terminal and cold own
winpe-vsock rollback/readback ce89182f PASS. Ubuntu/P7/firmware/8CPU/H264
unchanged. Prototype preserved, no production switch/no commit/push.

WGC actual1920x1200 source-only run failed the58FPS gate:29.200 readFPS,
Map18.050ms, copy1.342ms. No additional DXGI/GDI/RAM source experiments.

## Admission result

Architectural candidate, NOT a60FPS proof and NOT a drop-in capture API.
Microsoft's IndirectDisplay sample explicitly has Debug/Release ARM64 targets,
UMDF2.25 and IddCx1.4 minimum. It creates a virtual adapter/monitor and gets
the compositor swapchain through MonitorAssignSwapChain/RenderAdapterLuid.
ReleaseAndAcquireBuffer returns a DirectX surface; FinishedProcessingFrame
allows the next frame. Its example discards frames; our encoder integration
would be new work. Readback cost is not guaranteed to disappear.

Actual swapchain receipt cannot be tested with an uninstalled sample DLL.
Windows must load the UMDF device and instantiate its adapter/monitor first.
Consequently BasicDisplay coexistence, actual60 unique frames, buffer readback
and guest driver trust remain UNPROVEN. Do not call successful host compilation
a guest PASS. Do not install a generic third-party VDD as a substitute.

Local ARM64 WDK NuGet headers/stubs already exist in tools/wdk-nuget,
including IddCx1.4 and WdfDriverStubUm2.25. No large SDK download needed.
Machine-wide SDK lacks WDF; observed VS18BuildTools lacks the checked
Hostx64/arm64/cl.exe. This is a build-tool prerequisite, not an OS impossibility.

## Minimal next proof (no production integration)

1. Build source-owned official-sample derivative ARM64 UMDF package on PC;
   exact catalog/signature/version/INF validation and uninstall identity.
2. Obtain a disposable Windows test environment or explicitly reversible
   driver-install plan. Current user constraint excludes installing the new
   driver in the working system; a boot profile alone does not isolate PnP
   registry/DriverStore changes on the same persistent disk.
3. Create a non-primary temporary1920x1200/60 monitor, retain BasicDisplay,
   receive swapchain frames and count unique motion/timestamps/readback cost.
   Do not alter firmware/SMP/Ubuntu/H264 or remove the existing display.
4. Only if source gate passes attach existing x264/UVF1/MediaCodec and measure
   Android actual present. Close software device and verify monitor removal.

Today: NO-GO for proving swapchain frames **without loading/installing an IDD**;
hardware/BasicDisplay compatibility is unknown, not declared unsupported.
No driver/security/monitor changes performed. WGC artifacts preserved; all4
own-slot cold rollback/readbacks PASS after CID2283, runner exit0, VM[].

Primary sources inspected:
- https://learn.microsoft.com/en-us/windows-hardware/drivers/display/indirect-display-driver-model-overview
- https://learn.microsoft.com/en-us/samples/microsoft/windows-driver-samples/indirect-display-driver-sample/
- https://github.com/microsoft/Windows-driver-samples/tree/main/video/IndirectDisplay

## Concrete system-memory source preparation

New stronger lever: IddCx1.5+ provides SwapChainInSystemMemory and
SwapChainReleaseAndAcquireSystemBuffer. Microsoft explicitly documents WARP
as a system-memory scenario; this avoids the subresource copy. It is NOT
arbitrary physical RAM mapping or cross-VM shared memory. Actual assignment
must return in_system_memory=true; absence returns NO-GO for this variant.
Only one acquire variant may be used for an entire swapchain lifetime.

Prepared idd-system-frame-source.cpp with version/function availability gate,
one system-buffer variant, strict1920x1200 BGRA/BGRX pitch/alignment gate and
synchronous borrowed-frame consumer. No retained OS pointers, no staging/Map,
no unbounded queue, no RAM/physical-address API. FinishedProcessingFrame called
after the consumer; source is not a full monitor driver or production backend.

build-idd-system-source.ps1 tested using existing clang cross target and
Microsoft SDK/ARM64 WDF/IddCx headers. Result COFF-ARM64 (AA64), host geometry
policy tests PASS. No installed ARM64 MSVC frontend needed for this compile-
only module. Linking/signing full UMDF driver remains separate and unproven.
Artifact E:/U-AVF/artifacts/idd-system-source-20261013/idd-system-frame-source.obj.

Cold read-only exact-file inventory: IndirectKmd.sys68608B, WUDFRd.sys343448B,
UMDF/IddCx.dll422912B, WUDFHost.exe321408B all found. Parser PEFormatError on
their data streams means versions/ARM64/trust remain unknown; compressed/WOF
is a possibility, not demonstrated. No claim of valid loadable framework.
Evidence guest-framework-v2.json in the same E artifact folder; zero writes.

Next remains a source-owned signed/admitted UMDF monitor package in a disposable
guest environment. No runtime proof is possible without instantiating that
driver device. Current working-system no-install constraint remains respected.
Do not open another DXGI/WGC/RAM benchmark or modify BasicDisplay to test this.

- https://learn.microsoft.com/en-us/windows-hardware/drivers/display/iddcx1.6-updates
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/iddcx/nf-iddcx-iddcxswapchainreleaseandacquiresystembuffer

## Official sample build gate

Pinned Microsoft Windows-driver-samples commit2dc3fd3a0cc84a2933f2194e7ec0871584979071.
Downloaded only Driver.cpp/Driver.h/Trace.h/INF and retained LICENSE.MS-PL
in a new E artifact directory. Upstream source unchanged. Build-only empty
Driver.tmh shim permitted only after asserting no WPP/Trace call sites.
This shim is not a tracing implementation for an installable driver.

Full official Driver.cpp ARM64 compilation PASS using clang/MSVC headers and
ARM64 WDF/IddCx SDK; native object IMAGE_FILE_MACHINE_ARM64 AA64. Two duplicate
extern warnings originate from WDF context macros, no compile error.
Artifact E:/U-AVF/artifacts/idd-official-arm64-fixed-20261013/IddSampleDriver.obj,
SHA927D9999F6B4830286E455F7033A8656753B52BF4A9688047CD701A96E506FEC.
Driver.cpp SHAB6E9320291372A3F041E0BEA716E15E7BC3D8DF6359169E822D3EB3A281BDF3F.
Small own system-buffer adapter remains a separate object, not wired into
the untouched sample's texture-processing worker. No integration claim.

Precise next build prerequisite: actual MSVC14.51 lib/arm64/vcruntime.lib and
msvcprt.lib are absent (arm64 directory contains clang runtime files only).
Machine-wide SDK ARM64 d3d11/ucrt libs ARE present. No reason to download the
entire WDK/SDK or rewrite CPU/display. build-official-idd-sample.ps1 explicitly
reports NO_INSTALLABLE_PACKAGE; it does not link with fake symbols/force flags.
Next: official ARM64 CRT build libraries, proper WDF export/link plan,
unique software-device/monitor identity, catalogue/signature validation,
then disposable guest admission. Current working-system no-install remains.
No device writes, VM restarts, certificate imports or monitor changes here.

## Runtime/toolchain gate resolved

Missing CRT libraries now obtained from actual installed Visual Studio official
catalog packages, without installing/modifying machine-wide tools:
Microsoft.VC.14.51.CRT.ARM64.Desktop.base.vsix SHAee3b6a07c359f7f08d2185a3fdab122fe158fba8d9421ac1e74fadcd48b16da7;
Store.base.vsix SHA63b45cf14780ca9fb2fc6425cf2283569c791e3f11b79847d7e1e79b65f4cc51.
Verified exact payload hashes BEFORE extraction. Only3 actual native ARM64
libs extracted from the Store package's normal lib/arm64 directory. Artifacts
E:/U-AVF/artifacts/msvc-arm64-crt-20261013, about134MB of download, no C copy.
Build inputs are not redistributed/relicensed or committed.

Full unchanged official sample DLL now links using native WDF2.25/IddCx1.6
stubs, actual CRT startup, explicit UMDF2.25 minimum and CFG instrumentation.
The WDF stub itself exports FxDriverEntryUm; no alias/forced unresolved symbols.
Latest DLL E:/U-AVF/artifacts/idd-official-arm64-wdf25-20261013/IddSampleDriver.dll,
23040B, SHA8A16746AF02E20AC974CFB5EC0370C234906344FF9F49D2354A5BDF8792BE0E3.
verify-idd-dll.py actual structural PASS: AA64/DLL/real entry, exact WDF export,
CFG10500, ASLR/NX, actual VCRUNTIME140.dll dependency reported. Authenticode
NotSigned; catalog/signature, guest runtime dependency resolution and loading
UNPROVEN. Current INF is untouched upstream template, NOT a staging package.

This supersedes the missing-runtime-libraries blocker above. Next work is
source-owned single-monitor/system-buffer worker + INF/catalog admission in
a disposable guest. The current linked DLL still runs upstream texture worker
and is not connected to our system-buffer adapter/encoder. No FPS claim.
No new driver deployed, no Windows/Ubuntu/firmware/SMP/display modifications,
VM list empty. No commit/push. Source build prerequisites are now solved.

## Wired system-buffer prototype and offline package gate

The earlier "adapter not wired" state is superseded. PC-only derivative of the
pinned MS-PL sample now has one EDID-less 1920x1200/60 monitor, stable container
identity and a worker using only the admitted IddCx system-buffer API. No
staging/Map, physical RAM mapping, or alternate capture API fallback is used.
Protected frames fail closed. Each full frame is copied synchronously before
FinishedProcessingFrame/next acquire. The copy is in a separate non-LTO object,
so the benchmark's otherwise-unused pixels cannot eliminate the timed read.
Worker checks termination on every iteration and waits on the real new-buffer
event for E_PENDING. These are source/build checks, not guest lifecycle proof.

Metrics use ETW provider43ea47ba-292b-4e83-a8ca-c0d468ea31ae, event1/version1:
ten little-endian uint64 values (version, HRESULT, QPC frequency, elapsed ticks,
frames, unique PresentationFrameNumber count, read ticks, width, height, pitch).
Reports every30s and on exit/error. No pointers or guest pixels emitted.
Metadata uniqueness is not whole-image hash verification or Android presentation.
idd-proof-metrics.py synthetic decoder/invalid-input tests PASS; no real ETW
capture yet. Geometry tests and updated ARM64 adapter compilation PASS.

Latest DLL:
E:/U-AVF/artifacts/idd-system-worker-copy-20261013/IddSampleDriver.dll
SHA75EFA21C040D1F110EBFC7247735025B77FB060EE9A5DF724EC4CF00944C6AFB.
Native ARM64/WDF export/CFG/ASLR/NX structural PASS. Unsigned.

prepare-idd-proof-package.py produced a dedicated Root\\UavfIddSourceProbe /
UavfIddSourceProbe identity and independent UMDF group. No BasicDisplay matching.
E:/U-AVF/artifacts/idd-source-package-20261013 contains DLL, unique INF,
LICENSE.MS-PL and generated unsigned catalog. Actual Inf2Cat /os:10_GE_ARM64
signability check PASS with no errors/warnings. This checks offline package
structure, NOT trusted driver admission or compatibility with BasicDisplay.
Upstream extension registration IddCx0102 retained; linked minimum1.6 and
UMDF2.25 runtime resolution still require actual admission evidence.

No software device instantiated, certificate imported, driver staged or display
mode changed. No VM running, no Windows/Ubuntu data writes or commit/push.
Current working-system no-install constraint prevents claiming swapchain/FPS
PASS here. Next is a safely disposable driver-load test with explicit rollback,
then a real30s full-motion source test. Only if >=58 unique source FPS may the
existing x264/vsock/MediaCodec path be attached. Goal60 still NOT achieved.

## Bounded software-device admission helper

idd-probe-device.cpp/build-idd-probe-device.ps1 now build a native ARM64
console helper using documented SwDeviceCreate/SwDeviceClose and ConfigManager
APIs. Default invocation exits without any device change. Only explicit
--disposable-load-probe enumerates the dedicated UavfIddSourceProbe identity.
The flag is an opt-in guard, NOT proof of disk isolation. No INF staging,
certificate import, monitor primary/mode change, or privileged physical access.
Creation callback HRESULT is checked; driver-start status gets a bounded10s
PnP wait. DN_STARTED alone is labelled NOT_SWAPCHAIN_OR_FPS_PASS. Then hold60s
for tracing/workload and close the lifetime handle on success/failure/timeout.
Callback context is retained until SwDeviceClose guarantees no more callbacks.

Microsoft documents that close initiates asynchronous unplug, not uninstall;
registry/device properties and DriverStore remain. Therefore this helper does
not make a working-disk experiment safe or fully reversible by itself.
Primary API references:
- https://learn.microsoft.com/en-us/windows/win32/api/swdevice/nf-swdevice-swdevicecreate
- https://learn.microsoft.com/en-us/windows/win32/api/swdevice/nf-swdevice-swdeviceclose

Helper compiled/linked on PC only; not executed on guest. This completes the
bounded launch utility, not the outstanding signed-package/disposable-guest
admission gate. Do not manufacture another preparation-only FPS milestone.

## Authorized disposable guest preparation

User now approved the proposed separate test disk ("Делай что нужно"). Working
Windows disk is not an installation target. Official cached split WIM parts
again hashverified6A843CDB...452A /7FC03F45...727F; image3 is ARM64 Pro26200.6584.
Fresh root volume length68157440000 matches the established target geometry,
but contains no cloned installed Windows/user data. Boot/admission still pending.

Only obsolete August tablet files removed:
/storage/emulated/0/linux/windows-setup-avf-imported.tar.gz (7082123430B),
/storage/emulated/0/linux/windows-setup-avf-onecpu.tar.gz (7082123391B).
Exact resolved paths verified; no current code references, dated2026-08-14,
VM list empty. Irrecoverable deletion of those obsolete archives; current
Windows/Ubuntu preserved. Free57336804 ->71184252KiB, about13.2GiB reclaimed.

Catalog signed by a separate temporary own test key, thumbprint
80E30D3F7A18BD2A26A40EDF43C71F1D31F41558; artifact on E under
idd-test-signed-package-20261013. No Root/TrustedPublisher imports or private
key export. Authenticode chain not trusted on host; NOT guest admission PASS.

Host construction correction: loop on DrvFS stalled in fsync/folio writeback.
Both owned processes ended; only the incomplete new E root.ntfs was removed.
Direct libwim NTFS regular-file API is documented and preserves full Windows
metadata. DrvFS per-file operations were too slow; local WSL scratch is next,
with C>=66GiB upfront and reserve abort below51GiB, final artifact on E.
Two immutable old PC media compressed in place to obtain headroom; content
hash validation required after compression. No VM/firmware/SMP/display change.
References: https://github.com/ebiggers/wimlib/blob/master/include/wimlib.h
(WIMLIB_EXTRACT_FLAG_NTFS regular file support; STRICT_ACLS).

Fresh root now complete: native API exit0, strict NTFS metadata/ACL mode0x81;
Pro image3, same official26200.6584 ARM64 source. Applied volume actual local
allocation13986088KiB, logical68157440000B. Exported as sparse E artifact:
E:/U-AVF/artifacts/idd-fresh-root-native-20261013/root.ntfs.
Both full logical hashes agree:
C3D9607C6A057DB1DDC469A5DC1A3A84D9B8E555442A687E412BA8374ECE75C7.
No root is marked bootable/installed/admitted: GPT/boot carrier and guest-side
provisioning remain required. All task build/copy/hash processes completed.

Host in-place LZX compression of two immutable old9.12GB media completed;
18,253,611,008 logical bytes now use1,588,043,776 bytes. Logical source SHA:
windows-display-winpe-20261007.img A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5;
windows-display-shell-lifetime-20261008.img DDC0E4C2EF2E155D700736A7808AABF5999D689787724612615F537F7140363C.
Both match original checkpoints; no content changes or relocation.

Concrete dependency gate: official Pro WIM has IddCx.dll but not
Windows/System32/vcruntime140.dll. Existing proof DLL imports that runtime.
Microsoft ARM64 redist14.51.36247.0 downloaded from documented official
https://aka.ms/vc14/vc_redist.arm64.exe, Authenticode Valid/Microsoft signer.
Artifact E:/U-AVF/artifacts/idd-test-ms-runtime-20261013/VC_redist.arm64.exe,
SHAB70EF586669A620A0A30A1156969C05C6A3831DC8F8BC992DA75779D2A92F944.
Not executed on host or guest. Install only in disposable guest before probe;
do not misdiagnose a missing CRT as IddCx/BasicDisplay incompatibility.
Reference: https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist/

Next concrete action: assemble a boot carrier around this fresh volume using
the unchanged proven platform; WinPE provision signed virtio packages/boot
state, then load the test package and measure actual monitor frames. No new
DXGI/GDI/WGC/RAM tests, no current Windows/Ubuntu data mutation. Goal active.

## Sparse disposable disk assembly and bounded transfer

Full new disk assembled:77846282240B logical. Source carrier SHA verified,
frozen EFI893B...02E8B and boot WIMA90B...BEF15 verified. GPT copied read-only
from the cold working disk and CRC/geometry checked; no device metadata writes.
Fresh NTFS allocated clusters14321070080B copied/readback SHA:
31FCF36E7476179B305ACB9A391C634FADB47D3C022D37714B169DDE291AFEAF.
This is an allocated-cluster digest, NOT the prior full-volume logical digest.
Free clusters are sparse zero; fresh NTFS backup boot sector retained.

Only redundant SWMs in the NEW carrier were removed (7213342720B FAT allocation
released). Original ISO/media immutable. Native sparse EOF avoids CRT zero-fill;
one incomplete construction output was deleted and rebuilt, no user data lost.
Task artifacts E:/U-AVF/artifacts/idd-disposable-disk-20261013.

Signed package, official VC runtime, helper and guarded provision scripts staged
on the new ESP. New label UAVF_IDD_TEST, actual native-apply provenance required;
the existing production provision path retains its original DISM-exit guard.
Test-only unattended account/autologon and test-signing are scoped to this
disposable guest, not working Windows. Trust imports/load not performed yet.

Same-UID new-file sparse transfer started:15401680896 allocated bytes,
64MiB maximum staging, each private-file extent read back SHA256. Initial
tablet free72889094144B, required71303364608B including52GiB reserve. Old
windows-diagnostic.img never opened for writing. No activation/boot/driver/FPS
PASS. Cold activation/restore tool journals original inode,size,mtime,NTFS boot
sector digest and serial, fails closed without completed transfer evidence.

App goal status currently usageLimited; human explicitly requests task work.
No programmatic override/resume claim. Product60FPS still unproven.

## Current authoritative state: approved working-guest test

Disposable sparse transfer completed and verified. A cold boot reached real
WinPE/Setup with 8CPU and native1200p; it did not load IddCx. Automatic fresh-root
bootstrap exited before provisioning; no admission/source metrics exist.
Original working disk name and recorded identity restored, VM list empty.
Do not repeat the fresh-install detour as the quickest path.

Human explicitly approved a limited IddCx test in current working Windows.
New helper requires exact C volume serial3EF669B7 and explicit approval marker.
Working job refuses pre-existing probe driver/trust, records rollback journal,
removes only its own published INF and test certs after measurement. Native
primary display/encoder/platform are not modified by this job. Shared Microsoft
VC runtime, if installed, is deliberately retained; not a full-system rollback.

Cold read-only dependency audit: native drivers/UMDF/IddCx.dll present422912B;
vcruntime140.dll and msvcp140.dll absent in System32. First audit incorrectly
looked for IddCx at System32 root; corrected framework-path audit supersedes it.
Evidence E:/U-AVF/artifacts/idd-working-admission-framework-20261013/report.json.
Prepared job includes verified official ARM64 runtime install, exact guest/root
guards and certificate-validity/testsigning gates. Missing CRT is a prerequisite
blocker, not evidence of a source-FPS limit or IddCx incompatibility.
Driver still NOT loaded; source/present60FPS still unproven. Next action is
programmatic payload staging/execution in working guest with elevated consent,
then the bounded30s secondary-display source test. No UI terminal automation.

Delivery implemented using bounded copy-on-write ESP view, not disk cloning.
11939385B staged under IDDWORK; all touched pages journaled. Explicit check
refuses modifications to preexisting allocated data except root directory.
Initial delivery/boot overlap was canceled: cold guards refused writes while VM
ran; own binary/GOP restored, metadata recovery verified zero modified metadata
pages. No IddCx load occurred. Unallocated payload residue is not a live file.
Runner now hard-requires completed delivery report. Second delivery completed
with readback,21 modified64KiB pages; original root/Windows signed files untouched.
Native asynchronous vsock helper requests only exact serial-scoped test job;
display accept not blocked by elevation dialog. Existing own binary backup and
exact fixed-slot rollback retained. No special command channel or UI automation.
Actual runner session kd-windows-idd-working2-20261013 started only after PASS.
Driver and FPS results remain unproven until real logs are collected.

Working-run result: CID2287 passes kernel/SMP and continuous H264 Surface
1920x1200. Native job starts automatically (PowerShell visible in first frame).
Own job-error logs prove missing destination idd-working-test.ps1 following
GUID-path enumeration/copy. Driver, certificate imports and CRT install not
reached; no admission/metrics exist. This is a delivery failure, NOT IddCx NO-GO.
Evidence E:/U-AVF/artifacts/idd-working-live-observe2-20261013.
Delivery fix prepared: temporary unused V/Y/Z volume alias, exact same GUID,
ordinary path copying + readback, preserve earlier failed delivery, finally
remove only newly created alias. PowerShell parse PASS; actual fix execution
unproven. Next minimal test needs cold own run.ps1 update and retry marker,
not another Windows copy or graphics/backend experiment. Current run remains
live; no filesystem patch while its VM/observer is running.

Correction tested: alias delivery now reaches C:/UAVF/IddWorkingProbe test
script. Concrete next gate: TESTSIGNING off, not driver incompatibility.
Read-only live BCD double-read confirmed identical clean store, no16000049
in any object; current loader86df57c0-9fb3-11f1-a311-83a31eab37aa.
Native Offline Registry prepared Boolean16000049=1 ONLY; canonical whole-hive
diff and existing security descriptors unchanged. Cold apply/readback PASS,
32768B BCD without allocation. Original BD953... preserved; candidate45D482...
in E:/U-AVF/artifacts/idd-test-signing-audit-20261013. Temporary guest setting
must be reverted after driver cleanup, without overwriting unrelated later BCD
changes. No firmware, CPU, Android/root/SELinux changes.
Current run CID2289 / kd-windows-idd-working4-20261013; existing native EFI
byte-identical359DED...,8CPU untouched, default H264 unchanged.
Native launcher uses explicit request3 and waits for Default input desktop;
does not automate authentication. No driver/trust/runtime install PASS yet.

New timing evidence2026-10-14 (idd-timing-observe2) supersedes attribution:
GDI flush A/B (idd-gdi-flush-observe2): actual mode60Hz, generator62.8665/s,
GdiFlush0.010539ms/paint; Idd48.4984/s vs48.5171 control. Waiting92.17%,
only1number gap. GDI batching and wrong30Hz mode do NOT explain the gap.
Final E_PENDING is explicit requested worker teardown while waiting, not
driver admission denial; future host source corrects only that terminal status.
No runtime restaging for a metric-label-only repair. No60 or Android PASS.

Source-only A/B (idd-source-only-observe2): primary Android H264 client absent,
owner marker confirms suppression, same Idd timing/generator. 1460/30.092474s
48.5171presentation/s, generator62.77paints/s, read0.691ms, acquire0.813ms,
finish0.027ms, waiting19.066ms/frame (92.504%). Only3number gaps (vs49),
24timeouts (vs6). Primary capture removal does not reach60; upstream availability
remains main boundary. Single cold A/B does not precisely quantify its causal
FPS contribution. Next: GDI submission flush / actual mode cadence, not codec,
Android, source polling, platform flags or new capture APIs.

45.999FPS/30.06583s, generator63.23paints/s. Per frame copy0.574ms,
acquire0.732ms, finish0.024ms, waiting20.399ms (~93.83% interval).
1389 pending calls,1383 event wakes,6timeouts,49number gaps,0repeats.
Reader predominantly waits for actual OS buffer, not copying/encoding it.
Recovering all49gaps still cannot reach60. Do not claim full-image uniqueness
or Android60. Bounded job finally uninstalls driver/removes own trust; close
deadline mismatch identified and future host script fixed without changing
the current staged payload. Next source/composition A/B, not codec rewrite.

Motion experiment: idd-motion-observe3-20261013, full-monitor generator1895
paints/30016ms (~63.13/s). System swapchain1330 distinct presentation numbers
over30.0105496s (~44.31775/s), full-frame read0.66175ms,1920x1200/pitch7680.
This does NOT pass58FPS source gate, whole-image uniqueness or Android60.
No production switch, no codec/transport changes. Boundary now composition /
frame availability before callback; next narrow timing/metadata test only.
Own temporary driver/trust removal and secondary monitor teardown completed.

Final admission result (supersedes earlier pending results):
idd-pnp-wait-observe3-20261013, native HRESULT0, PnP0x180600a/problem0.
Signed ARM64 driver loaded and started; virtual monitor swapchain1920x1200,
pitch7680. Successful ETW interval30.04632677s:3 unique PresentationNumbers,
2.670564ms mean full-frame read. Locked/static guest, no animation producer:
0.0998458updates/s is not a throughput limit; neither image-hash nor Android
60FPS PASS. Two lifecycle intervals report DXGI_ERROR_ACCESS_LOST0x887a0026.
Rollback script verified own cert absence and pnputil package removal; no
ACL/security/firmware/CPU/H264 changes. Certificate/elevation gate closed.
Next narrow experiment is animated secondary-monitor source throughput, then
shared x264/vsock pipeline only if source gate passes. No permission NO-GO.

Latest certificate diagnosis: idd-background-observe4-20261013 captured actual
SYSTEM token (S-1-5-18), enabled Administrators, SYSTEM mandatory integrity.
Root ACL grants SYSTEM/admin FullControl, own test cert absent. TrustedPublisher
registry store missing; Get-Acl failed with GetAcl_PathNotFound_Exception.
No auth/UI needed: finite inherited-token CreateProcessW hidden audit succeeded.
One pre-kernel host GenieZone SIGABRT required a single equivalent retry.
Next finite run idd-store-init-run-20261013/67767 uses documented X509Store
ReadWrite/Add initialization + own cert readback and strict rollback. No ACL,
CI, SecureBoot or boot-configuration changes. Admission-only device/ETW test;
no motion window on Winlogon, no source or Android 60 FPS claim yet.
Reference: https://learn.microsoft.com/en-us/answers/questions/1679945/import-certificate-unauthorizedaccessexception-e-a

Run8 actual result: calendar correction/readback PASS (3405 ->2026 UTC,8ms).
Run9 now stages a guarded AdmissionAuditOnly retry, exact prior script/journal/
Import-Certificate0x80070005 match. It collects token and store ACL/own-cert
presence only; does not change trust, clock, driver or ACL. VM2294/observer93559
live at lock screen, request8 unconsumed. Human unlock required by current native
Default-desktop launcher; don't reboot on a wait or treat missing logs as FAIL.
Evidence idd-store-audit-screen1.png and idd-store-audit-observe3-20261013.
Next failure is Import-Certificate0x80070005 before driver staging. Rollback
journal plus own driver/test-trust cleanup marker retained. Driver never loaded,
source60 unproven. Preserve current VM2293/observer86131; no more calendar or
platform changes. Next narrow gate: actual token/store-access diagnosis, not
an IddCx compatibility verdict. Evidence idd-clock-gate-mft-observe1-20261013.

Run7 actual admission gate confirmed after human unlock: native CI query status0,
returned8, options2622086 (TESTSIGN bit2 present). Official Microsoft runtime
Authenticode status Valid. UTC3405-07-12 versus test-cert validity2026-10-13 to
2026-10-27; fresh job-error explicitly rejects calendar. These are authoritative
run7 logs retained in idd-clock-gate-observe1-20261013, not new run8 evidence.
Run8 current (CID2293, observer86131): test-only prepared native Set-Date is
allowed ONLY after that exact error/script/cert guard, records readback. No
signature bypass or counter/CPU changes. Driver/source result remains unproven.

Run7 historical: native one-off request is now consumed only by a Default-attached
producer after Default input-desktop observation. Broker replacement on
Winlogon->Default killed the old request-owning producer before the test ran.
Instrumented request/broker ordering located this harness defect; unchanged
driver/CPU/firmware/display candidates. CID2292 lock screen reached; guard logs
NON_DEFAULT_PRODUCER_SKIPPED and no request6 yet. Human normal unlock required;
observer11548 retained. Driver admission/source/FPS remains unproven. Exact CI,
Authenticode status and certificate validity will be logged before mutations.
Calendar repair only if a concrete failing gate requires it.

CID2289 screenshot shows working Desktop and native Test Mode watermark.
Still no driver admission output. UTC/calendar anomaly visible12.07.3405 may
block certificate validation; exact failing gate has NOT yet been captured.
Found another harness defect: resume call outside try/catch left stale old
job-error on any later failure. Corrected resume logging prepared. Admission
gate replaced localized BCDEdit regex with native SystemCodeIntegrityInformation
TESTSIGN bit; records actual CI options/status and UTC before driver mutation.
Reference https://learn.microsoft.com/windows/win32/api/winternl/nf-winternl-ntquerysysteminformation
Prepared script parse PASS only; not yet staged/run. No inference that test mode
alone suffices for ARM64 driver admission, and no driver/source FPS PASS.
