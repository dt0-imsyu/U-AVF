"""Replace only BOOT.WIM on the cold, separately labelled disposable disk.

Actual FAT preimages retained; original production filename is not a target.
Driver/kernel/platform unchanged; test job admitted separately by diagnostic APK.
"""
import hashlib
import importlib.util
import json
import math
import struct
import subprocess
from pathlib import Path

BASE=Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
OUT=BASE/'bootstrap-wim-apply'
SOURCE=Path('E:/U-AVF/artifacts/idd-bootstrap-wim-20261013/BOOT.WIM')
EXPECTED='DD297166CD89B075B7242DD33D3583ABB2159CFB3EDFF533629723B48E7D56B0'
ORIGINAL='A90BA45CC1404CF18FE2F10CE195E565B8F70E45416583F2F25F3EF4391BEF15'
ADB=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe')
TARGET='files/windows-idd-disposable-20261013.img'
assert not OUT.exists()
assert json.loads((BASE/'activation.json').read_text())['status']=='ORIGINAL_RESTORED_IDENTITY_PASS'
with SOURCE.open('rb') as src:assert hashlib.file_digest(src,'sha256').hexdigest().upper()==EXPECTED
spec=importlib.util.spec_from_file_location('iddfat',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
g.DISK=TARGET
OUT.mkdir()
f=g.DeviceFat(ADB,OUT,g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA)
def read(offset,size):
    assert 0<=offset and 0<size<=64*1024**2 and offset+size<=g.END
    assert offset%512==0
    length=(size+511)//512*512
    data=subprocess.check_output([ADB,'exec-out',f'run-as {g.PACKAGE} dd if={TARGET} bs=512 skip={offset//512} count={length//512} 2>/dev/null'])
    assert len(data)==length;return data[:size]
directory=next(e[2] for e in f.entries(f.root) if e[1]=='SOURCES')
entry=next(e for e in f.entries(directory) if e[1]=='BOOT.WIM')
first,oldsize=entry[2],entry[3]
boot=f.read(g.PART,512);fat_bytes=struct.unpack_from('<I',boot,36)[0]*512
mirror=f.fat+fat_bytes
table=bytearray()
for offset in range(0,fat_bytes,65536):
    part=f.read(f.fat+offset,min(65536,fat_bytes-offset))
    assert part==f.read(mirror+offset,len(part));table.extend(part)
chain=[];number=first
while number<0x0ffffff8:
    assert 2<=number<len(table)//4 and len(chain)<100000 and number not in set(chain[-8:])
    chain.append(number);number=struct.unpack_from('<I',table,number*4)[0]&0x0fffffff
assert len(set(chain))==len(chain) and len(chain)==math.ceil(oldsize/8192)
def segments(clusters):
    result=[]
    for c in clusters:
        offset=f.offset(c)
        if result and result[-1][0]+result[-1][1]==offset and result[-1][1]<64*1024**2:
            result[-1]=(result[-1][0],result[-1][1]+8192)
        else:result.append((offset,8192))
    return result
def hash_file(clusters,size):
    digest=hashlib.sha256();left=size
    for offset,length in segments(clusters):
        part=read(offset,min(length,left));digest.update(part);left-=len(part)
        if not left:break
    assert left==0;return digest.hexdigest().upper()
assert hash_file(chain,oldsize)==ORIGINAL,'Actual frozen parent WIM, not filename inference'
needed=math.ceil(SOURCE.stat().st_size/8192)
assert len(chain)<=needed<=len(chain)+256,'Only bounded <=2MiB append for new WIM metadata/job'
extra=[]
for c in range(2,len(table)//4):
    if len(extra)==needed-len(chain):break
    if struct.unpack_from('<I',table,c*4)[0]==0 and f.offset(c)+8192<=g.END:extra.append(c)
assert len(extra)==needed-len(chain)
newchain=chain+extra
metadata={}
def edit(offset,within,value):
    if offset not in metadata:metadata[offset]=[f.read(offset,512),None]
    old,new=metadata[offset]
    if new is None:new=bytearray(old);metadata[offset][1]=new
    struct.pack_into('<I',new,within,value)
if extra:
    for c,nxt in zip([chain[-1]]+extra,extra+[0x0fffffff]):
        for start in (f.fat,mirror):edit(start+(c*4)//512*512,c*4%512,nxt)
found=[]
for c in f.chain(directory):
    data=f.read(f.offset(c),8192)
    for pos in range(0,len(data),32):
        if data[pos:pos+11]==b'BOOT    WIM':found.append(f.offset(c)+pos)
assert len(found)==1
address=found[0];edit(address//512*512,address%512+28,SOURCE.stat().st_size)
for offset,(old,new) in metadata.items():
    (OUT/f'{offset}-old.bin').write_bytes(old);(OUT/f'{offset}-new.bin').write_bytes(new)
remote='/sdcard/Android/data/com.example.uavfwinpetest/files/idd-wim-update.tmp'
def write(offset,data):
    f.cold();local=OUT/'chunk.bin';local.write_bytes(data)
    subprocess.run([ADB,'push',str(local),remote],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    assert offset%512==len(data)%512==0
    subprocess.run([ADB,'shell','run-as',g.PACKAGE,'dd','if='+remote,'of='+TARGET,'bs=512','seek='+str(offset//512),'count='+str(len(data)//512),'conv=notrunc'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    assert read(offset,len(data))==data
with SOURCE.open('rb') as src:
    copied=0
    for offset,length in segments(newchain):
        data=src.read(length);write(offset,data.ljust(length,b'\0'))
        copied+=len(data);print(f'NEW_BOOTSTRAP_WIM_READBACK_BYTES={copied}',flush=True)
    assert copied==SOURCE.stat().st_size
for offset,(old,new) in sorted(metadata.items(),key=lambda item:item[0]==address//512*512):
    assert f.read(offset,512)==old;write(offset,bytes(new))
assert hash_file(newchain,SOURCE.stat().st_size)==EXPECTED
assert g.sha(f.file('/EFI/BOOT/BOOTAA64.EFI')[0])==g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA
(OUT/'report.json').write_text(json.dumps(dict(result='DISPOSABLE_BOOTSTRAP_WIM_READBACK_PASS',old_sha256=ORIGINAL,new_sha256=EXPECTED,app_target=TARGET,working_disk_writes=0,extra_clusters=extra),indent=2))
(OUT/'chunk.bin').unlink();subprocess.run([ADB,'shell','rm','-f',remote],check=True)
print('TEST_WIM_READBACK_PASS; NOT_PROVISION_DRIVER_OR_FPS_PASS')
