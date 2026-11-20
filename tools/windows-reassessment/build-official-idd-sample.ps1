param([Parameter(Mandatory=$true)][string]$OutputDirectory,
      [string]$RuntimeLibraries,[switch]$LinkSample,[switch]$SystemBufferProof,[switch]$FramePipe)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
. "$PSScriptRoot/windows-build-output.ps1"
$output=Assert-WindowsBuildOutput $OutputDirectory $root
$llvm=Join-Path $root 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$sdk='C:\Program Files (x86)\Windows Kits\10\Include\10.0.26100.0'
$msvc='C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Tools\MSVC\14.51.36231'
$wdk=Join-Path $root 'tools/wdk-nuget/Microsoft.Windows.WDK.ARM64.10.0.26100.6584/c'
$commit='2dc3fd3a0cc84a2933f2194e7ec0871584979071'
$remote="https://raw.githubusercontent.com/microsoft/Windows-driver-samples/$commit"
New-Item -ItemType Directory -Path $output | Out-Null
# Official upstream assets only, retain their MS-PL notices. No source rewrite.
foreach($file in @('Driver.cpp','Driver.h','Trace.h','IddSampleDriver.inf')) {
    Invoke-WebRequest "$remote/video/IndirectDisplay/IddSampleDriver/$file" -OutFile (Join-Path $output $file)
}
Invoke-WebRequest "$remote/LICENSE" -OutFile (Join-Path $output 'LICENSE.MS-PL')
$source=[IO.File]::ReadAllText((Join-Path $output 'Driver.cpp'))
if($source -match '\b(WPP_INIT_TRACING|WPP_CLEANUP|TraceEvents|Trace)\s*\('){throw 'Actual WPP generation required; no empty shim allowed'}
$flags=@('--target=aarch64-pc-windows-msvc','-std=c++17','-O2','-fms-extensions','-fms-compatibility','-fno-exceptions','-fno-rtti','-DUMDF_DRIVER','-D_WIN32_WINNT=0x0A00','-DNTDDI_VERSION=0x0A00000C','-DIDDCX_VERSION_MAJOR=1','-DIDDCX_VERSION_MINOR=6','-DIDDCX_MINIMUM_VERSION_REQUIRED=6')
if($LinkSample){$flags+=@('-D_DLL','-D_MT','-DUMDF_VERSION_MAJOR=2','-DUMDF_VERSION_MINOR=25','-DUMDF_MINIMUM_VERSION_REQUIRED=25','-Xclang','-cfguard')}
if($SystemBufferProof -and !$LinkSample){throw 'Proof requires complete link gate'}
if($FramePipe -and !$SystemBufferProof){throw 'Frame pipe requires isolated system-buffer proof'}
if($FramePipe){$flags+=@('-DUAVF_IDD_FRAME_PIPE=1','-Oz','-ffunction-sections','-fdata-sections','-flto=thin')}
foreach($include in @("$msvc/include","$sdk/ucrt","$sdk/shared","$sdk/um","$sdk/winrt","$wdk/Include/10.0.26100.0/shared","$wdk/Include/10.0.26100.0/um","$wdk/Include/wdf/umdf/2.25","$wdk/Include/10.0.26100.0/um/iddcx/1.6","$PSScriptRoot/idd-build-shims")) {
    if(Test-Path -LiteralPath $include){$flags+=@('-isystem',$include)}
}
$compileSource="$output/Driver.cpp"
if($SystemBufferProof) {
    & 'C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe' "$PSScriptRoot/prepare-idd-system-proof.py" $output
    if($LASTEXITCODE){throw 'Pinned proof generation failed'}
    $compileSource="$output/DriverProof.cpp"
    $flags+=@('-I',$PSScriptRoot)
}
& "$llvm/clang.exe" @flags -c $compileSource -o "$output/IddSampleDriver.obj"
if($LASTEXITCODE){throw 'Official ARM64 sample compile failed'}
$extraObjects=@()
if($SystemBufferProof) {
    & "$llvm/clang.exe" @flags -c "$PSScriptRoot/idd-proof-copy.cpp" -o "$output/idd-proof-copy.obj"
    if($LASTEXITCODE){throw 'Actual full-frame copy helper failed'}
    if(!$FramePipe){$extraObjects+=@("$output/idd-proof-copy.obj")}
}
if($FramePipe){$extraObjects+=@('/opt:ref','/opt:icf')}
if(!$FramePipe){
    & "$llvm/llvm-readobj.exe" --file-headers "$output/IddSampleDriver.obj"
    if($LASTEXITCODE){throw 'ARM64 object inspection failed'}
}
'OFFICIAL_ARM64_IDD_SAMPLE_COMPILE_ONLY_PASS; NOT_INSTALLED=1; NOT_FPS_PASS=1'
Get-FileHash -Algorithm SHA256 "$output/Driver.cpp","$output/Driver.h","$output/IddSampleDriver.obj"
if(!$RuntimeLibraries){$RuntimeLibraries="$msvc/lib/arm64"}
if(!(Test-Path "$RuntimeLibraries/vcruntime.lib") -or !(Test-Path "$RuntimeLibraries/msvcprt.lib")) {
    'FULL_DLL_LINK_GATE=MISSING_ARM64_MSVC_RUNTIME_LIBRARIES; NO_INSTALLABLE_PACKAGE=1'
    return
}
if(!$LinkSample){'RUNTIME_LIBRARIES_PRESENT; LINK_NOT_REQUESTED=1';return}
# Standard CRT DLL startup. The official WDF stub supplies its own
# /EXPORT:FxDriverEntryUm directive; no fake aliases or unresolved symbols.
$sdkLib='C:\Program Files (x86)\Windows Kits\10\Lib\10.0.26100.0'
& "$llvm/ld.lld.exe" -flavor link /dll /machine:arm64 /entry:_DllMainCRTStartup /dynamicbase /nxcompat /guard:cf "/out:$output/IddSampleDriver.dll" "/implib:$output/IddSampleDriver.lib" "/libpath:$RuntimeLibraries" "/libpath:$sdkLib/um/arm64" "/libpath:$sdkLib/ucrt/arm64" "$output/IddSampleDriver.obj" @extraObjects "$wdk/Lib/wdf/umdf/ARM64/2.25/WdfDriverStubUm.lib" "$wdk/Lib/10.0.26100.0/um/ARM64/iddcx/1.6/iddcxstub.lib" msvcrt.lib msvcprt.lib vcruntime.lib ucrt.lib kernel32.lib ntdll.lib ole32.lib d3d11.lib dxgi.lib avrt.lib uuid.lib advapi32.lib
if($LASTEXITCODE){throw 'Strict ARM64 sample link failed; no driver staging allowed'}
& "$llvm/llvm-readobj.exe" --file-headers --coff-exports --coff-imports "$output/IddSampleDriver.dll"
if($LASTEXITCODE){throw 'DLL import/export inspection failed'}
& 'C:\Users\denis\AppData\Local\Programs\Python\Python314\python.exe' "$PSScriptRoot/verify-idd-dll.py" "$output/IddSampleDriver.dll"
if($LASTEXITCODE){throw 'Structural DLL gate failed; never stage'}
'OFFICIAL_ARM64_SAMPLE_DLL_LINK_PASS; UNSIGNED_NOT_INSTALLABLE=1; NOT_GUEST_OR_FPS_PASS=1'
