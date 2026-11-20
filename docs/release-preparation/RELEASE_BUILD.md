# U-AVF 1.0 — local release candidate

Prepared 2026-10-04. Public release/tag/commit: **HOLD**.

- File: U-AVF 1.0.apk; internal version 1.0-rc.5, versionCode13.
- SHA256: 7104BA1221455B6F2E8098298E25C875C7D3F813FFAD7B3F07139EBEBD8B0295.
- debuggable=false; backup disabled; development intent actions disabled.
- Signature/alignment verified. Same private release key as RC7.
- All43 runtime payloads identical to audited account-policy base, six legal
  assets included. Installer-selected authentication preserved; session audio
  and clipboard resolve the real account. Encoded EOF handoff reconnect added.
  Early CPIO/kernel/launcher/GPT/ISO and encoder/capture algorithms unchanged.
- Clipboard switch hot-applies; channel diagnostics added; wire tests PASS.
- RC4 isolated clean installation and repeat custom-user login PASS; Firefox Snap,
  encoded1920x1200 handoff, audio PCM and clipboard socket observed. Broad device
  acceptance (including actual bidirectional clipboard and storage growth) pending.
- Six Java regression suites rerun against ordinary production RC5 classes PASS.
- RC5 changes only cloud-consent/privacy text; runtime assets remain unchanged.
  RC4 is installed; RC5 device UI smoke still pending, no running VM interrupted.
- Licenses/privacy/notices are packaged and accessible in settings. Privacy contact,
  retention/deletion and complete third-party source compliance remain gates.

Updates signed with this release key update RC7 without uninstalling/deleting
Ubuntu. Debug-signed APK migration is separate: do not clear data merely to bypass
a signature mismatch. Back up workspaces first.

Signing key and Windows-DPAPI credentials are outside the repository/kit. Portable
recovery still needs owner verification. Never publish keys, tokens, internal/,
guest disks or personal logs/screenshots. Licensing compliance, required sources,
privacy and device acceptance remain gates; release mode is not release approval.

## Русский

Это 1.0-rc.5/code13, не финальный опубликованный релиз. Подпись, упаковка и рабочие payload
проверены. Чистая QA-установка, обычный вход и восстановление encoded-картинки проверены. Обновление той же
подписью не требует удаления Ubuntu. Ключи не входят в набор. Правовые документы
включены в APK; публичная раздача требует закрытия остальных gates.
