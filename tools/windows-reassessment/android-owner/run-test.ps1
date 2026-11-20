[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$EvidenceName,[switch]$PrivateStorage,[switch]$SmssPmuWorkaround,[switch]$RestorePrivate,[switch]$ShellParity,[switch]$PacedUart,[switch]$DeferDisplay,[switch]$ObservePmu)
$ErrorActionPreference='Stop'
if($EvidenceName -notmatch '^[a-zA-Z0-9-]+$'){throw 'Invalid evidence directory'}
$taskRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '..\..\..'))
$workspaceRoot=Split-Path -Parent $repoRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$python=Join-Path $env:LOCALAPPDATA 'Programs\Python\Python314\python.exe'
$policy=& $adb shell dumpsys window policy
if($policy -match 'showing=true' -or $policy -match 'screenState=SCREEN_STATE_OFF'){throw 'Unlock tablet before visible display test; no lock bypass'}
$running=& $adb shell /apex/com.android.virt/bin/vm list
if(($running -join "`n") -notmatch 'Running VMs: \[\]'){throw 'A VM is active; preserve it, do not run another blindly'}
$evidence=Join-Path $workspaceRoot "build-logs\windows-post-linux-reassessment-20261004\$EvidenceName"
if(Test-Path -LiteralPath $evidence){throw 'Preserve previous evidence'}
$original=(& $adb shell settings get global hidden_api_policy).Trim()
$token=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
try {
    & $adb shell settings put global hidden_api_policy 1
    if($LASTEXITCODE){throw 'Diagnostic console policy not applied'}
    & $adb forward tcp:39101 tcp:39101
    & $adb shell am force-stop com.example.uavfwinpetest
    & $adb shell am start -n com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity --es token $token --ez private_storage $PrivateStorage.IsPresent.ToString().ToLowerInvariant() --ez restore_private $RestorePrivate.IsPresent.ToString().ToLowerInvariant() --ez shell_parity $ShellParity.IsPresent.ToString().ToLowerInvariant() --ez paced_uart $PacedUart.IsPresent.ToString().ToLowerInvariant() --ez defer_display $DeferDisplay.IsPresent.ToString().ToLowerInvariant()
    $extra=@();if($SmssPmuWorkaround){$extra+='--smss-pmu-workaround'}
    if($ObservePmu){$extra+='--sample-pmu-after-io'}
    & $python "$repoRoot\tools\windows-reassessment\trace-kd-boot-drivers.py" $adb unused $evidence "$workspaceRoot\build-logs\windows-arm64-timer-static-20260913\ntoskrnl.exe" --candidate-sha A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5 --cam-access --periodic-imask-workaround --sample-after-io --process-inventory --device-inventory --break-on-reboot --break-on-idle --idle-seconds 60 --seconds 150 --app-bridge-token $token @extra
    if($LASTEXITCODE){throw 'Observer failed: inspect retained evidence'}
} finally {
    & $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/owner-report.txt "$evidence\owner-report.txt"
    & $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/virtmgr-log.txt "$evidence\virtmgr-log.txt"
    & $adb shell am force-stop com.example.uavfwinpetest
    if($original -eq 'null'){& $adb shell settings delete global hidden_api_policy}
    else {& $adb shell settings put global hidden_api_policy $original}
    & $adb forward --remove tcp:39101
    & $adb shell settings get global hidden_api_policy
    & $adb shell /apex/com.android.virt/bin/vm list
}
