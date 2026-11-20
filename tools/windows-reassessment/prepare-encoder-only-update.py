"""Read/backup one existing owned encoder; prepare fixed-slot diagnostic update.

No device writes. Caller must separately apply with full cold/readback guards.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
from pathlib import Path


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def arm64(data):
    assert len(data) >= 64 and data[:2] == b"MZ"
    pe = struct.unpack_from("<I", data, 60)[0]
    assert pe + 6 <= len(data) and data[pe:pe+4] == b"PE\0\0"
    assert struct.unpack_from("<H", data, pe+4)[0] == 0xaa64


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument('--kind',choices=('encoder','vsock'),default='encoder',help='One existing OWN helper; never signed driver/provider')
    parser.add_argument('--native-gop',action='store_true',help='Read under unchanged proven native1200p EFI')
    args = parser.parse_args()
    assert not args.output.exists(), "Preserve previous backups"
    folder = Path(__file__).parent
    reader = load("encoder_cold_reader", folder / "extract-installed-setup-evidence.py")
    guard_module = load("encoder_cold_guard", folder / "isolated-device-bcd.py")
    guard = guard_module.DeviceFat(args.adb, args.output.parent, expected_efi_sha=guard_module.RAM_GOP_HANDOFF_AUDIT_EFI_SHA if args.native_gop else guard_module.EFI_SHA)
    volume = reader.ColdRoot(args.adb)
    boot = volume.read(512)
    assert boot[3:11] == b"NTFS    " and struct.unpack_from("<I", boot, 72)[0] == 0x3ef669b7
    volume.seek(0)
    fs = reader.NTFS(volume)
    name = "Windows/System32/"+('winpe-encode.exe' if args.kind=='encoder' else 'winpe-vsock.exe')
    stream = fs.mft.get(name.replace("/", "\\")).open()
    assert type(stream).__name__ == "RunlistStream" and 0 < stream.size <= 1048576
    original = stream.read(stream.size)
    binary = args.binary.read_bytes()
    arm64(original)
    arm64(binary)
    assert len(original) % 512 == 0 and len(binary) <= len(original), "No allocation/metadata changes"
    guard.cold()
    changed = binary + bytes(len(original)-len(binary))
    args.output.mkdir(parents=True)
    backup = args.output / name
    backup.parent.mkdir(parents=True)
    backup.write_bytes(original)
    (args.output / ("winpe-encode-observation-slot.exe" if args.kind=='encoder' else 'winpe-vsock-observation-slot.exe')).write_bytes(changed)
    sha = lambda data: hashlib.sha256(data).hexdigest()
    report = dict(result="OWNED_ENCODER_BINARY_PREFLIGHT_PASS" if args.kind=='encoder' else 'OWNED_VSOCK_BINARY_PREFLIGHT_PASS',
                  unrelated_values_changed=0, existing_key_security_changed=0,
                  registry_hive_writes=0, file=name, slot_bytes=len(original),
                  scope="One existing owned "+args.kind+" helper only; no signed driver/provider, hive, platform or allocation changes")
    report['original_'+args.kind+'_sha256']=sha(original)
    report['changed_'+args.kind+'_sha256']=sha(changed)
    (args.output / "report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == "__main__":
    main()
