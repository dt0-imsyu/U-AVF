"""Re-arm the retained one-shot only; never replace production bridge."""
from pathlib import Path
import os
import subprocess
import signal
import sys
import urllib.request

if os.geteuid() != 0:
    raise RuntimeError('Ubuntu sudo required')
directory = Path('/var/tmp/uavf-mutter-ab-20261001')
dropin = Path('/etc/systemd/system/winavf-frame@.service.d/91-uavf-mutter-ab.conf')
if dropin.exists():
    raise RuntimeError('An override is already armed')
for name in ('wrapper.py', 'fps-mutter-stream.py', 'uavf-fast-delta.py', 'fps-scraper-ab.py'):
    data = (directory / name).read_bytes()
    compile(data, name, 'exec')
wrapper = (directory / 'wrapper.py').read_text()
if 'timeout=48' not in wrapper or 'dropin.unlink()' not in wrapper:
    raise RuntimeError('Not the bounded self-restoring wrapper')
if '--controlled-ab' in sys.argv:
    # Isolated wrapper only: extend the bounded timeout for two 20s phases.
    wrapper = wrapper.replace('timeout=48', 'timeout=65').replace(
        "command += ['/usr/bin/python3'", "command += ['UAVF_CAPTURE_CONTROLLED_AB=1', '/usr/bin/python3'")
    compile(wrapper, 'controlled-wrapper.py', 'exec')
    (directory / 'controlled-wrapper.py').write_text(wrapper)
if '--async-write' in sys.argv:
    wrapper = wrapper.replace("command += ['/usr/bin/python3'",
                              "command += ['UAVF_CAPTURE_ASYNC_WRITE=1', '/usr/bin/python3'")
    compile(wrapper, 'async-wrapper.py', 'exec')
    (directory / 'async-wrapper.py').write_text(wrapper)
# Only replace this explicitly diagnostic helper, not any production file.
candidate = urllib.request.urlopen('http://10.159.93.215:39054/fps-mutter-stream.py', timeout=5).read()
compile(candidate, 'fps-mutter-stream.py', 'exec')
(directory / 'fps-mutter-stream.py').write_bytes(candidate)
dropin.parent.mkdir(parents=True, exist_ok=True)
with dropin.open('x') as stream:
    name = 'controlled-wrapper.py' if '--controlled-ab' in sys.argv else 'wrapper.py'
    if '--async-write' in sys.argv:
        name = 'async-wrapper.py'
    stream.write('[Service]\nExecStart=\nExecStart=/usr/bin/python3 ' + str(directory / name) + '\n')
subprocess.run(['systemctl', 'daemon-reload'], check=True)
print('MUTTER_AB=REARMED ONE_SHOT', flush=True)
if '--disconnect-current' in sys.argv:
    # Only exact production frame-bridge processes, never GNOME/user applications.
    # Closing their accepted socket permits a fresh systemd instance to read the
    # newly armed drop-in. The Android receiver is explicitly reconnected next.
    for process in Path('/proc').iterdir():
        if not process.name.isdigit():
            continue
        try:
            args = (process / 'cmdline').read_bytes().split(b'\0')
            if len(args) > 1 and args[1] == b'/usr/local/sbin/winavf-frame-bridge.py':
                os.kill(int(process.name), signal.SIGTERM)
                print('DISCONNECTED_FRAME_PID=' + process.name, flush=True)
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            pass
