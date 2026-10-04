"""Create a disposable P7-platform/P9-initrd control; never modify inputs."""

import argparse
import hashlib
import shutil
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS

OFFSET = 1024 * 1024
ORIGINAL_INITRD = "9E1E87ED0486D790C760AC5BC89C5FE87112CEF404E430C5932990E932BAE6DE"
NEW_INITRD = "4D0DB4ADA9CA844A90FA315AAAC344EEA68A7ECF9DD12E4EF809B35F3E438603"
UNCHANGED = {
    "/EFI/BOOT/BOOTAA64.EFI": "7F388FCB12598FAF5D8D0265BF41BD222328820064C3F7C9278B7979E2F0F740",
    "/EFI/EDK2/QEMU_EFI.fd": "7162202A2ED14C6BE3433915CB5786D9A41E3722647E40AA3CFEF29720D14963",
    "/CASPER/VMLINUZ": "3F18B4B8D4DA3ED5EFD9B3AD0ED4AB3286178DED979BFDCBD759CC30EB805CE4",
}


def digest(reader):
    value = hashlib.sha256()
    size = 0
    while block := reader.read(1024 * 1024):
        value.update(block)
        size += len(block)
    return size, value.hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--initrd", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--old-sha", default=ORIGINAL_INITRD)
    parser.add_argument("--new-sha", default=NEW_INITRD)
    args = parser.parse_args()
    old_expected = args.old_sha.upper()
    new_expected = args.new_sha.upper()
    if len(old_expected) != 64 or len(new_expected) != 64:
        raise SystemExit("INVALID_EXPECTED_SHA")
    if args.output.exists():
        raise SystemExit("REFUSING_TO_OVERWRITE_OUTPUT")
    with args.initrd.open("rb") as reader:
        _, incoming = digest(reader)
    if incoming != new_expected:
        raise SystemExit("NEW_INITRD_SHA_MISMATCH")
    shutil.copyfile(args.input, args.output)
    fs = PyFatFS(str(args.output), offset=OFFSET, preserve_case=True)
    try:
        for name, expected in UNCHANGED.items():
            with fs.openbin(name, "r") as reader:
                _, current = digest(reader)
            if current != expected:
                raise RuntimeError(f"UNEXPECTED_P7_BASE_FILE={name} SHA={current}")
        with fs.openbin("/CASPER/INITRD", "r") as reader:
            _, old = digest(reader)
        if old != old_expected:
            raise RuntimeError(f"UNEXPECTED_P7_INITRD_SHA={old}")
        with args.initrd.open("rb") as reader, fs.openbin("/CASPER/INITRD", "r+") as writer:
            shutil.copyfileobj(reader, writer, 1024 * 1024)
            writer.truncate(args.initrd.stat().st_size)
        with fs.openbin("/CASPER/INITRD", "r") as reader:
            size, installed = digest(reader)
        if installed != new_expected:
            raise RuntimeError(f"INSTALLED_INITRD_SHA_MISMATCH={installed}")
        for name, expected in UNCHANGED.items():
            with fs.openbin(name, "r") as reader:
                _, current = digest(reader)
            if current != expected:
                raise RuntimeError(f"OTHER_FILE_CHANGED={name}")
    finally:
        fs.close()
    with args.output.open("rb") as reader:
        output_size, output_sha = digest(reader)
    print(f"RESULT=PASS\nOUTPUT={args.output}\nOUTPUT_BYTES={output_size}\nOUTPUT_SHA256={output_sha}")
    print(f"INSTALLED_INITRD_BYTES={size}\nINSTALLED_INITRD_SHA256={installed}")
    print("P7_LAUNCHER_FIRMWARE_KERNEL_UNCHANGED=PASS")


if __name__ == "__main__":
    main()
