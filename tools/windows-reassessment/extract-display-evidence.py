"""Read-only screenshot extraction from stopped disposable WinPE disk."""
import json,hashlib,struct,zlib,io,subprocess,argparse
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS,PyFatBytesIOFS
root=Path(__file__).resolve().parents[3]
folder=root/'build-logs/windows-post-linux-reassessment-20261004'
image=folder/'windows-display-captured-20261007.img'
p=argparse.ArgumentParser(); p.add_argument('--remote',action='store_true'); p.add_argument('--app-owned',action='store_true'); p.add_argument('--suffix',default=''); args=p.parse_args()
assert not args.app_owned or args.remote, 'App private read requires remote mode'
assert not args.suffix or args.suffix.isalnum(), 'Invalid evidence suffix'
class RemoteDisk(io.RawIOBase):
    def __init__(self): self.pos=0;self.cache={};self.size=9126805504
    def readable(self):return True
    def writable(self):return False
    def seekable(self):return True
    def tell(self):return self.pos
    def seek(self,n,whence=0):
        self.pos=n if whence==0 else self.pos+n if whence==1 else self.size+n
        assert 0<=self.pos<=self.size
        return self.pos
    def read(self,n=-1):
        assert 0<=n<=32*1024*1024, 'Unbounded remote read refused'
        result=bytearray(); block=1024*1024
        while n and self.pos<self.size:
            index=self.pos//block
            if index not in self.cache:
                count=min(block,self.size-index*block)//512
                command=(f'run-as com.example.uavfwinpetest dd if=files/windows-diagnostic.img bs=512 skip={index*block//512} count={count} 2>/dev/null'
                         if args.app_owned else
                         f'dd if=/data/local/tmp/uavf-windows-uart-gas-20261004/windows-kd.img bs=512 skip={index*block//512} count={count} 2>/dev/null')
                data=subprocess.check_output([adb,'exec-out',command])
                assert len(data)==count*512, 'Short remote disk read'
                self.cache[index]=data
            data=self.cache[index]; offset=self.pos%block; take=min(n,len(data)-offset)
            assert take>0
            result+=data[offset:offset+take];self.pos+=take;n-=take
        return bytes(result)
adb=r'C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if args.remote:
    listing=subprocess.check_output([adb,'shell','/apex/com.android.virt/bin/vm list'],text=True)
    assert 'Running VMs: []' in listing, 'Require authoritative stopped VM state before raw disk readback'
    filesystem=PyFatBytesIOFS(RemoteDisk(),offset=1048576)
else:
    assert image.stat().st_size==9126805504, 'Pull not complete'
    filesystem=PyFatFS(str(image),offset=1048576,read_only=True)
out=folder/('winpe-display-evidence'+args.suffix); out.mkdir(exist_ok=True)
items=[]
with filesystem as fs:
    print('ROOT',list(fs.listdir('/')),flush=True)
    if not fs.exists('/UAVF-DISPLAY'):
        print('NO_SCREENSHOT_DIRECTORY',flush=True)
    else:
        print('CAPTURE_FILES',fs.listdir('/UAVF-DISPLAY'),flush=True)
        for name in fs.listdir('/UAVF-DISPLAY'):
            if name.lower() in ('capture.uvf','encode-report.txt','vsock-report.txt','setup-exit-report.txt','vsock-exit-report.txt','vsock-stderr.txt','vsock-fault-report.txt') or name.lower().startswith('setup-log-'):
                target=out/name;assert not target.exists(), 'Preserve evidence'
                data=fs.readbytes('/UAVF-DISPLAY/'+name)
                target.write_bytes(data)
                if name.lower().endswith('.txt'):print(data.decode('ascii'),flush=True)
                else:
                    pos=0;packets=[]
                    while pos<len(data):
                        assert len(data)-pos>=40
                        magic,version,kind,codec,gen,seq,w,h,pts,size,res=struct.unpack_from('<4sHHIIIHHQII',data,pos)
                        assert magic==b'UVF1' and version==1 and kind==2 and codec==1
                        assert 0<size<=4194304 and pos+40+size<=len(data)
                        payload=data[pos+40:pos+40+size]
                        assert payload.startswith(b'\0\0\0\1') or payload.startswith(b'\0\0\1')
                        packets.append(dict(sequence=seq,width=w,height=h,pts_us=pts,bytes=size,sha256=hashlib.sha256(payload).hexdigest()))
                        pos+=40+size
                    (out/'uvf-report.json').write_text(json.dumps(packets,indent=2))
                    print('UVF1_PACKETS',len(packets),flush=True)
                continue
            if not name.lower().endswith('.bmp'):continue
            data=fs.readbytes('/UAVF-DISPLAY/'+name)
            assert data[:2]==b'BM' and len(data)>=54
            target=out/name; assert not target.exists(), 'Preserve evidence'
            target.write_bytes(data)
            offset=struct.unpack_from('<I',data,10)[0]
            width,height=struct.unpack_from('<ii',data,18)
            assert 0<width<=8192 and 0<abs(height)<=8192
            assert struct.unpack_from('<H',data,28)[0]==32 and struct.unpack_from('<I',data,30)[0]==0
            rows=[]; nonblack=0
            for y in range(abs(height)):
                sy=y if height<0 else height-1-y
                row=data[offset+sy*width*4:offset+(sy+1)*width*4]
                assert len(row)==width*4
                rgb=bytearray()
                for x in range(0,len(row),4):
                    b,g,r=row[x:x+3];rgb.extend((r,g,b)); nonblack+=bool(r or g or b)
                rows.append(b'\0'+rgb)
            def chunk(kind,payload):
                return struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload))
            png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,abs(height),8,2,0,0,0))
            png+=chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
            target.with_suffix('.png').write_bytes(png)
            items.append(dict(file=name,width=width,height=abs(height),
                nonblack=nonblack,sha256=hashlib.sha256(data).hexdigest()))
(out/'report.json').write_text(json.dumps(items,indent=2))
print(json.dumps(items),flush=True)
