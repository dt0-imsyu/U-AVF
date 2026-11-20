"""Offline byte-exact RFB -> WAVF tests; no VM or tablet required."""
import importlib.util
import argparse
import socket
import struct
import sys
import threading
import types
import zlib
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument(
    "--bridge", type=Path,
    default=Path(__file__).with_name("guest") / "winavf-frame-bridge.py")
args = parser.parse_args()
BRIDGE = args.bridge
if sys.platform == "win32":
    # The protocol fixture never invokes the Linux-only installer launcher.
    sys.modules.setdefault("pwd", types.ModuleType("pwd"))
spec = importlib.util.spec_from_file_location("bridge", BRIDGE)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


def recv_exact(sock, count):
    result = bytearray()
    while len(result) < count:
        part = sock.recv(count - len(result))
        if not part:
            raise RuntimeError("fixture socket closed early")
        result.extend(part)
    return bytes(result)


def send_update(conn, rectangle):
    x, y, width, height, raw = rectangle
    conn.sendall(b"\x00\x00\x00\x01" +
                 struct.pack(">HHHHi", x, y, width, height, 0) + raw)


def server(listener, requests, continuous):
    conn, _ = listener.accept()
    with conn:
        conn.sendall(b"RFB 003.008\n")
        assert recv_exact(conn, 12) == b"RFB 003.008\n"
        conn.sendall(b"\x01\x01")
        assert recv_exact(conn, 1) == b"\x01"
        conn.sendall(b"\x00\x00\x00\x00")
        assert recv_exact(conn, 1) == b"\x01"
        pixel_format = struct.pack(">BBBBHHHBBBxxx", 32, 24, 0, 1,
                                   255, 255, 255, 16, 8, 0)
        conn.sendall(struct.pack(">HH", 4, 2) + pixel_format +
                     struct.pack(">I", 4) + b"test")
        assert recv_exact(conn, 20) == b"\x00\x00\x00\x00" + pixel_format
        assert recv_exact(conn, 16) == struct.pack(">BBHiii", 2, 0, 3,
                                                    0, -312, -313)
        # TigerVNC acknowledges the advertised Fence and ContinuousUpdates
        # pseudo-encodings before the first framebuffer response.
        conn.sendall(struct.pack(">B3xIB", 248, 0x80000001, 1) + b"\x01" + b"\x96")

        first_request = recv_exact(conn, 10)
        requests.append(first_request)
        assert first_request == struct.pack(">BBHHHH", 3, 0, 0, 0, 4, 2)
        assert recv_exact(conn, 10) == struct.pack(">B3xIB", 248, 1, 1) + b"\x01"
        send_update(conn, (0, 0, 4, 2, bytes([7, 8, 9, 0]) * 8))

        if continuous:
            enable = recv_exact(conn, 10)
            requests.append(enable)
            assert enable == struct.pack(">BBHHHH", 150, 1, 0, 0, 4, 2)
        else:
            second_request = recv_exact(conn, 10)
            requests.append(second_request)
            assert second_request == struct.pack(">BBHHHH", 3, 1, 0, 0, 4, 2)

        # Continuous mode must receive this update without another request.
        send_update(conn, (1, 0, 2, 1, bytes([1, 2, 3, 0]) * 2))


def parse(packet):
    magic, version, kind, flags, _, width, height, seq, length, crc, _ = \
        struct.unpack("<4sBBBBHHIIII", packet[:28])
    payload = packet[28:]
    assert magic == b"WAVF" and version == 1 and (width, height) == (4, 2)
    assert len(payload) == length and zlib.crc32(payload) & 0xffffffff == crc
    return kind, flags, seq, payload


def run_case(continuous):
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen(1)
    requests = []
    errors = []

    def serve():
        try:
            server(listener, requests, continuous)
        except Exception as error:
            errors.append(error)

    worker = threading.Thread(target=serve, daemon=True)
    worker.start()
    capture = bridge.RfbIncrementalCapture(listener.getsockname(),
                                            continuous_updates=continuous)
    try:
        first = parse(capture.next_packet(1))
        assert first[:3] == (1, 1, 1)
        assert zlib.decompress(first[3]) == bytes([7, 8, 9, 255]) * 8
        second = parse(capture.next_packet(2))
        assert second[:3] == (2, 0, 2)
        x, y, width, height, length = struct.unpack("<HHHHI", second[3][:12])
        assert (x, y, width, height) == (1, 0, 2, 1)
        tile = second[3][12:12 + length]
        if len(tile) != width * height * 4:
            tile = zlib.decompress(tile)
        assert tile == bytes([1, 2, 3, 255]) * 2
        worker.join(timeout=2)
        assert not worker.is_alive()
        if errors:
            raise errors[0]
        assert len(requests) == 2
    finally:
        capture.sock.close()
        listener.close()
        worker.join(timeout=2)


