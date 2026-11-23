# U-AVF privacy notice

**Updated:** November 22, 2026 · **Version:** U-AVF 1.2 · **Developer:** [dt0-imsyu](https://github.com/dt0-imsyu)

For questions, use [GitHub Issues](https://github.com/dt0-imsyu/U-AVF/issues). Please don't post passwords, tokens or private files from your virtual machines there.

## What stays on your device

Your Ubuntu and Windows virtual disks remain on your Android device. Screen output, audio, keyboard and mouse input, and optional text clipboard sharing are handled locally between U-AVF and the virtual machine. U-AVF does not send them to the developer's servers.

Clipboard sharing is off by default. Enable it only with a VM you trust.

Updating the APK does not reinstall your guest systems. However, deleting a workspace or clearing U-AVF's app data can delete its virtual disk. Back up any files you want to keep.

For Windows installation, U-AVF reads the ISO you select. During setup it creates a temporary, private copy of the WinPE image in app storage and removes it afterwards. ISO contents are not uploaded.

## Compatibility reports

The compatibility test runs on your device whether you choose to share a report or not. U-AVF keeps the **12 most recent completed reports** in app storage. You can search, copy and export them yourself. Exported logs may include device information and guest console output, so review them before sharing.

When you first use U-AVF, it asks whether to share compatibility results. You can change that choice on the compatibility screen. Consent is checked when the test begins and again before a report is sent.

**If you agree to share**, U-AVF signs in anonymously with Google Firebase and sends a limited report to the developer's Cloud Firestore project, `u-avf-7481c`. Reports are used to identify devices that may work with U-AVF.

A submitted report includes:

- U-AVF version, Android version and API level
- normalized device manufacturer and model
- approximate RAM category and CPU count available to the app
- results of the local AVF checks
- memory reported by the test VM in whole MiB, or zero if unavailable
- random test ID and consent/schema versions
- server submission time

Firebase associates the report with a **persistent anonymous account ID** and receives connection and network metadata. Anonymous sign-in does not make this data completely anonymous.

Reports are not designed to contain passwords, clipboard text, guest files, screenshots, audio, filenames or file paths, or complete serial/logcat logs. The APK contains no Firebase administrator or service-account credentials. Reports come from client devices and are not proof that every feature works.

The developer can read and manage submitted reports in Firebase. The app can submit reports for its own anonymous account and read its own delivery confirmations. Testing and VM operation do not depend on sharing.

**Retention and deletion:** Reports are grouped by device manufacturer, model and configuration. There is currently no automatic expiry and no in-app option to delete reports already sent. Choosing **Continue without sharing** prevents the current test report from being uploaded; it does not remove earlier reports or the Firebase account.

## Other apps and guest operating systems

Ubuntu, Windows and any software you install inside them have their own privacy practices. This notice covers U-AVF, not those products.

## Русский

**Обновлено:** 22 ноября 2026 г. · **Версия:** U-AVF 1.2 · **Разработчик:** [dt0-imsyu](https://github.com/dt0-imsyu)

По вопросам конфиденциальности можно написать в [GitHub Issues](https://github.com/dt0-imsyu/U-AVF/issues). Не публикуйте там пароли, токены и личные файлы виртуальных машин.

### Что остаётся на устройстве

Диски Ubuntu и Windows хранятся на вашем Android-устройстве. Изображение, звук, ввод с клавиатуры и мыши, а также необязательный обмен текстом через буфер обмена работают локально между U-AVF и виртуальной машиной. Разработчику эти данные не отправляются.

Обмен буфером выключен по умолчанию. Включайте его только для виртуальной машины, которой доверяете.

При обновлении APK Ubuntu и Windows не переустанавливаются. Но удаление воркспейса или очистка данных U-AVF может удалить виртуальный диск. Сделайте резервную копию важных файлов.

При установке Windows приложение читает выбранный вами ISO. Для подготовки оно временно сохраняет в своём хранилище отдельную копию установочного образа WinPE, а затем удаляет её. Содержимое ISO не загружается в облако.

### Отчёты о совместимости

Проверка совместимости выполняется прямо на устройстве независимо от того, согласились ли вы отправлять отчёт. Приложение сохраняет **12 последних завершённых отчётов**: их можно найти, скопировать или экспортировать вручную. Экспортированные логи могут содержать сведения об устройстве и вывод консоли виртуальной машины — просмотрите их перед отправкой.

При первом запуске U-AVF предлагает выбрать, отправлять ли результаты. Изменить решение можно на экране проверки совместимости. Приложение проверяет согласие перед запуском теста и ещё раз перед отправкой.

**Если вы согласились**, U-AVF выполняет анонимный вход в Google Firebase и отправляет ограниченный отчёт в проект Cloud Firestore разработчика `u-avf-7481c`. Это помогает выяснять, какие устройства могут поддерживаться.

В отчёт входят:

- версии U-AVF и Android, а также уровень Android API
- нормализованные производитель и модель устройства
- приблизительная категория объёма RAM и число процессоров, доступных приложению
- результаты локальных проверок AVF
- объём памяти тестовой VM в целых МиБ (или ноль, если он неизвестен)
- случайный идентификатор теста и версии согласия/схемы
- время отправки на сервер

Firebase связывает отчёт с **постоянным идентификатором анонимного аккаунта** и получает сетевые данные о подключении. Поэтому анонимный вход не означает полной анонимности.

В отчёт намеренно не включаются пароли, текст из буфера обмена, файлы гостевой системы, скриншоты, звук, названия и пути файлов, полные логи последовательного порта и logcat. В APK нет административных ключей Firebase. Такие отчёты отражают результаты, присланные устройством, но не гарантируют работоспособность всех функций.

Разработчик может просматривать и обрабатывать отправленные отчёты в Firebase. Приложение может отправлять отчёты от своего анонимного аккаунта и получать подтверждения доставки. Отправка не обязательна для работы VM и самой проверки совместимости.

**Хранение и удаление:** Отчёты группируются по производителю, модели и конфигурации устройства. Сейчас нет автоматического срока удаления и функции удаления ранее отправленных отчётов из приложения. Кнопка **«Продолжить без отправки»** отключает загрузку текущего отчёта, но не удаляет прошлые отчёты и аккаунт Firebase.

### Другие приложения и гостевые системы

Ubuntu, Windows и программы, которые вы устанавливаете внутри них, работают по собственным правилам конфиденциальности. Это уведомление относится к U-AVF, а не к ним.
