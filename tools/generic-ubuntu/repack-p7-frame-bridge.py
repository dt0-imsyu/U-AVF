"""Repack selected entries in a known-booting hybrid initrd."""
import argparse
import io
import struct
import subprocess
from pathlib import Path

MAGIC = b"070701"
ZSTD = b"\x28\xb5\x2f\xfd"


def cpio_end(data):
    pos = 0
    while data[pos:pos + 6] == MAGIC:
        namesize = int(data[pos + 94:pos + 102], 16)
        filesize = int(data[pos + 54:pos + 62], 16)
        name = data[pos + 110:pos + 110 + namesize].rstrip(b"\0")
        pos = (pos + 110 + namesize + 3) & ~3
        pos = (pos + filesize + 3) & ~3
        if name == b"TRAILER!!!":
            return (pos + 511) & ~511
    raise RuntimeError("CPIO trailer not found")


def directory_record(name):
    fields = (0x0A11C0DE, 0x41ED, 0, 0, 2, 0, 0, 0, 0, 0, 0,
              len(name) + 1, 0)
    record = MAGIC + b"".join(f"{value:08x}".encode("ascii") for value in fields)
    record += name + b"\0"
    return record + b"\0" * (-len(record) % 4)


def file_record(name, contents):
    fields = (0x0A11C0DF, 0x81ED, 0, 0, 1, 0, len(contents), 0, 0, 0, 0,
              len(name) + 1, 0)
    record = MAGIC + b"".join(f"{value:08x}".encode("ascii") for value in fields)
    record += name + b"\0"
    record += b"\0" * (-len(record) % 4)
    record += contents
    return record + b"\0" * (-len(record) % 4)


def repack_main(data, replacements, ensure_dir=None, additions=None, add_dirs=None):
    additions = additions or {}
    add_dirs = add_dirs or []
    out = io.BytesIO()
    pos = 0
    replaced = set()
    inserted_dir = False
    seen_additions = set()
    while pos < len(data):
        if data[pos:pos + 6] != MAGIC:
            raise RuntimeError("invalid newc record")
        namesize = int(data[pos + 94:pos + 102], 16)
        filesize = int(data[pos + 54:pos + 62], 16)
        record_end = (pos + 110 + namesize + 3) & ~3
        record_end = (record_end + filesize + 3) & ~3
        name = data[pos + 110:pos + 110 + namesize].rstrip(b"\0")
        if name in add_dirs:
            raise RuntimeError(f"added directory already exists: {name!r}")
        if name in additions:
            seen_additions.add(name)
        if ensure_dir is not None:
            if name == ensure_dir:
                raise RuntimeError(f"directory already exists: {ensure_dir!r}")
            if name.startswith(ensure_dir + b"/") and not inserted_dir:
                out.write(directory_record(ensure_dir))
                inserted_dir = True
        if name == b"TRAILER!!!":
            for new_dir in add_dirs:
                out.write(directory_record(new_dir))
            for new_name, contents in additions.items():
                out.write(file_record(new_name, contents))
            out.write(data[pos:record_end])
            out.write(data[record_end:])
            pos = len(data)
            break
        if name in replacements:
            replacement = replacements[name]
            header = bytearray(data[pos:pos + 110])
            header[54:62] = f"{len(replacement):08x}".encode("ascii")
            out.write(header)
            out.write(data[pos + 110:record_end - filesize - ((4 - filesize % 4) % 4)])
            out.write(replacement)
            out.write(b"\0" * ((4 - len(replacement) % 4) % 4))
            replaced.add(name)
        else:
            out.write(data[pos:record_end])
        pos = record_end
    if replaced != set(replacements):
        raise RuntimeError(f"CPIO entries not found: {set(replacements) - replaced}")
    if ensure_dir is not None and not inserted_dir:
        raise RuntimeError(f"no child found for directory: {ensure_dir!r}")
    if seen_additions:
        raise RuntimeError(f"added entry already exists: {seen_additions!r}")
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--bridge", type=Path)
    parser.add_argument("--systemd-hook", type=Path)
    parser.add_argument("--replace-file", action="append", default=[],
                        help="existing CPIO_PATH=HOST_FILE; repeat as needed")
    parser.add_argument("--ensure-dir", help="insert a missing newc directory before its first child")
    parser.add_argument("--add-file", action="append", default=[],
                        help="new CPIO_PATH=HOST_FILE; repeat as needed")
    parser.add_argument("--add-dir", action="append", default=[],
                        help="missing parent directory; parent-before-child order")
    parser.add_argument("--zstd", required=True, type=Path)
    parser.add_argument("--zstd-level", type=int, default=19)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not 1 <= args.zstd_level <= 22:
        raise SystemExit("INVALID_ZSTD_LEVEL")
    replacements = {}
    if args.bridge is not None:
        replacements[b"usr/local/sbin/winavf-frame-bridge"] = args.bridge.read_bytes()
    if args.systemd_hook is not None:
        replacements[b"scripts/init-bottom/99-winavf-vsock-systemd"] = args.systemd_hook.read_bytes()
    for pair in args.replace_file:
        name, path = pair.split("=", 1)
        encoded = name.encode("ascii")
        if encoded in replacements:
            raise SystemExit("DUPLICATE_REPLACEMENT")
        replacements[encoded] = Path(path).read_bytes()
    additions = {}
    for pair in args.add_file:
        name, path = pair.split("=", 1)
        encoded = name.encode("ascii")
        if encoded in additions:
            raise SystemExit("DUPLICATE_ADDITION")
        additions[encoded] = Path(path).read_bytes()
    if not replacements and not args.ensure_dir and not additions:
        raise SystemExit("NO_REPLACEMENTS_SPECIFIED")
    if args.output.exists():
        raise SystemExit("REFUSING_TO_OVERWRITE_OUTPUT")
    source = args.source.read_bytes()
    early_end = cpio_end(source)
    if source[early_end:early_end + 4] != ZSTD:
        raise SystemExit("P7 main Zstd stream not found")
    compressed = args.output.with_suffix(".main.zst")
    cpio = args.output.with_suffix(".main.cpio")
    compressed.write_bytes(source[early_end:])
    subprocess.run([str(args.zstd), "-d", "-q", "-f", str(compressed), "-o", str(cpio)], check=True)
    patched = repack_main(cpio.read_bytes(), replacements,
                          args.ensure_dir.encode("ascii") if args.ensure_dir else None,
                          additions, [name.encode('ascii') for name in args.add_dir])
    patched_cpio = args.output.with_suffix(".patched.cpio")
    patched_cpio.write_bytes(patched)
    patched_zst = args.output.with_suffix(".patched.zst")
    compressor = [str(args.zstd), "-q"]
    if args.zstd_level > 19:
        compressor.append("--ultra")
    compressor += ["-" + str(args.zstd_level), "-f",
                   str(patched_cpio), "-o", str(patched_zst)]
    subprocess.run(compressor, check=True)
    args.output.write_bytes(source[:early_end] + patched_zst.read_bytes())
    for path in (compressed, cpio, patched_cpio, patched_zst):
        path.unlink(missing_ok=True)
    print(f"EARLY_CPIO_BYTES={early_end}")
    print(f"OUTPUT_BYTES={args.output.stat().st_size}")
    print("P7_BASE_PRESERVED=PASS")
    print("REPLACED_ENTRIES=" + ",".join(name.decode() for name in replacements))
    print("ADDED_ENTRIES=" + ",".join(name.decode() for name in additions))
    if args.ensure_dir:
        print("INSERTED_DIRECTORY=" + args.ensure_dir)


if __name__ == "__main__":
    main()
