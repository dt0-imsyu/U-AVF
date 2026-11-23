// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

/** The in-app privacy notice, ordered by UiLanguage.NAMES. */
final class PrivacyPolicy {
    private PrivacyPolicy() {}

    static String current() {
        return switch (UiLanguage.selected()) {
            case 0 -> """
                    Локальная работа
                    Виртуальные диски, изображение, звук, ввод и текстовый буфер обмена остаются на устройстве. Общий буфер выключен по умолчанию. Диагностика хранит до 12 последних проверок. Экспорт выполняется вручную и может содержать сведения об устройстве и вывод гостевой системы.

                    Отчёты о совместимости
                    С вашего согласия U-AVF отправляет в Google Firebase отчёт о проверке AVF. В отчёте указаны производитель и модель, конфигурация памяти и процессоров, версии приложения и Android, уровень API, результаты тестов, случайный ID проверки, версия схемы, отметка согласия и время отправки. Отчёты группируются по производителю, модели и конфигурации. Разработчик использует их для расширения списка поддерживаемых устройств.

                    Firebase связывает отчёты с идентификатором анонимного аккаунта и получает сетевые метаданные. В отчёт не входят пароли, буфер обмена, файлы гостевой системы, скриншоты, звук или полные технические журналы.

                    Ваш выбор
                    Отправку можно отключить на экране проверки. Проверка и виртуальные машины работают и без неё. Отключение не удаляет ранее отправленные отчёты. Сейчас для них нет автоматического срока удаления или кнопки удаления в приложении.

                    Связь с разработчиком
                    dt0-imsyu · https://github.com/dt0-imsyu/U-AVF/issues
                    Не публикуйте пароли, токены и личные файлы.
                    """;
            case 2 -> """
                    Funcionamiento local
                    Los discos virtuales, la imagen, el audio, la entrada y el portapapeles permanecen en el dispositivo. El portapapeles compartido está desactivado de forma predeterminada. Diagnóstico guarda las últimas 12 pruebas. La exportación es manual y puede incluir información del dispositivo y mensajes del sistema invitado.

                    Informes de compatibilidad
                    Con tu consentimiento, U-AVF envía a Google Firebase un informe de la prueba AVF. Incluye fabricante, modelo, configuración de memoria y procesadores, versiones de la aplicación y Android, nivel de API, resultados, ID aleatorio de la prueba, versión del esquema, consentimiento y hora de envío. Los informes se agrupan por fabricante, modelo y configuración para ampliar la lista de dispositivos compatibles.

                    Firebase asocia los informes a un identificador de cuenta anónima y recibe metadatos de conexión. Los informes no incluyen contraseñas, contenido del portapapeles, archivos del invitado, capturas de pantalla, audio ni registros técnicos completos.

                    Tu elección
                    Puedes desactivar el envío en la pantalla de comprobación. Las pruebas y las máquinas virtuales funcionan igualmente. Desactivar el envío no borra los informes previos. No hay un plazo automático de eliminación ni una opción para borrarlos desde la aplicación.

                    Contacto con el desarrollador
                    dt0-imsyu · https://github.com/dt0-imsyu/U-AVF/issues
                    No publiques contraseñas, tokens ni archivos privados.
                    """;
            case 3 -> """
                    Lokaler Betrieb
                    Virtuelle Laufwerke, Bild, Ton, Eingaben und die Text-Zwischenablage bleiben auf dem Gerät. Die gemeinsame Zwischenablage ist standardmäßig aus. Die Diagnose speichert bis zu zwölf aktuelle Prüfergebnisse. Ein Export erfolgt manuell und kann Geräteinformationen und Ausgaben des Gastsystems enthalten.

                    Kompatibilitätsberichte
                    Mit Ihrer Einwilligung sendet U-AVF den AVF-Prüfbericht an Google Firebase. Er enthält Hersteller, Modell, Speicher- und Prozessorkonfiguration, App- und Android-Version, API-Stufe, Prüfergebnisse, eine zufällige Prüf-ID, Schemaversion, Einwilligung und Übermittlungszeit. Der Entwickler gruppiert die Berichte nach Hersteller, Modell und Konfiguration, um mehr Geräte unterstützen zu können.

                    Firebase ordnet die Berichte einer anonymen Konto-ID zu und erhält Verbindungsmetadaten. Passwörter, Zwischenablageinhalte, Gastdateien, Bildschirmfotos, Audio und vollständige technische Protokolle sind nicht Teil des Berichts.

                    Ihre Entscheidung
                    Sie können die Übermittlung auf der Prüfseite deaktivieren. Prüfungen und virtuelle Maschinen funktionieren weiterhin. Das Ausschalten löscht keine früheren Berichte. Es gibt weder eine automatische Löschfrist noch eine Löschfunktion in der App.

                    Kontakt zum Entwickler
                    dt0-imsyu · https://github.com/dt0-imsyu/U-AVF/issues
                    Veröffentlichen Sie keine Passwörter, Tokens oder privaten Dateien.
                    """;
            case 4 -> """
                    本地运行
                    虚拟磁盘、画面、音频、输入和文本剪贴板均保留在设备上。默认关闭共享剪贴板。诊断功能保留最近 12 次检查。诊断报告由用户手动导出，可能包含设备信息和虚拟机的输出。

                    兼容性报告
                    经你同意，U-AVF 会向 Google Firebase 发送 AVF 测试报告。报告包括制造商、型号、内存和处理器配置、应用和 Android 版本、API 级别、测试结果、随机测试标识、报告格式版本、同意记录和提交时间。开发者按制造商、型号和配置归类报告，以扩大支持的设备范围。

                    Firebase 将报告与匿名账号标识关联，并接收网络连接元数据。报告不含密码、剪贴板内容、虚拟机文件、截图、音频或完整技术日志。

                    你的选择
                    可在兼容性检查页面关闭发送。关闭后仍可进行检查和运行虚拟机。关闭发送不会删除已提交的报告。目前没有自动删除期限，也无法在应用内删除已提交的报告。

                    联系开发者
                    dt0-imsyu · https://github.com/dt0-imsyu/U-AVF/issues
                    请勿公开密码、令牌或私人文件。
                    """;
            case 5 -> """
                    端末内での動作
                    仮想ディスク、画面、音声、入力、テキストのクリップボードは端末内に留まります。共有クリップボードは初期設定でオフです。診断には直近 12 件の検査結果が保存されます。手動で書き出すレポートには端末情報やゲストの出力が含まれる場合があります。

                    互換性レポート
                    同意した場合、U-AVF は AVF の検査結果を Google Firebase に送信します。メーカー、モデル、メモリと CPU の構成、アプリと Android のバージョン、API レベル、検査結果、ランダムな検査 ID、スキーマのバージョン、同意記録、送信時刻が含まれます。開発者はメーカー、モデル、構成ごとに結果を分類し、対応端末の拡大に役立てます。

                    Firebase はレポートを匿名アカウントの識別子に関連付け、接続のメタデータを受け取ります。レポートにパスワード、クリップボードの内容、ゲストのファイル、画面キャプチャ、音声、完全な技術ログは含まれません。

                    送信の設定
                    互換性検査画面で送信を無効にできます。無効にしても検査や仮想マシンは使えます。すでに送信したレポートは削除されません。自動削除の期限やアプリ内の削除機能はありません。

                    開発者への連絡
                    dt0-imsyu · https://github.com/dt0-imsyu/U-AVF/issues
                    パスワード、トークン、個人ファイルは公開しないでください。
                    """;
            default -> """
                    Local operation
                    Virtual disks, display, audio, input and the text clipboard stay on your device. Clipboard sharing is off by default. Diagnostics keeps the last 12 checks. You can export reports manually. Exports may include device details and guest output.

                    Compatibility reports
                    With your consent, U-AVF sends the AVF test report to Google Firebase. It includes manufacturer, model, memory and CPU configuration, app and Android versions, API level, test outcomes, a random test ID, the schema version, your consent and the submission time. The developer groups reports by manufacturer, model and configuration to support more devices.

                    Firebase associates reports with an anonymous account identifier and receives connection metadata. Reports exclude passwords, clipboard contents, guest files, screenshots, audio and full technical logs.

                    Your choice
                    Turn sharing off on the compatibility screen. Testing and virtual machines still work. Turning it off does not delete earlier reports. There is no automatic expiry or in-app deletion for submitted reports.

                    Developer contact
                    dt0-imsyu · https://github.com/dt0-imsyu/U-AVF/issues
                    Keep passwords, tokens and private files out of public issues.
                    """;
        };
    }
}
