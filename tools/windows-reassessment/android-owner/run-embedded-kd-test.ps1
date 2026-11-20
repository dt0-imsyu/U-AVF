[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9-]+$')][string]$EvidenceName,
    [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ApkSha256,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial
)

$ErrorActionPreference='Stop'
$workspace=([IO.DirectoryInfo]$PSScriptRoot).Parent.Parent.Parent.Parent.FullName
$archive=Join-Path $workspace 'build-logs/windows-post-linux-reassessment-20261004'
$source=Join-Path $archive 'windows-display-live-name-fix-20261008.img'
$evidence=Join-Path $archive $EvidenceName
$package='com.example.uavfwinpetest'
$privateDisk='files/windows-embedded-kd.img'
$external="/sdcard/Android/data/$package/files"
$mediaSha='A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A'
$mediaBytes=9126805504L
$chunkBytes=32MB
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'

function Invoke-Device([string[]]$DeviceArgs){
    $output=& $adb -s $Serial @DeviceArgs
    if($LASTEXITCODE -ne 0){throw "ADB failed ($($DeviceArgs[0]))"}
    return $output
}

function Get-TabletFreeBytes {
    $line=(Invoke-Device @('shell','df','-k','/data') | Select-Object -Last 1).Trim()
    return [long](($line -split '\s+')[3])*1024
}

function Assert-IdleVm {
    $state=Invoke-Device @('shell','/apex/com.android.virt/bin/vm','list')
    if(($state -join '') -notmatch 'Running VMs: \[\]'){throw 'An AVF VM is already running'}
}

if(Test-Path -LiteralPath $evidence){throw 'Use a fresh evidence directory'}
if((Get-Item -LiteralPath $source).Length -ne $mediaBytes){throw 'Unexpected WinPE image size'}
$drive=[IO.Path]::GetPathRoot($evidence).Substring(0,1)
if((Get-PSDrive -Name $drive).Free -lt 50GB+$chunkBytes){throw 'PC storage reserve for test artifacts'}
if((Get-TabletFreeBytes) -lt 50GB+$mediaBytes+$chunkBytes+64MB){
    throw 'Tablet storage reserve for the disposable WinPE image'
}
Assert-IdleVm
$policy=Invoke-Device @('shell','dumpsys','window','policy')
if($policy -match 'showing=true|screenState=SCREEN_STATE_OFF'){throw 'The tablet screen is unavailable'}
if((Invoke-Device @('shell','settings','get','global','hidden_api_policy')) -ne '1'){
    throw 'The existing diagnostic AVF interface is unavailable. This test does not change Android settings.'
}
$installed=Invoke-Device @('shell','pm','path',$package)
if($installed.Count -ne 1 -or $installed -notmatch '^package:(/data/app/[^\s]+/base\.apk)$'){
    throw 'Install the matching single-APK diagnostic build first'
}
$installedApk=$Matches[1]
$installedHash=(Invoke-Device @('shell','sha256sum',$installedApk)) -split '\s+'
if($installedHash[0] -ne $ApkSha256){throw 'Installed diagnostic APK identity mismatch'}
if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $mediaSha){
    throw 'WinPE template identity mismatch'
}

New-Item -ItemType Directory -Path $evidence | Out-Null
$chunk=Join-Path $evidence 'chunk.tmp'
$nonce=[Guid]::NewGuid().ToString('N')
$remoteChunk="$external/embedded-kd-$nonce.tmp"
$ownsDisk=$false
$launched=$false
try{
    # The installed diagnostic disk uses a different path. Noclobber also protects
    # a prior disposable copy if the last run was interrupted.
    Invoke-Device @('shell',"run-as $package sh -c 'set -C; : > $privateDisk'") | Out-Null
    $ownsDisk=$true
    $buffer=[byte[]]::new(1MB)
    $input=[IO.File]::OpenRead($source)
    try{
        for($offset=0L;$offset -lt $mediaBytes;$offset+=$chunkBytes){
            $left=[Math]::Min([long]$chunkBytes,$mediaBytes-$offset)
            $part=[IO.File]::Create($chunk)
            try{
                while($left -gt 0){
                    $read=$input.Read($buffer,0,[int][Math]::Min($left,$buffer.Length))
                    if($read -eq 0){throw 'WinPE template changed during transfer'}
                    $part.Write($buffer,0,$read)
                    $left-=$read
                }
            }finally{$part.Dispose()}
            Invoke-Device @('push',$chunk,$remoteChunk) | Out-Null
            Invoke-Device @('shell','run-as',$package,'dd',"if=$remoteChunk","of=$privateDisk",
                'bs=1048576',"seek=$([long]($offset/1MB))",'conv=notrunc') | Out-Null
            if((Get-TabletFreeBytes) -lt 50GB){throw 'Tablet reserve changed during staging'}
        }
    }finally{$input.Dispose()}
    $hash=(Invoke-Device @('shell','run-as',$package,'sha256sum',$privateDisk)) -split '\s+'
    if($hash[0] -ne $mediaSha){throw 'Transferred WinPE image identity mismatch'}
    Assert-IdleVm
    Invoke-Device @('shell','run-as',$package,'rm','-f',"$external/android-surface-first.png") | Out-Null
    Invoke-Device @('shell','am','force-stop',$package) | Out-Null
    $token=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    $launched=$true
    Invoke-Device @('shell','am','start','-W','-n',
        "$package/com.example.winavf.WindowsDiagnosticActivity",'--es','token',$token,
        '--es','candidate_sha',$mediaSha,'--ez','service_owner','true','--ez','embedded_kd','true') | Out-Null
    $clock=[Diagnostics.Stopwatch]::StartNew()
    $firstFrame=$false
    while($clock.Elapsed.TotalSeconds -lt 210){
        if((Get-TabletFreeBytes) -lt 50GB){throw 'Tablet reserve changed during WinPE boot'}
        & $adb -s $Serial shell run-as $package test -s "$external/android-surface-first.png"
        if($LASTEXITCODE -eq 0){$firstFrame=$true;break}
        Start-Sleep -Seconds 2
    }
    if(!$firstFrame){throw 'No fresh Surface readback in 210 seconds'}
    Invoke-Device @('pull',"$external/android-surface-first.png",$evidence) | Out-Null
    $report=Invoke-Device @('shell','run-as',$package,'cat',"$external/service-owner-report.txt")
    if('EMBEDDED_KD_KERNEL_RESUMED; WAIT_FOR_VSOCK' -notin $report -or
        'APP_OWNED_AVF_FD_READY' -notin $report){
        throw 'Surface frame without embedded KD and app-owned vsock evidence'
    }
    'EMBEDDED_KD_FIRST_FRAME_PASS'
}finally{
    if($launched){
        & $adb -s $Serial shell am force-stop $package | Out-Null
        foreach($name in @('service-owner-report.txt','owner-report.txt','service-effective-config.json')){
            & $adb -s $Serial pull "$external/$name" $evidence | Out-Null
        }
    }
    if($ownsDisk){& $adb -s $Serial shell run-as $package rm -f $privateDisk | Out-Null}
    & $adb -s $Serial shell run-as $package rm -f $remoteChunk | Out-Null
    if(Test-Path -LiteralPath $chunk){Remove-Item -LiteralPath $chunk}
}
