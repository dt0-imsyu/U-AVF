"""Exercise the actual typed wait observation without any guest or writes."""
import ast
import struct
from pathlib import Path

source=Path(__file__).with_name('trace-kd-boot-drivers.py')
node=next(n for n in ast.parse(source.read_text()).body if isinstance(n,ast.FunctionDef) and n.name=='hot_wait_timeout')
workspace=source.resolve().parents[3]
original=(workspace/'build-logs/windows-post-linux-reassessment-20261004/lsass-source-code-20261010/ntdll.dll').read_bytes()
pe=struct.unpack_from('<I',original,60)[0]
count=struct.unpack_from('<H',original,pe+6)[0];optional=struct.unpack_from('<H',original,pe+20)[0]
layout=[struct.unpack_from('<IIII',original,pe+24+optional+i*40+8) for i in range(count)]
def image_bytes(rva,size,data,sections):
    for _,va,raw,offset in sections:
        if va<=rva and rva+size<=va+raw:return data[offset+rva-va:offset+rva-va+size]
    raise AssertionError('Original code bounds')

thread=0xffff800000003000;trap=0xffff800000005000;base=0x7ffdf53b0000
sp=0x10000;pointer=sp+0x180
window=image_bytes(0x2e64,48,original,layout).hex()
item=dict(thread=hex(thread),state=2,saved_pc=hex(base+0x2e74),saved_lr=hex(base+0x6c208),instruction_window=window)
modules=[dict(name='ntdll.dll',base=hex(base))]
raw=bytearray(0x1b0);raw[3]=0x40;raw[0x2a3-0xf8]=37
struct.pack_into('<Q',raw,0x18,800)
def shared(value):return struct.pack('<III',value&0xffffffff,value>>32,value>>32)
memory={thread+0x88:struct.pack('<Q',trap),trap+0x90:struct.pack('<I',0),
        trap+0x98:struct.pack('<Q',sp),trap+0xa8:struct.pack('<Q',pointer),
        pointer:struct.pack('<q',-10000000),thread+0xf8:bytes(raw),
        0xfffff78000000008:shared(10000000),0xfffff78000000014:shared(458317221921012929)}
class Reader:
    def __init__(self):self.reads=[]
    def read(self,address,size):
        self.reads.append((address,size));data=memory[address]
        assert len(data)==size
        return data
def run(selected=None,current=thread):
    kd=Reader()
    scope=dict(kd=kd,struct=struct,Path=Path,__file__=str(source),image_bytes=image_bytes)
    exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual typed observation>','exec'),scope)
    return scope['hot_wait_timeout'](selected or item,modules,current),kd

result,kd=run()
assert result['timeout_kind']=='relative' and result['timeout_100ns']==-10000000
assert result['timer_inserted'] and result['wait_reason']==37
assert [(address,size) for address,size in kd.reads if address<0x800000000000]==[(pointer,8)], 'Exactly one typed user value'
for value,kind in ((0,'immediate'),(458317221921012000,'absolute')):
    memory[pointer]=struct.pack('<q',value)
    result,_=run();assert result['timeout_kind']==kind
    if value:assert result['absolute_remaining_100ns']==-929
memory[trap+0xa8]=struct.pack('<Q',0)
result,kd=run();assert result['timeout_kind']=='infinite'
assert not any(address<0x800000000000 for address,size in kd.reads)
memory[trap+0xa8]=struct.pack('<Q',sp+65536)
result,kd=run();assert not result['available']
assert not any(address<0x800000000000 for address,size in kd.reads)
for selected in (dict(item,state=1),dict(item,saved_lr=hex(base+1)),dict(item,saved_pc=hex(base+1))):
    result,kd=run(selected);assert not result['available'] and not kd.reads
result,kd=run(current=thread+16);assert not result['available'] and not kd.reads
kernelbase=(workspace/'build-logs/windows-post-linux-reassessment-20261004/lsass-source-code-20261010/kernelbase.dll').read_bytes()
kbpe=struct.unpack_from('<I',kernelbase,60)[0]
kbcount=struct.unpack_from('<H',kernelbase,kbpe+6)[0];kboptional=struct.unpack_from('<H',kernelbase,kbpe+20)[0]
kblayout=[struct.unpack_from('<IIII',kernelbase,kbpe+24+kboptional+i*40+8) for i in range(kbcount)]
kbbase=0x7ffa70000000
modules.append(dict(name='KERNELBASE.dll',base=hex(kbbase)))
result_item=dict(item,saved_pc=hex(kbbase+0x1c7250),saved_lr=hex(kbbase+0x10cb0c),
                 instruction_window=image_bytes(0x1c7240,48,kernelbase,kblayout).hex())
memory[sp+0x10]=struct.pack('<q',-100000)
memory[sp+8]=struct.pack('<Q',0x7ffaaabbccdd)
memory[trap+0xa0]=struct.pack('<I',1460)
result,kd=run(result_item)
assert result['timeout_100ns']==-100000 and result['return_win32_error']==1460
assert 'return_ntstatus' not in result
assert result['caller_pc']=='0x7ffaaabbccdd'
assert [(address,size) for address,size in kd.reads if address<0x800000000000]==[(sp+0x10,8),(sp+8,8)]
memory[sp+0x10]=bytes(8);memory[trap+0xa0]=bytes(4)
result,_=run(result_item);assert result['timeout_kind'].startswith('zero-wrapper-slot')
memory[trap+0xa0]=struct.pack('<I',1460)
result,_=run(result_item);assert result['timeout_kind']=='immediate'
try:run(dict(item,instruction_window='00'*48))
except AssertionError:pass
else:raise AssertionError('Mismatched signed-code window accepted')
print('HOT_WAIT_TIMEOUT_GUARDS=PASS: exact syscall/result/caller/source, typed timeout/status/code pointer only, no arbitrary user reads or writes')
