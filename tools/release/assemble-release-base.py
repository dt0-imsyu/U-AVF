"""Preserve tested APK payloads; generate release-only manifest and package base."""
import argparse
import hashlib
import json
import re
import shutil
import struct
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
p.add_argument('--qa-debuggable',action='store_true',help='Enable run-as only for the isolated acceptance app')
p.add_argument('--mavk-runtime-dir',type=Path,help='Opt-in experimental Live prefix/manifest')
p.add_argument('--mavk-native-library',type=Path)
p.add_argument('--require-ubuntu-desktop-choices',action='store_true')
p.add_argument('--windows-display-broker',type=Path)
p.add_argument('--windows-setup-fat-library',type=Path)
p.add_argument('--windows-iso-library',type=Path,
               help='libuavf_windows_iso.so providing all five JNI methods; '
                    'overrides the copy inherited from the base APK')
p.add_argument('--udfread-library',type=Path,
               help='libudfread.so with the partition-number fix; overrides the '
                    'copy inherited from the base APK')
p.add_argument('--windows-wim-library',type=Path,
               help='ARM64 Windows WIM conversion and WinPE staging library')
p.add_argument('--windows-iso-boot-runtime',type=Path,help='Small WinPE runtime bundle for ISO-only Setup')
p.add_argument('--windows-guided-profile',type=Path,help='Directory containing the boot-file fingerprints of previously prepared Windows disks')
a=p.parse_args()
if a.qa_debuggable and not a.qa_isolated:
    p.error('--qa-debuggable requires --qa-isolated')
if bool(a.mavk_runtime_dir)!=bool(a.mavk_native_library):
    raise ValueError('Mavk guest and host must be supplied together')
mavk_entries={}
if a.mavk_runtime_dir:
    description=a.mavk_runtime_dir/'uavf-mavk-runtime.json'
    info=json.loads(description.read_text(encoding='utf-8'))
    prefix=a.mavk_runtime_dir/'p7-platform-prefix-mavk-live.img'
    assert info['codec_version']==3 and info['prefix_bytes']==134217728
    assert info['prefix_asset']==prefix.name and prefix.stat().st_size==134217728
    assert info['base_sha256']=='FE451CA13834DC5EDE3096EDC3AF1EF2894BFDECAA7801CED9E69EDB07447858'
    assert hashlib.sha256(prefix.read_bytes()).hexdigest().upper()==info['prefix_sha256']
    assert a.mavk_native_library.read_bytes()[:4]==b'\x7fELF'
    mavk_entries={'assets/'+prefix.name:prefix,'assets/'+description.name:description,
                  'lib/arm64-v8a/libmavk_runtime_probe.so':a.mavk_native_library}
repo=Path(__file__).resolve().parents[2]
windows_runtime='assets/windows-setup/iso-boot-runtime.zip'
windows_commands={'assets/windows-setup/'+name for name in
                  ('INSTALL.CMD','BASE.CMD','BOOTPREP.CMD','FIRSTBOT.CMD','DISPLAY.CMD')}
windows_entries={}
if a.windows_iso_boot_runtime:
    if a.windows_iso_boot_runtime.stat().st_size > 4 * 1024**2:
        p.error('The WinPE runtime bundle exceeds 4 MiB')
    windows_entries[windows_runtime]=a.windows_iso_boot_runtime.read_bytes()
if a.windows_wim_library:
    wim=a.windows_wim_library.read_bytes()
    if wim[:6]!=b'\x7fELF\x02\x01' or wim[18:20]!=b'\xb7\x00':
        p.error('The Windows WIM library must be Android ARM64')
    windows_entries['lib/arm64-v8a/libuavf_windows_wim.so']=wim
if a.windows_display_broker and not a.windows_setup_fat_library:
    p.error('A new Windows display broker requires the matching FAT library')
