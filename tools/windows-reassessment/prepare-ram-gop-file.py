"""Read-only plan for growing ONE owned EFI file into one verified free cluster.

Does NOT write to the device. All exact preimages are retained for an audited
transaction/rollback implementation; no generic filesystem mutation interface.
"""
import argparse
import hashlib
import importlib.util
import json
import struct
from pathlib import Path

CANDIDATE_SHA = '1643e97ca438f6d0edafb504a91f99f2faaf56e2a3d3a10a9e6e377b0742c9a3'
DEVICEPATH_SHA = '7e60950f8d525df05607774227c737e97873ee2ddb3cd80e3d4432bb0e0e1f5f'
HANDOFF_AUDIT_SHA = '359ded6e811f23b011566938b6f3cc8a01df480d5e8225a320fefe357cd98e3a'


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adb', required=True)
    p.add_argument('--candidate', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    assert not a.output.exists(), 'Preserve all prior evidence'
    candidate = a.candidate.read_bytes()
    sha = lambda data: hashlib.sha256(data).hexdigest()
    assert (len(candidate), sha(candidate)) in ((10240,CANDIDATE_SHA),(10240,DEVICEPATH_SHA),(10752,HANDOFF_AUDIT_SHA))
    spec = importlib.util.spec_from_file_location('gop_file_guard', Path(__file__).with_name('isolated-device-bcd.py'))
    g = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(g)
    f = g.DeviceFat(a.adb, a.output.parent)
    original, chain = f.file('/EFI/BOOT/BOOTAA64.EFI')
    assert len(original) == f.cluster_bytes == 8192 and len(chain) == 1
    assert sha(original).upper() == g.EFI_SHA
    first = f.root
    for name in ('EFI', 'BOOT'):
        matches = [entry for entry in f.entries(first) if name in (entry[0].upper(), entry[1].upper())]
        assert len(matches) == 1 and matches[0][4] & 16
        first = matches[0][2]
    entries = []
    for cluster in f.chain(first):
        data = f.read(f.offset(cluster), f.cluster_bytes)
        for offset in range(0, len(data), 32):
            entry = data[offset:offset+32]
            if not entry[0]:
                break
            if entry[:11] == b'BOOTAA64EFI' and entry[11] not in (15, 16):
                entries.append((f.offset(cluster)+offset, entry))
    assert len(entries) == 1
    address, entry = entries[0]
    entry_cluster = (struct.unpack_from('<H', entry, 20)[0] << 16) | struct.unpack_from('<H', entry, 26)[0]
    assert entry_cluster == chain[0] and struct.unpack_from('<I', entry, 28)[0] == 8192
    boot = f.read(g.PART, 512)
    fat_bytes = struct.unpack_from('<I', boot, 36)[0]*512
    mirror = f.fat + fat_bytes
    # Search only the next4096 FAT entries, in bounded contiguous reads.
    window = ((chain[0]+1)*4)//512*512
    length = min(16896, fat_bytes-window)
    assert 0 < length <= 65536
    primary = f.read(f.fat+window, length)
    secondary = f.read(mirror+window, length)
    assert primary == secondary, 'FAT mirrors must agree before selecting a free cluster'
    free = None
    for number in range(chain[0]+1, min(chain[0]+4097, (window+length)//4)):
        if struct.unpack_from('<I', primary, number*4-window)[0] == 0 and f.offset(number)+8192 <= g.END:
            free = number
            break
    assert free is not None, 'No bounded verified free cluster; no allocation guessed'
    sectors = {}

    def change_sector(offset, within, data, label):
        assert offset % 512 == 0 and within+len(data) <= 512
        if offset not in sectors:
            old = f.read(offset, 512)
            sectors[offset] = [old, bytearray(old), []]
        sectors[offset][1][within:within+len(data)] = data
        sectors[offset][2].append(label)

    for table in (f.fat, mirror):
        for number, value, old_kind in ((chain[0], free, 'end'), (free, 0x0fffffff, 'free')):
            offset = table+(number*4)//512*512
            within = number*4 % 512
            old = struct.unpack('<I', f.read(offset+within, 4))[0]
            assert (old & 0x0fffffff) >= 0x0ffffff8 if old_kind == 'end' else old == 0
            change_sector(offset, within, struct.pack('<I', (old & 0xf0000000) | value), 'own EFI chain '+old_kind)
    change_sector(address//512*512, address % 512+28, struct.pack('<I', len(candidate)), 'own EFI size only')
    assert len(sectors) <= 5
    a.output.mkdir(parents=True)
    plans = []
    for offset, (old, new, labels) in sorted(sectors.items()):
        stem = str(offset)
        (a.output/(stem+'-old.bin')).write_bytes(old)
        (a.output/(stem+'-new.bin')).write_bytes(new)
        plans.append(dict(offset=offset, length=512, labels=labels, original_sha256=sha(old), candidate_sha256=sha(new)))
    for index, number in enumerate((chain[0], free)):
        old = f.read(f.offset(number), 8192)
        segment = candidate[index*8192:(index+1)*8192]
        # Preserve bytes after EOF rather than destroying free-cluster content.
        new = segment + old[len(segment):]
        stem = 'cluster-'+str(number)
        (a.output/(stem+'-old.bin')).write_bytes(old)
        (a.output/(stem+'-new.bin')).write_bytes(new)
        plans.append(dict(offset=f.offset(number), length=8192, labels=[stem], original_sha256=sha(old), candidate_sha256=sha(new)))
    (a.output/'original-BOOTAA64.EFI').write_bytes(original)
    (a.output/'candidate-BOOTAA64.EFI').write_bytes(candidate)
    f.cold()
    assert f.file('/EFI/BOOT/BOOTAA64.EFI') == (original, chain)
    report = dict(result='READ_ONLY_OWN_EFI_GROWTH_PLAN_PASS', device_writes=0,
                  old_bytes=len(original), new_bytes=len(candidate), original_cluster=chain[0], free_cluster=free,
                  directory_entry_offset=address, original_sha256=sha(original), candidate_sha256=sha(candidate),
                  cluster_bytes=8192, blocks=plans,
                  scope='Own EFI data/size and its two mirrored FAT links only; GPT, NTFS, signed Windows and Ubuntu untouched',
                  next='Requires typed transaction/rollback and APK admission. This tool does not apply.')
    (a.output/'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == '__main__':
    main()
