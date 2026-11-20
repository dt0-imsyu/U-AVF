# Existing authenticated UIN1 diagnostic channel; never logs its owner token.
param([Parameter(Mandatory=$true)][string]$TokenFile,
      [ValidateRange(1,65535)][int]$Symbol=0,[string]$Text)
$ErrorActionPreference='Stop'
$adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim()
if($token -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Invalid diagnostic token'}
if($PSBoundParameters.ContainsKey('Text')) {
    if($Text.Length -gt 2048 -or $Text -match '[\x00-\x1f\x7f]'){throw 'Explicit keys required for control characters'}
    $encoded=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Text))
    $result=& $adb shell content call --uri content://com.example.uavfwinpetest.owner --method input_text --arg $token --extra "base64:s:$encoded"
    if($LASTEXITCODE -or ($result -join '') -notmatch 'channel_written=true'){throw 'Guest text channel unavailable'}
} else {
    if($Symbol -lt 1){throw 'Text or symbol required'}
    foreach($state in @(1,0)) {
        $result=& $adb shell content call --uri content://com.example.uavfwinpetest.owner --method input_key --arg $token --extra "symbol:i:$Symbol" --extra "state:i:$state"
        if($LASTEXITCODE -or ($result -join '') -notmatch 'channel_written=true'){throw 'Guest key channel unavailable'}
    }
}
'DIAGNOSTIC_INPUT_CHANNEL_WRITTEN'
