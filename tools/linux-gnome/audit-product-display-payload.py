"""Fail closed if the product prefix ships runtime scripts older than source."""
import argparse
import hashlib
import importlib.machinery
import io
from pathlib import Path
import subprocess
import tarfile
from pyfatfs.PyFatFS import PyFatFS

p = argparse.ArgumentParser()
p.add_argument('prefix', type=Path)
args = p.parse_args()
here = Path(__file__).resolve().parent
m = importlib.machinery.SourceFileLoader('cpio', str(here.parent / 'generic-ubuntu' /
                                      'compare-p7-clean-p9.py')).load_module()
with PyFatFS(str(args.prefix), offset=1048576, read_only=True) as fat:
    data = fat.readbytes('/CASPER/INITRD')
files, _ = m.parse_cpio(subprocess.run([r'C:\msys64\usr\bin\zstd.exe', '-d', '-q', '-c'],
                                     input=data[m.early_end(data):], capture_output=True,
                                     check=True).stdout)
expected = {
    'scripts/init-premount/winavf-frame-bridge.py': 'winavf-frame-bridge.py',
    'usr/local/sbin/winavf-frame-bridge': 'winavf-frame-bridge.py',
    'scripts/init-bottom/99-winavf-vsock-systemd': '99-winavf-vsock-systemd',
    'usr/local/sbin/uavf-install-runtime.sh': 'uavf-install-runtime.sh',
    'usr/local/sbin/uavf-gdm-display-share.py': 'uavf-gdm-display-share.py',
    'usr/local/sbin/uavf-control.py': 'uavf-control.py',
    'usr/local/sbin/uavf-control.socket': 'uavf-control.socket',
    'usr/local/sbin/uavf-control@.service': 'uavf-control@.service',
    'usr/local/sbin/uavf-clipboard.py': 'uavf-clipboard.py',
    'usr/local/sbin/uavf-clipboard.socket': 'uavf-clipboard.socket',
    'usr/local/sbin/uavf-clipboard@.service': 'uavf-clipboard@.service',
    'usr/local/sbin/uavf-encoded-display.py': 'uavf-encoded-display.py',
    'usr/local/sbin/uavf-encoded.socket': 'uavf-encoded.socket',
    'usr/local/sbin/uavf-encoded@.service': 'uavf-encoded@.service',
    'usr/local/sbin/stage-uavf-encoded-runtime.sh': 'stage-uavf-encoded-runtime.sh',
    'usr/local/sbin/stage-uavf-safe-jit.sh': 'stage-uavf-safe-jit.sh',
    'usr/local/sbin/uavf-safe-jit.py': 'uavf-safe-jit.py',
}
for name, source in expected.items():
    if files.get(name) != (here / 'guest' / source).read_bytes():
        raise SystemExit('PRODUCT_RUNTIME_SOURCE_MISMATCH=' + name)
if files.get('usr/local/sbin/uavf-encoded-capture.py') != (here/'diagnostics/encoded-glx-pbo-probe.py').read_bytes():
    raise SystemExit('PRODUCT_RUNTIME_SOURCE_MISMATCH=encoded-capture')
import ast
for name in ('uavf-encoded-display.py','uavf-encoded-capture.py'):
    ast.parse(files['usr/local/sbin/'+name].decode())
for name, digest in {
    'libgstx264.so':'09b86bab85ea78835b0337e659278adc24503fba4b5226a5299cac5a34fa20ce',
    'libx264.so.164':'86f6821f969333395981f782145b102821462f635d9913903b4c7bd27850b491',
}.items():
    if hashlib.sha256(files.get('usr/local/sbin/'+name,b'')).hexdigest()!=digest:
        raise SystemExit('ENCODED_DEPENDENCY_HASH_MISMATCH='+name)
for name in ('GST_X264_COPYRIGHT','X264_COPYRIGHT'):
    assert files.get('usr/local/sbin/'+name), 'Missing encoder license notice'
for marker in (b'dbus-run-session -- gnome-session', b'LV:DISPLAY_SOURCE=UAVF_SESSION_X1'):
    if marker in files['scripts/init-bottom/99-winavf-vsock-systemd']:
        raise SystemExit('PRODUCT_LEGACY_DESKTOP_PRESENT')
addon_dir = 'usr/local/lib/uavf-installer/'
addon_name = 'mavk-installed-addon.tar.gz'
if addon_dir + addon_name in files:
    for name in ('uavf-install-preflight.sh', 'stage-uavf-installer-mavk.sh',
                 'uavf-plasma-session.py', 'uavf-plasma-install.sh', 'uavf-plasma-install.service'):
        if files.get('usr/local/sbin/' + name) != (here / 'guest' / name).read_bytes():
            raise SystemExit('INSTALLER_MAVK_SOURCE_MISMATCH=' + name)
    archive = files[addon_dir + addon_name]
    digest = hashlib.sha256(archive).hexdigest()
    if files.get(addon_dir + 'mavk-installed-addon.sha256') != f'{digest}  {addon_name}\n'.encode('ascii'):
        raise SystemExit('INSTALLER_MAVK_ARCHIVE_DIGEST_MISMATCH')
    profile = files.get(addon_dir + 'desktop-profile')
    if profile is not None:
        if profile != b'plasma-x11\n':
            raise SystemExit('INSTALLER_PLASMA_PROFILE_MISMATCH')
        print('INSTALLER_PLASMA_PROFILE=PASS')
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as package:
        checksums = package.extractfile('SHA256SUMS').read().decode('ascii').splitlines()
        expected = dict(line.split('  ', 1)[::-1] for line in checksums)
        if not {'installed-kwin','installed-kwin.conf'} <= expected.keys():
            raise SystemExit('INSTALLER_KWIN_WRAPPER_MISSING')
        entries = [entry for entry in package if entry.name != 'SHA256SUMS']
        if len(entries) != len(expected) or {entry.name for entry in entries} != expected.keys():
            raise SystemExit('INSTALLER_MAVK_ARCHIVE_MANIFEST_MISMATCH')
        for entry in entries:
            if not entry.isfile() or hashlib.sha256(package.extractfile(entry).read()).hexdigest() != expected[entry.name]:
                raise SystemExit('INSTALLER_MAVK_ARCHIVE_FILE_MISMATCH=' + entry.name)
    print('INSTALLER_MAVK_ARCHIVE=PASS')
print('PRODUCT_DISPLAY_PAYLOAD_SOURCE_EXACT=PASS')
print('LIVE_SINGLE_GDM_SESSION=PASS')
