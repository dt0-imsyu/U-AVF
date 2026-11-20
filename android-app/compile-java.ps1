[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Javac,
    [Parameter(Mandatory)][string]$AndroidJar,
    [Parameter(Mandatory)][string]$ClassesDir,
    [Parameter(Mandatory)][string[]]$GeneratedSources
)

$ErrorActionPreference = 'Stop'
$sourceRoot = Join-Path $PSScriptRoot 'src'
$packageRoot = Join-Path $sourceRoot 'com\example\winavf'
$modules = @('navigation', 'storage', 'diagnostics', 'runtime') | ForEach-Object {
    (Resolve-Path -LiteralPath (Join-Path $packageRoot $_)).Path + [IO.Path]::DirectorySeparatorChar
}

$core = @()
$app = @()
foreach ($source in Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File) {
    $isCore = $false
    foreach ($module in $modules) {
        if ($source.FullName.StartsWith($module, [StringComparison]::OrdinalIgnoreCase)) {
            $isCore = $true
            break
        }
    }
    if ($isCore) { $core += $source.FullName } else { $app += $source.FullName }
}

# Share the output directory with the established dex/packaging steps.
# An empty classpath prevents the core from depending on old app classes.
$emptyClasspath = New-Item -ItemType Directory -Path "$ClassesDir.empty-classpath"
try {
    & $Javac '-encoding' 'UTF-8' '--release' '17' '-classpath' $emptyClasspath.FullName '-d' $ClassesDir @core
    if ($LASTEXITCODE -ne 0) { throw 'U-AVF core compilation failed' }
} finally {
    Remove-Item -LiteralPath $emptyClasspath.FullName
}

$classpath = $AndroidJar + [IO.Path]::PathSeparator + $ClassesDir
& $Javac '-encoding' 'UTF-8' '-source' '17' '-target' '17' '-classpath' $classpath '-d' $ClassesDir @app @GeneratedSources
if ($LASTEXITCODE -ne 0) { throw 'U-AVF Android compilation failed' }
