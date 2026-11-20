$ErrorActionPreference='Stop'
$script=Get-Content "$PSScriptRoot/idd-monitor-readiness-stage-run.ps1" -Raw
$first=$script.IndexOf('    if((Get-FileHash $source).Hash')
$last=$script.IndexOf('    $delivery=',$first)
if($first -lt 0 -or $last -lt 0){throw 'Actual upgrade block absent'}
$block=[scriptblock]::Create($script.Substring($first,$last-$first))
$target=Join-Path ([IO.Path]::GetTempPath()) ('uavf-script-upgrade-'+[guid]::NewGuid())
New-Item -ItemType Directory $target|Out-Null
try {
    $source="$target/delivery.ps1"
    $text=[IO.File]::ReadAllText("$PSScriptRoot/idd-working-test.ps1").Replace("`r`n","`n").Replace("`n","`r`n")
    [IO.File]::WriteAllBytes($source,[Text.Encoding]::UTF8.GetBytes($text))
    Copy-Item 'E:/U-AVF/artifacts/idd-after-control-cold-job-20261014/UAVF/IddWorkingProbe/idd-working-test.ps1' "$target/idd-working-test.ps1"
    & $block
    & $block
    if((Get-FileHash "$target/idd-working-test.ps1").Hash -ne 'C9CEDD3A4F7F1CA166B73334F52CC660BA690C46031CCA43F7A7A2B02D0EC43C'){throw 'Upgrade readback failed'}
    if((Get-FileHash "$target/idd-working-test.ps1.before-native-exit").Hash -ne '50EF014C20689AE5A5F3668C1982C622E06EE79C82776EB48D1BE83D67E6F4F9'){throw 'Backup readback failed'}
    'ACTUAL_OWN_SCRIPT_UPGRADE_AND_IDEMPOTENCE_PASS'
} finally {
    $resolved=[IO.Path]::GetFullPath($target)
    if(!$resolved.StartsWith([IO.Path]::GetTempPath()) -or [IO.Path]::GetFileName($resolved) -notlike 'uavf-script-upgrade-*'){throw 'Unsafe cleanup'}
    Remove-Item -LiteralPath $resolved -Recurse
}
