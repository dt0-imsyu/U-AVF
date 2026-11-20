"""Read-only explicit display settings; Microsoft BCD element reference.

Inspects all objects, including inherited settings. Does not infer defaults.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
from Registry import Registry

p = argparse.ArgumentParser()
p.add_argument('bcd', type=Path)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
assert not a.output.exists(), 'Preserve prior evidence'
data = a.bcd.read_bytes()
assert data[:4] == b'regf' and len(data) <= 4*1024*1024
assert data[4:8] == data[8:12], 'Dirty hive'
hive = Registry.Registry(io.BytesIO(data))
types = {'26000040': 'Vga', '16000046': 'GraphicsModeDisabled',
         '15000052': 'GraphicsResolution', '16000054': 'HighestMode',
         '1600006c': 'BootUxDisabled'}
objects = hive.open('Objects').subkeys()
assert 0 < len(objects) <= 128
found = []
for obj in objects:
    try:
        elements = obj.subkey('Elements').subkeys()
    except Registry.RegistryKeyNotFoundException:
        continue
    assert len(elements) <= 128
    for e in elements:
        name = e.name().lower()
        if name not in types:
            continue
        value = e.value('Element').raw_data()
        assert 0 < len(value) <= 8
        found.append(dict(object=obj.name(), element=name, name=types[name],
                          value=int.from_bytes(value, 'little')))
report = dict(scope='Explicit documented display settings, all BCD objects',
              source_sha256=hashlib.sha256(data).hexdigest(), objects=len(objects),
              explicit_display_options=found, device_writes=0,
              default_behavior_not_proven=True)
a.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report, indent=2))
