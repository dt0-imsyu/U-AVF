"""Run actual own-publication return handler; prove exact saved ABI restoration."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
blocks=[n for n in ast.walk(tree) if isinstance(n,ast.If) and "a.psci_cache_publish" in ast.unparse(n.test) and "getattr(kd, 'psci_publication'" in ast.unparse(n.test)]
assert len(blocks)==1
program=compile(ast.fix_missing_locations(ast.Module(body=[ast.For(target=ast.Name(id='_',ctx=ast.Store()),iter=ast.Tuple(elts=[ast.Constant(0)],ctx=ast.Load()),body=[blocks[0]],orelse=[])],type_ignores=[])),'<actual-own-publish-return>','exec')
va=0xffff800000000000
helper=struct.pack('<10I',0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0x91008210,0xd50b7a30,0xd5033f9f,0xd5033fdf,0xd43e0000)
class FakeKD:
    processor=0
    def __init__(self,saved,cpu):self.psci_publication=(saved,cpu,va);self.restored=None;self.resumed=False
    def read(self,address,length):assert (address,length)==(va+512,40);return helper
    def restore_context(self,data):self.restored=data
    def note(self,tag,**fields):assert tag=='OWN_TRAMPOLINE_POC_PUBLISHED' and fields['bytes']==128
    def resume(self):self.resumed=True
for cpu in range(1,8):
    saved=bytearray(912)
    original=[0x100000+i for i in range(31)]
    for i,value in enumerate(original):struct.pack_into('<Q',saved,8+8*i,value)
    struct.pack_into('<Q',saved,256,va+0x100000)
    struct.pack_into('<Q',saved,264,va+0x4aaf1c)
    saved=bytes(saved)
    registers=original[:];registers[16]=va+cpu*4096+96
    kd=FakeKD(saved,cpu)
    state=dict(a=SimpleNamespace(psci_cache_publish=True),kd=kd,code=0x80000003,pc=va+548,registers=registers,ctx=dict(sp=va+0x100000),struct=struct)
    exec(program,state)
    assert kd.restored==saved and kd.resumed and kd.psci_publication is None
    for wrong in ('processor','pc','gpr','sp'):
        kd=FakeKD(saved,cpu);state['kd']=kd;state['pc']=va+548;state['ctx']=dict(sp=va+0x100000);state['registers']=registers[:]
        if wrong=='processor':kd.processor=1
        if wrong=='pc':state['pc']+=4
        if wrong=='gpr':state['registers'][0]+=1
        if wrong=='sp':state['ctx']['sp']+=16
        try:exec(program,state)
        except AssertionError:pass
        else:raise AssertionError('Wrong helper context accepted: '+wrong)
        assert kd.restored is None and not kd.resumed
print('ACTUAL_OWN_PUBLISH_CONTEXT_ALL7_ABI_RESTORATIONS_AND28_REJECTIONS_PASS')
