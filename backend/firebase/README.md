# Compatibility reports (u-avf-7481c)

Development ingest only. Firestore location: europe-central2. No paid services
or billing upgrade are needed. Deployment status is tracked in RELEASE_CHECKLIST.

The installed Android REST client now authenticates anonymously and submits after
an opted-in completed check. Device submission verified 2026-10-03 17:32:37 UTC+3.
Auth users appear in Authentication → Users. Reports are under Cloud Firestore →
Data → compatibility_reports → UID → runs → numeric document. UID parents have
no fields: the Console may say the parent document does not exist; expand `runs`
to see the actual report. No separate `users` Firestore collection is needed.
Refresh credentials are encrypted using Android Keystore in private app storage.
No automatic retry queue: an error is shown and Share report permits a retry.

- `compatibility_reports/{anonymousUid}/runs/{0..31}`: immutable opt-in reports.
- No app read/list/update/delete; administrators use Firebase Console.
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
