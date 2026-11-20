"""Read LLVM feature detection and execute only the identified opcode in isolated children."""
import ctypes, json, mmap, os, pathlib, resource, struct, urllib.request
report={}
for path in ['/usr/lib/aarch64-linux-gnu/libLLVM.so.20.1','/snap/mesa-2404/1836/usr/lib/aarch64-linux-gnu/libLLVM.so.20.1']:
    try:
        lib=ctypes.CDLL(path)
        result={}
        for symbol in ['LLVMGetHostCPUName','LLVMGetHostCPUFeatures']:
            fun=getattr(lib,symbol);fun.restype=ctypes.c_void_p;fun.argtypes=[]
            ptr=fun();result[symbol]=ctypes.string_at(ptr).decode()
            lib.LLVMDisposeMessage.argtypes=[ctypes.c_void_p];lib.LLVMDisposeMessage(ptr)
        report[path]=result
    except Exception as e:report[path]={'error':str(e)}
tests=[]
for cpu in sorted(os.sched_getaffinity(0)):
    pid=os.fork()
    if pid==0:
        resource.setrlimit(resource.RLIMIT_CORE,(0,0))
        os.sched_setaffinity(0,{cpu})
        # INDEX z4.s,#0,#1, identical to snap-store core, then RET.
        buf=mmap.mmap(-1,4096,prot=mmap.PROT_READ|mmap.PROT_WRITE|mmap.PROT_EXEC)
        buf.write(struct.pack('<II',0x04a14004,0xd65f03c0))
        ptr=ctypes.addressof(ctypes.c_char.from_buffer(buf))
        ctypes.CFUNCTYPE(None)(ptr)()
        os._exit(0)
    _,status=os.waitpid(pid,0)
    tests.append({'vcpu':cpu,'signal':os.WTERMSIG(status) if os.WIFSIGNALED(status) else None,'exit':os.WEXITSTATUS(status) if os.WIFEXITED(status) else None})
report['exact_index_opcode_per_vcpu']=tests
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-sigill-cores/sve-llvm.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=payload,method='POST'),timeout=20).read()
print('SVE_LLVM_AUDIT_SAVED',flush=True)
