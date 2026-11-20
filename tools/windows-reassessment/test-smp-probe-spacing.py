"""Execute actual probe gate; neighboring SMP checkpoints must be sequential."""
import ast
import struct
from pathlib import Path
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
function=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='probe']
assert len(function)==1
class FakeKD:
    def read(self,address,length):assert length==4;return b'abcd'
    def rpc(self,*args):return bytes(24)+struct.pack('<I',1)
    def note(self,*args,**kwargs):pass
state=dict(base=0x100000,probes={},kernel=b'',sections=[],image_bytes=lambda *args:b'abcd',struct=struct,kd=FakeKD())
exec(compile(ast.fix_missing_locations(ast.Module(body=function,type_ignores=[])),'<actual-probe-spacing>','exec'),state)
probe=state['probe'];probes=state['probes']
probe(0xb362dc,'SMP_POST_START_PPM_BOUNDARY')
for rva in (0xb362e0,0xb362e8):
    try:probe(rva,'SMP_RETURN')
    except AssertionError:pass
    else:raise AssertionError('Adjacent SMP probe accepted')
probes.clear();probe(0xb362e0,'SMP_PPM_ENGINE_RETURN')
probes.clear();probe(0xb362e8,'SMP_PPM_PHASE_COMPLETE')
probe(0xb36374,'SMP_SECOND_HAL_INIT_COMPLETE')
probe(0xb363bc,'SMP_START_ALL_RETURN')
print('ACTUAL_SMP_FORWARD_WINDOW_SPACING_AND_SEQUENTIAL_RETURN_PROBES_PASS')
