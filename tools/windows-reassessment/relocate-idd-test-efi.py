"""Relocate unchanged own EFI in NEW test carrier to admit bounded GOP growth.

Original carrier EFI precedes boot.wim, so the old installed-disk planner finds
no free cluster within its strict 4096-cluster window. Do not weaken that guard.
Move only our unchanged file to a verified free pair on the disposable ESP.
"""
import hashlib
import json
import subprocess
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS

BASE=Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
IMAGE=BASE/'windows-idd-test.img'
OUT=BASE/'efi-relocation'
ADB=Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'
REMOTE='files/windows-idd-disposable-20261013.img'
PART=1048576
EXPECTED='893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B'
assert not OUT.exists()
assert json.loads((BASE/'activation.json').read_text())['status']=='ORIGINAL_RESTORED_IDENTITY_PASS'
assert 'Running VMs: []' in subprocess.check_output([str(ADB),'shell','/apex/com.android.virt/bin/vm','list'],text=True)
assert json.loads((BASE/'transfer.json').read_text())['result']=='READBACK_PASS_NOT_BOOT_OR_FPS'
OUT.mkdir()
with PyFatFS(str(IMAGE),offset=PART) as fs:
    e=fs.fs.root_dir.get_entry('/EFI/BOOT/BOOTAA64.EFI')
    original=fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')
    assert len(original)==8192 and hashlib.sha256(original).hexdigest().upper()==EXPECTED
    old=e.get_cluster()
    assert len(list(fs.fs.get_cluster_chain(old)))==1
    new=next(i for i in range(2,len(fs.fs.fat)-1) if fs.fs.fat[i]==fs.fs.fat[i+1]==0)
    block=fs.fs.bytes_per_cluster; assert block==8192
    fat=PART+fs.fs.bpb_header['BPB_RsvdSecCnt']*512
    mirror=fat+fs.fs.bpb_header['BPB_FATSz32']*512
    dirchain=list(fs.fs.get_cluster_chain(e.get_parent_dir().get_cluster()))
    offsets={PART+fs.fs.get_data_cluster_address(new):8192}
    for table in (fat,mirror):
        for number in (old,new): offsets[table+(number*4)//512*512]=512
    for c in dirchain: offsets[PART+fs.fs.get_data_cluster_address(c)]=8192
    with IMAGE.open('rb') as src:
        before={}
        for offset,size in offsets.items():src.seek(offset);before[offset]=src.read(size)
    fs.fs.fat[new]=0x0fffffff
    fs.fs.write_data_to_cluster(original,new,extend_cluster=False)
    fs.fs.fat[old]=0
    fs.fs.flush_fat();e.set_cluster(new);fs.fs.update_directory_entry(e.get_parent_dir())
    assert fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')==original
with IMAGE.open('rb') as src:
    changes=[]
    for offset,size in offsets.items():
        src.seek(offset);after=src.read(size)
        if after!=before[offset]:
            (OUT/f'{offset}-old.bin').write_bytes(before[offset])
            (OUT/f'{offset}-new.bin').write_bytes(after)
            changes.append((offset,size))
def read(offset,size):
    result=subprocess.check_output([str(ADB),'exec-out',f'run-as com.example.uavfwinpetest dd if={REMOTE} bs=1 skip={offset} count={size} 2>/dev/null'])
    assert len(result)==size;return result
for offset,size in changes:assert read(offset,size)==before[offset], 'Exact test-only preimage'
# Data then FAT then directory. No writes to default/protected working filename.
for offset,size in sorted(changes,key=lambda v:0 if v[0]==PART+fs.fs.get_data_cluster_address(new) else 1):
    payload=OUT/f'{offset}-new.bin'
    stage='/sdcard/Android/data/com.example.uavfwinpetest/files/idd-efi-relocation.tmp'
    subprocess.run([str(ADB),'push',str(payload),stage],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run([str(ADB),'shell','run-as','com.example.uavfwinpetest','dd','if='+stage,'of='+REMOTE,'bs=1','seek='+str(offset),'count='+str(size),'conv=notrunc'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    assert read(offset,size)==payload.read_bytes()
subprocess.run([str(ADB),'shell','rm','-f',stage],check=True)
(OUT/'report.json').write_text(json.dumps(dict(result='TEST_ONLY_LAYOUT_READBACK_PASS',old_cluster=old,new_cluster=new,efi_sha256=EXPECTED,blocks=changes,working_disk_writes=0),indent=2))
print('TEST_ONLY_UNCHANGED_EFI_RELOCATION_READBACK_PASS; NOT_BOOT_OR_FPS')
