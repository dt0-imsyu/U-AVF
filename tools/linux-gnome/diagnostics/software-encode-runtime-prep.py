import json, subprocess, urllib.request
from pathlib import Path
report={}
for unit in ('winavf-frame-bridge.service','winavf-xvnc.service'):
    p=subprocess.run(['systemctl','show',unit,'-p','ControlGroup','-p','MainPID','-p','ActiveState'],capture_output=True,text=True)
    report[unit]={'properties':p.stdout,'processes':[]}
    for l in p.stdout.splitlines():
        if l.startswith('ControlGroup=') and l.split('=',1)[1]:
            group=Path('/sys/fs/cgroup'+l.split('=',1)[1])
            for path in group.rglob('cgroup.procs'):
                for pid in path.read_text().split():
                    try: report[unit]['processes'].append({'pid':int(pid),'command':Path('/proc/'+pid+'/cmdline').read_bytes().replace(b'\0',b' ').decode()})
                    except OSError: pass
data=json.dumps(report,indent=2).encode()
print(data.decode())
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=data,method='POST'),timeout=15).read()
