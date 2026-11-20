"""Read-only, exact-candidate plan: separate UAVF8.DLL on isolated FAT media.

Never applies writes. Original NTFS codec, EFI and other directory entries are
untouched. Application requires a separately tested typed rollback transaction.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
import zlib
from pathlib import Path

SIZE = 843264
SHA = '533f9c108fd71828e7a209a10f0202fe9d8312b0502bd8035c9f8167d7cd5e04'
X264_SHA = '0463f4b9d081cd6f921837fbe27e9d03d29cd848981a9c3654113621ef55d129'
NAME = b'UAVF8   DLL'
GOP_RESERVED = 994747


def digest(data):
    return hashlib.sha256(data).hexdigest()


def select_free(table, start, count, maximum):
    assert len(table) % 4 == 0 and count == 103
    result = []
    for index in range(len(table) // 4):
        number = start + index
        if 2 <= number <= maximum and number != GOP_RESERVED:
            if struct.unpack_from('<I', table, index * 4)[0] == 0:
                result.append(number)
                if len(result) == count:
                    return result
    raise ValueError('Insufficient verified free clusters in bounded window')


def new_entry(number):
    entry = bytearray(32)
    entry[:11] = NAME
    entry[11] = 0x20
    struct.pack_into('<H', entry, 20, number >> 16)
    struct.pack_into('<H', entry, 26, number & 65535)
    struct.pack_into('<I', entry, 28, SIZE)
    # Fixed valid DOS date, not a guess about Windows' current calendar.
    struct.pack_into('<H', entry, 24, ((2026 - 1980) << 9) | (10 << 5) | 12)
    return bytes(entry)


def main():
    global SHA
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--candidate', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--codec', choices=('openh264', 'x264'), default='openh264')
    parser.add_argument('--native-gop', action='store_true', help='Read-only plan under exact proven native1200p EFI')
    args = parser.parse_args()
    if args.codec == 'x264':
        SHA = X264_SHA
    assert not args.output.exists(), 'Preserve earlier evidence'
    candidate = args.candidate.read_bytes()
    assert len(candidate) == SIZE and digest(candidate) == SHA
    assert candidate[:2] == b'MZ'
    pe = struct.unpack_from('<I', candidate, 60)[0]
    assert candidate[pe:pe + 4] == b'PE\0\0'
    assert struct.unpack_from('<H', candidate, pe + 4)[0] == 0xaa64
    assert struct.unpack_from('<H', candidate, pe + 22)[0] & 0x2000
    spec = importlib.util.spec_from_file_location('codec_fat_guard',
        Path(__file__).with_name('isolated-device-bcd.py'))
    guard = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(guard)
    fat = guard.DeviceFat(args.adb, args.output.parent,
        expected_efi_sha=guard.RAM_GOP_HANDOFF_AUDIT_EFI_SHA if args.native_gop else guard.EFI_SHA)
    boot = fat.read(guard.PART, 512)
    assert fat.cluster_bytes == 8192
    assert struct.unpack_from('<H', boot, 40)[0] == 0, 'Mirrored FAT required'
    header = fat.read(512, 512)
    assert struct.unpack_from('<Q', header, 72)[0] == 2
    assert struct.unpack_from('<II', header, 80) == (128, 128)
    partitions = fat.read(1024, 16384)
    assert zlib.crc32(partitions) == struct.unpack_from('<I', header, 88)[0]
    efi, efi_chain = fat.file('/EFI/BOOT/BOOTAA64.EFI')
    if args.native_gop:assert len(efi)==10752 and efi_chain==[994732,GOP_RESERVED]
    else:assert len(efi)==8192 and efi_chain==[994732]
    root = list(fat.entries(fat.root))
    assert not any('UAVF8.DLL' in (e[0].upper(), e[1].upper()) for e in root)
    slot = None
    for cluster in fat.chain(fat.root):
        directory = fat.read(fat.offset(cluster), 8192)
        for offset in range(0, 8192 - 32, 32):
            if directory[offset] == 0:
                assert directory[offset + 32] == 0, 'Preserve end marker'
                slot = fat.offset(cluster) + offset
                break
        if slot is not None:
            break
    assert slot is not None, 'No existing safe root slot; never grow directory'
    fat_bytes = struct.unpack_from('<I', boot, 36)[0] * 512
    start = 994733
    table = fat.read(fat.fat + start * 4, 4096 * 4)
    assert table == fat.read(fat.fat + fat_bytes + start * 4, len(table))
    maximum = min(fat_bytes // 4 - 1,
        1 + (guard.END - fat.data) // 8192)
    clusters = select_free(table, start, 103, maximum)
    blocks = {}

    def replace(offset, value, label):
        assert offset % 512 == 0 and len(value) % 512 == 0
        assert guard.PART <= offset < offset + len(value) <= guard.END
        old = fat.read(offset, len(value))
        assert offset not in blocks
        blocks[offset] = (old, bytes(value), label)

    sectors = {}
    for table_offset in (fat.fat, fat.fat + fat_bytes):
        for index, number in enumerate(clusters):
            sector = table_offset + (number * 4 // 512) * 512
            if sector not in sectors:
                sectors[sector] = bytearray(fat.read(sector, 512))
            within = number * 4 % 512
            assert struct.unpack_from('<I', sectors[sector], within)[0] == 0
            next_cluster = clusters[index + 1] if index + 1 < len(clusters) else 0x0fffffff
            struct.pack_into('<I', sectors[sector], within, next_cluster)
    for offset, data in sorted(sectors.items()):
        replace(offset, data, 'fat')
    directory_sector = slot // 512 * 512
    directory = bytearray(fat.read(directory_sector, 512))
    directory[slot % 512:slot % 512 + 32] = new_entry(clusters[0])
    replace(directory_sector, directory, 'directory')
    # FSInfo is advisory, but never leave a known free count overstated.
    reserved = struct.unpack_from('<H', boot, 14)[0]
    info = struct.unpack_from('<H', boot, 48)[0]
    backup = struct.unpack_from('<H', boot, 50)[0]
    assert 0 < info < reserved and 0 < backup + info < reserved
    for sector in (info, backup + info):
        offset = guard.PART + sector * 512
        data = bytearray(fat.read(offset, 512))
        assert data[:4] == b'RRaA' and data[484:488] == b'rrAa'
        assert data[508:512] == b'\0\0\x55\xaa'
        struct.pack_into('<II', data, 488, 0xffffffff, 0xffffffff)
        replace(offset, data, 'fsinfo-unknown-hints')
    for index, number in enumerate(clusters):
        offset = fat.offset(number)
        old = fat.read(offset, 8192)
        payload = candidate[index * 8192:(index + 1) * 8192]
        blocks[offset] = (old, payload + old[len(payload):], 'data')
    assert len(blocks) <= 115
    fat.cold()
    assert fat.file('/EFI/BOOT/BOOTAA64.EFI') == (efi, efi_chain)
    assert list(fat.entries(fat.root)) == root
    # Race detector: all retained preimages must still be exact before saving.
    for offset, (old, _, _) in blocks.items():
        assert fat.read(offset, len(old)) == old
    fat.cold()
    args.output.mkdir(parents=True)
    manifest = []
    for offset, (old, new, label) in sorted(blocks.items()):
        (args.output / f'{offset}-old.bin').write_bytes(old)
        (args.output / f'{offset}-new.bin').write_bytes(new)
        manifest.append(dict(offset=offset, length=len(old), kind=label,
            old_sha256=digest(old), new_sha256=digest(new)))
    report = dict(result='READ_ONLY_CODEC_STAGING_PLAN_PASS', device_writes=0,
        candidate_sha256=SHA, candidate_bytes=SIZE, name='UAVF8.DLL',
        directory_entry=slot, clusters=clusters, excluded_gop_cluster=GOP_RESERVED,
        gpt_header_sha256=digest(header), gpt_entries_sha256=digest(partitions),
        boot_sector_sha256=digest(boot), efi_sha256=digest(efi), blocks=manifest,
        next='Typed apply/rollback negative tests and explicit guest loader required; do not apply this manifest blindly')
    (args.output / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps({k: v for k, v in report.items() if k not in ('blocks', 'clusters')}))


if __name__ == '__main__':
    main()
