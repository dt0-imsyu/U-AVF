"""Validate KD data packets without treating firmware ASCII as KD traffic."""
import argparse, collections, hashlib, json, struct
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('serial');p.add_argument('--output');a=p.parse_args()
data=Path(a.serial).read_bytes();counts=collections.Counter();exceptions={};position=0;states=[];debug_text=[]
while True:
    offset=data.find(b'0000',position)
    if offset<0:break
    position=offset+1
    if offset+16>len(data):continue
    _,kind,length,packet_id,checksum=struct.unpack_from('<IHHII',data,offset)
    if length>16384 or offset+17+length>len(data):continue
    body=data[offset+16:offset+16+length]
    if sum(body)!=checksum or data[offset+16+length]!=0xAA:continue
    counts[kind]+=1;position=offset+17+length
    if kind==3 and length>=16:
        debug_text.append(body[16:].decode('utf-8','replace'))
    if kind==7 and length>=32:
        states.append(dict(offset=offset,state=hex(struct.unpack_from('<I',body)[0]),pc=hex(struct.unpack_from('<Q',body,24)[0]),length=length,head=body[:80].hex()))
        if length>=80 and struct.unpack_from('<I',body)[0]==0x3031:
            path_length=struct.unpack_from('<I',body,32)[0]
            if 0<path_length<=length-80:
                states[-1]['load_path_tail']=body[-path_length:].decode('ascii','replace').rstrip('\0')
    if kind==7 and length>=188 and struct.unpack_from('<I',body)[0]==0x3030:
        pc=struct.unpack_from('<Q',body,24)[0];code=struct.unpack_from('<I',body,32)[0]
        n=struct.unpack_from('<I',body,56)[0]
        params=[hex(struct.unpack_from('<Q',body,64+i*8)[0]) for i in range(min(n,15))]
        key=(pc,code,tuple(params))
        if key not in exceptions:
            exceptions[key]=dict(pc=hex(pc),code=hex(code),parameters=params,
                                 first_chance=struct.unpack_from('<I',body,184)[0],count=0)
        exceptions[key]['count']+=1
result=dict(serial=a.serial,sha256=hashlib.sha256(data).hexdigest().upper(),
            bytes=len(data),validated_data_packets=dict(counts),exceptions=list(exceptions.values()),
            states=states,debug_text=debug_text,
            note='A packet proves a reported debug state, not a normal non-debug boot failure.')
text=json.dumps(result,indent=2);print(text)
if a.output:Path(a.output).write_text(text)
