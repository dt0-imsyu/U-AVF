# DXGI cadence / waiting audit

One unchanged native1920x1200,8CPU, serial DXGI/x264 run. No platform,
CPU, signed driver, production codec or Ubuntu changes. The optional native
full-repaint window closed itself after30s; VM was stopped through its own UI
after collection. Early observer disconnect is intentional, not a guest hang.
GOP, socket helper, encoder and separate codec cold rollback/full readbacks
all PASS. VM list empty. No commit/push or production-default change.

Evidence: `build-logs/windows-x264-20261012/runtime-cadence-audit` and
`build-logs/windows-post-linux-reassessment-20261004/kd-windows-direct-x264-20261012-cadence-audit`.
Encoder raw SHA256 `2F47494F4AF6E60AD9CE558D8C185C9E38939F4BA3B18BAEEFF50F64B37365EB`;
padded existing slot `3A0D2EB206E7D1BBDD5ECEFDA271E75CC5199E8FFE6A88714D35C5F7F8B63848`.
Strict ARM64 compile, packet/color tests and actual mock DXGI ownership/audit
counter tests passed. Host mocks are not performance evidence.

## Measurements

Warm intervals from workload elapsed10.047 through25.094s (four approximately
5s intervals, excluding first startup interval):

| Layer | Result |
|---|---|
| Native WM_PAINT callbacks | approximately61-63/s; not presentation proof |
| DXGI accumulated desktop updates | 922/20.063s =45.95/s |
| Unique nonzero LastPresentTime acquired | 556/20.063s =27.71/s |
| Updates accumulated beyond acquired frames | 366; no repeated/backwards timestamps or cursor-only successes |
| Encoded frames | same as capture; interval26.0-28.7/s |
| Android actual present fences | 519 new fences;25.7978FPS over20.079s |
| Android ready-to-present | mean38.315ms; not end-to-end capture latency |

Across those four warm intervals: MapDesktopSurface wall8757ms versus capture
thread CPU15.625ms, or15.75ms wall per acquired image. AcquireNextFrame
wall5503ms versus thread CPU109ms (includes calls ending in timeout).
Conversion approximately4.6-4.9ms, x2645.3-6.1ms, pipe write0.07-0.09ms.
GetThreadTimes is coarse; these totals classify capture-thread waiting, not
all OS/DWM/driver CPU cost or the exact kernel wait reason.

Texture:1920x1200, D3D11_USAGE_DEFAULT, CPUAccessFlags=0,
BindFlags=0x28, MiscFlags=0x2900. Returned map is PAGE_READWRITE/MEM_MAPPED.
This does NOT establish cache attributes, physical memory backing or zero-copy.

DwmGetCompositionTimingInfo returned S_OK. Advertised refresh/compose ratio
13000000/203125 =64Hz, but displayed/complete counts remained0 and cFrame
incremented once per diagnostic call. These counters do not establish actual
compositor FPS on this Basic Display path. Do not report64FPS as rendered FPS.

## Conclusion and next minimal test

Not a demonstrated30Hz source cap: Desktop Duplication reports approximately46
accumulated desktop updates/s while only approximately28 are acquired.
Substantial time is blocked inside MapDesktopSurface, rather than CPU time in
the capture thread. Source60 unique presented FPS remains UNPROVEN.

The next evidence-backed A/B is only the readback mechanism: acquired DXGI
texture -> reusable CPU-readable D3D11 staging texture -> CopyResource/Map,
versus current MapDesktopSurface. Keep DXGI ownership, conversion, x264,
vsock, Android and platform identical. Measure copy/Map wall+CPU separately;
this is a hypothesis, not a promised fix. No GDI/OpenH264 or display/platform
flag sweep. Do not deploy staging readback to production without actual proof.

References: Microsoft DXGI_OUTDUPL_FRAME_INFO documents nonzero LastPresentTime
and AccumulatedFrames; DWM_TIMING_INFO separates refresh rate from displayed
frames. Current run therefore rejects paint/refresh counters as FPS evidence.
