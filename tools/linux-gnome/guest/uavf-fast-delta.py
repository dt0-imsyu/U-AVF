#!/usr/bin/env python3
"""Optional WAVF flag-2 LZ4 block encoder, for a matching Android decoder only."""
import ctypes
import ctypes.util
import struct


class FastDelta:
    LIMIT = 16 * 1024 * 1024

    def __init__(self):
        library = ctypes.util.find_library('lz4')
        if not library:
            raise RuntimeError('LZ4_LIBRARY_UNAVAILABLE')
        self.lib = ctypes.CDLL(library)
        self.lib.LZ4_compressBound.argtypes = [ctypes.c_int]
        self.lib.LZ4_compressBound.restype = ctypes.c_int
        self.lib.LZ4_compress_default.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_int, ctypes.c_int]
        self.lib.LZ4_compress_default.restype = ctypes.c_int
        self.buffer = None
        self.capacity = 0

    def encode(self, raw):
        length = len(raw)
        if not 0 < length <= self.LIMIT:
            raise ValueError('LZ4_INPUT_BOUNDS')
        bound = self.lib.LZ4_compressBound(length)
        if bound <= 0:
            raise ValueError('LZ4_BOUND_INVALID')
        if bound > self.capacity:
            self.buffer = ctypes.create_string_buffer(bound)
            self.capacity = bound
        source = (ctypes.c_char * length).from_buffer(raw) if isinstance(raw, bytearray) else raw
        size = self.lib.LZ4_compress_default(source, self.buffer, length, self.capacity)
        if not 0 < size <= self.capacity:
            raise RuntimeError('LZ4_COMPRESSION_FAILED')
        if size + 4 >= length:
            return 0, raw
        return 2, struct.pack('<I', length) + ctypes.string_at(self.buffer, size)
