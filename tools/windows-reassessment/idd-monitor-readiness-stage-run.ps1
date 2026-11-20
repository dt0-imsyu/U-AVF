param([switch]$DriverAdmissionOnly,[switch]$RealDesktop,[switch]$ReconnectSource,[switch]$RecoverInterruptedDesktop)
$ErrorActionPreference='Stop'
$target='C:\UAVF\IddWorkingProbe';$alias=$null;$receiver=$null;$replaced=@()
$suffix=if($RealDesktop){'idd-desktop22'}else{'idd-x264'}
if($ReconnectSource){
    if(!$RealDesktop){throw 'Reconnect source requires the same real Desktop proof'}
    $suffix='idd-reconnect-desktop22'
}
if($RecoverInterruptedDesktop){
    if(!$RealDesktop -or !$ReconnectSource){throw 'Recovery requires Desktop proof'}
    $suffix='idd-continuous-workload29'
}
if(!$DriverAdmissionOnly){throw 'Only the prepared finite driver admission is permitted'}
try {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    if($identity.User.Value -ne 'S-1-5-18'){throw 'Expected existing SYSTEM broker token'}
    $journal=if(Test-Path "$target\rollback.json"){"$target\rollback.json"}else{"$target\rollback-before-pipe.json"}
    $state=Get-Content $journal -Raw|ConvertFrom-Json
    if($state.serial -ne '3EF669B7' -or $state.thumb -ne '80E30D3F7A18BD2A26A40EDF43C71F1D31F41558'){throw 'Unexpected journal'}
    if((Get-FileHash "$target\idd-working-test.ps1").Hash -notin @('C9CEDD3A4F7F1CA166B73334F52CC660BA690C46031CCA43F7A7A2B02D0EC43C','3458A34A12622600A725295F9F47882A0583577EB990CA76452F1DB45FF97ACB')){throw 'Unexpected admission preimage'}
    if((Get-Content "$target\admission.txt" -Raw) -notmatch 'STATUS=0x180600a PROBLEM=0'){throw 'Expected admitted driver result'}
    if((Get-FileHash "$target\idd-probe-device.exe").Hash -ne 'EDEE1DFFF5ACAF0DC61AB2085DFAD1C8C2BB10546A21886CF07D04C1F7A59F64'){throw 'Unexpected device helper preimage'}
    if($PSScriptRoot -notmatch '^\\\\\?\\Volume\{[0-9a-fA-F-]+\}\\'){throw 'Expected native volume GUID'}
    $volume=$PSScriptRoot.Substring(0,$PSScriptRoot.IndexOf('}')+2)
    foreach($letter in @('V','Y','Z')){if(!(Test-Path "${letter}:\")){$alias="${letter}:";break}}
    if(!$alias){throw 'No free volume alias'}
    & mountvol $alias $volume
    if($LASTEXITCODE){$alias=$null;throw 'Alias failed'}
    $source="$alias\$($PSScriptRoot.Substring($volume.Length))\idd-working-test.ps1"
    if((Get-FileHash $source).Hash -ne '3458A34A12622600A725295F9F47882A0583577EB990CA76452F1DB45FF97ACB'){throw 'Wrong bounded workload source'}
    if((Get-FileHash "$target\idd-working-test.ps1").Hash -ne (Get-FileHash $source).Hash){
        if(!(Test-Path "$target\idd-working-test.ps1.before-long-workload")){Copy-Item "$target\idd-working-test.ps1" "$target\idd-working-test.ps1.before-long-workload"}
        if((Get-FileHash "$target\idd-working-test.ps1.before-long-workload").Hash -ne 'C9CEDD3A4F7F1CA166B73334F52CC660BA690C46031CCA43F7A7A2B02D0EC43C'){throw 'Wrong bounded workload backup'}
        Copy-Item $source "$target\idd-working-test.ps1"
        if((Get-FileHash "$target\idd-working-test.ps1").Hash -ne (Get-FileHash $source).Hash){throw 'Exit guard readback failed'}
    }
    $delivery="$alias\$($PSScriptRoot.Substring($volume.Length))"
    $motion="$target\idd-motion-source.exe"
    if(!$RealDesktop -and (Get-FileHash $motion).Hash -ne '7F80E85CBABB15BC74B9251C107A216625A5594E93CB0DEA3868C377CB5161DD'){throw 'Motion readback failed'}
    $payloads=@(
        @('package\IddSampleDriver.dll','C2D90A00838E512F1CA089245D3A02874ACAB7792819FDFD4904AB91DABEE767','B15D9F4C840F3EB8569DAA6181136BAB37BB3F270933822E83F9920B4F0AFF5A'),
        @('package\IddSampleDriver.cat','2306BC28BC3DB27BBED28D476A5993C5E45FDE17E17CA08CE2B459B5D0597FF1','4B649830BF0B0A44CB55547DAEAA38948829142FECDE49BDA778E884A48E7707'),
        @('idd-etw-read.exe','0C976FAB39879F3F9D855355EDEDEEF313305809271C20B44F43876CA39E474E','3775F62B007BB61AF8CF70F1D6D9EA981A803870AAD342BDD802E3A2AB7B721C'))
    if($ReconnectSource){
        $payloads[0][2]='42F1D3F3DBC86AFE079E3AA6EBC0672DCF26B9A976DDA6574FC612D5138EE29C'
        $payloads[1][2]='A484F851A6498EB90BF941D0A1A48B959F30D840C8F982F8889747FE2D223F1F'
    }
    if($ReconnectSource){$payloads[2][2]='7DFCC6B28776654A70931B08FAFFFDC6B9DE36B91E67442EBB9F787E3FD352FA'}
    if($RealDesktop){
        $payloads+=,@('idd-motion-source.exe','7F80E85CBABB15BC74B9251C107A216625A5594E93CB0DEA3868C377CB5161DD','7DA58079674C0BD417AB54159506D3185892B4590931B6D4465981BF3608C554')
        $payloads+=,@('idd-probe-device.exe','EDEE1DFFF5ACAF0DC61AB2085DFAD1C8C2BB10546A21886CF07D04C1F7A59F64','93CFD28264DEEA6EF22203D4CD5D8FE85B161EA32E807E1260C340FB276FDE42')
    }
    if($RecoverInterruptedDesktop){
        $logs=@(@('desktop-topology22.txt','C048D7B72C24DBB78202A7A5ABCA54C8D8321083413A045B5E5ED9A00C5A88A4'),@('desktop-topology22-before.bin','EB66CF423917FF1328C4EE83C811D6DDF3CA18C073DD632B60B50062D30756FB'))
        foreach($log in $logs){if((Get-FileHash "$target\$($log[0])").Hash -ne $log[1] -or (Test-Path "$target\$($log[0]).before-continuous29")){throw 'Unknown previous topology proof'}}
        foreach($log in $logs){Move-Item "$target\$($log[0])" "$target\$($log[0]).before-continuous29"}
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
    if((Get-FileHash "$target\idd-probe-device.exe").Hash -ne '93CFD28264DEEA6EF22203D4CD5D8FE85B161EA32E807E1260C340FB276FDE42'){throw 'Bounded helper readback failed'}
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
    'IDD_ENCODER_OWNS_PIPE; NO_COMPETING_PROBE_CLIENT'|Set-Content "$target\encoded-stage.txt"
    & "$target\idd-working-test.ps1"
} catch {($_|Out-String)|Set-Content "$target\job-error.txt";throw}
finally {
    foreach($item in $replaced){
        $dest="$target\$($item[0])"
        if((Get-FileHash "$dest.before-$suffix").Hash -ne $item[1]){throw 'Rollback preimage changed'}
        Copy-Item "$dest.before-$suffix" $dest
        if((Get-FileHash $dest).Hash -ne $item[1]){throw 'Rollback readback failed'}
    }
    if($replaced.Count -gt 0 -and $replaced.Count -eq $payloads.Count){'OWN_PIPE_PAYLOAD_RESTORED'|Set-Content "$target\pipe-payload-restored.txt"}
    if($alias){& mountvol $alias /D}
}
