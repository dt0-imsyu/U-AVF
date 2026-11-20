Add-Type -AssemblyName System.Windows.Forms,System.Drawing
$f=New-Object Windows.Forms.Form
$f.FormBorderStyle='None'
$f.WindowState='Maximized'
$s=[Diagnostics.Stopwatch]::StartNew()
$t=New-Object Windows.Forms.Timer
$t.Interval=1
$script:n=0
$f.add_Paint({$script:n++;$_.Graphics.DrawString("U-AVF full repaint $n / $($s.ElapsedMilliseconds) ms",[Drawing.SystemFonts]::DefaultFont,[Drawing.Brushes]::White,20,80)})
$t.add_Tick({if($s.ElapsedMilliseconds -gt 30000){$t.Stop();$f.Close()}else{$v=[int]($s.ElapsedMilliseconds%256);$f.BackColor=[Drawing.Color]::FromArgb($v,128,255-$v)}})
$t.Start()
[Windows.Forms.Application]::Run($f)
$t.Dispose()
$f.Dispose()
