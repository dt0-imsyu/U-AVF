"""Swap one owned EFI file on the isolated ESP, retaining an exact rollback."""
import argparse
import json
import struct
import subprocess
import uuid
from pathlib import Path
from runpy import run_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--journal", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--restore", action="store_true")
    args = parser.parse_args()
    folder = Path(__file__).parent
    g = run_path(str(folder / "isolated-device-bcd.py"))
    transact = run_path(str(folder / "apply-ram-gop-file.py"))["transact"]
    frozen = g["RAM_GOP_HANDOFF_AUDIT_EFI_SHA"]
    experimental = g["WDDM_PNP_EFI_SHA"]
    candidate = args.candidate.read_bytes()
    assert g["sha"](candidate) == experimental, "Exact WDDM PnP image required"
    package, disk = g["PACKAGE"], g["DISK"]
    volume = subprocess.check_output([
        args.adb, "exec-out",
        f"run-as {package} dd if={disk} bs=512 skip=17858560 count=1 2>/dev/null",
    ])
    assert volume[3:11] == b"NTFS    " and volume[72:80].hex() == "b769f63e81f63ec6"
    fat = g["DeviceFat"](args.adb, args.journal.parent,
                         expected_efi_sha=experimental if args.restore else frozen)
    path = "/EFI/BOOT/BOOTAA64.EFI"
    current, clusters = fat.file(path)
    original = (args.journal / "original.efi").read_bytes() if args.restore else current
    assert g["sha"](original) == frozen
    assert len(clusters) == 2 and fat.cluster_bytes == 8192
    directory = fat.root
    for name in ("EFI", "BOOT"):
        (entry,) = [e for e in fat.entries(directory) if e[1] == name and e[4] & 16]
        directory = entry[2]

    locations = []
    for number in fat.chain(directory):
        position = fat.offset(number)
        data = fat.read(position, fat.cluster_bytes)
        for offset in range(0, len(data), 32):
            if data[offset] == 0:
                break
            part = data[offset:offset + 32]
            if part[:11] == b"BOOTAA64EFI" and not part[11] & 0x18:
                lo, hi = struct.unpack_from("<H", part, 26)[0], struct.unpack_from("<H", part, 20)[0]
                assert (hi << 16) | lo == clusters[0]
                assert struct.unpack_from("<I", part, 28)[0] == len(current)
                locations.append(position + offset)

    (entry_position,) = locations
    sector = entry_position // 512 * 512
    old_dir = fat.read(sector, 512)
    new_dir = bytearray(old_dir)
    replacement = original if args.restore else candidate
    struct.pack_into("<I", new_dir, entry_position - sector + 28, len(replacement))
    blocks = []
    for index, cluster in enumerate(clusters):
        position = fat.offset(cluster)
        start = index * fat.cluster_bytes
        old = fat.read(position, fat.cluster_bytes)
        new = replacement[start:start + fat.cluster_bytes]
        if args.restore:
            new = (args.journal / f"before-{index}.bin").read_bytes()
            expected = (args.journal / f"after-{index}.bin").read_bytes()
            assert old == expected and new[:min(len(original)-start, fat.cluster_bytes)] == original[start:start + fat.cluster_bytes]
        else:
            new += old[len(new):]
        blocks.append((position, old, new))
    blocks.append((sector, old_dir, bytes(new_dir)))

    report = {"old_sha": g["sha"](current), "new_sha": g["sha"](replacement),
              "offsets": [b[0] for b in blocks], "bytes": sum(len(b[1]) for b in blocks)}
    if not args.apply and not args.restore:
        print(json.dumps({**report, "mode": "read-only"}))
        return
    if args.apply:
        args.journal.mkdir()
        (args.journal / "original.efi").write_bytes(original)
        for index, (_, before, after) in enumerate(blocks[:2]):
            (args.journal / f"before-{index}.bin").write_bytes(before)
            (args.journal / f"after-{index}.bin").write_bytes(after)
    temp = args.journal / "transfer.bin"
    remote = "/data/local/tmp/uavf-wddm-pnp-" + uuid.uuid4().hex + ".bin"

    def write(offset, data):
        temp.write_bytes(data)
        subprocess.run([args.adb, "push", str(temp), remote], check=True, capture_output=True)
        subprocess.run([args.adb, "shell", "chmod", "644", remote], check=True, capture_output=True)
        fat.cold()
        subprocess.run([args.adb, "shell", "run-as", package, "dd",
                        "if=" + remote, "of=" + disk, "bs=512", f"seek={offset//512}",
                        f"count={len(data)//512}", "conv=notrunc"], check=True, capture_output=True)

    def finish():
        assert fat.file(path) == (replacement, clusters)

    try:
        transact(blocks, fat.read, write, fat.cold, finish)
    finally:
        subprocess.run([args.adb, "shell", "rm", "-f", remote], capture_output=True)
    report["mode"] = "restored" if args.restore else "applied"
    (args.journal / (report["mode"] + ".json")).write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == "__main__":
    main()
