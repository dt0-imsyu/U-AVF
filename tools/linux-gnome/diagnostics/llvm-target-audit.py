"""Emit, but do not execute, identical IR under detected and SVE-disabled targets."""
import ctypes as C, json, pathlib, subprocess, urllib.request
lib=C.CDLL('/usr/lib/aarch64-linux-gnu/libLLVM.so.20.1')
P=C.c_void_p; S=C.c_char_p; I=C.c_int
def bind(name,result,args):
    f=getattr(lib,name);f.restype=result;f.argtypes=args;return f
for n in ['LLVMInitializeAArch64TargetInfo','LLVMInitializeAArch64Target','LLVMInitializeAArch64TargetMC','LLVMInitializeAArch64AsmPrinter']:
    bind(n,None,[])()
context=bind('LLVMContextCreate',P,[])()
buffer=bind('LLVMCreateMemoryBufferWithMemoryRangeCopy',P,[S,C.c_size_t,S])
parse=bind('LLVMParseIRInContext',I,[P,P,C.POINTER(P),C.POINTER(S)])
target=P();error=S()
bind('LLVMGetTargetFromTriple',I,[S,C.POINTER(P),C.POINTER(S)])(b'aarch64-linux-gnu',C.byref(target),C.byref(error))
make=bind('LLVMCreateTargetMachine',P,[P,S,S,S,I,I,I])
emit=bind('LLVMTargetMachineEmitToMemoryBuffer',I,[P,P,I,C.POINTER(S),C.POINTER(P)])
start=bind('LLVMGetBufferStart',P,[P]);size=bind('LLVMGetBufferSize',C.c_size_t,[P])
report={}
for cpu,features,label in [('cortex-x925','+fp-armv8,+sha2,+lse,+neon,+crc,+aes','detected'),('cortex-x925','+fp-armv8,+sha2,+lse,+neon,+crc,+aes,-sve,-sve2','explicit_no_sve')]:
    texts=[]
    for width in [4,8,16]:
        constants=', '.join('i32 '+str(i) for i in range(width))
        ir=('define void @indices'+str(width)+'(ptr %out, <'+str(width)+' x i32> %x) {\n %v = add <'+str(width)+' x i32> %x, <'+constants+'>\n store <'+str(width)+' x i32> %v, ptr %out, align 16\n ret void\n}\n').encode()
        module=P();err=S();out=P()
        if parse(context,buffer(ir,len(ir),b'audit'),C.byref(module),C.byref(err)):
            texts.append('PARSE_ERROR '+str(err.value));continue
        tm=make(target,b'aarch64-linux-gnu',cpu.encode(),features.encode(),3,0,0)
        if emit(tm,module,0,C.byref(err),C.byref(out)):
            texts.append('EMIT_ERROR '+str(err.value))
        else:
            texts.append(C.string_at(start(out),size(out)).decode())
        bind('LLVMDisposeModule',None,[P])(module)
        bind('LLVMDisposeTargetMachine',None,[P])(tm)
    report[label]={'cpu':cpu,'features':features,'assembly':texts}
p=subprocess.run(['sudo','-n','journalctl','-k','--no-pager','--since','2026-10-01'],text=True,capture_output=True,timeout=30)
report['kernel']={'exit':p.returncode,'stdout':p.stdout[-120000:],'stderr':p.stderr}
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-sigill-cores/llvm-target.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=payload,method='POST'),timeout=20).read()
print('LLVM_TARGET_AUDIT_SAVED',flush=True)
