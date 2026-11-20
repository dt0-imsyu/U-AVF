"""Offline package only. Never stages a device, imports trust, or installs a driver."""
import argparse
import hashlib
import shutil
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('source', type=Path)
p.add_argument('output', type=Path)
a = p.parse_args()
assert not a.output.exists(), 'Preserve package evidence; choose a new output directory'
dll = a.source / 'IddSampleDriver.dll'
assert dll.is_file() and (a.source / 'DriverProof.cpp').is_file()
text = (a.source / 'IddSampleDriver.inf').read_text(encoding='utf-8')
assert 'Root\\IddSampleDriver' in text and '$ARCH$' in text
text = text.replace('$ARCH$', 'arm64').replace('$UMDFVERSION$', '2.25.0')
text = text.replace('DriverVer= ; TODO: set DriverVer in stampinf property pages',
                    'DriverVer=10/13/2026,0.0.1.0')
# Match only our dedicated software device, never BasicDisplay or a generic root ID.
text = text.replace('Root\\IddSampleDriver', 'Root\\UavfIddSourceProbe')
text = text.replace('IddSampleDriverGroup', 'UavfIddSourceProbeGroup')
text = text.replace('%DeviceName%=MyDevice_Install, IddSampleDriver',
                    '%DeviceName%=MyDevice_Install, UavfIddSourceProbe')
text = text.replace('"<Your manufacturer name>"', '"U-AVF experimental"')
text = text.replace('"IddSampleDriver Installation Disk"', '"U-AVF source probe"')
text = text.replace('"IddSampleDriver Device"', '"U-AVF isolated source probe"')
assert '$ARCH$' not in text and '$UMDFVERSION$' not in text
# Preserve upstream extension registration; runtime version/admission is NOT proven
# by an INF/catalog check. The linked driver requires IddCx 1.6 and UMDF 2.25.
assert 'UmdfExtensions = IddCx0102' in text
a.output.mkdir(parents=True)
(a.output / 'UavfIddSourceProbe.inf').write_text(text, encoding='utf-8')
shutil.copyfile(dll, a.output / dll.name)
shutil.copyfile(a.source / 'LICENSE.MS-PL', a.output / 'LICENSE.MS-PL')
print('OFFLINE_PACKAGE_ONLY; NOT_SIGNED_NOT_INSTALLED_NOT_FPS_PASS')
print('DLL_SHA256=' + hashlib.sha256(dll.read_bytes()).hexdigest())
