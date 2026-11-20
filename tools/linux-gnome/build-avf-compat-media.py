"""Build a disposable compatibility boot image from verified U-AVF assets."""

import argparse
import hashlib
import importlib.util
from pathlib import Path
import shutil
import tempfile
from zipfile import ZipFile

from pyfatfs.PyFat import PyFat
from pyfatfs.PyFatFS import PyFatFS

TOOLS = Path(__file__).resolve().parent.parent / 'generic-ubuntu'
BASE_NAME = 'p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img'
BASE_HASH = 'FE451CA13834DC5EDE3096EDC3AF1EF2894BFDECAA7801CED9E69EDB07447858'
BOOT_FILES = ('/CASPER/VMLINUZ', '/EFI/BOOT/BOOTAA64.EFI', '/EFI/EDK2/QEMU_EFI.fd')
MODULES = ('vsock.ko', 'vmw_vsock_virtio_transport_common.ko', 'vmw_vsock_virtio_transport.ko')


def load_tool(name, file):
    spec = importlib.util.spec_from_file_location(name, TOOLS / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def build(package, init, modules, output):
    platform = load_tool('platform', 'stage-generic-ubuntu.py')
    cpio = load_tool('cpio', 'repack-p7-frame-bridge.py')
    if (platform.IMAGE_SIZE != 128 * 1024 * 1024
            or platform.START_LBA * platform.SECTOR_SIZE + platform.FAT_SIZE != 127 * 1024 * 1024
            or 127 * 1024 * 1024 + 8192 >= platform.IMAGE_SIZE - 33 * platform.SECTOR_SIZE):
        raise ValueError('Probe storage region is outside the expected free sectors')
    binary = init.read_bytes()
    if (binary[:6] != b'\x7fELF\x02\x01' or binary[18:20] != b'\xb7\x00'
            or len(binary) > 2 * 1024 * 1024):
        raise ValueError('Expected a small little-endian ARM64 ELF init')
    archive = cpio.directory_record(b'dev') + cpio.directory_record(b'modules') + cpio.file_record(b'init', binary)
    for name in MODULES:
        module = (modules / name).read_bytes()
        if (module[:6] != b'\x7fELF\x02\x01' or module[18:20] != b'\xb7\x00'
                or b'vermagic=7.0.0-31-generic ' not in module):
            raise ValueError(f'Unexpected guest kernel module ABI: {name}')
        archive += cpio.file_record(('modules/' + name).encode('ascii'), module)
    archive += cpio.file_record(b'TRAILER!!!', b'')
    archive += bytes(-len(archive) % 512)
    with tempfile.TemporaryDirectory(prefix='uavf-compat-') as temp:
        base = Path(temp) / 'source.img'
        fat_path = Path(temp) / 'compat.fat'
        with ZipFile(package) as apk, apk.open('assets/' + BASE_NAME) as original, base.open('xb') as target:
            shutil.copyfileobj(original, target)
        if platform.sha256(base) != BASE_HASH:
            raise ValueError('The known-booting prefix failed verification')
        fat_path.touch()
        fat = PyFat()
        fat.mkfs(str(fat_path), fat_type=PyFat.FAT_TYPE_FAT32,
                 size=platform.FAT_SIZE, label='UAVFTEST')
        fat.close()
        hashes = {}
        with PyFatFS(str(base), offset=1048576, read_only=True) as source, PyFatFS(str(fat_path)) as destination:
            for folder in ('/EFI/BOOT', '/EFI/EDK2', '/CASPER'):
                destination.makedirs(folder)
            for name in BOOT_FILES:
                content = source.readbytes(name)
                hashes[name] = hashlib.sha256(content).hexdigest()
                destination.writebytes(name, content)
            destination.writebytes('/CASPER/INITRD', archive)
        mbr, first, last, entries = platform.gpt()
        with output.open('xb') as raw, fat_path.open('rb') as volume:
            raw.truncate(platform.IMAGE_SIZE)
            raw.write(mbr)
            raw.write(first)
            raw.write(entries)
            raw.seek(platform.START_LBA * platform.SECTOR_SIZE)
            shutil.copyfileobj(volume, raw)
            raw.seek(platform.IMAGE_SIZE - 33 * platform.SECTOR_SIZE)
            raw.write(entries)
            raw.write(last)
        with PyFatFS(str(output), offset=1048576, read_only=True) as verify:
            for name, digest in hashes.items():
                if hashlib.sha256(verify.readbytes(name)).hexdigest() != digest:
                    raise ValueError(f'Boot component changed: {name}')
            if verify.readbytes('/CASPER/INITRD') != archive:
                raise ValueError('Probe initrd failed readback')
    print(f'AVF_COMPAT_MEDIA_SHA256={platform.sha256(output)}')
    print(f'AVF_COMPAT_INITRD_BYTES={len(archive)}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-apk', type=Path, required=True)
    parser.add_argument('--init', type=Path, required=True)
    parser.add_argument('--modules', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build(args.base_apk, args.init, args.modules, args.output)
