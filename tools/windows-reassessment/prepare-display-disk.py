"""Build only a new disposable display disk; original candidate read-only."""
import hashlib,json,shutil,argparse,math
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS
root=Path(__file__).resolve().parents[3]
folder=root/'build-logs/windows-post-linux-reassessment-20261004'
source=folder/'windows-kd-uart-gas-20261004-winpe.img'
target=folder/'windows-display-winpe-20261007.img'
p=argparse.ArgumentParser(); p.add_argument('--restore-partial',action='store_true'); p.add_argument('--reuse-audited',action='store_true'); p.add_argument('--cam-shim',action='store_true'); args=p.parse_args()
assert not target.exists() or args.restore_partial or args.reuse_audited, 'Refuse overwrite'
if not args.reuse_audited:
    with source.open('rb') as stream:
        assert hashlib.file_digest(stream,'sha256').hexdigest().upper()=='87CD1D4486EEB0C29AB6FBA691F73B3B4C8AE5E0538532BDE14364311A34DB35'
if args.reuse_audited:
    expected=json.loads((folder/'display-disk-report.json').read_text())['sha256']
    with target.open('rb') as stream: assert hashlib.file_digest(stream,'sha256').hexdigest().upper()==expected
else: shutil.copyfile(source,target)
with PyFatFS(str(target),offset=1048576) as fs:
    # Preserve existing chain; FatIO truncate/reallocation failed on this image.
    entry=fs.fs.root_dir.get_entry('/SOURCES/BOOT.WIM')
    chain=list(fs.fs.get_cluster_chain(entry.get_cluster()))
    block=fs.fs.bytes_per_cluster
    wim=folder/'display-candidate.wim'; needed=math.ceil(wim.stat().st_size/block)
    extra=needed-len(chain)
    if extra>0:
        free=[i for i in range(2,len(fs.fs.fat)) if fs.fs.fat[i]==0][:extra]
        assert len(free)==extra, 'Insufficient free FAT clusters'
        chain+=free
    for current,nxt in zip(chain,chain[1:]): fs.fs.fat[current]=nxt
    fs.fs.fat[chain[-1]]=0x0FFFFFFF; fs.fs.flush_fat()
    with wim.open('rb') as src:
        for cluster in chain[:needed]:
            data=src.read(block)
            fs.fs.write_data_to_cluster(data.ljust(block,b'\0'),cluster,extend_cluster=False)
    entry.filesize=wim.stat().st_size
    fs.fs.update_directory_entry(entry.get_parent_dir())
    fs.writebytes('/UAVF-DISPLAY.TAG',b'U-AVF disposable screenshot target 2026-10-07\r\n')
    if args.cam_shim:
        payload=(folder/'uart-windows-cam.efi').read_bytes()
        assert len(payload)==8192 and fs.fs.bytes_per_cluster==8192
        entry=fs.fs.root_dir.get_entry('/EFI/BOOT/BOOTAA64.EFI')
        assert entry.filesize==8192
        fs.fs.write_data_to_cluster(payload,entry.get_cluster(),extend_cluster=False)
        assert fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')==payload
    boot_sha=hashlib.sha256(fs.readbytes('/EFI/BOOT/BOOTAA64.EFI')).hexdigest().upper()
    with fs.openbin('/SOURCES/BOOT.WIM','r') as stream:
        actual=hashlib.file_digest(stream,'sha256').hexdigest()
    with (folder/'display-candidate.wim').open('rb') as stream:
        assert actual==hashlib.file_digest(stream,'sha256').hexdigest()
with target.open('rb') as stream:
    sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
report=dict(result='PASS',path=str(target),sha256=sha,wim_sha256=actual,boot_sha256=boot_sha)
(folder/'display-disk-report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report),flush=True)
