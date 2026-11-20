"""Actual last-common startup snapshot is read-only and restores KD selection."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
block=[n for n in ast.walk(tree) if isinstance(n,ast.If) and ast.unparse(n.test)=="tag == 'SMP_FINAL_START_HANDSHAKE'"]
assert len(block)==1
program=compile(ast.fix_missing_locations(ast.Module(body=[ast.For(target=ast.Name(id='_',ctx=ast.Store()),iter=ast.Tuple(elts=[ast.Constant(0)],ctx=ast.Load()),body=block,orelse=[])],type_ignores=[])),'<actual-final-start-state>','exec')
base=0xffff800000000000
class FakeKD:
    processor=0
    last_context=b'original BSP context'
    def __init__(self,fail=None):self.fail=fail;self.notes=[];self.resumed=False;self.reads=[]
    def read(self,address,length):
        self.reads.append((address,length))
        assert length==8
        if address==base+0x1000:return struct.pack('<Q',base+0x2000)
        assert address==base+0x2088
        return struct.pack('<Q',base+0x3000)
    def context(self):
        if self.processor==self.fail:raise ValueError('Unsupported CPU read')
        self.last_context=b'CPU-specific read'
        return dict(pc=base+0x100*self.processor,sp=base+0x10000,cpsr=0x3c5)
    def note(self,tag,**fields):self.notes.append((tag,fields))
    def resume(self):self.resumed=True
    def write_memory(self,*args):raise AssertionError('No writes')
    def restore_context(self,*args):raise AssertionError('Read-only snapshot must not change guest context')
registers=[0]*31;registers[0]=4;registers[26]=base+0x1000
for fail in (None,3):
    kd=FakeKD(fail);probes=[]
    def image_bytes(rva,length):assert (rva,length)==(0xb360b0,4);return struct.pack('<I',0xaa1303e0)
    def probe(*args):probes.append(args)
    state=dict(tag='SMP_FINAL_START_HANDSHAKE',a=SimpleNamespace(psci_final_start_state=True),psci_adapted=list(range(1,8)),kd=kd,registers=registers,struct=struct,base=base,image_bytes=image_bytes,probe=probe)
    try:exec(program,state)
    except ValueError:assert fail==3
    else:
        assert fail is None and kd.resumed and len(kd.notes[0][1]['snapshots'])==8
        assert probes==[(0xb360b0,'SMP_FINAL_START_HANDSHAKE_COMPLETE')]
    assert kd.processor==0 and kd.last_context==b'original BSP context'
    assert kd.reads==[(base+0x1000,8),(base+0x2088,8)]
print('ACTUAL_FINAL_START_STATE_READ_ONLY_ALL8_AND_FAILURE_SELECTION_RESTORE_PASS')
