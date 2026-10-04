# U-AVF 1.0 setup

## English

Requirements: Android16/API36 or newer, ARM64, AVF and available custom-VM permissions.

1. Download the official signed APK from GitHub Releases and verify it against SHA256SUMS.
2. For a new installation use the release-mode APK. Do not install it over the
   current debug app: the signatures differ. Do not uninstall/clear app data as a
   workaround; first plan a verified workspace backup/migration. Updates signed
   by the same release key do not inherently require deleting an Ubuntu workspace.
3. Complete the device prerequisite check. Grant only the explained permissions.
   Optional compatibility-report sharing is not required to run a VM.
4. Choose the official **Ubuntu24.04.5 Desktop ARM64** ISO. Use the app's verified
   website link or select the exact file yourself. The app opens the Ubuntu page;
   it does not automatically download the ISO. Expected bytes:3967463424;
   SHA256:`2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14`.
   Windows/x86 images and Ubuntu Server are not interchangeable with this profile.
5. Create the workspace, choose Ubuntu root storage and start installation.
   Additional space is needed for the platform/ISO and Android free-space reserve.
6. Choose language, keyboard and your own account in Ubuntu Setup. Do not replace
   the managed storage layout or choose whole-disk erase.
7. Finish installation and reboot. Wait for the installed-system bootstrap and
   first valid desktop. Installation status may not change until the first reboot.
8. Later use Launch Ubuntu. Temporary Live Session intentionally starts the live
   environment; it is not the installed-system launch button.
9. For optional text sharing enable Workspace settings → Input → Share text
   clipboard. This candidate applies it immediately; older RC7 applies it on next VM start.

Reinstall and Delete workspace erase Ubuntu data. Back up first. Expanding storage
requires a stopped VM and a verified guest growth helper. Never interrupt disk
operations. Keep the tablet powered. Workspaces remain app-private; uninstalling
or clearing application data can remove them. Hardware OpenGL is supported in the
tested configuration; hardware Vulkan is not.

## Русский

Требования: Android16/API36 или новее, ARM64, AVF и доступные custom-VM разрешения.

Проверь SHA APK. Новая release-подпись отличается от прежней debug-подписи;
не удаляй старое приложение ради установки без проверенного переноса Ubuntu.
Для новой установки установи APK и пройди тест устройства.
Выбери официальный Ubuntu24.04.5 **Desktop ARM64**, создай workspace и выбери размер
Ubuntu. В установщике задай язык, клавиатуру и свою учётную запись; не стирай весь
виртуальный диск и не меняй служебную разметку. После завершения перезагрузи Ubuntu
и дождись первого запуска. Далее используй Launch Ubuntu, а не Temporary Live
Session. Reinstall/Delete удаляют данные Ubuntu. Очистка данных приложения или
удаление APK также может удалить приватный workspace. Обновление APK само по себе
не требует переустановки Ubuntu. Сначала делай резервную копию важных файлов.
