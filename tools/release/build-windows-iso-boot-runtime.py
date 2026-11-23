"""Package the WinPE add-ons and a bounded BCD patch for the ISO-only installer."""

import argparse
from difflib import SequenceMatcher
from hashlib import sha256
from pathlib import Path
import struct
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


def require_digest(data, digest, description):
    if sha256(data).hexdigest() != digest:
        raise ValueError(f"{description} does not match the supported Windows profile")


def make_bcd_patch(source, target):
    patch = bytearray(b"UAVFBCD1" + struct.pack(">i", len(target)))
    # Registry hives contain long repeated zero runs. Ignore those as anchors.
    for operation, first, last, output_start, output_end in SequenceMatcher(
        None, source, target
    ).get_opcodes():
        length = output_end - output_start
        if not length:
            continue
        if operation == "equal":
            patch.extend(struct.pack(">ii", first, last - first))
        else:
            patch.extend(struct.pack(">ii", -1, length))
            patch.extend(target[output_start:output_end])
    return patch


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overlay", type=Path, required=True,
                        help="WinPE add-ons rooted at Windows/System32")
    parser.add_argument("--loader", type=Path, required=True)
    parser.add_argument("--firmware", type=Path, required=True)
    parser.add_argument("--stock-bcd", type=Path, required=True)
    parser.add_argument("--setup-bcd", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    stock, setup, loader = (path.read_bytes() for path in (args.stock_bcd, args.setup_bcd, args.loader))
    require_digest(stock, "b90ef16b94c3dda7d76cc39840befaa9786887517445c3b6f47ebc806d0ab105", "Stock BCD")
    require_digest(setup, "034362e7531ff242e32f24f1abd37ebeca05cc888981d95094e7986a75521784", "Setup BCD")
    require_digest(loader, "359ded6e811f23b011566938b6f3cc8a01df480d5e8225a320fefe357cd98e3a", "EFI loader")
    firmware = args.firmware.read_bytes()
    require_digest(firmware, "9ed3dd035116a39feac7c79a9c0c9ac640ea222d499a8ec3b1c6fcee1adfe72f", "Guest firmware")
    files = sorted(path for path in args.overlay.rglob("*") if path.is_file())
    if len(files) != 17 or any(path.is_symlink() or path.stat().st_size > 2 * 1024**2 for path in files):
        raise ValueError("Expected the 17 WinPE add-ons for this profile")
    patch = make_bcd_patch(stock, setup)
    entries = {
        "bootaa64.efi": loader,
        "qemu_efi.fd": firmware,
        "bcd.patch": patch,
    }
    for path in files:
        entries["overlay/" + path.relative_to(args.overlay).as_posix()] = path.read_bytes()
    # Keep ZIP metadata independent of the checkout's timestamps and host OS.
    with ZipFile(args.output, "x", compression=ZIP_DEFLATED) as bundle:
        for name, data in sorted(entries.items()):
            info = ZipInfo(name)
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            bundle.writestr(info, data, compress_type=ZIP_DEFLATED)
    print(f"WinPE runtime bundle: {args.output.stat().st_size} bytes")


if __name__ == "__main__":
    main()
