# U-AVF privacy notice

Updated2026-10-04. Project developer: GitHub dt0-imsyu.
Project support: https://github.com/dt0-imsyu/U-AVF/issues
Do not post passwords, authentication tokens or private guest files publicly.

## Local operation

VM disks remain on the Android device. Display, audio, keyboard, mouse and
optional text clipboard use local VM-to-app channels, not the developer's
servers. Text clipboard sharing is off by default. Enable it only for a VM you
trust. Exported diagnostics can contain device details and guest console output;
review them before sending. Updating the APK does not reinstall Ubuntu.
Deleting a workspace or clearing application data can delete its virtual disk.

## Optional cloud compatibility reports

Sharing is OFF by default and is not required to use U-AVF. You can choose it
on the Welcome screen before the compatibility test. The developer uses reports
for compatibility analysis, optimization and further development.

With consent, U-AVF authenticates anonymously with Google Firebase and sends
a bounded report to the developer's Cloud Firestore project u-avf-7481c.
This is an external cloud database, NOT local-only storage. The report contains:
app version; Android/API version; manufacturer/model; compatibility-test and
runtime-evidence states; consent/schema version; server submission time.
Firebase associates reports with a persistent anonymous account identifier.
Anonymous authentication does not make the data absolutely anonymous; the
provider also necessarily receives network/connection metadata.

Reports do not intentionally include passwords, clipboard contents, guest files,
screenshots, audio, filenames/paths or full serial/logcat logs. The APK has no
Firebase administrator/service-account credentials. Results are untrusted
client reports, not a certification that every device/application works.

Only the developer can view/manage reports in Firebase. The app can create its
bounded own reports but cannot read, change or delete reports. Sharing is not
Firebase Analytics and is not required for device testing or VM operation.
There is currently no automatic expiry or in-app report-deletion mechanism.
Disabling sharing stops future uploads; it does not erase prior reports or
the Firebase account. No fixed retention/deletion deadline is promised.
Do not enable sharing if these limits are unacceptable.

## Русский

Разработчик — GitHub dt0-imsyu. Вопросы о проекте:
https://github.com/dt0-imsyu/U-AVF/issues . Не публикуйте там пароли, токены
или личные файлы гостевой системы.

Диски VM остаются на устройстве. Картинка, звук, ввод и добровольный текстовый
буфер работают локально и не отправляются разработчику. Буфер выключен по
умолчанию. Экспорт диагностики — отдельное действие: перед отправкой проверьте
содержимое. Обновление APK не переустанавливает Ubuntu. Удаление workspace или
данных Android-приложения может удалить виртуальный диск.

Отправка совместимости добровольная, по умолчанию выключена и не нужна для
работы VM. При согласии результаты отправляются во внешнюю облачную Firebase
разработчика для анализа совместимости, оптимизации и разработки U-AVF.
Это НЕ локальная база на планшете. Передаются версия приложения/Android,
производитель/модель, состояния проверок и runtime, согласие, версия схемы и
время отправки. Firebase создаёт устойчивый идентификатор анонимного аккаунта;
это не абсолютная анонимность, сервис также получает сетевые метаданные.

Пароли, буфер, файлы гостя, скриншоты, звук, имена/пути файлов и полные логи
намеренно не включаются. Административных ключей Firebase в APK нет. Приложение
не может читать, изменять или удалять отчёты; доступ к ним у разработчика.
Автоматического срока удаления и кнопки удаления старых отчётов сейчас нет.
Отключение согласия прекращает новые отправки, но не удаляет прежние отчёты и
аккаунт. Фиксированный срок хранения/удаления не обещается. Если это не подходит,
оставьте согласие выключенным — приложение и VM продолжат работать.
