"""Prove P10 differs from clean P9 in only the vsock systemd hook."""

import argparse
import hashlib
import subprocess
from pathlib import Path

from importlib.machinery import SourceFileLoader


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--p9", required=True, type=Path)
    parser.add_argument("--p10", required=True, type=Path)
    parser.add_argument("--hook", required=True, type=Path)
    parser.add_argument("--zstd", required=True, type=Path)
    args = parser.parse_args()
    helper = SourceFileLoader("cpio_compare", str(Path(__file__).with_name("compare-p7-clean-p9.py"))).load_module()
    p9, p10 = args.p9.read_bytes(), args.p10.read_bytes()
    e9, e10 = helper.early_end(p9), helper.early_end(p10)
    if e9 != e10 or p9[:e9] != p10[:e10]:
        raise SystemExit("EARLY_CPIO_NOT_IDENTICAL")
    decoded = []
    for data in (p9[e9:], p10[e10:]):
        result = subprocess.run([str(args.zstd), "-d", "-q", "-c"], input=data,
                                capture_output=True, check=True).stdout
        entries, _ = helper.parse_cpio(result)
        decoded.append(entries)
    old, new = decoded
    if old.keys() != new.keys():
        raise SystemExit("FILE_LIST_CHANGED")
    changed = [name for name in old if old[name] != new[name]]
    expected = "scripts/init-bottom/99-winavf-vsock-systemd"
    if changed != [expected]:
        raise SystemExit(f"UNEXPECTED_CHANGED_FILES={changed}")
    if new[expected] != args.hook.read_bytes():
        raise SystemExit("HOOK_SOURCE_MISMATCH")
    if b"vsock:-1:" in new[expected] or b"ListenStream=vsock::4051" not in new[expected] or b"ListenStream=vsock::4052" not in new[expected]:
        raise SystemExit("VSOCK_ADDRESS_NOT_FIXED")
    for label, data in (("P9", p9), ("P10", p10)):
        print(f"{label}_BYTES={len(data)}")
        print(f"{label}_SHA256={hashlib.sha256(data).hexdigest().upper()}")
    print(f"EARLY_CPIO_BYTES={e9}")
    print(f"CHANGED_FILE={expected}")
    print("RESULT=PASS")


if __name__ == "__main__":
    main()
