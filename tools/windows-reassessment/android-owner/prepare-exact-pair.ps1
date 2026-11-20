$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
$reports=Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004'
function Checked([string[]]$Arguments){$result=& $adb @Arguments;if($LASTEXITCODE){throw "ADB failed: $($Arguments -join ' ')"};return $result}
$state=Checked @('shell','/apex/com.android.virt/bin/vm','list')
if(($state -join '') -notmatch 'Running VMs: \[\]'){throw 'Preserve active VM'}
$parent=(Get-Content -Raw -LiteralPath (Join-Path $reports 'producer-exit-disk-report.json') | ConvertFrom-Json).base_sha256
Checked @('shell','cp','/sdcard/Android/data/com.example.uavfwinpetest/files/windows-real-setup.img','/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img')
$digest=(Checked @('shell','sha256sum','/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img')) -split '\s+'
if($digest[0].ToUpperInvariant() -ne $parent){throw 'Original device preimage differs'}
foreach($revision in @('producer-exit','producer-stderr','producer-fault','producer-accept-fix')){
    $report=Get-Content -Raw -LiteralPath (Join-Path $reports "$revision-disk-report.json") | ConvertFrom-Json
    if($report.result -ne 'PASS' -or $report.base_sha256 -ne $parent){throw 'Exact manifest chain differs'}
    $delta="/data/local/tmp/uavf-windows-uart-gas-20261004/$revision.delta"
    $hash=(Checked @('shell','sha256sum',$delta)) -split '\s+'
    if($hash[0].ToUpperInvariant() -ne $report.delta_sha256){throw 'Delta identity differs'}
    # Native patcher validates every preimage/postimage block CRC. No unchecked writes.
    Checked @('shell','/data/local/tmp/uavf-windows-uart-gas-20261004/apply-windows-delta',$delta)
    $parent=$report.sha256
}
$digest=(Checked @('shell','sha256sum','/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img')) -split '\s+'
if($digest[0].ToUpperInvariant() -ne $parent -or $parent -ne '1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE'){throw 'Final candidate identity differs'}
# Overwrite only these two disposable pre-installation test copies; Ubuntu untouched.
Checked @('shell','cat /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img | run-as com.example.uavfwinpetest dd of=files/windows-diagnostic.img bs=1048576')
$private=(Checked @('shell','run-as','com.example.uavfwinpetest','sha256sum','files/windows-diagnostic.img')) -split '\s+'
if($private[0].ToUpperInvariant() -ne $parent){throw 'Private pair identity differs'}
'IDENTICAL_COLD_BOOT_PAIR_READY='+$parent
