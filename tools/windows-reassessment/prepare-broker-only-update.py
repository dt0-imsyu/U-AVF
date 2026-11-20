"""Prepare only the existing owned broker slot, never a registry/root rollback."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True);p.add_argument('--binary',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--expected-original-sha256',default='ce84a2fde4897b822b7b25a1938adc2820a19306ed6416ebd0d0cc398f8acfc1')
a=p.parse_args()
assert not a.output.exists()
old=(a.evidence/'Windows/System32/winpe-screen.exe').read_bytes()
assert len(a.expected_original_sha256)==64 and all(c in '0123456789abcdefABCDEF' for c in a.expected_original_sha256)
assert len(old)==71168 and hashlib.sha256(old).hexdigest()==a.expected_original_sha256.lower()
binary=a.binary.read_bytes();pe=struct.unpack_from('<I',binary,60)[0]
assert binary[:2]==b'MZ' and binary[pe:pe+4]==b'PE\0\0' and struct.unpack_from('<H',binary,pe+4)[0]==0xaa64
assert len(binary)<=len(old)
changed=binary+bytes(len(old)-len(binary));a.output.mkdir(parents=True)
(a.output/'winpe-screen-service-slot.exe').write_bytes(changed)
report=dict(result='OWNED_BROKER_BINARY_PREFLIGHT_PASS',original_helper_sha256=hashlib.sha256(old).hexdigest(),changed_helper_sha256=hashlib.sha256(changed).hexdigest(),unrelated_values_changed=0,existing_key_security_changed=0,registry_hive_writes=0,scope='Existing owned broker executable only; exact legacy Run value retired at native SCM startup')
(a.output/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
