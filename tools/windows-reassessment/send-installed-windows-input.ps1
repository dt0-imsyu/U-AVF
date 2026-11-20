param(
    [Parameter(Mandatory=$true)][string]$TokenFile,
    [Parameter(Mandatory=$true,ParameterSetName='Text')][string]$Text,
    [Parameter(Mandatory=$true,ParameterSetName='Key')][ValidateRange(1,65535)][int]$KeySym,
    [Parameter(ParameterSetName='Key')][ValidateSet('Press','Down','Up')][string]$Action='Press',
    [string]$Adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
)
$ErrorActionPreference='Stop'
$token=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $TokenFile)).Trim()
if($token -notmatch '^[0-9a-f]{64}$'){throw 'Invalid isolated owner token'}
function Invoke-Key([int]$state) {
    $answer=& $Adb shell content call --uri content://com.example.uavfwinpetest.owner --method input_key --arg $token --extra "symbol:i:$KeySym" --extra "state:i:$state" 2>&1
    if($LASTEXITCODE -ne 0 -or ($answer -join '') -notmatch 'channel_written=true') {
        throw 'Windows input channel unavailable; inspect VM/viewer before retry'
    }
}
if($PSCmdlet.ParameterSetName -eq 'Text') {
    if($Text.Length -gt 2048 -or $Text -match '[\x00-\x1f\x7f]'){throw 'Bounded printable text only; controls use explicit keys'}
    $encoded=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Text))
    $answer=& $Adb shell content call --uri content://com.example.uavfwinpetest.owner --method input_text --arg $token --extra "base64:s:$encoded" 2>&1
    if($LASTEXITCODE -ne 0 -or ($answer -join '') -notmatch 'channel_written=true') {
        throw 'Windows text channel unavailable; inspect VM/viewer before retry'
    }
}elseif($Action -eq 'Press') {
    Invoke-Key 1
    try { Start-Sleep -Milliseconds 40 } finally { Invoke-Key 0 }
}else { Invoke-Key $(if($Action -eq 'Down'){1}else{0}) }
# This confirms app-owned channel writes only, not execution of guest commands.
# Keep owner token and text/password contents out of diagnostic output.
'APP_OWNED_INPUT_WRITTEN=1'
