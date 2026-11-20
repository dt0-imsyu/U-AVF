"""No GTK/display needed: protocol, bounds and cross-language test vector."""
import importlib.util
import io
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('clipboard', Path(__file__).parent / 'guest/uavf-clipboard.py')
clipboard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(clipboard)


class WireTests(unittest.TestCase):
    def test_roundtrip(self):
        text = 'Android ↔ Ubuntu\nПривет\n😄\n'
        packet = clipboard.encode(1, text)
        self.assertEqual(packet[:8], b'UCIP\x01\x01\0\0')
        self.assertEqual(clipboard.read_message(io.BytesIO(packet)), (1, text))

    def test_fragmented(self):
        class Fragmented(io.BytesIO):
            def read(self, size):
                return super().read(min(size, 1))
        self.assertEqual(clipboard.read_message(Fragmented(clipboard.encode(1, 'one\ntwo'))), (1, 'one\ntwo'))

    def test_limits(self):
        with self.assertRaises(ValueError):
            clipboard.encode(1, 'x' * (clipboard.MAX_BYTES + 1))
        with self.assertRaises(ValueError):
            clipboard.read_message(io.BytesIO(clipboard.HEADER.pack(b'UCIP', 1, 1, 0, 0xffffffff)))
        with self.assertRaises(UnicodeError):
            clipboard.read_message(io.BytesIO(clipboard.HEADER.pack(b'UCIP', 1, 1, 0, 1) + b'\xff'))
        with self.assertRaises(EOFError):
            clipboard.read_message(io.BytesIO(clipboard.encode(1, 'abc')[:-1]))
        self.assertEqual(clipboard.read_message(io.BytesIO(clipboard.encode(2))), (2, ''))


if __name__ == '__main__':
    unittest.main()
