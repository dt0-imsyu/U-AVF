"""Read-only verification of the unified encoder in the stock Ubuntu Live root."""
import hashlib
import json
import os
import pathlib
import subprocess
import urllib.request

report = {'mode': 'live', 'runtime': {}}
commands = {
    'units': ['systemctl', 'list-units', 'uavf-encoded*', '--no-pager'],
    'journal': ['sudo', '-n', 'journalctl', '-b', '--no-pager', '-n', '100', '-u', 'uavf-encoded@*'],
    'sessions': ['loginctl', 'list-sessions', '--no-pager'],
    'renderer': ['glxinfo', '-B'],
    'display_mode': ['xrandr', '--current'],
    'plugin': ['gst-inspect-1.0', 'x264enc'],
    'plugin_dependencies': ['ldd', '/usr/local/lib/uavf-encoded/plugins/libgstx264.so'],
    'disks': ['lsblk', '-o', 'NAME,SIZE,FSTYPE,PARTLABEL,MOUNTPOINTS'],
}
env = dict(os.environ, GST_PLUGIN_PATH='/usr/local/lib/uavf-encoded/plugins',
           LD_LIBRARY_PATH='/usr/local/lib/uavf-encoded/lib')
for key, command in commands.items():
    try:
        result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=15)
        report[key] = {'exit': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr}
    except (OSError, subprocess.TimeoutExpired) as exc:
        report[key] = {'error': str(exc)}
for p in pathlib.Path('/usr/local/lib/uavf-encoded').rglob('*'):
    if p.is_file():
        report['runtime'][str(p)] = {'bytes': p.stat().st_size,
                                     'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
version = pathlib.Path('/usr/local/lib/uavf-encoded/runtime-version')
report['version'] = version.read_text() if version.exists() else None
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
    data=json.dumps(report, indent=2).encode(), method='POST'), timeout=15).read()
print('ENCODED_LIVE_AUDIT_SAVED', flush=True)
