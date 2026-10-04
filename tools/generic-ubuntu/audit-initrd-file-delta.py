"""Audit exact changed files in two hybrid Ubuntu initrds."""

import argparse
import hashlib
import importlib.machinery
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--zstd", required=True, type=Path)
    parser.add_argument("--replace", action="append", default=[],
                        help="CPIO_PATH=HOST_FILE; repeat for every expected changed file")
    parser.add_argument("--add", action="append", default=[],
                        help="CPIO_PATH=HOST_FILE; repeat for every expected new file")
    args = parser.parse_args()
    expected = {}
    for pair in args.replace:
        name, path = pair.split("=", 1)
        expected[name] = Path(path).read_bytes()
    additions = {}
    for pair in args.add:
        name, path = pair.split("=", 1)
        additions[name] = Path(path).read_bytes()
    helper = importlib.machinery.SourceFileLoader(
        "cpio_compare", str(Path(__file__).with_name("compare-p7-clean-p9.py"))).load_module()
    images = [args.base.read_bytes(), args.candidate.read_bytes()]
    offsets = [helper.early_end(image) for image in images]
    if offsets[0] != offsets[1] or images[0][:offsets[0]] != images[1][:offsets[1]]:
        raise SystemExit("EARLY_CPIO_NOT_IDENTICAL")
    archives = []
    for image, offset in zip(images, offsets):
        main = subprocess.run([str(args.zstd), "-d", "-q", "-c"],
                              input=image[offset:], capture_output=True, check=True).stdout
        files, _ = helper.parse_cpio(main)
        archives.append(files)
    before, after = archives
    if set(after) - set(before) != set(additions) or set(before) - set(after):
        raise SystemExit("UNEXPECTED_FILE_LIST_CHANGE")
    changed = {name for name in before if before[name] != after[name]}
    if changed != set(expected):
        raise SystemExit(f"UNEXPECTED_CHANGED_FILES={sorted(changed)}")
    for name, contents in expected.items():
        if after[name] != contents:
            raise SystemExit(f"SOURCE_MISMATCH={name}")
    for name, contents in additions.items():
        if after[name] != contents:
            raise SystemExit(f"ADDED_SOURCE_MISMATCH={name}")
    for name, image in (("BASE", images[0]), ("CANDIDATE", images[1])):
        print(f"{name}_BYTES={len(image)}")
        print(f"{name}_SHA256={hashlib.sha256(image).hexdigest().upper()}")
    print(f"EARLY_CPIO_BYTES={offsets[0]}")
    print("CHANGED_FILES=" + ",".join(sorted(changed)))
    print("ADDED_FILES=" + ",".join(sorted(additions)))
    print("RESULT=PASS")


if __name__ == "__main__":
    main()
