"""Typed cold transaction for one offline-verified Windows RAM-GOP EFI file.

Not a generic block writer: reconstruct and compare every permissible change
from exact binaries and actual FAT geometry. Signed Windows/NTFS/GPT untouched.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
import subprocess
import uuid
from pathlib import Path

ORIGINAL_SHA = '893b7859b6e27598b8392f5ec9a9fb33c948fcaf4ef1918f28072ed5ab402e8b'
CANDIDATE_SHA = '1643e97ca438f6d0edafb504a91f99f2faaf56e2a3d3a10a9e6e377b0742c9a3'
DEVICEPATH_SHA = '7e60950f8d525df05607774227c737e97873ee2ddb3cd80e3d4432bb0e0e1f5f'
HANDOFF_AUDIT_SHA = '359ded6e811f23b011566938b6f3cc8a01df480d5e8225a320fefe357cd98e3a'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verified_blocks(report, records, candidate, original, cluster_bytes, fat, mirror, data, directory):
    """Pure validation: returns only recomputed, semantically justified blocks."""
    assert report['result'] == 'READ_ONLY_OWN_EFI_GROWTH_PLAN_PASS' and report['device_writes'] == 0
    assert cluster_bytes == report['cluster_bytes'] == 8192
    assert len(original) == report['old_bytes'] == 8192 and sha(original) == ORIGINAL_SHA
    assert len(candidate) == report['new_bytes']
    assert (len(candidate),sha(candidate)) in ((10240,CANDIDATE_SHA),(10240,DEVICEPATH_SHA),(10752,HANDOFF_AUDIT_SHA))
    first, free = report['original_cluster'], report['free_cluster']
    assert first >= 2 and first < free <= first+4096
    assert directory == report['directory_entry_offset'] and directory % 32 == 0
    expected = {}
    for table in (fat, mirror):
        assert table % 512 == 0 and table < data
        for number, value, kind in ((first, free, 'end'), (free, 0x0fffffff, 'free')):
            offset = table+(number*4)//512*512
            old, new = records[offset]
            assert len(old) == len(new) == 512
            changed = expected.setdefault(offset, bytearray(old))
            within = number*4 % 512
            before = struct.unpack_from('<I', old, within)[0]
            assert (before & 0x0fffffff) >= 0x0ffffff8 if kind == 'end' else before == 0
            struct.pack_into('<I', changed, within, (before & 0xf0000000) | value)
    offset = directory//512*512
    old, new = records[offset]
    assert len(old) == len(new) == 512 and offset >= data
    within = directory % 512
    assert old[within:within+11] == b'BOOTAA64EFI' and not old[within+11] & 0x18
    assert struct.unpack_from('<I', old, within+28)[0] == 8192
    actual = (struct.unpack_from('<H', old, within+20)[0] << 16) | struct.unpack_from('<H', old, within+26)[0]
    assert actual == first
    changed = bytearray(old)
    struct.pack_into('<I', changed, within+28, len(candidate))
    expected[offset] = changed
    for index, number in enumerate((first, free)):
        offset = data+(number-2)*8192
        old, new = records[offset]
        assert len(old) == len(new) == 8192
        if index == 0:
            assert old == original
        segment = candidate[index*8192:(index+1)*8192]
        expected[offset] = segment + old[len(segment):]
    assert len(expected) == len(records) == len(report['blocks']) == 5
    assert set(expected) == set(records)
    result = []
    for item in report['blocks']:
        offset = item['offset']
        old, new = records[offset]
        assert item['length'] == len(old) and bytes(expected[offset]) == new
        assert sha(old) == item['original_sha256'] and sha(new) == item['candidate_sha256']
        result.append((offset, old, new))
    # Data first, both FAT copies next, own file size last. No unrelated block.
    return sorted(result, key=lambda b: (0 if len(b[1]) == 8192 else 2 if b[0] == directory//512*512 else 1, b[0]))


def transact(blocks, read, write, cold, finish, rollback=False):
    cold()
    target, before = (1, 2) if rollback else (2, 1)
    for block in blocks:
        assert read(block[0], len(block[before])) == block[before], 'Whole-block preimage changed'
    touched = []
    try:
        for block in reversed(blocks) if rollback else blocks:
            cold()
            touched.append(block)  # A failed write can have changed a prefix.
            write(block[0], block[target])
            assert read(block[0], len(block[target])) == block[target], 'Immediate readback failed'
        cold()
        finish()
    except BaseException:
        for block in reversed(touched):
            cold()
            write(block[0], block[before])
            assert read(block[0], len(block[before])) == block[before], 'Own EFI recovery failed; never boot'
        raise


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adb', required=True)
    p.add_argument('--prepared', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--apply', action='store_true')
    p.add_argument('--rollback', action='store_true')
    a = p.parse_args()
    assert not a.output.exists(), 'Preserve all previous evidence'
    spec = importlib.util.spec_from_file_location('ram_gop_guard', Path(__file__).with_name('isolated-device-bcd.py'))
    g = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(g)
    report = json.loads((a.prepared/'report.json').read_text())
    candidate = (a.prepared/'candidate-BOOTAA64.EFI').read_bytes()
    candidate_sha = hashlib.sha256(candidate).hexdigest()
    assert candidate_sha in (CANDIDATE_SHA, DEVICEPATH_SHA, HANDOFF_AUDIT_SHA)
    f = g.DeviceFat(a.adb, a.output.parent, candidate_sha.upper() if a.rollback else g.EFI_SHA)
    original = (a.prepared/'original-BOOTAA64.EFI').read_bytes()
    first = f.root
    for name in ('EFI', 'BOOT'):
        matches = [entry for entry in f.entries(first) if name in (entry[0].upper(), entry[1].upper())]
        assert len(matches) == 1 and matches[0][4] & 16
        first = matches[0][2]
    found = []
    for number in f.chain(first):
        contents = f.read(f.offset(number), f.cluster_bytes)
        for pos in range(0, len(contents), 32):
            if not contents[pos]:
                break
            if contents[pos:pos+11] == b'BOOTAA64EFI':
                found.append(f.offset(number)+pos)
    assert len(found) == 1
    boot = f.read(g.PART, 512)
    mirror = f.fat + struct.unpack_from('<I', boot, 36)[0]*512
    records = {}
    for item in report['blocks']:
        labels = item['labels']
        stem = labels[0] if len(labels) == 1 and labels[0].startswith('cluster-') else str(item['offset'])
        assert '/' not in stem and '\\' not in stem
        records[item['offset']] = ((a.prepared/(stem+'-old.bin')).read_bytes(), (a.prepared/(stem+'-new.bin')).read_bytes())
    blocks = verified_blocks(report, records, candidate, original, f.cluster_bytes, f.fat, mirror, f.data, found[0])
    assert all(offset % 512 == 0 and g.PART <= offset and offset+len(old) <= g.END for offset, old, new in blocks)
    expected_chain = [report['original_cluster']] + ([report['free_cluster']] if a.rollback else [])
    assert f.file('/EFI/BOOT/BOOTAA64.EFI')[1] == expected_chain
    prefix = f.read(0, 65536)
    before = 2 if a.rollback else 1
    assert all(f.read(block[0], len(block[before])) == block[before] for block in blocks)
    a.output.mkdir(parents=True)
    result = dict(result='TYPED_OWN_EFI_GROWTH_PREFLIGHT_PASS', apply=a.apply, rollback=a.rollback, blocks=len(blocks))
    (a.output/'report.json').write_text(json.dumps(result, indent=2))
    if not a.apply:
        print(json.dumps(result));return
    remote = '/data/local/tmp/uavf-ram-gop-'+uuid.uuid4().hex+'.bin'

    def write(offset, contents):
        host = a.output/'transaction-block.bin'
        host.write_bytes(contents)
        subprocess.run([a.adb, 'push', str(host), remote], check=True, capture_output=True)
        subprocess.run([a.adb, 'shell', 'chmod', '644', remote], check=True, capture_output=True)
        f.cold()
        subprocess.run([a.adb, 'shell', 'run-as', g.PACKAGE, 'dd', 'if='+remote, 'of='+g.DISK,
                        'bs=512', 'seek='+str(offset//512), 'count='+str(len(contents)//512), 'conv=notrunc'], check=True, capture_output=True)

    def finish():
        actual, chain = f.file('/EFI/BOOT/BOOTAA64.EFI')
        assert actual == (original if a.rollback else candidate)
        assert chain == [report['original_cluster']] + ([] if a.rollback else [report['free_cluster']])
        assert f.read(0, 65536) == prefix, 'GPT/prefix changed'

    try:
        transact(blocks, f.read, write, f.cold, finish, a.rollback)
    finally:
        subprocess.run([a.adb, 'shell', 'rm', '-f', remote], check=False, capture_output=True)
    result['result'] = 'TYPED_OWN_EFI_ROLLBACK_READBACK_PASS' if a.rollback else 'TYPED_OWN_EFI_GROWTH_READBACK_PASS'
    (a.output/'report.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
