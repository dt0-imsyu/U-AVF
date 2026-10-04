"""Strict P7 versus clean P9 hybrid-initrd delta validation."""
import argparse
import hashlib
import subprocess
from pathlib import Path


def parse_cpio(data):
    pos = 0
    files = {}
    while data[pos:pos + 6] == b"070701":
        namesize = int(data[pos + 94:pos + 102], 16)
        filesize = int(data[pos + 54:pos + 62], 16)
        name_start = pos + 110
        name = data[name_start:name_start + namesize].rstrip(b"\0").decode()
        content_start = (name_start + namesize + 3) & ~3
        content_end = content_start + filesize
        files[name] = data[content_start:content_end]
        pos = (content_end + 3) & ~3
        if name == "TRAILER!!!":
            return files, pos
    raise RuntimeError("invalid CPIO")


def early_end(data):
    files, pos = parse_cpio(data)
    return (pos + 511) & ~511


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--p7", required=True, type=Path)
    p.add_argument("--p9", required=True, type=Path)
    p.add_argument("--bridge", required=True, type=Path)
    p.add_argument("--zstd", required=True, type=Path)
    args = p.parse_args()
    p7, p9 = args.p7.read_bytes(), args.p9.read_bytes()
    e7, e9 = early_end(p7), early_end(p9)
    if p7[:e7] != p9[:e9] or e7 != e9:
        raise SystemExit("EARLY_CPIO_NOT_IDENTICAL")
    print(f"EARLY_CPIO_BYTES={e7}")
    print("EARLY_CPIO_BYTE_IDENTICAL=PASS")
    paths = []
    extracted = []
    for label, data in (("P7", p7), ("P9", p9)):
        zst = args.p9.with_suffix(f".{label}.zst")
        cpio = args.p9.with_suffix(f".{label}.cpio")
        zst.write_bytes(data[e7:])
        subprocess.run([str(args.zstd), "-d", "-q", "-f", str(zst), "-o", str(cpio)], check=True)
        files, _ = parse_cpio(cpio.read_bytes())
        paths.append(set(files))
        extracted.append(files)
        zst.unlink(); cpio.unlink()
    if paths[0] != paths[1]:
        raise SystemExit("FILE_LIST_NOT_IDENTICAL")
    differing = [name for name in sorted(paths[0]) if hashlib.sha256(extracted[0][name]).digest() != hashlib.sha256(extracted[1][name]).digest()]
    print(f"FILE_LIST_IDENTICAL={len(paths[0])}")
    print(f"DIFFERING_FILES={len(differing)}")
    for name in differing:
        print(f"DIFFERING_FILE={name}")
    expected = args.bridge.read_bytes()
    if extracted[1]["usr/local/sbin/winavf-frame-bridge"] != expected:
        raise SystemExit("BRIDGE_SOURCE_MISMATCH")
    print("BRIDGE_SOURCE_EXACT=PASS")
    for name in ("scripts/init-premount/99-winavf-vsock", "scripts/init-premount/ORDER", "scripts/init-bottom/99-winavf-vsock-systemd", "scripts/init-bottom/ORDER"):
        if extracted[0][name] != extracted[1][name]:
            raise SystemExit(f"P7_FILE_CHANGED={name}")
    payload = b"\n".join(extracted[1].values())
    for marker in (b"LV:PRE", b"LV:UNIT", b"4051", b"4052"):
        if marker not in payload:
            raise SystemExit(f"MISSING_MARKER={marker.decode()}")
    if differing != ["usr/local/sbin/winavf-frame-bridge"]:
        raise SystemExit("UNEXPECTED_P7_P9_DELTA")
    print("HOOKS_ORDER_LV_MARKERS=PASS")
    print("RESULT=PASS")


if __name__ == "__main__":
    main()