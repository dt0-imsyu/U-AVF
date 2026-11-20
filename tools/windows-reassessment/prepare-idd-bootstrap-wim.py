"""New test-only WinPE bootstrap WIM; preserve frozen parent and kernel bytes.
No tablet writes. Only own executable/startup ini and own test-job directory.
"""
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

WORKSPACE=Path(__file__).resolve().parents[3]
TOOLS=Path(__file__).resolve().parent
SOURCE=WORKSPACE/'build-logs/windows-post-linux-reassessment-20261004/live-name-fix-candidate.wim'
OUT=Path('E:/U-AVF/artifacts/idd-bootstrap-wim-20261013')
assert not OUT.exists()
with SOURCE.open('rb') as s:
    assert hashlib.file_digest(s,'sha256').hexdigest().upper()=='A90BA45CC1404CF18FE2F10CE195E565B8F70E45416583F2F25F3EF4391BEF15'
OUT.mkdir();JOB=OUT/'job';JOB.mkdir()
def linux(path):
    path=path.resolve();return '/mnt/'+path.drive[0].lower()+path.as_posix()[2:]
for source,name in [(TOOLS/'provision-idd-fresh.cmd','provision.cmd'),
                    (TOOLS/'idd-disposable-unattend.xml','unattend.xml'),
                    (TOOLS/'idd-install-and-source-test.cmd','install-and-source-test.cmd'),
                    (Path('E:/U-AVF/artifacts/idd-motion-source-20261013/idd-motion-source.exe'),'idd-motion-source.exe'),
                    (Path('E:/U-AVF/artifacts/idd-etw-reader-20261013/idd-etw-read.exe'),'idd-etw-read.exe')]:
    data=source.read_bytes()
    if source.suffix in ('.cmd','.xml'):data=data.replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')
    (JOB/name).write_bytes(data)
BOOTSTRAP=Path('E:/U-AVF/artifacts/idd-winpe-bootstrap-job-20261013/idd-winpe-bootstrap.exe')
(OUT/'winpeshl.ini').write_bytes(b'[LaunchApps]\r\n%SYSTEMROOT%\\System32\\idd-winpe-bootstrap.exe\r\n')
WIM=OUT/'BOOT.WIM';shutil.copyfile(SOURCE,WIM)
commands=(f'add "{linux(BOOTSTRAP)}" /Windows/System32/idd-winpe-bootstrap.exe\n'
          f'add "{linux(OUT/"winpeshl.ini")}" /Windows/System32/winpeshl.ini\n'
          f'add "{linux(JOB)}" /UAVFIddJob\n')
subprocess.run(['wsl','-d','Ubuntu','--','wimlib-imagex','update',linux(WIM),'2'],input=commands.encode(),check=True)
def stream_hash(wim,name):
    p=subprocess.Popen(['wsl','-d','Ubuntu','--','wimlib-imagex','extract',linux(wim),'2',name,'--to-stdout'],stdout=subprocess.PIPE)
    digest=hashlib.file_digest(p.stdout,'sha256').hexdigest().upper()
    assert p.wait()==0;return digest
core={}
for name in ('/Windows/System32/ntoskrnl.exe','/Windows/System32/hal.dll'):
    before=stream_hash(SOURCE,name);after=stream_hash(WIM,name)
    assert before==after;core[name]=after
with WIM.open('rb') as s:sha=hashlib.file_digest(s,'sha256').hexdigest().upper()
report=dict(result='BOOTSTRAP_WIM_PC_ONLY_NOT_GUEST_OR_FPS_PASS',path=str(WIM),sha256=sha,
            unchanged_core=core,changes=['own bootstrap','winpeshl.ini','own disposable job'],
            original_wim_unchanged=True,device_writes=0)
(OUT/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report),flush=True)
