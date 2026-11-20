param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][ValidatePattern('^(X:\\[a-zA-Z0-9_-]+\.(cmd|txt)|C:\\UAVF\\[a-zA-Z0-9_-]+\.(cmd|txt|xml))$')][string]$Destination,
    [Parameter(Mandatory=$true)][switch]$PromptReady,
    [string]$Adb='C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe',
    [switch]$PlanOnly,
    [switch]$InstalledSession
)
$ErrorActionPreference='Stop'
if(-not $PromptReady){throw 'Verify English-layout WinPE CMD is idle; never type during DISM apply'}
if($InstalledSession -ne $Destination.StartsWith('C:\UAVF\')){throw 'Installed session explicitly required for isolated C:\UAVF staging'}
$stageScript=if($InstalledSession){'C:\UAVF\uavf-stage.js'}else{'X:\uavf-stage.js'}
$content=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $Source))
if($content -match '[^\x00-\x7f]'){throw 'ASCII payload required'}
$content=$content.Replace("`r`n","`n").Replace("`n","`r`n")
$payload=[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($content))
$jsPath=$Destination.Replace('\','\\')
$javascript='var a="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",o="",n=0,v=0;for(var i=0;i!=b.length;i++){var k=a.indexOf(b.charAt(i));if(k==-1)break;v=(v<<6)|k;n+=6;if(n>=8){n-=8;o+=String.fromCharCode((v>>n)&255);}}var f=new ActiveXObject("Scripting.FileSystemObject").CreateTextFile("'+$jsPath+'",false,false);f.Write(o);f.Close();WScript.Echo("FILE_STAGED_BYTES="+o.length);'
$escaped=$javascript.Replace('^','^^').Replace('&','^&').Replace('|','^|').Replace('<','^<').Replace('>','^>')
# Real input proved a multi-kilobyte single line loses its tail. Bound each
# line and pace the existing keyboard path instead of changing transport.
$commands=@('echo var b=""; > '+$stageScript)
for($position=0;$position -lt $payload.Length;$position+=320){
    $part=$payload.Substring($position,[Math]::Min(320,$payload.Length-$position))
    $commands+=('echo b+="'+$part+'"; >> '+$stageScript)
}
$commands+=('echo '+$escaped+' >> '+$stageScript)
foreach($line in $commands){
    if($line.Length -gt 700 -or $line.Contains("'") -or $line.Contains('%')){throw 'Unsafe/bounded input line'}
}
if($PlanOnly){'PLAN_BYTES='+$content.Length; 'INPUT_LINES='+$commands.Count; exit 0}
function Send-Command([string]$line){
    & $Adb shell ("input text '"+$line.Replace(' ','%s')+"'")
    if($LASTEXITCODE -ne 0){throw 'ADB input failed; inspect screen before retry'}
    & $Adb shell input keyevent 66
    if($LASTEXITCODE -ne 0){throw 'ADB Enter failed'}
    Start-Sleep -Milliseconds 800
}
foreach($line in $commands){Send-Command $line}
Send-Command ('cscript //nologo '+$stageScript)
# Stages a file only. Caller must visually verify byte count/no errors before
# invoking the separate provisioning script. No auto reboot or install command.
