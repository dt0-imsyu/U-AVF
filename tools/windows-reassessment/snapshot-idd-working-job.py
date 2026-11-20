"""Read only our own IddCx job logs from exact approved working guest.
Live filesystem reads are non-coherent; absence is not a hang/failure verdict.
"""
import argparse,importlib.util,json,struct,subprocess
import hashlib
import re
from pathlib import Path
from dissect.ntfs.exceptions import BrokenIndexError, BrokenMftError
from dissect.ntfs.c_ntfs import ATTRIBUTE_TYPE_CODE, FILE_RECORD_SEGMENT_IN_USE
from dissect.ntfs.util import segment_reference
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--mft-fallback',action='store_true');p.add_argument('--cold',action='store_true');p.add_argument('--stream-owner-pid',type=int,action='append',default=[]);a=p.parse_args()
assert not a.output.exists()
def owner():
    state=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    if a.cold:assert state.strip()=='Running VMs: []','Cold observation requires all VMs stopped'
    else:assert 'requesterUid: 10042' in state and state.count('requesterUid:')==1
    return state
before=owner()
spec=importlib.util.spec_from_file_location('read',Path(__file__).with_name('extract-installed-setup-evidence.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
root=m.ColdRoot(a.adb);boot=root.read(512)
assert boot[3:11]==b'NTFS    ' and boot[72:80].hex()=='b769f63e81f63ec6'
root.seek(0);fs=m.NTFS(root);a.output.mkdir(parents=True);items={}
owned_payloads={}
for relative in ('package/IddSampleDriver.dll','package/IddSampleDriver.cat','idd-etw-read.exe','idd-motion-source.exe','idd-probe-device.exe'):
    for suffix in ('','before-idd-desktop22','before-idd-reconnect-desktop22','before-idd-reconnect-probe23','before-idd-long-workload28'):
        path='UAVF/IddWorkingProbe/'+relative+('.'+suffix if suffix else '')
        try:
            stream=fs.mft.get(path.replace('/','\\')).open()
            assert stream.size<=65536
            data=stream.read(stream.size)
            owned_payloads[path]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
        except (FileNotFoundError,BrokenIndexError,BrokenMftError):
            owned_payloads[path]={'unavailable':True}
(a.output/'owned-payload-hashes.json').write_text(json.dumps({'live':not a.cold,'files':owned_payloads},indent=2))
names=['ProgramData/UAVF/idd-working-launch-requested.txt','ProgramData/UAVF/idd-working-launch-error.bin']
names+=['ProgramData/UAVF/idd-working-launch-requested2.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested3.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested4.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested5.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested6.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested7.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested8.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested9.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested10.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested11.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested12.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested13.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested14.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested15.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested16.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested17.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested18.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested19.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested20.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested21.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested22.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested23.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested24.txt','ProgramData/UAVF/idd-working-job24.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested25.txt','ProgramData/UAVF/idd-working-job25.txt','UAVF/IddWorkingProbe/recovered-desktop22.txt']
names+=['ProgramData/UAVF/idd-working-launch-requested26.txt','ProgramData/UAVF/idd-working-job26.txt','UAVF/IddWorkingProbe/desktop-topology22.txt.before-active26','UAVF/IddWorkingProbe/desktop-topology22-before.bin.before-active26']
names+=['ProgramData/UAVF/idd-working-launch-requested27.txt','ProgramData/UAVF/idd-working-job27.txt','UAVF/IddWorkingProbe/idd-working-test.ps1.before-native-exit']
names+=['ProgramData/UAVF/idd-working-launch-requested28.txt','ProgramData/UAVF/idd-working-job28.txt','UAVF/IddWorkingProbe/idd-working-test.ps1.before-long-workload']
names+=['ProgramData/UAVF/idd-working-launch-requested29.txt','ProgramData/UAVF/idd-working-job29.txt']
# Preserve individual owned stream generations: Android's current report restarts
# on reconnect and is not the history of a temporary source handover.
try:
    own_display=fs.mft.get('ProgramData\\UAVF\\WindowsDisplay')
    servers=[n for n in own_display.listdir(ignore_dos=True) if re.fullmatch(r'LIVE-SERVER-[0-9]{1,10}-[0-9]{1,20}',n)]
    servers=[n for n in servers if int(n.split('-')[2]) in a.stream_owner_pid]
    assert len(servers)<=256
    for server in servers:
        server_path='ProgramData/UAVF/WindowsDisplay/'+server
        names.extend(server_path+'/'+log for log in ('vsock-live-report.txt','vsock-fault-report.txt'))
        generations=[n for n in fs.mft.get(server_path.replace('/','\\')).listdir(ignore_dos=True) if re.fullmatch(r'LIVE-[0-9]{1,10}',n)]
        assert len(generations)<=256
        for generation in generations:
            for log in ('live-encode-report.txt','encoder-stderr.txt'):
                names.append(server_path+'/'+generation+'/'+log)
except (FileNotFoundError,BrokenIndexError,BrokenMftError):pass
names+=['UAVF/IddWorkingProbe/pipe-reconnect23.txt','UAVF/IddWorkingProbe/pipe-reconnect23-errors.txt']
names+=['UAVF/IddWorkingProbe/desktop-topology22.txt','UAVF/IddWorkingProbe/desktop-topology22-before.bin']
names+=['UAVF/IddWorkingProbe/encoded-stage.txt']
names+=['UAVF/IddWorkingProbe/pipe-clock-fixture.json']
names+=['UAVF/IddWorkingProbe/'+name for name in ('pipe-report.txt','pipe-errors.txt','pipe-payload-restored.txt')]
names+=['ProgramData/UAVF/idd-working-launch-state.txt']
names+=['ProgramData/UAVF/display-broker.log']
names+=['UAVF/IddWorkingProbe/'+name for name in ('job-error.txt','rollback.json','admission.txt','admission-errors.txt','driver-stage.txt','source-report.txt','motion-report.txt','result.txt','rollback-complete.txt')]
names+=['UAVF/IddWorkingProbe/'+name for name in ('bcd-current.txt','admission-preflight.json')]
names+=['UAVF/IddWorkingProbe/'+name for name in ('runtime-signature.json','certificate-preflight.json')]
names+=['UAVF/IddWorkingProbe/'+name for name in ('idd-working-test.ps1','idd-working-test-before-ci-query.ps1')]
names+=['UAVF/IddWorkingProbe/clock-admission.json']
names+=['UAVF/IddWorkingProbe/'+name for name in ('admission-token.txt','cert-import-attempt.txt','cert-store-access.txt')]
names+=['UAVF/IddWorkingProbe/audit-error.txt']
names+=['UAVF/IddWorkingProbe/driver-remove.txt']
names+=['UAVF/IddWorkingProbe/rollback-before-store-init.json']
for path in names:
    try:
        stream=fs.mft.get(path.replace('/','\\')).open()
        if stream.size>262144:
            items[path]={'unavailable':'Own log exceeds bounded live snapshot limit','reported_size':stream.size}
            continue
        data=stream.read(stream.size);dest=a.output/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
        items[path]={'bytes':len(data)}
    except FileNotFoundError:items[path]={'unavailable':'Not found in non-coherent live snapshot'}
    except (BrokenIndexError,BrokenMftError) as error:
        items[path]={'unavailable':'Non-coherent live metadata: '+type(error).__name__}
if a.mft_fallback:
    # A growing live directory's $I30 can be unflushed. Inspect only a bounded
    # metadata window, and read DATA only for exact own names/parent+sequence.
    parent=fs.mft.get('UAVF\\IddWorkingProbe')
    assert parent.is_dir()
    total=fs.mft.get(0).size()//fs._record_size
    ranges=[(max(0,parent.segment-2048),min(total-1,parent.segment+2047)),(max(0,total-4096),total-1)]
    wanted={Path(path).name:path for path in names if path.startswith('UAVF/IddWorkingProbe/') and 'unavailable' in items[path]}
    candidates={};visited=set()
    for first,last in ranges:
        for record in fs.mft.segments(first,last):
            if record.segment in visited:continue
            visited.add(record.segment)
            if not record.header.Flags & FILE_RECORD_SEGMENT_IN_USE or not record.is_file():continue
            for attr in record.attributes[ATTRIBUTE_TYPE_CODE.FILE_NAME]:
                if attr.file_name not in wanted:continue
                ref=attr.attr.ParentDirectory
                if segment_reference(ref)==parent.segment and ref.SequenceNumber==parent.header.SequenceNumber:
                    candidates.setdefault(attr.file_name,{})[record.segment]=record
    for name,records in candidates.items():
        if len(records)!=1:continue
        record=next(iter(records.values()));stream=record.open()
        if stream.size>262144:continue
        data=stream.read(stream.size);path=wanted[name];dest=a.output/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
        items[path]={'bytes':len(data),'via':'bounded-own-parent-MFT','segment':record.segment,'coherent':False}
# Read bounded installation diagnostics; export only own probe sections.
try:
    setup=fs.mft.get('Windows\\INF\\setupapi.dev.log').open()
    assert setup.size<=32*1024*1024
    lines=setup.read(setup.size).decode('utf-8',errors='replace').splitlines()
    sections=[];current=[]
    for line in lines:
        if line.startswith('>>>  ['):
            if any('uavfiddsourceprobe' in x.lower() for x in current):sections+=current
            current=[]
        current.append(line)
    if any('uavfiddsourceprobe' in x.lower() for x in current):sections+=current
    (a.output/'own-setupapi.txt').write_text('\n'.join(sections),encoding='utf-8')
except (FileNotFoundError,BrokenIndexError,BrokenMftError):pass
assert owner()==before,'VM owner changed during observation'
(a.output/'report.json').write_text(json.dumps({'logs':items,'read_bytes':root.read_bytes,'live':not a.cold,'coherent':False,'fps_pass':False},indent=2))
print(json.dumps(items))
