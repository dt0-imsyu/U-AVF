[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$sourceRoot='D:\UAVF-backups\GOOD-installed-720p30-20260930'
$targetRoot='E:\UAVF-rescued-backups\GOOD-installed-720p30-20260930'
# A previous CopyFile stalled in platform-prefix-GOOD.img. Preserve that
# partial destination and ALL originals. Only these two small unique files.
if((Get-PSDrive E).Free -lt 50GB+1MB){throw 'E reserve required'}
if(!(Test-Path -LiteralPath $targetRoot)){throw 'Existing rescue directory required'}
$entries=@()
foreach($name in @('README.md','runtime-source-GOOD.zip')){
    $source=(Resolve-Path -LiteralPath (Join-Path $sourceRoot $name)).Path
    if([IO.Path]::GetDirectoryName($source) -ne $sourceRoot){throw 'Exact source only'}
    $target=Join-Path $targetRoot $name
    if(Test-Path -LiteralPath $target){throw 'Never overwrite previous rescue'}
    $bytes=(Get-Item -LiteralPath $source).Length
    if($bytes -gt 1MB){throw 'Only bounded small backups'}
    Write-Output ('RESCUING_SMALL='+$name)
    $hash=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    Copy-Item -LiteralPath $source -Destination $target
    if((Get-Item -LiteralPath $target).Length -ne $bytes -or
       (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash){throw 'Copy verification failed; preserve originals'}
    $entries+=@([ordered]@{source=$source;target=$target;bytes=$bytes;sha256=$hash})
    @{result='SMALL_FILES_VERIFIED_ONLY';files=$entries;originals_preserved=$true;
      unverified_files=@('platform-prefix-GOOD.img','U-AVF-GOOD.apk')} |
      ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $targetRoot 'small-rescue-manifest.json') -Encoding utf8
    Write-Output ('VERIFIED_RESCUED='+$name)
}
