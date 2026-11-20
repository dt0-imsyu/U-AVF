"""Read-only, non-coherent snapshot of OWN diagnostic logs in isolated VM.

Never writes/mounts the Android disk. Positive persisted markers are useful;
missing/stale data is NOT evidence of guest failure because NTFS can be cached.
No registry, accounts, user files or unrelated filesystem tree are inspected.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess

p = argparse.ArgumentParser()
p.add_argument("--adb", required=True)
p.add_argument("--output", type=Path, required=True)
p.add_argument("--producer-pid", type=int, help="Read bounded own producer diagnostics for an observed PID only")
p.add_argument("--producer-cid", type=int, help="Select own producer logs by current observed VM CID, not recycled PID")
p.add_argument("--crash-reports", action="store_true", help="Read bounded Windows crash summaries, never user documents or dumps")
p.add_argument("--cpu-profile-pid", type=int, help="Read exact observed own broker's private CPU ETL/log only; never LSASS dumps")
p.add_argument('--cold',action='store_true',help='Require VMs[] before/after; read same isolated own diagnostic files only')
p.add_argument('--wgc-user-run-id',type=int,help='Read only our exact uavf-wgc-<CID>/wgc-source-report.txt in the known test account TEMP')
p.add_argument('--wgc-user-root-report',action='store_true',help='Read only our wgc-source-report.txt from the observed uavf console working directory')
a = p.parse_args()
assert not a.output.exists(), "Preserve earlier evidence"
assert a.producer_pid is None or 1 <= a.producer_pid <= 0xffffffff
assert a.producer_cid is None or 3 <= a.producer_cid <= 0xffffffff
assert not (a.producer_pid is not None and a.producer_cid is not None)
assert a.cpu_profile_pid is None or 1 <= a.cpu_profile_pid <= 0xffffffff


def owner():
    state = subprocess.check_output([a.adb, "shell", "/apex/com.android.virt/bin/vm", "list"], text=True)
    if a.cold:
        assert 'Running VMs: []' in state,'Cold evidence requires no running VM'
        return dict(cold=True,package='com.example.uavfwinpetest')
    matches = re.findall(r"cid: (\d+).*?requesterUid: (\d+).*?requesterPid: (\d+)", state, re.S)
    assert len(matches) == 1 and matches[0][1] == "10042", "Only the live isolated diagnostic owner"
    return dict(cid=int(matches[0][0]), uid=int(matches[0][1]), pid=int(matches[0][2]))


before = owner()
if a.producer_cid is not None and not a.cold:
    assert before['cid']==a.producer_cid,'Observed CID must match the current isolated VM'
spec = importlib.util.spec_from_file_location("cold_reader", Path(__file__).with_name("extract-installed-setup-evidence.py"))
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
disk = reader.ColdRoot(a.adb)  # Class implements reads only; no DeviceFat writer/guard imported.
boot = disk.read(512)
assert boot[3:11] == b"NTFS    " and struct.unpack_from("<I", boot, 72)[0] == 0x3ef669b7
disk.seek(0)
fs = reader.NTFS(disk)
a.output.mkdir(parents=True)
report = dict(owner=before, read_only=True, filesystem_coherent=False,
              limitation="Positive persisted markers only; missing/stale data cannot prove a hang", files=[])
if a.wgc_user_run_id is not None or a.wgc_user_root_report:
    assert not (a.wgc_user_run_id is not None and a.wgc_user_root_report)
    if a.wgc_user_run_id is not None:
        assert 3 <= a.wgc_user_run_id <= 0xffffffff
        path = f'Users\\uavf\\AppData\\Local\\Temp\\uavf-wgc-{a.wgc_user_run_id}\\wgc-source-report.txt'
    else:
        path = 'Users\\uavf\\wgc-source-report.txt'
    try:
        stream = fs.mft.get(path).open()
        assert stream.size <= 65536, 'Own WGC text report only'
        data = stream.read(stream.size)
        (a.output / 'wgc-user-source-report.txt').write_bytes(data)
        report['files'].append(dict(name=path,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
        print('WGC_USER_SOURCE_REPORT\n' + data.decode('ascii','replace'))
    except Exception as error:
        report['files'].append(dict(name=path,unavailable=type(error).__name__+'; execution unconfirmed'))
for name in ("display-broker.log", "producer-restarts.txt", "runtime-cpu.log"):
    try:
        stream = fs.mft.get("ProgramData\\UAVF\\" + name).open()
        assert stream.size <= 1024 * 1024, "Bounded owned diagnostics only"
        data = stream.read(stream.size)
        (a.output / name).write_bytes(data)
        report["files"].append(dict(name=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
        print(name + "\n" + data.decode("ascii", "replace")[-4096:])
    except Exception as error:
        report["files"].append(dict(name=name, unavailable=type(error).__name__))
if a.cpu_profile_pid is not None:
    for name,limit in ((f'cpu-profile-{a.cpu_profile_pid}.log',256*1024),
                       (f'cpu-sample-{a.cpu_profile_pid}.etl',32*1024*1024)):
        try:
            try:stream=fs.mft.get('ProgramData\\UAVF\\'+name).open()
            except FileNotFoundError:
                # A live directory index can be non-coherent for binary search.
                # Enumerate only OUR bounded diagnostic folder, dereference
                # only the exact previously observed broker's filename.
                folder=fs.mft.get('ProgramData\\UAVF')
                matching=[]
                for index,entry in enumerate(folder.iterdir()):
                    assert index<256,'Bounded own directory only'
                    if entry.attribute.file_name.casefold()==name.casefold():matching.append(entry)
                if not matching:raise FileNotFoundError('Exact own profile file not persisted yet')
                assert len(matching)==1,'Ambiguous own profile identity'
                stream=matching[0].dereference().open()
            if stream.size==0:
                report['files'].append(dict(name=name,pending='Empty live file; not proof of failure'));continue
            assert 0<stream.size<=limit, 'Bounded own CPU evidence only'
            data=stream.read(stream.size)
            (a.output/name).write_bytes(data)
            report['files'].append(dict(name=name,bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),private_local_diagnostic=True))
            print(name+' bytes='+str(len(data)))
            if name.endswith('.log'):print(data.decode('utf-8','replace')[-4096:])
            assert disk.read_bytes<=64*1024*1024, 'Read budget'
        except FileNotFoundError:
            report['files'].append(dict(name=name,unavailable='FileNotFoundError'))
if a.producer_pid is not None or a.producer_cid is not None:
    directory = fs.mft.get("ProgramData\\UAVF\\WindowsDisplay")
    prefix = "LIVE-SERVER-"+(str(a.producer_pid)+"-" if a.producer_pid is not None else "")
    matches=[]
    for index,entry in enumerate(directory.iterdir(ignore_dos=True)):
        assert index<512,"Bounded own producer directory inventory"
        if not entry.attribute.file_name.startswith(prefix):continue
        if a.producer_cid is not None:
            try:
                # Resolve only the fixed own identity filename. Old producer
                # folders can contain hundreds of encoder generations; do not
                # enumerate all of them merely to identify the current VM.
                selected=fs.mft.get("ProgramData\\UAVF\\WindowsDisplay\\"+entry.attribute.file_name+"\\vsock-live-report.txt")
                stream=selected.open()
                # CID is in the fixed report header. Old packet logs may be
                # large; identity selection must not read their payload log.
                identity=stream.read(min(stream.size,1024))
                if not re.search(rb"(?m)^CID="+str(a.producer_cid).encode()+rb"\r?$",identity):continue
            except FileNotFoundError:continue
        matches.append(entry)
        assert disk.read_bytes<=64*1024*1024,"Read budget"
    assert len(matches) <= 4, "Bounded exact producer identity"
    def own_reports(record, relative, depth=0):
        if depth > 1:
            return
        for entry in record.iterdir(ignore_dos=True):
            name = entry.attribute.file_name
            if name in (".", ".."):
                continue
            child = entry.dereference()
            path = relative + "/" + name
            if child.is_dir():
                if name.startswith("LIVE-"):
                    own_reports(child, path, depth + 1)
            elif name in ("vsock-live-report.txt", "vsock-fault-report.txt", "clipboard-status.txt", "wgc-source-report.txt",
                           "live-encode-report.txt", "encoder-stderr.txt"):
                stream = child.open()
                assert stream.size <= 1024 * 1024, "Bounded own report"
                data = stream.read(stream.size)
                target = a.output / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                report["files"].append(dict(name=path, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
                print(path + "\n" + data.decode("ascii", "replace")[-4096:])
                assert disk.read_bytes <= 64 * 1024 * 1024, "Read budget"
    for entry in matches:
        own_reports(entry.dereference(), entry.attribute.file_name)

if a.crash_reports:
    for base in ("ProgramData/Microsoft/Windows/WER/ReportArchive", "ProgramData/Microsoft/Windows/WER/ReportQueue"):
        try:
            directory=fs.mft.get(base.replace("/", "\\"))
            entries=[entry for entry in directory.iterdir(ignore_dos=True)
                     if entry.attribute.file_name.startswith(("AppCrash_", "AppHang_"))]
        except FileNotFoundError:
            continue
        assert len(entries)<=64, "Bounded crash summary enumeration"
        for entry in entries:
            folder=entry.attribute.file_name
            try:
                child=entry.dereference()
                summary=next(item for item in child.iterdir(ignore_dos=True) if item.attribute.file_name=="Report.wer")
                stream=summary.dereference().open()
                assert stream.size<=256*1024, "Summary only, not dump"
                data=stream.read(stream.size)
                path=base+"/"+folder+"/Report.wer"
                target=a.output/path;target.parent.mkdir(parents=True, exist_ok=True);target.write_bytes(data)
                report["files"].append(dict(name=path, bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
                lines=data.decode("utf-16" if data.startswith(b"\xff\xfe") else "utf-8", "replace").splitlines()
                print(path+"\n"+"\n".join(line for line in lines if re.match(r"^(EventType|AppName|AppPath|Sig\[\d+\]\.(Name|Value))=",line)))
                assert disk.read_bytes<=64*1024*1024, "Read budget"
            except (FileNotFoundError, StopIteration):
                continue

after = owner()
assert before == after, "Owner changed during observational read"
report.update(device_bytes_read=disk.read_bytes, owner_lifetime_unchanged=True)
(a.output / "report.json").write_text(json.dumps(report, indent=2))
print("OWNED_LOG_READ_ONLY_SNAPSHOT_COMPLETE; NOT_COHERENT_FILESYSTEM_AUDIT")
