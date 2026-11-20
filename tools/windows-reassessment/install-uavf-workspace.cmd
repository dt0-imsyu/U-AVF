@echo off
setlocal EnableExtensions
rem Entry point for a fresh disk built by U-AVF. Run inside its verified WinPE.
if /i not "%SystemDrive%"=="X:" exit /b 10
if /i not "%~d0"=="S:" exit /b 11
cd /d X:\Windows\System32
if not exist S:\UAVF-DISPLAY.TAG exit /b 12
if not exist S:\sources\install.swm exit /b 13
if not exist S:\sources\install2.swm exit /b 14
if exist W:\ exit /b 15
if exist S:\UAVF\INPROG.TAG exit /b 16
if exist S:\UAVF\BOOTRDY.TAG exit /b 17
for %%F in (BASE BOOTPREP FIRSTBOT DISPLAY) do if not exist S:\UAVF\%%F.CMD exit /b 18

set "Scratch=X:\Windows\Temp\uavf-install"
if exist "%Scratch%" exit /b 19
mkdir "%Scratch%" || exit /b 20
> "%Scratch%\identify.txt" (
  echo select disk 0
  echo uniqueid disk
)
diskpart /s "%Scratch%\identify.txt" > S:\UAVF\IDENTIFY.LOG 2>&1
if errorlevel 1 exit /b 21
find /i "CA74DE42-15B0-4E7E-A686-9C21E2D41320" S:\UAVF\IDENTIFY.LOG >nul
if errorlevel 1 exit /b 22
echo Windows will be installed into the unallocated area of the U-AVF virtual disk.
if /i "%~1"=="--approved" goto app_approval
set "Confirm="
set /p "Confirm=Type INSTALL to proceed: "
if /i not "%Confirm%"=="INSTALL" exit /b 23
goto approved
:app_approval
if not exist S:\UAVF\INSTALL.OK exit /b 23
setlocal EnableDelayedExpansion
set "Approval="
< S:\UAVF\INSTALL.OK set /p "Approval="
if not "!Approval!"=="UAVF_WINDOWS_INSTALL_APPROVED_V1" exit /b 23
endlocal
:approved
echo UAVF_WINDOWS_INSTALL_STARTED> S:\UAVF\INPROG.TAG
if errorlevel 1 exit /b 24
if /i "%~1"=="--approved" (
  del S:\UAVF\INSTALL.OK
  if exist S:\UAVF\INSTALL.OK exit /b 24
)

rem DiskPart stops on error. Never select or format a pre-existing partition.
> "%Scratch%\partition.txt" (
  echo select disk 0
  echo create partition msr size=16 offset=8912896
  echo create partition primary size=65000 offset=8929280
  echo format quick fs=ntfs label=UAVFWindows
  echo assign letter=W
)
reg add HKLM\SYSTEM\CurrentControlSet\Control\FileSystem /v DisableDeleteNotification /t REG_DWORD /d 1 /f >nul
if errorlevel 1 exit /b 25
diskpart /s "%Scratch%\partition.txt" > S:\UAVF\DISKPART.LOG 2>&1
if errorlevel 1 exit /b 26
vol W: | find "UAVFWindows" >nul
if errorlevel 1 exit /b 27
if exist W:\Windows exit /b 28
mkdir W:\uavf-scratch || exit /b 29

echo Applying Windows 11 ARM64...
dism /Apply-Image /ImageFile:S:\sources\install.swm /SWMFile:S:\sources\install*.swm /Index:3 /ApplyDir:W:\ /Compact /CheckIntegrity /Verify /ScratchDir:W:\uavf-scratch /LogPath:W:\uavf-apply.log
set "ApplyExit=%errorlevel%"
> W:\uavf-apply-exit.txt echo APPLY_EXIT=%ApplyExit%
if not "%ApplyExit%"=="0" exit /b 30
call S:\UAVF\BASE.CMD
if errorlevel 1 exit /b 31

copy /b /y S:\UAVF\FIRSTBOT.CMD X:\uavf-firstboot.cmd >nul || exit /b 32
copy /b /y S:\UAVF\DISPLAY.CMD X:\uavf-display.cmd >nul || exit /b 33
copy /b /y X:\uavf-firstboot.cmd W:\Windows\System32\uavf-firstboot.cmd >nul || exit /b 34
copy /b /y X:\uavf-display.cmd W:\Windows\System32\uavf-installed-display.cmd >nul || exit /b 35
if exist S:\UAVF\OFFLINE.XML (
  if exist W:\Windows\Panther\unattend.xml exit /b 39
  if not exist W:\Windows\Panther mkdir W:\Windows\Panther || exit /b 39
  copy /b S:\UAVF\OFFLINE.XML W:\Windows\Panther\unattend.xml >nul || exit /b 39
  W:\Windows\System32\fc.exe /b S:\UAVF\OFFLINE.XML W:\Windows\Panther\unattend.xml >nul || exit /b 39
)
call S:\UAVF\BOOTPREP.CMD
if errorlevel 1 exit /b 36
> S:\UAVF\BOOTRDY.TAG echo UAVF_WINDOWS_BOOT_PREPARED_V1
if errorlevel 1 exit /b 37
if /i "%~1"=="--approved" (
  wpeutil shutdown
  if errorlevel 1 exit /b 38
  exit /b 0
)
echo Windows deployment and boot preparation completed. Shut down WinPE with wpeutil shutdown.
exit /b 0
