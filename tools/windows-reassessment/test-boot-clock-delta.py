"""Actual boot delta reader: typed operands, unsigned wrap and rejection tests."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
src=Path(__file__).with_name('trace-kd-boot-drivers.py').read_text()
node=next(n for n in ast.parse(src).body if isinstance(n,ast.FunctionDef) and n.name=='boot_clock_delta_evidence')
base=0xfffff80000000000;ptr=base+0xd00000;sp=base+0x1000000
code=struct.pack('<4I',0xf9452ac8,0x910043e2,0x58000aa1,0xcb08000b)
reads=[]
def read(addr,n):
    reads.append((addr,n))
    values={(base+0xb17f6c,16):code,(ptr+0xa48,16):struct.pack('<QQ',134000000000000000,100000),
            (sp+0x18,8):struct.pack('<Q',13000000)}
    assert (addr,n) in values
    return values[addr,n]
scope=dict(base=base,kd=SimpleNamespace(read=read),struct=struct,
           image_bytes=lambda r,n:code if r==0xb17f6c else struct.pack('<I',0xf94526d7))
exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual boot delta>','exec'),scope)
x=[0]*31;x[0]=200000;x[1]=10000000;x[8]=100000;x[22]=ptr;x[23]=134000000000000000
ctx=dict(pc=base+0xb17f78,cpsr=4,sp=sp,x=x)
r=scope['boot_clock_delta_evidence'](ctx)
assert not r['counter_went_backwards'] and r['unsigned_delta']==100000 and len(reads)==3
x[0]=1
r=scope['boot_clock_delta_evidence'](ctx)
assert r['counter_went_backwards'] and r['unsigned_delta']==(1-100000)%(1<<64)
x[8]=99999
try:scope['boot_clock_delta_evidence'](ctx)
except AssertionError:pass
else:raise AssertionError('Mismatched saved counter accepted')
assert not any(isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and 'write' in n.func.attr for n in ast.walk(node))
print('BOOT_CLOCK_DELTA=PASS: exact site, three40B reads, typed clock data, backwards arithmetic, no writes')
