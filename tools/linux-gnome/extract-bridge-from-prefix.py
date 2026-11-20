"""Extract the initramfs frame bridge from a verified-size platform prefix."""

import argparse
import hashlib
import importlib.machinery
import subprocess
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--expected-prefix-sha", required=True)
    parser.add_argument("--zstd", required=True, type=Path)
    args = parser.parse_args()
    if args.prefix.stat().st_size != 128 * 1024 * 1024:
        raise SystemExit("PREFIX_SIZE_MISMATCH")
    digest = hashlib.sha256(args.prefix.read_bytes()).hexdigest().upper()
    if digest != args.expected_prefix_sha.upper():
        raise SystemExit("PREFIX_SHA_MISMATCH=" + digest)
    fs = PyFatFS(str(args.prefix), offset=1024 * 1024, preserve_case=True)
    try:
        with fs.openbin("/CASPER/INITRD", "r") as initrd:
            data = initrd.read()
    finally:
        fs.close()
    helper = importlib.machinery.SourceFileLoader(
        "cpio_compare", str(Path(__file__).parents[1] / "generic-ubuntu" /
                            "compare-p7-clean-p9.py")).load_module()
    early_end = helper.early_end(data)
    main_cpio = subprocess.run([str(args.zstd), "-d", "-q", "-c"],
                               input=data[early_end:], capture_output=True,
                               check=True).stdout
    files, _ = helper.parse_cpio(main_cpio)
    bridge = files["scripts/init-premount/winavf-frame-bridge.py"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as output:
        output.write(bridge)
    print("RESULT=PASS")
    print("PREFIX_SHA256=" + digest)
    print("INITRD_BYTES=" + str(len(data)))
    print("INITRD_SHA256=" + hashlib.sha256(data).hexdigest().upper())
    print("EARLY_CPIO_BYTES=" + str(early_end))
    print("BRIDGE_BYTES=" + str(len(bridge)))
    print("BRIDGE_SHA256=" + hashlib.sha256(bridge).hexdigest().upper())
    print("OUTPUT=" + str(args.output))


if __name__ == "__main__":
    main()
