# Windows live display preparation — 2026-10-08

Status: COMPILED ARM64 CANDIDATE, NOT runtime PASS. No new VM run, APK install,
disk/WIM mutation or device staging. Await explicit direction before reopening
the user-closed CPU/PMU diagnosis; no smss patch.

Existing shared transport preserved: Windows GDI -> OpenH264 AVC -> UVF1/AVF
vsock4055 -> existing Android EncodedDisplayProbe/MediaCodec/Surface. UIN1 input
unchanged. No new Android decoder, shell AF_VSOCK, Binder byte proxy or security
bypass. Raw4052/replay path is not being substituted for the target live stream.

## Prepared corrections

- Live encoder no longer stops after900 captures (~60 seconds). Its lifetime
  follows pipe disconnect, source failure or display-size change.
- Live server no longer exits after two clients or its45-second diagnostic
  accept watchdog. Bounded replay behavior is unchanged.
- New server process creates a unique LIVE-SERVER directory, preserving prior
  diagnostics. Reconnection uses a fresh encoder/stream generation.
- Encoder output handles partial pipe writes. Both source/server compile-time
  assertions enforce the unchanged40-byte UVF1 header.
- Clock/end headers use current Windows desktop dimensions. Encoder detects
  resolution changes and exits with code10 for supervisor reconnection.
- On disconnect supervisor closes the source pipe; if its own encoder child
  does not exit within5 seconds, it stops only that exact child process handle.
  Setup/other Windows processes/VM are never terminated here.
- Final live metrics report actual capture count/averages instead of hardcoded
  captured30 and900-frame denominators.

Setup-oriented live cap remains15 FPS. No60FPS/performance claim; first goal is
usable stable Setup. Actual throughput/latency will require Windows runtime.

## Artifacts

`build-logs/windows-post-linux-reassessment-20261004/live-runtime-prepared-20261008/`:

- `winpe-encode-live.exe`
- `winpe-vsock-live-current.exe` (current; direct accept compile option enabled)
- `prepared-manifest.json` (artifact/source/dependency SHA, prepared-only status)

Compiler: existing LLVM-MinGW aarch64 target, O2/static CRT/Wall/Wextra.
Both PE headers verified IMAGE_FILE_MACHINE_ARM64/64bit. Existing OpenH2642.5.1
DLL provenance unchanged. Earlier `winpe-vsock-live.exe` in this same preparation
directory is preserved intermediate output; use manifest's current artifact.

## Remaining before integration

1. App-owned WinPE must reliably reach its guest producer.
2. Bootstrap must invoke the prepared producer with `--live` and include the
   current encoder binary; current proven image intentionally stays replay-only.
3. Prove continuous changing real Setup pixels, >60seconds lifetime and at least
   three reconnects on tablet through existing MediaCodec Surface.
4. Check keyboard/mouse/basic touch and any real resolution transition; current
   diagnostic Activity input/readback still assumes640x480, so dynamic source
   headers alone are NOT a complete Android resolution-reconnect PASS.
5. Then integrate into isolated Windows installation/runtime, not Ubuntu.

No claim of first app-owned frame or installed Windows is made.
