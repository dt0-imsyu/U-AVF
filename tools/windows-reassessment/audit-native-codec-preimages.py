"""Read-only exact native x264 rollback difference, never edits the disk."""
import argparse,importlib.util,json
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);a=p.parse_args()
spec=importlib.util.spec_from_file_location('codec',Path(__file__).with_name('apply-eight-thread-codec.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);m.select_codec('x264',True)
plan,blocks=m.read_plan(Path('E:/U-AVF/artifacts/idd-x264-fat-readonly-plan3-20261014'))
fat=m.g.DeviceFat(a.adb,Path('E:/U-AVF/artifacts'),expected_efi_sha=m.g.EFI_SHA)
kinds={item['offset']:item['kind'] for item in plan['blocks']};diffs=[]
for offset,old,new in blocks:
    current=fat.read(offset,len(new))
    if current==new:continue
    positions=[i for i,(x,y) in enumerate(zip(current,new)) if x!=y]
    item={'offset':offset,'kind':kinds[offset],'changed_bytes':len(positions),'positions':positions[:32],
          'current_sha':m.p.digest(current),'candidate_sha':m.p.digest(new),'is_original':current==old}
    if kinds[offset]!='data' and len(positions)<=32:item['bytes']=[{'at':i,'expected':new[i],'actual':current[i]} for i in positions]
    diffs.append(item)
fat.cold();print(json.dumps({'device_writes':0,'differences':diffs},indent=2))
