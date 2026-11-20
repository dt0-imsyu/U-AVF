"""Bounded read-only BCD audit in the isolated live Windows VM.
Two identical clean-hive reads required. No writes, CPU options or user data.
"""
import argparse,hashlib,importlib.util,io,json,subprocess
from pathlib import Path
from Registry import Registry
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
assert not a.output.exists();a.output.mkdir(parents=True)
def owner():
    value=subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
    assert value.count('requesterUid:')==1 and 'requesterUid: 10042' in value
    return value
initial=owner()
spec=importlib.util.spec_from_file_location('f',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
class ReadOnly(g.DeviceFat):
    def cold(self):assert owner()==initial,'Owner changed during read-only audit'
    def write_bcd(self,*args):raise AssertionError('Read only audit')
f=ReadOnly(a.adb,a.output,expected_efi_sha=g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA)
data,_=f.file('/EFI/Microsoft/Boot/BCD');again,_=f.file('/EFI/Microsoft/Boot/BCD')
assert data==again and data[:4]==b'regf' and data[4:8]==data[8:12]
(a.output/'BCD-readonly').write_bytes(data)
r=Registry.Registry(io.BytesIO(data));default=r.open('Objects\\{9dea862c-5cdd-4e70-acc1-f32b344d4795}\\Elements\\23000003').value('Element').value()
found=[]
for obj in r.open('Objects').subkeys():
    try:v=obj.subkey('Elements').subkey('16000049').value('Element').raw_data()
    except Registry.RegistryKeyNotFoundException:continue
    assert len(v)==1;found.append({'object':obj.name(),'testsigning':bool(v[0])})
assert owner()==initial
report={'scope':'BCD test signing only, read only','default':default,'entries':found,'sha256':hashlib.sha256(data).hexdigest(),'stable_clean_reads':True,'runtime_ci_state_verified':False}
(a.output/'report.json').write_text(json.dumps(report,indent=2));print(json.dumps(report))
