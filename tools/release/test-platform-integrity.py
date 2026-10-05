"""Small fixtures for the packaged-asset integrity generator."""
import hashlib
import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('integrity', Path(__file__).with_name('generate-platform-integrity.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class IntegrityTest(unittest.TestCase):
    def setUp(self):
        self.original = module.ASSETS
        module.ASSETS = {'platform.img': 16, 'trailer.bin': 4}
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        module.ASSETS = self.original
        self.temp.cleanup()

    def apk(self, platform=b'P' * 16, trailer=b'GPT!'):
        path = self.root / 'base.apk'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('assets/platform.img', platform)
            if trailer is not None:
                archive.writestr('assets/trailer.bin', trailer)
        return path

    def test_actual_payload_not_local_copy(self):
        (self.root / 'platform.img').write_bytes(b'X' * 16)
        expected = hashlib.sha256(b'P' * 16).hexdigest().upper()
        module.generate(self.apk(), self.root / 'generated')
        source = (self.root / 'generated/com/example/winavf/BundledPlatformAssets.java').read_text()
        self.assertIn(expected, source)
        self.assertNotIn(hashlib.sha256(b'X' * 16).hexdigest().upper(), source)

    def test_payload_change_changes_hash(self):
        before = module.inventory(self.apk())
        after = module.inventory(self.apk(platform=b'Q' * 16))
        self.assertNotEqual(before['platform.img'], after['platform.img'])

    def test_missing_asset_fails(self):
        with self.assertRaises(KeyError):
            module.inventory(self.apk(trailer=None))

    def test_wrong_size_fails(self):
        with self.assertRaises(ValueError):
            module.inventory(self.apk(platform=b'P'))

if __name__ == '__main__':
    unittest.main()
