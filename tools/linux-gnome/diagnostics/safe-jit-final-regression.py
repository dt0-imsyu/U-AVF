"""Read policy and real GL renderer; bounded ordinary app startup checks."""
import json, pathlib, subprocess, urllib.request
report={}
for name,args in [('setup',['/usr/local/bin/uavf-safe-jit','--setup-desktop']),('vkmark_policy',['/usr/local/bin/uavf-safe-jit','--probe','vkmark']),('snap_policy',['/usr/local/bin/uavf-safe-jit','--probe','snap-store'])]:
    p=subprocess.run(args,text=True,capture_output=True,timeout=10)
    report[name]={'exit':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
source=urllib.request.urlopen('http://10.159.93.215:39054/uavf-encoded-capture.py',timeout=15).read().decode()
# Run only context creation and renderer enumeration, before any capture/readback.
snippet=source.split("    bind_texture=proc('glBindTexture'")[0]
snippet+='\nexcept Exception as e:\n report["error"]=str(e)\nprint(json.dumps(report))\n'
p=subprocess.run(['python3','-c',snippet],text=True,capture_output=True,timeout=15)
report['opengl']={'exit':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
for name,args in [('firefox',['firefox','--new-window','about:blank']),('code',['code','--new-window'])]:
    log=pathlib.Path('/tmp/uavf-sigill-cores')/(name+'-ordinary-startup.log')
    try:
        with log.open('w') as f:
            p=subprocess.Popen(args,stdout=f,stderr=subprocess.STDOUT)
            try:code=p.wait(timeout=8);alive=False
            except subprocess.TimeoutExpired:code=None;alive=True
        report[name]={'exit':code,'alive_at_deadline':alive,'pid':p.pid,'log':log.read_text(errors='replace')[-5000:]}
    except Exception as e:report[name]={'error':str(e)}
desktop=pathlib.Path.home()/'.local/share/applications/snap-store_snap-store.desktop'
report['desktop_entry']=desktop.read_text() if desktop.exists() else None
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-sigill-cores/final-regression.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=payload,method='POST'),timeout=15).read()
print('SAFE_JIT_FINAL_REGRESSION_SAVED',flush=True)
