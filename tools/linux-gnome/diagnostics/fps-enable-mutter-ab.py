"""One-shot 4052 override; original bridge/boot/disk untouched."""
from pathlib import Path
import os
import subprocess
import urllib.request

if os.geteuid() != 0:
    raise RuntimeError('Ubuntu sudo required')
directory = Path('/var/tmp/uavf-mutter-ab-20261001')
dropin = Path('/etc/systemd/system/winavf-frame@.service.d/91-uavf-mutter-ab.conf')
if directory.exists() or dropin.exists():
    raise RuntimeError('Refusing to overwrite a retained experiment')
directory.mkdir(mode=0o755)
for name in ('fps-mutter-stream.py', 'uavf-fast-delta.py', 'fps-scraper-ab.py'):
    data = urllib.request.urlopen('http://10.159.93.215:39054/' + name, timeout=5).read()
    compile(data, name, 'exec')
    (directory / name).write_bytes(data)
wrapper = '''import importlib.util, os, subprocess, threading, pwd
from pathlib import Path
spec = importlib.util.spec_from_file_location('bridge', '/usr/local/sbin/winavf-frame-bridge.py')
bridge = importlib.util.module_from_spec(spec); spec.loader.exec_module(bridge)
env = bridge.find_display(True)
if not env: raise RuntimeError('No authenticated GNOME')
uid = int(env['XDG_RUNTIME_DIR'].rsplit('/', 1)[1]); user = pwd.getpwuid(uid).pw_name
dropin = Path('/etc/systemd/system/winavf-frame@.service.d/91-uavf-mutter-ab.conf')
if dropin.exists(): dropin.unlink()
subprocess.run(['systemctl', 'daemon-reload'], check=True)
threading.Thread(target=bridge.input_loop, daemon=True).start()
keys = ('DISPLAY', 'XAUTHORITY', 'XDG_RUNTIME_DIR', 'DBUS_SESSION_BUS_ADDRESS', 'HOME')
command = ['runuser', '-u', user, '--', 'env'] + [k+'='+env[k] for k in keys]
command += ['/usr/bin/python3', '/var/tmp/uavf-mutter-ab-20261001/fps-mutter-stream.py']
try:
    subprocess.run(['systemctl', 'stop', 'winavf-xvnc.service'], check=True)
    result = subprocess.run(command, stdin=subprocess.DEVNULL, timeout=48)
finally:
    subprocess.run(['systemctl', 'start', 'winavf-xvnc.service'])
if result.returncode != 0: raise RuntimeError('Capture failed; close this connection safely')
os.execv('/usr/bin/python3', ['python3', '/usr/local/sbin/winavf-frame-bridge.py'])
'''
compile(wrapper, 'wrapper.py', 'exec')
(directory / 'wrapper.py').write_text(wrapper)
dropin.parent.mkdir(parents=True, exist_ok=True)
dropin.write_text('[Service]\nExecStart=\nExecStart=/usr/bin/python3 ' + str(directory / 'wrapper.py') + '\n')
subprocess.run(['systemctl', 'daemon-reload'], check=True)
print('MUTTER_AB=PREPARED ONE_SHOT; original bridge restored after bounded run', flush=True)
