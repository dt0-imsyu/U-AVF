"""Read-only capture profiler inventory; no VM/service/display mutations."""
import json
import shutil
import subprocess
import urllib.request
from pathlib import Path

report={'kind':'capture-profile-inventory','tools':{}}
for name in ('perf','strace','gdb','bpftrace','sysprof-cli','nm','objdump'):
    report['tools'][name]=shutil.which(name)
for name in ('kernel/perf_event_paranoid','kernel/yama/ptrace_scope'):
    path=Path('/proc/sys')/name
    report[name]=path.read_text().strip() if path.exists() else None
report['shell_libraries']=[]
for proc in Path('/proc').iterdir():
    if not proc.name.isdigit(): continue
    try:
        if proc.joinpath('comm').read_text().strip()!='gnome-shell': continue
        report['shell_pid']=int(proc.name)
        report['shell_libraries']=sorted({line.split()[-1] for line in proc.joinpath('maps').read_text().splitlines()
            if any(name in line for name in ('libmutter','libmutter-cogl','libsysprof','virgl','dri.so'))})
        break
    except (OSError,ValueError): pass
query=subprocess.run(['gdbus','introspect','--session','--dest','org.gnome.Shell',
                      '--object-path','/org/gnome/Sysprof3/Profiler'],capture_output=True,text=True,timeout=10)
report['sysprof_dbus']={'exit':query.returncode,'stdout':query.stdout[:12000],'stderr':query.stderr[:2000]}
data=json.dumps(report,indent=2).encode()
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=data,method='POST'),timeout=15).read()
print('CAPTURE_PROFILE_INVENTORY_SAVED',flush=True)
