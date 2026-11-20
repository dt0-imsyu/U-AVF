"""Parse actual EFI EBS observations; never equate registration with desktop."""
import argparse
import hashlib
import json
import re
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('rx',type=Path)
p.add_argument('--output',type=Path,required=True)
a = p.parse_args()
assert not a.output.exists()
data = a.rx.read_bytes()
assert 0 < len(data) <= 16*1024*1024
names = ('QUERY_CALLS','SET_CALLS','BLT_CALLS','BASE','MODE','WIDTH','HEIGHT','SAMPLED_NONZERO')
values = {}
for name in names:
    matches = re.findall(rb'WG:EBS_GOP_'+name.encode()+rb'=([0-9A-F]{16})\r\n',data)
    assert len(matches) <= 1, 'Ambiguous multiple EBS observations'
    if matches:
        values[name] = int(matches[0],16)
complete = len(values) == len(names)
report = dict(source_sha256=hashlib.sha256(data).hexdigest(),
              complete=complete,efi_ebs_gop=values,
              desktop_resolution_not_proven=True,
              limits='Query/set/blt counts include all firmware clients; direct Mode reads and direct framebuffer writes bypass callbacks. Sparse samples are not complete framebuffer content.')
if complete:
    assert values['WIDTH'] == 1920 and values['HEIGHT'] == 1200
    assert values['MODE'] == 0 and values['BASE'] % 4096 == 0
    assert 0x80000000 <= values['BASE'] <= 0x100000000-9216000
    assert values['SAMPLED_NONZERO'] <= 256
a.output.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
