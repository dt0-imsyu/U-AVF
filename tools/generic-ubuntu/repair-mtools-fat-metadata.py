"""Repair only FAT32 metadata damaged by mtools on a disposable disk copy.

The unusual platform ESP has zero CHS geometry.  mtools requires
MTOOLS_SKIP_CHECK=1 and overwrites FAT entry 0 while leaving stale FSInfo free
counts.  This script validates every affected field before writing anything.
"""

import argparse
import hashlib
import struct
from pathlib import Path

PARTITION = 1_048_576
SECTOR = 512
P30_SHA = "AAEBB2FFC5BB375E43502D62CC15D5927E44C94D0A3AF7819E993F0849C53A27"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--baseline-sha", default=P30_SHA)
    parser.add_argument("--initrd-sha", required=True)
    parser.add_argument("--expected-stale-free-count", type=int,
                        help="exact mtools-written FSInfo count to replace when audited")
    args = parser.parse_args()
    if args.baseline.resolve() == args.candidate.resolve():
        raise SystemExit("BASELINE_AND_CANDIDATE_IDENTICAL")
    if sha256(args.baseline) != args.baseline_sha.upper():
        raise SystemExit("BASELINE_SHA_MISMATCH")
    if args.candidate.stat().st_size != args.baseline.stat().st_size:
        raise SystemExit("CANDIDATE_SIZE_MISMATCH")

    with args.baseline.open("rb") as base, args.candidate.open("r+b") as image:
        image.seek(PARTITION)
        bpb = image.read(SECTOR)
        if bpb[82:90] != b"FAT32   ":
            raise SystemExit("NOT_FAT32")
        bytes_per_sector = struct.unpack_from("<H", bpb, 11)[0]
        reserved = struct.unpack_from("<H", bpb, 14)[0]
        fats = bpb[16]
        sectors_per_fat = struct.unpack_from("<I", bpb, 36)[0]
        fsinfo_sector = struct.unpack_from("<H", bpb, 48)[0]
        backup_sector = struct.unpack_from("<H", bpb, 50)[0]
        sectors_total = struct.unpack_from("<I", bpb, 32)[0]
        sectors_per_cluster = bpb[13]
        if (bytes_per_sector, reserved, fats, sectors_per_fat, fsinfo_sector,
                backup_sector, sectors_total, sectors_per_cluster) != (
                    512, 32, 2, 2001, 1, 6, 258048, 1):
            raise SystemExit("UNEXPECTED_FAT_GEOMETRY")
        fat_start = PARTITION + reserved * SECTOR
        fat_size = sectors_per_fat * SECTOR
        image.seek(fat_start)
        fat1 = image.read(fat_size)
        fat2 = image.read(fat_size)
        if fat1 != fat2:
            raise SystemExit("FAT1_FAT2_DIFFER")
        base.seek(fat_start)
        original = base.read(4)
        if original != b"\xf8\xff\xff\x0f" or fat1[:4] not in (b"\x00" * 4, original):
            raise SystemExit("UNEXPECTED_FAT0")
        data_clusters = (sectors_total - reserved - fats * sectors_per_fat) // sectors_per_cluster
        entries = struct.unpack_from("<" + "I" * data_clusters, fat1, 8)
        free_clusters = sum((entry & 0x0fffffff) == 0 for entry in entries)
        if not 54000 <= free_clusters <= 56000:
            raise SystemExit(f"UNEXPECTED_FREE_CLUSTER_COUNT={free_clusters}")
        planned = ([(fat_start, original), (fat_start + fat_size, original)]
                   if fat1[:4] != original else [])
        for sector in (fsinfo_sector, backup_sector + fsinfo_sector):
            location = PARTITION + sector * SECTOR
            base.seek(location + 488)
            baseline_count = struct.unpack("<I", base.read(4))[0]
            image.seek(location)
            info = image.read(SECTOR)
            if (info[:4] != b"RRaA" or info[484:488] != b"rrAa"
                    or info[508:512] != b"\x00\x00\x55\xaa"):
                raise SystemExit(f"FSINFO_SIGNATURE_INVALID={sector}")
            old_count = struct.unpack_from("<I", info, 488)[0]
            allowed_counts = {free_clusters, baseline_count}
            if args.expected_stale_free_count is not None:
                allowed_counts.add(args.expected_stale_free_count)
            if old_count not in allowed_counts:
                raise SystemExit(f"FSINFO_UNEXPECTED_OLD_COUNT={sector}:{old_count}")
            if old_count != free_clusters:
                planned.append((location + 488, struct.pack("<I", free_clusters)))
        for location, value in planned:
            image.seek(location)
            image.write(value)
        image.flush()
        for location, value in planned:
            image.seek(location)
            if image.read(len(value)) != value:
                raise SystemExit(f"WRITE_VERIFY_FAILED={location}")
    print("RESULT=PASS")
    print(f"PATCHED_METADATA_FIELDS={len(planned)}")
    print(f"FREE_CLUSTERS={free_clusters}")
    print("FAT1_FAT2_MATCH=PASS")
    print(f"CANDIDATE_SHA256={sha256(args.candidate)}")
    print(f"INITRD_EXPECTED_SHA256={args.initrd_sha.upper()}")


if __name__ == "__main__":
    main()
