"""Read-only exact codec/immutable guard audit after an isolated Windows crash."""
import argparse, importlib.util, hashlib, json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--prepared',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
assert not a.output.exists()
s=importlib.util.spec_from_file_location('codec',Path(__file__).with_name('apply-eight-thread-codec.py'))
m=importlib.util.module_from_spec(s);s.loader.exec_module(m);m.select_codec('x264',True)
plan,blocks=m.read_plan(a.prepared);f=m.g.DeviceFat(a.adb,a.output.parent,expected_efi_sha=m.g.EFI_SHA)
digest=lambda b:hashlib.sha256(b).hexdigest()
guards=[]
for offset,size,key in ((512,512,'gpt_header_sha256'),(1024,16384,'gpt_entries_sha256'),(m.g.PART,512,'boot_sector_sha256')):
    current=f.read(offset,size);g={'offset':offset,'sha256':digest(current),'expected':plan[key],'match':digest(current)==plan[key]}
    if key=='boot_sector_sha256' and not g['match']:
        # Report ONLY; try the FAT32 reserved status byte, no disk writes.
        candidates=[]
        for value in range(256):
            b=bytearray(current);b[65]=value
            if digest(b)==plan[key]:candidates.append({'offset':65,'current':current[65],'original':value})
        g['exact_original_status_byte_matches']=candidates
    guards.append(g)
observed=[]
for offset,old,new in blocks:
    current=f.read(offset,len(old))
    item={'offset':offset,'bytes':len(old),'sha256':digest(current),'state':'old' if current==old else 'candidate' if current==new else 'unknown'}
    if item['state']=='unknown':
        item['candidate_diff']=[{'offset':i,'current':x,'candidate':new[i]} for i,x in enumerate(current) if x!=new[i]][:32]
        item['diff_count']=sum(x!=y for x,y in zip(current,new))
    observed.append(item)
f.cold();a.output.mkdir(parents=True)
result={'device_writes':0,'guards':guards,'efi_sha':digest(f.file('/EFI/BOOT/BOOTAA64.EFI')[0]),'blocks':observed}
(a.output/'report.json').write_text(json.dumps(result,indent=2))
print(json.dumps({'device_writes':0,'guards':guards,'unknown_blocks':[x for x in observed if x['state']=='unknown']}))
