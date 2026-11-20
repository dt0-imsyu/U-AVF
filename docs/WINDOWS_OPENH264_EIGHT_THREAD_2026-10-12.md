# Windows encoder parallelism: isolated build feasibility

## Latest codec test / staging decision

User explicitly authorized deletion of wrong-version Ubuntu workspace.
Deleted through app-owned UI, retained ISO and Windows disk; tablet ~69GiB
free, battery43%, no running VMs before staging. One new controlled run:
runtime-after-cleanup / observer600s. Eight-thread runtime and sustained
desktop-motion FPS remain unproven until this run produces evidence.

after-cleanup actual ARM64 runtime: selected=1/load_error=0/selftest=0;
GetOption threads=8, slices8, LOW,60cap. Real Winlogon->Default reconnect,
user login succeeded, Android native1920x1200 Surface. Observer600s normal
deadline. NOT60FPS: sparse intervals encode~29-56ms; initial lock-screen
sample12ms cannot be treated as desktop throughput. No valid30s motion
sample: rapidly injected command was truncated/PSReadLine escape corruption
(retained screen-motion-proof.png). Next fix diagnostic bulk typing pacing
only, then validate actual workload before claiming performance. Physical
keyboard/display/boot contracts unchanged. No more reboots without a prepared
and visibly verified workload.

Experimental loader compiled: encoder raw59392B/SHA59D8B63267568F8F5B1CA0487FD38B6961584064EE7113247E91DE42DD0BEC55,
existing79872B slot padded1A17C5F7F7D8CEF5C12DA837652CA869870B9B6E041B8FBACC033BC20101289B.
Explicit public volume-GUID/FAT32/UAVF8.DLL lookup, exact843264B/SHA admission,
read handle denies write/delete through LoadLibrary, bounded128volumes,
system-only dependency search. No drive-letter guess or unsigned driver.
Before admitting streaming, SAME reusable12-frame1920x1200 encode/decode/
stride/luma/thread-count selftest executes. Failure selects original codec;
UVF1 kind6 reports selection/load error/selftest. Not runtime PASS yet.
Native-host actual reusable selftest and admission hash/size/reset/file-lock
tests PASS. ARM64 encoder strict build and UVF1/pitch tests PASS. Default
non-experimental build also compiles/tests; preserved stable binary untouched.

Single A/B wrapper run-eight-thread-codec-ab.ps1: checks reserve/battery/cold/
unlocked state; separate codec first, then NEW GOP preimages derived after
codec allocation because FAT sectors overlap. Reverse rollback order restores
GOP before codec; old GOP plan must never be used after allocation.
Attempt2026-10-12 terminal: separate DLL apply/full readback PASS, encoder/
socket apply PASS, freshly composed GOP apply and25 negative tests PASS.
VM owner rejected startup BEFORE VM creation: Tablet install reserve/headroom
(available~48.95GiB, owner requires52GiB). No ARM64 codec execution or FPS
result. NO new display regression proven. Session57074 terminal exit1.
All FOUR recovery readbacks PASS: GOP, socket, encoder, separate DLL112blocks
(including data/FAT/directory/FSInfo/GPT guards). VM list empty. Working
Windows/Ubuntu unchanged. Wrapper now checks identical52GiB admission BEFORE
staging; do not repeat this run while storage remains below that guard.
Do not delete production disks or official ISO to make the test run.

Typed exact-plan transaction implemented (apply-eight-thread-codec.py),
manifest SHA6669F8BA4B756C73D1C2CCE26728F595DF048DDBB44334B895E6AEDA2FF81A76.
Independently reconstructs permitted FAT links, one directory slot, FSInfo
advisory hints and exact codec data, preserving tail bytes; directory last.
Actual verifier/transaction passed465 offline checks, including interrupted
and silent partial writes at EACH of112 apply AND rollback positions,
post-commit verification failure, stale preimage and forged semantic blocks.
Real connected-device typed dry-run PASS, zero device writes; immutable GPT/
boot/EFI guards and all112 preimages still match. This does not prove physical
power-loss atomicity; interrupted process recovery must remain explicit.
No deployment or VM start yet. Next implement verified explicit experimental
codec loader and ARM64 codec selftest before the live performance run.

2026-10-12 read-only device preflight PASS: separate root UAVF8.DLL can use
103 verified free 8192B clusters on the isolated first FAT partition. Excludes
994747 reserved for the proven GOP transaction. GPT table CRC, exact EFI,
mirrored FAT, safe existing directory end slot and FSInfo signatures checked;
all planned blocks re-read unchanged while cold. Original NTFS codec untouched.
Bounded old/new block evidence in build-logs/openh264-windows-eight-thread-
20261012/fat-codec-readonly-plan; device_writes=0. Six offline allocation
edge tests plus actual manifest/hash/reconstruction/directory/FSInfo/FAT-link
invariants PASS. These are NOT apply/rollback failure tests and do not admit
deployment. Next: typed transaction fault tests, then ARM64 guest selftest
with explicit codec path; only after that active DXGI performance comparison.

