@echo off
setlocal EnableExtensions
if /i not "%SystemDrive%"=="X:" exit /b 10
if not exist S:\UAVF-DISPLAY.TAG exit /b 11
if not exist W:\uavf-base-provisioned.txt exit /b 12
if not exist W:\Windows\System32\uavf-firstboot.cmd exit /b 13
if not exist W:\Windows\System32\uavf-installed-display.cmd exit /b 14
if exist W:\UAVF\BCD-winpe.bak exit /b 15
W:\Windows\System32\fc.exe /b X:\uavf-firstboot.cmd W:\Windows\System32\uavf-firstboot.cmd >nul
if errorlevel 1 exit /b 16
W:\Windows\System32\fc.exe /b X:\uavf-display.cmd W:\Windows\System32\uavf-installed-display.cmd >nul
if errorlevel 1 exit /b 17
copy /b S:\EFI\Microsoft\Boot\BCD W:\UAVF\BCD-winpe.bak >nul
if errorlevel 1 exit /b 18
copy /b S:\EFI\BOOT\BOOTAA64.EFI W:\UAVF\BOOTAA64-before-bcdboot.EFI >nul
if errorlevel 1 exit /b 19
copy /b S:\EFI\BOOT\WINORIG.EFI W:\UAVF\WINORIG-before-bcdboot.EFI >nul
if errorlevel 1 exit /b 20
reg load HKLM\UAVFInstalled W:\Windows\System32\Config\SYSTEM
if errorlevel 1 exit /b 21
set "Original="
for /f "tokens=2,*" %%A in ('reg query HKLM\UAVFInstalled\Setup /v CmdLine 2^>nul ^| find "REG_SZ"') do set "Original=%%B"
if /i not "%Original%"=="oobe\windeploy.exe" goto hive_failed
reg export HKLM\UAVFInstalled\Setup W:\UAVF\Setup-before-hook.reg
if errorlevel 1 goto hive_failed
reg add HKLM\UAVFInstalled\Setup /v UAVFOriginalCmdLine /t REG_SZ /d "%Original%" /f
if errorlevel 1 goto hive_failed
reg add HKLM\UAVFInstalled\Setup /v CmdLine /t REG_SZ /d "cmd.exe /d /c uavf-firstboot.cmd" /f
if errorlevel 1 goto hive_failed
reg query HKLM\UAVFInstalled\Setup /v CmdLine > W:\UAVF\firstboot-hook-readback.txt
reg unload HKLM\UAVFInstalled
if errorlevel 1 exit /b 22

rem Only the isolated source ESP boot store changes. Keep custom entry shim and
rem original chainloader byte-identical; source WIM and SWMs are never changed.
bcdboot W:\Windows /s S: /f UEFI /v > W:\UAVF\bcdboot-installed.log 2>&1
set "BcdExit=%errorlevel%"
copy /b /y W:\UAVF\BOOTAA64-before-bcdboot.EFI S:\EFI\BOOT\BOOTAA64.EFI >nul
if errorlevel 1 exit /b 23
W:\Windows\System32\fc.exe /b W:\UAVF\BOOTAA64-before-bcdboot.EFI S:\EFI\BOOT\BOOTAA64.EFI >nul
if errorlevel 1 exit /b 24
W:\Windows\System32\fc.exe /b W:\UAVF\WINORIG-before-bcdboot.EFI S:\EFI\BOOT\WINORIG.EFI >nul
if errorlevel 1 exit /b 25
if not "%BcdExit%"=="0" goto bcd_failed
rem Match the proven WinPE serial debugger contract, not new CPU/HAL flags.
bcdedit /store S:\EFI\Microsoft\Boot\BCD /dbgsettings serial debugport:1 baudrate:115200
if errorlevel 1 goto bcd_failed
bcdedit /store S:\EFI\Microsoft\Boot\BCD /debug {default} on
if errorlevel 1 goto bcd_failed
bcdedit /store S:\EFI\Microsoft\Boot\BCD /bootdebug {default} on
if errorlevel 1 goto bcd_failed
bcdedit /store S:\EFI\Microsoft\Boot\BCD /enum all > W:\UAVF\bcd-installed-readback.txt
if errorlevel 1 goto bcd_failed
rem The retained WinPE template lacks the system-store role. Exact installed
rem runtime A/B: missing Description\System -> SpBcd 0xC0000098; System1 ->
rem default bcdedit succeeds, including after cold boot. No boot flags changed.
reg load HKLM\UAVFInstalledBCD S:\EFI\Microsoft\Boot\BCD
if errorlevel 1 goto bcd_failed
reg query HKLM\UAVFInstalledBCD\Description /v KeyName | find "BCD00000000" >nul
if errorlevel 1 goto bcd_hive_failed
reg add HKLM\UAVFInstalledBCD\Description /v System /t REG_DWORD /d 1 /f
if errorlevel 1 goto bcd_hive_failed
reg query HKLM\UAVFInstalledBCD\Description /v System > W:\UAVF\bcd-system-role-readback.txt
if errorlevel 1 goto bcd_hive_failed
type W:\UAVF\bcd-system-role-readback.txt | find "0x1" >nul
if errorlevel 1 goto bcd_hive_failed
reg unload HKLM\UAVFInstalledBCD
if errorlevel 1 exit /b 32
echo BOOT_PREPARED=1; NOT_INSTALLED_BOOT_PASS > W:\UAVF\boot-prepared.txt
exit /b 0

:hive_failed
reg unload HKLM\UAVFInstalled
exit /b 30
:bcd_hive_failed
reg unload HKLM\UAVFInstalledBCD
if errorlevel 1 exit /b 32
:bcd_failed
copy /b /y W:\UAVF\BCD-winpe.bak S:\EFI\Microsoft\Boot\BCD >nul
exit /b 31
