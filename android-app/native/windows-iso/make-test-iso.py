"""Create small UDF fixtures without any Windows content."""
import argparse
import io
from pathlib import Path
import struct

import pycdlib


def make_iso(path, machine, install_kind, valid_boot, real_wim=None):
    iso = pycdlib.PyCdlib()
    iso.new(udf='2.60')
    for name in ('/EfI', '/EfI/Boot', '/Sources'):
        iso.add_directory(udf_path=name)
    pe = bytearray(512)
    pe[:2] = b'MZ'
    struct.pack_into('<I', pe, 60, 128)
    pe[128:132] = b'PE\0\0'
    struct.pack_into('<H', pe, 132, machine)
    iso.add_fp(io.BytesIO(pe), len(pe), udf_path='/EfI/Boot/BOOTAA64.EFI')
    wim = real_wim
    if wim is None:
        wim = bytearray(512)
        wim[:8] = b'MSWIM\0\0\0'
        struct.pack_into('<I', wim, 8, 208)
        struct.pack_into('<II', wim, 40, 0x10001, 1)
    boot = wim if valid_boot else bytes(len(wim))
    iso.add_fp(io.BytesIO(boot), len(boot), udf_path='/Sources/Boot.Wim')
    if install_kind:
        iso.add_fp(io.BytesIO(wim), len(wim), udf_path='/Sources/INSTALL.' + install_kind)
    iso.write(str(path))
    iso.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('output', type=Path)
    parser.add_argument('--real-wim', type=Path, help='Add a small actual WIM fixture to the UDF tests')
    args = parser.parse_args()
    output = args.output
    output.mkdir()
    make_iso(output / 'arm64-wim.iso', 0xaa64, 'WIM', True)
    make_iso(output / 'arm64-esd.iso', 0xaa64, 'ESD', True)
    make_iso(output / 'x64-efi.iso', 0x8664, 'WIM', True)
    make_iso(output / 'corrupt-boot.iso', 0xaa64, 'WIM', False)
    make_iso(output / 'missing-install.iso', 0xaa64, None, True)
    if args.real_wim:
        if args.real_wim.stat().st_size > 16 * 1024 ** 2:
            parser.error('Use a small WIM fixture')
        make_iso(output / 'random-access.iso', 0xaa64, 'WIM', True, args.real_wim.read_bytes())
