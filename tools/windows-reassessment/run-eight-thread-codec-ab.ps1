# Single isolated codec A/B on the unchanged proven8CPU/native1200p contract.
# Separate DLL first, freshly derived GOP preimages after it (shared FAT sector).
param([ValidateRange(210,900)][int]$ObserverSeconds=300,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunTag='initial',
    [ValidateSet('openh264','x264')][string]$Codec='openh264',
    [switch]$DxgiLateRelease,[switch]$NativeCaptureWorkload,[switch]$OverlappedCapture,[switch]$GdiOnlyCapture,[switch]$CaptureCpuAudit,[switch]$StagingReadback,[switch]$AsyncStaging,[switch]$PollAudit,[switch]$HighResPoll,[switch]$ReleasePhaseAudit,[switch]$FramePacingFix,[switch]$RamFramebufferAudit,[switch]$SharedPrimaryAudit,[switch]$WindowsClipboardProbe,[switch]$WgcSourceProbe)
$ErrorActionPreference='Stop'
if($WgcSourceProbe -and ($Codec -ne 'x264' -or $RunTag -eq 'initial' -or $WindowsClipboardProbe -or $DxgiLateRelease -or $NativeCaptureWorkload -or $OverlappedCapture -or $GdiOnlyCapture -or $CaptureCpuAudit -or $StagingReadback -or $AsyncStaging -or $PollAudit -or $HighResPoll -or $ReleasePhaseAudit -or $FramePacingFix -or $RamFramebufferAudit -or $SharedPrimaryAudit)){throw 'WGC source-only probe requires unchanged named x264 control'}
if($WindowsClipboardProbe -and ($Codec -ne 'x264' -or $RunTag -eq 'initial' -or $DxgiLateRelease -or $NativeCaptureWorkload -or $OverlappedCapture -or $GdiOnlyCapture -or $CaptureCpuAudit -or $StagingReadback -or $AsyncStaging -or $PollAudit -or $HighResPoll -or $ReleasePhaseAudit -or $FramePacingFix -or $RamFramebufferAudit -or $SharedPrimaryAudit)){throw 'Clipboard proof uses original display control only'}
if($RamFramebufferAudit -and (!$NativeCaptureWorkload -or !$CaptureCpuAudit -or $StagingReadback -or $OverlappedCapture -or $FramePacingFix)){throw 'Framebuffer audit uses the exact original native cadence control, no new source candidate'}
if($SharedPrimaryAudit -and ($Codec -ne 'x264' -or !$NativeCaptureWorkload -or !$CaptureCpuAudit -or $StagingReadback -or $OverlappedCapture -or $RamFramebufferAudit)){throw 'Shared primary query requires exact original cadence control, no other A/B'}
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$base=Join-Path $root 'build-logs\windows-post-linux-reassessment-20261004'
$codecBase=Join-Path $root 'build-logs\openh264-windows-eight-thread-20261012'
if($Codec -eq 'x264'){$codecBase=Join-Path $root 'build-logs\windows-x264-20261012'}
$python='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$prepared=Join-Path $codecBase 'fat-codec-readonly-plan'
$encoder=Join-Path $codecBase 'encoder-eight-prepared'
if($Codec -eq 'x264'){$prepared=Join-Path $codecBase 'fat-codec-plan';$encoder=Join-Path $codecBase 'encoder-prepared'}
if($DxgiLateRelease) {
    if($Codec -ne 'x264' -or $RunTag -eq 'initial'){throw 'Named isolated x264 source A/B required'}
    $encoder=Join-Path $codecBase 'encoder-late-release-prepared'
}
if($NativeCaptureWorkload) {
    if($Codec -ne 'x264' -or $RunTag -eq 'initial' -or $DxgiLateRelease){throw 'Named x264 native workload on original capture contract required'}
    $encoder=Join-Path $codecBase 'encoder-native-workload-prepared'
}
if($OverlappedCapture) {
    if(!$NativeCaptureWorkload){throw 'Controlled native workload comparison required'}
    $encoder=Join-Path $codecBase 'encoder-overlap-prepared'
}
if($GdiOnlyCapture) {
    if(!$NativeCaptureWorkload -or $OverlappedCapture -or $DxgiLateRelease){throw 'Controlled original GDI source comparison required'}
    $encoder=Join-Path $codecBase 'encoder-gdi-prepared'
}
if($CaptureCpuAudit) {
    if(!$NativeCaptureWorkload -or $OverlappedCapture -or $GdiOnlyCapture -or $DxgiLateRelease){throw 'Cadence audit requires original DXGI native workload'}
    $encoder=Join-Path $codecBase 'encoder-cadence-audit-prepared'
}
if($StagingReadback) {
    if(!$CaptureCpuAudit){throw 'Staging readback requires audited original DXGI control'}
    $encoder=Join-Path $codecBase 'encoder-staging-prepared'
}
if($AsyncStaging) {
    if(!$StagingReadback){throw 'Async readback requires staged audit contract'}
    $encoder=Join-Path $codecBase 'encoder-async-staging-prepared'
}
if($PollAudit) {
    if(!$AsyncStaging){throw 'Poll observation requires exact async control'}
    $encoder=Join-Path $codecBase 'encoder-async-poll-prepared'
}
if($HighResPoll) {
    if(!$PollAudit){throw 'Private timer comparison requires measured poll audit'}
    $encoder=Join-Path $codecBase 'encoder-private-poll-prepared'
}
if($ReleasePhaseAudit) {
    if(!$HighResPoll){throw 'Release phase observation requires private poll control'}
    $encoder=Join-Path $codecBase 'encoder-release-phase-prepared'
}
if($FramePacingFix) {
    if(!$ReleasePhaseAudit){throw 'Frame cap correction requires exact release-phase control'}
    $encoder=Join-Path $codecBase 'encoder-frame-pacing-prepared'
}
if($SharedPrimaryAudit){$encoder=Join-Path $codecBase 'encoder-shared-primary-prepared'}
if($WgcSourceProbe){$encoder='E:\U-AVF\artifacts\windows-wgc-user-only-20261013\cold-plan'}
$vsock=Join-Path $base 'windows-vsock-live-diagnostics-prepared-20261011'
if($WindowsClipboardProbe){
    $vsock='E:\U-AVF\artifacts\windows-clipboard-worker-status-verified-20261012\cold-plan'
    if((Get-FileHash -LiteralPath (Join-Path $vsock 'winpe-vsock-observation-slot.exe') -Algorithm SHA256).Hash -ne 'F0190C62DA3596C37A995111A02F1A698B72472664AAB3021F21C4BF6436BD9E'){throw 'Exact optional clipboard server required'}
}
$gop=Join-Path $codecBase 'gop-after-codec-prepared'
$gopBinary=Join-Path $base 'smp-ram-gop-handoff-audit-build2-20261011\uart-gas8-gic-ds-ebs.efi'
$evidenceName='kd-windows-direct-eight-codec-20261012'
if($Codec -eq 'x264'){$evidenceName='kd-windows-direct-x264-20261012'}
$runBase=$codecBase
if($RunTag -ne 'initial') {
    $runBase=Join-Path $codecBase ('runtime-'+$RunTag)
    $gop=Join-Path $runBase 'gop-after-codec-prepared'
    $evidenceName+='-'+$RunTag
    if(Test-Path -LiteralPath $runBase){throw 'Preserve previous run evidence'}
}
if((Get-PSDrive C).Free -lt 50GB){throw 'Reserve below50GiB; no staging or launch'}
$state=& $adb shell /apex/com.android.virt/bin/vm list
if($LASTEXITCODE -or ($state -join "`n") -notmatch 'Running VMs: \[\]'){throw 'Preserve existing VM'}
# Mirror the owner admission BEFORE any staging, not after minutes of writes.
$availableText=& $adb shell "run-as com.example.uavfwinpetest stat -f -c '%a %S' files"
if($LASTEXITCODE){throw 'Cannot verify tablet reserve'}
$availableParts=($availableText -join '').Trim() -split '\s+'
if($availableParts.Count -ne 2 -or ([long]$availableParts[0]*[long]$availableParts[1]) -lt 52GB){throw 'Tablet owner reserve below52GiB; zero staging writes'}
$policy=& $adb shell dumpsys window policy
if($LASTEXITCODE -or $policy -match 'showing=true|screenState=SCREEN_STATE_OFF'){throw 'Unlock tablet; no bypass'}
$battery=& $adb shell dumpsys battery
$level=($battery | Select-String '^\s*level:\s*(\d+)\s*$' | Select-Object -First 1).Matches.Groups[1].Value
if(!$level -or [int]$level -lt 10){throw 'Battery below10%; no launch'}
foreach($name in @('codec-applied','encoder-applied','vsock-applied','gop-after-codec-prepared','gop-applied','gop-rollback','vsock-rollback','encoder-rollback','codec-rollback')) {
    if(Test-Path -LiteralPath (Join-Path $runBase $name)){throw "Preserve previous evidence: $name"}
}
if(Test-Path -LiteralPath (Join-Path $base $evidenceName)){throw 'Preserve previous runtime evidence'}
# Actual verifier and EVERY partial-failure position must pass before mutation.
& $python "$PSScriptRoot/test-eight-thread-codec-transaction.py" --prepared $prepared --codec $Codec
if($LASTEXITCODE){throw 'Codec transaction test failed'}
$encoderSha=if($Codec -eq 'x264'){'C4E5574202B106E830402F27FC7F729E376A1EBDE0BBAF8249D1D5D070E688E5'}else{'1A17C5F7F7D8CEF5C12DA837652CA869870B9B6E041B8FBACC033BC20101289B'}
if($DxgiLateRelease){$encoderSha='88FB05320C9C3164352889EB4B41CBF904851A91C6AC421E67FD587271334381'}
if($NativeCaptureWorkload){$encoderSha='42F9B21719CF08DDFCDBBCDBA2CCB7DFA0C0BA72533CE6E57F16348206488010'}
if($OverlappedCapture){$encoderSha='BE57937E4BF63D57F1A712E329B79E6E624408214ECE2F4152CEA727531A0EB0'}
if($GdiOnlyCapture){$encoderSha='7BD95455FD4D1C946B487B66EB0A7F10459898A0C1997323A89D1C7E7D38D9B8'}
if($CaptureCpuAudit){$encoderSha='3A0D2EB206E7D1BBDD5ECEFDA271E75CC5199E8FFE6A88714D35C5F7F8B63848'}
if($StagingReadback){$encoderSha='F0F904452AD5DB851CD81DE4947DF362414DA8E426D0315723192C7DB3D72800'}
if($AsyncStaging){$encoderSha='4AC72BE8EE94319997CFD8771E88938D45A6D89918B2104A162C3779CEDD97CD'}
if($PollAudit){$encoderSha='A9CD4DC89BCE96746E130CA71D950DDFAB7132F265FD1E83C247807537CB64A4'}
if($HighResPoll){$encoderSha='6EF28A056C1F41A244CE84C96345A26EF86357C905DCBA5D66A7ACF524D73777'}
if($ReleasePhaseAudit){$encoderSha='D10BDEB018177EE109448C58C6B16DF73CC150C4EC6E1184A4D90FA126A5F35E'}
if($FramePacingFix){$encoderSha='4B678FA0347E0F6AFCD9B7AEA67922B1916F99AE5381AEE7E352E0052104754E'}
if($SharedPrimaryAudit){$encoderSha='A4F177EDC2E6B298742187F3057B54F0A467CF48A92004644BA5A0C4B54C1A02'}
if($WgcSourceProbe){$encoderSha='5FB45EB555E815947E03266ED4158C265806961D1A7D0044A9C15DD1DE16C282'}
if((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $encoder 'winpe-encode-observation-slot.exe')).Hash -ne $encoderSha){throw 'Exact experimental encoder required'}
$codecApplied=$false;$encoderApplied=$false;$vsockApplied=$false;$gopApplied=$false
$savedEnvironment=@{}
$keys=@('PSCI_CPU_ON','PSCI_RESERVE','PSCI_RUNTIME','PSCI_ZERO_CONTEXT','PSCI_ALL_CPUS','PSCI_PMU','PMU_COMMON_AB','PSCI_ENTRY_MARKER','PSCI_DEFER_RETURN','PSCI_PRE_START_ONLY','PSCI_STARTUP_WAIT','PSCI_CACHE_PUBLISH','PSCI_FINAL_STATE','PSCI_EXPLICIT_IPI_AB')
foreach($key in ($keys | ForEach-Object {'UAVF_KD_'+$_}) + @('UAVF_KD_PSCI_POST_START_MINIMAL','UAVF_WINDOWS_RAM_GOP','UAVF_KD_RAM_FRAMEBUFFER_AUDIT')) {
    $savedEnvironment[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
}
try {
    if($runBase -ne $codecBase){New-Item -ItemType Directory -Path $runBase | Out-Null}
    & $python "$PSScriptRoot/apply-eight-thread-codec.py" --adb $adb --prepared $prepared --output (Join-Path $runBase 'codec-applied') --codec $Codec --apply
    if($LASTEXITCODE){throw 'Separate codec apply failed'};$codecApplied=$true
    & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $encoder --prepared $encoder --output (Join-Path $runBase 'encoder-applied') --encoder-only --apply
    if($LASTEXITCODE){throw 'Own encoder apply failed'};$encoderApplied=$true
    & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $vsock --prepared $vsock --output (Join-Path $runBase 'vsock-applied') --vsock-only --apply
    if($LASTEXITCODE){throw 'Own vsock apply failed'};$vsockApplied=$true
    # Never use old GOP FAT-sector preimages after adding UAVF8.DLL.
    & $python "$PSScriptRoot/prepare-ram-gop-file.py" --adb $adb --candidate $gopBinary --output $gop
    if($LASTEXITCODE){throw 'Fresh GOP plan failed'}
    & $python "$PSScriptRoot/test-ram-gop-file.py" --prepared $gop
    if($LASTEXITCODE){throw 'Fresh GOP transaction tests failed'}
    & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared $gop --output (Join-Path $runBase 'gop-applied') --apply
    if($LASTEXITCODE){throw 'Proven GOP apply failed'};$gopApplied=$true
    foreach($key in $keys){[Environment]::SetEnvironmentVariable('UAVF_KD_'+$key,'1','Process')}
    [Environment]::SetEnvironmentVariable('UAVF_KD_PSCI_POST_START_MINIMAL',$null,'Process')
    $env:UAVF_WINDOWS_RAM_GOP='1'
    [Environment]::SetEnvironmentVariable('UAVF_KD_RAM_FRAMEBUFFER_AUDIT',$(if($RamFramebufferAudit){'1'}else{$null}),'Process')
    & "$PSScriptRoot/android-owner/run-service-test.ps1" -EvidenceName $evidenceName -ResumeWindowsInstallTarget -HoldRuntime -ObserverSeconds $ObserverSeconds -EmulateUserPmcc -QuietDriverNotifications -Candidate live-name-fix -FixedEightCpuGic -MatchHostCpus -WindowsClipboard:$WindowsClipboardProbe -WindowsClipboardProbe:$WindowsClipboardProbe
    if($LASTEXITCODE){throw 'Runtime observer failed; preserve logs and rollback'}
} finally {
    # Normal observer stops its VM. Never write to an active filesystem.
    $state=& $adb shell /apex/com.android.virt/bin/vm list
    if($LASTEXITCODE -or ($state -join "`n") -notmatch 'Running VMs: \[\]'){throw 'VM remains active; no cold rollback permitted'}
    if($gopApplied) {
        & $python "$PSScriptRoot/apply-ram-gop-file.py" --adb $adb --prepared $gop --output (Join-Path $runBase 'gop-rollback') --rollback --apply
        if($LASTEXITCODE){throw 'GOP recovery failed; DO NOT BOOT'}
    }
    if($vsockApplied) {
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $vsock --prepared $vsock --output (Join-Path $runBase 'vsock-rollback') --vsock-only --rollback --apply
        if($LASTEXITCODE){throw 'Vsock recovery failed; DO NOT BOOT'}
    }
    if($encoderApplied) {
        & $python "$PSScriptRoot/apply-isolated-display-service.py" --adb $adb --evidence $encoder --prepared $encoder --output (Join-Path $runBase 'encoder-rollback') --encoder-only --rollback --apply
        if($LASTEXITCODE){throw 'Encoder recovery failed; DO NOT BOOT'}
    }
    if($codecApplied) {
        & $python "$PSScriptRoot/apply-eight-thread-codec.py" --adb $adb --prepared $prepared --output (Join-Path $runBase 'codec-rollback') --codec $Codec --rollback --apply
        if($LASTEXITCODE){throw 'Codec allocation recovery failed; DO NOT BOOT'}
    }
    foreach($key in $savedEnvironment.Keys){[Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key],'Process')}
}
