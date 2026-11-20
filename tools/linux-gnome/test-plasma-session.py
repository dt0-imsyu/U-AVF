"""Validate default-session provisioning without touching real user accounts."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location(
    'plasma', Path(__file__).parent / 'guest/uavf-plasma-session.py')
plasma = importlib.util.module_from_spec(spec)
spec.loader.exec_module(plasma)


class PlasmaSessionTest(unittest.TestCase):
    def test_selects_only_setup_user_and_preserves_account_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'etc').mkdir()
            (root / 'etc/passwd').write_text(
                'installer:x:1000:1000::/home/installer:/bin/bash\n'
                'other:x:1001:1001::/home/other:/bin/bash\n')
            shadow = root / 'etc/shadow'
            shadow.write_text('installer:!:1::::::\n')
            session = root / 'usr/share/xsessions/plasma.desktop'
            session.parent.mkdir(parents=True)
            session.write_text('[Desktop Entry]\nExec=startplasma-x11\n')
            with self.assertRaisesRegex(ValueError, 'locked'):
                plasma.select_plasma(root)
            shadow.write_text('installer:$6$disposable-test-hash:1::::::\n')
            accounts = root / 'var/lib/AccountsService/users'
            accounts.mkdir(parents=True)
            account = accounts / 'installer'
            account.write_text('[User]\nLanguage=es_ES.UTF-8\nSystemAccount=false\n')
            other = accounts / 'other'
            other.write_text('[User]\nXSession=ubuntu\n')
            plasma.select_plasma(root)
            self.assertIn('XSession=plasma\n', account.read_text())
            self.assertIn('Language=es_ES.UTF-8\n', account.read_text())
            self.assertIn('SystemAccount=false\n', account.read_text())
            self.assertEqual(other.read_text(), '[User]\nXSession=ubuntu\n')
            self.assertEqual(shadow.read_text(), 'installer:$6$disposable-test-hash:1::::::\n')


if __name__ == '__main__':
    unittest.main()
