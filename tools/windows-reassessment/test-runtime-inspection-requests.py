"""Actual bounded request parser; no VM, socket, commands or filesystem writes."""
import ast
import json
from pathlib import Path
from types import SimpleNamespace

source=Path(__file__).with_name('trace-kd-boot-drivers.py')
node=next(n for n in ast.parse(source.read_text()).body if isinstance(n,ast.FunctionDef) and n.name=='runtime_inspection_request')
class Request:
    def __init__(self,content,size=64):self.content=content;self.size=size
    def exists(self):return True
    def stat(self):return SimpleNamespace(st_size=self.size)
    def read_text(self,encoding):return json.dumps(self.content)
class Root:
    def __init__(self,request):self.request=request
    def __truediv__(self,name):assert name=='runtime-inspect.request.json';return self.request
def run(content,previous=0,last=100,now=200,size=64,replacement=False):
    notes=[];checks=[]
    kd=SimpleNamespace(inspection_request_sequence=previous,inspection_last_time=last,
                       note=lambda tag,**fields:notes.append(tag))
    def owner(kd):
        checks.append(True)
        assert not replacement,'Do not inspect a replacement VM'
    scope=dict(json=json,runtime_inspection_owner=owner)
    exec(compile(ast.Module(body=[node],type_ignores=[]),'<actual request parser>','exec'),scope)
    return scope['runtime_inspection_request'](Root(Request(content,size)),kd,now),checks,notes

assert run(dict(sequence=1,kind='hot-wait'))[:2]==(True,[True])
assert run(dict(sequence=2,kind='hot-wait'),previous=1)[:2]==(True,[True])
for request in (dict(sequence=3,kind='hot-wait'),dict(sequence=True,kind='hot-wait'),
                dict(sequence=2,kind='hot-wait'),dict(sequence=1,kind='command'),
                dict(sequence=1,kind='hot-wait',address='arbitrary')):
    accepted,checks,_=run(request);assert not accepted and not checks
assert not run(dict(sequence=1,kind='hot-wait'),previous=1)[0]
assert not run(dict(sequence=1,kind='hot-wait'),now=159)[0]
assert not run(dict(sequence=1,kind='hot-wait'),size=1025)[0]
assert not run(dict(sequence=1,kind='hot-wait'),replacement=True)[0]
print('RUNTIME_INSPECTION_REQUESTS=PASS: exact schema, two ordered requests,60sec spacing, replacement-owner rejection, no arbitrary commands/reads')
