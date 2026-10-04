# U-AVF 1.0

APK version1.0/code14. SHA256:
`4C0AAFC1BAB2BF3000CBFDEB473C925FFA65DF15D151D390E26550D80B4184D8`.

Ubuntu Desktop ARM64 on stock Android through AVF — no root, unlock or flashing.
Tested on Samsung Galaxy Tab S11, Android16. Other devices need their own checks.

## English

- Guided official Ubuntu24.04.5 Desktop ARM64 installation and persistent boot.
- Normal GDM login, GNOME desktop and Firefox Snap verified on the test device.
- Hardware OpenGL via VirGL/Mali; encoded1920x1200 display with60FPS capability.
  Performance varies with workload/device; hardware Vulkan is not available here.
- Keyboard, mouse, touch, network and audio runtime; optional text clipboard.
- Workspaces, real compatibility checks, settings and diagnostics.
- Firebase compatibility reports are opt-in and OFF by default. Privacy details
  are available before consent. No guest files, clipboard, audio or screenshots
  are intentionally sent. No automatic report expiry/in-app deletion exists yet.

Install **U-AVF.1.0.apk**, then use the in-app ADB permission instructions and
select the official Ubuntu24.04.5 Desktop ARM64 ISO from Ubuntu's site. ISO is
not bundled. Preserve the managed installer layout; do not erase the whole disk.
Same-key APK updates preserve workspaces. Do not uninstall to bypass a signature
mismatch: debug-to-release migration requires a verified backup first.

Known limitations: installer flicker, slow App Center, installed status after
first reboot. Windows remains research-only. Shared folders, hardware Vulkan
and arbitrary ISO support are not release promises. Real disk-growth and current
clipboard hot-toggle acceptance were waived, not marked PASS. Full third-party
source/build/relink compliance review is still incomplete, not certified by this
release. Prior Apache and third-party licenses remain; new original code has
the explicit U-AVF source-available scope, not a blanket proprietary license.

## Русский

U-AVF запускает Ubuntu Desktop ARM64 через AVF без root и разблокировки.
Проверено на Samsung Galaxy Tab S11 / Android16. Есть установка и постоянный
диск, обычный вход GNOME, аппаратный OpenGL VirGL/Mali, кодированная картинка
1920×1200 с возможностью60FPS, ввод, сеть и звук. FPS зависит от нагрузки.

Установи APK, выдай разрешения по подсказке приложения, скачай официальный
Ubuntu24.04.5 Desktop ARM64 ISO и выбери его в приложении. Во время установки
сохрани служебную разметку. После reboot запускай установленную Ubuntu.
Обновления с той же подписью не удаляют workspace. Reinstall/Delete и очистка
данных могут удалить систему — сначала сделай резервную копию.

Отправка результатов теста в облачную Firebase добровольная и выключена по
умолчанию. Условия доступны до согласия. Пароли, гостевые файлы, буфер, звук и
скриншоты намеренно не отправляются. Автоматического удаления отчётов пока нет.

Известны мерцание установщика, медленный App Center и обновление статуса после
первой перезагрузки. Аппаратный Vulkan и рабочая Windows пока не поддерживаются.
Полная проверка сторонних исходников/сборки ещё не завершена. Лицензии старого
Apache-кода и сторонних компонентов не изменяются новой лицензией своих частей.
