[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)] [string] $RawPath,
  [string] $Adb = 'C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe',
  [string] $Package = 'com.example.winavf',
  [string] $PrivateRelativePath = 'files/ubuntu-gnome-payload/ubuntu-gnome-24.04.5-v13-vsock.img',
  [string] $ReportPath = 'C:\Users\denis\MainProjects\win11ontab\build-logs\ubuntu-gnome-v13-vsock\adb-private-preload-report.txt'
)

# Direct binary ADB -> run-as transfer avoids keeping both a 9 GiB external
# staging copy and an app-private copy on the tablet.  It targets only the
# debug-signed WinAVF package's private directory; it cannot access Windows
# media or another Android package.
$ErrorActionPreference = 'Stop'
foreach ($path in @($RawPath, $Adb)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required input: $path" }
}
if (Test-Path -LiteralPath $ReportPath) { throw "Refusing to overwrite: $ReportPath" }
$raw = (Resolve-Path -LiteralPath $RawPath).Path
$bytes = (Get-Item -LiteralPath $raw).Length
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $raw).Hash.ToLowerInvariant()
if ($bytes -ne 9126805504) { throw 'Unexpected disposable Ubuntu raw length.' }

& $Adb shell "run-as $Package mkdir -p files/ubuntu-gnome-payload"
if ($LASTEXITCODE -ne 0) { throw 'Could not create the private Ubuntu payload directory.' }
& $Adb shell "run-as $Package sh -c 'test ! -e $PrivateRelativePath'"
if ($LASTEXITCODE -ne 0) { throw "Refusing to overwrite an existing private target: $PrivateRelativePath" }

$psi = [Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $Adb
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardError = $true
$psi.RedirectStandardOutput = $true
# exec-out is intentionally output-only and leaves StandardInput disconnected.
# A non-PTY shell keeps the stream byte-transparent while carrying host input
# into the app-private cat process.
$psi.Arguments = "shell -T `"run-as $Package sh -c 'cd /data/user/0/$Package && cat > $PrivateRelativePath'`""
$process = [Diagnostics.Process]::new(); $process.StartInfo = $psi
if (-not $process.Start()) { throw 'Could not start adb exec-out transfer.' }
try {
  $source = [IO.File]::OpenRead($raw)
  try { $source.CopyTo($process.StandardInput.BaseStream, 4MB); $process.StandardInput.Close() } finally { $source.Dispose() }
  $stdout = $process.StandardOutput.ReadToEnd(); $stderr = $process.StandardError.ReadToEnd()
  $process.WaitForExit()
  if ($process.ExitCode -ne 0) { throw "adb private preload failed exit=$($process.ExitCode): $stderr $stdout" }
} finally { $process.Dispose() }

$remote = (& $Adb shell "run-as $Package sha256sum $PrivateRelativePath" 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or $remote -notmatch "^$hash\s") { throw "Private target SHA-256 mismatch: $remote" }
@(
  'RESULT=PASS',
  'DESTINATION=WinAVF app-private storage',
  "PACKAGE=$Package", "PRIVATE_RELATIVE_PATH=$PrivateRelativePath", "RAW_BYTES=$bytes", "RAW_SHA256=$hash",
  'ADB_BINARY_TRANSFER=PASS', 'PRIVATE_SHA256=PASS',
  'NEXT=Stage only the small matching firmware patch externally, then launch one disposable Ubuntu vsock probe.'
) | Set-Content -LiteralPath $ReportPath -Encoding utf8
Get-Content -LiteralPath $ReportPath
