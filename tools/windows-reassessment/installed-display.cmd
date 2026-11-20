@echo off
setlocal EnableExtensions
rem Run only in the actual interactive Setup/user session, NOT Session0 service.
rem The SCM broker already handles login and desktop; never start a competing loop.
sc query UAVFWindowsDisplay 2>nul | findstr /C:"RUNNING" >nul
if not errorlevel 1 exit /b 0
rem Setup can reach this hook before SCM has started the packaged broker.
rem Ask SCM to start it first, then fall back to the interactive producer
rem if the service is absent or cannot start.
sc start UAVFWindowsDisplay >nul 2>&1
if not errorlevel 1 exit /b 0
sc query UAVFWindowsDisplay 2>nul | findstr /C:"RUNNING" /C:"START_PENDING" >nul
if not errorlevel 1 exit /b 0
rem Standard users cannot write the LocalSystem-created ProgramData diagnostics.
set "UAVF_USER_DISPLAY=%LOCALAPPDATA%\UAVF\WindowsDisplay"
if not defined LOCALAPPDATA set "UAVF_USER_DISPLAY=%TEMP%\UAVF\WindowsDisplay"
if not exist "%UAVF_USER_DISPLAY%" mkdir "%UAVF_USER_DISPLAY%"
if not exist "%UAVF_USER_DISPLAY%" exit /b 20
:retry
"%SystemRoot%\System32\winpe-vsock.exe" "%UAVF_USER_DISPLAY%" --live
echo PRODUCER_EXIT=%errorlevel% >> "%UAVF_USER_DISPLAY%\producer-restarts.txt"
rem Signed viosock device may appear after initial PnP. Retry our producer only.
timeout /t 3 /nobreak >nul
goto retry
