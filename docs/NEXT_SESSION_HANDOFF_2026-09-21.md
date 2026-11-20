# WinAVF continuation handoff — 2026-09-21

Paste the following as the first message in a new session.

```text
Continue the WinAVF project in C:\Users\denis\MainProjects\win11ontab.
Read these first, in this order:
1. Non-root-Windows-VM\STATE.md
2. CHECKLIST.md
3. Non-root-Windows-VM\docs\UBUNTU_GNOME_VSOCK_V17_V18_RUNTIME_2026-09-21.md
4. this handoff file.

Work autonomously, in Russian. Do not reopen closed Windows post-EBS branches
(BCD/KD/GDB/ftrace/Perfetto/ACPI/GIC/CPU masks/WIM) unless a new concrete
platform capability appears. Windows is still blocked after EBS on stock
Samsung/GenieZone; immutable external baseline is:
E:\winavf-a3-append-only-runtime.img
SHA256=2582CAE49FDB3BCD7229280DC8595E5407460BCBADED8FF97AEC73D8211278A7.
Never modify it. The matching Android external baseline is:
/sdcard/Android/data/com.example.winavf/files/win11-gop-ebs-r1.img
and must hash exactly the same after every disposable Linux run.

Current active work is Linux-only public app-owned vsock proof, not Windows.
Major confirmed milestones:
* U-Boot -> EDK2 -> Windows Boot Manager -> boot.wim -> ExitBootServices
  post-return succeeds on the product VM, but Windows/WinPE is unobservable
  after that boundary.
* Pre-EBS GOP/WAVF -> Android decoder -> SurfaceView is PASS.
* Linux on the same app-owned AVF/GenieZone VM passes EBS, kernel, systemd,
  graphical.target, and Ubuntu serial login. This establishes a real
  post-EBS Linux control.
* Android app has the public API VirtualMachine.connectVsock(long), but
  APP_OWNED_LINUX_VSOCK is NOT CONFIRMED.

V17 and V18 facts:
* V17 injected a hook and static ARM64 listener into the correct Zstd main
  initramfs CPIO and added it to scripts/init-premount/ORDER. Serial proved
  LV:HOOK, but listener exec said "not found".
* V18 rebuilt the listener compactly (static ARM64, PT_LOAD=0x1000) and
  repeated one Linux-only runtime. It reached graphical.target/login yet
  repeated the exact "not found" exec failure. Android connectVsock got
  ECONNRESET; this is not proof of transport failure.
* V18 evidence:
  build-logs\ubuntu-gnome-v18-vsock-compact\runtime-v18-serial.log
  build-logs\ubuntu-gnome-v18-vsock-compact\runtime-v18-vsock-report.txt
  full hashes are in the V17/V18 runtime report.

The next narrow task:
Determine why /usr/local/sbin/winavf-vsock-hello is shell-visible as
"not found" despite being a present executable static AArch64 ELF in the
same initramfs. Do read-only/source/offline analysis first. The listener must
be proven executable in the exact Ubuntu initramfs before another VM run.
Do not claim vsock failure or return to Windows. Candidate explanations must
be tested statically or with a minimal executable-control artifact, not by
blindly changing AVF/firmware. The desired next runtime evidence is
LV:LISTEN, then LVH1 returned through Android connectVsock.

Key source and tools:
* Android app:
  Non-root-Windows-VM\android-app\src\com\example\winavf\MainActivity.java
  Build: powershell -NoProfile -ExecutionPolicy Bypass -File
  Non-root-Windows-VM\android-app\build.ps1
  Install: C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe
  install -r Non-root-Windows-VM\android-app\out\WinAVF-test.apk
* guest hook:
  Non-root-Windows-VM\tools\linux-gnome\guest\99-winavf-vsock
* listener source and build:
  Non-root-Windows-VM\tools\linux-gnome\guest\winavf_vsock_hello.S
  Non-root-Windows-VM\tools\linux-gnome\build-ubuntu-vsock-probe.ps1
* initrd editor:
  Non-root-Windows-VM\tools\linux-gnome\append-ubuntu-vsock-initrd.ps1
  It preserves the early uncompressed CPIO, edits the Zstd main CPIO, and
  must preserve/append the init-premount ORDER record.
* Linux VHD/FAT materializer (requires elevated PowerShell):
  Non-root-Windows-VM\tools\linux-gnome\materialize-ubuntu-gnome-v13-vsock.ps1
  It copies a disposable V10 Linux raw into a new fixed VHD, replaces only
  CASPER\INITRD with the normal Windows FAT32 driver, runs chkdsk, exports a
  new raw, and creates a reversible firmware range patch. It refuses existing
  output paths. Source raw must stay:
  E:\winavf-ubuntu-gnome-24.04.5-v10-fdtclient-cpu0-candidate.img
  SHA256=FB201BABDD0E309D5177D683387495D058ACF910BAAEF8733DCB944CA92E569A.
* Tool locations:
  adb=C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe
  zstd=C:\msys64\usr\bin\zstd.exe
  bsdtar=C:\msys64\usr\bin\bsdtar.exe
  cross toolchain=C:\Users\denis\MainProjects\win11ontab\tools\arm-gnu-toolchain-15.2\bin

Safe runtime sequence (only after static/offline gates PASS):
1. Build a fresh listener/initrd under a new V-number directory; never reuse
   an existing output raw/VHD/patch path.
2. Materialize with the elevated Windows FAT32 workflow. Require RESULT=PASS,
   FAT32_CHKDSK=PASS, expected initrd hash and a new patch hash.
3. Rebuild/install the app after updating only the three V-number media/patch
   constants in MainActivity.java.
4. Stage the 9GB disposable raw using ordinary adb push to
   /sdcard/Android/data/com.example.winavf/files/<media-name> and stage the
   small patch beside it. Verify both SHA256s and baseline Windows SHA256.
   Do NOT use preload-ubuntu-gnome-private.ps1 for multi-GB staging: this
   device's adb shell stdin transport stops at about 1 MiB. The app itself
   safely validates then copies the external raw to app-private storage.
5. Start exactly one run:
   adb shell am force-stop com.example.winavf
   adb shell am start -n com.example.winavf/.MainActivity --ez ubuntu_gnome true
6. Pull full serial and vsock report from the app-scoped external directory.
7. Cleanup only disposable Linux state:
   adb shell am force-stop com.example.winavf
   adb shell am start -n com.example.winavf/.MainActivity --ez ubuntu_gnome_cleanup true
   Then remove only exact staged Linux raw+patch filenames and rehash the
   Android Windows baseline.

Never delete broad folders or the Windows image. Existing old VHD/raw files
may be retained; do not clean them merely for space without explicit user
approval and exact target verification. Existing unrelated git changes exist;
use selective git add, never blanket-add. Before any future commit, inspect
git status and preserve user-owned changes.
```
