@echo off
setlocal EnableExtensions
rem Read/copy only. WinPE must mount the existing clean root W: and source S:.
if /i not "%SystemDrive%"=="X:" exit /b 60
if not exist S:\UAVF-DISPLAY.TAG exit /b 61
vol W: | findstr /i /c:"3EF6-69B7" >nul
if errorlevel 1 exit /b 62
if not exist W:\ProgramData\UAVF\firstboot-state.txt exit /b 63
set "Evidence=S:\UAVFRECOVERY\after-clean-firstboot-20261009"
if exist "%Evidence%" exit /b 64
mkdir "%Evidence%"
if errorlevel 1 exit /b 65
xcopy W:\ProgramData\UAVF "%Evidence%\Runtime\" /e /i /h /k
if errorlevel 1 exit /b 66
xcopy W:\Windows\Panther "%Evidence%\Panther\" /e /i /h /k
if errorlevel 1 exit /b 67
W:\Windows\System32\fc.exe /b W:\ProgramData\UAVF\firstboot-state.txt "%Evidence%\Runtime\firstboot-state.txt"
if errorlevel 1 exit /b 68
echo FIRSTBOOT_EVIDENCE_COPIED; NO_SETUP_STATE_CHANGED > "%Evidence%\preserved.txt"
cls
type "%Evidence%\Runtime\firstboot.txt"
type "%Evidence%\Runtime\firstboot-state.txt"
echo === NATIVE SETUP ERRORS ===
if exist "%Evidence%\Panther\setuperr.log" type "%Evidence%\Panther\setuperr.log"
echo === NATIVE UNATTEND ERRORS ===
if exist "%Evidence%\Panther\UnattendGC\setuperr.log" type "%Evidence%\Panther\UnattendGC\setuperr.log"
exit /b 0
