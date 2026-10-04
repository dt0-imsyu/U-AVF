"""Build a disposable P33 prefix with an audited initrd and optional EFI launcher."""
import argparse
import hashlib
import shutil
import struct
import tempfile
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS


PREFIX_BYTES = 128 * 1024 * 1024
SECTOR_BYTES = 512
EXPECTED_PREFIX_SHA = "D5DE3958A956D3ED4D02FFEE7FC34A5CAAAB8A624709B1967B9926D120C38B76"
PRESERVED_FILES = (
    "/EFI/BOOT/BOOTAA64.EFI",
    "/EFI/EDK2/QEMU_EFI.fd",
    "/CASPER/VMLINUZ",
)


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


def read_entry(fs, path):
    with fs.openbin(path, "r") as stream:
        return stream.read()


def locate_fat_partition(raw):
    if raw[512:520] != b"EFI PART":
        raise RuntimeError("source is not a GPT platform prefix")
    entry_lba = struct.unpack_from("<Q", raw, 512 + 72)[0]
    count, entry_size = struct.unpack_from("<II", raw, 512 + 80)
    if count < 1 or entry_size != 128:
        raise RuntimeError("unexpected GPT entry geometry")
    entries = entry_lba * SECTOR_BYTES
    first_type = raw[entries:entries + 16]
    if first_type != bytes.fromhex("28732ac11ff8d211ba4b00a0c93ec93b"):
        raise RuntimeError("first partition is not the EFI System Partition")
    first_lba, last_lba = struct.unpack_from("<QQ", raw, entries + 32)
    if first_lba == 0 or last_lba < first_lba:
        raise RuntimeError("invalid FAT partition range")
    offset = first_lba * SECTOR_BYTES
    size = (last_lba - first_lba + 1) * SECTOR_BYTES
    if offset + size > len(raw):
        raise RuntimeError("FAT partition extends beyond prefix")
    return offset, size


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline-prefix", required=True, type=Path)
    parser.add_argument("--expected-baseline-sha", default=EXPECTED_PREFIX_SHA)
    parser.add_argument("--initrd", required=True, type=Path)
    parser.add_argument("--launcher", type=Path,
                        help="optional disposable BOOTAA64.EFI test candidate")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit("REFUSING_TO_OVERWRITE_OUTPUT")
    original = args.baseline_prefix.read_bytes()
    if len(original) != PREFIX_BYTES:
        raise SystemExit("BASELINE_PREFIX_SIZE_MISMATCH")
    original_sha = sha(original)
    if original_sha != args.expected_baseline_sha.upper():
        raise SystemExit("BASELINE_PREFIX_SHA_MISMATCH")
    initrd = args.initrd.read_bytes()
    launcher = args.launcher.read_bytes() if args.launcher else None
    offset, size = locate_fat_partition(original)
    with tempfile.TemporaryDirectory(prefix="uavf-p33-audio-") as temporary:
        fat_path = Path(temporary) / "esp.fat"
        fat_path.write_bytes(original[offset:offset + size])
        fs = PyFatFS(str(fat_path), preserve_case=True)
        try:
            preserved = {name: sha(read_entry(fs, name)) for name in PRESERVED_FILES
                         if launcher is None or name != "/EFI/BOOT/BOOTAA64.EFI"}
            fs.remove("/CASPER/INITRD")
            with fs.openbin("/CASPER/INITRD", "w") as target:
                target.write(initrd)
            if launcher is not None:
                fs.remove("/EFI/BOOT/BOOTAA64.EFI")
                with fs.openbin("/EFI/BOOT/BOOTAA64.EFI", "w") as target:
                    target.write(launcher)
        finally:
            fs.close()
        updated_fat = fat_path.read_bytes()
        if len(updated_fat) != size:
            raise RuntimeError("PyFatFS changed partition size")
        check = PyFatFS(str(fat_path), preserve_case=True)
        try:
            replaced = read_entry(check, "/CASPER/INITRD")
            if sha(replaced) != sha(initrd):
                raise RuntimeError("updated initrd failed FAT readback SHA")
            if launcher is not None and sha(read_entry(check, "/EFI/BOOT/BOOTAA64.EFI")) != sha(launcher):
                raise RuntimeError("updated EFI launcher failed FAT readback SHA")
            for name, expected in preserved.items():
                if sha(read_entry(check, name)) != expected:
                    raise RuntimeError(f"preserved platform file changed: {name}")
        finally:
            check.close()
        output = bytearray(original)
        output[offset:offset + size] = updated_fat
        if output[:offset] != original[:offset] or output[offset + size:] != original[offset + size:]:
            raise RuntimeError("bytes outside the ESP changed")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("xb") as target:
            target.write(output)
            target.flush()
    print("RESULT=PASS")
    print(f"BASELINE_PREFIX_SHA256={original_sha}")
    print(f"NEW_INITRD_SHA256={sha(initrd)}")
    print(f"NEW_PREFIX_SHA256={sha(output)}")
    print(f"PREFIX_BYTES={len(output)}")
    print(f"ESP_OFFSET={offset}")
    print(f"ESP_BYTES={size}")
    print("P7_FIRMWARE_KERNEL=BYTE_IDENTICAL")
    print("P7_LAUNCHER=" + ("DISPOSABLE_TEST_CANDIDATE" if launcher is not None else "BYTE_IDENTICAL"))
    if launcher is not None:
        print(f"TEST_LAUNCHER_SHA256={sha(launcher)}")
    print("BYTES_OUTSIDE_ESP=BYTE_IDENTICAL")


if __name__ == "__main__":
    main()
