# Encoded display feasibility — 2026-10-01

## Decision

### Final control: local artifact fix verified; research paused

ONE permitted control completed with explicit `x264enc bitrate=32000`;
runtime properties confirm qual/CRF25, VBV600ms, ultrafast, zerolatency,
two sliced encoder threads. No other capture/codec/transport change.
Final AU300 / matched input PTS8689226542: source and local software decode
are visually clean, and the frozen Android Surface has the same clean image.
All301 AUs received/decoded/rendered callbacks, no pending frame at end.
Severe blocking from previous matched frame is absent. Minor ordinary lossy
H.264/scaling differences remain; do not describe output as pixel-identical.

Control evidence: `matched-control-guest.json`, `matched-control-android.txt`,
`control-motion_source.png`, `control-motion_input.png`,
`control-decoded_motion.png`, `control-android.png` in the existing D: folder.
Original matched evidence retained separately. No further FPS experiment:
this short, final-frame-stop control is NOT sustained throughput acceptance.

Cause confirmed: inappropriate default bitrate ceiling in quality mode, not
raw source corruption, RGB/I420 channel order or Android-only decoder bug.
Experimental script retains the local correction. H.264 backend is archived
as EXPERIMENTAL; no production bootstrap rewrite/promotion. Stable display
returned, VM/data intact, HTTP delivery endpoint and ADB reverse removed.
Resume main app/release plan; 60-FPS capture is a separate future stage.

### Matched-frame visual corruption verdict (latest)

ONE test completed after user manually started prepared diagnostic. Exact
encoded AU 300 is the last sent frame and remained on Android Surface.
Matched encoder-input PTS: 8849592717; source bytes 9,216,000, I420 3,456,000.
301 AUs sent / received / decoded / rendered callbacks. Extra in-flight source
buffers after GLib stop are NOT sent to Android. This is not an FPS benchmark.

Evidence under `D:\UAVF-builds\encoded-display-20261001`:

- `matched-noise-guest.json`: matched BGRx/I420/software decode PNGs embedded.
- `matched-noise-android.txt`: all 301 frames, no pending frame at completion.
- `motion_source.png`: clean source, SHA256
  9F1EAB2FAF400DA254166E2F49FF57DA94BE7252F383D13C56E2B47591BE42A8.
- `motion_input.png`: clean converted I420, SHA256
  49EC3E4B892154739788C2734E36C10BE6996D04F1146617354A42A3E6728A8C.
- `decoded_motion.png`: SAME image, visibly large block artifacts, SHA256
  388D4D9852564FEE852C0F628B58A7B8A61A55B2A638DEEE86950A1692AF3098.
- `matched-android.png`: frozen Android screen, same artifacts, SHA256
  0A456EB07189AD92273194853668641413A4F23C62BFB7FB90C16F089339D638.

Verdict: artifacts arise at encoding quality, not BGRx->I420 conversion and
not Android-only decoder/presentation. Specific local config defect discovered:
GStreamer x264enc `pass=qual` still applies `bitrate` as maximum; omitted value
defaults to 2048 kbit/s. Measured active stream ~1.6–1.8 Mbit/s is consistent
with this ceiling. Official documentation:
https://gstreamer.freedesktop.org/documentation/x264/index.html

Minimal correction applied ONLY to experimental script: explicit
`bitrate=32000`, same CRF25, threads, preset, capture and Android codec.
Encoder runtime properties now recorded. Syntax check PASS; runtime quality
fix is NOT yet confirmed. Exactly ONE control prepared, output
`matched-control-guest.json`. Human must rerun previous terminal command to
download corrected script; automated guest script launch previously denied.
After control archive prototype as experimental; do not promote / chase FPS.

Stable display/dialog return completed; production disk, guest bootstrap and
platform untouched. First JSON POST failed solely due >4MiB PNG report cap;
existing report transferred after raising loopback endpoint limit to 32MiB,
NOT by repeating capture. Cleanup delivery server/reverse after control.

### Latest correction: motion validity and visual artifacts