if a.windows_setup_fat_library:
    fat=a.windows_setup_fat_library.read_bytes()
    if fat[:6]!=b'\x7fELF\x02\x01' or fat[18:20]!=b'\xb7\x00':
        p.error('The Windows Setup FAT library must be Android ARM64')
    windows_entries['lib/arm64-v8a/libuavf_windows_setup_fat.so']=fat
if a.windows_display_broker:
    broker=a.windows_display_broker.read_bytes()
    if not (64<=len(broker)<=71168 and broker[:2]==b'MZ'):
        p.error('Invalid Windows display broker')
    pe_offset=struct.unpack_from('<I',broker,0x3c)[0]
    if broker[pe_offset:pe_offset+6]!=b'PE\0\0\x64\xaa':
        p.error('The Windows display broker must be ARM64')
    windows_tools=repo/'tools/windows-reassessment'
    def batch(name):
        return (windows_tools/name).read_bytes().replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')
    windows_entries.update({
        'assets/windows-setup/BASE.CMD':batch('provision-installed-base.cmd'),
        'assets/windows-setup/DISPLAY.CMD':batch('installed-display.cmd'),
        'assets/windows-setup/INSTALL.CMD':batch('install-uavf-workspace.cmd'),
        'assets/windows-setup/BROKER.EXE':broker,
        'assets/windows-setup/OFFLINE.XML':(windows_tools/'unattend-oobe-offline.xml').read_bytes(),
    })
if a.windows_guided_profile:
    with (a.windows_guided_profile/'guided-profile.txt').open('rb') as file:
        profile=file.read(131)
    if not re.fullmatch(rb'[0-9A-F]{64}\n[0-9A-F]{64}\n',profile):
        p.error('Invalid guided Windows Setup fingerprints')
    windows_entries['assets/windows-setup/guided-profile.txt']=profile
if a.windows_iso_library:
    # The base APK may carry an older libuavf_windows_iso.so that only exports
    # probeFd. WindowsInstallIso.installDigest() needs five JNI methods, so a
    # stale copy makes every ISO selection fail with UnsatisfiedLinkError.
    iso_bytes=a.windows_iso_library.read_bytes()
    if iso_bytes[:6]!=b'\x7fELF\x02\x01':
        p.error('The Windows ISO library must be Android ARM64')
    windows_entries['lib/arm64-v8a/libuavf_windows_iso.so']=iso_bytes
if a.udfread_library:
    # libudfread 1.2.0 pins the partition number to 0, but retail Windows ARM64
    # ISOs declare partition 2989, so every selection failed with
    # "Could not read the UDF filesystem in this ISO."
    udf_bytes=a.udfread_library.read_bytes()
    if udf_bytes[:6]!=b'\x7fELF\x02\x01':
        p.error('The UDF reader library must be Android ARM64')
    windows_entries['lib/arm64-v8a/libudfread.so']=udf_bytes
installer_check=[__import__('sys').executable,str(Path(__file__).with_name('verify-installer-payload.py')),
                 '--apk',str(a.output/'unsigned.apk')]
if a.require_ubuntu_desktop_choices:
    installer_check.append('--require-desktop-choices')
