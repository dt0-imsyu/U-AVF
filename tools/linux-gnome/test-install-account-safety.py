"""Execute the real embedded guard on disposable passwd/shadow fixtures."""
from pathlib import Path
import subprocess, sys, tempfile
runtime=(Path(__file__).parent/'guest/uavf-install-runtime.sh').read_text()
guard=runtime.split("<<'ACCOUNT_PY'",1)[1].split('\nACCOUNT_PY',1)[0]
account='uavf:x:1000:1000:U-AVF:/home/uavf:/bin/bash\n'
cases=[('locked','!',False),('empty','',False),('shadow-placeholder','x',False),
       ('locked-hash','!$6$salt$abcdefghijklmnopqrstuv',False),
       ('selected-hash','$6$salt$abcdefghijklmnopqrstuv',True)]
for name,password,accepted in cases:
    with tempfile.TemporaryDirectory() as directory:
        root=Path(directory);(root/'etc').mkdir()
        (root/'etc/passwd').write_text(account)
        (root/'etc/shadow').write_text('uavf:'+password+':20000:0:99999:7:::\n')
        result=subprocess.run([sys.executable,'-c',guard,str(root)],capture_output=True)
        assert (result.returncode==0)==accepted,name
print('INSTALL_ACCOUNT_GUARD=PASS (locked/empty/shadow-placeholder rejected, unlocked hash accepted)')
