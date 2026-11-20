"""First HAL clock writer typed argument guard tests; no guest access."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

src=Path(__file__).with_name('trace-kd-boot-drivers.py').read_text()
node=next(n for n in ast.parse(src).body if isinstance(n,ast.FunctionDef) and n.name=='hal_clock_seed_evidence')
base=0xfffff80000000000
code=bytes.fromhex('7f2303d5f353bfa9')
ptr=base+0xe03c48
reads=[]
def read(address,size):
    reads.append((address,size))
    if address==base+0x48a8c0 and size==8:return code
    if address==ptr and size==8:return struct.pack('<Q',541490910014448004)
    if address==0xfffff78000000014 and size==12:return struct.pack('<III',123,45,45)
    raise AssertionError('Unbounded/unexpected read')
scope=dict(base=base,kd=SimpleNamespace(read=read),image_bytes=lambda r,n:code,struct=struct)
exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual seed reader>','exec'),scope)
ctx=dict(pc=base+0x48a8c0,cpsr=4,x=[0]*31)
ctx['x'][0]=ptr;ctx['x'][30]=base+0x49c5a0
r=scope['hal_clock_seed_evidence'](ctx)
assert r['calendar_100ns']==541490910014448004 and r['source']=='explicit_calendar_argument'
assert reads==[(base+0x48a8c0,8),(ptr,8)]
ctx['x'][0]=0
r=scope['hal_clock_seed_evidence'](ctx)
assert r['source']=='null_argument_current_system_time_fallback' and r['calendar_100ns']==(45<<32)|123
ctx['x'][0]=0x1234
try:scope['hal_clock_seed_evidence'](ctx)
except AssertionError:pass
else:raise AssertionError('User pointer accepted')
ctx['x'][0]=ptr;ctx['x'][30]=base+0x1234
try:scope['hal_clock_seed_evidence'](ctx)
except AssertionError:pass
else:raise AssertionError('Unknown caller accepted')
assert not any(isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and 'write' in n.func.attr for n in ast.walk(node))
print('HAL_CLOCK_SEED=PASS: exact code/EL1/caller, typed8B or public12B time only; no writes')
