"""Exercise actual typed verifier and transaction, including partial writes."""
import copy
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

folder = Path(__file__).parent
spec = importlib.util.spec_from_file_location('ram_gop_transaction', folder/'apply-ram-gop-file.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
parser = argparse.ArgumentParser()
parser.add_argument('--prepared',type=Path,default=folder.parents[2]/'build-logs/windows-post-linux-reassessment-20261004/smp-ram-gop-file-preflight-20261011')
prepared = parser.parse_args().prepared
report = json.loads((prepared/'report.json').read_text())
records = {}
for item in report['blocks']:
    stem = item['labels'][0] if item['labels'][0].startswith('cluster-') else str(item['offset'])
    records[item['offset']] = ((prepared/(stem+'-old.bin')).read_bytes(), (prepared/(stem+'-new.bin')).read_bytes())
original = (prepared/'original-BOOTAA64.EFI').read_bytes()
candidate = (prepared/'candidate-BOOTAA64.EFI').read_bytes()
# Exact previously read geometry, independent of report-controlled offsets.
fat = 5043712-(994732*4//512)*512
mirror = 9500160-(994732*4//512)*512
data = 8158806016-(994732-2)*8192
directory = 29188160


def verify(r=report, rec=records, c=candidate, o=original):
    return m.verified_blocks(r, rec, c, o, 8192, fat, mirror, data, directory)


blocks = verify()
assert [len(b[1]) for b in blocks] == [8192,8192,512,512,512]
checks = 1


def rejected(r=report, rec=records, c=candidate, o=original):
    global checks
    try:
        verify(r, rec, c, o)
    except (AssertionError, KeyError):
        checks += 1
    else:
        raise AssertionError('Malformed own EFI mutation admitted')


rejected(c=candidate[:-1]+bytes([candidate[-1]^1]))
rejected(o=original[:-1]+bytes([original[-1]^1]))
for key, value in [('free_cluster',994732),('directory_entry_offset',directory+32),('cluster_bytes',4096),('device_writes',1)]:
    r = copy.deepcopy(report);r[key] = value;rejected(r=r)
for offset in records:
    rec = records.copy();old, new = rec[offset]
    changed = bytearray(new);changed[-1] ^= 1;rec[offset] = (old, bytes(changed))
    # Even a tampered matching report hash cannot authorize unrelated bytes.
    r = copy.deepcopy(report)
    next(b for b in r['blocks'] if b['offset']==offset)['candidate_sha256'] = hashlib.sha256(changed).hexdigest()
    rejected(r=r, rec=rec)

for rollback in (False, True):
    for fail_at in (None,1,2,3,4,5):
        initial = 2 if rollback else 1
        target = 1 if rollback else 2
        disk = {b[0]:b[initial] for b in blocks}
        expected = disk.copy();state = dict(writes=0, failed=False, cold=0)

        def read(offset, size):
            assert len(disk[offset]) == size
            return disk[offset]

        def write(offset, contents):
            assert offset in disk and len(contents)==len(disk[offset])
            state['writes'] += 1
            if not state['failed'] and fail_at==state['writes']:
                state['failed'] = True
                disk[offset] = contents[:128]+disk[offset][128:]
                raise OSError('Simulated interrupted sector write')
            disk[offset] = contents

        def cold():
            state['cold'] += 1

        def finish():
            assert all(disk[b[0]]==b[target] for b in blocks)

        try:
            m.transact(blocks, read, write, cold, finish, rollback)
        except OSError:
            assert fail_at is not None and disk==expected
        else:
            assert fail_at is None and all(disk[b[0]]==b[target] for b in blocks)
        assert state['cold'] >= 2
        checks += 1

# Stale preimage is rejected before the first write.
bad_disk = {b[0]:b[1] for b in blocks}
bad_disk[blocks[-1][0]] = bytes(512)
writes = []
try:
    m.transact(blocks, lambda offset,size:bad_disk[offset], lambda offset,contents:writes.append(offset), lambda:None, lambda:None)
except AssertionError:
    assert not writes
    checks += 1
else:
    raise AssertionError('Stale metadata allowed')
print(f'ACTUAL_RAM_GOP_TYPED_PLAN_AND_PARTIAL_WRITE_RECOVERY_CHECKS={checks} PASS; NO_DEVICE_WRITES')
