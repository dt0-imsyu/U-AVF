# Generic Ubuntu P26: GNOME Shell visible in U-AVF

## Verdict

```text
P7_PLATFORM_PRESERVED                 = PASS
STOCK_UBUNTU_ISO_UNCHANGED           = PASS
GNOME_SHELL_RUNNING                   = PASS
GNOME_SHELL_VISIBLE_IN_APP            = PASS
WAVF_VSOCK_4052_ANDROID_SURFACEVIEW   = PASS
GNOME_FULL_DESKTOP_INTERACTIVE        = NOT_TESTED
```

P25 ran the isolated GNOME session on Xtigervnc `:1`, but showed the GNOME
fatal screen. A guest Apport core and GDB identified `SIGILL` at PC
`0xe52523d02408`, instruction `index z1.s, #0, #1` (ARM SVE). This proves the
faulting instruction; it does **not** by itself identify the generator or
prove a specific GenieZone defect. The P25 core mapping and HWCAP were not
recorded.

P26 used a narrow A/B: the existing Xvnc GNOME session inherited
`GALLIUM_DRIVER=softpipe` in addition to `LIBGL_ALWAYS_SOFTWARE=1`. Mesa
[documents](https://docs.mesa3d.org/envvars.html) this driver selection and
[describes](https://docs.mesa3d.org/systems.html) softpipe as a shader
interpreter, unlike LLVMpipe's JIT. No P7 platform, Ubuntu ISO, Android frame
decoder, WAVF protocol or vsock port changed. This is strong evidence that
the prior graphics software path caused the SVE SIGILL, but the exact library
or JIT mapping remains unproven.

## Runtime evidence

- Serial: `LV:SOFTPIPE=ENABLED`, `LV:XVNC_SOCKET=PASS`, `GNOME Shell
  started`, `LV:GNOME_CRASH_SUMMARY_BEGIN`, `CRASH_FILES=0`. No recorded
  `SIGILL` or `Unrecoverable failure` in the bounded capture.
- WAVF: 3 complete 1024x768 BGRA frames, all CRC PASS; first two black,
  third with 786,432 nonblack pixels.
- Android SurfaceView screenshot shows the same GNOME Shell top bar, date and
  accessibility icon as the decoded third WAVF frame; the previous fatal
  GNOME screen is absent.
- This is a visible live GNOME Shell session, **not yet** proof of working
  wallpaper, app windows, keyboard/mouse/touch, continuing 2–5 FPS capture,
  or a finished consumer Linux mode.

## Exact artifacts

Root: `C:\Users\denis\MainProjects\win11ontab`.

| Artifact | SHA-256 |
|---|---|
| P25 initrd input | `A157B57F47A0F37819EBE9640EC68D8BB29D4364B4878FB2AE56970547C4CE5A` |
| P26 initrd, `build-logs\generic-ubuntu-frame-bridge\initrd-P26-softpipe` | `4052CCF4B6BE4ACA1DE4869B29A87E6B5962A9C7CC84AF27DA54364A972B39AE` |
| P26 host disk, `E:\winavf-P26-P7-platform-gnome-softpipe.img` | `33D8E9F7021CC7678F51C8C16D94F6A6D46405E70630354C3DF3E38CA2DA2CCD` |
| Same disk after `adb push`, device-side SHA | `33D8E9F7021CC7678F51C8C16D94F6A6D46405E70630354C3DF3E38CA2DA2CCD` |
| Unchanged stock ISO partition | `2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14` |
| Installed test APK | `C3928352FEAF2AC8BB84F3649046ADB6641F5974DA5CB29DEDF08BDFDAAD7B5E` |
| Raw WAVF, `runtime-P26-raw.wavf` | `142B8C773EECD76224E3CEE4B0DA22684A27BE11E9A57436FAC79C4F2E791236` |
| Final serial, `runtime-P26-serial-final.log` | `F164BB75264FD863587B67C81E3BFFCFACC9CC69A911974F50B4CC7517631C99` |
| Android screen, `runtime-P26-android-screen.png` | `2A35034EC90D20B53D42B49AB6A30A4F8F2C35EF1CC0D667E896C514E3ECF9EF` |

Evidence files are in `build-logs\generic-ubuntu-frame-bridge\`. The
decoded final frame is `runtime-P26-last-frame.png` (SHA
`48C9C01E83D5290CECA17A880DD1B0988912E93D3D13A03560F2CD1C57DD0705`).

The P26 initrd audit found exactly one changed entry,
`scripts/init-bottom/99-winavf-vsock-systemd`, versus P25. The early CPIO
remained byte-identical. Disk audit passed both GPT headers, ISO partition
hash, ISO9660 PVD and `FAT1 == FAT2`. P7 launcher, firmware FD and kernel
hashes were checked by the disk replacement script. GOLDEN and the immutable
Windows baseline were not touched.

## Reproduce and inspect

The installed test APK currently selects the P26 disk for the diagnostic
`generic_ubuntu_frame_bridge_test` intent. The device path is
`/sdcard/Android/data/com.example.winavf/files/winavf-P26-P7-platform-gnome-softpipe.img`.

```powershell
$adb = 'C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb shell sha256sum /sdcard/Android/data/com.example.winavf/files/winavf-P26-P7-platform-gnome-softpipe.img
& $adb shell am start -n com.example.winavf/.MainActivity --ez generic_ubuntu_frame_bridge_test true
& $adb pull /sdcard/Android/data/com.example.winavf/files/frame-bridge-test-serial.log C:\Users\denis\MainProjects\win11ontab\build-logs\generic-ubuntu-frame-bridge\serial-copy.log
& $adb pull /sdcard/Android/data/com.example.winavf/files/ubuntu-gnome-frame-bridge-raw.wavf C:\Users\denis\MainProjects\win11ontab\build-logs\generic-ubuntu-frame-bridge\raw-copy.wavf
python C:\Users\denis\MainProjects\win11ontab\Non-root-Windows-VM\tools\generic-ubuntu\audit-wavf-raw.py C:\Users\denis\MainProjects\win11ontab\build-logs\generic-ubuntu-frame-bridge\raw-copy.wavf
```

Do not use the user-facing Linux Launch button as a claim of P26 graphics
yet: it still routes to the earlier generic profile. The P26 VM was left
running in the app after evidence capture for inspection; use the app's Stop
control when done. Before any repeat, save or clear the old per-run evidence
files so their timestamps cannot be mistaken for fresh results. Do not
modify or delete GOLDEN or the official ISO.

## Next focused milestone

Verify a sustained changing GNOME Shell frame sequence and one real app
window through the same 4052/WAVF/SurfaceView path. Then connect supported
input and promote the P26-tested configuration to the normal Linux mode.
