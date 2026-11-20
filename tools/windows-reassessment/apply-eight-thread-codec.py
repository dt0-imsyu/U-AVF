"""Exact, cold-only UAVF8.DLL R&D transaction; original codec is not replaced.

Only the retained 2026-10-12 plan is admitted. Dry-run by default. No generic
filesystem writer; signed Windows, NTFS, GPT and original EFI stay untouched.
"""
import argparse
import importlib.util
import json
import struct
import subprocess
import uuid
from pathlib import Path


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


p = load('eight_codec_plan', 'prepare-eight-thread-codec.py')
g = load('eight_codec_disk_guard', 'isolated-device-bcd.py')
transaction = load('eight_codec_transaction', 'apply-ram-gop-file.py')
PLAN_SHA = '6669f8ba4b756c73d1c2cce26728f595df048ddbb44334b895e6aeda2ff81a76'
X264_PLAN_SHA = '39cc398f90ffb26b636e09b8ebf546a0b607c2ddd1463e4597fd96f18c705efa'
NATIVE_X264_PLAN_SHA = '795c7e46c21c9e109e606c607bd592cc7f7a9e49cfa9674bf281fe4a557ba24a'
LEGACY_EFI_SHA = g.EFI_SHA


def select_codec(codec, native_gop=False):
    global PLAN_SHA, DIRECTORY, CLUSTERS
    DIRECTORY=9978528
    CLUSTERS=list(range(994748,994773))+list(range(995233,995291))+list(range(995785,995805))
    g.EFI_SHA=LEGACY_EFI_SHA
    if codec == 'x264':
        p.SHA = p.X264_SHA
        PLAN_SHA = X264_PLAN_SHA
    else:
        assert codec == 'openh264'
        p.SHA = '533f9c108fd71828e7a209a10f0202fe9d8312b0502bd8035c9f8167d7cd5e04'
        PLAN_SHA = '6669f8ba4b756c73d1c2cce26728f595df048ddbb44334b895e6aeda2ff81a76'
    if native_gop:
        assert codec=='x264','Native profile admits only the exact already-proven x264 payload'
        PLAN_SHA=NATIVE_X264_PLAN_SHA
        DIRECTORY=9978624
        CLUSTERS=list(range(994750,994773))+list(range(995233,995291))+list(range(995785,995807))
        g.EFI_SHA=g.RAM_GOP_HANDOFF_AUDIT_EFI_SHA

FAT, MIRROR, DATA, DIRECTORY = 1064960, 5521408, 9977856, 9978528
CLUSTERS = list(range(994748, 994773)) + list(range(995233, 995291)) + list(range(995785, 995805))


