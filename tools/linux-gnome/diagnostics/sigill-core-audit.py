"""Inspect existing app cores without changing CPU flags or relaunching apps."""
import json,pathlib,subprocess,urllib.request,os
report={}
def run(key,args,timeout=30):
    try:
        p=subprocess.run(args,text=True,capture_output=True,timeout=timeout)
        report[key]={'exit':p.returncode,'stdout':p.stdout[-180000:],'stderr':p.stderr[-30000:]}
    except Exception as e:report[key]={'error':str(e)}
base=pathlib.Path('/tmp/uavf-sigill-cores');base.mkdir(exist_ok=True)
for name,pid,exe in [('snap-store',4809,'/snap/snap-store/1428/bin/snap-store'),
                     ('vkmark',6542,'/usr/bin/vkmark')]:
    core=base/(name+'.core')
    run(name+'_info',['coredumpctl','--no-pager','info',str(pid)])
    run(name+'_dump',['coredumpctl','dump',str(pid),'--output='+str(core)])
    if core.is_file():
        run(name+'_gdb',['gdb','-q','-batch',exe,str(core),
            '-ex','set pagination off','-ex','info registers pc cpsr',
            '-ex','x/12i $pc-20','-ex','x/12wx $pc-20',
            '-ex','bt','-ex','info sharedlibrary','-ex','info proc mappings'],90)
    run(name+'_elf',['readelf','-h','-A','-n',exe])
run('kernel',['sudo','-n','journalctl','-k','--no-pager','--since','2026-10-01'],30)
run('cpuinfo',['cat','/proc/cpuinfo'])
run('lscpu',['lscpu'])
payload=json.dumps(report,indent=2).encode()
(base/'report.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
    data=payload,method='POST'),timeout=20).read()
print('SIGILL_EXISTING_CORES_SAVED',flush=True)
