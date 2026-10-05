# U-AVF 1.0.1 — Compatibility hotfix

## English

- Fixed a false platform-integrity failure observed after Samsung OTA. The released APK preserved a tested platform image, while the check expected a different local image hash. Expected hashes now come from actual packaged assets during the build and remain protected by the APK signature.
- Compatibility PASS is invalidated when the Android build fingerprint changes.
- Fixed overlap between the retry button and status message.
- Added **Skip test** on the incomplete/failed test screen, with an unverified-device warning. This does not grant PASS or bypass VM permissions and runtime integrity checks.
- Existing workspace data and all 43 tested runtime/native payloads are preserved. No Ubuntu reinstall required.

## Русский

- Исправлена ложная ошибка целостности платформы, замеченная после Samsung OTA. APK содержал проверенный образ платформы, а проверка ожидала SHA другой локальной копии. Теперь SHA берутся из фактически упакованных файлов при сборке и защищены подписью APK.
- Сохранённый PASS сбрасывается при изменении fingerprint Android.
- Убрано наложение кнопки повторного теста на сообщение.
- Добавлена **Skip test** на экране незавершённого/неуспешного теста с предупреждением. Пропуск не даёт PASS и не обходит разрешения VM или runtime-проверки целостности.
- Данные workspace и все 43 проверенных runtime/native-компонента сохранены. Переустановка Ubuntu не нужна.

## Verification / Проверка

- APK: version 1.0.1, versionCode 15, release signing certificate unchanged; signature and 16 KiB ZIP alignment PASS.
- SHA-256: `61729665D1FC185F6B327A3A4F04A24F56D71A1C260C0978109F7CB760CE7104`.
- Four integrity-generator tests and six Java regression suites PASS.
- In-place APK update on Galaxy Tab S11 / Android 16 PASS. ARM64, custom VM permissions, AVF manager and bundled asset integrity all PASS.
- User confirmed the updated version works. A new VM performance benchmark was not run for this compatibility-only hotfix.
- Existing 1.0 limitations remain; this release does not claim an OTA hypervisor fix or hardware Vulkan support.
