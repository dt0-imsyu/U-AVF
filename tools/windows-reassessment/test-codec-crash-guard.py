"""Actual cold sector identity + exhaustive single-bit guard rejection; no writes."""
import argparse,importlib.util
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);a=p.parse_args()
s=importlib.util.spec_from_file_location('recover',Path(__file__).with_name('recover-eight-thread-codec.py'))
r=importlib.util.module_from_spec(s);s.loader.exec_module(r)
m=r.load();m.select_codec('x264',True)
f=m.g.DeviceFat(a.adb,Path('E:/U-AVF/artifacts'),expected_efi_sha=m.g.EFI_SHA)
sector=f.read(m.g.PART,512)
expected='4bda9265ad59680936b16c7ac344937fa3944b3101c29102d7294d805fcde9c6'
assert r.boot_guard_matches(sector,expected,True)
assert not r.boot_guard_matches(sector,expected,False)
original=bytearray(sector);original[65]=0
assert r.boot_guard_matches(original,expected,False)
assert not r.boot_guard_matches(sector,'0'*64,True)
assert not r.boot_guard_matches(sector[:-1],expected,True)
tests=0
for i in range(512):
    for bit in range(8):
        if i==65 and bit==0:continue # This is exactly the admitted original.
        changed=bytearray(sector);changed[i]^=1<<bit
        assert not r.boot_guard_matches(changed,expected,True),(i,bit)
        tests+=1
f.cold();print(f'EXACT_CRASH_BOOT_GUARD_PASS; UNKNOWN_SINGLE_BIT_REJECTIONS={tests}; DEVICE_WRITES=0')
