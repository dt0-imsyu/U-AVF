"""Check that a packaged WinPE runtime matches the app's input contract."""

import argparse
import hashlib
import io
import re
import zipfile
from pathlib import Path


def verify(apk, source):
    java = source.read_text(encoding="utf-8")
    asset, = re.findall(r'\bASSET = "([^"]+)";', java)
    allowlist, = re.findall(r'\bENTRIES = Set\.of\((.*?)\);', java, re.S)
    expected = set(re.findall(r'"([^"]+)"', allowlist))
    asset = "assets/" + asset
    with zipfile.ZipFile(apk) as package:
        if asset not in package.namelist():
            raise ValueError("Missing packaged WinPE runtime")
        if not 0 < package.getinfo(asset).file_size <= 4 * 1024**2:
            raise ValueError("Invalid packaged WinPE bundle size")
        data = package.read(asset)
    with zipfile.ZipFile(io.BytesIO(data)) as bundle:
        entries = bundle.infolist()
        if len(entries) != len(expected) or set(bundle.namelist()) != expected:
            raise ValueError("Packaged WinPE file list differs from WindowsIsoBootRuntime")
        for entry in entries:
            if entry.is_dir() or not 0 < entry.file_size <= 2 * 1024**2:
                raise ValueError(f"Invalid WinPE file size: {entry.filename}")
        corrupted = bundle.testzip()
        if corrupted:
            raise ValueError(f"Invalid WinPE ZIP checksum: {corrupted}")
        for name, constant in (("bootaa64.efi", "EFI_SHA"), ("qemu_efi.fd", "FIRMWARE_SHA")):
            digest, = re.findall(r'\b' + constant + r' = "([0-9A-F]{64})";', java)
            if hashlib.sha256(bundle.read(name)).hexdigest().upper() != digest:
                raise ValueError(f"Packaged WinPE file fingerprint differs: {name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.apk, args.source)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, f"WINDOWS_ISO_RUNTIME=FAIL {error}\n")
    print("WINDOWS_ISO_RUNTIME=PASS")


if __name__ == "__main__":
    main()
