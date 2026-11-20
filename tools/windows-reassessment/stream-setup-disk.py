"""Assemble the proven Windows Setup media and an independent sparse install target."""
import argparse
from dataclasses import dataclass
import hashlib
import importlib.util
import io
from itertools import islice
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile
import zlib

from pyfatfs.FatIO import FatIO
from pyfatfs.PyFat import PyFat

SECTOR = 512
ESP = 1024 * 1024
MEDIA_BYTES = 9126805504
GIB = 1024 ** 3
RESERVE = 50 * GIB
CHUNK = 4 * 1024 * 1024
SOURCE_SHA = 'DDC0E4C2EF2E155D700736A7808AABF5999D689787724612615F537F7140363C'
SETUP_SHA = '03E3DB8CBD9B3B7B16DD0AE22C356DDB68E9B4B5847FDD305A7E2692DABB8EE5'
PARTS = {
    'install.swm': (3086496461, '6A843CDBACB13C5A1579E8ABE8BDB946957A0A342D78E177F27C2B19FD4A452A'),
    'install2.swm': (3237422003, '7FC03F45A32A8F19BB99F4EB70CCE0BE214311AF795D26ECFE356781C10A727F'),
}
spec = importlib.util.spec_from_file_location('geometry', Path(__file__).with_name('prepare-install-geometry.py'))
geometry = importlib.util.module_from_spec(spec)
spec.loader.exec_module(geometry)


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


class MetadataOverlay(io.RawIOBase):
    """Seekable read-through view. FAT metadata writes never reach the base image."""

    def __init__(self, base):
        self.base = base
        self.writes = {}

    def readable(self):
        return True

    def writable(self):
        return True

    def seekable(self):
        return True

    def seek(self, offset, whence=0):
        return self.base.seek(offset, whence)

    def read(self, size):
        start = self.base.tell()
        result = bytearray(self.base.read(size))
        for offset, data in self.writes.items():
            first = max(offset, start)
            last = min(offset + len(data), start + len(result))
            if first < last:
                result[first - start:last - start] = data[first - offset:last - offset]
        return bytes(result)

    def write(self, data):
        offset = self.base.tell()
        require(ESP <= offset and offset + len(data) <= MEDIA_BYTES, 'FAT write escapes the source partition')
        for old, previous in list(self.writes.items()):
            require(old == offset and len(previous) == len(data) or
                    old + len(previous) <= offset or offset + len(data) <= old,
                    'Unexpected overlapping FAT metadata writes')
        self.writes[offset] = bytes(data)
        self.base.seek(len(data), os.SEEK_CUR)
        return len(data)


@dataclass(frozen=True)
class Span:
    offset: int
    size: int
    data: bytes | Path
    start: int = 0


def digest(path, expected_size, expected_sha):
    with path.open('rb') as source:
        require(os.fstat(source.fileno()).st_size == expected_size, f'Unexpected size: {path.name}')
        actual = hashlib.file_digest(source, 'sha256').hexdigest().upper()
    require(actual == expected_sha, f'Unexpected SHA-256: {path.name}')


def plan_setup(base, payload):
    spans = []
    with base.open('rb') as stream:
        overlay = MetadataOverlay(stream)
        fat = PyFat(offset=ESP)
        fat.set_fp(overlay)
        require(fat.fat_type == 32, 'Expected FAT32 source')
        for name, (size, _) in PARTS.items():
            path = '/SOURCES/' + name.upper()
            with FatIO(fat, path) as placeholder:
                require(placeholder.read(48) == bytes(48), f'{name}: expected original placeholder')
            entry = fat.root_dir.get_entry(path)
            chain = list(fat.get_cluster_chain(entry.get_cluster()))
            cluster_size = fat.bytes_per_cluster
            needed = (size + cluster_size - 1) // cluster_size
            extra = max(0, needed - len(chain))
            if extra:
                free = list(islice((i for i, value in enumerate(fat.fat) if i >= 2 and value == 0), extra))
                require(len(free) == extra, 'Insufficient free FAT32 clusters')
                chain.extend(free)
            for current, following in zip(chain, chain[1:]):
                fat.fat[current] = following
            fat.fat[chain[-1]] = 0x0fffffff
            fat.flush_fat()
            entry.filesize = size
            fat.update_directory_entry(entry.get_parent_dir())

            # Coalesce physically adjacent clusters without copying the SWM.
            source = payload / name
            first = None
            for index, cluster in enumerate(chain[:needed]):
                address = ESP + fat.get_data_cluster_address(cluster)
                if first is None:
                    first, source_start, length = address, index * cluster_size, cluster_size
                elif address == first + length:
                    length += cluster_size
                else:
                    spans.append(Span(first, length, source, source_start))
                    first, source_start, length = address, index * cluster_size, cluster_size
            spans.append(Span(first, length, source, source_start))
        fat.close()
        spans.extend(Span(offset, len(data), data) for offset, data in overlay.writes.items())
    spans.sort(key=lambda span: span.offset)
    end = 0
    for span in spans:
        require(end <= span.offset and span.offset + span.size <= MEDIA_BYTES, 'Media patch overlaps or escapes source')
        end = span.offset + span.size
    return spans


