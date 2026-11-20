param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
New-Item -ItemType Directory -Path $output | Out-Null
$target=Join-Path $output 'VC_redist.arm64.exe'
# Official Microsoft permalink, binary saved only; never executed on host.
Invoke-WebRequest https://aka.ms/vc14/vc_redist.arm64.exe -OutFile $target
$signature=Get-AuthenticodeSignature $target
if($signature.Status -ne 'Valid' -or !$signature.SignerCertificate -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') {
    throw 'Official runtime signature gate failed; do not execute or deploy'
}
Get-FileHash $target
(Get-Item $target).VersionInfo | Select-Object ProductVersion,FileDescription
'MICROSOFT_RUNTIME_SIGNATURE_PASS; DOWNLOADED_ONLY_NOT_INSTALLED=1'
