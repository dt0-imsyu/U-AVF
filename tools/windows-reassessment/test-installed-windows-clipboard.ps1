param([Parameter(Mandatory=$true)][string]$TokenFile)
$ErrorActionPreference='Stop'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim()
if($token -notmatch '^[0-9a-f]{64}$'){throw 'Invalid isolated owner token'}
$answer=& $adb shell content call --uri content://com.example.uavfwinpetest.owner `
    --method clipboard_roundtrip --arg $token 2>&1
if($LASTEXITCODE -ne 0 -or ($answer -join '') -notmatch 'guest_clipboard_roundtrip=true') {
    # Never emit a credential/clipboard value or treat connection as success.
    $probeNotes=& $adb shell cat /sdcard/Android/data/com.example.uavfwinpetest/files/owner-report.txt 2>$null
    $probeNotes | Where-Object {$_ -match '^CLIPBOARD_PROBE_STAGE=(CONNECT|SEND|READ|MATCH|UNMATCHED)$' -or $_ -match '^CLIPBOARD_PROBE_ERROR=(EOF|TIMEOUT|IO|CHANNEL)$'} | Select-Object -Last 8
    throw 'Synthetic Windows clipboard roundtrip not confirmed; inspect probe report'
}
'ACTUAL_WINDOWS_CLIPBOARD_API_APP_OWNED_UCIP_ROUNDTRIP=PASS'
'ANDROID_SYSTEM_CLIPBOARD_AND_LOCK_RECONNECT_NOT_YET_PROVEN=1'
