# WinPE display preparation — 2026-10-07

User needs tablet for other work: no diagnostic VM launch, APK replacement,
foreground automation or production VM stop during this preparation.

WinPE process chain is proven by kd-native-winpe-processes-20261007-runtime.
Do not repeat the old post-EBS diagnosis. Keep the guarded periodic CNTV RAM
workaround and exact disposable candidate/kernel identity for the next test.

## First bounded proof

Built tools/windows-reassessment/winpe-screen.c with ARM64 LLVM/MinGW:
`-O2 -Wall -Wextra -Werror -municode -static -lgdi32 -luser32`.
Output: build-logs/windows-post-linux-reassessment-20261004/winpe-screen-arm64.exe
SHA256: 2BBF8193833DF3D6B9E4F0B26859D38119637E4DFDD557650DA4B43289CB418B.
PE verified coff-arm64; imports GDI32/USER32/KERNEL32 and system UCRT API sets,
no additional compiler runtime DLL. Runtime availability remains to be checked.

Uses public GetDC/BitBlt/CreateDIBSection to snapshot current desktop as a
top-down 32-bit BMP. Validates geometry, reports nonblack pixels/mean RGB,
does not switch desktops or inject input. CREATE_NEW refuses to overwrite.
Guest execution and pixels are NOT yet proven. This is not a continuous viewer.

## Prepared automatic capture candidate

Verified exact retained source WIM SHA A0D1106F85ED9CF449182ED220BCC8480DDC645791D136D9C6AA4880177D48C4.
Index 2 has no winpeshl.ini; startnet.cmd is stock `wpeinit`.
Setup executable at /setup.exe and GDI32/USER32/UCRTBASE exist.
Separate display-candidate.wim adds only two ARM64 helpers and winpeshl.ini
to index 2. wimlib verify PASSED integrity table and file data; extracted
screen helper SHA equals built binary. SACL capture/extraction warnings from
non-elevated local tooling were observed; no runtime load PASS inferred.

winpe-display-startup launches root Setup, retains its process handle and
waits for Setup to exit AFTER taking three snapshots at five-second intervals.
This prevents ending winpeshl lifetime immediately after snapshots. It does
not manually launch a second wpeinit; original Setup initialization stays intact.
Writes only to a uniquely marked volume (UAVF-DISPLAY.TAG), refuses ambiguous
markers and existing output directory. The marker must be staged ONLY on
the disposable FAT test disk. No marker -> no screenshot writes, Setup still runs.

No WIM injected into any raw disk yet; no tablet VM launched in this preparation.
Next: guarded disposable disk copy/WIM replacement/marker, fresh SHA pinning,
then one isolated cold-boot capture test when tablet testing is appropriate.

Next isolated run: stage this helper and an automatic WinPE startup command
ONLY into a disposable diagnostic WIM/disk, preserving original setup startup.
Launch in the actual interactive WinPE session, not session-0 service context.
Export screenshot to disposable accessible FAT and retrieve after clean VM stop.
Verify that it contains real Setup pixels before implementing a streaming relay.
Review UCRT presence and supported WIM index before staging; don't blindly
replace winpeshl.ini or silently disable Setup. Re-audit candidate SHA afterward.

## Continuous display decision

Prefer public guest userspace capture -> existing WAVF/vsock -> Android Surface,
not patched display driver internals. Existing signed viogpudo/viosock packages
have offline compatibility evidence, NOT current runtime binding proof.
Current shell diagnostic VM config has no explicit GPU; first inspect its
effective device configuration. A screenshot success may use firmware basic
display and does not prove virtio GPU. Firmware GOP exporter stops at EBS and
cannot supply a continuing WinPE desktop. Do not promise native Android scanout.

If GDI returns black/error, check actual interactive desktop/display adapter
before adding protocols or changing platform. Keep Windows-specific experiments
isolated; never change working Linux production display, user disk or P7 contract.
