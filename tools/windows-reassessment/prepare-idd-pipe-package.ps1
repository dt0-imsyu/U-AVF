param([Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$ReconnectDriver)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$source='E:\U-AVF\artifacts\idd-frame-pipe-lto-20261014'
$expected='B15D9F4C840F3EB8569DAA6181136BAB37BB3F270933822E83F9920B4F0AFF5A'
if($ReconnectDriver){$source='E:\U-AVF\artifacts\idd-reconnect-driver-20261014';$expected='42F1D3F3DBC86AFE079E3AA6EBC0672DCF26B9A976DDA6574FC612D5138EE29C'}
if((Get-FileHash "$source/IddSampleDriver.dll").Hash -ne $expected -or (Get-Item "$source/IddSampleDriver.dll").Length -gt 24576){throw 'Unexpected/oversize pipe driver'}
$cert=Get-Item 'Cert:\CurrentUser\My\80E30D3F7A18BD2A26A40EDF43C71F1D31F41558'
if(!$cert.HasPrivateKey -or $cert.NotAfter -le (Get-Date)){throw 'Existing own test key unavailable/expired'}
& 'C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe' "$PSScriptRoot/prepare-idd-proof-package.py" $source $output
if($LASTEXITCODE){throw 'Package generation failed'}
$inf2cat=Join-Path $root 'tools/wdk-nuget/Microsoft.Windows.WDK.ARM64.10.0.26100.6584/c/bin/10.0.26100.0/x86/Inf2Cat.exe'
& $inf2cat "/driver:$output" /os:10_GE_ARM64
if($LASTEXITCODE){throw 'Exact ARM64 catalog generation failed'}
$sign='C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe'
& $sign sign /fd SHA256 /sha1 $cert.Thumbprint "$output/IddSampleDriver.cat"
if($LASTEXITCODE){throw 'Catalog signing failed'}
$signature=Get-AuthenticodeSignature "$output/IddSampleDriver.cat"
if(!$signature.SignerCertificate -or $signature.SignerCertificate.Thumbprint -ne $cert.Thumbprint){throw 'Wrong signer'}
Export-Certificate -Cert $cert -FilePath "$output/UavfIddTestOnly.cer"|Out-Null
Get-FileHash "$output/IddSampleDriver.dll","$output/IddSampleDriver.cat","$output/UavfIddSourceProbe.inf"
'EXACT_TEST_CATALOG_SIGNED; NO_HOST_TRUST_IMPORT; NOT_STAGED_OR_GUEST_PASS'
