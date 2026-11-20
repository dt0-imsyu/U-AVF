# Windows clock-source checkpoint — 2026-10-11

Isolated installed Windows, original EFI/one vCPU/frozen RAM adapters. No Ubuntu,
credentials, hive resets or user data changes.

## Proven loader selection

CID2204 restores ONE exact winload breakpoint at OslSetSystemTimeParameters
RVA2e3c8. Loader SHA F7747F4AC18CCD66EBF6A043D979CF30C7731114C27C33FF2FC3C0610F491D31.
Evidence: kd-windows-loader-time-selection-canonical-control-20261011/events.jsonl.

| Typed operand | Actual value |
| --- | --- |
| EFI GetTime | 2026-08-24 12:00:06, timezone2047 (unspecified) |
| BlGetTime candidate | 134321400062890000 FILETIME |
| Separate converted EFI candidate | 134320464062900000 FILETIME |
| Boot-status candidate | 193076811012890000 FILETIME |
| Selected loader calendar | 193076811012890000 FILETIME |
| Primary retained | false |

Cold bootstat before this experiment: CRC-valid193076811012898812 =
**2212-11-02T12:45:01.289881Z**. Selected value matches its millisecond conversion.
The loader demonstrably chooses persisted future time over plausible EFI time.
EFI itself is stale (August vs October), but NOT centuries ahead.
This explains persistence, NOT original creation. Do not delete/reset bootstat.

CID2201 reached real Desktop/input/live640x480~13FPS/cap15 without calendar RAM
correction. CPU broker2588 LSASS0..1.134%, late totalCPU95..100%; UI Search102.4%
is not reliable delta attribution. Earlier CID2198 CPU relief is boot-specific.

## Observer repairs, not Windows fixes

First attempt used kernel-only breakpoint table before kernel entry. Exact
loader PDB/static BdDeleteBreakpoint proves BdBreakpointTable3074a0,24-byte
entries, flags+0. Require empty entry AND original instruction readback.
Second attempt revealed canonical kernel extension VA despite physical loader
PC/SP. Typed aligned physical/canonical mapping accepted; no pointer traversal.
Tests execute actual observer branches and reject malformed/active states.
CID2204 captures typed clock fields AFTER restoring probe. No signed code remains
patched. Initial host guard errors are NOT new Windows boot regressions.

Normal test deadline now has a separate retention gate: same owner and fresh
receive/decode/render callbacks, queue0,2s apart. Runtime proof pending; callbacks
are not a physical presented-FPS claim.

## Next discriminator

CID2206 executed this observation from naturally persisted clean2026 bootstat,
WITHOUT calendar correction. Saved18446744072233833192,current18446744073463950269,
freq222389687, delta1230117077 (~5.53s), predicted134361842818803584 (2026).
No underflow/centuries delta this boot. Frequency differs from CID2194's
1109808934, but this alone does NOT prove a counter-domain bug. Do not clamp
kernel arithmetic without capturing the actual bad conversion.

Keep EFI/CPU unchanged. Observe already audited kernel boot-clock subtraction
RVA b17f78: saved PMCC/current QPC/frequency/delta under a fresh calendar control.
Previous CID2194~1.2s NORMAL delta does not exclude intermittent reset/migration
or counter-domain mismatch creating the FIRST future timestamp.
No blind clock-hive/CPU-feature mutation. Calendar-only mitigation previously
worked but is not a permanent source fix. SMP/1200p60 remain downstream.
