# U-AVF audio output fix candidate — 2026-09-26

## Finding

The active Ubuntu runtime enumerates `virtio_snd`, but its control requests
`PCM_PREPARE (0x102)` and `PCM_SET_PARAMS (0x101)` repeatedly time out. The
Android `crosvm` log shows the AAudio backend receiving output streams at
48 kHz, but Android AppOps repeatedly reports an undeclared *empty*
attribution tag for package `com.example.winavf` while those streams are
being opened. The current app targets API 36 and declares no attribution tags.

The AppOps messages are a plausible Android-side cause, not yet a proven root
cause. The source change declares that exact empty tag. The official Ubuntu
ISO, P7 firmware/platform, guest disk, and current running VM were not changed.

## Candidate

- Source: `android-app/AndroidManifest.xml`
- Change: declare `<attribution android:tag="" ...>` with a platform label.
- Candidate APK: `android-app/out/WinAVF-test.apk`
- SHA-256: `2103C7685313E33520D6BBC0B03A04C279ED92DC0579292F376B8AD1D510EC2D`
- APK signature verification: PASS (v3)
- Compiled manifest inspection: confirms attribution tag is exactly empty.

## Runtime result after install

The candidate was installed and tested in a fresh Ubuntu launch. The empty
attribution-tag AppOps warnings disappeared, but guest audio still does not
play. So this was a real Android warning fix, not the root audio fix.

Fresh evidence is in `build-logs/audio-check-20260926/`:

- `serial-after-attribution-fix.log`
- `logcat-after-attribution-fix.txt`
- `audioflinger-after-fix.txt`
- `audio-after-fix.txt`
- `runtime-report-after-attribution-fix.txt`
- `vsock-after-attribution-fix.txt`
- `tablet-after-speaker-test.png`

The Linux guest still reports repeated `virtio_snd` control timeouts for
`PCM_PREPARE (0x102)` and `PCM_SET_PARAMS (0x101)`. At the same time, crosvm
creates short-lived AAudio output tracks, then closes them after only a few
hundred to a few thousand frames. Its logs repeatedly say that it cannot bind
the Android audio service, waits 10 seconds for the permission service, then
gives up. The Android shell can see both services, but the crosvm process runs
in the separate `u:r:crosvm` SELinux domain. This points to the shipped AVF /
crosvm service-access path, not a missing guest mixer setting. No AVC denial
was visible in the collected logcat, so SELinux is a likely boundary, not a
proven denial.

The app update is installed; the current candidate SHA remains
`2103C7685313E33520D6BBC0B03A04C279ED92DC0579292F376B8AD1D510EC2D`.
Do not mark audio as fixed. The next engineering path is either vendor-side
AVF/crosvm permission-service access (which the APK cannot grant itself), or
an app-owned PCM relay that captures guest audio into a PipeWire/PulseAudio
null sink and streams it over a second vsock port to Android `AudioTrack`.

## App-owned PCM relay candidate

The installed APK did not fix audio. A second, independent candidate has now
been prepared without touching the running VM, Windows media, firmware, or the
official Ubuntu ISO:

- App-owned PCM transport: guest PipeWire/PulseAudio null sink → monitor PCM →
  vsock port 4053 → Android `AudioTrack`.
- The null sink is created and selected by the guest helper only after GNOME's
  audio session is available. Audio is 48 kHz, stereo, signed 16-bit PCM.
- The official ISO manifest contains PipeWire, PipeWire-Pulse, WirePlumber,
  and `pipewire-alsa`, but not `pulseaudio-utils`. The two required CLI tools
  (`pactl`, `parec`) were obtained from the official Ubuntu Noble-updates ARM64
  package and its SHA-256 was matched to the official signed-repository
  package index: `84E2BC869FB9AE499B0A96FE3689EFFF3B812F4BD6333222AEAF90901520ECEE`.
- The utilities' required PulseAudio library version matches the version in
  the stock ISO package manifest. The stock ISO itself remains byte-identical.
- The new hybrid initrd was rebuilt from the current P33/P7 initrd. Early CPIO
  is unchanged. Its exact main-CPIO delta is one modified init-bottom hook and
  three expected additions (`winavf-audio-bridge`, `pactl`, `parec`). Audit:
  `RESULT=PASS`.
- Before the host connected, the helper was refined to move already-open GNOME
  playback streams to the capture sink as well as making it the default. The
  final v2 initrd SHA-256 is
  `F46EF847A47A1D260DE2CD6640DE36921153D871F22B5CCC6B41CC59BA879DA3`.
- New 128 MiB P33 prefix SHA-256:
  `3BD7C71E5C8915D89D75AC38E3CCAB90984CA3AA7B09BC68D6BEAB3F9EFD5C75`.
  It differs from the known P33 prefix only inside the ESP; the launcher,
  firmware FD, kernel, GPT geometry, and bytes outside the ESP are preserved.
- Candidate APK has been built and its v3 signature verifies. APK SHA-256:
  `C94A03EEDEE554E55406D3AFF6E306F1ECB45E6F46FD5D0D669455A031F9B084`.

This candidate is **not installed and not runtime-tested**. The current VM was
left running to preserve the user's session. Runtime PASS still requires
stopping that VM, installing the candidate APK, launching once, and confirming
sound while measuring that port 4053 carries sustained PCM. Do not describe the
app-owned audio path as fixed until it is audible and the guest report confirms
`PCM_HEADER_PASS` with sustained data.
