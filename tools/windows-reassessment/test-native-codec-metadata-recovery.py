"""Actual pinned native plan, exact audited hints/date, reject every other byte."""
import importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location('recovery',Path(__file__).with_name('recover-eight-thread-codec.py'))
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
m=r.load();m.select_codec('x264',True)
_,blocks=m.read_plan(Path('E:/U-AVF/artifacts/idd-x264-fat-readonly-plan3-20261014'))
memory={o:bytearray(new) for o,old,new in blocks}
memory[1049088][488:496]=bytes.fromhex('59b40100df310000')
memory[9978368][274:276]=bytes.fromhex('4e5d')
def read(offset,size):assert len(memory[offset])==size;return bytes(memory[offset])
states,pending=r.classify(blocks,read,True);assert len(states)==112
checks=1
for offset in (1049088,9978368):
    for i in range(512):
        memory[offset][i]^=1
        try:r.classify(blocks,read,True)
        except AssertionError:checks+=1
        else:raise AssertionError('Unobserved metadata admitted')
        memory[offset][i]^=1
def write(offset,data):memory[offset]=bytearray(data)
def finish():assert all(bytes(memory[o])==old for o,old,new in blocks)
m.transaction.transact(pending,read,write,lambda:None,finish,rollback=True)
finish();print(f'EXACT_NATIVE_METADATA_RECOVERY_TESTS_PASS checks={checks}; DEVICE_WRITES=0')
