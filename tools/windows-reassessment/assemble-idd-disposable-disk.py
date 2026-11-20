"""Create a NEW sparse IddCx test disk. Never writes a device/current VM disk.

Copies the frozen WinPE carrier and allocated clusters of the fresh NTFS root.
Reads only GPT metadata from the cold diagnostic disk. Removing cached SWMs
affects this new copy only; official ISO and working Windows remain unchanged.
"""
import argparse
import ctypes
import hashlib
import json
import msvcrt
import shutil
import struct
import subprocess
import time
import zlib
from pathlib import Path

from dissect.ntfs import NTFS
from pyfatfs.PyFatFS import PyFatFS

WORKSPACE = Path(__file__).resolve().parents[3]
SOURCE = WORKSPACE / 'build-logs/windows-post-linux-reassessment-20261004/windows-display-live-name-fix-20261008.img'
ROOT = Path('E:/U-AVF/artifacts/idd-fresh-root-native-20261013/root.ntfs')
OUT = Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
SIZE = 77846282240
START = 17858560 * 512
ROOT_SIZE = 68157440000
SOURCE_SHA = 'A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A'
EFI_SHA = '893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B'


def device_read(sector, count):
    assert count <= 34
    command = ('run-as com.example.uavfwinpetest dd '
               f'if=files/windows-diagnostic.img bs=512 skip={sector} count={count} 2>/dev/null')
    data = subprocess.check_output([str(ADB), 'exec-out', command])
    assert len(data) == count * 512
    return data


def validate_gpt(primary, backup):
    for block, expected_lba in ((primary[512:1024], 1), (backup[-512:], 152043519)):
        assert block[:8] == b'EFI PART'
        assert struct.unpack_from('<I', block, 12)[0] == 92
        check = bytearray(block[:92]); check[16:20] = bytes(4)
        assert zlib.crc32(check) == struct.unpack_from('<I', block, 16)[0]
        assert struct.unpack_from('<Q', block, 24)[0] == expected_lba
    entries = primary[1024:17408]
    assert entries == backup[:16384]
    assert zlib.crc32(entries) == struct.unpack_from('<I', primary, 512 + 88)[0]
    assert struct.unpack_from('<QQ', entries, 32) == (2048, 17825758)
    assert struct.unpack_from('<QQ', entries, 128 + 32) == (17825792, 17858559)
    assert struct.unpack_from('<QQ', entries, 256 + 32) == (17858560, 150978559)
    assert entries[384:] == bytes(len(entries) - 384)


