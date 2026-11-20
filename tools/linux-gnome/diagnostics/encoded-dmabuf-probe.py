"""Bounded capture negotiation proof; leaves raw display and desktop untouched."""
import json
import sys
import time
import urllib.request
import gi
gi.require_version('Gst', '1.0')
from gi.repository import Gio, GLib, Gst

Gst.init(None)
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
dest = 'org.gnome.Mutter.ScreenCast'
report = {'kind': 'mutter-dmabuf-negotiation', 'frames': 0}
legacy = '--legacy-bgrx' in sys.argv
report['requested_format'] = 'BGRx/DMABuf' if legacy else 'DMA_DRM/DMABuf -> GL -> I420'
loop = GLib.MainLoop()
pipeline = session = None

def call(path, interface, method, args=None):
    return bus.call_sync(dest, path, interface, method, args, None,
                         Gio.DBusCallFlags.NONE, 5000, None)

def added(conn, sender, path, interface, signal, params, unused):
    global pipeline
    try:
        pipeline = Gst.parse_launch(
            'pipewiresrc name=pw path=%d do-timestamp=true '
            '! video/x-raw(memory:DMABuf),format=%s ! identity name=source '
            '%s ! fakesink name=sink sync=false signal-handoffs=true' % (
                params.unpack()[0], 'BGRx' if legacy else 'DMA_DRM',
                '' if legacy else '! glupload ! glcolorconvert ! gldownload ! video/x-raw,format=I420'))
        def handoff(sink, buffer, pad):
            report['frames'] += 1
            report['output_caps'] = pad.get_current_caps().to_string()
            report['source_caps'] = pipeline.get_by_name('source').get_static_pad('src').get_current_caps().to_string()
        pipeline.get_by_name('sink').connect('handoff', handoff)
        pipeline.set_state(Gst.State.PLAYING)
    except Exception as error:
        report['error'] = str(error)
        loop.quit()

def tick():
    if pipeline:
        message = pipeline.get_bus().pop_filtered(Gst.MessageType.ERROR)
        if message:
            error, debug = message.parse_error()
            report['error'] = str(error)
            report['debug'] = debug
            loop.quit()
            return False
    return True

try:
    session = call('/org/gnome/Mutter/ScreenCast', dest, 'CreateSession',
                   GLib.Variant('(a{sv})', ({},))).unpack()[0]
    stream = call(session, dest + '.Session', 'RecordArea',
                  GLib.Variant('(iiiia{sv})', (0, 0, 1280, 800,
                      {'cursor-mode': GLib.Variant('u', 1)}))).unpack()[0]
    bus.signal_subscribe(dest, dest + '.Stream', 'PipeWireStreamAdded', stream,
                         None, Gio.DBusSignalFlags.NONE, added, None)
    call(session, dest + '.Session', 'Start')
    started = time.monotonic()
    GLib.timeout_add(100, tick)
    GLib.timeout_add_seconds(8, lambda: (loop.quit(), False)[1])
    loop.run()
    report['seconds'] = time.monotonic() - started
finally:
    if pipeline: pipeline.set_state(Gst.State.NULL)
    if session:
        try: call(session, dest + '.Session', 'Stop')
        except Exception: pass
    report['verdict'] = ('DMA_BUF_SOURCE_PASS' if legacy else 'DMA_BUF_TO_I420_PASS') if report['frames'] else 'NO_DMABUF_FRAMES_OBSERVED'
    data = json.dumps(report, indent=2).encode()
    urllib.request.urlopen(urllib.request.Request(
        'http://10.159.93.215:39054/report', data=data, method='POST'), timeout=15).read()
    print(data.decode(), flush=True)
