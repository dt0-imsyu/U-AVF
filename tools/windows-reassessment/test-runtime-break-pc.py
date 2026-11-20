"""Actual context setter: exact known PC change, bounded wire staging and commit."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

source=Path(__file__).with_name('trace-kd-boot-drivers.py')
klass=next(n for n in ast.parse(source.read_text()).body if isinstance(n,ast.ClassDef) and n.name=='KD')
method=next(n for n in klass.body if isinstance(n,ast.FunctionDef) and n.name=='restore_context')
base=0xfffff803b8600000
scope=dict(struct=struct,base=base)
exec(compile(ast.Module(body=[method],type_ignores=[]),'<actual setter>','exec'),scope)
old=bytearray(912);struct.pack_into('<Q',old,264,base+0x201370)
updated=bytearray(old);struct.pack_into('<Q',updated,264,base+0x201374)
def run(data,previous=old,short=False,bad_read=False):
    calls=[];notes=[];staged=bytearray(old)
    def rpc(api,union,extra=b''):
        calls.append((api,union,extra));result=bytearray(56)
        if api==0x3160:
            offset,count,total=struct.unpack('<III',union)
            assert total==912 and count==len(extra) and 0<count<=32
            assert offset==sum(len(call[2]) for call in calls[:-1])
            assert extra==updated[offset:offset+count]
            staged[offset:offset+count]=extra
            struct.pack_into('<I',result,24,count-1 if short else count)
        if api==0x315f:
            assert union==struct.pack('<III',0,912,0) and staged==updated
            result.extend(bytes(912) if bad_read else staged)
        return bytes(result)
    kd=SimpleNamespace(last_context=bytes(previous),rpc=rpc,note=lambda tag,**fields:notes.append(tag))
    scope['restore_context'](kd,bytes(data))
    return calls,notes
calls,notes=run(updated)
assert [api for api,_,_ in calls]==[0x3160]*29+[0x315f]
assert len(calls[-2][2])==16 and notes==['KD_RUNTIME_BREAK_PC_ONLY_VERIFIED']
changed=bytearray(updated);changed[40]=1
assert run(changed)[0][0][0]==0x3133, 'General context restores retain original path'
other=bytearray(old);struct.pack_into('<Q',other,264,base+0x1234)
assert run(updated,previous=other)[0][0][0]==0x3133, 'No generic breakpoint PC fast path'
for kwargs in (dict(short=True),dict(bad_read=True)):
    try:run(updated,**kwargs)
    except AssertionError:pass
    else:raise AssertionError('Invalid partial-context response accepted')
print('RUNTIME_BREAK_PC_ONLY=PASS: exact known PC+4,32-byte staged SetContextEx with total912/final commit/full readback, unchanged GPR/SP/flags; short/mismatch rejected')
