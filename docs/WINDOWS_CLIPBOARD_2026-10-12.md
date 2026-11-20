# Windows clipboard: shared UCIP worker preparation

Status: ARM64 worker compiled,47 actual native tests and cross-language vector
PASS. **Not installed, not Windows guest clipboard runtime PASS.** No installed APK,
VM, disk, registry, permission or production Ubuntu change for this work.

## Optional Android client wiring (offline, not installed)

The diagnostic APK now reuses the existing GuestClipboard / ClipboardWire,
not another decoder or protocol. Clipboard defaultsOFF and starts only after
first valid frame with explicit clipboard_enabled=true. The persistent own
VM service admits4054 only for explicit installed-Windows opt-in AND existing
readiness;4055 display remains unchanged. Same-UID and owner-token checks
remain mandatory, then supported connectVsock(port); no private system Binder.
Delegated channel rejects unsupported/oversized long port values before
narrowing.52 actual admission-predicate tests PASS. They do NOT test Binder
UID/token enforcement on a device; those existing checks were not removed.

Shared GuestClipboard has an overload for the Windows label; existing Ubuntu
constructor still delegates to labelUbuntu, unchanged default behavior.
Diagnostic clipboard receives foreground/focus lifecycle and closes on VM
Stop, owner disconnect or Activity destruction. A missing optional4054 port
continues retrying without failing display. No clipboard payload logging.

Final compile/D8/signature verification PASS, including volatile opt-in flag,
in new C-only output build-logs/windows-clipboard-20261012-apk-build2;
APK SHA F4A0ED458D1E0682153EDE3E10A116210F4D370E055CB98DA970659552CED8A4.
Not installed or published. Current installed diagnostic
APK and stable DXGI checkpoint untouched. Optional guest listener/stdio
supervision still needs implementation; don't enable the test flag yet.

## Architecture

## Optional guest listener preparation

The optional4054 listener is now implemented inside our existing winpe-vsock
binary under UAVF_WINDOWS_CLIPBOARD (absent from the default build). It starts
a separate owned clipboard sidecar process only on Default; same binary also
provides --clipboard-worker stdio mode. No new guest executable allocation,
signed driver/provider changes or second Android client. The signed provider
is reused, never re-registered from the optional channel. Listener accepts
hostCID2 only, nonblocking accept; unsupported provider operations fail ONLY
the sidecar. Each connection has a bounded socket/pipe relay and own child job;
disconnect/session loss kills only owned clipboard workers. Display4055 UVF1
is untouched. Runtime provider support, actual peer identity and copy/paste
remain unproven until device test.

Actual native-host localhost/pipe/child test passed68 checks: fragmented
Unicode, request/reply,262144B packet, malformed length, disallowed context,
disconnect and owned child cleanup. This tests REAL relay implementation but
AF_INET/synthetic worker, NOT viosock or Windows clipboard API on the guest.
No host clipboard read/write. ARM64 helper raw30720B
SHA8BEA20E2751B35C859943B158F39FF73763529B01B0E052A77CA6B29A0BB161F.
Cold device original helper SHA4492CA53...2D19 still exact; read-only prepared
81408B slot SHA08F6E931E3F040C8B166790A79B2995AF20F97A956F549B8317E9CC555ECB80B
in build-logs/windows-clipboard-20261012-vsock-prepared. Device writes0.

Diagnostic runner has explicit WindowsClipboard switch, defaultOFF and only
admitted on isolated installed runtime. Parser PASS. It does not by itself
install the new APK/helper; both must be admitted by cold identity/rollback
checks. No test flag has been activated on the tablet.

## Runtime gate / next test

Tablet reserve has failed repeatedly across at least3 consecutive goal turns:
latest12832238*4096B (~48.95GiB) available versus owner52GiB. VM list empty.
No safe expendable >=3.05GiB artifact identified outside production/user data
or official ISO. Do NOT weaken guard or remove those protected files.
All runtime mutations from the attempted codec boot already rolled back and
verified. More independent untested features are not a substitute for runtime
validation. Runtime continuation requires freeing roughly4GB of tablet space
or explicit user direction identifying an expendable artifact.

