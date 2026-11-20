"""Local duplex byte pipe: independent overlapped read/write/connect operations."""
import ctypes
from ctypes import wintypes
import threading
import uuid


class Overlapped(ctypes.Structure):
    _fields_ = [("Internal", ctypes.c_size_t), ("InternalHigh", ctypes.c_size_t),
                ("Offset", wintypes.DWORD), ("OffsetHigh", wintypes.DWORD),
                ("hEvent", wintypes.HANDLE)]


class NativePipe:
    def __init__(self, stopping=None):
        self.stopping = stopping or threading.Event()
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        signatures = {
            "CreateNamedPipeW": ([wintypes.LPCWSTR]+[wintypes.DWORD]*6+[ctypes.c_void_p], wintypes.HANDLE),
            "CreateEventW": ([ctypes.c_void_p, wintypes.BOOL, wintypes.BOOL, wintypes.LPCWSTR], wintypes.HANDLE),
            "CloseHandle": ([wintypes.HANDLE], wintypes.BOOL),
            "ConnectNamedPipe": ([wintypes.HANDLE, ctypes.POINTER(Overlapped)], wintypes.BOOL),
            "ReadFile": ([wintypes.HANDLE, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(wintypes.DWORD), ctypes.POINTER(Overlapped)], wintypes.BOOL),
            "WriteFile": ([wintypes.HANDLE, ctypes.c_void_p, wintypes.DWORD, ctypes.POINTER(wintypes.DWORD), ctypes.POINTER(Overlapped)], wintypes.BOOL),
            "GetOverlappedResult": ([wintypes.HANDLE, ctypes.POINTER(Overlapped), ctypes.POINTER(wintypes.DWORD), wintypes.BOOL], wintypes.BOOL),
            "CancelIoEx": ([wintypes.HANDLE, ctypes.POINTER(Overlapped)], wintypes.BOOL),
            "WaitForSingleObject": ([wintypes.HANDLE, wintypes.DWORD], wintypes.DWORD),
            "DisconnectNamedPipe": ([wintypes.HANDLE], wintypes.BOOL),
        }
        for name, (args, result) in signatures.items():
            method = getattr(self.api, name)
            method.argtypes, method.restype = args, result
        self.name = r"\\.\pipe\uavf-isolated-native-kd-"+uuid.uuid4().hex
        self.handle = self.api.CreateNamedPipeW(self.name, 3 | 0x40000000, 8, 1, 65536, 65536, 0, None)
        if self.handle in (None, ctypes.c_void_p(-1).value):
            raise ctypes.WinError(ctypes.get_last_error())

    def operation(self, name, buffer=None, length=0):
        event = self.api.CreateEventW(None, True, False, None)
        if not event:
            raise ctypes.WinError(ctypes.get_last_error())
        overlap = Overlapped(hEvent=event)
        count = wintypes.DWORD()
        try:
            if name == "ConnectNamedPipe":
                ok = self.api.ConnectNamedPipe(self.handle, ctypes.byref(overlap))
            else:
                ok = getattr(self.api, name)(self.handle, buffer, length, ctypes.byref(count), ctypes.byref(overlap))
            error = 0 if ok else ctypes.get_last_error()
            if name == "ConnectNamedPipe" and error == 535:
                return 0
            if error not in (0, 997):
                raise ctypes.WinError(error)
            if error == 997:
                while self.api.WaitForSingleObject(event, 50) == 258:
                    if self.stopping.is_set():
                        self.api.CancelIoEx(self.handle, ctypes.byref(overlap))
                        self.api.GetOverlappedResult(self.handle, ctypes.byref(overlap), ctypes.byref(count), True)
                        raise InterruptedError("Local pipe stopped")
                if not self.api.GetOverlappedResult(self.handle, ctypes.byref(overlap), ctypes.byref(count), False):
                    raise ctypes.WinError(ctypes.get_last_error())
            return count.value
        finally:
            self.api.CloseHandle(event)

    def connect(self):
        self.operation("ConnectNamedPipe")

    def read(self, length=4096):
        buffer = ctypes.create_string_buffer(length)
        count = self.operation("ReadFile", buffer, length)
        return buffer.raw[:count]

    def write(self, data):
        buffer = ctypes.create_string_buffer(data)
        return self.operation("WriteFile", buffer, len(data))

    def disconnect(self):
        self.api.DisconnectNamedPipe(self.handle)

    def close(self):
        self.stopping.set()
        self.api.CancelIoEx(self.handle, None)
        self.api.CloseHandle(self.handle)
