"""Read retained wire: only verified context PC/PSTATE/length, never GPR/data."""
import struct
import sys
import json
from pathlib import Path

raw=Path(sys.argv[1]).read_bytes();offset=0;contexts=[];state_thread=None
events=[json.loads(line) for line in Path(sys.argv[1]).with_name('events.jsonl').read_text().splitlines() if line.strip()]
kernel_base=int(next(item['base'] for item in events if item.get('tag')=='KERNEL_CONNECTED'),16)
inventories=[item for item in events if item.get('tag')=='HOT_PROCESS_CODE_INVENTORY']
hot=inventories[-1] if inventories else None
target=hot['threads'][0] if hot else None
while offset+16<=len(raw):
    if raw[offset:offset+4]!=b'0000':offset+=1;continue
    magic,kind,size,pid,checksum=struct.unpack_from('<IHHII',raw,offset)
    if size>16384 or offset+17+size>len(raw):offset+=1;continue
    body=raw[offset+16:offset+16+size]
    if sum(body)!=checksum or raw[offset+16+size]!=170:offset+=1;continue
    if kind==7 and len(body)>=80:state_thread=struct.unpack_from('<Q',body,16)[0]
    if kind==2 and len(body)==968 and struct.unpack_from('<I',body)[0]==0x315f:
        api,status=struct.unpack_from('<I',body)[0],struct.unpack_from('<I',body,8)[0]
        if status==0:
            contexts.append(dict(pc=hex(struct.unpack_from('<Q',body,56+264)[0]),
                                 pstate=hex(struct.unpack_from('<I',body,56+4)[0]),bytes=len(body)-56))
            item=contexts[-1]
            if (target and hot['image']=='lsass.exe' and item['pc']==hex(kernel_base+0x8f7d74)
                    and int(item['pstate'],16)&15 in (4,5) and state_thread==int(target['thread'],16)):
                item.update(pid=hot['pid'],tid=target['tid'],
                            return_ntstatus=hex(struct.unpack_from('<I',body,56+8)[0]),
                            scope='Exact guarded wait-return W0 only, matched hot thread; timeout/date not read')
    offset+=17+size
for item in contexts[-6:]:print(item)
