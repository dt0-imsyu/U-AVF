"""Independent content validation for a P9 hybrid initrd."""
import argparse
import hashlib
import subprocess
from pathlib import Path


def cpio_files(data):
    pos = 0
    result = {}
    while data[pos:pos + 6] == b"070701":
        namesize = int(data[pos + 94:pos + 102], 16)
        filesize = int(data[pos + 54:pos + 62], 16)
        name_start = pos + 110
        name = data[name_start:name_start + namesize].rstrip(b"\0").decode()
        content_start = (name_start + namesize + 3) & ~3
        content_end = content_start + filesize
        result[name] = data[content_start:content_end]
        pos = (content_end + 3) & ~3
        if name == "TRAILER!!!":
            return result, pos
    raise RuntimeError("invalid newc archive")


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--p7", required=True, type=Path)
    parser.add_argument("--p9", required=True, type=Path)
    parser.add_argument("--bridge", required=True, type=Path)
    parser.add_argument("--zstd", required=True, type=Path)
    args = parser.parse_args()
    p7 = args.p7.read_bytes()
    p9 = args.p9.read_bytes()
    early = 0
    while p7[early:early + 6] == b"070701":
        namesize = int(p7[early + 94:early + 102], 16)
        filesize = int(p7[early + 54:early + 62], 16)
        name = p7[early + 110:early + 110 + namesize].rstrip(b"\0")
        pos = (early + 110 + namesize + 3) & ~3
        early = (pos + filesize + 3) & ~3
        if name == b"TRAILER!!!":
            early = (early + 511) & ~511
            break
    if p7[:early] != p9[:early]:
        raise SystemExit("EARLY_CPIO_NOT_IDENTICAL")
    print(f"EARLY_CPIO_BYTES={early}")
    print("EARLY_CPIO_BYTE_IDENTICAL=PASS")
    work = args.p9.with_suffix(".validation.zst")
    main = args.p9.with_suffix(".validation.cpio")
    work.write_bytes(p9[early:])
    subprocess.run([str(args.zstd), "-d", "-q", "-f", str(work), "-o", str(main)], check=True)
    files, _ = cpio_files(main.read_bytes())
    expected_bridge = args.bridge.read_bytes()
    actual_bridge = files["usr/local/sbin/winavf-frame-bridge"]
    if actual_bridge != expected_bridge:
        raise SystemExit("BRIDGE_BYTES_MISMATCH")
    print(f"BRIDGE_SHA256={sha(actual_bridge)}")
    print("BRIDGE_BYTES_EXACT=PASS")
    for name in ("scripts/init-premount/99-winavf-vsock", "scripts/init-premount/ORDER",
                 "scripts/init-bottom/99-winavf-vsock-systemd", "scripts/init-bottom/ORDER"):
        if name not in files:
            raise SystemExit(f"MISSING_CPIO_ENTRY={name}")
    payload = b"\n".join(files.values())
    for marker in (b"LV:PRE", b"LV:UNIT", b"4051", b"4052"):
        if marker not in payload:
            raise SystemExit(f"MISSING_MARKER={marker.decode()}")
    print("HOOKS_ORDER_LV_MARKERS=PASS")
    work.unlink()
    main.unlink()
    print("RESULT=PASS")


if __name__ == "__main__":
    main()