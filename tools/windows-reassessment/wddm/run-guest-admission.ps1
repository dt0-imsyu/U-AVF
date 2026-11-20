[CmdletBinding()]
param([switch]$Run, [switch]$TrustTestCertificate, [switch]$RetainedDiagnosticGuest,
      [switch]$Codec)

$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
$elevated = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
$system = $identity.IsSystem
$architecture = $env:PROCESSOR_ARCHITECTURE
$secureBoot = try { Confirm-SecureBootUEFI } catch { "unknown ($($_.FullyQualifiedErrorId))" }
$deviceGuard = try {
    Get-CimInstance -Namespace root/Microsoft/Windows/DeviceGuard -ClassName Win32_DeviceGuard
} catch {
    Write-Warning "Device Guard query failed: $($_.FullyQualifiedErrorId)"
}
$bcd = & bcdedit.exe /enum ACTIVE 2>&1
$bcdExit = $LASTEXITCODE
$providers = Get-ChildItem HKLM:\SYSTEM\CurrentControlSet\Services | Where-Object {
    $_.PSChildName -match 'viosock'
} | ForEach-Object { $_.PSChildName }
if (!('UavfMavkCodeIntegrity' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class UavfMavkCodeIntegrity {
    [StructLayout(LayoutKind.Sequential)]
    public struct Information { public uint Length; public uint Options; }
    [DllImport("ntdll.dll")]
    public static extern int NtQuerySystemInformation(int kind, ref Information data,
        uint length, out uint returned);
}
'@
}
$ciInfo = [UavfMavkCodeIntegrity+Information]::new()
$ciInfo.Length = 8
$ciBytes = [uint32]0
$ciStatus = [UavfMavkCodeIntegrity]::NtQuerySystemInformation(103, [ref]$ciInfo, 8, [ref]$ciBytes)
$ciKnown = $ciStatus -eq 0 -and $ciBytes -eq 8
$ciTestSigning = $ciKnown -and ($ciInfo.Options -band 2) -ne 0
[ordered]@{
    architecture = $architecture
    elevated = $elevated
    system = $system
    secure_boot = $secureBoot
    vbs = $deviceGuard.VirtualizationBasedSecurityStatus
    hvci_running = $(if ($deviceGuard) { 2 -in $deviceGuard.SecurityServicesRunning })
    code_integrity = $deviceGuard.CodeIntegrityPolicyEnforcementStatus
    native_ci_status = '0x{0:X8}' -f $ciStatus
    native_ci_options = $(if ($ciKnown) { '0x{0:X8}' -f $ciInfo.Options })
    native_ci_test_signing = $(if ($ciKnown) { $ciTestSigning })
    native_ci_hvci = $(if ($ciKnown) { ($ciInfo.Options -band 0x400) -ne 0 })
    viosock_services = @($providers)
    bcd_exit = $bcdExit
} | ConvertTo-Json -Compress
$bcd | Out-Host
if (!$Run) { return }

if (!$elevated -or $bcdExit -ne 0) { throw 'An elevated guest token and readable BCD are required' }
if ($architecture -ne 'ARM64') {
    throw 'The admission package requires Windows ARM64'
}
$volumeInfo = & fsutil.exe fsinfo ntfsinfo $env:SystemDrive
$expectedSerial = if ($RetainedDiagnosticGuest) { 'C63EF6813EF669B7' } else { '4AA580661DE89658' }
if ($LASTEXITCODE -ne 0 -or ($volumeInfo -join "`n") -notmatch "\b0x$expectedSerial\b") {
    throw 'Isolated guest volume identity mismatch'
}

$service = if ($Codec) { 'UavfMavkCodecAdmission' } else { 'UavfMavkAdmission' }
$driver = Join-Path $PSScriptRoot 'UavfMavkAdmission.sys'
$client = Join-Path $PSScriptRoot 'UavfMavkAdmission.exe'
$localDriver = Join-Path $env:SystemRoot "System32\drivers\$service.sys"
$driverHash = (Get-FileHash -LiteralPath $driver).Hash
$certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new(
    (Join-Path $PSScriptRoot 'UavfMavkTransportTest.cer'))
$signature = Get-AuthenticodeSignature -LiteralPath $driver
if ($signature.SignerCertificate.Thumbprint -ne $certificate.Thumbprint -or
    $signature.Status -notin @('Valid','NotTrusted','UnknownError')) {
    throw 'Driver and test certificate do not match'
}
function Invoke-Sc([string[]]$ScArgs) {
    & sc.exe @ScArgs | Out-Host
    return $LASTEXITCODE
}
$serviceKey = "HKLM:\SYSTEM\CurrentControlSet\Services\$service"
if (Test-Path -LiteralPath $serviceKey) {
    $prior = Get-ItemProperty -LiteralPath $serviceKey
    if (!$RetainedDiagnosticGuest -or $prior.Start -ne 3 -or $prior.Type -ne 1 -or
        $prior.ImagePath -notin '\??\Q:\WDDMTEST\UavfMavkAdmission.sys','\??\C:\UavfMavkAdmission.sys',("\??\$localDriver"),("\SystemRoot\System32\drivers\$service.sys")) {
        throw 'Preserving preexisting service'
    }
    $status = Get-Service -Name $service
    if ($status.Status -ne 'Stopped') { throw 'Restart isolated guest to unload the previous test driver' }
    $status.Dispose()
    $rc = Invoke-Sc @('delete', $service)
    if ($rc -ne 0) { throw "SC_RECOVER_DELETE_FAILED win32=$rc" }
}
if (Test-Path -LiteralPath $localDriver) {
    $priorHash = (Get-FileHash -LiteralPath $localDriver).Hash
    $ownedDriverHashes = 'E3BDA12AE7003EC5E5EAA699BA7D80057CFDECA6682FBAE47D08A1FA61D8F843',
                         'E62C4C0F0E6353A93EF4E52EF7820C36D2B480FA22587EBE50A1EA140DBA01C7',
                         $driverHash,
                         '93BF267D95E9B75A583E04BC61119F4866DEF6378927EF11EB2CB80BECD3A56A'
    if (!$RetainedDiagnosticGuest -or $priorHash -notin $ownedDriverHashes) {
        throw 'Preserving preexisting driver file'
    }
    Remove-Item -LiteralPath $localDriver
}
$created = $false
$started = $false
$localCopy = $false
$imported = @()
$probeStart = Get-Date
try {
    if ($TrustTestCertificate) {
        if (!$ciTestSigning) { throw 'Effective Code Integrity test mode is required for temporary trust' }
        foreach ($store in 'Root','TrustedPublisher') {
            $path = "Cert:\LocalMachine\$store\$($certificate.Thumbprint)"
            if (!(Test-Path -LiteralPath $path)) {
                $imported += $path
                $certStore = [Security.Cryptography.X509Certificates.X509Store]::new($store, 'LocalMachine')
                try {
                    $certStore.Open('ReadWrite')
                    $certStore.Add($certificate)
                } finally { $certStore.Dispose() }
                if (!(Test-Path -LiteralPath $path)) { throw "Certificate readback failed in $store" }
            }
        }
    }
    [IO.File]::Copy($driver, $localDriver, $false)
    $localCopy = $true
    if ((Get-FileHash -LiteralPath $localDriver).Hash -ne $driverHash) {
        throw 'Local driver verification failed'
    }
    $rc = Invoke-Sc @('create', $service, 'type=', 'kernel', 'start=', 'demand', 'binPath=', $localDriver)
    if ($rc -ne 0) { throw "SC_CREATE_FAILED win32=$rc" }
    $created = $true
    # Let Windows make the authoritative load decision with its existing policy.
    $rc = Invoke-Sc @('start', $service)
    if ($rc -ne 0) { throw "SC_START_FAILED win32=$rc" }
    $started = $true
    $probeArgs = if ($Codec) { @('--codec') } else { @() }
    & $client @probeArgs
    if ($LASTEXITCODE -ne 0) { throw "MAVK_ADMISSION_FAILED exit=$LASTEXITCODE" }
} finally {
    if ($started) {
        $rc = Invoke-Sc @('stop', $service)
        if ($rc -ne 0) { Write-Warning "SC_STOP_FAILED win32=$rc" }
    }
    if ($created) {
        $rc = Invoke-Sc @('delete', $service)
        if ($rc -ne 0) { Write-Warning "SC_DELETE_FAILED win32=$rc" }
    }
    if ($localCopy) {
        try { Remove-Item -LiteralPath $localDriver -ErrorAction Stop }
        catch { Write-Warning "DRIVER_FILE_ROLLBACK_FAILED $($_.FullyQualifiedErrorId)" }
    }
    foreach ($path in $imported) {
        if (Test-Path -LiteralPath $path) {
            try { Remove-Item -LiteralPath $path -ErrorAction Stop }
            catch { Write-Warning "CERT_ROLLBACK_FAILED $path $($_.FullyQualifiedErrorId)" }
        }
    }
    try {
        Get-WinEvent -FilterHashtable @{
            LogName = 'Microsoft-Windows-CodeIntegrity/Operational'
            StartTime = $probeStart
        } -ErrorAction Stop | Where-Object { $_.Message -like "*$service.sys*" } |
            Select-Object Id, TimeCreated, Message | Format-List | Out-Host
    } catch {
        Write-Verbose "Code Integrity event query: $($_.FullyQualifiedErrorId)"
    }
}
