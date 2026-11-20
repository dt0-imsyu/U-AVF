# U-AVF 1.0 preview — RC5

**Private draft; not approved for public distribution. Do not publish this draft
until corresponding-source, privacy/backend and final source/tag checks close.**

U-AVF runs Ubuntu Desktop ARM64 using Android Virtualization Framework without
root or bootloader unlock. Tested on Samsung Galaxy Tab S11 / Android16.

- Guided official Ubuntu24.04.5 Desktop ARM64 installation and persistent boot.
- Installer-selected account and real GDM login; GNOME and Firefox Snap tested.
- Hardware OpenGL via VirGL/Mali; production encoded1920x1200 display backend.
  Existing60FPS capability is not a guarantee for every workload/device.
- Keyboard/mouse/touch, network and audio runtime; optional text clipboard.
- Compatibility checks and explicit opt-in Firebase cloud report sharing for
  compatibility analysis, optimization and development. Sharing defaults off.

GitHub APK asset: **U-AVF.1.0.apk**, version1.0-rc.5/code13, AndroidAPI36+ ARM64.
SHA256: `7104BA1221455B6F2E8098298E25C875C7D3F813FFAD7B3F07139EBEBD8B0295`.

Updates using the same release signing key preserve workspaces. Debug-signed
builds require a separate migration/backup plan; do not clear app data to bypass
a signature mismatch. Obtain the official Ubuntu ISO separately.

Known limitations: installer flicker, slow App Center, installed-state transition
after first reboot; hardware Vulkan unavailable on the tested host; Windows is
research-only. Clipboard hot-toggle and real storage growth acceptance were
waived by the owner, not certified PASS. RC4 is the device-tested runtime baseline;
RC5 changes cloud-consent/privacy wording and preserves all43 runtime payloads.

Publication blockers: incomplete source/build/relink provenance, unfinished
Firebase retention/deletion/contact and deployed-rule verification, independent
signing-key recovery, remaining device acceptance and an accurate source commit
for the final tag. Draft target currently references the existing repository HEAD,
not the uncommitted candidate tree; update the target before publication.

## Русский

Это непубличный черновик кандидата U-AVF1.0, не готовый опубликованный релиз.
Работают установка и постоянная Ubuntu, обычный вход, аппаратный OpenGL и
кодированный вывод. Firebase — добровольная внешняя облачная отправка, не
локальная база. Подготовка исходников сторонних компонентов и политики обработки
отчётов ещё не завершена. Этот черновик нельзя публиковать, просто сняв флаг Draft.
