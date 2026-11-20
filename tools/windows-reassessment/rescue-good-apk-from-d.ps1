[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$source='D:\UAVF-backups\GOOD-installed-720p30-20260930\U-AVF-GOOD.apk'
$target='E:\UAVF-rescued-backups\GOOD-installed-720p30-20260930\U-AVF-GOOD.apk'
$expected='72AD2E18D60E3AE158A93129497966EE47060AD88DFD8BE496DEDCFEE2BCF491'
# Hash is from the rescued GOOD README. Single sequential source read, no
# source modification and no second large read on the failing disk.
if(Test-Path -LiteralPath $target){throw 'Preserve prior rescue; no overwrite'}
if((Get-PSDrive E).Free -lt 50GB+405009772){throw 'Preserve E reserve'}
if((Get-Item -LiteralPath $source).Length -ne 405009772){throw 'Exact GOOD APK size required'}
$inputStream=[IO.File]::OpenRead($source)
$outputStream=[IO.File]::Open($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$buffer=New-Object byte[] 1048576;$total=0L;$reported=0L
try {
    while(($count=$inputStream.Read($buffer,0,$buffer.Length)) -gt 0){
        $outputStream.Write($buffer,0,$count);$total+=$count
        if($total-$reported -ge 16MB){$outputStream.Flush();Write-Output ('APK_RESCUE_BYTES='+$total);$reported=$total}
    }
}finally{$inputStream.Dispose();$outputStream.Dispose()}
if($total -ne 405009772 -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $expected){throw 'Unverified partial rescue; originals preserved'}
@{result='GOOD_APK_RESCUED_SHA256_PASS';source=$source;target=$target;sha256=$expected;bytes=$total;
  originals_preserved=$true;platform_prefix_still_unverified=$true} |
  ConvertTo-Json | Set-Content -LiteralPath 'E:\UAVF-rescued-backups\GOOD-installed-720p30-20260930\apk-rescue-manifest.json' -Encoding utf8
'GOOD_APK_RESCUED_SHA256_PASS'
