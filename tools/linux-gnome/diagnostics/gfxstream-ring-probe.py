"""One isolated Gfxstream ICD enumeration. Never installs a global ICD."""
import ctypes, fcntl, json, os, pathlib, struct, subprocess, time, urllib.request
base = pathlib.Path.home()/'.local/state/uavf/vulkan-probe'
base.mkdir(parents=True, exist_ok=True)
# Run on Gfxstream only, never repeatedly test production VirGL.
fd=os.open('/dev/dri/renderD128', os.O_RDWR|os.O_CLOEXEC)
value=ctypes.c_uint64()
fcntl.ioctl(fd,0xc0106443,bytearray(struct.pack('<QQ',7,ctypes.addressof(value))),True)
os.close(fd)
if not value.value & (1 << 3):
    print('ALIGNED_RING_PROBE_SKIP_NON_GFXSTREAM', value.value)
    raise SystemExit(0)
if (base/'aligned-ring-result.json').exists():
    print('ALIGNED_RING_PROBE_ALREADY_COMPLETED')
    raise SystemExit(0)
lib = base/'libvulkan_gfxstream.so'
for attempt in range(6):
    try:
        lib.write_bytes(urllib.request.urlopen('http://10.159.93.215:39056/libvulkan_gfxstream.so', timeout=10).read())
        break
    except OSError:
        if attempt == 5: raise
        time.sleep(2)
icd = base/'gfxstream.json'
icd.write_text(json.dumps({'file_format_version':'1.0.1', 'ICD':
                         {'library_path':str(lib), 'api_version':'1.1.318'}}))
env = dict(os.environ, VK_DRIVER_FILES=str(icd))
report = {'kind':'gfxstream-aligned-ring-probe', 'global_icd_mutated':False,
          'capset_mask':value.value}
for name, args in [('ldd',['ldd',str(lib)]),
                   ('vulkaninfo',['vulkaninfo','--summary'])]:
    try:
        p = subprocess.run(args, env=env, capture_output=True, text=True, timeout=40)
        report[name] = {'exit':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
    except subprocess.TimeoutExpired as e:
        report[name] = {'timeout':True,'stdout':str(e.stdout),'stderr':str(e.stderr)}
out = base/'aligned-ring-result.json'
out.write_text(json.dumps(report,indent=2))
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
    data=out.read_bytes(), method='POST'), timeout=15).read()
print('ALIGNED_RING_PROBE_SAVED', out)
