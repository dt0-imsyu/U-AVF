# Actual polling wait audit and interrupted recovery

Opt-in async/DO_NOT_WAIT control plus QPC timing around unchanged Sleep(1).
No wait-policy, codec, platform, CPU, timer/GIC or Android changes in this run.
Encoder raw94919319CCBE7F95CFFD94E1D2A67EC9FFA9A04662409CD070BAAB8154B5BCA8;
79872B slotA9CD4DC89BCE96746E130CA71D950DDFAB7132F265FD1E83C247807537CB64A4.

Evidence: build-logs/windows-x264-20261012/runtime-async-poll-audit and
build-logs/windows-post-linux-reassessment-20261004/kd-windows-direct-x264-20261012-async-poll-audit.

Guest Winlogon measurement:918 waits,13100.978ms total,14.271ms mean,
36.019ms max, versus requested1ms. Another earlier interval730 waits mean
14.629ms. This proves coarse actual helper wake cadence in this session,
not the exact kernel/hypervisor reason. Not an active Desktop FPS proof.

All8 startup complete: ke_number_processors8, ki_barrier_wait0 at28899.080.
At29105.334 Windows reported bugcheck0x101 CLOCK_WATCHDOG_TIMEOUT,
parameters(0x18,0,0xfffff802dc2f0980,0), before Default/native test window.
No native full-repaint or sustained Android present result for this run;
sampler correctly failed rather than reporting a synthetic PASS.
Do not attribute watchdog causality to polling or a platform flag without
additional evidence. No CPU/GIC/timer random mutations in response.

## USB interruption / verified recovery

Normal wrapper stopped VM and restored GOP/socket/encoder with readbacks.
USB disappeared during112-block separate-codec rollback. Do not boot a
partially recovered FAT allocation. After reconnect, exact original/candidate
classification found48 original blocks,64 candidate blocks,0 unknown.

New narrowly scoped recover-eight-thread-codec.py admits only the fixed x264
plan/112 blocks, original GPT/boot/EFI, cold isolated disk, complete known
preimages; unknown/torn/user-changed blocks reject writes. Dry-run first.
Actual-plan225 host recovery/rejection tests PASS. Resumed only64 remaining
blocks; final ALL112_ORIGINAL_READBACK_PASS, UAVF8.DLL absent. All four
components original/readback verified; VM list empty. No production defaults,
signed Windows files, NTFS user data or Ubuntu changes.

## Evidence-backed next A/B

Private per-helper CreateWaitableTimerExW(HIGH_RESOLUTION), relative1ms,
bounded wait, one reused handle; safe Sleep fallback on unsupported/failure.
No timeBeginPeriod/global timer-policy changes, no VM timer/CPU flags.
Candidate rawCB1DCA3016B852844E67437CD04D5E3A04824A61754CDFC403ADCC801B74350E,
slot6EF28A056C1F41A244CE84C96345A26EF86357C905DCBA5D66A7ACF524D73777.
Strict ARM64 packet/color and actual host timer reuse/close/fallback/queue
tests PASS. Guest actual wake/FPS and watchdog regression not yet verified.
One isolated runtime test in progress; not a production performance claim.
