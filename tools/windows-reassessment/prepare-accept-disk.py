"""Clone the exact known-good disposable WinPE disk; replace only BOOT.WIM.

Never edit the baseline or a production Ubuntu disk. Re-running refuses overwrite.
"""
import hashlib
import json
import math
import shutil
import argparse
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS

root = Path(__file__).resolve().parents[3]
folder = root / 'build-logs/windows-post-linux-reassessment-20261004'
parser=argparse.ArgumentParser()
parser.add_argument('--shell-lifetime',action='store_true')
args=parser.parse_args()
variant='shell-lifetime' if args.shell_lifetime else 'direct-accept'
source = folder / 'windows-display-winpe-20261007.img'
target = folder / ('windows-display-'+variant+'-20261008.img')
wim = folder / (variant+'-candidate.wim')
expected = 'A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5'
assert not target.exists(), 'Preserve existing candidate and evidence'
assert source.stat().st_size == 9126805504
assert wim.is_file()
with source.open('rb') as stream:
    assert hashlib.file_digest(stream, 'sha256').hexdigest().upper() == expected
assert shutil.disk_usage(folder).free > source.stat().st_size + 2 * 1024**3
shutil.copyfile(source, target)
with PyFatFS(str(target), offset=1048576) as fs:
    boot_before = fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')
    entry = fs.fs.root_dir.get_entry('/SOURCES/BOOT.WIM')
    chain = list(fs.fs.get_cluster_chain(entry.get_cluster()))
    block = fs.fs.bytes_per_cluster
    needed = math.ceil(wim.stat().st_size / block)
    extra = needed - len(chain)
    if extra > 0:
        free = [i for i in range(2, len(fs.fs.fat)) if fs.fs.fat[i] == 0][:extra]
        assert len(free) == extra, 'Insufficient FAT clusters'
        chain += free
    for current, nxt in zip(chain, chain[1:]):
        fs.fs.fat[current] = nxt
    fs.fs.fat[chain[-1]] = 0x0FFFFFFF
    fs.fs.flush_fat()
    with wim.open('rb') as src:
        for cluster in chain[:needed]:
            fs.fs.write_data_to_cluster(src.read(block).ljust(block, b'\0'), cluster, extend_cluster=False)
    entry.filesize = wim.stat().st_size
    fs.fs.update_directory_entry(entry.get_parent_dir())
    assert fs.readbytes('/EFI/BOOT/BOOTAA64.EFI') == boot_before
    with fs.openbin('/SOURCES/BOOT.WIM', 'r') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    with wim.open('rb') as stream:
        assert actual == hashlib.file_digest(stream, 'sha256').hexdigest()
with target.open('rb') as stream:
    sha = hashlib.file_digest(stream, 'sha256').hexdigest().upper()
report = dict(result='PASS', path=str(target), bytes=target.stat().st_size,
              base_sha256=expected, sha256=sha, wim_sha256=actual,
              boot_sha256=hashlib.sha256(boot_before).hexdigest().upper(),
              scope=('Own WinPE server and startup lifetime helpers only; not a stream PASS'
                     if args.shell_lifetime else 'Only own WinPE server binary changes; not a stream PASS'))
(folder / (variant+'-disk-report.json')).write_text(json.dumps(report, indent=2))
print(json.dumps(report), flush=True)
