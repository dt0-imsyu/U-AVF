$ErrorActionPreference='Stop'
$source=Get-Content "$PSScriptRoot/idd-working-stage-run.ps1" -Raw
$start=$source.IndexOf('    if($RecoverInterruptedDesktop){', $source.IndexOf('$payloads=@('))
$end=$source.IndexOf('    foreach($item in $payloads){', $source.IndexOf("'OWN_FOUR_FILES_RECOVERED'",$start))
if($start -lt 0 -or $end -lt 0){throw 'Actual recovery block absent'}
$block=[scriptblock]::Create($source.Substring($start,$end-$start))
$target=Join-Path ([IO.Path]::GetTempPath()) ('uavf-recovery-'+[guid]::NewGuid())
New-Item -ItemType Directory $target|Out-Null
# Test only the actual recovery block on fresh owned files, never a guest disk.
try {
    # Supply a local harmless stand-in for the already separately guarded rollback.
    Set-Content "$target/idd-working-test.ps1" ("param([switch]`$RollbackOnly); 'called' | Set-Content '"+$target+"/rollback-called.txt'")
    $payloads=@(); foreach($i in 0..3){
        $name="file$i"; Set-Content "$target/$name" "original$i"
        Copy-Item "$target/$name" "$target/$name.before-idd-reconnect-desktop22"
        $old=(Get-FileHash "$target/$name").Hash
        Set-Content "$target/$name" "candidate$i"
        $payloads+=,@($name,$old,(Get-FileHash "$target/$name").Hash)
    }
    $RecoverInterruptedDesktop=$true
    & $block
    & $block
    foreach($p in $payloads){if((Get-FileHash "$target/$($p[0])").Hash -ne $p[1]){throw 'Idempotent readback failed'}}
    foreach($kind in @('unknown-current','unknown-backup')){
        Remove-Item "$target/rollback-called.txt" -ErrorAction SilentlyContinue
        $bad=if($kind -eq 'unknown-current'){"$target/file3"}else{"$target/file3.before-idd-reconnect-desktop22"}
        $prior=[IO.File]::ReadAllBytes($bad); [IO.File]::WriteAllBytes($bad,[byte[]](1,2,3))
        $rejected=$false;try {& $block}catch {if($_ -notmatch 'Unknown recovery preimage'){throw};$rejected=$true}
        if(!$rejected -or (Test-Path "$target/rollback-called.txt")){throw 'Rejection must precede mutations'}
        [IO.File]::WriteAllBytes($bad,$prior)
    }
    'ACTUAL_RECOVERY_BLOCK_IDEMPOTENT_AND_UNKNOWN_PREIMAGE_REJECTION_PASS'
    $active=Get-Content "$PSScriptRoot/idd-active-desktop-stage-run.ps1" -Raw
    $first=$active.IndexOf('    if($RecoverInterruptedDesktop){',$active.IndexOf('$payloads=@('))
    $last=$active.IndexOf('    foreach($item in $payloads){',$first)
    $archive=[scriptblock]::Create($active.Substring($first,$last-$first))
    $fixture='E:/U-AVF/artifacts/idd25-cold-20261014/UAVF/IddWorkingProbe'
    foreach($name in @('desktop-topology22.txt','desktop-topology22-before.bin')){Copy-Item "$fixture/$name" "$target/$name"}
    & $archive
    foreach($name in @('desktop-topology22.txt','desktop-topology22-before.bin')){
        if((Test-Path "$target/$name") -or (Get-FileHash "$target/$name.before-active26").Hash -ne (Get-FileHash "$fixture/$name").Hash){throw 'Actual archive readback failed'}
    }
    'ACTUAL_PREVIOUS_TOPOLOGY_ARCHIVE_READBACK_PASS'
} finally {
    $resolved=[IO.Path]::GetFullPath($target)
    if(!$resolved.StartsWith([IO.Path]::GetTempPath()) -or [IO.Path]::GetFileName($resolved) -notlike 'uavf-recovery-*'){throw 'Unsafe cleanup target'}
    Remove-Item -LiteralPath $resolved -Recurse
}
