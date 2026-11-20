"""Read-only current-device gate; historical delivery reports are not admission."""
import argparse
import importlib.util
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--prepared', type=Path, required=True)
    parser.add_argument('--observed-native-crash',action='store_true')
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location('native_codec', Path(__file__).with_name('apply-eight-thread-codec.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.select_codec('x264', True)
    guard_spec=importlib.util.spec_from_file_location('status_guard',Path(__file__).with_name('recover-eight-thread-codec.py'))
    status_guard=importlib.util.module_from_spec(guard_spec);guard_spec.loader.exec_module(status_guard)
    plan, _ = module.read_plan(args.prepared)
    fat = module.g.DeviceFat(args.adb, args.prepared.parent, expected_efi_sha=module.g.EFI_SHA)
    for offset, size, key in ((512, 512, 'gpt_header_sha256'),
                              (1024, 16384, 'gpt_entries_sha256'),
                              (module.g.PART, 512, 'boot_sector_sha256')):
        current=fat.read(offset,size)
        assert (status_guard.boot_guard_matches(current,plan[key],args.observed_native_crash) if key=='boot_sector_sha256' else module.p.digest(current)==plan[key]), 'Immutable platform/storage guard failed'
    # DeviceFat intentionally permits only the ESP range. Do not relax that
    # guard to inspect the separate, exact approved NTFS boot sector.
    boot = subprocess.check_output([args.adb, 'exec-out',
        f'run-as {module.g.PACKAGE} dd if={module.g.DISK} bs=512 skip=17858560 count=1 2>/dev/null'])
    assert len(boot) == 512
    assert boot[3:11] == b'NTFS    ' and boot[72:80].hex() == 'b769f63e81f63ec6'
    present = any('UAVF8.DLL' in (entry[0].upper(), entry[1].upper()) for entry in fat.entries(fat.root))
    if not present:
        print(json.dumps({'result': 'CURRENT_NATIVE_X264_ABSENT_NO_BOOT', 'device_writes': 0}))
        return 2
    data, chain = fat.file('/UAVF8.DLL')
    assert len(data) == module.p.SIZE and module.p.digest(data) == module.p.SHA and chain == module.CLUSTERS
    assert module.p.digest(fat.file('/EFI/BOOT/BOOTAA64.EFI')[0]) == plan['efi_sha256']
    fat.cold()
    print(json.dumps({'result': 'CURRENT_NATIVE_X264_READBACK_PASS', 'device_writes': 0,
                      'bytes': len(data), 'sha256': module.p.SHA, 'clusters': len(chain)}))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
