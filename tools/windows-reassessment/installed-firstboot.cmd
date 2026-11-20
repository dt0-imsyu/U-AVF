@echo off
setlocal EnableExtensions
rem Experimental isolated Windows first-boot hook. Provision ONLY after reading
rem the exact offline Setup CmdLine and proving it is oobe\windeploy.exe.
rem Restore that original command BEFORE starting anything; no OOBE flags edited.
set "Original="
for /f "tokens=2,*" %%A in ('reg query HKLM\SYSTEM\Setup /v UAVFOriginalCmdLine 2^>nul ^| findstr "REG_SZ"') do set "Original=%%B"
if /i not "%Original%"=="oobe\windeploy.exe" exit /b 40
reg add HKLM\SYSTEM\Setup /v CmdLine /t REG_SZ /d "%Original%" /f
if errorlevel 1 exit /b 41
if not exist "%ProgramData%\UAVF" mkdir "%ProgramData%\UAVF"
if not exist "%ProgramData%\UAVF\WindowsDisplay" mkdir "%ProgramData%\UAVF\WindowsDisplay"
echo FIRSTBOOT_HOOK_ENTERED; NOT_DESKTOP_PASS > "%ProgramData%\UAVF\firstboot.txt"
start "" /min "%SystemRoot%\System32\cmd.exe" /d /c "%SystemRoot%\System32\uavf-installed-display.cmd"
rem Observe the native clock and actual Setup state BEFORE signed deployment.
rem Earlier failed installation reported year2587; do not assume its cause or
rem forge completion flags. This records evidence only and never sets time.
echo FIRSTBOOT_LOCAL_DATE=%date% TIME=%time% > "%ProgramData%\UAVF\firstboot-state.txt"
reg query HKLM\SYSTEM\Setup >> "%ProgramData%\UAVF\firstboot-state.txt" 2>&1
reg query HKLM\SYSTEM\Setup\Status\SysprepStatus >> "%ProgramData%\UAVF\firstboot-state.txt" 2>&1
reg query HKLM\BCD00000000\Description >> "%ProgramData%\UAVF\firstboot-state.txt" 2>&1
cd /d "%SystemRoot%\System32"
"%SystemRoot%\System32\oobe\windeploy.exe"
set "DeployExit=%errorlevel%"
echo WINDEPLOY_EXIT=%DeployExit%; NOT_OOBE_PASS >> "%ProgramData%\UAVF\firstboot.txt"
rem Native first boot may finish successfully and request reboot BEFORE OOBE.
rem Keep only our capture wrapper available for that next native Setup launch.
rem Never set completion/OOBE/SystemSetup flags or rerun specialize ourselves.
if not "%DeployExit%"=="0" exit /b %DeployExit%
reg query HKLM\SYSTEM\Setup /v OOBEInProgress | find "0x1" >nul
if errorlevel 1 exit /b %DeployExit%
reg add HKLM\SYSTEM\Setup /v CmdLine /t REG_SZ /d "cmd.exe /d /c uavf-firstboot.cmd" /f
if errorlevel 1 exit /b 42
echo CAPTURE_WRAPPER_REARMED_FOR_NATIVE_OOBE >> "%ProgramData%\UAVF\firstboot.txt"
exit /b %DeployExit%