This section supersedes older resume/APK and source-ceiling statements below.
Current installed APK is `apk-local-present/WinAVF-test.apk`, SHA256
`D3BBE6670F9E80C8591CC2ECECA4DAC459C5C639B28C3AC630DF0121357A5C9A`.
Normal fallback is restored and the encoded dialog dismissed. VM/userdata,
firmware/kernel/ISO and production guest runtime were not replaced.

Evidence directory remains `D:\UAVF-builds\encoded-display-20261001`:

| Fixed 20s run | Source | Encoded/sent | Android receive/decode | Capture->send mean/P95 |
|---|---:|---:|---:|---:|
| surface-cadence | 51.20 | 51.15 | 51.15 / 51.10 | 13.43 / 17.10 ms |
| surface-present | 29.75 | 29.80 | 29.80 / 29.85 | 11.81 / 15.77 ms |

Boundary-count differences are expected for a finite synchronized interval.
Both isolate H.264 and stop exact RFB service instances/socket temporarily.
No encoded P-frames are discarded.

The decisive motion trace: cadence run actual window position changes
54.32/sec, present run 30.64/sec. In present run `(200,-8)` appears in 693/1500
samples: a large terminal cannot follow the requested trajectory because the
window manager constrains it. Requests/sec must NOT substitute for actual
desktop change rate. This explains much of the alternating source cadence;
it does not establish a Mutter/PipeWire 30-FPS limit.

SurfaceFlinger collector correction: old cadence/fixed collector polled slower
than ring-history retention; consecutive captures had zero overlap. Therefore
old 37.0/28.1 distinct-present figures are LOWER BOUNDS, not exact rates.
The new fast, exact-layer collector on present run observed 27.35 distinct
display instants/sec, 29.90 unique frame/present pairs/sec. These differ because
frames can share a display instant. Display period still alternates 60/48 Hz;
setFrameRate(60) is a hint, not proof of fixed panel cadence. That low-source
run cannot determine Android's sustained >=50-FPS capability.

Visible corruption is a release blocker. `reference-source.png` and initial
`reference-decoded.png` look clean; software-decoded motion H.264 reference
`reference-decoded-motion.jpg` shows blocky/incorrect dock/background regions,
also visible in Android motion screenshots. Thus Android presentation is not
the only possible cause. Capture vs RGB->I420 vs encoder remains UNPROVEN:
the previous raw reference and motion reference were DIFFERENT frames.

One minimal next diagnostic is prepared, syntax-checked, NOT executed:
`software-encode-stream.py --gstreamer --cadence --reference` now retains the
matched BGRx source and encoder I420 input for encoded AU 300 plus its software
decode. The ordinary terminal is temporarily resized to 1000x650 for valid
movement and its original geometry restored afterwards. Full capture remains
1920x1200, but this is a moving-window workload, NOT a full-screen stress proof.
One-off reference copies are diagnostic overhead, not production capture.
The command to fetch/run this diagnostic was rejected by tool policy; do not
bypass it. Its intended evidence prefix is `surface-matched`; no runtime
report exists yet. Temporary HTTP server stopped. No further reboot needed.

### Latest result and resume point

Software path is feasible and has an actual end-to-end image proof, but
`ENCODED_1920x1200_50_VISIBLE_FPS` is **NOT PROVEN**. Do not reopen hardware
encode or RGB565/raw optimization yet: software encode passed the requested
gate, so only direction 1 was developed.

Runtime proofs on the real GDM/GNOME desktop, moving the existing terminal:

- FFmpeg/Python full-RGB input pipe, production RFB still running: 548 received,
  548 decoded, 548 MediaCodec rendered callbacks. Send ~0.14 ms/AU; guest
  capture-to-send ~119 ms including pipe/AUD assembly and idle intervals.
- Same FFmpeg proof with exact scraper process temporarily paused/resumed:
  1069 received/decoded/rendered callbacks; original service/mode restored.
  Do not call this 50 FPS: it is ~36 average across the complete 30-second run.
- Native GStreamer `pipewiresrc -> queue(depth1, leaky BEFORE encode) ->
  videoconvert -> x264enc -> AU appsink -> UVF1/4055` removes Python RGB mapping,
  FFmpeg raw pipe and one-frame AUD assembly. RFB still running. 958 received,
  decoded and rendered callbacks. Active seconds 4–28: average 37.25 sent FPS,
  max ~44.98; average capture-to-send 14.79 ms. CPU ~0.8–0.9 core in typical
  active intervals. Source and submitted/sent rates match; no decoder buildup.

