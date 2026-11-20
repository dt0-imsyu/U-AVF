"""Bounded read-only encoder inventory; no packages/services/settings changed."""
import glob
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import sys

if '--va' in sys.argv:
    import ctypes as C
    import ctypes.util
    fd = os.open('/dev/dri/renderD128', os.O_RDWR)
    va = C.CDLL(ctypes.util.find_library('va') or 'libva.so.2')
    drm = C.CDLL(ctypes.util.find_library('va-drm') or 'libva-drm.so.2')
    drm.vaGetDisplayDRM.argtypes = [C.c_int]
    drm.vaGetDisplayDRM.restype = C.c_void_p
    display = drm.vaGetDisplayDRM(fd)
    va.vaInitialize.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_int)]
    major = C.c_int(); minor = C.c_int()
    result = va.vaInitialize(display, C.byref(major), C.byref(minor))
    va.vaErrorStr.argtypes = [C.c_int]; va.vaErrorStr.restype = C.c_char_p
    print(json.dumps({'vaInitialize': result, 'error': va.vaErrorStr(result).decode(),
                      'version': [major.value, minor.value]}), flush=True)
    if result == 0:
        va.vaMaxNumProfiles.argtypes = [C.c_void_p]
        n = min(256, va.vaMaxNumProfiles(display))
        profiles = (C.c_int * max(n, 1))(); count = C.c_int()
        va.vaQueryConfigProfiles.argtypes = [C.c_void_p, C.POINTER(C.c_int), C.POINTER(C.c_int)]
        status = va.vaQueryConfigProfiles(display, profiles, C.byref(count))
        print(json.dumps({'profile_status': status, 'profiles': list(profiles)[:count.value]}))
        va.vaTerminate.argtypes = [C.c_void_p]; va.vaTerminate(display)
    os.close(fd)
    sys.exit(0)

def run(argv, timeout=12):
    try:
        p = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, timeout=timeout)
        return {'exit': p.returncode, 'output': p.stdout[-24000:]}
    except Exception as e:
        return {'error': str(e)}

report = {'kind': 'encoded-display-read-only-audit', 'uid': os.getuid(),
          'devices': {}, 'commands': {}}
report['va_driver_files'] = glob.glob('/usr/lib/aarch64-linux-gnu/dri/*drv_video.so')
report['commands']['va_native_probe'] = run([sys.executable, __file__, '--va'])
for pattern in ('/dev/dri/*', '/dev/video*', '/dev/media*', '/dev/mali*'):
    for name in glob.glob(pattern):
        report['devices'][name] = {'read': os.access(name, os.R_OK),
                                   'write': os.access(name, os.W_OK)}
for path in glob.glob('/sys/class/drm/*/device/uevent') + glob.glob('/sys/class/video4linux/*/name'):
    try:
        report['devices'][path] = Path(path).read_text()
    except OSError as e:
        report['devices'][path] = str(e)
checks = [('ffmpeg_encoders', ['ffmpeg', '-hide_banner', '-encoders']),
          ('ffmpeg_hwaccels', ['ffmpeg', '-hide_banner', '-hwaccels']),
          ('gst_plugins', ['gst-inspect-1.0']),
          ('vainfo', ['vainfo', '--display', 'drm', '--device', '/dev/dri/renderD128']),
          ('v4l2_devices', ['v4l2-ctl', '--list-devices']),
          ('pci', ['lspci', '-nn']),
          ('vulkan', ['vulkaninfo', '--summary'])]
checks += [('gl_renderer', ['glxinfo', '-B']),
           ('apt_history', ['tail', '-n', '35', '/var/log/apt/history.log'])]
for key, argv in checks:
    report['commands'][key] = run(argv) if shutil.which(argv[0]) else {'missing': argv[0]}
if shutil.which('gst-inspect-1.0'):
    for plugin in ('vaapih264enc', 'vah264enc', 'v4l2h264enc', 'v4l2slh264dec',
                   'x264enc', 'x265enc', 'openh264enc'):
        report['commands'][plugin] = run(['gst-inspect-1.0', plugin])
data = json.dumps(report, indent=2).encode()
print(data.decode(), flush=True)
try:
    request = urllib.request.Request('http://10.159.93.215:39054/report', data=data, method='POST')
    print(urllib.request.urlopen(request, timeout=10).read().decode())
except Exception as e:
    print('REPORT_UPLOAD_ERROR=' + str(e))
