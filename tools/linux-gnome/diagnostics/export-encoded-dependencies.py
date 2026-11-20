"""Read-only export of the exact installed x264 plugin, library and licenses."""
import base64, hashlib, json, pathlib, subprocess, urllib.request

report = {'kind': 'encoded-runtime-dependencies', 'files': {}, 'commands': {}}
for package in ('gstreamer1.0-plugins-ugly', 'libx264-164', 'python3-gi', 'gir1.2-gstreamer-1.0'):
    result = subprocess.run(['dpkg-query', '-W', package], capture_output=True, text=True)
    report['commands'][package] = {'exit': result.returncode, 'output': result.stdout + result.stderr}
paths = ('/usr/lib/aarch64-linux-gnu/gstreamer-1.0/libgstx264.so',
         '/usr/lib/aarch64-linux-gnu/libx264.so.164',
         '/usr/share/doc/gstreamer1.0-plugins-ugly/copyright',
         '/usr/share/doc/libx264-164/copyright')
for name in paths:
    path = pathlib.Path(name)
    data = path.read_bytes()
    report['files'][name] = {'sha256': hashlib.sha256(data).hexdigest(),
                           'base64': base64.b64encode(data).decode()}
    if '.so' in name:
        result = subprocess.run(['ldd', name], capture_output=True, text=True)
        report['commands']['ldd:' + name] = {'exit': result.returncode, 'output': result.stdout + result.stderr}
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
    data=json.dumps(report).encode(), method='POST'), timeout=20).read()
print('ENCODED_DEPENDENCIES_EXPORTED', flush=True)