All proofs use `c2.mtk.avc.decoder` directly into a real `SurfaceView`, NOT
Bitmap. Screenshots show the real desktop. Rendered callbacks are not physical
display fences and must not be substituted for a sustained visible-FPS claim.
Initial candidate's receive-to-render field mixed BOOTTIME with nanoTime:
its zero values are INVALID latency evidence; this is fixed in the next APK.

Current tablet APK: `D:\UAVF-builds\encoded-display-20261001\apk-native-surface\WinAVF-test.apk`,
SHA256 `9F14D282B34D093D2F7A2CD33D3763D990151AEA5DCE50851FD2206BF8FF50B3`.
It disconnects the ordinary raw client while the isolated H.264 proof runs and
automatically reconnects the normal bridge afterwards. Latency clock fix is
included. The subsequent source-only `encodedProbeGeneration` guard was edited
~2 seconds AFTER javac; it is NOT in this APK. Rebuild before repeated-probe
acceptance or promotion. Normal RFB fallback has booted and terminal is ready.

One final test is prepared, not performed: native GStreamer with this APK's
raw-client disconnect, so no parallel legacy framebuffer consumer competes.
Its launch command was rejected by the tool execution policy. Do not bypass
that denial. Installed Ubuntu is running normally, no test currently active.

Evidence directory `D:\UAVF-builds\encoded-display-20261001` contains:
`software-encode-benchmark.json`, `software-encode-install-report.json`,
`surface-proof-first-{guest.json,android.txt}`, `surface-proof-paused-rfb-{guest.json,android.txt}`,
`surface-native-{guest.json,android.txt}`, and screenshots. Guest corpus and
bounded script reports remain in `/var/tmp/uavf-software-encode-20261001`.
Native proof launch (after tools allow it): download `software-encode-stream.py`
and `fps-scraper-ab.py` to the same directory under `/tmp`; run as desktop user
with `--gstreamer`, then deliver MainActivity intent boolean
`encoded_display_probe=true` with SINGLE_TOP. No guest root/pause flag is needed
with the new raw-client-disconnect APK. The test restores resolution at 30s;
close its dialog to return to normal display. Do not run another image/profile.
Temporary delivery server and adb reverse were closed after saving evidence.
Resume requires restarting `diagnostics/fps-evidence-server.py` with a NEW
report-output path and re-establishing tcp:39054 reverse; do not overwrite the
saved proof reports. Current guest-route URL was `http://10.159.93.215:39054`.

Update: CPU software encode passes the first feasibility gate. Real GNOME
Mutter capture supplied 32 1920x1200 BGRx samples (25 unique CRCs) while moving
an ordinary terminal window. With that bounded corpus, 600 frames of x264
ultrafast/zerolatency, CRF25, two sliced encoder threads, no B/lookahead:
135.16 encode FPS, 7.40 ms average throughput service time, 1.74 average CPU
cores; 16.66 Mbps normalized at 60 fps. RGB->I420 separately: 129.61 FPS,
7.72 ms, 1.17 cores. Encoding used ARMv8 NEON; eight total process threads
includes FFmpeg infrastructure, not eight busy cores. Wall runs are only ~4.4s
and ~4.6s; a short repeated desktop corpus is not sustained live/end-to-end proof.

These separately measured stages must NOT be combined as independent FPS or
called end-to-end latency. A bounded actual-live H.264/4055/MediaCodec Surface
prototype is being tested; production RFB/WAVF remains intact. Diagnostic
FFmpeg dependencies: 67 new packages, 131 MB, zero upgrades/removals. Evidence:
`D:\UAVF-builds\encoded-display-20261001\software-encode-benchmark.json`.

Preserve raw/RFB/WAVF production fallback. No encoded transport promotion and
no claim of 1920x1200/60 achieved. The currently exposed guest GPU has NO video
encoding profiles, despite working hardware-accelerated VirGL rendering.

