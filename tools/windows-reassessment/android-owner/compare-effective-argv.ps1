param([Parameter(Mandatory=$true)][string]$ShellEvents,[Parameter(Mandatory=$true)][string]$AppEvents)
$ErrorActionPreference='Stop'
function Events([string]$Path){Get-Content -LiteralPath $Path | ForEach-Object {$_ | ConvertFrom-Json}}
function Command($Events){
    $value=(($Events | Where-Object tag -eq HOST_CROSVM_COMMAND | Select-Object -First 1).processes -split "`n" | Where-Object {$_ -match '^\s*\d+\s+\d+\s+crosvm_VmRun'} | Select-Object -First 1)
    if(!$value){throw 'No actual expanded crosvm command evidence'};return $value
}
$shell=Events $ShellEvents;$app=Events $AppEvents
$shellArgv=Command $shell;$appArgv=Command $app
$a=$shellArgv -replace '^\s*\d+\s+\d+\s+','' -replace '--cid \d+','--cid <CID>'
$b=$appArgv -replace '^\s*\d+\s+\d+\s+','' -replace '--cid \d+','--cid <CID>'
$result=[ordered]@{
    scope='actual-expanded-crosvm-argv; not proof of all CPU register state';shell=$shellArgv;app=$appArgv
    allowed_ignored=@('PID','PPID','CID');fd_numbers_ignored=$false
    semantic_equal=($a -ceq $b);differences=@(Compare-Object ($a -split ' ') ($b -split ' '))
    shell_workaround_order=@($shell | Where-Object tag -match '^WINDOWS_CAM_|^PERIODIC_IMASK_RAM_' | ForEach-Object tag)
    app_workaround_order=@($app | Where-Object tag -match '^WINDOWS_CAM_|^PERIODIC_IMASK_RAM_' | ForEach-Object tag)
}
$json=$result | ConvertTo-Json -Depth 8
# Generated evidence artifact, not a source edit. Never overwrite earlier evidence.
$target=Join-Path (Split-Path -Parent $AppEvents) 'shell-app-effective-argv-diff.json'
if(Test-Path -LiteralPath $target){throw 'Preserve previous machine-readable diff'}
[IO.File]::WriteAllText($target,$json,[Text.UTF8Encoding]::new($false));$json
