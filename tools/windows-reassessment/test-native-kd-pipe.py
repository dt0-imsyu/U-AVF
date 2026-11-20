"""Real Win32 pipe test, no VM: write while the same server handle awaits read."""
import ctypes
from ctypes import wintypes
import importlib.util
from pathlib import Path
import threading

spec = importlib.util.spec_from_file_location("pipe", Path(__file__).with_name("native-kd-pipe.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
pipe = module.NativePipe()
api = pipe.api
api.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
api.CreateFileW.restype = wintypes.HANDLE
received, errors = [], []

def client():
    handle = api.CreateFileW(pipe.name, 0xc0000000, 0, None, 3, 0, None)
    try:
        assert handle not in (None, ctypes.c_void_p(-1).value)
        buffer, count = ctypes.create_string_buffer(5), wintypes.DWORD()
        assert api.ReadFile(handle, buffer, 5, ctypes.byref(count), None)
        assert buffer.raw[:count.value] == b"hello"
        answer = ctypes.create_string_buffer(b"world")
        assert api.WriteFile(handle, answer, 5, ctypes.byref(count), None)
    except Exception as error:
        errors.append(error)
    finally:
        api.CloseHandle(handle)

thread = threading.Thread(target=client, daemon=True)
thread.start()
pipe.connect()
reader = threading.Thread(target=lambda: received.append(pipe.read()), daemon=True)
reader.start()
assert pipe.write(b"hello") == 5
reader.join(3); thread.join(3)
assert not reader.is_alive() and not thread.is_alive() and not errors and received == [b"world"], (received, errors)
pipe.close()
print("NATIVE_PIPE_SIMULTANEOUS_READ_WRITE_REAL_WIN32=PASS")