def plan_gpt(base):
    with base.open('rb') as stream:
        mbr, primary, entries = bytearray(stream.read(SECTOR)), stream.read(SECTOR), stream.read(16384)
    addresses, table = geometry.header(primary)
    old_last = MEDIA_BYTES // SECTOR - 1
    require(addresses == (1, old_last, 34, old_last - 33), 'Original GPT geometry differs')
    require(table == (2, 128, 128, zlib.crc32(entries)), 'Original GPT entries differ')
    require(struct.unpack_from('<QQ', entries, 32) == (2048, old_last - 33) and not any(entries[128:]),
            'Original setup partition differs')
    require(mbr[510:] == b'\x55\xaa' and mbr[450] == 0xee and not any(mbr[462:510]), 'Expected protective MBR')
    last = (MEDIA_BYTES + 64 * GIB) // SECTOR - 1
    struct.pack_into('<I', mbr, 458, last)
    return (bytes(mbr) + geometry.updated(primary, 1, last, last - 33, 2),
            entries + geometry.updated(primary, last, 1, last - 33, last - 32))


def stream_setup(base, spans, emit):
    def pipe(source, count, pad=False):
        while count:
            amount = min(CHUNK, count)
            data = source.read(amount)
            if pad:
                data = data.ljust(amount, b'\0')
            require(len(data) == amount, 'Source ended during assembly')
            emit(data)
            count -= amount

    cursor = 0
    with base.open('rb') as original:
        for span in spans:
            original.seek(cursor)
            pipe(original, span.offset - cursor)
            if isinstance(span.data, bytes):
                emit(span.data)
            else:
                with span.data.open('rb') as source:
                    source.seek(span.start)
                    pipe(source, span.size, pad=True)
            cursor = span.offset + span.size
        original.seek(cursor)
        pipe(original, MEDIA_BYTES - cursor)


def enable_sparse(disk):
    if os.name != 'nt':
        return
    import ctypes
    from ctypes import wintypes
    import msvcrt

    ioctl = ctypes.WinDLL('kernel32', use_last_error=True).DeviceIoControl
    ioctl.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPVOID, wintypes.DWORD,
                     wintypes.LPVOID, wintypes.DWORD, ctypes.POINTER(wintypes.DWORD), wintypes.LPVOID]
    ioctl.restype = wintypes.BOOL
    returned = wintypes.DWORD()
    if not ioctl(msvcrt.get_osfhandle(disk.fileno()), 0x000900C4, None, 0, None, 0,
                 ctypes.byref(returned), None):
        raise ctypes.WinError(ctypes.get_last_error())


def build(base, payload, output=None):
    if output is not None:
        require(not output.exists(), 'Refusing to replace an existing disk')
        require(shutil.disk_usage(output.parent).free >= RESERVE + MEDIA_BYTES,
                'Insufficient space for the media and 50 GiB reserve')
    digest(base, MEDIA_BYTES, SOURCE_SHA)
    for name, (size, sha) in PARTS.items():
        digest(payload / name, size, sha)
    spans = plan_setup(base, payload)
    head, tail = plan_gpt(base)
    result = hashlib.sha256()
    new_size = MEDIA_BYTES + 64 * GIB
    if output is None:
        stream_setup(base, spans, result.update)
        require(result.hexdigest().upper() == SETUP_SHA, 'Reconstructed media differs from known Setup')
    else:
        # Hard-link publish is atomic and fails if an existing output appears.
        with tempfile.TemporaryDirectory(prefix='.windows-setup-', dir=output.parent) as directory:
            stage = Path(directory) / 'workspace.raw'
            with stage.open('xb+') as disk:
                enable_sparse(disk)
                def write(data):
                    result.update(data)
                    disk.write(data)
                stream_setup(base, spans, write)
                require(result.hexdigest().upper() == SETUP_SHA, 'Reconstructed media differs from known Setup')
                disk.seek(0)
                disk.write(head)
                disk.seek(new_size - len(tail))
                disk.write(tail)
                disk.flush()
                os.fsync(disk.fileno())
                disk.seek(0)
                require(disk.read(len(head)) == head, 'Primary GPT readback differs')
                disk.seek(new_size - len(tail))
                require(disk.read(len(tail)) == tail, 'Backup GPT readback differs')
            require(shutil.disk_usage(output.parent).free >= RESERVE, 'Storage reserve crossed during assembly')
            os.link(stage, output)
    return dict(mode='write' if output else 'verify', setup_sha256=result.hexdigest().upper(),
                logical_bytes=new_size, fat_spans=len(spans),
                install_first_lba=MEDIA_BYTES // SECTOR, install_last_lba=new_size // SECTOR - 34)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True, type=Path)
    parser.add_argument('--payload', required=True, type=Path)
    parser.add_argument('--output', type=Path, help='Optional new output disk. Omit for read-only verification.')
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.payload, args.output), indent=2))
