param([string]$OutputDirectory='',[string]$MavkBundle='',[switch]$WddmAdmission,
      [string]$WddmCodecLibrary='')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '..\..\..'))
$out=[IO.Path]::GetFullPath((Join-Path $repoRoot '..\build-logs\windows-post-linux-reassessment-20261004\android-owner'))
if($WddmCodecLibrary -and !$WddmAdmission){throw 'Codec payload requires isolated WDDM admission'}
if($OutputDirectory) {
    . "$taskRoot/../windows-build-output.ps1"
    if($WddmAdmission){
        if(!$MavkBundle){throw 'WDDM diagnostic build needs the existing Mavk bundle'}
        $candidateOutput=Assert-WindowsBuildOutput $OutputDirectory (Split-Path -Parent $repoRoot)
    }else{
        $candidateOutput=Assert-WindowsBuildOutput $OutputDirectory (Split-Path -Parent $repoRoot)
    }
    $out=$candidateOutput
}elseif($WddmAdmission){throw 'Use a fresh output directory for WDDM diagnostics'}
$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$bt=Join-Path $sdk 'build-tools\36.0.0'
$jar=Join-Path $sdk 'platforms\android-36\android.jar'
$java='C:\Program Files\Android\Android Studio\jbr\bin'
$env:JAVA_HOME=Split-Path -Parent $java;$env:Path="$java;$env:Path"
New-Item -ItemType Directory -Force -Path $out,(Join-Path $out 'classes'),(Join-Path $out 'dex') | Out-Null
& "$bt\aapt2.exe" link -I $jar --manifest "$taskRoot\AndroidManifest.xml" --min-sdk-version 36 --target-sdk-version 36 -o "$out\unsigned.apk"
if($LASTEXITCODE){throw 'aapt2'}
$appSource="$repoRoot\android-app\src\com\example\winavf"
$appFiles=@('EncodedDisplayProbe','GuestKeyboard','UvfDiagnosticPacket','GuestClipboard','ClipboardWire','WorkspaceConfig',
    'WindowsAvfOwner','WindowsKdWire','WindowsKdBootProfile','WindowsKdSession','WindowsKdSmpBootstrap','WindowsKdUart') | ForEach-Object { "$appSource\$_.java" }
& "$java\javac.exe" -encoding UTF-8 -source 17 -target 17 -classpath $jar -d "$out\classes" "$taskRoot\WindowsDiagnosticActivity.java" "$taskRoot\WindowsOwnerProvider.java" "$taskRoot\WindowsVmOwnerService.java" "$taskRoot\AvfOwnerBridge.java" "$taskRoot\WindowsChannelPolicy.java" "$taskRoot\WindowsClipboardProbe.java" @appFiles
if($LASTEXITCODE){throw 'javac'}
if($MavkBundle){
    $mavkRoot=Join-Path (Split-Path -Parent $repoRoot) 'Mavk'
    & "$java/javac.exe" -source 17 -target 17 -classpath $jar -d "$out/classes" "$mavkRoot/adapters/android/org/mavk/bridge/BridgeNative.java" "$mavkRoot/adapters/android/org/mavk/bridge/BridgeEndpoint.java"
    if($LASTEXITCODE){throw 'Optional Mavk descriptor adapter compile'}
    New-Item -ItemType Directory -Force -Path "$out/mavk-payload/lib/arm64-v8a","$out/mavk-payload/assets/mavk" | Out-Null
    Copy-Item -LiteralPath "$MavkBundle/payload/lib/arm64-v8a/libmavk_probe.so" -Destination "$out/mavk-payload/lib/arm64-v8a/"
    Copy-Item -LiteralPath "$MavkBundle/payload/assets/verify.comp.spv" -Destination "$out/mavk-payload/assets/mavk/"
    if($WddmCodecLibrary){Copy-Item -LiteralPath $WddmCodecLibrary -Destination "$out/mavk-payload/lib/arm64-v8a/libmavk_runtime_probe.so"}
    if(Test-Path -LiteralPath "$MavkBundle/payload/lib/arm64-v8a/libVkLayer_khronos_validation.so"){Copy-Item -LiteralPath "$MavkBundle/payload/lib/arm64-v8a/libVkLayer_khronos_validation.so" -Destination "$out/mavk-payload/lib/arm64-v8a/"}
}
& "$java\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& "$bt\d8.bat" --min-api 36 --lib $jar --output "$out\dex" "$out\classes.jar"
if($LASTEXITCODE){throw 'd8'}
& "$java\jar.exe" uf "$out\unsigned.apk" -C "$out\dex" classes.dex
$native=Join-Path $out 'native\lib\arm64-v8a'
New-Item -ItemType Directory -Force -Path $native | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot '..\build-logs\windows-post-linux-reassessment-20261004\kd-base64-ingress-written-trace') -Destination (Join-Path $native 'libuavf_ingress.so')
Copy-Item -LiteralPath (Join-Path $repoRoot 'tools\shell-serial-loopback\console-file-base64-tailer') -Destination (Join-Path $native 'libuavf_console_tail.so')
& "$java\jar.exe" uf "$out\unsigned.apk" -C "$out\native" lib
if($LASTEXITCODE){throw 'Exact native UART helpers packaging'}
if($MavkBundle){& "$java/jar.exe" uf "$out/unsigned.apk" -C "$out/mavk-payload" lib -C "$out/mavk-payload" assets;if($LASTEXITCODE){throw 'Optional Mavk packaging'}}
& "$bt\zipalign.exe" -f -P 16 4 "$out\unsigned.apk" "$out\aligned.apk"
if($LASTEXITCODE){throw 'align'}
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$out\U-AVF-Windows-diagnostic.apk" "$out\aligned.apk"
if($LASTEXITCODE){throw 'sign'}
& "$bt\apksigner.bat" verify "$out\U-AVF-Windows-diagnostic.apk"
if($LASTEXITCODE){throw 'verify'}
Get-FileHash "$out\U-AVF-Windows-diagnostic.apk"
