param([Parameter(Mandatory=$true)][string]$OutputDirectory,[ValidateRange(30,900)][int]$Seconds=600)
$ErrorActionPreference='Stop'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if(Test-Path $OutputDirectory){throw 'Preserve prior stream evidence'}
New-Item -ItemType Directory $OutputDirectory|Out-Null
$deadline=[DateTime]::UtcNow.AddSeconds($Seconds)
while([DateTime]::UtcNow -lt $deadline){
    $owner=(& $adb shell /apex/com.android.virt/bin/vm list) -join "`n"
    if($LASTEXITCODE){throw 'USB unavailable; no guest input attempted'}
    if(($owner|Select-String 'requesterUid:' -AllMatches).Matches.Count -eq 1 -and $owner -match 'requesterUid: 10042'){
        $report=(& $adb shell cat /sdcard/Android/data/com.example.uavfwinpetest/files/encoded-display-report.txt) -join "`n"
        if($report -match 'CAPTURE_SOURCE=IDD_LOCAL_PIPE open_error=0'){
            $report|Set-Content "$OutputDirectory/source-connected.txt"
            & $adb shell screencap -p /sdcard/Download/uavf-primary-idd-current.png
            & $adb pull /sdcard/Download/uavf-primary-idd-current.png "$OutputDirectory/current.png"
            if($LASTEXITCODE){throw 'Current frame unavailable'}
            'PRIMARY_IDD_STREAM_OBSERVED; INSPECT_CURRENT_FRAME_BEFORE_INPUT; NOT_FPS_PASS'
            exit 0
        }
    }
    Start-Sleep -Seconds 2
}
throw 'Primary IDD stream not observed inside finite bound; no input attempted'
