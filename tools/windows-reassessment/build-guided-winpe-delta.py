"""Build a bounded WinPE FAT delta against the frozen media without cloning it."""
import argparse
import hashlib
import io
from pathlib import Path
import struct
import zlib

from pyfatfs.PyFatFS import PyFatBytesIOFS

BLOCK = 1024 * 1024
SIZE = 9126805504
BASE_SHA = "a1de9652e81a173bc4e242cfa53f23c4d8ad31c2d54500397650f894d927df2a"
WIM_SHA = "a90ba45cc1404cf18fe2f10ce195e565b8f70e45416583f2f25f3ef4391bef15"
BOOT_EFI_SHA = "893b7859b6e27598b8392f5ec9a9fb33c948fcaf4ef1918f28072ed5ab402e8b"
SMP_EFI_SHA = "359ded6e811f23b011566938b6f3cc8a01df480d5e8225a320fefe357cd98e3a"


class Overlay(io.RawIOBase):
    def __init__(self, source):
        self.source = source
        self.blocks = {}

    def readable(self): return True
    def writable(self): return True
    def seekable(self): return True
    def tell(self): return self.source.tell()
    def seek(self, *args): return self.source.seek(*args)

    def read(self, size=-1):
        offset = self.tell()
        data = bytearray(self.source.read(size))
        for index in range(offset // BLOCK, (offset + len(data) + BLOCK - 1) // BLOCK):
            if index in self.blocks:
                start = max(offset, index * BLOCK)
                end = min(offset + len(data), (index + 1) * BLOCK)
                data[start-offset:end-offset] = self.blocks[index][start-index*BLOCK:end-index*BLOCK]
        return bytes(data)

    def write(self, data):
        written = 0
        while written < len(data):
            index, within = divmod(self.tell(), BLOCK)
            amount = min(len(data)-written, BLOCK-within)
            if index not in self.blocks:
                if len(self.blocks) == 31:
                    raise ValueError("WinPE delta exceeded the 31 MiB overlay limit")
                self.source.seek(index * BLOCK)
                self.blocks[index] = bytearray(self.source.read(BLOCK))
            self.blocks[index][within:within+amount] = data[written:written+amount]
            self.source.seek(index * BLOCK + within + amount)
            written += amount
        return written

    def readinto(self, buffer):
        data = self.read(len(buffer))
        buffer[:len(data)] = data
        return len(data)

    def close(self):
        # The overlay remains readable after the filesystem unmounts.
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("template", type=Path)
    parser.add_argument("guided_wim", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--smp-efi", type=Path, help="Use the verified 8-vCPU 1920x1200 loader")
    parser.add_argument("--from-guided-delta", action="store_true",
                        help="Use a retained guided-delta directory in place of the WIM")
    args = parser.parse_args()
    args.output.mkdir()
    with args.template.open("rb") as source:
        assert args.template.stat().st_size == SIZE
        assert hashlib.file_digest(source, "sha256").hexdigest() == BASE_SHA
        disk = Overlay(source)
        if args.from_guided_delta:
            template_sha, guided_sha = (args.guided_wim / "guided-profile.txt").read_text(encoding="ascii").lower().splitlines()
            with (args.guided_wim / "guided.delta").open("rb") as delta:
                magic, size, count = struct.unpack("<8sQI", delta.read(20))
                if magic != b"UWDP0001" or size != SIZE or not 1 <= count <= 31:
                    raise ValueError("Invalid retained guided delta")
                end = 0
                for _ in range(count):
                    offset, size, before, after = struct.unpack("<QIII", delta.read(20))
                    if offset < end or offset % BLOCK or size != BLOCK or offset > SIZE - size:
                        raise ValueError("Invalid retained guided delta range")
                    data = delta.read(BLOCK)
                    disk.seek(offset)
                    if len(data) != BLOCK or zlib.crc32(data) != after or zlib.crc32(disk.read(BLOCK)) != before:
                        raise ValueError("Retained guided delta block failed verification")
                    disk.seek(offset)
                    disk.write(data)
                    end = offset + BLOCK
                if delta.read(1):
                    raise ValueError("Unexpected retained guided delta data")
            disk.seek(0)
            if hashlib.file_digest(disk, "sha256").hexdigest() != template_sha:
                raise ValueError("Retained guided template digest differs")
        else:
            with args.guided_wim.open("rb") as wim:
                guided_sha = hashlib.file_digest(wim, "sha256").hexdigest()
                with PyFatBytesIOFS(disk, offset=BLOCK) as fat:
                    replace_wim(disk, fat, wim, args.guided_wim.stat().st_size)
        if guided_sha == WIM_SHA:
            raise ValueError("Expected a guided WinPE WIM")
        with PyFatBytesIOFS(disk, offset=BLOCK) as fat:
            with fat.openbin("/SOURCES/BOOT.WIM") as actual:
                assert hashlib.file_digest(actual, "sha256").hexdigest() == guided_sha
            if args.smp_efi:
                loader = args.smp_efi.read_bytes()
                if hashlib.sha256(loader).hexdigest() != SMP_EFI_SHA:
                    raise ValueError("Unexpected Windows SMP EFI")
                path = "/EFI/BOOT/BOOTAA64.EFI"
                with fat.openbin(path) as original:
                    if hashlib.file_digest(original, "sha256").hexdigest() != BOOT_EFI_SHA:
                        raise ValueError("Unexpected base Windows EFI")
                with fat.openbin(path, "wb") as target:
                    target.write(loader)
                with fat.openbin(path) as actual:
                    if actual.read() != loader:
                        raise ValueError("SMP EFI readback differs")
        disk.seek(0)
        final_sha = hashlib.file_digest(disk, "sha256").hexdigest()
        records = []
        for index, data in sorted(disk.blocks.items()):
            source.seek(index * BLOCK)
            old = source.read(BLOCK)
            if old != data:
                records.append((index * BLOCK, zlib.crc32(old), zlib.crc32(data), data))
        assert records
        with (args.output / "guided.delta").open("xb") as delta:
            delta.write(struct.pack("<8sQI", b"UWDP0001", SIZE, len(records)))
            for offset, before, after, data in records:
                delta.write(struct.pack("<QIII", offset, len(data), before, after))
                delta.write(data)
        (args.output / "guided-profile.txt").write_bytes(f"{final_sha.upper()}\n{guided_sha.upper()}\n".encode("ascii"))
        print(f"GUIDED_WINPE_DELTA_PASS blocks={len(records)} template={final_sha} wim={guided_sha}")


def replace_wim(disk, fat, wim, size):
    with fat.openbin("/SOURCES/BOOT.WIM") as old:
        assert hashlib.file_digest(old, "sha256").hexdigest() == WIM_SHA
    entry = fat.fs.root_dir.get_entry("/SOURCES/BOOT.WIM")
    chain = list(fat.fs.get_cluster_chain(entry.get_cluster()))
    cluster_size = fat.fs.bytes_per_cluster
    wim.seek(0)
    for cluster in chain:
        disk.seek(BLOCK + fat.fs.get_data_cluster_address(cluster))
        original = disk.read(cluster_size)
        updated = wim.read(cluster_size)
        if not updated:
            raise ValueError("Guided WIM shrank below the original cluster count")
        if updated != original[:len(updated)]:
            fat.fs.write_data_to_cluster(updated.ljust(cluster_size, b"\0"), cluster, extend_cluster=False)
    if remaining := wim.read():
        extra = (len(remaining) + cluster_size - 1) // cluster_size
        assert len(remaining) <= 2 * BLOCK
        new_chain = fat.fs.allocate_bytes(len(remaining))
        assert len(new_chain) == extra
        fat.fs.fat[chain[-1]] = new_chain[0]
        fat.fs.flush_fat()
        for index, cluster in enumerate(new_chain):
            part = remaining[index*cluster_size:(index+1)*cluster_size]
            fat.fs.write_data_to_cluster(part.ljust(cluster_size, b"\0"), cluster, extend_cluster=False)
    entry.filesize = size
    fat.fs.update_directory_entry(entry.get_parent_dir())


if __name__ == "__main__":
    main()
