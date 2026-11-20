"""Bounded sparse transfer to a NEW diagnostic private file; no disk activation.

Working windows-diagnostic.img is never opened for writing or renamed here.
64MiB sync staging, same-UID sparse writes, SHA256 readback for each extent.
"""
import argparse
import ctypes
import hashlib
import json
import msvcrt
import struct
import subprocess
from pathlib import Path

FOLDER = Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
SOURCE = FOLDER / 'windows-idd-test.img'
ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
REMOTE = '/sdcard/Android/data/com.example.uavfwinpetest/files/idd-transfer-chunk.bin'
TARGET = 'files/windows-idd-disposable-20261013.img'
LIMIT = 64 * 1024**2
SIZE = 77846282240


def adb(*args):
    return subprocess.check_output([str(ADB), *args], text=True).strip()


def ranges():
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    call = kernel.DeviceIoControl
    call.argtypes = [ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32,
                     ctypes.c_void_p, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_void_p]
    returned = ctypes.c_uint32()
    buf = ctypes.create_string_buffer(16 * 1024**2)
    payload = ctypes.create_string_buffer(struct.pack('<qq', 0, SIZE))
    with SOURCE.open('rb') as src:
        assert call(msvcrt.get_osfhandle(src.fileno()), 0x940CF, payload, 16,
                    buf, len(buf), ctypes.byref(returned), None), ctypes.get_last_error()
    result = list(struct.iter_unpack('<qq', buf.raw[:returned.value]))
    assert result and all(o >= 0 and n > 0 and o % 4096 == 0 for o, n in result)
    # The final GPT extent reaches EOF; this disk's EOF is 4KiB aligned.
    result = [(o, min(n, SIZE - o)) for o, n in result]
    assert all(n % 4096 == 0 for o, n in result)
    return result


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--transfer', action='store_true')
    args = parser.parse_args()
    assert json.loads((FOLDER / 'assembly.json').read_text())['root_readback'] == 'PASS'
    assert json.loads((FOLDER / 'staging.json').read_text())['device_writes'] == 0
    assert SOURCE.stat().st_size == SIZE
    assert 'Running VMs: []' in adb('shell', '/apex/com.android.virt/bin/vm', 'list')
    extents = ranges(); total = sum(n for _, n in extents)
    free = int(adb('shell', 'df', '-k', '/data').splitlines()[-1].split()[3]) * 1024
    required = 52 * 1024**3 + total + LIMIT
    print(json.dumps(dict(allocated_transfer_bytes=total, tablet_free=free,
                          required_free=required, reserve_pass=free >= required)), flush=True)
    if not args.transfer:
        return
    assert free >= required, 'Preserve 52GiB launch reserve plus bounded staging'
    assert adb('shell', 'run-as', 'com.example.uavfwinpetest', 'sh', '-c',
               f"'test ! -e {TARGET} && echo NEW_TARGET_ONLY'") == 'NEW_TARGET_ONLY'
    assert not (FOLDER / 'transfer.json').exists(), 'No overwrite/repeated transfer'
    adb('shell', 'run-as', 'com.example.uavfwinpetest', 'truncate', '-s', str(SIZE), TARGET)
    pending = list(extents)
    local = FOLDER / 'transfer-chunk.bin'
    journal = []; copied = 0
    with SOURCE.open('rb') as src:
        while pending:
            pieces = []; packed = 0
            with local.open('wb') as chunk:
                while pending and packed < LIMIT and len(pieces) < 80:
                    offset, left = pending.pop(0)
                    length = min(left, LIMIT - packed)
                    src.seek(offset); data = src.read(length); assert len(data) == length
                    chunk.write(data)
                    pieces.append((offset, length, packed, hashlib.sha256(data).hexdigest()))
                    packed += length
                    if length < left:
                        pending.insert(0, (offset + length, left - length))
            subprocess.run([str(ADB), 'push', str(local), REMOTE], check=True,
                           stdout=subprocess.DEVNULL)
            command = ' && '.join(
                f'dd if={REMOTE} of={TARGET} bs=4096 skip={start//4096} seek={offset//4096} count={length//4096} conv=notrunc 2>/dev/null'
                for offset, length, start, _ in pieces)
            adb('shell', f"run-as com.example.uavfwinpetest sh -c '{command}'")
            # Compare actual newly written disk ranges, not merely pushed file.
            command = ' ; '.join(
                f'dd if={TARGET} bs=4096 skip={offset//4096} count={length//4096} 2>/dev/null | sha256sum'
                for offset, length, _, _ in pieces)
            actual = adb('shell', f"run-as com.example.uavfwinpetest sh -c '{command}'").splitlines()
            assert len(actual) == len(pieces)
            assert all(line.split()[0] == piece[3] for line, piece in zip(actual, pieces)), 'Private-file extent readback'
            journal.extend(dict(offset=o, length=n, sha256=h) for o, n, _, h in pieces)
            copied += packed
            (FOLDER / 'transfer-progress.json').write_text(json.dumps(dict(
                target=TARGET, verified_bytes=copied, total_bytes=total, extents=journal)))
            print(f'PRIVATE_TEST_VERIFIED_BYTES={copied}/{total}', flush=True)
    assert copied == total
    adb('shell', 'rm', '-f', REMOTE)
    local.unlink()
    assert int(adb('shell', 'run-as', 'com.example.uavfwinpetest', 'stat', '-c', '%s', TARGET)) == SIZE
    free = int(adb('shell', 'df', '-k', '/data').splitlines()[-1].split()[3]) * 1024
    assert free >= 52 * 1024**3
    (FOLDER / 'transfer.json').write_text(json.dumps(dict(result='READBACK_PASS_NOT_BOOT_OR_FPS',
        target=TARGET, verified_bytes=copied, tablet_free=free, working_disk_writes=0), indent=2))
    print('PRIVATE_DISPOSABLE_TRANSFER_READBACK_PASS; NOT_ACTIVE_OR_BOOT_OR_FPS_PASS')


if __name__ == '__main__':
    main()
