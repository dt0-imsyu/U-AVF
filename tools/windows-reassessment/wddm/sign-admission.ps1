[CmdletBinding()]
param([Parameter(Mandatory)][string]$BuildDirectory,
      [Parameter(Mandatory)][string]$OutputDirectory)

$ErrorActionPreference = 'Stop'
$build = (Resolve-Path -LiteralPath $BuildDirectory).Path
$out = [IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $out){throw 'Use a fresh package directory'}
$sign = Join-Path ([Environment]::GetFolderPath('ProgramFilesX86')) 'Windows Kits/10/bin/10.0.26100.0/x64/signtool.exe'
if(!(Test-Path -LiteralPath $sign)){throw 'Windows SDK SignTool missing'}
$files = 'UavfMavkAdmission.sys','UavfMavkAdmission.exe','d3d11-draw.exe'
foreach($name in $files) {
    $file = Join-Path $build $name
    $inputPe = [IO.File]::ReadAllBytes($file)
    if($inputPe.Length -lt 64 -or [BitConverter]::ToUInt16($inputPe,0) -ne 0x5a4d){throw "Invalid PE $name"}
    $pe = [BitConverter]::ToInt32($inputPe,60)
    if($pe -lt 64 -or $pe -gt $inputPe.Length-6 -or
       [BitConverter]::ToUInt32($inputPe,$pe) -ne 0x4550 -or
       [BitConverter]::ToUInt16($inputPe,$pe+4) -ne 0xaa64){throw "Expected ARM64 PE $name"}
}
$subject = 'CN=U-AVF isolated WDDM transport test'
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Where-Object {
    $_.Subject -eq $subject -and $_.HasPrivateKey -and $_.NotBefore -le (Get-Date) -and
    $_.NotAfter -gt (Get-Date).AddDays(7)
} | Sort-Object NotAfter -Descending | Select-Object -First 1
if(!$cert){
    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject $subject -KeyAlgorithm RSA -KeyLength 3072 -HashAlgorithm SHA256 -KeyExportPolicy NonExportable -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddDays(90)
}
New-Item -ItemType Directory -Path $out | Out-Null
foreach($name in $files){Copy-Item -LiteralPath (Join-Path $build $name) -Destination $out}
$sys = Join-Path $out 'UavfMavkAdmission.sys'
& $sign sign /fd SHA256 /s My /sha1 $cert.Thumbprint $sys
if($LASTEXITCODE){throw 'Embedding the ARM64 test signature failed'}
$signature = Get-AuthenticodeSignature -LiteralPath $sys
if($signature.SignerCertificate.Thumbprint -ne $cert.Thumbprint -or
   $signature.Status -notin @('Valid','NotTrusted','UnknownError')){throw 'Authenticode signer verification failed'}
$publicCert = Join-Path $out 'UavfMavkTransportTest.cer'
Export-Certificate -Cert $cert -FilePath $publicCert | Out-Null
$paths = @($files | ForEach-Object {Join-Path $out $_}) + $publicCert
Get-FileHash -LiteralPath $paths
