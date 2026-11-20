$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
$folder=Join-Path $root 'build-logs\windows-post-linux-reassessment-20261004'
function Checked([string[]]$Arguments){$result=& $adb @Arguments;if($LASTEXITCODE){throw 'ADB failed'};return $result}
if((Checked @('shell','/apex/com.android.virt/bin/vm','list') -join '') -notmatch 'Running VMs: \[\]'){throw 'Preserve running VMs'}
$entries=@(
 @{Path='/storage/emulated/0/Android/data/com.example.uavfwinpetest/files/windows.img';Backup='windows-display-winpe-20261007.img';Sha='A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5'},
 @{Path='/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img';Backup='windows-display-live-name-fix-20261008.img';Sha='A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A'}
)
$manifest=Join-Path $folder 'redundant-device-windows-staging-cleanup-20261009.json'
if(Test-Path -LiteralPath $manifest){throw 'Preserve prior manifest'}
foreach($entry in $entries){
 $entry.Backup=Join-Path $folder $entry.Backup
 if((Get-FileHash -LiteralPath $entry.Backup -Algorithm SHA256).Hash -ne $entry.Sha){throw 'PC backup mismatch'}
 if((Checked @('shell','readlink','-f',$entry.Path) -join '').Trim() -ne $entry.Path){throw 'Path mismatch'}
 if(((Checked @('shell','sha256sum',$entry.Path)) -split '\s+')[0].ToUpperInvariant() -ne $entry.Sha){throw 'Device mismatch'}
}
$before=Checked @('shell','df','-k','/data')
@{Status='VERIFIED_PENDING_DELETE';Files=$entries;Before=$before;Recovery='Exact full PC backups retained. Prepared scratch restore requires restaging.'} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifest
foreach($entry in $entries){Checked @('shell','rm','-f',$entry.Path)}
$after=Checked @('shell','df','-k','/data')
@{Status='REMOVED';Files=$entries;Before=$before;After=$after;Recovery='Exact full PC backups retained; not recycle bin';Scope='Only redundant diagnostic staging, private VM and Ubuntu untouched'} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifest
$after
