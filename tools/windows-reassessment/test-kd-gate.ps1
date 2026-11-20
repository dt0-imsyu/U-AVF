$ErrorActionPreference='Stop'
$source=Get-Content -Raw "$PSScriptRoot/../shell-serial-loopback/run-windows-kd-framed-bridge.ps1"
$match=[regex]::Match($source,"(?s)Add-Type -TypeDefinition @'\r?\n(.*?)\r?\n'@")
Add-Type -TypeDefinition $match.Groups[1].Value
$flags=[Reflection.BindingFlags]'NonPublic,Static'
$type=[WinAvfFramedKdBridge]
$dir=Join-Path ([IO.Path]::GetTempPath()) ('uavf-kd-gate-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $dir | Out-Null
foreach($split in @(1,5,12,26)) {
    $marker='WG:LOAD=PASS,START_WINDOWS'
    $type.GetField('gateOpen',$flags).SetValue($null,$false)
    $type.GetField('gateMarker',$flags).SetValue($null,$marker)
    $type.GetField('gateWindow',$flags).SetValue($null,'')
    $ready=New-Object System.Threading.ManualResetEventSlim($false)
    $type.GetField('gateReady',$flags).SetValue($null,$ready)
    $first=$marker.Substring(0,[Math]::Min($split,$marker.Length))
    $second=$marker.Substring($first.Length)+'KD_PACKET'
    $wire='O:'+ [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($first))+"`n"+'O:'+ [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($second))+"`n"
    $inputStream=New-Object IO.MemoryStream(,[Text.Encoding]::ASCII.GetBytes($wire))
    $outputStream=New-Object IO.MemoryStream
    $invokeArgs=New-Object 'object[]' 4
    $invokeArgs[0]=$inputStream.PSObject.BaseObject
    $invokeArgs[1]=$outputStream.PSObject.BaseObject
    $invokeArgs[2]=[string](Join-Path $dir "rx-$split.bin")
    $invokeArgs[3]=[string](Join-Path $dir 'status.txt')
    $type.GetMethod('DecodeFromAdb',$flags).Invoke($null,$invokeArgs) | Out-Null
    if([Text.Encoding]::ASCII.GetString($outputStream.ToArray()) -ne 'KD_PACKET'){throw "RX suffix lost at split=$split"}
    if(-not $ready.IsSet){throw 'TX gate not released'}
}
'SPLIT_MARKER_AND_KD_SUFFIX=PASS'
"TEST_EVIDENCE=$dir"