```
ANDROID_MTK_AVC_HEVC_CODEC_INIT = PASS (shell context)
GUEST_VIRGL_VAAPI_INITIALIZE = PASS
GUEST_H264_HEVC_HARDWARE_ENCODE = UNAVAILABLE_CURRENT_CONTRACT
ENCODE_BEFORE_VSOCK = BLOCKED_FOR_HARDWARE_PATH
ANDROID_MEDIACODEC_SURFACE = DOCUMENTED / NOT_RUNTIME_PROVEN_HERE
ENCODED_1200P60_END_TO_END = NOT_TESTED
```

This does not mean that Android lacks a hardware encoder, or that CPU encoding
cannot work. It distinguishes Android's media engine from guest-accessible
VirGL and video-device functionality. No root/system permissions changed.

## Actual device evidence

Evidence directory: `D:\UAVF-builds\encoded-display-20261001`.

Android NDK probe configures 1920x1200, frame-rate=60, encoder bitrate=20 Mb/s:
`c2.mtk.avc.decoder`, `c2.mtk.avc.encoder`, `c2.mtk.hevc.decoder`,
`c2.mtk.hevc.encoder` all configure/start successfully in the first inventory.
These names also appear in `/vendor/etc/media_codecs_c2.xml`. This is codec
initialization, NOT sustained throughput, actual APK UID access, Surface output,
or proof that any of these codecs is exposed inside Ubuntu.

One neutral-frame ByteBuffer latency probe stalled after AVC configure/start;
stopped the exact probe process, then reproduced a bounded timeout with a
12-second process alarm. No latency number is available. This may be an input,
buffering or shell-context issue; do NOT call it a vendor codec defect. Logs:
`host-codec-probe.txt` (initial attempt was superseded),
`host-codec-latency.txt`; source `diagnostics/host-codec-probe.c`.

Guest audit ran as the actual desktop user UID1000, with no services replaced:
- `/dev/dri/renderD128` readable/writable, virtio GPU PCI `1AF4:1050`;
- no `/dev/video*`, `/dev/media*`, `/dev/mali*`;
- PCI inventory has GPU, consoles, block, RNG, network, vsock, PVPanic but NO
  virtio-video encoder device;
- existing GStreamer has CPU VP8/VP9/Theora/JPEG encoders;
- H.264/H.265 plugins `x264enc`, `x265enc`, `openh264enc`, VAAPI/V4L2 encoder
  elements absent; ffmpeg initially absent. Absence of these packages alone
  would NOT prove a hardware limitation.

Installed official Ubuntu `vainfo` and its VAAPI dependencies to test the actual
driver, rather than conclude from missing tools. This also pulled six normal
Mesa packages from 25.2.8-0ubuntu0.24.04.2 to .3 as dependencies; exact apt history
is preserved in `guest-encoder-audit.json`. No firmware/ISO/kernel/display-service
configuration changes were made, and the existing desktop/raw bridge stayed up.
This diagnostic package addition is NOT read-only; only the audit script is.

Definitive output, independently reproduced with ctypes/libva:

```
VA-API version: 1.20
vaInitialize = 0
Mesa Gallium driver 25.2.8-0ubuntu0.24.04.3
for virgl (Mali-G925-Immortalis MC12)
Supported profile and entrypoints:
  VAProfileNone : VAEntrypointVideoProc
```

No AVC/HEVC profile or encoding entrypoint is advertised. Therefore adding
ffmpeg/x264 would supply SOFTWARE encode, not unlock the Android encoder.
Source: `tools/linux-gnome/diagnostics/encoded-display-audit.py`; full report
`guest-encoder-audit.json`.

Exact crosvm launch (`crosvm-args.txt`) uses
`backend=virglrenderer,context-types=virgl2,egl=true,gles=true,surfaceless=true`.
There is no `--video-encoder` or video-device argument. Fresh framework jar was
pulled from this device; complete custom-image Builder inventory contains GPU,
display, audio, input, network, disk, USB and shared-path configuration but no
video encoder/device selector. See `custom-config-builder-methods.txt` and
`framework-class-inventory.txt`. This is evidence about this shipped contract,
not all upstream crosvm versions or future Samsung firmware.

