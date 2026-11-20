param([Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$AdmissionAuditOnly,[switch]$DriverAdmissionOnly,[switch]$DesktopSource,[switch]$ReconnectSource,[switch]$ReconnectProbe,[ValidateSet(22,24,25,26,27,28,29)][int]$DesktopAttempt=22)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
New-Item -ItemType Directory $output|Out-Null
$llvm=Join-Path $root 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$auditFlags=@();if($AdmissionAuditOnly){$auditFlags=@('-DUAVF_IDD_ADMISSION_AUDIT_ONLY=1')}
if($DriverAdmissionOnly){$auditFlags=@('-DUAVF_IDD_ADMISSION_AUDIT_ONLY=1','-DUAVF_IDD_ADMISSION_LOAD_ONLY=1')}
if($DesktopSource){
    if(!$DriverAdmissionOnly){throw 'Desktop source requires existing finite SYSTEM admission job'}
    $auditFlags+=@('-DUAVF_IDD_DESKTOP_SOURCE=1')
}
if($ReconnectSource){
    if(!$DesktopSource){throw 'Reconnect launcher requires exact real Desktop admission'}
    $auditFlags+=@('-DUAVF_IDD_RECONNECT_SOURCE=1')
}
if($ReconnectProbe){
    if(!$DriverAdmissionOnly -or $DesktopSource -or $ReconnectSource){throw 'Headless IPC probe requires existing finite SYSTEM admission only'}
    $auditFlags+=@('-DUAVF_IDD_RECONNECT_PROBE=1')
}
if($DesktopAttempt -in @(24,25,26,27,28,29)){
    if(!$DesktopSource -or !$ReconnectSource -or $ReconnectProbe){throw 'Attempt24 is only the same Desktop proof after the interrupted once22 attempt'}
    $auditFlags+=@("-DUAVF_IDD_DESKTOP_ATTEMPT=$DesktopAttempt")
}
& "$llvm/aarch64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode '-Wl,--no-insert-timestamp' -DUAVF_DIRECT_ACCEPT=1 -DUAVF_IDD_WORKING_LAUNCH=1 @auditFlags "$PSScriptRoot/winpe-vsock.c" -o "$output/winpe-vsock.exe" -lws2_32 -luser32 -lshell32
if($LASTEXITCODE){throw 'One-off launcher build failed'}
& "$llvm/llvm-strip.exe" --strip-all "$output/winpe-vsock.exe"
if($LASTEXITCODE -or (Get-Item "$output/winpe-vsock.exe").Length -gt 81408){throw 'Must fit existing own socket binary slot'}
Get-FileHash "$output/winpe-vsock.exe"
