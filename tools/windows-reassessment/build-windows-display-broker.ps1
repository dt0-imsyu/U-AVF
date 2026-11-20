[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$workspaceRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$destination=[IO.Path]::GetFullPath($OutputDirectory)
if([IO.Path]::GetPathRoot($destination) -eq 'D:\'){throw 'D is failing; do not build there'}
if(Test-Path -LiteralPath $destination){throw 'Preserve previous build'}
$toolchain=Join-Path $workspaceRoot 'tools\toolchains\llvm-mingw-20260826-clean-extract\llvm-mingw-20260826-ucrt-x86_64\bin'
New-Item -ItemType Directory -Path $destination | Out-Null
& (Join-Path $toolchain 'aarch64-w64-mingw32-clang.exe') -O2 -Wall -Wextra -municode '-Wl,--no-insert-timestamp' `
  (Join-Path $PSScriptRoot 'windows-display-service.c') -o (Join-Path $destination 'winpe-screen-service.exe') -luserenv -ladvapi32
if($LASTEXITCODE){throw 'ARM64 broker build failed'}
& (Join-Path $toolchain 'llvm-strip.exe') --strip-all (Join-Path $destination 'winpe-screen-service.exe')
if($LASTEXITCODE){throw 'Broker strip failed'}
if((Get-Item -LiteralPath (Join-Path $destination 'winpe-screen-service.exe')).Length -gt 71168){throw 'Must fit existing isolated helper slot'}
& (Join-Path $toolchain 'x86_64-w64-mingw32-clang.exe') -O2 -municode `
  (Join-Path $PSScriptRoot 'windows-display-service-selftest.c') -o (Join-Path $destination 'broker-selftest.exe') -luserenv -ladvapi32
if($LASTEXITCODE){throw 'Broker selftest build failed'}
& (Join-Path $destination 'broker-selftest.exe')
if($LASTEXITCODE){throw 'Broker selftest failed'}
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $destination 'winpe-screen-service.exe')
