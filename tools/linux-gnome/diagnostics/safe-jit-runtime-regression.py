"""Bounded cold/warm cache regression for the production scoped launcher."""
import json, pathlib, subprocess, time, urllib.request
report={}
def get(name):
    path=pathlib.Path('/tmp')/name
    path.write_bytes(urllib.request.urlopen('http://10.159.93.215:39055/'+name,timeout=15).read())
    return str(path)
wrapper=get('uavf-safe-jit.py')
stager=get('stage-uavf-safe-jit.sh')
p=subprocess.run(['sh','-n',stager],text=True,capture_output=True)
report['shell_syntax']={'exit':p.returncode,'stderr':p.stderr}
for app in ['vkmark','snap-store']:
    p=subprocess.run(['python3',wrapper,'--probe',app],text=True,capture_output=True)
    report[app+'_policy']={'exit':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
    for passname in ['cold','warm']:
        out=pathlib.Path('/tmp/uavf-sigill-cores')/(app+'-production-'+passname+'.log')
        with out.open('w') as f:
            proc=subprocess.Popen(['python3',wrapper,app],stdout=f,stderr=subprocess.STDOUT)
            try:code=proc.wait(timeout=15);alive=False
            except subprocess.TimeoutExpired:
                alive=True;proc.terminate()
                try:code=proc.wait(timeout=5)
                except subprocess.TimeoutExpired:proc.kill();code=proc.wait()
        report[app+'_'+passname]={'exit':code,'alive_at_deadline':alive,'log':out.read_text(errors='replace')[-8000:]}
        if code == -4:
            break
for name,args in [('firefox_inventory',['pgrep','-a','firefox']),('code_inventory',['pgrep','-af','/code']),('gl_renderer_log',['sudo','-n','journalctl','--no-pager','-n','200','-u','uavf-encoded@*'])]:
    p=subprocess.run(args,text=True,capture_output=True,timeout=15)
    report[name]={'exit':p.returncode,'stdout':p.stdout[-10000:],'stderr':p.stderr}
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-sigill-cores/production-policy-regression.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=payload,method='POST'),timeout=15).read()
print('SAFE_JIT_RUNTIME_REGRESSION_SAVED',flush=True)
