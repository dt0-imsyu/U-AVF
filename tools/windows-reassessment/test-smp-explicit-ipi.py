"""Execute exact capability A/B: one bit, no CPU/timer/barrier/count mutation."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
gate=[n for n in tree.body if isinstance(n,ast.Assert) and isinstance(n.msg,ast.Constant) and n.msg.value=='Explicit IPI A/B requires exact final-state profile, excludes minimal observer']
assert len(gate)==1
gate_code=compile(ast.fix_missing_locations(ast.Module(body=gate,type_ignores=[])),'<actual-explicit-gate>','exec')
for enabled,parent,minimal in ((False,False,False),(True,False,False),(True,True,True),(True,True,False)):
    try:exec(gate_code,dict(a=SimpleNamespace(psci_explicit_ipi_ab=enabled,psci_final_start_state=parent,psci_post_start_minimal=minimal)))
    except AssertionError:assert enabled and (not parent or minimal)
    else:assert not enabled or (parent and not minimal)
functions=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='smp_explicit_ipi_ab']
assert len(functions)==1
code=compile(ast.fix_missing_locations(ast.Module(body=functions,type_ignores=[])),'<actual-explicit-ipi>','exec')
base=0xffff800000000000;controller=base+0x300000
class KD:
    processor=0
    def __init__(self,flags):self.flags=flags;self.writes=[]
    def read(self,address,length):
        if address==base+0xc100e0:assert length==8;return struct.pack('<Q',controller)
        assert address==controller+0xf4 and length==4
        return struct.pack('<I',self.flags)
    def write_memory(self,address,data):
        assert address==controller+0xf4 and data==struct.pack('<I',0x80b)
        self.writes.append((address,data));self.flags=struct.unpack('<I',data)[0]
    def note(self,*args,**kw):pass
def image_bytes(rva,length):
    instructions={0x484d98:(0xb940f509,0x362800c9),0x484dd0:(0x794167e1,0xb0004c28,0x91028102,0x9102c3e0)}[rva]
    assert length==len(instructions)*4
    return struct.pack('<'+'I'*len(instructions),*instructions)
for flags,affinities in ((0x82b,range(8)),(0x80b,range(8)),(0x82b,range(1,9))):
    kd=KD(flags)
    state=dict(a=SimpleNamespace(psci_explicit_ipi_ab=True),kd=kd,struct=struct,base=base,image_bytes=image_bytes,smp_gic_mapped_snapshot=lambda:dict(cpus=[dict(mpidr=hex(x)) for x in affinities]))
    exec(code,state)
    try:state['smp_explicit_ipi_ab']()
    except AssertionError:assert flags!=0x82b or set(affinities)!=set(range(8));assert not kd.writes
    else:assert len(kd.writes)==1 and kd.flags==0x80b
print('ACTUAL_EXPLICIT_AFFINITY_ONE_BIT_AND_NEGATIVE_GUARDS_PASS')
