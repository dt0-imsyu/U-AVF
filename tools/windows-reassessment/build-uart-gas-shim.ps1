param([ValidateSet('control','gas8','gas8-runtime-quiet-ro','gas8-gic-audit','gas8-gic-ds-probe','gas8-gic-ds-fix','gas8-gic-ds-ebs')][string[]]$Modes=@('control','gas8','gas8-runtime-quiet-ro'),[string]$OutputDirectory='', [datetime]$RtcUtc=[datetime]::MinValue,[switch]$WindowsCam,[switch]$DynamicGic,[switch]$PsciEntryReserve,[switch]$PsciRuntimeReserve,[switch]$PsciRuntimeAllCpus,[switch]$PsciRuntimeEntryMarker,[switch]$PsciRuntimeCachePublish,[switch]$WindowsRamGop,[switch]$WddmPnp)
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$llvm=Join-Path $root 'tools/toolchains/llvm-mingw-20260826-clean-extract/llvm-mingw-20260826-ucrt-x86_64/bin'
$include=Join-Path $root 'firmware-work/edk2/MdePkg/Include'
$output=Join-Path $root 'build-logs/windows-post-linux-reassessment-20261004'
if($RtcUtc -ne [datetime]::MinValue){
    if(!$WindowsCam -or !$OutputDirectory -or $Modes.Count -ne 1 -or $Modes[0] -ne 'gas8-gic-ds-ebs'){throw 'RTC A/B requires exact CAM platform, distinct output and proven isolated shim mode'}
}
if($WindowsCam -and (!$OutputDirectory -or $Modes.Count -ne 1 -or $Modes[0] -ne 'gas8-gic-ds-ebs')){throw 'CAM build must preserve frozen outputs and exact mode'}
if($DynamicGic -and (!$WindowsCam -or !$OutputDirectory -or $RtcUtc -ne [datetime]::MinValue)){throw 'Dynamic GIC requires isolated CAM build and no clock experiment'}
if($PsciEntryReserve -and (!$WindowsCam -or !$OutputDirectory -or $DynamicGic -or $RtcUtc -ne [datetime]::MinValue)){throw 'PSCI own reserve requires isolated fixed eight CPU build only'}
if($PsciRuntimeReserve -and !$PsciEntryReserve){throw 'Runtime mapping requires isolated own reserve'}
if($PsciRuntimeAllCpus -and !$PsciRuntimeReserve){throw 'All CPU pages require exact owned runtime mapping'}
if($PsciRuntimeEntryMarker -and !$PsciRuntimeAllCpus){throw 'Entry marker requires separate owned pages for all CPUs'}
if($PsciRuntimeCachePublish -and !$PsciRuntimeEntryMarker){throw 'Cache publication requires exact own AP marker profile'}
if($WddmPnp -and (!$WindowsRamGop -or !$PsciRuntimeCachePublish)){throw 'WDDM PnP uses the isolated eight-CPU RAM GOP control'}
if($WindowsRamGop -and (!$WindowsCam -or !$OutputDirectory -or $DynamicGic -or
    $RtcUtc -ne [datetime]::MinValue -or ($PsciEntryReserve -and !$PsciRuntimeCachePublish))){
    throw 'RAM GOP requires the isolated single-CPU control or the existing eight-CPU cache profile'
}
if($OutputDirectory){
    $output=[IO.Path]::GetFullPath($OutputDirectory)
    $allowed=[IO.Path]::GetFullPath((Join-Path $root 'build-logs'))+[IO.Path]::DirectorySeparatorChar
    if(!$output.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)){throw 'New scoped build-logs directory required; preserve prior artifacts'}
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
foreach($mode in $Modes) {
    $enabled=if($mode -eq 'control'){0}else{1}
    $noRaw=if($mode -eq 'control' -or $mode -eq 'gas8'){0}else{1}
    $gicAudit=if($mode -like 'gas8-gic-*'){1}else{0}
    $dsProbe=if($mode -in @('gas8-gic-ds-probe','gas8-gic-ds-fix','gas8-gic-ds-ebs')){1}else{0}
    $dsBoot=if($mode -in @('gas8-gic-ds-fix','gas8-gic-ds-ebs')){1}else{0}
    $dsEbs=if($mode -eq 'gas8-gic-ds-ebs'){1}else{0}
    $opt=if($noRaw){'-Oz'}else{'-O2'}
    $obj=Join-Path $output "uart-$mode.obj"
    $efi=Join-Path $output "uart-$mode.efi"
    $rtcArgs=@()
    $camArgs=@();$camObjects=@()
    if($WindowsCam){
        $camObject=Join-Path $root 'build-logs/windows-post-linux-reassessment-20261004/windows-cam-ssdt.obj'
        if((Get-FileHash -Algorithm SHA256 -LiteralPath $camObject).Hash -ne '3BB6E30F07D510C374A9DAEADB113D5F6B5508669A0F3983F02D94895BAD9C68'){throw 'Frozen CAM SSDT object changed'}
        $camArgs=@('-DWINDOWS_CAM=1');$camObjects=@($camObject)
    }
    if($RtcUtc -ne [datetime]::MinValue){
        $utc=$RtcUtc.ToUniversalTime()
        if($utc.Year -lt 2020 -or $utc.Year -gt 2100){throw 'Invalid trusted host RTC date'}
        $rtcArgs+=('-DWINDOWS_RTC_UTC={'+($utc.Year,$utc.Month,$utc.Day,$utc.Hour,$utc.Minute,$utc.Second,0,0,2047,0,0 -join ',')+'}')
    }
    $flags=@('--target=aarch64-pc-windows-msvc',"-DGAS8=$enabled","-DNO_RAW_DIAG=$noRaw","-DGIC_AUDIT=$gicAudit","-DGIC_DS_PROBE=$dsProbe","-DGIC_DS_BOOT=$dsBoot","-DGIC_DS_EBS=$dsEbs")+$camArgs+@('-ffreestanding','-fshort-wchar','-fno-stack-protector',$opt,'-I',$include,'-I',"$include/AArch64")
    if($RtcUtc -ne [datetime]::MinValue -or $DynamicGic -or $PsciEntryReserve -or $WindowsRamGop){
        $controlObj=Join-Path $output 'rtc-control.obj';$controlEfi=Join-Path $output 'rtc-control.efi'
        & "$llvm/clang.exe" @flags -c "$PSScriptRoot/uart-gas-shim.c" -o $controlObj
        if($LASTEXITCODE){throw 'RTC control compile'}
        & "$llvm/ld.lld.exe" -flavor link /subsystem:efi_application /entry:efi_main /nodefaultlib /machine:arm64 /timestamp:0 "/out:$controlEfi" $controlObj @camObjects
        if($LASTEXITCODE -or (Get-FileHash -Algorithm SHA256 -LiteralPath $controlEfi).Hash -ne '893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B'){throw 'Exact known-good control reproduction required; do not deploy RTC candidate'}
        'RTC_CONTROL_BYTE_PARITY=PASS'
    }
    $gicArgs=@();if($DynamicGic){$gicArgs=@('-DGIC_DYNAMIC=1')}
    if($PsciEntryReserve){
        $fixedObj=Join-Path $output 'fixed-eight-control.obj';$fixedEfi=Join-Path $output 'fixed-eight-control.efi'
        & "$llvm/clang.exe" @flags -DGIC_FIXED_EIGHT=1 -c "$PSScriptRoot/uart-gas-shim.c" -o $fixedObj
        if($LASTEXITCODE){throw 'Fixed eight control compile'}
        & "$llvm/ld.lld.exe" -flavor link /subsystem:efi_application /entry:efi_main /nodefaultlib /machine:arm64 /timestamp:0 "/out:$fixedEfi" $fixedObj @camObjects
        if($LASTEXITCODE -or (Get-FileHash -Algorithm SHA256 -LiteralPath $fixedEfi).Hash -ne '365B733271DA6DEEB5EEA2C262041F6E1793D6DA3C21ECE077BA15FE84B1A865'){throw 'Exact fixed eight byte-parity control required'}
        'FIXED_EIGHT_CONTROL_BYTE_PARITY=PASS'
        $gicArgs=@('-DGIC_FIXED_EIGHT=1','-DPSCI_ENTRY_RESERVE=1')
        if($PsciRuntimeReserve){
            $gicArgs+='-DPSCI_RUNTIME_RESERVE=1'
            if($PsciRuntimeAllCpus){$gicArgs+='-DPSCI_RUNTIME_ALL_CPUS=1'}
            if($PsciRuntimeEntryMarker){$gicArgs+='-DPSCI_RUNTIME_ENTRY_MARKER=1'}
            if($PsciRuntimeCachePublish){
                $gicArgs+='-DPSCI_RUNTIME_CACHE_PUBLISH=1'
                $publication=Join-Path $output 'psci-cache-publish.obj'
                & "$llvm/clang.exe" --target=aarch64-pc-windows-msvc -c "$PSScriptRoot/psci-cache-publish.S" -o $publication
                if($LASTEXITCODE){throw 'Own cache publication helper compile'}
                $camObjects+=$publication
            }
            $callback=Join-Path $output 'psci-runtime-callback.obj'
            & "$llvm/clang.exe" --target=aarch64-pc-windows-msvc -c "$PSScriptRoot/psci-runtime-callback.S" -o $callback
            if($LASTEXITCODE){throw 'Runtime callback compile'}
            $camObjects+=$callback
        }
    }
    if($WindowsRamGop){$gicArgs+='-DWINDOWS_RAM_GOP=1'}
    if($WddmPnp){
        $asl=Join-Path $root 'tools/wdk-nuget/Microsoft.Windows.WDK.x64.10.0.26100.6584/c/tools/10.0.26100.0/x64/ACPIVerify/asl.exe'
        $aml=Join-Path $output 'mavk-render-pnp.aml'
        & $asl /nologo "/Fo=$aml" "$PSScriptRoot/wddm/mavk-render-pnp.asl"
        if($LASTEXITCODE){throw 'WDDM PnP SSDT compile'}
        $asm=Join-Path $output 'mavk-render-pnp.S'
        $tableObj=Join-Path $output 'mavk-render-pnp.obj'
        @('.section .rdata,"dr"', '.global wddm_ssdt_begin', '.global wddm_ssdt_end',
          'wddm_ssdt_begin:', ('.incbin "' + $aml.Replace('\','/') + '"'), 'wddm_ssdt_end:') |
            Set-Content -LiteralPath $asm -Encoding ascii
        & "$llvm/clang.exe" --target=aarch64-pc-windows-msvc -c $asm -o $tableObj
        if($LASTEXITCODE){throw 'WDDM PnP SSDT embedding'}
        $gicArgs+='-DWINDOWS_WDDM_PNP=1'
        $camObjects+=$tableObj
    }
    & "$llvm/clang.exe" @flags @rtcArgs @gicArgs -c "$PSScriptRoot/uart-gas-shim.c" -o $obj
    if($LASTEXITCODE){throw "Compile $mode failed"}
    & "$llvm/ld.lld.exe" -flavor link /subsystem:efi_application /entry:efi_main /nodefaultlib /machine:arm64 /timestamp:0 "/out:$efi" $obj @camObjects
    if($LASTEXITCODE){throw "Link $mode failed"}
}
$test=Join-Path $output 'uart-gas-selftest.exe'
& "$llvm/clang.exe" -O2 -I $include -I "$include/X64" "$PSScriptRoot/uart-gas-selftest.c" -o $test
if($LASTEXITCODE){throw 'Self-test build failed'}
& $test
if($LASTEXITCODE){throw 'Self-test failed'}
'UART_GAS_SELFTEST=PASS'
if($WindowsRamGop){
    $ramTest=Join-Path $output 'windows-ram-gop-selftest.exe'
    & "$llvm/clang.exe" -O2 -Wall -Wextra -Werror -I $include -I "$include/X64" "$PSScriptRoot/windows-ram-gop-selftest.c" -o $ramTest
    if($LASTEXITCODE){throw 'RAM GOP selftest compile'}
    & $ramTest
    if($LASTEXITCODE){throw 'RAM GOP selftest failure'}
}
if($PsciEntryReserve){
    $reserveTest=Join-Path $output 'psci-entry-reserve-selftest.exe'
    & "$llvm/clang.exe" -O2 -I $include -I "$include/X64" "$PSScriptRoot/psci-entry-reserve-selftest.c" -o $reserveTest
    if($LASTEXITCODE){throw 'Own reserved page test compile'}
    & $reserveTest
    if($LASTEXITCODE){throw 'Own reserved page test failed'}
    'OWN_PSCI_RESERVE_MAP_TEMPLATE_SELFTEST=PASS'
    if($PsciRuntimeReserve){
        $runtimeTest=Join-Path $output 'psci-runtime-map-selftest.exe'
        $runtimeArgs=@('-DPSCI_RUNTIME_RESERVE=1');if($PsciRuntimeAllCpus){$runtimeArgs+='-DPSCI_RUNTIME_ALL_CPUS=1'}
        if($PsciRuntimeEntryMarker){$runtimeArgs+='-DPSCI_RUNTIME_ENTRY_MARKER=1'}
        & "$llvm/clang.exe" -O2 @runtimeArgs -I $include -I "$include/X64" "$PSScriptRoot/psci-entry-reserve-selftest.c" -o $runtimeTest
        if($LASTEXITCODE){throw 'Runtime map test compile'}
        & $runtimeTest
        if($LASTEXITCODE){throw 'Runtime map test failed'}
        'OWN_PSCI_RUNTIME_MAP_TEMPLATE_SELFTEST=PASS'
    }
}
if($DynamicGic){
    $gicTest=Join-Path $output 'windows-gic-layout-selftest.exe'
    & "$llvm/clang.exe" -O2 -Wall -Wextra -I $include -I "$include/X64" "$PSScriptRoot/windows-gic-layout-selftest.c" -o $gicTest
    if($LASTEXITCODE){throw 'GIC selector self-test compile failed'}
    & $gicTest
    if($LASTEXITCODE){throw 'GIC selector self-test failed'}
    'WINDOWS_GIC_LAYOUT_SELFTEST=PASS'
}
