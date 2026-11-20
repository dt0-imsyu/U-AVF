# U-AVF Full-HD frame-rate work

Updated: 2026-09-29

## Goal and current baseline

- User reports the installed Ubuntu desktop feels close to 20 FPS during ordinary use.
- The installed profile is already 1920×1080. Keep that resolution for the first performance milestone; raising it increases pixels per frame and is not a route to 40+ FPS.
- Target order: stable 40+ genuinely updated/presented frames per second at 1920×1080, then evaluate a 60 FPS mode. A 60 Hz display or repeated unchanged frames do not count as 60 FPS.
- Existing measurement points: guest `FRAME_STATS capture_fps/packet_fps/compute_ms/write_ms`; Android `PERF decoded_fps/prepared_fps/drawn_fps/prepare_ms/draw_call_ms`.
- Most recent retained Android report includes an active-motion interval around 11.5 decoded/drawn FPS, while idle intervals are near zero (expected for damage-only RFB). This is not a controlled current 20-FPS measurement and must not be presented as one.

## Changes prepared in this iteration

- Android WAVF parser now accumulates fragmented records in a bounded growable buffer instead of materializing/copying its entire pending stream on every socket read.
- Delta tiles are parsed from the packet buffer without an intermediate copy. The listener receives the decoder's shared frame buffer synchronously; the normal app path copies it directly into a synchronized Bitmap before the decoder mutates it again.
- Small delta updates now copy only their dirty rectangles into the existing Android Bitmap. Keyframes, large-area deltas, and oversized rectangle lists use the full-frame path.
- Android presentation copies native little-endian BGRA bytes directly into ARGB_8888 Bitmap storage rather than converting every pixel in Java and copying it again.
- Removed routine full-frame diagnostic archive writes from the normal frame-bridge path; diagnostics report the archive as disabled. Existing user files are not deleted.
- Guest capture source requests up to 60 updates/s, clamped to 30–60, and avoids rebuilding the 1080p backing frame for every incremental RFB update. This source change is not yet proven on the currently installed root filesystem.

## Validation and next gate

- Host decoder tests pass for noisy serial + keyframe + delta, and for fragmented 1080p keyframes including an 8.3 MB high-entropy payload.
- Guest bridge Python syntax validation passes.
- A separate signed APK candidate has been built (final iteration SHA-256: `EFFCEB56D9737168114F2A98F7A5B697B68A769A0CD943AA14EB1BDD79A53249`); install/runtime acceptance is pending. Do not interrupt an active VM to install it.
- Before claiming a gain, run one purposeful installed-system motion test after the candidate is active, and correlate at least three 10-second windows of guest packet FPS with Android decoded/prepared/drawn FPS. Use a continuously moving desktop/window pattern; static GNOME is intentionally damage-silent.
- Stop at stable 40+ presented FPS for the first milestone. Attempt 60 only if both guest packet production and Android presentation maintain headroom without freezes, thermal collapse, or input lag.
- The current installed guest bridge may still be from runtime v4 and may retain its earlier 30-FPS cap. A supported, non-destructive way to update the already-installed guest bridge is still required; do not reinstall/format vda3 merely to test FPS.

## Guardrails

- Keep the proven P7 platform, GOLDEN, official Ubuntu ISO, persistent user data, and installed root partition unchanged.
- Do not interpret requested capture FPS as actual output FPS.
- Preserve damage-based frame delivery; do not waste CPU fabricating 60 copies of a static desktop.

## Direct XDamage window-tree diagnostic — 2026-09-29

