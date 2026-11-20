[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $VhdPath,
  [Parameter(Mandatory = $true)] [string] $OutputRaw,
  [Parameter(Mandatory = $true)] [string] $PatchPath,
  [Parameter(Mandatory = $true)] [string] $ReportPath
)

# Recovery-only path for an interrupted raw export.  It performs no filesystem
# writes: the VHD is attached read-only, audited, then copied sector-for-sector
# to a new raw candidate before the ordinary reversible firmware patch is made.
$ErrorActionPreference = 'Stop'
$expectedBytes = [int64]9126805504
$expectedOffset = [int64]1048576
$expectedPartitionBytes = [int64]9125740032
$expectedInitrd = 'D89C8E7776AEC4E603AA26CB658379E354C69DC293AF6E28C0C42658463D6D27'
$expectedBootaa = '9926FBE93D47384E516C9B440C0502F5CE0163EED7EF7DC956394D45414F5BE3'
$expectedKernel = '3F18B4B8D4DA3ED5EFD9B3AD0ED4AB3286178DED979BFDCBD759CC30EB805CE4'
$firmwareFd = 'C:\Users\denis\MainProjects\win11ontab\firmware-work\edk2\artifacts\KVMTOOL_EFI-ubuntu-fdt-handoff-v11.fd'
$expectedFirmware = '7162202A2ED14C6BE3433915CB5786D9A41E3722647E40AA3CFEF29720D14963'
$patchBuilder = Join-Path (Split-Path -Parent $PSScriptRoot) 'uefi-serial-input\new-firmware-range-patch.ps1'

function Hash([string] $Path) { (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToUpperInvariant() }
function Assert-Admin {
  $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
  if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run from an elevated PowerShell window.' }
}
function Copy-PhysicalRead([string] $Source, [string] $Destination, [int64] $Bytes) {
  $sourceStream = [IO.File]::Open($Source, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
  $destinationStream = [IO.File]::Open($Destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try {
    $buffer = New-Object byte[] 8MB; [int64] $done = 0
    while ($done -lt $Bytes) {
      $want = [int][Math]::Min([int64]$buffer.Length, $Bytes - $done)
      $read = $sourceStream.Read($buffer, 0, $want)
      if ($read -le 0) { throw 'Unexpected VHD source EOF.' }
      $destinationStream.Write($buffer, 0, $read); $done += $read
    }
    $destinationStream.Flush($true)
  } finally { $destinationStream.Dispose(); $sourceStream.Dispose() }
}

Assert-Admin
foreach ($path in @($VhdPath, $firmwareFd, $patchBuilder)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing input: $path" } }
foreach ($path in @($OutputRaw, $PatchPath, $ReportPath)) { if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite: $path" } }
if ((Get-Item -LiteralPath $VhdPath).Length -ne ($expectedBytes + 512)) { throw 'Interrupted V16 VHD file length is unexpected.' }
if ((Hash $firmwareFd) -ne $expectedFirmware) { throw 'V11 FDT firmware artifact hash mismatch.' }
foreach ($directory in @((Split-Path -Parent $PatchPath), (Split-Path -Parent $ReportPath))) {
  if (-not (Test-Path -LiteralPath $directory -PathType Container)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
}

$mounted = $false
try {
  Mount-DiskImage -ImagePath $VhdPath -Access ReadOnly -NoDriveLetter | Out-Null; $mounted = $true
  $disk = Get-DiskImage -ImagePath $VhdPath | Get-Disk
  if ($disk.Size -ne $expectedBytes) { throw 'V16 VHD virtual disk size is unexpected.' }
  $part = Get-Partition -DiskNumber $disk.Number | Where-Object { $_.Offset -eq $expectedOffset -and $_.Size -eq $expectedPartitionBytes }
  if (@($part).Count -ne 1) { throw 'V16 VHD GPT geometry does not match the disposable Linux topology.' }
  $volume = $part | Get-Volume
  if ($volume.FileSystem -ne 'FAT32') { throw "Expected FAT32, got $($volume.FileSystem)." }
  if (-not $volume.DriveLetter) {
    Add-PartitionAccessPath -DiskNumber $disk.Number -PartitionNumber $part.PartitionNumber -AssignDriveLetter
    $volume = Get-Partition -DiskNumber $disk.Number -PartitionNumber $part.PartitionNumber | Get-Volume
  }
  if (-not $volume.DriveLetter) { throw 'Could not obtain a read-only VHD access path.' }
  $root = "$($volume.DriveLetter):"
  $initrd = Join-Path $root 'CASPER\INITRD'; $bootaa = Join-Path $root 'EFI\BOOT\BOOTAA64.EFI'; $kernel = Join-Path $root 'CASPER\VMLINUZ'
  foreach ($path in @($initrd, $bootaa, $kernel)) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "V16 VHD is missing expected payload: $path" } }
  if ((Hash $initrd) -ne $expectedInitrd) { throw 'V16 main-initramfs hash mismatch.' }
  if ((Hash $bootaa) -ne $expectedBootaa -or (Hash $kernel) -ne $expectedKernel) { throw 'Unexpected change outside CASPER\INITRD.' }
  $check = (& "$env:SystemRoot\System32\chkdsk.exe" "$($volume.DriveLetter):" 2>&1) -join "`n"
  if ($LASTEXITCODE -ne 0) { throw "CHKDSK reported a FAT32 error: $check" }
  Copy-PhysicalRead "\\.\PhysicalDrive$($disk.Number)" $OutputRaw $expectedBytes
  $rawHash = Hash $OutputRaw
  & $patchBuilder -BaselineRaw $OutputRaw -ExpectedBaselineSha256 $rawHash -FirmwareFd $firmwareFd -PatchPath $PatchPath -ReportPath (Join-Path (Split-Path -Parent $ReportPath) 'firmware-patch-report.txt')
  if ($LASTEXITCODE -ne 0) { throw 'V16 reversible firmware patch build failed.' }
  @(
    'RESULT=PASS', 'RECOVERY=Interrupted raw export resumed from read-only V16 VHD after full semantic audit',
    "VHD_PATH=$VhdPath", "VHD_DISK_NUMBER=$($disk.Number)", "RAW_PATH=$OutputRaw", "RAW_BYTES=$((Get-Item -LiteralPath $OutputRaw).Length)", "RAW_SHA256=$rawHash",
    "PATCHED_INITRD_SHA256=$expectedInitrd", "BOOTAA64_EFI_SHA256_UNCHANGED=$expectedBootaa", "VMLINUZ_SHA256_UNCHANGED=$expectedKernel",
    "PARTITION_OFFSET=$($part.Offset)", "PARTITION_BYTES=$($part.Size)", 'FAT32_CHKDSK=PASS',
    "FIRMWARE_PATCH=$PatchPath", "FIRMWARE_PATCH_SHA256=$(Hash $PatchPath)", "FIRMWARE_PATCH_BYTES=$((Get-Item -LiteralPath $PatchPath).Length)",
    'NEXT=V16 is offline-ready; stage only this raw and matching patch for one Linux vsock runtime.'
  ) | Set-Content -LiteralPath $ReportPath -Encoding utf8
  Get-Content -LiteralPath $ReportPath
} finally { if ($mounted) { Dismount-DiskImage -ImagePath $VhdPath -ErrorAction SilentlyContinue } }
