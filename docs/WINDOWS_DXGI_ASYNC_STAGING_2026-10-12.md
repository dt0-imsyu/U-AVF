# Two-slot asynchronous DXGI readback

One opt-in A/B, same native1920x1200/8CPU/platform/x264/UVF1/app-owned
AVF/MediaCodec/Surface. No Ubuntu, ISO, driver, CPU or memory-policy changes.
No production default change. No40-45 or60FPS PASS.

Implemented exactly two reusable staging textures, queued CopyResource+Flush,
Map(READ, DO_NOT_WAIT), explicit pending/mapped ownership, zero-timeout
AcquireNextFrame and bounded Sleep(1) polling. No overwrite while pending
or mapped, no unbounded queue. Frame metadata remains attached to each copy.
Flush is submission, not completion; only successful Map increments ready.

Strict ARM64 build and actual host packet/color tests PASS. Actual mock
vtables prove two-slot limit/no overwrite, WAS_STILL_DRAWING, ready-after-
timeout, mapped ownership guard, error cleanup. ARM64 mock compiled only;
actual device performance evidence is separate below.

Raw binary B5C63891884D50A0EB26EE614914BDF7DEEA097AEFEAF96CFEE12EE74ADA7463;
79872B slot4AC72BE8EE94319997CFD8771E88938D45A6D89918B2104A162C3779CEDD97CD.
Artifacts: build-logs/windows-dxgi-async-staging-build2-20261012,
build-logs/windows-x264-20261012/runtime-async-staging,
build-logs/windows-post-linux-reassessment-20261004/kd-windows-direct-x264-20261012-async-staging.

## Result

Android SurfaceFlinger502 new present fences over20.118813s =24.902065FPS.
Android ready-to-present22.578ms, not end-to-end latency.
Warm capture/encode24.4,26.154,28.116,28.6FPS; no40-45 sustained result.
Paint source warm~62/s, still NOT60 unique presented-image proof.

At workload25.078s:649 submitted/ready,686 not-ready responses, errors0,
pending0; earlier interval pending1. No accumulated queue growth observed.
Mean LastPresentTime-to-ready age35.275ms over those649 images.
Map wall TOTAL11.997ms across the entire25s workload; blocking15-20ms/call
is avoided. Copy/Flush submission TOTAL75.567ms. Conversion6.6-7ms and
encode~7-9ms in warm intervals. Acquire calls nonblocking as well.

Important: avoiding the blocking API did not turn into a substantial FPS gain.
The next precise boundary is readiness/poll scheduling. Sleep(1) actual wake
duration was NOT measured; it must not be assumed1ms. Measure polling idle
wall time and individual wake durations before attributing this to DWM or
trying a dedicated high-resolution wait. No global timeBeginPeriod or platform
timer/CPU flags. This does not prove a hard BasicDisplay30FPS ceiling.

Captured screenshot shows a full-frame color surface; no claim that text/
desktop visual fidelity and resource reuse across display changes are fully
production-tested. Retain original DXGI as stable source; asynchronous path
remains R&D only. VM stopped through own UI after collection; observer
disconnect intentional. All four GOP/socket/encoder/separate codec cold
rollback/full readbacks PASS; VM list empty. No commit/push.

Next read-only timing instrumentation compiled/selftested separately:
build-logs/windows-dxgi-async-poll-audit-build-20261012, raw binary
94919319CCBE7F95CFFD94E1D2A67EC9FFA9A04662409CD070BAAB8154B5BCA8.
It records actual Sleep(1) wall mean/max/total without changing waiting policy.
NOT deployed or measured yet; runner still pins the tested async binary above.
