param([Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$MotionSource,[switch]$DesktopSource,[switch]$CompactDesktopDiagnostic,[ValidateSet(30,120)][int]$DesktopSeconds=30,[switch]$OrdinaryWindowWorkload)
$ErrorActionPreference='Stop'
$taskWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $taskWorkspace
New-Item -ItemType Directory -Path $output | Out-Null
$llvm=Join-Path $taskWorkspace 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$sdk='C:\Program Files (x86)\Windows Kits\10'
$msvc='C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Tools\MSVC\14.51.36231'
$flags=@('--target=aarch64-pc-windows-msvc','-std=c11','-O2','-ffreestanding','-fno-stack-protector','-D_WIN32_WINNT=0x0A00')
if($CompactDesktopDiagnostic){if(!$DesktopSource -or $MotionSource){throw 'Compact build is only the finite topology diagnostic'};$flags+=@('-Oz')}
if($DesktopSeconds -ne 30){if(!$DesktopSource){throw 'Aperture applies only to the same Desktop source'};$flags+=@("-DUAVF_IDD_DESKTOP_OBSERVE_MS=$($DesktopSeconds*1000)")}
if($OrdinaryWindowWorkload){if(!$DesktopSource -or $DesktopSeconds -ne 120){throw 'Workload requires finite same Desktop aperture'};$flags+=@('-DUAVF_IDD_ORDINARY_WINDOW_WORKLOAD=1')}
foreach($inc in @("$msvc/include","$sdk/Include/10.0.26100.0/ucrt","$sdk/Include/10.0.26100.0/shared","$sdk/Include/10.0.26100.0/um")){$flags+=@('-isystem',$inc)}
if($MotionSource -and $DesktopSource){throw 'Desktop proof must not include synthetic stimulus'}
$source=if($DesktopSource){'idd-desktop-source'}elseif($MotionSource){'idd-motion-source'}else{'idd-winpe-bootstrap'}
if($DesktopSource){
    & "$llvm/x86_64-w64-mingw32-clang++.exe" -std=c++17 -O2 -Wall -Wextra -Werror "$PSScriptRoot/idd-desktop-policy-test.cpp" -o "$output/desktop-policy-test.exe"
    if($LASTEXITCODE){throw 'Real-desktop policy test build failed'}
    & "$output/desktop-policy-test.exe"
    if($LASTEXITCODE){throw 'Real-desktop policy test failed'}
}
& "$llvm/clang.exe" @flags -c "$PSScriptRoot/$source.c" -o "$output/bootstrap.obj"
if($LASTEXITCODE){throw 'Bootstrap compile failed'}
& "$llvm/ld.lld.exe" -flavor link /machine:arm64 /subsystem:windows /entry:idd_start /nodefaultlib /dynamicbase /nxcompat "/out:$output/$source.exe" "/libpath:$sdk/Lib/10.0.26100.0/um/arm64" "$output/bootstrap.obj" kernel32.lib user32.lib gdi32.lib
if($LASTEXITCODE){throw 'Bootstrap link failed'}
& "$llvm/llvm-readobj.exe" --coff-imports "$output/$source.exe"
if($LASTEXITCODE){throw 'Bootstrap inspection failed'}
Get-FileHash "$output/$source.exe"
'BOOTSTRAP_BUILD_ONLY; NOT_GUEST_PROVISION_OR_FPS_PASS'
