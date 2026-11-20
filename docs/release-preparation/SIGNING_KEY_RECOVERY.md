# Private signing-key recovery

Signing works with the current key; portable recovery is not yet accepted.
Losing this key prevents normal updates to installed release APKs. Do not replace
the signing key merely because its Windows-DPAPI credential cannot be opened.

The interactive tools/release/backup-signing-key.ps1 creates an encrypted PKCS12
copy of the existing key in a private directory OUTSIDE the repository. It asks
for a new passphrase twice, never prints it and checks the release certificate.
It does not alter the original key or copy Windows-bound credentials into the kit.
Choose independent private storage; preserve the passphrase separately in a
password manager. Never commit/upload the p12 or passphrase.

Example (replace the path with an existing private backup directory):

```powershell
& .\tools\release\backup-signing-key.ps1 -OutputDirectory 'X:\Private-UAVF-Key-Backup'
```

Acceptance requires opening the backup with its passphrase on an independent
machine/account, checking SHA256 certificate
E98E3ADFB577D0134B25234C6C72497D40F8221174CFC0EBA49F5CA95E1E8240,
and signing/verifying a disposable APK there. Do not publish that test APK or
clear user workspaces. This script's parser check is not a recovery test.

## Русский

Ключ нельзя включать в релизный комплект. Скрипт создаёт защищённую переносимую
копию существующего ключа, пароль вводишь сам. Храни файл на отдельном носителе,
пароль — отдельно. После этого нужно проверить восстановление в другой среде.
Сейчас этот пункт остаётся НЕ ПРОВЕРЕН, новый ключ не генерировался.
