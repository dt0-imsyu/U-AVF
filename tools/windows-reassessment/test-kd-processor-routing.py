"""Actual request/packet code: stopped CPU selection, not default BSP context."""
import ast
import struct
import time
from pathlib import Path

tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
owner=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='KD')
methods=[n for n in owner.body if isinstance(n,ast.FunctionDef) and n.name in ('request','packet','control')]
ns=dict(struct=struct,time=time)
exec(compile(ast.fix_missing_locations(ast.Module(body=[ast.ClassDef(name='Probe',bases=[],keywords=[],body=methods,decorator_list=[])],type_ignores=[])),'<actual-kd-cpu-routing>','exec'),ns)
probe=ns['Probe']();sent=[]
probe.send_data=lambda kind,body:sent.append((kind,body))
probe.request(0x315f)
assert struct.unpack_from('<H',sent[-1][1],6)[0]==0
probe.processor=7;probe.request(0x315f)
assert struct.unpack_from('<H',sent[-1][1],6)[0]==7
probe.deadline=time.monotonic()+2;probe.gate=True;probe.last_rx=None
probe.pending_tx=None;probe.write=lambda data:None;probe.note=lambda *args,**kwargs:None
state=bytearray(80);struct.pack_into('<IHHI',state,0,0x3030,0,1,8)
probe.buf=bytearray(struct.pack('<IHHII',0x30303030,7,len(state),0x80800001,sum(state))+state+b'\xaa')
assert probe.packet()[0]==7 and probe.processor==1
probe.request(0x315f)
assert struct.unpack_from('<H',sent[-1][1],6)[0]==1
print('ACTUAL_STOPPED_CPU1_AND_CPU7_KD_CONTEXT_ROUTING_PASS')
