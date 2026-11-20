# Windows app-owned live Setup baseline

## Proven, not an installed Windows claim

Run `kd-winpe-app-live-name-fixed-20261008-runtime`:
VM app UID10042, CID2133; private disk, own connectVsock4055 descriptor;
existing UVF1 AVC -> c2.mtk.avc.decoder -> Surface. No shell AF_VSOCK,
cross-UID FD delegation, raw fallback or new Android decoder.

First long live metrics: total1621/1621/1621 received/decoded/render callbacks,
~14.97FPS, receive-to-render~6.6–10ms, pending0. Subsequent retained device report
ends with33075/33075/33075 and ECONNRESET. This is long actual live capture, not
replay. Render callbacks are not physical display fences or full capture latency.

Keyboard: Shift+F10 opened real WinPE CMD. First text entered with Russian guest
layout; Alt+Shift switched layout, exact `echo UAVF_INPUT_OK` executed and output
was visually verified. Enter, Escape and typing work through shared UIN1.
Touch/click: actual tap on Setup Browse opened guest folder dialog, screenshot
`input-touch-browse.png`. First attempt at title-bar close landed on Android
window decoration; do not mislabel that as successful guest click.
DiskPart `list disk` proves online GPT Disk0,8GiB,free0.

Evidence directory contains real tablet screenshots, not generated mockups:
input-shift-f10.png, input-text-enter-en.png, input-touch-browse.png,
storage-list-disk.png; reports/events and initial Surface readback.

Service retains VM after KD EOF, proves `KD_OBSERVER_DISCONNECTED_VM_RETAINED_BY_APP_SERVICE`;
vm list independently confirmed running requesterUid10042 after observer exited.
2026-10-09 current vm list is empty. No restart solely on observation timeout.
Reconnect after deliberate producer restart is still NOT separately tested.

## Exact artifacts and fixes

Installed separate diagnostic APK SHA
BBA1CB23DAA89EEB537AA9840AFB5C7F5F8D45D1C88F08FD6455B5BC0091F606.
Production Ubuntu APK/data unchanged.

Pristine current candidate,9,126,805,504bytes:
`windows-display-live-name-fix-20261008.img`
SHA A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A.
ParentE45161...FEE6F. Native guarded7MiB delta,7changedblocks, reverse retained.
All current PC working-image renames are recoverable from retained reverse
deltas; original Windows baseline and firmware remain unchanged.

Live first attempt reached socket but no frames: server called nonexistent
winpe-encode-live.exe while payload installs winpe-encode.exe. Only that own
helper filename corrected. Current source/server calls winpe-encode.exe --stdout;
bootstrap starts server --live. One production codec/transport, guest GDI source.

RAM-only boot adaptations: proven CAM/timer fixes plus exact EL0 PMCCNTR trap
adapter (monotonic CNTVCT fallback, NOT working PMU), and diagnostic-only
DbgLoadImageSymbols notification suppression. No signed userland bytes changed.
Standalone boot without host KD applying these RAM fixes is NOT yet proven.

## Next installation boundary

Host GPT audit: partition1 typec12a7328-f81f-11d2-ba4b-00a0c93ec93b,
LBA2048..17825758, labelWindows Setup; only one ESP uses entire media.
Thus folder picker has only Boot(X:) despite working viostor disk discovery.
No Windows root target. Do not format media/ESP to get past Setup.

Next safe actions: inspect/reclaim verified obsolete Android diagnostic copies
only (not Ubuntu or baseline); extend a disposable isolated Windows working
disk with guarded sparse target region; preserve exact media partition;
mount media ESP with a drive letter and point Setup /InstallFrom at install.swm.
Microsoft documents /InstallFrom split SWM support. Test source mount and
target visibility before any installation/formats. No second virtio-blk needed.
Desktop disk reserves C/D/E >=50GiB still mandatory.
