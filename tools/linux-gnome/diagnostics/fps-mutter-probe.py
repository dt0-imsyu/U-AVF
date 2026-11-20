"""Read-only capability audit; temporary Mutter session is closed in finally."""
import json
import traceback
import urllib.request
from pathlib import Path

report = {}
session = None
try:
    import gi
    from gi.repository import Gio, GLib
    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    dest = 'org.gnome.Mutter.ScreenCast'
    def call(path, interface, method, args=None):
        return bus.call_sync(dest, path, interface, method, args, None,
                             Gio.DBusCallFlags.NONE, 5000, None)
    report['root_xml'] = call('/org/gnome/Mutter/ScreenCast',
        'org.freedesktop.DBus.Introspectable', 'Introspect').unpack()[0]
    session = call('/org/gnome/Mutter/ScreenCast', dest, 'CreateSession',
                   GLib.Variant('(a{sv})', ({},))).unpack()[0]
    report['session_xml'] = call(session, 'org.freedesktop.DBus.Introspectable',
                                 'Introspect').unpack()[0]
    gi.require_version('Gst', '1.0')
    from gi.repository import Gst
    Gst.init(None)
    report['gst_version'] = Gst.version_string()
    report['elements'] = {name: Gst.ElementFactory.find(name) is not None
                          for name in ['pipewiresrc', 'appsink', 'videoconvert']}
except Exception:
    report['error'] = traceback.format_exc()
finally:
    if session:
        try:
            call(session, 'org.gnome.Mutter.ScreenCast.Session', 'Stop')
            report['session_closed'] = True
        except Exception as exc:
            report['close_error'] = str(exc)
data = json.dumps(report, indent=2).encode()
Path('/var/tmp/uavf-mutter-probe.json').write_bytes(data)
print(data.decode(), flush=True)
try:
    urllib.request.urlopen(urllib.request.Request(
        'http://10.159.93.215:39054/report', data=data, method='POST'), timeout=5).read()
except Exception as exc:
    print('Evidence retained in /var/tmp:', exc)
