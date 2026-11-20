"""Cold scope/identity check only; no mutations."""
import importlib.util
import json
import subprocess
from pathlib import Path
base=Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
state=json.loads((base/'activation.json').read_text())
assert state['status']=='TEST_ACTIVE_ORIGINAL_PROTECTED'
assert state['test']['serial_hex']=='5896e81d6680a54a'
adb=str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe')
sector=subprocess.check_output([adb,'exec-out','run-as com.example.uavfwinpetest dd if=files/windows-diagnostic.img bs=512 skip=17858560 count=1 2>/dev/null'])
assert len(sector)==512 and sector[72:80].hex()==state['test']['serial_hex']
spec=importlib.util.spec_from_file_location('iddgop',Path(__file__).with_name('isolated-device-bcd.py'))
g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
fat=g.DeviceFat(adb,base,g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA)
assert g.sha(fat.file('/EFI/BOOT/BOOTAA64.EFI')[0])==g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA
print('EXACT_DISPOSABLE_ROOT_NATIVE_GOP_READ_ONLY_PASS')
