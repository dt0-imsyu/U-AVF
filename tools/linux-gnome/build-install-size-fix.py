"""Patch explicit installer entries in a verified product prefix/APK base."""
import argparse, hashlib, importlib.machinery, json, shutil, subprocess, sys, zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--base-apk',type=Path,required=True)
p.add_argument('--output-dir',type=Path,required=True)
p.add_argument('--expected-source-sha',required=True)
p.add_argument('--replace-file',action='append',required=True,help='CPIO_PATH=SOURCE_FILE')
a=p.parse_args()
a.output_dir.mkdir(parents=True,exist_ok=False)
here=Path(__file__).resolve().parent
repo=here.parents[1]
sha=lambda data:hashlib.sha256(data).hexdigest().upper()
prefix=repo/'android-app/assets/uavf-install-platform-prefix-gdm-share.img'
original_sha=sha(prefix.read_bytes())
assert original_sha==a.expected_source_sha.upper()
source=a.output_dir/'source-initrd'
subprocess.run([sys.executable,str(here/'extract-prefix-initrd.py'),str(prefix),str(source),'--prefix-sha256',original_sha],check=True)
replacements={}
for pair in a.replace_file:
    key,path=pair.split('=',1)
    assert key not in replacements
    normalized=a.output_dir/('replacement-'+str(len(replacements)))
    normalized.write_bytes(Path(path).read_bytes().replace(b'\r\n',b'\n'))
    replacements[key]=normalized
output=a.output_dir/'fixed-initrd'
zstd=r'C:\msys64\usr\bin\zstd.exe'
command=[sys.executable,str(here.parent/'generic-ubuntu/repack-p7-frame-bridge.py'),'--source',str(source),'--output',str(output),'--zstd',zstd]
for key,path in replacements.items():command+=['--replace-file',key+'='+str(path)]
subprocess.run(command,check=True)
cpio=importlib.machinery.SourceFileLoader('cpio',str(here.parent/'generic-ubuntu/compare-p7-clean-p9.py')).load_module()
def unpack(path):
    data=path.read_bytes(); early=cpio.early_end(data)
    files,_=cpio.parse_cpio(subprocess.run([zstd,'-d','-q','-c'],input=data[early:],capture_output=True,check=True).stdout)
    return data[:early],files
old_early,old_files=unpack(source);new_early,new_files=unpack(output)
assert old_early==new_early and old_files.keys()==new_files.keys()
changed=[key for key in old_files if old_files[key]!=new_files[key]]
assert set(changed)==set(replacements),changed
fixed=a.output_dir/'fixed-install-prefix.img'
subprocess.run([sys.executable,str(here/'replace-esp-initrd.py'),str(prefix),str(output),str(fixed),'--source-sha256',original_sha,'--initrd-sha256',sha(output.read_bytes())],check=True)
data=fixed.read_bytes()
entry='assets/'+prefix.name
base=a.output_dir/'runtime-updated-base.apk'
with zipfile.ZipFile(a.base_apk) as original,zipfile.ZipFile(base,'w') as target:
    assert original.read(entry)==prefix.read_bytes()
    for item in original.infolist():
        if item.filename.startswith('META-INF/'):continue
        target.writestr(item,data if item.filename==entry else original.read(item.filename))
shutil.copy2(prefix,a.output_dir/'previous-install-prefix.img')
shutil.copy2(fixed,prefix)
report={'changed_initrd_files':changed,'early_cpio_unchanged':True,'prefix_sha256':sha(data),'esp_sha256':sha(data[1048576:1048576+126*1024*1024]),'base_apk':str(base),'official_iso_touched':False}
(a.output_dir/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