def runs(bitmap, clusters):
    start = None
    for i in range(clusters):
        allocated = bool(bitmap[i // 8] & (1 << (i % 8)))
        if allocated and start is None:
            start = i
        elif not allocated and start is not None:
            yield start, i - start
            start = None
    if start is not None:
        yield start, clusters - start


def ioctl(stream, code, payload=b''):
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    call = kernel.DeviceIoControl
    call.argtypes = [ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p,
                     ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32,
                     ctypes.POINTER(ctypes.c_uint32), ctypes.c_void_p]
    call.restype = ctypes.c_int
    buf = ctypes.create_string_buffer(payload) if payload else None
    returned = ctypes.c_uint32()
    if not call(msvcrt.get_osfhandle(stream.fileno()), code, buf, len(payload),
                None, 0, ctypes.byref(returned), None):
        raise ctypes.WinError(ctypes.get_last_error())


def sparse_length(stream, size):
    # CRT _chsize_s/Python truncate explicitly zero-fills even a sparse file.
    # Native SetEndOfFile extends sparse logical length without that full write.
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    move = kernel.SetFilePointerEx
    move.argtypes = [ctypes.c_void_p, ctypes.c_longlong, ctypes.c_void_p, ctypes.c_uint32]
    move.restype = ctypes.c_int
    end = kernel.SetEndOfFile
    end.argtypes = [ctypes.c_void_p]; end.restype = ctypes.c_int
    handle = msvcrt.get_osfhandle(stream.fileno())
    if not move(handle, size, None, 0) or not end(handle):
        raise ctypes.WinError(ctypes.get_last_error())
    stream.seek(0)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--resume-carrier', action='store_true')
    args = parser.parse_args()
    state = subprocess.check_output([str(ADB), 'shell', '/apex/com.android.virt/bin/vm', 'list'], text=True)
    assert 'Running VMs: []' in state, 'Cold metadata only'
    assert ROOT.stat().st_size == ROOT_SIZE
    assert SOURCE.stat().st_size == 9126805504
    primary = device_read(0, 34)
    backup = device_read(152043487, 33)
    validate_gpt(primary, backup)
    with ROOT.open('rb') as root:
        boot = root.read(512)
        assert boot[3:11] == b'NTFS    ' and boot[-2:] == b'\x55\xaa'
        cluster_size = struct.unpack_from('<H', boot, 11)[0] * boot[13]
        assert cluster_size == 4096
        clusters = (struct.unpack_from('<Q', boot, 40)[0] + 1) // boot[13]
        ntfs = NTFS(root)
        bitmap = ntfs.mft.get('$Bitmap').open().read()
        assert len(bitmap) * 8 >= clusters
        assert ntfs.mft.get('Windows/System32/ntoskrnl.exe').size() == 11438496
        extents = list(runs(bitmap, clusters))
    allocated = sum(length * cluster_size for _, length in extents)
    assert 12 * 1024**3 < allocated < 16 * 1024**3
    print(json.dumps(dict(preflight='PASS', allocated_root_bytes=allocated,
                          root_runs=len(extents), build_requested=args.build)), flush=True)
    if not args.build:
        return
    assert not OUT.exists() or args.resume_carrier, 'New output only'
    assert shutil.disk_usage('E:/').free >= 50 * 1024**3 + allocated + SOURCE.stat().st_size
    OUT.mkdir(exist_ok=args.resume_carrier)
    target = OUT / 'windows-idd-test.img'
    last = time.monotonic()
    if args.resume_carrier:
        assert not (OUT / 'assembly.json').exists(), 'Never overwrite completed test disk'
        assert target.stat().st_size == SIZE
        with target.open('rb') as dst:
            assert dst.read(17408) == primary
            dst.seek(152043487 * 512); assert dst.read(16896) == backup
            dst.seek(START); assert dst.read(8 * 1024**2) == bytes(8 * 1024**2), 'Root must be unwritten'
        with PyFatFS(str(target), offset=1048576, read_only=True) as fs:
            assert hashlib.sha256(fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')).hexdigest().upper() == EFI_SHA
            with fs.openbin('/SOURCES/BOOT.WIM') as stream:
                assert hashlib.file_digest(stream, 'sha256').hexdigest().upper() == 'A90BA45CC1404CF18FE2F10CE195E565B8F70E45416583F2F25F3EF4391BEF15'
    else:
      with target.open('xb+') as dst, SOURCE.open('rb') as src:
        ioctl(dst, 0x900C4)  # FSCTL_SET_SPARSE, new artifact only
        sparse_length(dst, SIZE)
        digest = hashlib.sha256()
        offset = 0
        while data := src.read(4 * 1024**2):
            digest.update(data)
            if data.count(0) != len(data):
                dst.seek(offset); dst.write(data)
            offset += len(data)
            if time.monotonic() - last > 20:
                print(f'CARRIER_BYTES={offset}', flush=True); last = time.monotonic()
        assert digest.hexdigest().upper() == SOURCE_SHA, 'Frozen source mismatch'
        dst.seek(0); dst.write(primary)
        dst.seek(152043487 * 512); dst.write(backup)
    # Drop only redundant installation WIM parts in the NEW carrier.
    freed = []
    with PyFatFS(str(target), offset=1048576) as fs:
        assert hashlib.sha256(fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')).hexdigest().upper() == EFI_SHA
        for name in ('/SOURCES/INSTALL.SWM', '/SOURCES/INSTALL2.SWM'):
            entry = fs.fs.root_dir.get_entry(name)
            chain = list(fs.fs.get_cluster_chain(entry.get_cluster()))
            for cluster in chain:
                freed.append((fs.fs.get_data_cluster_address(cluster), fs.fs.bytes_per_cluster))
            fs.remove(name)
    with target.open('rb+') as dst:
        # PyFat reports partition-relative data offsets. Deallocate stale payload
        # using documented sparse-file zeroing; no working-disk holes punched.
        merged = []
        for offset, length in sorted(freed):
            offset += 1048576
            if merged and merged[-1][0] + merged[-1][1] == offset:
                merged[-1] = (merged[-1][0], merged[-1][1] + length)
            else:
                merged.append((offset, length))
        for offset, length in merged:
            ioctl(dst, 0x980C8, struct.pack('<qq', offset, offset + length))
        # Fresh-volume free clusters are zero, not copied stale data. Verify
        # exact allocated bytes including all filesystem metadata.
        digest = hashlib.sha256(); copied = 0
        with ROOT.open('rb') as root:
            for start, length in extents:
                offset = start * cluster_size
                root.seek(offset); dst.seek(START + offset)
                left = length * cluster_size
                while left:
                    data = root.read(min(left, 4 * 1024**2))
                    assert data
                    digest.update(data); dst.write(data)
                    copied += len(data); left -= len(data)
                if time.monotonic() - last > 20:
                    print(f'ROOT_ALLOCATED_BYTES={copied}/{allocated}', flush=True)
                    last = time.monotonic()
            # NTFS backup boot sector occupies the final sector outside bitmap.
            root.seek(ROOT_SIZE - 512)
            dst.seek(START + ROOT_SIZE - 512); dst.write(root.read(512))
        dst.flush()
        verify = hashlib.sha256()
        for start, length in extents:
            dst.seek(START + start * cluster_size)
            left = length * cluster_size
            while left:
                data = dst.read(min(left, 4 * 1024**2)); assert data
                verify.update(data); left -= len(data)
        assert verify.digest() == digest.digest(), 'Allocated NTFS readback'
    with PyFatFS(str(target), offset=1048576, read_only=True) as fs:
        assert not fs.exists('/SOURCES/INSTALL.SWM') and not fs.exists('/SOURCES/INSTALL2.SWM')
        assert hashlib.sha256(fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')).hexdigest().upper() == EFI_SHA
        with fs.openbin('/SOURCES/BOOT.WIM') as stream:
            assert hashlib.file_digest(stream, 'sha256').hexdigest().upper() == 'A90BA45CC1404CF18FE2F10CE195E565B8F70E45416583F2F25F3EF4391BEF15'
    report = dict(result='ARTIFACT_ONLY_NOT_BOOT_OR_FPS_PASS', target=str(target),
                  logical_size=SIZE, root_allocated_bytes=allocated,
                  allocated_root_sha256=digest.hexdigest().upper(),
                  root_readback='PASS', carrier_source_sha256=SOURCE_SHA,
                  removed_cached_swm_bytes=sum(x[1] for x in merged),
                  device_writes=0, pending='provision and boot disposable guest')
    (OUT / 'assembly.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report), flush=True)


if __name__ == '__main__':
    main()
