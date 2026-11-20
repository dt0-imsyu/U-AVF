# Publication gate: HOLD

- [x] Build signed 1.0-rc.5/code13; preserve tested production payloads.
- [x] Prepare bilingual notes and usage guide with limitations.
- [x] Owner confirms original non-borrowed code/design ownership; scoped local license adopted.
- [ ] Final prior-publication/contributor provenance and jurisdiction review.
- [ ] Verify contributor permissions and inherited/copied code.
- [ ] Fulfil GPL/LGPL corresponding-source, notice and relinking obligations.
- [ ] Review final terms with a qualified lawyer if a restrictive public license is adopted.
- [x] Candidate versionName=1.0-rc.5/versionCode=13 assigned, filename U-AVF 1.0.apk.
- [x] Build with debuggable=false and disable exported diagnostic intent actions.
- [x] Generate and use separate private release signing key; absent from kit.
- [ ] Secure portable signing-key backup/recovery and debug-to-release migration.
- [x] Remove shared fallback password; locked placeholder + real-account readiness guard.
- [x] Root-only formatting/preflight preserved, fixture/static safety checks PASS.
- [x] Accept locked-placeholder template in disposable clean installation; no shared password.
- [x] RC4 isolated clean install: chosen username/password policy and real greeter-to-desktop login; encoded reconnect, session audio and clipboard socket verified.
- [x] Current clean installation completed; installed clipboard socket listening.
- WAIVED by owner 2026-10-04: new APK clipboard hot-toggle + bidirectional copy/paste acceptance; not a test PASS.
- WAIVED by owner 2026-10-04: real partition/ext4 expansion acceptance; not a test PASS.
- [ ] Review privacy notice, retention/deletion, consent and backend rules.
- [x] Owner chose opt-in Firebase cloud reporting; explicit disclosure/purpose added, no local-only claim.
- [ ] Final smoke: install/reboot/data retention, display, input, audio and network.
- [x] Include settings-accessible license/notices/privacy inside APK.
- [x] Replace fixed dev appVersion in compatibility reports with actual package version.
- [ ] Owner approves known issues and publication after license decision.

Keep as known issues, not tasks to fix during this preparation: installer flicker,
slow App Center, installed-status transition after the first reboot.

Never upload `internal/`, signing keys, auth tokens, personal logs/screenshots,
guest disks or unofficial Windows media. Do not publish the candidate ZIP merely
because the preparation script succeeded.
