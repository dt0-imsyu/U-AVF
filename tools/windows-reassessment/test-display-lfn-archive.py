"""Exercise the actual Java cold diagnostic rename against tiny FAT fixtures."""
import struct
import subprocess
import tempfile
from pathlib import Path

source = Path(__file__).parent / 'android-owner/WindowsVmOwnerService.java'
text = source.read_text()
start = text.index('    private void archiveOwnDisplayEvidence(')
opening = text.index('{', start)
depth = 1
end = opening + 1
while depth:
    depth += (text[end] == '{') - (text[end] == '}')
    end += 1
method = text[start:end]
java = Path('C:/Program Files/Android/Android Studio/jbr/bin')
char_offsets = (1, 3, 5, 7, 9, 14, 16, 18, 20, 22, 24, 28, 30)
base = 1048576
data = base + 32 * 512 + 2 * 512

def fixture(path, corrupt=False, occupied=False):
    disk = bytearray(data + 512)
    struct.pack_into('<H', disk, base + 11, 512)
    disk[base + 13] = 1
    struct.pack_into('<H', disk, base + 14, 32)
    disk[base + 16] = 2
    struct.pack_into('<I', disk, base + 36, 1)
    struct.pack_into('<I', disk, base + 44, 2)
    def entries(at, name, alias):
        disk[at] = 65; disk[at + 11] = 15
        checksum = 0
        for value in alias: checksum = (((checksum & 1) << 7) + (checksum >> 1) + value) & 255
        disk[at + 13] = checksum
        for i, offset in enumerate(char_offsets):
            struct.pack_into('<H', disk, at + offset, ord(name[i]) if i < len(name) else 0)
        disk[at + 32:at + 43] = alias
        disk[at + 43] = 16
        struct.pack_into('<H', disk, at + 58, 3)
    entries(data, 'UAVF-DISPLAY', b'UAVF-D~1   ')
    if corrupt: disk[data + 13] ^= 1
    if occupied: entries(data + 64, 'UVFLOG000001', b'UAVF-D~2   ')
    path.write_bytes(disk)
    return bytes(disk)

with tempfile.TemporaryDirectory(prefix='uavf-lfn-test-') as tmp:
    root = Path(tmp)
    code = '''import java.io.*;import java.nio.*;import java.nio.charset.*;import java.util.*;
public class ArchiveAudit {
 File dir;ArchiveAudit(File d){dir=d;}File getFilesDir(){return dir;}
 void note(String s){System.out.println(s);}
''' + method + '''
 public static void main(String[] a)throws Exception{
 new ArchiveAudit(new File(a[1])).archiveOwnDisplayEvidence(new File(a[0]));}}
'''
    (root / 'ArchiveAudit.java').write_text(code)
    subprocess.run([str(java / 'javac.exe'), str(root / 'ArchiveAudit.java')], check=True)
    for case, corrupt, occupied in [('rename', False, False), ('collision', False, True), ('invalid', True, False)]:
        folder = root / case; folder.mkdir()
        path = folder / 'fixture.img'; before = fixture(path, corrupt, occupied)
        run = subprocess.run([str(java / 'java.exe'), '-cp', str(root), 'ArchiveAudit', str(path), str(folder)], capture_output=True)
        after = path.read_bytes()
        if corrupt:
            assert run.returncode != 0 and before == after
            assert not list(folder.glob('display-lfn-archive-*.bin'))
        else:
            assert run.returncode == 0, run.stderr
            assert before[:data] == after[:data] and before[data + 32:] == after[data + 32:]
            name = ''.join(chr(struct.unpack_from('<H', after, data + o)[0]) for o in char_offsets).rstrip('\0')
            assert name == ('UVFLOG000002' if occupied else 'UVFLOG000001')
            journals = list(folder.glob('display-lfn-archive-*.bin')); assert len(journals) == 1
            journal = journals[0].read_bytes()
            assert len(journal) == 72 and journal[8:40] == before[data:data + 32] and journal[40:] == after[data:data + 32]
        print('PASS:', case, 'actual Java method; no changes outside own LFN entry')
