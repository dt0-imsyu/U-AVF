"""Offline, bounded XPRESS WOF reconstruction of the captured BasicDisplay only."""
import argparse
import hashlib
import json
import struct
import ctypes


def decode_chunk(block, expected):
    # Native decoder receives exact output size, avoiding interpreting padding
    # bits as additional symbols. This only decompresses owned host buffers.
    ntdll = ctypes.WinDLL('ntdll')
    workspace_size, fragment_size = ctypes.c_ulong(), ctypes.c_ulong()
    get_size = ntdll.RtlGetCompressionWorkSpaceSize
    get_size.argtypes = [ctypes.c_ushort, ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_ulong)]
    get_size.restype = ctypes.c_long
    assert get_size(4, ctypes.byref(workspace_size), ctypes.byref(fragment_size)) == 0
    assert workspace_size.value <= 1024 * 1024
    workspace = ctypes.create_string_buffer(workspace_size.value)
    source, output = ctypes.create_string_buffer(block), ctypes.create_string_buffer(expected)
    size = ctypes.c_ulong()
    decode = ntdll.RtlDecompressBufferEx
    decode.argtypes = [ctypes.c_ushort, ctypes.c_void_p, ctypes.c_ulong,
                       ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_ulong), ctypes.c_void_p]
    decode.restype = ctypes.c_long
    status = decode(4, output, expected, source, len(block), ctypes.byref(size), workspace)
    assert status == 0 and size.value == expected, 'Native exact-size XPRESS decode failed'
    return output.raw


def main():
    from pathlib import Path
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('directory', type=Path)
    a = p.parse_args()
    manifest = json.loads((a.directory / 'manifest.json').read_text())
    assert manifest['read_only'] and not manifest['executable_reconstructed']
    raw = (a.directory / 'WofCompressedData.bin').read_bytes()
    assert hashlib.sha256(raw).hexdigest() == manifest['compressed_sha256']
    tag, length, reserved, version, provider, file_version, algorithm = struct.unpack(
        '<IHHIIII', bytes.fromhex(manifest['reparse_hex']))
    assert (tag, length, reserved, version, provider, file_version) == (0x80000017, 16, 0, 1, 2, 1)
    assert algorithm in (0, 2, 3), 'Only XPRESS file-provider formats; no LZX guess'
    chunk = {0:4096, 2:8192, 3:16384}[algorithm]
    size = manifest['logical_bytes']
    assert 0 < size <= 2 * 1024 * 1024
    count = (size + chunk - 1) // chunk
    table_bytes = 4 * (count - 1)
    ends = list(struct.unpack('<' + 'I' * (count - 1), raw[:table_bytes]))
    payload = raw[table_bytes:]
    ends.append(len(payload))
    start, result = 0, bytearray()
    for index, end in enumerate(ends):
        assert start < end <= len(payload)
        expected = min(chunk, size - index * chunk)
        block = payload[start:end]
        decoded = block if len(block) == expected else decode_chunk(block, expected)
        assert len(decoded) == expected, f'Chunk {index}: decoded {len(decoded)} != {expected}; no executable published'
        result.extend(decoded)
        start = end
    assert len(result) == size and result[:2] == b'MZ'
    pe = struct.unpack_from('<I', result, 60)[0]
    assert result[pe:pe+4] == b'PE\0\0' and struct.unpack_from('<H', result, pe+4)[0] == 0xAA64
    target = a.directory / 'BasicDisplay.sys'
    with target.open('xb') as output:
        output.write(result)
    report = dict(bytes=size, chunks=count, xpress_chunk=chunk,
                  sha256=hashlib.sha256(result).hexdigest(),
                  scope='Offline exact-size reconstruction; no device writes or signature claim')
    with (a.directory / 'reconstruction.json').open('x') as output:
        json.dump(report, output, indent=2)
    print(json.dumps(report))


if __name__ == '__main__':
    main()
