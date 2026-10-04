#!/usr/bin/env python3
"""Share an existing GDM Xorg session; never create another desktop."""
import os
import sys
import time

deadline = time.monotonic() + 180
while True:
    processes = []
    for pid in os.listdir('/proc'):
        if not pid.isdigit():
            continue
        try:
            base = '/proc/' + pid + '/'
            with open(base + 'comm') as f:
                comm = f.read().strip()
            if comm not in ('gnome-shell', 'gdm-x-session', 'Xorg'):
                continue
            with open(base + 'environ', 'rb') as f:
                env = dict(x.decode(errors='replace').split('=', 1)
                           for x in f.read().split(b'\0') if b'=' in x)
            with open(base + 'cmdline', 'rb') as f:
                args = f.read().split(b'\0')
            processes.append((comm, os.stat(base).st_uid, env, args))
        except (OSError, ValueError):
            pass
    for comm, uid, env, _ in processes:
        display = env.get('DISPLAY', '')
        authority = env.get('XAUTHORITY', '')
        runtime = env.get('XDG_RUNTIME_DIR', '')
        number = display[1:].split('.', 1)[0] if display.startswith(':') else ''
        if (comm != 'gnome-shell' or uid == 0 or env.get('XDG_SESSION_TYPE') != 'x11'
                or not number.isdigit() or not os.path.exists('/tmp/.X11-unix/X' + number)
                or not os.path.isfile(authority) or runtime != '/run/user/' + str(uid)
                or not os.path.isdir(runtime)
                or not env.get('DBUS_SESSION_BUS_ADDRESS', '').startswith('unix:')
                or not any(c == 'gdm-x-session' and u == uid for c, u, _, _ in processes)
                or not any(c == 'Xorg' and (display.encode() in args or authority.encode() in args)
                           for c, _, _, args in processes)):
            continue
        selected = os.environ.copy()
        selected.update(env)
        print('UAVF_GDM_SHARE=START UID=%d DISPLAY=%s XAUTHORITY=%s TYPE=x11 '
              'XDG_RUNTIME_DIR=%s DBUS_SESSION_BUS_ADDRESS=%s GNOME_SHELL_COUNT=%d' %
              (uid, display, authority, runtime, env['DBUS_SESSION_BUS_ADDRESS'],
               sum(c == 'gnome-shell' for c, _, _, _ in processes)), file=sys.stderr, flush=True)
        server = '/usr/local/sbin/winavf-X0tigervnc'
        os.execve(server, [server, '-display', display, '-rfbport', '5901', '-localhost',
                           '-SecurityTypes', 'None', '-AcceptKeyEvents',
                           '-AcceptPointerEvents', '-AlwaysShared'], selected)
    if time.monotonic() >= deadline:
        print('UAVF_GDM_SHARE=DISPLAY_NOT_READY', file=sys.stderr, flush=True)
        sys.exit(1)
    time.sleep(1)
