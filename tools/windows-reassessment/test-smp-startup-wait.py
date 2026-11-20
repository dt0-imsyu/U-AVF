"""Execute the real bounded read-only wait handler with synthetic KD transport."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
blocks=[n for n in ast.walk(tree) if isinstance(n,ast.If) and "SMP_STARTUP_WAIT_ENTRY" in ast.unparse(n.test) and "SMP_STARTUP_WAIT_EXIT" in ast.unparse(n.test)]
assert len(blocks)==1
program=compile(ast.fix_missing_locations(ast.Module(body=[ast.For(target=ast.Name(id='_',ctx=ast.Store()),iter=ast.Tuple(elts=[ast.Constant(0)],ctx=ast.Load()),body=[blocks[0]],orelse=[])],type_ignores=[])),'<actual-startup-wait-handler>','exec')
va=0xffff800000000000
pointer=va+0x100000
class FakeKD:
    processor=0
    def __init__(self):self.notes=[];self.reads=[];self.resumes=0
    def read(self,address,length):
        self.reads.append((address,length))
        if address==pointer and length==1:return b'\x01'
        assert length==8 and address in [va+c*4096+48 for c in range(1,8)]
        return struct.pack('<Q',1)
    def note(self,tag,**fields):self.notes.append((tag,fields))
    def resume(self):self.resumes+=1
    def write_memory(self,*args):raise AssertionError('Read-only handler cannot write memory')
def image_bytes(rva,length):
    expected={0x481e94:struct.pack('<2I',0x110006b5,0x17fffff7),0x481ebc:struct.pack('<I',0x2a1303e0)}
    assert len(expected[rva])==length
    return expected[rva]
for cpu in range(1,8):
    for tag in ('SMP_STARTUP_WAIT_ENTRY','SMP_STARTUP_STALL_RETURN','SMP_STARTUP_WAIT_EXIT'):
        kd=FakeKD();registers=[0]*31
        registers[0]=pointer;registers[20]=pointer;registers[19]=4
        probes={};removed=[]
        def probe(rva,name):probes[rva]=(1,name,b'original')
        def remove_probe(*args):removed.append(args)
        if tag=='SMP_STARTUP_WAIT_EXIT':probe(0x481e94,'SMP_STARTUP_STALL_RETURN')
        state=dict(tag=tag,a=SimpleNamespace(psci_startup_wait_probe=True,psci_final_start_state=False),psci_adapted=list(range(1,cpu+1)),kd=kd,registers=registers,runtime_mapping=(0xfffd0000,va),struct=struct,image_bytes=image_bytes,probe=probe,probes=probes,remove_probe=remove_probe)
        exec(program,state)
        assert kd.resumes==1 and kd.notes[0][1]['affinity']==cpu
        assert len(kd.reads)==cpu+1 and kd.notes[0][1]['startup_flag']==1
        if tag=='SMP_STARTUP_WAIT_ENTRY':assert set(probes)=={0x481e94,0x481ebc}
        if tag=='SMP_STARTUP_WAIT_EXIT':assert not probes and len(removed)==1
        kd.processor=1
        try:exec(program,state)
        except AssertionError:pass
        else:raise AssertionError('Must reject AP context')
print('ACTUAL_BSP_WAIT_HANDLER_21_CASES_READ_ONLY_AND_AP_REJECTION_PASS')
