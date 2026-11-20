@echo off
setlocal EnableExtensions
rem Isolated test deployment ONLY, after DISM Apply-Image has exited successfully.
rem No format, partition changes, firmware replacement or OOBE flag mutation.
if /i not "%SystemDrive%"=="X:" exit /b 10
if /i "%~1"=="--idd-fresh" goto idd_fresh_admission
rem Capture DISM's errorlevel as the FIRST idle-prompt command, before staging.
if not exist W:\uavf-apply-exit.txt exit /b 18
find "APPLY_EXIT=0" W:\uavf-apply-exit.txt >nul
if errorlevel 1 exit /b 19
goto apply_admitted
:idd_fresh_admission
rem Separate fresh official image, not a forged DISM Apply-Image exit code.
rem This opt-in is valid ONLY for the new labelled disposable IddCx root.
vol W: | find "UAVF_IDD_TEST" >nul
if errorlevel 1 exit /b 45
if not exist S:\UAVFIDD\fresh-root-provenance.txt exit /b 46
find "NATIVE_WIM_APPLY_EXIT=0; STRICT_ACLS=1; IMAGE=3; ARM64=1" S:\UAVFIDD\fresh-root-provenance.txt >nul
if errorlevel 1 exit /b 47
:apply_admitted
rem Exact shipped fsutil uses DisableDeleteNotification (not the query label
rem NtfsDisableDeleteNotify). Require live WinPE's actual persisted value first.
reg query HKLM\SYSTEM\CurrentControlSet\Control\FileSystem /v DisableDeleteNotification | find "0x1" >nul
if errorlevel 1 exit /b 26
if not exist S:\UAVF-DISPLAY.TAG exit /b 11
if not exist W:\Windows\System32\Config\SYSTEM exit /b 12
if not exist W:\Windows\System32\ntoskrnl.exe exit /b 13
if /i "%~1"=="--idd-fresh" (vol W: | find "UAVF_IDD_TEST" >nul) else (vol W: | find "UAVFWindows" >nul)
if errorlevel 1 exit /b 14
if exist W:\uavf-base-provisioned.txt exit /b 15
W:\Windows\System32\fc.exe /b X:\Windows\System32\ntoskrnl.exe W:\Windows\System32\ntoskrnl.exe >nul
if errorlevel 1 exit /b 17
if not exist W:\UAVF mkdir W:\UAVF
if errorlevel 1 exit /b 16

rem Stage only the signed driver packages already proven in this exact WinPE.
dism /Image:W:\ /Add-Driver /Driver:X:\Windows\System32\uavf-drivers /Recurse /LogPath:W:\UAVF\driver-provision.log
if errorlevel 1 exit /b 20
for %%F in (winpe-vsock.exe winpe-encode.exe winpe-screen.exe openh264.dll) do (
  if not exist X:\Windows\System32\%%F exit /b 21
  copy /b /y X:\Windows\System32\%%F W:\Windows\System32\%%F >nul
  if errorlevel 1 exit /b 22
  W:\Windows\System32\fc.exe /b X:\Windows\System32\%%F W:\Windows\System32\%%F >nul
  if errorlevel 1 exit /b 23
)
for %%F in (viosocklib.dll viosockwspsvc.exe) do (
  copy /b /y X:\Windows\System32\uavf-drivers\viosock\%%F W:\Windows\System32\%%F >nul
  if errorlevel 1 exit /b 24
  W:\Windows\System32\fc.exe /b X:\Windows\System32\uavf-drivers\viosock\%%F W:\Windows\System32\%%F >nul
  if errorlevel 1 exit /b 25
)

