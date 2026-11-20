# Windows native resolution / cap60 checkpoint

## Latest direct capture result — supersedes next-A/B plans below

`kd-windows-encode-direct-dxgi-20261011` terminal NORMAL at bounded deadline.
Direct source open_hr0, real1920x1200 BGRA system-memory mapping; actual
Winlogon -> Default/Desktop source/session reconnect and shared Android
MediaCodec/Surface stream. User reports improvement. First frame may be black
during boot: final per-session PixelCopy shows actual bamboo Desktop/icons.
GDI fallback remains unchanged, no Windows hardware-GPU claim.

Current sparse boot/static Desktop interval capture2.783/encode2.261FPS;
wait/map/convert27.564ms combined, encode40.436ms, pipe0.215ms. Android pending
0/1, typical receive-to-render callback7-9ms. Not a sustained active-workload
benchmark, not60FPS. Encoder4 effective threads/LOW/slices8/cap60 unchanged.
Next separate wait/map/conversion timings and measure active Desktop. If
encoding remains>16.7ms, investigate bounded capture/encode overlap; do not
reinterpret static duplicateframes as performance or restart CPU/GOP sweeps.

Typed own encoder/socket/EFI/FAT rollback full-readback PASS, no VM retained.
Exact encoder paddedSHA B79D7B10292712A4D85E7CCEFB0DF95973A68753BB909589206C144AC4375877;
rawSHA CF629B7C44B2C5B39C97E784FAE99CD5F458EE4E4C39E825A57756A8B7F85925.
Small recovery bundle C:/Users/denis/MainProjects/win11ontab/safe-checkpoints/
windows-dxgi-1920x1200-20261011; no disk/userdata copy. Immersive system-bars
viewer fix APK D5B0A757...D74740 installed, runtime check pending.

## Latest live diagnostics result — DXGI direct source available

`kd-windows-encode-live-diagnostics-20261011` terminal NORMAL live Surface
at300s. Shared UVF1 kind6 metadata delivered through SAME app-owned channel;
native chunk/order test and actual Java18-case validator PASS. Encoder padded
88B9DC34...194573E; OWN socket helper8588E5F1...0B8BD7F; diagnostic APK
E4D2656D...C4ED5EB8. No signed socket driver/provider or registry changes.
Metadata does not increment receive/decode/render frame counts; no new decoder.

Authoritative actual runtime values recovered LIVE on Android:
DXGI factory0; adapter0 Microsoft Basic Render Driver1414:008c(flags0);
D3D11 device0/featureB000; attached output1920x1200; DuplicateOutput0;
AcquireNextFrame0, mode1920x1200, DesktopImageInSystemMemory=1, frames1.
Adapter1 is software flags2, device creation0. This is NOT hardware GPU proof.
Native direct-memory Desktop Duplication is available even on Winlogon;
do not claim unsupported based on prior missing NTFS logs.

OpenH264 requested8 threads/LOW/slices8/cap60; actual GetOption status0,
effective4 threads/complexity0/slices8/fps60. Blindly requesting8 again is NOT
a fix. Current GDI source windows~16-17fps: BitBlt32-36ms +conversion4.0-4.6ms
+encode21-24ms; pipe~0.1ms. Transient GDI~304ms dropped to3.025fps; recovered.
Android pending0/1, typical~7ms callback latency, not a physical60fps claim.
Same GOP359DED... and eightCPU contract. All own encoder/socket/EFI/FAT full
readback rollbacks PASS; VMs[] confirmed. Artifacts small/on C only.

NEXT precise A/B: use DuplicateOutput + MapDesktopSurface(system-memory mode)
instead of BitBlt, honor row pitch/BGRA, release map/frame after conversion,
keep existing H264/vsock/MediaCodec. No GDI capture flags/CPU/GOP experiments.
First test unchanged encoder to isolate capture benefit. Then pipeline overlap/
encoder tuning if encode remains>16.7ms. Preserve GDI fallback on access-lost/
unsupported desktop, and do not report repeated static frames as active60fps.

## Latest encoder/DXGI A/B — 2026-10-11

One bounded test `kd-windows-encode-threads-dxgi-20261011` completed normally
at300s with live Surface. Kept exact359DED...1200p EFI and eight-CPU runtime;
changed ONLY own encoder in its existing79872B slot. Binary89B977F0...B8B73A,
padded slot E085E0CA...BE4240. InitializeExt requests SCREEN_CONTENT_REAL_TIME,
LOW_COMPLEXITY, one spatial/temporal layer and min(actualCPUs,8) fixed slices/
threads; logs requested and effective parameters. Public DXGI factory/adapter/
output/D3D11/duplication/one1ms Acquire probe is bounded, read-only, does not
replace GDI. No imported driver or security changes.

