"""Read-only inventory of actual shipped platform payloads and local source evidence.

Never declares corresponding-source compliance from a matching binary alone.
All outputs are private and must be reviewed before distribution.
"""
import argparse
import hashlib
import importlib.machinery
import json
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS

p=argparse.ArgumentParser()
p.add_argument('--apk',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--uboot-tree',type=Path)
p.add_argument('--wrapper-source',type=Path)
p.add_argument('--previous-evidence',type=Path,
               help='Reuse a prior source checkpoint only when its shipped U-Boot binary hash matches')
p.add_argument('--zstd',type=Path,required=True)
a=p.parse_args()
assert (a.previous_evidence is not None) != (a.uboot_tree is not None), 'Choose one source-evidence mode'
assert a.previous_evidence is not None or a.wrapper_source is not None, 'Wrapper source required'
a.output.mkdir(parents=True,exist_ok=False)
repo=Path(__file__).resolve().parents[2]
cpio=importlib.machinery.SourceFileLoader('release_cpio',str(repo/'tools/generic-ubuntu/compare-p7-clean-p9.py')).load_module()
sha=lambda b:hashlib.sha256(b).hexdigest().upper()
report={'apk_sha256':sha(a.apk.read_bytes()),'platforms':[],
        'corresponding_source_complete':False,'publication':'HOLD'}
with zipfile.ZipFile(a.apk) as apk:
    wrapper=apk.read('assets/u-boot-wrapper-v24.Image')
    uboot=wrapper[4096:]
    if a.previous_evidence is not None:
        previous=json.loads((a.previous_evidence/'bundled-platform-inventory.json').read_text(encoding='utf-8'))
        assert previous['uboot']['embedded_binary_exact'] and previous['uboot']['binary_sha256']==sha(uboot), 'Prior U-Boot evidence does not match current APK'
        for filename in ('u-boot-local-source-candidate.zip','u-boot-source-manifest.json'):
            assert (a.previous_evidence/filename).is_file(), 'Missing previous source checkpoint'
            shutil.copy2(a.previous_evidence/filename,a.output/filename)
    else:
        assert uboot==(a.uboot_tree/'u-boot.bin').read_bytes(),'Local U-Boot binary does not match packaged wrapper'
    report['uboot']={'embedded_binary_exact':True,'binary_sha256':sha(uboot),
                     'source_rebuild_reproduced':False}
    for entry in apk.namelist():
        if not entry.startswith('assets/') or not entry.endswith('.img') or 'platform-prefix' not in entry:continue
        prefix=apk.read(entry)
        item={'asset':entry,'sha256':sha(prefix),'bytes':len(prefix)}
        with tempfile.TemporaryDirectory(prefix='uavf-release-inventory-') as scratch:
            image=Path(scratch)/'prefix.img';image.write_bytes(prefix)
            with PyFatFS(str(image),offset=1048576,read_only=True) as fat:
                for name in ('/EFI/BOOT/BOOTAA64.EFI','/CASPER/VMLINUZ','/CASPER/INITRD'):
                    if fat.exists(name):
                        data=fat.readbytes(name)
                        item[name]={'bytes':len(data),'sha256':sha(data)}
                        if name.endswith('INITRD'):
                            early=cpio.early_end(data)
                            unpacked=subprocess.run([str(a.zstd),'-d','-q','-c'],input=data[early:],capture_output=True,check=True).stdout
                            files,_=cpio.parse_cpio(unpacked)
                            manifest=[]
                            for path,content in sorted(files.items()):
                                if content.startswith(b'\x7fELF') or '/copyright' in path or '/usr/local/' in '/'+path:
                                    manifest.append({'path':path,'bytes':len(content),'sha256':sha(content),
                                                     'elf':content.startswith(b'\x7fELF')})
                            item['main_cpio_files']=len(files)
                            item['binary_notice_and_local_inventory']=manifest
        report['platforms'].append(item)

# Exact local source-tree checkpoint. Keep configuration and scripts; omit
# generated objects, alternate build output, git metadata and host credentials.
excluded={'.git','build-qemu','__pycache__'}
suffixes={'.o','.a','.so','.pyc','.cmd','.su','.dtb','.elf','.map','.sym','.bin'}
source_manifest=[]
if a.previous_evidence is not None:
    source_manifest=json.loads((a.output/'u-boot-source-manifest.json').read_text(encoding='utf-8'))
    report['uboot']['source_file_count']=len(source_manifest)
    report['uboot']['source_checkpoint_reused_from']=str(a.previous_evidence)
    (a.output/'bundled-platform-inventory.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('UBOOT_EMBEDDED_BINARY=EXACT; CURRENT_PLATFORM_COUNT='+str(len(report['platforms'])))
    print('SOURCE_COMPLIANCE=NOT_COMPLETE; PUBLICATION=HOLD')
    raise SystemExit(0)
with zipfile.ZipFile(a.output/'u-boot-local-source-candidate.zip','w',zipfile.ZIP_DEFLATED) as out:
    for path in sorted(a.uboot_tree.rglob('*')):
        rel=path.relative_to(a.uboot_tree)
        if not path.is_file() or excluded.intersection(rel.parts) or path.suffix in suffixes:continue
        if path.name in ('u-boot','System.map') or path.suffix in ('.log','.jks','.keystore'):continue
        data=path.read_bytes();out.writestr('u-boot/'+rel.as_posix(),data)
        source_manifest.append({'path':rel.as_posix(),'bytes':len(data),'sha256':sha(data)})
    out.write(a.wrapper_source,'winavf-test/kernel-first/u-boot-wrapper.S')
report['uboot']['source_file_count']=len(source_manifest)
(a.output/'u-boot-source-manifest.json').write_text(json.dumps(source_manifest,indent=2)+'\n',encoding='utf-8')
(a.output/'bundled-platform-inventory.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('UBOOT_EMBEDDED_BINARY=EXACT; PLATFORM_COUNT='+str(len(report['platforms'])))
print('SOURCE_COMPLIANCE=NOT_COMPLETE; PUBLICATION=HOLD')
