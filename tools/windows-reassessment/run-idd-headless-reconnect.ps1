param([ValidateRange(210,600)][int]$ObserverSeconds=240)
$ErrorActionPreference='Stop'
$python='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$prepared='E:\U-AVF\artifacts\idd-headless-socket-prepared-20261014'
$base='E:\U-AVF\artifacts\idd-headless-reconnect-run-20261014'
$guards=@(
 @('driver','42f1d3f3dbc86afe079e3aa6ebc0672dcf26b9a976dda6574fc612d5138ee29c'),
 @('catalog','a484f851a6498eb90bf941d0a1a48b959f30d840c8f982f8889747fe2d223f1f'),
 @('reader','7dfcc6b28776654a70931b08fafffdc6b9de36b91e67442ebb9f787e3fd352fa'),
 @('stage','68f68efc97867ea03ecfbd322b0fd404311ac33def30c90103ab5eebbcd251e3'))
foreach($g in $guards){
 $r=Get-Content "E:\U-AVF\artifacts\idd-headless-$($g[0])-update-20261014\report.json" -Raw|ConvertFrom-Json
 if(!$r.applied -or $r.new_sha -ne $g[1]){throw 'Exact headless payload delivery not verified'}
}
$r=Get-Content "$prepared/report.json" -Raw|ConvertFrom-Json
if($r.result -ne 'OWNED_VSOCK_BINARY_PREFLIGHT_PASS' -or $r.changed_vsock_sha256 -ne 'f8df40f3de0ea8a8fde625b8143dfaef6a10838a38f206291d39b3f2011ce5df'){throw 'Wrong owned launcher preparation'}
if(Test-Path -LiteralPath $base){throw 'Preserve previous experiment'}
New-Item -ItemType Directory -Path $base|Out-Null
$previous=@{};$applied=$false
try {
 & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $prepared --prepared $prepared --output "$base/socket-applied" --vsock-only --native-gop --apply
 if($LASTEXITCODE){throw 'Owned launcher apply refused'};$applied=$true
 foreach($key in @('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')){
  $name='UAVF_KD_'+$key;$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,'1','Process')
 }
 foreach($name in @('UAVF_WINDOWS_RAM_GOP','UAVF_KD_PSCI_POST_START_MINIMAL','UAVF_IDD_SOURCE_ONLY')){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
 $env:UAVF_WINDOWS_RAM_GOP='1'
 [Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
 [Environment]::SetEnvironmentVariable('UAVF_IDD_SOURCE_ONLY',$null,'Process')
 & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName kd-windows-idd-headless-reconnect-20261014 -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds $ObserverSeconds -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus
 if($LASTEXITCODE){throw 'Observer ended without PASS; inspect actual guest evidence'}
} finally {
 foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
 $cold=((& $adb shell /apex/com.android.virt/bin/vm list)-join "`n") -match 'Running VMs: \[\]'
 if(!$cold){Write-Warning 'VM retained: cold launcher rollback deferred'}
 elseif($applied){
  & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $prepared --prepared $prepared --output "$base/socket-restored" --vsock-only --native-gop --rollback --apply
  if($LASTEXITCODE){throw 'Owned launcher rollback refused; no further launch'}
 }
}
