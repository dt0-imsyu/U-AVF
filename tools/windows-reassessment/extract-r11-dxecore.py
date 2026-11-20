"""Read-only extraction from the exact retained r11 LZMA firmware volume."""
import hashlib, json, lzma, struct, uuid
from pathlib import Path
root=Path(__file__).resolve().parents[3]
log=root/'build-logs/windows-post-linux-reassessment-20261004'
fd=(root/'firmware-work/edk2/artifacts/KVMTOOL_EFI-r11-debug-uart-align.fd').read_bytes()
assert hashlib.sha256(fd).hexdigest().upper()=='9ED3DD035116A39FEAC7C79A9C0C9AC640EA222D499A8EC3B1C6FCEE1ADFE72F'
lzma_guid=uuid.UUID('ee4e5898-3914-4259-9d6e-dc7bd79403cf').bytes_le
core_guid=uuid.UUID('d6a2cb7f-6a18-4e2f-b43b-9920a733700a').bytes_le
streams=[('fd',fd)];pos=0
while True:
    pos=fd.find(lzma_guid,pos)
    if pos<0:break
    begin=pos-4;size=int.from_bytes(fd[begin:begin+3],'little');offset=struct.unpack_from('<H',fd,pos+16)[0]
    pos+=16
    if not (begin>=0 and fd[begin+3]==2 and 24<=offset<size and begin+size<=len(fd)):
        continue  # GUID constants inside executable code are not sections.
    payload=fd[begin+offset:begin+size]
    decoder=lzma.LZMADecompressor(format=lzma.FORMAT_ALONE)
    unpacked=decoder.decompress(payload,max_length=32*1024*1024)
    assert decoder.eof
    streams.append((f'lzma-{begin:x}',unpacked))
found=[]
signature=bytes.fromhex('4300403983002836017f80d220000039')
hits=[]
for source,blob in streams:
    at=0
    while True:
        at=blob.find(signature,at)
        if at<0:break
        containers=[]
        for start in range(0,at,8):
            if start+24>len(blob):break
            size=int.from_bytes(blob[start+20:start+23],'little')
            if blob[start+18] not in (3,5,6,7,9) or not 24<size<=len(blob)-start or not start<=at<start+size:continue
            section=start+24
            while section+4<=start+size:
                n=int.from_bytes(blob[section:section+3],'little');kind=blob[section+3]
                if n<4 or section+n>start+size:break
                if kind in (0x10,0x12) and section+4<=at<section+n:
                    guid=str(uuid.UUID(bytes_le=blob[start:start+16]))
                    filename=f'r11-uart-owner-{guid}.efi'
                    (log/filename).write_bytes(blob[section+4:section+n])
                    containers.append(dict(guid=guid,ffs_offset=start,type=blob[start+18],file=filename,
                                           image_offset=at-section-4))
                section=(section+n+3)&~3
        hits.append(dict(source=source,offset=at,containers=containers,nearby=blob[max(0,at-32):at+64].hex()))
        at+=4
    (log/(source+'-extracted.bin')).write_bytes(blob)
(log/'r11-uart-signature-hits.json').write_text(json.dumps(hits,indent=2))
print(json.dumps({'uart_hits':hits},indent=2))
for source,blob in streams:
    pos=0
    while True:
        pos=blob.find(core_guid,pos)
        if pos<0:break
        size=int.from_bytes(blob[pos+20:pos+23],'little')
        if pos+24<=len(blob) and blob[pos+18]==5 and 24<size<=len(blob)-pos:
            sections=blob[pos+24:pos+size];at=0
            while at+4<=len(sections):
                n=int.from_bytes(sections[at:at+3],'little');kind=sections[at+3]
                if n<4 or at+n>len(sections):break
                if kind in (0x10,0x12):
                    image=sections[at+4:at+n]
                    name=f'r11-DxeCore-{len(found)}.efi';(log/name).write_bytes(image)
                    signature=bytes.fromhex('4300403983002836017f80d220000039')
                    match=image.find(signature)
                    found.append(dict(source=source,file=name,size=len(image),sha256=hashlib.sha256(image).hexdigest().upper(),
                                      uart_instruction_offset=match,nearby=image[max(0,match-24):match+56].hex() if match>=0 else None))
                at=(at+n+3)&~3
        pos+=16
assert found, 'No validated DXE_CORE FFS/PE section'
text=json.dumps(found,indent=2);(log/'r11-dxecore-extraction.json').write_text(text);print(text)
