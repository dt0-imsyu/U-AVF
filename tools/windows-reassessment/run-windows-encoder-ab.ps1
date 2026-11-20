# Capture unchanged; native1200p/8CPU contract unchanged. Always restore both.
param([switch]$LiveDiagnostics,[switch]$DirectCapture,
    [ValidateSet('direct-dxgi-timed')][string]$CaseTag='',
    [ValidateRange(210,900)][int]$ObserverSeconds=300)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$base=Join-Path $root 'build-logs\windows-post-linux-reassessment-20261004'
$python='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if($DirectCapture -and !$LiveDiagnostics){throw 'Direct capture needs proven live diagnostics relay'}
$caseName=if($DirectCapture){'direct-dxgi'}elseif($LiveDiagnostics){'live-diagnostics'}else{'threads-dxgi'}
if($CaseTag){if(!$DirectCapture -or !$LiveDiagnostics){throw 'Timed case requires exact direct capture contract'};$caseName=$CaseTag}
$encoder=Join-Path $base "windows-encode-$caseName-prepared-20261011"
if($CaseTag -eq 'direct-dxgi-timed'){$encoder=Join-Path $base 'windows-encode-direct-dxgi-timed-final-prepared-20261011'}
$vsock=Join-Path $base 'windows-vsock-live-diagnostics-prepared-20261011'
$gop=Join-Path $base 'smp-ram-gop-handoff-audit-preflight2-20261011'
$encoderApplied=$false;$gopApplied=$false;$vsockApplied=$false
try {
    & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $encoder --prepared $encoder --output (Join-Path $base "windows-encode-$caseName-applied-20261011") --encoder-only --apply
    if($LASTEXITCODE){throw 'Own encoder update failed'}
    $encoderApplied=$true
    if($LiveDiagnostics) {
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $vsock --prepared $vsock --output (Join-Path $base "windows-vsock-$caseName-applied-20261011") --vsock-only --apply
        if($LASTEXITCODE){throw 'Own vsock helper apply failed'}
        $vsockApplied=$true
    }
    & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared $gop --output (Join-Path $base "smp-encode-$caseName-gop-applied-20261011") --apply
    if($LASTEXITCODE){throw 'Known1200p GOP apply failed'}
    $gopApplied=$true
    foreach($envKey in @('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')) {
        [Environment]::SetEnvironmentVariable('UAVF_KD_'+$envKey,'1','Process')
    }
    [Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
    $env:UAVF_WINDOWS_RAM_GOP='1'
    & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName "kd-windows-encode-$caseName-20261011" -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds $ObserverSeconds -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus
} finally {
    if($gopApplied) {
        & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared $gop --output (Join-Path $base "smp-encode-$caseName-gop-rollback-20261011") --rollback --apply
        if($LASTEXITCODE){throw 'GOP rollback failed; do not relaunch or modify NTFS'}
    }
    if($vsockApplied) {
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $vsock --prepared $vsock --output (Join-Path $base "windows-vsock-$caseName-rollback-20261011") --vsock-only --rollback --apply
        if($LASTEXITCODE){throw 'Own vsock rollback failed'}
    }
    if($encoderApplied) {
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $encoder --prepared $encoder --output (Join-Path $base "windows-encode-$caseName-rollback-20261011") --encoder-only --rollback --apply
        if($LASTEXITCODE){throw 'Encoder rollback failed; do not relaunch'}
    }
}