rem Preserve hive before the one scoped workaround. Select is inspected first;
rem never guess ControlSet001 or set an online production registry value.
if exist W:\UAVF\SYSTEM-before-trim.hiv exit /b 30
copy /b W:\Windows\System32\Config\SYSTEM W:\UAVF\SYSTEM-before-trim.hiv >nul
if errorlevel 1 exit /b 31
reg load HKLM\UAVFInstalled W:\Windows\System32\Config\SYSTEM
if errorlevel 1 exit /b 32
reg query HKLM\UAVFInstalled\Select > W:\UAVF\select-before.txt
reg query HKLM\UAVFInstalled\Setup > W:\UAVF\setup-before.txt
set "Current="
for /f "tokens=3" %%C in ('reg query HKLM\UAVFInstalled\Select /v Current 2^>nul ^| find "REG_DWORD"') do set "Current=%%C"
set "ControlSet="
if /i "%Current%"=="0x1" set "ControlSet=ControlSet001"
if /i "%Current%"=="0x2" set "ControlSet=ControlSet002"
if /i "%Current%"=="0x3" set "ControlSet=ControlSet003"
if not defined ControlSet goto hive_failed
reg query HKLM\UAVFInstalled\%ControlSet%\Control\FileSystem > W:\UAVF\filesystem-before.txt
if errorlevel 1 goto hive_failed
reg add HKLM\UAVFInstalled\%ControlSet%\Control\FileSystem /v DisableDeleteNotification /t REG_DWORD /d 1 /f
if errorlevel 1 goto hive_failed
reg query HKLM\UAVFInstalled\%ControlSet%\Control\FileSystem /v DisableDeleteNotification > W:\UAVF\trim-readback.txt
if errorlevel 1 goto hive_failed
find "0x1" W:\UAVF\trim-readback.txt >nul
if errorlevel 1 goto hive_failed
rem The broker chooses the active console session and its input desktop.
rem Older media keep the existing interactive capture launch below.
if exist S:\UAVF\BROKER.EXE (
  copy /b S:\UAVF\BROKER.EXE W:\Windows\System32\uavf-display-broker.exe >nul || goto hive_failed
  W:\Windows\System32\fc.exe /b S:\UAVF\BROKER.EXE W:\Windows\System32\uavf-display-broker.exe >nul || goto hive_failed
  set "Broker=HKLM\UAVFInstalled\%ControlSet%\Services\UAVFWindowsDisplay"
  call :register_broker || goto hive_failed
)
reg unload HKLM\UAVFInstalled
if errorlevel 1 exit /b 35
rem Real user-session capture after OOBE/login; never a Session0 service.
reg load HKLM\UAVFInstalledSoftware W:\Windows\System32\Config\SOFTWARE
if errorlevel 1 exit /b 36
reg query HKLM\UAVFInstalledSoftware\Microsoft\Windows\CurrentVersion\Run /v UAVFDisplay >nul 2>&1
if not errorlevel 1 goto software_failed
reg export HKLM\UAVFInstalledSoftware\Microsoft\Windows\CurrentVersion\Run W:\UAVF\Run-before-display.reg
if errorlevel 1 goto software_failed
reg add HKLM\UAVFInstalledSoftware\Microsoft\Windows\CurrentVersion\Run /v UAVFDisplay /t REG_SZ /d "cmd.exe /d /c start /min cmd.exe /d /c uavf-installed-display.cmd" /f
if errorlevel 1 goto software_failed
reg query HKLM\UAVFInstalledSoftware\Microsoft\Windows\CurrentVersion\Run /v UAVFDisplay > W:\UAVF\user-display-autostart-readback.txt
if errorlevel 1 goto software_failed
reg unload HKLM\UAVFInstalledSoftware
if errorlevel 1 exit /b 37
echo BASE_PROVISIONED=1; NOT_INSTALLED_BOOT_PASS > W:\uavf-base-provisioned.txt
exit /b 0

:software_failed
reg unload HKLM\UAVFInstalledSoftware
exit /b 38
:hive_failed
reg unload HKLM\UAVFInstalled
exit /b 34

:register_broker
reg query "%Broker%" >nul 2>&1
if not errorlevel 1 exit /b 1
reg add "%Broker%" /v Type /t REG_DWORD /d 16 /f || exit /b 1
reg add "%Broker%" /v Start /t REG_DWORD /d 2 /f || exit /b 1
reg add "%Broker%" /v ErrorControl /t REG_DWORD /d 1 /f || exit /b 1
reg add "%Broker%" /v ObjectName /t REG_SZ /d LocalSystem /f || exit /b 1
reg add "%Broker%" /v DisplayName /t REG_SZ /d "U-AVF Windows display" /f || exit /b 1
reg add "%Broker%" /v ImagePath /t REG_EXPAND_SZ /d "\SystemRoot\System32\uavf-display-broker.exe --display-service" /f || exit /b 1
reg query "%Broker%" > W:\UAVF\display-service-readback.txt
exit /b %errorlevel%
