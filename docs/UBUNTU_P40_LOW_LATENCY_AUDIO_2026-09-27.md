# P40: low-latency Ubuntu audio in the standard U-AVF launch

## Result

`LINUX_AUDIO_OUTPUT = PASS`. The user heard the Ubuntu volume-control sound
through the tablet speaker. With P39, the repeated sound lagged by about one
second. With P40, the user reports only minimal delay. The VM remains running.

This is the ordinary in-app Linux launch with the official ISO, not a separate
Ubuntu carrier. The ISO is not modified. P39's platform prefix was preserved
in `build-logs/gpu-virgl-headless-p39-20260927/` before replacement of the
packaged app asset. No GOLDEN or Windows baseline was touched.

## Evidence-backed change

- Guest `parec` explicitly requests 30 ms recording latency and 10 ms process
  time. Its stdout transfer chunk is 4 KiB instead of 16 KiB.
- Android requests a low-latency `AudioTrack` with `USAGE_GAME`, limits its
  effective buffer to 4096 frames, and reports actual buffer size and
  underruns. The previous default path had about 343 ms AudioFlinger track
  latency and 12288 buffered frames. P40 uses the fast track (`F1`) and
  AudioFlinger reports about 66 ms track latency.
- The initial `underruns=6` stayed at 6 for the captured 60-second stream.
  Steady PCM throughput is about 192,000 B/s, matching 48 kHz, stereo,
  signed 16-bit audio. The old cumulative `pcm_bytes_per_s` calculation was
  corrected to an interval rate.
- Audio is still PipeWire/Pulse monitor → guest vsock 4053 → Android
  `AudioTrack`. Native AVF virtio-snd is not enabled in standard launch.

## Identity and runtime

P40 initrd SHA-256:
`486415F5345C85D828EDD73E53D61CD2E1AF607648C11593A14C659A511D644F`

P40 packaged 128 MiB platform prefix SHA-256:
`5C82EA44E721C457B17DE3E536C892A9B2BD50AF1376B944EB656309AE1A5F52`

Installed APK SHA-256:
`8EAB4BD0F098DE950012EF150BABE70CD01B6A1E48DDC4977469454073677CD7`

The app report confirms `VM_LAUNCH=PASS`, the P40 prefix hash, unchanged
official ISO hash `2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14`, and
`ONE_DISK_ESP_PLUS_STOCK_ISO_PARTITION=PASS`. Serial confirms Linux, Xvnc,
frame bridge and GNOME. Frame report confirms non-black Android frames.

Saved raw reports and hashes are under
`build-logs/gpu-virgl-headless-p40-20260927/`. The running VM was not stopped
after this test. `GNOME_HARDWARE_RENDERER=virgl` still needs a direct guest
renderer-string measurement; smooth animations and a responsive desktop are
user observations, not that direct proof. Stable 1080p30 under motion is also
not yet established.
