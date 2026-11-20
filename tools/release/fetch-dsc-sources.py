"""Fetch exact source-package artifacts over HTTPS, verify DSC SHA256/size.

Does not claim PGP signature verification or whole-APK source completeness.
Never extracts archives or executes downloaded code.
"""
import argparse,hashlib,json,re,urllib.parse,urllib.request
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--dsc',action='append',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
manifest=a.output/'download-manifest.json'
reports=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else []
assert isinstance(reports,list), 'Invalid existing source manifest'
for url in a.dsc:
    parsed=urllib.parse.urlparse(url)
    assert parsed.scheme=='https' and parsed.hostname in ('ports.ubuntu.com','archive.ubuntu.com'), 'Unapproved source host'
    name=Path(urllib.parse.unquote(parsed.path)).name
    assert re.fullmatch(r'[A-Za-z0-9_.+~-]+\.dsc',name)
    with urllib.request.urlopen(url,timeout=45) as request:data=request.read(262145)
    assert len(data)<=262144
    text=data.decode()
    block=text.split('Checksums-Sha256:\n',1)[1]
    refs=[]
    for line in block.splitlines():
        if not line.startswith(' '):break
        digest,size,filename=line.split()
        assert re.fullmatch(r'[0-9a-f]{64}',digest) and Path(filename).name==filename
        assert re.fullmatch(r'[A-Za-z0-9_.+~-]+',filename)
        size=int(size);assert 0<size<1024*1024*1024
        target=a.output/filename
        if target.exists():payload=target.read_bytes()
        else:
            with urllib.request.urlopen(urllib.parse.urljoin(url,filename),timeout=45) as response:payload=response.read(size+1)
        assert len(payload)==size and hashlib.sha256(payload).hexdigest()==digest,filename
        if not target.exists():target.write_bytes(payload)
        refs.append({'name':filename,'bytes':size,'sha256':digest.upper()})
    assert refs
    target=a.output/name
    if target.exists():assert target.read_bytes()==data
    else:target.write_bytes(data)
    record={'dsc':name,'url':url,'dsc_sha256':hashlib.sha256(data).hexdigest().upper(),
            'artifacts':refs,'pgp_signature_verified':False}
    previous=[r for r in reports if r['dsc']==name]
    assert all(r['dsc_sha256']==record['dsc_sha256'] and r['artifacts']==refs
               for r in previous), 'Conflicting existing source evidence'
    if not previous:reports.append(record)
    print('DSC_SOURCE_CHECKSUMS=PASS '+name,flush=True)
manifest.write_text(json.dumps(reports,indent=2)+'\n',encoding='utf-8')