Then: one named after-reserve encoder run (run-eight-thread-codec-ab.ps1
RunTag preserves prior failure evidence), actual ARM64 selftest/codec selection
and active Desktop measurements first; then one optional clipboard app-owned
4054 test, true Default session copy/paste/lock/reconnect and display concurrent
regression. Do not mix unvalidated clipboard changes into the FPS baseline.

## Shared protocol

Windows-specific source uses public CF_UNICODETEXT / OpenClipboard /
SetClipboardData. UCIP v1 is byte-identical to existing Android ClipboardWire
and Linux4054 agent:12B header, UTF8 text, TEXT1/REQUEST2,262144B max.
Worker stdin/stdout must be connected by optional4054 same-VM vsock broker;
never sent to UVF1/H264 decoder. Existing guest input/display unaffected.

- Active console Default desktop only; Session0, another session and Winlogon
  rejected. Session/desktop checked before opening and around access. No ACL,
  token, desktop switching, driver or trust modifications.
- Real message-only owner HWND: required after EmptyClipboard so ownership
  transfer via SetClipboardData works. Read never resolves files, URIs or
  non-text formats. No clipboard payload logging/history/persistence.
- Strict UTF8/UTF16, supplementary characters supported; embedded NUL and
  lone surrogates rejected rather than truncating. Empty text supported.
- Blocking protocol reader is separate from clipboard owner loop. Pending
  text latest-wins, bounded allocation.200ms poll, sequence-based echo
  suppression, temporary contention retries, cancellation on stream EOF.
- Desktop loss exits worker; supervisor must restart only with new active
  Default desktop. A blocked clipboard worker must never stop display/audio.

## Evidence

tools/windows-reassessment/build-windows-clipboard.ps1 builds on C only,
respecting50GiB reserve; -O2 -Wall -Wextra -Werror, public Windows APIs.
Final worker build-logs/windows-clipboard-20261012-build2/
windows-clipboard-agent.exe: ARM64,16384B,
SHA439890894C3DE2D2B52CD515CFC4E9C7FAEBB6029ADB1CDE0308C99BB5ED5BF7.

Actual native test compiles the same agent/wire implementation:47 checks
cover session/desktop gate, wrong invocation, Unicode/emoji, malformed UTF8,
surrogate/NUL rejection, exact262144B boundary, headers/types/reserved/length,
real one-byte fragmented pipe reads, EOF/truncation and actual packet output.
HOST_CLIPBOARD_ACCESSES=0: no host user clipboard read/write performed.

The emitted synthetic native packet was read and byte-round-tripped by BOTH
unchanged Android ClipboardWire and Linux uavf-clipboard.py. Existing Android
clipboard tests also PASS. This proves wire interop, not runtime vsock,
clipboard ownership across real Windows apps or Android clipboard consent.

## Next admission gates

1. Optional4054 listener/stdio child supervision in isolated Windows path.
   Guest4054 sidecar compiled/prepared, not applied; Android4054 opt-in/defaultOFF.
   Do not enable it until actual guest supervision is proven. No adb shell socket.
2. Exercise the reused GuestClipboard on Android, foreground/opt-in/Windows
   label and lifecycle. Offline wiring is not a device copy/paste test.
3. Bounded reversible placement of OWN worker or embedding in own helper;
   never overwrite signed Windows or user data. Then actual Notepad ↔ Android
   bidirectional Unicode test, contention/reconnect, lock/session isolation,
   disable/background behavior and clipboard+display concurrent regression.
4. VM runtime currently gated by tablet52GiB owner reserve;~48.95GiB available.
   Do not retry blocked boot, weaken reserve or delete official ISO/production
   disk. Encoder8-thread test remains next performance measurement once safe.
