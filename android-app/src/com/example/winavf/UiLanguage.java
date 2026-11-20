// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** In-app language, independent of Android and the guest. Journal data is never translated here. */
final class UiLanguage {
    private static final String[] CODES={"ru","en","es","de","zh-Hans","ja"};
    static final String[] NAMES={"Русский","English","Español","Deutsch","简体中文","日本語"};
    private static final Map<String,String[]> STRINGS=new HashMap<>();
    private static volatile int language;

    static void load(Context context) {
        String saved=context.getSharedPreferences("product_ui",0).getString("language","ru");
        for(int i=0;i<CODES.length;i++)if(CODES[i].equals(saved)){language=i;return;}
        language=0;
    }

    static void select(Context context,int index) {
        if(index<0 || index>=CODES.length)throw new IllegalArgumentException("Unsupported interface language");
        language=index;
        context.getSharedPreferences("product_ui",0).edit().putString("language",CODES[index]).apply();
    }

    static int selected() { return language; }
    static Locale locale() { return Locale.forLanguageTag(CODES[language]); }
    static String text(String value) {
        String[] translation=STRINGS.get(value);
        return translation==null?value:translation[language];
    }
    static String english(String value) {
        String[] translation=STRINGS.get(value);
        return translation==null?value:translation[1];
    }
    static String format(String value,Object... parameters) {
        return String.format(locale(),text(value),parameters);
    }

