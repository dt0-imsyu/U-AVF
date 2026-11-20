param(
    [Parameter(Mandatory)][string]$FatFsZip,
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
tar -xf $archive -C $out source/ff.c source/ff.h source/diskio.h source/ffconf.h
if ($LASTEXITCODE) { throw 'FatFs extraction failed' }
$configPath = "$out/source/ffconf.h"
$config = [IO.File]::ReadAllText($configPath)
$pattern = '(?m)^(#define[ \t]+FF_FS_NORTC[ \t]+)0\b'
if ([regex]::Matches($config, $pattern).Count -ne 1) { throw 'Unexpected FatFs configuration' }
[IO.File]::WriteAllText($configPath, [regex]::Replace($config, $pattern, '${1}1'))
$clang = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
$common = @('--target=aarch64-linux-android35','-fPIC','-O2',"-I$out/source")
& $clang @common -c "$out/source/ff.c" -o "$out/ff.o"
if ($LASTEXITCODE) { throw 'FatFs compile failed' }
$linkArgs = @('-Wall','-Wextra','-Werror','-shared','-Wl,--no-undefined,-z,relro,-z,now,-z,max-page-size=16384',
    "$PSScriptRoot/windows_setup_fat.c","$out/ff.o",'-o',"$out/libuavf_windows_setup_fat.so")
& $clang @common @linkArgs
if ($LASTEXITCODE) { throw 'Setup FAT JNI link failed' }
