"""Verify release payload identity and stage it without installing/publishing."""
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--apk',type=Path,required=True)
p.add_argument('--baseline',type=Path,required=True)
p.add_argument('--kit',type=Path,required=True)
p.add_argument('--aapt2',type=Path,required=True)
p.add_argument('--version-name',default='0.1.0-rc.1')
p.add_argument('--version-code',type=int,default=1)
p.add_argument('--filename')
a=p.parse_args()
repo=Path(__file__).resolve().parents[2]
badging=subprocess.run([str(a.aapt2),'dump','badging',str(a.apk)],capture_output=True,text=True,check=True).stdout
assert f"versionCode='{a.version_code}'" in badging and f"versionName='{a.version_name}'" in badging
assert 'application-debuggable' not in badging
xml=subprocess.run([str(a.aapt2),'dump','xmltree','--file','AndroidManifest.xml',str(a.apk)],capture_output=True,text=True,check=True).stdout
debug_line=next(line for line in xml.splitlines() if 'android:debuggable' in line)
assert '0x0' in debug_line,debug_line
preserved=[]
with zipfile.ZipFile(a.baseline) as baseline,zipfile.ZipFile(a.apk) as release:
    for entry in baseline.infolist():
        if not entry.filename.startswith(('assets/','lib/')) or entry.is_dir():continue
        original=baseline.read(entry.filename); current=release.read(entry.filename)
        assert current==original,'Changed tested payload: '+entry.filename
        preserved.append(entry.filename)
candidate=a.kit/'candidate';internal=a.kit/'internal'
development=candidate/'U-AVF-preview-2026-10-03.apk'
if development.exists():shutil.move(development,internal/'tablet-tested-development-reference.apk')
assert not a.filename or Path(a.filename).name==a.filename
destination=candidate/(a.filename or a.apk.name)
if destination.exists():
    previous_hash=hashlib.sha256(destination.read_bytes()).hexdigest()[:16]
    shutil.copy2(destination,internal/('previous-candidate-'+previous_hash+'.apk'))
shutil.copy2(a.apk,destination)
shutil.copy2(repo/'docs/release-preparation/RELEASE_BUILD.md',candidate/'RELEASE_BUILD.md')
digest=lambda path:hashlib.sha256(path.read_bytes()).hexdigest().upper()
report={'version_name':a.version_name,'version_code':a.version_code,'debuggable':False,
        'payloads_identical':preserved,'apk_sha256':digest(destination),
        'publication':'HOLD','installed_on_user_device':False,
        'new_signature_requires_migration_from_debug_app':True}
(internal/'release-build-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
(internal/'release-manifest.txt').write_text(xml,encoding='utf-8')
checksums=candidate/'SHA256SUMS'
checksums.write_text(''.join(digest(path)+'  '+path.relative_to(candidate).as_posix()+'\n'
                    for path in sorted(candidate.rglob('*')) if path.is_file() and path!=checksums),encoding='ascii')
print('RELEASE_MODE=PASS')
print('TESTED_ASSETS_AND_NATIVE_LIBRARIES=PASS ('+str(len(preserved))+')')
print('APK_SHA256='+digest(destination))
print('PUBLICATION=HOLD')
