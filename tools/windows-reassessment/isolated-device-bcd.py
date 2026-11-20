"""Bounded cold-only BCD access in the disposable APK disk; no image clone.

Extract first; edit that explicit local store with host BCDEdit; replace only an
equal-length hive after validating the original extraction and frozen EFI shim.
Does not change FAT, GPT, root NTFS or any other file. No running VM allowed.
"""
import argparse
import hashlib
import json
import struct
import subprocess
import tempfile
import zlib
from pathlib import Path

PACKAGE = 'com.example.uavfwinpetest'
DISK = 'files/windows-diagnostic.img'
PART = 1048576
END = (17825758 + 1) * 512
EFI_SHA = '893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B'
RTC_EFI_SHA = 'AAD53298182B48845DB08F00FC85305A85E4D14EBDFCE171007E883B84446F5D'
GIC_EFI_SHA = 'E550FD640FC3864305A6F6F0A794B582A5DB43D7F0556FA69FFD11BE5C1FDC47'
GIC8_EFI_SHA = '365B733271DA6DEEB5EEA2C262041F6E1793D6DA3C21ECE077BA15FE84B1A865'
PSCI_RESERVE_EFI_SHA = '3B1805F0982AC660A9197F98FBC2A111854B99D01C620849F221DDD7231B9ABD'
PSCI_FIRST_EFI_SHA = '36943A414743D10960C445102BE9EFCA4C2ADCAA46DAEB144CF7AA4F0E11620A'
PSCI_RUNTIME_EFI_SHA = '20A6364A39A5B58888923DDE027975A4B15771D6F652614397C48C872C8F4AF4'
PSCI_RUNTIME_ALL_EFI_SHA = '7F33A06FEE99DD373AB2CAB99A6CFABB8E883342882EC3F79FB6BA238363EA9E'
PSCI_ENTRY_MARKER_EFI_SHA = '597AC0561E32A1210BC030B1B4A4BC432E2AAB1BBACE945862D25368120EBE29'
PSCI_CACHE_PUBLISH_EFI_SHA = '0347CB7BC2BB33C34C8940392EACB62795CDBE66D77FB081EEBFA29A88CC9007'
RAM_GOP_EFI_SHA = '1643E97CA438F6D0EDAFB504A91F99F2FAAF56E2A3D3A10A9E6E377B0742C9A3'
RAM_GOP_DEVICEPATH_EFI_SHA = '7E60950F8D525DF05607774227C737E97873EE2DDB3CD80E3D4432BB0E0E1F5F'
RAM_GOP_HANDOFF_AUDIT_EFI_SHA = '359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A'
WDDM_PNP_EFI_SHA = '3D81FEEA37CC742FD55389FDA2E7DDC02A750A5469A49C8A639243CA8C13D6FA'

def sha(data):
    return hashlib.sha256(data).hexdigest().upper()

