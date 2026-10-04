#!/usr/bin/env python3
"""Text-only clipboard on a separate socket-activated vsock 4054 stream.

UCIP v1: magic[4], version:u8, type:u8, reserved:u16, length:u32 LE,
UTF-8 payload. TEXT=1, REQUEST=2. Payloads are never logged.
"""
import os
import struct
import sys
import threading

MAX_BYTES = 262144
HEADER = struct.Struct('<4sBBHI')


def encode(kind, text=''):
    payload = text.encode('utf-8')
    if kind not in (1, 2) or len(payload) > MAX_BYTES or (kind == 2 and payload):
        raise ValueError('invalid clipboard packet')
    return HEADER.pack(b'UCIP', 1, kind, 0, len(payload)) + payload


def exact(stream, size):
    chunks = bytearray()
    while len(chunks) < size:
        data = stream.read(size - len(chunks))
        if not data:
            raise EOFError('clipboard peer closed')
        chunks.extend(data)
    return bytes(chunks)


def read_message(stream):
    magic, version, kind, reserved, size = HEADER.unpack(exact(stream, HEADER.size))
    if magic != b'UCIP' or version != 1 or reserved or kind not in (1, 2):
        raise ValueError('invalid clipboard header')
    if size > MAX_BYTES or (kind == 2 and size):
        raise ValueError('invalid clipboard size')
    return kind, exact(stream, size).decode('utf-8')


def main():
    # Importing the frame helper only discovers the proven GDM session; its
    # __main__ capture/input loop is not executed.
    import importlib.util
    import pwd
    import time
    env = None
    if os.geteuid() != 0:
        # Isolated proof can run from a terminal in the real user session,
        # without installing a system service or touching existing runtime.
        selected = os.environ.copy()
        if (selected.get('DISPLAY', '').startswith(':') and
                os.path.isfile(selected.get('XAUTHORITY', '')) and
                selected.get('XDG_RUNTIME_DIR') == '/run/user/' + str(os.getuid()) and
                selected.get('DBUS_SESSION_BUS_ADDRESS', '').startswith('unix:')):
            env = selected
    else:
        spec = importlib.util.spec_from_file_location('uavf_session',
            '/usr/local/sbin/winavf-frame-bridge.py')
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            env = helper.find_display()
            if env and 1000 <= int(env['XDG_RUNTIME_DIR'].rsplit('/', 1)[1]) < 60000:
                break
            env = None
            time.sleep(1)
    if not env:
        raise RuntimeError('GDM X11 user session unavailable')
    uid = int(env['XDG_RUNTIME_DIR'].rsplit('/', 1)[1])
    if not 1000 <= uid < 60000:
        raise RuntimeError('Clipboard requires an authenticated user, not the greeter')
    user = pwd.getpwuid(uid)
    os.environ.update(env)
    os.environ.update(HOME=user.pw_dir, USER=user.pw_name, LOGNAME=user.pw_name)
    if os.geteuid() == 0:
        os.initgroups(user.pw_name, user.pw_gid)
        os.setgid(user.pw_gid)
        os.setuid(uid)
    import gi
    gi.require_version('Gtk', '3.0')
    from gi.repository import Gtk, Gdk, GLib
    ok, _ = Gtk.init_check([])
    if not ok:
        raise RuntimeError('X11 clipboard initialization failed')
    clipboard = Gtk.Clipboard.get(Gdk.SELECTION_CLIPBOARD)
    output_lock = threading.Lock()
    last_text = [None]
    generation = [0]
    input_stream = os.fdopen(os.dup(0), 'rb', buffering=0)
    output_stream = os.fdopen(os.dup(1), 'wb', buffering=0)

    def send_text(text, force=False):
        if text is None or len(text.encode('utf-8')) > MAX_BYTES:
            return
        if not force and text == last_text[0]:
            return
        last_text[0] = text
        try:
            with output_lock:
                packet = memoryview(encode(1, text))
                while packet:
                    count = output_stream.write(packet)
                    if not count:
                        raise OSError('clipboard write closed')
                    packet = packet[count:]
        except OSError:
            Gtk.main_quit()

    def query(force=False):
        captured_generation = generation[0]
        clipboard.request_text(lambda _clip, text, _data:
            send_text(text, force) if captured_generation == generation[0] else None, None)
        return False

    def receive(kind, text):
        if kind == 2:
            query(True)
        elif text != last_text[0]:
            generation[0] += 1
            last_text[0] = text
            clipboard.set_text(text, -1)
            clipboard.set_can_store(None)
        return False

    def reader():
        try:
            while True:
                kind, text = read_message(input_stream)
                GLib.idle_add(receive, kind, text)
        except (EOFError, OSError, ValueError, UnicodeError):
            GLib.idle_add(Gtk.main_quit)

    clipboard.connect('owner-change', lambda *_: query())
    threading.Thread(target=reader, name='uavf-clipboard-reader', daemon=True).start()
    print('UAVF_CLIPBOARD=READY PORT=4054 UID=%d DISPLAY=%s' % (uid, env['DISPLAY']),
          file=sys.stderr, flush=True)
    Gtk.main()
    clipboard.store()


if __name__ == '__main__':
    if sys.argv[1:] == ['--listen']:
        import socket
        import subprocess
        with socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM) as listener:
            listener.bind((socket.VMADDR_CID_ANY, 4054))
            listener.listen(1)
            print('UAVF_CLIPBOARD_LISTENER=READY PORT=4054', file=sys.stderr, flush=True)
            while True:
                connection, peer = listener.accept()
                with connection:
                    if peer[0] == 2:
                        subprocess.run([sys.executable, os.path.abspath(__file__)],
                            stdin=connection, stdout=connection, stderr=sys.stderr)
    elif not sys.argv[1:]:
        main()
    else:
        raise SystemExit('Usage: uavf-clipboard.py [--listen]')
