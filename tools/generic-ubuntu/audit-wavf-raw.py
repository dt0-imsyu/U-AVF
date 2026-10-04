"""Read-only validation of captured WAVF packets and visible pixel content."""

import argparse
import struct
import zlib
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("raw", type=Path)
    parser.add_argument("--last-frame-png", type=Path,
                        help="decode the final BGRA frame for visual evidence")
    args = parser.parse_args()
    data = args.raw.read_bytes()
    header = struct.Struct("<4sBBBBHHIIII")
    offset = 0
    frames = 0
    visible = 0
    while offset < len(data):
        if len(data) - offset < header.size:
            raise SystemExit(f"TRUNCATED_HEADER_AT={offset}")
        magic, version, kind, flags, reserved, width, height, sequence, size, crc, extra = header.unpack_from(data, offset)
        if magic != b"WAVF" or version != 1 or size != width * height * 4:
            raise SystemExit(f"BAD_HEADER_AT={offset}")
        end = offset + header.size + size
        if end > len(data):
            raise SystemExit(f"TRUNCATED_PAYLOAD_AT={offset}")
        pixels = memoryview(data)[offset + header.size:end]
        if zlib.crc32(pixels) & 0xFFFFFFFF != crc:
            raise SystemExit(f"BAD_CRC_AT={offset}")
        nonblack = sum(bool(pixels[i] | pixels[i + 1] | pixels[i + 2])
                       for i in range(0, size, 4))
        if args.last_frame_png is not None:
            last_pixels = bytes(pixels)
            last_size = (width, height)
        print(f"FRAME={sequence} SIZE={width}x{height} CRC=PASS NONBLACK_PIXELS={nonblack}")
        frames += 1
        visible += nonblack > 0
        offset = end
    print(f"COMPLETE_FRAMES={frames} FRAMES_WITH_NONBLACK_PIXELS={visible}")
    if frames and args.last_frame_png is not None:
        width, height = last_size
        rgba = bytearray(last_pixels)
        rgba[0::4], rgba[2::4] = rgba[2::4], rgba[0::4]
        scanlines = b"".join(b"\0" + rgba[y * width * 4:(y + 1) * width * 4]
                             for y in range(height))
        def chunk(kind, payload):
            return (struct.pack(">I", len(payload)) + kind + payload
                    + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))
        png = (b"\x89PNG\r\n\x1a\n"
               + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
               + chunk(b"IDAT", zlib.compress(scanlines)) + chunk(b"IEND", b""))
        args.last_frame_png.write_bytes(png)
        print(f"LAST_FRAME_PNG={args.last_frame_png}")
    print("RESULT=PASS" if frames else "RESULT=NO_FRAMES")


if __name__ == "__main__":
    main()
