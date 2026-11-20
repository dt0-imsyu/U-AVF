# Isolated D3D11 staging readback A/B

Result: no FPS gain; NOT a production fix. Native1920x1200, same8CPU,
platform/workarounds, x264, UVF1, app-owned AVF and Android MediaCodec/Surface.
Only readback changed from MapDesktopSurface to acquired texture -> reusable
D3D11_USAGE_STAGING/CPU_ACCESS_READ texture -> CopyResource -> Map(READ).
Default path unchanged; opt-in UAVF_DXGI_STAGING_READBACK only.

Strict ARM64 compile/packet/color tests PASS. Mock real vtables exercise
descriptor sanitization, texture reuse, acquire timeout, map failure,
resource release and unmapped/held cleanup. Host execution PASS; ARM64 test
compiled, not executed (not claimed as actual device performance).

Artifacts:
- build-logs/windows-dxgi-staging-build-20261012
- build-logs/windows-x264-20261012/encoder-staging-prepared
- build-logs/windows-x264-20261012/runtime-staging-readback
- build-logs/windows-post-linux-reassessment-20261004/kd-windows-direct-x264-20261012-staging-readback

Raw encoder SHA2569B4464D43E97F6905BA621E1052478C50AD355AE5D1768949D5A9D7130CB63DA;
existing79872B slot F0F904452AD5DB851CD81DE4947DF362414DA8E426D0315723192C7DB3D72800.

Actual Android SurfaceFlinger:466 new fences over20.10007s =23.13424FPS,
versus prior original-readback audit25.79778FPS. Ready-to-present35.475ms,
not end-to-end capture latency. Native full-repaint screen visible and saved;
WM_PAINT callbacks remain source activity, not compositor presentation proof.

Warm captures/encoded intervals:29.6,28.315,22.655,19.8FPS. CopyResource
submission roughly0.02-0.04ms/image; Map completion still15-20ms/image.
The short CopyResource call is asynchronous SUBMISSION, not completed transfer.
Conversion5.96-10.04ms, encoder8.47-15.61ms varied during this run; therefore
do not attribute the entire FPS difference uniquely to one readback API.
No CAPTURE_FALLBACK occurred; this was staging readback, not GDI.

Post-run host thermal status0, AP49.9C; this does not prove absence of CPU
frequency changes during the workload. No thermal/CPU policy modifications.

Conclusion: swapping the blocking readback API does not remove the completion
wait. Keep original DXGI as best proven source. A next focused hypothesis is
bounded asynchronous staging: two reusable pending copies, Map DO_NOT_WAIT,
explicit readiness and latest-frame policy, measuring completed rather than
submitted copies. No claim this is implemented or will reach60FPS. No new
codec/platform/CPU flags, raw tuning or GDI repetition.

VM stopped through its own UI after workload collection; observer disconnect
intentional. GOP/socket/encoder/separate codec rollback plus complete cold
readbacks all PASS. VM list empty. No commit/push or production-default change.
