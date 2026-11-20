# U-AVF 1.0 — private release preparation

Updated 2026-10-04. No commit, tag, push or public release authorized.

Current candidate: U-AVF 1.0.apk, internal1.0-rc.5/code13.
SHA256 7104BA1221455B6F2E8098298E25C875C7D3F813FFAD7B3F07139EBEBD8B0295.
RC4 isolated clean install/login/session handoff passed; broad acceptance remains pending.
43 audited runtime assets preserved, six legal assets added. Installer identity
and account/session policy updated; encoded handoff reconnect added. Capture and encoder algorithms unchanged.
RC5 adds explicit optional Firebase-cloud disclosure; production tablet remains RC4.

Bundle:

- candidate/: APK, bilingual README/notes/guide, SHA256SUMS, license index,
  scoped source-available terms, preserved Apache, scope, notices and legal/privacy gates.
- internal/: private source checkpoint, source hashes and baseline diff; never upload.
- PREPUBLICATION_CHECKLIST.md inside candidate: unfinished public-release gates.

License locally adopted after owner confirmed original ownership. It cannot revoke
old Apache rights or replace GPL/LGPL/BSD/OFL. Proprietary scope is explicit, not a
directory-wide restriction. Corresponding sources/build configs and legal/privacy/
key-recovery/device acceptance remain incomplete. This is preparation, not a
certification that public distribution is legally or technically ready.

## Русский

Набор локальный; публикация и коммит запрещены. В candidate — APK, документы,
лицензии, уведомления и контрольные суммы. В internal — непубличный снимок кода/
baseline diff, не для загрузки. Новая лицензия принята только для собственных
перечисленных частей, старые/сторонние права сохранены. Новый APK ещё нужно принять на
планшете. Исходники сторонних компонентов, приватность и восстановление ключа —
обязательные незакрытые gates.
