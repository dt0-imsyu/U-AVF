$ErrorActionPreference='Stop'
$previous=@{}
# Exact previously proven eight-CPU boot; baseline helpers, no display staging.
# Only the observer adds bounded read-only data at an already-occurring crash.
try {
    foreach($key in @('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')){
        $name='UAVF_KD_'+$key;$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,'1','Process')
    }
    foreach($name in @('UAVF_WINDOWS_RAM_GOP','UAVF_KD_PSCI_POST_START_MINIMAL','UAVF_IDD_SOURCE_ONLY')){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
    $env:UAVF_WINDOWS_RAM_GOP='1'
    [Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
    [Environment]::SetEnvironmentVariable('UAVF_IDD_SOURCE_ONLY',$null,'Process')
    & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName kd-windows-runtime-ipi-observation-20261014 -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds 600 -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus
    if($LASTEXITCODE){throw 'Inspect bounded runtime observation; no retry loop'}
} finally {
    foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}
