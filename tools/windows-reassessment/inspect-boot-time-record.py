"""Read only the exact installed loader's boot-status calendar field.

No disk/hive changes. The current record does not reconstruct earlier boots.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import struct
import zlib

p = argparse.ArgumentParser()
p.add_argument('--loader', type=Path, required=True)
p.add_argument('--bootstat', type=Path, required=True)
a = p.parse_args()
b = a.loader.read_bytes()
assert hashlib.sha256(b).hexdigest() == 'f7747f4ac18ccd66ebf6a043d979cf30c7731114c27c33ff2fc3c0610f491d31'
pe = struct.unpack_from('<I', b, 60)[0]
n = struct.unpack_from('<H', b, pe + 6)[0]
optional = struct.unpack_from('<H', b, pe + 20)[0]

def at(rva):
    for i in range(n):
        s = pe + 24 + optional + i * 40
        _, va, size, raw = struct.unpack_from('<IIII', b, s + 8)
        if va <= rva < va + size:
            return raw + rva - va
    raise AssertionError('RVA outside file-backed section')

# OslProcessBootStatusData reads field 7; OslSetSystemTimeParameters
# checks crc32(record+16,8) against record+24 before selecting this date.
offset, size = struct.unpack_from('<II', b, at(0x29d670 + 7 * 8))
assert (offset, size) == (16, 32)
# OslSetControlSet8550 finds a root subkey via CmpFindSubKeyByName25e850,
# storing its cell index at hive-table+12e8. Loader fallback dereferences that
# cell and reads CM_KEY_NODE.LastWriteTime at +4. No value-data enumeration.
name_len, name_max, name_ptr = struct.unpack_from('<HH4xQ', b, at(0x2c67e8))
assert (name_len, name_max) == (36, 38)
name = b[at(name_ptr - 0x180000000):at(name_ptr - 0x180000000) + name_len].decode('utf-16le')
assert name == 'ControlSetOverride'
assert struct.unpack_from('<I', b, at(0x860c))[0] == 0xb912ea60
assert struct.unpack_from('<I', b, at(0x2e3b0))[0] == 0xf8404008
d = a.bootstat.read_bytes()
assert 200 <= len(d) <= 8 * 1024 * 1024
header = struct.unpack_from('<I', d)[0]
assert offset + size <= header <= len(d)
t = struct.unpack_from('<Q', d, offset + 16)[0]
stored = struct.unpack_from('<I', d, offset + 24)[0]
crc = zlib.crc32(d[offset + 16:offset + 24])
date = datetime.datetime(1601, 1, 1) + datetime.timedelta(microseconds=t // 10)
print(json.dumps(dict(read_only=True, bootstat_sha256=hashlib.sha256(d).hexdigest(),
                     field=7, offset=offset, size=size, calendar_100ns=t,
                     calendar_utc=date.isoformat() + 'Z', crc_stored=stored,
                     crc_calculated=crc, crc_valid=stored == crc,
                     registry_fallback=dict(root_subkey=name, source='Key LastWriteTime, not W32Time values'),
                     limitation='Current cold record only; not earlier boot source proof'), indent=2))
