#!/usr/bin/env python3
"""Explicit reversible installed-runtime A/B; never replaces the GOOD bridge."""
from pathlib import Path
import hashlib
import os
import subprocess
import sys

directory = Path('/var/tmp/uavf-lz4-ab-20261001')
dropin = Path('/etc/systemd/system/winavf-frame@.service.d/90-uavf-lz4-ab.conf')
if os.geteuid() != 0:
    raise RuntimeError('sudo required inside Ubuntu, not Android root')
if '--rollback' in sys.argv:
    expected = '[Service]\nExecStart=\nExecStart=/usr/bin/python3 /var/tmp/uavf-lz4-ab-20261001/bridge.py\n'
    if dropin.exists():
        if dropin.read_text() != expected:
            raise RuntimeError('Refusing to remove unknown override')
        dropin.unlink()
    subprocess.check_call(['systemctl', 'daemon-reload'])
    print('LZ4_AB=ROLLED_BACK; next display connection uses original bridge')
else:
    if directory.exists() or dropin.exists():
        raise RuntimeError('Existing experiment; refuse overwrite')
    original = Path('/usr/local/sbin/winavf-frame-bridge.py').read_text()
    marker = 'if __name__ == "__main__":'
    needle = 'if len(raw) < 4096:'
    if original.count(marker) != 1 or original.count(needle) != 1:
        raise RuntimeError('Exact known bridge structure required')
    codec = Path('/tmp/uavf-fast-delta.py').read_bytes()
    directory.mkdir(mode=0o755)
    directory.joinpath('original.sha256').write_text(hashlib.sha256(original.encode()).hexdigest())
    directory.joinpath('uavf-fast-delta.py').write_bytes(codec)
    injection = '''import importlib.util
_spec = importlib.util.spec_from_file_location('uavf_fast_delta', '/var/tmp/uavf-lz4-ab-20261001/uavf-fast-delta.py')
_fast_module = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_fast_module)
_fast_codec = _fast_module.FastDelta()
_original_record = wavf_record
def wavf_record(kind, flags, width, height, sequence, payload):
    if kind == 2 and flags == 0:
        flags, payload = _fast_codec.encode(payload)
    return _original_record(kind, flags, width, height, sequence, payload)
print('FRAME_DELTA_CODEC=LZ4_AB_V1', file=os.sys.stderr, flush=True)
'''
    candidate = original.replace(needle, 'if True:  # raw tiles, one native LZ4 block per delta')
    candidate = candidate.replace(marker, injection + '\n' + marker)
    compile(candidate, str(directory / 'bridge.py'), 'exec')
    directory.joinpath('bridge.py').write_text(candidate)
    dropin.parent.mkdir(parents=True, exist_ok=True)
    dropin.write_text('[Service]\nExecStart=\nExecStart=/usr/bin/python3 /var/tmp/uavf-lz4-ab-20261001/bridge.py\n')
    subprocess.check_call(['systemctl', 'daemon-reload'])
    print('LZ4_AB=PREPARED; existing stream unchanged; requires matching APK before next connection')
