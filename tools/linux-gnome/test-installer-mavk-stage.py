"""Exercise the installer staging shell with a disposable target and mocked mounts."""
import hashlib
from pathlib import Path
import os
import subprocess
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).parent / 'guest/stage-uavf-installer-mavk.sh'


class InstallerMavkStageTest(unittest.TestCase):
    def setUp(self):
        workspace = tempfile.TemporaryDirectory()
        self.addCleanup(workspace.cleanup)
        root = Path(workspace.name)
        self.bundle = root / 'bundle'
        self.target = root / 'target'
        self.commands = root / 'bin'
        for path in (self.bundle, self.target, self.commands):
            path.mkdir()
        self.script = root / 'stage.sh'
        self.script.write_text(SCRIPT.read_text().replace(
            'bundle=/usr/local/lib/uavf-installer', f'bundle={self.bundle}'
        ).replace('target=/target', f'target={self.target}'
        ).replace('scripts=/usr/local/sbin', f'scripts={self.commands}'))
        for name in ('uavf-plasma-session.py', 'uavf-plasma-install.sh', 'uavf-plasma-install.service'):
            (self.commands / name).write_text('fixture\n')
        entry = root / 'install-mavk-installed.sh'
        entry.write_text('#!/bin/sh\n')
        self.archive = self.bundle / 'mavk-installed-addon.tar.gz'
        with tarfile.open(self.archive, 'w:gz') as archive:
            archive.add(entry, arcname=entry.name)
        (self.bundle / 'mavk-installed-addon.sha256').write_text(
            hashlib.sha256(self.archive.read_bytes()).hexdigest() + '  ' + self.archive.name + '\n')
        self.ready = self.target / 'opt/uavf/mavk/installed-version'
        self.calls = root / 'calls'
        self.command('mountpoint', 'exit 0')
        self.command('findmnt', "printf '/dev/vda3 ext4\\n'")
        self.command('lsblk', "printf 'U-AVF Ubuntu Root\\n'")
        self.command('curtin', f'''
set -eu
[ "$1" = in-target ] && [ "$2" = "--target={self.target}" ] &&
    [ "$3" = -- ] && [ "$4" = /bin/sh ] && [ "$6" = --offline ]
[ -s "{self.target}$5" ]
printf 'stage\\n' >> "{self.calls}"
mkdir -p "{self.ready.parent}"
printf 'version=1\\n' > "{self.ready}"
''')

    def command(self, name, body):
        path = self.commands / name
        path.write_text('#!/bin/sh\n' + body + '\n')
        path.chmod(0o755)

    def run_stage(self, option):
        return subprocess.run(['/bin/sh', str(self.script), option], capture_output=True,
                              env=dict(os.environ, PATH=str(self.commands) + ':' + os.environ['PATH']))

    def test_verified_stage_and_retry(self):
        self.assertEqual(self.run_stage('--verify').returncode, 0)
        self.assertFalse(self.calls.exists())
        result = self.run_stage('--stage')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.ready.read_text(), 'version=1\n')
        self.assertEqual(self.run_stage('--stage').returncode, 0)
        self.assertEqual(self.calls.read_text(), 'stage\n')
        self.assertEqual(list((self.target / 'var/lib/uavf').glob('mavk-stage.*')), [])

    def test_corrupt_bundle_rejected_before_target_changes(self):
        self.archive.write_bytes(b'corrupt')
        self.assertNotEqual(self.run_stage('--verify').returncode, 0)
        self.assertNotEqual(self.run_stage('--stage').returncode, 0)
        self.assertEqual(list(self.target.iterdir()), [])

    def test_wrong_partition_rejected(self):
        self.command('findmnt', "printf '/dev/vda2 ext4\\n'")
        self.assertNotEqual(self.run_stage('--stage').returncode, 0)
        self.assertFalse(self.calls.exists())
        self.assertEqual(list(self.target.iterdir()), [])

    def test_installer_failure_never_reports_ready(self):
        self.command('curtin', 'exit 42')
        self.assertEqual(self.run_stage('--stage').returncode, 42)
        self.assertFalse(self.ready.exists())
        self.assertEqual(list((self.target / 'var/lib/uavf').glob('mavk-stage.*')), [])

    def test_plasma_download_precedes_mavk_and_keeps_gdm(self):
        (self.bundle / 'desktop-profile').write_text('plasma-x11\n')
        manager = self.target / 'etc/systemd/system/display-manager.service'
        manager.parent.mkdir(parents=True)
        manager.symlink_to('/usr/lib/systemd/system/gdm3.service')
        kwin = self.target / 'etc/systemd/user/plasma-kwin_x11.service.d/91-uavf-installed-mavk.conf'
        self.command('curtin', f'''
set -eu
[ "$1" = in-target ] && [ "$2" = "--target={self.target}" ] && [ "$3" = -- ]
case "$4" in
    apt-get) printf 'update\\n' >> "{self.calls}" ;;
    env)
        [ "$*" = "in-target --target={self.target} -- env DEBIAN_FRONTEND=noninteractive apt-get -y --no-install-recommends install plasma-desktop kwin-x11" ]
        printf 'install\\n' >> "{self.calls}"
        ;;
    /bin/sh)
        printf 'stage\\n' >> "{self.calls}"
        mkdir -p "{self.ready.parent}" "{kwin.parent}"
        printf 'version=1\\n' > "{self.ready}"
        printf 'fixture\\n' > "{kwin}"
        ;;
    *) exit 64 ;;
esac
''')
        (self.commands / 'uavf-plasma-session.py').write_text(
            f'from pathlib import Path\nPath({str(self.calls)!r}).open("a").write("select\\n")\n')
        result = self.run_stage('--stage')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.calls.read_text(), 'update\ninstall\nstage\nselect\n')
        self.assertEqual(manager.readlink(), Path('/usr/lib/systemd/system/gdm3.service'))
        self.assertTrue((self.target / 'var/lib/uavf/plasma-x11.ready').is_file())


if __name__ == '__main__':
    unittest.main()