class DeviceFat:
    def __init__(self, adb, staging, expected_efi_sha=EFI_SHA):
        self.adb = adb
        self.staging = staging
        self.cold()
        size = subprocess.check_output([self.adb, 'shell', 'run-as', PACKAGE, 'stat', '-c', '%s', DISK], text=True).strip()
        assert size == '77846282240', 'Exact isolated install disk length'
        header = self.read(512, 512)
        assert header[:8] == b'EFI PART' and struct.unpack_from('<I', header, 12)[0] == 92
        check = bytearray(header[:92])
        check[16:20] = bytes(4)
        assert zlib.crc32(check) == struct.unpack_from('<I', header, 16)[0]
        partition = self.read(1024, 128)
        assert struct.unpack_from('<QQ', partition, 32) == (2048, 17825758)
        boot = self.read(PART, 512)
        self.sector = struct.unpack_from('<H', boot, 11)[0]
        self.cluster_bytes = self.sector * boot[13]
        assert self.sector == 512 and 512 <= self.cluster_bytes <= 65536
        reserved = struct.unpack_from('<H', boot, 14)[0]
        fats = boot[16]
        fat_sectors = struct.unpack_from('<I', boot, 36)[0]
        assert fats == 2 and boot[510:512] == b'\x55\xaa'
        self.fat = PART + reserved * 512
        self.data = self.fat + fats * fat_sectors * 512
        self.root = struct.unpack_from('<I', boot, 44)[0]
        assert expected_efi_sha in (EFI_SHA,RTC_EFI_SHA,GIC_EFI_SHA,GIC8_EFI_SHA,PSCI_RESERVE_EFI_SHA,PSCI_FIRST_EFI_SHA,PSCI_RUNTIME_EFI_SHA,PSCI_RUNTIME_ALL_EFI_SHA,PSCI_ENTRY_MARKER_EFI_SHA,PSCI_CACHE_PUBLISH_EFI_SHA,RAM_GOP_EFI_SHA,RAM_GOP_DEVICEPATH_EFI_SHA,RAM_GOP_HANDOFF_AUDIT_EFI_SHA,WDDM_PNP_EFI_SHA), 'Only exact isolated EFI candidates allowed'
        assert sha(self.file('/EFI/BOOT/BOOTAA64.EFI')[0]) == expected_efi_sha

    def cold(self):
        state = subprocess.check_output([self.adb, 'shell', '/apex/com.android.virt/bin/vm', 'list'], text=True)
        assert 'Running VMs: []' in state, 'No cold disk access while any VM runs'

    def read(self, offset, size):
        assert 0 <= offset and 0 < size <= 65536 and offset + size <= END
        command = f'run-as {PACKAGE} dd if={DISK} bs=1 skip={offset} count={size} 2>/dev/null'
        result = subprocess.check_output([self.adb, 'exec-out', command], stderr=subprocess.DEVNULL)
        assert len(result) == size, 'Exact bounded binary read required'
        return result

    def offset(self, cluster):
        result = self.data + (cluster - 2) * self.cluster_bytes
        assert cluster >= 2 and result + self.cluster_bytes <= END
        return result

    def chain(self, first):
        result = []
        while first < 0x0ffffff8:
            assert first >= 2 and first not in result and len(result) < 128
            result.append(first)
            first = struct.unpack('<I', self.read(self.fat + first * 4, 4))[0] & 0x0fffffff
        return result

    def entries(self, first):
        lfn = {}
        for cluster in self.chain(first):
            data = self.read(self.offset(cluster), self.cluster_bytes)
            for start in range(0, len(data), 32):
                entry = data[start:start + 32]
                if not entry[0]:
                    return
                if entry[0] == 0xe5:
                    lfn = {}
                    continue
                if entry[11] == 0x0f:
                    if entry[0] & 0x40:
                        lfn = {}
                    lfn[entry[0] & 0x1f] = (entry[1:11] + entry[14:26] + entry[28:32]).decode('utf-16le')
                    continue
                short = entry[:8].decode('ascii').strip()
                ext = entry[8:11].decode('ascii').strip()
                short += '.' + ext if ext else ''
                long = ''.join(lfn[k] for k in sorted(lfn)).split('\0')[0].rstrip('\uffff')
                lfn = {}
                number = (struct.unpack_from('<H', entry, 20)[0] << 16) | struct.unpack_from('<H', entry, 26)[0]
                yield long or short, short, number, struct.unpack_from('<I', entry, 28)[0], entry[11]

    def file(self, path):
        first = self.root
        parts = path.strip('/').split('/')
        for i, name in enumerate(parts):
            matches = [e for e in self.entries(first) if name.upper() in (e[0].upper(), e[1].upper())]
            assert len(matches) == 1, 'Unique exact FAT entry: ' + path
            _, _, first, size, flags = matches[0]
            if i != len(parts) - 1:
                assert flags & 0x10
        assert not flags & 0x10 and 0 < size <= 4 * 1024**2
        chain = self.chain(first)
        result = b''.join(self.read(self.offset(c), self.cluster_bytes) for c in chain)
        assert len(result) >= size
        return result[:size], chain

    def write_bcd(self, data, chain):
        self.cold()
        for index, cluster in enumerate(chain):
            begin = index * self.cluster_bytes
            block = data[begin:begin + self.cluster_bytes]
            if not block:
                break
            # Windows adb shell binary stdin did not apply the new bytes; cold
            # readback/rollback retained the original. Reuse the proven sync
            # push + same-UID dd workflow, bounded to one FAT cluster.
            remote = '/sdcard/Android/data/' + PACKAGE + '/files/bcd-transfer-' + sha(block)[:16] + '.tmp'
            with tempfile.NamedTemporaryFile(dir=self.staging, prefix='bcd-transfer-', suffix='.tmp', delete=False) as stream:
                local = Path(stream.name)
                stream.write(block)
            try:
                subprocess.run([self.adb, 'push', str(local), remote], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                staged = subprocess.check_output([self.adb, 'exec-out', f'run-as {PACKAGE} cat {remote} 2>/dev/null'])
                assert staged == block, 'Exact staged bytes required before disk write'
                subprocess.run([self.adb, 'shell', 'run-as', PACKAGE, 'dd', 'if=' + remote, 'of=' + DISK,
                    'bs=1', 'seek=' + str(self.offset(cluster)), 'count=' + str(len(block)), 'conv=notrunc'],
                    check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                assert self.read(self.offset(cluster), len(block)) == block
            finally:
                local.unlink()
                subprocess.run([self.adb, 'shell', 'rm', '-f', remote], check=True, stdout=subprocess.DEVNULL)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--extract', type=Path, required=True)
    parser.add_argument('--replace', type=Path)
    parser.add_argument('--native-gop',action='store_true',help='Preserve already-proven exact native1200p EFI')
    args = parser.parse_args()
    expected_efi = RAM_GOP_HANDOFF_AUDIT_EFI_SHA if args.native_gop else EFI_SHA
    fat = DeviceFat(args.adb, args.extract.resolve().parent, expected_efi_sha=expected_efi)
    original, chain = fat.file('/EFI/Microsoft/Boot/BCD')
    assert original[:4] == b'regf'
    if not args.replace:
        with args.extract.open('xb') as stream:
            stream.write(original)
        print(json.dumps(dict(result='EXTRACT_PASS', bytes=len(original), sha256=sha(original), clusters=chain)))
        return
    assert original == args.extract.read_bytes(), 'Disk changed since retained extraction'
    replacement = args.replace.read_bytes()
    assert replacement[:4] == b'regf' and len(replacement) == len(original)
    assert struct.unpack_from('<I', replacement, 4)[0] == struct.unpack_from('<I', replacement, 8)[0], 'Clean closed BCD hive required'
    try:
        fat.write_bcd(replacement, chain)
        actual, after_chain = fat.file('/EFI/Microsoft/Boot/BCD')
        assert actual == replacement and after_chain == chain
        assert sha(fat.file('/EFI/BOOT/BOOTAA64.EFI')[0]) == expected_efi
    except BaseException:
        fat.write_bcd(original, chain)
        assert fat.file('/EFI/Microsoft/Boot/BCD')[0] == original, 'Rollback readback failed; do not boot'
        raise
    print(json.dumps(dict(result='REPLACE_READBACK_PASS', bytes=len(actual), before_sha256=sha(original), after_sha256=sha(actual), scope='Existing BCD bytes only; original extraction is retained rollback input')))

if __name__ == '__main__':
    main()
