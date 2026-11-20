@echo off
setlocal EnableExtensions
rem Isolated Windows recovery only. Copies evidence; never formats or deletes.
if /i not "%SystemDrive%"=="X:" exit /b 10
if not exist S:\UAVF-DISPLAY.TAG exit /b 11
vol W: | find "UAVFWindows" >nul
if errorlevel 1 exit /b 12
vol W: | find "A8E6-FF6E" >nul
if errorlevel 1 exit /b 13
if not exist W:\UAVF\SYSTEM-before-trim.hiv exit /b 14
if not exist W:\Windows\Panther\setuperr.log exit /b 15
set "Saved=S:\UAVFRECOVERY\before-clean-specialize-20261009"
if exist "%Saved%" exit /b 16
mkdir "%Saved%"
if errorlevel 1 exit /b 17
xcopy W:\UAVF "%Saved%\UAVF\" /E /H /I /Y /Q
if errorlevel 1 exit /b 18
xcopy W:\Windows\Panther "%Saved%\Panther\" /E /H /I /Y /Q
if errorlevel 1 exit /b 19
xcopy W:\ProgramData\UAVF "%Saved%\Runtime\" /E /H /I /Y /Q
if errorlevel 1 exit /b 20
W:\Windows\System32\fc.exe /b W:\UAVF\SYSTEM-before-trim.hiv "%Saved%\UAVF\SYSTEM-before-trim.hiv" >nul
if errorlevel 1 exit /b 21
W:\Windows\System32\fc.exe /b W:\Windows\Panther\setuperr.log "%Saved%\Panther\setuperr.log" >nul
if errorlevel 1 exit /b 22
W:\Windows\System32\fc.exe /b W:\Windows\Panther\setupact.log "%Saved%\Panther\setupact.log" >nul
if errorlevel 1 exit /b 23
echo EVIDENCE_COPIED_AND_KEY_FILES_BYTE_VERIFIED; ROOT_NOT_FORMATTED > "%Saved%\preserved.txt"
type "%Saved%\preserved.txt"
exit /b 0
