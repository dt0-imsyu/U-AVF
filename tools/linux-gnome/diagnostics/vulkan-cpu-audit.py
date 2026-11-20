"""Read-only guest DRM/Vulkan/CPU inventory; no GPU configuration changes."""
import ctypes as C
import fcntl,json,os,pathlib,struct,subprocess,urllib.request
report={}
def run(key,args,timeout=20):
    try:
        p=subprocess.run(args,capture_output=True,text=True,timeout=timeout)
        report[key]={'exit':p.returncode,'stdout':p.stdout[-180000:],'stderr':p.stderr[-24000:]}
    except Exception as e: report[key]={'error':str(e)}
for key,args in {
    'uname':['uname','-a'], 'cpuinfo':['cat','/proc/cpuinfo'], 'lscpu':['lscpu'],
    'packages':['dpkg-query','-W','mesa-vulkan-drivers','libgl1-mesa-dri','vulkan-tools','gdb','binutils'],
    'glxinfo':['glxinfo','-B'], 'vulkaninfo':['vulkaninfo','--summary'],
    'snap_list':['snap','list'], 'crash_files':['ls','-lah','/var/crash'],
    'coredumps':['coredumpctl','--no-pager','list'],
    'kernel':['sudo','-n','journalctl','-k','-b','--no-pager'],
    'drm_sysfs':['sh','-c','ls -l /dev/dri; cat /sys/bus/pci/devices/0000:00:01.0/virtio*/features'],
}.items(): run(key,args,30)
report['icds']={str(p):p.read_text() for root in ['/usr/share/vulkan/icd.d','/etc/vulkan/icd.d']
    for p in pathlib.Path(root).glob('*.json')}
report['venus_libraries']=[str(p) for p in pathlib.Path('/usr/lib').glob('**/*vulkan*virtio*')]
libc=C.CDLL(None); libc.getauxval.restype=C.c_ulong
report['auxv']={'AT_HWCAP':hex(libc.getauxval(16)),'AT_HWCAP2':hex(libc.getauxval(26))}
params={1:'3D_FEATURES',2:'CAPSET_QUERY_FIX',3:'RESOURCE_BLOB',4:'HOST_VISIBLE',
        5:'CROSS_DEVICE',6:'CONTEXT_INIT',7:'SUPPORTED_CAPSET_IDS',9:'BLOB_ALIGNMENT'}
report['drm_params']={}
try:
    fd=os.open('/dev/dri/renderD128',os.O_RDWR|os.O_CLOEXEC)
    for param,name in params.items():
        value=C.c_uint64(); request=bytearray(struct.pack('<QQ',param,C.addressof(value)))
        try:
            fcntl.ioctl(fd,0xc0106443,request,True)
            report['drm_params'][name]=value.value
        except OSError as e: report['drm_params'][name]={'errno':e.errno,'error':str(e)}
    os.close(fd)
except Exception as e: report['drm_error']=str(e)
# Force only an existing Venus ICD for one bounded enumeration; no global env change.
for name in report['icds']:
    if 'virtio' in name:
        run('venus_forced',['env','VK_DRIVER_FILES='+name,'VK_ICD_FILENAMES='+name,
             'vulkaninfo','--summary'],20)
payload=json.dumps(report,indent=2).encode()
pathlib.Path('/tmp/uavf-vulkan-cpu-audit.json').write_bytes(payload)
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
    data=payload,method='POST'),timeout=20).read()
print('VULKAN_CPU_AUDIT_SAVED',flush=True)
