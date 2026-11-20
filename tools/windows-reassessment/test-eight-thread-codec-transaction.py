"""Exercise actual separate-codec verifier/transaction; never writes a device."""
import argparse
import copy
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location('codec_apply',
    Path(__file__).with_name('apply-eight-thread-codec.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
parser = argparse.ArgumentParser()
parser.add_argument('--prepared', type=Path, required=True)
parser.add_argument('--codec', choices=('openh264', 'x264'), default='openh264')
parser.add_argument('--native-gop',action='store_true')
args = parser.parse_args()
m.select_codec(args.codec,args.native_gop)
report, blocks = m.read_plan(args.prepared)
records = {b[0]: (b[1], b[2]) for b in blocks}
checks = 1
assert len(blocks) == 112 and len(blocks[-1][1]) == 512
assert blocks[-1][0] == m.DIRECTORY // 512 * 512


def rejected(r=report, rec=records):
    global checks
    try:
        m.verified_blocks(r, rec)
    except (AssertionError, KeyError):
        checks += 1
    else:
        raise AssertionError('Invalid codec block write admitted')


for field, value in [('device_writes', 1), ('candidate_bytes', 800856),
        ('directory_entry', m.DIRECTORY + 32), ('name', 'openh264.dll'),
        ('excluded_gop_cluster', 0)]:
    bad = copy.deepcopy(report)
    bad[field] = value
    rejected(r=bad)
bad = copy.deepcopy(report)
bad['clusters'][0] = m.p.GOP_RESERVED
rejected(r=bad)
for kind in ('data', 'fat', 'directory', 'fsinfo-unknown-hints'):
    item = next(i for i in report['blocks'] if i['kind'] == kind)
    offset = item['offset']
    old, new = records[offset]
    changed = bytearray(new)
    changed[-1] ^= 1
    rec = records.copy()
    rec[offset] = (old, bytes(changed))
    bad = copy.deepcopy(report)
    next(i for i in bad['blocks'] if i['offset'] == offset)['new_sha256'] = m.p.digest(changed)
    rejected(r=bad, rec=rec)
bad = copy.deepcopy(report)
bad['blocks'][1] = copy.deepcopy(bad['blocks'][0])
rejected(r=bad)

for rollback in (False, True):
    initial, target = (2, 1) if rollback else (1, 2)
    for mode in ('raise-after-prefix', 'silent-partial-write'):
        for fail_at in range(1, len(blocks) + 1):
            disk = {b[0]: b[initial] for b in blocks}
            snapshot = disk.copy()
            state = dict(writes=0, failed=False, cold=0)

            def read(offset, size):
                assert len(disk[offset]) == size
                return disk[offset]

            def write(offset, data):
                assert offset in disk and len(data) == len(disk[offset])
                state['writes'] += 1
                if not state['failed'] and state['writes'] == fail_at:
                    state['failed'] = True
                    # Ensure the simulated interrupted write corrupts a byte
                    # even when leading unchanged bytes would hide it.
                    partial = bytearray(disk[offset])
                    for i in range(min(128, len(data))):
                        partial[i] = data[i]
                    partial[-1] ^= 1
                    disk[offset] = bytes(partial)
                    if mode == 'raise-after-prefix':
                        raise OSError('Simulated interrupted write')
                    return
                disk[offset] = data

            def cold():
                state['cold'] += 1

            try:
                m.transaction.transact(blocks, read, write, cold, lambda: None, rollback)
            except (OSError, AssertionError):
                assert state['failed'] and disk == snapshot, 'Byte-exact recovery required'
            else:
                raise AssertionError('Partial write was accepted')
            assert state['cold'] >= 2
            checks += 1

    # Successful round trip and validation failure after final commit.
    for final_fail in (False, True):
        disk = {b[0]: b[initial] for b in blocks}
        snapshot = disk.copy()

        def finish():
            assert all(disk[b[0]] == b[target] for b in blocks)
            if final_fail:
                raise ValueError('Simulated post-commit verification failure')

        try:
            m.transaction.transact(blocks, lambda o, s: disk[o],
                lambda o, d: disk.__setitem__(o, d), lambda: None, finish, rollback)
        except ValueError:
            assert final_fail and disk == snapshot
        else:
            assert not final_fail and all(disk[b[0]] == b[target] for b in blocks)
        checks += 1

disk = {b[0]: b[1] for b in blocks}
disk[blocks[-1][0]] = bytes(512)
writes = []
try:
    m.transaction.transact(blocks, lambda o, s: disk[o],
        lambda o, d: writes.append(o), lambda: None, lambda: None)
except AssertionError:
    assert not writes
    checks += 1
else:
    raise AssertionError('Stale directory was accepted')
print(json.dumps(dict(result='ACTUAL_TYPED_CODEC_TRANSACTION_TESTS_PASS',
    checks=checks, planned_blocks=len(blocks), device_writes=0,
    scope='Every apply/rollback write: raised and silent partial failure; final validation failure; malformed plans; stale preimage')))
