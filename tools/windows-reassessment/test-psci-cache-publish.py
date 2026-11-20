"""Independent publication-helper opcode, relocation and access-range gate."""
import struct
import sys
from pathlib import Path
obj=Path(sys.argv[1]).read_bytes()
machine,count,_,_,_,optional,_=struct.unpack_from('<HHIIIHH',obj)
assert machine==0xaa64 and optional==0
code=None
for index in range(count):
    at=20+40*index
    if obj[at:at+8].rstrip(b'\0')!=b'.text':continue
    size,raw=struct.unpack_from('<II',obj,at+16)
    assert struct.unpack_from('<H',obj,at+32)[0]==0,'PIC helper must have no relocations'
    code=obj[raw:raw+size]
assert len(code)==40
words=struct.unpack('<10I',code)
assert words==(0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0xd5033f9f,0xd5033fdf,0xd43e0000)
# Four PoC clean addresses32B apart cover128B on these64B-cache-line CPUs.
# Only x16 changes; no loads/stores, branch into Windows, timer or PMU writes.
offsets=[];offset=0
for word in words:
    if word==0xd50b7a30:offsets.append(offset)
    if word==0x91008210:offset+=32
assert offsets==[0,32,64,96] and offset==96
assert words[-1]==0xd43e0000 and 9*4==36
print('OWN_PUBLISH40B_PIC_FOUR_POC_CLEANS_DSB_ISB_OWN_BRK_PASS')
