#!/usr/bin/env python3
"""Upgrade the two H.264 Python components of an existing Ubuntu guest."""

import argparse
import fcntl
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import tempfile

RUNTIME = Path('/usr/local/lib/uavf-encoded')
PRODUCER = 'uavf-encoded-display.py'
CAPTURE = 'uavf-encoded-capture.py'
BACKUP = 'uavf-encoded-display.pre-geometry.py'
SERVICE = Path('/etc/systemd/system/uavf-encoded@.service')
CAPTURE_REFERENCE = b"str(HERE/'uavf-encoded-capture.py')"


def installed_file(path):
    info = path.lstat()
    if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
            or info.st_mode & 0o022 or info.st_size > 2 * 1024 * 1024):
        raise ValueError(f'Unexpected owner, permissions or size for {path}')
    return path.read_bytes()


def replace_file(path, data):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        staged = Path(stream.name)
        try:
            os.fchmod(stream.fileno(), 0o644)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
            os.replace(staged, path)
            directory = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
        finally:
            staged.unlink(missing_ok=True)


def upgrade(runtime, payload, operation):
    info = runtime.lstat()
    if (not stat.S_ISDIR(info.st_mode) or info.st_uid != os.geteuid()
            or info.st_mode & 0o022 or runtime.resolve() != runtime):
        raise ValueError('Expected an owner-controlled, non-symlink runtime directory')

    capture = (payload / CAPTURE).read_bytes()
    producer = (payload / PRODUCER).read_bytes()
    if producer.count(CAPTURE_REFERENCE) != 1 or b"b'SIZE'" not in producer:
        raise ValueError('Expected the size-negotiating UVF1 producer')
    capture_name = 'uavf-encoded-capture-' + hashlib.sha256(capture).hexdigest() + '.py'
    producer = producer.replace(CAPTURE_REFERENCE, f"str(HERE/'{capture_name}')".encode())
    compile(capture, capture_name, 'exec')
    compile(producer, PRODUCER, 'exec')

    lock = os.open(runtime / '.encoded-geometry.lock',
                   os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(lock, 'rb') as held:
        fcntl.flock(held, fcntl.LOCK_EX)
        current = installed_file(runtime / PRODUCER)
        installed_file(runtime / CAPTURE)
        installed_file(runtime / 'winavf-frame-bridge.py')
        backup = runtime / BACKUP
        capture_path = runtime / capture_name

        if operation == 'rollback':
            original = installed_file(backup)
            if current == original:
                return 'ALREADY_ROLLED_BACK'
            if current != producer:
                raise ValueError('The active producer changed since this update')
            compile(original, BACKUP, 'exec')
            replace_file(runtime / PRODUCER, original)
            return 'ROLLED_BACK'

        if current == producer:
            if installed_file(capture_path) != capture:
                raise ValueError('Versioned capture failed verification')
            return 'ALREADY_APPLIED'
        if os.path.lexists(backup):
            if installed_file(backup) != current:
                raise ValueError('The active producer differs from the saved original')
        else:
            compile(current, PRODUCER, 'exec')
            replace_file(backup, current)
        if os.path.lexists(capture_path):
            if installed_file(capture_path) != capture:
                raise ValueError('Versioned capture failed verification')
        else:
            replace_file(capture_path, capture)
        # Switch entrypoints last. A running old producer can still load its
        # original capture, and rollback can coexist with a running new one.
        replace_file(runtime / PRODUCER, producer)
        return 'APPLIED'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('apply', 'rollback'))
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Run as the Ubuntu guest administrator')
    release = os.freedesktop_os_release()
    if release.get('ID') != 'ubuntu' or release.get('VERSION_ID') != '24.04':
        parser.error('Expected Ubuntu 24.04')
    entry = f'ExecStart=/usr/bin/python3 {RUNTIME / PRODUCER}'.encode()
    try:
        mount = subprocess.run(['findmnt', '-n', '-o', 'SOURCE,FSTYPE', '/'],
                               capture_output=True, text=True, timeout=3, check=True)
        if mount.stdout.split() != ['/dev/vda3', 'ext4']:
            raise ValueError('Expected the managed installed Ubuntu root on /dev/vda3')
        if entry not in installed_file(SERVICE).splitlines():
            raise ValueError('The installed systemd service uses a different entrypoint')
        result = upgrade(RUNTIME, Path(__file__).resolve().parent, args.operation)
    except (OSError, ValueError, SyntaxError, subprocess.SubprocessError) as error:
        parser.exit(1, f'Encoded geometry update refused: {error}\n')
    print(f'ENCODED_GEOMETRY_UPDATE={result}')
    print('The next H.264 connection will use the selected producer.')


if __name__ == '__main__':
    main()
