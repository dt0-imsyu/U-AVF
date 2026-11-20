"""Resolve code addresses against retained exact kernel/PDB, not guest memory."""
import argparse
from pathlib import Path
import re
import struct
import subprocess

p=argparse.ArgumentParser()
p.add_argument('--image',type=Path,required=True)
p.add_argument('--pdb',type=Path,required=True)
p.add_argument('--pdbutil',type=Path,required=True)
p.add_argument('rva',nargs='+',type=lambda value:int(value,0))
a=p.parse_args()
data=a.image.read_bytes();assert data[:2]==b'MZ'
pe=struct.unpack_from('<I',data,60)[0]
n=struct.unpack_from('<H',data,pe+6)[0]
optional=struct.unpack_from('<H',data,pe+20)[0]
vas=[struct.unpack_from('<I',data,pe+24+optional+i*40+12)[0] for i in range(n)]
text=subprocess.check_output([str(a.pdbutil),'dump','--publics',str(a.pdb)],text=True)
symbols=sorted((vas[int(section)-1]+int(offset),name)
    for name,section,offset in re.findall(r'S_PUB32[^\n]*`([^`]+)`\s+flags = function, addr = (\d+):(\d+)',text)
    if 1<=int(section)<=n)
for rva in a.rva:
    previous=[symbol for symbol in symbols if symbol[0]<=rva]
    if previous:
        address,name=previous[-1];print(hex(rva),name,'+'+hex(rva-address))
    else:print(hex(rva),'unknown')
print('Nearest public symbols only; not proof of private function extent/hot PC.')
