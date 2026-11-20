# Windows clipboard channel recovery

RAM mapping branch is stopped at its explicit safety gate. Full product goal
remains incomplete: no60FPS, release network/audio/clipboard/UI/coexistence PASS.
Next bounded product step is optional Windows clipboard through shared UCIP4054.

## Fixed source defect

The optional server returned after provider/socket/listen failure, while the
independent working display producer could remain alive indefinitely. Server
now retries initialization at1s intervals ONLY while the authenticated active
Default-desktop predicate remains true. Every failed attempt closes its socket,
WSA/provider references before retry. Lock/session mismatch stops retries and
clipboard access; existing display broker creates fresh per-desktop producers.
No display source/codec/CPU/firmware/Android or Linux code changed. Clipboard
continues opt-in; failure never promotes itself to a display/VM failure.

## Verified on PC / actual device read-only preflight

- Strict ARM64 optional server build fits existing81408-byte slot (30720 raw).
- Actual localhost socket/owned-pipe relay:68 checks PASS, including fragments,
  maximum size, malformed input and denied desktop callback.
- Recovery policy:8 checks PASS (initial denial, shutdown, transient failures,
  bounded retry delay, no restart after denial).
- Native Unicode/header/bounds/pipe/EOF:47 checks PASS, no host clipboard reads.
- Produced vector matches unchanged Linux UCIP encoder/parser exactly.
- Default feature-OFF strict ARM64 build also PASS.
- Build artifacts may use new E:/U-AVF/artifacts with50GiB reserve and no
  ancestor reparse point. Guard tests reject D, path escape and existing output.
- Read-only tablet plan confirms only existing OWN winpe-vsock.exe changes,
  159 sectors/two ranges; no allocations/hive/driver/GPT/NTFS metadata changes.
  Original slot4492CA531764A5854DE657AA07228D1B06357017C8447E673B3862EFA80F2D19;
  candidate paddedA0F3D8FD171BD29F03AE5040ED674991D3E3E6C4D67269DE9E0DDCF050AD4704.
  Actual cold apply-preflight PASS; zero device writes; VM list empty.

Artifacts and rollback preimages:
E:/U-AVF/artifacts/windows-clipboard-recovery-20261012/cold-plan/.
Raw server SHA1E100CC67C980AB7CC926F93F63BD97AF5F1E25731FE4CA5353D002EC0968739.
Worker artifacts: E:/U-AVF/artifacts/windows-clipboard-worker-20261012/.

## Bounded runtime test result

The opt-in diagnostic APK was built and installed over the existing diagnostic
package (previous APK saved on E). One original8CPU/x264 launch reached the
actual Windows Desktop. The synthetic app-owned UCIP roundtrip verifier did
NOT confirm success; this is not a clipboard PASS. No existing clipboard text
or private owner token was emitted. VM was stopped; cold rollback is required
before any next launch. All four cold rollback/readbacks completed PASS
(GOP, own socket, own encoder, all112 codec blocks); runner exited0, VM list
empty. No further clipboard investigation in this RAM task.

## Earlier preparation state (superseded by the runtime result above)

No guest binary staged or APK installed this turn. Real Windows clipboard APIs,
guest viosock and Android clipboard roundtrip still NOT runtime PASS. Need one
explicit opt-in original8CPU launch with the bounded own-socket replacement,
synthetic text/request roundtrip, lock/unlock/reconnect and exact rollback.
Channel-written acknowledgements and source compilation must not be relabelled
as successful guest command execution or clipboard delivery. Do not boot just
to collect another screenshot without a ready roundtrip verifier.

## Follow-up source fix and next bounded gate

Inspection found a separate ordering defect: after a failed clipboard TEXT
write (temporary contention), REQUEST could return the old clipboard and be
consumed; the eventual successful retry suppressed its own sequence change.
The worker now defers reads while TEXT remains queued and preserves REQUEST
until the write commits. This is a demonstrated source defect, NOT yet the
proven cause of the preceding guest roundtrip failure.

