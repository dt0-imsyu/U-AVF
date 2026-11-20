param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
if(Test-Path $OutputDirectory){throw 'Preserve previous payloads'}
$output=[IO.Path]::GetFullPath($OutputDirectory)
if(!$output.StartsWith('E:\U-AVF\artifacts\',[StringComparison]::OrdinalIgnoreCase)){throw 'Large artifacts belong on approved E disk'}
New-Item -ItemType Directory $output|Out-Null
$package=Join-Path $output 'package';New-Item -ItemType Directory $package|Out-Null
Copy-Item E:\U-AVF\artifacts\idd-test-signed-package-20261013\* $package
Copy-Item E:\U-AVF\artifacts\idd-working-helper-20261013\idd-probe-device.exe $output
Copy-Item E:\U-AVF\artifacts\idd-working-motion-bounded-20261013\idd-motion-source.exe $output
Copy-Item E:\U-AVF\artifacts\idd-etw-reader-20261013\idd-etw-read.exe $output
Copy-Item E:\U-AVF\artifacts\idd-test-ms-runtime-20261013\VC_redist.arm64.exe $output
$script=Join-Path $output 'idd-working-test.ps1'
# Windows PowerShell5 reads UTF8-BOM, required for localized BCD Yes/Да.
[IO.File]::WriteAllText($script,[IO.File]::ReadAllText("$PSScriptRoot\idd-working-test.ps1"),[Text.UTF8Encoding]::new($true))
$parseErrors=$null;[Management.Automation.Language.Parser]::ParseFile($script,[ref]$null,[ref]$parseErrors)|Out-Null
if($parseErrors.Count){throw 'Guest job parse failed'}
$manifest=@(Get-ChildItem $output -File -Recurse|ForEach-Object {@{file=$_.FullName.Substring($output.Length+1);bytes=$_.Length;sha256=(Get-FileHash $_.FullName).Hash}})
@{scope='User-approved one-off working Windows 3EF669B7';driverLoaded=$false;android60FpsProven=$false;files=$manifest}|ConvertTo-Json -Depth 4|Set-Content (Join-Path $output 'manifest.json')
Compress-Archive -LiteralPath $output -DestinationPath "$output.zip"
Get-FileHash "$output.zip"
'PAYLOAD_BUILD_ONLY; NOT_DRIVER_ADMISSION_OR_FPS_PASS'
