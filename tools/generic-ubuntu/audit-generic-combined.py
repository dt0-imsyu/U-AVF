"""Read-only exact-byte audit of the one-disk ESP + stock ISO layout."""
import argparse
import hashlib
from pathlib import Path
import struct
import subprocess
import zlib
from pyfatfs.PyFatFS import PyFatFS

SECTOR = 512
ESP_FIRST = 2048
ESP_BYTES = 126 * 1024 * 1024
ISO_FIRST = 262144
ISO_HASH = "2BE09CA883921BFF6D8E6B0BFBAFD13E32436553B7086F33BCE3A4C5BAD8BD14"


def region_stream_hash(source):
    value = hashlib.sha256()
    for block in iter(lambda: source.read(1024 * 1024), b""):
        value.update(block)
    return value.hexdigest().upper()


def region_hash(source, offset, length):
    source.seek(offset)
    value = hashlib.sha256()
    while length:
        block = source.read(min(length, 1024 * 1024))
        if not block:
            raise RuntimeError("truncated image region")
        value.update(block)
        length -= len(block)
    return value.hexdigest().upper()


def main_zstd_payload(path):
    data = path.read_bytes()
    offset = 0
    while offset + 110 <= len(data) and data[offset:offset + 6] == b"070701":
        namesize = int(data[offset + 94:offset + 102], 16)
        filesize = int(data[offset + 54:offset + 62], 16)
        record = (offset + 110 + namesize + 3) & ~3
        record = (record + filesize + 3) & ~3
        name = data[offset + 110:offset + 110 + namesize].rstrip(b"\0")
        offset = record
        if name == b"TRAILER!!!":
            offset = (offset + 511) & ~511
            break
    if data[offset:offset + 4] != b"\x28\xb5\x2f\xfd":
        raise SystemExit("initrd main Zstd stream missing")
    return data[offset:]


