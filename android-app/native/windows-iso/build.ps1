param(
    [Parameter(Mandatory)][string]$UdfreadTarball,
    [Parameter(Mandatory)][string]$OutputDir,
    [string]$Ndk = "$env:LOCALAPPDATA/Android/Sdk/ndk/28.2.13676358"
)
$ErrorActionPreference = 'Stop'
$archive = (Resolve-Path -LiteralPath $UdfreadTarball).Path
$expected = 'E3ED8DC7FAB472AD382B1B6CD068F1DC0084C34E5EC2C460C1DD84FA14A9D368C4D7AF08A23EFC5057E7F021F5812222C01247D5F304632716C94FC8C689A1A2'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $expected) { throw 'libudfread source digest mismatch' }
if (Test-Path -LiteralPath $OutputDir) { throw 'Use a fresh output directory' }
$out = [IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Path $out | Out-Null
tar -xf $archive -C $out
if ($LASTEXITCODE) { throw 'libudfread extraction failed' }
python "$PSScriptRoot/enable-udfread-partition.py" "$out/libudfread-1.2.0"
if ($LASTEXITCODE) { throw 'libudfread partition patch failed' }
$verify = Join-Path $PSScriptRoot '../../../tools/release/verify-udfread-partition.py'
if (Test-Path -LiteralPath $verify) {
    python $verify --source "$out/libudfread-1.2.0"
    if ($LASTEXITCODE) { throw 'libudfread partition guard failed' }
}
$source = "$out/libudfread-1.2.0/src"
$clang = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
$common = @('--target=aarch64-linux-android35','-fPIC','-O2','-shared','-Wl,-z,relro,-z,now','-Wl,-z,max-page-size=16384')
$readerArgs = @('-Wl,-soname,libudfread.so','-DUDFREAD_API_EXPORT','-DHAVE_UNISTD_H','-DHAVE_FCNTL_H',
    "$source/default_blockinput.c","$source/ecma167.c","$source/udfread.c",'-o',"$out/libudfread.so")
& $clang @common @readerArgs
if ($LASTEXITCODE) { throw 'libudfread build failed' }
$probeArgs = @('-Wall','-Wextra','-Werror','-Wl,-soname,libuavf_windows_iso.so',"-I$source",
    "$PSScriptRoot/windows_iso.c","$PSScriptRoot/windows_iso_jni.c","-L$out",'-ludfread','-o',"$out/libuavf_windows_iso.so")
& $clang @common @probeArgs
if ($LASTEXITCODE) { throw 'ISO probe build failed' }
Copy-Item -LiteralPath "$out/libudfread-1.2.0/COPYING" -Destination "$out/libudfread-COPYING"
Copy-Item -LiteralPath $archive -Destination "$out/libudfread-1.2.0.tar.xz"
