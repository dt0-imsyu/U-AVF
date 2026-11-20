[CmdletBinding()]
param([string]$BaseBuild,[string]$OutputDir)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$java='C:\Program Files\Android\Android Studio\jbr\bin'
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt=Join-Path $sdk 'build-tools\36.0.0'
$jar=Join-Path $sdk 'platforms\android-36\android.jar'
$env:JAVA_HOME=Split-Path -Parent $java
$env:Path="$java;$env:Path"
if(Test-Path -LiteralPath $OutputDir) {throw 'Use a fresh output directory to preserve previous APKs'}
New-Item -ItemType Directory -Path $OutputDir,(Join-Path $OutputDir 'classes'),(Join-Path $OutputDir 'dex'),(Join-Path $OutputDir 'package\assets') | Out-Null
Copy-Item -LiteralPath (Join-Path $BaseBuild 'unsigned.apk') -Destination (Join-Path $OutputDir 'unsigned.apk')
& python (Join-Path $root '..\tools\release\generate-platform-integrity.py') --apk (Join-Path $OutputDir 'unsigned.apk') --generated (Join-Path $OutputDir 'generated')
if($LASTEXITCODE -ne 0){throw 'Packaged platform integrity generation failed'}
$generatedJava = @(Get-ChildItem (Join-Path $BaseBuild 'generated') -Recurse -Filter '*.java' | Where-Object Name -ne 'BundledPlatformAssets.java' | Select-Object -ExpandProperty FullName)+@(Get-ChildItem (Join-Path $OutputDir 'generated') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& (Join-Path $root 'compile-java.ps1') -Javac "$java\javac.exe" -AndroidJar $jar -ClassesDir (Join-Path $OutputDir 'classes') -GeneratedSources $generatedJava
$classes=@(Get-ChildItem (Join-Path $OutputDir 'classes') -Recurse -Filter '*.class' | Select-Object -ExpandProperty FullName)
& "$bt\d8.bat" --min-api 36 --lib $jar --output (Join-Path $OutputDir 'dex') $classes
if($LASTEXITCODE -ne 0) {throw 'd8 failed'}
& "$java\jar.exe" uf (Join-Path $OutputDir 'unsigned.apk') -C (Join-Path $OutputDir 'dex') classes.dex
if($LASTEXITCODE -ne 0) {throw 'dex packaging failed'}
$guest=Join-Path $root '..\tools\linux-gnome\guest'
foreach($name in @('uavf-encoded-display.py','uavf-encoded.socket','uavf-encoded@.service','install-uavf-encoded-addon.sh','winavf-frame-bridge.py','uavf-safe-jit.py','stage-uavf-safe-jit.sh')) {
    Copy-Item -LiteralPath (Join-Path $guest $name) -Destination (Join-Path $OutputDir "package\assets\$name")
}
Copy-Item -LiteralPath (Join-Path $root '..\tools\linux-gnome\diagnostics\encoded-glx-pbo-probe.py') -Destination (Join-Path $OutputDir 'package\assets\uavf-encoded-capture.py')
& "$java\jar.exe" uf (Join-Path $OutputDir 'unsigned.apk') -C (Join-Path $OutputDir 'package') assets
if($LASTEXITCODE -ne 0) {throw 'encoded assets packaging failed'}
foreach($name in @('uavf-install-platform-prefix-gdm-share.img','p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img')) {
    Copy-Item -LiteralPath (Join-Path $root "assets\$name") -Destination (Join-Path $OutputDir "package\assets\$name")
}
& "$java\jar.exe" uf (Join-Path $OutputDir 'unsigned.apk') -C (Join-Path $OutputDir 'package') assets
if($LASTEXITCODE -ne 0) {throw 'unified platform assets packaging failed'}
& "$bt\zipalign.exe" -f -P 16 4 (Join-Path $OutputDir 'unsigned.apk') (Join-Path $OutputDir 'aligned.apk')
if($LASTEXITCODE -ne 0) {throw 'zipalign failed'}
& "$bt\apksigner.bat" sign --ks (Join-Path $env:USERPROFILE '.android\debug.keystore') --ks-pass pass:android --key-pass pass:android --out (Join-Path $OutputDir 'WinAVF-test.apk') (Join-Path $OutputDir 'aligned.apk')
if($LASTEXITCODE -ne 0) {throw 'sign failed'}
& "$bt\apksigner.bat" verify --verbose (Join-Path $OutputDir 'WinAVF-test.apk')
if($LASTEXITCODE -ne 0) {throw 'verify failed'}
Get-FileHash -Algorithm SHA256 (Join-Path $OutputDir 'WinAVF-test.apk')
