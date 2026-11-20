"""Actual post-start observations read only matching public kernel fields."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
blocks=[n for n in ast.walk(tree) if isinstance(n,ast.If) and "SMP_POST_START_PPM_BOUNDARY" in ast.unparse(n.test) and "SMP_START_ALL_RETURN" in ast.unparse(n.test)]
assert len(blocks)==1
program=compile(ast.fix_missing_locations(ast.Module(body=[ast.For(target=ast.Name(id='_',ctx=ast.Store()),iter=ast.Tuple(elts=[ast.Constant(0)],ctx=ast.Load()),body=blocks,orelse=[])],type_ignores=[])),'<actual-post-start>','exec')
base=0xffff800000000000
class FakeKD:
    processor=0
    def __init__(self,count,barrier):self.count=count;self.barrier=barrier;self.reads=[];self.notes=[];self.resumed=False
    def read(self,address,length):
        self.reads.append((address,length));assert length==4
        assert address in (base+0xe08a80,base+0xe08c38)
        return struct.pack('<I',self.count if address==base+0xe08a80 else self.barrier)
    def note(self,tag,**fields):self.notes.append((tag,fields))
    def resume(self):self.resumed=True
for tag in ('SMP_POST_START_PPM_BOUNDARY','SMP_PPM_ENGINE_RETURN','SMP_PPM_PHASE_COMPLETE','SMP_SCHED_CONFIG_CALL','SMP_SCHED_CONFIG_RETURN','SMP_SECOND_HAL_CALL','SMP_SECOND_HAL_INIT_COMPLETE','SMP_START_ALL_RETURN'):
    for count,barrier in ((8,1),(8,0),(1,1),(9,1),(8,2)):
        kd=FakeKD(count,barrier)
        probes=[]
        def probe(*args):probes.append(args)
        def image_bytes(rva,length):
            assert length==4
            return struct.pack('<I',{0xb362e0:0x37f80700,0xb362e8:0x97fd485a,0xb36354:0x9400022b,0xb36358:0xd0000648,0xb36370:0xd63f01e0,0xb36374:0x52800028,0x4f895c:0x97f5f559}[rva])
        registers=[0]*31;registers[15]=base+0x4cdb50
        if tag=='SMP_SECOND_HAL_CALL':registers[0]=1
        state=dict(tag=tag,a=SimpleNamespace(psci_final_start_state=True),psci_adapted=list(range(1,8)),kd=kd,struct=struct,base=base,registers=registers,probe=probe,image_bytes=image_bytes)
        try:exec(program,state)
        except AssertionError:assert count==9 or barrier==2
        else:
            assert count<=8 and barrier in (0,1) and kd.resumed
            assert kd.notes[0][1]['ke_number_processors']==count
            if tag=='SMP_POST_START_PPM_BOUNDARY':assert probes==[(0xb362e0,'SMP_PPM_ENGINE_RETURN')]
            if tag=='SMP_PPM_ENGINE_RETURN':assert probes==[(0xb362e8,'SMP_PPM_PHASE_COMPLETE')]
        assert kd.reads==[(base+0xe08a80,4),(base+0xe08c38,4)]
print('ACTUAL_POST_START_TWO_PUBLIC_FIELDS_AND_BOUNDS_PASS')
