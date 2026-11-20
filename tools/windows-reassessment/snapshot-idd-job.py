"""Bounded read-only, non-coherent OWN disposable job evidence snapshot.
Positive persisted markers only; absence/staleness cannot establish a hang.
"""
import argparse
import hashlib
import importlib.util
import json
import re
import struct
import subprocess
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--cold',action='store_true');a=p.parse_args()
assert not a.output.exists()
adb=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe')
base=Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
state=json.loads((base/'activation.json').read_text())
assert state['status']=='TEST_ACTIVE_ORIGINAL_PROTECTED'
def owner():
    text=subprocess.check_output([adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    if a.cold:assert 'Running VMs: []' in text;return 'cold'
    matches=re.findall(r'cid: (\d+).*?requesterUid: (\d+).*?requesterPid: (\d+)',text,re.S)
    assert len(matches)==1 and matches[0][1]=='10042';return matches[0]
before=owner();a.output.mkdir()
spec=importlib.util.spec_from_file_location('fatreader',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
# Use bounded read/traversal methods only; do not construct the cold writer.
fat=g.DeviceFat.__new__(g.DeviceFat);fat.adb=adb
boot=fat.read(g.PART,512)
fat.sector=512;fat.cluster_bytes=boot[13]*512
fat.fat=g.PART+struct.unpack_from('<H',boot,14)[0]*512
fat.data=fat.fat+boot[16]*struct.unpack_from('<I',boot,36)[0]*512
fat.root=struct.unpack_from('<I',boot,44)[0]
assert fat.cluster_bytes==8192
report=dict(owner=before,filesystem_coherent=False,read_only=True,files=[])
try:
    data,_=fat.file('/UAVFIDD/bootstrap-log.txt')
    assert len(data)<=1024**2
    (a.output/'bootstrap-log.txt').write_bytes(data)
    report['files'].append(dict(path='UAVFIDD/bootstrap-log.txt',bytes=len(data)))
    print(data.decode('utf-8','replace')[-12000:])
except Exception as e:report['files'].append(dict(path='UAVFIDD/bootstrap-log.txt',unavailable=type(e).__name__))
spec=importlib.util.spec_from_file_location('ntfsreader',Path(__file__).with_name('extract-installed-setup-evidence.py'))
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
disk=r.ColdRoot(adb);boot=disk.read(512)
assert boot[3:11]==b'NTFS    ' and boot[72:80].hex()=='5896e81d6680a54a','Fresh isolated root only'
disk.seek(0);ntfs=r.NTFS(disk)
for name in ('uavf-base-provisioned.txt','UAVF/IddProbe/bcd-testmode.txt','UAVF/IddProbe/source-job-started.txt','UAVF/IddProbe/source-job-finished.txt','UAVF/IddProbe/source-job-failed.txt','UAVF/IddProbe/driver-stage.txt','UAVF/IddProbe/admission.txt','UAVF/IddProbe/source-report.txt','UAVF/IddProbe/motion-report.txt'):
    try:
        stream=ntfs.mft.get(name).open();assert stream.size<=1024**2
        data=stream.read(stream.size);(a.output/Path(name).name).write_bytes(data)
        report['files'].append(dict(path=name,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
        print(name+'\n'+data.decode('utf-8','replace')[-4000:])
    except Exception as e:report['files'].append(dict(path=name,unavailable=type(e).__name__))
assert owner()==before
(a.output/'report.json').write_text(json.dumps(report,indent=2))
print('OWN_DISPOSABLE_JOB_SNAPSHOT_READ_ONLY; ABSENCE_NOT_HANG_PROOF')
