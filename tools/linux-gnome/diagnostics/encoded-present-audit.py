"""Read-only installed Xorg/VirGL presentation inventory."""
import json
from pathlib import Path
import subprocess as S
import urllib.request

report = {'kind': 'read-only-xorg-present-audit'}
for name, args in {
    'kernel': ['uname', '-a'],
    'xorg_version': ['Xorg', '-version'],
    'xorg_process': ['ps', '-eo', 'pid,args'],
    'packages': ['dpkg-query', '-W', 'xserver-xorg-core', 'libgl1-mesa-dri'],
    'connectors': ['ls', '-l', '/sys/class/drm'],
}.items():
    p = S.run(args, capture_output=True, text=True, timeout=5)
    text = p.stdout + p.stderr
    if name == 'xorg_process': text = '\n'.join(l for l in text.splitlines() if 'Xorg' in l or 'gnome-shell' in l)
    report[name] = {'exit': p.returncode, 'text': text}
report['logs'] = {}
for path in [Path('/var/log/Xorg.0.log'), *Path.home().glob('.local/share/xorg/Xorg.*.log')]:
    try: report['logs'][str(path)] = path.read_text()[-100000:]
    except OSError as exc: report['logs'][str(path)] = repr(exc)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report', data=json.dumps(report).encode(), method='POST'), timeout=10).read()
print('PRESENT_AUDIT_SAVED', flush=True)
