# U-AVF privacy notice

Updated 2026-11-15. Project developer: GitHub dt0-imsyu.
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

The compatibility check runs locally. Diagnostics keeps the 12 most recent
completed reports on the device for search, copying and manual export.
The first-run dialog saves your choice
about sharing. You can change it on the compatibility screen before any later
check. If sharing is on when a check begins, the app sends that result after
the check. The developer uses reports to expand device support.

With consent, U-AVF authenticates anonymously with Google Firebase and sends
a bounded report to the developer's Cloud Firestore project u-avf-7481c.
Reports include the app and Android versions, normalized manufacturer and
model, Android API level, a RAM bucket and the processor count available to
the app. They include the local AVF check outcomes, a random test identifier,
the consent and schema version, and the server submission time. Ubuntu,
Windows and Mavk outcomes have separate local diagnostics.
Firebase associates reports with a persistent anonymous account identifier.
Anonymous authentication does not make the data absolutely anonymous; the
provider also necessarily receives network/connection metadata.

Reports do not intentionally include passwords, clipboard contents, guest files,
screenshots, audio, filenames/paths or full serial/logcat logs. The APK has no
Firebase administrator/service-account credentials. Results are untrusted
client reports, not a certification that every device/application works.

The developer can view and manage reports in Firebase. The app can create
reports for its own account and read its own delivery receipts. Report bodies
are available to the developer. VM operation and device testing work with
sharing disabled.
Reports are grouped by manufacturer, model and device configuration. The
AVF section includes the test VM's reported memory in whole MiB, or zero
if unavailable. There is no automatic expiry or in-app report deletion.
Choose “Continue without sharing” to run a test without uploading its result.
Previously submitted reports and the Firebase account remain in place.

## Русский

Разработчик — GitHub dt0-imsyu. Вопросы о проекте:
https://github.com/dt0-imsyu/U-AVF/issues . Не публикуйте там пароли, токены
или личные файлы гостевой системы.

Диски VM остаются на устройстве. Картинка, звук, ввод и добровольный текстовый
буфер работают локально и не отправляются разработчику. Буфер выключен по
умолчанию. Экспорт диагностики — отдельное действие: перед отправкой проверьте
содержимое. Обновление APK не переустанавливает Ubuntu. Удаление workspace или
данных Android-приложения может удалить виртуальный диск.

Проверка совместимости выполняется локально. Диагностика хранит на устройстве
до 12 последних завершённых отчётов для поиска, копирования и ручного экспорта.
При первом запуске приложение
предлагает сохранить выбор об отправке. Его можно изменить переключателем
на экране проверки перед следующим тестом. Если отправка включена в момент
запуска теста, отчёт отправляется после проверки в облачную Firebase разработчика, чтобы
расширять список поддерживаемых устройств. Передаются версия
приложения и Android, производитель и модель, уровень Android API,
округлённый объём памяти, доступное приложению число процессоров и результаты
проверок AVF. Также сохраняются случайный идентификатор прогона, согласие,
версия схемы и время отправки. Firebase связывает отчёты с устойчивым
идентификатором анонимного аккаунта и получает сетевые метаданные.

Пароли, буфер, файлы гостя, скриншоты, звук, имена/пути файлов и полные логи
намеренно не включаются. Административных ключей Firebase в APK нет. Приложение
может отправлять отчёты и читать подтверждения доставки своего аккаунта.
Содержимое отправленных отчётов доступно разработчику. Отчёты группируются
по производителю, модели и конфигурации. Раздел AVF содержит объём памяти,
измеренный внутри тестовой VM, в целых МиБ или ноль, если данных нет.
Автоматического срока удаления и кнопки удаления старых отчётов сейчас нет.
Чтобы запустить тест локально, выберите «Продолжить без отправки».
Ранее отправленные отчёты и аккаунт Firebase сохраняются.
