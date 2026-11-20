"""Read-only equality check of retained WinPE payload; no installation SWMs."""
import hashlib, json
from pyfatfs.PyFatFS import PyFatFS
from pathlib import Path
root=Path(__file__).resolve().parents[3]
log=root/'build-logs/windows-post-linux-reassessment-20261004'
images=[Path(r'E:\winavf-kd-shell-kdcom-clone-20260907.img'),log/'windows-kd-uart-gas-20261004-winpe.img']
result=[]
for image in images:
    with PyFatFS(str(image),offset=1048576,read_only=True) as fs:
        with fs.openbin('/SOURCES/BOOT.WIM','r') as stream:
            digest=hashlib.file_digest(stream,'sha256').hexdigest().upper()
        result.append(dict(image=str(image),sha256=digest,size=fs.getsize('/SOURCES/BOOT.WIM')))
        print(json.dumps(result[-1]),flush=True)
assert result[0]['sha256']==result[1]['sha256']
(log/'boot-wim-equality.json').write_text(json.dumps(dict(result='PASS',files=result),indent=2))
