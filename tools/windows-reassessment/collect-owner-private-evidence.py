"""Read only bounded evidence from the isolated diagnostic APK, never disk bytes.

ADB pull cannot read these app-private files; exec-out preserves binary KD data.
No socket connection, root, guest memory write or token collection.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('output', type=Path)
p.add_argument('--adb', required=True)
p.add_argument('--since', default='08:27:00', help='Today HH:MM:SS lower bound for host lifecycle evidence')
p.add_argument('--cid', type=int, default=2136, help='VM under test; never reuse an old CID as current evidence')
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=False)
result = {}
for name in ('owner-serial.bin', 'owner-decoded-host-tx.bin', 'service-crosvm.log',
             'ingress-stderr.txt', 'tailer-stderr.txt'):
    proc = subprocess.run([a.adb, 'exec-out', 'run-as', 'com.example.uavfwinpetest',
                           'cat', 'files/' + name], capture_output=True, timeout=30)
    if proc.returncode:
        result[name] = {'error': proc.stderr.decode(errors='replace')}
        continue
    if len(proc.stdout) > 16 * 1024 * 1024:
        raise RuntimeError('Evidence size bound exceeded: ' + name)
    (a.output / name).write_bytes(proc.stdout)
    result[name] = {'bytes': len(proc.stdout),
                    'sha256': hashlib.sha256(proc.stdout).hexdigest().upper()}
log = subprocess.run([a.adb, 'shell', 'logcat', '-d', '-b', 'all', '-v', 'threadtime'],
                     capture_output=True, timeout=30, check=True)
lines = [line for line in log.stdout.decode(errors='replace').splitlines()
         if ('crosvm' in line or 'virtmgr' in line or f'CID {a.cid}' in line)
         and len(line.split()) > 1 and line.split()[1] >= a.since]
(a.output / 'host-lifecycle.log').write_text('\n'.join(lines) + '\n', encoding='utf-8')
result['host_lifecycle_scope'] = {'cid': a.cid, 'since': a.since}
(a.output / 'manifest.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
print(json.dumps(result, indent=2))
