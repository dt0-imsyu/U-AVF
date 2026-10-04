[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $SourceInitrd,
  [Parameter(Mandatory = $true)] [string] $ProbePath,
  [string] $SystemdHookPath = '',
  [string] $FrameBridgePath = '',
  [string] $InstallRuntimePath = '',
  [string] $InstallPreflightPath = '',
  [string] $AutoinstallConfigPath = '',
  [string] $GuestXtigervncPath = '',
  [string] $GuestX0tigervncPath = '',
  [string] $GuestX0tigervncCopyrightPath = '',
  [string] $GuestAudioBridgePath = '',
  [string] $GuestPactlPath = '',
  [string] $GuestParecPath = '',
  [string] $GuestGfxstreamIcdPath = '',
  [string] $GuestGfxstreamJsonPath = '',
  [string] $GuestLibdrmPath = '',
  [string] $OutputInitrd = 'C:\Users\denis\MainProjects\win11ontab\build-logs\ubuntu-gnome-vsock\initrd-with-winavf-vsock',
  [string] $ReportPath = 'C:\Users\denis\MainProjects\win11ontab\build-logs\ubuntu-gnome-vsock\initrd-append-report.txt',
  [string] $ZstdPath = 'C:\msys64\usr\bin\zstd.exe'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$x0tigervncDefault = Join-Path $repo 'android-app\assets\winavf-X0tigervnc-1.13.1-noble-arm64'
if (-not $GuestX0tigervncPath) { $GuestX0tigervncPath = $x0tigervncDefault }
$x0tigervncCopyrightDefault = Join-Path $repo 'android-app\assets\TIGERVNC_X0_COPYRIGHT'
if (-not $GuestX0tigervncCopyrightPath) { $GuestX0tigervncCopyrightPath = $x0tigervncCopyrightDefault }
$hookPath = Join-Path $PSScriptRoot 'guest\99-winavf-vsock'
if (-not $SystemdHookPath) { $SystemdHookPath = Join-Path $PSScriptRoot 'guest\99-winavf-vsock-systemd' }
if (-not $FrameBridgePath) { $FrameBridgePath = Join-Path $PSScriptRoot 'guest\winavf-frame-bridge.py' }
if (-not $InstallRuntimePath) { $InstallRuntimePath = Join-Path $PSScriptRoot 'guest\uavf-install-runtime.sh' }
if (-not $InstallPreflightPath) { $InstallPreflightPath = Join-Path $PSScriptRoot 'guest\uavf-install-preflight.sh' }
if (-not $AutoinstallConfigPath) { $AutoinstallConfigPath = Join-Path $PSScriptRoot 'guest\uavf-autoinstall.yaml' }
foreach ($path in @($SourceInitrd, $hookPath, $SystemdHookPath, $FrameBridgePath, $InstallRuntimePath, $InstallPreflightPath, $AutoinstallConfigPath, $ZstdPath)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required input: $path" }
}
foreach ($path in @($GuestGfxstreamIcdPath, $GuestGfxstreamJsonPath, $GuestLibdrmPath, $GuestXtigervncPath, $GuestX0tigervncPath, $GuestX0tigervncCopyrightPath, $GuestAudioBridgePath, $GuestPactlPath, $GuestParecPath)) {
  if ($path -and -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing optional GPU input: $path" }
}
$autoinstallAudit = Join-Path $PSScriptRoot 'audit-uavf-autoinstall.py'
if (-not (Test-Path -LiteralPath $autoinstallAudit -PathType Leaf)) { throw "Missing autoinstall audit: $autoinstallAudit" }
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'Python is required to validate the autoinstall config before initrd assembly.' }
$auditOutput = & $python.Source $autoinstallAudit $AutoinstallConfigPath 2>&1
if ($LASTEXITCODE -ne 0) { throw "Autoinstall validation failed before initrd assembly: $($auditOutput -join [Environment]::NewLine)" }
Write-Output ($auditOutput -join [Environment]::NewLine)
$gpuPathCount = 0
foreach ($path in @($GuestGfxstreamIcdPath, $GuestGfxstreamJsonPath, $GuestLibdrmPath)) { if ($path) { $gpuPathCount++ } }
if ($gpuPathCount -notin @(0, 3)) {
  throw 'Supply all three Gfxstream guest files together, or none of them.'
}
$audioPathCount = 0
foreach ($path in @($GuestAudioBridgePath, $GuestPactlPath, $GuestParecPath)) { if ($path) { $audioPathCount++ } }
if ($audioPathCount -notin @(0, 3)) { throw 'Supply all three audio relay files together, or none of them.' }
if ($ProbePath -and -not (Test-Path -LiteralPath $ProbePath -PathType Leaf)) { throw "Missing required input: $ProbePath" }
if ((Get-Item -LiteralPath $SourceInitrd).Length -lt 1MB) { throw 'Source initrd is unexpectedly small.' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputInitrd), (Split-Path -Parent $ReportPath) | Out-Null
if (Test-Path -LiteralPath $OutputInitrd) { Remove-Item -LiteralPath $OutputInitrd -Force }

function Write-Ascii([IO.Stream] $stream, [string] $text) {
  $bytes = [Text.Encoding]::ASCII.GetBytes($text)
  $stream.Write($bytes, 0, $bytes.Length)
}
function Write-Padding([IO.Stream] $stream, [int64] $length) {
  $pad = (4 - ($length % 4)) % 4
  if ($pad) { $stream.Write((New-Object byte[] $pad), 0, $pad) }
}
function Write-NewcEntry([IO.Stream] $stream, [string] $path, [byte[]] $content, [int] $mode, [int] $inode) {
  $nameBytes = [Text.Encoding]::ASCII.GetBytes($path + [char]0)
  $header = '070701{0:X8}{1:X8}{2:X8}{3:X8}{4:X8}{5:X8}{6:X8}{7:X8}{8:X8}{9:X8}{10:X8}{11:X8}{12:X8}' -f $inode, $mode, 0, 0, 1, 0, $content.Length, 0, 0, 0, 0, $nameBytes.Length, 0
  Write-Ascii $stream $header
  $stream.Write($nameBytes, 0, $nameBytes.Length)
  Write-Padding $stream (110 + $nameBytes.Length)
  if ($content.Length) { $stream.Write($content, 0, $content.Length) }
  Write-Padding $stream $content.Length
}
function Read-Exact([IO.Stream] $stream, [int] $count) {
  $buffer = New-Object byte[] $count
  $offset = 0
  while ($offset -lt $count) {
    $read = $stream.Read($buffer, $offset, $count - $offset)
    if ($read -le 0) { throw 'Unexpected EOF while auditing appended newc layer.' }
    $offset += $read
  }
  return $buffer
}
function Read-AppendedNewcNames([string] $path, [int64] $start) {
  $names = [System.Collections.Generic.List[string]]::new()
  $stream = [IO.File]::OpenRead($path)
  try {
    $stream.Position = $start
    while ($true) {
      $header = [Text.Encoding]::ASCII.GetString((Read-Exact $stream 110))
      if (-not $header.StartsWith('070701')) { throw 'Appended layer does not begin with a newc header.' }
      $fileSize = [Convert]::ToInt32($header.Substring(54, 8), 16)
      $nameSize = [Convert]::ToInt32($header.Substring(94, 8), 16)
      $name = [Text.Encoding]::ASCII.GetString((Read-Exact $stream $nameSize)).TrimEnd([char]0)
      $names.Add($name)
      $namePad = (4 - ((110 + $nameSize) % 4)) % 4
      if ($namePad) { [void](Read-Exact $stream $namePad) }
      if ($fileSize) { [void](Read-Exact $stream $fileSize) }
      $filePad = (4 - ($fileSize % 4)) % 4
      if ($filePad) { [void](Read-Exact $stream $filePad) }
      if ($name -eq 'TRAILER!!!') { return $names }
    }
  } finally { $stream.Dispose() }
}
function Find-NewcTrailerOffset([string] $path) {
  $stream = [IO.File]::OpenRead($path)
  try {
    while ($true) {
      $entryOffset = $stream.Position
      $header = [Text.Encoding]::ASCII.GetString((Read-Exact $stream 110))
      if (-not $header.StartsWith('070701')) { throw 'Source initrd is not a contiguous newc archive.' }
      $fileSize = [Convert]::ToInt32($header.Substring(54, 8), 16)
      $nameSize = [Convert]::ToInt32($header.Substring(94, 8), 16)
      $name = [Text.Encoding]::ASCII.GetString((Read-Exact $stream $nameSize)).TrimEnd([char]0)
      $namePad = (4 - ((110 + $nameSize) % 4)) % 4
      if ($namePad) { [void](Read-Exact $stream $namePad) }
      if ($fileSize) { [void](Read-Exact $stream $fileSize) }
      $filePad = (4 - ($fileSize % 4)) % 4
      if ($filePad) { [void](Read-Exact $stream $filePad) }
      if ($name -eq 'TRAILER!!!') { return $entryOffset }
    }
  } finally { $stream.Dispose() }
}
function Read-NewcFile([string] $path, [string] $wantedName) {
  $stream = [IO.File]::OpenRead($path)
  try {
    while ($true) {
      $header = [Text.Encoding]::ASCII.GetString((Read-Exact $stream 110))
      if (-not $header.StartsWith('070701')) { throw 'Expected a contiguous newc archive while reading a control file.' }
      $fileSize = [Convert]::ToInt32($header.Substring(54, 8), 16)
      $nameSize = [Convert]::ToInt32($header.Substring(94, 8), 16)
      $name = [Text.Encoding]::ASCII.GetString((Read-Exact $stream $nameSize)).TrimEnd([char]0)
      $namePad = (4 - ((110 + $nameSize) % 4)) % 4
      if ($namePad) { [void](Read-Exact $stream $namePad) }
      $content = if ($fileSize) { Read-Exact $stream $fileSize } else { [byte[]]@() }
      $filePad = (4 - ($fileSize % 4)) % 4
      if ($filePad) { [void](Read-Exact $stream $filePad) }
      if ($name -eq $wantedName) { return $content }
      if ($name -eq 'TRAILER!!!') { break }
    }
  } finally { $stream.Dispose() }
  throw "Expected initramfs control file is absent: $wantedName"
}
function Read-NewcFileLast([string] $path, [string] $wantedName) {
  $found = $null
  $stream = [IO.File]::OpenRead($path)
  try {
    while ($true) {
      $header = [Text.Encoding]::ASCII.GetString((Read-Exact $stream 110))
      if (-not $header.StartsWith('070701')) { throw 'Expected a contiguous newc archive while reading the final tree.' }
      $fileSize = [Convert]::ToInt32($header.Substring(54, 8), 16)
      $nameSize = [Convert]::ToInt32($header.Substring(94, 8), 16)
      $name = [Text.Encoding]::ASCII.GetString((Read-Exact $stream $nameSize)).TrimEnd([char]0)
      $namePad = (4 - ((110 + $nameSize) % 4)) % 4
      if ($namePad) { [void](Read-Exact $stream $namePad) }
      $content = if ($fileSize) { Read-Exact $stream $fileSize } else { [byte[]]@() }
      $filePad = (4 - ($fileSize % 4)) % 4
      if ($filePad) { [void](Read-Exact $stream $filePad) }
      if ($name -eq $wantedName) { $found = $content }
      if ($name -eq 'TRAILER!!!') { break }
    }
  } finally { $stream.Dispose() }
  if ($null -eq $found) { throw "Expected final initramfs file is absent: $wantedName" }
  return $found
}
function Get-NewcArchiveEnd([string] $path, [int64] $trailerOffset) {
  $stream = [IO.File]::OpenRead($path)
  try {
    $stream.Position = $trailerOffset
    $header = [Text.Encoding]::ASCII.GetString((Read-Exact $stream 110))
    if (-not $header.StartsWith('070701')) { throw 'Expected a newc trailer header.' }
    $fileSize = [Convert]::ToInt32($header.Substring(54, 8), 16)
    $nameSize = [Convert]::ToInt32($header.Substring(94, 8), 16)
    $name = [Text.Encoding]::ASCII.GetString((Read-Exact $stream $nameSize)).TrimEnd([char]0)
    if ($name -ne 'TRAILER!!!') { throw 'Expected a newc TRAILER!!! record.' }
    $namePad = (4 - ((110 + $nameSize) % 4)) % 4
    $filePad = (4 - ($fileSize % 4)) % 4
    $end = $trailerOffset + 110 + $nameSize + $namePad + $fileSize + $filePad
    return [int64]([Math]::Ceiling($end / 512.0) * 512)
  } finally { $stream.Dispose() }
}
function Assert-ZstdMagic([string] $path, [int64] $offset) {
  $stream = [IO.File]::OpenRead($path)
  try {
    $stream.Position = $offset
    $magic = Read-Exact $stream 4
    if (($magic[0] -ne 0x28) -or ($magic[1] -ne 0xB5) -or ($magic[2] -ne 0x2F) -or ($magic[3] -ne 0xFD)) {
      throw 'Expected a Zstd-compressed main initramfs after the early CPIO layer.'
    }
  } finally { $stream.Dispose() }
}
function Copy-Range([IO.Stream] $Source, [IO.Stream] $Destination, [int64] $count) {
  $buffer = New-Object byte[] 4MB
  $remaining = $count
  while ($remaining -gt 0) {
    $want = [int][Math]::Min([int64]$buffer.Length, $remaining)
    $read = $Source.Read($buffer, 0, $want)
    if ($read -le 0) { throw 'Unexpected EOF while copying initrd.' }
    $Destination.Write($buffer, 0, $read)
    $remaining -= $read
  }
}
function Assert-FileEqual([string] $left, [string] $right, [string] $message) {
  $a = [IO.File]::ReadAllBytes($left); $b = [IO.File]::ReadAllBytes($right)
  if ($a.Length -ne $b.Length) { throw $message }
  for ($i = 0; $i -lt $a.Length; $i++) { if ($a[$i] -ne $b[$i]) { throw $message } }
}

$hook = [IO.File]::ReadAllBytes($hookPath)
$systemdHook = [IO.File]::ReadAllBytes($SystemdHookPath)
$gdmShare = [IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'guest\uavf-gdm-display-share.py'))
$frameBridge = [IO.File]::ReadAllBytes($FrameBridgePath)
$installRuntime = [IO.File]::ReadAllBytes($InstallRuntimePath)
$installPreflight = [IO.File]::ReadAllBytes($InstallPreflightPath)
$autoinstallConfig = [IO.File]::ReadAllBytes($AutoinstallConfigPath)
$guestXtigervnc = if ($GuestXtigervncPath) { [IO.File]::ReadAllBytes($GuestXtigervncPath) } else { $null }
$guestX0tigervnc = if ($GuestX0tigervncPath) { [IO.File]::ReadAllBytes($GuestX0tigervncPath) } else { $null }
$guestX0tigervncCopyright = if ($GuestX0tigervncCopyrightPath) { [IO.File]::ReadAllBytes($GuestX0tigervncCopyrightPath) } else { $null }
$guestAudioBridge = if ($GuestAudioBridgePath) { [IO.File]::ReadAllBytes($GuestAudioBridgePath) } else { $null }
$guestPactl = if ($GuestPactlPath) { [IO.File]::ReadAllBytes($GuestPactlPath) } else { $null }
$guestParec = if ($GuestParecPath) { [IO.File]::ReadAllBytes($GuestParecPath) } else { $null }
$guestGfxstreamIcd = if ($GuestGfxstreamIcdPath) { [IO.File]::ReadAllBytes($GuestGfxstreamIcdPath) } else { $null }
$guestGfxstreamJson = if ($GuestGfxstreamJsonPath) { [IO.File]::ReadAllBytes($GuestGfxstreamJsonPath) } else { $null }
$guestLibdrm = if ($GuestLibdrmPath) { [IO.File]::ReadAllBytes($GuestLibdrmPath) } else { $null }
$probe = if ($ProbePath) { [IO.File]::ReadAllBytes($ProbePath) } else { $null }
$listenerPath = 'usr/local/sbin/winavf-vsock-hello'
$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $SourceInitrd).Hash.ToUpperInvariant()
$earlyTrailerOffset = Find-NewcTrailerOffset $SourceInitrd
$zstdOffset = Get-NewcArchiveEnd $SourceInitrd $earlyTrailerOffset
Assert-ZstdMagic $SourceInitrd $zstdOffset
$temp = Join-Path ([IO.Path]::GetTempPath()) ('winavf-vsock-' + [Guid]::NewGuid().ToString('N'))
$mainCompressed = Join-Path $temp 'main.cpio.zst'
$mainSource = Join-Path $temp 'main-source.cpio'
$mainPatched = Join-Path $temp 'main-patched.cpio'
$mainRecompressed = Join-Path $temp 'main-patched.cpio.zst'
try {
  New-Item -ItemType Directory -Path $temp | Out-Null
  $sourceStream = [IO.File]::Open($SourceInitrd, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
  $compressedStream = [IO.File]::Open($mainCompressed, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try {
    $sourceStream.Position = $zstdOffset
    Copy-Range $sourceStream $compressedStream ($sourceStream.Length - $zstdOffset)
    $compressedStream.Flush($true)
  } finally { $compressedStream.Dispose(); $sourceStream.Dispose() }
  & $ZstdPath -d -q -f $mainCompressed -o $mainSource
  if ($LASTEXITCODE -ne 0) { throw 'Could not decompress the main Zstd initramfs.' }
  $mainTrailerOffset = Find-NewcTrailerOffset $mainSource
  $orderPath = 'scripts/init-premount/ORDER'
  $orderText = [Text.Encoding]::ASCII.GetString((Read-NewcFile $mainSource $orderPath))
  if (-not $orderText.EndsWith("`n")) { $orderText += "`n" }
  if ($orderText -notmatch '(?m)^/scripts/init-premount/99-winavf-vsock "\$@"$') {
    $orderText += '/scripts/init-premount/99-winavf-vsock "$@"' + "`n"
  }
  $order = [Text.Encoding]::ASCII.GetBytes($orderText)
  $bottomOrderPath = 'scripts/init-bottom/ORDER'
  $bottomOrderText = ""
  try { $bottomOrderText = [Text.Encoding]::ASCII.GetString((Read-NewcFile $mainSource $bottomOrderPath)) } catch { }
  if (-not $bottomOrderText.EndsWith("`n")) { $bottomOrderText += "`n" }
  if ($bottomOrderText -notmatch '(?m)^/scripts/init-bottom/99-winavf-vsock-systemd "\$@"$') {
    $bottomOrderText += '/scripts/init-bottom/99-winavf-vsock-systemd "$@"' + "`n"
  }
  $bottomOrder = [Text.Encoding]::ASCII.GetBytes($bottomOrderText)
  $mainIn = [IO.File]::OpenRead($mainSource)
  $mainOut = [IO.File]::Open($mainPatched, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try {
    Copy-Range $mainIn $mainOut $mainTrailerOffset
    if ($null -eq $probe) { throw 'ProbePath is required so the init-premount listener can execute.' }
    Write-NewcEntry $mainOut $listenerPath $probe 0x81ED 1
    Write-NewcEntry $mainOut 'scripts/init-premount/99-winavf-vsock' $hook 0x81ED 2
    Write-NewcEntry $mainOut $orderPath $order 0x81A4 3
    Write-NewcEntry $mainOut 'scripts/init-bottom/99-winavf-vsock-systemd' $systemdHook 0x81ED 4
    Write-NewcEntry $mainOut 'scripts/init-premount/winavf-frame-bridge.py' $frameBridge 0x81ED 5
    Write-NewcEntry $mainOut $bottomOrderPath $bottomOrder 0x81A4 6
    if (-not $guestXtigervnc -and -not $guestX0tigervnc -and -not $guestAudioBridge) {
      Write-NewcEntry $mainOut 'usr/local' ([byte[]]::new(0)) 0x41ED 19
      Write-NewcEntry $mainOut 'usr/local/sbin' ([byte[]]::new(0)) 0x41ED 20
    }
    Write-NewcEntry $mainOut 'usr/local/sbin/uavf-install-runtime.sh' $installRuntime 0x81ED 21
    Write-NewcEntry $mainOut 'usr/local/sbin/winavf-frame-bridge' $frameBridge 0x81ED 31
    Write-NewcEntry $mainOut 'usr/local/sbin/uavf-install-preflight.sh' $installPreflight 0x81ED 22
    Write-NewcEntry $mainOut 'usr/local/sbin/uavf-autoinstall.yaml' $autoinstallConfig 0x81A4 23
    Write-NewcEntry $mainOut 'autoinstall.yaml' $autoinstallConfig 0x81A4 24
    if ($guestXtigervnc) {
      Write-NewcEntry $mainOut 'usr/local/sbin' ([byte[]]::new(0)) 0x41ED 12
      Write-NewcEntry $mainOut 'usr/local/sbin/winavf-Xtigervnc' $guestXtigervnc 0x81ED 13
      Write-NewcEntry $mainOut 'usr/local/sbin/winavf-virgl-gbm-mode' ([Text.Encoding]::ASCII.GetBytes("#!/bin/sh`nexit 0`n")) 0x81ED 14
    }
    if ($guestX0tigervnc) {
      Write-NewcEntry $mainOut 'usr/local/sbin' ([byte[]]::new(0)) 0x41ED 25
      Write-NewcEntry $mainOut 'usr/local/sbin/winavf-X0tigervnc' $guestX0tigervnc 0x81ED 26
      Write-NewcEntry $mainOut 'usr/local/sbin/uavf-gdm-display-share.py' $gdmShare 0x81ED 30
      Write-NewcEntry $mainOut 'usr/local/share' ([byte[]]::new(0)) 0x41ED 27
      Write-NewcEntry $mainOut 'usr/local/share/uavf-licenses' ([byte[]]::new(0)) 0x41ED 28
      Write-NewcEntry $mainOut 'usr/local/share/uavf-licenses/TIGERVNC_X0_COPYRIGHT' $guestX0tigervncCopyright 0x81A4 29
    }
    if ($guestAudioBridge) {
      if (-not $guestXtigervnc) { Write-NewcEntry $mainOut 'usr/local/sbin' ([byte[]]::new(0)) 0x41ED 15 }
      Write-NewcEntry $mainOut 'usr/local/sbin/winavf-audio-bridge' $guestAudioBridge 0x81ED 16
      Write-NewcEntry $mainOut 'usr/local/sbin/winavf-pactl' $guestPactl 0x81ED 17
      Write-NewcEntry $mainOut 'usr/local/sbin/winavf-parec' $guestParec 0x81ED 18
    }
    if ($guestGfxstreamIcd) {
      # The kernel's initramfs unpacker creates each CPIO entry at its exact
      # path; it does not synthesize missing parent directories. Add them
      # explicitly (bsdtar-based audits otherwise hide this packaging error).
      Write-NewcEntry $mainOut 'usr/local/lib' ([byte[]]::new(0)) 0x41ED 7
      Write-NewcEntry $mainOut 'usr/local/lib/uavf-gfxstream' ([byte[]]::new(0)) 0x41ED 8
      Write-NewcEntry $mainOut 'usr/local/lib/uavf-gfxstream/libvulkan_gfxstream.so' $guestGfxstreamIcd 0x81A4 9
      Write-NewcEntry $mainOut 'usr/local/lib/uavf-gfxstream/gfxstream_vk_icd.json' $guestGfxstreamJson 0x81A4 10
      Write-NewcEntry $mainOut 'usr/local/lib/uavf-gfxstream/libdrm.so.2.125.0' $guestLibdrm 0x81A4 11
    }
    $mainIn.Position = $mainTrailerOffset
    Copy-Range $mainIn $mainOut ($mainIn.Length - $mainTrailerOffset)
    $mainOut.Flush($true)
  } finally { $mainOut.Dispose(); $mainIn.Dispose() }
  $mainEntries = Read-AppendedNewcNames $mainPatched 0
  if ($mainEntries -notcontains $listenerPath -or $mainEntries -notcontains 'scripts/init-premount/99-winavf-vsock' -or $mainEntries -notcontains 'scripts/init-bottom/99-winavf-vsock-systemd' -or $mainEntries -notcontains $bottomOrderPath -or $mainEntries[-1] -ne 'TRAILER!!!') { throw 'Main initramfs content audit failed.' }
  & $ZstdPath -q -19 -f $mainPatched -o $mainRecompressed
  if ($LASTEXITCODE -ne 0) { throw 'Could not recompress the patched main initramfs.' }
  $sourceStream = [IO.File]::OpenRead($SourceInitrd)
  $outputStream = [IO.File]::Open($OutputInitrd, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try {
    Copy-Range $sourceStream $outputStream $zstdOffset
    $recompressedStream = [IO.File]::OpenRead($mainRecompressed)
    try { Copy-Range $recompressedStream $outputStream $recompressedStream.Length } finally { $recompressedStream.Dispose() }
    $outputStream.Flush($true)
  } finally { $outputStream.Dispose(); $sourceStream.Dispose() }
  $finalCompressed = Join-Path $temp 'final-main.cpio.zst'
  $finalSource = Join-Path $temp 'final-main.cpio'
  $finalTree = Join-Path $temp 'final-extracted-tree'
  New-Item -ItemType Directory -Path $finalTree | Out-Null
  $outputRead = [IO.File]::OpenRead($OutputInitrd)
  $finalCompressedOut = [IO.File]::Open($finalCompressed, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try { $outputRead.Position = $zstdOffset; Copy-Range $outputRead $finalCompressedOut ($outputRead.Length - $zstdOffset) } finally { $finalCompressedOut.Dispose(); $outputRead.Dispose() }
  & $ZstdPath -d -q -f $finalCompressed -o $finalSource
  if ($LASTEXITCODE -ne 0) { throw 'Could not decompress the final main Zstd initramfs for audit.' }
  $finalPaths = @($listenerPath,'scripts/init-premount/winavf-frame-bridge.py','scripts/init-premount/99-winavf-vsock','scripts/init-premount/ORDER','scripts/init-bottom/99-winavf-vsock-systemd','scripts/init-bottom/ORDER','usr/local/sbin/uavf-install-runtime.sh','usr/local/sbin/uavf-install-preflight.sh','usr/local/sbin/uavf-autoinstall.yaml','autoinstall.yaml')
  if ($guestXtigervnc) { $finalPaths += @('usr/local/sbin/winavf-Xtigervnc','usr/local/sbin/winavf-virgl-gbm-mode') }
  if ($guestX0tigervnc) { $finalPaths += @('usr/local/sbin/winavf-X0tigervnc','usr/local/share/uavf-licenses/TIGERVNC_X0_COPYRIGHT') }
  if ($guestAudioBridge) { $finalPaths += @('usr/local/sbin/winavf-audio-bridge','usr/local/sbin/winavf-pactl','usr/local/sbin/winavf-parec') }
  if ($guestGfxstreamIcd) { $finalPaths += @('usr/local/lib/uavf-gfxstream/libvulkan_gfxstream.so','usr/local/lib/uavf-gfxstream/gfxstream_vk_icd.json','usr/local/lib/uavf-gfxstream/libdrm.so.2.125.0') }
  foreach ($relative in $finalPaths) {
    $target = Join-Path $finalTree ($relative.Replace('/', '\\'))
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    [IO.File]::WriteAllBytes($target, (Read-NewcFileLast $finalSource $relative))
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Final extracted tree is missing $relative" }
  }
  Assert-FileEqual (Join-Path $finalTree 'scripts/init-premount/99-winavf-vsock') $hookPath 'Final init-premount hook content mismatch.'
  Assert-FileEqual (Join-Path $finalTree $listenerPath) $ProbePath 'Final listener content mismatch.'
  Assert-FileEqual (Join-Path $finalTree 'scripts/init-premount/winavf-frame-bridge.py') $FrameBridgePath 'Final frame bridge content mismatch.'
  Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/uavf-install-runtime.sh') $InstallRuntimePath 'Final install-runtime late-command content mismatch.'
  Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/uavf-install-preflight.sh') $InstallPreflightPath 'Final install preflight content mismatch.'
  Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/uavf-autoinstall.yaml') $AutoinstallConfigPath 'Final autoinstall configuration payload mismatch.'
  Assert-FileEqual (Join-Path $finalTree 'autoinstall.yaml') $AutoinstallConfigPath 'Final root autoinstall configuration mismatch.'
  if ((Get-Item -LiteralPath (Join-Path $finalTree $listenerPath)).Length -le 0) { throw 'Final listener is empty.' }
  Assert-FileEqual (Join-Path $finalTree 'scripts/init-bottom/99-winavf-vsock-systemd') $SystemdHookPath 'Final init-bottom hook content mismatch.'
  if ($guestXtigervnc) {
    Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/winavf-Xtigervnc') $GuestXtigervncPath 'Final Xtigervnc binary mismatch.'
    if ($mainEntries -notcontains 'usr/local/sbin') { throw 'Main initramfs is missing the Xtigervnc parent directory.' }
  }
  if ($guestX0tigervnc) {
    Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/winavf-X0tigervnc') $GuestX0tigervncPath 'Final X0tigervnc binary mismatch.'
    Assert-FileEqual (Join-Path $finalTree 'usr/local/share/uavf-licenses/TIGERVNC_X0_COPYRIGHT') $GuestX0tigervncCopyrightPath 'Final X0tigervnc copyright mismatch.'
  }
  if ($guestAudioBridge) {
    Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/winavf-audio-bridge') $GuestAudioBridgePath 'Final audio bridge mismatch.'
    Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/winavf-pactl') $GuestPactlPath 'Final pactl mismatch.'
    Assert-FileEqual (Join-Path $finalTree 'usr/local/sbin/winavf-parec') $GuestParecPath 'Final parec mismatch.'
  }
  if ($guestGfxstreamIcd) {
    $gpuDirectoryEntries = @('usr/local/lib', 'usr/local/lib/uavf-gfxstream')
    foreach ($directory in $gpuDirectoryEntries) {
      if ($mainEntries -notcontains $directory) { throw "Main initramfs is missing explicit GPU directory entry: $directory" }
    }
    $lastDirectoryIndex = [Math]::Max([Array]::IndexOf($mainEntries, $gpuDirectoryEntries[0]), [Array]::IndexOf($mainEntries, $gpuDirectoryEntries[1]))
    $firstGpuFileIndex = [Array]::IndexOf($mainEntries, 'usr/local/lib/uavf-gfxstream/libvulkan_gfxstream.so')
    if ($lastDirectoryIndex -lt 0 -or $firstGpuFileIndex -le $lastDirectoryIndex) { throw 'GPU directory entries must precede the guest ICD files.' }
    Assert-FileEqual (Join-Path $finalTree 'usr/local/lib/uavf-gfxstream/libvulkan_gfxstream.so') $GuestGfxstreamIcdPath 'Final Gfxstream ICD mismatch.'
    Assert-FileEqual (Join-Path $finalTree 'usr/local/lib/uavf-gfxstream/gfxstream_vk_icd.json') $GuestGfxstreamJsonPath 'Final Gfxstream ICD JSON mismatch.'
    Assert-FileEqual (Join-Path $finalTree 'usr/local/lib/uavf-gfxstream/libdrm.so.2.125.0') $GuestLibdrmPath 'Final libdrm mismatch.'
  }
  $finalPreOrder = [IO.File]::ReadAllText((Join-Path $finalTree 'scripts/init-premount/ORDER'))
  $finalBottomOrder = [IO.File]::ReadAllText((Join-Path $finalTree 'scripts/init-bottom/ORDER'))
  if ($finalPreOrder -notmatch '(?m)^/scripts/init-premount/99-winavf-vsock "\$@"$' -or $finalBottomOrder -notmatch '(?m)^/scripts/init-bottom/99-winavf-vsock-systemd "\$@"$') { throw 'Final extracted ORDER audit failed.' }
  $sourceEarly = [byte[]]::new([int]$zstdOffset); $outputEarly = [byte[]]::new([int]$zstdOffset)
  $sourceEarlyStream = [IO.File]::OpenRead($SourceInitrd); $outputEarlyStream = [IO.File]::OpenRead($OutputInitrd)
  try { [void]$sourceEarlyStream.Read($sourceEarly,0,$sourceEarly.Length); [void]$outputEarlyStream.Read($outputEarly,0,$outputEarly.Length) } finally { $sourceEarlyStream.Dispose(); $outputEarlyStream.Dispose() }
  if (-not [System.Linq.Enumerable]::SequenceEqual($sourceEarly, $outputEarly)) { throw 'Early CPIO bytes changed.' }
} catch { if (Test-Path -LiteralPath $OutputInitrd) { Remove-Item -LiteralPath $OutputInitrd -Force }; throw } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force } }

$outputHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $OutputInitrd).Hash.ToUpperInvariant()
$guestXtigervncHash = 'NOT_INCLUDED'
if ($GuestXtigervncPath) { $guestXtigervncHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $GuestXtigervncPath).Hash.ToUpperInvariant() }
$guestX0tigervncHash = 'NOT_INCLUDED'
if ($GuestX0tigervncPath) { $guestX0tigervncHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $GuestX0tigervncPath).Hash.ToUpperInvariant() }
$guestX0tigervncCopyrightHash = 'NOT_INCLUDED'
if ($GuestX0tigervncCopyrightPath) { $guestX0tigervncCopyrightHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $GuestX0tigervncCopyrightPath).Hash.ToUpperInvariant() }
@(
  'RESULT=PASS',
  'CHANGE=Early CPIO preserved; listener, init-premount and init-bottom hooks plus both ORDER files inserted in main Zstd CPIO',
  "SOURCE_INITRD_SHA256=$sourceHash",
  "OUTPUT_INITRD_SHA256=$outputHash",
  "SOURCE_BYTES=$((Get-Item -LiteralPath $SourceInitrd).Length)",
  "OUTPUT_BYTES=$((Get-Item -LiteralPath $OutputInitrd).Length)",
  "SYSTEMD_HOOK_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $SystemdHookPath).Hash.ToUpperInvariant())",
  "PROBE_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $ProbePath).Hash.ToUpperInvariant())",
  "GUEST_XTIGERVNC_SHA256=$guestXtigervncHash",
  "GUEST_X0TIGERVNC_SHA256=$guestX0tigervncHash",
  "GUEST_X0TIGERVNC_COPYRIGHT_SHA256=$guestX0tigervncCopyrightHash",
  "LISTENER=$listenerPath",
  'HOOK=scripts/init-premount/99-winavf-vsock',
  'ORDER=scripts/init-premount/ORDER and scripts/init-bottom/ORDER audited',
  'SYSTEMD_HOOK=scripts/init-bottom/99-winavf-vsock-systemd',
  "EARLY_CPIO_BYTES_PRESERVED=$zstdOffset",
  'MAIN_ZSTD_NEWC_AUDIT=PASS',
  "GPU_PARENT_DIRECTORY_ENTRIES=$($(if ($guestGfxstreamIcd) { 'PASS' } else { 'NOT_APPLICABLE' }))",
  'FINAL_EXTRACTED_TREE_AUDIT=PASS',
  'EARLY_CPIO_UNCHANGED=PASS',
  'PORT=4051',
  'NEXT=Replace only CASPER\\INITRD in a new disposable Ubuntu raw candidate, then audit before Android staging.'
) | Set-Content -LiteralPath $ReportPath -Encoding utf8
Get-Content -LiteralPath $ReportPath