Upstream [crosvm video support](https://doc.crosvm.dev/book/devices/video.html)
requires a video-encoder build feature and explicit virtual video device/backend.
It is not enabled simply by selecting VirGL; an upstream flag is not an
app-accessible API on this device.

## Decoder and proposed framing

[Android MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
accepts a real `Surface` in decoder `configure`, avoiding Bitmap conversion.
Use a separate `SurfaceView` for an encoded proof: the existing
`FrameSurfaceView` is a custom View, despite its name. Do NOT replace the working
WAVF receiver until a bounded proof succeeds. Surface decode is documented but
has not been exercised by this audit. Low-latency support and actual Surface
rendered rate/latency must be measured, not inferred from codec initialization.

Proposed isolated port 4055 (verify available before use), UVF1 framed byte stream:
- fixed 40-byte little-endian header: magic[4], version u16, type u16,
  codec u32 (1 AVC / 2 HEVC), generation u32, sequence u32,
  width u16, height u16, PTS-us u64, payload-bytes u32, flags u32;
- CONFIG records carry Annex-B SPS/PPS (and VPS for HEVC); FRAME is exactly one
  complete Annex-B access unit in decode order; EOS terminates a generation;
- bounded payload <=4 MiB, dimensions <=4096, total decoded size bounded;
  reject malformed/truncated records and never mix with WAVF/4052;
- disable B-frames/lookahead where supported, periodic IDR and explicit IDR
  request/reconfigure after reconnect; retain codec config for every generation;
- bounded input queue, drop/coalesce raw samples BEFORE encoding. Unlike raw
  independent keyframes, arbitrary encoded P-frame dropping breaks references:
  on backlog overflow reset/request IDR, discard dependent frames until IDR;
- record capture/encode/send/decode/render timestamps and queue depth. Guest
  and host monotonic clocks require offset calibration for end-to-end latency.
  Codec rendered callbacks may be delayed/batched, not an instantaneous fence.

## Best next option

Hardware encode on Android AFTER vsock leaves the expensive raw capture/transfer
in place and adds encode/decode overhead; it is not a demonstrated fix.
Hardware encode BEFORE vsock needs a guest video device or exported host render
target, neither available in the audited current contract. Do not patch Android,
invent AVF flags, or rebuild the proven firmware to pursue it.

One bounded native CPU H.264 test (x264 ultrafast/zerolatency, NEON, no B-frames)
is a credible separate feasibility option: measure 1920x1200 RGB->YUV+encode on
actual desktop frames, contention with GNOME, and latency before building a
vsock/MediaCodec UI. It is NOT yet available/measured here. If it cannot sustain
the budget, stop; do not promise 60 FPS. Capture itself has shown ~54 FPS on one
controlled workload, so an encoder also needs a faster sustainable source to
reach 60. A host-accessible direct scanout/Surface remains a separate privileged
API question, not solved by Android codec availability.

Production raw fallback, installed userdata, P7, official ISO, GOLDEN and Windows
baseline preserved. No APK replacement or Git publication in this audit.
# Mandatory no-parallel-raw test — completed 2026-10-01

Same native Mutter RecordArea -> PipeWire BGRx -> videoconvert -> x264enc
ultrafast/zerolatency -> UVF1/4055 -> MediaTek AVC -> Surface path. No codec,
firmware, kernel, ISO, installed-userdata or architecture change. Diagnostic
APK SHA256 `54CFB0E1A379687B2A8C6F85D8675B80AD910AC934CA118E1F52E8D003D5E9FF`.

Android raw client closed; production `winavf-frame.socket` and its exact active
instance stopped for the test, because the existing bridge retries after EPIPE
and can otherwise reconnect RFB. Guest `ss` confirms ZERO established :5901
connections. Socket and normal display restored automatically after the test.

One common 20s interval, starting 5s after capture setup; ordinary existing GNOME
terminal moves with 1500 WM movement requests over 25s. Diagnostic round-trip
clock alignment uncertainty +/-0.1692ms. Counts filtered by stage event time,
not unrelated per-second averages or refresh rate:

| Stage | FPS |
| --- | ---: |
| Source/capture pad | 28.65 |
| Encoded access units | 28.65 |
| Sent access units | 28.65 |
| Android received | 28.65 |
| MediaCodec output/decode | 28.65 |
| Actual distinct SurfaceFlinger present timestamps | 28.10 |

Capture->send mean 11.5965ms; P95 15.0240ms. Zero pre-encode coalescing
over the full run. All 758 total received frames decoded; no accumulating
decoder queue (normally 0–1 pending).

Actual Surface metric uses column TWO of the selected BLAST SurfaceView's
`dumpsys SurfaceFlinger --latency` records, sampled repeatedly to avoid the
128-record ring overwriting evidence, deduplicated and filtered to the same
monotonic window. 568 distinct frame/present records correspond to 562 distinct
display instants: some records share a present instant. Report conservative
28.10 visible update FPS, not 28.40 queued frame-record FPS.
The fixed-window codec rendered callback counter is INVALID on this device
(zero while SF records presentations); it is not used to assert visible FPS.
Likewise receive-to-render zeros from that callback are NOT a latency result.

Verdict: source BELOW 50. Removing raw/RFB does not establish >=50 sustained
FPS. There is no large downstream decoder/present loss capable of explaining
the missing ~20+ FPS. Source intervals fluctuate 3–54 FPS despite regular
movement requests. This identifies the next boundary, NOT a proven Mutter bug:
inspect PipeWire negotiated cadence, arrival gaps, buffer retention/backpressure
and actual compositor movement/damage. Do not retune encode/send while they
remain ~11–15ms, and do not return to raw optimization.

Evidence (all under `D:\UAVF-builds\encoded-display-20261001`):
`surface-fixed-guest.json`, `surface-fixed-android.txt`, `surface-fixed-sf.json`,
`surface-fixed-summary.json`. Reproducible summary parser:
`tools/linux-gnome/diagnostics/encoded-fixed-summary.py`.

## 2026-10-02: native Xorg capture and presentation cadence

Same H.264/vsock/MediaCodec architecture and 1920x1200, zero competing RFB
clients during each bounded test. Installed userdata/ISO/P7 unchanged.

| Test | Source FPS | Decode FPS | Distinct actual present FPS |
| --- | ---: | ---: | ---: |
| RecordArea quality control | 53.70 | 53.75 | 50.15 |
| Temporary RecordMonitor90 | 52.65 | 52.65 | 49.90 |
| Pause background scraper | 50.45 | 50.45 | 48.10 |
| Native ximagesrc, Android Surface60 | 59.80 | 59.85 | 52.30 |
| Native ximagesrc, Android Surface120 | 61.30 | 61.35 | 60.05 |

All are one common 20-second interval, not idle peaks. The last optional window
mode produced an 8.333ms SF period; no global Android display setting changed.
Capture-to-send mean 10.574ms / p95 13.538ms; decoder pending usually 0–1.
Normal raw display is restored automatically after EOS/error; its fallback is
not replaced by this experimental path.

**Not yet a unique-content 60 FPS result.** Conservative CRC sampling of 24
full-width desktop rows detected changed pixels at 44.75 FPS. Unchanged samples
do not prove the entire frame is unchanged, but repeated polling buffers must
not be presented as proof of new GNOME frames. Actual queried ordinary-window
positions changed about 51/sec. Next boundary is source/compositor sampling,
not vsock bandwidth or decoder queue. The Surface120 result removes the
previous downstream coalescing bottleneck only.

Current GLX screencast DMA-BUF negotiation failed for both DMA_DRM and legacy
BGRx forced formats; no zero-copy claim. Do not integrate the ineffective
RecordMonitor90 or paused-scraper experiments.

Evidence: D:\UAVF-builds\encoded-display-20261002, prefixes quality50,
monitor90, pause-rfb, ximage, display120. Installed diagnostic APK display120:
CAEB605FE2ED42D7563DC6554904DD1EEE81286B5C04162F35BB7845A05431FB.

Next prepared diagnostic: same native source with --source-fps=120 plus sampled
change verification. Its guest fetch/start command was rejected by tool policy
before execution. It is NOT tested, no result may be inferred. Do not bypass
that rejection. Last completed test rolled back normally; Ubuntu remains live.

