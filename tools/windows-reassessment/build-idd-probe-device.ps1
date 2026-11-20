param([Parameter(Mandatory=$true)][string]$OutputDirectory,[switch]$EtwReader,[switch]$HostX64Reader,[switch]$FramePipeReader,[switch]$ReconnectProbe,[ValidateSet(60,180)][int]$HoldSeconds=60)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$llvm=Join-Path $root 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$sdk='C:\Program Files (x86)\Windows Kits\10'
$msvc='C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Tools\MSVC\14.51.36231'
$crt='E:\U-AVF\artifacts\msvc-arm64-crt-20261013\lib'
if($HostX64Reader -and !$EtwReader){throw 'Host variant is offline trace reader only, never device probe'}
$arch=if($HostX64Reader){'x64'}else{'arm64'}
$target=if($HostX64Reader){'x86_64-pc-windows-msvc'}else{'aarch64-pc-windows-msvc'}
if($HostX64Reader){$crt="$msvc/lib/x64"}
New-Item -ItemType Directory -Path $output | Out-Null
$flags=@("--target=$target",'-std=c++17','-O2','-fms-extensions','-fms-compatibility','-fno-exceptions','-fno-rtti','-D_DLL','-D_MT','-D_WIN32_WINNT=0x0A00','-Xclang','-cfguard')
if($HoldSeconds -ne 60){if($EtwReader){throw 'Hold applies only to finite device lifetime'};$flags+=@("-DUAVF_IDD_HOLD_MS=$($HoldSeconds*1000)")}
if($FramePipeReader -and (!$EtwReader -or $HostX64Reader)){throw 'Guest pipe reader is ARM64 finite ETW helper only'}
if($FramePipeReader){$flags+=@('-DUAVF_IDD_PIPE_READER=1')}
if($ReconnectProbe){
    if(!$FramePipeReader){throw 'Reconnect probe requires the same finite SYSTEM pipe reader'}
    $pin='42f1d3f3dbc86afe079e3aa6ebc0672dcf26b9a976dda6574fc612d5138ee29c'
    $pinBytes=(0..31 | ForEach-Object {'0x'+$pin.Substring($_*2,2)}) -join ','
    $flags+=@('-DUAVF_IDD_RECONNECT_PROBE=1',"-DUAVF_EXPECTED_IDD_DRIVER_SHA_BYTES=$pinBytes")
}
foreach($include in @("$msvc/include","$sdk/Include/10.0.26100.0/ucrt","$sdk/Include/10.0.26100.0/shared","$sdk/Include/10.0.26100.0/um")){$flags+=@('-isystem',$include)}
$sourceName=if($EtwReader){'idd-etw-read'}else{'idd-probe-device'}
& "$llvm/clang.exe" @flags -c "$PSScriptRoot/$sourceName.cpp" -o "$output/$sourceName.obj"
if($LASTEXITCODE){throw 'Probe helper compile failed'}
& "$llvm/ld.lld.exe" -flavor link "/machine:$arch" /subsystem:console /entry:wmainCRTStartup /dynamicbase /nxcompat /guard:cf "/out:$output/$sourceName.exe" "/libpath:$crt" "/libpath:$sdk/Lib/10.0.26100.0/um/$arch" "/libpath:$sdk/Lib/10.0.26100.0/ucrt/$arch" "$output/$sourceName.obj" msvcrt.lib msvcprt.lib vcruntime.lib ucrt.lib kernel32.lib cfgmgr32.lib swdevice.lib advapi32.lib bcrypt.lib
if($LASTEXITCODE){throw 'Probe helper strict link failed'}
& "$llvm/llvm-readobj.exe" --file-headers --coff-imports "$output/$sourceName.exe"
if($LASTEXITCODE){throw 'Probe inspection failed'}
Get-FileHash "$output/$sourceName.exe"
'ARM64_PROBE_HELPER_BUILD_ONLY_PASS; NOT_RUN_IN_GUEST=1; NOT_DRIVER_LOAD_OR_FPS_PASS=1'
