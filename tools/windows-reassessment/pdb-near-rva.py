"""Read-only lookup of nearest public symbols using the matching image sections."""
import re, struct, subprocess, sys
from pathlib import Path
tool, pdb, image, *addresses = sys.argv[1:]
data = Path(image).read_bytes()
pe = struct.unpack_from('<I', data, 0x3c)[0]
n = struct.unpack_from('<H', data, pe+6)[0]
opt = struct.unpack_from('<H', data, pe+20)[0]
sections = [struct.unpack_from('<I', data, pe+24+opt+40*i+12)[0] for i in range(n)]
dump = subprocess.check_output([tool, 'dump', '--publics', pdb], text=True, encoding='utf-8', errors='replace')
symbols = []
for match in re.finditer(r'`([^`]+)`\s+flags = [^\n]*addr = (\d+):(\d+)', dump):
    name, section, offset = match.groups()
    if 1 <= int(section) <= len(sections):
        symbols.append((sections[int(section)-1]+int(offset), name))
symbols.sort()
for address in addresses:
    rva = int(address, 0)
    before = [s for s in symbols if s[0] <= rva][-4:]
    after = [s for s in symbols if s[0] > rva][:3]
    print('RVA', hex(rva))
    for value, name in before+after:
        print(hex(value), name, 'delta='+hex(rva-value) if value <= rva else '')
