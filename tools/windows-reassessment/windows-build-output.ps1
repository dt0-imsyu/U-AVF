function Assert-WindowsBuildOutput([string]$OutputDirectory,[string]$WorkspaceRoot,[long]$ReserveBytes=50GB) {
    $target=[IO.Path]::GetFullPath($OutputDirectory)
    $approved=@(((Join-Path $WorkspaceRoot 'build-logs')+'\'),'D:\UAVF-builds\','E:\U-AVF\artifacts\')
    if(-not (@($approved | Where-Object { $target.StartsWith($_,[StringComparison]::OrdinalIgnoreCase) }).Count)) {
        throw 'Use a new directory under project build-logs, D:\UAVF-builds or E:\U-AVF\artifacts'
    }
    if(Test-Path -LiteralPath $target){throw 'Preserve existing build artifacts'}
    $drive=[IO.Path]::GetPathRoot($target).Substring(0,1)
    if((Get-PSDrive -Name $drive).Free -lt $ReserveBytes){throw "Build drive reserve below $ReserveBytes bytes"}
    $ancestor=[IO.DirectoryInfo]::new([IO.Path]::GetDirectoryName($target))
    while($ancestor) {
        if($ancestor.Exists -and ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Do not redirect artifacts through a junction/symlink'
        }
        $ancestor=$ancestor.Parent
    }
    return $target
}
