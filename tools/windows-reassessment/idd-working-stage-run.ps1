param([switch]$DriverAdmissionOnly,[switch]$RealDesktop,[switch]$ReconnectSource,[switch]$ReconnectProbe,[switch]$RecoverInterruptedDesktop)
$ErrorActionPreference='Stop'
$target='C:\UAVF\IddWorkingProbe';$alias=$null;$receiver=$null;$replaced=@()
$suffix=if($RealDesktop){'idd-desktop22'}else{'idd-x264'}
if($ReconnectSource){
    if(!$RealDesktop){throw 'Reconnect source requires the same real Desktop proof'}
    $suffix='idd-reconnect-desktop22'
}
if($ReconnectProbe){
    if($RealDesktop -or $ReconnectSource){throw 'Headless IPC test is not a Desktop proof'}
    $suffix='idd-reconnect-probe23'
}
if($RecoverInterruptedDesktop){
    if(!$RealDesktop -or !$ReconnectSource -or $ReconnectProbe){throw 'Recovery requires Desktop proof'}
    $suffix='idd-reconnect-desktop25'
}
if(!$DriverAdmissionOnly){throw 'Only the prepared finite driver admission is permitted'}
try {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    if($identity.User.Value -ne 'S-1-5-18'){throw 'Expected existing SYSTEM broker token'}
    $journal=if(Test-Path "$target\rollback.json"){"$target\rollback.json"}else{"$target\rollback-before-pipe.json"}
    $state=Get-Content $journal -Raw|ConvertFrom-Json
    if($state.serial -ne '3EF669B7' -or $state.thumb -ne '80E30D3F7A18BD2A26A40EDF43C71F1D31F41558'){throw 'Unexpected journal'}
    if((Get-FileHash "$target\idd-working-test.ps1").Hash -ne '50EF014C20689AE5A5F3668C1982C622E06EE79C82776EB48D1BE83D67E6F4F9'){throw 'Unexpected admission preimage'}
    if((Get-Content "$target\admission.txt" -Raw) -notmatch 'STATUS=0x180600a PROBLEM=0'){throw 'Expected admitted driver result'}
    if((Get-FileHash "$target\idd-probe-device.exe").Hash -ne 'EDEE1DFFF5ACAF0DC61AB2085DFAD1C8C2BB10546A21886CF07D04C1F7A59F64'){throw 'Unexpected device helper preimage'}
    if($PSScriptRoot -notmatch '^\\\\\?\\Volume\{[0-9a-fA-F-]+\}\\'){throw 'Expected native volume GUID'}
    $volume=$PSScriptRoot.Substring(0,$PSScriptRoot.IndexOf('}')+2)
    foreach($letter in @('V','Y','Z')){if(!(Test-Path "${letter}:\")){$alias="${letter}:";break}}
    if(!$alias){throw 'No free volume alias'}
    & mountvol $alias $volume
    if($LASTEXITCODE){$alias=$null;throw 'Alias failed'}
    $source="$alias\$($PSScriptRoot.Substring($volume.Length))\idd-working-test.ps1"
    $delivery="$alias\$($PSScriptRoot.Substring($volume.Length))"
    $motion="$target\idd-motion-source.exe"
    if(!$RealDesktop -and (Get-FileHash $motion).Hash -ne '7F80E85CBABB15BC74B9251C107A216625A5594E93CB0DEA3868C377CB5161DD'){throw 'Motion readback failed'}
    $payloads=@(
        @('package\IddSampleDriver.dll','C2D90A00838E512F1CA089245D3A02874ACAB7792819FDFD4904AB91DABEE767','B15D9F4C840F3EB8569DAA6181136BAB37BB3F270933822E83F9920B4F0AFF5A'),
        @('package\IddSampleDriver.cat','2306BC28BC3DB27BBED28D476A5993C5E45FDE17E17CA08CE2B459B5D0597FF1','4B649830BF0B0A44CB55547DAEAA38948829142FECDE49BDA778E884A48E7707'),
        @('idd-etw-read.exe','0C976FAB39879F3F9D855355EDEDEEF313305809271C20B44F43876CA39E474E','3775F62B007BB61AF8CF70F1D6D9EA981A803870AAD342BDD802E3A2AB7B721C'))
    if($ReconnectSource -or $ReconnectProbe){
        $payloads[0][2]='42F1D3F3DBC86AFE079E3AA6EBC0672DCF26B9A976DDA6574FC612D5138EE29C'
        $payloads[1][2]='A484F851A6498EB90BF941D0A1A48B959F30D840C8F982F8889747FE2D223F1F'
    }
    if($ReconnectProbe -or $ReconnectSource){$payloads[2][2]='7DFCC6B28776654A70931B08FAFFFDC6B9DE36B91E67442EBB9F787E3FD352FA'}
    if($RealDesktop){$payloads+=,@('idd-motion-source.exe','7F80E85CBABB15BC74B9251C107A216625A5594E93CB0DEA3868C377CB5161DD','86C45C9C87416D374AB2B40AA309F66734CB0DC24BA00F2E4DCCBB479C96CC8F')}
    if($RecoverInterruptedDesktop){
        foreach($item in $payloads){
            $dest="$target\$($item[0])";$backup="$dest.before-idd-reconnect-desktop22"
            if((Get-FileHash $backup).Hash -ne $item[1] -or (Get-FileHash $dest).Hash -notin @($item[1],$item[2])){throw 'Unknown recovery preimage'}
        }
        & "$target\idd-working-test.ps1" -RollbackOnly
        foreach($item in $payloads){
            $dest="$target\$($item[0])";Copy-Item "$dest.before-idd-reconnect-desktop22" $dest
            if((Get-FileHash $dest).Hash -ne $item[1]){throw 'Recovery readback failed'}
        }
        'OWN_FOUR_FILES_RECOVERED'|Set-Content "$target\recovered-desktop22.txt"
    }
    foreach($item in $payloads){
        $dest="$target\$($item[0])"
        if((Get-FileHash $dest).Hash -ne $item[1] -or (Test-Path "$dest.before-$suffix")){throw 'Timing payload preimage changed'}
        if((Get-FileHash "$delivery\$($item[0])").Hash -ne $item[2]){throw 'Unexpected delivery'}
        Copy-Item $dest "$dest.before-$suffix";$replaced+=,$item
        Copy-Item "$delivery\$($item[0])" $dest
        if((Get-FileHash $dest).Hash -ne $item[2]){throw 'Pipe payload readback failed'}
    }
    if(Test-Path "$target\motion-report.txt"){Move-Item "$target\motion-report.txt" "$target\motion-before-$suffix.txt"}
    if((Get-FileHash "$target\idd-probe-device.exe").Hash -ne 'EDEE1DFFF5ACAF0DC61AB2085DFAD1C8C2BB10546A21886CF07D04C1F7A59F64'){throw 'Helper readback failed'}
    if((Get-FileHash $source).Hash -ne (Get-FileHash "$target\idd-working-test.ps1").Hash){throw 'Script readback failed'}
    & "$target\idd-working-test.ps1" -RollbackOnly
    if(Test-Path "$target\rollback-before-$suffix.json"){throw 'Preserve previous journal'}
    if(Test-Path "$target\rollback.json"){Move-Item "$target\rollback.json" "$target\rollback-before-$suffix.json"}
    $cert=[Security.Cryptography.X509Certificates.X509Certificate2]::new((Join-Path $target 'package\UavfIddTestOnly.cer'))
    if($cert.Thumbprint -ne $state.thumb){throw 'Unexpected own certificate'}
    $before=Get-Date
    $changed=$before -lt $cert.NotBefore -or $before -gt $cert.NotAfter
    if($changed){Set-Date ([DateTime]::Parse('2026-10-14T05:39:54Z').ToLocalTime())|Out-Null}
    $after=Get-Date
    @{before=$before.ToString('o');after=$after.ToString('o');changed=$changed;scope='Diagnostic certificate fixture only; native QPC/CPU/firmware unchanged'}|ConvertTo-Json|Set-Content "$target\pipe-clock-fixture.json"
    if($after -lt $cert.NotBefore -or $after -gt $cert.NotAfter){throw 'Clock fixture failed; no admission'}
    if($ReconnectProbe){
        if((Test-Path "$target\pipe-reconnect23.txt") -or (Test-Path "$target\pipe-reconnect23-errors.txt")){throw 'Preserve previous headless probe logs'}
        $receiver=Start-Process "$target\idd-etw-read.exe" -ArgumentList '--pipe-probe' -PassThru -RedirectStandardOutput "$target\pipe-reconnect23.txt" -RedirectStandardError "$target\pipe-reconnect23-errors.txt"
        'HEADLESS_SYSTEM_IPC_PROBE_ONLY; NO_USER_DESKTOP_OR_FPS_CLAIM'|Set-Content "$target\encoded-stage.txt"
    }else{'IDD_ENCODER_OWNS_PIPE; NO_COMPETING_PROBE_CLIENT'|Set-Content "$target\encoded-stage.txt"}
    & "$target\idd-working-test.ps1"
    if($ReconnectProbe){
        if(!$receiver.WaitForExit(15000) -or $receiver.ExitCode -ne 0){throw 'Guest same-worker reconnect probe failed; retain logs'}
        if((Get-Content "$target\pipe-reconnect23.txt" -Raw) -notmatch 'GUEST_PIPE_RECONNECT_RESULT=0 CONNECTIONS=3 SAMPLES_PER_CONNECTION=4'){throw 'Incomplete reconnect result'}
    }
} catch {($_|Out-String)|Set-Content "$target\job-error.txt";throw}
finally {
    if($receiver -and !$receiver.HasExited){Stop-Process -Id $receiver.Id -ErrorAction SilentlyContinue}
    foreach($item in $replaced){
        $dest="$target\$($item[0])"
        if((Get-FileHash "$dest.before-$suffix").Hash -ne $item[1]){throw 'Rollback preimage changed'}
        Copy-Item "$dest.before-$suffix" $dest
        if((Get-FileHash $dest).Hash -ne $item[1]){throw 'Rollback readback failed'}
    }
    if($replaced.Count -gt 0 -and $replaced.Count -eq $payloads.Count){'OWN_PIPE_PAYLOAD_RESTORED'|Set-Content "$target\pipe-payload-restored.txt"}
    if($alias){& mountvol $alias /D}
}
