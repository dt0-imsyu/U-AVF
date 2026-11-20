param([switch]$RollbackOnly,[switch]$AdmissionAuditOnly,[switch]$AdmissionLoadOnly)
$ErrorActionPreference='Stop'
$base='C:\UAVF\IddWorkingProbe'
$thumb='80E30D3F7A18BD2A26A40EDF43C71F1D31F41558'
$principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if(!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Run this test elevated inside the approved Windows guest'}
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class IdGuest {
[StructLayout(LayoutKind.Sequential)] public struct Ci { public uint Length; public uint Options; }
[DllImport("ntdll.dll")] public static extern int NtQuerySystemInformation(int kind,ref Ci value,uint size,out uint written);
[DllImport("kernel32.dll",SetLastError=true)] public static extern bool GetExitCodeProcess(IntPtr process,out uint code);
[DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)]
public static extern bool GetVolumeInformation(string root,IntPtr name,uint size,out uint serial,IntPtr max,IntPtr flags,IntPtr fs,uint fsSize);
}
'@
[uint32]$serial=0
if(![IdGuest]::GetVolumeInformation('C:\',[IntPtr]::Zero,0,[ref]$serial,[IntPtr]::Zero,[IntPtr]::Zero,[IntPtr]::Zero,0) -or $serial -ne 0x3ef669b7){throw 'Not the approved working Windows root'}
$journal=Join-Path $base 'rollback.json'
if($AdmissionAuditOnly){
& whoami /all 2>&1|Out-File "$base\admission-token.txt"
foreach($store in @('Root','TrustedPublisher')){
Get-Acl "Registry::HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\SystemCertificates\$store"|Format-List|Out-File "$base\cert-store-access.txt" -Append
"$store ownCertPresent=$(Test-Path "Cert:\LocalMachine\$store\$thumb")"|Add-Content "$base\cert-store-access.txt"
}
return
}
function Undo-Probe {
if(!(Test-Path $journal)){return}
$state=Get-Content $journal -Raw|ConvertFrom-Json
if($state.serial -ne '3EF669B7' -or $state.thumb -ne $thumb){throw 'Unexpected rollback scope'}
$matches=@(Get-WindowsDriver -Online -All|Where-Object {
[IO.Path]::GetFileName($_.OriginalFileName) -ieq 'UavfIddSourceProbe.inf' -and $_.ProviderName -eq 'U-AVF experimental'
})
foreach($d in $matches){
if($d.Driver -notmatch '^oem\d+\.inf$'){throw 'Unexpected published driver name'}
& pnputil /delete-driver $d.Driver /uninstall 2>&1|Out-File (Join-Path $base 'driver-remove.txt') -Append
if($LASTEXITCODE){throw 'Driver removal failed; retain rollback journal, do not claim restored'}
}
foreach($store in @('Root','TrustedPublisher')){
$path="Cert:\LocalMachine\$store\$thumb"
if($state.certAbsentBefore -and (Test-Path $path)){Remove-Item -LiteralPath $path -ErrorAction Stop}
if(Test-Path $path){throw 'Own test certificate remains; do not claim cleanup'}
}
Remove-Item (Join-Path $base 'approved-working-test.txt') -ErrorAction SilentlyContinue
'DRIVER_AND_TEST_TRUST_REMOVED; NO_FPS_CLAIM'|Set-Content (Join-Path $base 'rollback-complete.txt')
}
if($RollbackOnly){Undo-Probe;return}
if(Test-Path $journal){throw 'Previous probe exists: inspect or run -RollbackOnly first'}
if((Get-Item $PSScriptRoot).FullName -ne $base){throw 'Payload must be staged in the exact working-test directory'}
foreach($store in @('Root','TrustedPublisher')){if(Test-Path "Cert:\LocalMachine\$store\$thumb"){throw 'Existing test trust: do not delete preexisting certificates'}}
if(@(Get-WindowsDriver -Online -All|Where-Object {[IO.Path]::GetFileName($_.OriginalFileName) -ieq 'UavfIddSourceProbe.inf'}).Count){throw 'Preexisting probe driver: refuse ambiguous rollback'}
$bcd=(& bcdedit /enum '{current}') -join "`n"
$bcd|Set-Content "$base\bcd-current.txt"
$ci=[IdGuest+Ci]::new();$ci.Length=8;[uint32]$returned=0
$ciStatus=[IdGuest]::NtQuerySystemInformation(103,[ref]$ci,8,[ref]$returned)
@{status=$ciStatus;options=$ci.Options;returned=$returned;utc=(Get-Date).ToUniversalTime().ToString('o')}|ConvertTo-Json|Set-Content "$base\admission-preflight.json"
if($ciStatus -ne 0 -or ($ci.Options -band 2) -eq 0){throw 'Runtime Code Integrity does not allow test signing; no driver staged'}
if(!(Test-Path 'C:\Windows\System32\drivers\UMDF\IddCx.dll')){throw 'Native IddCx framework absent: no driver staging allowed'}
if(!(Test-Path 'C:\Windows\System32\vcruntime140.dll') -or !(Test-Path 'C:\Windows\System32\msvcp140.dll')){
$redist=Join-Path $base 'VC_redist.arm64.exe'
if((Get-FileHash $redist).Hash -ne 'B70EF586669A620A0A30A1156969C05C6A3831DC8F8BC992DA75779D2A92F944'){throw 'Unexpected Microsoft runtime package'}
$sig=Get-AuthenticodeSignature $redist
@{status=[string]$sig.Status;message=$sig.StatusMessage;subject=$sig.SignerCertificate.Subject;utc=(Get-Date).ToUniversalTime().ToString('o')}|ConvertTo-Json|Set-Content "$base\runtime-signature.json"
if($sig.Status -ne 'Valid' -or $sig.SignerCertificate.Subject -notmatch 'Microsoft Corporation'){throw 'Microsoft runtime signature failed'}
$vc=Start-Process $redist -ArgumentList '/install /quiet /norestart' -Wait -PassThru
if($vc.ExitCode -notin @(0,3010)){throw "Microsoft runtime install failed: $($vc.ExitCode)"}
foreach($dll in @('vcruntime140.dll','msvcp140.dll')){if(!(Test-Path "C:\Windows\System32\$dll")){throw "Runtime still missing: $dll"}}
}
$cert=[Security.Cryptography.X509Certificates.X509Certificate2]::new((Join-Path $base 'package\UavfIddTestOnly.cer'))
if($cert.Thumbprint -ne $thumb){throw 'Unexpected certificate'}
@{utc=(Get-Date).ToUniversalTime().ToString('o');notBefore=$cert.NotBefore.ToUniversalTime().ToString('o');notAfter=$cert.NotAfter.ToUniversalTime().ToString('o');thumb=$cert.Thumbprint}|ConvertTo-Json|Set-Content "$base\certificate-preflight.json"
if((Get-Date) -lt $cert.NotBefore -or (Get-Date) -gt $cert.NotAfter){throw 'Guest calendar outside test certificate validity; no clock changes made'}
@{serial='3EF669B7';thumb=$thumb;certAbsentBefore=$true;started=(Get-Date).ToString('o')}|ConvertTo-Json|Set-Content $journal
[IO.File]::WriteAllText((Join-Path $base 'approved-working-test.txt'),'APPROVED_WORKING_IDD_TEST_3EF669B7',[Text.Encoding]::ASCII)
$device=$null;$motion=$null;$trace=$false
try {
foreach($store in @('Root','TrustedPublisher')){
$store|Set-Content "$base\cert-import-attempt.txt"
$s=[Security.Cryptography.X509Certificates.X509Store]::new($store,'LocalMachine')
try {$s.Open('ReadWrite');$s.Add($cert)} finally {$s.Close()}
if(!(Test-Path "Cert:\LocalMachine\$store\$thumb")){throw 'Certificate readback failed'}
}
& pnputil /add-driver "$base\package\UavfIddSourceProbe.inf" /install 2>&1|Out-File "$base\driver-stage.txt"
if($LASTEXITCODE){throw 'Driver staging failed'}
& logman start UavfIddWorkingSource -p '{43ea47ba-292b-4e83-a8ca-c0d468ea31ae}' 0xffffffffffffffff 5 -o "$base\source.etl" -ets
if($LASTEXITCODE){throw 'Trace start failed'};$trace=$true
$device=Start-Process "$base\idd-probe-device.exe" -ArgumentList '--approved-working-load-probe' -PassThru -RedirectStandardOutput "$base\admission.txt" -RedirectStandardError "$base\admission-errors.txt"
$deviceHandle=$device.Handle
if(!$AdmissionLoadOnly){
$motion=Start-Process "$base\idd-motion-source.exe" -PassThru
$motionHandle=$motion.Handle
if(!$motion.WaitForExit(185000)){throw 'Motion deadline reached'}
} else {if(!$device.WaitForExit(245000)){throw 'Admission deadline reached'}}
if(!$device.WaitForExit(75000)){throw 'Device close deadline reached'}
[uint32]$deviceExit=259;[uint32]$motionExit=0
if(![IdGuest]::GetExitCodeProcess($deviceHandle,[ref]$deviceExit)){throw 'Device exit query failed'}
if($motion -and ![IdGuest]::GetExitCodeProcess($motionHandle,[ref]$motionExit)){throw 'Motion exit query failed'}
"DEVICE_EXIT=$deviceExit; MOTION_EXIT=$motionExit; NOT_ANDROID_PRESENT_PASS"|Set-Content "$base\result.txt"
if($deviceExit -ne 0 -or $motionExit -ne 0){throw 'Child probe failed; no Desktop/FPS PASS'}
} finally {
foreach($p in @($motion,$device)){if($null -ne $p -and !$p.HasExited){Stop-Process -Id $p.Id -ErrorAction SilentlyContinue}}
if($trace){& logman stop UavfIddWorkingSource -ets}
if(Test-Path "$base\source.etl"){& "$base\idd-etw-read.exe" "$base\source.etl"|Out-File "$base\source-report.txt"}
Undo-Probe
}
