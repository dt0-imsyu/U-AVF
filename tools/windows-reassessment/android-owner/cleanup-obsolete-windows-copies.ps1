$ErrorActionPreference='Stop'
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
$folder=Join-Path $root 'build-logs\windows-post-linux-reassessment-20261004'
function Checked([string[]]$Arguments){$result=& $adb @Arguments;if($LASTEXITCODE){throw "ADB failed: $($Arguments -join ' ')"};return $result}
$state=Checked @('shell','/apex/com.android.virt/bin/vm','list')
if(($state -join '') -notmatch 'Running VMs: \[\]'){throw 'No cleanup while VM active'}
$base=Join-Path $folder 'windows-display-winpe-20261007.img'
if((Get-FileHash -Algorithm SHA256 -LiteralPath $base).Hash -ne 'A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5'){throw 'Preserve verified reconstruction base'}
$entries=@(
    @{Name='windows-direct-accept.img';Sha='88EADE49B3F5680B0568BC342FE2987BF1CDBA4CAA8938F9A69027759EF2F6A7';Wim='direct-accept-candidate.wim';WimSha='CD659F7CDEAD72120104FFBC09D75224404559DD12345BFB354807F8F2B8B402'},
    @{Name='windows-shell-lifetime.img';Sha='DDC0E4C2EF2E155D700736A7808AABF5999D689787724612615F537F7140363C';Wim='shell-lifetime-candidate.wim';WimSha='85F614E3E3BABFE76F0ACBC5D77399F85758C41318BD0B4323118BB664206941'}
)
foreach($entry in $entries){
    $entry.Path='/storage/emulated/0/Android/data/com.example.uavfwinpetest/files/'+$entry.Name
    $entry.BackupWim=Join-Path $folder $entry.Wim
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $entry.BackupWim).Hash -ne $entry.WimSha){throw 'Frozen WIM reconstruction payload mismatch'}
    $resolved=(Checked @('shell','readlink','-f',$entry.Path) -join '').Trim()
    if($resolved -ne $entry.Path){throw 'Exact diagnostic path resolution required'}
    $hash=(Checked @('shell','sha256sum',$entry.Path)) -split '\s+'
    if($hash[0].ToUpperInvariant() -ne $entry.Sha){throw 'Unexpected device bytes; preserve unknown state'}
    $entry.Bytes=9126805504
    'VERIFIED_OBSOLETE_DIAGNOSTIC_COPY='+$entry.Path
}
$before=Checked @('shell','df','-h','/data')
$manifest=Join-Path $folder 'obsolete-device-windows-cleanup-20261009.json'
if(Test-Path -LiteralPath $manifest){throw 'Preserve previous cleanup manifest'}
@{Status='VERIFIED_PENDING_DELETE';Files=$entries;ReconstructionBase=$base;Before=$before;Scope='Only two obsolete never-installed diagnostic media copies; no Ubuntu/current/baseline deletion'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifest
foreach($entry in $entries){Checked @('shell','rm','-f',$entry.Path)}
$after=Checked @('shell','df','-h','/data')
@{Status='REMOVED';Files=$entries;ReconstructionBase=$base;Before=$before;After=$after;Recovery='Rebuild from exact retained PC base + frozen WIM; not recycle bin';Scope='Ubuntu/current/private Windows candidate and original baseline untouched'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifest
$after