def run_continuous_timeout_recovery_case():
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen(1)
    requests = []
    errors = []

    def serve():
        try:
            conn, _ = listener.accept()
            with conn:
                conn.sendall(b"RFB 003.008\n")
                assert recv_exact(conn, 12) == b"RFB 003.008\n"
                conn.sendall(b"\x01\x01")
                assert recv_exact(conn, 1) == b"\x01"
                conn.sendall(b"\x00\x00\x00\x00")
                assert recv_exact(conn, 1) == b"\x01"
                pixel_format = struct.pack(">BBBBHHHBBBxxx", 32, 24, 0, 1,
                                           255, 255, 255, 16, 8, 0)
                conn.sendall(struct.pack(">HH", 4, 2) + pixel_format +
                             struct.pack(">I", 4) + b"test")
                assert recv_exact(conn, 20) == b"\x00\x00\x00\x00" + pixel_format
                assert recv_exact(conn, 16) == struct.pack(">BBHiii", 2, 0, 3,
                                                            0, -312, -313)
                conn.sendall(b"\xf8" + b"\x00" * 8 + b"\x96")
                first_request = recv_exact(conn, 10)
                requests.append(first_request)
                assert first_request == struct.pack(">BBHHHH", 3, 0, 0, 0, 4, 2)
                send_update(conn, (0, 0, 4, 2, bytes(32)))
                assert recv_exact(conn, 10) == struct.pack(">BBHHHH", 150, 1,
                                                            0, 0, 4, 2)
                # The client times out waiting for pushed damage and explicitly
                # disables it before requesting a fresh full framebuffer.
                disable = recv_exact(conn, 10)
                requests.append(disable)
                assert disable == struct.pack(">BBHHHH", 150, 0, 0, 0, 4, 2)
                keyframe = recv_exact(conn, 10)
                requests.append(keyframe)
                assert keyframe == struct.pack(">BBHHHH", 3, 0, 0, 0, 4, 2)
                send_update(conn, (0, 0, 4, 2, bytes([7, 8, 9, 0]) * 8))
        except Exception as error:
            errors.append(error)

    worker = threading.Thread(target=serve, daemon=True)
    worker.start()
    capture = bridge.RfbIncrementalCapture(listener.getsockname(),
                                            continuous_updates=True)
    try:
        first = parse(capture.next_packet(1))
        assert first[:3] == (1, 1, 1)
        capture.sock.settimeout(0.05)
        assert capture.next_packet(2) is None
        second = parse(capture.next_packet(3))
        assert second[:3] == (1, 1, 3)
        pixels = zlib.decompress(second[3])
        assert pixels == bytes([7, 8, 9, 255]) * 8
        worker.join(timeout=2)
        assert not worker.is_alive()
        if errors:
            raise errors[0]
        assert len(requests) == 3
    finally:
        capture.sock.close()
        listener.close()
        worker.join(timeout=2)


def test_xdamage_wavf_record():
    rectangles = [
        (2, 1, 2, 1, bytes([10, 20, 30, 255]) * 2),
        (0, 0, 1, 2, bytes([40, 50, 60, 255]) * 2),
    ]
    packet = bridge.wavf_damage_record(4, 2, 9, rectangles)
    magic, version, kind, flags, _, width, height, sequence, length, crc, _ = \
        struct.unpack("<4sBBBBHHIIII", packet[:28])
    payload = packet[28:]
    assert (magic, version, kind, flags, width, height, sequence) == \
        (b"WAVF", 1, 2, 0, 4, 2, 9)
    assert len(payload) == length and zlib.crc32(payload) & 0xffffffff == crc
    decoded = []
    offset = 0
    for expected in rectangles:
        x, y, w, h, size = struct.unpack("<HHHHI", payload[offset:offset + 12])
        offset += 12
        data = payload[offset:offset + size]
        offset += size
        raw = data if len(data) == w * h * 4 else zlib.decompress(data)
        decoded.append((x, y, w, h, raw))
    assert decoded == rectangles
    assert bridge.wavf_damage_record(4, 2, 10, []) is None
    try:
        bridge.wavf_damage_record(4, 2, 11, [(3, 1, 2, 1, b"short")])
    except ValueError as error:
        assert str(error) == "DAMAGE_RECTANGLE_INVALID"
    else:
        raise AssertionError("invalid damage rectangle was accepted")


def main():
    run_case(False)
    run_case(True)
    run_continuous_timeout_recovery_case()
    test_xdamage_wavf_record()
    print("FRAME_TRANSPORT_OFFLINE=PASS RFB_PULL RFB_CONTINUOUS X11_DAMAGE_WAVF BRIDGE=" + str(BRIDGE))


if __name__ == "__main__":
    main()
