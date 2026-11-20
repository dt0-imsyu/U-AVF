"""Read-only KD framing/ACK audit, independent of textual debugger messages."""
import argparse,json,struct
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('file');a=p.parse_args()
data=Path(a.file).read_bytes();events=[];at=0
while at+16<=len(data):
    leader=data[at:at+4]
    if leader not in (b'0000',b'iiii'):at+=1;continue
    _,kind,n,pid,total=struct.unpack_from('<IHHII',data,at)
    if leader==b'iiii':
        if n or kind not in (4,5,6):at+=1;continue
        events.append(dict(offset=at,control=kind,id=hex(pid)));at+=16;continue
    if n>16384 or at+17+n>len(data):at+=1;continue
    body=data[at+16:at+16+n]
    if sum(body)!=total or data[at+16+n]!=0xaa:at+=1;continue
    e=dict(offset=at,data=kind,id=hex(pid),size=n)
    if len(body)>=4:e['api_or_state']=hex(struct.unpack_from('<I',body)[0])
    if kind==2 and len(body)>=56:
        api=struct.unpack_from('<I',body)[0]
        e['status']=hex(struct.unpack_from('<I',body,8)[0])
        if api in (0x3130,0x3131):
            e.update(address=hex(struct.unpack_from('<Q',body,16)[0]),
                     requested=struct.unpack_from('<I',body,24)[0],
                     actual=struct.unpack_from('<I',body,28)[0])
    if kind==7 and len(body)>=73:e.update(pc=hex(struct.unpack_from('<Q',body,24)[0]),unload=body[72])
    events.append(e);at+=17+n
print(json.dumps(dict(file=a.file,events=len(events),tail=events[-35:]),indent=2))
