"""Cold, journaled staging of one new test folder on isolated Windows ESP.
No full image copy. Existing EFI/BCD/WIM/root data remain outside write scope.
"""
import argparse, hashlib, importlib.util, io, json, struct, subprocess
from pathlib import Path
from pyfatfs.PyFatFS import PyFatBytesIOFS

p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--payload',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--apply',action='store_true')
p.add_argument('--wddm-admission',action='store_true')
p.add_argument('--wddm-revision',type=int,choices=range(1,10),default=1)
a=p.parse_args();assert not a.output.exists();a.output.mkdir(parents=True)
spec=importlib.util.spec_from_file_location('fat_guard',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
efi_sha=g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA if a.wddm_admission else g.EFI_SHA
assert a.wddm_admission or a.wddm_revision==1
folder=('WDDMTEST' if a.wddm_revision==1 else f'WDDMTS{a.wddm_revision}') if a.wddm_admission else 'IDDWORK'
guard=g.DeviceFat(a.adb,a.output,expected_efi_sha=efi_sha);guard.cold()
root=subprocess.check_output([a.adb,'exec-out',f'run-as {g.PACKAGE} dd if={g.DISK} bs=512 skip=17858560 count=1 2>/dev/null'])
assert root[3:11]==b'NTFS    ' and root[72:80].hex()=='b769f63e81f63ec6'

class Overlay(io.RawIOBase):
    def __init__(self):self.pos=0;self.before={};self.pages={};self.dirty=set()
    def readable(self):return True
    def writable(self):return True
    def seekable(self):return True
    def tell(self):return self.pos
    def seek(self,n,w=0):
        self.pos=n if w==0 else (self.pos if w==1 else g.END-g.PART)+n
        assert 0<=self.pos<=g.END-g.PART;return self.pos
    def page(self,i):
        if i not in self.pages:
            assert len(self.pages)<1024,'64MiB read/write budget'
            n=min(65536,g.END-g.PART-i*65536)
            data=guard.read(g.PART+i*65536,n);self.before[i]=data;self.pages[i]=bytearray(data)
        return self.pages[i]
    def read(self,n=-1):
        assert 0<=n<=16*1024*1024
        out=bytearray()
        while n:
            i,k=divmod(self.pos,65536);page=self.page(i);take=min(n,len(page)-k)
            assert take>0;out+=page[k:k+take];self.pos+=take;n-=take
        return bytes(out)
    def write(self,data):
        size=len(data);cursor=0
        while cursor<size:
            i,k=divmod(self.pos,65536);page=self.page(i);take=min(size-cursor,len(page)-k)
            assert take>0;page[k:k+take]=data[cursor:cursor+take];self.dirty.add(i);self.pos+=take;cursor+=take
        return size
    def close(self):pass # Keep journal accessible after PyFat close.

overlay=Overlay();files={}
allowed_existing_data=set()
for cluster in guard.chain(guard.root):
    allowed_existing_data.update(range(guard.offset(cluster),guard.offset(cluster)+guard.cluster_bytes,512))
with PyFatBytesIOFS(overlay) as fs:
    assert not fs.exists('/'+folder),'Never overwrite existing admission payload'
    fs.makedir('/'+folder)
    if not a.wddm_admission:fs.makedir('/IDDWORK/package')
    for src in sorted(a.payload.rglob('*')):
        if src.is_file():
            name=src.relative_to(a.payload).as_posix();data=src.read_bytes();assert len(data)<=16*1024*1024
            fs.writebytes('/'+folder+'/'+name,data);assert fs.readbytes('/'+folder+'/'+name)==data
            files[name]=hashlib.sha256(data).hexdigest()
changed=[i for i in sorted(overlay.dirty) if bytes(overlay.pages[i])!=overlay.before[i]]
assert len(changed)*65536<=32*1024*1024
for i in changed:
    before=overlay.before[i];after=overlay.pages[i]
    for k in range(0,len(before),512):
        address=g.PART+i*65536+k
        if before[k:k+512]==after[k:k+512] or address<guard.data or address in allowed_existing_data:continue
        cluster=2+(address-guard.data)//guard.cluster_bytes
        pos=guard.fat+cluster*4-g.PART;page,index=divmod(pos,65536)
        # Original FAT cached before the filesystem mutation, never altered bytes.
        overlay.page(page)
        old_entry=struct.unpack_from('<I',overlay.before[page],index)[0]&0x0fffffff
        assert old_entry==0,'Attempt to alter preexisting allocated data cluster refused'
for i in changed:
    (a.output/f'{i}-before.bin').write_bytes(overlay.before[i]);(a.output/f'{i}-after.bin').write_bytes(overlay.pages[i])
report={'scope':'One new '+folder+' folder on the isolated diagnostic Windows ESP','pages':changed,'files':files,'applied':False,'driver_loaded':False}
(a.output/'plan.json').write_text(json.dumps(report,indent=2))
def write(i,data):
    guard.cold();tmp=a.output/'transfer.bin';tmp.write_bytes(data)
    remote='/data/local/tmp/uavf-idd-fat-transfer.bin'
    subprocess.run([a.adb,'push',str(tmp),remote],check=True,capture_output=True)
    subprocess.run([a.adb,'shell','chmod','644',remote],check=True,capture_output=True)
    subprocess.run([a.adb,'shell','run-as',g.PACKAGE,'dd','if='+remote,'of='+g.DISK,'bs=512','seek='+str((g.PART+i*65536)//512),'count='+str(len(data)//512),'conv=notrunc'],check=True,capture_output=True)
    assert guard.read(g.PART+i*65536,len(data))==data
if a.apply:
    for i in changed:assert guard.read(g.PART+i*65536,len(overlay.before[i]))==overlay.before[i],'Cold preimage changed'
    # Data first, FAT/root metadata last; all affected pages retain preimages.
    order=sorted(changed,key=lambda i:(g.PART+i*65536<guard.data,i))
    touched=[]
    try:
        for i in order:touched.append(i);write(i,bytes(overlay.pages[i]))
        assert g.sha(guard.file('/EFI/BOOT/BOOTAA64.EFI')[0])==efi_sha
        report['applied']=True
    except Exception:
        for i in reversed(touched):write(i,overlay.before[i])
        raise
    finally:subprocess.run([a.adb,'shell','rm','-f','/data/local/tmp/uavf-idd-fat-transfer.bin'],check=True,capture_output=True)
(a.output/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps({'staged_bytes':sum((a.payload/n).stat().st_size for n in files),'changed_pages':len(changed),'applied':report['applied'],'driver_loaded':False}))
