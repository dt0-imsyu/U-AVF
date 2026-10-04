"""Fail release builds if Java integrity constants don't match packaged ESP."""
import argparse, hashlib, re, zipfile
from pathlib import Path

def verify(apk, repo, source=None):
    java=source if source is not None else (repo/'android-app/src/com/example/winavf/MainActivity.java').read_text()
    disk=(repo/'android-app/src/com/example/winavf/PersistentUbuntuDisk.java').read_text()
    def constant(name):
        return re.search(r'\b'+name+r'\s*=\s*"([^"]+)"',java).group(1)
    # Use the same explicit ranges as Android's updater, never "rest of file".
    assert re.search(r'ESP_PARTITION_OFFSET\s*=\s*MIB\s*;',disk), 'Unexpected ESP offset expression'
    offset=1024**2
    size=int(re.search(r'ESP_PARTITION_BYTES\s*=\s*(\d+)L?\s*\*\s*MIB',disk).group(1))*1024**2
    assert offset==1048576 and size==126*1024**2, 'Unexpected protected ESP contract'
    with zipfile.ZipFile(apk) as archive:
        payload=archive.read('assets/'+constant('UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET'))
    assert len(payload)==128*1024**2, 'Installer prefix size mismatch'
    digest=lambda data:hashlib.sha256(data).hexdigest().upper()
    assert digest(payload)==constant('UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256'), 'Installer prefix SHA mismatch'
    assert digest(payload[offset:offset+size])==constant('UBUNTU_INSTALL_PLATFORM_ESP_SHA256'), 'Installer ESP range/SHA mismatch'
    return digest(payload[offset:offset+size])

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--test-regression',action='store_true')
    a=p.parse_args();repo=Path(__file__).resolve().parents[2]
    print('PACKAGED_INSTALLER_ESP_SHA=PASS '+verify(a.apk,repo))
    if a.test_regression:
        source=(repo/'android-app/src/com/example/winavf/MainActivity.java').read_text()
        bad=re.sub(r'(UBUNTU_INSTALL_PLATFORM_ESP_SHA256\s*=\s*")[^"]+',r'\g<1>11B716A8D712BE1FB058E91C2D4D2E036E9221756EC80B0A1337D330CBE130A9',source)
        try:verify(a.apk,repo,bad)
        except AssertionError:print('REJECT_INCORRECT_127M_ESP_SHA=PASS')
        else:raise SystemExit('Regression: accepted incorrect ESP range')
