"""Exercise the exact early-command YAML updater without guest disks."""
import re, subprocess, sys, tempfile
from pathlib import Path
import yaml

guest = Path(__file__).resolve().parent / 'guest'
script = (guest / 'uavf-install-preflight.sh').read_text()
code = script.split("<<'PY' || fail AUTOINSTALL_ROOT_SIZE_UPDATE\n", 1)[1].split('\nPY\n', 1)[0]
base = (guest / 'uavf-autoinstall.yaml').read_text()
original = yaml.safe_load(base)
with tempfile.TemporaryDirectory() as directory:
    target = Path(directory) / 'autoinstall.yaml'
    for gib in (32, 48, 64, 96, 128, 256, 1024):
        target.write_text(base)
        size = gib * 1024**3
        result = subprocess.run([sys.executable, '-c', code, str(target), str(size)], capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        updated = yaml.safe_load(target.read_text())
        root = next(item for item in updated['autoinstall']['storage']['config'] if item['id'] == 'uavf-root')
        assert root['size'] == str(size) + 'B'
        root['size'] = '32G'
        assert updated == original, 'Changed a field outside root size'
        before = target.read_bytes()
        subprocess.run([sys.executable, '-c', code, str(target), str(size)], check=True, capture_output=True)
        assert target.read_bytes() == before, 'Not idempotent'
    for invalid in (0, 31 * 1024**3, 48 * 1024**3 + 1, 1025 * 1024**3):
        target.write_text(base)
        result = subprocess.run([sys.executable, '-c', code, str(target), str(invalid)], capture_output=True)
        assert result.returncode != 0 and target.read_text() == base
    target.write_text(base.replace('offset: 4102029312', 'offset: 0'))
    before = target.read_bytes()
    result = subprocess.run([sys.executable, '-c', code, str(target), str(48 * 1024**3)], capture_output=True)
    assert result.returncode != 0 and target.read_bytes() == before
print('ROOT_SIZE_SYNC=PASS (32/48/64/96/128/256/1024 GiB)')
print('ONLY_ROOT_SIZE_CHANGED=PASS; IDEMPOTENT=PASS; INVALID_INPUT_FAIL_CLOSED=PASS')
