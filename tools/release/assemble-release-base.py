"""Preserve tested APK payloads; generate release-only manifest and package base."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--base',required=True,type=Path)
p.add_argument('--output',required=True,type=Path)
p.add_argument('--aapt2',required=True,type=Path)
p.add_argument('--android-jar',required=True,type=Path)
p.add_argument('--version-name',default='1.0')
p.add_argument('--version-code',type=int,default=14)
p.add_argument('--qa-isolated',action='store_true',help='Private side-by-side acceptance app; never distribute')
a=p.parse_args()
repo=Path(__file__).resolve().parents[2]
subprocess.run([__import__('sys').executable,str(Path(__file__).with_name('verify-installer-payload.py')),
               '--apk',str(a.base)],check=True)
a.output.mkdir(parents=True,exist_ok=False)
android='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
manifest=ET.parse(repo/'android-app/AndroidManifest.xml')
root=manifest.getroot()
assert a.version_code>0 and re.fullmatch(r'[A-Za-z0-9._-]+',a.version_name),'Invalid version'
root.set(android+'versionCode',str(a.version_code));root.set(android+'versionName',a.version_name)
app=root.find('application');app.set(android+'debuggable','false');app.set(android+'allowBackup','false')
qa_args=[]
if a.qa_isolated:
    # Keep resource/source namespace, isolate Android data/VM ownership.
    original_package=root.get('package')
    for component in app:
        name=component.get(android+'name','')
        if name.startswith('.'):component.set(android+'name',original_package+name)
    app.set(android+'label','U-AVF Release QA')
    qa_args=['--rename-manifest-package','com.example.winavf.releaseqa']
manifest.write(a.output/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
resources=subprocess.run([str(a.aapt2),'dump','resources',str(a.base)],capture_output=True,text=True,check=True).stdout
ids=re.findall(r'resource (0x[0-9a-fA-F]+) ([\w]+/[\w]+)',resources)
assert ids,'No stable resource IDs'
(a.output/'stable-ids.txt').write_text(''.join('com.example.winavf:'+name+' = '+number+'\n' for number,name in ids),encoding='utf-8')
subprocess.run([str(a.aapt2),'compile','--dir',str(repo/'android-app/res'),'-o',str(a.output/'resources.zip')],check=True)
subprocess.run([str(a.aapt2),'link','-I',str(a.android_jar),'--manifest',str(a.output/'AndroidManifest.xml'),
               '--stable-ids',str(a.output/'stable-ids.txt'),'--auto-add-overlay','-R',str(a.output/'resources.zip'),
               '--java',str(a.output/'generated'),'--min-sdk-version','36','--target-sdk-version','36',
               '-o',str(a.output/'resource-base.apk'),*qa_args],check=True)
new_resources=subprocess.run([str(a.aapt2),'dump','resources',str(a.output/'resource-base.apk')],capture_output=True,text=True,check=True).stdout
assert set(ids)==set(re.findall(r'resource (0x[0-9a-fA-F]+) ([\w]+/[\w]+)',new_resources)),'Resource ID changed'
preserved=[]
legal = {
    'LICENSE': repo/'LICENSE',
    'LICENSE-UAVF.txt': repo/'LICENSE-UAVF.txt',
    'LICENSE-APACHE-2.0.txt': repo/'LICENSE-APACHE-2.0.txt',
    'LICENSE_SCOPE.md': repo/'LICENSE_SCOPE.md',
    'PRIVACY.md': repo/'docs/release-preparation/PRIVACY.md',
}
for path in legal.values():
    assert path.is_file(), 'Missing release legal document: '+str(path)
notices_dir = repo/'android-app/assets/legal-notices'
assert notices_dir.is_dir(), 'Missing checked third-party notices'
notice_paths=sorted(notices_dir.glob('*.txt'))
assert notice_paths, 'No third-party notices'
notice_text='Third-party components retain their individual licenses. This is not a source offer.\n\n'
for path in notice_paths:
    notice_text+='===== '+path.name+' =====\n'+path.read_text(encoding='utf-8',errors='replace')+'\n\n'
with zipfile.ZipFile(a.base) as original,zipfile.ZipFile(a.output/'resource-base.apk') as resource,zipfile.ZipFile(a.output/'unsigned.apk','w') as result:
    replaced=set(resource.namelist())
    for entry in original.infolist():
        if entry.filename in replaced or entry.filename.startswith(('META-INF/','res/','assets/legal/')) or entry.filename=='classes.dex':continue
        data=original.read(entry.filename);result.writestr(entry,data)
        preserved.append({'path':entry.filename,'sha256':hashlib.sha256(data).hexdigest().upper()})
    for entry in resource.infolist():result.writestr(entry,resource.read(entry.filename))
    for name,path in legal.items():result.writestr('assets/legal/'+name,path.read_bytes())
    result.writestr('assets/legal/THIRD_PARTY_NOTICES.txt',notice_text.encode('utf-8'))
(a.output/'preserved-payloads.json').write_text(json.dumps(preserved,indent=2)+'\n',encoding='utf-8')
print('STABLE_RESOURCE_IDS=PASS')
print('PRESERVED_TESTED_PAYLOADS='+str(len(preserved)))
