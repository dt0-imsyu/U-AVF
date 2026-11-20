"""Read-only retained KD wire audit; no live VM, probes or instruction writes."""
import json,struct,sys
from pathlib import Path

def packets(path):
    data=path.read_bytes();result=[];offset=0
    while offset+16<=len(data):
        marker=data[offset:offset+4]
        if marker not in (b'0000',b'iiii'):
            offset+=1;continue
        _,kind,n,pid,checksum=struct.unpack_from('<IHHII',data,offset)
        control=marker==b'iiii';length=16 if control else 17+n
        if n>16384 or offset+length>len(data):offset+=1;continue
        body=data[offset+16:offset+16+n]
        if control and (n!=0 or kind not in (4,5,6)):offset+=1;continue
        if not control and (data[offset+length-1]!=170 or sum(body)!=checksum):offset+=1;continue
        entry=dict(offset=offset,kind=kind,pid=hex(pid),bytes=n)
        if len(body)>=4:entry['api_or_state']=hex(struct.unpack_from('<I',body)[0])
        if kind==2 and len(body)>=12:entry['status_or_union']=hex(struct.unpack_from('<I',body,8)[0])
        if kind==2 and body[:4]==struct.pack('<I',0x3136):entry['continue_status']=hex(struct.unpack_from('<I',body,16)[0])
        result.append(entry);offset+=length
    return dict(bytes=len(data),packet_count=len(result),last_packets=result[-20:])

root=Path(sys.argv[1]);print(json.dumps(dict(directory=str(root),tx=packets(root/'tx.bin'),rx=packets(root/'rx.bin')),indent=2))
