"""Execute actual one-shot typed observation, without a VM or any writes."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

source=Path(__file__).with_name('trace-kd-boot-drivers.py')
tree=ast.parse(source.read_text())
node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='kernel_wait_return_evidence')
base=0xfffff80206800000
thread=0xffff800000003000
process=0xffff800000008000
sp=0xffff80000000a000
pid=740;tid=780
memory={thread+0x240:struct.pack('<Q',process),process+0x328:b'lsass.exe'+bytes(6),
        thread+0x4e8:struct.pack('<QQ',pid,tid),thread+0x30:struct.pack('<QQ',sp-4096,sp+4096),
        sp+0x10:struct.pack('<q',-100000),0xfffff78000000014:struct.pack('<III',123,45,45)}
original=struct.pack('<III',0xa8c27bfd,0xd50323ff,0xd65f03c0)
context=dict(pc=base+0x8f7d74,cpsr=5,sp=sp,x=[0x102]+[0]*30)
target=(pid,tid,thread)

def run(ctx=context,selected=target,code=original):
    reads=[]
    def read(address,size):
        reads.append((address,size));value=memory[address]
        assert len(value)==size
        return value
    def image_bytes(rva,size):
        if (rva,size)==(0x52f104,4):return struct.pack('<I',0xf9401d00)
        if (rva,size)==(0x52f114,8):return struct.pack('<II',0x9100c108,0xf8bfc100)
        assert (rva,size)==(0x8f7d74,12)
        return code
    scope=dict(base=base,struct=struct,image_bytes=image_bytes,kd=SimpleNamespace(read=read))
    exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual return observation>','exec'),scope)
    return scope['kernel_wait_return_evidence'](thread,ctx,selected),reads

result,reads=run()
assert result['available'] and result['return_ntstatus']=='0x102' and result['timeout_100ns']==-100000
assert reads[-1]==(sp+0x10,8) and reads[3]==(0xfffff78000000014,12) and len(reads)==6
assert result['system_time_100ns']==(45<<32)|123
assert run(dict(context,cpsr=4))[0]['available'], 'Actual Windows EL1t must be accepted, not only EL1h'
for status in (0,0xc000000d,0xc0000001):
    result,_=run(dict(context,x=[status]+[0]*30))
    assert result['return_ntstatus']==hex(status)
for value,kind in ((0,'zero:'),(100000,'absolute')):
    memory[sp+0x10]=struct.pack('<q',value)
    result,_=run();assert result['timeout_kind'].startswith(kind)
result,reads=run(selected=(pid,tid+4,thread))
assert not result['available'] and len(reads)==4 and all(address!=sp+0x10 for address,_ in reads)
assert 'return_ntstatus' not in result and result['system_time_100ns']==(45<<32)|123
for kwargs in (dict(ctx=dict(context,pc=base+1)),dict(ctx=dict(context,cpsr=0)),dict(ctx=dict(context,cpsr=9)),
               dict(ctx=dict(context,sp=sp+1)),dict(ctx=dict(context,sp=sp+8192)),dict(code=bytes(12))):
    try:run(**kwargs)
    except AssertionError:pass
    else:raise AssertionError('Invalid observation accepted')
text=ast.get_source_segment(source.read_text(),node)
assert 'write' not in text and 'resume' not in text and 'rpc' not in text
print('KERNEL_WAIT_RETURN=PASS: exact code/EL1/thread/stack slot, one status/timeout, no user data or writes; identity/range mismatches rejected')
