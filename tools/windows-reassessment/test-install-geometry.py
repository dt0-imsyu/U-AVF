"""Offline guards for GPT growth; does not prove VM or installation success."""
import importlib.util
import struct
import unittest
import zlib
from pathlib import Path

spec=importlib.util.spec_from_file_location('geometry',Path(__file__).with_name('prepare-install-geometry.py'))
geometry=importlib.util.module_from_spec(spec)
spec.loader.exec_module(geometry)

def source_header():
    raw=bytearray(512)
    raw[:8]=b'EFI PART'
    struct.pack_into('<IIII',raw,8,0x10000,92,0,0)
    struct.pack_into('<QQQQ',raw,24,1,17825791,34,17825758)
    raw[56:72]=bytes(range(16))
    struct.pack_into('<QIII',raw,72,2,128,128,1234)
    struct.pack_into('<I',raw,16,zlib.crc32(raw[:92]))
    return bytes(raw)

class GrowthTests(unittest.TestCase):
    def test_primary_growth(self):
        raw=source_header()
        grown=geometry.updated(raw,1,152043519,152043486,2)
        addresses,table=geometry.header(grown)
        self.assertEqual(addresses,(1,152043519,34,152043486))
        self.assertEqual(table,(2,128,128,1234))
        self.assertEqual(grown[56:72],raw[56:72])

    def test_backup_growth(self):
        grown=geometry.updated(source_header(),152043519,1,152043486,152043487)
        self.assertEqual(geometry.header(grown),((152043519,1,34,152043486),(152043487,128,128,1234)))

    def test_corrupt_crc_rejected(self):
        raw=bytearray(source_header());raw[40]^=1
        with self.assertRaises(AssertionError):geometry.header(raw)

    def test_missing_backup_not_valid_header(self):
        with self.assertRaises(AssertionError):geometry.header(bytes(512))

if __name__=='__main__':unittest.main()
