[CmdletBinding()]
param(
    [string]$Toolchain = $env:LLVM_MINGW_ROOT,
    [string]$OutputDir = (Join-Path $PSScriptRoot 'out')
)

$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
if (-not $Toolchain) {
    $Toolchain = Join-Path $root '../../tools/toolchains/llvm-mingw-20260826-ucrt-x86_64'
}
$gcc = Join-Path $Toolchain 'bin/aarch64-w64-mingw32-gcc.exe'
$objdump = Join-Path $Toolchain 'bin/llvm-objdump.exe'
$out = [IO.Path]::GetFullPath($OutputDir)
$exe = Join-Path $out 'WinAvfInput.exe'

if (!(Test-Path -LiteralPath $gcc) -or !(Test-Path -LiteralPath $objdump)) {
    throw 'ARM64 llvm-mingw not found. Set LLVM_MINGW_ROOT or pass -Toolchain.'
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
& $gcc -Os -s -nostdlib -fno-stack-protector -mwindows `
    '-Wl,--entry,WinMainCRTStartup' (Join-Path $root 'WinAvfInput.c') `
    -o $exe -lkernel32 -lws2_32
if ($LASTEXITCODE -ne 0) { throw 'WinAvfInput ARM64 build failed.' }

$headers = & $objdump -p $exe
if (($headers -join "`n") -notmatch 'file format coff-arm64') {
    throw 'Output is not an ARM64 PE.'
}
if (($headers -join "`n") -match 'DLL Name: USER32.dll') {
    throw 'USER32 must remain delay-loaded through LoadLibraryW for the HELLO test.'
}
if (($headers -join "`n") -match 'api-ms-win-crt|msvcrt') {
    throw 'The early WinPE HELLO agent must not import a C runtime.'
}

[pscustomobject]@{
    Path = $exe
    Length = (Get-Item -LiteralPath $exe).Length
    Sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $exe).Hash
    Architecture = 'ARM64'
    InitialImports = (($headers | Select-String 'DLL Name:').Line.Trim() -join '; ')
}
