"""Inspect Ubuntu repository metadata and verify ARM64 Xvnc package bytes."""

import argparse
import hashlib
import lzma
from pathlib import Path


NAMES = {"tigervnc-standalone-server", "tigervnc-common",
         "libfile-readbackwards-perl"}


def records(path):
    text = lzma.decompress(path.read_bytes()).decode("utf-8")
    for paragraph in text.split("\n\n"):
        fields = {}
        for line in paragraph.splitlines():
            if line.startswith(" "):
                continue
            if ": " in line:
                key, value = line.split(": ", 1)
                fields[key] = value
        if fields.get("Package") in NAMES:
            yield fields


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("index", type=Path, nargs="+")
    parser.add_argument("--downloads", type=Path)
    args = parser.parse_args()
    seen = {}
    for index in args.index:
        for fields in records(index):
            name = fields["Package"]
            if name in seen:
                continue
            seen[name] = fields
            print(f"PACKAGE={name} VERSION={fields.get('Version')} ARCH={fields.get('Architecture')}")
            print(f"FILENAME={fields.get('Filename')}")
            print(f"SIZE={fields.get('Size')} SHA256={fields.get('SHA256')}")
            print(f"DEPENDS={fields.get('Depends')}")
            if args.downloads:
                path = args.downloads / Path(fields["Filename"]).name
                if path.exists():
                    actual = hashlib.sha256(path.read_bytes()).hexdigest()
                    if (path.stat().st_size != int(fields["Size"])
                            or actual.lower() != fields["SHA256"].lower()):
                        raise SystemExit(f"PACKAGE_HASH_MISMATCH={name}")
                    print(f"DOWNLOADED_SHA256_PASS={name}")
    missing = NAMES - seen.keys()
    if missing:
        raise SystemExit(f"MISSING_PACKAGES={sorted(missing)}")
    print("RESULT=PASS")


if __name__ == "__main__":
    main()
