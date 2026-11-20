# U-AVF

Ubuntu Desktop and experimental Windows 11 ARM64 on Android Virtualization
Framework. U-AVF 1.2 is in preparation. The current public release is
[U-AVF 1.0.1](https://github.com/dt0-imsyu/U-AVF/releases/tag/v1.0.1).

## English

U-AVF requires an ARM64 device with Android 16 (API 36) or newer, usable AVF and
custom-VM permissions. It runs without root, an unlocked bootloader or a
modified system image. The reference device is the Samsung Galaxy Tab S11.

### What’s in 1.2

- **Ubuntu 24.04.5 Desktop ARM64.** Select the official ISO from Android’s file
  picker. A managed workspace keeps the installed system and virtual disk.
  The installer offers GNOME X11 with H.264/OpenGL or experimental Mavk, and
  experimental KDE Plasma X11 with Mavk. The KDE option downloads packages on
  first setup. H.264/VirGL remains the stable display path.
- **Windows 11 ARM64 (Experimental).** Open the Windows workspace and select
  Windows 11 ARM64 25H2 (ru-RU), build **26200.6584**, in Android’s file picker.
  The [Microsoft Arm64 download page](https://www.microsoft.com/en-us/software-download/windows11arm64)
  tracks the current release, which may differ from this profile.
  The profile also requires the matching verified WinPE template. The wizard verifies
  the media, prepares a persistent disk and prompts before installing Windows.
  WinPE, installation and reboot into the installed system have been tested
  on Tab S11. Other ISO builds and editions are outside this profile.
- **New interface.** English, Russian, Spanish, German, Simplified Chinese and
  Japanese. Workspace controls cover keyboard, mouse, touch, optional system
  key capture, display mode and resolution. Available resolutions depend on
  the guest and display path. The verified Windows profile uses 1920×1200.
- **Compatibility and diagnostics.** A local AVF test checks VM creation,
  boot, memory, storage and vsock. Diagnostics support search, copy and
  English-language log export. Sending the compatibility result to Firebase
  uses the opt-in switch on the compatibility screen.

Mavk/AHB graphics are experimental on Ubuntu. Some apps, compositor animations
and desktop configurations remain limited. Windows uses the existing display
path. Hardware D3D, WDDM and DWM acceleration are still in development.

### Setup and updates

Use a signed APK and update in place with the same signing key to retain your
workspace. An installed Ubuntu workspace boots from its existing disk without
the separate ISO download. Separate QA apps keep their own workspaces. Follow the in-app AVF
permission instructions, then choose an OS
from the dashboard. Use the official Ubuntu ISO or the supported Windows media
described above. To try Ubuntu from ISO, open **Systems → Temporary live session**.
Reinstalling, deleting a workspace or clearing Android app
data can erase a virtual disk.

## Русский

Для U-AVF нужны ARM64, Android 16 (API 36) или новее, работающий AVF и
разрешения для пользовательских VM. Root, разблокировка загрузчика и
изменение системного образа не нужны. Основное тестовое устройство —
Samsung Galaxy Tab S11.

### Что входит в 1.2

- **Ubuntu 24.04.5 Desktop ARM64.** Официальный ISO выбирается в системном
  файловом менеджере Android. Установленная система остаётся на постоянном
  виртуальном диске. Установщик предлагает GNOME X11 с H.264/OpenGL или
  экспериментальным Mavk, а также экспериментальный KDE Plasma X11 с Mavk.
  Для KDE при первой настройке загружаются пакеты. H.264/VirGL остаётся
  стабильным способом вывода.
- **Windows 11 ARM64 (Experimental).** В воркспейсе Windows выберите
  ISO Windows 11 ARM64 25H2 (ru-RU), сборка **26200.6584**, в файловом менеджере Android.
  [Страница загрузки Microsoft](https://www.microsoft.com/ru-ru/software-download/windows11arm64)
  предлагает актуальный выпуск, который может отличаться от этого профиля.
  Для этого профиля также нужен проверенный шаблон WinPE. Мастер проверяет
  файлы, готовит постоянный диск и запрашивает подтверждение перед установкой.
  WinPE, установка и повторная загрузка установленной системы проверены на
  Tab S11. Другие ISO и редакции не входят в этот профиль.
- **Новый интерфейс.** Русский, английский, испанский, немецкий, упрощённый
  китайский и японский. В воркспейсе доступны клавиатура, мышь, сенсорный ввод,
  опциональный перехват системных клавиш, настройка вывода и разрешения.
  Доступные разрешения зависят от гостя и режима вывода. Проверенный профиль
  Windows работает при 1920×1200.
- **Совместимость и диагностика.** Локальный тест AVF проверяет создание и
  загрузку VM, память, накопитель и vsock. В диагностике есть поиск,
  копирование и экспорт английских логов. Отправка результата в Firebase
  управляется переключателем на экране проверки и требует согласия.

Mavk/AHB на Ubuntu остаётся экспериментальным. Для части приложений,
анимаций и конфигураций рабочего стола есть ограничения. Windows использует
существующий вывод. Аппаратный D3D, WDDM и ускорение DWM в разработке.

### Установка и обновление

Используйте подписанный APK и обновляйте приложение поверх установленного
с тем же ключом подписи, чтобы сохранить воркспейс. Установленная Ubuntu
загружается со своего диска без отдельного файла ISO. У отдельных тестовых
приложений свои воркспейсы. Пройдите проверку AVF
по инструкции приложения и выберите ОС на главном экране. Запуск Ubuntu из ISO
доступен в **Системы → Временная Live-сессия**. Переустановка,
удаление воркспейса или очистка данных приложения могут удалить виртуальный диск.

## Licensing and privacy / Лицензии и приватность

The repository uses multiple licenses. First-party scope, earlier Apache
rights and third-party terms are documented separately. The APK build requires
a retained binary-payload base. Corresponding-source and redistribution
checks remain open for some bundled components.

В репозитории несколько лицензий. Область собственной лицензии, прежние права
Apache и условия сторонних компонентов описаны отдельно. Для сборки APK нужна
сохранённая база с бинарными компонентами. Для части из них ещё требуется
проверить комплект исходников и условия распространения.

[License / Лицензия](LICENSE) ·
[License scope / Область лицензии](LICENSE_SCOPE.md) ·
[Third-party notices / Сторонние компоненты](docs/release-preparation/THIRD_PARTY_COMPLIANCE.md) ·
[Privacy / Приватность](docs/release-preparation/PRIVACY.md) ·
[Compatibility data / Статистика совместимости](backend/firebase/README.md)
