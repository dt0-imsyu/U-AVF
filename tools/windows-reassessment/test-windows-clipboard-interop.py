"""Read actual Windows-C vector with unchanged Linux shared UCIP implementation."""
import argparse
import importlib.util
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--vector', type=Path, required=True)
args = parser.parse_args()
source = Path(__file__).parents[1] / 'linux-gnome/guest/uavf-clipboard.py'
spec = importlib.util.spec_from_file_location('existing_linux_ucip', source)
wire = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wire)
expected = 'Android ↔ Windows\nПривет\n😄\n'
with args.vector.open('rb') as stream:
    assert wire.read_message(stream) == (1, expected)
    assert stream.read() == b''
assert args.vector.read_bytes() == wire.encode(1, expected)
print('ACTUAL_NATIVE_WINDOWS_TO_LINUX_UCIP_VECTOR=PASS')
