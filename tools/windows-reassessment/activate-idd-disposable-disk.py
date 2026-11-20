"""Cold-only name transaction; preserves working disk inode under protected name.

No payload copy, formatting, firmware or data writes. Explicit restore verifies
the protected original identity before putting its original filename back.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
FOLDER = Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
PACKAGE = 'com.example.uavfwinpetest'
WORKING = 'files/windows-diagnostic.img'
TEST = 'files/windows-idd-disposable-20261013.img'
PROTECTED = 'files/windows-protected-before-idd-20261013.img'
JOURNAL = FOLDER / 'activation.json'


def adb(*args):
    return subprocess.check_output([str(ADB), *args], text=True).strip()


def cold():
    assert 'Running VMs: []' in adb('shell', '/apex/com.android.virt/bin/vm', 'list')


def identity(name):
    metadata = adb('shell', 'run-as', PACKAGE, 'stat', '-c', '%i:%s:%Y', name)
    data = subprocess.check_output([str(ADB), 'exec-out',
        f'run-as {PACKAGE} dd if={name} bs=512 skip=17858560 count=1 2>/dev/null'])
    assert len(data) == 512 and data[3:11] == b'NTFS    '
    return dict(stat=metadata, boot_sha256=hashlib.sha256(data).hexdigest(),
                serial_hex=data[72:80].hex())


def move(old, new):
    cold()
    adb('shell', f"run-as {PACKAGE} sh -c 'test -f {old} && test ! -e {new} && mv {old} {new}'")


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('action', choices=('activate', 'restore'))
    args = parser.parse_args(); cold()
    if args.action == 'activate':
        prior = json.loads(JOURNAL.read_text()) if JOURNAL.exists() else None
        if prior:
            assert prior['status'] == 'ORIGINAL_RESTORED_IDENTITY_PASS', 'Prior transaction must be restored'
            archive = FOLDER / ('activation-round-' + str(prior.get('round', 1)) + '.json')
            assert not archive.exists()
            archive.write_text(json.dumps(prior, indent=2))
        transfer = json.loads((FOLDER / 'transfer.json').read_text())
        assert transfer['result'] == 'READBACK_PASS_NOT_BOOT_OR_FPS'
        assert transfer['working_disk_writes'] == 0
        original, test = identity(WORKING), identity(TEST)
        if prior:
            assert original == prior['original'], 'Same protected working system only'
        assert original['serial_hex'] != test['serial_hex'], 'Distinct fresh filesystem'
        assert original['serial_hex'].startswith('b769f63e'), 'Protected known working root'
        state = dict(original=original, test=test, status='PREPARED_NO_RENAME',
                     round=prior.get('round', 1) + 1 if prior else 1)
        JOURNAL.write_text(json.dumps(state, indent=2))
        move(WORKING, PROTECTED)
        assert identity(PROTECTED) == original
        state['status'] = 'ORIGINAL_PROTECTED'; JOURNAL.write_text(json.dumps(state, indent=2))
        try:
            move(TEST, WORKING)
            assert identity(WORKING) == test
        except BaseException:
            # Restore original only if failed second rename left the slot empty.
            if adb('shell', f"run-as {PACKAGE} sh -c 'test ! -e {WORKING} && echo EMPTY || echo OCCUPIED'") == 'EMPTY':
                move(PROTECTED, WORKING)
            raise
        state['status'] = 'TEST_ACTIVE_ORIGINAL_PROTECTED'
    else:
        state = json.loads(JOURNAL.read_text())
        assert state['status'] == 'TEST_ACTIVE_ORIGINAL_PROTECTED'
        assert identity(PROTECTED) == state['original'], 'Protected original changed; do not overwrite'
        move(WORKING, TEST)
        try:
            move(PROTECTED, WORKING)
            assert identity(WORKING) == state['original']
        except BaseException:
            if adb('shell', f"run-as {PACKAGE} sh -c 'test ! -e {WORKING} && echo EMPTY || echo OCCUPIED'") == 'EMPTY':
                move(TEST, WORKING)
            raise
        state['status'] = 'ORIGINAL_RESTORED_IDENTITY_PASS'
    JOURNAL.write_text(json.dumps(state, indent=2))
    print(state['status'] + '; NAME_TRANSACTION_ONLY; NOT_BOOT_OR_FPS_PASS')


if __name__ == '__main__':
    main()
