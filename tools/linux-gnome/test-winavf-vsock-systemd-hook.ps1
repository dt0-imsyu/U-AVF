[CmdletBinding()]
param(
  [string] $HookPath = '',
  [string] $ReportPath = ''
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $HookPath) { $HookPath = Join-Path $scriptRoot 'guest\99-winavf-vsock-systemd' }
if (-not $ReportPath) { $ReportPath = Join-Path $scriptRoot '..\..\build-logs\ubuntu-gnome-vsock-hook-test\vsock-systemd-hook-test-report.txt' }
if (-not (Test-Path -LiteralPath $HookPath -PathType Leaf)) { throw "Missing hook: $HookPath" }
if (Test-Path -LiteralPath $ReportPath) { Remove-Item -LiteralPath $ReportPath -Force }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ReportPath) | Out-Null

$content = [IO.File]::ReadAllText($HookPath)
$expectedExec = 'ExecStart=/bin/sh -c ''printf "LVH1\001\000\000\000\000\000\000\000\001\000\000\000"'''
if ($content -notmatch [regex]::Escape('ListenStream=vsock:-1:4051')) { throw 'Missing public AF_VSOCK port 4051 listener.' }
if ($content -notmatch [regex]::Escape($expectedExec)) { throw 'Service does not contain the exact 16-byte LVH1 printf command.' }
if ($content -match '\\\\001|\\\\000') { throw 'Double-escaped octal sequence would emit literal backslashes.' }

$expected = [byte[]]@(0x4c,0x56,0x48,0x31,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01,0x00,0x00,0x00)
$actual = [Text.Encoding]::ASCII.GetBytes('LVH1') + [byte[]]@(1,0,0,0,0,0,0,0,1,0,0,0)
if ($actual.Length -ne 16 -or [BitConverter]::ToString($actual) -ne [BitConverter]::ToString($expected)) { throw 'LVH1 wire record construction mismatch.' }

@(
  'RESULT=PASS',
  'PORT=4051',
  'SYSTEMD_SOCKET=vsock:-1:4051',
  'WIRE_BYTES=4C564831010000000000000001000000',
  'WIRE_LENGTH=16',
  'REVISION=1',
  'STATUS=0',
  'CAPABILITIES=1',
  'NEXT=Rebuild only the disposable initrd before the ext4-root runtime.'
) | Set-Content -LiteralPath $ReportPath -Encoding utf8
Get-Content -LiteralPath $ReportPath