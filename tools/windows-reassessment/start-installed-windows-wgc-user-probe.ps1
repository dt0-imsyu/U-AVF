param([Parameter(Mandatory=$true)][string]$TokenFile)
$ErrorActionPreference='Stop'
# Call only after visually confirming Windows Run (Win+R) is open. The shell
# inherits the authenticated desktop user's token, not our SYSTEM producer.
# Avoid Windows Terminal's malformed Unicode/VT handling of digits and complex
# punctuation observed in CID2283. Our encoder refuses an existing report, so
# this never overwrites a prior file. After completion execute the simple
# `type wgc-source-report.txt` separately from a visibly focused console.
$command='cmd /k winpe-encode.exe . --wgc-probe-only'
if($command.Length -gt 255){throw 'Windows Run command bound'}
& "$PSScriptRoot/send-installed-windows-input.ps1" -TokenFile $TokenFile -KeySym 65507 -Action Down
try { & "$PSScriptRoot/send-installed-windows-input.ps1" -TokenFile $TokenFile -KeySym 97 }
finally { & "$PSScriptRoot/send-installed-windows-input.ps1" -TokenFile $TokenFile -KeySym 65507 -Action Up }
& "$PSScriptRoot/send-installed-windows-input.ps1" -TokenFile $TokenFile -Text $command
& "$PSScriptRoot/send-installed-windows-input.ps1" -TokenFile $TokenFile -KeySym 65293
'WGC_USER_PROBE_COMMAND_WRITTEN_ONLY; WAIT_FOR_ACTUAL_REPORT=1'
