"""Resolve bounded code-only snapshot against exact PE/PDB, never guest memory."""
import argparse
import json
import re
import struct
import subprocess
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('events',type=Path)
p.add_argument('--code-directory',type=Path,required=True)
p.add_argument('--pdbutil',type=Path,required=True)
p.add_argument('--latest',action='store_true',help='Resolve only the latest actual same-boot snapshot')
a=p.parse_args()
events=[json.loads(line) for line in a.events.read_text().splitlines() if line.strip()]
snapshots=[event for event in events if event.get('tag')=='HOT_PROCESS_CODE_INVENTORY']
assert snapshots, 'No actual code snapshot yet'
if a.latest:snapshots=snapshots[-1:]
def layout(data):
    assert data[:2]==b'MZ'
    pe=struct.unpack_from('<I',data,60)[0];count=struct.unpack_from('<H',data,pe+6)[0]
    optional=struct.unpack_from('<H',data,pe+20)[0]
    return [struct.unpack_from('<IIII',data,pe+24+optional+i*40+8) for i in range(count)]
def bytes_at(data,sections,rva,size):
    for virtual_size,va,raw_size,offset in sections:
        if va<=rva and rva+size<=va+raw_size:return data[offset+rva-va:offset+rva-va+size]
    return None
cache={}
for event in snapshots:
    print('HOT_PROCESS',event['image'],'PID',event['pid'],'CURRENT_MATCH',event['current_process_matches'])
    for thread in event['threads'][:8]:
        result={key:thread[key] for key in ('tid','state','kernel_ticks','user_ticks')}
        for key in ('saved_pc','saved_lr'):
            if key not in thread:continue
            address=int(thread[key],16)
            candidates=[module for module in event['modules'] if int(module['base'],16)<=address<int(module['base'],16)+module['size']]
            if len(candidates)!=1:result[key]=dict(address=hex(address),module='unknown');continue
            module=candidates[0];name=module['name'].split('\\')[-1].lower();rva=address-int(module['base'],16)
            value=dict(address=hex(address),module=name,rva=hex(rva))
            code=a.code_directory/name
            if code.exists():
                if name not in cache:
                    data=code.read_bytes();sections=layout(data);symbols=[]
                    pdb=code.with_suffix('.pdb')
                    if pdb.exists():
                        text=subprocess.check_output([str(a.pdbutil),'dump','--publics',str(pdb)],text=True)
                        for match in re.finditer(r'S_PUB32[^\n]*`([^`]+)`\s+flags = function, addr = (\d+):(\d+)',text):
                            symbol,section,offset=match.groups();section=int(section);offset=int(offset)
                            if 1<=section<=len(sections):symbols.append((sections[section-1][1]+offset,symbol))
                    cache[name]=(data,sections,sorted(symbols))
                data,sections,symbols=cache[name]
                previous=[symbol for symbol in symbols if symbol[0]<=rva]
                if previous:value.update(nearest_public_symbol=previous[-1][1],symbol_offset=hex(rva-previous[-1][0]),
                                         symbol_caveat='Nearest public symbol, not exact private function extent')
                if key=='saved_pc' and 'instruction_window' in thread:
                    original=bytes_at(data,sections,rva-16,48)
                    value['source_code_window_matches']=original is not None and original.hex()==thread['instruction_window']
            result[key]=value
        print(json.dumps(result))
