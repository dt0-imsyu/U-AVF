"""Read-only PE string mapping and nearby ADRP/ADD ARM64 references."""
import struct, sys
from pathlib import Path

data = Path(sys.argv[1]).read_bytes()
needle = sys.argv[2].encode()
pe = struct.unpack_from('<I', data, 0x3c)[0]
n = struct.unpack_from('<H', data, pe + 6)[0]
opt = struct.unpack_from('<H', data, pe + 20)[0]
sections = []
for i in range(n):
    at = pe + 24 + opt + 40*i
    name = data[at:at+8].rstrip(b'\0').decode()
    _, rva, size, raw = struct.unpack_from('<IIII', data, at+8)
    flags = struct.unpack_from('<I', data, at+36)[0]
    sections.append((name, rva, raw, size, flags))
offset = data.find(needle)
assert offset >= 0, 'String not found'
target = next(rva + offset - raw for _, rva, raw, size, _ in sections if raw <= offset < raw+size)
print('STRING_RVA', hex(target), 'RAW', hex(offset))
for name, rva, raw, size, flags in sections:
    if not flags & 0x20000000:
        continue
    for rel in range(0, size-32, 4):
        w = struct.unpack_from('<I', data, raw+rel)[0]
        if w & 0x9f000000 != 0x90000000:
            continue
        imm = ((w >> 5) & 0x7ffff) << 2 | ((w >> 29) & 3)
        if imm & (1 << 20):
            imm -= 1 << 21
        page = ((rva+rel) & ~4095) + (imm << 12)
        if page != target & ~4095:
            continue
        for delta in range(4, 32, 4):
            add = struct.unpack_from('<I', data, raw+rel+delta)[0]
            if add & 0xffc00000 == 0x91000000 and ((add >> 5) & 31) == (w & 31):
                if page + ((add >> 10) & 4095) == target:
                    print('XREF', name, hex(rva+rel), 'ADD', hex(rva+rel+delta))
