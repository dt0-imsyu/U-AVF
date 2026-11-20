"""Bounded read-only signed executable metadata for the isolated Windows root.

Never reads LSASS memory, credentials, registry or user files. Live NTFS reads
are non-coherent; PE identity is audited, absence is not a runtime verdict.
"""
import argparse
import hashlib
import importlib.util
import json
import re
import struct
import subprocess
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
assert not a.output.exists(), 'Preserve earlier executable evidence'
spec=importlib.util.spec_from_file_location('reader',Path(__file__).with_name('extract-installed-setup-evidence.py'))
reader=importlib.util.module_from_spec(spec);spec.loader.exec_module(reader)
def owner():
    state=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    owners=re.findall(r'cid: (\d+).*?requesterUid: (\d+).*?requesterPid: (\d+)',state,re.S)
    assert len(owners)==1 and owners[0][1]=='10042', 'Isolated diagnostic owner only'
    return owners[0]
before=owner()
disk=reader.ColdRoot(a.adb)
boot=disk.read(512)
assert boot[3:11]==b'NTFS    ' and struct.unpack_from('<I',boot,72)[0]==0x3ef669b7
disk.seek(0);fs=reader.NTFS(disk)
a.output.mkdir(parents=True)
items=[]
for name in ('lsass.exe','lsasrv.dll','bcryptprimitives.dll','ntdll.dll'):
    stream=fs.mft.get('Windows\\System32\\'+name).open()
    assert 0<stream.size<=8*1024*1024
    data=stream.read(stream.size)
    if data[:2]!=b'MZ':
        item=dict(name=name,unavailable='NTFS stream is not reconstructed PE; sparse/WOF backing or non-coherent read. Do not use as executable evidence.')
        items.append(item);print(json.dumps(item));continue
    pe=struct.unpack_from('<I',data,60)[0]
    assert data[pe:pe+4]==b'PE\0\0' and struct.unpack_from('<H',data,pe+4)[0] in (0xaa64,0xa64e,0xa641)
    (a.output/name).write_bytes(data)
    item=dict(name=name,bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
    items.append(item);print(json.dumps(item))
    assert disk.read_bytes<=48*1024*1024, 'Bounded executable-only read budget'
assert owner()==before, 'Owner lifetime changed'
(a.output/'manifest.json').write_text(json.dumps(dict(owner=before,files=items,read_bytes=disk.read_bytes,
    read_only=True,filesystem_coherent=False,scope='Four ARM64 code files only; no credentials or user data'),indent=2))
