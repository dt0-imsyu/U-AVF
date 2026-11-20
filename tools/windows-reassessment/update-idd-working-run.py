"""Cold finite update of our existing ESP run.ps1; no allocation/EFI changes."""
import argparse,hashlib,importlib.util,json,struct,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--script',choices=('run','test','device','driver','catalog','reader','motion'),default='run');p.add_argument('--binary',type=Path);p.add_argument('--real-desktop-motion',action='store_true');p.add_argument('--reconnect-payload',action='store_true');p.add_argument('--reconnect-reader',action='store_true');p.add_argument('--active-desktop-proof',action='store_true');p.add_argument('--monitor-readiness',action='store_true');p.add_argument('--long-workload',action='store_true');p.add_argument('--continuous-workload',action='store_true');a=p.parse_args()
assert not a.continuous_workload or (a.script=='run' and not any((a.long_workload,a.monitor_readiness,a.active_desktop_proof)))
assert not a.long_workload or (not a.monitor_readiness and a.script in ('run','test','device','motion'))
assert not a.monitor_readiness or a.script in ('run','motion','test')
assert not a.active_desktop_proof or a.script=='run'
assert not a.real_desktop_motion or a.script=='motion'
assert not a.reconnect_payload or a.script in ('driver','catalog')
assert not a.reconnect_reader or a.script=='reader'
assert not a.output.exists();a.output.mkdir(parents=True)
spec=importlib.util.spec_from_file_location('g',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
f=g.DeviceFat(a.adb,a.output,expected_efi_sha=g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA)
boot=subprocess.check_output([a.adb,'exec-out',f'run-as {g.PACKAGE} dd if={g.DISK} bs=512 skip=17858560 count=1 2>/dev/null'])
assert boot[72:80].hex()=='b769f63e81f63ec6'
name={'run':'run.ps1','test':'idd-working-test.ps1','device':'idd-probe-device.exe','driver':'IddSampleDriver.dll','catalog':'IddSampleDriver.cat','reader':'idd-etw-read.exe','motion':'idd-motion-source.exe'}[a.script]
path='/IDDWORK/'+('package/' if a.script in ('driver','catalog') else '')+name
old,chain=f.file(path);assert 1<=len(chain)<=4 and len(old)<=len(chain)*f.cluster_bytes
if a.active_desktop_proof:assert hashlib.sha256(old).hexdigest()=='dc279fcb036db5f52227b846cbfc82f1bfafbb3e8b7bdb7014a69144eba598e7'
if a.monitor_readiness:
    pins={'run':'d3149ce1b54deefdd53ee20bc934f444acbc2c75725e49f09e4d0ed7d263c1e0','test':'50ef014c20689ae5a5f3668c1982c622e06ee79c82776eb48d1be83d67e6f4f9','motion':'86c45c9c87416d374ab2b40aa309f66734cb0dc24ba00f2e4dccbb479c96cc8f'}
    assert hashlib.sha256(old).hexdigest()==pins[a.script]
if a.long_workload:
    long_pins={'run':'815ea94ba20d5159f0cdbda0f3b3b284d74709c078db5a94ab99f79d91b25223','test':'c9cedd3a4f7f1ca166b73334f52cc660ba690c46031cca43f7a7a2b02d0ec43c','device':'edee1dfff5acaf0dc61ab2085dfad1c8c2bb10546a21886cf07d04c1f7a59f64','motion':'f4d6edd45631c6166002520fdbbc1ca79626a40cb6afba9a711a831c407aff51'}
    assert hashlib.sha256(old).hexdigest()==long_pins[a.script]
if a.continuous_workload:assert hashlib.sha256(old).hexdigest()=='9a55748378818a55130a30edbde9588c8c3fbc6e89b1e90557872199b10bbfbb'
source='idd-working-stage-run.ps1' if a.script=='run' else name
if a.active_desktop_proof:source='idd-active-desktop-stage-run.ps1'
if a.monitor_readiness and a.script=='run':source='idd-monitor-readiness-stage-run.ps1'
if a.long_workload and a.script=='run':source='idd-monitor-readiness-stage-run.ps1'
if a.continuous_workload:source='idd-monitor-readiness-stage-run.ps1'
if a.script in ('device','driver','catalog','reader','motion'):
    preimages={'device':'ad4ad15bd04a692ca5e15a69f0945a5344a29a5808633c1de740bd711863cd06','driver':'75efa21c040d1f110ebfc7247735025b77fb060ee9a5df724ec4cf00944c6afb','catalog':'490629fcfb818745103758e4e1811fd29a23a3eae7c3a7b93e48581eed879a39','reader':'4660fef9faee0fc2bfb2ffb8eed9f2a7180c6b01f361272330b0d3a226ac30e7'}
    preimages['motion']='69250d4ccc778e3c5688d20679e85820da67d48073508db2ae2c19ea4e0bbd96'
    if a.real_desktop_motion:preimages['motion']='7f80e85cbabb15bc74b9251c107a216625a5594e93cb0dea3868c377cb5161dd'
    if a.monitor_readiness:preimages[a.script]=pins[a.script]
    preimages.update(driver='c2d90a00838e512f1ca089245d3a02874acab7792819fdfd4904ab91dabee767',catalog='2306bc28bc3db27bbed28d476a5993c5e45fde17e17ca08ce2b459b5d0597ff1',reader='0c976fab39879f3f9d855355ededeef313305809271c20b44f43876ca39e474e')
    if a.reconnect_payload:
        preimages.update(driver='b15d9f4c840f3eb8569daa6181136bab37bb3f270933822e83f9920b4f0aff5a',catalog='4b649830bf0b0a44cb55547daeaa38948829142fecde49bda778e884a48e7707')
    if a.reconnect_reader:preimages['reader']='3775f62b007bb61af8cf70f1d6d9ea981a803870aad342bdd802e3a2ab7b721c'
    if a.long_workload:preimages[a.script]=long_pins[a.script]
    assert a.binary and hashlib.sha256(old).hexdigest()==preimages[a.script]
    new=a.binary.read_bytes()
    if a.real_desktop_motion:assert hashlib.sha256(new).hexdigest()==('7da58079674c0bd417ab54159506d3185892b4590931b6d4465981bf3608c554' if a.long_workload else ('f4d6edd45631c6166002520fdbbc1ca79626a40cb6afba9a711a831c407aff51' if a.monitor_readiness else '86c45c9c87416d374ab2b40aa309f66734cb0dc24ba00f2e4dccbb479c96cc8f'))
    if a.long_workload and a.script=='device':assert hashlib.sha256(new).hexdigest()=='93cfd28264deea6ef22203d4cd5d8fe85b161ea32e807e1260c340fb276fde42'
    if a.reconnect_payload:
        pins={'driver':'42f1d3f3dbc86afe079e3aa6ebc0672dcf26b9a976dda6574fc612d5138ee29c','catalog':'a484f851a6498eb90bf941d0a1a48b959f30d840c8f982f8889747fe2d223f1f'}
        assert hashlib.sha256(new).hexdigest()==pins[a.script]
    if a.reconnect_reader:assert hashlib.sha256(new).hexdigest()=='7dfcc6b28776654a70931b08fafffdc6b9de36b91e67442ebb9f787e3fd352fa'
    if a.script!='catalog':
        assert new[:2]==b'MZ'
        pe=struct.unpack_from('<I',new,60)[0];assert struct.unpack_from('<H',new,pe+4)[0]==0xaa64
else:new=Path(__file__).with_name(source).read_bytes().replace(b'\r\n',b'\n').replace(b'\n',b'\r\n')
assert len(new)<=len(chain)*f.cluster_bytes
folder=[e for e in f.entries(f.root) if e[0].upper()=='IDDWORK'];assert len(folder)==1 and folder[0][4]&16
if a.script in ('driver','catalog'):
    folder=[e for e in f.entries(folder[0][2]) if e[0].upper()=='PACKAGE'];assert len(folder)==1 and folder[0][4]&16
entry=[e for e in f.entries(folder[0][2]) if e[0].upper()==name.upper()];assert len(entry)==1
short=entry[0][1].split('.');key=short[0].ljust(8).encode()+short[1].ljust(3).encode()
offsets=[]
for c in f.chain(folder[0][2]):
    data=f.read(f.offset(c),f.cluster_bytes)
    for i in range(0,len(data),32):
        if data[i:i+11]==key:offsets.append(f.offset(c)+i)
assert len(offsets)==1
block=offsets[0]//512*512;before_dir=f.read(block,512);after_dir=bytearray(before_dir)
assert struct.unpack_from('<I',before_dir,offsets[0]-block+28)[0]==len(old)
struct.pack_into('<I',after_dir,offsets[0]-block+28,len(new))
changes=[]
for i,c in enumerate(chain):
    address=f.offset(c);before_data=f.read(address,f.cluster_bytes);piece=new[i*f.cluster_bytes:(i+1)*f.cluster_bytes]
    changes.append((address,before_data,piece+before_data[len(piece):]))
changes.append((block,before_dir,bytes(after_dir)))
for i,(address,before,after) in enumerate(changes):
    (a.output/f'{i}-before.bin').write_bytes(before);(a.output/f'{i}-after.bin').write_bytes(after)
report={'path':path,'bytes':len(new),'old_sha':hashlib.sha256(old).hexdigest(),'new_sha':hashlib.sha256(new).hexdigest(),'ranges':[{'offset':o,'bytes':len(b)} for o,b,_ in changes],'applied':False,'driver_loaded':False}
(a.output/'plan.json').write_text(json.dumps(report,indent=2))
def write(address,data):
    f.cold();local=a.output/'transfer.bin';local.write_bytes(data);remote='/data/local/tmp/uavf-idd-run-update.bin'
    subprocess.run([a.adb,'push',str(local),remote],check=True,capture_output=True);subprocess.run([a.adb,'shell','chmod','644',remote],check=True,capture_output=True)
    subprocess.run([a.adb,'shell','run-as',g.PACKAGE,'dd','if='+remote,'of='+g.DISK,'bs=512','seek='+str(address//512),'count='+str(len(data)//512),'conv=notrunc'],check=True,capture_output=True)
    assert f.read(address,len(data))==data
touched=[]
try:
    for address,before,after in changes:
        assert f.read(address,len(before))==before;touched.append((address,before));write(address,after)
    assert f.file(path)[0]==new;report['applied']=True
except Exception:
    for address,before in reversed(touched):write(address,before)
    raise
finally:subprocess.run([a.adb,'shell','rm','-f','/data/local/tmp/uavf-idd-run-update.bin'],check=True,capture_output=True)
(a.output/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
