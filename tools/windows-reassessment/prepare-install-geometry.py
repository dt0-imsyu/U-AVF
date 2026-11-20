"""Audit the frozen Windows media; emit a small GPT growth plan, never mutate it."""
import argparse
import hashlib
import json
import struct
import uuid
import zlib
from pathlib import Path

SECTOR = 512
EXPECTED_SIZE = 9126805504
EXPECTED_SHA = 'A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A'

def header(raw):
    assert raw[:8] == b'EFI PART', 'GPT signature'
    size = struct.unpack_from('<I', raw, 12)[0]
    assert 92 <= size <= SECTOR
    check = bytearray(raw[:size])
    stored = struct.unpack_from('<I', check, 16)[0]
    struct.pack_into('<I', check, 16, 0)
    assert zlib.crc32(check) == stored, 'GPT header CRC'
    return struct.unpack_from('<QQQQ', raw, 24), struct.unpack_from('<QIII', raw, 72)

def updated(raw, current, alternate, last_usable, entries_lba):
    result = bytearray(raw)
    struct.pack_into('<QQ', result, 24, current, alternate)
    struct.pack_into('<Q', result, 48, last_usable)
    struct.pack_into('<Q', result, 72, entries_lba)
    struct.pack_into('<I', result, 16, 0)
    size = struct.unpack_from('<I', result, 12)[0]
    struct.pack_into('<I', result, 16, zlib.crc32(result[:size]))
    header(result)
    return bytes(result)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    assert not args.output.exists(), 'Preserve previous plan'
    assert args.source.stat().st_size == EXPECTED_SIZE
    with args.source.open('rb') as disk:
        assert hashlib.file_digest(disk, 'sha256').hexdigest().upper() == EXPECTED_SHA
        disk.seek(0)
        mbr, primary = disk.read(SECTOR), disk.read(SECTOR)
        (current, alternate, first, last), (entry_lba, count, stride, crc) = header(primary)
        old_sectors = EXPECTED_SIZE // SECTOR
        assert (current, alternate, first, last) == (1, old_sectors-1, 34, old_sectors-34)
        assert (entry_lba, count, stride) == (2, 128, 128)
        disk.seek(entry_lba * SECTOR)
        entries = disk.read(count * stride)
        assert zlib.crc32(entries) == crc
        assert uuid.UUID(bytes_le=entries[:16]) == uuid.UUID('c12a7328-f81f-11d2-ba4b-00a0c93ec93b')
        assert struct.unpack_from('<QQ', entries, 32) == (2048, last)
        assert not any(entries[stride:]), 'Only the known source ESP may exist'
        disk.seek(alternate * SECTOR)
        backup = disk.read(SECTOR)
        backup_missing = not any(backup)
        if backup_missing:
            # Some inherited media omitted the backup GPT. Reconstruct only from
            # the SHA-verified, CRC-validated primary; never accept corrupt bytes.
            backup = primary
        else:
            (bc, ba, bf, bl), (be, bn, bs, bcrc) = header(backup)
            assert (bc, ba, bf, bl, bn, bs, bcrc) == (alternate, current, first, last, count, stride, crc)
            disk.seek(be * SECTOR)
            assert disk.read(len(entries)) == entries
    assert mbr[510:] == b'\x55\xaa' and mbr[450] == 0xee
    assert not any(mbr[462:510]), 'No hybrid MBR'
    new_size = EXPECTED_SIZE + 64 * 1024**3
    new_last = new_size // SECTOR - 1
    mbr_new = bytearray(mbr)
    struct.pack_into('<I', mbr_new, 458, min(new_last, 0xffffffff))
    patches = [
        (0, bytes(mbr_new)),
        (SECTOR, updated(primary, 1, new_last, new_last-33, 2)),
        ((new_last-32)*SECTOR, entries),
        (new_last*SECTOR, updated(backup, new_last, 1, new_last-33, new_last-32)),
    ]
    args.output.mkdir(parents=True)
    manifest = dict(status='AUDITED_PLAN_ONLY_NOT_APPLIED', source=str(args.source.resolve()),
                    source_sha256=EXPECTED_SHA, old_bytes=EXPECTED_SIZE, new_bytes=new_size,
                    source_backup_gpt_missing=backup_missing,
                    preserved_source_partition_lba=[2048, last],
                    install_target_first_aligned_lba=old_sectors,
                    install_target_last_usable_lba=new_last-33,
                    reserve_bytes=50*1024**3,
                    safety='Sparse growth only; no format; preserve source ESP. Recheck physical free space before installation. Never cold-restore after installation starts.', patches=[])
    for index, (offset, payload) in enumerate(patches):
        name = f'patch-{index}.bin'
        (args.output/name).write_bytes(payload)
        manifest['patches'].append(dict(offset=offset, file=name, bytes=len(payload), sha256=hashlib.sha256(payload).hexdigest()))
    (args.output/'plan.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps(manifest, indent=2))

if __name__ == '__main__':
    main()
