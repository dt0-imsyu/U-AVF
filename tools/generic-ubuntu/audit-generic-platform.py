"""Read-only audit of the separate GPT/FAT32 Ubuntu platform disk."""
import argparse
import hashlib
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import zlib

from pyfatfs.PyFatFS import PyFatFS

IMAGE_SIZE = 128 * 1024 * 1024
PART_OFFSET = 2048 * 512
FAT_SIZE = 126 * 1024 * 1024


def digest(stream):
    value = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        value.update(block)
    return value.hexdigest().upper()


def main_zstd_payload(path):
    data = path.read_bytes()
    offset = 0
    while offset + 110 <= len(data) and data[offset:offset + 6] == b"070701":
        namesize = int(data[offset + 94:offset + 102], 16)
        filesize = int(data[offset + 54:offset + 62], 16)
        record = offset + 110 + namesize
        record = (record + 3) & ~3
        record += filesize
        record = (record + 3) & ~3
        name = data[offset + 110:offset + 110 + namesize].rstrip(b"\0")
        offset = record
        if name == b"TRAILER!!!":
            offset = (offset + 511) & ~511
            break
    if data[offset:offset + 4] != b"\x28\xb5\x2f\xfd":
        raise SystemExit("initrd main Zstd stream missing")
    return data[offset:]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--esp", required=True, type=Path)
    parser.add_argument("--launcher", required=True, type=Path)
    parser.add_argument("--firmware", required=True, type=Path)
    parser.add_argument("--initrd", required=True, type=Path)
    parser.add_argument("--zstd", required=True, type=Path)
    args = parser.parse_args()
    if args.esp.stat().st_size != IMAGE_SIZE:
        raise SystemExit("platform disk size mismatch")
    with args.esp.open("rb") as raw:
        mbr = raw.read(512)
        header = raw.read(512)
        entries = raw.read(128 * 128)
        if mbr[510:512] != b"\x55\xaa" or header[:8] != b"EFI PART":
            raise SystemExit("protective MBR or primary GPT missing")
        expected_header_crc = struct.unpack_from("<I", header, 16)[0]
        crc_source = bytearray(header[:92])
        crc_source[16:20] = b"\0" * 4
        if zlib.crc32(crc_source) != expected_header_crc:
            raise SystemExit("primary GPT header CRC mismatch")
        if zlib.crc32(entries) != struct.unpack_from("<I", header, 88)[0]:
            raise SystemExit("GPT entries CRC mismatch")
        start, end = struct.unpack_from("<QQ", entries, 32)
        if start != 2048 or end - start + 1 != FAT_SIZE // 512:
            raise SystemExit("GPT partition geometry mismatch")
        raw.seek(PART_OFFSET)
        with tempfile.TemporaryDirectory(prefix="uavf-audit-") as scratch:
            fat_path = Path(scratch) / "partition.fat"
            with fat_path.open("wb") as target:
                remaining = FAT_SIZE
                while remaining:
                    block = raw.read(min(1024 * 1024, remaining))
                    if not block:
                        raise SystemExit("short FAT partition read")
                    target.write(block)
                    remaining -= len(block)
            fs = PyFatFS(str(fat_path))
            try:
                for inside, outside in (
                    ("/EFI/BOOT/BOOTAA64.EFI", args.launcher),
                    ("/EFI/EDK2/QEMU_EFI.fd", args.firmware),
                ):
                    with fs.openbin(inside, "r") as reader, outside.open("rb") as original:
                        if digest(reader) != digest(original):
                            raise SystemExit(f"file mismatch: {inside}")
                for inside in ("/CASPER/VMLINUZ", "/CASPER/INITRD"):
                    if fs.getsize(inside) == 0:
                        raise SystemExit(f"empty file: {inside}")
                with fs.openbin("/CASPER/INITRD", "r") as installed, args.initrd.open("rb") as expected:
                    installed_hash = digest(installed)
                    expected_hash = digest(expected)
                if installed_hash != expected_hash:
                    raise SystemExit(f"initrd reverse-extraction SHA mismatch: {installed_hash} != {expected_hash}")
                extracted = Path(scratch) / "extracted-initrd"
                with fs.openbin("/CASPER/INITRD", "r") as installed, extracted.open("wb") as target:
                    shutil.copyfileobj(installed, target)
                compressed = Path(scratch) / "main-initrd.zst"
                compressed.write_bytes(main_zstd_payload(extracted))
                unpacked = Path(scratch) / "unpacked-initrd"
                subprocess.run([str(args.zstd), "-d", "-q", "-f", str(compressed), "-o", str(unpacked)], check=True)
                payload = unpacked.read_bytes()
                for marker in (b"winavf-frame-bridge.py", b"99-winavf-vsock", b"LV:PRE", b"LV:UNIT"):
                    if marker not in payload:
                        raise SystemExit(f"initrd CPIO marker missing after FAT extraction: {marker.decode()}")
                print(f"INITRD_SOURCE_SHA256={expected_hash}")
                print(f"INITRD_EXTRACTED_SHA256={installed_hash}")
                print("INITRD_REVERSE_EXTRACTION=PASS")
            finally:
                fs.close()
    print("RESULT=PASS")
    print("GPT_CRC=PASS")
    print("PARTITION_LBA=2048")
    print("UBOOT_FIRMWARE_PATH=PASS")
    print("EFI_LAUNCHER_PATH=PASS")
    print("CASPER_KERNEL_INITRD=PASS")


if __name__ == "__main__":
    main()
