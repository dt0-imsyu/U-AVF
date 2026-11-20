"""Read-only dependencies and install simulation for bounded encoder proof."""
import ctypes.util
import json
import shutil
import subprocess
import urllib.request
report = {'tools': {n: shutil.which(n) for n in ('gcc', 'cc', 'ffmpeg', 'pkg-config')},
          'libraries': {n: ctypes.util.find_library(n) for n in ('x264', 'openh264')},
          'commands': {}}
for key, cmd in [('cpu', ['lscpu']), ('storage', ['df', '-h', '/var/tmp']),
                 ('packages', ['apt-get', '-s', '--no-upgrade', '--no-install-recommends',
                               'install', 'libx264-dev', 'libopenh264-dev'])]:
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=20)
    report['commands'][key] = {'exit': p.returncode, 'output': p.stdout}
data = json.dumps(report, indent=2).encode()
print(data.decode(), flush=True)
print(urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
                        data=data, method='POST'), timeout=10).read().decode())
