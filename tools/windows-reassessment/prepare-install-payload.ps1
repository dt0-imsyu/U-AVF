[CmdletBinding()]
param([string]$MountedSourcePath)
$ErrorActionPreference='Stop'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$iso=(Resolve-Path -LiteralPath (Join-Path $workspaceRoot 'win11-iso-work\Win11_25H2_ARM64_ru-ru.iso')).Path
$destination='E:\UAVF-Windows-install-payload-20261008'
$wimlib=Join-Path $workspaceRoot 'tools\wimlib-1.14.5\wimlib-imagex.exe'
if(Test-Path -LiteralPath $destination){throw 'Preserve existing payload evidence; no overwrite'}
if((Get-PSDrive E).Free -lt 8GB){throw 'Insufficient scratch space on E'}
$mountedByUs=$false
try {
    Write-Host 'PAYLOAD_SOURCE_CHECK'
    if($MountedSourcePath){
        # Explicit source on the ISO mounted read-only by this diagnostic task.
        # Avoid repeated Storage-provider discovery in an existing mount.
        $source=(Resolve-Path -LiteralPath $MountedSourcePath).Path
        if($source -ne 'G:\sources\install.wim' -or (Get-Item -LiteralPath $source).Length -ne 6347029986){throw 'Unexpected mounted source'}
    } else {
        if(!(Get-DiskImage -ImagePath $iso).Attached){Mount-DiskImage -ImagePath $iso -Access ReadOnly | Out-Null;$mountedByUs=$true}
        $volume=Get-DiskImage -ImagePath $iso | Get-Volume
        if(@($volume).Count -ne 1 -or !$volume.DriveLetter){throw 'Ambiguous ISO volume'}
        $source=Join-Path ($volume.DriveLetter.ToString()+':\') 'sources\install.wim'
    }
    if(!(Test-Path -LiteralPath $source)){throw 'Official local source has no install.wim'}
    New-Item -ItemType Directory -Path $destination | Out-Null
    Write-Host 'PAYLOAD_SPLIT_BEGIN'
    & $wimlib split $source (Join-Path $destination 'install.swm') 3500
    if($LASTEXITCODE){throw 'SWM split failed'}
    & $wimlib verify (Join-Path $destination 'install.swm') "--ref=$destination\install*.swm"
    if($LASTEXITCODE){throw 'Split payload verification failed'}
    $parts=Get-ChildItem -LiteralPath $destination -Filter 'install*.swm' | Sort-Object Name
    if(!$parts -or @($parts | Where-Object {$_.Length -ge 4GB}).Count){throw 'Payload does not fit FAT32 per-file limit'}
    $report=[ordered]@{
        result='PASS'; scope='Install payload prepared only; NOT installation or display PASS'
        iso=$iso; isoSha256=(Get-FileHash -LiteralPath $iso -Algorithm SHA256).Hash
        sourceBytes=(Get-Item -LiteralPath $source).Length
        sourceSha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        parts=@($parts | ForEach-Object {[ordered]@{name=$_.Name;bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}})
        sourceUnmodified=$true; windowsTargetNotCreated=$true; ubuntuUntouched=$true
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'payload-report.json') -Encoding utf8
    $report | ConvertTo-Json -Depth 5
} finally {
    if($mountedByUs){Dismount-DiskImage -ImagePath $iso | Out-Null}
}
