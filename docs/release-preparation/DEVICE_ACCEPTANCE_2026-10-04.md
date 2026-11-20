# Device acceptance — ongoing, no publication

Device: Samsung SM-X736B / Android16. Production package com.example.winavf.
Candidate: 1.0-rc.2/code10, SHA256
B24C82EC41521E8CC2D4F51B0C97E2715F6612B37DAD4F34A88C346DC0AC4BC2.

## Confirmed

- RC1/RC2 update accepted with original release certificate; AVF permissions
  retained. Settings legal notices actually open and scroll.
- RC1 acceptance exposed an updater regression: installed ESP marker existed
  but ready preference was absent, so boot files were replaced by the Live ESP.
  Root/ISO/GPT were untouched; backup created before ESP write.
- RC2 skips that replacement and restores only a unique SHA-verified installed
  ESP backup over an exact managed Live ESP. Sparse fixture verifies marker
  crossing a scan-chunk boundary, idempotence, unknown ESP refusal and root/ISO/GPT
  preservation. Recovery refuses ambiguity rather than guessing.
- Actual tablet restoration exact SHA:
  E034AAA119F9F14F69969079DFDB365C4F4806B336D0F18DA6FC9A3AD5973A28.
  Installed uavf-ubuntu GNOME and LVH1 revision2/ready confirmed; not Live ISO.
- Ctrl+Alt+T opens guest terminal; host control connected. Hardware MediaCodec
  H.2641920x1200 presents frames pending0; idle cadence is not an FPS benchmark.
  PCM stream advances, underruns0; audible sound not reconfirmed this run.
- Normal guest shutdown reaches poweroff.target, crosvm exits.
- Seven Java suites rerun against RC2 classes PASS, plus isolated recovery test.

## Isolated clean installation

QA APK com.example.winavf.releaseqa, version1.0-rc.2-qa/code10, different UID,
private VM and disk ownership; not for public release. Production disk preserved.

- Fresh onboarding/permissions/local-prerequisite checks completed without ISO
  or storage requirements in compatibility check. Report-sharing left off.
- ADB-owned ISO caused EACCES in initial QA staging, before VM creation. Corrected
  test method: select the official unchanged ISO from Downloads through SAF.
  Import succeeded, actual file app-owned and SHA verified. No product workaround
  to Android file restrictions introduced.
- Create32GiB root/one virtio disk/GPT audit/VM creation/Live LVH1 PASS.
- Installer automatically opens fullscreen; no terminal/manual installer launch.
- Actual installer accepts locked-placeholder identity template; interactive pages
  are locale/keyboard/identity only. Missing password keeps Next disabled;
  independent random QA-only password entered and confirmation accepted.
- Preflight PASS: runtime payload, exact protected GPT/media, root size sync.
- System copy and curtin completed. Account guard emitted USER_ACCOUNT_READY;
  late-command staged encoded display, clipboard4054, control/storage growth
  and audio payload. Installer reached ApplicationState.DONE.
- Completion UI required the normal Restart now action; no claim of unattended
  shutdown. After that action, APK automatically began the next UEFI boot.
  First installed boot automatically reached GNOME with hardware MediaCodec
  1920x1200 output and PCM audio stream (underruns0); not a speaker listening test.
- RC2 readiness probe exhausted 90 rapid revision1 replies before firstboot
  finished; serial later emitted RUNTIME_ACTIVE. Installed desktop was usable,
  but Android reported NOT_READY. RC3 uses elapsed-time limits (300s installed,
  90s Live), one-second pacing for preparing replies and VM-owner cancellation.
- Normal QA guest shutdown completed. In-place RC3 QA update and repeat boot of
  the SAME disk returned LVH1 revision2/capabilities7/agentStatus0 on attempt45.
  Workspace UI now reads Installed · ready. No reinstall or root rewrite.
- Six Java regression suites rerun against RC3 QA classes PASS. Guest scripts,
  firmware and all43 tested runtime assets preserved. Clipboard hot-toggle,
  manual unlock, storage expansion and human audio acceptance still pending.
- Account-policy review remains open: bootstrap currently forces automatic login
  for uavf, although installer exposes username and password-login choices.

