"""Cold read-only exact UART byte comparison for the last isolated owner run.

No guest commands, arbitrary paths, VM changes or packet replays. Retains bounded
own transport logs only; never reads Windows process memory or credentials.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

p=argparse.ArgumentParser()
p.add_argument('--adb',required=True)
p.add_argument('--evidence',type=Path,required=True)
a=p.parse_args()
assert a.evidence.is_dir() and not (a.evidence/'uart-byte-audit.json').exists()
def cold():
    state=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    assert 'Running VMs: []' in state,'Cold owner evidence only'
cold()
host_tx=(a.evidence/'tx.bin').read_bytes()
host_rx=(a.evidence/'rx.bin').read_bytes()
logs={}
for name in ('owner-decoded-host-tx.bin','owner-written-uart-tx.bin','owner-serial.bin'):
    size=int(subprocess.check_output([a.adb,'exec-out','run-as','com.example.uavfwinpetest','stat','-c','%s','files/'+name]))
    assert 0<=size<=8*1024*1024,'Bounded last-owner transport log only'
    data=subprocess.check_output([a.adb,'exec-out','run-as','com.example.uavfwinpetest','cat','files/'+name])
    assert len(data)==size,'Stable exact read'
    with (a.evidence/name).open('xb') as out:out.write(data)
    logs[name]=data
def compare(expected,actual):
    common=min(len(expected),len(actual))
    first=next((i for i in range(common) if expected[i]!=actual[i]),None)
    return dict(expected_bytes=len(expected),actual_bytes=len(actual),
                exact=expected==actual,prefix_equal=first is None,first_differing_offset=first,
                expected_sha256=hashlib.sha256(expected).hexdigest(),
                actual_sha256=hashlib.sha256(actual).hexdigest())
report=dict(host_to_decoded=compare(host_tx,logs['owner-decoded-host-tx.bin']),
            decoded_to_written=compare(logs['owner-decoded-host-tx.bin'],logs['owner-written-uart-tx.bin']),
            serial_to_host=compare(logs['owner-serial.bin'],host_rx),
            scope='Own transport byte parity only; pipe write is not proof of UART guest consumption')
cold()
with (a.evidence/'uart-byte-audit.json').open('x') as out:json.dump(report,out,indent=2)
print(json.dumps(report,indent=2))
