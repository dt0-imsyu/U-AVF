"""Read-only packet tail from retained wire evidence."""
import struct
import sys
from pathlib import Path

for name in ('tx.bin','rx.bin'):
    raw=(Path(sys.argv[1])/name).read_bytes();offset=0;packets=[]
    while offset+16<=len(raw):
        if raw[offset:offset+4] not in (b'0000',b'iiii'):
            offset+=1;continue
        magic,kind,n,pid,checksum=struct.unpack_from('<IHHII',raw,offset)
        if magic==0x69696969 and (kind not in (4,5,6) or n!=0):offset+=1;continue
        if magic==0x30303030 and (kind not in (2,3,7,11) or n>16384):offset+=1;continue
        length=16 if magic==0x69696969 else 17+n
        if offset+length>len(raw):break
        body=raw[offset+16:offset+16+n]
        if magic==0x30303030 and (sum(body)!=checksum or raw[offset+length-1]!=170):offset+=1;continue
        item=dict(offset=offset,kind=kind,n=n,pid=hex(pid))
        if len(body)>=4:item['api']=hex(struct.unpack_from('<I',body)[0])
        if kind==2 and len(body)>=32:item['address']=hex(struct.unpack_from('<Q',body,16)[0]);item['count']=struct.unpack_from('<I',body,24)[0]
        packets.append(item);offset+=length
    print(name, 'packets=',len(packets))
    for item in packets[-35:]:print(item)