Do not claim full release acceptance from partial progress. Private screenshots
and serial reports reside in F:/dt/UAVF-release-prep/2026-10-04-rc2/internal.
No git commit/push/tag or release performed.

## RC3 production smoke

Production package updated in place to1.0-rc.3/code11 with original release cert;
both AVF permissions retained. Existing52GB workspace still Installed ready.
Normal launch reached the original Russian GNOME desktop; fresh LVH1 revision2,
agentStatus0, capabilities7. Hardware H.264 Surface1920x1200 pending0..1 and
PCM_HEADER_PASS observed. Six Java suites pass against production RC3 classes;
signature,16KiB alignment,43 payload identity/ZIP/checksum/legal-scope audit PASS.
VM left running for user. No root format or ISO/firmware/runtime changes.

## RC4 isolated account-policy acceptance

QA only: 1.0-rc.4-qa/code12, SHA454E15081E4E805108551A8623DCBD61E27FC7406D3C9840F674FC211F53AC32.
Production remains RC3; normally shut down before QA launch. Only QA test vda3
is replaced. Verified installer prefix applied with hash-verified ESP backup;
official ISO, platform kernel/launcher and production disk not modified.

- Five executed/static session-policy tests PASS; no forced uavf autologin.
- Actual fullscreen automatic Setup reached identity. Selected custom qauser,
  independent QA-only password and Require my password to log in checked.
- Installer accepted matching credentials. Review shows vda1 reused for
  /boot/efi and vda3 for /; copying started normally.
- Copy/curtin/account guard and all runtime late commands completed; Setup
  reached DONE. Security-update substep declined the metered connection
  ("System is on metered connection, stopping"); not an installation-copy
  failure, but do not claim security updates fully applied. Normal Restart now
  requested. Real GDM greeter appeared, selected password accepted and qauser
  desktop opened. First boot exposed encoded EOF handoff falling back to raw.

### RC4 encoded session handoff and repeat login: PASS

- Minimal APK-only fix retries validated-stream EOF during GDM Xorg handoff,
  bounded to three retries/minute; malformed protocol/codec errors retain fallback.
- QA APK D13DFD78B2B570079E736DE87FF4E1B9094F43E791806A0A0BD32B3C0DCF0FC4
  installed after normal shutdown. SAME installed QA disk; no further root patch.
- Repeat greeter/password login reached qauser desktop. Android emitted
  "Reconnecting after guest X11 session handoff"; fresh report retained
  hardware c2.mtk.avc.decoder1920x1200 with pending0..1. Static-screen cadence
  is not a sustained workload FPS benchmark.
- Actual id: uid1000 qauser; clipboard socket active. Firefox Snap GUI opened
  from this authenticated session, without the prior login-loop/Snap symptom.
- Audio PCM48kHz16bit stereo stream ~192KB/s, underruns0; not a new listening test.
- Bidirectional clipboard toggle, physical input/background lifecycle, storage
  growth and audible latency still require acceptance; socket alone is not enough.
- Ordinary production RC4 SHA:
  8C156D47A4BABCFF89C1B93AAE7B39A705904406BA199F5DA87E718B746D72F8.
- Private QA evidence under F:/dt/UAVF-release-prep/2026-10-04-rc3/internal/:
  qa-rc4-install-serial.log, qa-rc4-handoff-serial.log,
  qa-rc4-handoff-encoded-report.txt. No passwords recorded in this report.

## RC4 ordinary production update

Signed ordinary RC4/code12 installed in place; both AVF permissions remain
granted. Existing52GB workspace remains Installed ready. Normal Launch Ubuntu
passes GPT audit and skips installed ESP replacement; no production root/ISO
format. Original installed runtime is not silently rewritten to migrate account
policy. New clean installations receive the corrected bootstrap.
Private RC4 kit checksum/ZIP/manifest/43-payload/scope audit PASS; six Java suites
and packaged installer ESP range/regression tests PASS. No commit or publication.
Fresh ordinary production boot reaches the original installed Russian GNOME;
LVH1 revision2/capabilities7/agentStatus0 and hardware c2.mtk.avc.decoder1920x1200
observed. Screenshot and readiness report saved under private RC4/internal.
VM left running for the user. No renewed sustained FPS benchmark or listening test.
