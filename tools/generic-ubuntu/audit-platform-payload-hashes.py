"""Read-only hashes of boot payloads inside a generic-Ubuntu platform image."""

import argparse
import hashlib
import tempfile
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS

PART_OFFSET = 2048 * 512
FAT_SIZE = 126 * 1024 * 1024
PATHS = (
    "/EFI/BOOT/BOOTAA64.EFI",
    "/EFI/EDK2/QEMU_EFI.fd",
    "/CASPER/VMLINUZ",
    "/CASPER/INITRD",
)


def digest(reader):
    value = hashlib.sha256()
    size = 0
    while block := reader.read(1024 * 1024):
        value.update(block)
        size += len(block)
    return size, value.hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="uavf-payload-audit-") as scratch:
        fat_path = Path(scratch) / "platform.fat"
        with args.image.open("rb") as source, fat_path.open("wb") as target:
            source.seek(PART_OFFSET)
            remaining = FAT_SIZE
            while remaining:
                block = source.read(min(1024 * 1024, remaining))
                if not block:
                    raise SystemExit("SHORT_FAT_PARTITION")
                target.write(block)
                remaining -= len(block)
        if fat_path.stat().st_size != FAT_SIZE:
            raise SystemExit("SHORT_FAT_PARTITION")
        fs = PyFatFS(str(fat_path))
        try:
            for name in PATHS:
                with fs.openbin(name, "r") as reader:
                    size, sha = digest(reader)
                print(f"{name} BYTES={size} SHA256={sha}")
        finally:
            fs.close()


if __name__ == "__main__":
    main()
