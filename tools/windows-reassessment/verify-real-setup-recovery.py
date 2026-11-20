"""Prove old03E3 recovery through reverse deltas without another full image."""
import hashlib
import json
import struct
import zlib
from pathlib import Path

folder=Path(__file__).resolve().parents[3]/'build-logs/windows-post-linux-reassessment-20261004'
source=folder/'windows-display-live-name-fix-20261008.img'
report=folder/'real-setup-recovery-20261009.json'
assert not report.exists(), 'Preserve prior audit'
size=9126805504
assert source.stat().st_size==size
blocks={}
audits=[]
with source.open('rb') as disk:
    for name in ('live-name-fix','live','producer-accept-fix','producer-fault','producer-stderr','producer-exit'):
        path=folder/(name+'.reverse.delta')
        payload=path.read_bytes()
        assert payload[:8]==b'UWDP0001'
        disk_size,count=struct.unpack_from('<QI',payload,8)
        assert disk_size==size and 0<count<32
        cursor=20
        previous_end=0
        for _ in range(count):
            offset,n,old_crc,new_crc=struct.unpack_from('<QIII',payload,cursor);cursor+=20
            assert offset%1048576==0 and offset>=previous_end and 0<n<=1048576 and offset+n<=size
            data=payload[cursor:cursor+n];cursor+=n
            if offset in blocks:
                old=blocks[offset]
            else:
                disk.seek(offset);old=disk.read(n)
            assert len(old)==n and zlib.crc32(old)==old_crc
            assert len(data)==n and zlib.crc32(data)==new_crc
            blocks[offset]=data
            previous_end=offset+n
        assert cursor==len(payload)
        audits.append(dict(file=path.name,sha256=hashlib.sha256(payload).hexdigest(),blocks=count))
    disk.seek(0)
    original=hashlib.sha256();recovered=hashlib.sha256();offset=0
    while chunk:=disk.read(1048576):
        original.update(chunk)
        recovered.update(blocks.get(offset,chunk))
        offset+=len(chunk)
assert original.hexdigest().upper()=='A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A'
assert recovered.hexdigest().upper()=='03E3DB8CBD9B3B7B16DD0AE22C356DDB68E9B4B5847FDD305A7E2692DABB8EE5'
result=dict(status='FULL_RECOVERY_VERIFIED_WITHOUT_IMAGE_CLONE',source=str(source),source_sha256=original.hexdigest(),recovered_sha256=recovered.hexdigest(),reverse_deltas=audits,scope='No source/device mutation')
report.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
