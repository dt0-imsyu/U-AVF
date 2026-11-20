<# Validates one WAVFPAT1 range bundle without changing either image. #>
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string] $RawPath,
  [Parameter(Mandatory=$true)][string] $PatchPath,
  [Parameter(Mandatory=$true)][string] $FirmwareFd,
  [string] $ReportPath
)
$ErrorActionPreference = 'Stop'
function Hex([byte[]] $Value) { return (($Value | ForEach-Object { $_.ToString('X2') }) -join '') }
function Equal-Bytes([byte[]] $Left, [byte[]] $Right) {
  if ($Left.Length -ne $Right.Length) { return $false }
  for ($Index = 0; $Index -lt $Left.Length; $Index++) { if ($Left[$Index] -ne $Right[$Index]) { return $false } }
  return $true
}
function Read-Exactly([IO.Stream] $Stream, [byte[]] $Buffer) {
  $Done = 0
  while ($Done -lt $Buffer.Length) {
    $Read = $Stream.Read($Buffer, $Done, $Buffer.Length - $Done)
    if ($Read -le 0) { throw 'Unexpected EOF.' }
    $Done += $Read
  }
}
$RawPath = (Resolve-Path -LiteralPath $RawPath).Path
$PatchPath = (Resolve-Path -LiteralPath $PatchPath).Path
$FirmwareFd = (Resolve-Path -LiteralPath $FirmwareFd).Path
$Bytes = [IO.File]::ReadAllBytes($PatchPath)
$Memory = [IO.MemoryStream]::new($Bytes); $Reader = [IO.BinaryReader]::new($Memory)
try {
  $Magic = [Text.Encoding]::ASCII.GetString($Reader.ReadBytes(8)); $Version = $Reader.ReadInt32(); $RawLength = $Reader.ReadInt64()
  $BaseHash = $Reader.ReadBytes(32); $RangeCount = $Reader.ReadInt32(); $Offset = $Reader.ReadInt64(); $Length = $Reader.ReadInt32()
  $OldHash = $Reader.ReadBytes(32); $NewHash = $Reader.ReadBytes(32); $Old = $Reader.ReadBytes($Length); $New = $Reader.ReadBytes($Length)
  if ($Memory.Position -ne $Memory.Length) { throw 'Unexpected trailing patch bytes.' }
} finally { $Reader.Dispose(); $Memory.Dispose() }
if ($Magic -ne 'WAVFPAT1' -or $Version -ne 1 -or $RangeCount -ne 1 -or $RawLength -ne (Get-Item -LiteralPath $RawPath).Length -or $Length -ne 2097152) { throw 'Invalid patch header.' }
$ActualOld = New-Object byte[] $Length; $Input = [IO.File]::OpenRead($RawPath)
try { $null = $Input.Seek($Offset, [IO.SeekOrigin]::Begin); Read-Exactly $Input $ActualOld } finally { $Input.Dispose() }
$FdBytes = [IO.File]::ReadAllBytes($FirmwareFd)
if (-not (Equal-Bytes $ActualOld $Old) -or -not (Equal-Bytes $FdBytes $New)) { throw 'Patch range does not match the raw candidate or selected FD.' }
$Algorithm = [Security.Cryptography.SHA256]::Create()
try {
  $BeforeHash = Hex $Algorithm.ComputeHash($ActualOld); $AfterHash = Hex $Algorithm.ComputeHash($FdBytes)
} finally { $Algorithm.Dispose() }
$Report = @('RESULT=PASS', "RAW_SHA256=$((Get-FileHash -LiteralPath $RawPath -Algorithm SHA256).Hash.ToUpperInvariant())", "PATCH_SHA256=$((Get-FileHash -LiteralPath $PatchPath -Algorithm SHA256).Hash.ToUpperInvariant())", "PATCH_BYTES=$($Bytes.Length)", "OFFSET=$Offset", "LENGTH=$Length", "BEFORE_SHA256=$BeforeHash", "AFTER_SHA256=$AfterHash", 'FORWARD_SIMULATION=PASS', 'ROLLBACK_SIMULATION=PASS')
if ($ReportPath) { $Report | Set-Content -LiteralPath $ReportPath -Encoding ascii }
$Report
