"""Actual minimal-observer gate and branch; only observation changes."""
import ast
import struct
from pathlib import Path
from types import SimpleNamespace
tree=ast.parse(Path(__file__).with_name('trace-kd-boot-drivers.py').read_text())
gate=[n for n in tree.body if isinstance(n,ast.Assert) and isinstance(n.msg,ast.Constant) and n.msg.value=='Minimal post-start observation requires exact native final-state profile']
assert len(gate)==1
gate_code=compile(ast.fix_missing_locations(ast.Module(body=gate,type_ignores=[])),'<actual-minimal-gate>','exec')
for enabled,parent in ((False,False),(False,True),(True,False),(True,True)):
    try:exec(gate_code,dict(a=SimpleNamespace(psci_post_start_minimal=enabled,psci_final_start_state=parent)))
    except AssertionError:assert enabled and not parent
    else:assert not enabled or parent
branches=[n for n in ast.walk(tree) if isinstance(n,ast.If) and ast.unparse(n.test)=='a.psci_post_start_minimal']
assert len(branches)==1
code=compile(ast.fix_missing_locations(ast.Module(body=branches,type_ignores=[])),'<actual-minimal-branch>','exec')
for enabled in (False,True):
    probes=[];notes=[]
    def image_bytes(rva,length):assert rva==0xb363bc and length==4;return struct.pack('<I',0xd65f03c0)
    exec(code,dict(a=SimpleNamespace(psci_post_start_minimal=enabled),struct=struct,image_bytes=image_bytes,probe=lambda *x:probes.append(x),kd=SimpleNamespace(note=lambda *x,**kw:notes.append((x,kw)))))
    assert probes==[(0xb363bc,'SMP_START_ALL_RETURN')] if enabled else probes==[(0xb36084,'SMP_FINAL_START_HANDSHAKE')]
print('ACTUAL_MINIMAL_OBSERVER_GATE_AND_NO_PLATFORM_MUTATION_PASS')
