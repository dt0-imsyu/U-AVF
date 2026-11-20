"""Compare local Debian package binaries to actual APK initrd inventory hashes."""
import argparse,hashlib,io,json,tarfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--inventory',type=Path,required=True)
p.add_argument('--deb-root',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
inventory=json.loads(a.inventory.read_text(encoding='utf-8'))
shipped={}
for platform in inventory['platforms']:
    for item in platform['binary_notice_and_local_inventory']:
        shipped.setdefault(item['sha256'],[]).append(platform['asset']+':'+item['path'])
reports=[]
for deb in sorted(a.deb_root.rglob('*.deb')):
    data=deb.read_bytes()
    assert data[:8]==b'!<arch>\n',str(deb)
    members={};pos=8
    while pos+60<=len(data):
        header=data[pos:pos+60];size=int(header[48:58]);name=header[:16].decode().strip().rstrip('/')
        members[name]=data[pos+60:pos+60+size];pos+=60+size+(size&1)
    control=next(name for name in members if name.startswith('control.tar'))
    with tarfile.open(fileobj=io.BytesIO(members[control]),mode='r:*') as archive:
        entry=next(item for item in archive if item.name.strip('./')=='control')
        metadata=archive.extractfile(entry).read().decode()
    content=next(name for name in members if name.startswith('data.tar'))
    matches=[]
    with tarfile.open(fileobj=io.BytesIO(members[content]),mode='r:*') as archive:
        for entry in archive:
            if not entry.isfile():continue
            stream=archive.extractfile(entry)
            digest=hashlib.file_digest(stream,'sha256').hexdigest().upper()
            if digest in shipped:matches.append({'package_file':entry.name,'sha256':digest,'shipped':shipped[digest]})
    fields={line.split(':',1)[0]:line.split(':',1)[1].strip() for line in metadata.splitlines() if line and not line[0].isspace() and ':' in line}
    reports.append({'deb':str(deb),'deb_sha256':hashlib.sha256(data).hexdigest().upper(),
                    'package':fields.get('Package'),'version':fields.get('Version'),
                    'source':fields.get('Source',fields.get('Package')),'matches':matches})
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(reports,indent=2)+'\n',encoding='utf-8')
for report in reports:
    if report['matches']:print(report['package'],report['version'],'MATCHED_FILES='+str(len(report['matches'])))
print('DEB_PROVENANCE_MATCH_COMPLETE; CORRESPONDING_SOURCE_STILL_REQUIRED')