Native Windows Desktop PixelCopy1920x1200 verified (icons/wallpaper, no taskbar
visible in first-frame snapshot). Fresh broker1640 -> agent3340 -> Winlogon
producer3556, then Default producer7624 at guesttick215937. Terminal Android
receive/decode/render callback windows~12-14fps, pending0/1,~7ms callback
latency,294 matched frames at end. Not a sustained full-motion60FPS PASS.
Desktop vs preceding Winlogon is NOT a clean encode-speed A/B comparison.

Read-only cold retrieval of both exact observed producers found no directories
persisted yet; this filesystem reader is explicitly non-coherent. Therefore
effective OpenH264 thread count, encode timings and DXGI HRESULTs remain
UNKNOWN. Do not infer DXGI unsupported, initialization failure, or no threading
benefit from missing NTFS directory entries. All original own encoder and
EFI/FAT full-file readback rollbacks PASS; VMs[] confirmed. Production unchanged.

Next diagnostic requirement: recover producer metrics coherently (for example
normal guest shutdown before cold reads, or bounded diagnostics via existing
app-owned channel) before another performance comparison. Do not repeat the
same forced-stop capture test with the same missing-log problem. Keep native
1200p candidate, stable source fallback and common decoder. No new CPU/GOP flags.
Public duplication failure semantics (secure desktop/access and unsupported
current modes, not a global hardware conclusion):
https://learn.microsoft.com/en-us/windows/desktop/api/DXGI1_2/nf-dxgi1_2-idxgioutput1-duplicateoutput

## Latest milestone — native1200p Winlogon live stream PASS

`kd-windows-ram-gop-handoff-audit-2-20261011` terminal NORMAL live Surface
bound at210s, not an observer failure. Exact EFI359DED6E...CD98E3A10752B,
APK A7D123DB...95735E0A. Native startup returned8/barrier0. Windows source
now enumerates ROOT\\BasicDisplay1920x120032bpp64Hz, not Android upscaling.
Fresh producer: broker3272 -> agent3496 -> launcher3732 -> encoder3808,
`ram-gop-handoff-cold-producer-20261011/LIVE-SERVER-3732-52468/LIVE-1/live-encode-report.txt`.
Surface PixelCopy verified real Windows lock screen1920x1200.1544 matched
receive/decode/render callbacks by end, pending0, typically6-9ms receive to
callback. This is a live stream, NOT actual physical sustained60FPS or Desktop
workload proof. Enter was written through app-owned input, not an authentication
or successful guest-execution claim; no password entered.

Source cap60: typical15-18fps; BitBlt33-40ms, GdiFlush~0.015ms, conversion
~4.3ms, encode23-25ms after first interval, pipe~0.1ms. One transient16984ms
interval fell to4.122fps (GDI129.67/encode108.44ms); queue did not accumulate.
Thus source/capture + encode dominate; vsock/Android are not current choke.
Do NOT solve this by increasing cap or dropping native resolution.

Read-only EBS audit: framebufferFF700000,1920x1200, query/set/blt counts0,
three nonzero of256 samples. Direct Mode/framebuffer accesses bypass callbacks;
counts0 do not prove GOP ignored. Terminal parser artifact:
`ram-gop-handoff-terminal-20261011.json`. Own framebuffer was zero at init.
Static exact loader F7747F4A...491D31 enumerates GOP and requires DevicePath
(GUID09576e91...) before mode access; EfiGopGetCurrentMode reads Mode directly.
Public-symbol GUID matches, PDB age4 vs imageage2 (official age2 URL returned
same CCEAAD... file). Names are static navigation hints ONLY, not admitted new
runtime patch/breakpoint identities. No signed loader changes.

Original EFI/FAT five-block rollback PASS; VMs[] confirmed. Keep this successful
1200p candidate; previous300s failure does not establish deterministic GOP
regression. No need another boot-handoff/CPU search now. Next measure/probe a
faster native Windows capture source (DXGI Desktop Duplication feasibility) and
encoder threading/overlap from this mode, common H264/vsock/Android retained.
Require real Desktop active-motion proof before claiming production1200p60.

Typed preflight initially rejected hardcoded10240 size for new10752 binary;
fixed both report and reconstructed directory size, all25 checks PASS on old
and new fixtures. First wrapper pre-VM argument failure rolled EFI back; no
VM launched. Fixed env-loop variable collision, exactly one actual audit boot.
These failures are not Windows boot regressions. C currently~54GiB reserve;
all new artifacts small and on C, no D/E writes or full disk copies.

## Authoritative latest result — RAM-GOP A/B 2026-10-11

