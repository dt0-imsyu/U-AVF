param(
    [Parameter(Mandatory)][string]$WimlibTarball,
    [Parameter(Mandatory)][string]$OutputDir,
    [string]$Ndk = "$env:LOCALAPPDATA/Android/Sdk/ndk/28.2.13676358",
    [string]$Bash = ''
)
$ErrorActionPreference = 'Stop'
if (-not $Bash) {
    # Resolve bash instead of pinning one developer's MSYS2 install path.
    $candidates = @("$env:MSYS2_ROOT\usr\bin\bash.exe", 'C:\msys64\usr\bin\bash.exe')
    $Bash = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } |
        Select-Object -First 1
    if (-not $Bash) {
        $found = Get-Command bash.exe -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($found) { $Bash = $found.Source }
    }
    if (-not $Bash) { throw 'bash.exe not found; pass -Bash or set MSYS2_ROOT' }
}
$archive = (Resolve-Path -LiteralPath $WimlibTarball).Path
$expected = '84221A3ABD5B91228F15F8E6065C335A336237B5738197B75BF419EEA561A194'
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected) { throw 'wimlib source digest mismatch' }
if (Test-Path -LiteralPath $OutputDir) { throw 'Use a fresh output directory' }
$out = [IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Path $out | Out-Null
tar -xf $archive -C $out
if ($LASTEXITCODE) { throw 'wimlib extraction failed' }
$source = "$out/wimlib-1.14.5"
python "$PSScriptRoot/enable-wimlib-fd.py" $source
if ($LASTEXITCODE) { throw 'wimlib patch failed' }
$env:UAVF_WIMLIB_SOURCE = $source
$env:UAVF_WIMLIB_NDK_BIN = "$Ndk/toolchains/llvm/prebuilt/windows-x86_64/bin"
$build = @'
set -eu
export PATH=/usr/bin:$PATH
ndk=$(cygpath -u "$UAVF_WIMLIB_NDK_BIN")
cd "$(cygpath -u "$UAVF_WIMLIB_SOURCE")"
export CC="$ndk/clang.exe --target=aarch64-linux-android35"
export AR="$ndk/llvm-ar.exe" RANLIB="$ndk/llvm-ranlib.exe" NM="$ndk/llvm-nm.exe"
export CFLAGS="-O2 -fPIC"
# Both optional pkg-config dependencies are disabled.
export PKG_CONFIG=/usr/bin/true
./configure --host=aarch64-linux-android --without-ntfs-3g --without-fuse --disable-shared --enable-static
make -j4 libwim.la
'@
$buildScript = "$out/build-android.sh".Replace('\', '/')
[IO.File]::WriteAllText($buildScript, $build.Replace("`r`n", "`n") + "`n")
& $Bash $buildScript
if ($LASTEXITCODE) { throw 'wimlib Android build failed' }
$linkArgs = @('--target=aarch64-linux-android35','-fPIC','-O2','-shared','-Wall','-Wextra','-Werror',
    '-Wl,-z,relro,-z,now','-Wl,-z,max-page-size=16384','-Wl,--exclude-libs,ALL',
    '-Wl,-soname,libuavf_windows_wim.so',"-I$source/include",
    "$PSScriptRoot/windows_wim_split_jni.c","$source/.libs/libwim.a",'-o',"$out/libuavf_windows_wim.so")
& "$env:UAVF_WIMLIB_NDK_BIN/clang.exe" @linkArgs
if ($LASTEXITCODE) { throw 'WIM JNI build failed' }
Copy-Item -LiteralPath "$source/COPYING.LGPL" -Destination "$out/wimlib-COPYING.LGPL"
Copy-Item -LiteralPath $archive -Destination "$out/wimlib-1.14.5.tar.gz"
