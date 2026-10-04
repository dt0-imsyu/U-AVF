"""Verify P11 only adds the missing parent directory for the frame bridge."""

import argparse
import hashlib
import importlib.machinery
import subprocess
from pathlib import Path


def records(data):
    pos = 0
    result = []
    while data[pos:pos + 6] == b"070701":
        header = data[pos:pos + 110]
        inode = int(header[6:14], 16)
        mode = int(header[14:22], 16)
        namesize = int(header[94:102], 16)
        size = int(header[54:62], 16)
        name = data[pos + 110:pos + 110 + namesize].rstrip(b"\0").decode()
        content = (pos + 110 + namesize + 3) & ~3
        result.append((name, inode, mode, data[content:content + size]))
        pos = (content + size + 3) & ~3
        if name == "TRAILER!!!":
            return result
    raise RuntimeError("main CPIO trailer missing")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--p10", required=True, type=Path)
    parser.add_argument("--p11", required=True, type=Path)
    parser.add_argument("--zstd", required=True, type=Path)
    args = parser.parse_args()
    helper = importlib.machinery.SourceFileLoader(
        "cpio_compare", str(Path(__file__).with_name("compare-p7-clean-p9.py"))).load_module()
    images = [args.p10.read_bytes(), args.p11.read_bytes()]
    offsets = [helper.early_end(image) for image in images]
    if offsets[0] != offsets[1] or images[0][:offsets[0]] != images[1][:offsets[1]]:
        raise SystemExit("EARLY_CPIO_NOT_IDENTICAL")
    decoded = []
    for image, offset in zip(images, offsets):
        main = subprocess.run([str(args.zstd), "-d", "-q", "-c"],
                              input=image[offset:], capture_output=True, check=True).stdout
        decoded.append(records(main))
    old, new = decoded
    directory = "usr/local/sbin"
    added = [record for record in new if record[0] == directory]
    if len(added) != 1 or added[0][2] != 0x41ED or added[0][3] != b"":
        raise SystemExit("BAD_NEW_DIRECTORY")
    if any(record[1] == added[0][1] for record in old):
        raise SystemExit("DIRECTORY_INODE_COLLISION")
    if [record for record in new if record[0] != directory] != old:
        raise SystemExit("OTHER_CPIO_RECORD_CHANGED")
    names = [record[0] for record in new]
    if not (names.index("usr/local") < names.index(directory)
            < names.index("usr/local/sbin/winavf-frame-bridge")):
        raise SystemExit("DIRECTORY_ORDER_INVALID")
    for label, image in (("P10", images[0]), ("P11", images[1])):
        print(f"{label}_BYTES={len(image)}")
        print(f"{label}_SHA256={hashlib.sha256(image).hexdigest().upper()}")
    print("PARENT_DIRECTORY_ORDER_AND_MODE=PASS")
    print("OTHER_CPIO_RECORDS_IDENTICAL=PASS")
    print("RESULT=PASS")


if __name__ == "__main__":
    main()
