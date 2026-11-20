"""Actual clock-only reader: fixed public addresses, no guest or mutations."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

source=Path(__file__).with_name('trace-kd-boot-drivers.py')
node=next(n for n in ast.parse(source.read_text()).body if isinstance(n,ast.FunctionDef) and n.name=='kernel_clock_metadata')
shared=0xfffff78000000000
memory={0x340:struct.pack('<Q',2),0x14:struct.pack('<III',123,45,45),
        8:struct.pack('<III',456,0,0),0x300:struct.pack('<Q',13000000),
        0x348:struct.pack('<Q',100000),0x358:struct.pack('<Q',999),0x368:b'\x03',
        0x3c6:b'\x00',0x3b8:bytes(8)}
def run():
    reads=[]
    def read(address,size):
        assert address>=shared
        reads.append((address-shared,size))
        value=memory[address-shared];assert len(value)==size
        return value
    scope=dict(kd=SimpleNamespace(read=read),struct=struct)
    exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual clock-only read>','exec'),scope)
    return scope['kernel_clock_metadata'](),reads
result,reads=run()
assert result['system_time_100ns']==(45<<32)|123 and result['qpc_frequency']==13000000
assert result['projection_fields_coherent'] and len(reads)==10
memory[0x340]=struct.pack('<Q',3)
assert not run()[0]['projection_fields_coherent'],'Odd seqlock is incomplete, not a valid projection'
memory[0x14]=struct.pack('<III',123,45,46)
try:run()
except AssertionError:pass
else:raise AssertionError('Incoherent shared time accepted')
assert 'write' not in ast.get_source_segment(source.read_text(),node)
print('KERNEL_CLOCK_METADATA=PASS: fixed public fields, coherent timestamp checks, odd seqlock untrusted, no user reads/writes')
