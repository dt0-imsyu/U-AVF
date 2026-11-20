"""Exercise publication and rollback with a small synthetic disk."""
import hashlib
import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location(
    'stream_setup_disk', pathlib.Path(__file__).with_name('stream-setup-disk.py'))
media = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = media
spec.loader.exec_module(media)


class SetupDiskTests(unittest.TestCase):
    def test_publishes_new_disk_and_keeps_inputs(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            base, output = root / 'source.img', root / 'workspace.img'
            source = bytes(range(128))
            base.write_bytes(source)
            payload = b'test'
            patch_span = media.Span(20, len(payload), payload)
            setup = source[:20] + payload + source[24:]
            digest = hashlib.sha256(setup).hexdigest().upper()
            head, tail = b'new head', b'new tail'
            with patch.multiple(media, MEDIA_BYTES=len(source), GIB=1024, RESERVE=0,
                                SOURCE_SHA=hashlib.sha256(source).hexdigest().upper(),
                                SETUP_SHA=digest, PARTS={}), patch.object(
                    media, 'plan_setup', return_value=[patch_span]), patch.object(
                    media, 'plan_gpt', return_value=(head, tail)):
                report = media.build(base, root, output)
                with self.assertRaisesRegex(ValueError, 'existing disk'):
                    media.build(base, root, output)
                with patch.object(media, 'SETUP_SHA', 'invalid'):
                    failed = root / 'failure.img'
                    with self.assertRaisesRegex(ValueError, 'Reconstructed media'):
                        media.build(base, root, failed)
                    self.assertFalse(failed.exists())
            expected = head + setup[len(head):]
            self.assertEqual(report['logical_bytes'], len(source) + 64 * 1024)
            with output.open('rb') as disk:
                self.assertEqual(disk.read(len(source)), expected)
                disk.seek(-len(tail), 2)
                self.assertEqual(disk.read(), tail)
            self.assertEqual(base.read_bytes(), source)
            self.assertEqual(sorted(p.name for p in root.iterdir()), ['source.img', 'workspace.img'])


if __name__ == '__main__':
    unittest.main()
