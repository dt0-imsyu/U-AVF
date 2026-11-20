"""Load independently assembled isolated PMCC trap adapter, exact COFF layout."""
import struct
from pathlib import Path

SOURCE = 0x2ba430
CAVE = 0x665c40

def branch(start, end, link=False):
    delta=end-start
    assert delta%4==0 and -(1<<27)<=delta<(1<<27)
    return (0x94000000 if link else 0x14000000)|((delta//4)&0x3ffffff)

def load_thunk(workspace):
    data=(Path(workspace)/'build-logs/windows-post-linux-reassessment-20261004/pmu-counter-emulation.obj').read_bytes()
    machine,nsections,_,symptr,nsyms,optsize,_=struct.unpack_from('<HHIIIHH',data)
    assert machine==0xaa64 and optsize==0
    strings=data[symptr+18*nsyms:]
    symbols={};i=0
    while i<nsyms:
        name,value,section,_,_,aux=struct.unpack_from('<8sIhHBB',data,symptr+18*i)
        if name[:4]==bytes(4):
            offset=struct.unpack_from('<I',name,4)[0]
            name=strings[offset:].split(b'\0')[0]
        else:name=name.split(b'\0')[0]
        symbols[name.decode()]=(value,section)
        i+=1+aux
    text=next(struct.unpack_from('<8sIIIIIIHHI',data,20+40*i) for i in range(nsections) if data[20+40*i:20+40*i+8].rstrip(b'\0')==b'.text')
    size,offset=text[3:5]
    code=bytearray(data[offset:offset+size])
    assert len(code)==244 and symbols['uavf_pmu_read_call'][0]==0x48
    assert symbols['uavf_pmu_emulated_point'][0]==0xc0 and symbols['uavf_pmu_fallback_branch'][0]==0xf0
    assert struct.unpack_from('<I',code,0)[0]==0xd503237f
    assert struct.unpack_from('<I',code,0x48)[0]==0x94000000
    assert struct.unpack_from('<I',code,0xf0)[0]==0x14000000
    struct.pack_into('<I',code,0x48,branch(CAVE+0x48,0x620850,True))
    struct.pack_into('<I',code,0xf0,branch(CAVE+0xf0,SOURCE+4))
    return bytes(code),0xc0
