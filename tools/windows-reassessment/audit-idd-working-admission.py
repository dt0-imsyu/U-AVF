"""Cold read-only guest dependency and current own startup evidence; no UI input."""
import importlib.util
import json
import struct
import subprocess
from pathlib import Path
import argparse

p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
assert not a.output.exists()
def cold():
    assert 'Running VMs: []' in subprocess.check_output([a.adb,'shell','/apex/com.android.virt/bin/vm','list'],text=True)
cold()
spec=importlib.util.spec_from_file_location('idd_read',Path(__file__).with_name('extract-installed-setup-evidence.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
root=m.ColdRoot(a.adb);boot=root.read(512)
assert boot[3:11]==b'NTFS    ' and struct.unpack_from('<Q',boot,72)[0]==0xc63ef6813ef669b7
root.seek(0);fs=m.NTFS(root);items={}
a.output.mkdir(parents=True)
for name in ('vcruntime140.dll','msvcp140.dll','drivers/UMDF/IddCx.dll','uavf-installed-display.cmd'):
    try:
        stream=fs.mft.get('Windows\\System32\\'+name.replace('/','\\')).open()
        items[name]={'exists':True,'bytes':stream.size}
        if name.endswith('.cmd'):
            assert stream.size<65536
            (a.output/name).write_bytes(stream.read(stream.size))
    except FileNotFoundError:items[name]={'exists':False}
cold()
report={'scope':'Working Windows approved test; cold read only','dependencies':items,'read_bytes':root.read_bytes,'driver_loaded':False,'fps_pass':False}
(a.output/'report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
