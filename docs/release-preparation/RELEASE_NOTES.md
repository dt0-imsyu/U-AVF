# U-AVF 1.0 — release candidate notes

Prepared 2026-10-04. Internal APK 1.0-rc.5/code13. Publication: HOLD.

RC5 explicitly discloses optional Firebase cloud reporting for compatibility,
optimization and development. Reports are not local-only. Consent defaults off;
privacy retention/contact/deletion and live backend verification remain gates.
Runtime and capture/encoder paths are unchanged from tested RC4.

## English

U-AVF runs Ubuntu Desktop ARM64 through Android Virtualization Framework without
root, bootloader unlock or flashing. Tested: Samsung Galaxy Tab S11. Other devices
are not certified by prerequisite checks alone.

Established on the test device:

- Persistent Ubuntu GNOME and automatic installer/bootstrap/installed boot.
- Hardware OpenGL: VirGL → Mali; encoded 1920×1200 display with 60 FPS capability.
  This is not a guarantee of 60 FPS for every workload.
- Keyboard/mouse/touch, network and audio. User reports the current installation works.
- Workspace settings, real prerequisite checks, diagnostics, root-storage selection
  and expansion. Expansion acceptance for every layout/device remains pending.

This candidate adds immediate opt-in text clipboard enable/disable and channel diagnostics.
It preserves installer-selected account/login policy and reconnects encoded display
when GDM hands off to the user session. A clean custom-account install and repeat
login passed in the isolated QA package; Firefox Snap opened normally.
Installed Ubuntu already has its clipboard socket. New-candidate end-to-end tablet acceptance
is pending; do not call clipboard fully validated from the socket marker alone.

Release hardening: no shared fallback installer password; post-install readiness
requires an unlocked user account. Reports use the actual package version.
Settings includes licenses, privacy and bundled third-party notices. The identity
template/guard pass offline checks but require a disposable clean-install test.
The actual production display/audio/input implementations are unchanged.

Known issues accepted for preparation, not fixed here:

1. Installer may flicker.
2. App Center may feel slow.
3. Installed state is recognized after the first reboot/bootstrap.
4. Clipboard sharing is optional/default off; RC7 requires restart after enabling.

Limitations:

- Guided installation is supported only with Ubuntu 24.04.5 Desktop ARM64.
  Other ISO versions are not certified or universally supported.
- Hardware Vulkan is blocked on this host; software Vulkan is not hardware acceleration.
- Windows is research-only: no confirmed WinPE/Windows desktop.
- Shared folder is not a release promise. Some lifecycle/input edge cases still
  need final acceptance.
- No public release until legal/source/privacy/signing recovery and smoke gates close.

## Русский

Проверенный сценарий: установленная Ubuntu GNOME, автоматическая установка и
загрузка, постоянный диск, аппаратный OpenGL VirGL/Mali, кодированный вывод
1920×1200 с возможностью 60 FPS, ввод, сеть и звук. Пользователь подтвердил работу
текущей установки. Это не сертификация всех планшетов и не обещание 60 FPS везде.

Кандидат исправляет применение включения буфера без перезапуска; агент/socket уже есть
в Ubuntu. Проверка самого обмена в новом APK ещё нужна. Он добровольный, по умолчанию
выключен. Сохраняются мерцание installer, медленный App Center и переход статуса
«установлено» после первого reboot. Поддерживаемый installer — Ubuntu 24.04.5
Desktop ARM64. Аппаратный Vulkan/рабочая Windows не готовы. Публикация запрещена.
