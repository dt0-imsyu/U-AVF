param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
New-Item -ItemType Directory $output|Out-Null
$llvm=Join-Path $root 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
foreach($arch in @('x86_64','aarch64')) {
    & "$llvm/$arch-w64-mingw32-clang++.exe" -O2 -Wall -Wextra -Werror -static '-Wl,--no-insert-timestamp' "$PSScriptRoot/idd-frame-pipe-selftest.cpp" -o "$output/idd-frame-pipe-$arch.exe" -ladvapi32
    if($LASTEXITCODE){throw "Pipe test build failed: $arch"}
    & "$llvm/$arch-w64-mingw32-clang++.exe" -O2 -Wall -Wextra -Werror -static '-Wl,--no-insert-timestamp' "$PSScriptRoot/idd-frame-reader-selftest.cpp" -o "$output/idd-frame-reader-$arch.exe" -ladvapi32
    if($LASTEXITCODE){throw "Reader test build failed: $arch"}
    & "$llvm/$arch-w64-mingw32-clang++.exe" -O2 -Wall -Wextra -Werror -static '-Wl,--no-insert-timestamp' "$PSScriptRoot/idd-frame-reconnect-selftest.cpp" -o "$output/idd-frame-reconnect-$arch.exe" -ladvapi32
    if($LASTEXITCODE){throw "Reconnect test build failed: $arch"}
}
& "$output/idd-frame-pipe-x86_64.exe"
if($LASTEXITCODE){throw "Actual IPC selftest failed: $LASTEXITCODE"}
& "$output/idd-frame-reader-x86_64.exe"
if($LASTEXITCODE){throw "Actual reader selftest failed: $LASTEXITCODE"}
& "$output/idd-frame-reconnect-x86_64.exe"
if($LASTEXITCODE){throw "Actual same-server reconnect selftest failed: $LASTEXITCODE"}
Get-FileHash "$output/idd-frame-pipe-aarch64.exe"
