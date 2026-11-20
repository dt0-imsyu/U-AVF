"""Cold read-only inventory of four fixed Windows display framework binaries.
No account/registry scan, no driver install, no disk write or binary extraction.
Presence and PE version are prerequisites, NOT proof of driver admission.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import subprocess
import pefile

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
assert not a.output.exists(), 'Preserve evidence'
def cold():
    state=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    assert 'Running VMs: []' in state, 'No live filesystem inspection'
cold()
spec=importlib.util.spec_from_file_location('reader',Path(__file__).with_name('extract-installed-setup-evidence.py'))
reader=importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
disk=reader.ColdRoot(a.adb)
boot=disk.read(512)
assert boot[3:11]==b'NTFS    ' and struct.unpack_from('<I',boot,72)[0]==0x3ef669b7
disk.seek(0)
fs=reader.NTFS(disk)
items=[]
for name in ('Windows/System32/drivers/IndirectKmd.sys','Windows/System32/drivers/WUDFRd.sys',
             'Windows/System32/drivers/UMDF/IddCx.dll','Windows/System32/WUDFHost.exe'):
    item={'path':name}
    try:
        stream=fs.mft.get(name.replace('/','\\')).open()
        item.update(present=True,bytes=stream.size)
        assert 0<stream.size<=4*1024*1024, 'Bounded framework only'
        data=stream.read(stream.size)
        pe=pefile.PE(data=data)
        info=getattr(pe,'VS_FIXEDFILEINFO',[])
        version=None
        if info:
            v=info[0]
            version=[v.FileVersionMS>>16,v.FileVersionMS&65535,v.FileVersionLS>>16,v.FileVersionLS&65535]
        item.update(present=True,bytes=len(data),machine=hex(pe.FILE_HEADER.Machine),version=version,
                    sha256=hashlib.sha256(data).hexdigest(),signed_structure_present=bool(pe.OPTIONAL_HEADER.DATA_DIRECTORY[4].Size))
        pe.close()
    except Exception as error:
        item.setdefault('present',None)
        item.update(unavailable=type(error).__name__,version=None,
                    limitation='File lookup/stream evidence only; WOF/compressed streams may not expose PE bytes through this parser')
    items.append(item)
    assert disk.read_bytes<=64*1024*1024
cold()
report={'cold_read_only':True,'device_writes':0,'driver_admission':'UNPROVEN',
        'signature_trust':'NOT_VERIFIED','items':items}
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
