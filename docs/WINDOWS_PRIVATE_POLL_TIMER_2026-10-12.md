# Private per-helper wait experiment

One isolated source-side change: measured Sleep(1) replaced by reused
CreateWaitableTimerExW(HIGH_RESOLUTION), relative1ms/bounded wait. Same
async two-slot readback,8CPU/native1200p/platform/x264/UVF1/Android path.
No timeBeginPeriod/global policy or firmware/CPU/GIC changes.

Runtime encoder rawCB1DCA3016B852844E67437CD04D5E3A04824A61754CDFC403ADCC801B74350E,
79872B slot6EF28A056C1F41A244CE84C96345A26EF86357C905DCBA5D66A7ACF524D73777.
Artifacts: build-logs/windows-x264-20261012/runtime-private-poll-timer and
build-logs/windows-post-linux-reassessment-20261004/kd-windows-direct-x264-20261012-private-poll-timer.

## Actual result

Desktop helper timer active1/error0 for all30s workload. At29.938s:
1180 successful high-resolution waits, mean2.284ms/max18.940ms, total2695ms.
This does reduce the measured14.271ms ordinary Sleep wake cost.

Actual Android434 new present fences /20.108406s =21.533283FPS; not40-45/60.
Android ready-to-present27.162ms, NOT whole capture-to-Surface latency.
Warm capture23.6,26.321,26.116,24.920FPS. Conversion~7ms/encode~8-9ms;
source paint later~62/s remains activity, not60 presented-image proof.
No queue growth or async copy errors. Ready age40.226ms over622 ready images
at29.938s. Copy+Flush submission~96ms total and Map~14ms total over the run,
so neither proves where all remaining wall time goes.

Winlogon helper had one WAIT_TIMEOUT(258) after3551 successful waits, and
the first candidate permanently disabled its private timer. Desktop helper
is a new process, so its timer remained active and its FPS result is valid
for that case. Do not conclude timer availability from the old Winlogon state.

No0x101 bugcheck in this run; observer terminal after intentional UI stop.
GOP/socket/encoder/separate codec cold rollback/full readbacks all PASS,
VM list empty. No production default change, no commit/push.

## Next precise source boundary

ReleaseFrame and source/resource COM Release had not been individually
timed. Work done on calls that return NOT_READY is not all represented by
the successful-frame capture_convert metric. Obtain these missing totals
before blaming DWM or trying another capture API/codec.

Prepared opt-in phase candidate:
build-logs/windows-dxgi-release-phase-verified-20261012,
rawE8FAAC18A4F9CF618B35C36EBD0B3597E6343BA859640EB7989B538609B5BE62.
Adds ReleaseFrame wall/CPU, resource-release and setup timings. Also fixes
the observed timer fallback weakness: transient WAIT_TIMEOUT cancels/rearms
the same valid handle; unsupported/invalid handle still closes and falls back.
Host actual timer reuse/invalid-type fallback and injected transient timeout
retry/handle-retention checks PASS; no guest FPS claim for this candidate yet.
