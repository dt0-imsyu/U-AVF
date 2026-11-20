# Frame-based cap correction (private R&D)

Protected platform/codec/transport/Android remain unchanged. This is opt-in,
not a production default and not a 60 FPS claim.

## Evidence

Release-phase control produced 461 actual Android present fences in
20.037582386 seconds: 22.956861319 FPS. ReleaseFrame took only 33.110 ms
TOTAL for 551 calls; COM release 0.850 ms and setup 4.085 ms. These are not
the missing per-frame 15–20 ms.

The legacy loop advances `n` on every acquisition attempt, including
NOT_READY/timeout, then sleeps to `(n+1)/fps`. Its cap therefore limits
attempts, not delivered frames. Control 951 polling pauses plus 550 frames
over 24.984 seconds is approximately 60 attempts/s.

Opt-in `UAVF_FRAME_PACING_FIX` advances only on successfully written video
access units. Deadline uses QPC; long idle cannot create a catch-up burst.
Video sequence numbers are contiguous actual deliveries. Polling cannot
consume the frame budget. Codec, UVF1 layout, Surface decoder and cap60
otherwise unchanged. Host tests prove idle/poll independence, 60/120 rate
thresholds, backward-counter handling, staging ownership and packet order.

First device run reached Desktop and the bounded full repaint workload:
warm capture 22.528–24.2 FPS, conversion about8–9 ms, encode11–13 ms.
At25.016s:574 ready,4047 NOT_READY, errors0, pending0, ready age29.583ms.
Actual Surface fences were NOT sampled; do not infer presented FPS from
callbacks or paint count. The pacing bug is real, but fixing it alone did
not demonstrate the desired performance gain.

## Repeat / admission failure

Exact repeat `frame-pacing-present` was solely for the missing Surface
sample. It failed BEFORE Windows/encoder execution. Saved UART shows
`SECOND_STAGE_EDK2: handoff entry=90000000`, then U-Boot synchronous abort
ESR02000000, relocated ELR13f7e5224, instruction bytes zero, CPU reset.
This is not an encoder/readback FPS result or evidence of a graphics hang.
Do not change CPU/firmware/GIC/timer flags based on this single event.
Measurement waiter was cancelled, not reused against stale report data.

## Artifacts / safety

Build: `build-logs/windows-frame-pacing-fix-build-20261012`.
Raw encoder SHA256:
`60D9E82A26D0E7576389453C5ADC8214458329768D6C49372B092A77D7BE1701`.
Padded existing79872-byte slot SHA256:
`4B678FA0347E0F6AFCD9B7AEA67922B1916F99AE5381AEE7E352E0052104754E`.
First runtime: `kd-windows-direct-x264-20261012-frame-pacing-fix` under
`build-logs/windows-post-linux-reassessment-20261004`; four original cold
rollback/readbacks PASS, VM list empty.
Repeat rollback must be verified terminal before another boot/disk write.

Next useful source work is actual readback-ready cadence and whether
readback can progress while conversion/encoding runs, not another codec,
Android decoder or platform sweep. Previously tested overlap used the OLD
blocking capture/cap contract, so it does not prove the revised combination
fast or slow. Any future combination needs explicit cross-thread resource
ownership, bounded latest-frame queue and actual Android present fences.
