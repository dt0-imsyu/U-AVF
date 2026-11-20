$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '..\..\..\..'))
$out=Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004\shell-owner-api-audit'
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$java='C:\Program Files\Android\Android Studio\jbr\bin'
$env:JAVA_HOME=Split-Path -Parent $java;$env:Path="$java;$env:Path"
New-Item -ItemType Directory -Force -Path $out,(Join-Path $out 'classes'),(Join-Path $out 'dex') | Out-Null
& "$java\javac.exe" -source 17 -target 17 -classpath "$sdk\platforms\android-36\android.jar" -d "$out\classes" "$taskRoot\AvfOwnerApiAudit.java" "$taskRoot\AvfOwnerBridge.java"
if($LASTEXITCODE){throw 'javac'}
& "$java\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& "$sdk\build-tools\36.0.0\d8.bat" --min-api 36 --lib "$sdk\platforms\android-36\android.jar" --output "$out\dex" "$out\classes.jar"
if($LASTEXITCODE){throw 'd8'}
& "$java\jar.exe" cf "$out\audit.jar" -C "$out\dex" classes.dex
Get-FileHash "$out\audit.jar"
