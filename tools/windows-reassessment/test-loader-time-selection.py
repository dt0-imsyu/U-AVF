"""Execute the actual typed observer branch without a VM or boot side effects."""
import ast
import struct
from pathlib import Path
source=Path(__file__).with_name('trace-kd-boot-drivers.py')
tree=ast.parse(source.read_text())
branch=next(node for node in ast.walk(tree) if isinstance(node,ast.If) and
    isinstance(node.test,ast.Compare) and isinstance(node.test.left,ast.Name) and
    node.test.left.id=='tag' and any(isinstance(c,ast.Constant) and c.value=='LOADER_TIME_SELECTION' for c in node.test.comparators))
module=ast.fix_missing_locations(ast.Module(body=[ast.For(target=ast.Name(id='_',ctx=ast.Store()),
    iter=ast.Tuple(elts=[ast.Constant(0)],ctx=ast.Load()),body=[branch],orelse=[])],type_ignores=[]))
code=compile(module,str(source),'exec')
sp=0x80000000;pointer=0xfffff8024389ec18;base=0x80983000
calendar=134361452702395218;persisted=193076811012898812
efi=struct.pack('<H5BxIhBB',2026,10,11,9,0,1,0,2047,0,0)
class Reader:
    def __init__(self):self.notes=[];self.resumed=0;self.reads=[]
    def read(self,address,size):
        self.reads.append((address,size))
        return {(sp+0x30,16):efi,(pointer+0xa48,8):struct.pack('<Q',persisted),
                (sp+0x18,8):struct.pack('<Q',calendar),(sp+0x28,8):struct.pack('<Q',persisted)}[(address,size)]
    def note(self,tag,**fields):self.notes.append((tag,fields))
    def resume(self):self.resumed+=1
registers=[0]*31;registers[20]=pointer;registers[21]=calendar;registers[22]=1
reader=Reader();env=dict(struct=struct,tag='LOADER_TIME_SELECTION',loader_time_seen=False,
    pc=base+0x2e3c8,loader_time_base=base,ctx={'sp':sp},registers=registers,kd=reader)
exec(code,env)
result=reader.notes[0][1]
assert result['efi_fields']['year']==2026 and result['selected_calendar_100ns']==persisted
assert result['bl_candidate_100ns']==calendar and result['persisted_candidate_100ns']==persisted
assert len(reader.reads)==4 and reader.resumed==1 and env['loader_time_seen']
for key,value in (('loader_time_seen',True),('ctx',{'sp':sp+1}),('pc',base+0x2e3cc)):
    invalid=dict(env);invalid['loader_time_seen']=False;invalid[key]=value
    try:exec(code,invalid)
    except AssertionError:pass
    else:raise AssertionError('Invalid typed observation accepted')
print('LOADER_TYPED_CLOCK_ONLY_EXACT_POINT_BOUNDS=PASS')

remove=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='remove_probe')
remove_code=compile(ast.fix_missing_locations(ast.Module(body=[remove],type_ignores=[])),str(source),'exec')
expected_delete=struct.pack('<4I',0xd2800308,0x9ba87c14,0xd0000748,0x91128113)
expected_instruction=struct.pack('<I',0x91298289)
class RemovedProbe:
    def __init__(self,flags=0):self.flags=flags
    def rpc(self,api,union,allow_status=False):
        assert api==0x3135 and union==struct.pack('<I',1) and allow_status
        return bytes(8)+struct.pack('<I',0xc0000001)
    def read(self,address,size):
        return {(base+0x21d720,16):expected_delete,(base+0x3074a0,4):struct.pack('<I',self.flags),
                (base+0x2e3c8,4):expected_instruction}[(address,size)]
    def note(self,*args,**kwargs):pass
def image_bytes(rva,size,*args):
    assert (rva,size)==(0x21d720,16)
    return expected_delete
namespace=dict(struct=struct,base=None,loader_time_base=base,loader_time_image=None,loader_time_layout=None,
    image_bytes=image_bytes,kd=RemovedProbe())
exec(remove_code,namespace)
namespace['remove_probe'](base+0x2e3c8,1,expected_instruction)
namespace['kd']=RemovedProbe(flags=1)
try:namespace['remove_probe'](base+0x2e3c8,1,expected_instruction)
except AssertionError:pass
else:raise AssertionError('Active loader breakpoint incorrectly accepted as removed')
print('LOADER_AUTO_REMOVED_EXACT_TABLE_CODE_READBACK_GUARDS=PASS')
