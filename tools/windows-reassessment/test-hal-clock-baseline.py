"""Bounded HAL clock reader guards and unaligned reference calendar layout."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

src=Path(__file__).with_name('trace-kd-boot-drivers.py').read_text()
node=next(n for n in ast.parse(src).body if isinstance(n,ast.FunctionDef) and n.name=='hal_clock_baseline_metadata')
base=0xfffff80000000000
code={0x48a804:struct.pack('<II',0xf0004bc8,0x91058115),
      0x48a80c:struct.pack('<III',0xf94002b7,0x910043e0,0xf840c2b6),
      0x48a820:struct.pack('<I',0xf9400ea8),
      0x47c82c:struct.pack('<IIII',0x58000468,0xf94007e9,0xf940010a,0x58000448),
      0x47c8b8:struct.pack('<QQ',0xfffff78000000014,864000000000)}
block=bytearray(40)
struct.pack_into('<Q',block,0,9876)
struct.pack_into('<Q',block,12,541490911030240127)
struct.pack_into('<Q',block,24,9876)
memory=dict(code)
memory.update({0xe05160:bytes(block),0xe04d88:struct.pack('<I',0),0xe062e0:struct.pack('<I',8)})
reads=[]
def read(addr,n):
    rva=addr-base
    assert rva in memory and len(memory[rva])==n
    reads.append((rva,n))
    return memory[rva]
scope=dict(base=base,kd=SimpleNamespace(read=read),struct=struct,image_bytes=lambda r,n:code[r])
exec(compile(ast.Module(body=[node],type_ignores=[]),'<HAL clock reader>','exec'),scope)
result=scope['hal_clock_baseline_metadata']()
assert result['reference_coherent'] and result['reference_calendar_100ns']==541490911030240127
assert len(reads)==7 and sum(n for _,n in reads)==88
memory[0x48a820]=bytes(4)
try:scope['hal_clock_baseline_metadata']()
except AssertionError:pass
else:raise AssertionError('Changed runtime HAL code accepted')
assert not any(isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and 'write' in n.func.attr for n in ast.walk(node))
print('HAL_CLOCK_BASELINE=PASS: exact code guards, fixed88B reads, unaligned calendar, no mutation')
