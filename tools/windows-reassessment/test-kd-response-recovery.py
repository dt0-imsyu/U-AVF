"""Test actual observer methods without importing its boot/start side effects.

No VM, socket, file write, Windows mutation or Android command is performed.
These tests prove host packet handling only, not UART delivery/runtime success.
"""
import ast
from pathlib import Path
import struct
import time

source = Path(__file__).with_name("trace-kd-boot-drivers.py")
tree = ast.parse(source.read_text())
owner = next(c for c in tree.body if isinstance(c, ast.ClassDef)
             and any(isinstance(n, ast.FunctionDef) and n.name == "packet" for n in c.body))
methods = [n for n in owner.body if isinstance(n, ast.FunctionDef)
           and n.name in ("packet", "control", "retry_pending", "rpc", "recover_boot_unload_epoch", "recover_dump_unload_continue_epoch")]
module = ast.fix_missing_locations(ast.Module(body=[ast.ClassDef(
    name="Replay", bases=[], keywords=[], body=methods, decorator_list=[])], type_ignores=[]))
namespace = dict(struct=struct, time=time)
exec(compile(module, str(source), "exec"), namespace)

probe = namespace["Replay"]()
probe.deadline = time.monotonic() + 2
probe.gate = True
probe.pid = 0x80800001
probe.last_rx = None
probe.last_ack = struct.pack("<IHHII", 0x69696969, 4, 0, 0x80800000, 0)
previous_ack = probe.last_ack
probe.last_tx = b"pending-rpc"
probe.pending_tx = dict(packet=probe.last_tx, pid=0x80800000, kind=2,
                        sent=time.monotonic(), retries=0)
probe.writes, probe.notes = [], []
probe.write = probe.writes.append
probe.note = lambda tag, **fields: probe.notes.append(tag)
body = bytes(56)
probe.buf = bytearray(struct.pack("<IHHII", 0x69696969, 5, 0, 0, 0)
                      + struct.pack("<IHHII", 0x30303030, 2, len(body), 0x80800001, sum(body))
                      + body + b"\xaa")
kind, actual = probe.packet()
assert kind == 2 and actual == body
assert probe.writes[:2] == [previous_ack, b"pending-rpc"]
assert "KD_RESPONSE_ACK_REPLAY" in probe.notes


class MemoryResponse:
    def __init__(self, api):
        self.sent, self.notes = [], []
        self.responses = [self.body(api, 0x2000), self.body(api, 0x1000)]

    def body(self, api, address):
        return (struct.pack("<IHHI", api, 0, 0, 0) + bytes(4)
                + struct.pack("<QII", address, 4, 4) + bytes(24) + b"data")

    def request(self, api, union):
        self.sent.append((api, union))

    def packet(self):
        return 2, self.responses.pop(0)

    def note(self, tag, **fields):
        self.notes.append(tag)


union = struct.pack("<QII", 0x1000, 4, 0)
probe = MemoryResponse(0x3130)
actual = namespace["Replay"].rpc(probe, 0x3130, union)
assert struct.unpack_from("<Q", actual, 16)[0] == 0x1000 and len(probe.sent) == 2
probe = MemoryResponse(0x3131)
try:
    namespace["Replay"].rpc(probe, 0x3131, union)
except ValueError:
    pass
else:
    raise AssertionError("Stale-address write was accepted")
assert len(probe.sent) == 1
print("KD_ACK_REPLAY_ORDER_AND_STALE_MEMORY_RESPONSE_TESTS=PASS")

class TimedOutRead(MemoryResponse):
    def __init__(self, api):
        super().__init__(api)
        self.deadline=time.monotonic()+30
        self.controls=[]
        self.responses=[TimeoutError('cached epoch'),self.body(api,0x1000)]
    def control(self,kind):self.controls.append(kind)
    def packet(self):
        response=self.responses.pop(0)
        if isinstance(response,Exception):raise response
        return 2,response

probe=TimedOutRead(0x3130)
assert namespace['Replay'].rpc(probe,0x3130,union)[56:]==b'data'
assert probe.controls==[6] and len(probe.sent)==2
for api in (0x3131,0x3133,0x3136):
    probe=TimedOutRead(api)
    try:namespace['Replay'].rpc(probe,api,union)
    except TimeoutError:pass
    else:raise AssertionError('Ambiguous mutation retried after timeout')
    assert not probe.controls and len(probe.sent)==1
print('KD_BOUNDED_READ_ONLY_EPOCH_RECOVERY=PASS')

probe=namespace['Replay']();probe.notes=[];probe.writes=[]
probe.note=lambda tag,**fields:probe.notes.append(tag);probe.write=probe.writes.append
state=bytearray(80);struct.pack_into('<I',state,0,0x3031)
struct.pack_into('<Q',state,40,0xffffffffffffffff);state[64]=1
probe.last_state_change=state
probe.last_tx=struct.pack('<IHHII',0x30303030,2,56,0x80800000,0)+struct.pack('<I',0x3136)+bytes(52)+b'\xaa'
assert probe.recover_boot_unload_epoch() and len(probe.writes)==1
assert not probe.recover_boot_unload_epoch()
for api,unloading in ((0x3131,1),(0x3130,1),(0x3136,0)):
    probe.boot_unload_epoch_recovered=False;state[64]=unloading
    probe.last_tx=struct.pack('<IHHII',0x30303030,2,56,0x80800000,0)+struct.pack('<I',api)+bytes(52)+b'\xaa'
    assert not probe.recover_boot_unload_epoch()
print('KD_EXACT_BOOT_UNLOAD_SINGLE_RECOVERY_GUARDS=PASS')

def dump_probe(path='dump_viostor.sys',api=0x3136,resends=2,unloading=1):
    probe=namespace['Replay']();probe.notes=[];probe.writes=[]
    probe.note=lambda tag,**fields:probe.notes.append(tag);probe.write=probe.writes.append
    name=path.encode()+b'\0';state=bytearray(80)+name
    struct.pack_into('<I',state,0,0x3031);struct.pack_into('<I',state,32,len(name));state[64]=unloading
    probe.last_state_change=state
    packet=struct.pack('<IHHII',0x30303030,2,56,0x80800001,0)+struct.pack('<I',api)+bytes(52)+b'\xaa'
    probe.pending_tx=dict(packet=packet,kind=2,target_resends=resends)
    return probe

for path in ('dump_dumpfve.sys','dump_viostor.sys','dump_storport.sys'):
    probe=dump_probe(path)
    assert probe.recover_dump_unload_continue_epoch()
    assert probe.writes==[struct.pack('<IHHII',0x69696969,6,0,0,0)]
    assert probe.pending_tx is None and not probe.recover_dump_unload_continue_epoch()
for probe in (dump_probe(api=0x3131),dump_probe(api=0x3130),dump_probe(resends=1),
              dump_probe(unloading=0),dump_probe(path='arbitrary.sys')):
    assert not probe.recover_dump_unload_continue_epoch() and not probe.writes
print('KD_CAPTURED_DUMP_UNLOAD_RESET_ONLY_GUARDS=PASS')
