param([ValidateSet('live','live-name-fix')][string]$Candidate='live')
$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
$folder=Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004'
function Checked([string[]]$Arguments){$result=& $adb @Arguments;if($LASTEXITCODE){throw "ADB failed: $($Arguments -join ' ')"};return $result}
$state=Checked @('shell','/apex/com.android.virt/bin/vm','list')
if(($state -join '') -notmatch 'Running VMs: \[\]'){throw 'Preserve active VM'}
$report=Get-Content -Raw -LiteralPath (Join-Path $folder "$Candidate-disk-report.json") | ConvertFrom-Json
$parent=if($Candidate -eq 'live'){'1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE'}else{'E45161B4037F586A1847470E04C6143EC52ACEE1EDB605CC450EB2CC363FEE6F'}
if($report.result -ne 'PASS' -or $report.base_sha256 -ne $parent -or $report.platform_base_sha256 -ne 'A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5' -or $report.sha256 -notmatch '^[0-9A-F]{64}$'){throw 'Exact live manifest guard'}
$before=(Checked @('shell','sha256sum','/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img')) -split '\s+'
if($before[0].ToUpperInvariant() -ne $report.base_sha256){throw 'Pristine disposable preimage required; no overwrite'}
$delta=Join-Path $folder "$Candidate.delta"
if((Get-FileHash -Algorithm SHA256 -LiteralPath $delta).Hash -ne $report.delta_sha256){throw 'Local delta identity mismatch'}
$remoteDelta="/data/local/tmp/uavf-windows-uart-gas-20261004/$Candidate.delta"
Checked @('push',$delta,$remoteDelta)
$remote=(Checked @('shell','sha256sum',$remoteDelta)) -split '\s+'
if($remote[0].ToUpperInvariant() -ne $report.delta_sha256){throw 'Transferred delta mismatch'}
Checked @('shell','/data/local/tmp/uavf-windows-uart-gas-20261004/apply-windows-delta',$remoteDelta)
$after=(Checked @('shell','sha256sum','/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img')) -split '\s+'
if($after[0].ToUpperInvariant() -ne $report.sha256){throw 'Patched disposable identity mismatch; do not launch'}
'LIVE_DISPOSABLE_STAGING_PASS='+$report.sha256
