#!/usr/bin/env python3
"""Select the installed Plasma X11 session for the account created by Setup."""
from configparser import ConfigParser
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


def setup_account(root):
    session = root / 'usr/share/xsessions/plasma.desktop'
    if not session.is_file():
        raise ValueError('Plasma X11 session is missing')
    accounts = [line.split(':') for line in (root / 'etc/passwd').read_text().splitlines()]
    users = [entry[0] for entry in accounts if len(entry) == 7 and entry[2] == '1000'
             and entry[6] not in ('/usr/sbin/nologin', '/bin/false')]
    if len(users) != 1 or not re.fullmatch(r'[a-z_][a-z0-9_-]{0,31}', users[0]):
        raise ValueError('Could not identify the account created by Ubuntu Setup')
    shadow = dict(line.split(':', 2)[:2] for line in
                  (root / 'etc/shadow').read_text().splitlines() if ':' in line)
    password = shadow.get(users[0], '')
    if not password.startswith('$') or len(password) < 20:
        raise ValueError('The Ubuntu Setup account is locked')
    return users[0]

def select_plasma(root):
    username = setup_account(root)
    directory = root / 'var/lib/AccountsService/users'
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    account = directory / username
    if account.is_symlink():
        raise ValueError('Unexpected AccountsService symlink')
    settings = ConfigParser(interpolation=None)
    settings.optionxform = str
    if account.exists():
        with account.open() as current:
            settings.read_file(current)
    if not settings.has_section('User'):
        settings.add_section('User')
    settings['User']['Session'] = 'plasma'
    settings['User']['XSession'] = 'plasma'
    with tempfile.NamedTemporaryFile(mode='w', dir=directory, delete=False) as output:
        temporary = Path(output.name)
        try:
            settings.write(output, space_around_delimiters=False)
            output.flush()
            os.fsync(output.fileno())
            temporary.replace(account)
        finally:
            temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    if sys.argv[1:] == ['/target']:
        select_plasma(Path('/target'))
    elif sys.argv[1:] == ['--running']:
        setup_account(Path('/'))
        subprocess.run(['busctl', 'call', 'org.freedesktop.Accounts',
                        '/org/freedesktop/Accounts/User1000', 'org.freedesktop.Accounts.User',
                        'SetXSession', 's', 'plasma'], check=True, timeout=15)
    else:
        raise SystemExit('Expected the verified installer target')
