[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
throw 'RETIRED: user reports D drive failing. Never archive new files to D. Use a verified healthy destination.'
$sourceRoot='C:\Users\denis\MainProjects\win11ontab\build-logs\windows-post-linux-reassessment-20261004'
$destination='D:\UAVF-archived-staging-20261010'
$names=@('producer-stderr-candidate.wim','producer-fault-candidate.wim','producer-exit-candidate.wim')
if(Test-Path -LiteralPath $destination){throw 'Preserve prior archive'}
if((Get-PSDrive D).Free -lt 52GB){throw 'Keep destination reserve'}
$entries=@()
foreach($name in $names){
    $source=(Resolve-Path -LiteralPath (Join-Path $sourceRoot $name)).Path
    if([IO.Path]::GetDirectoryName($source) -ne $sourceRoot){throw 'Exact workspace staging directory required'}
    $file=Get-Item -LiteralPath $source
    if($file.Length -lt 600MB -or $file.Length -gt 700MB){throw 'Unexpected intermediate WIM size'}
    $entries+=@([ordered]@{source=$source;destination=Join-Path $destination $name;bytes=$file.Length;sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash})
}
New-Item -ItemType Directory -Path $destination | Out-Null
foreach($entry in $entries){
    Copy-Item -LiteralPath $entry.source -Destination $entry.destination
    if((Get-Item -LiteralPath $entry.destination).Length -ne $entry.bytes -or
       (Get-FileHash -LiteralPath $entry.destination -Algorithm SHA256).Hash -ne $entry.sha256){throw 'Archive verification failed; preserve source'}
    # Never delete an unresolved path, broad folder, baseline or installed disk.
    $resolved=(Resolve-Path -LiteralPath $entry.source).Path
    if($resolved -ne $entry.source -or [IO.Path]::GetDirectoryName($resolved) -ne $sourceRoot){throw 'Source scope changed'}
    Remove-Item -LiteralPath $resolved
    'VERIFIED_RECOVERABLE_STAGING_ARCHIVE='+$entry.destination
}
@{result='ARCHIVED_BYTE_IDENTICAL';files=$entries;scope='Three obsolete intermediate WIMs only; current media/baselines/Ubuntu/installed data unchanged';recovery='Copy exact archived files back to their recorded source paths if historical reproduction is required'} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'archive-manifest.json') -Encoding utf8
Get-PSDrive C,D,E | Select-Object Name,@{n='FreeGiB';e={[math]::Round($_.Free/1GB,2)}}
