# Guest-only ordinary desktop workload: a newly opened empty Notepad window.
# No registry/file changes, no elevation, no synthetic framebuffer producer.
Add-Type 'using System;using System.Runtime.InteropServices;public class W{[DllImport("user32")]public static extern bool SetWindowPos(IntPtr h,IntPtr a,int x,int y,int w,int z,uint f);}'
$s=Get-Date
Start-Process notepad
Start-Sleep 3
$p=Get-Process notepad -ErrorAction SilentlyContinue | Where-Object {$_.StartTime -ge $s -and $_.MainWindowHandle -ne 0} | Select-Object -First 1
if(!$p){throw 'New test Notepad window not available'}
try {
    $t=[Diagnostics.Stopwatch]::StartNew()
    while($t.Elapsed.TotalSeconds -lt 30) {
        $v=$t.Elapsed.TotalSeconds*6
        [void][W]::SetWindowPos($p.MainWindowHandle,[IntPtr]::Zero,(180+140*[Math]::Sin($v)),(100+50*[Math]::Cos($v)),1560,980,20)
        Start-Sleep -Milliseconds 16
    }
} finally {[void]$p.CloseMainWindow()}
