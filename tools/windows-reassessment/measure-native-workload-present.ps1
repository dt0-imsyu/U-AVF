# Read-only Android present fences during the opt-in30s native Windows workload.
# No SurfaceFlinger reset/settings/Binder mutations. Exclude historical ring rows.
param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$output=[IO.Path]::GetFullPath($OutputDirectory)
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\build-logs'))+'\'
if(!$output.StartsWith($root,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)){throw 'New C project evidence directory required'}
function Present-Rows([string]$layer) {
    $lines=& $adb shell "dumpsys SurfaceFlinger --latency '$layer'"
    if($LASTEXITCODE){throw 'Present timestamps unavailable'}
    @($lines | ForEach-Object {
        if($_ -match '^\s*(\d+)\s+(\d+)\s+(\d+)\s*$') {
            $present=[long]$Matches[2];$ready=[long]$Matches[3]
            if($present -gt 0 -and $present -lt [long]::MaxValue){[pscustomobject]@{present_ns=$present;ready_ns=$ready}}
        }
    })
}
$wait=[Diagnostics.Stopwatch]::StartNew();$layer=$null;$source=$null
while($wait.Elapsed.TotalSeconds -lt 600) {
    $text=(& $adb shell cat /sdcard/Android/data/com.example.uavfwinpetest/files/encoded-display-report.txt) -join "`n"
    $matchesWorkload=[regex]::Matches($text,'SYNTHETIC_FULL_REPAINT paints=(\d+) elapsed_ms=(\d+) thread_exit=(\d+)')
    if($matchesWorkload.Count) {
        $last=$matchesWorkload[$matchesWorkload.Count-1]
        if([int]$last.Groups[1].Value -ge 2 -and [long]$last.Groups[2].Value -lt 10000 -and [int]$last.Groups[3].Value -eq 259){$source=$last.Value;break}
        if([long]$last.Groups[2].Value -ge 10000){throw 'Workload start missed; no fabricated sustained measurement'}
    }
    Start-Sleep -Seconds 1
}
if(!$source){throw 'No active native full repaint; no present benchmark'}
$layers=(& $adb shell dumpsys SurfaceFlinger --list) -join "`n"
$match=[regex]::Match($layers,'\{([^{}\r\n]*SurfaceView\[com\.example\.uavfwinpetest/com\.example\.winavf\.WindowsDiagnosticActivity\]@0\(BLAST\)#\d+)')
if(!$match.Success){throw 'Exact diagnostic video BLAST layer absent'}
$layer=$match.Groups[1].Value
$initial=@(Present-Rows $layer);if(!$initial.Count){throw 'No valid actual present fences'}
$baseline=($initial | Measure-Object -Property present_ns -Maximum).Maximum
$frames=@{};$snapshots=@();$clock=[Diagnostics.Stopwatch]::StartNew()
while($clock.Elapsed.TotalSeconds -lt 20) {
    $rows=@(Present-Rows $layer)
    foreach($row in $rows){if($row.present_ns -gt $baseline){$frames[$row.present_ns.ToString()]=$row}}
    $snapshots+=@{elapsed_ms=[math]::Round($clock.Elapsed.TotalMilliseconds);unique_new_present=$frames.Count}
    Start-Sleep -Milliseconds 500
}
$sorted=@($frames.Values | Sort-Object present_ns)
if($sorted.Count -lt 2){throw 'No sustained newly presented video frames'}
$span=($sorted[-1].present_ns-$sorted[0].present_ns)/1e9
$readyLatency=@($sorted | Where-Object {$_.ready_ns -gt 0 -and $_.ready_ns -lt [long]::MaxValue -and $_.ready_ns -le $_.present_ns} | ForEach-Object {($_.present_ns-$_.ready_ns)/1e6})
$result=[ordered]@{result='ACTUAL_SURFACE_PRESENT_FENCES_MEASURED';source='SYNTHETIC_FULL_REPAINT_NOT_ARBITRARY_DESKTOP';layer=$layer;
    workload_at_start=$source;sampling_seconds=$clock.Elapsed.TotalSeconds;baseline_actual_present_ns=$baseline;
    new_present_frames=$sorted.Count;new_present_span_seconds=$span;actual_present_fps=($sorted.Count-1)/$span;
    android_ready_to_present_mean_ms=($readyLatency | Measure-Object -Average).Average;
    snapshots=$snapshots;actual_fences=$sorted}
New-Item -ItemType Directory -Path $output | Out-Null
[IO.File]::WriteAllText((Join-Path $output 'present-fences.json'),($result | ConvertTo-Json -Depth 8))
& $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/encoded-display-report.txt (Join-Path $output 'encoded-display-report.txt') | Out-Null
([pscustomobject]$result | Select-Object result,source,new_present_frames,new_present_span_seconds,actual_present_fps,android_ready_to_present_mean_ms) | ConvertTo-Json
