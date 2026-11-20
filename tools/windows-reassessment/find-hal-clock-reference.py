"""Offline exact-kernel ADRP references to HAL clock data; no guest access."""
import argparse
import hashlib
from pathlib import Path
import struct

p=argparse.ArgumentParser()
p.add_argument('--kernel',type=Path,required=True)
a=p.parse_args()
d=a.kernel.read_bytes()
assert hashlib.sha256(d).hexdigest()=='c667739004d1186daca2fd3fe7ccde61394bc068f385b4bb8cd5364d3005a734'
pe=struct.unpack_from('<I',d,60)[0]
n=struct.unpack_from('<H',d,pe+6)[0]
optional=struct.unpack_from('<H',d,pe+20)[0]
for i in range(n):
    off=pe+24+optional+40*i
    va,size,raw=struct.unpack_from('<III',d,off+12)
    flags=struct.unpack_from('<I',d,off+36)[0]
    if not flags&0x20000000:continue
    for pos in range(0,size-28,4):
        w=struct.unpack_from('<I',d,raw+pos)[0]
        pc=va+pos
        if w&0xfc000000==0x94000000:
            displacement=w&0x3ffffff
            if displacement&0x2000000:displacement-=0x4000000
            call_target=pc+displacement*4
            if call_target in (0x48a8c0,0x47c8d0):
                print(f'CLOCK_CALL target={call_target:#x} RVA={pc:#x} word={w:08x}')
        if w&0x9f000000!=0x90000000:continue
        imm=((w>>5)&0x7ffff)<<2|((w>>29)&3)
        if imm&0x100000:imm-=0x200000
        target=(pc&~4095)+(imm<<12)
        if target!=0xe05000:continue
        reg=w&31
        following=struct.unpack_from('<6I',d,raw+pos+4)
        matches=[]
        for j,next_word in enumerate(following,1):
            if next_word&0xffc00000==0x91000000 and (next_word>>5)&31==reg and (next_word>>10)&4095==0x160:
                matches.append(hex(pc+4*j))
        if matches:
            print(f'RVA={pc:#x} baseline_add={matches} words={w:08x} '+ ' '.join(f'{x:08x}' for x in following))
