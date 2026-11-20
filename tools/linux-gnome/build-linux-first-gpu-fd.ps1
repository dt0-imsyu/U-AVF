[CmdletBinding()]
param()

# Disposable diagnostic firmware. The stock FDF, P7 FD and GOLDEN stay intact.
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$workspace = Join-Path $repo 'firmware-work\edk2'
$toolsPath = Join-Path $workspace 'BaseTools'
$win64 = Join-Path $toolsPath 'Bin\Win64'
$win32 = Join-Path $toolsPath 'Bin\Win32'
$python = Join-Path $repo 'tools\toolchains\llvm-mingw-20260826-clean-extract\llvm-mingw-20260826-ucrt-x86_64\python\bin\python.exe'
$gccPrefix = Join-Path $repo 'tools\arm-gnu-toolchain-15.2\bin\aarch64-none-elf-'
$gccBin = Split-Path -Parent $gccPrefix
$makeBin = 'C:\msys64\mingw64\bin'
$buildPy = Join-Path $toolsPath 'Source\Python\build\build.py'
$fdf = Join-Path $workspace 'ArmVirtPkg\ArmVirtKvmToolLinuxFirstGpu.fdf'
$stockFdf = Join-Path $workspace 'ArmVirtPkg\ArmVirtKvmTool.fdf'
$stockFd = Join-Path $workspace 'Build\ArmVirtKvmTool-AARCH64\DEBUG_GCC5\FV\KVMTOOL_EFI.fd'
$expectedStockFd = '7162202A2ED14C6BE3433915CB5786D9A41E3722647E40AA3CFEF29720D14963'
$logDir = Join-Path $repo 'build-logs\gfxstream-zink-20260926\linux-first-gpu'
$log = Join-Path $logDir 'firmware-build.log'
$backupFd = Join-Path $logDir 'KVMTOOL_EFI.p7-stock.backup.fd'
$candidateFd = Join-Path $logDir 'KVMTOOL_EFI.linux-first-gpu.candidate.fd'

foreach ($path in @($workspace, $win64, $win32, $python, ($gccPrefix + 'gcc.exe'),
                   (Join-Path $makeBin 'mingw32-make.exe'), $buildPy, $fdf, $stockFdf, $stockFd)) {
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing build input: $path" }
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $stockFd).Hash -ne $expectedStockFd) {
  throw 'Stock P7 firmware build output changed; refusing diagnostic build.'
}
$stockText = [IO.File]::ReadAllText($stockFdf).Replace("`r`n", "`n")
$expectedText = $stockText.Replace('  INF OvmfPkg/VirtioGpuDxe/VirtioGpu.inf',
  '  # Diagnostic A/B only: Linux must be the first virtio-GPU driver.' + "`n" +
  '  # Physical virtio-GPU remains present in crosvm; stock FDF is unchanged.')
if ([IO.File]::ReadAllText($fdf).Replace("`r`n", "`n") -cne $expectedText) {
  throw 'Diagnostic FDF differs from stock by more than the single GPU driver omission.'
}
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
if (-not (Test-Path -LiteralPath $backupFd)) {
  Copy-Item -LiteralPath $stockFd -Destination $backupFd
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $backupFd).Hash -ne $expectedStockFd) {
  throw 'Firmware backup is not the exact P7 stock FD.'
}
if (Test-Path -LiteralPath $candidateFd) { throw 'Candidate FD already exists; refusing overwrite.' }

$env:WORKSPACE = $workspace
$env:EDK_TOOLS_PATH = $toolsPath
$env:EDK_TOOLS_BIN = $win64
$env:CONF_PATH = Join-Path $workspace 'Conf'
$env:PYTHON_COMMAND = $python
$env:PYTHONPATH = Join-Path $toolsPath 'Source\Python'
$env:GCC5_AARCH64_PREFIX = $gccPrefix
$env:GCC_HOST_BIN = Join-Path $makeBin 'mingw32-'
$env:MAKE_FLAGS = ''
$env:SHELL = 'C:\Windows\System32\cmd.exe'
$env:MAKESHELL = 'C:\Windows\System32\cmd.exe'
$env:MSYS_NO_PATHCONV = '1'
$env:MSYS2_ARG_CONV_EXCL = '*'
$env:PATH = @('C:\Windows\System32', 'C:\Windows', $win64, $win32, $gccBin, $makeBin) -join ';'

$args = @($buildPy, '-n', '4', '-a', 'AARCH64', '-t', 'GCC5',
  '-p', 'ArmVirtPkg/ArmVirtKvmTool.dsc', '-b', 'DEBUG',
  '-f', 'ArmVirtPkg/ArmVirtKvmToolLinuxFirstGpu.fdf')
Push-Location $workspace
try {
  try {
    # build.py reports normal progress on stderr; PowerShell must not promote it.
    $ErrorActionPreference = 'Continue'
    & $python @args 2>&1 | Tee-Object -FilePath $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0 -or -not (Test-Path -LiteralPath $stockFd) -or
        (Get-Item -LiteralPath $stockFd).Length -ne 2097152 -or
        -not (Select-String -LiteralPath $log -Pattern '^- Done -$' -Quiet)) {
      throw "LINUX_FIRST_GPU_FD_BUILD=FAIL code=$code log=$log"
    }
    Copy-Item -LiteralPath $stockFd -Destination $candidateFd
  } finally {
    $ErrorActionPreference = 'Stop'
    Copy-Item -LiteralPath $backupFd -Destination $stockFd -Force
  }
} finally { Pop-Location }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $stockFd).Hash -ne $expectedStockFd) {
  throw 'Stock P7 firmware build output was modified.'
}
[pscustomobject]@{
  Result = 'PASS'
  DiagnosticFd = $candidateFd
  Bytes = (Get-Item -LiteralPath $candidateFd).Length
  Sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $candidateFd).Hash
  StockFdUnchanged = 'PASS'
  Log = $log
} | Format-List
