// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import static com.example.winavf.IoUtil.formatBytes;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import com.example.winavf.navigation.ProductScreen;
import com.example.winavf.runtime.VmLifecycle;
import java.io.File;
import java.util.function.IntFunction;

/** Constructs workspace and home pages from the current guest and storage state. */
final class WorkspaceScreens {
   interface Host {
      boolean vmRunning();
      boolean windowsSelected();
      void selectWindows(boolean windows);
      void returnToVm();
      void launchSelected();
      void startUbuntu();
      void resumeUbuntu();
      WorkspaceConfig config();
      String displayLabel();
      String draftTitle();
      void setDraftTitle(String title);
      void navigate(ProductScreen screen);
      void refreshWorkspace(ProductScreen screen);
      boolean currentWindowsPage(View view);
      void back();
      void settings(String tab);
      void wizardSettings();
      void pickWindowsIso();
      void pickLiveIso();
      void openUbuntuStorage();
      void refreshAppSettings();
      String releaseVersion();
      void showLegal(String name);
      void show(String message);
   }

   private final Activity activity;
   private final SharedPreferences preferences;
   private final UbuntuWorkspaceStorage storage;
   private final UbuntuWorkspaceActions ubuntu;
   private final WindowsSetupFlow windows;
   private final Host host;

   WorkspaceScreens(Activity activity, UbuntuWorkspaceStorage storage, UbuntuWorkspaceActions ubuntu,
                    WindowsSetupFlow windows, Host host) {
      this.activity = activity;
      this.preferences = activity.getPreferences(0);
      this.storage = storage;
      this.ubuntu = ubuntu;
      this.windows = windows;
      this.host = host;
   }

   FigmaCanvas create(ProductScreen screen) {
      return switch (screen) {
         case WINDOWS_HOME -> createWindowsWorkspace();
         case LINUX_HOME -> createUbuntuWorkspace();
         case CREATE_STORAGE, CREATE_REVIEW -> createStorageReview(screen == ProductScreen.CREATE_REVIEW);
         case CREATE_OS -> createOsPicker();
         default -> createUbuntuMediaScreen(screen);
      };
   }

   private boolean isoPresent() { return UbuntuIsoImport.present(activity.getExternalFilesDir(null)); }

   private void watchWindowsProgress(View view, WindowsSetupFlow.Progress seen) {
      activity.getWindow().getDecorView().postDelayed(() -> {
         if (!host.currentWindowsPage(view)) return;
         if (windows.progress() != seen) host.refreshWorkspace(ProductScreen.WINDOWS_HOME);
         else watchWindowsProgress(view, seen);
      }, 700);
   }

