"""Read-only installed encoded runtime evidence."""
import hashlib,json,pathlib,subprocess,urllib.request
report={}
commands={
    'encoded_units':['systemctl','list-units','uavf-encoded*','--no-pager'],
    'encoded_journal':['sudo','-n','journalctl','-b','--no-pager','-n','5000','-u','uavf-encoded@*'],
    'raw_instances':['systemctl','list-units','winavf-frame@*','--no-pager'],
    'mode':['xrandr','--current'],
    'processes':['ps','-eo','user,pid,comm,args'],
}
for key,command in commands.items():
    result=subprocess.run(command,capture_output=True,text=True,timeout=10)
    output=result.stdout
    if key == 'encoded_journal':
        output='\n'.join(line for line in output.splitlines() if 'INPUT_EVENT' not in line)
    report[key]={'exit':result.returncode,'stdout':output,'stderr':result.stderr}
report['runtime_sha256']={p.name:hashlib.sha256(p.read_bytes()).hexdigest()
    for p in pathlib.Path('/usr/local/lib/uavf-encoded').glob('*.py')}
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=json.dumps(report,indent=2).encode(),method='POST'),timeout=15).read()
print('ENCODED_INSTALLED_AUDIT_SAVED',flush=True)