PC validation:52 Unicode/header/pipe/context/deferral checks PASS (five new
pending-query checks); actual socket/child relay68 PASS, retry8 PASS. No host
clipboard access. Strict ARM64 worker and optional vsock build PASS. Artifacts:
E:/U-AVF/artifacts/windows-clipboard-ordering-20261012/ and
E:/U-AVF/artifacts/windows-clipboard-ordering-vsock-20261012/.
Raw integrated server SHA329848F8DE31B5352E5D160D8717E112474012166A9F4AAC3BC50DD0B2B53EEA.

Diagnostic probe now records only fixed CONNECT/SEND/READ/MATCH/UNMATCHED stages
and IO/CHANNEL error categories; no text, exception messages or private tokens.
Built/signed APK on E:/U-AVF/artifacts/windows-clipboard-staged-probe-20261012/,
SHA64F202A572F8C7F85B0C94BC933D75E791D6EB1DD4D4706FBA56742446511D5F.
This candidate is NOT installed/staged on the tablet. Next test must admit a
fresh exact own-slot cold plan before using it; one explicit fixture roundtrip
should classify the first failure stage, not trigger blind repeated boots.

## Ordering candidate guest test

One original8CPU/native1200p/x264 launch with the ordering candidate reached
Windows login, accepted the authorized password and displayed the real Desktop.
App-owned probe stages: CONNECT -> SEND -> READ -> IO error. No MATCH and no
clipboard PASS. This proves channel admission and outgoing fixture writes,
not guest clipboard application; the response/read path needs localization.
Do not blame desktop authentication, VM CPU or display flags based on this.

Run evidence: kd-windows-direct-x264-20261012-clipboard-ordering/owner-report.txt;
own-slot padded SHA E2534C5C165AB6508E28DDC150A045AE28DFDBE00046E4557969D6FC92F4E49A.
VM stopped explicitly after that one result. All four cold rollback readbacks
PASS, including all112 codec blocks; runner exit0. Previous diagnostic APK
restored. No repeated probe/boot in this run.
Read-only own-log snapshots are non-coherent: producer6984 log reports older
CID2167/LISTEN_ERROR10048 and is NOT attributed to this launch. Missing/current
worker details remain unknown, not proof of a worker crash.

## Prepared response/worker localization

Optional sidecar records at most64 fixed numeric lines in its own generation
directory, clipboard-status.txt. Event1: sidecar PID/session; event2: current
viosock CID/AF; event3: relay stage/error/natural child exit/result; event4:
server attempt result. No text, credentials, clipboard history or frames.
Natural child exit is sampled BEFORE job cleanup; STILL_ACTIVE means unknown,
not a successful or failed worker completion.

Relay stages:1 socket timeout setup,2 pipes,3 inheritance,4 NUL,5 job,
6 CreateProcess,7 job assignment,8 resume,9 upload thread,10 relay loop,
11 pipe peek failure,12 child termination observed,13 pipe read failure,
14 socket send failure. Early setup failures record the immediate Win32 error.
An explicit fake child exiting77 validates natural-exit reporting without any
host clipboard accesses. Actual socket/pipe/owned child tests90 PASS; retry8
PASS. Strict ARM64 integrated helper build fits the unchanged81408B slot.

Artifacts: E:/U-AVF/artifacts/windows-clipboard-worker-status-verified-20261012/;
raw SHA DC23B898AF4A4A1ABA3B4C88C486D376ED21501592501739594757C8D7EC7B02;
fresh cold-plan padded SHA F0190C62DA3596C37A995111A02F1A698B72472664AAB3021F21C4BF6436BD9E.
Plan reads the exact original4492...F2D19 own helper; zero device writes,
no registry/driver/partition changes. Android probe additionally distinguishes
EOF/TIMEOUT from generic IO without exception text; signed diagnostic APK:
E:/U-AVF/artifacts/windows-clipboard-worker-status-apk-20261012/,
SHA3AD48D19C7C9247B9355F73C2E2346C1F9AAB82E5B8FDD49AC4E19D988B8B78F.
Neither candidate is staged/installed. Next single test should save the actual
current VM CID and match it to sidecar event2 before attributing guest logs.
Then inspect event3 and Android EOF/TIMEOUT category, no blind reboot loop.
