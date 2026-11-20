@echo off
setlocal EnableExtensions
rem WinPE-only, explicit disposable target, no format/partition operations.
if /i not "%SystemDrive%"=="X:" exit /b 10
if not exist S:\UAVFIDD\fresh-root-provenance.txt exit /b 11
vol W: | find "UAVF_IDD_TEST" >nul
if errorlevel 1 exit /b 12
if exist W:\uavf-base-provisioned.txt exit /b 13
fsutil behavior set DisableDeleteNotify 1
if errorlevel 1 exit /b 14
call S:\UAVFIDD\provision-base.cmd --idd-fresh
if errorlevel 1 exit /b 15
copy /b /y X:\uavf-firstboot.cmd W:\Windows\System32\uavf-firstboot.cmd >nul
if errorlevel 1 exit /b 16
copy /b /y X:\uavf-display.cmd W:\Windows\System32\uavf-installed-display.cmd >nul
if errorlevel 1 exit /b 17
if not exist W:\UAVF\IddProbe mkdir W:\UAVF\IddProbe
if errorlevel 1 exit /b 18
xcopy /e /i /h /y S:\UAVFIDD\package W:\UAVF\IddProbe\package
if errorlevel 1 exit /b 19
copy /b /y S:\UAVFIDD\idd-probe-device.exe W:\UAVF\IddProbe\idd-probe-device.exe >nul
if errorlevel 1 exit /b 20
copy /b /y S:\UAVFIDD\VC_redist.arm64.exe W:\UAVF\IddProbe\VC_redist.arm64.exe >nul
if errorlevel 1 exit /b 21
xcopy /e /i /h /y X:\UAVFIddJob W:\UAVF\IddProbe
if errorlevel 1 exit /b 26
copy /b /y X:\UAVFIddJob\unattend.xml W:\Windows\Panther\unattend.xml >nul
if errorlevel 1 exit /b 22
call S:\UAVFIDD\prepare-boot.cmd
if errorlevel 1 exit /b 23
bcdedit /store S:\EFI\Microsoft\Boot\BCD /set {default} testsigning on
if errorlevel 1 exit /b 24
bcdedit /store S:\EFI\Microsoft\Boot\BCD /enum {default} > W:\UAVF\IddProbe\bcd-testmode.txt
if errorlevel 1 exit /b 25
echo IDD_DISPOSABLE_PROVISIONED; NOT_DRIVER_OR_FPS_PASS
exit /b 0
