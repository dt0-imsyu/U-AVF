"""Guarded mechanical generation of an MS-PL upstream sample derivative.
No device operations. Keep unmodified upstream and license beside generated code.
"""
import argparse
import hashlib
from pathlib import Path
import re

p=argparse.ArgumentParser()
p.add_argument('directory',type=Path)
a=p.parse_args()
original=a.directory/'Driver.cpp'
assert hashlib.sha256(original.read_bytes()).hexdigest()=='b6e9320291372a3f041e0bea716e15e7bc3d8df6359169e822d3eb3a281bdf3f'
source=original.read_text(encoding='utf-8')
def replace_once(old,new):
    global source
    assert source.count(old)==1, 'Pinned upstream anchor changed'
    source=source.replace(old,new)
replace_once('static constexpr DWORD IDD_SAMPLE_MONITOR_COUNT = 3;',
             'static constexpr DWORD IDD_SAMPLE_MONITOR_COUNT = 1;')
start=source.index('void SwapChainProcessor::RunCore()')
end=source.index('\n#pragma endregion',start)
source=source[:start]+'#include "idd-system-proof-worker.inc"\n'+source[end:]
mode=re.compile(r'(s_SampleDefaultModes\[\]\s*=\s*\{).*?(\n\};)',re.S)
source,count=mode.subn(r'\1\n    { 1920, 1200, 60 },\2',source)
assert count==1
replace_once('if (ConnectorIndex >= ARRAYSIZE(s_SampleMonitors))','if (true) // Proof monitor: no borrowed physical EDID')
replace_once('CoCreateGuid(&MonitorInfo.MonitorContainerId);',
    'MonitorInfo.MonitorContainerId = {0x7e45d4e9,0xcba1,0x4e50,{0x9d,0xee,0x9c,0x23,0x45,0xb3,0x26,0x71}};')
source,count=re.subn(r'    TargetModes\.push_back\(CreateIddCxTargetMode\(3840, 2160, 60\)\);.*?    TargetModes\.push_back\(CreateIddCxTargetMode\(1024,  768, 60\)\);',
    '    TargetModes.push_back(CreateIddCxTargetMode(1920, 1200, 60));',source,flags=re.S)
assert count==1
replace_once('L"IddSample Device"','L"U-AVF isolated system-buffer probe"')
replace_once('L"Microsoft"','L"U-AVF"')
replace_once('L"IddSample Model"','L"ARM64 source-only prototype"')
target=a.directory/'DriverProof.cpp'
assert not target.exists(), 'Preserve generated evidence'
target.write_text(source,encoding='utf-8')
print('GENERATED_SINGLE_1200P60_SYSTEM_BUFFER_PROOF; NOT_INSTALLED_OR_FPS_PASS=1')
