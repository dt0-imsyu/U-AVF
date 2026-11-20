[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$EvidenceName,[switch]$TraceProducerExit,[switch]$PrepareOnly,[switch]$ReusePrepared,[switch]$RuntimeSnapshot,[switch]$LoaderState,
      [ValidateSet('interrupt-init','driver-entry','io-pre-drivers','io-complete')][string]$BootMilestone,
      [ValidateSet('baseline','direct-accept','shell-lifetime','real-setup','producer-exit','producer-stderr','producer-fault','producer-accept-fix')][string]$Candidate='baseline')
$ErrorActionPreference='Stop'
if($LoaderState -and ($BootMilestone -or $RuntimeSnapshot -or $TraceProducerExit)){throw 'One observation scope only'}
if($EvidenceName -notmatch '^[a-zA-Z0-9-]+$'){throw 'Invalid evidence name'}
$taskRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '..\..\..'))
$workspaceRoot=Split-Path -Parent $repoRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
function Assert-AdbConnected {
    $deviceState=& $adb get-state
    if($LASTEXITCODE -ne 0 -or ($deviceState -join '').Trim() -ne 'device'){
        throw 'ADB connection unavailable; no VM launch or disk identity assumed'
    }
}
$python=Join-Path $env:LOCALAPPDATA 'Programs\Python\Python314\python.exe'
$candidateSha='A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5'
$candidateRemote='/sdcard/Android/data/com.example.uavfwinpetest/files/windows.img'
if($Candidate -ne 'baseline'){
    $manifest=Get-Content -LiteralPath (Join-Path $workspaceRoot "build-logs\windows-post-linux-reassessment-20261004\$Candidate-disk-report.json") -Raw | ConvertFrom-Json
    $platformBase=if($Candidate -in @('real-setup','producer-exit','producer-stderr','producer-fault','producer-accept-fix')){$manifest.platform_base_sha256}else{$manifest.base_sha256}
    if($manifest.result -ne 'PASS' -or $platformBase -ne $candidateSha -or $manifest.sha256 -notmatch '^[0-9A-F]{64}$'){throw 'Invalid isolated candidate manifest'}
    $candidateSha=$manifest.sha256
    $candidateRemote="/sdcard/Android/data/com.example.uavfwinpetest/files/windows-$Candidate.img"
    if($Candidate -in @('producer-exit','producer-stderr','producer-fault','producer-accept-fix')){$candidateRemote='/sdcard/Android/data/com.example.uavfwinpetest/files/windows-real-setup.img'}
}
Assert-AdbConnected
$running=& $adb shell /apex/com.android.virt/bin/vm list
if($LASTEXITCODE){throw 'Cannot obtain authoritative VM state; no launch'}
if(($running -join "`n") -notmatch 'Running VMs: \[\]'){throw 'Preserve existing VM'}
$policy=& $adb shell dumpsys window policy
if($LASTEXITCODE){throw 'Cannot verify tablet unlock state; no launch'}
if($policy -match 'showing=true' -or $policy -match 'screenState=SCREEN_STATE_OFF'){throw 'Unlock tablet; no bypass'}
$evidence=Join-Path $workspaceRoot "build-logs\windows-post-linux-reassessment-20261004\$EvidenceName"
if(Test-Path -LiteralPath $evidence){throw 'Preserve previous evidence'}
New-Item -ItemType Directory -Path $evidence | Out-Null
$token=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
try {
    if($ReusePrepared){
        $prepared=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img) -split '\s+'
        if($LASTEXITCODE -or !$prepared -or $prepared[0].ToUpperInvariant() -ne $candidateSha){throw 'Exact prepared image differs; no launch'}
    }else{
    # Replaces only the explicitly disposable preinstallation Windows copy.
    & $adb shell cp $candidateRemote /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img
    if($LASTEXITCODE){throw 'Disposable Windows restore failed'}
    if($Candidate -in @('producer-exit','producer-stderr','producer-fault','producer-accept-fix')){
        $exitManifest=Get-Content -LiteralPath (Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004\producer-exit-disk-report.json') -Raw | ConvertFrom-Json
        $baseHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img) -split '\s+'
        if($LASTEXITCODE -or !$baseHash -or $baseHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during base identity read; no launch'}
        $deltaHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/producer-exit.delta) -split '\s+'
        if($LASTEXITCODE -or !$deltaHash -or $deltaHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during delta identity read; no launch'}
        if($baseHash[0].ToUpperInvariant() -ne $exitManifest.base_sha256 -or $deltaHash[0].ToUpperInvariant() -ne $exitManifest.delta_sha256){throw 'Delta/preimage identity mismatch; no VM launch'}
        & $adb shell /data/local/tmp/uavf-windows-uart-gas-20261004/apply-windows-delta /data/local/tmp/uavf-windows-uart-gas-20261004/producer-exit.delta
        if($LASTEXITCODE){throw 'Delta application failed; disposable disk must be restored, no VM launch'}
        if($Candidate -in @('producer-stderr','producer-fault','producer-accept-fix')){
            $stderrManifest=Get-Content -LiteralPath (Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004\producer-stderr-disk-report.json') -Raw | ConvertFrom-Json
            $parentHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img) -split '\s+'
            if($LASTEXITCODE -or !$parentHash -or $parentHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during parent identity read; no launch'}
            $stderrHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/producer-stderr.delta) -split '\s+'
            if($LASTEXITCODE -or !$stderrHash -or $stderrHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during stderr delta read; no launch'}
            if($parentHash[0].ToUpperInvariant() -ne $stderrManifest.base_sha256 -or $stderrManifest.base_sha256 -ne $exitManifest.sha256 -or $stderrHash[0].ToUpperInvariant() -ne $stderrManifest.delta_sha256){throw 'Stderr delta chain mismatch; no VM launch'}
            & $adb shell /data/local/tmp/uavf-windows-uart-gas-20261004/apply-windows-delta /data/local/tmp/uavf-windows-uart-gas-20261004/producer-stderr.delta
            if($LASTEXITCODE){throw 'Stderr delta failed; no VM launch'}
            if($Candidate -in @('producer-fault','producer-accept-fix')){
                $faultManifest=Get-Content -LiteralPath (Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004\producer-fault-disk-report.json') -Raw | ConvertFrom-Json
                $parentHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img) -split '\s+'
                if($LASTEXITCODE -or !$parentHash -or $parentHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during fault-parent identity read; no launch'}
                $faultHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/producer-fault.delta) -split '\s+'
                if($LASTEXITCODE -or !$faultHash -or $faultHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during fault delta read; no launch'}
                if($parentHash[0].ToUpperInvariant() -ne $faultManifest.base_sha256 -or $faultManifest.base_sha256 -ne $stderrManifest.sha256 -or $faultHash[0].ToUpperInvariant() -ne $faultManifest.delta_sha256){throw 'Fault delta chain mismatch; no VM launch'}
                & $adb shell /data/local/tmp/uavf-windows-uart-gas-20261004/apply-windows-delta /data/local/tmp/uavf-windows-uart-gas-20261004/producer-fault.delta
                if($LASTEXITCODE){throw 'Fault delta failed; no VM launch'}
                if($Candidate -eq 'producer-accept-fix'){
                    $parentHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img) -split '\s+'
                    if($LASTEXITCODE -or !$parentHash -or $parentHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during fixed-parent identity read; no launch'}
                    $fixHash=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/producer-accept-fix.delta) -split '\s+'
                    if($LASTEXITCODE -or !$fixHash -or $fixHash[0] -notmatch '^[0-9a-fA-F]{64}$'){throw 'USB interruption during accept-fix delta read; no launch'}
                    if($parentHash[0].ToUpperInvariant() -ne $manifest.base_sha256 -or $manifest.base_sha256 -ne $faultManifest.sha256 -or $fixHash[0].ToUpperInvariant() -ne $manifest.delta_sha256){throw 'Accept fix chain mismatch; no VM launch'}
                    & $adb shell /data/local/tmp/uavf-windows-uart-gas-20261004/apply-windows-delta /data/local/tmp/uavf-windows-uart-gas-20261004/producer-accept-fix.delta
                    if($LASTEXITCODE){throw 'Accept fix delta failed; no VM launch'}
                }
            }
        }
        # Observer and AVF owner independently check the complete postimage SHA
        # before VM creation; delta CRC is not a substitute for these SHA guards.
    }
    }
    if($PrepareOnly){
        $prepared=(& $adb shell sha256sum /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img) -split '\s+'
        if($LASTEXITCODE -or !$prepared -or $prepared[0].ToUpperInvariant() -ne $candidateSha){throw 'Prepared final identity differs; no VM launch'}
        Write-Output 'PREPARED_ONLY_NO_VM_CREATED';return
    }
    & $adb shell am force-stop com.example.uavfwinpetest
    if($LASTEXITCODE){throw 'USB interruption before diagnostic activity; no launch'}
    & $adb shell am start -n com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity --es token $token --ez external_owner true
    if($LASTEXITCODE){throw 'Diagnostic activity launch failed; no VM launch'}
    [string[]]$observerOptions=if($LoaderState){@('--loader-state')}elseif($BootMilestone){@('--boot-milestone',$BootMilestone)}else{@('--sample-after-io','--device-inventory','--process-inventory','--break-on-reboot','--break-on-idle')}
    if($TraceProducerExit){$observerOptions+='--trace-producer-exit'}
    if($RuntimeSnapshot){$observerOptions+='--boot-runtime-snapshot'}
    & $python "$repoRoot\tools\windows-reassessment\trace-kd-boot-drivers.py" $adb "/data/local/tmp/uavf-windows-uart-gas-20261004/run-owner-avf.sh $token 300 $candidateSha" $evidence "$workspaceRoot\build-logs\windows-arm64-timer-static-20260913\ntoskrnl.exe" --candidate-sha $candidateSha --cam-access --periodic-imask-workaround --idle-seconds 60 --seconds 210 --delegate-shell-owner @observerOptions
    if($LASTEXITCODE){throw 'Observer failed; preserve evidence, do not classify as runtime result'}
} finally {
    & $adb shell touch /data/local/tmp/uavf-windows-uart-gas-20261004/owner-stop-requested
    & $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/owner-report.txt "$evidence\owner-report.txt"
    & $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/encoded-display-report.txt "$evidence\encoded-display-report.txt"
    & $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/android-surface-first.png "$evidence\android-surface-first.png"
    & $adb pull /data/local/tmp/uavf-windows-uart-gas-20261004/owner-crosvm.log "$evidence\owner-crosvm.log"
    & $adb shell am force-stop com.example.uavfwinpetest
    & $adb shell /apex/com.android.virt/bin/vm list
}
