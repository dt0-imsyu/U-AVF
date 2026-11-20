"""Offline ARM64 UMDF DLL structural checks. Never loads/installs the DLL.
Structural PASS is not signature/trust, guest admission or frame/FPS PASS.
"""
import argparse
import hashlib
import json
from pathlib import Path
import pefile

p=argparse.ArgumentParser()
p.add_argument('dll',type=Path)
a=p.parse_args()
data=a.dll.read_bytes()
assert 0<len(data)<=1024*1024, 'Small proof DLL only'
pe=pefile.PE(data=data)
assert pe.FILE_HEADER.Machine==0xaa64, 'Native ARM64 required'
assert pe.FILE_HEADER.Characteristics&0x2000, 'DLL required'
assert pe.OPTIONAL_HEADER.AddressOfEntryPoint, 'Real CRT entry required'
exports=[s.name.decode('ascii') for s in pe.DIRECTORY_ENTRY_EXPORT.symbols if s.name]
assert exports==['FxDriverEntryUm'], 'Official WDF binding export required'
imports={entry.dll.decode('ascii').lower():[s.name.decode('ascii') if s.name else '#'+str(s.ordinal) for s in entry.imports]
         for entry in pe.DIRECTORY_ENTRY_IMPORT}
assert 'd3d11.dll' in imports and 'dxgi.dll' in imports
assert 'vcruntime140.dll' in imports, 'Actual runtime dependency must be reported'
assert not any('winavf' in name or 'uavf' in name for name in imports), 'No invented private runtime'
flags=pe.DIRECTORY_ENTRY_LOAD_CONFIG.struct.GuardFlags
assert flags&0x100 and flags&0x400, 'CFG instrumentation/table required'
assert pe.OPTIONAL_HEADER.DllCharacteristics&0x40 and pe.OPTIONAL_HEADER.DllCharacteristics&0x100
result={'structural_pass':True,'machine':'ARM64','bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),
        'exports':exports,'imports':imports,'cfg_flags':hex(flags),
        'signature_directory_present':bool(pe.OPTIONAL_HEADER.DATA_DIRECTORY[4].Size),
        'guest_driver_admission':'UNPROVEN','fps_pass':False,'device_writes':0}
print(json.dumps(result,indent=2))
pe.close()
