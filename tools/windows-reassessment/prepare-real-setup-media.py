"""Replace invalid zero-header install placeholders in a disposable Windows clone."""
import hashlib
import json
import math
import shutil
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS

root=Path(__file__).resolve().parents[3]
folder=root/'build-logs/windows-post-linux-reassessment-20261004'
payload=folder/'install-payload-udf-20261008'
report=json.loads((payload/'payload-report.json').read_text())
assert report['result']=='PASS' and report['architecture']=='ARM64'
source=folder/'windows-display-shell-lifetime-20261008.img'
target=folder/'windows-display-real-setup-20261008.img'
expected='DDC0E4C2EF2E155D700736A7808AABF5999D689787724612615F537F7140363C'
assert not target.exists(), 'Preserve prior media'
assert shutil.disk_usage(folder).free>source.stat().st_size+2*1024**3
with source.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest().upper()==expected
for part in report['parts']:
    path=payload/part['name']
    with path.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest().upper()==part['sha256']
shutil.copyfile(source,target)
with PyFatFS(str(target),offset=1048576) as fs:
    boot=fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')
    for part in report['parts']:
        name='/SOURCES/'+part['name'].upper()
        with fs.openbin(name,'r') as stream:
            assert stream.read(48)==bytes(48), 'Not the diagnosed zero-header placeholder'
        entry=fs.fs.root_dir.get_entry(name)
        chain=list(fs.fs.get_cluster_chain(entry.get_cluster()))
        block=fs.fs.bytes_per_cluster
        needed=math.ceil(part['bytes']/block)
        extra=needed-len(chain)
        if extra>0:
            free=[i for i in range(2,len(fs.fs.fat)) if fs.fs.fat[i]==0][:extra]
            assert len(free)==extra, 'Insufficient media FAT space'
            chain+=free
        for current,nxt in zip(chain,chain[1:]):fs.fs.fat[current]=nxt
        fs.fs.fat[chain[-1]]=0x0fffffff
        fs.fs.flush_fat()
        with (payload/part['name']).open('rb') as stream:
            for cluster in chain[:needed]:
                fs.fs.write_data_to_cluster(stream.read(block).ljust(block,b'\0'),cluster,extend_cluster=False)
        entry.filesize=part['bytes']
        fs.fs.update_directory_entry(entry.get_parent_dir())
        with fs.openbin(name,'r') as stream:
            assert hashlib.file_digest(stream,'sha256').hexdigest().upper()==part['sha256']
        print('MEDIA_PART_VERIFIED',part['name'],flush=True)
    assert fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')==boot
with target.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
result=dict(result='PASS',path=str(target),bytes=target.stat().st_size,sha256=sha,
            base_sha256=expected,platform_base_sha256='A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5',
            boot_sha256=hashlib.sha256(boot).hexdigest().upper(),parts=report['parts'],
            scope='Valid Setup source; no install target or runtime/display PASS')
(folder/'real-setup-disk-report.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result),flush=True)
