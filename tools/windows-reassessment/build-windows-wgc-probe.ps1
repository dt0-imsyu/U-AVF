param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$out=Assert-WindowsBuildOutput $OutputDirectory $workspaceRoot
$llvm=Join-Path $workspaceRoot 'tools\toolchains\llvm-mingw-20260826-clean-extract\llvm-mingw-20260826-ucrt-x86_64\bin'
$sdk='C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\winrt'
if(!(Test-Path -LiteralPath "$sdk/windows.graphics.capture.h")){throw 'Official Windows SDK WinRT ABI headers required'}
New-Item -ItemType Directory -Path $out | Out-Null
& "$llvm/aarch64-w64-mingw32-clang.exe" -Os -ffunction-sections -fdata-sections '-Wl,--gc-sections' -Wall -Wextra -Werror -municode -isystem $sdk "$PSScriptRoot/windows-wgc-probe.c" -o "$out/wgc-probe.exe" -lruntimeobject -ld3d11 -ldxgi -luuid -lgdi32 -luser32
if($LASTEXITCODE){throw 'WGC ARM64 compile failed'}
& "$llvm/llvm-strip.exe" --strip-all "$out/wgc-probe.exe"
if($LASTEXITCODE){throw 'Strip failed'}
Get-Item "$out/wgc-probe.exe" | Select-Object Length
Get-FileHash "$out/wgc-probe.exe" -Algorithm SHA256
