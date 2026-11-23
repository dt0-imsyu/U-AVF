"""Update only session-policy scripts in copied production prefixes; never user disks."""
import argparse
import hashlib
import importlib.machinery
import json
from pathlib import Path
import subprocess
import sys
import zipfile
from pyfatfs.PyFatFS import PyFatFS

p = argparse.ArgumentParser()
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--installer-display', action='store_true',
               help='Refresh GDM discovery in the three Ubuntu installation prefixes')
a = p.parse_args()
repo = Path(__file__).resolve().parents[2]
guest = repo/'tools/linux-gnome/guest'
cpio = importlib.machinery.SourceFileLoader('cpio', str(repo/'tools/generic-ubuntu/compare-p7-clean-p9.py')).load_module()
def _find_zstd():
    """Locate zstd without pinning one developer's MSYS2 install path."""
    import os, shutil
    override = os.environ.get('UAVF_ZSTD')
    if override:
        return override
    found = shutil.which('zstd')
    if found:
        return found
    for candidate in (r'C:\msys64\usr\bin\zstd.exe', '/usr/bin/zstd', '/usr/local/bin/zstd'):
        if os.path.isfile(candidate):
            return candidate
    raise SystemExit('zstd not found; set UAVF_ZSTD or add it to PATH')


zstd = _find_zstd()
sha = lambda data: hashlib.sha256(data).hexdigest().upper()
a.output.mkdir(parents=True, exist_ok=False)
changes = { 'usr/local/sbin/'+name: guest/name for name in
            ('uavf-install-runtime.sh', 'uavf-encoded-display.py', 'uavf-clipboard.py') }
installer_assets = {
    'assets/uavf-install-platform-prefix-gdm-share.img',
    'assets/uavf-install-platform-mavk-r2.img',
    'assets/uavf-install-platform-plasma-r2.img',
}
display_bridge_sources = {
    'scripts/init-premount/winavf-frame-bridge.py': guest/'winavf-frame-bridge.py',
    'usr/local/sbin/winavf-frame-bridge': guest/'winavf-frame-bridge.py',
    'usr/local/sbin/winavf-frame-bridge.py': guest/'winavf-frame-bridge.py',
}
if a.installer_display:
    changes = {
        **display_bridge_sources,
        'usr/local/sbin/uavf-encoded-display.py': guest/'uavf-encoded-display.py',
    }
report = []
replacements = {}
with zipfile.ZipFile(a.apk) as archive:
    names = [name for name in archive.namelist() if name.startswith('assets/') and name.endswith('.img')]
    if a.installer_display:
        names = [name for name in names if name in installer_assets]
        if set(names) != installer_assets:
            raise SystemExit('Missing Ubuntu installer prefixes')
    for index, name in enumerate(names):
        source = a.output/('source-%d.img' % index)
        source.write_bytes(archive.read(name))
        if source.stat().st_size != 128*1024**2:
            raise SystemExit('Not a production prefix: '+name)
        with PyFatFS(str(source), offset=1048576, read_only=True) as fat:
            original = fat.readbytes('/CASPER/INITRD')
        early = cpio.early_end(original)
        before, _ = cpio.parse_cpio(subprocess.run([zstd, '-d', '-q', '-c'],
            input=original[early:], capture_output=True, check=True).stdout)
        candidate_changes = changes
        if 'usr/local/sbin/uavf-encoded-display.py' in before:
            candidate_changes = {**changes, **display_bridge_sources}
        selected = {key: path for key, path in candidate_changes.items()
                    if key in before and before[key] != path.read_bytes()}
        if a.installer_display:
            if 'usr/local/sbin/uavf-encoded-display.py' not in before or not any(
                    key in before for key in changes if 'winavf-frame-bridge' in key):
                raise SystemExit('Missing installed display sources in ' + name)
        if not selected:
            continue
        initrd = a.output/('policy-%d.initrd' % index)
        saved = a.output/('original-%d.initrd' % index)
        saved.write_bytes(original)
        cmd = [sys.executable, str(repo/'tools/generic-ubuntu/repack-p7-frame-bridge.py'),
               '--source', str(saved), '--output', str(initrd), '--zstd', zstd]
        for key, path in selected.items():
            cmd += ['--replace-file', key+'='+str(path)]
        subprocess.run(cmd, check=True)
        updated = initrd.read_bytes()
        assert updated[:early] == original[:early], 'Early CPIO changed'
        after, _ = cpio.parse_cpio(subprocess.run([zstd, '-d', '-q', '-c'],
            input=updated[early:], capture_output=True, check=True).stdout)
        actual = {key for key in set(before)|set(after) if before.get(key) != after.get(key)}
        assert actual == set(selected), 'Unexpected initrd changes'
        target = a.output/Path(name).name
        subprocess.run([sys.executable, str(repo/'tools/linux-gnome/replace-esp-initrd.py'),
                        str(source), str(initrd), str(target),
                        '--source-sha256', sha(source.read_bytes()),
                        '--initrd-sha256', sha(updated)], check=True)
        # Historical fallback prefixes deliberately contain older unrelated
        # hooks. Verify the exact allowed delta, not upgrade every hook to HEAD.
        assert all(after[key] == path.read_bytes() for key, path in selected.items())
        data = target.read_bytes()
        replacements[name] = target
        report.append(dict(asset=name, prefix_sha256=sha(data),
                           esp_sha256=sha(data[1048576:127*1024**2]),
                           changed_files=sorted(actual), early_cpio_preserved=True))
        if a.installer_display:
            source.unlink()
            saved.unlink()
            initrd.unlink()
    assert len(replacements) == 3, 'Expected installed/Live/installer production prefixes'
    base = a.output/'account-policy-base.apk'
    with zipfile.ZipFile(base, 'w') as output:
        for item in archive.infolist():
            if item.filename.startswith('META-INF/'):
                continue
            output.writestr(item, replacements[item.filename].read_bytes()
                            if item.filename in replacements else archive.read(item))
(a.output/'report.json').write_text(json.dumps(dict(result='PASS', platforms=report,
    official_iso_touched=False, installed_disks_touched=False, base_apk=str(base)), indent=2))
print('ACCOUNT_POLICY_BASE=PASS')
print(json.dumps(report, indent=2))
