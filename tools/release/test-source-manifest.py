"""Offline regression: fetching more sources must preserve prior evidence."""
import hashlib
import io
import json
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name('fetch-dsc-sources.py')

class ManifestTests(unittest.TestCase):
    def test_append_and_idempotent(self):
        payload = b'fixture source, never executed'
        digest = hashlib.sha256(payload).hexdigest()
        dsc = ('Checksums-Sha256:\n '+digest+' '+str(len(payload))+
               ' fixture.orig.tar.xz\nFiles:\n').encode()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = {'dsc': 'existing.dsc', 'dsc_sha256': 'A'*64,
                   'artifacts': [], 'pgp_signature_verified': False}
            manifest = root/'download-manifest.json'
            manifest.write_text(json.dumps([old]))
            def fetch(url, **kwargs):
                return io.BytesIO(dsc if url.endswith('.dsc') else payload)
            args = ['fetch', '--dsc',
                    'https://ports.ubuntu.com/fixture.dsc', '--output', directory]
            for _ in range(2):
                with patch('sys.argv', args), patch('urllib.request.urlopen', fetch):
                    runpy.run_path(str(SCRIPT), run_name='__main__')
                records = json.loads(manifest.read_text())
                self.assertEqual(len(records), 2)
                self.assertEqual(records[0], old)
                self.assertEqual(records[1]['artifacts'][0]['sha256'], digest.upper())
            records[1]['dsc_sha256'] = 'B'*64
            manifest.write_text(json.dumps(records))
            with patch('sys.argv', args), patch('urllib.request.urlopen', fetch):
                with self.assertRaisesRegex(AssertionError, 'Conflicting'):
                    runpy.run_path(str(SCRIPT), run_name='__main__')
            self.assertEqual(json.loads(manifest.read_text()), records)

if __name__ == '__main__':
    unittest.main()
