"""Offline allocation edge cases and actual read-only manifest invariants.

Does not test an apply/rollback implementation: none is admitted yet.
"""
import argparse
import importlib.util
import json
import struct
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('codec_plan',
    Path(__file__).with_name('prepare-eight-thread-codec.py'))
plan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plan)


class AllocationTests(unittest.TestCase):
    def test_exact(self):
        self.assertEqual(plan.select_free(bytes(103 * 4), 2, 103, 104), list(range(2, 105)))

    def test_no_free(self):
        with self.assertRaises(ValueError):
            plan.select_free(b'\xff' * 4096, 2, 103, 1025)

    def test_bounds(self):
        with self.assertRaises(ValueError):
            plan.select_free(bytes(1024), 2, 103, 103)

    def test_gop_reserve(self):
        result = plan.select_free(bytes(105 * 4), plan.GOP_RESERVED - 1,
            103, plan.GOP_RESERVED + 103)
        self.assertNotIn(plan.GOP_RESERVED, result)
        self.assertEqual(len(set(result)), 103)

    def test_reserved_fat_values(self):
        table = struct.pack('<III', 0x0ffffff7, 0xffffffff, 0x10000000) + bytes(103 * 4)
        self.assertEqual(plan.select_free(table, 2, 103, 107), list(range(5, 108)))

    def test_entry(self):
        entry = plan.new_entry(994733)
        self.assertEqual(len(entry), 32)
        self.assertEqual(entry[:12], b'UAVF8   DLL\x20')
        self.assertEqual((struct.unpack_from('<H', entry, 20)[0] << 16)
            | struct.unpack_from('<H', entry, 26)[0], 994733)
        self.assertEqual(struct.unpack_from('<I', entry, 28)[0], plan.SIZE)


def validate_manifest(directory):
    report = json.loads((directory / 'report.json').read_text())
    assert report['device_writes'] == 0 and report['candidate_sha256'] == plan.SHA
    assert report['candidate_bytes'] == plan.SIZE
    assert len(report['clusters']) == 103 and len(set(report['clusters'])) == 103
    assert plan.GOP_RESERVED not in report['clusters']
    blocks = report['blocks']
    assert 106 <= len(blocks) <= 115
    payload = []
    last_end = 0
    counts = {}
    fat_values = []
    for block in sorted(blocks, key=lambda b: b['offset']):
        offset, size, kind = block['offset'], block['length'], block['kind']
        assert offset >= last_end and offset % 512 == 0
        assert 1048576 <= offset < offset + size <= 17825759 * 512
        last_end = offset + size
        old = (directory / f'{offset}-old.bin').read_bytes()
        new = (directory / f'{offset}-new.bin').read_bytes()
        assert len(old) == len(new) == size
        assert plan.digest(old) == block['old_sha256']
        assert plan.digest(new) == block['new_sha256']
        counts[kind] = counts.get(kind, 0) + 1
        if kind == 'data':
            assert size == 8192
            payload.append(new)
        elif kind == 'directory':
            start = report['directory_entry'] - offset
            assert old[start] == 0
            assert new[start:start + 32] == plan.new_entry(report['clusters'][0])
            assert old[:start] == new[:start] and old[start + 32:] == new[start + 32:]
        elif kind == 'fsinfo-unknown-hints':
            assert new[488:496] == b'\xff' * 8
            assert old[:488] == new[:488] and old[496:] == new[496:]
        else:
            assert kind == 'fat' and size == 512
            for within in range(0, 512, 4):
                before = struct.unpack_from('<I', old, within)[0]
                after = struct.unpack_from('<I', new, within)[0]
                if before != after:
                    assert before == 0
                    assert after in report['clusters'][1:] or after == 0x0fffffff
                    fat_values.append(after)
    assert counts['data'] == 103 and counts['directory'] == 1
    assert counts['fsinfo-unknown-hints'] == 2
    assert len(fat_values) == 206
    for value in report['clusters'][1:] + [0x0fffffff]:
        assert fat_values.count(value) == 2, 'Both FAT mirrors must encode each link exactly once'
    assert plan.digest(b''.join(payload)[:plan.SIZE]) == plan.SHA
    print('ACTUAL_READ_ONLY_MANIFEST_INVARIANTS_PASS (not apply/rollback admission)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', type=Path)
    args = parser.parse_args()
    result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(AllocationTests))
    if not result.wasSuccessful():
        raise SystemExit(1)
    if args.manifest:
        validate_manifest(args.manifest)