The earlier offline-only status below is superseded. Typed own-EFI growth,
25 transaction/verifier negative and partial-write tests, and 8 APK admission
checks passed. Candidate1643... was deployed for exactly one isolated boot:
`kd-windows-native-ram-gop-20261011`. EFI reported GOP success, framebuffer
FF700000, size8CA000. Kernel startup returned8/barrier0 and actual broker
reported8 processors. Fresh Winlogon producer reports recovered by
`ram-gop-producer-cold-20261011` prove ROOT\\BasicDisplay still640x48032bpp.
Android correctly rejected this source against expected1920x1200; this was
not a1200p PASS and not a boot/vsock hang. Original8192-byte EFI and both FAT
copies were restored: `smp-ram-gop-file-rollback-20261011` PASS. No VM retained.

Read-only all17-object BCD audit found no explicit Vga, GraphicsModeDisabled,
GraphicsResolution, HighestMode or BootUxDisabled values; this does not prove
undocumented/default behavior. Evidence: ram-gop-bcd-display-audit-20261011.json,
source SHA39655ba8...7e345a. Element identities from Microsoft's BCD reference:
https://learn.microsoft.com/en-us/windows/security/operating-system-security/data-protection/bitlocker/bcd-settings-and-bitlocker

Next bounded candidate replaces only GOP publication: independent display
handle, own vendor device path + standard display ADR, following local EDK II
QemuRamfbDxe structure. No console pointers/ACPI/CPU changes. Build2 SHA
7E60950F8D525DF05607774227C737E97873EE2DDB3CD80E3D4432BB0E0E1F5F;
ARM64 build, original/fixed-eight byte-parity, bounds/refusal/registration/
rollback host tests PASS. NOT DEPLOYED; no Windows resolution success claim.
Requires exact typed admission/preflight before a runtime A/B. No full disk
copies, no D/E writes. Cleanup deferred at user's request.

### Devicepath A/B terminal result (supersedes NOT DEPLOYED above)

Exact candidate7E60950F...E0E1F5F and diagnostic APK19607F99...DEC12EB3
admitted/deployed. Both old and new candidate typed verifier fixtures passed25
negative/partial-write checks. Fresh plan matches original cluster994732 and
free994747; five blocks only. Run `kd-windows-ram-gop-devicepath-20261011`
terminal after300s: native KeStartAllProcessors returned8/barrier0, driver
loads continued through HTTP.sys at t1174.661. No app-owned display connection
or valid Surface frame. Observer stopped on its response bound at t1186.524;
this is NOT proof the VM hung or a GOP handoff fault. Cold own logs had only
older producer records; do not call them this run's Winlogon/source evidence.
Original EFI/FAT five-block rollback readback PASS and VMs[] confirmed.

Next: keep proven baseline. Obtain loader-side GOP selection/query/set-mode
evidence and actual Windows boot framebuffer handoff before another candidate.
Separate registered GOP is not proof Windows selected it. Do not repeat the
same300s boot or change CPU/timer flags simply because this observer expired.
No resolution/FPS advance claimed. All artifacts are small, on C.

## Preserved platform

The exact successful eight-CPU launch and explicit-affinity IPI adaptation are
unchanged. Ubuntu, P7, firmware FD, kernel, signed Windows executables,
partition metadata and user files are untouched. No commit/push.

## First cap60 measurement

Own encoder was rebuilt ARM64 and replaced in its existing79872-byte slot,
with retained original, exact preimage guard and complete readback PASS.
Prepared SHA2562987c51efbe7d2d0e9870683b0b82a6d86097eb79b19a49ee97716a582f9786c.
Default cap60; UVF1/OpenH264/app-owned vsock/MediaCodec path unchanged.

`kd-windows-display-cap60-20261011` completed; original EFI restored. It did
present valid frames (three PixelCopy PASS generations); the terminal report
was overwritten by the final connecting generation. Final counters0 are NOT
the whole run. Nor do PixelCopy passes establish sustained60FPS.

Current Desktop producer7144, folder `LIVE-SERVER-7144-179078`, encoder6784:

| Interval | Captured FPS | Encoded FPS | GDI ms | Convert ms | Encode ms | Pipe ms |
| --- | --- | --- | --- | --- | --- | --- |
| 5.063s | 13.036 | 12.443 | 70.748 | 0.801 | 5.033 | 0.140 |
| 5.000s | 13.200 | 13.200 | 66.816 | 0.787 | 8.133 | 0.146 |
| 5.078s | 13.588 | 13.588 | 66.279 | 0.910 | 5.958 | 0.174 |

Read-only native mode enumeration: ONLY640x480,32bpp,64Hz. Raising the Android
viewport would be scaling, not a native1200p mode. Native1200p/60FPS NOT PASS.
Evidence: `cap60-desktop-producer-cold-20261011`; distinguish PID7144's old
142500 folder from this run's179078 folder by CID2246 and cap60 marker.

## Completed minimal A/B

