"""Bounded receiver, only the exact shell-owned isolated Windows VM."""
import argparse,re,subprocess,json,struct,hashlib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('directory');a=p.parse_args()
adb=r'C:\Users\denis\AppData\Local\Android\Sdk\platform-tools\adb.exe'
state=subprocess.check_output([adb,'shell','/apex/com.android.virt/bin/vm list'],text=True)
items=re.findall(r'VirtualMachineDebugInfo \{(.*?)\}',state,re.S)
assert len(items)==1,'Require exactly one isolated VM, never production'
item=items[0];assert 'requesterUid: 2000,' in item
cid=int(re.search(r'cid: (\d+),',item)[1]);pid=int(re.search(r'requesterPid: (\d+),',item)[1])
cmdline=subprocess.check_output([adb,'shell',f'cat /proc/{pid}/cmdline'])
assert b'/data/local/tmp/uavf-windows-uart-gas-20261004/vm-config.json' in cmdline,'Not isolated Windows'
out=Path(a.directory);out.mkdir(exist_ok=True)
with (out/'received.uvf').open('xb') as f,(out/'receiver.stderr.txt').open('xb') as err:
    proc=subprocess.Popen([adb,'exec-out',f'/data/local/tmp/uavf-windows-uart-gas-20261004/uvf-vsock-reader {cid}'],stdout=f,stderr=err)
    try:code=proc.wait(timeout=80)
    except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=5);raise
print('RECEIVER_EXIT',code,(out/'receiver.stderr.txt').read_text(),flush=True)
data=(out/'received.uvf').read_bytes();pos=0;packets=[]
if not data.startswith(b'UVF1'):
    message=data.decode('utf-8',errors='replace')
    (out/'report.json').write_text(json.dumps(dict(result='NO_TRANSPORT_PASS',adb_exec_exit=code,message=message),indent=2))
    raise RuntimeError('Android shell receiver rejected: '+message)
while pos<len(data):
    assert len(data)-pos>=40
    magic,ver,kind,codec,gen,seq,w,h,pts,n,res=struct.unpack_from('<4sHHIIIHHQII',data,pos)
    assert magic==b'UVF1' and ver==1 and kind==2 and codec==1 and 0<n<=4194304 and pos+40+n<=len(data)
    packets.append(dict(sequence=seq,width=w,height=h,bytes=n,sha256=hashlib.sha256(data[pos+40:pos+40+n]).hexdigest()))
    pos+=40+n
report=dict(exit=code,frames=len(packets),bytes=len(data),cid=cid,packets=packets)
(out/'report.json').write_text(json.dumps(report,indent=2))
assert code==0 and len(packets)==30,'No transport PASS'
print('TRANSPORT_REPLAY_PASS (not live capture/presentation)',flush=True)
