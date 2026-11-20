"""Fail release builds if Java integrity constants don't match packaged ESP."""
import argparse, hashlib, re, zipfile
from pathlib import Path

def verify(apk, repo, source=None, *, require_desktop_choices=False, report=None):
    java=source if source is not None else (repo/'android-app/src/com/example/winavf/UbuntuWorkspaceMedia.java').read_text(encoding='utf-8')
    disk=(repo/'android-app/src/com/example/winavf/PersistentUbuntuDisk.java').read_text(encoding='utf-8')
    def constant(name):
        return re.search(r'\b'+name+r'\s*=\s*"([^"]+)"',java).group(1)
    # Use the same explicit ranges as Android's updater, never "rest of file".
    assert re.search(r'ESP_PARTITION_OFFSET\s*=\s*MIB\s*;',disk), 'Unexpected ESP offset expression'
    offset=1024**2
    size=int(re.search(r'ESP_PARTITION_BYTES\s*=\s*(\d+)L?\s*\*\s*MIB',disk).group(1))*1024**2
    assert offset==1048576 and size==126*1024**2, 'Unexpected protected ESP contract'
    profiles={
        'H264': ('UBUNTU_INSTALL_PLATFORM_PREFIX_ASSET', 'UBUNTU_INSTALL_PLATFORM_PREFIX_SHA256',
                 'UBUNTU_INSTALL_PLATFORM_ESP_SHA256'),
        'GNOME_MAVK': ('UBUNTU_INSTALL_MAVK_PREFIX', 'UBUNTU_INSTALL_MAVK_PREFIX_SHA256',
                       'UBUNTU_INSTALL_MAVK_ESP_SHA256'),
        'PLASMA_MAVK': ('UBUNTU_INSTALL_PLASMA_PREFIX', 'UBUNTU_INSTALL_PLASMA_PREFIX_SHA256',
                        'UBUNTU_INSTALL_PLASMA_ESP_SHA256'),
    }
    results={}
    with zipfile.ZipFile(apk) as archive:
        names=archive.namelist()
        for profile, (asset_key, sha_key, esp_key) in profiles.items():
            asset='assets/'+constant(asset_key)
            count=names.count(asset)
            if count==0 and profile!='H264' and not require_desktop_choices:
                continue
            assert count==1, f'{profile}: expected exactly one {asset}'
            entry=archive.getinfo(asset)
            assert entry.file_size==128*1024**2, f'{profile}: installer prefix size mismatch'
            full, esp=hashlib.sha256(), hashlib.sha256()
            position=0
            with archive.open(entry) as stream:
                while block:=stream.read(1024**2):
                    full.update(block)
                    start=max(position,offset)
                    end=min(position+len(block),offset+size)
                    if start<end:
                        esp.update(block[start-position:end-position])
                    position+=len(block)
            assert full.hexdigest().upper()==constant(sha_key), f'{profile}: installer prefix SHA mismatch'
            esp_sha=esp.hexdigest().upper()
            assert esp_sha==constant(esp_key), f'{profile}: installer ESP range/SHA mismatch'
            results[profile]=esp_sha
        if 'PLASMA_MAVK' in results:
            assert 'GNOME_MAVK' in results, 'Plasma choice requires the GNOME Mavk prefix'
        if 'GNOME_MAVK' in results:
            for asset in ('assets/uavf-mavk-runtime.json', 'lib/arm64-v8a/libmavk_runtime_probe.so'):
                assert names.count(asset)==1 and archive.getinfo(asset).file_size>0, (
                    f'Mavk installer choices require {asset}')
    if report is not None:
        report.update(results)
    return results['H264']

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--test-regression',action='store_true')
    p.add_argument('--require-desktop-choices',action='store_true')
    a=p.parse_args();repo=Path(__file__).resolve().parents[2]
    report={}
    print('PACKAGED_INSTALLER_ESP_SHA=PASS '+verify(
        a.apk,repo,require_desktop_choices=a.require_desktop_choices,report=report))
    for profile, digest in report.items():
        print(f'PACKAGED_INSTALLER_PROFILE={profile} ESP_SHA256={digest}')
    if a.test_regression:
        source=(repo/'android-app/src/com/example/winavf/UbuntuWorkspaceMedia.java').read_text(encoding='utf-8')
        keys={'H264':'PLATFORM','GNOME_MAVK':'MAVK','PLASMA_MAVK':'PLASMA'}
        for profile in report:
            bad=re.sub(r'(UBUNTU_INSTALL_'+keys[profile]+r'_ESP_SHA256\s*=\s*")[^"]+',
                       r'\g<1>'+'0'*64,source)
            try:
                verify(a.apk,repo,bad)
            except AssertionError as error:
                if str(error)!=f'{profile}: installer ESP range/SHA mismatch':
                    raise
                print(f'REJECT_INCORRECT_INSTALLER_ESP_SHA={profile}')
            else:
                raise SystemExit(f'Regression: accepted incorrect {profile} ESP digest')
