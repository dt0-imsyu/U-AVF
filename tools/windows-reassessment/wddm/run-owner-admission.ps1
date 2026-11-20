[CmdletBinding()]
param([Parameter(Mandatory)][string]$StagingReport,[switch]$Codec,[switch]$Pnp,
      [ValidatePattern('^[a-zA-Z0-9-]+$')][string]$EvidenceName = 'wddm-admission-r1')

$ErrorActionPreference = 'Stop'
$report = Get-Content -LiteralPath $StagingReport -Raw | ConvertFrom-Json
if ($Pnp) {
    if ($report.mode -ne 'applied' -or $report.new_sha -ne '3D81FEEA37CC742FD55389FDA2E7DDC02A750A5469A49C8A639243CA8C13D6FA') {
        throw 'Expected verified WDDM PnP EFI replacement on the diagnostic disk'
    }
} elseif (!$report.applied -or $report.scope -notmatch '^One new (WDDMTEST|WDDMTS[2-9]) folder on the isolated diagnostic Windows ESP$') {
    throw 'Expected applied and verified WDDM staging report'
}
$env:UAVF_MAVK_BRIDGE_OPTIN = '1'
$env:UAVF_WDDM_ADMISSION = '1'
$env:UAVF_WDDM_CODEC = if($Codec){'1'}else{'0'}
$env:UAVF_WDDM_PNP = if($Pnp){'1'}else{'0'}
$env:UAVF_WINDOWS_RAM_GOP = '1'
foreach ($name in 'PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT',
                  'PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER',
                  'PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT',
                  'PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB') {
    [Environment]::SetEnvironmentVariable("UAVF_KD_$name", '1', 'Process')
}
& "$PSScriptRoot/../android-owner/run-service-test.ps1" -EvidenceName $EvidenceName `
    -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus -ResumeWindowsInstallTarget `
    -HoldRuntime -EmulateUserPmcc -QuietDriverNotifications -ObserverSeconds 480 -KeepRunning
