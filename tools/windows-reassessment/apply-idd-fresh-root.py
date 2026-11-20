"""WSL native libwim NTFS-image apply; no loop mount or directory metadata loss.
Calls the documented wimlib API on one fixed, newly formatted disposable image.
Does not open a tablet disk, registry or existing installed Windows.
"""
import ctypes as C
import hashlib
from pathlib import Path
import time
import os

target=Path('/home/denis/uavf-build/idd-fresh-root-20261013/root.ntfs')
payload=Path('/mnt/c/Users/denis/MainProjects/win11ontab/build-logs/windows-post-linux-reassessment-20261004/install-payload-udf-20261008')
assert target.is_file() and not target.is_symlink()
assert target.stat().st_size==68157440000
with target.open('rb') as stream:
    boot=stream.read(512)
assert boot[3:11]==b'NTFS    '
for name,expected in (
    ('install.swm','6a843cdbacb13c5a1579e8abe8bdb946957a0a342d78e177f27c2b19fd4a452a'),
    ('install2.swm','7fc03f45a32a8f19bb99f4eb70cce0be214311af795d26ecfe356781c10a727f')):
    with (payload/name).open('rb') as stream:
        assert hashlib.file_digest(stream,'sha256').hexdigest()==expected
    print('SOURCE_PART_HASH_PASS='+name,flush=True)

lib=C.CDLL('libwim.so.15')
ptr=C.c_void_p
lib.wimlib_open_wim.argtypes=[C.c_char_p,C.c_int,C.POINTER(ptr)]
lib.wimlib_open_wim.restype=C.c_int
lib.wimlib_reference_resource_files.argtypes=[ptr,C.POINTER(C.c_char_p),C.c_uint,C.c_int,C.c_int]
lib.wimlib_reference_resource_files.restype=C.c_int
lib.wimlib_extract_image.argtypes=[ptr,C.c_int,C.c_char_p,C.c_int]
lib.wimlib_extract_image.restype=C.c_int
lib.wimlib_free.argtypes=[ptr]
lib.wimlib_free.restype=None
callback_type=C.CFUNCTYPE(C.c_int,C.c_int,ptr,ptr)
lib.wimlib_register_progress_function.argtypes=[ptr,callback_type,ptr]
lib.wimlib_register_progress_function.restype=None
last=[0.0]
@callback_type
def progress(message,info,context):
    now=time.monotonic()
    if now-last[0]>=10:
        print('NATIVE_WIM_PROGRESS_MESSAGE='+str(message),flush=True)
        last[0]=now
        # The WSL build volume is on C. Abort before violating the host reserve.
        space=os.statvfs('/mnt/c')
        if space.f_bavail*space.f_frsize < 51*1024**3:
            print('ABORT_HOST_C_RESERVE; NOT_APPLY_PASS',flush=True)
            return 1
    return 0

wim=ptr()
assert lib.wimlib_open_wim(bytes(payload/'install.swm'),0,C.byref(wim))==0
try:
    resource=(C.c_char_p*1)(bytes(payload/'install2.swm'))
    assert lib.wimlib_reference_resource_files(wim,resource,1,0,0)==0
    lib.wimlib_register_progress_function(wim,progress,None)
    print('FRESH_OFFICIAL_PRO_APPLY_BEGIN; NOT_BOOT_OR_FPS_PASS',flush=True)
    # Documented flags: NTFS regular-file image=1; strict Windows security=0x80.
    result=lib.wimlib_extract_image(wim,3,bytes(target),0x81)
    assert result==0, 'Native NTFS apply failure='+str(result)
    print('FRESH_OFFICIAL_PRO_NTFS_APPLY_PASS; NOT_BOOT_OR_DRIVER_OR_FPS_PASS',flush=True)
finally:
    lib.wimlib_free(wim)
