# U-AVF

Non-root Ubuntu Desktop ARM64 on Android Virtualization Framework.
[Download U-AVF 1.0](https://github.com/dt0-imsyu/U-AVF/releases/tag/v1.0.0).

## English

U-AVF launches an installed Ubuntu GNOME desktop without rooting Android, unlocking
the bootloader or flashing. Tested on Samsung Galaxy Tab S11 (GenieZone/crosvm).
The official Ubuntu ISO is not modified. Other devices require their own validation.
The current APK requires Android 16 / API 36 or newer, ARM64 and usable AVF/custom-VM permissions.

### What works

- Managed persistent workspace and automatic Ubuntu installation/bootstrap.
- Hardware OpenGL through VirGL/Mali; encoded 1920×1200 display with 60 FPS capability.
- Keyboard, mouse, touch, internet and audio on the tested configuration.
- Opt-in text clipboard; sharing can be toggled without restarting the VM.
- Device prerequisite checks, workspace settings, diagnostics and storage growth.

Hardware Vulkan is not available on the tested host. Windows remains research-only;
Windows/WinPE desktop is not achieved. Shared folder and universal ISO support are
not promised. Installer flicker, slow App Center and installed-status transition
after the first reboot remain known issues. See [release notes](docs/release-preparation/RELEASE_NOTES.md).

### Setup

1. Install the official signed APK from Releases; do not uninstall an existing app
   to bypass a signature mismatch. Normal same-key updates preserve the workspace.
2. Complete onboarding and the real device prerequisite check. If AVF permissions
   are missing, follow the app's USB-debugging/ADB instructions:
   `adb shell pm grant com.example.winavf android.permission.MANAGE_VIRTUAL_MACHINE`
   and `adb shell pm grant com.example.winavf android.permission.USE_CUSTOM_VIRTUAL_MACHINE`.
   Availability depends on the device; a grant command alone does not prove support.
3. Download **Ubuntu 24.04.5 Desktop ARM64** from the official Ubuntu page opened
   by the app. Select the ISO in U-AVF; it checks the supported file.
4. Create a workspace, choose root storage, then Install. Follow Ubuntu Setup for
   language/keyboard/account. Preserve the managed storage layout.
5. Reboot after installation and wait for bootstrap. Use Launch Ubuntu thereafter.
   Temporary Live Session intentionally boots the ISO, not your installed system.
6. Enable Share text clipboard under Input if desired. Reinstall/Delete erase
   Ubuntu data. Clearing Android application data may remove private workspaces.

[Detailed quick start](docs/release-preparation/QUICK_START.md) ·
[Privacy notice](docs/release-preparation/PRIVACY.md) ·
[Third-party compliance](docs/release-preparation/THIRD_PARTY_COMPLIANCE.md).

### Licensing and development

This is a multi-license repository. Only the original files in [LICENSE_SCOPE.md](LICENSE_SCOPE.md)
use [U-AVF Source-Available License 1.0](LICENSE-UAVF.txt), requiring permission for
reuse in another product. Older Apache rights remain; third-party components retain
their licenses. See [LICENSE](LICENSE). This is not a blanket open-source license.

A fresh clone does not include all large platform payloads and cannot build the
tested release standalone. The release build script accepts the official APK as
the tested binary-payload base; third-party source/build provenance review remains incomplete.
No guest disks, personal logs, signing keys or Windows media belong in Git.

## Русский

U-AVF запускает установленную Ubuntu GNOME через AVF без root, разблокировки и
прошивки. Проверенный планшет — Samsung Galaxy Tab S11. Официальный ISO не изменяется.
Текущий APK требует Android 16 / API 36 или новее, ARM64 и доступные разрешения AVF/custom VM.

Работают постоянная Ubuntu, установка/bootstrap, аппаратный OpenGL VirGL/Mali,
кодированный вывод 1920×1200 с возможностью 60 FPS, ввод, сеть и звук. Добровольный
текстовый буфер можно включать без перезапуска. Аппаратного Vulkan и рабочего Windows desktop нет. Универсальные ISO и
общая папка не обещаются. Сохраняются мерцание installer и медленный App Center.

Установите подписанный APK из Releases, пройдите тест совместимости и выдайте
указанные приложением AVF-разрешения через ADB. Скачайте официальный Ubuntu 24.04.5
**Desktop ARM64**, выберите ISO, создайте workspace с нужным размером и нажмите
Install. Задайте язык/клавиатуру/учётную запись, не меняя служебную разметку.
После reboot дождитесь первого запуска. Далее используйте Launch Ubuntu, а не
Temporary Live Session. Reinstall/Delete удаляют Ubuntu; очистка данных APK тоже
может удалить workspace. Обновления с той же подписью не требуют переустановки.

Новая source-available лицензия касается только перечисленных новых собственных
файлов. Чужие компоненты и старые Apache-права остаются прежними. Полная проверка
исходников/сборки сторонних компонентов ещё не завершена.

Статистика совместимости добровольная и по умолчанию выключена. При согласии
результаты идут в облачную Firebase для разработки и оптимизации, не в локальную
базу. Пароли, гостевые файлы и буфер туда не отправляются. Подробнее:
[Privacy](docs/release-preparation/PRIVACY.md).