    static {
        String data0="""
            Язык|Language|Idioma|Sprache|语言|言語
            Статистика совместимости|Compatibility statistics|Estadísticas de compatibilidad|Kompatibilitätsstatistik|兼容性统计|互換性の統計
            Разрешите отправить результат этого теста в Firebase. Это поможет нам расширить список поддерживаемых устройств. Передаются модель, конфигурация и результаты проверки AVF.|Allow this test result to be sent to Firebase to help us support more devices. It includes the device model, configuration and AVF check results.|Permite enviar este resultado a Firebase para ayudarnos a ampliar la compatibilidad. Incluye el modelo, la configuración y los resultados de AVF.|Erlaube, dieses Testergebnis an Firebase zu senden, damit wir mehr Geräte unterstützen können. Übermittelt werden Gerätemodell, Konfiguration und AVF-Prüfergebnisse.|允许将本次测试结果发送到 Firebase，帮助我们支持更多设备。数据包含设备型号、配置和 AVF 检查结果。|対応端末を増やすため、このテスト結果の Firebase への送信を許可してください。送信内容は端末モデル、構成、AVF の検証結果です。
            Продолжить без отправки|Continue without sharing|Continuar sin compartir|Ohne Teilen fortfahren|继续但不发送|送信せずに続行
            Отправлять результаты|Share results|Compartir resultados|Ergebnisse teilen|分享结果|結果を共有
            Разрешить и начать|Allow and start|Permitir e iniciar|Erlauben und starten|允许并开始|許可して開始
            Рабочий стол Ubuntu|Ubuntu desktop|Escritorio de Ubuntu|Ubuntu-Desktop|Ubuntu 桌面|Ubuntu デスクトップ
            Выберите графический режим GNOME X11. Его можно изменить после установки.|Choose a graphics mode for GNOME X11. You can change it after installation.|Elige el modo gráfico de GNOME X11. Podrás cambiarlo tras la instalación.|Wähle den Grafikmodus für GNOME X11. Du kannst ihn nach der Installation ändern.|选择 GNOME X11 的图形模式。安装后可以更改。|GNOME X11 のグラフィックモードを選択してください。インストール後に変更できます。
            GNOME X11 · Mavk (экспериментально)|GNOME X11 · Mavk (experimental)|GNOME X11 · Mavk (experimental)|GNOME X11 · Mavk (experimentell)|GNOME X11 · Mavk（实验性）|GNOME X11 · Mavk（試験運用）
            Выберите рабочий стол и графику. Для загрузки Plasma нужен интернет.|Choose a desktop and graphics mode. Plasma requires an internet connection to download.|Elige el escritorio y el modo gráfico. Para descargar Plasma se necesita Internet.|Wähle Desktop und Grafikmodus. Zum Herunterladen von Plasma ist eine Internetverbindung nötig.|选择桌面和图形模式。下载 Plasma 需要联网。|デスクトップとグラフィックモードを選んでください。Plasma のダウンロードにはインターネット接続が必要です。
            Включите интернет в настройках Ubuntu или выберите GNOME. Plasma загружается при установке.|Enable internet access in Ubuntu settings or choose GNOME. Plasma is downloaded during installation.|Activa Internet en los ajustes de Ubuntu o elige GNOME. Plasma se descarga durante la instalación.|Aktiviere den Internetzugang in den Ubuntu-Einstellungen oder wähle GNOME. Plasma wird während der Installation heruntergeladen.|请在 Ubuntu 设置中启用网络，或选择 GNOME。Plasma 会在安装时下载。|Ubuntu の設定でインターネット接続をオンにするか、GNOME を選んでください。Plasma はインストール中にダウンロードされます。
            Для загрузки Plasma нужен интернет в Ubuntu.|Plasma download requires Ubuntu internet access.|La descarga de Plasma requiere acceso a Internet en Ubuntu.|Das Herunterladen von Plasma erfordert Internetzugang in Ubuntu.|下载 Plasma 需要 Ubuntu 能够联网。|Plasma のダウンロードには Ubuntu のインターネット接続が必要です。
            Ubuntu скачает Plasma из своих репозиториев. После установки перезапустите Ubuntu.|Ubuntu will download Plasma from its repositories. Restart Ubuntu after installation.|Ubuntu descargará Plasma de sus repositorios. Reinicia Ubuntu después de la instalación.|Ubuntu lädt Plasma aus seinen Paketquellen herunter. Starte Ubuntu nach der Installation neu.|Ubuntu 将从其软件源下载 Plasma。安装后请重启 Ubuntu。|Ubuntu は公式リポジトリから Plasma をダウンロードします。インストール後に Ubuntu を再起動してください。
            Plasma установлена. Перезапустите Ubuntu.|Plasma is installed. Restart Ubuntu.|Plasma ya está instalado. Reinicia Ubuntu.|Plasma ist installiert. Starte Ubuntu neu.|Plasma 已安装。请重启 Ubuntu。|Plasma をインストールしました。Ubuntu を再起動してください。
            Повторить установку|Retry installation|Reintentar instalación|Installation wiederholen|重试安装|インストールを再試行
            Проверить ISO|Check ISO|Comprobar ISO|ISO prüfen|检查 ISO|ISO を確認
            Установка|Setup|Instalación|Installation|安装|セットアップ
            Установка Windows ARM64|Windows ARM64 setup|Instalación de Windows ARM64|Windows ARM64 installieren|安装 Windows ARM64|Windows ARM64 のセットアップ
            Экспериментальная установка Windows|Experimental Windows setup|Instalación experimental de Windows|Experimentelle Windows-Installation|实验性 Windows 安装|試験的な Windows インストール
            Диск Windows подготовлен|Windows disk prepared|Disco de Windows preparado|Windows-Datenträger vorbereitet|Windows 磁盘已准备好|Windows ディスクを準備済み
            Диск Windows сохранён|Windows disk saved|Disco de Windows guardado|Windows-Datenträger gespeichert|Windows 磁盘已保存|Windows ディスクを保存済み
            Не удалось проверить разметку диска Windows. Подробности доступны в журнале.|The Windows disk layout could not be verified. See the log for details.|No se pudo verificar la estructura del disco de Windows. Consulta el registro para más detalles.|Das Layout des Windows-Datenträgers konnte nicht geprüft werden. Details stehen im Protokoll.|无法验证 Windows 磁盘分区。详情请参阅日志。|Windows ディスクの構成を確認できませんでした。詳細はログを参照してください。
            Найден раздел NTFS. Завершение установки не подтверждено. Повторный запуск пока недоступен.|An NTFS partition was found. Installation completion is unverified. Restart is currently unavailable.|Se encontró una partición NTFS. No se ha verificado que la instalación haya terminado. El reinicio aún no está disponible.|Eine NTFS-Partition wurde gefunden. Der Abschluss der Installation ist unbestätigt. Ein Neustart ist derzeit nicht verfügbar.|检测到 NTFS 分区。尚未确认安装已完成。目前无法重新启动。|NTFS パーティションが見つかりました。インストールの完了は未確認です。現在は再起動できません。
            Диск уже размечен. Повторный запуск пока недоступен.|The disk is already partitioned. Restart is currently unavailable.|El disco ya está particionado. El reinicio aún no está disponible.|Der Datenträger ist bereits partitioniert. Ein Neustart ist derzeit nicht verfügbar.|磁盘已分区。目前无法重新启动。|ディスクは既にパーティション分割されています。現在は再起動できません。
            Не удалось подготовить диск|Disk preparation failed|No se pudo preparar el disco|Datenträger konnte nicht vorbereitet werden|磁盘准备失败|ディスクを準備できませんでした
            Проверка установочных файлов…|Checking installation files…|Comprobando archivos de instalación…|Installationsdateien werden geprüft…|正在检查安装文件…|インストールファイルを確認中…
            Проверка Windows ISO…|Checking Windows ISO…|Comprobando la ISO de Windows…|Windows-ISO wird geprüft…|正在检查 Windows ISO…|Windows ISO を確認中…
            Подготовка частей Windows…|Preparing Windows image parts…|Preparando partes de la imagen de Windows…|Windows-Abbildteile werden vorbereitet…|正在准备 Windows 映像分卷…|Windows イメージの分割ファイルを準備中…
            Копирование шаблона WinPE…|Copying the WinPE template…|Copiando la plantilla de WinPE…|WinPE-Vorlage wird kopiert…|正在复制 WinPE 模板…|WinPE テンプレートをコピー中…
            Запись установочных файлов…|Writing installation files…|Escribiendo archivos de instalación…|Installationsdateien werden geschrieben…|正在写入安装文件…|インストールファイルを書き込み中…
            Проверка разметки диска…|Checking the disk layout…|Comprobando el esquema del disco…|Datenträgerlayout wird geprüft…|正在检查磁盘分区…|ディスク構成を確認中…
            Выберите Windows 11 ARM64 25H2 (ru-RU) и проверенный образ WinPE windows-display-live-name-fix-20261008.img.|Select Windows 11 ARM64 25H2 (ru-RU) and the verified WinPE image windows-display-live-name-fix-20261008.img.|Selecciona Windows 11 ARM64 25H2 (ru-RU) y la imagen WinPE verificada windows-display-live-name-fix-20261008.img.|Wähle Windows 11 ARM64 25H2 (ru-RU) und das geprüfte WinPE-Abbild windows-display-live-name-fix-20261008.img.|请选择 Windows 11 ARM64 25H2 (ru-RU) 和已验证的 WinPE 映像 windows-display-live-name-fix-20261008.img。|Windows 11 ARM64 25H2 (ru-RU) と検証済みの WinPE イメージ windows-display-live-name-fix-20261008.img を選択してください。
            Свободно %s · требуется %s|%s free · %s required|%s libres · se necesitan %s|%s frei · %s erforderlich|可用 %s · 需要 %s|空き %s · 必要 %s
            Заменить ISO…|Replace ISO…|Cambiar ISO…|ISO ersetzen…|更换 ISO…|ISO を変更…
            Выбрать ISO…|Choose ISO…|Elegir ISO…|ISO auswählen…|选择 ISO…|ISO を選択…
            Выбранный ISO не подходит для этого профиля.|The selected ISO is incompatible with this profile.|La ISO seleccionada es incompatible con este perfil.|Die ausgewählte ISO ist mit diesem Profil nicht kompatibel.|所选 ISO 与此配置不兼容。|選択した ISO はこのプロファイルに対応していません。
            Нужны Windows 11 ARM64 25H2 (ru-RU), сборка %s, и соответствующий шаблон WinPE.|This profile requires Windows 11 ARM64 25H2 (ru-RU), build %s, and the matching WinPE template.|Este perfil requiere Windows 11 ARM64 25H2 (ru-RU), compilación %s, y la plantilla WinPE correspondiente.|Für dieses Profil sind Windows 11 ARM64 25H2 (ru-RU), Build %s, und die passende WinPE-Vorlage erforderlich.|此配置需要 Windows 11 ARM64 25H2 (ru-RU) 版本 %s 及对应的 WinPE 模板。|このプロファイルには Windows 11 ARM64 25H2 (ru-RU) ビルド %s と対応する WinPE テンプレートが必要です。
            Для шаблона WinPE нужен install.wim из Windows 11 ARM64 25H2 (ru-RU).|The WinPE template requires install.wim from Windows 11 ARM64 25H2 (ru-RU).|La plantilla de WinPE requiere install.wim de Windows 11 ARM64 25H2 (ru-RU).|Die WinPE-Vorlage benötigt install.wim aus Windows 11 ARM64 25H2 (ru-RU).|WinPE 模板需要 Windows 11 ARM64 25H2 (ru-RU) 的 install.wim。|WinPE テンプレートには Windows 11 ARM64 25H2 (ru-RU) の install.wim が必要です。
            Контрольная сумма install.wim не совпадает с профилем WinPE.|The install.wim checksum does not match the WinPE profile.|La suma de comprobación de install.wim no coincide con el perfil de WinPE.|Die Prüfsumme von install.wim stimmt nicht mit dem WinPE-Profil überein.|install.wim 的校验和与 WinPE 配置不匹配。|install.wim のチェックサムが WinPE プロファイルと一致しません。
            Выбрать шаблон WinPE…|Choose WinPE template…|Elegir plantilla WinPE…|WinPE-Vorlage auswählen…|选择 WinPE 模板…|WinPE テンプレートを選択…
            Заменить шаблон WinPE…|Replace WinPE template…|Cambiar plantilla WinPE…|WinPE-Vorlage ersetzen…|更换 WinPE 模板…|WinPE テンプレートを変更…
            Подготовить диск|Prepare disk|Preparar disco|Datenträger vorbereiten|准备磁盘|ディスクを準備
            Диск сохранён в U-AVF. Запуск Windows Setup с этого диска пока недоступен.|The disk was saved in U-AVF. Starting Windows Setup from it is currently unavailable.|El disco se guardó en U-AVF. Aún no se puede iniciar Windows Setup desde él.|Der Datenträger wurde in U-AVF gespeichert. Windows Setup kann derzeit nicht davon gestartet werden.|磁盘已保存在 U-AVF 中，暂不支持从该磁盘启动 Windows 安装程序。|ディスクは U-AVF に保存されました。このディスクからの Windows セットアップ起動は現在利用できません。
            Можно открыть WinPE и проверить экран и ввод. Автоматическая установка пока недоступна в этой сборке.|Open WinPE to check display and input. Automatic installation is currently unavailable in this build.|Abre WinPE para comprobar la pantalla y la entrada. La instalación automática aún no está disponible en esta versión.|Öffne WinPE, um Anzeige und Eingabe zu prüfen. Die automatische Installation ist in diesem Build noch nicht verfügbar.|打开 WinPE 可检查显示和输入。此版本暂不支持自动安装。|WinPE を開いて画面表示と入力を確認できます。このビルドでは自動インストールはまだ利用できません。
            Открыть WinPE|Open WinPE|Abrir WinPE|WinPE öffnen|打开 WinPE|WinPE を開く
            Установить Windows|Install Windows|Instalar Windows|Windows installieren|安装 Windows|Windows をインストール
            Установка Windows 11 Pro начнётся автоматически после подтверждения.|Windows 11 Pro installation starts automatically after confirmation.|La instalación de Windows 11 Pro comienza automáticamente después de confirmar.|Die Installation von Windows 11 Pro beginnt nach der Bestätigung automatisch.|确认后将自动开始安装 Windows 11 Pro。|確認後、Windows 11 Pro のインストールが自動的に始まります。
            U-AVF создаст разделы на подготовленном виртуальном диске и начнёт установку Windows 11 Pro.|U-AVF will create partitions on the prepared virtual disk and start installing Windows 11 Pro.|U-AVF creará particiones en el disco virtual preparado e iniciará la instalación de Windows 11 Pro.|U-AVF erstellt Partitionen auf dem vorbereiteten virtuellen Datenträger und startet die Installation von Windows 11 Pro.|U-AVF 将在已准备好的虚拟磁盘上创建分区并开始安装 Windows 11 Pro。|U-AVF は準備済みの仮想ディスクにパーティションを作成し、Windows 11 Pro のインストールを開始します。
            Для автоматической установки нужно %1$s свободного места. Сейчас доступно %2$s.|Automatic installation needs %1$s of free space. %2$s is available.|La instalación automática requiere %1$s de espacio libre. Hay %2$s disponibles.|Die automatische Installation benötigt %1$s freien Speicherplatz. Verfügbar sind %2$s.|自动安装需要 %1$s 可用空间。当前可用 %2$s。|自動インストールには %1$s の空き容量が必要です。現在の空き容量は %2$s です。
            Загрузчик установленной Windows проверен. При первом запуске продолжится настройка системы.|The installed Windows boot loader was verified. Windows will continue setup on first boot.|Se verificó el cargador de Windows instalado. La configuración continuará en el primer arranque.|Der Bootloader der installierten Windows-Version wurde geprüft. Beim ersten Start wird die Einrichtung fortgesetzt.|已验证已安装 Windows 的引导程序。首次启动时将继续设置。|インストール済みの Windows ブート ローダーを確認しました。初回起動時にセットアップが続きます。
            Это отключит питание VM. По возможности сначала завершите работу в Windows.|This cuts power to the VM. Shut down from Windows first when possible.|Esto apaga la VM de forma forzada. Si es posible, apaga Windows primero.|Die VM wird hart ausgeschaltet. Fahre Windows nach Möglichkeit zuerst herunter.|这会强制关闭虚拟机。如果可以，请先在 Windows 中关机。|仮想マシンの電源を強制的に切ります。可能であれば先に Windows からシャットダウンしてください。
            Для запуска нужны разрешения AVF.|AVF permissions are required to start.|Se necesitan permisos de AVF para iniciar.|Zum Start sind AVF-Berechtigungen erforderlich.|启动需要 AVF 权限。|起動には AVF の権限が必要です。
            Ядро Windows|Windows kernel|Kernel de Windows|Windows-Kernel|Windows 内核|Windows カーネル
            Ожидание изображения Windows…|Waiting for the Windows display…|Esperando la imagen de Windows…|Warte auf die Windows-Anzeige…|正在等待 Windows 画面…|Windows の画面を待機中…
            Экран установки подключён|Setup display connected|Pantalla de instalación conectada|Setup-Anzeige verbunden|安装界面已连接|セットアップ画面に接続しました
            Экран Windows подключён|Windows display connected|Pantalla de Windows conectada|Windows-Anzeige verbunden|Windows 画面已连接|Windows の画面に接続しました
            Установка Windows|Installing Windows|Instalando Windows|Windows wird installiert|正在安装 Windows|Windows をインストールしています
            Запускаем WinPE|Starting WinPE|Iniciando WinPE|WinPE wird gestartet|正在启动 WinPE|WinPE を起動しています
            Подготовка установки занимает больше времени|Setup preparation is taking longer than usual|La preparación de la instalación tarda más de lo habitual|Die Installationsvorbereitung dauert länger als üblich|安装准备时间比平时更长|セットアップの準備に通常より時間がかかっています
            Связь с экраном Windows прервалась. Вернитесь в рабочее пространство, чтобы подключиться снова.|The Windows display disconnected. Return to the workspace to reconnect.|Se perdió la conexión con la pantalla de Windows. Vuelve al espacio de trabajo para reconectar.|Die Verbindung zur Windows-Anzeige wurde unterbrochen. Kehre zum Arbeitsbereich zurück, um sie erneut herzustellen.|Windows 显示连接已断开。返回工作区以重新连接。|Windows の画面接続が切れました。再接続するにはワークスペースに戻ってください。
            Восстанавливаем изображение Windows…|Reconnecting to the Windows display…|Reconectando con la pantalla de Windows…|Windows-Anzeige wird erneut verbunden…|正在重新连接 Windows 显示…|Windows の画面に再接続しています…
            Восстанавливаем изображение %s|Reconnecting to the %s display|Reconectando con la pantalla de %s|Verbindung zur %s-Anzeige wird wiederhergestellt|正在重新连接 %s 显示|%s の画面に再接続しています
            Восстановление изображения занимает больше времени|Display recovery is taking longer than usual|La recuperación de la imagen tarda más de lo habitual|Die Wiederherstellung der Anzeige dauert länger als üblich|恢复画面所需的时间比平时更长|画面の復旧に通常より時間がかかっています
            Ждать|Wait|Esperar|Warten|等待|待機
            Отменить|Cancel|Cancelar|Abbrechen|取消|キャンセル
            ISO проверен · Experimental|ISO checked · Experimental|ISO comprobada · Experimental|ISO geprüft · Experimentell|ISO 已检查 · 实验性|ISO 確認済み · 試験運用
            Запустить прежний образ|Start existing boot image|Iniciar imagen existente|Vorhandenes Boot-Abbild starten|启动现有启动映像|既存のブートイメージを起動
            Образ содержит загрузчик ARM64 и файлы установки. Загрузка Setup из ISO пока недоступна.|The image contains an ARM64 boot loader and installation files. Booting Setup from ISO is currently unavailable.|La imagen contiene un cargador ARM64 y los archivos de instalación. El arranque desde la ISO aún no está disponible.|Das Abbild enthält einen ARM64-Bootloader und Installationsdateien. Der Start des Setups von ISO ist noch nicht verfügbar.|映像包含 ARM64 引导程序和安装文件。暂不支持从 ISO 启动安装程序。|ARM64 ブートローダーとインストールファイルを確認しました。ISO からのセットアップ起動は現在利用できません。
            Результат уже отправлен|Compatibility report already sent|El resultado ya se envió|Ergebnis bereits gesendet|结果已发送|結果は送信済みです
            Результат отправлен разработчикам|Report sent to developers|Resultado enviado a los desarrolladores|Ergebnis an die Entwickler gesendet|结果已发送给开发者|開発者に結果を送信しました
            Лимит отправки исчерпан|Report limit reached|Se alcanzó el límite de envíos|Limit für Berichte erreicht|已达到报告发送上限|送信上限に達しました
            Отправка отключена|Sharing is off|El envío está desactivado|Teilen ist deaktiviert|分享已关闭|共有は無効です
            Отправка отменена|Sharing cancelled|Envío cancelado|Senden abgebrochen|已取消发送|送信を中止しました
            Не удалось отправить результат проверки|Could not send the compatibility report|No se pudo enviar el resultado|Kompatibilitätsbericht konnte nicht gesendet werden|无法发送兼容性结果|互換性の結果を送信できませんでした
            Совместимо|Compatible|Compatible|Kompatibel|兼容|互換性あり
            Требуется доработка|Needs work|Requiere ajustes|Nachbesserung erforderlich|需要改进|要対応
            Несовместимо|Incompatible|Incompatible|Inkompatibel|不兼容|互換性なし
            Недостаточно данных|Insufficient data|Datos insuficientes|Unzureichende Daten|数据不足|データ不足
            Базовая совместимость AVF|Base AVF compatibility|Compatibilidad básica con AVF|AVF-Grundkompatibilität|AVF 基础兼容性|AVF の基本互換性
            Ubuntu, Windows и Mavk проверяются отдельно.|Ubuntu, Windows and Mavk are tested separately.|Ubuntu, Windows y Mavk se prueban por separado.|Ubuntu, Windows und Mavk werden separat geprüft.|Ubuntu、Windows 和 Mavk 分别测试。|Ubuntu、Windows、Mavk は個別に検証します。
            Создание VM|VM creation|Creación de la VM|VM-Erstellung|创建虚拟机|VM の作成
            Загрузка тестового ядра|Test kernel boot|Arranque del núcleo de prueba|Start des Testkernels|启动测试内核|テストカーネルの起動
            Память гостя|Guest memory|Memoria del invitado|Gastspeicher|客户机内存|ゲストメモリ
            Запись на тестовый диск|Test disk write|Escritura en el disco de prueba|Schreiben auf Testdatenträger|测试磁盘写入|テストディスクへの書き込み
            Обмен через vsock|Vsock exchange|Intercambio por vsock|Vsock-Datenaustausch|vsock 数据交换|vsock 通信
            Ubuntu передаёт %1$s. Запрошено %2$s.|Ubuntu is streaming %1$s. Requested %2$s.|Ubuntu transmite a %1$s. Se solicitó %2$s.|Ubuntu überträgt mit %1$s. Angefordert: %2$s.|Ubuntu 正在以 %1$s 传输。请求的分辨率为 %2$s。|Ubuntu は %1$s で配信中です。指定した解像度は %2$s です。
            Язык интерфейса|App language|Idioma de la aplicación|Sprache der Oberfläche|界面语言|表示言語
            Главная|Home|Inicio|Start|主页|ホーム
            Системы|Systems|Sistemas|Systeme|系统|システム
            Настройки|Settings|Ajustes|Einstellungen|设置|設定
            Назад|Back|Atrás|Zurück|返回|戻る
            Закрыть|Close|Cerrar|Schließen|关闭|閉じる
            Отмена|Cancel|Cancelar|Abbrechen|取消|キャンセル
            Понятно|OK|Entendido|OK|好的|OK
            Включено|On|Activado|Ein|开启|オン
            Выключено|Off|Desactivado|Aus|关闭|オフ
            Память|Memory|Memoria|Arbeitsspeicher|内存|メモリ
            Диск|Disk|Disco|Datenträger|磁盘|ディスク
            Дисплей|Display|Pantalla|Anzeige|显示|画面
            Графика|Graphics|Gráficos|Grafik|图形|グラフィック
            Ресурсы Ubuntu|Ubuntu resources|Recursos de Ubuntu|Ubuntu-Ressourcen|Ubuntu 资源|Ubuntu のリソース
            Проверки и диагностика|Checks and diagnostics|Comprobaciones y diagnóstico|Prüfungen und Diagnose|检查与诊断|チェックと診断
            Состояние устройства и последнего запуска|Device status and last boot|Estado del dispositivo y último arranque|Gerätestatus und letzter Start|设备状态与上次启动|デバイス状態と前回の起動
            Открыть диагностику|Open diagnostics|Abrir diagnóstico|Diagnose öffnen|打开诊断|診断を開く
            Недавние сессии|Recent sessions|Sesiones recientes|Letzte Sitzungen|最近会话|最近のセッション
            История появится после запуска|Sessions appear here after launch|Las sesiones aparecen aquí al iniciarse|Sitzungen erscheinen hier nach dem Start|启动后会在此显示会话|起動後にここへ表示されます
            Образы и диски|Images and disks|Imágenes y discos|Abbilder und Datenträger|映像与磁盘|イメージとディスク
            Создать воркспейс|Create workspace|Crear espacio|Arbeitsbereich erstellen|创建工作区|ワークスペースを作成
            Создать Ubuntu|Create Ubuntu|Crear Ubuntu|Ubuntu erstellen|创建 Ubuntu|Ubuntu を作成
            Создать Windows|Create Windows|Crear Windows|Windows erstellen|创建 Windows|Windows を作成
            Воркспейсы|Workspaces|Espacios|Arbeitsbereiche|工作区|ワークスペース
            Здесь появятся созданные воркспейсы|Your workspaces will appear here|Tus espacios aparecerán aquí|Deine Arbeitsbereiche erscheinen hier|创建的工作区会显示在这里|作成したワークスペースがここに表示されます
            Windows 11 ARM64 · экспериментально|Windows 11 ARM64 · experimental|Windows 11 ARM64 · experimental|Windows 11 ARM64 · experimentell|Windows 11 ARM64 · 实验性|Windows 11 ARM64 · 試験的
            Проверки и тесты|Checks and tests|Comprobaciones y pruebas|Prüfungen und Tests|检查与测试|チェックとテスト
            Клавиатура Android|Android keyboard|Teclado de Android|Android-Tastatur|Android 键盘|Android キーボード
            Делиться результатами|Share results|Compartir resultados|Ergebnisse teilen|分享结果|結果を共有
            О приложении|About|Acerca de|Über die App|关于应用|アプリについて
            Проверять образ перед запуском|Verify image before starting|Verificar imagen antes de iniciar|Abbild vor dem Start prüfen|启动前校验映像|起動前にイメージを検証
            Приватность|Privacy|Privacidad|Datenschutz|隐私|プライバシー
            Лицензии|Licenses|Licencias|Lizenzen|许可证|ライセンス
            Вернуться|Resume|Volver|Fortsetzen|继续使用|続きから
            Запустить|Start|Iniciar|Starten|启动|起動
            Продолжить|Continue|Continuar|Weiter|继续|続ける
            Создать|Create|Crear|Erstellen|创建|作成
            Работает|Running|En ejecución|Läuft|运行中|実行中
            Выключена|Stopped|Detenida|Gestoppt|已停止|停止
            Не установлена|Not installed|Sin instalar|Nicht installiert|未安装|未インストール
            Настройка не завершена|Setup incomplete|Configuración incompleta|Einrichtung unvollständig|设置未完成|セットアップ未完了
            Хранилище пусто|Storage is empty|Almacenamiento vacío|Speicher ist leer|存储为空|ストレージは空です
            1 образ|1 image|1 imagen|1 Abbild|1 个映像|イメージ 1 個
            1 проверенный образ|1 verified image|1 imagen verificada|1 geprüftes Abbild|1 个已校验映像|検証済みイメージ 1 個
            Ubuntu · Работа|Ubuntu · Work|Ubuntu · Trabajo|Ubuntu · Arbeit|Ubuntu · 工作|Ubuntu · 作業
            Ресурсы|Resources|Recursos|Ressourcen|资源|リソース
            Экран|Display|Pantalla|Anzeige|显示|画面
            Ввод|Input|Entrada|Eingabe|输入|入力
            Звук и сеть|Sound and network|Sonido y red|Ton und Netzwerk|声音与网络|サウンドとネットワーク
            Хранилище|Storage|Almacenamiento|Speicher|存储|ストレージ
            Процессор|Processor|Procesador|Prozessor|处理器|プロセッサ
            Как у устройства|Match device|Igual que el dispositivo|Wie Gerät|与设备相同|デバイスと同じ
            3D-ускорение|3D acceleration|Aceleración 3D|3D-Beschleunigung|3D 加速|3D アクセラレーション
            Дополнительно|Advanced|Avanzado|Erweitert|高级|詳細
            Быстрый вывод|Fast display|Pantalla rápida|Schnelle Anzeige|快速显示|高速表示
            Масштаб изображения|Image scaling|Escala de imagen|Bildskalierung|图像缩放|画像スケーリング
            По размеру окна|Fit to window|Ajustar a la ventana|An Fenster anpassen|适应窗口|ウィンドウに合わせる
            Mavk GPU · экспериментально|Mavk GPU · experimental|GPU Mavk · experimental|Mavk-GPU · experimentell|Mavk GPU · 实验性|Mavk GPU · 試験的
            Режим указателя|Pointer mode|Modo del puntero|Zeigermodus|指针模式|ポインターモード
            Общий буфер обмена|Shared clipboard|Portapapeles compartido|Gemeinsame Zwischenablage|共享剪贴板|共有クリップボード
            Жест от правого края|Right-edge gesture|Gesto del borde derecho|Geste am rechten Rand|右侧边缘手势|右端のジェスチャー
            Скрывать кнопку панели|Hide controls button|Ocultar botón de controles|Steuerungstaste ausblenden|隐藏控制按钮|コントロールボタンを隠す
            Горячие клавиши|Shortcuts|Atajos|Tastenkürzel|快捷键|ショートカット
            Перехват системных сочетаний|Capture system shortcuts|Capturar atajos del sistema|Systemkürzel abfangen|捕获系统快捷键|システムショートカットを取得
            Звук гостевой системы|Guest audio|Audio del invitado|Gastton|客户机声音|ゲストの音声
            Интернет|Internet|Internet|Internet|互联网|インターネット
            Расширить…|Expand…|Ampliar…|Erweitern…|扩展…|拡張…
            Расширить диск|Expand disk|Ampliar disco|Datenträger erweitern|扩展磁盘|ディスクを拡張
            Восстановление|Recovery|Recuperación|Wiederherstellung|恢复|復元
            Образ Ubuntu|Ubuntu image|Imagen de Ubuntu|Ubuntu-Abbild|Ubuntu 映像|Ubuntu イメージ
            Все образы|All images|Todas las imágenes|Alle Abbilder|全部映像|すべてのイメージ
            Проверен|Verified|Verificada|Geprüft|已校验|検証済み
            Не выбран|Not selected|Sin seleccionar|Nicht ausgewählt|未选择|未選択
            Диск ещё не создан|Disk not created|Disco sin crear|Datenträger noch nicht erstellt|磁盘尚未创建|ディスクは未作成
            Установка и восстановление|Installation and recovery|Instalación y recuperación|Installation und Wiederherstellung|安装与恢复|インストールと復元
            Статус установки|Installation status|Estado de instalación|Installationsstatus|安装状态|インストール状態
            Временная Live-сессия|Temporary live session|Sesión temporal|Temporäre Live-Sitzung|临时 Live 会话|一時的な Live セッション
            Запуск Ubuntu с ISO без установки|Run Ubuntu from an ISO without installing|Iniciar Ubuntu desde una ISO sin instalar|Ubuntu ohne Installation von einer ISO starten|从 ISO 启动 Ubuntu，无需安装|ISO から Ubuntu をインストールせずに起動
            Переустановить Ubuntu…|Reinstall Ubuntu…|Reinstalar Ubuntu…|Ubuntu neu installieren…|重新安装 Ubuntu…|Ubuntu を再インストール…
            Удалить воркспейс…|Delete workspace…|Eliminar espacio…|Arbeitsbereich löschen…|删除工作区…|ワークスペースを削除…
            Управление VM|VM controls|Controles de VM|VM-Steuerung|VM 控制|VM コントロール
            Показать клавиатуру|Show keyboard|Mostrar teclado|Tastatur anzeigen|显示键盘|キーボードを表示
            Режим ввода|Input mode|Modo de entrada|Eingabemodus|输入模式|入力モード
            Разрешение|Resolution|Resolución|Auflösung|分辨率|解像度
            Параметры воркспейса|Workspace settings|Ajustes del espacio|Arbeitsbereich-Einstellungen|工作区设置|ワークスペース設定
            На главную|Home|Inicio|Zum Startbildschirm|返回主页|ホームへ戻る
            Завершить…|Shut down…|Apagar…|Herunterfahren…|关机…|シャットダウン…
            Завершить|Shut down|Apagar|Herunterfahren|关机|シャットダウン
            Завершить сеанс|Shut down session|Apagar sesión|Sitzung beenden|结束会话|セッションを終了
            Перезагрузить|Restart|Reiniciar|Neu starten|重启|再起動
            Перезагрузить Ubuntu?|Restart Ubuntu?|¿Reiniciar Ubuntu?|Ubuntu neu starten?|重启 Ubuntu？|Ubuntu を再起動しますか？
            Сохраните работу в Ubuntu. U-AVF переподключится после перезагрузки.|Save your work in Ubuntu. U-AVF will reconnect after the restart.|Guarda tu trabajo en Ubuntu. U-AVF volverá a conectarse tras reiniciar.|Speichere deine Arbeit in Ubuntu. U-AVF verbindet sich nach dem Neustart erneut.|请保存在 Ubuntu 中的工作。重启后 U-AVF 会重新连接。|Ubuntu の作業を保存してください。再起動後に U-AVF が再接続します。
            Завершить работу %s?|Shut down %s?|¿Apagar %s?|%s herunterfahren?|要关闭 %s 吗？|%s をシャットダウンしますか？
            Сохраните открытые документы перед завершением сеанса.|Save open documents before shutting down.|Guarda los documentos abiertos antes de apagar.|Speichere offene Dokumente vor dem Herunterfahren.|关机前请保存打开的文档。|シャットダウン前に開いている書類を保存してください。
            Мышь|Mouse|Ratón|Maus|鼠标|マウス
            Захват мыши|Mouse captured|Ratón capturado|Maus erfasst|已捕获鼠标|マウスをキャプチャ中
            Управление указателем|Pointer controls|Controles del puntero|Zeigersteuerung|指针控制|ポインター操作
            Захватить указатель|Capture pointer|Capturar puntero|Zeiger erfassen|捕获指针|ポインターをキャプチャ
            Освободить указатель|Release pointer|Liberar puntero|Zeiger freigeben|释放指针|ポインターを解放
            Клавиатура U-AVF|U-AVF keyboard|Teclado de U-AVF|U-AVF-Tastatur|U-AVF 键盘|U-AVF キーボード
            Открыть настройки|Open settings|Abrir ajustes|Einstellungen öffnen|打开设置|設定を開く
            Ожидание|Waiting|En espera|Wartet|等待中|待機中
            Недоступно|Unavailable|No disponible|Nicht verfügbar|不可用|利用不可
            Устройство|Device|Dispositivo|Gerät|设备|デバイス
            Разрешения Custom VM|Custom VM permissions|Permisos de VM personalizada|Rechte für eigene VM|自定义 VM 权限|カスタム VM の権限
            Целостность платформы|Platform integrity|Integridad de la plataforma|Plattformintegrität|平台完整性|プラットフォーム整合性
            Последний запуск Ubuntu|Last Ubuntu boot|Último arranque de Ubuntu|Letzter Ubuntu-Start|上次 Ubuntu 启动|前回の Ubuntu 起動
            Загрузка и vsock|Boot and vsock|Arranque y vsock|Start und vsock|启动与 vsock|起動と vsock
            Тестовые прогоны|Test runs|Ejecuciones de prueba|Testläufe|测试运行|テスト実行
            """;
        String data1="""
            Открыть|Open|Abrir|Öffnen|打开|開く
            Проверить устройство|Check device|Comprobar dispositivo|Gerät prüfen|检查设备|デバイスを確認
            Журнал|Journal|Registro|Protokoll|日志|ログ
            Экспорт отчёта|Export report|Exportar informe|Bericht exportieren|导出报告|レポートを書き出す
            К рабочему столу|Back to desktop|Volver al escritorio|Zurück zum Desktop|返回桌面|デスクトップに戻る
            Поиск по событиям|Search events|Buscar eventos|Ereignisse durchsuchen|搜索事件|イベントを検索
            Все компоненты|All components|Todos los componentes|Alle Komponenten|全部组件|すべてのコンポーネント
            Все уровни|All levels|Todos los niveles|Alle Stufen|全部级别|すべてのレベル
            Проверка|Check|Comprobación|Prüfung|检查|チェック
            Событие|Event|Evento|Ereignis|事件|イベント
            Ошибка|Error|Error|Fehler|错误|エラー
            Нет подходящих событий|No matching events|No hay eventos coincidentes|Keine passenden Ereignisse|没有匹配的事件|一致するイベントはありません
            Поиск по отчётам|Search reports|Buscar informes|Berichte durchsuchen|搜索报告|レポートを検索
            Нет подходящих отчётов|No matching reports|No hay informes coincidentes|Keine passenden Berichte|没有匹配的报告|一致するレポートはありません
            Не удалось восстановить изображение %s|Could not restore the %s display|No se pudo recuperar la pantalla de %s|Die %s-Anzeige konnte nicht wiederhergestellt werden|无法恢复 %s 显示|%s の画面を復元できませんでした
            Сеанс Ubuntu всё ещё работает. Mavk потерял вывод изображения.|The Ubuntu session is still running. Mavk lost its display output.|La sesión de Ubuntu sigue activa. Mavk perdió la salida de pantalla.|Die Ubuntu-Sitzung läuft weiter. Die Mavk-Bildausgabe ist unterbrochen.|Ubuntu 会话仍在运行。Mavk 显示输出已中断。|Ubuntu セッションは実行中です。Mavk の画面出力が途切れました。
            Компонент|Component|Componente|Komponente|组件|コンポーネント
            Уровень|Level|Nivel|Stufe|级别|レベル
            Копировать найденное|Copy results|Copiar resultados|Treffer kopieren|复制匹配项|一致した項目をコピー
            Копировать отчёт|Copy report|Copiar informe|Bericht kopieren|复制报告|レポートをコピー
            Экспорт|Export|Exportar|Exportieren|导出|書き出す
            ARM64 отсутствует|ARM64 unavailable|ARM64 no disponible|ARM64 nicht verfügbar|ARM64 不可用|ARM64 は利用不可
            API доступен|API available|API disponible|API verfügbar|API 可用|API は利用可能
            API недоступен|API unavailable|API no disponible|API nicht verfügbar|API 不可用|API は利用不可
            Не проверен|Not checked|Sin comprobar|Nicht geprüft|未检查|未確認
            Не проверена|Not checked|Sin comprobar|Nicht geprüft|未检查|未確認
            Не проверены|Not checked|Sin comprobar|Nicht geprüft|未检查|未確認
            Предоставлены|Granted|Concedidos|Erteilt|已授予|許可済み
            Нет доступа|Access missing|Sin acceso|Kein Zugriff|没有访问权限|アクセス権なし
            Проверена|Verified|Verificada|Geprüft|已校验|検証済み
            Есть ошибка|Check failed|Comprobación fallida|Prüfung fehlgeschlagen|检查失败|チェック失敗
            Подтверждены|Confirmed|Confirmados|Bestätigt|已确认|確認済み
            Загрузка подтверждена|Boot confirmed|Arranque confirmado|Start bestätigt|启动已确认|起動を確認
            vsock подключён|vsock connected|vsock conectado|vsock verbunden|vsock 已连接|vsock 接続済み
            Mavk · кадр показан|Mavk · frame displayed|Mavk · fotograma mostrado|Mavk · Bild angezeigt|Mavk · 已显示画面|Mavk · フレーム表示済み
            H.264 · кадр показан|H.264 · frame displayed|H.264 · fotograma mostrado|H.264 · Bild angezeigt|H.264 · 已显示画面|H.264 · フレーム表示済み
            Кадр показан|Frame displayed|Fotograma mostrado|Bild angezeigt|已显示画面|フレーム表示済み
            Запрошен запуск|Start requested|Arranque solicitado|Start angefordert|已请求启动|起動を要求
            Виртуальная машина создана|Virtual machine created|Máquina virtual creada|Virtuelle Maschine erstellt|虚拟机已创建|仮想マシンを作成済み
            Запущен UEFI|UEFI started|UEFI iniciado|UEFI gestartet|UEFI 已启动|UEFI を起動済み
            Подтверждена загрузка ядра Linux|Linux kernel boot confirmed|Arranque del núcleo Linux confirmado|Linux-Kernelstart bestätigt|Linux 内核启动已确认|Linux カーネルの起動を確認
            Запущены службы systemd|systemd services started|Servicios systemd iniciados|systemd-Dienste gestartet|systemd 服务已启动|systemd サービスを起動済み
            Запущен дисплейный менеджер|Display manager started|Gestor de pantalla iniciado|Anzeigemanager gestartet|显示管理器已启动|ディスプレイマネージャーを起動済み
            Подключён вывод изображения|Display connected|Pantalla conectada|Anzeige verbunden|显示已连接|画面を接続済み
            Показан первый кадр рабочего стола|First desktop frame displayed|Primer fotograma del escritorio mostrado|Erstes Desktop-Bild angezeigt|已显示首个桌面画面|最初のデスクトップフレームを表示
            Запрошено завершение|Shutdown requested|Apagado solicitado|Herunterfahren angefordert|已请求关机|シャットダウンを要求
            Запрошен перезапуск|Restart requested|Reinicio solicitado|Neustart angefordert|已请求重启|再起動を要求
            Гость принял запрос питания|Guest accepted the power request|El invitado aceptó la solicitud de energía|Gast hat die Energieanforderung angenommen|客户机已接受电源请求|ゲストが電源要求を受理
            Подтверждена синхронизация файлов|Filesystem sync confirmed|Sincronización del sistema de archivos confirmada|Dateisystem-Synchronisierung bestätigt|文件系统同步已确认|ファイルシステム同期を確認
            Виртуальная машина остановлена|Virtual machine stopped|Máquina virtual detenida|Virtuelle Maschine gestoppt|虚拟机已停止|仮想マシンを停止済み
            Операция виртуальной машины завершилась ошибкой|Virtual machine operation failed|Falló la operación de la máquina virtual|Vorgang der virtuellen Maschine fehlgeschlagen|虚拟机操作失败|仮想マシンの操作に失敗
            Запущена проверка требований устройства|Device prerequisites check started|Comprobación de requisitos iniciada|Prüfung der Gerätevoraussetzungen gestartet|已开始检查设备要求|デバイス要件のチェックを開始
            Не удалось сохранить результат проверки|Failed to save the check result|No se pudo guardar el resultado|Prüfergebnis konnte nicht gespeichert werden|无法保存检查结果|チェック結果を保存できませんでした
            Требования устройства проверены. Пробная VM не запускалась.|Device prerequisites checked. No test VM was started.|Se comprobaron los requisitos. No se inició una VM de prueba.|Gerätevoraussetzungen geprüft. Es wurde keine Test-VM gestartet.|设备要求已检查。未启动测试 VM。|デバイス要件を確認しました。テスト VM は起動していません。
            Одно или несколько требований устройства не выполнены.|One or more device prerequisites were not met.|Uno o más requisitos del dispositivo no se cumplieron.|Mindestens eine Gerätevoraussetzung ist nicht erfüllt.|有一项或多项设备要求未满足。|1 つ以上のデバイス要件が満たされていません。
            Запрос отправлен|Request sent|Solicitud enviada|Anfrage gesendet|请求已发送|要求を送信しました
            Создание виртуальной машины|Creating the virtual machine|Creando la máquina virtual|Virtuelle Maschine wird erstellt|正在创建虚拟机|仮想マシンを作成中
            Запуск UEFI|Starting UEFI|Iniciando UEFI|UEFI wird gestartet|正在启动 UEFI|UEFI を起動中
            Загрузка ядра|Loading the kernel|Cargando el núcleo|Kernel wird geladen|正在加载内核|カーネルを読み込み中
            Запуск служб|Starting services|Iniciando servicios|Dienste werden gestartet|正在启动服务|サービスを起動中
            Подключение дисплея…|Connecting the display…|Conectando la pantalla…|Anzeige wird verbunden…|正在连接显示…|画面に接続中…
            Ожидание первого кадра…|Waiting for the first frame…|Esperando el primer fotograma…|Warten auf das erste Bild…|正在等待首帧…|最初のフレームを待機中…
            Рабочий стол готов|Desktop ready|Escritorio listo|Desktop bereit|桌面已就绪|デスクトップの準備完了
            Запускаем %s|Starting %s|Iniciando %s|%s wird gestartet|正在启动 %s|%s を起動中
            Завершаем %s|Shutting down %s|Apagando %s|%s wird heruntergefahren|正在关闭 %s|%s をシャットダウン中
            Перезапускаем %s|Restarting %s|Reiniciando %s|%s wird neu gestartet|正在重启 %s|%s を再起動中
            Не удалось запустить %s|Could not start %s|No se pudo iniciar %s|%s konnte nicht gestartet werden|无法启动 %s|%s を起動できませんでした
            Не удалось завершить %s|Could not shut down %s|No se pudo apagar %s|%s konnte nicht heruntergefahren werden|无法关闭 %s|%s をシャットダウンできませんでした
            Не удалось перезапустить %s|Could not restart %s|No se pudo reiniciar %s|%s konnte nicht neu gestartet werden|无法重启 %s|%s を再起動できませんでした
            %s запускается дольше обычного|%s is taking longer to start|%s tarda más de lo habitual en iniciarse|%s benötigt länger zum Starten|%s 启动时间比平时更长|%s の起動に通常より時間がかかっています
            %s завершает работу дольше обычного|%s is taking longer to shut down|%s tarda más de lo habitual en apagarse|%s benötigt länger zum Herunterfahren|%s 关机时间比平时更长|%s のシャットダウンに通常より時間がかかっています
            %s перезапускается дольше обычного|%s is taking longer to restart|%s tarda más de lo habitual en reiniciarse|%s benötigt länger zum Neustarten|%s 重启时间比平时更长|%s の再起動に通常より時間がかかっています
            Подготовка запуска…|Preparing to start…|Preparando el arranque…|Start wird vorbereitet…|正在准备启动…|起動を準備中…
            Запрос завершения|Shutdown request|Solicitud de apagado|Herunterfahren angefordert|关机请求|シャットダウン要求
            Запрос перезапуска|Restart request|Solicitud de reinicio|Neustart angefordert|重启请求|再起動要求
            Синхронизация файлов|Syncing files|Sincronización de archivos|Dateien werden synchronisiert|正在同步文件|ファイルを同期中
            VM остановлена|VM stopped|VM detenida|VM gestoppt|VM 已停止|VM 停止済み
            Ядро Linux|Linux kernel|Núcleo Linux|Linux-Kernel|Linux 内核|Linux カーネル
            Виртуальная машина|Virtual machine|Máquina virtual|Virtuelle Maschine|虚拟机|仮想マシン
            Остановка гостя|Stopping the guest|Detención del invitado|Gast wird gestoppt|正在停止客户机|ゲストを停止中
            Гостевые службы|Guest services|Servicios del invitado|Gastdienste|客户机服务|ゲストサービス
            Восстановление дисплея|Restoring the display|Restauración de la pantalla|Anzeige wird wiederhergestellt|正在恢复显示|画面を復元中
            Прошло %02d:%02d|Elapsed %02d:%02d|Transcurrido %02d:%02d|Vergangen %02d:%02d|已用 %02d:%02d|経過 %02d:%02d
            Состояние|Status|Estado|Status|状态|状態
            Принудительно остановить…|Force stop…|Forzar detención…|Hart stoppen…|强制停止…|強制停止…
            Продолжить ожидание|Keep waiting|Seguir esperando|Weiter warten|继续等待|待機を続ける
            Подтверждено|Confirmed|Confirmado|Bestätigt|已确认|確認済み
            %d ГБ|%d GB|%d GB|%d GB|%d GB|%d GB
            2 ГБ|2 GB|2 GB|2 GB|2 GB|2 GB
            3 ГБ|3 GB|3 GB|3 GB|3 GB|3 GB
            4 ГБ|4 GB|4 GB|4 GB|4 GB|4 GB
            6 ГБ|6 GB|6 GB|6 GB|6 GB|6 GB
            Область действия|License scope|Alcance de la licencia|Lizenzumfang|许可证范围|ライセンスの範囲
            Сторонние компоненты|Third-party components|Componentes de terceros|Komponenten von Drittanbietern|第三方组件|サードパーティのコンポーネント
            Состояние установки|Installation status|Estado de instalación|Installationsstatus|安装状态|インストール状態
            Проверено последним запуском|Evidence from the last launch|Datos del último arranque|Daten aus dem letzten Start|上次启动的数据|前回の起動からのデータ
            Установленная система|Installed system|Sistema instalado|Installiertes System|已安装系统|インストール済みシステム
            Официальный ISO|Official ISO|ISO oficial|Offizielles ISO|官方 ISO|公式 ISO
            Образ доступен|Image available|Imagen disponible|Abbild verfügbar|映像可用|イメージが利用可能
            Runtime гостя|Guest runtime|Entorno del invitado|Gast-Laufzeit|客户机运行时|ゲストランタイム
            Готова|Ready|Listo|Bereit|就绪|準備完了
            HELLO подтверждён|HELLO confirmed|HELLO confirmado|HELLO bestätigt|HELLO 已确认|HELLO を確認
            Чтение отчёта…|Reading the report…|Leyendo el informe…|Bericht wird gelesen…|正在读取报告…|レポートを読み込み中…
            Разметка проверена|Partition layout verified|Particiones verificadas|Partitionslayout geprüft|分区布局已校验|パーティション構成を検証済み
            Готовим установку…|Preparing installation…|Preparando la instalación…|Installation wird vorbereitet…|正在准备安装…|インストールを準備中…
            Запрошено открытие установщика|Installer launch requested|Inicio del instalador solicitado|Installer-Start angefordert|已请求启动安装程序|インストーラーの起動を要求
            Подготовка установленной системы…|Preparing the installed system…|Preparando el sistema instalado…|Installiertes System wird vorbereitet…|正在准备已安装的系统…|インストール済みシステムを準備中…
            Первый запуск Ubuntu…|Starting Ubuntu for the first time…|Iniciando Ubuntu por primera vez…|Ubuntu wird erstmals gestartet…|首次启动 Ubuntu…|Ubuntu を初回起動中…
            Установка не завершилась. Подробности в диагностике.|Installation did not finish. See Diagnostics for details.|La instalación no finalizó. Consulta Diagnóstico.|Die Installation wurde nicht abgeschlossen. Details in der Diagnose.|安装未完成。详情请查看诊断。|インストールが完了しませんでした。詳細は診断を参照してください。
            Подождите…|Please wait…|Espera…|Bitte warten…|请稍候…|しばらくお待ちください…
            Открыть воркспейс|Open workspace|Abrir espacio|Arbeitsbereich öffnen|打开工作区|ワークスペースを開く
            Продолжить настройку|Continue setup|Continuar configuración|Einrichtung fortsetzen|继续设置|セットアップを続ける
            Доступ к виртуализации|Virtualization access|Acceso a virtualización|Zugriff auf Virtualisierung|虚拟化访问权限|仮想化へのアクセス
            Доступ предоставлен|Access granted|Acceso concedido|Zugriff erteilt|已授予访问权限|アクセスを許可済み
            """;
        String data2="""
            Выполните обе команды|Run both commands|Ejecuta ambos comandos|Beide Befehle ausführen|执行两条命令|両方のコマンドを実行
            Проверить доступ|Check access|Comprobar acceso|Zugriff prüfen|检查访问权限|アクセスを確認
            Скопировать команды|Copy commands|Copiar comandos|Befehle kopieren|复制命令|コマンドをコピー
            Android ещё не предоставил оба разрешения.|Android has not granted both permissions yet.|Android aún no ha concedido ambos permisos.|Android hat noch nicht beide Rechte erteilt.|Android 尚未授予两项权限。|Android はまだ両方の権限を許可していません。
            Команды скопированы|Commands copied|Comandos copiados|Befehle kopiert|命令已复制|コマンドをコピーしました
            Выполните обе команды на компьютере, затем проверьте доступ.|Run both commands on your computer, then check access.|Ejecuta ambos comandos en tu equipo y comprueba el acceso.|Führe beide Befehle am Computer aus und prüfe danach den Zugriff.|请在电脑上执行两条命令，然后检查访问权限。|パソコンで両方のコマンドを実行し、アクセスを確認してください。
            Добро пожаловать в U-AVF|Welcome to U-AVF|Te damos la bienvenida a U-AVF|Willkommen bei U-AVF|欢迎使用 U-AVF|U-AVF へようこそ
            Начать|Get started|Empezar|Loslegen|开始使用|はじめる
            Совместимость можно проверить повторно в диагностике.|Compatibility runs once and can be repeated later from Diagnostics.|La comprobación puede repetirse desde Diagnóstico.|Die Kompatibilität lässt sich später in der Diagnose erneut prüfen.|兼容性可稍后在诊断中重新检查。|互換性は後から診断で再確認できます。
            Делиться результатами через Firebase · по желанию|Share test results via Firebase cloud · optional|Compartir resultados mediante Firebase · opcional|Testresultate über Firebase teilen · optional|通过 Firebase 分享测试结果 · 可选|Firebase でテスト結果を共有 · 任意
            Подробнее о приватности|Privacy details|Detalles de privacidad|Details zum Datenschutz|隐私详情|プライバシーの詳細
            Проверяем устройство|Checking this device|Comprobando el dispositivo|Gerät wird geprüft|正在检查设备|デバイスを確認中
            Проверка совместимости|Compatibility test|Prueba de compatibilidad|Kompatibilitätstest|兼容性测试|互換性テスト
            Проверяем требования для запуска U-AVF.|Testing the capabilities needed to run U-AVF.|Comprobando los requisitos de U-AVF.|Die für U-AVF nötigen Funktionen werden geprüft.|正在检查运行 U-AVF 所需的功能。|U-AVF の実行に必要な機能を確認しています。
            Требования проверены. Загрузка гостя проверяется отдельно.|Launch prerequisites verified. Runtime checks are shown separately.|Requisitos verificados. El arranque se comprueba por separado.|Startvoraussetzungen geprüft. Laufzeitprüfungen werden getrennt angezeigt.|启动前提已校验。运行时检查单独显示。|起動の前提条件を確認しました。実行時の確認は別に表示されます。
            Некоторые требования не выполнены.|Some requirements need attention.|Algunos requisitos no se cumplen.|Einige Voraussetzungen sind nicht erfüllt.|部分要求未满足。|一部の要件が満たされていません。
            Платформа Android AVF|Android Virtualization Framework|Plataforma Android AVF|Android Virtualization Framework|Android 虚拟化框架|Android 仮想化フレームワーク
            Система|System|Sistema|System|系统|システム
            Целостность|Integrity|Integridad|Integrität|完整性|整合性
            Проверяем…|Checking…|Comprobando…|Wird geprüft…|正在检查…|確認中…
            Сохраняем результаты…|Saving results…|Guardando resultados…|Ergebnisse werden gespeichert…|正在保存结果…|結果を保存中…
            Проверьте результаты и повторите тест.|Read the results above, then retry.|Revisa los resultados y vuelve a intentarlo.|Lies die Ergebnisse oben und versuche es erneut.|请查看上方结果后重试。|上の結果を確認して再試行してください。
            Повторить проверку|Run again|Repetir prueba|Erneut prüfen|重新检查|再確認
            Пропустить проверку…|Skip test…|Omitir prueba…|Test überspringen…|跳过测试…|テストをスキップ…
            Требования проверены|Prerequisites ready|Requisitos verificados|Voraussetzungen erfüllt|前提已就绪|前提条件が整いました
            Локальные проверки завершены|LOCAL CHECKS COMPLETE|COMPROBACIONES LOCALES COMPLETAS|LOKALE PRÜFUNGEN ABGESCHLOSSEN|本地检查已完成|ローカルチェック完了
            Результаты устройства|Your device results|Resultados del dispositivo|Ergebnisse deines Geräts|你的设备结果|デバイスの結果
            Загрузка и графика проверяются отдельно от файлов.|No boot or GPU success is inferred from file checks.|El arranque y los gráficos se comprueban por separado.|Dateiprüfungen belegen keinen Start und keine GPU-Beschleunigung.|文件检查不能证明启动或 GPU 加速成功。|ファイルの確認だけでは起動や GPU 加速の成功を示しません。
            Запуск гостя|Guest boot|Arranque del invitado|Gaststart|客户机启动|ゲストの起動
            Связь через vsock|vsock communication|Comunicación mediante vsock|vsock-Kommunikation|vsock 通信|vsock 通信
            Рендерер OpenGL|OpenGL renderer|Renderizador OpenGL|OpenGL-Renderer|OpenGL 渲染器|OpenGL レンダラー
            Поделиться отчётом|Share report|Compartir informe|Bericht teilen|分享报告|レポートを共有
            Выберите образ Ubuntu.|Choose where Ubuntu comes from.|Elige una imagen de Ubuntu.|Wähle die Quelle für Ubuntu.|选择 Ubuntu 的来源。|Ubuntu の入手元を選択してください。
            Выберите операционную систему.|Choose the operating system.|Elige el sistema operativo.|Wähle das Betriebssystem.|选择操作系统。|オペレーティングシステムを選択してください。
            Официальный образ Canonical · рекомендуется|Official Canonical image · Recommended|Imagen oficial de Canonical · recomendada|Offizielles Canonical-Abbild · empfohlen|Canonical 官方映像 · 推荐|Canonical 公式イメージ · 推奨
            SHA256 проверен|✓ SHA256 verified|SHA256 verificado|✓ SHA256 geprüft|✓ SHA256 已校验|✓ SHA256 検証済み
            SHA256 проверяется при импорте|SHA256 checked on import|SHA256 se verifica al importar|SHA256 wird beim Import geprüft|导入时校验 SHA256|インポート時に SHA256 を検証
            Сайт Ubuntu ↗|Ubuntu website ↗|Sitio de Ubuntu ↗|Ubuntu-Website ↗|Ubuntu 网站 ↗|Ubuntu のウェブサイト ↗
            Выбрать локальный ISO|Choose local ISO|Elegir ISO local|Lokales ISO wählen|选择本地 ISO|ローカル ISO を選択
            После скачивания выберите ISO Desktop ARM64|After downloading Desktop ARM64, select the ISO here|Tras descargar Desktop ARM64, selecciona el ISO|Wähle nach dem Download von Desktop ARM64 hier das ISO|下载 Desktop ARM64 后在此选择 ISO|Desktop ARM64 をダウンロード後、ここで ISO を選択してください
            Обзор файлов|Browse files|Explorar archivos|Dateien durchsuchen|浏览文件|ファイルを参照
            Рекомендуется|Recommended|Recomendado|Empfohlen|推荐|推奨
            Экспериментально|Experimental|Experimental|Experimentell|实验性|試験的
            Выбрано|✓ Selected|Seleccionado|✓ Ausgewählt|✓ 已选择|✓ 選択済み
            Проверка параметров|Review workspace|Revisar espacio|Arbeitsbereich prüfen|检查工作区|ワークスペースを確認
            Проверьте настройки.|Check your settings.|Revisa los ajustes.|Prüfe deine Einstellungen.|请确认设置。|設定を確認してください。
            Диск и производительность|Storage and performance|Disco y rendimiento|Speicher und Leistung|存储与性能|ストレージとパフォーマンス
            Операционная система|Operating system|Sistema operativo|Betriebssystem|操作系统|オペレーティングシステム
            Образ|Image|Imagen|Abbild|映像|イメージ
            Производительность|Performance|Rendimiento|Leistung|性能|パフォーマンス
            Путь|Location|Ubicación|Speicherort|位置|場所
            Внутренняя память / U-AVF|Internal storage / U-AVF|Almacenamiento interno / U-AVF|Interner Speicher / U-AVF|内部存储 / U-AVF|内部ストレージ / U-AVF
            Виртуальный диск|Virtual disk|Disco virtual|Virtueller Datenträger|虚拟磁盘|仮想ディスク
            Выбрать размер|Choose size|Elegir tamaño|Größe wählen|选择大小|サイズを選択
            Аппаратная графика|Hardware graphics|Gráficos por hardware|Hardware-Grafik|硬件图形|ハードウェア グラフィック
            Настроить CPU и память →|Customize CPU / memory settings →|Configurar CPU y memoria →|CPU- und Speichereinstellungen anpassen →|自定义 CPU / 内存设置 →|CPU とメモリの設定を変更 →
            Проверить параметры|Review|Revisar|Prüfen|检查|確認
            Установлена и готова|Installed · ready|Instalada y lista|Installiert · bereit|已安装 · 就绪|インストール済み · 準備完了
            Воркспейс Ubuntu|Ubuntu workspace|Espacio de Ubuntu|Ubuntu-Arbeitsbereich|Ubuntu 工作区|Ubuntu ワークスペース
            Требуется настройка|Setup required|Requiere configuración|Einrichtung erforderlich|需要设置|セットアップが必要
            Вернуться в Ubuntu|Return to Ubuntu|Volver a Ubuntu|Zu Ubuntu zurückkehren|返回 Ubuntu|Ubuntu に戻る
            Запустить Ubuntu|Launch Ubuntu|Iniciar Ubuntu|Ubuntu starten|启动 Ubuntu|Ubuntu を起動
            Диагностика|Diagnostics|Diagnóstico|Diagnose|诊断|診断
            Производительность, экран и ввод|Performance, display, input|Rendimiento, pantalla y entrada|Leistung, Anzeige, Eingabe|性能、显示与输入|パフォーマンス、画面、入力
            Совместимость и журнал|Compatibility and logs|Compatibilidad y registros|Kompatibilität und Protokolle|兼容性与日志|互換性とログ
            Установка и восстановление|Install & recovery|Instalación y recuperación|Installation und Wiederherstellung|安装与恢复|インストールと復元
            Переустановка, Live и состояние|Reinstall / Live / Setup status|Reinstalación, sesión temporal y estado|Neuinstallation / Live / Einrichtungsstatus|重装 / Live / 设置状态|再インストール / Live / セットアップ状態
            Изменение вступит в силу после перезапуска Ubuntu.|Changes take effect after restarting Ubuntu.|Los cambios se aplican al reiniciar Ubuntu.|Änderungen wirken nach einem Neustart von Ubuntu.|更改将在重启 Ubuntu 后生效。|変更は Ubuntu の再起動後に反映されます。
            Настройка применяется при запуске Ubuntu.|This setting applies when Ubuntu starts.|Este ajuste se aplica al iniciar Ubuntu.|Diese Einstellung gilt beim Start von Ubuntu.|此设置会在 Ubuntu 启动时应用。|この設定は Ubuntu の起動時に適用されます。
            Завершите Ubuntu перед изменением памяти.|Shut down Ubuntu before changing memory.|Apaga Ubuntu antes de cambiar la memoria.|Fahre Ubuntu vor dem Ändern des Arbeitsspeichers herunter.|更改内存前请关闭 Ubuntu。|メモリを変更する前に Ubuntu をシャットダウンしてください。
            Завершите Ubuntu перед изменением процессора.|Shut down Ubuntu before changing the processor.|Apaga Ubuntu antes de cambiar el procesador.|Fahre Ubuntu vor dem Ändern des Prozessors herunter.|更改处理器前请关闭 Ubuntu。|プロセッサを変更する前に Ubuntu をシャットダウンしてください。
            Оставьте для Android не менее 3 ГБ.|Leave at least 3 GB for Android.|Deja al menos 3 GB para Android.|Lass mindestens 3 GB für Android frei.|请为 Android 保留至少 3 GB。|Android 用に 3 GB 以上を残してください。
            Размер резервного вывода.|Fallback display size.|Tamaño de la pantalla alternativa.|Größe der Ersatzanzeige.|备用显示的大小。|代替画面のサイズ。
            Открывают управление VM.|Opens VM controls.|Abre los controles de VM.|Öffnet die VM-Steuerung.|打开 VM 控制。|VM コントロールを開きます。
            Завершите Ubuntu перед сменой образа.|Shut down Ubuntu before changing the image.|Apaga Ubuntu antes de cambiar la imagen.|Fahre Ubuntu vor dem Wechsel des Abbilds herunter.|更换映像前请关闭 Ubuntu。|イメージを変更する前に Ubuntu をシャットダウンしてください。
            Откроются настройки специальных возможностей Android. Включите службу U-AVF VM Keyboard для системных комбинаций. Служба не читает содержимое экрана и текста.|Android accessibility settings will open. Enable U-AVF VM Keyboard to use system shortcuts. The service does not read screen content or text.|Se abrirán los ajustes de accesibilidad de Android. Activa U-AVF VM Keyboard para usar atajos del sistema. El servicio no lee la pantalla ni el texto.|Die Android-Bedienungshilfen werden geöffnet. Aktiviere U-AVF VM Keyboard für Systemkürzel. Der Dienst liest weder Bildschirminhalt noch Text.|将打开 Android 无障碍设置。启用 U-AVF VM Keyboard 以使用系统快捷键。该服务不读取屏幕内容或文字。|Android のユーザー補助設定が開きます。システムショートカットを使うには U-AVF VM Keyboard を有効にしてください。このサービスは画面内容やテキストを読み取りません。
            Канал ввода ещё не подключён.|The input channel is not connected yet.|El canal de entrada aún no está conectado.|Der Eingabekanal ist noch nicht verbunden.|输入通道尚未连接。|入力チャネルはまだ接続されていません。
            Экспериментальный профиль Windows ARM64|Experimental Windows ARM64 profile|Perfil experimental de Windows ARM64|Experimentelles Windows-ARM64-Profil|实验性 Windows ARM64 配置|試験的な Windows ARM64 プロファイル
            Параметры виртуальной машины закреплены в проверенном профиле запуска.|The virtual machine settings are fixed by the tested boot profile.|La configuración de la máquina virtual está fijada por el perfil de arranque probado.|Die Einstellungen der virtuellen Maschine sind durch das geprüfte Startprofil festgelegt.|虚拟机设置由已验证的启动配置固定。|仮想マシンの設定は検証済みの起動プロファイルで固定されています。
            Экспериментальный запуск Windows|Experimental Windows boot|Arranque experimental de Windows|Experimenteller Windows-Start|实验性 Windows 启动|試験的な Windows 起動
            Запустить Windows|Start Windows|Iniciar Windows|Windows starten|启动 Windows|Windows を起動
            Выбрать другой воркспейс|Switch workspace|Cambiar de espacio|Arbeitsbereich wechseln|切换工作区|ワークスペースを切り替え
            Открыть журнал|Open journal|Abrir registro|Protokoll öffnen|打开日志|ログを開く
            Команды установки не изменяют ISO.|The installation does not modify the ISO.|La instalación no modifica el ISO.|Die Installation verändert das ISO nicht.|安装过程不会修改 ISO。|インストールは ISO を変更しません。
            Подготовить установщик|Continue to installer|Ir al instalador|Zum Installer|进入安装程序|インストーラーへ進む
            Безопасная установка Ubuntu|Install Ubuntu safely|Instalar Ubuntu de forma segura|Ubuntu sicher installieren|安全安装 Ubuntu|Ubuntu を安全にインストール
            Переустановить Ubuntu?|Reinstall Ubuntu?|¿Reinstalar Ubuntu?|Ubuntu neu installieren?|重新安装 Ubuntu？|Ubuntu を再インストールしますか？
            Переустановить Ubuntu|Reinstall Ubuntu|Reinstalar Ubuntu|Ubuntu neu installieren|重新安装 Ubuntu|Ubuntu を再インストール
            Восстановить или переустановить Ubuntu?|Repair or reinstall Ubuntu?|¿Reparar o reinstalar Ubuntu?|Ubuntu reparieren oder neu installieren?|修复还是重新安装 Ubuntu？|Ubuntu を修復または再インストールしますか？
            Удалить воркспейс Ubuntu?|Delete Ubuntu workspace?|¿Eliminar el espacio de Ubuntu?|Ubuntu-Arbeitsbereich löschen?|删除 Ubuntu 工作区？|Ubuntu ワークスペースを削除しますか？
            Удалить воркспейс|Delete workspace|Eliminar espacio|Arbeitsbereich löschen|删除工作区|ワークスペースを削除
            Пропустить проверку?|Skip compatibility test?|¿Omitir la prueba de compatibilidad?|Kompatibilitätstest überspringen?|跳过兼容性测试？|互換性テストをスキップしますか？
            Продолжить без проверки|Continue unverified|Continuar sin verificar|Ohne Prüfung fortfahren|不校验继续|未検証で続行
            Развёртывание Ubuntu|Ubuntu system storage|Almacenamiento de Ubuntu|Ubuntu-Systemspeicher|Ubuntu 系统存储|Ubuntu のシステムストレージ
            Расширить диск Ubuntu|Expand Ubuntu storage|Ampliar almacenamiento de Ubuntu|Ubuntu-Speicher erweitern|扩展 Ubuntu 存储|Ubuntu のストレージを拡張
            Принудительно перезапустить VM?|Force restart VM?|¿Forzar el reinicio de la VM?|VM erzwungen neu starten?|强制重启 VM？|VM を強制再起動しますか？
            Принудительно остановить VM?|Force stop VM?|¿Forzar la detención de la VM?|VM erzwungen stoppen?|强制停止 VM？|VM を強制停止しますか？
            Принудительно|Force|Forzar|Erzwingen|强制|強制
            Работа будет прервана без штатного выключения. Несохранённые данные могут быть потеряны, файловая система гостя может быть повреждена.|This cuts power without a guest shutdown. Unsaved data may be lost and the guest filesystem may be damaged.|Esto corta la alimentación sin apagar el invitado. Pueden perderse datos sin guardar y dañarse su sistema de archivos.|Dies unterbricht die Stromversorgung ohne Herunterfahren des Gasts. Nicht gespeicherte Daten können verloren gehen und das Dateisystem des Gasts kann beschädigt werden.|这将在客户机未关机的情况下断电。未保存的数据可能丢失，客户机文件系统可能损坏。|ゲストをシャットダウンせずに電源を切ります。保存していないデータが失われたり、ゲストのファイルシステムが破損する可能性があります。
            Пропуск оставит устройство непроверенным. Проверку можно повторить из диагностики. Требуемые разрешения и проверка образа остаются обязательными.|This device will remain unverified. You can open the dashboard and repeat the test from Diagnostics. Required VM permissions and image integrity checks still apply.|El dispositivo quedará sin verificar. Puedes repetir la prueba desde Diagnóstico. Los permisos de VM y la integridad de la imagen siguen siendo obligatorios.|Das Gerät bleibt ungeprüft. Du kannst den Test später in der Diagnose wiederholen. Nötige VM-Rechte und die Prüfung der Abbildintegrität bleiben erforderlich.|设备将保持未校验状态。你可以在诊断中重复测试。所需的 VM 权限与映像完整性检查仍然有效。|デバイスは未検証のままになります。診断からテストを再実行できます。必要な VM 権限とイメージ整合性の確認は引き続き必要です。
            На разделе Ubuntu будут удалены личные файлы и настройки. Официальный образ и раздел загрузки U-AVF сохранятся. Перед запуском приложение проверит разметку.|This replaces the Ubuntu system on its dedicated vda3 partition. Personal files and settings on that Ubuntu partition will be erased. The official installer image on vda2 and U-AVF boot partition on vda1 are preserved. The app verifies the disk layout before it starts.|Se borrarán los archivos y ajustes personales de la partición de Ubuntu. Se conservarán el ISO oficial y la partición de arranque de U-AVF. La aplicación verificará las particiones antes de empezar.|Dies ersetzt das Ubuntu-System auf der dedizierten Partition vda3. Persönliche Dateien und Einstellungen auf dieser Partition werden gelöscht. Das offizielle Installer-Abbild auf vda2 und die U-AVF-Startpartition auf vda1 bleiben erhalten. Die App prüft das Partitionslayout vor dem Start.|这会替换专用 vda3 分区上的 Ubuntu 系统。该分区上的个人文件和设置将被删除。vda2 上的官方安装映像和 vda1 上的 U-AVF 启动分区会保留。应用会在启动前校验磁盘布局。|専用の vda3 パーティション上の Ubuntu システムを置き換えます。そのパーティションの個人ファイルと設定は削除されます。vda2 の公式インストーラーイメージと vda1 の U-AVF 起動パーティションは保持されます。起動前にアプリがディスク構成を検証します。
            Диск Ubuntu и установленные файлы будут удалены без возможности восстановления. Официальный ISO и другие данные приложения сохранятся.|This permanently deletes U-AVF's persistent Ubuntu workspace disk and installed files. The downloaded official Ubuntu ISO and other app data are kept. This cannot be undone.|El disco de Ubuntu y sus archivos se eliminarán de forma permanente. Se conservarán el ISO oficial y los demás datos de la aplicación.|Dies löscht den dauerhaften Ubuntu-Datenträger und die installierten Dateien von U-AVF endgültig. Das heruntergeladene offizielle Ubuntu-ISO und andere App-Daten bleiben erhalten. Das lässt sich nicht rückgängig machen.|这将永久删除 U-AVF 的持久 Ubuntu 工作区磁盘和已安装文件。已下载的官方 Ubuntu ISO 及其他应用数据会保留。此操作无法撤销。|U-AVF の永続 Ubuntu ワークスペースディスクとインストール済みファイルを完全に削除します。ダウンロード済みの公式 Ubuntu ISO とその他のアプリデータは保持されます。この操作は取り消せません。
            Выберите объём диска Ubuntu. Загрузочные файлы и ISO занимают дополнительное место.|Choose space for Ubuntu. Boot files and the ISO use additional space.|Elige el espacio para Ubuntu. Los archivos de arranque y el ISO ocupan espacio adicional.|Wähle den Speicherplatz für Ubuntu. Startdateien und das ISO benötigen zusätzlichen Platz.|选择 Ubuntu 的磁盘空间。启动文件和 ISO 会额外占用空间。|Ubuntu 用の容量を選択してください。起動ファイルと ISO が追加で容量を使います。
            Виртуальные машины ARM64 на Android|ARM64 virtual machines on Android|Máquinas virtuales ARM64 en Android|ARM64-VMs unter Android|Android 上的 ARM64 虚拟机|Android 上の ARM64 仮想マシン
            Проверка устройства|Device check|Comprobación del dispositivo|Geräteprüfung|设备检查|デバイスの確認
            Устройство подготовлено|Device ready|Dispositivo preparado|Gerät bereit|设备已就绪|デバイスの準備完了
            Проверка не завершена|Check incomplete|Comprobación incompleta|Prüfung unvollständig|检查未完成|確認未完了
            Локальная проверка|Local check|Comprobación local|Lokale Prüfung|本地检查|ローカル確認
            Результат|Result|Resultado|Ergebnis|结果|結果
            Пройдено|Passed|Superada|Bestanden|通过|合格
            """;
        String data3="""
            Нужен доступ|Access required|Se requiere acceso|Zugriff erforderlich|需要访问权限|アクセス権が必要
            Повторить|Retry|Reintentar|Wiederholen|重试|再試行
            Действия воркспейса|Workspace actions|Acciones del espacio|Arbeitsbereich-Aktionen|工作区操作|ワークスペースの操作
            Воркспейс|Workspace|Espacio|Arbeitsbereich|工作区|ワークスペース
            Переименовать…|Rename…|Cambiar nombre…|Umbenennen…|重命名…|名前を変更…
            Параметры|Preferences|Preferencias|Einstellungen|首选项|環境設定
            Переустановить…|Reinstall…|Reinstalar…|Neu installieren…|重新安装…|再インストール…
            Удалить…|Delete…|Eliminar…|Löschen…|删除…|削除…
            Название воркспейса|Workspace name|Nombre del espacio|Name des Arbeitsbereichs|工作区名称|ワークスペース名
            Сохранить|Save|Guardar|Speichern|保存|保存
            Новый воркспейс|New workspace|Nuevo espacio|Neuer Arbeitsbereich|新工作区|新しいワークスペース
            Новый Ubuntu|New Ubuntu workspace|Nuevo espacio de Ubuntu|Neuer Ubuntu-Arbeitsbereich|新 Ubuntu 工作区|新しい Ubuntu ワークスペース
            Ubuntu · Новый|Ubuntu · New|Ubuntu · Nuevo|Ubuntu · Neu|Ubuntu · 新建|Ubuntu · 新規
            Название|Name|Nombre|Name|名称|名前
            Установочный ISO|Installer ISO|ISO de instalación|Installer-ISO|安装 ISO|インストーラー ISO
            Выбрать файл…|Choose file…|Elegir archivo…|Datei wählen…|选择文件…|ファイルを選択…
            Выбрать ISO|Choose ISO|Elegir ISO|ISO wählen|选择 ISO|ISO を選択
            Среда рабочего стола|Desktop environment|Entorno de escritorio|Desktopumgebung|桌面环境|デスクトップ環境
            Графическая сессия|Graphics session|Sesión gráfica|Grafiksitzung|图形会话|グラフィックセッション
            Фиксированный профиль|Fixed profile|Perfil fijo|Festes Profil|固定配置|固定プロファイル
            Отправлять результаты проверок|Send device check results|Enviar resultados de comprobación|Prüfergebnisse senden|发送检查结果|確認結果を送信
            Виртуальные процессоры|Virtual processors|Procesadores virtuales|Virtuelle Prozessoren|虚拟处理器|仮想プロセッサ
            Все ядра|All cores|Todos los núcleos|Alle Kerne|全部核心|すべてのコア
            Графический вывод|Display pipeline|Modo de pantalla|Anzeigepipeline|显示管线|表示パイプライン
            Резервный|Fallback|Alternativo|Ersatz|备用|代替
            Резервный вывод. Ниже частота кадров, можно выбрать разрешение.|Fallback display. Lower frame rate and a choice of resolution.|Pantalla alternativa. Menor frecuencia y resolución ajustable.|Ersatzanzeige. Geringere Bildrate, Auflösung wählbar.|备用显示。帧率较低，可选择分辨率。|代替画面。フレームレートは低めで、解像度を選択できます。
            Аппаратный OpenGL (VirGL) и передача видео H.264.|Hardware OpenGL (VirGL) with H.264 video.|OpenGL por hardware (VirGL) con vídeo H.264.|Hardware-OpenGL (VirGL) mit H.264-Video.|硬件 OpenGL (VirGL) 与 H.264 视频。|ハードウェア OpenGL (VirGL) と H.264 ビデオ。
            GPU Mali и прямой вывод на экран без видеокодека. Экспериментально.|Mali GPU and direct display without a video codec. Experimental.|GPU Mali y salida directa sin códec de vídeo. Experimental.|Mali-GPU und direkte Anzeige ohne Videocodec. Experimentell.|Mali GPU 与无视频编解码器的直接显示。实验性。|Mali GPU とビデオコーデックなしの直接表示。試験的。
            Завершите Ubuntu перед сменой режима вывода.|Shut down Ubuntu before changing the display mode.|Apaga Ubuntu antes de cambiar el modo de pantalla.|Fahre Ubuntu vor dem Wechsel des Anzeigemodus herunter.|切换显示模式前请关闭 Ubuntu。|表示モードを変更する前に Ubuntu をシャットダウンしてください。
            Открыть панель VM|Open VM controls|Abrir controles de VM|VM-Steuerung öffnen|打开 VM 控制|VM コントロールを開く
            Передаёт системные сочетания в VM. Для этого нужна служба U-AVF.|Send system shortcuts to the VM. Requires the U-AVF service.|Envía atajos del sistema a la VM. Requiere el servicio de U-AVF.|Sendet Systemkürzel an die VM. Erfordert den U-AVF-Dienst.|将系统快捷键发送到 VM。需要 U-AVF 服务。|システムショートカットを VM に送ります。U-AVF サービスが必要です。
            Завершите сеанс Windows перед изменением ресурсов Ubuntu.|Shut down Windows before changing Ubuntu resources.|Apaga Windows antes de cambiar los recursos de Ubuntu.|Fahre Windows vor dem Ändern der Ubuntu-Ressourcen herunter.|更改 Ubuntu 资源前请关闭 Windows。|Ubuntu のリソースを変更する前に Windows をシャットダウンしてください。
            Сначала завершите текущий сеанс VM.|Shut down the current VM session first.|Apaga primero la sesión de VM actual.|Beende zuerst die aktuelle VM-Sitzung.|请先结束当前 VM 会话。|先に現在の VM セッションを終了してください。
            Как у устройства|Match-host vCPU|Igual que el dispositivo|Wie Gerät|与设备相同|デバイスと同じ
            Открыть существующий воркспейс|Open existing workspace|Abrir espacio existente|Vorhandenen Arbeitsbereich öffnen|打开现有工作区|既存のワークスペースを開く
            Продолжить настройку|Continue existing setup|Continuar configuración existente|Einrichtung fortsetzen|继续设置|セットアップを続ける
            Создать и установить|Create & install|Crear e instalar|Erstellen und installieren|创建并安装|作成してインストール
            U-AVF запустит установщик с проверенной разметкой. Раздел загрузки и исходный ISO сохранятся. Язык, клавиатуру и учётную запись выберите в Ubuntu Setup. Не меняйте подготовленную разметку.|U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. You will choose your language, keyboard and account in Ubuntu Setup; do not change its storage layout.|U-AVF iniciará el instalador con las particiones verificadas. Se conservan la partición de arranque y la imagen ISO. Elige el idioma, el teclado y la cuenta en Ubuntu Setup. No cambies las particiones preparadas.|U-AVF startet den geführten Installer mit dem geprüften Layout. Die U-AVF-Startpartition und das offizielle Ubuntu-Abbild bleiben erhalten, nur die dedizierte Systempartition wird formatiert. Sprache, Tastatur und Konto wählst du in Ubuntu Setup; ändere dort das Speicherlayout nicht.|U-AVF 将使用已验证的布局启动引导安装程序。U-AVF 启动分区和官方 Ubuntu 映像会保留，仅格式化专用系统分区。请在 Ubuntu Setup 中选择语言、键盘和账户；不要更改其存储布局。|U-AVF は検証済みのレイアウトでガイド付きインストーラーを開始します。U-AVF 起動パーティションと公式 Ubuntu イメージは保持され、専用のシステムパーティションのみがフォーマットされます。言語、キーボード、アカウントは Ubuntu Setup で選択してください。ストレージ構成は変更しないでください。
            U-AVF запустит установщик с проверенной разметкой. Раздел загрузки и исходный ISO сохранятся. Сделайте резервную копию файлов Ubuntu перед продолжением.|U-AVF will start the guided installer using the verified layout. It preserves the U-AVF boot partition and official Ubuntu image, and formats only the dedicated Ubuntu system partition. Back up any files on the existing Ubuntu system first. Do not change the storage layout in Ubuntu Setup.|U-AVF iniciará el instalador con las particiones verificadas. Se conservan la partición de arranque y la imagen ISO. Haz una copia de tus archivos de Ubuntu antes de continuar.|U-AVF startet den geführten Installer mit dem geprüften Layout. Die U-AVF-Startpartition und das offizielle Ubuntu-Abbild bleiben erhalten, nur die dedizierte Systempartition wird formatiert. Sichere vorher Dateien auf dem vorhandenen Ubuntu-System. Ändere das Speicherlayout in Ubuntu Setup nicht.|U-AVF 将使用已验证的布局启动引导安装程序。U-AVF 启动分区和官方 Ubuntu 映像会保留，仅格式化专用系统分区。请先备份现有 Ubuntu 系统中的文件。不要更改 Ubuntu Setup 中的存储布局。|U-AVF は検証済みのレイアウトでガイド付きインストーラーを開始します。U-AVF 起動パーティションと公式 Ubuntu イメージは保持され、専用のシステムパーティションのみがフォーマットされます。先に既存の Ubuntu システムのファイルをバックアップしてください。Ubuntu Setup のストレージ構成は変更しないでください。
            Запустите VM, чтобы захватить мышь.|Start a VM before capturing the mouse.|Inicia una VM antes de capturar el ratón.|Starte zuerst eine VM, um die Maus zu erfassen.|请先启动 VM 再捕获鼠标。|マウスをキャプチャする前に VM を起動してください。
            Переподключение к активному сеансу Linux…|Reconnecting to the active Linux session…|Reconectando con la sesión de Linux activa…|Verbindung zur aktiven Linux-Sitzung wird wiederhergestellt…|正在重新连接活动的 Linux 会话…|実行中の Linux セッションに再接続しています…
            Нет запущенной виртуальной машины.|There is no running virtual machine to return to.|No hay ninguna máquina virtual en ejecución.|Es läuft keine virtuelle Maschine.|没有正在运行的虚拟机。|実行中の仮想マシンはありません。
            Сначала включите отправку результатов на экране приветствия.|Enable report sharing on the Welcome screen first.|Activa primero el envío de resultados en la pantalla de bienvenida.|Aktiviere zuerst das Teilen von Ergebnissen im Willkommensbildschirm.|请先在欢迎界面启用结果分享。|先にようこそ画面で結果の共有を有効にしてください。
            Найдено завершённое переключение загрузки Ubuntu; запускаю установленную систему.|Found the completed Ubuntu boot handoff; starting the persistent installation.|Se encontró el traspaso de arranque de Ubuntu completado; iniciando la instalación persistente.|Abgeschlossene Ubuntu-Startübergabe gefunden; die dauerhafte Installation wird gestartet.|已找到完成的 Ubuntu 启动交接，正在启动持久安装。|完了した Ubuntu 起動ハンドオフを検出しました。永続インストールを起動します。
            Не удалось проверить переход установленной Ubuntu:|Could not verify the installed Ubuntu handoff: |No se pudo verificar el traspaso de Ubuntu instalado: |Die Startübergabe der installierten Ubuntu konnte nicht geprüft werden: |无法校验已安装 Ubuntu 的启动交接：|インストール済み Ubuntu のハンドオフを検証できませんでした:
            Для существующего воркспейса используйте расширение диска Ubuntu.|Use Expand Ubuntu storage for an existing workspace.|Usa «Ampliar almacenamiento de Ubuntu» para un espacio existente.|Nutze „Ubuntu-Speicher erweitern“ für einen vorhandenen Arbeitsbereich.|已有工作区请使用扩展 Ubuntu 存储。|既存のワークスペースでは Ubuntu ストレージの拡張を使用してください。
            Недостаточно места в Android для этого размера, ISO и резерва 2 ГБ.|Not enough Android storage for this size, ISO and 2 GiB reserve.|No hay espacio suficiente en Android para este tamaño, ISO y la reserva de 2 GiB.|Nicht genügend Android-Speicher für diese Größe, das ISO und 2 GiB Reserve.|Android 存储不足，无法容纳该大小、ISO 与 2 GiB 预留空间。|このサイズ、ISO、2 GiB の予備領域に必要な Android ストレージが足りません。
            Операция с хранилищем уже выполняется.|Storage operation is already running.|Ya hay una operación de almacenamiento en curso.|Es läuft bereits ein Speichervorgang.|存储操作已在进行中。|ストレージ操作はすでに実行中です。
            Подготовка Windows уже выполняется. Дождитесь завершения.|Windows preparation is already running. Wait for it to finish.|La preparación de Windows ya está en curso. Espera a que termine.|Die Windows-Vorbereitung läuft bereits. Warte, bis sie abgeschlossen ist.|Windows 准备已在进行中，请等待完成。|Windows の準備はすでに実行中です。完了までお待ちください。
            Завершите Ubuntu перед расширением диска.|Stop Ubuntu before expanding storage.|Detén Ubuntu antes de ampliar el almacenamiento.|Fahre Ubuntu vor dem Erweitern des Speichers herunter.|扩展存储前请关闭 Ubuntu。|ストレージを拡張する前に Ubuntu を停止してください。
            Сначала завершите установку Ubuntu.|Finish Ubuntu installation first.|Termina primero la instalación de Ubuntu.|Schließe zuerst die Ubuntu-Installation ab.|请先完成 Ubuntu 安装。|先に Ubuntu のインストールを完了してください。
            Запустите Ubuntu, чтобы завершить предыдущее расширение файловой системы.|Start Ubuntu to finish the previous filesystem expansion first.|Inicia Ubuntu para terminar antes la ampliación del sistema de archivos.|Starte Ubuntu, um die vorherige Dateisystem-Erweiterung abzuschließen.|请启动 Ubuntu 以先完成上次的文件系统扩展。|前回のファイルシステム拡張を完了するため、Ubuntu を起動してください。
            Недостаточно места в Android; оставьте не менее 2 ГБ свободными.|Not enough Android storage; keep at least 2 GiB free.|Espacio insuficiente en Android; deja al menos 2 GiB libres.|Nicht genügend Android-Speicher; lasse mindestens 2 GiB frei.|Android 存储不足，请保留至少 2 GiB 可用空间。|Android の空き容量が不足しています。2 GiB 以上を空けてください。
            Не удалось проверить хранилище:|Storage check failed: |Falló la comprobación del almacenamiento: |Speicherprüfung fehlgeschlagen: |存储检查失败：|ストレージの確認に失敗しました:
            Раздел расширен. Запустите Ubuntu, чтобы завершить расширение ext4.|Partition expanded. Start Ubuntu to finish ext4 expansion.|Partición ampliada. Inicia Ubuntu para completar la ampliación de ext4.|Partition erweitert. Starte Ubuntu, um die ext4-Erweiterung abzuschließen.|分区已扩展。启动 Ubuntu 以完成 ext4 扩展。|パーティションを拡張しました。Ubuntu を起動して ext4 の拡張を完了してください。
            Не удалось расширить хранилище:|Storage expansion not completed: |No se completó la ampliación del almacenamiento: |Speichererweiterung nicht abgeschlossen: |存储扩展未完成：|ストレージの拡張が完了しませんでした:
            Не удалось запустить установку Ubuntu:|Ubuntu installation could not start: |No se pudo iniciar la instalación de Ubuntu: |Ubuntu-Installation konnte nicht starten: |无法启动 Ubuntu 安装：|Ubuntu のインストールを開始できませんでした:
            Завершите работающую VM перед открытием установщика Ubuntu или установленной системы.|Stop the running VM before opening the Ubuntu installer or installed system.|Detén la VM en ejecución antes de abrir el instalador de Ubuntu o el sistema instalado.|Stoppe die laufende VM, bevor du den Ubuntu-Installer oder das installierte System öffnest.|打开 Ubuntu 安装程序或已安装系统前，请停止正在运行的 VM。|Ubuntu インストーラーまたはインストール済みシステムを開く前に、実行中の VM を停止してください。
            Требуется одна строка ограниченной длины.|One bounded line required.|Se requiere una sola línea acotada.|Eine einzelne Zeile ist erlaubt.|仅允许一行有限长度的内容。|1 行のみ入力できます。
            Строка отправлена в консоль гостя.|Guest console line sent.|Línea enviada a la consola del invitado.|Zeile an die Gastkonsole gesendet.|已向客户机控制台发送该行。|ゲストコンソールに送信しました。
            Консоль гостя недоступна:|Guest console unavailable: |La consola del invitado no está disponible: |Gastkonsole nicht verfügbar: |客户机控制台不可用：|ゲストコンソールを利用できません:
            Завершите работающую VM перед удалением воркспейса.|Stop the running VM before deleting its workspace.|Detén la VM en ejecución antes de eliminar su espacio.|Stoppe die laufende VM, bevor du ihren Arbeitsbereich löschst.|删除工作区前请停止正在运行的 VM。|ワークスペースを削除する前に実行中の VM を停止してください。
            Воркспейс Ubuntu удалён. Проверенный исходный ISO сохранён.|Ubuntu workspace deleted. The verified source ISO was kept.|Espacio de Ubuntu eliminado. Se conservó el ISO original verificado.|Ubuntu-Arbeitsbereich gelöscht. Das geprüfte Quell-ISO bleibt erhalten.|Ubuntu 工作区已删除，已验证的源 ISO 已保留。|Ubuntu ワークスペースを削除しました。検証済みの元 ISO は保持されています。
            Воркспейс не удалён:|Workspace was not deleted: |No se eliminó el espacio: |Arbeitsbereich wurde nicht gelöscht: |工作区未删除：|ワークスペースは削除されませんでした:
            Переподключено к работающему сеансу Linux.|Reconnected to the running Linux session.|Reconectado a la sesión de Linux en ejecución.|Verbindung zur laufenden Linux-Sitzung wiederhergestellt.|已重新连接到运行中的 Linux 会话。|実行中の Linux セッションに再接続しました。
            Не удалось открыть выбор файла:|Could not open image picker: |No se pudo abrir el selector de archivos: |Dateiauswahl konnte nicht geöffnet werden: |无法打开文件选择器：|ファイル選択を開けませんでした:
            Проверка выбранного официального образа Ubuntu…|Checking the selected official Ubuntu image…|Comprobando la imagen oficial de Ubuntu seleccionada…|Das gewählte offizielle Ubuntu-Abbild wird geprüft…|正在校验选定的官方 Ubuntu 映像…|選択した公式 Ubuntu イメージを検証しています…
            Выбранный образ Ubuntu совпадает с уже подготовленной копией в U-AVF.|The selected Ubuntu image matches the copy already staged in U-AVF.|La imagen de Ubuntu seleccionada coincide con la copia ya preparada en U-AVF.|Das gewählte Ubuntu-Abbild entspricht der bereits in U-AVF vorbereiteten Kopie.|所选 Ubuntu 映像与 U-AVF 中已准备的副本一致。|選択した Ubuntu イメージは U-AVF に準備済みのコピーと一致します。
            Импорт образа Ubuntu:|Importing Ubuntu image: |Importando la imagen de Ubuntu: |Ubuntu-Abbild wird importiert: |正在导入 Ubuntu 映像：|Ubuntu イメージをインポート中:
            Официальный образ Ubuntu 24.04.5 ARM64 проверен и готов.|Official Ubuntu 24.04.5 ARM64 image verified and ready.|Imagen oficial Ubuntu 24.04.5 ARM64 verificada y lista.|Offizielles Ubuntu-24.04.5-ARM64-Abbild geprüft und bereit.|官方 Ubuntu 24.04.5 ARM64 映像已验证并准备就绪。|公式 Ubuntu 24.04.5 ARM64 イメージを検証しました。準備完了です。
            Образ Ubuntu не принят:|Ubuntu image was not accepted: |La imagen de Ubuntu no fue aceptada: |Ubuntu-Abbild wurde nicht akzeptiert: |Ubuntu 映像未被接受：|Ubuntu イメージは受け入れられませんでした:
            Нет доступного браузера. Откройте cdimage.ubuntu.com и скачайте Ubuntu Desktop ARM64.|No browser available. Open cdimage.ubuntu.com to download Ubuntu Desktop ARM64.|No hay navegador disponible. Abre cdimage.ubuntu.com para descargar Ubuntu Desktop ARM64.|Kein Browser verfügbar. Öffne cdimage.ubuntu.com, um Ubuntu Desktop ARM64 herunterzuladen.|没有可用的浏览器。请打开 cdimage.ubuntu.com 下载 Ubuntu Desktop ARM64。|利用できるブラウザがありません。cdimage.ubuntu.com で Ubuntu Desktop ARM64 をダウンロードしてください。
            Подключение к серверам Ubuntu…|Connecting to Ubuntu servers…|Conectando con los servidores de Ubuntu…|Verbindung zu den Ubuntu-Servern…|正在连接 Ubuntu 服务器…|Ubuntu サーバーに接続しています…
            Скачивание официального образа Ubuntu:|Downloading official Ubuntu image: |Descargando la imagen oficial de Ubuntu: |Offizielles Ubuntu-Abbild wird heruntergeladen: |正在下载官方 Ubuntu 映像：|公式 Ubuntu イメージをダウンロード中:
            Официальный образ Ubuntu скачан и проверен.|Official Ubuntu image downloaded and verified.|Imagen oficial de Ubuntu descargada y verificada.|Offizielles Ubuntu-Abbild heruntergeladen und geprüft.|官方 Ubuntu 映像已下载并校验。|公式 Ubuntu イメージをダウンロードして検証しました。
            Проверенный образ Ubuntu уже готов.|The verified Ubuntu image is already ready.|La imagen verificada de Ubuntu ya está lista.|Das geprüfte Ubuntu-Abbild ist bereits bereit.|已验证的 Ubuntu 映像已就绪。|検証済みの Ubuntu イメージはすでに準備済みです。
            Не удалось скачать Ubuntu:|Ubuntu download failed: |Falló la descarga de Ubuntu: |Ubuntu-Download fehlgeschlagen: |Ubuntu 下载失败：|Ubuntu のダウンロードに失敗しました:
            Не удалось сохранить отчёт о готовности:|Could not save readiness report: |No se pudo guardar el informe de preparación: |Bereitschaftsbericht konnte nicht gespeichert werden: |无法保存就绪报告：|準備状況レポートを保存できませんでした:
            Сначала выберите официальный образ Ubuntu 24.04.5 ARM64.|Choose the official Ubuntu 24.04.5 ARM64 image first.|Elige primero la imagen oficial Ubuntu 24.04.5 ARM64.|Wähle zuerst das offizielle Ubuntu-24.04.5-ARM64-Abbild.|请先选择官方 Ubuntu 24.04.5 ARM64 映像。|先に公式 Ubuntu 24.04.5 ARM64 イメージを選択してください。
            VM уже запущена. Остановите её перед запуском другого профиля.|A VM is already running. Stop it before launching another profile.|Ya hay una VM en ejecución. Detenla antes de iniciar otro perfil.|Es läuft bereits eine VM. Stoppe sie, bevor du ein anderes Profil startest.|VM 已在运行。启动其他配置前请先停止它。|VM はすでに実行中です。別のプロファイルを起動する前に停止してください。
            Нет запущенной VM.|No running VM.|No hay ninguna VM en ejecución.|Keine laufende VM.|没有正在运行的 VM。|実行中の VM はありません。
            Запрос питания гостя:|Guest power request: |Solicitud de energía del invitado: |Energieanforderung des Gasts: |客户机电源请求：|ゲストの電源要求:
            Управление гостем недоступно. Жёсткая остановка не выполнялась; ожидание перед предложением восстановления.|Guest control unavailable. No hard stop was performed; waiting before offering recovery.|El control del invitado no está disponible. No se forzó la detención; esperando antes de ofrecer recuperación.|Gaststeuerung nicht verfügbar. Es wurde kein hartes Stoppen ausgeführt; vor dem Anbieten der Wiederherstellung wird gewartet.|客户机控制不可用。未执行强制停止；在提供恢复选项前继续等待。|ゲスト制御を利用できません。強制停止は行っていません。復元を提示する前に待機します。
            Нет запущенной VM:|No running |No hay VM en ejecución: |Keine laufende VM: |没有正在运行的 VM：|実行中の VM はありません:
            Не удалось остановить VM:|Could not stop VM: |No se pudo detener la VM: |VM konnte nicht gestoppt werden: |无法停止 VM：|VM を停止できませんでした:
            Проверка диска пропущена для|Disk verification skipped for |Verificación de disco omitida para|Datenträgerprüfung übersprungen für|已跳过磁盘校验：|ディスク検証をスキップしました:
            Запустите установленную Ubuntu перед проверкой изолированного H.264-вывода.|Start installed Ubuntu before the isolated encoded display proof.|Inicia Ubuntu instalado antes de la prueba aislada de pantalla codificada.|Starte das installierte Ubuntu vor dem isolierten H.264-Anzeigetest.|在测试隔离的 H.264 显示前，请启动已安装的 Ubuntu。|分離された H.264 表示テストの前に、インストール済み Ubuntu を起動してください。
            Mavk-вывод остановлен. Отключите экспериментальный режим и перезапустите Ubuntu, чтобы вернуться к стабильному выводу.|Mavk display stopped. Disable the experimental option and restart to return to the stable display.|La pantalla Mavk se detuvo. Desactiva la opción experimental y reinicia para volver a la pantalla estable.|Mavk-Anzeige gestoppt. Deaktiviere die experimentelle Option und starte neu, um zur stabilen Anzeige zurückzukehren.|Mavk 显示已停止。请关闭实验选项并重新启动以返回稳定显示。|Mavk 表示が停止しました。試験的オプションを無効にして再起動すると、安定した表示に戻ります。
            H.264-вывод недоступен. Восстанавливается стабильный вывод.|Encoded display unavailable. Restoring stable display.|Pantalla codificada no disponible. Restaurando la pantalla estable.|Kodierte Anzeige nicht verfügbar. Stabile Anzeige wird wiederhergestellt.|H.264 显示不可用。正在恢复稳定显示。|H.264 表示を利用できません。安定した表示に戻しています。
            Не удалось запустить мост кадров Ubuntu:|Ubuntu frame bridge failed: |Falló el puente de fotogramas de Ubuntu: |Ubuntu-Frame-Bridge fehlgeschlagen: |Ubuntu 帧桥接失败：|Ubuntu フレームブリッジに失敗しました:
            Перезагрузка Ubuntu: переподключение воркспейса…|Ubuntu reboot: reconnecting the workspace…|Reinicio de Ubuntu: reconectando el espacio…|Ubuntu-Neustart: Arbeitsbereich wird verbunden…|Ubuntu 重启：正在重新连接工作区…|Ubuntu 再起動: ワークスペースに再接続しています…
            Сеанс установки завершён; проверка перехода к установленной системе.|Install session ended; checking the persistent boot handoff.|La sesión de instalación terminó; comprobando el traspaso de arranque persistente.|Installationssitzung beendet; die dauerhafte Startübergabe wird geprüft.|安装会话已结束；正在检查持久启动交接。|インストールセッションが終了しました。永続起動のハンドオフを確認しています。
            VM остановлена.|VM stopped.|VM detenida.|VM gestoppt.|VM 已停止。|VM を停止しました。
            Ошибка VM:|VM error: |Error de la VM: |VM-Fehler: |VM 错误：|VM エラー:
            Маркер загрузки установленной Ubuntu не найден; постоянный диск не изменялся.|No installed Ubuntu boot marker was found; the persistent disk was left untouched.|No se encontró el marcador de arranque de Ubuntu instalado; el disco persistente quedó intacto.|Kein Startmarker der installierten Ubuntu gefunden; der dauerhafte Datenträger blieb unverändert.|未找到已安装 Ubuntu 的启动标记；持久磁盘未被修改。|インストール済み Ubuntu の起動マーカーが見つかりません。永続ディスクは変更していません。
            Не удалось запустить переход к установленной Ubuntu:|Could not start the installed Ubuntu handoff: |No se pudo iniciar el traspaso a Ubuntu instalado: |Startübergabe zur installierten Ubuntu konnte nicht gestartet werden: |无法启动已安装 Ubuntu 的启动交接：|インストール済み Ubuntu のハンドオフを開始できませんでした:
            Полный журнал последовательного порта VM записывается в|Writing complete VM serial log to |Escribiendo el registro serie completo de la VM en |Vollständiges serielles VM-Protokoll wird geschrieben nach|正在写入完整的 VM 串口日志：|VM のシリアルログ全体を書き込み中:
            Консоль закрыта:|Console closed: |Consola cerrada: |Konsole geschlossen: |控制台已关闭：|コンソールを閉じました:
            Копирование установочного носителя Windows во внутреннее хранилище приложения…|Copying Windows setup medium into app-private storage…|Copiando el medio de instalación de Windows al almacenamiento privado…|Windows-Setup-Medium wird in den app-privaten Speicher kopiert…|正在将 Windows 安装介质复制到应用私有存储…|Windows セットアップメディアをアプリ専用ストレージにコピーしています…
            Диагностический отчёт сохранён.|Diagnostic report saved.|Informe de diagnóstico guardado.|Diagnosebericht gespeichert.|诊断报告已保存。|診断レポートを保存しました。
            Не удалось сохранить отчёт.|Could not save the report.|No se pudo guardar el informe.|Bericht konnte nicht gespeichert werden.|无法保存报告。|レポートを保存できませんでした。
            Завершите сеанс Ubuntu перед запуском Windows.|Shut down Ubuntu before starting Windows.|Apaga Ubuntu antes de iniciar Windows.|Fahre Ubuntu herunter, bevor du Windows startest.|启动 Windows 前请先关闭 Ubuntu。|Windows を起動する前に Ubuntu を終了してください。
            Завершите сеанс Windows перед запуском Ubuntu.|Shut down Windows before starting Ubuntu.|Apaga Windows antes de iniciar Ubuntu.|Fahre Windows herunter, bevor du Ubuntu startest.|启动 Ubuntu 前请先关闭 Windows。|Ubuntu を起動する前に Windows を終了してください。
            · диск U-AVF|· U-AVF disk|· disco U-AVF|· U-AVF-Datenträger|· U-AVF 磁盘|· U-AVF ディスク
            Ram|RAM|RAM|RAM|内存|メモリ
            EFI / платформа U-AVF|EFI / U-AVF platform|EFI / plataforma U-AVF|EFI / U-AVF-Plattform|EFI / U-AVF 平台|EFI / U-AVF プラットフォーム
            Разрешение изменится после перезапуска Ubuntu.|The resolution changes after Ubuntu restarts.|La resolución cambia al reiniciar Ubuntu.|Die Auflösung ändert sich nach einem Neustart von Ubuntu.|分辨率将在重启 Ubuntu 后变更。|解像度は Ubuntu の再起動後に変更されます。
            Перезапустить сейчас|Restart now|Reiniciar ahora|Jetzt neu starten|立即重启|今すぐ再起動
            Применить при следующем запуске|Applies at the next start|Se aplica en el próximo inicio|Gilt beim nächsten Start|下次启动时应用|次回起動時に適用
            Текущий сеанс работает в|Current session is running at|La sesión actual se ejecuta en|Aktuelle Sitzung läuft mit|当前会话运行于|現在のセッション:
            Выбранное разрешение недоступно в этом режиме вывода.|The selected resolution is not available in this display mode.|La resolución seleccionada no está disponible en este modo de pantalla.|Die gewählte Auflösung ist in diesem Anzeigemodus nicht verfügbar.|所选分辨率在此显示模式下不可用。|選択した解像度はこの表示モードでは利用できません。
            """;
        String data4="""
            Проверьте ввод после смены разрешения.|Pointer input is mapped to the new size automatically.|La entrada del puntero se ajusta al nuevo tamaño.|Die Zeigereingabe wird automatisch an die neue Größe angepasst.|指针输入会自动适配新尺寸。|ポインター入力は新しいサイズに自動で合わせられます。
            Журнал всегда ведётся на английском языке.|Journal entries are always written in English.|Las entradas del registro siempre están en inglés.|Protokolleinträge werden immer auf Englisch geschrieben.|日志条目始终以英文记录。|ログは常に英語で記録されます。
            Сведения о приложении|About this app|Acerca de esta aplicación|Über diese App|关于此应用|このアプリについて
            Версия|Version|Versión|Version|版本|バージョン
            Сборка Android|Android build|Compilación de Android|Android-Build|Android 版本|Android ビルド
            Проверенная сборка|Verified build|Compilación verificada|Geprüfter Build|已验证构建|検証済みビルド
            Открыть журнал событий|Open event journal|Abrir registro de eventos|Ereignisprotokoll öffnen|打开事件日志|イベントログを開く
            Быстрые действия|Quick actions|Acciones rápidas|Schnellaktionen|快捷操作|クイック操作
            Размер экрана|Screen size|Tamaño de pantalla|Bildschirmgröße|屏幕尺寸|画面サイズ
            Управление указателем включено|Pointer capture is on|Captura del puntero activada|Zeigererfassung aktiv|指针捕获已开启|ポインターキャプチャは有効です
            Управление указателем выключено|Pointer capture is off|Captura del puntero desactivada|Zeigererfassung aus|指针捕获已关闭|ポインターキャプチャは無効です
            Не запущено|Not running|Sin ejecutar|Nicht gestartet|未运行|未実行
            Остановить|Stop|Detener|Stoppen|停止|停止
            Без изменений|No changes|Sin cambios|Keine Änderungen|无更改|変更なし
            По умолчанию|Default|Predeterminado|Standard|默认|既定
            Недоступно в этом режиме|Unavailable in this mode|No disponible en este modo|In diesem Modus nicht verfügbar|此模式下不可用|このモードでは利用できません
            Размер гостевого рабочего стола. Изменение вступает в силу после перезапуска Ubuntu.|Guest desktop size. Changes take effect after Ubuntu restarts.|Tamaño del escritorio del invitado. El cambio se aplica al reiniciar Ubuntu.|Größe des Gast-Desktops. Änderungen wirken nach einem Neustart von Ubuntu.|客户机桌面尺寸。更改将在重启 Ubuntu 后生效。|ゲストデスクトップのサイズ。変更は Ubuntu の再起動後に反映されます。
            Разрешение %s уже применено.|%s is already in use.|%s ya está en uso.|%s wird bereits verwendet.|已在使用 %s。|%s はすでに適用されています。
            Разрешение изменится после перезапуска Ubuntu. Сейчас %s.|The resolution changes after Ubuntu restarts. Current size: %s.|La resolución cambia al reiniciar Ubuntu. Tamaño actual: %s.|Die Auflösung ändert sich nach einem Neustart von Ubuntu. Aktuell: %s.|分辨率将在重启 Ubuntu 后变更。当前为 %s。|解像度は Ubuntu の再起動後に変更されます。現在: %s。
            Для Windows используется H.264.|Windows uses H.264.|Windows usa H.264.|Windows verwendet H.264.|Windows 使用 H.264。|Windows は H.264 を使用します。
            Mavk недоступен в этой сборке.|Mavk is not available in this build.|Mavk no está disponible en esta compilación.|Mavk ist in diesem Build nicht verfügbar.|此构建中 Mavk 不可用。|このビルドでは Mavk を利用できません。
            Графический вывод: %s. Применяется при следующем запуске Ubuntu.|Display pipeline: %s. Applies the next time Ubuntu starts.|Canal de pantalla: %s. Se aplica al próximo inicio de Ubuntu.|Anzeigepipeline: %s. Gilt beim nächsten Start von Ubuntu.|显示管线：%s。将在下次启动 Ubuntu 时应用。|表示パイプライン: %s。次回 Ubuntu 起動時に適用されます。
            Графический вывод, %s|Display pipeline, %s|Canal de pantalla, %s|Anzeigepipeline, %s|显示管线，%s|表示パイプライン、%s
            Режим ввода, %s|Input mode, %s|Modo de entrada, %s|Eingabemodus, %s|输入模式，%s|入力モード、%s
            All components|All components|Todos los componentes|Alle Komponenten|全部组件|すべてのコンポーネント
            All levels|All levels|Todos los niveles|Alle Stufen|全部级别|すべてのレベル
            %1$s ОЗУ · %2$s|%1$s RAM · %2$s|%1$s RAM · %2$s|%1$s RAM · %2$s|%1$s 内存 · %2$s|%1$s メモリ · %2$s
            %s · диск U-AVF|%s · U-AVF disk|%s · disco U-AVF|%s · U-AVF-Datenträger|%s · U-AVF 磁盘|%s · U-AVF ディスク
            Нет запущенной виртуальной машины, к которой можно вернуться.|There is no running virtual machine to return to.|No hay ninguna máquina virtual en ejecución a la que volver.|Es läuft keine virtuelle Maschine, zu der zurückgekehrt werden kann.|没有可返回的运行中虚拟机。|戻り先の実行中仮想マシンがありません。
            Не удалось проверить переход установленной Ubuntu: |Could not verify the installed Ubuntu handoff: |No se pudo verificar el traspaso de Ubuntu instalado: |Die Startübergabe der installierten Ubuntu konnte nicht geprüft werden: |无法校验已安装 Ubuntu 的启动交接：|インストール済み Ubuntu のハンドオフを検証できませんでした:
            Не удалось проверить хранилище: |Storage check failed: |Falló la comprobación del almacenamiento: |Speicherprüfung fehlgeschlagen: |存储检查失败：|ストレージの確認に失敗しました:
            Не удалось расширить хранилище: |Storage expansion not completed: |No se completó la ampliación del almacenamiento: |Speichererweiterung nicht abgeschlossen: |存储扩展未完成：|ストレージの拡張が完了しませんでした:
            Не удалось запустить установку Ubuntu: |Ubuntu installation could not start: |No se pudo iniciar la instalación de Ubuntu: |Ubuntu-Installation konnte nicht starten: |无法启动 Ubuntu 安装：|Ubuntu のインストールを開始できませんでした:
            Допускается одна строка ограниченной длины.|One bounded line required.|Se requiere una sola línea acotada.|Eine einzelne Zeile ist erlaubt.|仅允许一行有限长度的内容。|1 行のみ入力できます。
            Консоль гостя недоступна: |Guest console unavailable: |La consola del invitado no está disponible: |Gastkonsole nicht verfügbar: |客户机控制台不可用：|ゲストコンソールを利用できません:
            Воркспейс не удалён: |Workspace was not deleted: |No se eliminó el espacio: |Arbeitsbereich wurde nicht gelöscht: |工作区未删除：|ワークスペースは削除されませんでした:
            Не удалось открыть выбор файла: |Could not open image picker: |No se pudo abrir el selector de archivos: |Dateiauswahl konnte nicht geöffnet werden: |无法打开文件选择器：|ファイル選択を開けませんでした:
            Импорт образа Ubuntu: |Importing Ubuntu image: |Importando la imagen de Ubuntu: |Ubuntu-Abbild wird importiert: |正在导入 Ubuntu 映像：|Ubuntu イメージをインポート中:
            Образ Ubuntu не принят: |Ubuntu image was not accepted: |La imagen de Ubuntu no fue aceptada: |Ubuntu-Abbild wurde nicht akzeptiert: |Ubuntu 映像未被接受：|Ubuntu イメージは受け入れられませんでした:
            Скачивание официального образа Ubuntu: |Downloading official Ubuntu image: |Descargando la imagen oficial de Ubuntu: |Offizielles Ubuntu-Abbild wird heruntergeladen: |正在下载官方 Ubuntu 映像：|公式 Ubuntu イメージをダウンロード中:
            Не удалось скачать Ubuntu: |Ubuntu download failed: |Falló la descarga de Ubuntu: |Ubuntu-Download fehlgeschlagen: |Ubuntu 下载失败：|Ubuntu のダウンロードに失敗しました:
            Не удалось сохранить отчёт о готовности: |Could not save readiness report: |No se pudo guardar el informe de preparación: |Bereitschaftsbericht konnte nicht gespeichert werden: |无法保存就绪报告：|準備状況レポートを保存できませんでした:
            Запрос питания гостя: |Guest power request: |Solicitud de energía del invitado: |Energieanforderung des Gasts: |客户机电源请求：|ゲストの電源要求:
            Не удалось остановить VM: |Could not stop VM: |No se pudo detener la VM: |VM konnte nicht gestoppt werden: |无法停止 VM：|VM を停止できませんでした:
            Проверка диска пропущена для |Disk verification skipped for |Verificación de disco omitida para |Datenträgerprüfung übersprungen für |已跳过磁盘校验：|ディスク検証をスキップしました:
            Не удалось запустить мост кадров Ubuntu: |Ubuntu frame bridge failed: |Falló el puente de fotogramas de Ubuntu: |Ubuntu-Frame-Bridge fehlgeschlagen: |Ubuntu 帧桥接失败：|Ubuntu フレームブリッジに失敗しました:
            Ошибка VM: |VM error: |Error de la VM: |VM-Fehler: |VM 错误：|VM エラー:
            Не удалось запустить переход к установленной Ubuntu: |Could not start the installed Ubuntu handoff: |No se pudo iniciar el traspaso a Ubuntu instalado: |Startübergabe zur installierten Ubuntu konnte nicht gestartet werden: |无法启动已安装 Ubuntu 的启动交接：|インストール済み Ubuntu のハンドオフを開始できませんでした:
            Полный журнал последовательного порта VM записывается в |Writing complete VM serial log to |Escribiendo el registro serie completo de la VM en |Vollständiges serielles VM-Protokoll wird geschrieben nach |正在写入完整的 VM 串口日志：|VM のシリアルログ全体を書き込み中:
            Консоль закрыта: |Console closed: |Consola cerrada: |Konsole geschlossen: |控制台已关闭：|コンソールを閉じました:
            Воркспейс уже запускается, работает или останавливается.|The workspace is already starting, running or stopping.|El espacio ya se está iniciando, ejecutando o deteniendo.|Der Arbeitsbereich startet, läuft oder stoppt bereits.|工作区正在启动、运行或停止中。|ワークスペースは起動中、実行中、または停止中です。
            Ubuntu Live запущена. Сеанс временный; для выключения используйте «Завершить».|Ubuntu Live launched. This session is temporary; use Shut down to power off.|Ubuntu Live iniciado. La sesión es temporal; usa Apagar para apagarla.|Ubuntu Live gestartet. Diese Sitzung ist temporär; zum Ausschalten „Herunterfahren“ verwenden.|Ubuntu Live 已启动。此会话为临时会话；请使用「关机」关闭电源。|Ubuntu Live を起動しました。このセッションは一時的です。電源を切るには「シャットダウン」を使用してください。
            Не удалось повторить мост кадров: |Frame bridge retry failed: |Falló el reintento del puente de fotogramas: |Wiederholung der Frame-Bridge fehlgeschlagen: |帧桥接重试失败：|フレームブリッジの再試行に失敗しました:
            Загрузка|Loading|Cargando|Wird geladen|正在加载|読み込み中
            Continue to storage|Continue to storage|Continuar al almacenamiento|Weiter zum Speicher|继续前往存储|ストレージへ進む
            Reading the last setup report…|Reading the last setup report…|Leyendo el último informe de instalación…|Der letzte Installationsbericht wird gelesen…|正在读取上次安装报告…|前回のセットアップレポートを読み込み中…
            Standard AVF|Standard AVF|AVF estándar|Standard-AVF|标准 AVF|標準 AVF
            Dark|Dark|Oscuro|Dunkel|深色|ダーク
            Light|Light|Claro|Hell|浅色|ライト
            Ubuntu Root|Ubuntu Root|Ubuntu Root|Ubuntu Root|Ubuntu Root|Ubuntu Root
            Match-host vCPU|Match-host vCPU|vCPU del host|vCPU wie Host|与主机同核|ホストと同じ vCPU
            1 vCPU|1 vCPU|1 vCPU|1 vCPU|1 vCPU|1 vCPU
            Ubuntu 24.04 LTS|Ubuntu 24.04 LTS|Ubuntu 24.04 LTS|Ubuntu 24.04 LTS|Ubuntu 24.04 LTS|Ubuntu 24.04 LTS
            Для установки нужен официальный образ Ubuntu 24.04.5 Desktop ARM64. Без него U-AVF не может создать систему, поэтому установка не начинается.|Installing needs the official Ubuntu 24.04.5 Desktop ARM64 image. Without it U-AVF cannot create the system, so installation does not start.|La instalación necesita la imagen oficial Ubuntu 24.04.5 Desktop ARM64. Sin ella U-AVF no puede crear el sistema, así que la instalación no comienza.|Die Installation benötigt das offizielle Ubuntu-24.04.5-Desktop-ARM64-Abbild. Ohne es kann U-AVF das System nicht erstellen, daher startet die Installation nicht.|安装需要官方 Ubuntu 24.04.5 Desktop ARM64 映像。没有它 U-AVF 无法创建系统，因此不会开始安装。|インストールには公式 Ubuntu 24.04.5 Desktop ARM64 イメージが必要です。これがないと U-AVF はシステムを作成できないため、インストールは開始されません。
            Для установки нужен официальный образ Ubuntu 24.04.5 Desktop ARM64. Выберите ISO в свойствах воркспейса — без него установка невозможна.|Installing needs the official Ubuntu 24.04.5 Desktop ARM64 image. Choose the ISO in the workspace settings — installation is impossible without it.|La instalación necesita la imagen oficial Ubuntu 24.04.5 Desktop ARM64. Elige el ISO en los ajustes del espacio — sin él la instalación es imposible.|Die Installation benötigt das offizielle Ubuntu-24.04.5-Desktop-ARM64-Abbild. Wähle das ISO in den Arbeitsbereich-Einstellungen — ohne es ist keine Installation möglich.|安装需要官方 Ubuntu 24.04.5 Desktop ARM64 映像。请在工作区设置中选择 ISO，否则无法安装。|インストールには公式 Ubuntu 24.04.5 Desktop ARM64 イメージが必要です。ワークスペース設定で ISO を選択してください。これがないとインストールできません。
            Нужен образ Ubuntu|Ubuntu image required|Se necesita la imagen de Ubuntu|Ubuntu-Abbild erforderlich|需要 Ubuntu 映像|Ubuntu イメージが必要です
            Выбрать образ|Choose image|Elegir imagen|Abbild wählen|选择映像|イメージを選択
            Технические подробности|Technical details|Detalles técnicos|Technische Details|技术详情|技術的な詳細
            Android не выдал приложение разрешения виртуализации. Откройте «Проверки и тесты» и повторите проверку устройства.|Android did not grant the virtualisation permissions. Open Checks and tests and repeat the device check.|Android no concedió los permisos de virtualización. Abre «Comprobaciones y pruebas» y repite la verificación.|Android hat die Virtualisierungsrechte nicht erteilt. Öffne „Prüfungen und Tests“ und wiederhole die Geräteprüfung.|Android 未授予虚拟化权限。请打开「检查与测试」并重新检查设备。|Android が仮想化の権限を許可していません。「チェックとテスト」を開いてデバイス確認をやり直してください。
            Диск воркспейса не прошёл проверку раскладки. Он не изменялся. Откройте «Хранилище», чтобы проверить состояние.|The workspace disk failed its layout check. It was not modified. Open Storage to inspect its state.|El disco del espacio no superó la comprobación de diseño. No se modificó. Abre «Almacenamiento» para revisar su estado.|Der Arbeitsbereich-Datenträger hat die Layoutprüfung nicht bestanden. Er wurde nicht verändert. Öffne „Speicher“, um den Zustand zu prüfen.|工作区磁盘未通过布局检查。它未被修改。请打开「存储」查看状态。|ワークスペースのディスクがレイアウト検査に合格しませんでした。変更は行われていません。「ストレージ」を開いて状態を確認してください。
            Не хватает памяти для запуска. Уменьшите объём ОЗУ воркспейса или закройте другие приложения.|Not enough memory to start. Reduce the workspace RAM or close other apps.|No hay memoria suficiente para iniciar. Reduce la RAM del espacio o cierra otras aplicaciones.|Nicht genügend Speicher zum Starten. Verringere den Arbeitsbereich-RAM oder schließe andere Apps.|内存不足，无法启动。请减少工作区内存或关闭其他应用。|起動に必要なメモリが足りません。ワークスペースのメモリを減らすか、他のアプリを閉じてください。
            Гость не подтвердил запрос питания. Принудительная остановка не выполнялась — можно повторить запрос.|The guest did not confirm the power request. No forced stop was performed — you can retry the request.|El invitado no confirmó la solicitud de energía. No se forzó la detención — puedes reintentar la solicitud.|Der Gast hat die Energieanforderung nicht bestätigt. Es wurde kein hartes Stoppen ausgeführt — die Anforderung kann wiederholt werden.|客户机未确认电源请求。未执行强制停止——可以重试该请求。|ゲストが電源要求を確認しませんでした。強制停止は行っていません。要求を再試行できます。
            Запуск прервался до появления рабочего стола. Подробности ниже; их можно приложить к отчёту.|The launch stopped before the desktop appeared. Details are below and can be attached to a report.|El inicio se detuvo antes de que apareciera el escritorio. Los detalles están abajo y pueden adjuntarse a un informe.|Der Start brach vor dem Desktop ab. Details stehen unten und können einem Bericht beigelegt werden.|启动在桌面出现前中断。详情见下，可附在报告中。|デスクトップが表示される前に起動が中断しました。詳細は下にあり、レポートに添付できます。
            """;
        String data=String.join("",data0,data1,data2,data3,data4);
        for(String line:data.split("\n")) {
            String[] values=line.split("\\|",-1);
            if(values.length!=6)continue;
            STRINGS.putIfAbsent(values[0],values);
            STRINGS.putIfAbsent(values[1],values);
        }
    }
}
