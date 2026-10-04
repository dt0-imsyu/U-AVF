[CmdletBinding()]
param(
    [string] $OutputDir = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$buildTools = Join-Path $sdk 'build-tools\36.0.0'
$androidJar = Join-Path $sdk 'platforms\android-36\android.jar'
$ndkBin = Join-Path $sdk 'ndk\28.2.13676358\toolchains\llvm\prebuilt\windows-x86_64\bin'
$javaBin = 'C:\Program Files\Android\Android Studio\jbr\bin'
$env:JAVA_HOME = Split-Path -Parent $javaBin
$env:Path = "$javaBin;$env:Path"
$out = if ($OutputDir) { [IO.Path]::GetFullPath($OutputDir) } else { Join-Path $root 'out' }
$generated = Join-Path $out 'generated'
New-Item -ItemType Directory -Force -Path $out, (Join-Path $out 'classes'), (Join-Path $out 'assets'), (Join-Path $out 'dex'), $generated | Out-Null
$packageAssets = Join-Path $out ('apk-assets-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $packageAssets | Out-Null
$appAssets = Join-Path $root 'assets'
if (Test-Path -LiteralPath $appAssets) {
    # Ship only the user-facing Linux paths: the validated temporary VirGL
    # profile and the guided-install platform. Keep diagnostic A/B images in
    # the source tree, but out of the public APK.
    $productAssets = @(
        'p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img',
        'p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-real-gnome.img',
        'p33-gpt-trailer.bin',
        'uavf-install-platform-prefix-gdm-share.img'
    )
    foreach ($assetName in $productAssets) {
        $assetPath = Join-Path $appAssets $assetName
        if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
            throw "Missing required product asset: $assetName"
        }
        Copy-Item -LiteralPath $assetPath -Destination (Join-Path $packageAssets $assetName) -Force
    }
}
$productUiAssets=Join-Path $appAssets 'product-ui'
if(Test-Path -LiteralPath $productUiAssets) {
    Copy-Item -LiteralPath $productUiAssets -Destination $packageAssets -Recurse -Force
}
$clipboardSource = Join-Path $root '..\tools\linux-gnome\guest'
foreach ($encodedAsset in @('uavf-encoded-display.py','uavf-encoded.socket','uavf-encoded@.service','install-uavf-encoded-addon.sh','winavf-frame-bridge.py','uavf-safe-jit.py','stage-uavf-safe-jit.sh')) {
    Copy-Item -LiteralPath (Join-Path $clipboardSource $encodedAsset) -Destination (Join-Path $packageAssets $encodedAsset) -Force
}
Copy-Item -LiteralPath (Join-Path $root '..\tools\linux-gnome\diagnostics\encoded-glx-pbo-probe.py') -Destination (Join-Path $packageAssets 'uavf-encoded-capture.py') -Force
foreach ($clipboardAsset in @('uavf-clipboard.py', 'uavf-clipboard.socket', 'uavf-clipboard@.service', 'install-uavf-clipboard-addon.sh', 'install-uavf-clipboard-user.sh')) {
    Copy-Item -LiteralPath (Join-Path $clipboardSource $clipboardAsset) -Destination (Join-Path $packageAssets $clipboardAsset) -Force
}
$installPrefix = Join-Path $packageAssets 'uavf-install-platform-prefix-gdm-share.img'
foreach ($controlAsset in @('uavf-control.py','uavf-control.socket','uavf-control@.service','install-uavf-control-addon.sh')) {
    Copy-Item -LiteralPath (Join-Path $clipboardSource $controlAsset) -Destination (Join-Path $packageAssets $controlAsset) -Force
}
& python (Join-Path $root '..\tools\linux-gnome\audit-product-display-payload.py') $installPrefix
if ($LASTEXITCODE -ne 0) { throw 'Product display payload differs from current guest runtime sources; rebuild the initrd first.' }
$mainActivity = Get-Content -LiteralPath (Join-Path $root 'src\com\example\winavf\MainActivity.java') -Raw
$prefixMatch = [regex]::Match($mainActivity, 'UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256\s*=\s*"([0-9A-Fa-f]{64})"')
$espMatch = [regex]::Match($mainActivity, 'UBUNTU_INSTALL_PLATFORM_ESP_SHA256\s*=\s*"([0-9A-Fa-f]{64})"')
if (-not $prefixMatch.Success -or -not $espMatch.Success) { throw 'Ubuntu install prefix SHA constants are missing' }
if ((Get-FileHash -LiteralPath $installPrefix -Algorithm SHA256).Hash -ne $prefixMatch.Groups[1].Value) {
    throw 'Ubuntu install platform prefix does not match its embedded SHA-256 constant'
}
$espStream = [IO.File]::OpenRead($installPrefix)
try {
    $espStream.Position = 1MB
    $espHash = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
    $espBuffer = New-Object byte[] (1MB)
    $espRemaining = 126MB
    while ($espRemaining -gt 0) {
        $espRead = $espStream.Read($espBuffer, 0, [int][Math]::Min($espBuffer.Length, $espRemaining))
        if ($espRead -le 0) { throw 'Unexpected end of Ubuntu install platform ESP payload' }
        $espHash.AppendData($espBuffer, 0, $espRead)
        $espRemaining -= $espRead
    }
    $actualEspSha = ([BitConverter]::ToString($espHash.GetHashAndReset())).Replace('-', '')
    $espHash.Dispose()
} finally { $espStream.Dispose() }
if ($actualEspSha -ne $espMatch.Groups[1].Value) {
    throw "Ubuntu install ESP range does not match its embedded SHA-256 constant: $actualEspSha"
}
$wrapper = Join-Path $root '..\..\handoff-compact-2026-08-23\handoff-compact-2026-08-23\winavf-test\out\assets\u-boot-wrapper-v24.Image'
if ((Get-FileHash -LiteralPath $wrapper -Algorithm SHA256).Hash -ne '93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234') { throw 'U-Boot wrapper SHA-256 mismatch' }
Copy-Item -LiteralPath $wrapper -Destination (Join-Path $packageAssets 'u-boot-wrapper-v24.Image') -Force
$source = Join-Path $root 'kernel-first\console-binary-echo.S'
& (Join-Path $ndkBin 'clang.exe') --target=aarch64-none-elf -c $source -o (Join-Path $out 'console-binary-echo.o')
if ($LASTEXITCODE -ne 0) { throw 'echo assembly failed' }
& (Join-Path $ndkBin 'ld.lld.exe') -m aarch64elf -Ttext=0 -o (Join-Path $out 'console-binary-echo.elf') (Join-Path $out 'console-binary-echo.o')
if ($LASTEXITCODE -ne 0) { throw 'echo link failed' }
& (Join-Path $ndkBin 'llvm-objcopy.exe') -O binary (Join-Path $out 'console-binary-echo.elf') (Join-Path $packageAssets 'console-binary-echo.Image')
if ($LASTEXITCODE -ne 0) { throw 'echo image failed' }
$resources = Join-Path $out 'resources.zip'
& (Join-Path $buildTools 'aapt2.exe') compile --dir (Join-Path $root 'res') -o $resources
if ($LASTEXITCODE -ne 0) { throw 'resource compilation failed' }
& (Join-Path $buildTools 'aapt2.exe') link -I $androidJar --manifest (Join-Path $root 'AndroidManifest.xml') -A $packageAssets --auto-add-overlay -R $resources --java $generated --min-sdk-version 36 --target-sdk-version 36 -o (Join-Path $out 'unsigned.apk')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }
$sources = @(Get-ChildItem (Join-Path $root 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName) + @(Get-ChildItem $generated -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& (Join-Path $javaBin 'javac.exe') -source 17 -target 17 -classpath $androidJar -d (Join-Path $out 'classes') $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classes = Get-ChildItem (Join-Path $out 'classes') -Recurse -Filter '*.class' | Select-Object -ExpandProperty FullName
& (Join-Path $buildTools 'd8.bat') --min-api 36 --lib $androidJar --output (Join-Path $out 'dex') $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& (Join-Path $javaBin 'jar.exe') uf (Join-Path $out 'unsigned.apk') -C (Join-Path $out 'dex') classes.dex
if ($LASTEXITCODE -ne 0) { throw 'APK packaging failed' }
$nativeDir = Join-Path $out 'native-package\lib\arm64-v8a'
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
& (Join-Path $ndkBin 'clang.exe') --target=aarch64-linux-android35 -O3 -fPIC -shared -Wall -Wextra -Werror (Join-Path $root 'native\wavf_lz4.c') -o (Join-Path $nativeDir 'libwavf.so')
if ($LASTEXITCODE -ne 0) { throw 'Native WAVF decoder compilation failed' }
& (Join-Path $javaBin 'jar.exe') uf (Join-Path $out 'unsigned.apk') -C (Join-Path $out 'native-package') lib/arm64-v8a/libwavf.so
if ($LASTEXITCODE -ne 0) { throw 'Native WAVF decoder packaging failed' }
$keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path $keystore)) { throw "Missing established signing key: $keystore" }
& (Join-Path $buildTools 'zipalign.exe') -f -P 16 4 (Join-Path $out 'unsigned.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& (Join-Path $buildTools 'apksigner.bat') sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out (Join-Path $out 'WinAVF-test.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& (Join-Path $buildTools 'apksigner.bat') verify --verbose (Join-Path $out 'WinAVF-test.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
Get-FileHash -Algorithm SHA256 (Join-Path $out 'WinAVF-test.apk')
