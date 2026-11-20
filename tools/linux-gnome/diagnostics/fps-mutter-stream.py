"""One bounded compositor/WAVF proof. No production guest files are replaced."""
import importlib.util
import json
import os
import struct
import subprocess
import threading
import time
import traceback
import zlib
from pathlib import Path
import gi
gi.require_version('Gst', '1.0')
from gi.repository import Gio, GLib, Gst

def log(message):
    print(message, file=os.sys.stderr, flush=True)

spec = importlib.util.spec_from_file_location('codec', str(Path(__file__).with_name('uavf-fast-delta.py')))
codec = importlib.util.module_from_spec(spec)
spec.loader.exec_module(codec)
encoder = codec.FastDelta()
Gst.init(None)
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
dest = 'org.gnome.Mutter.ScreenCast'
loop = GLib.MainLoop()
condition = threading.Condition()
latest = None
stopping = False
session = pipeline = worker = motion_worker = None
stats = dict(source=0, sent=0, dropped=0, bytes=0, encode_seconds=0.0,
             write_seconds=0.0, map_seconds=0.0)
started = time.monotonic()
original_mode = output = None
report = {'kind': 'Mutter-RecordArea-WAVF-LZ4-full-frame-AB'}
controlled_ab = os.environ.get('UAVF_CAPTURE_CONTROLLED_AB') == '1'
async_write = os.environ.get('UAVF_CAPTURE_ASYNC_WRITE') == '1'
write_condition = threading.Condition()
pending_packet = None
writer_stopping = False
writer = None
stats.update(encoded=0, write_coalesced=0)
phases = {'source_only': {'source': 0}, 'full_pipeline': {'source': 0, 'sent': 0}}
phase_started = None

def phase():
    if not controlled_ab:
        return 'full_pipeline'
    elapsed = time.monotonic() - phase_started if phase_started else -1
    return ('warmup' if elapsed < 0 else 'source_only' if elapsed < 20
            else 'full_pipeline' if elapsed < 40 else 'idle')

def call(path, interface, method, args=None):
    return bus.call_sync(dest, path, interface, method, args, None,
                         Gio.DBusCallFlags.NONE, 5000, None)

def write_packet(packet, captured_at):
    before = time.monotonic()
    remaining = memoryview(packet)
    while remaining:
        written = os.write(1, remaining)
        if written <= 0:
            raise BrokenPipeError('vsock closed')
        remaining = remaining[written:]
    stats['write_seconds'] += time.monotonic() - before
    stats['sent'] += 1
    if controlled_ab:
        phases['full_pipeline']['sent'] += 1
    stats['bytes'] += len(packet)
    stats['last_capture_to_write_ms'] = 1000 * (time.monotonic() - captured_at)

def socket_writer():
    global pending_packet
    try:
        while True:
            with write_condition:
                write_condition.wait_for(lambda: pending_packet is not None or writer_stopping)
                if pending_packet is None and writer_stopping:
                    return
                packet, captured_at = pending_packet
                pending_packet = None
            # Finish the whole record. Only queued independent keyframes may
            # be replaced; never interrupt a partially written WAVF record.
            write_packet(packet, captured_at)
    except Exception:
        report['writer_error'] = traceback.format_exc()
        log(report['writer_error'])
        GLib.idle_add(loop.quit)

def sender():
    global latest, stopping, pending_packet
    sequence = 1
    try:
        while True:
            with condition:
                condition.wait_for(lambda: latest is not None or stopping)
                if stopping:
                    return
                item, captured_at = latest
                latest = None
            caps = item.get_caps().get_structure(0)
            width, height = caps.get_value('width'), caps.get_value('height')
            if caps.get_value('format') not in ('BGRA', 'BGRx') or not (0 < width <= 4096 and 0 < height <= 4096):
                raise ValueError('Unexpected capture format/bounds')
            buffer = item.get_buffer()
            if buffer.get_size() != width * height * 4:
                raise ValueError('Padded image unsupported in this proof')
            before = time.monotonic()
            ok, mapped = buffer.map(Gst.MapFlags.READ)
            if not ok:
                raise RuntimeError('Capture map failed')
            try:
                # One owned immutable copy; Gst buffer is released before socket I/O.
                pixels = bytearray(mapped.data)
                native_xrgb = caps.get_value('format') == 'BGRx'
            finally:
                buffer.unmap(mapped)
            # Gst.Sample keeps the PipeWire buffer borrowed even after unmap.
            # Return it before encoding/writing, so a tiny upstream pool cannot
            # throttle compositor capture while the socket is busy.
            item = buffer = caps = mapped = None
            if native_xrgb:
                pixels[3::4] = b'\xff' * (width * height)
            stats['map_seconds'] += time.monotonic() - before
            before = time.monotonic()
            flags, payload = encoder.encode(pixels)
            packet = struct.pack('<4sBBBBHHIIII', b'WAVF', 1, 1, flags, 0,
                width, height, sequence, len(payload), zlib.crc32(payload), 0) + payload
            stats['encode_seconds'] += time.monotonic() - before
            stats['encoded'] += 1
            if async_write:
                with write_condition:
                    if pending_packet is not None:
                        stats['write_coalesced'] += 1
                    pending_packet = (packet, captured_at)
                    write_condition.notify()
            else:
                write_packet(packet, captured_at)
            sequence += 1
    except Exception:
        report['sender_error'] = traceback.format_exc()
        log(report['sender_error'])
        GLib.idle_add(loop.quit)

