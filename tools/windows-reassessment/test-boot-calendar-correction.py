"""Exact one-scalar loader calendar A/B guards, readback and no-op tests."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
src=Path(__file__).with_name('trace-kd-boot-drivers.py').read_text()
node=next(n for n in ast.parse(src).body if isinstance(n,ast.FunctionDef) and n.name=='correct_invalid_boot_calendar')
base=0xfffff80000000000;ptr=base+0xd00000;field=ptr+0xa48
code=struct.pack('<3I',0xf94526d7,0xaa0103fb,0xf94532c8)
memory={base+0xb17f14:code,field:struct.pack('<Q',600240385940110000)}
writes=[]
def read(addr,n):
    assert addr in memory and len(memory[addr])==n
    return memory[addr]
def write(addr,data):
    assert addr==field and len(data)==8
    writes.append((addr,data));memory[addr]=data
scope=dict(base=base,kd=SimpleNamespace(read=read,write_memory=write),struct=struct,image_bytes=lambda r,n:code)
exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual calendar A/B>','exec'),scope)
x=[0]*31;x[22]=ptr;ctx=dict(pc=base+0xb17f14,cpsr=4,x=x)
target=134000000000000000
r=scope['correct_invalid_boot_calendar'](ctx,target)
assert r['applied'] and len(writes)==1 and memory[field]==struct.pack('<Q',target)
assert not scope['correct_invalid_boot_calendar'](ctx,target)['applied'] and len(writes)==1
try:scope['correct_invalid_boot_calendar'](ctx,600240385940110000)
except AssertionError:pass
else:raise AssertionError('Far-future replacement accepted')
x[22]=0x100000
try:scope['correct_invalid_boot_calendar'](ctx,target)
except AssertionError:pass
else:raise AssertionError('User pointer accepted')
assert len(writes)==1
print('BOOT_CALENDAR_AB=PASS: exact EL1 LDR, one8B RAM scalar, verified readback, plausible no-op, bad target/pointer rejected')
