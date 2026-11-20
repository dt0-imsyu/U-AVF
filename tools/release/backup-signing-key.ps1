[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
# Interactive private operation: never include the result in the release kit.
$repoRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$backupRoot=(Resolve-Path -LiteralPath $OutputDirectory).Path
if($backupRoot.Equals($repoRoot,[StringComparison]::OrdinalIgnoreCase) -or
   $backupRoot.StartsWith($repoRoot+'\',[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose a private directory outside the repository'
}
$target=Join-Path $backupRoot 'uavf-release-portable.p12'
if(Test-Path -LiteralPath $target){throw 'Backup already exists; do not overwrite it'}
$keyDir=Join-Path $env:USERPROFILE '.android\uavf-release'
$key=Join-Path $keyDir 'uavf-release.jks'
$credential=Join-Path $keyDir 'store-password.dpapi'
$keytool='C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
if(-not (Test-Path -LiteralPath $key) -or -not (Test-Path -LiteralPath $credential)) {
    throw 'Existing release key/Windows credential required; no new signing identity will be generated'
}
$first=Read-Host 'New portable-backup passphrase (at least 16 characters; save in your password manager)' -AsSecureString
$second=Read-Host 'Confirm portable-backup passphrase' -AsSecureString
$plainFirst=[Net.NetworkCredential]::new('', $first).Password
$plainSecond=[Net.NetworkCredential]::new('', $second).Password
if($plainFirst.Length -lt 16 -or $plainFirst -cne $plainSecond){throw 'Passphrases differ or are too short'}
$sourceSecure=Get-Content -LiteralPath $credential | ConvertTo-SecureString
try {
    $env:UAVF_BACKUP_SOURCE_PASSWORD=[Net.NetworkCredential]::new('', $sourceSecure).Password
    $env:UAVF_BACKUP_DEST_PASSWORD=$plainFirst
    & $keytool -importkeystore -noprompt -srckeystore $key -srcstoretype JKS -srcalias uavf-release -srcstorepass:env UAVF_BACKUP_SOURCE_PASSWORD -srckeypass:env UAVF_BACKUP_SOURCE_PASSWORD -destkeystore $target -deststoretype PKCS12 -destalias uavf-release -deststorepass:env UAVF_BACKUP_DEST_PASSWORD -destkeypass:env UAVF_BACKUP_DEST_PASSWORD
    if($LASTEXITCODE){throw 'Private backup import failed; original key unchanged'}
    $listing=& $keytool '-J-Duser.language=en' -list -v -alias uavf-release -keystore $target -storetype PKCS12 -storepass:env UAVF_BACKUP_DEST_PASSWORD
    if($LASTEXITCODE){throw 'Portable backup could not be opened'}
    $expected='E98E3ADFB577D0134B25234C6C72497D40F8221174CFC0EBA49F5CA95E1E8240'
    $found=[regex]::Match(($listing -join "`n"),'SHA256:\s*([0-9A-Fa-f:]+)')
    if(-not $found.Success){throw 'Certificate fingerprint not found'}
    $fingerprint=$found.Groups[1].Value.Replace(':','').ToUpperInvariant()
    if($fingerprint -ne $expected){throw 'Backup certificate mismatch; do not use this file'}
    Write-Output 'ENCRYPTED_PORTABLE_BACKUP=PASS; ORIGINAL_KEY_UNCHANGED'
    Write-Output 'Store the p12 privately on independent storage and the passphrase separately. Never upload either.'
    Write-Output 'Recovery on another device is still required before marking the recovery gate PASS.'
} finally {
    Remove-Item Env:\UAVF_BACKUP_SOURCE_PASSWORD,Env:\UAVF_BACKUP_DEST_PASSWORD -ErrorAction SilentlyContinue
    $plainFirst=$null;$plainSecond=$null;$first=$null;$second=$null;$sourceSecure=$null
}
