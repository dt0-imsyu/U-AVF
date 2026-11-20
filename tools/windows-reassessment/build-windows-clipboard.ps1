param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$llvm=Join-Path $root 'tools\toolchains\llvm-mingw-20260826-clean-extract\llvm-mingw-20260826-ucrt-x86_64\bin'
New-Item -ItemType Directory -Path $output | Out-Null
& "$llvm/aarch64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode '-Wl,--no-insert-timestamp' "$PSScriptRoot/windows-clipboard-agent.c" -o "$output/windows-clipboard-agent.exe" -luser32
if($LASTEXITCODE){throw 'ARM64 clipboard worker compile failed'}
& "$llvm/llvm-strip.exe" --strip-all "$output/windows-clipboard-agent.exe"
if($LASTEXITCODE){throw 'Strip failed'}
& "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode "$PSScriptRoot/windows-clipboard-selftest.c" -o "$output/clipboard-selftest.exe" -luser32
if($LASTEXITCODE){throw 'Native clipboard protocol test compile failed'}
& "$output/clipboard-selftest.exe" "$output/windows-ucip-vector.bin"
if($LASTEXITCODE){throw 'Native clipboard protocol test failed'}
Get-FileHash -Algorithm SHA256 -LiteralPath "$output/windows-clipboard-agent.exe" | Select-Object -ExpandProperty Hash
