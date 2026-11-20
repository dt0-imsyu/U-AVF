"""Execute actual read-only time IPI observation branches with bounded mocks."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
base=0xffff800000000000
for tag in ('SMP_TIME_IPI_CALL','SMP_TIME_IPI_WAIT','SMP_TIME_IPI_WAIT_LOOP','SMP_TIME_IPI_RETURN'):
    blocks=[n for n in ast.walk(tree) if isinstance(n,ast.If) and ast.unparse(n.test)==f"tag == '{tag}'"]
    assert len(blocks)==1
    program=compile(ast.fix_missing_locations(ast.Module(body=[ast.For(target=ast.Name(id='_',ctx=ast.Store()),iter=ast.Tuple(elts=[ast.Constant(0)],ctx=ast.Load()),body=blocks,orelse=[])],type_ignores=[])),'<actual-time-ipi>','exec')
    class KD:
        processor=0
        last_context=b'original'
        def __init__(self):self.notes=[];self.reads=[];self.cpus=[];self.physical=[];self.resumed=False
        def note(self,*args,**kw):self.notes.append((args,kw))
        def read(self,address,length):
            self.reads.append((address,length));assert address in (base+0x100000,base+0x100080) and length==4
            return struct.pack('<I',8 if address==base+0x100000 else 1)
        def physical_sample(self,address):
            self.physical.append(address)
            return dict(address=hex(address),status='0x0',data='00000000')
        def context(self):
            self.cpus.append(self.processor);self.last_context=b'transient'
            return dict(pc=base+0x27b218,sp=base+0x200000,cpsr=0x345)
        def resume(self):self.resumed=True
    kd=KD();probes=[];registers=[0]*31
    registers[0]=base+0xa982e0;registers[1]=base+0x300000
    registers[19]=base+0x100000;registers[24]=base+0xa982e0
    def image_bytes(rva,length):
        assert length==4
        return struct.pack('<I',{0x275f3c:0xb9400268,0x275f70:0xb9400268,0x4f8960:0x52800020}[rva])
    state=dict(tag=tag,a=SimpleNamespace(psci_final_start_state=True,psci_explicit_ipi_ab=False),kd=kd,psci_adapted=list(range(1,8)),registers=registers,ctx=dict(sp=base+0x300000),base=base,struct=struct,image_bytes=image_bytes,probe=lambda *args:probes.append(args),smp_gic_mapped_snapshot=lambda:dict(count=8))
    exec(program,state)
    assert kd.resumed and kd.processor==0
    if tag=='SMP_TIME_IPI_WAIT':
        assert kd.cpus==list(range(8)) and kd.last_context==b'original'
        assert kd.reads==[(base+0x100000,4)] and kd.notes[0][1]['pending']==8
    elif tag=='SMP_TIME_IPI_CALL':
        assert probes==[(0x275f3c,'SMP_TIME_IPI_WAIT'),(0x4f8960,'SMP_TIME_IPI_RETURN')]
    elif tag=='SMP_TIME_IPI_WAIT_LOOP':
        assert kd.reads==[(base+0x100000,4),(base+0x100080,4)]
        assert kd.physical==[] and kd.notes[0][1]['gic']==dict(count=8)
        assert kd.notes[0][1]['iterations']==1
    assert not kd.reads or tag in ('SMP_TIME_IPI_WAIT','SMP_TIME_IPI_WAIT_LOOP')
print('ACTUAL_TIME_IPI_PUBLIC_COUNT_CPU_CONTEXT_READ_ONLY_PASS')
