"""Cold read-only BasicDisplay identity/code for the isolated installed guest.

No full disk copy, mount, registry access, device write or driver installation.
Reject unreconstructed WOF streams rather than disassembling compressed data.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
from pathlib import Path


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    assert not a.output.exists(), 'Preserve previous evidence'
    reader = load('basicdisplay_reader', 'extract-installed-setup-evidence.py')
    guard_module = load('basicdisplay_guard', 'isolated-device-bcd.py')
    guard = guard_module.DeviceFat(a.adb, a.output.parent)
    guard.cold()
    root = reader.ColdRoot(a.adb)
    boot = root.read(512)
    assert boot[3:11] == b'NTFS    ' and struct.unpack_from('<I', boot, 72)[0] == 0x3EF669B7
    root.seek(0)
    fs = reader.NTFS(root)
    folder = 'Windows\\System32\\DriverStore\\FileRepository'
    names = fs.mft.get(folder).listdir(ignore_dos=True)
    matching = sorted(n for n in names if n.lower().startswith('basicdisplay.inf_arm64_'))
    assert len(matching) == 1, 'Require unique current BasicDisplay package, not guess among versions'
    source = folder + '\\' + matching[0] + '\\BasicDisplay.sys'
    record = fs.mft.get(source)
    stream = record.open()
    assert 0 < stream.size <= 2 * 1024 * 1024
    data = stream.read(stream.size)
    assert len(data) == stream.size, 'Short stream'
    if data[:2] != b'MZ':
        # Preserve bounded compression metadata, not fake executable identity.
        reparse = record.open(attr_type=0xC0)
        assert reparse.size <= 16384
        reparse_data = reparse.read(reparse.size)
        compressed = record.open('WofCompressedData')
        assert 0 < compressed.size <= 2 * 1024 * 1024
        compressed_data = compressed.read(compressed.size)
        guard.cold()
        a.output.mkdir(parents=True)
        (a.output / 'reparse.bin').write_bytes(reparse_data)
        (a.output / 'WofCompressedData.bin').write_bytes(compressed_data)
        report = dict(source=source, logical_bytes=stream.size,
                      reparse_hex=reparse_data.hex(), compressed_bytes=len(compressed_data),
                      compressed_sha256=hashlib.sha256(compressed_data).hexdigest(),
                      read_only=True, executable_reconstructed=False,
                      device_bytes_read=root.read_bytes, owner_cache_proven=False)
        (a.output / 'manifest.json').write_text(json.dumps(report, indent=2))
        print(json.dumps(report))
        return
    pe = struct.unpack_from('<I', data, 60)[0]
    assert data[pe:pe + 4] == b'PE\0\0' and struct.unpack_from('<H', data, pe + 4)[0] == 0xAA64
    assert root.read_bytes <= 32 * 1024 * 1024, 'Bounded driver-only read'
    guard.cold()
    a.output.mkdir(parents=True)
    (a.output / 'BasicDisplay.sys').write_bytes(data)
    report = dict(source=source, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                  read_only=True, device_bytes_read=root.read_bytes,
                  owner_cache_proven=False, scope='One exact ARM64 driver; static analysis only')
    (a.output / 'manifest.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == '__main__':
    main()
