[CmdletBinding()]
param([Parameter(Mandatory)][string]$BaseApk,[Parameter(Mandatory)][string]$OutputDir,
      [string]$VersionName='1.0',[int]$VersionCode=14,[switch]$QaIsolated,
      [string]$MavkRuntimeDir='',[string]$MavkNativeLibrary='',[switch]$RequireUbuntuDesktopChoices,
      [string]$WindowsDisplayBroker='',[string]$WindowsSetupFatLibrary='',[string]$WindowsGuidedProfile='',
      [string]$WindowsIsoLibrary='',[string]$UdfreadLibrary='',
      [string]$JdkDir=$env:JAVA_HOME,[string]$SdkDir=$env:ANDROID_HOME,[string]$Python='python')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $JdkDir) { $JdkDir = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
if (-not $SdkDir) { $SdkDir = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$java=Join-Path $JdkDir 'bin'
$bt=Join-Path $SdkDir 'build-tools\36.0.0'
$androidJar=Join-Path $SdkDir 'platforms\android-36\android.jar'
foreach ($tool in @("$java\javac.exe", "$java\jar.exe", "$java\keytool.exe", "$bt\aapt2.exe",
        "$bt\d8.bat", "$bt\zipalign.exe", "$bt\apksigner.bat", $androidJar)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Missing Android build dependency: $tool" }
}
Get-Command $Python -CommandType Application -ErrorAction Stop | Out-Null
if(Test-Path -LiteralPath $OutputDir){throw 'Fresh output directory required'}
$qaArgs=@()
if($QaIsolated){$qaArgs=@('--qa-isolated')}
if($RequireUbuntuDesktopChoices){$qaArgs+=@('--require-ubuntu-desktop-choices')}
if([bool]$MavkRuntimeDir -ne [bool]$MavkNativeLibrary){throw 'Mavk guest and host must be supplied together'}
if($MavkRuntimeDir){$qaArgs+=@('--mavk-runtime-dir',$MavkRuntimeDir,'--mavk-native-library',$MavkNativeLibrary)}
if([bool]$WindowsDisplayBroker -ne [bool]$WindowsSetupFatLibrary){throw 'Windows display broker and matching FAT library must be supplied together'}
if($WindowsDisplayBroker){$qaArgs+=@('--windows-display-broker',$WindowsDisplayBroker,'--windows-setup-fat-library',$WindowsSetupFatLibrary)}
if($WindowsGuidedProfile){$qaArgs+=@('--windows-guided-profile',$WindowsGuidedProfile)}
if($WindowsIsoLibrary){$qaArgs+=@('--windows-iso-library',$WindowsIsoLibrary)}
if($UdfreadLibrary){$qaArgs+=@('--udfread-library',$UdfreadLibrary)}
& $python (Join-Path $root '..\tools\release\assemble-release-base.py') --base $BaseApk --output $OutputDir --aapt2 "$bt\aapt2.exe" --android-jar $androidJar --version-name $VersionName --version-code $VersionCode @qaArgs
if($LASTEXITCODE -ne 0){throw 'Release base failed'}
& $python (Join-Path $root '..\tools\release\generate-platform-integrity.py') --apk "$OutputDir\unsigned.apk" --generated "$OutputDir\generated"
if($LASTEXITCODE -ne 0){throw 'Packaged platform integrity generation failed'}
New-Item -ItemType Directory -Path "$OutputDir\classes","$OutputDir\dex" | Out-Null
$generatedJava = @(Get-ChildItem "$OutputDir\generated" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& (Join-Path $root 'compile-java.ps1') -Javac "$java\javac.exe" -AndroidJar $androidJar -ClassesDir "$OutputDir\classes" -GeneratedSources $generatedJava
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
