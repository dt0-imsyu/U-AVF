"""Offline virtual-clock test of actual KD.packet; never starts a VM/ADB."""
import ast
import io
import queue
import struct
from pathlib import Path
from types import SimpleNamespace

source = Path(__file__).with_name('trace-kd-boot-drivers.py')
tree = ast.parse(source.read_text())
definition = next(node for node in tree.body if isinstance(node, ast.ClassDef) and node.name == 'KD')
clock = SimpleNamespace(now=0.0)
namespace = dict(time=SimpleNamespace(monotonic=lambda: clock.now), queue=queue,
                 struct=struct, re=__import__('re'))
exec(compile(ast.Module(body=[definition], type_ignores=[]), str(source), 'exec'), namespace)
KD = namespace['KD']

def data_packet(kind, body, pid):
    return struct.pack('<IHHII', 0x30303030, kind, len(body), pid, sum(body)) + body + b'\xaa'

file_request = bytearray(64)
struct.pack_into('<I', file_request, 0, 0x3430)
traffic = [(20.0, data_packet(11, bytes(file_request), 0x80800000)),
           (40.0, data_packet(3, bytes(16) + b'loader alive', 0x80800001)),
           (65.0, data_packet(7, bytes(80), 0x80800000))]

class ScheduledQueue:
    def __init__(self, items): self.items = list(items)
    def get(self, timeout):
        clock.now += timeout
        if self.items and self.items[0][0] <= clock.now:
            return self.items.pop(0)[1]
        raise queue.Empty

def observer(items, deadline):
    clock.now = 0.0
    kd = KD.__new__(KD)
    kd.q = ScheduledQueue(items); kd.buf = bytearray(); kd.gate = True
    kd.pid = 0x80800000; kd.last_rx = None; kd.last_tx = None
    kd.pending_tx = None
    kd.deadline = deadline; kd.rx = io.BytesIO()
    kd.write = lambda data: None
    kd.note = lambda *args, **kwargs: None
    kd.user_fault_record = None; kd.user_fault_context = None
    return kd

assert observer(traffic, 100.0).packet(timeout=30)[0] == 7
assert 65 <= clock.now < 66
for items, deadline, expected in (([], 100.0, 30.0), (traffic, 55.0, 55.0)):
    try: observer(items, deadline).packet(timeout=30)
    except TimeoutError: pass
    else: raise AssertionError('Timeout bound lost')
    assert expected <= clock.now < expected + 1
print('PASS: active FILE_IO/PRINT extends inactivity window; silence and global deadline remain bounded')

try:observer(traffic,100.0).packet(timeout=30,wake_at=45.0)
except TimeoutError:pass
else:raise AssertionError('Loader activity starved scheduled runtime observation')
assert 45<=clock.now<46
print('PASS: explicit scheduled observation remains bounded despite active loader FILE_IO/PRINT; ordinary RPC behavior unchanged')

def control_packet(kind, pid=0):
    return struct.pack('<IHHII', 0x69696969, kind, 0, pid, 0)

def replay_case(extra, state_at):
    messages=[(0.2,data_packet(11,bytes(file_request),0x80800000))]+extra
    messages.append((state_at,data_packet(7,bytes(80),0x80800001)))
    kd=observer(messages,20.0); writes=[]; kd.write=writes.append
    assert kd.packet(timeout=10)[0]==7
    frames=[x for x in writes if x.startswith(b'0000')]
    assert frames and all(x==frames[0] for x in frames), 'Retry changed packet/ID'
    assert kd.pending_tx is None, 'New target progress did not settle pending send'
    return kd,writes,frames

kd,writes,frames=replay_case([],5.0)
assert len(frames)==4, 'Initial packet plus exactly three automatic replays'
kd,writes,frames=replay_case([(0.4,control_packet(5)),(0.8,control_packet(4,0x80800000))],5.0)
assert len(frames)==2, 'Explicit resend then matching ACK must cancel future retries'
kd,writes,frames=replay_case([(0.4,control_packet(6))],5.0)
assert len(frames)==1, 'RESET must cancel stale pending packet'
kd,writes,frames=replay_case([(0.4,data_packet(11,bytes(file_request),0x80800000)),
                            (0.8,control_packet(4,0x80800000))],5.0)
assert len(frames)==2, 'Duplicate FILE_IO must replay cached packet, not advance ID'
print('PASS: bounded byte-identical replay; explicit RESEND/duplicate; ACK/RESET cancellation')

state=bytes(80)
reply=struct.pack('<I',0x3130)+bytes(52)
kd=observer([],20.0);kd.last_state_change=state;requests=[]
kd.request=lambda api,union=b'':requests.append(api)
responses=iter([(7,state),(2,reply)])
kd.packet=lambda:next(responses)
# Actual memory RPCs always carry an address/length union. Preserve the newer
# response-range guard rather than testing an impossible empty memory request.
assert kd.rpc(0x3130,struct.pack('<QII',0,0,0))==reply and requests==[0x3130,0x3130]
for api,new_state in ((0x3131,state),(0x3130,b'x'+state[1:])):
    kd=observer([],20.0);kd.last_state_change=state
    kd.request=lambda *args:None;kd.packet=lambda:(7,new_state)
    try:kd.rpc(api)
    except ValueError:pass
    else:raise AssertionError('Ambiguous write/new state must not be retried')
print('PASS: identical stopped-state retry is limited to reads; new stops/writes fail closed')

kd=observer([],20.0);memory={};writes=[]
def ram_rpc(api,union=b'',allow_status=False,extra=b''):
    assert api==0x3131
    address,n,_=struct.unpack('<QII',union)
    assert n==len(extra)<=32
    memory.update({address+i:b for i,b in enumerate(extra)});writes.append((address,n))
    reply=bytearray(56);struct.pack_into('<I',reply,28,n);return bytes(reply)
kd.rpc=ram_rpc;kd.read=lambda address,n:bytes(memory[address+i] for i in range(n))
payload=bytes(range(244));kd.write_memory(0x10000,payload)
assert writes==[(0x10000+i,min(32,244-i)) for i in range(0,244,32)]
print('PASS: bounded32-byte RAM writes preserve244-byte thunk and verify every segment')
