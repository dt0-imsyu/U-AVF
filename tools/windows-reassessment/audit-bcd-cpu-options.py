"""Read-only public CPU boot options, not registry/credentials/user data.

Element identities: Microsoft BcdOSLoaderElementTypes documentation.
Reads ALL BCD objects so inherited explicit limits cannot hide in another object.
"""
import argparse
import io
import json
from pathlib import Path
from Registry import Registry

p=argparse.ArgumentParser();p.add_argument('bcd',type=Path);p.add_argument('--output',type=Path)
a=p.parse_args();data=a.bcd.read_bytes()
assert data[:4]==b'regf' and len(data)<=4*1024*1024 and data[4:8]==data[8:12]
r=Registry.Registry(io.BytesIO(data))
types={'26000060':'UseBootProcessorOnly','25000061':'NumberOfProcessors',
       '26000062':'ForceMaximumProcessors','25000063':'ProcessorConfigurationFlags',
       '25000066':'GroupSize'}
default=r.open(r'Objects\{9dea862c-5cdd-4e70-acc1-f32b344d4795}\Elements\23000003').value('Element').value()
assert default.lower()=='{86df57c0-9fb3-11f1-a311-83a31eab37aa}'
found=[]
objects=r.open('Objects').subkeys();assert len(objects)<=128
for obj in objects:
    try:elements=obj.subkey('Elements').subkeys()
    except Registry.RegistryKeyNotFoundException:continue
    assert len(elements)<=128
    for item in elements:
        if item.name().lower() not in types:continue
        value=item.value('Element').raw_data()
        assert 0<len(value)<=8
        found.append(dict(object=obj.name(),element=item.name(),name=types[item.name().lower()],value=int.from_bytes(value,'little')))
report=dict(scope='Read-only BCD public CPU options across all objects',active_loader=default,objects=len(objects),explicit_cpu_options=found)
if a.output:
    assert not a.output.exists();a.output.write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
