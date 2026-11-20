"""Build artifact only: pad the exact isolated ARM64 x264 DLL, no device writes."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--candidate', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
raw = args.candidate.read_bytes()
assert len(raw) == 688128
assert hashlib.sha256(raw).hexdigest() == '4460127af53ce780043dc6a7cbbc2755f63284a6c9457206b330065c0cf8445d'
assert raw[:2] == b'MZ'
pe = struct.unpack_from('<I', raw, 60)[0]
assert raw[pe:pe+4] == b'PE\0\0'
assert struct.unpack_from('<H', raw, pe+4)[0] == 0xaa64
assert struct.unpack_from('<H', raw, pe+22)[0] & 0x2000
assert not args.output.exists(), 'Preserve previous artifact'
args.output.mkdir(parents=True)
padded = raw + bytes(843264-len(raw))
(args.output/'UAVF8.DLL').write_bytes(padded)
report = dict(result='EXACT_X264_PADDING_PASS', device_writes=0,
              bytes=len(padded), sha256=hashlib.sha256(padded).hexdigest(),
              note='Private R&D codec; not release/licensing approval')
(args.output/'report.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report))
