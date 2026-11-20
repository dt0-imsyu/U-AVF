"""Offline bounds/relocation/predicate audit; NOT a runtime emulation PASS."""
import importlib
import struct
from pathlib import Path

adapter=importlib.import_module('pmu-emulation-thunk')
workspace=Path(__file__).resolve().parents[3]
code,point=adapter.load_thunk(workspace)
kernel=(workspace/'build-logs/windows-arm64-timer-static-20260913/ntoskrnl.exe').read_bytes()
pe=struct.unpack_from('<I',kernel,60)[0]
n=struct.unpack_from('<H',kernel,pe+6)[0];opt=struct.unpack_from('<H',kernel,pe+20)[0]
sections=[struct.unpack_from('<IIII',kernel,pe+24+opt+40*i+8) for i in range(n)]
def image(rva,size):
    for vs,va,raw,offset in sections:
        if va<=rva and rva+size<=va+raw:return kernel[offset+rva-va:offset+rva-va+size]
    raise AssertionError('Outside raw image')
text=next(s for s in sections if s[1]==0x200000)
assert text[1]+text[0]<=adapter.CAVE and adapter.CAVE+len(code)<=((text[1]+text[0]+4095)&~4095)
assert adapter.CAVE>=text[1]+text[2] # Verified mapped executable page zero-fill, not on-disk bytes.
assert image(adapter.SOURCE,8)==bytes.fromhex('7f2303d5f353bba9')
assert image(0x620850,8)==bytes.fromhex('230080d2845582d2')
def branch_target(offset):
    word=struct.unpack_from('<I',code,offset)[0]
    imm=word&0x3ffffff
    if imm&(1<<25):imm-=1<<26
    return adapter.CAVE+offset+imm*4
assert branch_target(0x48)==0x620850
assert branch_target(0xf0)==adapter.SOURCE+4
assert point==0xc0 and len(code)==244
assert struct.unpack_from('<I',code,0x7c)[0]==0xf8287949
assert struct.unpack_from('<I',code,0x94)[0]==0xf82a7a89 # X19..28 exception frame
assert struct.unpack_from('<I',code,0xc8)[0]==0xf900a668 # advance saved PC
assert struct.unpack_from('<I',code,0xc4)[0]==0x91001108
def accepted(cpsr,esr,opcode,read_ok=True):
    return cpsr&15==0 and esr>>26 in (0,24) and read_ok and opcode&0xffffffe0==0xd53b9d00
for rt in range(32):
    assert accepted(0,0,0xd53b9d00|rt)
    assert accepted(0,24<<26,0xd53b9d00|rt)
    assert not accepted(4,0,0xd53b9d00|rt)
    assert not accepted(0,21<<26,0xd53b9d00|rt)
    assert not accepted(0,0,0xd53b9d00|rt,False)
assert not accepted(0,0,0xd53be048) # CNTVCT not handled
assert not accepted(0,0,0xd51b9d08) # MSR not handled
print('OFFLINE_PMU_ADAPTER_BOUNDS_RELOCATIONS_PREDICATES=PASS; runtime pending')
