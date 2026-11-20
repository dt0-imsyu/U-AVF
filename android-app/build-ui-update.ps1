[CmdletBinding()]
param([Parameter(Mandatory)][string]$BaseBuild,[Parameter(Mandatory)][string]$GeneratedSources,[Parameter(Mandatory)][string]$OutputDir)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$java='C:\Program Files\Android\Android Studio\jbr\bin'
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt=Join-Path $sdk 'build-tools\36.0.0'
$androidJar=Join-Path $sdk 'platforms\android-36\android.jar'
if(Test-Path -LiteralPath $OutputDir) {throw 'Output must be a fresh directory'}
New-Item -ItemType Directory -Path $OutputDir,(Join-Path $OutputDir 'classes'),(Join-Path $OutputDir 'dex') | Out-Null
Copy-Item -LiteralPath (Join-Path $BaseBuild 'unsigned.apk') -Destination (Join-Path $OutputDir 'unsigned.apk')
& python (Join-Path $root '..\tools\release\generate-platform-integrity.py') --apk (Join-Path $OutputDir 'unsigned.apk') --generated (Join-Path $OutputDir 'generated')
if($LASTEXITCODE -ne 0){throw 'Packaged platform integrity generation failed'}
$generatedJava = @(Get-ChildItem -LiteralPath $GeneratedSources -Recurse -Filter '*.java' | Where-Object Name -ne 'BundledPlatformAssets.java' | Select-Object -ExpandProperty FullName)+@(Get-ChildItem (Join-Path $OutputDir 'generated') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& (Join-Path $root 'compile-java.ps1') -Javac "$java\javac.exe" -AndroidJar $androidJar -ClassesDir (Join-Path $OutputDir 'classes') -GeneratedSources $generatedJava
$classes=@(Get-ChildItem (Join-Path $OutputDir 'classes') -Recurse -Filter '*.class' | Select-Object -ExpandProperty FullName)
$env:JAVA_HOME=Split-Path -Parent $java
& "$bt\d8.bat" --min-api 36 --lib $androidJar --output (Join-Path $OutputDir 'dex') $classes
if($LASTEXITCODE -ne 0) {throw 'd8 failed'}
& "$java\jar.exe" uf (Join-Path $OutputDir 'unsigned.apk') -C (Join-Path $OutputDir 'dex') classes.dex
if($LASTEXITCODE -ne 0) {throw 'dex packaging failed'}
$uiAssets=Join-Path $root 'assets\product-ui'
if(Test-Path -LiteralPath $uiAssets) {
    $uiPackage=Join-Path $OutputDir 'ui-package\assets'
    New-Item -ItemType Directory -Force -Path $uiPackage | Out-Null
    Copy-Item -LiteralPath $uiAssets -Destination $uiPackage -Recurse
    & "$java\jar.exe" uf (Join-Path $OutputDir 'unsigned.apk') -C (Split-Path -Parent $uiPackage) assets/product-ui
    if($LASTEXITCODE -ne 0) {throw 'UI asset packaging failed'}
}
& "$bt\zipalign.exe" -f -P 16 4 (Join-Path $OutputDir 'unsigned.apk') (Join-Path $OutputDir 'aligned.apk')
if($LASTEXITCODE -ne 0) {throw 'zipalign failed'}
& "$bt\apksigner.bat" sign --ks (Join-Path $env:USERPROFILE '.android\debug.keystore') --ks-pass pass:android --key-pass pass:android --out (Join-Path $OutputDir 'WinAVF-test.apk') (Join-Path $OutputDir 'aligned.apk')
if($LASTEXITCODE -ne 0) {throw 'signing failed'}
& "$bt\apksigner.bat" verify (Join-Path $OutputDir 'WinAVF-test.apk')
if($LASTEXITCODE -ne 0) {throw 'signature verification failed'}
# UI-only build must preserve ALL assets/resources/native libraries, not just a selected prefix.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$before=[IO.Compression.ZipFile]::OpenRead((Join-Path $BaseBuild 'unsigned.apk'))
$after=[IO.Compression.ZipFile]::OpenRead((Join-Path $OutputDir 'WinAVF-test.apk'))
try {
    $expected=@($before.Entries | Where-Object {-not $_.FullName.EndsWith('/') -and $_.FullName -ne 'classes.dex' -and -not $_.FullName.StartsWith('META-INF/')})
    foreach($entry in $expected) {
        $other=$after.GetEntry($entry.FullName)
        if($null -eq $other -or $entry.Length -ne $other.Length) {throw "Preserved entry mismatch: $($entry.FullName)"}
        $a=$entry.Open();$b=$other.Open();$ha=[Security.Cryptography.SHA256]::Create();$hb=[Security.Cryptography.SHA256]::Create()
        try {if([Convert]::ToHexString($ha.ComputeHash($a)) -ne [Convert]::ToHexString($hb.ComputeHash($b))) {throw "Preserved entry hash mismatch: $($entry.FullName)"}}
        finally {$a.Dispose();$b.Dispose();$ha.Dispose();$hb.Dispose()}
    }
    "PRESERVED_APK_ENTRIES=PASS ($($expected.Count))"
    if(Test-Path -LiteralPath $uiAssets) {
        $uiFiles=@(Get-ChildItem -LiteralPath $uiAssets -File)
        foreach($uiFile in $uiFiles) {
            $entry=$after.GetEntry("assets/product-ui/$($uiFile.Name)")
            if($null -eq $entry -or $entry.Length -ne $uiFile.Length) {throw "Missing UI asset: $($uiFile.Name)"}
            $packed=$entry.Open();$local=[IO.File]::OpenRead($uiFile.FullName);$sha=[Security.Cryptography.SHA256]::Create()
            try {if([Convert]::ToHexString($sha.ComputeHash($packed)) -ne [Convert]::ToHexString($sha.ComputeHash($local))) {throw "UI asset hash mismatch: $($uiFile.Name)"}}
            finally {$packed.Dispose();$local.Dispose();$sha.Dispose()}
        }
        "PRODUCT_UI_ASSETS=PASS ($($uiFiles.Count))"
    }
} finally {$before.Dispose();$after.Dispose()}
Get-FileHash -Algorithm SHA256 (Join-Path $OutputDir 'WinAVF-test.apk')
