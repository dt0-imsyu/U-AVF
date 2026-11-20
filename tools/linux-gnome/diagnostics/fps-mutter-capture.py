"""Bounded real-compositor PipeWire proof; preserves the production stream."""
import json
import time
import traceback
import urllib.request
import subprocess
import zlib
import sys
import os
import threading
import importlib.util
from pathlib import Path
import gi
gi.require_version('Gst', '1.0')
from gi.repository import Gio, GLib, Gst

Gst.init(None)
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
dest = 'org.gnome.Mutter.ScreenCast'
report = {'kind': 'Mutter-PipeWire-source-only-not-Android-FPS', 'samples': 0}
session = None
pipeline = None
loop = GLib.MainLoop()
started = time.monotonic()
original_mode = None
motion_worker = None
scraper_stopped = False
cpu_before = {}
def cpu_snapshot():
    result = {}
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            name = (proc / 'comm').read_text().strip()
            if name not in ('gnome-shell', 'Xorg', 'pipewire', 'python3'):
                continue
            fields = (proc / 'stat').read_text().rsplit(')', 1)[1].split()
            result[proc.name] = {'name': name, 'ticks': int(fields[11]) + int(fields[12])}
        except (OSError, ValueError):
            pass
    return result
def call(path, interface, method, args=None):
    return bus.call_sync(dest, path, interface, method, args, None,
                         Gio.DBusCallFlags.NONE, 5000, None)
def sample(sink):
    global started
    try:
        item = sink.emit('pull-sample')
        buffer = item.get_buffer()
        report['samples'] += 1
        second = int(time.monotonic() - started)
        bins = report.setdefault('sample_bins', {})
        bins[str(second)] = bins.get(str(second), 0) + 1
        if report['samples'] == 1:
            started = time.monotonic()
            report['caps'] = item.get_caps().to_string()
            report['bytes'] = buffer.get_size()
            ok, mapped = buffer.map(Gst.MapFlags.READ)
            if ok:
                try:
                    data = mapped.data
                    report['crc32'] = '%08X' % zlib.crc32(data)
                    # Sample B/G/R bytes, not the opaque alpha channel.
                    values = [data[i] for i in range(0, len(data), 4096)]
                    report['sampled_blue_min_max'] = [min(values), max(values)]
                    report['sampled_blue_nonzero'] = sum(v != 0 for v in values)
                finally:
                    buffer.unmap(mapped)
        return Gst.FlowReturn.OK
    except Exception:
        report['sample_error'] = traceback.format_exc()
        GLib.idle_add(loop.quit)
        return Gst.FlowReturn.ERROR
def stream_added(conn, sender, path, interface, signal, params, *unused):
    global pipeline
    try:
        node = params.unpack()[0]
        report['node_id'] = node
        conversion = 'video/x-raw ! ' if '--native' in sys.argv else 'videoconvert ! video/x-raw,format=BGRA ! '
        report['native_format'] = '--native' in sys.argv
        pipeline = Gst.parse_launch(
            ('pipewiresrc path=%d do-timestamp=true ! ' % node) + conversion +
            'appsink name=capture emit-signals=true '
            'max-buffers=1 drop=true sync=false')
        pipeline.get_by_name('capture').connect('new-sample', sample)
        pipeline.set_state(Gst.State.PLAYING)
        def watch_bus():
            msg = pipeline.get_bus().pop_filtered(Gst.MessageType.ERROR)
            if msg:
                error, debug = msg.parse_error()
                report['pipeline_error'] = str(error)
                report['pipeline_debug'] = debug
                loop.quit()
                return False
            return True
        GLib.timeout_add(100, watch_bus)
    except Exception:
        report['stream_error'] = traceback.format_exc()
        loop.quit()
try:
    modes = subprocess.check_output(['xrandr', '--current'], text=True)
    report['xrandr'] = modes
    connector = next(line.split()[0] for line in modes.splitlines()
                     if ' connected' in line)
    if '--motion' in sys.argv:
        original_mode = next(line.split()[0] for line in modes.splitlines()
                             if '*' in line and line.startswith(' '))
        subprocess.run(['xrandr', '--output', connector, '--mode', '1920x1200'], check=True)
        spec = importlib.util.spec_from_file_location('motion', '/tmp/uavf-fps-ab.py')
        motion = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(motion)
    if '--isolated' in sys.argv:
        subprocess.run(['sudo', '-n', 'systemctl', 'stop', 'winavf-xvnc.service'], check=True)
        scraper_stopped = True
        report['competing_rfb_scraper_stopped'] = True
    session = call('/org/gnome/Mutter/ScreenCast', dest, 'CreateSession',
                   GLib.Variant('(a{sv})', ({},))).unpack()[0]
    area = next((arg.split('=', 1)[1] for arg in sys.argv if arg.startswith('--area=')), None)
    if area:
        width, height = map(int, area.split('x'))
        report['requested_area'] = [0, 0, width, height]
        stream = call(session, dest + '.Session', 'RecordArea',
                      GLib.Variant('(iiiia{sv})', (0, 0, width, height, {
                          'cursor-mode': GLib.Variant('u', 1)}))).unpack()[0]
    else:
        stream = call(session, dest + '.Session', 'RecordMonitor',
                      GLib.Variant('(sa{sv})', (connector, {
                          'cursor-mode': GLib.Variant('u', 1)}))).unpack()[0]
    subscription = bus.signal_subscribe(dest, dest + '.Stream',
        'PipeWireStreamAdded', stream, None, Gio.DBusSignalFlags.NONE,
        stream_added, None)
    cpu_before = cpu_snapshot()
    cpu_started = time.monotonic()
    call(session, dest + '.Session', 'Start')
    if '--motion' in sys.argv:
        def move():
            report['movement'] = motion.motion(os.environ.copy(), 20)
        motion_worker = threading.Thread(target=move)
        motion_worker.start()
    GLib.timeout_add_seconds(22 if '--motion' in sys.argv else 15,
                             lambda: (loop.quit(), False)[1])
    loop.run()
except Exception:
    report['error'] = traceback.format_exc()
finally:
    if cpu_before:
        cpu_elapsed = time.monotonic() - cpu_started
        cpu_after = cpu_snapshot()
        report['cpu_one_core_percent'] = {
            pid + ':' + value['name']: round(100 * (cpu_after[pid]['ticks'] - value['ticks']) /
                os.sysconf('SC_CLK_TCK') / cpu_elapsed, 2)
            for pid, value in cpu_before.items() if pid in cpu_after}
    report['elapsed'] = time.monotonic() - started
    report['source_samples_per_sec'] = report['samples'] / report['elapsed']
    if pipeline:
        pipeline.set_state(Gst.State.NULL)
    if motion_worker:
        motion_worker.join(timeout=22)
    if session:
        try:
            call(session, dest + '.Session', 'Stop')
        except Exception as exc:
            report['close_error'] = str(exc)
    if original_mode:
        subprocess.run(['xrandr', '--output', connector, '--mode', original_mode])
    if scraper_stopped:
        subprocess.run(['sudo', '-n', 'systemctl', 'start', 'winavf-xvnc.service'], check=True)
data = json.dumps(report, indent=2).encode()
Path('/var/tmp/uavf-mutter-capture.json').write_bytes(data)
print(data.decode(), flush=True)
try:
    urllib.request.urlopen(urllib.request.Request(
        'http://10.159.93.215:39054/report', data=data, method='POST'), timeout=5).read()
except Exception as exc:
    print('Evidence retained in /var/tmp:', exc)
