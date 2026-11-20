"""Read-only SurfaceFlinger ring sampling for the fixed H.264 interval."""
import json, re, subprocess, sys, time
from pathlib import Path

adb=r'C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
output=Path(sys.argv[1])
samples=[]
names=[]
next_discovery=0
deadline=time.monotonic()+42
while time.monotonic()<deadline:
    if not names or time.monotonic()>=next_discovery:
        listing=subprocess.check_output([adb,'shell','dumpsys','SurfaceFlinger','--list'],text=True)
        names=re.findall(r'RequestedLayerState\{([^\n]*SurfaceView\[com.example.winavf[^\n]*?\(BLAST\)#\d+)(?: parentId=|\})',listing)
        next_discovery=time.monotonic()+5
    row={'host_monotonic_ns':time.monotonic_ns(),'layers':{}}
    for name in names:
        for candidate in (name,):
            command='dumpsys SurfaceFlinger --latency "'+candidate+'"'
            row['layers'][candidate]=subprocess.check_output([adb,'shell',command],text=True)
    samples.append(row)
    # Cache layer discovery and avoid serializing the entire history on each
    # sample: capture rings before 128 entries can be overwritten at 60 FPS.
    time.sleep(.1)
output.write_text(json.dumps(samples,indent=2))
