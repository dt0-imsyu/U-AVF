#!/usr/bin/env python3
"""Bounded read-only RFB encoder profile on the existing GNOME display."""
import cProfile
import importlib.util
import io
import json
import os
import pstats
import threading
import time
import urllib.request
import sys
import hashlib


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bridge = load('uavf_bridge_profile', '/usr/local/sbin/winavf-frame-bridge.py')
if '--raw' in sys.argv or '--lz4' in sys.argv:
    from pathlib import Path
    source = Path('/usr/local/sbin/winavf-frame-bridge.py').read_text()
    needle = 'if len(raw) < 4096:'
    if source.count(needle) != 1:
        raise RuntimeError('Expected single RFB tile encoder not found')
    exec(compile(source.replace(needle, 'if True:  # diagnostic raw tiles'),
                 'rfb-raw-diagnostic', 'exec'), bridge.__dict__)
if '--lz4' in sys.argv:
    codec = load('uavf_fast_delta', '/tmp/uavf-fast-delta.py').FastDelta()
    original_record = bridge.wavf_record
    def fast_record(kind, flags, width, height, sequence, payload):
        if kind == 2 and flags == 0:
            flags, payload = codec.encode(payload)
        return original_record(kind, flags, width, height, sequence, payload)
    bridge.wavf_record = fast_record
if '--buffered' in sys.argv:
    def buffered_read(self, length):
        if not hasattr(self, '_diagnostic_reader'):
            self._diagnostic_reader = self.sock.makefile('rb', buffering=262144)
        data = self._diagnostic_reader.read(length)
        if len(data) != length:
            raise RuntimeError('RFB_STREAM_CLOSED')
        return data
    bridge.RfbIncrementalCapture._read = buffered_read
motion = load('uavf_fps_motion', '/tmp/uavf-fps-ab.py')
profile = cProfile.Profile()
capture = bridge.RfbIncrementalCapture()
stop = time.monotonic() + 30
sequence = 1
packets = 0
total = 0
movement = {}
def move():
    movement.update(motion.motion(os.environ.copy(), 30))
worker = threading.Thread(target=move)
worker.start()
started = time.monotonic()
profile.enable()
try:
    while time.monotonic() < stop:
        packet = capture.next_packet(sequence)
        if packet:
            sequence += 1; packets += 1; total += len(packet)
finally:
    profile.disable()
    if hasattr(capture, '_diagnostic_reader'):
        capture._diagnostic_reader.close()
    capture.sock.close()
    worker.join()
elapsed = time.monotonic() - started
stream = io.StringIO()
pstats.Stats(profile, stream=stream).strip_dirs().sort_stats('cumulative').print_stats(35)
report = dict(kind='existing-display-rfb-cprofile-not-end-to-end', elapsed=elapsed,
              packets=packets, bytes=total, fps=packets/elapsed, movement=movement,
              profile=stream.getvalue(), raw_tiles='--raw' in sys.argv,
              buffered='--buffered' in sys.argv, lz4='--lz4' in sys.argv)
if '--lz4' in sys.argv:
    fixture = bytearray(bytes(range(256)) * 400)
    flag, payload = codec.encode(fixture)
    report['native_fixture'] = dict(flag=flag, payload_hex=payload.hex(),
                                    decoded_bytes=len(fixture), sha256=hashlib.sha256(fixture).hexdigest())
req = urllib.request.Request(sys.argv[1] + '/report', data=json.dumps(report).encode(), method='POST')
from pathlib import Path
Path('/tmp/uavf-rfb-profile-report.json').write_text(json.dumps(report))
print('LOCAL_PROFILE_FPS=%.2f RAW=%s BUFFERED=%s BYTES=%d' %
      (report['fps'], report['raw_tiles'], report['buffered'], total), flush=True)
with urllib.request.urlopen(req, timeout=15) as response:
    print('PROFILE_REPORT=' + str(response.status), flush=True)