   IntFunction<FigmaMainTabs> mainPageFactory() {
      File disk = storage.disk();
      boolean exists = disk.isFile();
      boolean ready = exists && preferences.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false);
      boolean ubuntuRunning = ubuntuWorkspaceRunning();
      boolean windowsRunning = host.vmRunning() && host.windowsSelected();
      String diskSize = exists ? formatBytes(disk.length()) : "—";
      String ubuntuStatus = ubuntuRunning ? "Работает"
            : ready ? "Выключена" : exists ? "Настройка не завершена" : "Не установлена";
      Runnable createUbuntu = () -> host.navigate(exists ? ProductScreen.INSTALL_STATUS : ProductScreen.CREATE_IMAGE);
      Runnable openUbuntu = () -> openWorkspaceCard(false);
      Runnable openWindows = () -> openWorkspaceCard(true);
      Runnable launch = () -> {
         if (host.vmRunning()) openUbuntu.run();
         else if (exists) openUbuntuWorkspace(false, true, ready);
         else {
            host.selectWindows(false);
            createUbuntu.run();
         }
      };
      String launchLabel = ubuntuRunning ? "Вернуться" : ready ? "Запустить" : exists ? "Продолжить" : "Создать";
      String storageLabel = storageLabel(exists, diskSize);
      FigmaMainTabs.Workspace windowsCard = windowsCard(windowsRunning, openWindows);
      return tab -> new FigmaMainTabs(activity, tab, !ready && !ubuntuRunning, exists && !ready,
            new FigmaMainTabs.Workspace(workspaceTitle(false), "Ubuntu 24.04 · " + UiLanguage.text(ubuntuStatus),
                  ubuntuStatus, UiLanguage.format("%d ГБ", host.config().ramGiB), diskSize, host.displayLabel(),
                  lastDesktopSession("last_desktop_ubuntu"), false, openUbuntu),
            windowsCard, storageLabel, launchLabel, launch, () -> host.navigate(ProductScreen.CREATE_OS), createUbuntu,
            openWindows, this::openUbuntuResources, host::openUbuntuStorage, this::showLiveSession,
            () -> host.navigate(ProductScreen.DIAGNOSTICS), !preferences.getBoolean("skip_disk_validation", false),
            this::toggleDiskValidation, () -> ProductDialog.notice(activity, "U-AVF", host.releaseVersion()),
            () -> host.showLegal("PRIVACY.md"), this::showLicenses, index -> {
               UiLanguage.select(activity, index);
               host.refreshAppSettings();
            });
   }

   private void openWorkspaceCard(boolean windows) {
      if (windows ? host.vmRunning() && host.windowsSelected() : ubuntuWorkspaceRunning()) {
         host.returnToVm();
         return;
      }
      if (host.vmRunning()) {
         ProductDialog.notice(activity, windows ? "Windows ARM64" : "Ubuntu",
               windows ? "Завершите сеанс Ubuntu перед запуском Windows." : "Сначала завершите текущий сеанс VM.");
         return;
      }
      host.selectWindows(windows);
      host.navigate(windows ? ProductScreen.WINDOWS_HOME : ProductScreen.LINUX_HOME);
   }

   private void openUbuntuResources() {
      if (host.vmRunning() && !ubuntuWorkspaceRunning()) {
         ProductDialog.notice(activity, "Ресурсы Ubuntu", "Сначала завершите текущий сеанс VM.");
         return;
      }
      if (!host.vmRunning()) host.selectWindows(false);
      host.settings("Performance");
   }

   private FigmaMainTabs.Workspace windowsCard(boolean running, Runnable open) {
      if (!running && !windows.diskReady()) return null;
      String status = running ? "Работает" : "Диск Windows сохранён";
      return new FigmaMainTabs.Workspace(workspaceTitle(true), "Windows ARM64 · " + UiLanguage.text(status),
            status, "—", "—", "—", lastDesktopSession("last_display_windows"), true, open);
   }

   private String storageLabel(boolean diskExists, String diskSize) {
      boolean hasIso = isoPresent();
      if (!diskExists) return hasIso ? "1 проверенный образ" : "Хранилище пусто";
      return (hasIso ? UiLanguage.text("1 образ") + " · " : "") + diskSize;
   }

   private String lastDesktopSession(String preference) {
      long time = preferences.getLong(preference, 0);
      if (time == 0) return "";
      return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT, UiLanguage.locale()).format(new java.util.Date(time));
   }

   private void toggleDiskValidation() {
      preferences.edit().putBoolean("skip_disk_validation", !preferences.getBoolean("skip_disk_validation", false)).apply();
   }

   private void showLicenses() {
      String[] labels = {"U-AVF", "Область действия", "Apache 2.0", "Сторонние компоненты"};
      String[] documents = {"LICENSE-UAVF.txt", "LICENSE_SCOPE.md", "LICENSE-APACHE-2.0.txt", "THIRD_PARTY_NOTICES.txt"};
      ProductDialog.choose(activity, "Лицензии", host.releaseVersion(), labels, -1, index -> host.showLegal(documents[index]));
   }

   private FigmaCanvas createUbuntuWorkspace() {
      File disk = storage.disk();
      boolean exists = disk.isFile();
      var prefs = preferences;
      boolean ready = exists && prefs.getBoolean(UbuntuWorkspaceMedia.INSTALLED_READY, false);
      boolean running = this.ubuntuWorkspaceRunning();
      String state = running ? "Работает" : ready ? "Выключена" : "Требуется настройка";
      String resources = UiLanguage.format("%d ГБ", host.config().ramGiB)
            + " · " + (exists ? formatBytes(disk.length()) : "—");
      String action = running ? "Вернуться" : ready ? "Запустить" : exists ? "Продолжить" : "Создать";
      return new FigmaWorkspaceHome(activity, false, this.workspaceTitle(false), state, resources,
            host.displayLabel(), action, () -> this.openUbuntuWorkspace(running, exists, ready),
            host::back, () -> this.showWorkspaceActions(false),
            tab -> host.settings(FigmaWorkspaceSettings.TABS[tab]), null);
   }

   private boolean ubuntuWorkspaceRunning() {
      return host.vmRunning() && !host.windowsSelected()
            && !preferences.getBoolean("linux_current_boot_temporary", false);
   }

   private void openUbuntuWorkspace(boolean running, boolean exists, boolean ready) {
      if (running) {
         host.returnToVm();
         return;
      }
      if (host.vmRunning()) {
         host.show("Сначала завершите текущий сеанс VM.");
         return;
      }
      host.selectWindows(false);
      if (ready) new Thread(() -> host.startUbuntu(), "U-AVF-persistent-ubuntu").start();
      else if (exists) new Thread(host::resumeUbuntu, "U-AVF-resume-installed-ubuntu").start();
      else host.navigate(this.isoPresent() ? ProductScreen.CREATE_STORAGE : ProductScreen.CREATE_OS);
   }

   private FigmaCanvas createWindowsWorkspace() {
      boolean vmRunning = host.vmRunning();
      boolean running = vmRunning && host.windowsSelected();
      var workspace = windows.workspace();
      boolean chooseIso = workspace.canChooseMedia(vmRunning) && !workspace.isoSelected();
      var details = new FigmaWorkspaceHome.WindowsDetails(workspace.diskLabel(),
            workspace.isoSelected() ? "Образ доступен" : "Не выбран",
            workspace.canChooseMedia(vmRunning) ? () -> this.chooseWindowsWorkspaceMedia(workspace.setupAvailable()) : null);
      var canvas = new FigmaWorkspaceHome(activity, true, this.workspaceTitle(true), running ? "Работает" : workspace.status(),
            "Фиксированный профиль", "—", running ? "Вернуться" : workspace.action(chooseIso),
            () -> this.openWindowsWorkspace(running, chooseIso, workspace),
            host::back, () -> this.showWorkspaceActions(true),
            tab -> host.settings(FigmaWorkspaceSettings.TABS[tab]), details);
      if (workspace.setupAvailable() && workspace.progress().phase() != null) {
         canvas.widget(new FigmaProgress(activity), 112, 1370, 1312, 12);
         watchWindowsProgress(canvas, workspace.progress());
      }
      return canvas;
   }

   private void chooseWindowsWorkspaceMedia(boolean setupAvailable) {
      if (host.vmRunning()) return;
      if (setupAvailable) windows.open();
      else host.pickWindowsIso();
   }

   private void openWindowsWorkspace(boolean running, boolean chooseIso, WindowsSetupFlow.Workspace workspace) {
      if (running) {
         host.returnToVm();
         return;
      }
      if (host.vmRunning()) {
         host.show("Сначала завершите текущий сеанс VM.");
         return;
      }
      host.selectWindows(true);
      if (chooseIso) host.pickWindowsIso();
      else if (workspace.setupAvailable()) windows.open();
      else if (workspace.isoAvailable()) host.pickWindowsIso();
      else host.launchSelected();
   }

   private FigmaCanvas createStorageReview(boolean review) {
      File disk=storage.disk();WorkspaceConfig config=host.config();
      boolean ready=disk.isFile() && preferences.getBoolean("ubuntu_installed_runtime_ready",false);
      return new FigmaStorageReview(activity,review,disk.isFile()?formatBytes(disk.length()):formatBytes(PersistentUbuntuDisk.expectedDiskBytes(UbuntuIsoImport.BYTES,storage.plannedRootBytes())),config.oneCpu?"1 vCPU":"Match-host vCPU",config.ramGiB,this.isoPresent(),host.displayLabel(),
            disk.isFile()?ready?"Open existing workspace":"Continue existing setup":"Create & install",()-> {
               if(!review) {host.navigate(ProductScreen.CREATE_REVIEW);return;}
               if(disk.isFile()) {
                  if(ready)host.navigate(ProductScreen.LINUX_HOME);
                  else if(host.vmRunning())host.returnToVm();
                  else new Thread(host::resumeUbuntu,"U-AVF-create-resume").start();
               } else ubuntu.confirmInstall(false);
            },host::back,()-> {
               host.wizardSettings();
            },ubuntu.rootSizeLabel(),disk.isFile()?null:ubuntu::chooseRootSize);
   }

   private FigmaCanvas createOsPicker() {
      boolean exists=storage.disk().isFile();
      return new FigmaCreateView(activity,exists,host::back,()->{

         host.navigate(exists?ProductScreen.LINUX_HOME:ProductScreen.CREATE_IMAGE);
      },()->{
         if(host.vmRunning() && !host.windowsSelected()) {
            host.show("Сначала завершите текущий сеанс VM.");
            return;
         }
         host.selectWindows(true);
         host.navigate(ProductScreen.WINDOWS_HOME);
      });
   }

   private FigmaCanvas createUbuntuMediaScreen(ProductScreen screen) {
      boolean hasDisk=storage.disk().isFile();
      String name=hasDisk?this.workspaceTitle(false):
            host.draftTitle().isEmpty()?UiLanguage.text("Ubuntu · Новый"):host.draftTitle();
      String resources=UiLanguage.format("%d ГБ",host.config().ramGiB)+" · "+
            (hasDisk?formatBytes(storage.disk().length()):ubuntu.rootSizeLabel());
      return new FigmaCreateView(activity,name,resources,this.isoPresent(),hasDisk,host::back,
            ()->ProductDialog.rename(activity,name,next->{
               if(hasDisk)preferences.edit().putString("ubuntu_workspace_title",next).apply();
               else host.setDraftTitle(next);
               host.refreshWorkspace(screen);
            }),ubuntu::chooseIso,()->host.navigate(ProductScreen.CREATE_STORAGE),()->{
               if(hasDisk) {host.navigate(ProductScreen.LINUX_HOME);return;}
               if(host.vmRunning()) {host.show("Сначала завершите текущий сеанс VM.");return;}
               host.selectWindows(false);
               ubuntu.confirmInstall(false);
            });
   }

   private String workspaceTitle(boolean windows) {
      return preferences.getString(windows?"windows_workspace_title":"ubuntu_workspace_title",windows?"Windows":"Ubuntu");
   }

   void showLiveSession() {
      if(host.vmRunning() && !host.windowsSelected()
            && preferences.getBoolean("linux_current_boot_temporary",false)) {
         host.returnToVm();
         return;
      }
      if(host.vmRunning() || VmLifecycle.compatibilityProbeActive()) {
         ProductDialog.notice(activity,"Временная Live-сессия","Сначала завершите текущий сеанс VM.");
         return;
      }
      boolean ready=this.isoPresent();
      String[] media={"Выбрать ISO…","Сайт Ubuntu ↗"};
      String[] options=ready?new String[]{"Запустить",media[0],media[1]}:media;
      ProductDialog.choose(activity,"Временная Live-сессия","Запуск Ubuntu с ISO без установки",options,-1,index->{
         if(ready && index==0) {
            host.selectWindows(false);
            host.launchSelected();
         } else if(index==(ready?1:0)) {
            host.pickLiveIso();
         } else {
            ubuntu.openOfficialDownload();
         }
      });
   }

   private void showWorkspaceActions(boolean windows) {
      boolean canModify=!windows && storage.disk().isFile() && !host.vmRunning();
      String[] actions=canModify?new String[]{"Переименовать…","Параметры","Переустановить…","Удалить…"}:
            new String[]{"Переименовать…","Параметры","Диагностика"};
      if(windows && WindowsInstallIso.available() && !host.vmRunning())
         actions=new String[]{"Переименовать…","Параметры","Диагностика","Запустить прежний образ"};
      ProductDialog.menu(activity,"Воркспейс",actions,index->{
         if(index==0)ProductDialog.rename(activity,this.workspaceTitle(windows),name->{
            preferences.edit().putString(windows?"windows_workspace_title":"ubuntu_workspace_title",name).apply();
            host.refreshWorkspace(windows?ProductScreen.WINDOWS_HOME:ProductScreen.LINUX_HOME);
         });
         else if(index==1)host.settings("Performance");
         else if(windows && index==3)host.launchSelected();
         else if(!canModify)host.navigate(ProductScreen.DIAGNOSTICS);
         else if(index==2)ubuntu.confirmReinstall();
         else ubuntu.confirmDelete();
      });
   }

}
