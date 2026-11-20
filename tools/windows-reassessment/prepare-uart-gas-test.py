"""Create or change only the designated disposable Windows KD test disk."""
import argparse, hashlib, json, shutil
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS

SOURCE = Path(r'E:\winavf-kd-shell-kdcom-clone-20260907.img')
ROOT = Path(r'C:\Users\denis\MainProjects\win11ontab')
LOG = ROOT / 'build-logs/windows-post-linux-reassessment-20261004'
TARGET = LOG / 'windows-kd-uart-gas-20261004-winpe.img'
FD_SHA = '9ED3DD035116A39FEAC7C79A9C0C9AC640EA222D499A8EC3B1C6FCEE1ADFE72F'
BOOT_SHA = 'EFCC88441775A1ECEF644E05A52D7A01DC98388EA4426F133B9132CBE1483A19'
BCD_SHA = '034362E7531FF242E32F24F1ABD37EBECA05CC888981D95094E7986A75521784'
def sha(data): return hashlib.sha256(data).hexdigest().upper()
def main():
    p=argparse.ArgumentParser();p.add_argument('--mode',choices=['control','gas8','gas8-noraw','gas8-runtime-quiet','gas8-runtime-quiet-ro','gas8-gic-audit','gas8-gic-ds-probe','gas8-gic-ds-fix','gas8-gic-ds-ebs'],required=True);p.add_argument('--create',action='store_true');a=p.parse_args()
    efi=LOG / ('uart-'+a.mode+'.efi')
    assert efi.is_file()
    if a.create:
        if TARGET.exists(): raise SystemExit('Refuse existing target')
        assert SOURCE.stat().st_size==9126805504
        # Audit source payload BEFORE copying; never open source for writes.
        with PyFatFS(str(SOURCE),offset=1048576,read_only=True) as f:
            assert sha(f.readbytes('/EFI/BOOT/BOOTAA64.EFI'))==BOOT_SHA
            assert sha(f.readbytes('/EFI/microsoft/BOOT/BCD'))==BCD_SHA
            # WinPE-only diagnostic: retain original FAT/GPT geometry, dirs and
            # every allocated cluster except the unused OS installation payload.
            # Both A/B runs share this same disk. It is not an installation image.
            excluded=set()
            omitted=[]
            for path in f.walk.files():
                if f.getsize(path)>1024**3:
                    if path.upper() not in ['/SOURCES/INSTALL.WIM','/SOURCES/INSTALL.ESD','/SOURCES/INSTALL.SWM','/SOURCES/INSTALL2.SWM']:
                        raise SystemExit('Unexpected large file: '+path)
                    entry=f.fs.root_dir.get_entry(path)
                    excluded.update(f.fs.get_cluster_chain(entry.get_cluster()))
                    omitted.append(path)
            data_start=1048576+f.fs.first_data_sector*512
            cluster_bytes=f.fs.bytes_per_cluster
            spans=[(0,data_start)]
            for cluster,value in enumerate(f.fs.fat[2:],2):
                if not value or cluster in excluded: continue
                start=data_start+(cluster-2)*cluster_bytes
                if spans[-1][0]+spans[-1][1]==start:
                    old,length=spans[-1];spans[-1]=(old,length+cluster_bytes)
                else:spans.append((start,cluster_bytes))
        fd=(ROOT/'firmware-work/edk2/artifacts/KVMTOOL_EFI-r11-debug-uart-align.fd').read_bytes()
        assert len(fd)==2097152 and sha(fd)==FD_SHA
        TARGET.parent.mkdir(exist_ok=True)
        with SOURCE.open('rb') as src,TARGET.open('xb') as dst:
            dst.truncate(9126805504)
            for start,length in spans:
                src.seek(start);dst.seek(start)
                while length:
                    block=src.read(min(length,32*1024*1024))
                    if not block:raise SystemExit('Source EOF')
                    dst.write(block);length-=len(block)
        print('WINPE_ONLY_OMITTED_INSTALL_PAYLOAD='+repr(omitted),flush=True)
        with TARGET.open('r+b') as raw:
            raw.seek(7250927616);raw.write(fd);raw.flush()
        with PyFatFS(str(TARGET),offset=1048576) as f:
            f.writebytes('/EFI/BOOT/WINORIG.EFI',f.readbytes('/EFI/BOOT/BOOTAA64.EFI'))
    assert TARGET.is_file() and TARGET.stat().st_size==9126805504
    spans=[]
    with PyFatFS(str(TARGET),offset=1048576) as f:
        assert sha(f.readbytes('/EFI/BOOT/WINORIG.EFI'))==BOOT_SHA
        assert sha(f.readbytes('/EFI/microsoft/BOOT/BCD'))==BCD_SHA
        payload=efi.read_bytes().ljust(8192,b'\0')
        assert len(payload)==8192
        # Never truncate this existing file via FatIO: the retained image has
        # a surplus chain after the short shim. Reuse its first allocated
        # cluster and terminate it explicitly, with no allocation/freeing.
        entry=f.fs.root_dir.get_entry('/EFI/BOOT/BOOTAA64.EFI')
        cluster=entry.get_cluster()
        assert f.fs.bytes_per_cluster==8192 and f.fs.fat[cluster]!=0
        f.fs.fat[cluster]=0x0FFFFFFF
        f.fs.flush_fat()
        f.fs.write_data_to_cluster(payload,cluster,extend_cluster=False)
        entry.filesize=len(payload)
        f.fs.update_directory_entry(entry.get_parent_dir())
        assert sha(f.readbytes('/EFI/BOOT/BOOTAA64.EFI'))==sha(payload)
        data_start=1048576+f.fs.first_data_sector*512
        spans=[(0,data_start)]
        for path in ['/EFI/BOOT','/EFI/BOOT/BOOTAA64.EFI']:
            entry=f.fs.root_dir.get_entry(path)
            for c in f.fs.get_cluster_chain(entry.get_cluster()):
                spans.append((data_start+(c-2)*f.fs.bytes_per_cluster,f.fs.bytes_per_cluster))
    manifest=[]
    with TARGET.open('rb') as raw:
        for index,(offset,length) in enumerate(spans):
            raw.seek(offset);block=raw.read(length);assert len(block)==length
            name=f'uart-{a.mode}-block-{index}.bin';(LOG/name).write_bytes(block)
            manifest.append(dict(file=name,offset=offset,length=length,sha256=sha(block)))
    (LOG/f'uart-{a.mode}-blocks.json').write_text(json.dumps(manifest,indent=2))
    with TARGET.open('rb') as raw:
        raw.seek(7250927616);assert sha(raw.read(2097152))==FD_SHA
        raw.seek(0);digest=hashlib.file_digest(raw,'sha256').hexdigest().upper()
    report=dict(result='PASS',mode=a.mode,target=str(TARGET),disk_sha256=digest,boot_sha256=BOOT_SHA,bcd_sha256=BCD_SHA,fd_sha256=FD_SHA,shim_sha256=sha(efi.read_bytes()))
    (LOG/('uart-'+a.mode+'-prepare.json')).write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2))
if __name__=='__main__': main()
