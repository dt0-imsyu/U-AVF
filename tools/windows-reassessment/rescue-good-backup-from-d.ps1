[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$sourceRoot='D:\UAVF-backups\GOOD-installed-720p30-20260930'
$targetRoot='E:\UAVF-rescued-backups\GOOD-installed-720p30-20260930'
$names=@('platform-prefix-GOOD.img','README.md','runtime-source-GOOD.zip','U-AVF-GOOD.apk')
if(Test-Path -LiteralPath $targetRoot){throw 'Preserve existing rescue; do not overwrite'}
$entries=@();$total=0L
foreach($name in $names){
    $source=(Resolve-Path -LiteralPath (Join-Path $sourceRoot $name)).Path
    if([IO.Path]::GetDirectoryName($source) -ne $sourceRoot){throw 'Exact source only'}
    $file=Get-Item -LiteralPath $source
    $total+=$file.Length
    $entries+=@([ordered]@{source=$source;target=Join-Path $targetRoot $name;bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash})
}
if($total -gt 1GB -or (Get-PSDrive E).Free -lt 50GB+$total){throw 'Bounded backup and E reserve required'}
New-Item -ItemType Directory -Path $targetRoot | Out-Null
foreach($entry in $entries){
    Copy-Item -LiteralPath $entry.source -Destination $entry.target
    if((Get-Item -LiteralPath $entry.target).Length -ne $entry.bytes -or
        (Get-FileHash -LiteralPath $entry.target -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Rescue failed verification; originals preserved'}
    'VERIFIED_RESCUED='+$entry.target
}
# D is read-only throughout. Do not remove originals from a failing drive.
@{result='GOOD_BACKUP_RESCUED_SHA256_PASS';files=$entries;originals_preserved=$true;source_drive_read_only=$true} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $targetRoot 'rescue-manifest.json') -Encoding utf8
