"""Finalize a disposable 128 MiB U-AVF prefix after replacing CASPER/INITRD.

Only the generated copy is repaired. The original prefix is read only.
"""
import argparse
import hashlib
import shutil
import subprocess
from pathlib import Path


OFFSET = 1_048_576
ESP_BYTES = 126 * OFFSET
PREFIX_BYTES = 128 * OFFSET


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def copy_bytes(source, target, count):
    remaining = count
    while remaining:
        chunk = source.read(min(1024 * 1024, remaining))
        if not chunk:
            raise RuntimeError("UNEXPECTED_EOF")
        target.write(chunk)
        remaining -= len(chunk)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--candidate-sha256", required=True)
    parser.add_argument("--fsck", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit("REFUSING_TO_OVERWRITE_OUTPUT")
    if args.source.resolve() == args.candidate.resolve() or args.source.resolve() == args.output.resolve():
        raise SystemExit("SOURCE_MUST_BE_READ_ONLY")
    if args.source.stat().st_size != PREFIX_BYTES or args.candidate.stat().st_size != PREFIX_BYTES:
        raise SystemExit("PREFIX_SIZE_MISMATCH")
    if sha256(args.source) != args.source_sha256.upper():
        raise SystemExit("SOURCE_SHA_MISMATCH")
    if sha256(args.candidate) != args.candidate_sha256.upper():
        raise SystemExit("CANDIDATE_SHA_MISMATCH")
    with args.source.open("rb") as original, args.candidate.open("rb") as modified:
        if original.read(OFFSET) != modified.read(OFFSET):
            raise SystemExit("GPT_OR_PRE_ESP_CHANGED")

    esp = args.output.with_suffix(".esp.tmp")
    if esp.exists():
        raise SystemExit("REFUSING_TO_OVERWRITE_TEMP_ESP")
    try:
        with args.candidate.open("rb") as candidate, esp.open("wb") as dest:
            candidate.seek(OFFSET)
            copy_bytes(candidate, dest, ESP_BYTES)
        repaired = subprocess.run([str(args.fsck), "-a", str(esp)], capture_output=True, text=True)
        if repaired.returncode not in (0, 1):
            raise RuntimeError("FSCK_REPAIR_FAILED=" + repaired.stdout + repaired.stderr)
        verified = subprocess.run([str(args.fsck), "-n", str(esp)], capture_output=True, text=True)
        if verified.returncode != 0:
            raise RuntimeError("FSCK_READ_ONLY_FAILED=" + verified.stdout + verified.stderr)
        if esp.stat().st_size != ESP_BYTES:
            raise RuntimeError("ESP_SIZE_CHANGED")
        shutil.copyfile(args.candidate, args.output)
        with args.output.open("r+b") as target, esp.open("rb") as source:
            target.seek(OFFSET)
            copy_bytes(source, target, ESP_BYTES)
        with args.source.open("rb") as original, args.output.open("rb") as final:
            if original.read(OFFSET) != final.read(OFFSET):
                raise RuntimeError("GPT_OR_PRE_ESP_CHANGED_AFTER_REPAIR")
        if args.output.stat().st_size != PREFIX_BYTES or sha256(args.source) != args.source_sha256.upper():
            raise RuntimeError("PREFIX_VERIFICATION_FAILED")
        print("RESULT=PASS")
        print("FSCK_READ_ONLY=PASS")
        print("SOURCE_SHA256=" + args.source_sha256.upper())
        print("OUTPUT_SHA256=" + sha256(args.output))
        print("OUTPUT_BYTES=" + str(args.output.stat().st_size))
        print("ESP_SHA256=" + sha256(esp))
        print("FSCK_REPAIR_OUTPUT=" + repaired.stdout.strip().replace("\n", " | "))
    except Exception:
        args.output.unlink(missing_ok=True)
        raise
    finally:
        esp.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
