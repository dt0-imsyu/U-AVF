param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$source='E:\U-AVF\artifacts\idd-source-package-20261013'
if((Get-FileHash "$source/IddSampleDriver.dll").Hash -ne '75EFA21C040D1F110EBFC7247735025B77FB060EE9A5DF724EC4CF00944C6AFB'){throw 'Exact source proof DLL only'}
New-Item -ItemType Directory -Path $output | Out-Null
Copy-Item "$source/IddSampleDriver.dll","$source/IddSampleDriver.cat","$source/UavfIddSourceProbe.inf","$source/LICENSE.MS-PL" -Destination $output
# Own disposable test key, CurrentUser/My only. No root/trusted-store import,
# no production signing key access, no export of private key or password.
$cert=New-SelfSignedCertificate -Type CodeSigningCert -Subject 'CN=U-AVF isolated IddCx test ONLY' -CertStoreLocation Cert:\CurrentUser\My -KeyAlgorithm RSA -KeyLength 2048 -HashAlgorithm SHA256 -NotAfter (Get-Date).AddDays(14)
Export-Certificate -Cert $cert -FilePath "$output/UavfIddTestOnly.cer" | Out-Null
$sign='C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe'
& $sign sign /fd SHA256 /sha1 $cert.Thumbprint "$output/IddSampleDriver.cat"
if($LASTEXITCODE){throw 'Test catalog signing failed; do not stage'}
$auth=Get-AuthenticodeSignature "$output/IddSampleDriver.cat"
if(!$auth.SignerCertificate -or $auth.SignerCertificate.Thumbprint -ne $cert.Thumbprint){throw 'Actual test signer mismatch'}
$cert.Thumbprint
$auth | Select-Object Status,StatusMessage
'TEST_CATALOG_SIGNED; NO_TRUST_IMPORTED; NOT_GUEST_DRIVER_LOAD_OR_FPS_PASS=1'
