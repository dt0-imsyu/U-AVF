"""Actual recovery: no ambiguous replay; fresh write needs exact original proof."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

source=Path(__file__).with_name('trace-kd-boot-drivers.py')
tree=ast.parse(source.read_text())
klass=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='KD')
method=next(n for n in klass.body if isinstance(n,ast.FunctionDef) and n.name=='confirm_timed_out_platform_write')
base=0xfffff80340000000
namespace=dict(struct=struct,base=base,image_bytes=lambda r,n:bytes.fromhex('7f2303d5f30f1ff8') if (r,n)==(0x2fbfa0,8) else b'')
exec(compile(ast.Module(body=[method],type_ignores=[]),'<actual guarded readback>','exec'),namespace)

def packet(address,data,api=0x3131):
    body=struct.pack('<IHHI',api,0,0,0)+bytes(4)+struct.pack('<QII',address,len(data),0)+bytes(24)+data
    return struct.pack('<IHHII',0x30303030,2,len(body),0x80800000,sum(body))+body+b'\xaa'
def run(address=base+0x665c60,data=bytes(range(32)),actual=None,recovered=(),tx=None):
    controls=[];reads=[];notes=[];writes=[]
    def read(where,length):
        reads.append((where,length));return data if actual is None or writes else actual
    def rpc(api,union,extra):
        assert api==0x3131 and struct.unpack_from('<Q',union)[0]==address and extra==data
        writes.append((api,address,len(extra)))
        response=bytearray(56);struct.pack_into('<I',response,28,len(extra));return response
    probe=SimpleNamespace(last_tx=packet(address,data) if tx is None else tx,
        platform_write_recovered_ranges=set(recovered),control=lambda kind:controls.append(kind),read=read,
        note=lambda tag,**values:notes.append(tag),rpc=rpc)
    try:namespace['confirm_timed_out_platform_write'](probe,address,data)
    except TimeoutError:error=True
    else:error=False
    return error,controls,reads,notes,writes

failed,controls,reads,notes,writes=run()
assert not failed and controls==[6] and reads==[(base+0x665c60,32)]
assert notes[-1]=='KD_PLATFORM_WRITE_CONFIRMED_BY_READBACK'
assert not writes
for offset,value in ((0xc03c31,8),(0xc03c32,11),(0xc03c38,16)):
    assert not run(address=base+offset,data=bytes([value]))[0]
failed,controls,reads,notes,writes=run(actual=bytes(32))
assert not failed and controls==[6] and len(reads)==2 and len(writes)==1
assert 'KD_PLATFORM_WRITE_PROVEN_NOT_APPLIED' in notes and notes[-1]=='KD_PLATFORM_FRESH_WRITE_VERIFIED'
failed,controls,reads,notes,writes=run(actual=b'\xff'*32)
assert failed and controls==[6] and len(reads)==1 and notes[-1]=='KD_PLATFORM_WRITE_NOT_CONFIRMED'
assert not writes
for kwargs in (dict(address=base+0x2ba430),dict(address=base+0x665d20),
               dict(data=bytes(33)),dict(data=b''),dict(recovered=((base+0x665c60,32),)),
               dict(recovered=((base+0x665c80,32),(base+0x665ca0,32))),
               dict(address=base+0xc03c32,data=b'\x10'),
               dict(tx=packet(base+0x665c60,bytes(range(32)),api=0x3133)),
               dict(tx=packet(base+0x665c80,bytes(range(32))))):
    failed,controls,reads,notes,writes=run(**kwargs)
    assert failed and not controls and not reads and not writes, kwargs
assert not run(recovered=((base+0x665c80,32),),actual=bytes(32))[0], 'Second distinct original-proven range allowed'
notify=dict(address=base+0x2fbfa0,data=struct.pack('<I',0xd65f03c0),actual=struct.pack('<I',0xd503237f),
            recovered=((base+0xc03c38,1),(base+0x665b20,16)))
failed,controls,reads,notes,writes=run(**notify)
assert not failed and len(writes)==1 and 'KD_PLATFORM_WRITE_PROVEN_NOT_APPLIED' in notes
notify['actual']=b'\xff'*4
assert run(**notify)[0], 'Partial/unknown notification bytes never replayed'
notify['recovered']+=((base+0x2fbfa0,4),)
assert run(**notify)[0], 'No repeated notification recovery'
assert 'self.write(' not in ast.get_source_segment(source.read_text(),method)
assert 'self.request(' not in ast.get_source_segment(source.read_text(),method)
print('PLATFORM_WRITE_READBACK_GUARDS=PASS: two platform ranges plus exact third notification only; once per range; fresh WRITE only with original-byte proof; partial mismatch fails closed')
