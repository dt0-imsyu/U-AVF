"""One-time installed test deployment. Runtime itself has no HTTP dependency."""
import hashlib,json,pathlib,subprocess,tempfile,urllib.request
base='http://10.159.93.215:39054/'
report={'stage':'installed-encoded-update','installer_live_changed':False}
try:
    with tempfile.TemporaryDirectory(prefix='uavf-encoded-update-') as tmp:
        directory=pathlib.Path(tmp)
        names=('uavf-encoded-display.py','uavf-encoded-capture.py','winavf-frame-bridge.py',
               'uavf-encoded.socket','uavf-encoded@.service','install-uavf-encoded-addon.sh')
        report['payload_sha256']={}
        for name in names:
            data=urllib.request.urlopen(base+name,timeout=20).read()
            directory.joinpath(name).write_bytes(data)
            report['payload_sha256'][name]=hashlib.sha256(data).hexdigest()
        result=subprocess.run(['sudo','-n','sh',str(directory/'install-uavf-encoded-addon.sh')],capture_output=True,text=True,timeout=30)
        report.update(exit=result.returncode,stdout=result.stdout,stderr=result.stderr)
        report['result']='PASS' if result.returncode==0 else 'FAIL'
except Exception as error: report.update(result='FAIL',error=str(error))
urllib.request.urlopen(urllib.request.Request(base+'report',data=json.dumps(report,indent=2).encode(),method='POST'),timeout=15).read()
print('ENCODED_UPDATE='+report['result'],flush=True)
