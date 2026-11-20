"""Exercise actual observer inventory with bounded synthetic memory, no target."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace

source=Path(__file__).with_name('trace-kd-boot-drivers.py').read_text()
node=next(n for n in ast.parse(source).body if isinstance(n,ast.FunctionDef) and n.name=='hot_process_inventory')
process=0xffff800000001000
thread=0xffff800000003000
trap=0xffff800000005000
head=process+0x360
entry=thread+0x558
raw=bytearray(0x568)
struct.pack_into('<Q',raw,0x240,process)
struct.pack_into('<QQ',raw,0x558,head,head)
struct.pack_into('<QQ',raw,0x4e8,728,999)
struct.pack_into('<Q',raw,0x88,trap)
struct.pack_into('<I',raw,0x2ac,7)
struct.pack_into('<I',raw,0x2fc,123)
raw[0x17c]=2
memory={head:struct.pack('<Q',entry),head+8:struct.pack('<Q',entry),
        thread:bytes(raw),thread+0x240:struct.pack('<Q',process),
        trap+0x138:struct.pack('<QQQ',0x1020,0x9000,0x1010),
        0x1000:bytes(48),0x2018:struct.pack('<Q',0x3000),
        0x3010:struct.pack('<Q',0x3010)}
class Reader:
    def __init__(self):self.events=[];self.reads=[]
    def read(self,address,size):
        self.reads.append((address,size))
        result=memory[address]
        assert len(result)==size
        return result
    def note(self,tag,**fields):self.events.append((tag,fields))

def run(current_matches=True,owner_unavailable=False):
    kd=Reader()
    if owner_unavailable:
        original_read=kd.read
        def read(address,size):
            if address==thread+0x240:raise ValueError('KD status 0xc0000001 for 0x3130')
            return original_read(address,size)
        kd.read=read
    memory[thread+0x240]=struct.pack('<Q',process if current_matches else process+0x10000)
    scope={'kd':kd,'struct':struct,'a':SimpleNamespace(inspect_hot_process='lsass.exe'),
           'process_inventory':lambda:[dict(address=hex(process),pid=728,image='lsass.exe',peb='0x2000')]}
    exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual observer inventory>','exec'),scope)
    scope['hot_process_inventory'](thread)
    return kd

kd=run()
event=kd.events[-1][1]
assert event['threads'][0]['saved_pc']=='0x1010'
assert event['threads'][0]['user_ticks']==123
assert 'instruction_window' in event['threads'][0]
assert not any(address==0x9000 for address,size in kd.reads), 'No user stack read'
kd=run(False)
assert 'instruction_window' not in kd.events[-1][1]['threads'][0]
assert not any(address<0x800000000000 for address,size in kd.reads), 'No foreign address-space reads'
kd=run(owner_unavailable=True)
assert kd.events[-1][1]['threads'][0]['saved_pc']=='0x1010'
assert not kd.events[-1][1]['current_process_matches']
assert not any(address<0x800000000000 for address,size in kd.reads), 'No unknown address-space reads'
raw[0x240:0x248]=struct.pack('<Q',process+1)
memory[thread]=bytes(raw)
try:run()
except AssertionError:pass
else:raise AssertionError('Owner mismatch accepted')
print('HOT_PROCESS_INVENTORY_TEST_PASS: saved PC/counters, no credentials/stack, foreign-space guard, owner guard')
