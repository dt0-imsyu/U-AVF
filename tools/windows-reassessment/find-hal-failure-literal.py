"""Read-only PE mapping of the observed HAL bugcheck discriminator."""
import struct,uuid,json,sys
from pathlib import Path
data=Path(sys.argv[1]).read_bytes();pe=struct.unpack_from('<I',data,0x3c)[0]
assert data[pe:pe+4]==b'PE\0\0'
n=struct.unpack_from('<H',data,pe+6)[0];opt=struct.unpack_from('<H',data,pe+20)[0]
sections=[]
for i in range(n):
    at=pe+24+opt+40*i
    name=data[at:at+8].rstrip(b'\0').decode();vsize,rva,size,raw=struct.unpack_from('<IIII',data,at+8)
    sections.append((name,rva,raw,size))
hits=[];at=0
while True:
    at=data.find(bytes.fromhex('31062505'),at)
    if at<0:break
    for name,rva,raw,size in sections:
        if raw<=at<raw+size:hits.append(dict(offset=hex(at),rva=hex(at-raw+rva),section=name))
    at+=4
ids=[];at=0
while True:
    at=data.find(b'RSDS',at)
    if at<0:break
    ids.append(str(uuid.UUID(bytes_le=data[at+4:at+20])).replace('-','').upper()+str(struct.unpack_from('<I',data,at+20)[0]));at+=4
print(json.dumps(dict(file=sys.argv[1],pdb_ids=ids,hal_discriminator_hits=hits),indent=2))
