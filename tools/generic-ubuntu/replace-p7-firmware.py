"""Make a disposable P7 ESP prefix with only QEMU_EFI.fd replaced."""

import argparse
import hashlib
import shutil
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS


PREFIX_BYTES = 134_217_728
ESP_OFFSET = 1_048_576
FD_PATH = "/EFI/EDK2/QEMU_EFI.fd"
FD_KEY = FD_PATH.upper()


def sha_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest().upper()


def tree_hashes(fs: PyFatFS) -> dict[str, str]:
    result = {}
    for name in fs.walk.files("/"):
        digest = hashlib.sha256()
        with fs.openbin(name, "r") as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        result[name.upper()] = digest.hexdigest().upper()
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("firmware", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--stock-fd-sha256", required=True)
    parser.add_argument("--candidate-fd-sha256", required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise FileExistsError(args.output)
    if args.source.stat().st_size != PREFIX_BYTES:
        raise ValueError("source prefix size mismatch")
    if sha_file(args.source) != args.source_sha256.upper():
        raise ValueError("source prefix SHA mismatch")
    if args.firmware.stat().st_size != 2_097_152:
        raise ValueError("candidate FD size mismatch")
    if sha_file(args.firmware) != args.candidate_fd_sha256.upper():
        raise ValueError("candidate FD SHA mismatch")

    with PyFatFS(str(args.source), offset=ESP_OFFSET, read_only=True) as fs:
        before = tree_hashes(fs)
    if before.get(FD_KEY) != args.stock_fd_sha256.upper():
        raise ValueError("source ESP does not contain expected stock FD")

    shutil.copyfile(args.source, args.output)
    try:
        with PyFatFS(str(args.output), offset=ESP_OFFSET, read_only=False) as fs:
            fs.remove(FD_PATH)
            with args.firmware.open("rb") as source, fs.openbin(FD_PATH, "w") as dest:
                shutil.copyfileobj(source, dest, 1024 * 1024)
        with PyFatFS(str(args.output), offset=ESP_OFFSET, read_only=True) as fs:
            after = tree_hashes(fs)
        if set(before) != set(after):
            raise ValueError("ESP file set changed")
        if after[FD_KEY] != args.candidate_fd_sha256.upper():
            raise ValueError("FD FAT readback mismatch")
        for name, digest in before.items():
            if name != FD_KEY and after[name] != digest:
                raise ValueError(f"unrelated ESP file changed: {name}")
        if args.output.stat().st_size != PREFIX_BYTES:
            raise ValueError("output prefix size changed")
        with args.source.open("rb") as original, args.output.open("rb") as updated:
            if original.read(ESP_OFFSET) != updated.read(ESP_OFFSET):
                raise ValueError("GPT/pre-ESP bytes changed")
        if sha_file(args.source) != args.source_sha256.upper():
            raise ValueError("source prefix changed during operation")
    except Exception:
        args.output.unlink(missing_ok=True)
        raise
    print("RESULT=PASS")
    print(f"SOURCE_PREFIX_SHA256={args.source_sha256.upper()}")
    print(f"CANDIDATE_FD_SHA256={args.candidate_fd_sha256.upper()}")
    print(f"OUTPUT_PREFIX_SHA256={sha_file(args.output)}")
    print(f"OTHER_ESP_FILES_IDENTICAL={len(before) - 1}")
    print("GPT_PRE_ESP_IDENTICAL=PASS")


if __name__ == "__main__":
    main()
