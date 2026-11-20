# One bounded isolated display-handle A/B; exact EFI/FAT rollback mandatory.
param([switch]$HandoffAudit,[ValidateRange(1,2)][int]$Attempt=1)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$python='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$base=Join-Path $root 'build-logs\windows-post-linux-reassessment-20261004'
$name=if($HandoffAudit){'handoff-audit'}else{'devicepath'}
$preflight=if($HandoffAudit){'preflight2'}else{'preflight'}
$prepared=Join-Path $base "smp-ram-gop-$name-$preflight-20261011"
if($Attempt -gt 1){$name+="-$Attempt"}
$seconds=if($HandoffAudit){210}else{300}
$applied=$false
try {
    & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared $prepared --output (Join-Path $base "smp-ram-gop-$name-applied-20261011") --apply
    if($LASTEXITCODE){throw 'Typed EFI transaction failed'}
    $applied=$true
    foreach($envKey in @('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')) {
        [Environment]::SetEnvironmentVariable('UAVF_KD_'+$envKey,'1','Process')
    }
    [Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
    $env:UAVF_WINDOWS_RAM_GOP='1'
    & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName "kd-windows-ram-gop-$name-20261011" -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds $seconds -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus
} finally {
    if($applied) {
        & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared $prepared --output (Join-Path $base "smp-ram-gop-$name-rollback-20261011") --rollback --apply
        if($LASTEXITCODE){throw 'MANDATORY EFI rollback failed; do not relaunch'}
    }
}
