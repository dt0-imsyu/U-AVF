#!/usr/bin/env python3
"""Offline producer/consumer check for 1080p WAVF v1 keyframe + tiles."""
import importlib.util
from pathlib import Path
import struct
import zlib


bridge = Path(__file__).resolve().parents[1] / "linux-gnome/guest/winavf-frame-bridge.py"
spec = importlib.util.spec_from_file_location("winavf_frame_bridge", bridge)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def apply(packet, canvas):
    magic, version, kind, flags, reserved, width, height, sequence, length, crc, extra = (
        struct.unpack("<4sBBBBHHIIII", packet[:28]))
    assert (magic, version, reserved, extra) == (b"WAVF", 1, 0, 0)
    assert (width, height) == (1920, 1080)
    payload = packet[28:]
    assert len(payload) == length and zlib.crc32(payload) & 0xffffffff == crc
    if kind == 1:
        assert flags == 1
        result = bytearray(zlib.decompress(payload))
        assert len(result) == width * height * 4
        return result
    assert kind == 2 and flags == 0 and canvas is not None
    offset = 0
    while offset < len(payload):
        x, y, w, h, size = struct.unpack_from("<HHHHI", payload, offset)
        offset += 12
        tile = payload[offset:offset + size]
        offset += size
        if len(tile) != w * h * 4:
            tile = zlib.decompress(tile)
        assert len(tile) == w * h * 4
        for row in range(h):
            start = ((y + row) * width + x) * 4
            canvas[start:start + w * 4] = tile[row * w * 4:(row + 1) * w * 4]
    assert offset == len(payload)
    return canvas


def main():
    width, height = 1920, 1080
    source = bytearray(width * height * 4)
    source[3::4] = b"\xff" * (width * height)
    encoder = module.DeltaEncoder()
    first = encoder.encode(width, height, source, 1)
    canvas = apply(first, None)
    assert canvas == source
    assert encoder.encode(width, height, source, 2) is None
    # Edits cross both horizontal and vertical 64-pixel tile boundaries.
    for x, y in ((63, 63), (64, 64), (1919, 1079)):
        start = (y * width + x) * 4
        source[start:start + 4] = b"\x11\x22\x33\xff"
    update = encoder.encode(width, height, source, 2)
    assert update and update[5] == 2
    canvas = apply(update, canvas)
    assert canvas == source
    assert len(first) <= 16 * 1024 * 1024 and len(update) < len(source) // 10
    print("WAVF_1080P_DELTA_SELF_TEST=PASS keyframe_bytes=%d update_bytes=%d" %
          (len(first), len(update)))


if __name__ == "__main__":
    main()
