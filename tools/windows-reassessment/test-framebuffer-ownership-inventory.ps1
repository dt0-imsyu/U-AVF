$ErrorActionPreference = 'Stop'
$collector = Join-Path $PSScriptRoot 'collect-windows-framebuffer-ownership.ps1'
# Mocks shadow CIM only in this test process; never inspect the host as a guest.
$global:uavfInventoryMockFail = $false
$global:uavfAssociationMockFail = $false
function Get-CimInstance {
    param([string]$ClassName)
    if ($global:uavfInventoryMockFail) { throw 'mock CIM unavailable' }
    if ($ClassName -eq 'Win32_DeviceMemoryAddress') {
        [pscustomobject]@{StartingAddress=[UInt64]4285530112; EndingAddress=[UInt64]4294746111}
        [pscustomobject]@{StartingAddress=[UInt64]0; EndingAddress=[UInt64]4095}
    } elseif ($ClassName -eq 'Win32_VideoController') {
        [pscustomobject]@{Name='Mock BasicDisplay'; PNPDeviceID='ROOT\MOCK'; DriverVersion='test';
            CurrentHorizontalResolution=1920; CurrentVerticalResolution=1200; CurrentBitsPerPixel=32}
    } else { throw 'Unexpected CIM class' }
}
function Get-CimAssociatedInstance {
    param($InputObject, [string]$Association, [string]$ResultClassName)
    if ($global:uavfAssociationMockFail) { throw 'mock association unavailable' }
    if ($Association -ne 'Win32_PnPAllocatedResource' -or $ResultClassName -ne 'Win32_PnPEntity') {
        throw 'Unexpected association'
    }
    [pscustomobject]@{DeviceID='ROOT\MOCK'; Name='Mock owner'; Service='BasicDisplay'; PNPClass='Display'}
}
function Invoke-InventoryTest {
    (& $collector -FramebufferBase 4285530112 -FramebufferBytes 9216000) | ConvertFrom-Json
}
$report = Invoke-InventoryTest
if (@($report.MatchingResources).Count -ne 1 -or
    -not $report.MatchingResources[0].CoversWholeFrame -or
    $report.MatchingResources[0].Owners[0].Service -ne 'BasicDisplay' -or
    $report.MappingAuthorized -ne $false -or $report.CacheAttributes -notlike 'UNKNOWN:*' -or
    $report.WindowsMemoryType -notlike 'UNKNOWN:*') { throw 'Mock owner inventory mismatch' }
$global:uavfAssociationMockFail = $true
$report = Invoke-InventoryTest
if (@($report.Errors).Count -ne 1 -or @($report.MatchingResources[0].Owners).Count -ne 0 -or
    $report.MappingAuthorized -ne $false) { throw 'Failed association became permission proof' }
$global:uavfInventoryMockFail = $true
$report = Invoke-InventoryTest
if (@($report.Errors).Count -ne 2 -or @($report.MatchingResources).Count -ne 0 -or
    $report.MappingAuthorized -ne $false) { throw 'Failed inventory became permission proof' }
'PASS: owner association/whole-frame coverage, association failure, CIM failure; no real CIM calls'
