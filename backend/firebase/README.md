# Compatibility reports (u-avf-7481c)

Opt-in compatibility ingest. Firestore location: europe-central2. No paid services
or billing upgrade are needed.

## U-AVF 1.2 schema

- `compatibility_devices/{manufacturer}/{model}/{configuration}/reporters/{uid}/tests/{run}`
  stores each completed AVF result under its hardware group. Configuration is
  `api<N>-ram<N>-cpu<N>`, using the Android API, rounded RAM in GiB and app-visible
  processor count. Firestore alternates collections and documents, so each model
  is a collection directly under its manufacturer's document. The old
  `models/{model}/configurations/{configuration}` route remains for earlier APKs.
- `compatibility_reporters/{uid}/receipts/{run}` and
  `compatibility_reporters/{uid}/slots/{0..31}` track delivery and enforce the quota.
  A run is a random ID persisted with the local result. A single commit creates
  the report, receipt and slot, and rules require all three to agree. The
  reporter can read its own receipt or slot by ID to recover after a lost response.
- A report contains app/Android versions, check outcomes, status, consent,
  schema version, slot and server timestamp. The `device` section contains
  Android API level, rounded-up RAM in GiB and app-visible CPU count. The
  `avf` section contains the probe's reported guest memory in whole MiB
  (zero when unavailable). Earlier v2 clients can still omit both sections.
  Ubuntu, Windows and Mavk results remain separate local diagnostics.
  Report bodies are write-only to clients.
- The first-run dialog saves the sharing choice and starts the local check.
  Later checks use the saved switch on the compatibility screen.
  The report uses the completed run's
  ID. Refresh credentials stay encrypted in Android Keystore. No retry queue.

Run the Auth and Firestore emulator tests from the repository root:

```powershell
npx --yes firebase-tools@14.23.0 emulators:exec --only auth,firestore --project demo-uavf --config backend/firebase/firebase.json 'python backend/firebase/test-compatibility-rules.py'
```

The emulator accepted the grouped commit and legacy v1 write. It rejected
partial writes, replay across configurations, occupied slots, writes without
consent, another account's access and a claimed compatible result with an
unverified check. The emulators use a loopback demo project.

Production rules were deployed on 2026-11-15. Readback matched this
`firestore.rules`. Two legacy v2 reports were moved to the direct model
path. The report payloads and original submission timestamps matched after
the move. The previous path still accepts older APKs. The emulator covered
the new path, receipt lookup, consent, duplicate rejection and legacy writes.
The deployed v1 paths and permissions were preserved.

## Historical v1 reports

- `compatibility_reports/{anonymousUid}/runs/{0..31}`: immutable opt-in reports.
- Existing records and old clients keep their path and rules.
- Administrators can inspect the history in Firebase Console.
- Server timestamp and exact bounded field/schema validation.
- ABI/API, permissions, storage and checksum probe outcomes are separate from
  boot/vsock/actual-renderer evidence. Missing evidence remains `not_tested`.
- Never upload serial/logcat, hardware serial numbers, credentials, file names/paths,
  clipboard, screenshots or guest contents.
- Disabled consent means no authentication or upload. Permission changes must
  take effect before a new upload. This is not Firebase Analytics.

Anonymous Auth is pseudonymous, not absolute anonymity. The service sees network
metadata. 32 reports per UID is a bound, not a defence against new UID creation.
Known hardening limits: App Check / signed APK validation and automatic retention
are not implemented. Privacy notice describes the actual optional cloud flow.
Client reports are untrusted claims,
not authoritative device certifications or a remotely controlled launch policy.

Keep admin/service-account credentials OUT of the APK and repository. The client
uses a Firebase public client API key plus its own short-lived Auth token.

Live release validation2026-10-04: anonymous sign-in and one marked synthetic
report accepted. Unauthenticated create, reads, update/delete, another-owner
create, consent=false, extra fields, oversized values and run32 all rejected
with403. Two test identities removed; one synthetic report retained. This does
not certify protection against bulk creation of fresh anonymous identities.
