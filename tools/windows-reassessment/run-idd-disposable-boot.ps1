param([Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9-]+$')][string]$EvidenceName,
      [ValidateRange(210,3600)][int]$ObserverSeconds=1800,[switch]$Bootstrap,[switch]$ExistingNativeGop)
$ErrorActionPreference='Stop'
$taskPython='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
$taskAdb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$taskOutput='E:\U-AVF\artifacts\idd-disposable-disk-20261013'
$taskCandidate=Join-Path $taskWorkspace 'build-logs\windows-post-linux-reassessment-20261004\smp-ram-gop-handoff-audit-preflight2-20261011\candidate-BOOTAA64.EFI'
$taskPlan=Join-Path $taskOutput "$EvidenceName-gop-plan"
$taskApplied=Join-Path $taskOutput "$EvidenceName-gop-applied"
$taskRollback=Join-Path $taskOutput "$EvidenceName-gop-restored"
$taskActive=$false;$taskGopApplied=$false;$taskEnvPrevious=@{}
try {
    & $taskPython "$PSScriptRoot/activate-idd-disposable-disk.py" activate
    if($LASTEXITCODE){throw 'Disposable activation failed'}
    $taskActive=$true
    if($ExistingNativeGop){
        & $taskPython "$PSScriptRoot/verify-idd-native-gop.py"
        if($LASTEXITCODE){throw 'Existing test GOP identity failed'}
    } else {
    & $taskPython "$PSScriptRoot/prepare-ram-gop-file.py" --adb $taskAdb --candidate $taskCandidate --output $taskPlan
    if($LASTEXITCODE){throw 'Disposable GOP preflight failed'}
    & $taskPython "$PSScriptRoot/apply-ram-gop-file.py" --adb $taskAdb --prepared $taskPlan --output $taskApplied --apply
    if($LASTEXITCODE){throw 'Disposable known GOP apply failed'}
    $taskGopApplied=$true
    }
    # Literal known-good eight-CPU runtime sequence, not a new CPU/timer A/B.
    foreach($key in @('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')) {
        $name='UAVF_KD_'+$key
        $taskEnvPrevious[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
        [Environment]::SetEnvironmentVariable($name,'1','Process')
    }
    foreach($name in @('UAVF_KD_PSCI_POST_START_MINIMAL','UAVF_WINDOWS_RAM_GOP')){
        $taskEnvPrevious[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
    }
    $env:UAVF_WINDOWS_RAM_GOP='1'
    [Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
    & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName $EvidenceName -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds $ObserverSeconds -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus -IddDisposableBootstrap:$Bootstrap
    if($LASTEXITCODE){throw 'Disposable observer failed'}
} finally {
    foreach($name in $taskEnvPrevious.Keys){[Environment]::SetEnvironmentVariable($name,$taskEnvPrevious[$name],'Process')}
    if($taskActive){
        # Stop only own diagnostic APK; never stop production Ubuntu/app.
        & $taskAdb shell am force-stop com.example.uavfwinpetest
        $taskState=(& $taskAdb shell /apex/com.android.virt/bin/vm list) -join "`n"
        if($LASTEXITCODE -or $taskState -notmatch 'Running VMs: \[\]'){throw 'No cold restore possible; original remains protected, do not write'}
        $taskRecoveryError=$null
        if($taskGopApplied){
            & $taskPython "$PSScriptRoot/apply-ram-gop-file.py" --adb $taskAdb --prepared $taskPlan --output $taskRollback --rollback --apply
            if($LASTEXITCODE){$taskRecoveryError='Test-only GOP whole-block rollback refused after guest FAT changes; test retained, no force overwrite'}
        }
        & $taskPython "$PSScriptRoot/activate-idd-disposable-disk.py" restore
        if($LASTEXITCODE){throw 'Protected original restoration failed; do not launch'}
        if($taskRecoveryError){throw $taskRecoveryError}
    }
}