def sample(sink):
    global latest
    item = sink.emit('pull-sample')
    with condition:
        stats['source'] += 1
        current = phase()
        if controlled_ab:
            if current in phases:
                phases[current]['source'] += 1
            if current != 'full_pipeline':
                return Gst.FlowReturn.OK
        if latest is not None:
            stats['dropped'] += 1
        latest = (item, time.monotonic())
        condition.notify()
    return Gst.FlowReturn.OK

def stream_added(conn, sender_name, path, interface, signal, params, *unused):
    global pipeline
    try:
        node = params.unpack()[0]
        pipeline = Gst.parse_launch(
            'pipewiresrc path=%d do-timestamp=true ! '
            'video/x-raw,format=BGRx ! appsink name=capture emit-signals=true '
            'max-buffers=1 drop=true sync=false enable-last-sample=false' % node)
        pipeline.get_by_name('capture').connect('new-sample', sample)
        pipeline.set_state(Gst.State.PLAYING)
    except Exception:
        report['stream_error'] = traceback.format_exc()
        loop.quit()

previous = dict(stats)
previous_at = started
def metrics():
    global previous, previous_at
    now = time.monotonic()
    elapsed = now - previous_at
    delta = {k: stats[k] - previous[k] for k in previous}
    sent = max(1, delta['sent'])
    encoded = max(1, delta['encoded'])
    log('MUTTER_STREAM phase=%s source_fps=%.2f sent_fps=%.2f dropped=%d bytes_per_s=%.0f map_ms=%.2f encode_ms=%.2f write_ms=%.2f age_ms=%.2f queue_max=1 encoded_fps=%.2f write_coalesced=%d' % (
        phase(),
        delta['source']/elapsed, delta['sent']/elapsed, delta['dropped'],
        delta['bytes']/elapsed, 1000*delta['map_seconds']/encoded,
        1000*delta['encode_seconds']/encoded, 1000*delta['write_seconds']/sent,
        stats.get('last_capture_to_write_ms', 0), delta['encoded']/elapsed,
        delta['write_coalesced']))
    previous = {k: stats[k] for k in previous}
    previous_at = now
    if pipeline:
        error = pipeline.get_bus().pop_filtered(Gst.MessageType.ERROR)
        if error:
            report['pipeline_error'] = str(error.parse_error())
            loop.quit()
            return False
    return True

try:
    modes = subprocess.check_output(['xrandr', '--current'], text=True)
    output = next(line.split()[0] for line in modes.splitlines() if ' connected' in line)
    original_mode = next(line.split()[0] for line in modes.splitlines() if '*' in line and line.startswith(' '))
    subprocess.run(['xrandr', '--output', output, '--mode', '1920x1200'], check=True)
    session = call('/org/gnome/Mutter/ScreenCast', dest, 'CreateSession',
                   GLib.Variant('(a{sv})', ({},))).unpack()[0]
    stream = call(session, dest + '.Session', 'RecordArea',
                  GLib.Variant('(iiiia{sv})', (0, 0, 1920, 1200, {
                      'cursor-mode': GLib.Variant('u', 1)}))).unpack()[0]
    bus.signal_subscribe(dest, dest + '.Stream', 'PipeWireStreamAdded', stream,
                         None, Gio.DBusSignalFlags.NONE, stream_added, None)
    worker = threading.Thread(target=sender, daemon=True)
    if async_write:
        writer = threading.Thread(target=socket_writer, daemon=True)
        writer.start()
    worker.start()
    call(session, dest + '.Session', 'Start')
    def move():
        global phase_started
        spec = importlib.util.spec_from_file_location('motion', str(Path(__file__).with_name('fps-scraper-ab.py')))
        motion = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(motion)
        time.sleep(4)
        phase_started = time.monotonic()
        if controlled_ab:
            report['movement'] = [motion.motion(os.environ.copy(), 20),
                                  motion.motion(os.environ.copy(), 20)]
        else:
            report['movement'] = motion.motion(os.environ.copy(), 25)
    motion_worker = threading.Thread(target=move, daemon=True)
    motion_worker.start()
    duration = 50 if controlled_ab else 35
    log('FRAME_CAPTURE=MUTTER_RECORD_AREA_AB WAVF=1 LZ4=2 QUEUE=1 DURATION=%d CONTROLLED_AB=%s ASYNC_WRITE=%s' % (duration, controlled_ab, async_write))
    GLib.timeout_add_seconds(5, metrics)
    GLib.timeout_add_seconds(duration, lambda: (loop.quit(), False)[1])
    loop.run()
except Exception:
    report['error'] = traceback.format_exc()
    log(report['error'])
finally:
    with condition:
        stopping = True
        latest = None
        condition.notify_all()
    if worker:
        worker.join(timeout=6)
        if worker.is_alive():
            # Never append fallback packets behind a partially written record.
            log('MUTTER_STREAM_SEND_STUCK; close this connection')
            os._exit(3)
    if pipeline:
        pipeline.set_state(Gst.State.NULL)
    if writer:
        with write_condition:
            writer_stopping = True
            write_condition.notify_all()
        writer.join(timeout=6)
        if writer.is_alive():
            log('MUTTER_STREAM_WRITE_STUCK; close this connection')
            os._exit(3)
    if session:
        try:
            call(session, dest + '.Session', 'Stop')
        except Exception as exc:
            log(str(exc))
    if original_mode:
        subprocess.run(['xrandr', '--output', output, '--mode', original_mode])
    report.update(stats=stats, elapsed=time.monotonic()-started)
    if controlled_ab:
        report.update(phases=phases, phase_seconds=20,
                      comparison='same session, drawable, RecordArea and 40s motion; source-only then full pipeline')
    Path('/var/tmp/uavf-mutter-stream-report.json').write_text(json.dumps(report, indent=2))
    log('MUTTER_STREAM_FINISHED ' + json.dumps(report))