- A disposable Live run used the unchanged P7 platform prefix, Xvnc at 1920×1080, and direct XDamage capture on the root plus mapped top-level windows. The guest ran a continuously moving small window over the GNOME desktop; this is a pipeline/load proof, not a full-screen application benchmark.
- The motion source sustained about 118–120 FPS. After warmup, guest capture/packet windows were mostly 84–115 FPS; `capture_ms` was about 1.75–5.52 ms and `write_ms` about 0.05–0.14 ms. Dirty updates covered roughly 0.7–2.3% of the screen. MIT-SHM was unavailable on this Xvnc path, so dirty regions used XGetImage.
- Android received non-black frames continuously. Across the sustained report windows, decoded rate was 93.6–114.7 FPS and actually drawn rate was 80.3–109.2 FPS (mean about 96 FPS after warmup). This clears the 40-FPS pipeline target for sparse window motion, but does not establish 60-FPS behavior for full-screen/high-damage workloads or subjective latency under ordinary GNOME interaction.
- Runtime evidence: `build-logs/xdamage-120/runtime-xdamage-window-tree-serial-final.log` (SHA-256 `06E6FE1B3067020B101194F48C896FBB4E43A48BB05889A209F6EA3AA95A7E20`) and `build-logs/xdamage-120/runtime-xdamage-window-tree-android-report-final.txt` (SHA-256 `EF6C39BB3CC805FC191BABF34238FBE7BB8D9E847ECDB2EC52335BF904729704`). Diagnostic VM was stopped after collection.
- Next performance gate: validate visually and with a genuinely changing full GNOME workload (not just the small moving test window), then choose a production cap based on sustained drawn FPS and interaction latency. No additional runtime was started in this session.

## Real GNOME XDamage source check — 2026-09-29

- Two bounded disposable-Live iterations were used; no persistent installation, P7 firmware/kernel, or official ISO was changed. The first watched the root and mapped window tree with synthetic motion disabled. It showed a fast idle capture loop (~119 polls/s, ~0.3–0.8 ms capture) but only ~0.2–1.2 packets/s on ordinary GNOME state changes. This is not a transport-capacity failure: the capture source was not announcing the compositor's actual repaint damage.
- The second captured the XComposite overlay drawable directly (the composited output source), again with synthetic motion disabled. GNOME Activities/app grid and a real Network Connections window were visible; guest input opened the app and moved the window, and scroll input was accepted. Thus this was a real GNOME tree/workload, not the small benchmark drawable.
- Overlay evidence: `build-logs/xdamage-120/runtime-composite-overlay-real-gnome-serial.log` (SHA-256 `C03365BDBC8FDD3F239D030A5DFA4B8D2A50A75E231B47BF7400D0016A286274`), `runtime-composite-overlay-android-report.txt` (SHA-256 `827210F2F5B494E4604F2952AC3B2A56655C9D76FD38FE7B34A256513A29FAAF`), and `runtime-composite-overlay-launch-report.txt` (SHA-256 `2D2A15DBF70CDC79442AD3F54A4A242703901E110A1F38613B0846404E670743`). Visual captures: `runtime-composite-overlay-appgrid.png`, `runtime-composite-overlay-settings.png`, and `runtime-composite-overlay-window-drag.png` in the same directory.
- The bridge positively identified `CAPTURE_BACKEND=X11_DAMAGE_COMPOSITE_OVERLAY` and `X11_DAMAGE_DRAWABLE=COMPOSITE_OVERLAY`. During ordinary window movement/scroll the guest loop remained ~119 polls/s with ~0.23–0.98 ms capture, but it emitted no sustained damage updates; `FRAME_STATS packet_fps` was typically `0.20`, briefly rising to `1.20` around transitions. Android `decoded_fps/prepared_fps/drawn_fps` tracked the same sparse arrival rate (typically `0.20–0.30`, brief transition windows up to about `1.2`). Dropped-frame count and end-to-end latency are not meaningful at this event-starved rate and were not inferred. One real window drag and scrolling were exercised; a separate maximize/fullscreen stress run was not warranted after ordinary compositor damage already failed at the source.
- Conclusion: the ~96-FPS small-drawable result proves the direct WAVF/4052/Android path has headroom; it does not prove GNOME source notifications. The current measured bottleneck is the XDamage notification path: Mutter/VirGL repaints are not surfacing as useful XDamage events on the selected overlay. Capture itself is cheap; packet production is starved before send, and Android presentation simply follows arrivals. Do not optimize RFB or rewrite WAVF/Android.
- The two requested iterations are exhausted without ≥60 sustained actual presented FPS on real GNOME. Keep the direct transport and current best source implementation. The minimal next fix is to obtain damage from Mutter/Xorg's GL present/redirected-surface path (or another compositor-provided damage source) and feed those rectangles into the existing queue-depth-1/latest-frame-wins WAVF sender. Only if that source is unavailable should a narrowly scoped, rate-limited tile-change sampler be considered; avoid full-frame compare/copy on every 120-Hz poll. No third experiment was run. Current tablet APK is a diagnostic overlay-source build, not a release candidate; diagnostic VM was force-stopped.
