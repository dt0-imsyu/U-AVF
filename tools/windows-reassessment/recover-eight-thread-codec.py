"""Resume interrupted x264 DLL cold rollback, only exact admitted112 blocks.

Dry-run first. Every observed block must equal its complete old or candidate
bytes. Unknown/torn/user-modified blocks reject all writes. Never a disk fixer.
"""
import argparse
import json
import subprocess
import uuid
import hashlib
from pathlib import Path
import importlib.util


def load():
    spec = importlib.util.spec_from_file_location('exact_codec', Path(__file__).with_name('apply-eight-thread-codec.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def classify(blocks, read, native_metadata=False):
    states = []
    pending = []
    for offset, old, candidate in blocks:
        current = read(offset, len(old))
        if current not in (old,candidate) and native_metadata:
            # Exactly observed 2026-10-14 Windows FAT cache/access-date updates,
            # NOT a generic metadata relaxation or filesystem repair.
            admitted={1049088:'645972a72f75e39c729f514f7a42650f2c2496f74970ae7249ce1d8a3eff980a',
                      9978368:'9af3442852b2d0e3d6c7ae12a682a2441c14e1f4dccfca25274e51b00ee2b3f5'}
            assert offset in admitted and hashlib.sha256(current).hexdigest()==admitted[offset]
            normalized=bytearray(current)
            if offset==1049088:normalized[488:496]=candidate[488:496]
            else:normalized[274:276]=candidate[274:276] # Own UAVF8 entry's LastAccessDate only.
            assert bytes(normalized)==candidate,'Unrelated metadata change; no writes'
        else:assert current in (old, candidate), f'Unknown preimage at {offset}; no writes'
        state = 'original' if current == old else 'candidate'
        states.append({'offset': offset, 'length': len(old), 'state': state})
        if current != old:
            pending.append((offset, old, current))
    return states, pending


def boot_guard_matches(current, expected, observed_crash=False):
    if hashlib.sha256(current).hexdigest()==expected:return True
    # Exact observed crash sector only. Validate its original identity without
    # writing/clearing the filesystem's dirty status or accepting other changes.
    if not observed_crash or len(current)!=512:return False
    if expected!='4bda9265ad59680936b16c7ac344937fa3944b3101c29102d7294d805fcde9c6':return False
    if hashlib.sha256(current).hexdigest()!='a5283eaff183fa8e89613a9720e4f2a320f494c897d356bd70f2d29952e34d8a':return False
    if current[65]!=1:return False
    original=bytearray(current);original[65]=0
    return hashlib.sha256(original).hexdigest()==expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--prepared', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--native-gop',action='store_true')
    parser.add_argument('--observed-native-metadata',action='store_true')
    parser.add_argument('--observed-native-crash',action='store_true')
    args = parser.parse_args()
    assert not args.output.exists(), 'Preserve prior evidence'
    assert not args.observed_native_metadata or args.native_gop
    assert not args.observed_native_crash or (args.native_gop and args.observed_native_metadata)
    m = load();m.select_codec('x264',args.native_gop)
    plan, blocks = m.read_plan(args.prepared)
    fat = m.g.DeviceFat(args.adb, args.output.parent,expected_efi_sha=m.g.EFI_SHA)

    def guards():
        fat.cold()
        for offset, size, key in ((512,512,'gpt_header_sha256'),(1024,16384,'gpt_entries_sha256'),(m.g.PART,512,'boot_sector_sha256')):
            current=fat.read(offset,size)
            if key=='boot_sector_sha256':assert boot_guard_matches(current,plan[key],args.observed_native_crash),'Unknown boot guard; no writes'
            else:assert m.p.digest(current) == plan[key],key
        assert m.p.digest(fat.file('/EFI/BOOT/BOOTAA64.EFI')[0]) == plan['efi_sha256']

    guards();states,pending=classify(blocks,fat.read,args.observed_native_metadata)
    args.output.mkdir(parents=True)
    report={'result':'EXACT_INTERRUPTED_CODEC_ROLLBACK_AUDIT_PASS','apply':args.apply,
            'original_blocks':len(blocks)-len(pending),'candidate_blocks':len(pending),
            'unknown_blocks':0,'device_writes':0,'blocks':states}
    def save():
        (args.output/'report.json').write_text(json.dumps(report,indent=2))
    save()
    if not args.apply:
        print(json.dumps({k:v for k,v in report.items() if k!='blocks'}));return
    remote='/data/local/tmp/uavf-codec-recovery-'+uuid.uuid4().hex+'.bin'
    def write(offset,data):
        host=args.output/'transaction-block.bin';host.write_bytes(data)
        subprocess.run([args.adb,'push',str(host),remote],check=True,capture_output=True)
        subprocess.run([args.adb,'shell','chmod','644',remote],check=True,capture_output=True)
        assert subprocess.check_output([args.adb,'exec-out',f'run-as {m.g.PACKAGE} cat {remote} 2>/dev/null'])==data
        fat.cold()
        subprocess.run([args.adb,'shell','run-as',m.g.PACKAGE,'dd','if='+remote,'of='+m.g.DISK,
                        'bs=512','seek='+str(offset//512),'count='+str(len(data)//512),'conv=notrunc'],check=True,capture_output=True)
        report['device_writes']+=1;save()
    def finish():
        guards()
        assert all(fat.read(offset,len(old))==old for offset,old,_ in blocks)
        assert not any('UAVF8.DLL' in (e[0].upper(),e[1].upper()) for e in fat.entries(fat.root))
    try:
        # Restore only candidate-state blocks; existing transaction rolls back
        # a partial failure to this observed mixed state, not an invented state.
        m.transaction.transact(pending,fat.read,write,fat.cold,finish,rollback=True)
        report['result']='EXACT_CODEC_RECOVERY_ALL112_ORIGINAL_READBACK_PASS';save()
    finally:
        subprocess.run([args.adb,'shell','rm','-f',remote],capture_output=True)
    print(json.dumps({k:v for k,v in report.items() if k!='blocks'}))


if __name__=='__main__':
    main()
