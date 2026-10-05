[CmdletBinding()]
param([Parameter(Mandatory)][string]$BaseApk,[Parameter(Mandatory)][string]$OutputDir,
      [string]$VersionName='1.0',[int]$VersionCode=14,[switch]$QaIsolated)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
$java='C:\Program Files\Android\Android Studio\jbr\bin'
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt=Join-Path $sdk 'build-tools\36.0.0'
$androidJar=Join-Path $sdk 'platforms\android-36\android.jar'
$python='C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe'
if(Test-Path -LiteralPath $OutputDir){throw 'Fresh output directory required'}
$qaArgs=@()
if($QaIsolated){$qaArgs=@('--qa-isolated')}
& $python (Join-Path $root '..\tools\release\assemble-release-base.py') --base $BaseApk --output $OutputDir --aapt2 "$bt\aapt2.exe" --android-jar $androidJar --version-name $VersionName --version-code $VersionCode @qaArgs
if($LASTEXITCODE -ne 0){throw 'Release base failed'}
& $python (Join-Path $root '..\tools\release\generate-platform-integrity.py') --apk "$OutputDir\unsigned.apk" --generated "$OutputDir\generated"
if($LASTEXITCODE -ne 0){throw 'Packaged platform integrity generation failed'}
New-Item -ItemType Directory -Path "$OutputDir\classes","$OutputDir\dex" | Out-Null
$sources=@(Get-ChildItem "$root\src" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)+@(Get-ChildItem "$OutputDir\generated" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& "$java\javac.exe" -source 17 -target 17 -classpath $androidJar -d "$OutputDir\classes" $sources
if($LASTEXITCODE -ne 0){throw 'Release javac failed'}
$env:JAVA_HOME=Split-Path -Parent $java
& "$java\jar.exe" cf "$OutputDir\classes.jar" -C "$OutputDir\classes" .
if($LASTEXITCODE -ne 0){throw 'Release class archive failed'}
& "$bt\d8.bat" --min-api 36 --lib $androidJar --output "$OutputDir\dex" "$OutputDir\classes.jar"
if($LASTEXITCODE -ne 0){throw 'Release dex failed'}
& "$java\jar.exe" uf "$OutputDir\unsigned.apk" -C "$OutputDir\dex" classes.dex
if($LASTEXITCODE -ne 0){throw 'Release dex packaging failed'}
& "$bt\zipalign.exe" -f -P 16 4 "$OutputDir\unsigned.apk" "$OutputDir\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Release alignment failed'}

# Private signing material stays outside the repository and the release kit.
$keyDir=Join-Path $env:USERPROFILE '.android\uavf-release'
$key=Join-Path $keyDir 'uavf-release.jks'
$credential=Join-Path $keyDir 'store-password.dpapi'
if((Test-Path $key) -ne (Test-Path $credential)){throw 'Partial signing key state; do not overwrite'}
if(-not (Test-Path $key)) {
    New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
    $principal=[Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $keyDir /inheritance:r /grant:r "${principal}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if($LASTEXITCODE -ne 0){throw 'Private signing directory ACL failed'}
    $secret=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))
    $protected=ConvertFrom-SecureString (ConvertTo-SecureString $secret -AsPlainText -Force)
    [IO.File]::WriteAllText($credential,$protected)
    $env:UAVF_RELEASE_STORE_PASSWORD=$secret
    try {
        & "$java\keytool.exe" -genkeypair -keystore $key -storetype JKS -alias uavf-release -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=U-AVF Release' -storepass:env UAVF_RELEASE_STORE_PASSWORD -keypass:env UAVF_RELEASE_STORE_PASSWORD
        if($LASTEXITCODE -ne 0){throw 'Release signing key generation failed'}
    } finally {Remove-Item Env:\UAVF_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue;$secret=$null}
}
$secure=ConvertTo-SecureString ([IO.File]::ReadAllText($credential))
$env:UAVF_RELEASE_STORE_PASSWORD=[Net.NetworkCredential]::new('', $secure).Password
try {
    & "$bt\apksigner.bat" sign --ks $key --ks-key-alias uavf-release --ks-pass env:UAVF_RELEASE_STORE_PASSWORD --key-pass env:UAVF_RELEASE_STORE_PASSWORD --out "$OutputDir\U-AVF-$VersionName-release.apk" "$OutputDir\aligned.apk"
    if($LASTEXITCODE -ne 0){throw 'Release signing failed'}
} finally {Remove-Item Env:\UAVF_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue}
& "$bt\apksigner.bat" verify --verbose --print-certs "$OutputDir\U-AVF-$VersionName-release.apk"
if($LASTEXITCODE -ne 0){throw 'Release signature verification failed'}
& "$bt\zipalign.exe" -c -P 16 4 "$OutputDir\U-AVF-$VersionName-release.apk"
if($LASTEXITCODE -ne 0){throw 'Release alignment verification failed'}
Get-FileHash -Algorithm SHA256 "$OutputDir\U-AVF-$VersionName-release.apk"
