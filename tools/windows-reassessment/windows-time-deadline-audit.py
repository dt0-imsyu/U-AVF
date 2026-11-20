"""Offline exact-SFAPM deadline arithmetic; no guest access or mutation."""
import argparse
from datetime import datetime, timedelta
import hashlib
import json
from pathlib import Path
import struct

p=argparse.ArgumentParser()
p.add_argument('--source',type=Path,required=True)
p.add_argument('--system-time-100ns',type=int,required=True)
p.add_argument('--remaining-steady-ns',type=int,default=1000000000)
a=p.parse_args()
data=a.source.read_bytes()
assert hashlib.sha256(data).hexdigest()=='e2276740fcdd76856266f8dab4329548895c5480718d76aa4a0cac7eeb3ff179', 'Exact official SFAPM only'
pe=struct.unpack_from('<I',data,60)[0]
n=struct.unpack_from('<H',data,pe+6)[0];optional=struct.unpack_from('<H',data,pe+20)[0]
sections=[struct.unpack_from('<IIII',data,pe+24+optional+40*i+8) for i in range(n)]
def image_word(rva):
    for _,va,size,offset in sections:
        if va<=rva and rva+4<=va+size:return struct.unpack_from('<I',data,offset+rva-va)[0]
    raise AssertionError('Original code bounds')
assert image_word(0x19eec)==0xf941c508, 'Verified IAT2d388 = _Xtime_get_ticks'
assert image_word(0x19f0c)==0xd2800c8b, 'MOV X11,100'
assert image_word(0x19f24)==0x9b0b250a, 'MADD X10,X8,X11,X9'
assert image_word(0x19f2c)==0x9ac90d48, 'Signed deadline seconds division'
assert 0<=a.system_time_100ns<2**64 and 0<=a.remaining_steady_ns<2**63
utc_ticks=a.system_time_100ns-116444736000000000
wide=utc_ticks*100+a.remaining_steady_ns
signed=(wide+2**63)%2**64-2**63
try:date=(datetime(1601,1,1)+timedelta(microseconds=a.system_time_100ns//10)).isoformat()
except OverflowError:date='outside datetime range'
print(json.dumps(dict(system_time_100ns=a.system_time_100ns,date_utc=date,
    remaining_steady_ns=a.remaining_steady_ns,deadline_ns_wide=wide,arm64_signed_deadline_ns=signed,
    overflow=not(-2**63<=wide<2**63),deadline_in_past_from_overflow=wide>=0 and signed<0,
    scope='Exact static conversion arithmetic; runtime CPU cause/fix requires A/B'),indent=2))