Same eight-CPU launch, same encoder/protocol: remove only CAPTUREBLT from GDI
capture (retain SRCCOPY and GdiFlush), split blit/flush timings. Candidate
ea36099c2ffd964e1c7c8913f0796150ddf90115a909ff5735f04c0e3c023243, cold
slot/readback PASS. Run `kd-windows-display-srccopy60-20261011`.
The300s run completed, original EFI rollback PASS. Actual broker3136 reports
processors8; native login/desktop producer transitions occurred. Android
live receive/decode/render callbacks~12-13FPS initially, later3-4FPS with no
queue and~4-6ms receive-to-render latency. It is NOT a60FPS optimization.
Neither cold nor live own-folder reads recovered the new source reports;
non-coherent NTFS absence must not be turned into a source-crash claim.
SRCCOPY-only candidate rolled back to the first tested cap60 encoder with
CAPTUREBLT. Separate blit/flush timing instrumentation remains in source for
the next build; it is not yet validated runtime evidence.

Exact retained runtime snapshots and fresh Android samples:
`srccopy60-live-broker-20261011`, `srccopy60-broker-second-20261011`,
`kd-windows-display-srccopy60-20261011/encoded-display-live-sample.txt`,
`encoded-display-live-second.txt`, terminal `encoded-display-report.txt`.
All temporary EFI changes rolled back. Windows VM not retained.

## Next technical boundary

Read-only adapter/timing instrumentation was built with-Werror and staged as
own existing encoder4161f240fbe7c68e7eda5fa3f3cf635604a5e60798223bfb8c0fde4987a00b84.
`kd-windows-display-adapter-audit-20261011` terminal: normal userspace/Surface
live~13FPS, pending0/1,~4-6ms receive-to-render callback; original EFI rollback
PASS. Own broker3328/agent3564/Default producer7972 transition recorded. New
source directory was not recovered by the non-coherent reader; actual adapter
identity and split blit/flush timings remain UNKNOWN, not fabricated PASS.

## Offline native1200p candidate

Standard reserved-RAM GOP candidate (not hardware GPU acceleration) is behind
WINDOWS_RAM_GOP=0 default; all current launcher bytes are unchanged when off.
Builder accepts-WindowsRamGop only in explicitly isolated eight-CPU/cache-
publication profile. It never replaces an existing GOP, modifies ACPI or CPU
state, changes driver integrity, writes signed Windows files or Ubuntu data.
The framebuffer is1920x1200 BGRA, reserved9216000bytes below4GiB; query/set/
fill/copy/overlap/invalid rectangles/invalid operations/stride overflow/
existing GOP refusal/install-failure cleanup tested on PC. ARM64 EFI build and
original893.../fixed-eight365... byte-parity controls PASS.

Candidate: `smp-ram-gop-hardened-build-20261011/uart-gas8-gic-ds-ebs.efi`
SHA2561643E97CA438F6D0EDAFB504A91F99F2FAAF56E2A3D3A10A9E6E377B0742C9A3,
10240bytes. NOT DEPLOYED / NOT A WINDOWS1200p PASS.
Current EFI8192bytes, one8192-byte FAT cluster994732 (read-only verified).
Do not write10240bytes through the existing8192-byte replacement helper.
Read-only growth preflight COMPLETED:
`smp-ram-gop-file-preflight-20261011/report.json`, device_writes=0.
Exactly one verified free cluster994747; primary/mirror FAT preimages match.
Plan touches five bounded blocks: two FAT sectors, own directory-size sector,
original8192-byte EFI cluster994732 and new8192-byte cluster994747. All full
preimages/candidates retained. No GPT/NTFS or official source changes required.
This is a PLAN, not an applied transaction.

Next: implement and offline-test a typed transaction/rollback for this plan,
preserving GPT/NTFS/official source/user files; exact mirrored FAT/entry/old-
cluster preimages and rollback. Add explicit diagnostic APK/profile admission
and1920x1200 existing decoder/input dimensions before any launch. If an existing
GOP is found, candidate refuses rather than overwriting it: inspect that mode
source instead. No implementation/runtime success claimed for this next step.

Do not raise the cap further or repeat capture-flag tests. First obtain native
adapter identity / DXGI output availability. The launch has noGPU/display
configuration and the proven native mode list is only640x480. A real monitor
source is required for1920x1200. Potential isolated standard RAM-framebuffer
GOP handoff is a hypothesis, NOT an implemented or tested fix; preserve the
successful SMP contract and require an offline-validated bounded A/B before
any launcher change. No driver-signing / trust-store / host policy weakening.

For resolution, a real display adapter/mode source is required. Existing
24.12.24 ARM64 virtual monitor is preflight-only; Authenticode validation is
not proof Windows ARM64 will accept its driver catalog. Do not import roots,
disable integrity or install it blindly. Inspect actual adapter identity /
supported production-signed ARM64 path before selecting an implementation.
