"""Extract CASPER/INITRD from a verified 128 MiB U-AVF platform prefix."""

import argparse
import hashlib
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS


PREFIX_BYTES = 134_217_728
ESP_OFFSET = 1_048_576


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("prefix", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--prefix-sha256", required=True)
    args = parser.parse_args()

    if args.prefix.stat().st_size != PREFIX_BYTES:
        raise ValueError("Input is not a 128 MiB platform prefix")
    actual = sha256(args.prefix)
    if actual != args.prefix_sha256.upper():
        raise ValueError(f"Platform prefix hash mismatch: {actual}")
    if args.output.exists():
        raise FileExistsError(f"Refusing to overwrite {args.output}")

    with PyFatFS(str(args.prefix), offset=ESP_OFFSET, read_only=True) as fat:
        with fat.openbin("/CASPER/INITRD", "r") as source:
            with args.output.open("xb") as target:
                while True:
                    block = source.read(1024 * 1024)
                    if not block:
                        break
                    target.write(block)

    size = args.output.stat().st_size
    if size < 10 * 1024 * 1024:
        args.output.unlink(missing_ok=True)
        raise ValueError(f"Extracted initrd unexpectedly small: {size}")
    print("RESULT=PASS")
    print(f"PREFIX_SHA256={actual}")
    print(f"INITRD_BYTES={size}")
    print(f"INITRD_SHA256={sha256(args.output)}")


if __name__ == "__main__":
    main()
