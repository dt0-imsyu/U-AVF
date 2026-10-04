[CmdletBinding()]
param(
  [string] $OutputPath = 'C:\Users\denis\MainProjects\win11ontab\build-logs\ubuntu-gnome-vsock\winavf-vsock-hello',
  [string] $ReportPath = 'C:\Users\denis\MainProjects\win11ontab\build-logs\ubuntu-gnome-vsock\build-probe-report.txt'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$toolchain = 'C:\Users\denis\MainProjects\win11ontab\tools\arm-gnu-toolchain-15.2\bin'
$gcc = Join-Path $toolchain 'aarch64-none-elf-gcc.exe'
$readelf = Join-Path $toolchain 'aarch64-none-elf-readelf.exe'
$source = Join-Path $PSScriptRoot 'guest\winavf_vsock_hello.S'

foreach ($path in @($gcc, $readelf, $source)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required build input: $path" }
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutputPath), (Split-Path -Parent $ReportPath) | Out-Null
if (Test-Path -LiteralPath $OutputPath) { Remove-Item -LiteralPath $OutputPath -Force }

# The bare-metal linker defaults to a 64 KiB LOAD offset.  The Ubuntu
# initramfs shell reports that ELF as "not found" even though the CPIO entry
# is present and executable.  Use a conventional Linux-compatible 4 KiB
# maximum page size; it removes the sparse 64 KiB ELF prefix without adding a
# dynamic loader dependency.
& $gcc '-nostdlib' '-static' '-Wl,--build-id=none' '-Wl,-e,_start' '-Wl,-z,max-page-size=4096' '-o' $OutputPath $source
if ($LASTEXITCODE -ne 0) { throw 'Linux/ARM64 vsock probe build failed.' }
$headers = & $readelf -h $OutputPath 2>&1
$headerText = $headers -join "`n"
if ($LASTEXITCODE -ne 0 -or $headerText -notmatch 'Machine:\s+AArch64' -or $headerText -notmatch 'Type:\s+EXEC') {
  throw 'Output is not a static ARM64 executable.'
}
$programHeaders = & $readelf -l $OutputPath 2>&1
$programHeaderText = $programHeaders -join "`n"
if ($LASTEXITCODE -ne 0 -or $programHeaderText -notmatch 'LOAD\s+0x0000000000001000') {
  throw 'Probe does not have the required compact 4 KiB ELF LOAD offset.'
}
$sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $OutputPath).Hash.ToUpperInvariant()
@(
  'RESULT=PASS',
  'TARGET=Linux ARM64 static AF_VSOCK listener',
  'PORT=4051',
  "PROBE_PATH=$OutputPath",
  "PROBE_SHA256=$sha",
  'ELF_AARCH64=PASS',
  'ELF_STATIC_EXEC=PASS',
  'ELF_COMPACT_LOAD_OFFSET_4K=PASS',
  'NEXT=Append this probe and its init-premount hook to a disposable Ubuntu initrd only.'
) | Set-Content -LiteralPath $ReportPath -Encoding utf8
Get-Content -LiteralPath $ReportPath
