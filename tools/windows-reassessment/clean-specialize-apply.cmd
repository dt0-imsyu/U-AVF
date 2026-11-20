@echo off
setlocal EnableExtensions
rem DESTRUCTIVE: only the explicitly disposable Windows NTFS root W:.
rem Preserved first-attempt evidence is mandatory. No disk/partition creation.
if /i not "%SystemDrive%"=="X:" exit /b 10
if not exist S:\UAVF-DISPLAY.TAG exit /b 11
if not exist S:\sources\install.swm exit /b 12
if not exist S:\sources\install2.swm exit /b 13
if not exist S:\UAVFRECOVERY\before-clean-specialize-20261009\preserved.txt exit /b 14
find "EVIDENCE_COPIED_AND_KEY_FILES_BYTE_VERIFIED" S:\UAVFRECOVERY\before-clean-specialize-20261009\preserved.txt >nul
if errorlevel 1 exit /b 15
vol W: | find "UAVFWindows" >nul
if errorlevel 1 exit /b 16
vol W: | find "A8E6-FF6E" >nul
if errorlevel 1 exit /b 17
if not exist W:\UAVF\SYSTEM-before-trim.hiv exit /b 18
fsutil behavior set DisableDeleteNotify 1
if errorlevel 1 exit /b 19
reg query HKLM\SYSTEM\CurrentControlSet\Control\FileSystem /v DisableDeleteNotification | find "0x1" >nul
if errorlevel 1 exit /b 20
echo FORMATTING_VERIFIED_DISPOSABLE_WINDOWS_ROOT_ONLY
format W: /FS:NTFS /Q /V:UAVFWindows /Y
if errorlevel 1 exit /b 21
if exist W:\Windows exit /b 22
if not exist S:\UAVFRECOVERY\before-clean-specialize-20261009\preserved.txt exit /b 23
mkdir W:\uavf-scratch
if errorlevel 1 exit /b 24
dism /Apply-Image /ImageFile:S:\sources\install.swm /SWMFile:S:\sources\install*.swm /Index:3 /ApplyDir:W:\ /Compact /CheckIntegrity /Verify /ScratchDir:W:\uavf-scratch /LogPath:W:\uavf-apply-trim-off.log
set "ApplyExit=%errorlevel%"
echo APPLY_EXIT=%ApplyExit% > W:\uavf-apply-exit.txt
type W:\uavf-apply-exit.txt
exit /b %ApplyExit%
