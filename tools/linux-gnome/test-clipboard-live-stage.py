"""Run the real clipboard init-bottom block against disposable mock roots."""
import os, shutil, subprocess, tempfile
from pathlib import Path

guest=Path(__file__).resolve().parent/'guest'
hook=(guest/'99-winavf-vsock-systemd').read_text()
block=hook.split('# Keep the independent clipboard agent',1)[1].split('if [ -x /usr/local/sbin/uavf-install-runtime.sh ]; then',1)[0]
block=block[block.index('rootfs='):].replace('"/usr/local/sbin/','"./initrd/usr/local/sbin/').replace('/dev/console','./console')
names=('uavf-clipboard.py','uavf-clipboard.socket','uavf-clipboard@.service')
for missing in (None,*names):
    with tempfile.TemporaryDirectory() as temporary:
        directory=Path(temporary);src=directory/'initrd/usr/local/sbin';src.mkdir(parents=True)
        for name in names:
            if name!=missing:(src/name).write_bytes((guest/name).read_bytes())
        env=os.environ.copy();env['rootmnt']='./root';env['PATH']=r'C:\msys64\usr\bin;'+env.get('PATH','')
        subprocess.run([r'C:\msys64\usr\bin\bash.exe','-eu','-c',block],cwd=directory,env=env,check=True)
        log=(directory/'console').read_text()
        if missing:
            assert 'PAYLOAD_MISSING' in log
            assert not (directory/'root/usr/local/sbin/uavf-clipboard.py').exists()
        else:
            assert 'READY PORT=4054' in log
            for name in names:assert (directory/'root/usr/local/sbin'/name).read_bytes()==(guest/name).read_bytes()
            for name in names[1:]:assert (directory/'root/etc/systemd/system'/name).read_bytes()==(guest/name).read_bytes()
            link=directory/'root/etc/systemd/system/sockets.target.wants/uavf-clipboard.socket'
            # MSYS may emulate symlinks as copies on Windows; both prove the target unit.
            assert link.exists()
print('CLIPBOARD_INITRD_TO_LIVE_STAGE=PASS')
print('ALL_THREE_MISSING_FILE_CASES_DETECTED=PASS')
