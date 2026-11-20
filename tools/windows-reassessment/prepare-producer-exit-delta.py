"""Update only own bootstrap in disposable media and emit guarded block delta.

Original A40B baseline stays read-only. Prior03E3 disk can be recovered from
the reverse delta; no second full-size PC clone is required.
"""
import argparse,hashlib,io,json,math,shutil,struct,zlib
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS,PyFatBytesIOFS

root=Path(__file__).resolve().parents[3]
folder=root/'build-logs/windows-post-linux-reassessment-20261004'
source=folder/'windows-display-real-setup-20261008.img'
target=folder/'windows-display-producer-exit-20261008.img'
wim=folder/'producer-exit-candidate.wim'
patch=folder/'producer-exit.delta'
reverse=folder/'producer-exit.reverse.delta'
before_path=folder/'producer-exit-before-blocks.tmp'
expected='03E3DB8CBD9B3B7B16DD0AE22C356DDB68E9B4B5847FDD305A7E2692DABB8EE5'
parser=argparse.ArgumentParser();mode=parser.add_mutually_exclusive_group();mode.add_argument('--stderr',action='store_true');mode.add_argument('--fault',action='store_true');mode.add_argument('--accept-fix',action='store_true');mode.add_argument('--live',action='store_true');mode.add_argument('--live-name-fix',action='store_true');args=parser.parse_args()
name='live-name-fix' if args.live_name_fix else ('live' if args.live else ('producer-accept-fix' if args.accept_fix else ('producer-fault' if args.fault else ('producer-stderr' if args.stderr else 'producer-exit'))))
old_wim=folder/'shell-lifetime-candidate.wim'
if args.stderr:
    source=folder/'windows-display-producer-exit-20261008.img'
    target=folder/'windows-display-producer-stderr-20261008.img'
    wim=folder/'producer-stderr-candidate.wim'
    patch=folder/'producer-stderr.delta';reverse=folder/'producer-stderr.reverse.delta'
    before_path=folder/'producer-stderr-before-blocks.tmp'
    expected='0AEE1BF9E2876DA898C55368943CAC0D7E51959CE4DF21A7AE57FDBF143F10EA'
    old_wim=folder/'producer-exit-candidate.wim'
if args.fault:
    source=folder/'windows-display-producer-stderr-20261008.img'
    target=folder/'windows-display-producer-fault-20261008.img'
    wim=folder/'producer-fault-candidate.wim'
    patch=folder/'producer-fault.delta';reverse=folder/'producer-fault.reverse.delta'
    before_path=folder/'producer-fault-before-blocks.tmp'
    expected='680B2A20078273444A3B69881859A7F51EB45A3564E53BDDD698F584C3BD2720'
    old_wim=folder/'producer-stderr-candidate.wim'
if args.accept_fix:
    source=folder/'windows-display-producer-fault-20261008.img'
    target=folder/'windows-display-producer-accept-fix-20261008.img'
    wim=folder/'producer-accept-fix-candidate.wim'
    patch=folder/'producer-accept-fix.delta';reverse=folder/'producer-accept-fix.reverse.delta'
    before_path=folder/'producer-accept-fix-before-blocks.tmp'
    expected='D05B10B95A533EF7D6CD62FBA4DD46D8EB610980A4DF12F723E563F78DEFBB5D'
    old_wim=folder/'producer-fault-candidate.wim'
if args.live:
    source=folder/'windows-display-producer-accept-fix-20261008.img'
    target=folder/'windows-display-live-20261008.img'
    wim=folder/'live-candidate.wim'
    patch=folder/'live.delta';reverse=folder/'live.reverse.delta'
    before_path=folder/'live-before-blocks.tmp'
    expected='1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE'
    old_wim=folder/'producer-accept-fix-candidate.wim'
if args.live_name_fix:
    source=folder/'windows-display-live-20261008.img'
    target=folder/'windows-display-live-name-fix-20261008.img'
    wim=folder/'live-name-fix-candidate.wim'
    patch=folder/'live-name-fix.delta';reverse=folder/'live-name-fix.reverse.delta'
    before_path=folder/'live-name-fix-before-blocks.tmp'
    expected='E45161B4037F586A1847470E04C6143EC52ACEE1EDB605CC450EB2CC363FEE6F'
    old_wim=folder/'live-candidate.wim'
assert all(not p.exists() for p in (target,patch,reverse,before_path)), 'Preserve existing artifacts'
assert source.resolve().parent==target.resolve().parent==folder.resolve()
assert source.stat().st_size==9126805504 and wim.exists()
assert shutil.disk_usage(folder).free>50*1024**3+1500*1024**2, 'Preserve 50 GiB reserve plus journal headroom'
with source.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest().upper()==expected
with PyFatFS(str(source),offset=1048576,read_only=True) as fs:
    boot=fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')
    with fs.openbin('/SOURCES/BOOT.WIM','r') as stream:
        old_wim_sha=hashlib.file_digest(stream,'sha256').hexdigest()
