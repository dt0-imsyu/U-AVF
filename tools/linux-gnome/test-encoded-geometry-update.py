"""Verify installed H.264 update recovery without touching an Ubuntu system."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    'encoded_update', ROOT / 'guest/update-uavf-encoded-geometry.py')
UPDATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(UPDATE)


class GeometryUpdateTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.runtime = Path(temp.name).resolve()
        self.payload = self.runtime / 'payload'
        self.payload.mkdir()
        self.original = b'# legacy producer\n'
        (self.runtime / UPDATE.PRODUCER).write_bytes(self.original)
        (self.runtime / UPDATE.CAPTURE).write_bytes(b'# legacy capture\n')
        (self.runtime / 'winavf-frame-bridge.py').write_bytes(b'# original input\n')
        (self.payload / UPDATE.PRODUCER).write_bytes(
            (ROOT / 'guest/uavf-encoded-display.py').read_bytes())
        (self.payload / UPDATE.CAPTURE).write_bytes(
            (ROOT / 'diagnostics/encoded-glx-pbo-probe.py').read_bytes())

    def run_update(self, operation):
        return UPDATE.upgrade(self.runtime, self.payload, operation)

    def test_apply_and_rollback_keep_old_capture(self):
        self.assertEqual(self.run_update('apply'), 'APPLIED')
        self.assertEqual(self.run_update('apply'), 'ALREADY_APPLIED')
        captures = list(self.runtime.glob('uavf-encoded-capture-*.py'))
        self.assertEqual(len(captures), 1)
        self.assertEqual(captures[0].read_bytes(), (self.payload / UPDATE.CAPTURE).read_bytes())
        self.assertIn(captures[0].name.encode(), (self.runtime / UPDATE.PRODUCER).read_bytes())
        self.assertEqual((self.runtime / UPDATE.CAPTURE).read_bytes(), b'# legacy capture\n')
        self.assertEqual((self.runtime / 'winavf-frame-bridge.py').read_bytes(), b'# original input\n')
        self.assertEqual(self.run_update('rollback'), 'ROLLED_BACK')
        self.assertEqual(self.run_update('rollback'), 'ALREADY_ROLLED_BACK')
        self.assertEqual((self.runtime / UPDATE.PRODUCER).read_bytes(), self.original)
        self.assertTrue(captures[0].is_file())

    def test_retry_after_interrupted_activation(self):
        replace = UPDATE.os.replace

        def interrupt(source, target):
            if target.name == UPDATE.PRODUCER:
                raise OSError('injected failure before activation')
            return replace(source, target)

        with patch.object(UPDATE.os, 'replace', side_effect=interrupt):
            with self.assertRaisesRegex(OSError, 'injected failure'):
                self.run_update('apply')
        self.assertEqual((self.runtime / UPDATE.PRODUCER).read_bytes(), self.original)
        self.assertEqual(self.run_update('apply'), 'APPLIED')

    def test_rollback_preserves_newer_external_install(self):
        self.run_update('apply')
        later = b'# a later installer replaced the producer\n'
        (self.runtime / UPDATE.PRODUCER).write_bytes(later)
        with self.assertRaisesRegex(ValueError, 'changed since'):
            self.run_update('rollback')
        self.assertEqual((self.runtime / UPDATE.PRODUCER).read_bytes(), later)

    def test_reject_symlink_at_entrypoint(self):
        entry = self.runtime / UPDATE.PRODUCER
        entry.unlink()
        entry.symlink_to(self.runtime / UPDATE.CAPTURE)
        with self.assertRaisesRegex(ValueError, 'Unexpected owner'):
            self.run_update('apply')
        self.assertFalse((self.runtime / UPDATE.BACKUP).exists())


if __name__ == '__main__':
    unittest.main()
