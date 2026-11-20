"""Read local Windows ISO directly (UDF); never mount/write it or any VM disk."""
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

root=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(root/'build-logs/windows-post-linux-reassessment-20261004/udf-reader-tools'))
import pycdlib

source=root/'win11-iso-work/Win11_25H2_ARM64_ru-ru.iso'
destination=root/'build-logs/windows-post-linux-reassessment-20261004/install-payload-udf-20261008'
wimlib=root/'tools/wimlib-1.14.5/wimlib-imagex.exe'
assert not destination.exists(), 'Preserve prior evidence, no overwrite'
assert source.stat().st_size==7253645312
assert shutil.disk_usage(destination.parent).free>18*1024**3
destination.mkdir()
iso=pycdlib.PyCdlib()
iso.open(str(source))
try:
    record=iso.get_record(udf_path='/sources/install.wim')
    assert record.get_data_length()==6347029986
    target=destination/'install.wim'
    print('UDF_EXTRACT_BEGIN',flush=True)
    with target.open('xb') as out:
        iso.get_file_from_iso_fp(out,udf_path='/sources/install.wim',blocksize=1024*1024)
    assert target.stat().st_size==record.get_data_length()
finally:
    iso.close()
print('UDF_EXTRACT_COMPLETE',flush=True)
metadata=subprocess.check_output([str(wimlib),'info',str(target)],text=True,errors='replace')
assert metadata.count('Architecture:           ARM64')==3
assert 'Build:                  26200' in metadata
(destination/'wim-info.txt').write_text(metadata,encoding='utf-8')
with target.open('rb') as stream:payload_sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
print('PAYLOAD_SPLIT_BEGIN',flush=True)
subprocess.run([str(wimlib),'split',str(target),str(destination/'install.swm'),'3500'],check=True)
print('PAYLOAD_VERIFY_BEGIN',flush=True)
subprocess.run([str(wimlib),'verify',str(destination/'install.swm'),'--ref='+str(destination/'install*.swm')],check=True)
parts=[]
for path in sorted(destination.glob('install*.swm')):
    assert 0<path.stat().st_size<4*1024**3
    with path.open('rb') as stream:sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
    parts.append(dict(name=path.name,bytes=path.stat().st_size,sha256=sha))
assert len(parts)==2
with source.open('rb') as stream:iso_sha=hashlib.file_digest(stream,'sha256').hexdigest().upper()
report=dict(result='PASS',scope='Payload only; not Setup/display/installation PASS',
            local_iso=str(source),iso_sha256=iso_sha,source_unmodified=True,
            payload_sha256=payload_sha,payload_bytes=target.stat().st_size,
            architecture='ARM64',build='26200.6584',parts=parts,
            vm_disks_untouched=True,official_origin_not_reverified=True)
(destination/'payload-report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report),flush=True)
