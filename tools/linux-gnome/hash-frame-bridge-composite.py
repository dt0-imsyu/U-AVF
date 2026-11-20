"""Hash the exact U-AVF one-disk layout without writing a 4 GiB raw file."""

import argparse
import hashlib
from pathlib import Path


DISK_BYTES = 4_102_029_312
PREFIX_BYTES = 134_217_728
ISO_BYTES = 3_967_463_424
TRAILER_BYTES = 16_896
GAP_BYTES = DISK_BYTES - PREFIX_BYTES - ISO_BYTES - TRAILER_BYTES


def append_file(digest, path: Path, expected_size: int) -> str:
    if path.stat().st_size != expected_size:
        raise ValueError(f"Wrong size: {path}")
    part = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(4 * 1024 * 1024), b""):
            digest.update(chunk)
            part.update(chunk)
    return part.hexdigest().upper()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("prefix", type=Path)
    parser.add_argument("iso", type=Path)
    parser.add_argument("trailer", type=Path)
    args = parser.parse_args()
    whole = hashlib.sha256()
    print("PREFIX_SHA256=" + append_file(whole, args.prefix, PREFIX_BYTES))
    print("ISO_SHA256=" + append_file(whole, args.iso, ISO_BYTES))
    whole.update(bytes(GAP_BYTES))
    print("ZERO_GAP_BYTES=" + str(GAP_BYTES))
    print("TRAILER_SHA256=" + append_file(whole, args.trailer, TRAILER_BYTES))
    print("DISK_BYTES=" + str(DISK_BYTES))
    print("DISK_SHA256=" + whole.hexdigest().upper())


if __name__ == "__main__":
    main()
