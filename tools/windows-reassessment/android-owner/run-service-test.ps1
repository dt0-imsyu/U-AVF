[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$EvidenceName,[switch]$PrepareDisk,[switch]$RestorePrivateFromPrepared,[switch]$RestorePrivateFromPc,[switch]$PrepareInstallTarget,[switch]$ResumePreparedInstallTarget,[switch]$ResumeWindowsInstallTarget,[switch]$HoldRuntime,[ValidateRange(210,3600)][int]$ObserverSeconds=210,[switch]$RuntimeSnapshot,[switch]$PmuCycleReadAb,[switch]$EmulateUserPmcc,[switch]$QuietDriverNotifications,[switch]$KeepRunning,[ValidateSet('producer-accept-fix','live','live-name-fix')][string]$Candidate='producer-accept-fix',[switch]$LoaderState,[ValidateSet('interrupt-init','driver-entry','io-pre-drivers','io-complete')][string]$BootMilestone,[switch]$RtcClockShim,[switch]$DynamicGic,[switch]$MatchHostCpus,[switch]$FixedEightCpuGic,[switch]$WindowsClipboard,[switch]$WindowsClipboardProbe,[switch]$IddDisposableBootstrap)
$ErrorActionPreference='Stop'
if($env:UAVF_WDDM_PNP -eq '1' -and $env:UAVF_WDDM_ADMISSION -ne '1'){throw 'PnP trial requires WDDM diagnostic admission'}
if($env:UAVF_WDDM_ADMISSION -eq '1' -and ($env:UAVF_MAVK_BRIDGE_OPTIN -ne '1' -or $IddDisposableBootstrap -or !$ResumeWindowsInstallTarget -or !$FixedEightCpuGic -or !$MatchHostCpus -or $env:UAVF_WINDOWS_RAM_GOP -ne '1')){
    throw 'WDDM admission requires the existing eight CPU diagnostic resume'
}
if($IddDisposableBootstrap -and (!$ResumeWindowsInstallTarget -or !$FixedEightCpuGic -or !$MatchHostCpus -or $WindowsClipboard -or $env:UAVF_WINDOWS_RAM_GOP -ne '1')){throw 'Explicit fresh disposable bootstrap only'}
if($WindowsClipboardProbe -and !$WindowsClipboard){throw 'Clipboard probe must be explicitly enabled'}
if($WindowsClipboard -and (!$ResumeWindowsInstallTarget -or !$HoldRuntime -or $Candidate -ne 'live-name-fix')){throw 'Optional clipboard requires isolated installed Windows runtime'}
if($env:UAVF_WINDOWS_RAM_GOP -eq '1' -and (!$FixedEightCpuGic -or !$MatchHostCpus -or !$ResumeWindowsInstallTarget -or !$HoldRuntime -or $env:UAVF_KD_PSCI_CACHE_PUBLISH -ne '1' -or $env:UAVF_KD_PSCI_EXPLICIT_IPI_AB -ne '1')){throw 'RAM GOP requires exact proven eight CPU cached/explicit-IPI installed profile'}
if($DynamicGic -and (!$ResumeWindowsInstallTarget -or !$HoldRuntime -or !$EmulateUserPmcc -or !$QuietDriverNotifications -or $RtcClockShim -or $Candidate -ne 'live-name-fix')){throw 'Dynamic GIC requires exact isolated retained runtime'}
if($FixedEightCpuGic -and (!$MatchHostCpus -or $DynamicGic -or !$ResumeWindowsInstallTarget -or !$HoldRuntime -or !$EmulateUserPmcc -or !$QuietDriverNotifications -or $RtcClockShim -or $Candidate -ne 'live-name-fix')){throw 'Fixed eight CPU GIC requires exact isolated SMP runtime'}
if($MatchHostCpus -and ((!$DynamicGic -and !$FixedEightCpuGic) -or $LoaderState -or $BootMilestone -or $RuntimeSnapshot -or $PmuCycleReadAb -or $env:UAVF_KD_LOADER_TIME -or $env:UAVF_KD_INSPECT_AFTER -or $env:UAVF_KD_CLOCK_SEED -or $env:UAVF_KD_BOOT_CLOCK_DELTA -or $env:UAVF_KD_BOOT_CALENDAR -or $env:UAVF_NATIVE_KD_COMMANDS)){throw 'SMP cannot use oneCPU observers or clock experiments'}
if($RtcClockShim -and (!$ResumeWindowsInstallTarget -or !$HoldRuntime -or $Candidate -ne 'live-name-fix')){throw 'RTC trial requires isolated installed runtime, no preparation/restore'}
if($ResumeWindowsInstallTarget -and ($PrepareInstallTarget -or $ResumePreparedInstallTarget -or $RestorePrivateFromPc -or $PrepareDisk -or $RestorePrivateFromPrepared -or $Candidate -ne 'live-name-fix')){throw 'Mutable target uses frozen live platform, no restore/growth'}
if($HoldRuntime -and (!$EmulateUserPmcc -or !$QuietDriverNotifications -or !$ResumeWindowsInstallTarget)){throw 'Runtime hold is scoped to the proven mutable Windows install path'}
if($LoaderState -and ($BootMilestone -or $RuntimeSnapshot)){throw 'One observation scope only'}
if($PmuCycleReadAb -and ($BootMilestone -ne 'driver-entry' -or $RuntimeSnapshot)){throw 'PMU A/B requires sparse driver-entry mode'}
if($EmulateUserPmcc -and ($PmuCycleReadAb -or $BootMilestone -or $RuntimeSnapshot -or $LoaderState)){throw 'Trap adapter uses its own minimal scope'}
if($PrepareDisk -and $RestorePrivateFromPrepared){throw 'Choose one cold restore method'}
if($RestorePrivateFromPc -and ($PrepareDisk -or $RestorePrivateFromPrepared -or $Candidate -ne 'live-name-fix')){throw 'PC restore requires only frozen live-name-fix'}
if($PrepareInstallTarget -and $Candidate -ne 'live-name-fix'){throw 'Install target requires proven live media'}
if($ResumePreparedInstallTarget -and ($PrepareInstallTarget -or $RestorePrivateFromPc -or $PrepareDisk -or $RestorePrivateFromPrepared -or $Candidate -ne 'live-name-fix')){throw 'Resume is read-only approved prepared media, not a cold restore'}
if($QuietDriverNotifications -and -not $EmulateUserPmcc){throw 'Quiet mode requires isolated PMCC adapter'}
if($Candidate -ne 'producer-accept-fix' -and $PrepareDisk){throw 'Live uses separately guarded delta staging, not the old preparation chain'}
if($EvidenceName -notmatch '^[a-zA-Z0-9-]+$'){throw 'Invalid evidence name'}
$taskRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot=[IO.Path]::GetFullPath((Join-Path $taskRoot '..\..\..'))
$workspaceRoot=Split-Path -Parent $repoRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$python=Join-Path $env:LOCALAPPDATA 'Programs\Python\Python314\python.exe'
$sha='1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE'
if($Candidate -ne 'producer-accept-fix'){
    $manifest=Get-Content -Raw -LiteralPath (Join-Path $workspaceRoot "build-logs\windows-post-linux-reassessment-20261004\$Candidate-disk-report.json") | ConvertFrom-Json
    $expectedLive=if($Candidate -eq 'live'){'E45161B4037F586A1847470E04C6143EC52ACEE1EDB605CC450EB2CC363FEE6F'}else{'A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A'}
    $expectedParent=if($Candidate -eq 'live'){$sha}else{'E45161B4037F586A1847470E04C6143EC52ACEE1EDB605CC450EB2CC363FEE6F'}
    if($manifest.result -ne 'PASS' -or $manifest.base_sha256 -ne $expectedParent -or $manifest.sha256 -ne $expectedLive){throw 'Unapproved live manifest'}
    $sha=$manifest.sha256
}
$evidence=Join-Path $workspaceRoot "build-logs\windows-post-linux-reassessment-20261004\$EvidenceName"
if(Test-Path -LiteralPath $evidence){throw 'Preserve prior evidence'}
function CheckedAdb([string[]]$Arguments){$result=& $adb @Arguments;if($LASTEXITCODE){throw "ADB failed: $($Arguments -join ' ')"};return $result}
$state=CheckedAdb @('shell','/apex/com.android.virt/bin/vm','list')
if(($state -join "`n") -notmatch 'Running VMs: \[\]'){throw 'Preserve existing VM'}
$policy=CheckedAdb @('shell','dumpsys','window','policy')
if($policy -match 'showing=true|screenState=SCREEN_STATE_OFF'){throw 'Unlock tablet; no bypass'}
New-Item -ItemType Directory -Path $evidence | Out-Null
# Previous tests retained their screenshots in their own evidence directory.
# A new run must not export an old Surface image as if it belonged to this VM.
CheckedAdb @('shell','run-as','com.example.uavfwinpetest','rm','-f','/sdcard/Android/data/com.example.uavfwinpetest/files/android-surface-first.png')
if($RestorePrivateFromPc){
    $size=(CheckedAdb @('shell','run-as','com.example.uavfwinpetest','stat','-c','%s','files/windows-diagnostic.img') -join '').Trim()
    if([long]$size -gt 9126805504 -or [long]$size -lt 0){throw 'Never cold-restore grown or installed disk'}
    $source=Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004\windows-display-live-name-fix-20261008.img'
    if((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $sha){throw 'PC source identity mismatch'}
    if($size -ne '9126805504'){
        # Recover only a proven truncated prefix from an interrupted PC transfer.
        $prefixHash=[Security.Cryptography.SHA256]::Create();$prefixStream=[IO.File]::OpenRead($source)
        try{
            $remaining=[long]$size;$buffer=New-Object byte[] 1048576
            while($remaining -gt 0){$n=$prefixStream.Read($buffer,0,[int][math]::Min($remaining,$buffer.Length));if(!$n){throw 'Source prefix EOF'};[void]$prefixHash.TransformBlock($buffer,0,$n,$buffer,0);$remaining-=$n}
            [void]$prefixHash.TransformFinalBlock([byte[]]@(),0,0)
            $expectedPrefix=[BitConverter]::ToString($prefixHash.Hash).Replace('-','')
        }finally{$prefixStream.Dispose();$prefixHash.Dispose()}
        $actualPrefix=((CheckedAdb @('shell','run-as','com.example.uavfwinpetest','sha256sum','files/windows-diagnostic.img')) -split '\s+')[0].ToUpperInvariant()
        if($actualPrefix -ne $expectedPrefix){throw 'Partial private file is not the exact interrupted source prefix; preserve it'}
        'VERIFIED_INTERRUPTED_PREFIX_RECOVERY_BYTES='+$size
    }
    # SDK36 exec-in repeatedly crashes the Windows adb server. Use stable sync
    # pushes, bounded to256MiB, and same-UID local assembly; never a second9GiB copy.
    $chunk=Join-Path $evidence 'private-transfer-chunk.tmp'
    $remote='/sdcard/Android/data/com.example.uavfwinpetest/files/private-transfer-chunk.tmp'
    $free=(CheckedAdb @('shell','df','-k','/data') | Select-Object -Last 1) -split '\s+'
    $extraNeeded=9126805504-[long]$size+256MB
    if([long]$free[3]*1024 -lt 50GB+$extraNeeded){throw 'Insufficient physical reserve for bounded restore'}
    if((Get-PSDrive -Name ([IO.Path]::GetPathRoot($chunk).Substring(0,1))).Free -lt 50GB+256MB){throw 'PC reserve for transfer chunk'}
    $offset=[long]([math]::Floor([long]$size/[double]256MB)*256MB)
    if($size -eq '9126805504'){$offset=0L} # A full previous boot copy is mutable, not an interrupted prefix.
    CheckedAdb @('shell','run-as','com.example.uavfwinpetest','truncate','-s',([string]$offset),'files/windows-diagnostic.img')
    $stream=[IO.File]::OpenRead($source);[void]$stream.Seek($offset,[IO.SeekOrigin]::Begin);$buffer=New-Object byte[] 1048576
    try{
        while($offset -lt 9126805504){
            $length=[math]::Min([long]256MB,9126805504-$offset)
            $part=[IO.File]::Create($chunk)
            try{$left=$length;while($left -gt 0){$n=$stream.Read($buffer,0,[int][math]::Min($left,$buffer.Length));if(!$n){throw 'Source EOF'};$part.Write($buffer,0,$n);$left-=$n}}finally{$part.Dispose()}
            CheckedAdb @('push',$chunk,$remote)
            CheckedAdb @('shell','run-as','com.example.uavfwinpetest','dd',"if=$remote",'of=files/windows-diagnostic.img','bs=1048576',('seek='+($offset/1MB)),'conv=notrunc')
            $offset+=$length
            'PRIVATE_RESTORE_BYTES='+$offset
        }
    }finally{
        $stream.Dispose()
        if(Test-Path -LiteralPath $chunk){Remove-Item -LiteralPath $chunk}
        & $adb shell rm -f $remote
    }
}
if($PrepareDisk){
    & "$taskRoot\run-delegated-test.ps1" -Candidate producer-accept-fix -EvidenceName "$EvidenceName-preparation" -PrepareOnly
    # Only overwrite the explicitly disposable, never-installed Windows copy.
    # No new 9GB tablet copy: stream shell-owned verified bytes into existing APK private file.
    CheckedAdb @('shell','cat /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img | run-as com.example.uavfwinpetest dd of=files/windows-diagnostic.img bs=1048576')
}
if($RestorePrivateFromPrepared){
    $prepared=CheckedAdb @('shell','sha256sum','/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img')
    if(($prepared -split '\s+')[0].ToUpperInvariant() -ne $sha){throw 'Prepared shell copy is not pristine; no private restore'}
    CheckedAdb @('shell','cat /data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img | run-as com.example.uavfwinpetest dd of=files/windows-diagnostic.img bs=1048576')
}
if(!$ResumePreparedInstallTarget -and !$ResumeWindowsInstallTarget){
    $digest=CheckedAdb @('shell','run-as','com.example.uavfwinpetest','sha256sum','files/windows-diagnostic.img')
    if(($digest -split '\s+')[0].ToUpperInvariant() -ne $sha){throw 'Private shell candidate identity mismatch; no launch'}
} # Resume service verifies original source hash through its journal + exact grown GPT.
$original=(CheckedAdb @('shell','settings','get','global','hidden_api_policy') -join '').Trim()
$token=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
[IO.File]::WriteAllText((Join-Path $evidence 'owner-resume-token.private.txt'),$token)
try{
    CheckedAdb @('shell','settings','put','global','hidden_api_policy','1')
    CheckedAdb @('forward','tcp:39101','tcp:39101')
    CheckedAdb @('shell','am','force-stop','com.example.uavfwinpetest')
    $launch=@('shell','am','start','-n','com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity','--es','token',$token,'--es','candidate_sha',$sha,'--ez','service_owner','true')
    if($PrepareInstallTarget){$launch+=@('--ez','prepare_install_target','true')}
    if($ResumePreparedInstallTarget){$launch+=@('--ez','resume_prepared_install_target','true')}
    if($ResumeWindowsInstallTarget){$launch+=@('--ez','resume_windows_install_target','true')}
    if($IddDisposableBootstrap){$launch+=@('--ez','idd_disposable_bootstrap','true')}
    if($WindowsClipboard){$launch+=@('--ez','clipboard_enabled','true')}
    if($WindowsClipboardProbe){$launch+=@('--ez','clipboard_probe','true')}
    if($env:UAVF_MAVK_BRIDGE_OPTIN -eq '1'){
        if(!$ResumeWindowsInstallTarget -or !$HoldRuntime -or !$FixedEightCpuGic -or !$MatchHostCpus){throw 'Mavk channel requires unchanged isolated installed eightCPU profile'}
        if($original -ne '1'){throw 'Mavk does not change global AVF access policy; existing project access required'}
        $launch+=@('--ez','mavk_bridge_enabled','true')
        if($env:UAVF_WDDM_ADMISSION -eq '1'){$launch+=@('--ez','wddm_admission','true')}
        if($env:UAVF_WDDM_CODEC -eq '1' -and $env:UAVF_WDDM_ADMISSION -eq '1'){$launch+=@('--ez','wddm_codec','true')}
        if($env:UAVF_WDDM_PNP -eq '1'){$launch+=@('--ez','wddm_pnp','true')}
        if($env:UAVF_MAVK_VALIDATION -eq '1'){$launch+=@('--ez','mavk_validation','true')}
    }
    if($env:UAVF_IDD_SOURCE_ONLY -eq '1'){
        if(!$ResumeWindowsInstallTarget -or !$FixedEightCpuGic -or !$MatchHostCpus -or !$HoldRuntime){throw 'Source-only probe requires exact isolated installed profile'}
        $launch+=@('--ez','source_only_probe','true')
    }
    if($RtcClockShim){$launch+=@('--ez','rtc_clock_shim','true')}
    if($DynamicGic){$launch+=@('--ez','dynamic_gic','true')}
    if($FixedEightCpuGic){$launch+=@('--ez','fixed_eight_cpu_gic','true')}
    if($env:UAVF_KD_PSCI_RESERVE -eq '1'){
        if(!$FixedEightCpuGic -or $env:UAVF_KD_PSCI_LOW_MEMORY -or $env:UAVF_KD_PSCI_ZERO_X7){throw 'Own reserve proof excludes other A/Bs'}
        $launch+=@('--ez','psci_owned_reserve','true')
        if($env:UAVF_KD_PSCI_ENTRY_AB -eq '1'){$launch+=@('--ez','psci_first_cpu_entry','true')}
        if($env:UAVF_KD_PSCI_RUNTIME -eq '1'){
            if($env:UAVF_KD_PSCI_ENTRY_AB){throw 'Runtime map control excludes static entry A/B'}
            $launch+=@('--ez','psci_runtime_reserve','true')
            if($env:UAVF_KD_PSCI_ALL_CPUS -eq '1'){$launch+=@('--ez','psci_runtime_all_cpus','true')}
            if($env:UAVF_KD_PSCI_ENTRY_MARKER -eq '1'){
                if($env:UAVF_KD_PSCI_ALL_CPUS -ne '1'){throw 'Entry marker requires all CPU pages'}
                $launch+=@('--ez','psci_entry_marker','true')
                if($env:UAVF_KD_PSCI_CACHE_PUBLISH -eq '1'){$launch+=@('--ez','psci_cache_publish','true')}
            }
        }
    }
    if($env:UAVF_KD_PSCI_LOW_MEMORY -eq '1'){
        if(!$FixedEightCpuGic -or $env:UAVF_KD_PSCI_CPU_ON -ne '1'){throw 'Low-address A/B requires exact CPU_ON observation and fixed eight CPU profile'}
        $launch+=@('--ez','psci_low_memory_ab','true')
    }
    if($MatchHostCpus){$launch+=@('--ez','match_host_cpus','true')}
    if($env:UAVF_WINDOWS_RAM_GOP -eq '1'){$launch+=@('--ez','windows_ram_gop','true','--ei','display_width','1920','--ei','display_height','1200')}
    CheckedAdb $launch
    [string[]]$observations=if($EmulateUserPmcc){@('--emulate-user-pmcc')}elseif($LoaderState){@('--loader-state')}elseif($BootMilestone){@('--boot-milestone',$BootMilestone)}else{@('--sample-after-io','--process-inventory','--device-inventory','--break-on-reboot','--break-on-idle')}
    if($RuntimeSnapshot){$observations+='--boot-runtime-snapshot'}
    if($PmuCycleReadAb){$observations+='--pmu-cycle-read-ab'}
    if($QuietDriverNotifications){$observations+='--quiet-driver-notifications'}
    if($PrepareInstallTarget -or $ResumePreparedInstallTarget){$observations+='--prepared-install-media'}
    if($ResumeWindowsInstallTarget){$observations+='--mutable-install-media'}
    if($IddDisposableBootstrap){$observations+='--idd-disposable-bootstrap'}
    if($env:UAVF_WDDM_PNP -eq '1'){$observations+='--wddm-pnp'}
    if($RtcClockShim){$observations+='--rtc-clock-shim'}
    if($DynamicGic){$observations+='--dynamic-gic'}
    if($FixedEightCpuGic){$observations+='--fixed-eight-cpu-gic'}
    if($env:UAVF_KD_PSCI_RESERVE -eq '1'){$observations+='--psci-owned-reserve'}
    if($env:UAVF_KD_PSCI_RUNTIME -eq '1'){
        if($env:UAVF_KD_PSCI_RESERVE -ne '1'){throw 'Runtime map control requires own reserve'}
        $observations+='--psci-runtime-reserve'
        if($env:UAVF_KD_PSCI_ZERO_CONTEXT -eq '1'){$observations+='--psci-zero-context-ab'}
        if($env:UAVF_KD_PSCI_ALL_CPUS -eq '1'){
            if($env:UAVF_KD_PSCI_ZERO_CONTEXT -ne '1'){throw 'All CPU adaptation requires exact context0 A/B'}
            $observations+='--psci-runtime-all-cpus'
            if($env:UAVF_KD_PSCI_PMU -eq '1'){$observations+='--psci-pmu-boundary'}
            if($env:UAVF_KD_PMU_COMMON_AB -eq '1'){
                if($env:UAVF_KD_PSCI_PMU -ne '1'){throw 'Counter allocation A/B requires exact PMU metadata profile'}
                $observations+='--pmu-common-counter-ab'
                if($env:UAVF_KD_PSCI_ENTRY_MARKER -eq '1'){$observations+='--psci-entry-marker'}
                if($env:UAVF_KD_PSCI_CACHE_PUBLISH -eq '1'){
                    if($env:UAVF_KD_PSCI_ENTRY_MARKER -ne '1' -or $env:UAVF_KD_PSCI_PRE_START_ONLY -ne '1' -or $env:UAVF_KD_PSCI_STARTUP_WAIT -ne '1'){throw 'Cache publication requires exact owned marker/wait profile'}
                    $observations+='--psci-cache-publish'
                    if($env:UAVF_KD_PSCI_FINAL_STATE -eq '1'){$observations+='--psci-final-start-state'}
                    if($env:UAVF_KD_PSCI_POST_START_MINIMAL -eq '1'){$observations+='--psci-post-start-minimal'}
                    if($env:UAVF_KD_PSCI_EXPLICIT_IPI_AB -eq '1'){$observations+='--psci-explicit-ipi-ab'}
                    if($env:UAVF_WINDOWS_RAM_GOP -eq '1'){$observations+='--windows-ram-gop'}
                }
                if($env:UAVF_KD_PSCI_DEFER_RETURN -eq '1'){
                    if($env:UAVF_KD_PSCI_ENTRY_MARKER -ne '1'){throw 'Deferred return requires own markers'}
                    $observations+='--psci-defer-return-probe'
                    if($env:UAVF_KD_PSCI_PRE_START_ONLY -eq '1'){$observations+='--psci-pre-start-only'}
                    if($env:UAVF_KD_PSCI_STARTUP_WAIT -eq '1'){
                        if($env:UAVF_KD_PSCI_PRE_START_ONLY -ne '1'){throw 'Startup wait requires pre-start marker profile'}
                        $observations+='--psci-startup-wait-probe'
                    }
                }
                if($env:UAVF_KD_SMP_START_SNAPSHOT){$observations+=@('--smp-start-snapshot-after',$env:UAVF_KD_SMP_START_SNAPSHOT)}
            }
        }
    }
    if($env:UAVF_KD_PSCI_ENTRY_AB -eq '1'){
        if($env:UAVF_KD_PSCI_RESERVE -ne '1'){throw 'First CPU entry requires own reserve'}
        $observations+='--psci-first-cpu-entry-ab'
    }
    if($env:UAVF_KD_PSCI_LOW_MEMORY -eq '1'){$observations+='--psci-low-memory-ab'}
    if($env:UAVF_KD_PSCI_CPU_ON -eq '1'){
        if(!$FixedEightCpuGic -or !$MatchHostCpus){throw 'PSCI observation only on fixed eight CPU profile'}
        $observations+='--inspect-psci-cpu-on'
        if($env:UAVF_KD_PSCI_ZERO_X7 -eq '1'){$observations+='--psci-zero-x7-ab'}
    }
    if($MatchHostCpus){$observations+='--match-host-cpus'}
    # A bounded observer must not leave HalReturnToFirmware armed after EOF.
    # CID2139 proved shutdown stops exactly on that stale diagnostic breakpoint.
    # Normal retained-runtime tests use lifecycle/serial evidence, not this probe.
    if($HoldRuntime){$observations+='--hold-runtime'}
    if($env:UAVF_KD_LOADER_TIME -eq '1'){
        if(!$HoldRuntime -or !$ResumeWindowsInstallTarget -or !$EmulateUserPmcc -or !$QuietDriverNotifications -or $DynamicGic -or $MatchHostCpus -or $env:UAVF_KD_INSPECT_AFTER -or $env:UAVF_KD_CLOCK_SEED -or $env:UAVF_KD_BOOT_CLOCK_DELTA -or $env:UAVF_KD_BOOT_CALENDAR){throw 'One exact isolated loader time observation only'}
        $observations+=@('--inspect-loader-time',(Join-Path $workspaceRoot 'build-logs\windows-post-linux-reassessment-20261004\installed-26200-kernel-audit\winload.efi'))
    }
    if($env:UAVF_KD_CLOCK_SEED -eq '1'){
        if(!$HoldRuntime -or !$ResumeWindowsInstallTarget -or !$EmulateUserPmcc -or !$QuietDriverNotifications -or $env:UAVF_KD_INSPECT_AFTER){throw 'Clock seed requires exact isolated runtime and no late inspection'}
        $observations+='--inspect-clock-seed'
    }
    if($env:UAVF_KD_BOOT_CLOCK_DELTA -eq '1'){
        if(!$HoldRuntime -or !$ResumeWindowsInstallTarget -or !$EmulateUserPmcc -or !$QuietDriverNotifications -or $env:UAVF_KD_INSPECT_AFTER -or $env:UAVF_KD_CLOCK_SEED -eq '1'){throw 'Boot clock delta requires one isolated observation only'}
        $observations+='--inspect-boot-clock-delta'
    }
    if($env:UAVF_KD_BOOT_CALENDAR -eq '1'){
        if(!$HoldRuntime -or !$ResumeWindowsInstallTarget -or !$EmulateUserPmcc -or !$QuietDriverNotifications -or $env:UAVF_KD_CLOCK_SEED -eq '1' -or $env:UAVF_KD_BOOT_CLOCK_DELTA -eq '1'){throw 'Boot calendar A/B requires frozen isolated runtime only'}
        $utcCalendar=[DateTime]::UtcNow.ToFileTimeUtc()
        $observations+=@('--correct-boot-calendar-100ns',[string]$utcCalendar)
    }
    if($env:UAVF_KD_RAM_FRAMEBUFFER_AUDIT -eq '1'){
        if(!$FixedEightCpuGic -or !$MatchHostCpus -or $env:UAVF_WINDOWS_RAM_GOP -ne '1' -or !$HoldRuntime -or $env:UAVF_KD_INSPECT_AFTER){throw 'Exact eightCPU GOP owner required for bounded read-only framebuffer observation'}
        $observations+='--ram-framebuffer-audit'
    }
    # Opt-in only: a purpose-built read-only late inspection, not a VM flag.
    if($env:UAVF_KD_INSPECT_AFTER){
        $inspectSeconds=0
        if(!$HoldRuntime -or ![int]::TryParse($env:UAVF_KD_INSPECT_AFTER,[ref]$inspectSeconds) -or $inspectSeconds -lt 120 -or $inspectSeconds -gt 1800){throw 'Late inspection requires hold-runtime and120..1800seconds'}
        $observations+=@('--process-inventory','--inspect-runtime-after',[string]$inspectSeconds)
        if($env:UAVF_KD_HOT_PROCESS){
            if($env:UAVF_KD_HOT_PROCESS -ne 'lsass.exe'){throw 'Only evidence-backed LSASS code observation is supported'}
            $observations+=@('--inspect-hot-process',$env:UAVF_KD_HOT_PROCESS)
            if($env:UAVF_KD_WAIT_TIMEOUT -eq '1'){$observations+='--inspect-wait-timeout'}
            if($env:UAVF_KD_WAIT_RETURN -eq '1'){
                if($env:UAVF_KD_WAIT_TIMEOUT -ne '1'){throw 'Wait return requires typed timeout scope'}
                $observations+='--inspect-wait-return'
            }
            if($env:UAVF_KD_WAIT_REQUESTS -eq '1'){
                if($env:UAVF_KD_WAIT_TIMEOUT -ne '1'){throw 'Bounded same-VM requests require typed wait inspection'}
                $observations+='--allow-runtime-inspection-requests'
            }
        }
        elseif($env:UAVF_KD_WAIT_TIMEOUT){throw 'Wait timeout requires the exact LSASS hot-process observation'}
    }
    elseif($env:UAVF_KD_HOT_PROCESS){throw 'Hot-thread observation requires explicit late inspection'}
    elseif($env:UAVF_KD_WAIT_TIMEOUT){throw 'Wait timeout requires explicit bounded late inspection'}
    if($env:UAVF_NATIVE_KD_COMMANDS){
        if(!$HoldRuntime -or $env:UAVF_KD_INSPECT_AFTER){throw 'Native KD is a separate retained-runtime observer scope'}
        $nativeArgs=@('--adb',$adb,'--evidence',$evidence,'--token-file',(Join-Path $evidence 'owner-resume-token.private.txt'),'--commands',$env:UAVF_NATIVE_KD_COMMANDS,'--seconds',([string]$ObserverSeconds))
        if($env:UAVF_NATIVE_KD_ACK_RECOVERY -eq '1'){$nativeArgs+='--replay-ack-on-resend'}
        & $python "$repoRoot\tools\windows-reassessment\native-kd-owner-bridge.py" @nativeArgs
    }else{
        & $python "$repoRoot\tools\windows-reassessment\trace-kd-boot-drivers.py" $adb unused $evidence "$workspaceRoot\build-logs\windows-arm64-timer-static-20260913\ntoskrnl.exe" --candidate-sha $sha --cam-access --periodic-imask-workaround --idle-seconds 60 --seconds $ObserverSeconds --app-bridge-token $token --app-service-owner @observations
    }
    if($LASTEXITCODE){throw 'Observer failed; inspect retained evidence'}
}finally{
    foreach($name in @('owner-report.txt','service-owner-report.txt','service-effective-config.json','encoded-display-report.txt','android-surface-first.png')){& $adb pull "/sdcard/Android/data/com.example.uavfwinpetest/files/$name" (Join-Path $evidence $name)}
    & $adb shell run-as com.example.uavfwinpetest cp files/service-crosvm.log /sdcard/Android/data/com.example.uavfwinpetest/files/service-private-crosvm.log
    & $adb shell run-as com.example.uavfwinpetest chmod 644 /sdcard/Android/data/com.example.uavfwinpetest/files/service-private-crosvm.log
    & $adb pull /sdcard/Android/data/com.example.uavfwinpetest/files/service-private-crosvm.log (Join-Path $evidence 'service-private-crosvm.log')
    $ownerEvidence=Join-Path $evidence 'owner-report.txt'
    $retain=$KeepRunning -and (Test-Path -LiteralPath $ownerEvidence) -and ((Get-Content -Raw -LiteralPath $ownerEvidence) -match 'ANDROID_SURFACE_READBACK=PASS')
    $observerEvents=Join-Path $evidence 'events.jsonl'
    if($retain -and (Test-Path -LiteralPath $observerEvents) -and
            (Select-String -LiteralPath $observerEvents -Pattern '"tag": "OBSERVER_STOP"' -Quiet)){
        # A historical Surface frame does NOT prove the guest resumed after
        # a failed late debugger RPC. Never retain a possibly debugger-halted VM.
        $retain=$false
        'WINDOWS_NOT_RETAINED_AFTER_OBSERVER_FAILURE; PREVIOUS_SURFACE_IS_NOT_CURRENT_LIVENESS'
    }
    if($retain){
        $actualVm=(& $adb shell /apex/com.android.virt/bin/vm list) -join "`n"
        if($LASTEXITCODE -eq 0 -and $actualVm -match 'requesterUid: 10042' -and $actualVm -match 'cid: \d+'){
            'VERIFIED_WINDOWS_VM_RETAINED_FOR_INPUT_TEST'
        }else{
            # A prior Surface readback is not evidence that the owner/VM is
            # still alive after an external stop or guest shutdown.
            'WINDOWS_VM_NOT_RETAINED; PRIOR_SURFACE_PASS_IS_HISTORICAL'
        }
    }else{& $adb shell am force-stop com.example.uavfwinpetest}
    if($original -eq 'null'){& $adb shell settings delete global hidden_api_policy}else{& $adb shell settings put global hidden_api_policy $original}
    & $adb forward --remove tcp:39101
    & $adb shell /apex/com.android.virt/bin/vm list
}
