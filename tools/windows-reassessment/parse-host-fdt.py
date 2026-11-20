"""Strict FDT serial dump parser; expose actual PCI ranges/IRQ map."""
import json,struct,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004'
lines=(root/'host-fdt-console.txt').read_text().splitlines()
assert lines[0]=='UAVF_FDT_HEX' and len(lines)==2
data=bytes.fromhex(lines[1]);header=struct.unpack_from('>10I',data)
assert header[0]==0xd00dfeed and header[1]==len(data)
start,strings=header[2:4];end=start+header[9];text=data[strings:strings+header[8]]
pos=start;stack=[];nodes={}
while pos<end:
    token=struct.unpack_from('>I',data,pos)[0];pos+=4
    if token==1:
        stop=data.index(b'\0',pos);name=data[pos:stop].decode();pos=(stop+4)&~3
        stack.append(name);path='/'.join(stack) or '/';nodes[path]={}
    elif token==2:stack.pop()
    elif token==3:
        size,nameoffset=struct.unpack_from('>II',data,pos);pos+=8
        name=text[nameoffset:text.index(b'\0',nameoffset)].decode()
        value=data[pos:pos+size];pos=(pos+size+3)&~3
        item={'hex':value.hex()}
        if size%4==0:item['cells']=[hex(x) for x in struct.unpack('>'+str(size//4)+'I',value)]
        if value.endswith(b'\0') and all(32<=c<127 or c==0 for c in value):item['strings']=value[:-1].decode().split('\0')
        nodes['/'.join(stack) or '/'][name]=item
    elif token==4:pass
    elif token==9:
        assert not stack;break
    else:raise ValueError('Unknown FDT token')
(root/'host-fdt.dtb').write_bytes(data)
report={'sha256':hashlib.sha256(data).hexdigest(),'nodes':nodes}
(root/'host-fdt.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in nodes.items() if 'pci' in k or 'intc' in k},indent=2))
