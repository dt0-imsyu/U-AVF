"""Replace CASPER/INITRD in a disposable P7 GPT prefix, preserving the source.

The prefix is the first 128 MiB of the one-disk Ubuntu carrier. Its FAT ESP
starts at byte 1 MiB. This helper never opens the official ISO or GOLDEN.
"""

import argparse
import hashlib
import shutil
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS


ESP_OFFSET = 1_048_576
EXPECTED_PREFIX_BYTES = 134_217_728
INITRD_PATH = "/CASPER/INITRD"
CONTROL_PATHS = ("/CASPER/VMLINUZ", "/EFI/BOOT/BOOTAA64.EFI")


def digest_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def digest_fat_file(fat: PyFatFS, path: str) -> str:
    digest = hashlib.sha256()
    with fat.openbin(path, "r") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def first_megabyte(path: Path) -> bytes:
    with path.open("rb") as source:
        return source.read(ESP_OFFSET)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_prefix", type=Path)
    parser.add_argument("new_initrd", type=Path)
    parser.add_argument("output_prefix", type=Path)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--initrd-sha256", required=True)
    args = parser.parse_args()

    source, initrd, output = args.source_prefix, args.new_initrd, args.output_prefix
    if output.exists():
        raise FileExistsError(f"Refusing to overwrite existing image: {output}")
    if source.stat().st_size != EXPECTED_PREFIX_BYTES:
        raise ValueError("Source is not the 128 MiB P7 prefix")
    if digest_file(source) != args.source_sha256.upper():
        raise ValueError("Source prefix SHA-256 mismatch")
    if digest_file(initrd) != args.initrd_sha256.upper():
        raise ValueError("Replacement initrd SHA-256 mismatch")

    with PyFatFS(str(source), offset=ESP_OFFSET, read_only=True) as fat:
        original_initrd_sha = digest_fat_file(fat, INITRD_PATH)
        controls = {path: digest_fat_file(fat, path) for path in CONTROL_PATHS}

    shutil.copyfile(source, output)
    try:
        with PyFatFS(str(output), offset=ESP_OFFSET, read_only=False) as fat:
            fat.remove(INITRD_PATH)
            with initrd.open("rb") as replacement, fat.openbin(INITRD_PATH, "w") as target:
                shutil.copyfileobj(replacement, target, 1024 * 1024)
        if output.stat().st_size != EXPECTED_PREFIX_BYTES:
            raise ValueError("Output prefix size changed")
        if first_megabyte(source) != first_megabyte(output):
            raise ValueError("GPT and pre-ESP bytes changed")
        with PyFatFS(str(output), offset=ESP_OFFSET, read_only=True) as fat:
            if digest_fat_file(fat, INITRD_PATH) != args.initrd_sha256.upper():
                raise ValueError("Replacement initrd failed FAT readback")
            for path, expected in controls.items():
                if digest_fat_file(fat, path) != expected:
                    raise ValueError(f"Unrelated ESP file changed: {path}")
        if digest_file(source) != args.source_sha256.upper():
            raise ValueError("Source prefix changed during replacement")
    except Exception:
        output.unlink(missing_ok=True)
        raise

    print("RESULT=PASS")
    print(f"SOURCE_PREFIX_SHA256={args.source_sha256.upper()}")
    print(f"SOURCE_INITRD_SHA256={original_initrd_sha}")
    print(f"NEW_INITRD_SHA256={args.initrd_sha256.upper()}")
    print(f"OUTPUT_PREFIX_SHA256={digest_file(output)}")
    print(f"OUTPUT_PREFIX_BYTES={output.stat().st_size}")
    print("GPT_AND_PRE_ESP_UNCHANGED=PASS")
    print("BOOTAA64_AND_VMLINUZ_UNCHANGED=PASS")


if __name__ == "__main__":
    main()
