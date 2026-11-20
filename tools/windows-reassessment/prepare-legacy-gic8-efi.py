"""Exact own EFI only: preserve known-good code/layout/logging, bind CPU0 bank.

Only for crosvm eight vCPUs, boot MPIDR affinity0, GICR3fef0000..3fff0000.
Not a generic topology selector and never a oneCPU candidate.
"""
import argparse
import hashlib
import json
import struct
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--original',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();original=a.original.read_bytes()
assert len(original)==8192 and hashlib.sha256(original).hexdigest().upper()=='893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B'
assert not a.output.exists(),'Preserve previous artifacts'
pe=struct.unpack_from('<I',original,60)[0]
assert original[pe:pe+4]==b'PE\0\0' and struct.unpack_from('<H',original,pe+4)[0]==0xaa64
n=struct.unpack_from('<H',original,pe+6)[0];optional=struct.unpack_from('<H',original,pe+20)[0]
sections=[(original[h:h+8].rstrip(b'\0'),struct.unpack_from('<4I',original,h+8)) for h in (pe+24+optional+40*i for i in range(n))]
text=next(values for name,values in sections if name==b'.text')
virtual,rva,size,raw=text;assert (virtual,rva)==(0x1068,0x1000)
plan=((0x18fc,0x72a7ffb6),(0x195c,0x72a7ffb4))
# The first register holds GICR_PIDR2; its +0x318 load is the active-PPI
# guard. Second holds GICR_TYPER and WAKER. No GICD or unrelated address.
result=bytearray(original);changed=set();patches=[]
for address,expected in plan:
    offset=raw+address-rva
    assert struct.unpack_from('<I',original,offset)[0]==expected
    assert (expected>>5)&65535==0x3ffd
    replacement=(expected&~(65535<<5))|(0x3fef<<5)
    struct.pack_into('<I',result,offset,replacement)
    changed.update(range(offset,offset+4))
    patches.append(dict(rva=hex(address),before=hex(expected),after=hex(replacement)))
assert all(original[i]==result[i] for i in range(len(result)) if i not in changed)
assert sum(x!=y for x,y in zip(original,result))==4,'Only four bytes in two MOVK instructions'
a.output.mkdir(parents=True)
path=a.output/'uart-legacy-eight-cpu-gic.efi';path.write_bytes(result)
report=dict(result='EXACT_LEGACY_EFI_GIC8_TWO_CONSTANTS_PASS',sha256=hashlib.sha256(result).hexdigest().upper(),
    bytes=8192,changed_bytes=4,patches=patches,expected_vcpus=8,boot_affinity=0,
    old_gicr_base='0x3ffd0000',new_gicr_base='0x3fef0000',gicr_end='0x3fff0000',
    unchanged='All other bytes including headers, code flow, data, logging, CAM/EBS/timer behavior; no Windows signed image changes')
(a.output/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