Actual native x64 sibling DLL (same2.5.1 source/private8-thread constant) passed
GetCodecVersion2.5.1, GetOption8threads/8slices,12 changing1920x1200 frames
encode+decode, dimensions/row stride and luma MAE<12 validation.
Test source tools/windows-reassessment/openh264-thread-selftest.c; ARM64 test
executable also compiled. This is ABI/thread/codec correctness evidence, NOT
ARM64 runtime/NEON performance or actual guest60FPS evidence.

Fresh COLD READ-ONLY isolated disk lookup: actual Windows/System32/openh264.dll
size800856, SHA256F5976BFF9E6DE84F6253C9BFF0F7C70CCDE446B3B20CF7B7206F7A12C35B460F.
Exactly the original Cisco file; no allocation/hive/file bytes changed.

Release flags -fno-rtti/-fno-exceptions are also used by upstream Android
build; ARM64 -O3 lean DLL899584B SHA624B5B2F830222A1250A9B65EF4230E700BB4DBE5066932BADC755EF9C28F8C5.
Final bounded -O2 size check:843264B SHA533F9C108FD71828E7A209A10F0202FE9D8312B0502BD8035C9F8167D7CD5E04.
Still larger than800856: **do not replace current file or truncate candidate**.
No more compiler-size sweeps. Native official DLL contains SIMD instructions
(static count9535 vs candidate15091; different toolchains/layouts, NOT proof
which optimized functions run). Do not assert original codec lacks NEON.

Next controlled staging option: ONE separate experimental codec file on the
isolated Windows media, with exact bounded allocation/metadata preimages and
rollback, explicit test loader path and original library left intact. First
read-only preflight/offline failure tests; never repurpose user files or signed
Windows modules. Then actual ARM64 ABI/thread/encode/decode test before live
DXGI performance. This remains work to implement, not a demonstrated path.

Current stable DXGI bundle remains unchanged:
safe-checkpoints/windows-dxgi-1920x1200-20261011.
No guest/APK/VM/platform mutation made for this work; VM list empty.

Official upstream2.5.1 commit e79561922cc82bf968a4fb8e9aa8fc33e37c1bce.
[wels_const.h](https://github.com/cisco/openh264/blob/2.5.1/codec/encoder/core/inc/wels_const.h)
defines MAX_THREADS_NUM=4; encoder_ext.cpp clips requested encoder threads to
this constant. This explains actual GetOption4 despite request8 without
changing VM CPU exposure. Changing the private compile-time constant to8 is
one evidence-backed local candidate, NOT a demonstrated FPS gain.

Source in build-logs/openh264-windows-eight-thread-20261012/source, shallow
tag checkout, one source delta (constant4->8), license retained. C-only bounded
build; no full VM disk copies. LLVM-MinGW existing toolchain/MSYS make; ARM64
-O3, HAVE_NEON_AARCH64, upstream NEON assembly actually compiled. No SVE/JIT
CPU-feature mutation. Public codec headers/exports remain unchanged.

First DLL compiled but imported libc++.dll/libunwind.dll: not suitable for
blind deployment. Static-runtime relink eliminates those extra dependencies,
using only Windows KERNEL32/UCRT plus six upstream C exports. GC-section build
retains the same codec public interface and -O3 optimization.

Final offline artifact libopenh264-eight-gc.dll:
SHA25676061725D67CF0B5CF8176B17B926DD77EEFAAB7197A901E0F07735C6395F6C4,
909824bytes, IMAGE_FILE_MACHINE_ARM64. Current official source DLL800856bytes.
It DOES NOT fit the currently known original DLL size. Never truncate it or
write it through the encoder-only fixed-size helper. No NTFS allocation/hive
or signed driver changes made. Generated version header has empty revision
because MSYS git was absent; exact source commit recorded above independently.

Build outline:
MSYS make OS=mingw_nt ARCH=arm64 OBJ=o8 -j4,
CC=aarch64-w64-mingw32-clang CXX=aarch64-w64-mingw32-clang++, AR=llvm-ar,
CFLAGS_OPT='-O3 -ffunction-sections -fdata-sections', PROJECT_NAME=openh264-eight-gc,
LDFLAGS='-static-libstdc++ -static-libgcc -Wl,--exclude-all-symbols -Wl,--gc-sections ./openh264.def',
target libopenh264-eight-gc.dll; llvm-strip --strip-all.

Next: actual codec ABI/encode+decode/thread-count test, then a bounded way to
stage ONE experimental own DLL without changing production library or user
data. Do not claim runtime8-thread/60FPS PASS based on compile/export checks.
Before any guest deployment also verify UCRT availability and controlled
LoadLibrary path. A source-built codec's licensing/patent distribution terms
are not the same as Cisco's prebuilt-binary grant: this is R&D, not a release
replacement. No commit/push/publish.