def header_ok(data, current_lba, alternate_lba, entries_crc):
    if data[:8] != b"EFI PART" or len(data) != SECTOR:
        return False
    crc = struct.unpack_from("<I", data, 16)[0]
    copy = bytearray(data)
    struct.pack_into("<I", copy, 16, 0)
    return (zlib.crc32(copy[:92]) == crc
            and struct.unpack_from("<Q", data, 24)[0] == current_lba
            and struct.unpack_from("<Q", data, 32)[0] == alternate_lba
            and struct.unpack_from("<I", data, 88)[0] == entries_crc)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", type=Path, required=True)
    parser.add_argument("--iso", type=Path, required=True)
    parser.add_argument("--combined", type=Path, required=True)
    parser.add_argument("--launcher", type=Path)
    parser.add_argument("--initrd", type=Path, required=True)
    parser.add_argument("--zstd", type=Path, required=True)
    args = parser.parse_args()
    iso_bytes = args.iso.stat().st_size
    combined_bytes = args.combined.stat().st_size
    if iso_bytes % SECTOR or combined_bytes % SECTOR:
        raise SystemExit("unaligned disk or ISO")
    total_lbas = combined_bytes // SECTOR
    with args.iso.open("rb") as source:
        expected_iso = region_hash(source, 0, iso_bytes)
    if expected_iso != ISO_HASH:
        raise SystemExit("stock ISO SHA mismatch")
    with args.platform.open("rb") as source:
        expected_esp = region_hash(source, ESP_FIRST * SECTOR, ESP_BYTES)
    with args.combined.open("rb") as disk:
        mbr = disk.read(SECTOR)
        primary = disk.read(SECTOR)
        entries = disk.read(128 * 128)
        entries_crc = zlib.crc32(entries)
        if mbr[510:512] != b"\x55\xaa" or mbr[450] != 0xee:
            raise SystemExit("protective MBR invalid")
        if not header_ok(primary, 1, total_lbas - 1, entries_crc):
            raise SystemExit("primary GPT invalid")
        first_esp, last_esp = struct.unpack_from("<QQ", entries, 32)
        first_iso, last_iso = struct.unpack_from("<QQ", entries, 128 + 32)
        if (first_esp, last_esp) != (ESP_FIRST, ESP_FIRST + ESP_BYTES // SECTOR - 1):
            raise SystemExit("ESP GPT span invalid")
        if (first_iso, last_iso) != (ISO_FIRST, ISO_FIRST + iso_bytes // SECTOR - 1):
            raise SystemExit("ISO GPT span invalid")
        current_esp = region_hash(disk, ESP_FIRST * SECTOR, ESP_BYTES)
        if args.launcher is None and current_esp != expected_esp:
            raise SystemExit("ESP partition differs from audited platform")
        if region_hash(disk, ISO_FIRST * SECTOR, iso_bytes) != expected_iso:
            raise SystemExit("ISO partition differs from untouched stock ISO")
        disk.seek(ISO_FIRST * SECTOR + 16 * 2048)
        descriptor = disk.read(2048)
        if descriptor[1:6] != b"CD001":
            raise SystemExit("ISO9660 primary volume descriptor missing")
        disk.seek((total_lbas - 33) * SECTOR)
        backup_entries = disk.read(128 * 128)
        backup = disk.read(SECTOR)
        if backup_entries != entries or not header_ok(backup, total_lbas - 1, 1, entries_crc):
            raise SystemExit("backup GPT invalid")
    if args.launcher is not None:
        volume = PyFatFS(str(args.combined), offset=ESP_FIRST * SECTOR, read_only=True)
        original = PyFatFS(str(args.platform), offset=ESP_FIRST * SECTOR, read_only=True)
        try:
            for path in ("/EFI/BOOT/BOOTAA64.EFI", "/EFI/EDK2/QEMU_EFI.fd",
                         "/CASPER/VMLINUZ", "/CASPER/INITRD"):
                with volume.openbin(path, "r") as current:
                    current_hash = region_stream_hash(current)
                if path.endswith("BOOTAA64.EFI"):
                    with args.launcher.open("rb") as source:
                        expected = region_stream_hash(source)
                elif path.endswith("INITRD"):
                    with args.initrd.open("rb") as source:
                        expected = region_stream_hash(source)
                    if current_hash != expected:
                        raise SystemExit(f"FAT reverse-extracted initrd SHA mismatch: {current_hash} != {expected}")
                    import tempfile
                    with tempfile.TemporaryDirectory(prefix="uavf-combined-initrd-") as scratch:
                        compressed = Path(scratch) / "initrd"
                        main_stream = Path(scratch) / "initrd.zst"
                        unpacked = Path(scratch) / "initrd.cpio"
                        with volume.openbin(path, "r") as source, compressed.open("wb") as target:
                            import shutil
                            shutil.copyfileobj(source, target)
                        main_stream.write_bytes(main_zstd_payload(compressed))
                        subprocess.run([str(args.zstd), "-d", "-q", "-f", str(main_stream), "-o", str(unpacked)], check=True)
                        payload = unpacked.read_bytes()
                    for marker in (b"winavf-frame-bridge.py", b"99-winavf-vsock", b"LV:PRE", b"LV:UNIT"):
                        if marker not in payload:
                            raise SystemExit(f"combined-disk initrd marker missing: {marker.decode()}")
                    print(f"INITRD_SOURCE_SHA256={expected}")
                    print(f"INITRD_EXTRACTED_SHA256={current_hash}")
                    print("INITRD_REVERSE_EXTRACTION=PASS")
                    continue
                else:
                    with original.openbin(path, "r") as source:
                        expected = region_stream_hash(source)
                if current_hash != expected:
                    raise SystemExit(f"FAT file hash mismatch: {path}")
        finally:
            original.close()
            volume.close()
    print("RESULT=PASS")
    print(f"COMBINED_BYTES={combined_bytes}")
    print(f"ESP_PARTITION_SHA256={current_esp}")
    print(f"ISO_PARTITION_SHA256={expected_iso}")
    print("GPT_PRIMARY_BACKUP=PASS\nISO9660_PVD=PASS\nSTOCK_ISO_MUTATED=NO")


if __name__ == "__main__":
    main()