def verified_blocks(report, records):
    assert report['result'] == 'READ_ONLY_CODEC_STAGING_PLAN_PASS'
    assert report['device_writes'] == 0 and report['candidate_sha256'] == p.SHA
    assert report['candidate_bytes'] == p.SIZE and report['name'] == 'UAVF8.DLL'
    assert report['clusters'] == CLUSTERS
    assert report['directory_entry'] == DIRECTORY
    assert report['excluded_gop_cluster'] == p.GOP_RESERVED
    assert report['efi_sha256'] == g.EFI_SHA.lower()
    assert len(records) == len(report['blocks']) == 112
    expected = {}
    kinds = {}
    for table in (FAT, MIRROR):
        for index, cluster in enumerate(CLUSTERS):
            offset = table + (cluster * 4 // 512) * 512
            old, _ = records[offset]
            assert len(old) == 512
            new = expected.setdefault(offset, bytearray(old))
            within = cluster * 4 % 512
            assert struct.unpack_from('<I', old, within)[0] == 0
            struct.pack_into('<I', new, within,
                CLUSTERS[index + 1] if index + 1 < len(CLUSTERS) else 0x0fffffff)
            kinds[offset] = 'fat'
    offset = DIRECTORY // 512 * 512
    old, _ = records[offset]
    within = DIRECTORY % 512
    assert len(old) == 512 and old[within] == old[within + 32] == 0
    new = bytearray(old)
    new[within:within + 32] = p.new_entry(CLUSTERS[0])
    expected[offset], kinds[offset] = new, 'directory'
    for offset in (1049088, 1052160):
        old, _ = records[offset]
        assert len(old) == 512 and old[:4] == b'RRaA' and old[484:488] == b'rrAa'
        assert old[508:512] == b'\0\0\x55\xaa'
        new = bytearray(old)
        new[488:496] = b'\xff' * 8
        expected[offset], kinds[offset] = new, 'fsinfo-unknown-hints'
    payload = []
    for index, cluster in enumerate(CLUSTERS):
        offset = DATA + (cluster - 2) * 8192
        old, new = records[offset]
        assert len(old) == len(new) == 8192
        length = min(8192, p.SIZE - index * 8192)
        assert length > 0 and new[length:] == old[length:]
        payload.append(new[:length])
        expected[offset], kinds[offset] = new, 'data'
    assert p.digest(b''.join(payload)) == p.SHA
    assert set(records) == set(expected)
    blocks = []
    seen = set()
    for item in report['blocks']:
        offset = item['offset']
        assert offset not in seen
        seen.add(offset)
        old, new = records[offset]
        assert item['kind'] == kinds[offset]
        assert item['length'] == len(old) == len(new)
        assert bytes(expected[offset]) == new
        assert p.digest(old) == item['old_sha256'] and p.digest(new) == item['new_sha256']
        assert offset % 512 == 0 and g.PART <= offset < offset + len(old) <= g.END
        blocks.append((offset, old, new))
    # Directory is the last commit point, first removed during rollback.
    order = {'data': 0, 'fat': 1, 'fsinfo-unknown-hints': 2, 'directory': 3}
    return sorted(blocks, key=lambda b: (order[kinds[b[0]]], b[0]))


def read_plan(folder):
    raw = (folder / 'report.json').read_bytes()
    assert p.digest(raw) == PLAN_SHA, 'Exact admitted read-only plan required'
    report = json.loads(raw)
    records = {}
    for item in report['blocks']:
        offset = item['offset']
        assert type(offset) is int and offset not in records
        records[offset] = ((folder / f'{offset}-old.bin').read_bytes(),
            (folder / f'{offset}-new.bin').read_bytes())
    return report, verified_blocks(report, records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--prepared', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--rollback', action='store_true')
    parser.add_argument('--codec', choices=('openh264', 'x264'), default='openh264')
    parser.add_argument('--native-gop',action='store_true')
    parser.add_argument('--observed-native-crash',action='store_true')
    args = parser.parse_args()
    select_codec(args.codec,args.native_gop)
    assert not args.observed_native_crash or (args.native_gop and args.codec=='x264')
    crash_guard=None
    if args.observed_native_crash:
        spec=importlib.util.spec_from_file_location('crash_guard',Path(__file__).with_name('recover-eight-thread-codec.py'))
        crash_guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(crash_guard)
    assert not args.output.exists(), 'Preserve earlier evidence'
    report, blocks = read_plan(args.prepared)
    fat = g.DeviceFat(args.adb, args.output.parent,expected_efi_sha=g.EFI_SHA)
    assert (fat.fat, fat.data, fat.cluster_bytes) == (FAT, DATA, 8192)
    assert fat.fat + struct.unpack_from('<I', fat.read(g.PART, 512), 36)[0] * 512 == MIRROR

    def guards():
        fat.cold()
        assert p.digest(fat.read(512, 512)) == report['gpt_header_sha256']
        assert p.digest(fat.read(1024, 16384)) == report['gpt_entries_sha256']
        boot=fat.read(g.PART,512)
        assert (crash_guard.boot_guard_matches(boot,report['boot_sector_sha256'],True) if crash_guard else p.digest(boot)==report['boot_sector_sha256']), 'Unknown boot status guard; no writes'
        assert p.digest(fat.file('/EFI/BOOT/BOOTAA64.EFI')[0]) == report['efi_sha256']

    guards()
    before = 2 if args.rollback else 1
    for block in blocks:
        assert fat.read(block[0], len(block[before])) == block[before], 'Stale preimage: no writes'
    args.output.mkdir(parents=True)
    result = dict(result='TYPED_SEPARATE_CODEC_PREFLIGHT_PASS', device_writes=0,
        apply=args.apply, rollback=args.rollback, blocks=len(blocks), candidate_sha256=p.SHA)
    (args.output / 'report.json').write_text(json.dumps(result, indent=2))
    if not args.apply:
        print(json.dumps(result))
        return
    remote = '/data/local/tmp/uavf-eight-codec-' + uuid.uuid4().hex + '.bin'

    def write(offset, data):
        host = args.output / 'transaction-block.bin'
        host.write_bytes(data)
        subprocess.run([args.adb, 'push', str(host), remote], check=True, capture_output=True)
        subprocess.run([args.adb, 'shell', 'chmod', '644', remote], check=True, capture_output=True)
        staged = subprocess.check_output([args.adb, 'exec-out',
            f'run-as {g.PACKAGE} cat {remote} 2>/dev/null'])
        assert staged == data, 'Exact transfer readback before disk write'
        fat.cold()
        subprocess.run([args.adb, 'shell', 'run-as', g.PACKAGE, 'dd', 'if=' + remote,
            'of=' + g.DISK, 'bs=512', 'seek=' + str(offset // 512),
            'count=' + str(len(data) // 512), 'conv=notrunc'], check=True, capture_output=True)
        result['device_writes'] += 1

    def finish():
        guards()
        if args.rollback:
            assert not any('UAVF8.DLL' in (e[0].upper(), e[1].upper()) for e in fat.entries(fat.root))
        else:
            codec, chain = fat.file('/UAVF8.DLL')
            assert len(codec) == p.SIZE and p.digest(codec) == p.SHA and chain == CLUSTERS
        target = 1 if args.rollback else 2
        assert all(fat.read(b[0], len(b[target])) == b[target] for b in blocks)

    try:
        transaction.transact(blocks, fat.read, write, fat.cold, finish, args.rollback)
    except BaseException:
        # The core restores to the transaction's initial state after partial
        # writes; verify every block and immutable guard before permitting boot.
        guards()
        assert all(fat.read(b[0], len(b[before])) == b[before] for b in blocks), 'Recovery failed; DO NOT BOOT'
        raise
    finally:
        subprocess.run([args.adb, 'shell', 'rm', '-f', remote], capture_output=True)
    result['result'] = 'SEPARATE_CODEC_ROLLBACK_READBACK_PASS' if args.rollback else 'SEPARATE_CODEC_APPLY_READBACK_PASS'
    (args.output / 'report.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
