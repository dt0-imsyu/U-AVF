"""Restore only staged filesystem metadata after interrupted cold staging.
Free-cluster payload residue is unallocated, not a live file. Never overwrite
foreign changes: every changed sector must equal its recorded before/after.
"""
import argparse,importlib.util,json,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--plan',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();assert not a.output.exists();a.output.mkdir(parents=True)
spec=importlib.util.spec_from_file_location('guard',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g);guard=g.DeviceFat(a.adb,a.output)
rootpages=set((guard.offset(c)-g.PART)//65536 for c in guard.chain(guard.root))
plan=json.loads((a.plan/'plan.json').read_text());restore=[]
for i in plan['pages']:
    if g.PART+i*65536>=guard.data and i not in rootpages:continue
    before=(a.plan/f'{i}-before.bin').read_bytes();after=(a.plan/f'{i}-after.bin').read_bytes()
    current=guard.read(g.PART+i*65536,len(before));candidate=bytearray(current)
    for k in range(0,len(before),512):
        if before[k:k+512]==after[k:k+512]:continue
        assert current[k:k+512] in (before[k:k+512],after[k:k+512]),'Foreign metadata change: refuse overwrite'
        candidate[k:k+512]=before[k:k+512]
    if candidate!=current:restore.append((i,bytes(candidate)))
for i,data in restore:
    guard.cold();local=a.output/'restore.bin';local.write_bytes(data);remote='/data/local/tmp/uavf-idd-stage-restore.bin'
    subprocess.run([a.adb,'push',str(local),remote],check=True,capture_output=True)
    subprocess.run([a.adb,'shell','chmod','644',remote],check=True,capture_output=True)
    subprocess.run([a.adb,'shell','run-as',g.PACKAGE,'dd','if='+remote,'of='+g.DISK,'bs=512','seek='+str((g.PART+i*65536)//512),'count='+str(len(data)//512),'conv=notrunc'],check=True,capture_output=True)
    assert guard.read(g.PART+i*65536,len(data))==data
subprocess.run([a.adb,'shell','rm','-f','/data/local/tmp/uavf-idd-stage-restore.bin'],check=True,capture_output=True)
report={'result':'RECORDED_METADATA_RESTORED_READBACK_PASS','pages_restored':len(restore),'unallocated_data_residue_retained':True,'driver_loaded':False}
(a.output/'report.json').write_text(json.dumps(report));print(json.dumps(report))
