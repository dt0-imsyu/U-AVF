param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$llvm=Join-Path $root 'tools\toolchains\llvm-mingw-20260826-clean-extract\llvm-mingw-20260826-ucrt-x86_64\bin'
New-Item -ItemType Directory -Path $output | Out-Null
& "$llvm/aarch64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode '-Wl,--no-insert-timestamp' -DUAVF_WINDOWS_CLIPBOARD=1 -DUAVF_DIRECT_ACCEPT=1 "$PSScriptRoot/winpe-vsock.c" -o "$output/winpe-vsock.exe" -lws2_32 -luser32
if($LASTEXITCODE){throw 'ARM64 optional clipboard sidecar compile failed'}
& "$llvm/llvm-strip.exe" --strip-all "$output/winpe-vsock.exe"
if($LASTEXITCODE -or (Get-Item -LiteralPath "$output/winpe-vsock.exe").Length -gt 81408){throw 'Must fit existing OWN socket slot; do not truncate'}
& "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode "$PSScriptRoot/windows-clipboard-relay-selftest.c" -o "$output/relay-selftest.exe" -lws2_32
if($LASTEXITCODE){throw 'Host relay selftest compile failed'}
& "$output/relay-selftest.exe"
if($LASTEXITCODE){throw 'Actual socket/pipe relay selftest failed'}
& "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror "$PSScriptRoot/windows-clipboard-retry-selftest.c" -o "$output/retry-selftest.exe"
if($LASTEXITCODE){throw 'Clipboard retry policy test compile failed'}
& "$output/retry-selftest.exe"
if($LASTEXITCODE){throw 'Clipboard retry policy test failed'}
Get-FileHash -Algorithm SHA256 -LiteralPath "$output/winpe-vsock.exe" | Select-Object -ExpandProperty Hash
