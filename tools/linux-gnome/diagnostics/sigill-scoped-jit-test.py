"""Bounded application-only LLVM_CPUINFO A/B; never alters VM CPU/kernel or session environment."""
import ctypes, json, os, pathlib, subprocess, time, urllib.request
report={}
features=next(x for x in pathlib.Path('/proc/cpuinfo').read_text().splitlines() if x.startswith('Features'))
# LLVM's documented diagnostic override: retain actual features, but do not
# infer extra ISA from a physical MIDR model that the hypervisor has restricted.
fixture=pathlib.Path.home()/'snap/snap-store/common/uavf-jit-cpuinfo'
fixture.parent.mkdir(parents=True,exist_ok=True)
fixture.write_text('processor : 0\n'+features+'\nCPU architecture : 8\n')
env=dict(os.environ,LLVM_CPUINFO=str(fixture),MESA_SHADER_CACHE_DISABLE='true')
probe='import ctypes; l=ctypes.CDLL("/usr/lib/aarch64-linux-gnu/libLLVM.so.20.1"); l.LLVMGetHostCPUName.restype=ctypes.c_char_p; print(l.LLVMGetHostCPUName().decode())'
p=subprocess.run(['python3','-c',probe],env=env,capture_output=True,text=True,timeout=10)
report['scoped_target']={'name':p.stdout.strip(),'features':features,'fixture':str(fixture),'stderr':p.stderr}
for name,args,seconds in [('vkmark',['vkmark'],15),('snap-store',['snap','run','snap-store'],20)]:
    log=pathlib.Path('/tmp/uavf-sigill-cores')/(name+'-scoped-jit.log')
    with log.open('w') as f:
        t=time.monotonic();p=subprocess.Popen(args,env=env,stdout=f,stderr=subprocess.STDOUT)
        try:
            code=p.wait(timeout=seconds);alive=False
        except subprocess.TimeoutExpired:
            alive=True;p.terminate()
            try:code=p.wait(timeout=5)
            except subprocess.TimeoutExpired:p.kill();code=p.wait()
    report[name]={'survived_window':alive,'observed_seconds':time.monotonic()-t,'exit':code,'stdout':log.read_text(errors='replace')[-16000:]}
    report[name]['target_diagnostics']=[line for line in log.read_text(errors='replace').splitlines() if 'mcpu' in line or 'mattr' in line or 'SVE' in line]
    if code == -4:
        exe='/usr/bin/vkmark' if name=='vkmark' else '/snap/snap-store/1428/bin/snap-store'
        core='/tmp/uavf-sigill-cores/'+name+'-scoped.core'
        d=subprocess.run(['coredumpctl','dump',exe,'--output='+core],text=True,capture_output=True,timeout=30)
        report[name]['dump_stderr']=d.stderr
        if pathlib.Path(core).is_file():
            g=subprocess.run(['gdb','-q','-batch',exe,core,'-ex','info registers pc','-ex','x/8i $pc-8','-ex','bt'],text=True,capture_output=True,timeout=60)
            report[name]['fault']=g.stdout[-12000:]
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-sigill-cores/scoped-jit.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=payload,method='POST'),timeout=20).read()
print('SCOPED_JIT_TEST_SAVED',flush=True)
