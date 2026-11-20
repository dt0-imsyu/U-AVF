@echo off
setlocal EnableExtensions
rem Native first-logon task in disposable Windows only, never UI keystrokes.
vol C: | find "UAVF_IDD_TEST" >nul
if errorlevel 1 exit /b 10
if exist C:\UAVF\IddProbe\source-job-started.txt exit /b 11
echo DISPOSABLE_IDD_SOURCE_JOB_STARTED > C:\UAVF\IddProbe\source-job-started.txt
cd /d C:\UAVF\IddProbe
VC_redist.arm64.exe /install /quiet /norestart /log C:\UAVF\IddProbe\vc-install.log
set "Code=%errorlevel%"
if "%Code%"=="3010" goto runtime_ok
if not "%Code%"=="0" goto fail
:runtime_ok
certutil -addstore -f Root package\UavfIddTestOnly.cer > cert-root.txt 2>&1
if errorlevel 1 goto fail
certutil -addstore -f TrustedPublisher package\UavfIddTestOnly.cer > cert-publisher.txt 2>&1
if errorlevel 1 goto fail
pnputil /add-driver package\UavfIddSourceProbe.inf /install > driver-stage.txt 2>&1
if errorlevel 1 goto fail
logman start UavfIddSource -p "{43ea47ba-292b-4e83-a8ca-c0d468ea31ae}" 0xffffffffffffffff 5 -o C:\UAVF\IddProbe\source.etl -ets > trace-start.txt 2>&1
if errorlevel 1 goto fail
start "" /b idd-probe-device.exe --disposable-load-probe > admission.txt 2>&1
idd-motion-source.exe > motion-exit.txt 2>&1
set "MotionExit=%errorlevel%"
timeout /t 5 /nobreak >nul
logman stop UavfIddSource -ets > trace-stop.txt 2>&1
idd-etw-read.exe C:\UAVF\IddProbe\source.etl > source-report.txt 2>&1
echo MOTION_EXIT=%MotionExit%; NOT_ANDROID_PRESENT_PASS > source-job-finished.txt
exit /b 0
:fail
echo SOURCE_JOB_FAILED=%errorlevel%; NOT_DRIVER_OR_FPS_PASS > source-job-failed.txt
exit /b 20
