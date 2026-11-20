"""Bounded CPU thread metadata; start address is NOT a sampled hot PC."""
import argparse
import json
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('log',type=Path)
a=p.parse_args();groups={};broker=None;sequence=None
def fields(line):return dict(field.split('=',1) for field in line.split()[1:] if '=' in field)
for line in a.log.read_text(errors='replace').splitlines():
    if line.startswith('CPU_SAMPLE '):
        row=fields(line);broker=int(row['broker']);sequence=int(row['seq'])
    elif line.startswith('CPU_HOT_THREAD ') and broker is not None:
        row=fields(line);assert int(row['seq'])==sequence
        key=(broker,int(row['pid']),int(row['tid']),int(row['created_100ns']))
        groups.setdefault(key,[]).append(row)
results=[]
for (broker,pid,tid,created),rows in groups.items():
    valid=[row for row in rows if row['ok']=='1']
    if len(valid)<2:continue
    first,last=valid[0],valid[-1]
    kernel=int(last['kernel_100ns'])-int(first['kernel_100ns'])
    user=int(last['user_100ns'])-int(first['user_100ns'])
    if kernel<0 or user<0:continue
    results.append(dict(broker=broker,pid=pid,tid=tid,base_priority=int(last['priority']),
        observations=len(valid),kernel_ms=kernel/10000,user_ms=user/10000,
        start_status=last['start_status'],start_address='0x'+last['start_address']))
print(json.dumps(dict(threads=sorted(results,key=lambda row:row['kernel_ms']+row['user_ms'],reverse=True)[:16],
    limitation='Native metadata/counters only. Start address names thread entry, NOT sampled hot instruction; guest clocks unverified.'),indent=2))
