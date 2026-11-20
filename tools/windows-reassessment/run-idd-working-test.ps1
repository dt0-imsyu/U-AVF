param([ValidateRange(210,1800)][int]$ObserverSeconds=900,[switch]$RealDesktop,[switch]$ReconnectSource,[switch]$PreflightOnly,[ValidatePattern('^[a-z0-9-]{1,32}$')][string]$RunTag='initial',[ValidateSet(22,24,25,26,27,28,29)][int]$DesktopAttempt=22)
$ErrorActionPreference='Stop'
$python='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$prepared='E:\U-AVF\artifacts\idd-x264-prepared-20261014'
$encoder='E:\U-AVF\artifacts\idd-x264-encoder-prepared-20261014'
$base='E:\U-AVF\artifacts\idd-x264-run-20261014'
$codec='E:\U-AVF\artifacts\idd-x264-fat-readonly-plan3-20261014'
$codecReportPath='E:\U-AVF\artifacts\idd-x264-fat-applied-20261014\report.json'
$stageReportPath='E:\U-AVF\artifacts\idd-x264-stage-update-20261014\report.json'
$stageSha='8e6a2860d0d38d612a45d8e30d101c7be7a4f0eaa543557cc9ed2b789456f957'
$motionReportPath='E:\U-AVF\artifacts\idd-d3d-flip-motion-update-20261014\report.json'
$motionSha='7f80e85cbabb15bc74b9251c107a216625a5594e93cb0dea3868c377cb5161dd'
$observerName='kd-windows-idd-x264-20261014'
if($RealDesktop){
    $prepared='E:\U-AVF\artifacts\idd-real-desktop-prepared-20261014'
    $encoder='E:\U-AVF\artifacts\idd-real-desktop-encoder-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-real-desktop-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-real-desktop-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-real-desktop-stage-update2-20261014\report.json'
    $stageSha='1d65f99b341be198671c9abf895b4d3a5612d27fefea7d52e9208a8d5eff69c1'
    $motionReportPath='E:\U-AVF\artifacts\idd-real-desktop-motion-update-20261014\report.json'
    $motionSha='86c45c9c87416d374ab2b40aa309f66734cb0dc24ba00f2e4dccbb479c96cc8f'
    $observerName='kd-windows-idd-real-desktop-20261014'
}
if($ReconnectSource){
    if(!$RealDesktop){throw 'Reconnect source requires the same real Desktop gate'}
    $prepared='E:\U-AVF\artifacts\idd-reconnect-prepared-20261014'
    $encoder='E:\U-AVF\artifacts\idd-reconnect-encoder-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-reconnect-desktop-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-reconnect-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-reconnect-stage-update-20261014\report.json'
    $stageSha='46a352cac37f30964e8f6cc285076bcffcd7e34fcc1d6ef1149e56a043fd85ab'
    $observerName='kd-windows-idd-reconnect-desktop-20261014'
}
if($RunTag -ne 'initial'){$base+='-'+$RunTag;$observerName+='-'+$RunTag}
if($ReconnectSource -and $RunTag -eq 'port-change'){$codecReportPath='E:\U-AVF\artifacts\idd-reconnect-port-change-codec-applied-20261014\report.json'}
if($DesktopAttempt -eq 24){
    if(!$RealDesktop -or !$ReconnectSource -or $RunTag -ne 'initial'){throw 'Attempt24 is one fresh marker for the same interrupted Desktop proof'}
    $prepared='E:\U-AVF\artifacts\idd-desktop-attempt24-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-desktop-attempt24-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-desktop-attempt24-codec-applied-20261014\report.json'
    $observerName='kd-windows-idd-desktop-attempt24-20261014'
}
if($DesktopAttempt -eq 25){
    if(!$RealDesktop -or !$ReconnectSource -or $RunTag -ne 'initial'){throw 'Attempt25 is the scoped interrupted-payload recovery proof'}
    $prepared='E:\U-AVF\artifacts\idd-desktop-attempt25-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-desktop-attempt25-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-desktop-attempt25-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-desktop-attempt25-stage-20261014\report.json'
    $stageSha='dc279fcb036db5f52227b846cbfc82f1bfafbb3e8b7bdb7014a69144eba598e7'
    $observerName='kd-windows-idd-desktop-attempt25-20261014'
}
if($DesktopAttempt -eq 26){
    if(!$RealDesktop -or !$ReconnectSource -or $RunTag -notin @('initial','after-control')){throw 'Active proof requires the same primary Desktop contract'}
    $prepared='E:\U-AVF\artifacts\idd-active-desktop-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-active-desktop-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-active-desktop-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-active-desktop-stage-20261014\report.json'
    $stageSha='d3149ce1b54deefdd53ee20bc934f444acbc2c75725e49f09e4d0ed7d263c1e0'
    $observerName='kd-windows-idd-active-desktop-20261014'
    if($RunTag -eq 'after-control'){
        $base+='-after-control';$observerName+='-after-control'
        $codecReportPath='E:\U-AVF\artifacts\idd-active-after-control-codec-applied-20261014\report.json'
    }
}
if($DesktopAttempt -eq 27){
    if(!$RealDesktop -or !$ReconnectSource -or $RunTag -ne 'initial'){throw 'One finite monitor-readiness test only'}
    $prepared='E:\U-AVF\artifacts\idd-readiness-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-readiness-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-readiness-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-readiness-stage-update-20261014\report.json'
    $stageSha='815ea94ba20d5159f0cdbda0f3b3b284d74709c078db5a94ab99f79d91b25223'
    $motionReportPath='E:\U-AVF\artifacts\idd-readiness-motion-update-20261014\report.json'
    $motionSha='f4d6edd45631c6166002520fdbbc1ca79626a40cb6afba9a711a831c407aff51'
    $observerName='kd-windows-idd-monitor-readiness-20261014'
}
if($DesktopAttempt -in @(28,29)){
    if(!$RealDesktop -or !$ReconnectSource -or $RunTag -ne 'initial'){throw 'One finite longer ordinary-workload aperture only'}
    $prepared='E:\U-AVF\artifacts\idd-long-workload-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-long-workload-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-long-workload-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-long-workload-stage-update-20261014\report.json'
    $stageSha='9a55748378818a55130a30edbde9588c8c3fbc6e89b1e90557872199b10bbfbb'
    $motionReportPath='E:\U-AVF\artifacts\idd-long-workload-motion-update-20261014\report.json'
    $motionSha='7da58079674c0bd417ab54159506d3185892b4590931b6d4465981bf3608c554'
    $observerName='kd-windows-idd-long-workload-20261014'
}
if($DesktopAttempt -eq 29){
    $prepared='E:\U-AVF\artifacts\idd-continuous-workload-prepared-20261014'
    $base='E:\U-AVF\artifacts\idd-continuous-workload-run-20261014'
    $codecReportPath='E:\U-AVF\artifacts\idd-continuous-workload-codec-applied-20261014\report.json'
    $stageReportPath='E:\U-AVF\artifacts\idd-continuous-workload-stage-update-20261014\report.json'
    $stageSha='ff69b597ac05a512efdec5dec025349c244f3dad80822c2d50f53c7f05c98e00'
    $observerName='kd-windows-idd-continuous-workload-20261014'
}
$crashArgs=@(if(($DesktopAttempt -eq 26 -and $RunTag -eq 'after-control') -or $DesktopAttempt -in @(27,28,29)){'--observed-native-crash'})
if(!$PreflightOnly){
$codecReport=Get-Content $codecReportPath -Raw|ConvertFrom-Json
if($codecReport.result -ne 'SEPARATE_CODEC_APPLY_READBACK_PASS' -or $codecReport.candidate_sha256 -ne '0463f4b9d081cd6f921837fbe27e9d03d29cd848981a9c3654113621ef55d129'){throw 'Exact x264 delivery must be complete before boot'}
& $python "$PSScriptRoot/verify-staged-native-x264.py" --adb $adb --prepared $codec @crashArgs
if($LASTEXITCODE){throw 'Current x264 payload missing/changed; historical report is not admission. No helpers written, no VM boot.'}
}
$staging=Get-Content 'E:\U-AVF\artifacts\idd-working-fat-apply2-20261013\report.json' -Raw|ConvertFrom-Json
if(!$staging.applied -or $staging.driver_loaded){throw 'Delivery must have completed with verified cold readback before boot'}
$update=Get-Content $stageReportPath -Raw|ConvertFrom-Json
if(!$update.applied -or $update.new_sha -ne $stageSha){throw 'Finite x264 delivery must be verified'}
$motionUpdate=Get-Content $motionReportPath -Raw|ConvertFrom-Json
if(!$motionUpdate.applied -or $motionUpdate.new_sha -ne $motionSha){throw 'Exact desktop/motion helper readback missing'}
$packageGuards=@(@('driver','b15d9f4c840f3eb8569daa6181136bab37bb3f270933822e83f9920b4f0aff5a','idd-pipe-driver-update2-20261014'),@('catalog','4b649830bf0b0a44cb55547daeaa38948829142fecde49bda778e884a48e7707','idd-pipe-catalog-update2-20261014'),@('reader','3775f62b007bb61af8cf70f1d6d9ea981a803870aad342bdd802e3a2ab7b721c','idd-pipe-reader-update2-20261014'))
if($ReconnectSource){
    $packageGuards[0][1]='42f1d3f3dbc86afe079e3aa6ebc0672dcf26b9a976dda6574fc612d5138ee29c';$packageGuards[0][2]='idd-headless-driver-update-20261014'
    $packageGuards[1][1]='a484f851a6498eb90bf941d0a1a48b959f30d840c8f982f8889747fe2d223f1f';$packageGuards[1][2]='idd-headless-catalog-update-20261014'
    $packageGuards[2][1]='7dfcc6b28776654a70931b08fafffdc6b9de36b91e67442ebb9f787e3fd352fa';$packageGuards[2][2]='idd-headless-reader-update-20261014'
}
foreach($part in $packageGuards){
    $r=Get-Content "E:\U-AVF\artifacts\$($part[2])\report.json" -Raw|ConvertFrom-Json
    if(!$r.applied -or $r.new_sha -ne $part[1]){throw 'Timing package readback missing'}
}
$testPath=if($DesktopAttempt -eq 27){'E:\U-AVF\artifacts\idd-readiness-test-update-20261014\report.json'}else{'E:\U-AVF\artifacts\idd-source-only-test-update-20261014\report.json'}
$testSha=if($DesktopAttempt -eq 27){'c9cedd3a4f7f1ca166b73334f52cc660ba690c46031cca43f7a7a2b02d0ec43c'}else{'50ef014c20689ae5a5f3668c1982c622e06ee79c82776eb48d1be83d67e6f4f9'}
if($DesktopAttempt -in @(28,29)){$testPath='E:\U-AVF\artifacts\idd-long-workload-test-update-20261014\report.json';$testSha='3458a34a12622600a725295f9f47882a0583577eb990ca76452f1db45ff97acb'}
$testUpdate=Get-Content $testPath -Raw|ConvertFrom-Json
if(!$testUpdate.applied -or $testUpdate.new_sha -ne $testSha){throw 'Admission script must be verified'}
$helperUpdate=Get-Content 'E:\U-AVF\artifacts\idd-pnp-wait-device-update-20261013\report.json' -Raw|ConvertFrom-Json
if(!$helperUpdate.applied -or $helperUpdate.new_sha -ne 'edee1dfff5acaf0dc61ab2085dfad1c8c2bb10546a21886cf07d04c1f7a59f64'){throw 'Bounded PnP wait helper must be verified'}
if($DesktopAttempt -in @(28,29)){
    $h=Get-Content 'E:\U-AVF\artifacts\idd-long-workload-device-update-20261014\report.json' -Raw|ConvertFrom-Json
    if(!$h.applied -or $h.new_sha -ne '93cfd28264deea6ef22203d4cd5d8fe85b161ea32e807e1260c340fb276fde42'){throw 'Longer finite device lifetime readback missing'}
}
if($ReconnectSource){
    $socketPlan=Get-Content "$prepared/report.json" -Raw|ConvertFrom-Json
    $encoderPlan=Get-Content "$encoder/report.json" -Raw|ConvertFrom-Json
    $socketSha=if($DesktopAttempt -eq 24){'98c5df7097ef62d3e9bdc4b9ee5ef812db081439b0e72885dc5703b18f8399b4'}else{'f93ddbc7da6844c3d3c314a868dbdd5212764e2cffa2c75a651c95202800e61d'}
    if($DesktopAttempt -eq 25){$socketSha='09666970a0139deab9bf596d47b95d737c66fec4d2b668306f6075278a5e7ba5'}
    if($DesktopAttempt -eq 26){$socketSha='680240a55fc9b827189493f2ed33beaf764cba68905c199a61dc7e7424c42604'}
    if($DesktopAttempt -eq 27){$socketSha='7c8b47f5d46f2191b08cb1bd676cfdf53a641575e2ad2cd82a57341f522a74cb'}
    if($DesktopAttempt -eq 28){$socketSha='ca96347c565f68c15ea5e17828b365fc6934340164a445aa317046e6601da1d1'}
    if($DesktopAttempt -eq 29){$socketSha='51d8c67c057261c4b3140631435c24c1d9c38ebec9950041c64f1a7d5a6ea43d'}
    if($socketPlan.result -ne 'OWNED_VSOCK_BINARY_PREFLIGHT_PASS' -or $socketPlan.changed_vsock_sha256 -ne $socketSha){throw 'Wrong Desktop-only launcher plan'}
    if($encoderPlan.result -ne 'OWNED_ENCODER_BINARY_PREFLIGHT_PASS' -or $encoderPlan.changed_encoder_sha256 -ne '7aec1f3a33f051c0d34f74b64df0f89262137110a9617992d3c0013b3ac93a8d'){throw 'Encoder must pin the exact reconnect-tested driver'}
    if((Get-FileHash "$prepared/winpe-vsock-observation-slot.exe").Hash.ToLower() -ne $socketPlan.changed_vsock_sha256 -or (Get-FileHash "$encoder/winpe-encode-observation-slot.exe").Hash.ToLower() -ne $encoderPlan.changed_encoder_sha256){throw 'Prepared helper bytes changed'}
}
if($PreflightOnly){
    'MATCHED_ARTIFACT_PREFLIGHT_PASS; NO_HELPER_WRITES_OR_VM_BOOT; DESKTOP_AND_FPS_UNPROVEN'
    & $python "$PSScriptRoot/verify-staged-native-x264.py" --adb $adb --prepared $codec @crashArgs
    if($LASTEXITCODE -eq 2){'CODEC_STAGING_REQUIRED_BEFORE_REAL_DESKTOP_BOOT';exit 2}
    if($LASTEXITCODE){throw 'Read-only current-device admission failed'}
    exit 0
}
if(Test-Path $base){throw 'Preserve previous run'}
New-Item -ItemType Directory $base|Out-Null
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$candidate=Join-Path $workspace 'build-logs/windows-post-linux-reassessment-20261004/smp-ram-gop-handoff-audit-preflight2-20261011/candidate-BOOTAA64.EFI'
$previous=@{};$socketApplied=$false;$gopApplied=$false;$encoderApplied=$false
try {
    & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $encoder --prepared $encoder --output "$base/encoder-applied" --encoder-only --native-gop --apply
    if($LASTEXITCODE){throw 'Own IDD encoder apply failed'};$encoderApplied=$true
    & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $prepared --prepared $prepared --output "$base/socket-applied" --vsock-only --native-gop --apply
    if($LASTEXITCODE){throw 'One-off own socket launcher apply failed'};$socketApplied=$true
    # Keep already-present exact proven native1200p handoff byte-identical.
    foreach($key in @('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')){
        $name='UAVF_KD_'+$key;$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,'1','Process')
    }
    foreach($name in @('UAVF_WINDOWS_RAM_GOP','UAVF_KD_PSCI_POST_START_MINIMAL')){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
    $env:UAVF_WINDOWS_RAM_GOP='1';[Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
    $previous['UAVF_IDD_SOURCE_ONLY']=[Environment]::GetEnvironmentVariable('UAVF_IDD_SOURCE_ONLY','Process')
    [Environment]::SetEnvironmentVariable('UAVF_IDD_SOURCE_ONLY',$null,'Process')
    & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName $observerName -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds $ObserverSeconds -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus
    if($LASTEXITCODE){throw 'Working test observer failed; preserve actual guest evidence'}
} finally {
    foreach($name in $previous.Keys){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
    $cold=((& $adb shell /apex/com.android.virt/bin/vm list)-join "`n") -match 'Running VMs: \[\]'
    if(!$cold){
        'VM_RETAINED; COLD_HELPER_AND_GOP_ROLLBACK_DEFERRED; NOT_DRIVER_OR_FPS_PASS'|Set-Content "$base/live-retained.txt"
    } else {
    # Owned binary independent of FAT bookkeeping: recover even if GOP rollback refuses.
    if($socketApplied){
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $prepared --prepared $prepared --output "$base/socket-restored" --vsock-only --native-gop --rollback --apply
        if($LASTEXITCODE){throw 'Own launcher restore failed; do not reboot'}
    }
    if($encoderApplied){
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $encoder --prepared $encoder --output "$base/encoder-restored" --encoder-only --native-gop --rollback --apply
        if($LASTEXITCODE){throw 'Own encoder restore failed; do not reboot'}
    }
    & $python "$PSScriptRoot/apply-eight-thread-codec.py" --adb $adb --prepared $codec --output "$base/codec-restored" --codec x264 --native-gop --rollback --apply @crashArgs
    if($LASTEXITCODE){
        if(!$RealDesktop){throw 'Exact x264 temporary codec rollback failed; do not reboot'}
        # No generic filesystem repair. Accept only old/candidate bytes and the
        # two previously audited COMPLETE native sector hashes; reject all else.
        & $python "$PSScriptRoot/recover-eight-thread-codec.py" --adb $adb --prepared $codec --output "$base/codec-recovery-audit" --native-gop --observed-native-metadata @crashArgs
        if($LASTEXITCODE){throw 'Unknown FAT preimage; no recovery writes or reboot allowed'}
        & $python "$PSScriptRoot/recover-eight-thread-codec.py" --adb $adb --prepared $codec --output "$base/codec-recovered" --native-gop --observed-native-metadata --apply @crashArgs
        if($LASTEXITCODE){throw 'Exact codec recovery not complete; do not reboot'}
    }
    if($gopApplied){
        & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared "$base/gop-plan" --output "$base/gop-restored" --rollback --apply
        if($LASTEXITCODE){throw 'GOP bookkeeping changed during guest runtime; no force-overwrite. Proven GOP retained; inspect transaction'}
    }
    }
}
