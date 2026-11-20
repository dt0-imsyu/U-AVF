param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$llvm=Join-Path $root 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$sdk='C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0'
$msvc='C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Tools\MSVC\14.51.36231\include'
$wdk=Join-Path $root 'tools/wdk-nuget/Microsoft.Windows.WDK.ARM64.10.0.26100.6584/c/Include'
New-Item -ItemType Directory -Path $output | Out-Null
& "$llvm/x86_64-w64-mingw32-clang++.exe" -std=c++17 -O2 -Wall -Wextra -Werror "$PSScriptRoot/idd-system-frame-policy-test.cpp" -o "$output/policy-test.exe"
if($LASTEXITCODE){throw 'Host frame policy build failed'}
& "$output/policy-test.exe"
if($LASTEXITCODE){throw 'Host frame policy failed'}
'HOST_FRAME_GEOMETRY_POLICY_PASS; NOT_GUEST_FRAME_PASS=1'
$flags=@('--target=aarch64-pc-windows-msvc','-std=c++17','-fms-extensions','-fms-compatibility','-fno-exceptions','-fno-rtti','-DUMDF_DRIVER','-D_WIN32_WINNT=0x0A00','-DNTDDI_VERSION=0x0A00000C','-DIDDCX_VERSION_MAJOR=1','-DIDDCX_VERSION_MINOR=6','-DIDDCX_MINIMUM_VERSION_REQUIRED=6')
foreach($include in @($msvc,"$sdk/ucrt","$sdk/shared","$sdk/um","$wdk/wdf/umdf/2.25","$wdk/10.0.26100.0/um/iddcx/1.6")) {
    if(!(Test-Path -LiteralPath $include)){throw "Missing prerequisite $include"}
    $flags+=@('-isystem',$include)
}
& "$llvm/clang.exe" @flags -c "$PSScriptRoot/idd-system-frame-source.cpp" -o "$output/idd-system-frame-source.obj"
if($LASTEXITCODE){throw 'ARM64 IddCx ABI compile failed'}
& "$llvm/llvm-readobj.exe" --file-headers "$output/idd-system-frame-source.obj"
if($LASTEXITCODE){throw 'COFF inspection failed'}
'ARM64_IDDCX_ABI_COMPILE_ONLY_PASS; NOT_DRIVER_LOAD_OR_FPS_PASS=1'
