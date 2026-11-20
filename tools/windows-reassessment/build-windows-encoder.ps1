param([Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$ExperimentalEightCodec,[switch]$ExperimentalX264,[switch]$DxgiLateRelease,[switch]$NativeCaptureWorkload,[switch]$OverlappedCapture,[switch]$GdiOnlyCapture,[switch]$CaptureCpuAudit,[switch]$StagingReadback,[switch]$AsyncStaging,[switch]$HighResPoll,[switch]$FramePacingFix,[switch]$SharedPrimaryAudit,[switch]$WgcSourceProbe,[switch]$IddPipeCapture,[switch]$IddDesktopLifecycle,[ValidatePattern('^[0-9a-fA-F]{64}$')][string]$IddDriverSha)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
if((Get-PSDrive C).Free -lt 50GB){throw 'C reserve below50GiB; no build started'}
$llvm=Join-Path $root 'tools\toolchains\llvm-mingw-20260826-clean-extract\llvm-mingw-20260826-ucrt-x86_64\bin'
$headers=Join-Path $root 'build-logs\windows-post-linux-reassessment-20261004'
New-Item -ItemType Directory -Path $output | Out-Null
if($ExperimentalEightCodec -and $ExperimentalX264){throw 'Select one experimental encoder'}
$extra=@();$sourceObjects=@();if($ExperimentalEightCodec){$extra=@('-DUAVF_EXPERIMENTAL_EIGHT_CODEC=1','-lbcrypt')}
if($WgcSourceProbe -and (!$ExperimentalX264 -or $ExperimentalEightCodec -or $DxgiLateRelease -or $NativeCaptureWorkload -or $OverlappedCapture -or $GdiOnlyCapture -or $CaptureCpuAudit -or $StagingReadback -or $AsyncStaging -or $HighResPoll -or $FramePacingFix -or $SharedPrimaryAudit)){throw 'WGC source gate uses unchanged x264 control, no other source experiments'}
if($ExperimentalX264) {
    $x264Source=Join-Path $root 'build-logs\windows-x264-20261012\source'
    if(!(Test-Path -LiteralPath (Join-Path $x264Source 'x264.h'))){throw 'Audited x264 headers required'}
    $extra=@('-DUAVF_EXPERIMENTAL_X264=1','-I',$x264Source,'-lbcrypt')
}
if($DxgiLateRelease){$extra+=@('-DUAVF_DXGI_LATE_RELEASE=1')}
if($IddDriverSha -and !$IddPipeCapture){throw 'Explicit driver pin is for IDD source only'}
if($IddDesktopLifecycle){
    if(!$IddPipeCapture){throw 'Desktop lifecycle requires admitted IDD source; not another capture A/B'}
    & "$llvm/x86_64-w64-mingw32-clang.exe" -std=c11 -O2 -Wall -Wextra -Werror "$PSScriptRoot/windows-idd-lifecycle-selftest.c" -o "$output/idd-lifecycle-selftest.exe"
    if($LASTEXITCODE){throw 'IDD lifecycle policy test compile failed'}
    & "$output/idd-lifecycle-selftest.exe"
    if($LASTEXITCODE){throw 'IDD lifecycle policy test failed'}
    $extra+=@('-DUAVF_IDD_DESKTOP_LIFECYCLE=1')
}
if($IddPipeCapture) {
    if(!$ExperimentalX264 -or $ExperimentalEightCodec -or $DxgiLateRelease -or $NativeCaptureWorkload -or $OverlappedCapture -or $GdiOnlyCapture -or $CaptureCpuAudit -or $StagingReadback -or $AsyncStaging -or $HighResPoll -or $FramePacingFix -or $SharedPrimaryAudit -or $WgcSourceProbe){throw 'IDD source uses unchanged x264 path only, no other capture experiments'}
    $sourcePin=@()
    if($IddDriverSha){
        $pinBytes=(0..31 | ForEach-Object {'0x'+$IddDriverSha.Substring($_*2,2)}) -join ','
        $sourcePin=@("-DUAVF_EXPECTED_IDD_DRIVER_SHA_BYTES=$pinBytes")
    }
    & "$llvm/aarch64-w64-mingw32-clang++.exe" -O2 -Wall -Wextra -Werror -fno-exceptions -fno-rtti @sourcePin -c "$PSScriptRoot/windows-idd-source.cpp" -o "$output/windows-idd-source.o"
    if($LASTEXITCODE){throw 'IDD C ABI adapter compile failed'}
    $extra+=@('-DUAVF_IDD_PIPE_CAPTURE=1');$sourceObjects+=@("$output/windows-idd-source.o")
    if($IddDriverSha){"EXACT_EXPECTED_DRIVER_SHA256=$($IddDriverSha.ToUpperInvariant()); NOT_SIGNATURE_OR_GUEST_ADMISSION_PASS"}
}
if($WgcSourceProbe){$extra+=@('-DUAVF_WGC_SOURCE_PROBE=1','-isystem','C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0\winrt','-ffunction-sections','-fdata-sections','-Wl,--gc-sections','-lruntimeobject','-luuid')}
if($NativeCaptureWorkload){$extra+=@('-DUAVF_NATIVE_CAPTURE_WORKLOAD=1')}
if($OverlappedCapture){$extra+=@('-DUAVF_OVERLAPPED_CAPTURE=1')}
if($GdiOnlyCapture){$extra+=@('-DUAVF_GDI_ONLY_CAPTURE=1')}
if($CaptureCpuAudit){
    if(($OverlappedCapture -and !$FramePacingFix) -or $GdiOnlyCapture -or $DxgiLateRelease){throw 'Overlap audit requires frame pacing fix and isolated snapshot ownership'}
    $extra+=@('-DUAVF_CAPTURE_CPU_AUDIT=1','-ldwmapi')
}
if($StagingReadback){
    if(!$CaptureCpuAudit -or !$NativeCaptureWorkload){throw 'Staging A/B requires original audited native workload'}
    $extra+=@('-DUAVF_DXGI_STAGING_READBACK=1')
}
if($AsyncStaging){
    if(!$StagingReadback){throw 'Async comparison requires staging audit'}
    $extra+=@('-DUAVF_DXGI_ASYNC_STAGING=1')
}
if($HighResPoll){
    if(!$AsyncStaging){throw 'Private timer A/B requires measured async poll control'}
    $extra+=@('-DUAVF_DXGI_HIGH_RES_POLL=1')
}
if($FramePacingFix){
    if(!$HighResPoll){throw 'Frame cap A/B requires audited private-poll source'}
    $extra+=@('-DUAVF_FRAME_PACING_FIX=1')
}
if($SharedPrimaryAudit){
    if(!$CaptureCpuAudit -or !$NativeCaptureWorkload -or $OverlappedCapture -or $StagingReadback){throw 'Query-only shared primary requires exact original cadence control'}
    $extra+=@('-DUAVF_SHARED_PRIMARY_AUDIT=1')
    & "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode -I $headers "$PSScriptRoot/windows-shared-primary-selftest.c" -o "$output/shared-primary-selftest.exe" -lgdi32 -ld3d11 -ldxgi -ldxguid
    if($LASTEXITCODE){throw 'Shared primary query test compile failed'}
    & "$output/shared-primary-selftest.exe"
    if($LASTEXITCODE){throw 'Actual shared-primary ABI/error/cleanup test failed'}
}
if($OverlappedCapture){
    foreach($test in @('windows-frame-queue-selftest','windows-capture-worker-selftest')){
        $entryFlags=@();if($test -eq 'windows-capture-worker-selftest'){$entryFlags=@('-municode')}
        & "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror @entryFlags -I $headers "$PSScriptRoot/$test.c" -o "$output/$test.exe" -lgdi32 -ld3d11 -ldxgi -ldxguid
        if($LASTEXITCODE){throw "Worker ownership test compile failed: $test"}
        & "$output/$test.exe"
        if($LASTEXITCODE){throw "Actual worker ownership test failed: $test"}
    }
}
& "$llvm/aarch64-w64-mingw32-clang.exe" -O3 -Wall -Wextra -Werror -municode '-Wl,--no-insert-timestamp' -I $headers "$PSScriptRoot/winpe-encode.c" @sourceObjects -o "$output/winpe-encode.exe" -lgdi32 -ld3d11 -ldxgi -ldxguid @extra
if($LASTEXITCODE){throw 'ARM64 encoder compile failed'}
& "$llvm/llvm-strip.exe" --strip-all "$output/winpe-encode.exe"
if($LASTEXITCODE -or (Get-Item -LiteralPath "$output/winpe-encode.exe").Length -gt 79872){throw 'Encoder must fit existing owned slot'}
Get-FileHash -Algorithm SHA256 -LiteralPath "$output/winpe-encode.exe" | Select-Object -ExpandProperty Hash
& "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode -I $headers "$PSScriptRoot/windows-encoder-diagnostic-selftest.c" -o "$output/diagnostic-selftest.exe" -lgdi32 -ld3d11 -ldxgi -ldxguid
if($LASTEXITCODE){throw 'Diagnostic selftest compile failed'}
& "$output/diagnostic-selftest.exe"
if($LASTEXITCODE){throw 'Actual diagnostic packet selftest failed'}
if($FramePacingFix){
    & "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror "$PSScriptRoot/windows-frame-pacer-selftest.c" -o "$output/frame-pacer-selftest.exe"
    if($LASTEXITCODE){throw 'Frame pacer test compile failed'}
    & "$output/frame-pacer-selftest.exe"
    if($LASTEXITCODE){throw 'Actual frame pacer test failed'}
}
if($StagingReadback){
    $stagingTestFlags=@()
    if($AsyncStaging){$stagingTestFlags=@('-DUAVF_CAPTURE_CPU_AUDIT=1','-DUAVF_DXGI_ASYNC_STAGING=1','-ldwmapi')}
    if($HighResPoll){$stagingTestFlags+=@('-DUAVF_DXGI_HIGH_RES_POLL=1')}
    & "$llvm/x86_64-w64-mingw32-clang.exe" -O2 -Wall -Wextra -Werror -municode -I $headers "$PSScriptRoot/windows-dxgi-staging-selftest.c" -o "$output/staging-selftest.exe" -lgdi32 -ld3d11 -ldxgi -ldxguid @stagingTestFlags
    if($LASTEXITCODE){throw 'Staging ownership selftest compile failed'}
    & "$output/staging-selftest.exe"
    if($LASTEXITCODE){throw 'Staging ownership/error/reuse selftest failed'}
}
