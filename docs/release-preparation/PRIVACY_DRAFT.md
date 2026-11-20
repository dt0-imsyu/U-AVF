# Privacy notice — draft, not a published policy

U-AVF runs a user-started local VM. Clipboard, input, display and audio bridges
are local channels between Android and the guest; they are not intended to upload
their contents to the developer. Diagnostics export is a separate user action.

Optional compatibility-report sharing defaults to off. With explicit consent,
the current client uses Firebase anonymous authentication and submits bounded
device/build identifiers and test results. It does not intentionally include
clipboard contents, passwords, guest files, audio, screenshots or raw serial logs.
Anonymous Firebase authentication still creates an identifier: do not describe
these reports as incapable of association with a client/device configuration.

Before publishing: verify the actual fields against the final APK, define retention,
deletion/contact procedure and controller identity, review Firebase security rules
and collection abuse controls, and include the final policy in onboarding/settings.
No retention promise or public deletion endpoint has been established yet.

## Русский

Буфер, ввод, картинка и звук передаются локально между Android и VM. Добровольная
отправка теста совместимости выключена по умолчанию; при согласии используются
Firebase anonymous auth, сведения об устройстве/сборке и результаты тестов.
«Anonymous auth» не означает отсутствие идентификатора. Перед релизом нужно
подтвердить поля, срок хранения, процедуру удаления и контакт ответственного лица.
