"""Actual admitted112-block plan, every mixed prefix; no device access."""
import importlib.util
import sys
from pathlib import Path

spec=importlib.util.spec_from_file_location('recovery',Path(__file__).with_name('recover-eight-thread-codec.py'))
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
m=r.load();m.select_codec('x264','--native-gop' in sys.argv);_,blocks=m.read_plan(Path(sys.argv[1]))
checks=0
for prefix in range(len(blocks)+1):
    memory={offset:(old if i<prefix else new) for i,(offset,old,new) in enumerate(blocks)}
    def read(offset,size):
        assert len(memory[offset])==size
        return memory[offset]
    def write(offset,data):
        memory[offset]=data
    states,pending=r.classify(blocks,read)
    assert len(states)==112 and len(pending)==sum(i>=prefix and old!=new for i,(_,old,new) in enumerate(blocks))
    def finish():
        assert all(memory[offset]==old for offset,old,_ in blocks)
    m.transaction.transact(pending,read,write,lambda:None,finish,rollback=True)
    checks+=1
for offset,old,new in blocks:
    unknown=bytes([old[0]^255])+old[1:]
    if unknown==new:unknown=bytes([old[0]^127])+old[1:]
    memory={o:a for o,a,_ in blocks};memory[offset]=unknown
    try:r.classify(blocks,read)
    except AssertionError:checks+=1
    else:raise AssertionError('Unknown block accepted')
print(f'ACTUAL112_BLOCK_ALL_MIXED_PREFIX_RECOVERY_UNKNOWN_REJECTION={checks} PASS; DEVICE_WRITES=0')
