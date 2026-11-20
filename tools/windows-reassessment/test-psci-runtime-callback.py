"""Audit copied COFF callback: no external relocations, bounded PC references."""
import struct
import sys
from pathlib import Path

obj=Path(sys.argv[1]).read_bytes()
machine,count,_,_,_,optional,_=struct.unpack_from('<HHIIIHH',obj)
assert machine==0xaa64 and optional==0
sections=[]
for i in range(count):
    header=20+i*40
    name=obj[header:header+8].rstrip(b'\0')
    size,raw,relocations=struct.unpack_from('<III',obj,header+16)
    nrel=struct.unpack_from('<H',obj,header+32)[0]
    assert nrel==0, 'Copied callback cannot reference original LoaderCode or external data'
    if name==b'.text':sections.append(obj[raw:raw+size])
assert len(sections)==1
code=sections[0]
assert len(code)==68
marker=len(code)
assert struct.unpack_from('<I',code,52)[0]==0xd43e0000
assert code[:8]==bytes.fromhex('fd7bbda9f35301a9')
assert code[-12:]==bytes.fromhex('f35341a9fd7bc3a8c0035fd6')

def signed(value,bits):
    return value-(1<<bits) if value>>(bits-1) else value

calls=0
for pc in range(0,marker,4):
    word=struct.unpack_from('<I',code,pc)[0]
    if word&0x7c000000==0x14000000: # B/BL immediate
        target=pc+signed(word&0x3ffffff,26)*4
        assert 0<=target<marker and target%4==0
    elif word&0xff000010==0x54000000 or word&0x7e000000==0x34000000:
        target=pc+signed((word>>5)&0x7ffff,19)*4
        assert 0<=target<marker and target%4==0
    elif word&0x7e000000==0x36000000:
        target=pc+signed((word>>5)&0x3fff,14)*4
        assert 0<=target<marker and target%4==0
    elif word&0x9f000000==0x10000000: # ADR, not ADRP
        target=pc+signed(((word>>5)&0x7ffff)<<2|((word>>29)&3),21)
        raise AssertionError('Callback must not reference image-relative data')
    elif word&0x9f000000==0x90000000:
        raise AssertionError('No original-image page-relative data addresses allowed')
    if word==0xd63f0200:calls+=1 # only loaded ConvertPointer x16
assert calls==1
print('COPIED_RUNTIME_CALLBACK_PIC_NO_RELOCATIONS_BRANCH_BOUNDS_PASS',len(code))
