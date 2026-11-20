param(
    [UInt64]$FramebufferBase,
    [UInt64]$FramebufferBytes,
    [switch]$SelfTest
)
# Read-only guest inventory. No mapping, driver loading, BCD writes or reboot.
# Base/length must come from THIS boot's EBS markers, not a remembered GPA.
$ErrorActionPreference = 'Stop'

function Get-FramebufferOverlap {
    param([UInt64]$Base, [UInt64]$Bytes, [UInt64]$Start, [UInt64]$End)
    if ($Bytes -eq 0 -or $Base -gt ([UInt64]::MaxValue - $Bytes)) {
        throw 'Invalid framebuffer range'
    }
    if ($End -lt $Start) { throw 'Invalid resource range' }
    $last = $Base + $Bytes - 1
    [pscustomobject]@{
        Overlap = ($Start -le $last -and $End -ge $Base)
        CoversWholeFrame = ($Start -le $Base -and $End -ge $last)
    }
}

if ($SelfTest) {
    $base = [UInt64]4285530112 # FF700000; avoid signed hex literals in Windows PowerShell.
    $bytes = [UInt64]9216000
    $cases = @(
        @($base, ($base + $bytes - 1), $true, $true),
        @(($base - 4096), ($base + $bytes), $true, $true),
        @($base, $base, $true, $false),
        @(($base + $bytes - 1), ($base + $bytes - 1), $true, $false),
        @(($base + $bytes), ($base + $bytes + 4096), $false, $false),
        @(($base - 4096), ($base - 1), $false, $false)
    )
    foreach ($case in $cases) {
        $result = Get-FramebufferOverlap $base $bytes $case[0] $case[1]
        if ($result.Overlap -ne $case[2] -or $result.CoversWholeFrame -ne $case[3]) {
            throw 'Range classification self-test failed'
        }
    }
    foreach ($case in @(@(0, 0, 0, 0), @([UInt64]::MaxValue, 1, 0, 0), @(1, 1, 2, 1))) {
        $rejected = $false
        try { $null = Get-FramebufferOverlap @case } catch { $rejected = $true }
        if (-not $rejected) { throw 'Malformed range was accepted' }
    }
    'PASS: 6 overlap/coverage cases and 3 malformed-range rejections; no guest queries'
    return
}

if ($FramebufferBytes -ne 9216000 -or ($FramebufferBase % 4096) -ne 0 -or
    $FramebufferBase -lt 2147483648 -or
    $FramebufferBase -gt (4294967296 - $FramebufferBytes)) {
    throw 'Expected fresh aligned native 1920x1200 EBS range below 4 GiB'
}
$queryErrors = [System.Collections.Generic.List[string]]::new()
$resources = @()
try {
    foreach ($resource in @(Get-CimInstance -ClassName Win32_DeviceMemoryAddress)) {
        $overlap = Get-FramebufferOverlap $FramebufferBase $FramebufferBytes `
            ([UInt64]$resource.StartingAddress) ([UInt64]$resource.EndingAddress)
        if (-not $overlap.Overlap) { continue }
        $owners = @()
        try {
            $owners = @(Get-CimAssociatedInstance -InputObject $resource `
                -Association Win32_PnPAllocatedResource -ResultClassName Win32_PnPEntity |
                Select-Object DeviceID, Name, Service, PNPClass)
        } catch { $queryErrors.Add('Resource association: ' + $_.Exception.Message) }
        $resources += [pscustomobject]@{
            Start = ('0x{0:X16}' -f [UInt64]$resource.StartingAddress)
            EndInclusive = ('0x{0:X16}' -f [UInt64]$resource.EndingAddress)
            CoversWholeFrame = $overlap.CoversWholeFrame
            Owners = $owners
        }
    }
} catch { $queryErrors.Add('Memory inventory: ' + $_.Exception.Message) }
$display = @()
try {
    $display = @(Get-CimInstance -ClassName Win32_VideoController |
        Select-Object Name, PNPDeviceID, DriverVersion, CurrentHorizontalResolution,
            CurrentVerticalResolution, CurrentBitsPerPixel)
} catch { $queryErrors.Add('Display inventory: ' + $_.Exception.Message) }
[pscustomobject]@{
    Kind = 'UAVF_FRAMEBUFFER_OWNERSHIP_READONLY_V1'
    ObservedBase = ('0x{0:X16}' -f $FramebufferBase)
    Bytes = $FramebufferBytes
    MatchingResources = $resources
    Display = $display
    Errors = @($queryErrors.ToArray())
    WindowsMemoryType = 'UNKNOWN: PnP inventory is not a PFN/loader classification'
    CacheAttributes = 'UNKNOWN: must inspect the existing mapping; do not guess'
    ReaderAdmission = 'NOT_PROVEN'
    MappingAuthorized = $false
    Note = 'No matching PnP resource is inconclusive for a firmware-inherited framebuffer'
} | ConvertTo-Json -Depth 8