with old_wim.open('rb') as stream:
    assert hashlib.file_digest(stream,'sha256').hexdigest()==old_wim_sha
source.rename(target) # Exact in-workspace disposable target; reverse retained.

class RecordingDisk(io.RawIOBase):
    BLOCK=1024*1024
    def __init__(self):
        self.disk=target.open('r+b',buffering=0)
        self.before=before_path.open('w+b')
        self.blocks={}
    def readable(self):return True
    def writable(self):return True
    def seekable(self):return True
    def tell(self):return self.disk.tell()
    def seek(self,*args):return self.disk.seek(*args)
    def read(self,n=-1):return self.disk.read(n)
    def write(self,data):
        pos=self.disk.tell()
        assert 0<=pos and pos+len(data)<=9126805504
        if data:
            for index in range(pos//self.BLOCK,(pos+len(data)-1)//self.BLOCK+1):
                if index not in self.blocks:
                    self.disk.seek(index*self.BLOCK)
                    old=self.disk.read(min(self.BLOCK,9126805504-index*self.BLOCK))
                    self.blocks[index]=(self.before.tell(),len(old))
                    self.before.write(old)
            self.disk.seek(pos)
        return self.disk.write(data)
    def flush(self):self.disk.flush()
    def close(self):
        # PyFat closes its file object; keep the journal until delta creation.
        if not self.disk.closed:self.disk.flush()

recording=RecordingDisk()
try:
    with PyFatBytesIOFS(recording,offset=1048576) as fs:
        entry=fs.fs.root_dir.get_entry('/SOURCES/BOOT.WIM')
        chain=list(fs.fs.get_cluster_chain(entry.get_cluster()))
        block=fs.fs.bytes_per_cluster
        needed=math.ceil(wim.stat().st_size/block)
        extra=needed-len(chain)
        if extra>0:
            free=[i for i in range(2,len(fs.fs.fat)) if fs.fs.fat[i]==0][:extra]
            assert len(free)==extra
            chain+=free
        for current,nxt in zip(chain,chain[1:]):fs.fs.fat[current]=nxt
        fs.fs.fat[chain[-1]]=0x0fffffff
        fs.fs.flush_fat()
        with wim.open('rb') as stream:
            for cluster in chain[:needed]:
                fs.fs.write_data_to_cluster(stream.read(block).ljust(block,b'\0'),cluster,extend_cluster=False)
        entry.filesize=wim.stat().st_size
        fs.fs.update_directory_entry(entry.get_parent_dir())
        assert fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')==boot
        with fs.openbin('/SOURCES/BOOT.WIM','r') as stream:
            actual_wim=hashlib.file_digest(stream,'sha256').hexdigest()
        with wim.open('rb') as stream:assert actual_wim==hashlib.file_digest(stream,'sha256').hexdigest()
    recording.before.flush()
    changed=[]
    for index,(position,length) in sorted(recording.blocks.items()):
        recording.before.seek(position);old=recording.before.read(length)
        recording.disk.seek(index*recording.BLOCK);new=recording.disk.read(length)
        if new!=old:changed.append((index*recording.BLOCK,old,new))
    assert 0<len(changed)<32, 'Own bootstrap append should change few blocks; preserve journal on failure'
    for path,invert in ((patch,False),(reverse,True)):
        with path.open('xb') as out:
            out.write(struct.pack('<8sQI',b'UWDP0001',9126805504,len(changed)))
            for offset,old,new in changed:
                if invert:old,new=new,old
                out.write(struct.pack('<QIII',offset,len(new),zlib.crc32(old),zlib.crc32(new)))
                out.write(new)
finally:
    recording.disk.close();recording.before.close()
with target.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
with patch.open('rb') as stream:delta_sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
report=dict(result='PASS',path=str(target),bytes=target.stat().st_size,sha256=sha,
            base_sha256=expected,platform_base_sha256='A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5',
            boot_sha256=hashlib.sha256(boot).hexdigest().upper(),wim_sha256=actual_wim,
            delta_sha256=delta_sha,delta_bytes=patch.stat().st_size,changed_blocks=len(changed),
            scope=('Own live capture/server/bootstrap only; original platform and shared UVF1/UIN1 unchanged; reverse retained; NOT runtime PASS' if args.live or args.live_name_fix else 'Own helper diagnostics only; reverse delta retained; NOT runtime PASS'))
(folder/(name+'-disk-report.json')).write_text(json.dumps(report,indent=2))
assert before_path.resolve().parent==folder.resolve()
before_path.unlink() # Own temporary journal replaced by durable reverse delta.
print(json.dumps(report),flush=True)
