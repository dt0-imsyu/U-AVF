[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$LibraryDirectory,[string]$TraceFile,[switch]$SelfTest,[string]$OutputPath)
$ErrorActionPreference='Stop'
$lib=(Resolve-Path -LiteralPath $LibraryDirectory).Path
$fast=Join-Path $lib 'Microsoft.Diagnostics.FastSerialization.dll'
$trace=Join-Path $lib 'Microsoft.Diagnostics.Tracing.TraceEvent.dll'
[Reflection.Assembly]::LoadFrom($fast) | Out-Null
[Reflection.Assembly]::LoadFrom($trace) | Out-Null
$references=@(Get-ChildItem -LiteralPath (Join-Path $PSHOME 'ref') -Filter '*.dll' | ForEach-Object FullName)+@($trace,$fast)
Add-Type -Path (Join-Path $PSScriptRoot 'EtwCpuSummary.cs') -ReferencedAssemblies $references
if($SelfTest){[UavfEtwCpuSummary]::SelfTest();'ETW_CPU_SUMMARY_SELFTEST_PASS'}
if($TraceFile){
    $summary=[UavfEtwCpuSummary]::Read((Resolve-Path -LiteralPath $TraceFile).Path) | ConvertTo-Json -Depth 6
    if($OutputPath){
        if(Test-Path -LiteralPath $OutputPath){throw 'Preserve earlier analysis'}
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath),$summary,[Text.UTF8Encoding]::new($false))
    }
    $summary
}
elseif(!$SelfTest){throw 'Provide exact own trace or selftest'}
