$ErrorActionPreference='Stop'
$source=Get-Content "$PSScriptRoot/idd-working-test.ps1" -Raw
$start=$source.IndexOf('[uint32]$deviceExit=259;')
$end=$source.IndexOf('} finally {',$start)
if($start -lt 0 -or $end -lt 0){throw 'Actual child exit block absent'}
$block=[scriptblock]::Create($source.Substring($start,$end-$start).Replace('[IdGuest]','[UavfExitCheck]'))
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class UavfExitCheck {
 [DllImport("kernel32.dll",SetLastError=true)] public static extern bool GetExitCodeProcess(IntPtr process,out uint code);
}
'@
$base=Join-Path ([IO.Path]::GetTempPath()) ('uavf-child-exit-'+[guid]::NewGuid())
New-Item -ItemType Directory $base|Out-Null
try {
foreach($pair in @(@(0,0),@(0,8),@(37,0))){
    $device=Start-Process $env:ComSpec -ArgumentList '/d','/c',"ping -n 2 127.0.0.1 > nul & exit $($pair[0])" -WindowStyle Hidden -PassThru
    $deviceHandle=$device.Handle
    $motion=Start-Process $env:ComSpec -ArgumentList '/d','/c',"ping -n 2 127.0.0.1 > nul & exit $($pair[1])" -WindowStyle Hidden -PassThru
    $motionHandle=$motion.Handle
    $device.WaitForExit();$motion.WaitForExit()
    $failed=$false;try {& $block}catch {if($_ -notmatch 'Child probe failed'){throw};$failed=$true}
    if($failed -ne ($pair[0] -ne 0 -or $pair[1] -ne 0)){throw 'Actual nonzero exit guard regression'}
    $result=Get-Content "$base/result.txt" -Raw
    if($result -notmatch "DEVICE_EXIT=$($pair[0]); MOTION_EXIT=$($pair[1]);"){throw 'Native child exit readback mismatch'}
    $device.Dispose();$motion.Dispose()
}
'ACTUAL_NATIVE_CHILD_EXIT_BLOCK_PASS; 0/0_ACCEPTED; 0/8_AND_37/0_REJECTED'
} finally {
    $resolved=[IO.Path]::GetFullPath($base)
    if(!$resolved.StartsWith([IO.Path]::GetTempPath()) -or [IO.Path]::GetFileName($resolved) -notlike 'uavf-child-exit-*'){throw 'Unsafe test cleanup'}
    Remove-Item -LiteralPath $resolved -Recurse
}
