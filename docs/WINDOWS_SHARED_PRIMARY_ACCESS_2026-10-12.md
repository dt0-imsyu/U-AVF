# Read-only shared-primary admission query

RAM framebuffer is now proven to change after Desktop (8 matching workload
pixels in two observations). Direct guest user-mode physical-memory mapping
is absent. Before committing to a new driver, check the documented graphics
subsystem query D3DKMTGetSharedPrimaryHandle.

This is NOT proof that a shared primary equals our GOP physical buffer.
Nonzero handle would permit further admission research, not immediate RAM
mapping/zero-copy/60FPS. Zero/unsupported closes this particular public query
route; do not create allocations, change modes or force flags to manufacture
a positive result.

## Exact bounded candidate

Only once on actual Default desktop, using its HDC:
OpenAdapterFromHdc -> GetSharedPrimaryHandle -> CloseAdapter -> ReleaseDC.
Reports all NTSTATUS values, source ID, adapter LUID and shared handle.
No CreateAllocation/OpenResource/Lock/Map/physical-memory calls; no display
mode, source/codec/transport/Android changes. No kernel driver installation,
test-signing, BCD/firmware/security-policy changes.

SDK ABI verified with24-byte OpenAdapter struct (adapter offset8, source20),
12-byte primary query and4-byte close structs; ARM64 and x64 strict builds.
Actual mock tests: absent function, failed open, success+zero handle,
success+nonzero handle, failed query with cleanup, failed close reporting.
UVF1/color host tests PASS. Tests do not prove device availability.

Build `build-logs/windows-shared-primary-verified-20261012`.
Raw SHA05C017DB9456BCC3C43487C2EF05C7DE253BA1A3A23D11EA2BC5159F6F12A2FC.
Existing79872B slot SHA
A4F177EDC2E6B298742187F3057B54F0A467CF48A92004644BA5A0C4B54C1A02.
Cold original encoder4161F240...A00B84 confirmed; no signed file/hive changes.
Run `shared-primary-query` reached actual Default Desktop. Device result:
open00000000, query00000000, close00000000; adapter1073741824, source0,
shared0, LUID00000000:000047d9. This is a successful query returning NO
shared primary, not missing exports or access denied. Preserved exact line
in `runtime-shared-primary-query/source-current.txt` and final
`kd-windows-direct-x264-20261012-shared-primary-query/encoded-display-report.txt`.
Screenshot shows actual Windows Desktop at native1200p.

Decision NO-GO for this documented shared-primary access route on the current
BasicDisplay configuration. No resource handle exists to open/map; do not
attempt CreateAllocation/new modes or manufacture a different graphics path.
It does not prove every possible future Windows API impossible.

Stopped through own UI after preserving result; observer disconnect was
intentional. VM list empty. GOP/socket/encoder/separate112-block codec cold
rollback/readbacks ALL PASS; runner terminal exit0. No active experiment.
User data/Ubuntu/P7 protected. No commit/push.

## Primary references

- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/d3dkmthk/nf-d3dkmthk-d3dkmtgetsharedprimaryhandle
- https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/d3dkmthk/ns-d3dkmthk-_d3dkmt_openadapterfromhdc
- https://learn.microsoft.com/en-us/windows-hardware/drivers/kernel/security-issues-for-section-objects-and-views

Microsoft explicitly notes the shared primary may not exist. This is a
public documented GDI export query, not an unsupported Android Binder call
or arbitrary physical-memory workaround.

Next available practical path under current no-policy-change boundaries:
the already prepared async capture-worker with corrected frame-based pacing,
bounded latest-frame ownership and separately owned timer, after its combined
admission/ownership tests. This is a different source scheduling test, not a
repeat of the old blocking overlap. RAM route needs a properly admitted reader;
do not spend more VM boots trying to turn shared0 into a nonzero handle.