a.output.mkdir(parents=True,exist_ok=False)
android='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android','http://schemas.android.com/apk/res/android')
manifest=ET.parse(repo/'android-app/AndroidManifest.xml')
root=manifest.getroot()
assert a.version_code>0 and re.fullmatch(r'[A-Za-z0-9._-]+',a.version_name),'Invalid version'
root.set(android+'versionCode',str(a.version_code));root.set(android+'versionName',a.version_name)
app=root.find('application')
app.set(android+'debuggable','true' if a.qa_debuggable else 'false')
app.set(android+'allowBackup','false')
qa_args=[]
if a.qa_isolated:
    # Keep resource/source namespace, isolate Android data/VM ownership.
    original_package=root.get('package')
    for component in app:
        for attribute in ('name','targetActivity'):
            name=component.get(android+attribute,'')
            if name.startswith('.'):component.set(android+attribute,original_package+name)
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
linked_ids=set(re.findall(r'resource (0x[0-9a-fA-F]+) ([\w]+/[\w]+)',new_resources))
assert set(ids)<=linked_ids,'Existing resource ID changed'
added_resources={name for _,name in linked_ids-set(ids)}
assert added_resources<={'drawable/ic_uavf_launcher_'+color for color in ('blue','grey','purple')}, 'Unexpected resources added'
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
    windows_assets=set(original.namelist()) | windows_entries.keys()
    if (windows_runtime in windows_assets or a.windows_display_broker) and not windows_commands <= windows_assets:
        p.error('Missing Windows Setup commands: '+', '.join(sorted(windows_commands - windows_assets)))
    replaced=set(resource.namelist())
    product_ui = {
        'assets/product-ui/v12/'+name: repo/'android-app/assets/product-ui/v12'/name
        for name in ('ubuntu-workspace.webp','windows-workspace.webp')
    }
    for name in ['dashboard-wallpaper.png','U-AVF ICON.png'] + [
            f'Themes and App icons/{kind} {color}.png'
            for kind in ('Icon','Wallpaper') for color in ('blue','grey','purple')]:
        product_ui['assets/product-ui/'+name] = repo/'android-app/assets/product-ui'/name
    for entry in original.infolist():
        if entry.filename=='assets/windows-setup/guided.delta':
            continue
        if entry.filename in replaced or entry.filename in product_ui or entry.filename in mavk_entries or entry.filename in windows_entries or entry.filename.startswith(('META-INF/','res/','assets/legal/')) or entry.filename=='classes.dex':continue
        data=original.read(entry.filename);result.writestr(entry,data)
        preserved.append({'path':entry.filename,'sha256':hashlib.sha256(data).hexdigest().upper()})
    for entry in resource.infolist():result.writestr(entry,resource.read(entry.filename))
    for name,path in product_ui.items():result.writestr(name,path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    for name,path in mavk_entries.items():result.writestr(name,path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
    for name,data in windows_entries.items():result.writestr(name,data,compress_type=zipfile.ZIP_DEFLATED)
    for name,path in legal.items():result.writestr('assets/legal/'+name,path.read_bytes())
    result.writestr('assets/legal/THIRD_PARTY_NOTICES.txt',notice_text.encode('utf-8'))
subprocess.run(installer_check,check=True)
if windows_runtime in windows_assets:
    subprocess.run([__import__('sys').executable,str(Path(__file__).with_name('verify-windows-iso-runtime.py')),
                    '--apk',str(a.output/'unsigned.apk'),
                    '--source',str(repo/'android-app/src/com/example/winavf/WindowsIsoBootRuntime.java')],check=True)
# A packaged .so with missing JNI methods passed every earlier check and broke
# Windows ISO selection at runtime. Fail the build instead.
native_check=[__import__('sys').executable,str(Path(__file__).with_name('verify-native-symbols.py')),
              '--apk',str(a.output/'unsigned.apk'),'--source',str(repo/'android-app/src'),
              '--allow-missing','com_example_winavf_MavkDisplayService.getPresentedExtent']
subprocess.run(native_check,check=True)
# libudfread pinned the UDF partition number to 0 while retail Windows ARM64
# ISOs use 2989, so every ISO selection failed with "Could not read the UDF
# filesystem". A stale library shipped that way once already.
udfread_check=[__import__('sys').executable,
               str(Path(__file__).with_name('verify-udfread-partition.py')),
               '--apk',str(a.output/'unsigned.apk')]
subprocess.run(udfread_check,check=True)
(a.output/'preserved-payloads.json').write_text(json.dumps(preserved,indent=2)+'\n',encoding='utf-8')
print('STABLE_RESOURCE_IDS=PASS')
print('PRESERVED_TESTED_PAYLOADS='+str(len(preserved)))
