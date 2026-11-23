param(
    [Parameter(Mandatory)][string]$FatFsZip,
    [Parameter(Mandatory)][string]$IsoLibraryDir,
    [Parameter(Mandatory)][string]$OutputDir,
    [string]$Ndk = "$env:LOCALAPPDATA/Android/Sdk/ndk/28.2.13676358"
)
$ErrorActionPreference = 'Stop'
$archive = (Resolve-Path -LiteralPath $FatFsZip).Path
$expected = '99F7DC1F7E095356E4A9E3DBE29959090D8B948AFE2BBC5441E52FDF4B85449E'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'FatFs source digest mismatch' }
if (Test-Path -LiteralPath $OutputDir) { throw 'Use a fresh output directory' }
$out = [IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Path $out | Out-Null
$iso = (Resolve-Path -LiteralPath $IsoLibraryDir).Path
if (-not (Test-Path -LiteralPath "$iso/libuavf_windows_iso.so" -PathType Leaf)) {
    throw 'Build the ISO reader with native/windows-iso/build.ps1 first.'
}
tar -xf $archive -C $out source/ff.c source/ff.h source/diskio.h source/ffconf.h source/ffunicode.c
if ($LASTEXITCODE) { throw 'FatFs extraction failed' }
$configPath = "$out/source/ffconf.h"
$config = [IO.File]::ReadAllText($configPath)
$options = @{
    FF_FS_NORTC = 1
    FF_USE_MKFS = 1
    FF_USE_LABEL = 1
    FF_USE_LFN = 2
    FF_LFN_UNICODE = 2
    FF_CODE_PAGE = 437
}
foreach ($option in $options.GetEnumerator()) {
    $pattern = "(?m)^(#define[ \t]+$($option.Key)[ \t]+)\d+\b"
    if ([regex]::Matches($config, $pattern).Count -ne 1) { throw "Unexpected FatFs option $($option.Key)" }
    $config = [regex]::Replace($config, $pattern, ('${1}' + $option.Value))
}
[IO.File]::WriteAllText($configPath, $config)
$clang = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
$common = @('--target=aarch64-linux-android35','-fPIC','-O2',"-I$out/source")
& $clang @common -c "$out/source/ff.c" -o "$out/ff.o"
if ($LASTEXITCODE) { throw 'FatFs compile failed' }
& $clang @common -c "$out/source/ffunicode.c" -o "$out/ffunicode.o"
if ($LASTEXITCODE) { throw 'FatFs Unicode compile failed' }
$linkArgs = @('-Wall','-Wextra','-Werror','-shared','-Wl,--no-undefined,-z,relro,-z,now,-z,max-page-size=16384',
    '-Wl,-soname,libuavf_windows_setup_fat.so',
    "$PSScriptRoot/windows_setup_fat.c","$out/ff.o","$out/ffunicode.o",
    "-L$iso",'-luavf_windows_iso','-o',"$out/libuavf_windows_setup_fat.so")
& $clang @common @linkArgs
if ($LASTEXITCODE) { throw 'Setup FAT JNI link failed' }
