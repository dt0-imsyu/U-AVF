[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory, [string]$MicrosoftLinker)

$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$out = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $out) { throw 'Use a fresh output directory' }
$wdk = Join-Path $workspace 'tools/wdk-nuget/Microsoft.Windows.WDK.ARM64.10.0.26100.6584/c'
$sdkShared = Join-Path $workspace 'tools/wdk-nuget/Microsoft.Windows.SDK.CPP.10.0.26100.9169/c/Include/10.0.26100.0/shared'
$llvm = Join-Path $workspace 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$includes = "$wdk/Include/10.0.26100.0"
$libs = "$wdk/Lib/10.0.26100.0/km/ARM64"
New-Item -ItemType Directory -Path $out | Out-Null

$flags = @('--target=aarch64-pc-windows-msvc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
    '-ffreestanding', '-fstack-protector-strong', '-mgeneral-regs-only', '-fms-extensions', '-D_ARM64_', '-D_WIN64',
    '-DNTDDI_VERSION=0x0A000010', '-D_WIN32_WINNT=0x0A00',
    '-isystem', "$includes/km", '-isystem', "$includes/shared", '-isystem', "$includes/km/crt",
    '-isystem', $sdkShared)
$objects = foreach ($name in 'admission-driver', 'mavk-viosock') {
    & "$llvm/clang.exe" @flags -c "$PSScriptRoot/$name.c" -o "$out/$name.obj"
    if ($LASTEXITCODE) { throw "ARM64 compile failed for $name" }
    "$out/$name.obj"
}
# Compile the WDDM support library separately from the diagnostic driver.
$renderObjects = foreach ($name in 'render-completion', 'codec-fence', 'codec-buffer', 'codec-device', 'render-worker', 'render-scheduler', 'render-miniport') {
    & "$llvm/clang.exe" @flags -c "$PSScriptRoot/$name.c" -o "$out/$name.obj"
    if ($LASTEXITCODE) { throw "ARM64 WDDM compile failed for $name" }
    "$out/$name.obj"
}
& "$llvm/llvm-lib.exe" "/out:$out/UavfMavkRender.lib" "$out/mavk-viosock.obj" @renderObjects
if ($LASTEXITCODE) { throw 'ARM64 WDDM support library build failed' }
$link = @('/machine:arm64', '/subsystem:native', '/entry:GsDriverEntry', '/Brepro',
    '/nodefaultlib', '/dynamicbase', '/nxcompat', "/out:$out/UavfMavkAdmission.sys") + $objects +
    @("$libs/ntoskrnl.lib", "$libs/hal.lib", "$libs/wdmsec.lib",
      "$libs/bufferoverflowfastfailk.lib", "$libs/libcntpr.lib")
if ($MicrosoftLinker) {
    & (Resolve-Path -LiteralPath $MicrosoftLinker).Path /nologo /driver:wdm @link
} else {
    & "$llvm/ld.lld.exe" -flavor link /driver @link
}
if ($LASTEXITCODE) { throw 'ARM64 driver link failed' }
$probe = @('-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', '-Wl,--no-insert-timestamp',
    "$PSScriptRoot/admission-probe.c", '-o', "$out/UavfMavkAdmission.exe")
& "$llvm/aarch64-w64-mingw32-clang.exe" @probe
if ($LASTEXITCODE) { throw 'ARM64 client link failed' }
$probe = @('-std=c++17', '-O2', '-Wall', '-Wextra', '-Werror', '-static', '-municode', '-Wl,--no-insert-timestamp',
    "$PSScriptRoot/d3d11-draw.cpp", '-o', "$out/d3d11-draw.exe", '-ld3d11', '-ldxgi', '-ld3dcompiler')
& "$llvm/aarch64-w64-mingw32-clang++.exe" @probe
if ($LASTEXITCODE) { throw 'ARM64 D3D11 consumer link failed' }
Get-FileHash -LiteralPath "$out/UavfMavkAdmission.sys", "$out/UavfMavkAdmission.exe", "$out/d3d11-draw.exe", "$out/UavfMavkRender.lib"
