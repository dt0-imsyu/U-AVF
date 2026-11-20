"""Bounded encode-only proof from actual Mutter desktop samples, not testsrc.

Only disposable /var/tmp diagnostic files and reversible display mode are used.
FPS includes input reading; conversion is separately measured. Mean service
time is not end-to-end latency. Sliced threads/zero lookahead eliminate frame
reordering, but this benchmark does not prove visible Android FPS.
"""
import importlib.util, json, os, resource, subprocess as S, threading, time, traceback, urllib.request, zlib
from pathlib import Path
import gi
gi.require_version('Gst', '1.0')
from gi.repository import Gio, GLib, Gst

directory = Path('/var/tmp/uavf-software-encode-20261001')
directory.mkdir(exist_ok=True)
report = {'kind': 'real-GNOME-corpus-encode-only', 'width': 1920, 'height': 1200, 'tests': []}
loop = GLib.MainLoop()
Gst.init(None)
bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
dest = 'org.gnome.Mutter.ScreenCast'
session = pipeline = None
original = output = None
frames = []
last_sample = 0
def call(path, interface, method, args=None):
    return bus.call_sync(dest, path, interface, method, args, None, Gio.DBusCallFlags.NONE, 5000, None)
def sample(sink):
    global last_sample
    item = sink.emit('pull-sample')
    now = time.monotonic()
    if now-last_sample < .05 or len(frames) >= 32:
        return Gst.FlowReturn.OK
    b = item.get_buffer()
    ok, mapped = b.map(Gst.MapFlags.READ)
    if ok:
        try:
            if len(mapped.data) != 1920*1200*4:
                raise RuntimeError('Unexpected stride/size: '+str(len(mapped.data)))
            frames.append(bytes(mapped.data))
            last_sample = now
        finally:
            b.unmap(mapped)
    if len(frames) == 32:
        GLib.idle_add(loop.quit)
    return Gst.FlowReturn.OK
def added(conn, sender, path, interface, signal, params, *unused):
    global pipeline
    pipeline = Gst.parse_launch('pipewiresrc path=%d do-timestamp=true ! video/x-raw,format=BGRx ! appsink name=c emit-signals=true max-buffers=1 drop=true sync=false enable-last-sample=false' % params.unpack()[0])
    pipeline.get_by_name('c').connect('new-sample', sample)
    pipeline.set_state(Gst.State.PLAYING)
def run(label, cmd, count):
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    start = time.monotonic()
    err = directory/(label+'.stderr')
    with err.open('wb') as f:
        p = S.Popen(cmd, stdout=S.DEVNULL, stderr=f)
        tasks = 0
        while p.poll() is None:
            try: tasks = max(tasks, len(list(Path('/proc/%d/task'%p.pid).iterdir())))
            except OSError: pass
            if time.monotonic()-start > 35:
                p.kill(); p.wait()
                raise RuntimeError(label+' exceeded 35 second bound')
            time.sleep(.01)
    elapsed = time.monotonic()-start
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    cpu = after.ru_utime+after.ru_stime-before.ru_utime-before.ru_stime
    result = {'label': label, 'exit': p.returncode, 'frames': count, 'wall_seconds': elapsed,
              'fps': count/elapsed, 'mean_service_ms': elapsed*1000/count,
              'cpu_percent_one_core': cpu/elapsed*100, 'average_cpu_cores': cpu/elapsed,
              'max_process_threads': tasks, 'command': cmd, 'stderr': err.read_text()[-5000:]}
    report['tests'].append(result)
    print(json.dumps({k:v for k,v in result.items() if k not in ('stderr','command')}), flush=True)
    if p.returncode: raise RuntimeError(label+' failed')
    return result
try:
    report['encoders'] = S.check_output(['ffmpeg','-hide_banner','-encoders'], stderr=S.STDOUT, text=True)
    modes = S.check_output(['xrandr','--current'], text=True)
    output = next(l.split()[0] for l in modes.splitlines() if ' connected' in l)
    original = next(l.split()[0] for l in modes.splitlines() if '*' in l and l.startswith(' '))
    S.run(['xrandr','--output',output,'--mode','1920x1200'], check=True)
    session = call('/org/gnome/Mutter/ScreenCast',dest,'CreateSession',GLib.Variant('(a{sv})',({},))).unpack()[0]
    stream = call(session,dest+'.Session','RecordArea',GLib.Variant('(iiiia{sv})',(0,0,1920,1200,{'cursor-mode':GLib.Variant('u',1)}))).unpack()[0]
    bus.signal_subscribe(dest,dest+'.Stream','PipeWireStreamAdded',stream,None,Gio.DBusSignalFlags.NONE,added,None)
    call(session,dest+'.Session','Start')
    spec = importlib.util.spec_from_file_location('motion', str(Path(__file__).with_name('fps-scraper-ab.py')))
    motion = importlib.util.module_from_spec(spec); spec.loader.exec_module(motion)
    def move():
        time.sleep(.3)
        try: report['motion'] = motion.motion(dict(os.environ), 5)
        except Exception: report['motion_error'] = traceback.format_exc()
    worker = threading.Thread(target=move); worker.start()
    GLib.timeout_add_seconds(8, lambda: (loop.quit(), False)[1])
    loop.run(); worker.join(timeout=6)
    call(session,dest+'.Session','Stop'); session = None
    if pipeline: pipeline.set_state(Gst.State.NULL)
    crcs = [zlib.crc32(x) for x in frames]
    report['corpus'] = {'frames': len(frames), 'unique_crcs': len(set(crcs)), 'crc32': crcs,
                        'source': 'Mutter RecordArea of real GNOME while moving existing terminal'}
    if len(frames)<16 or len(set(crcs))<16: raise RuntimeError('Insufficient real changing frames')
    raw = directory/'desktop.bgr0'
    with raw.open('wb') as f:
        for frame in frames: f.write(frame)
    frames.clear()
    S.run(['xrandr','--output',output,'--mode',original], check=True)
    original = None
    yuv = directory/'desktop.i420'
    common = ['ffmpeg','-hide_banner','-nostdin','-y','-benchmark','-loglevel','info','-filter_threads','1']
    source = ['-stream_loop','-1','-f','rawvideo','-pixel_format','bgr0','-video_size','1920x1200','-framerate','60','-i',str(raw)]
    run('conversion-i420',common+source+['-frames:v','600','-pix_fmt','yuv420p','-f','null','-'],600)
    S.run(common+source+['-frames:v','32','-pix_fmt','yuv420p','-f','rawvideo',str(yuv)], stdout=S.DEVNULL, stderr=S.DEVNULL, check=True, timeout=15)
    source = ['-stream_loop','-1','-f','rawvideo','-pixel_format','yuv420p','-video_size','1920x1200','-framerate','60','-i',str(yuv)]
    for threads in (2,4):
        out = directory/('x264-%d.h264'%threads)
        result = run('x264-%d'%threads,common+source+['-frames:v','600','-c:v','libx264','-preset','ultrafast','-tune','zerolatency','-threads',str(threads),'-crf','25','-x264-params','sliced-threads=1:rc-lookahead=0:sync-lookahead=0:bframes=0:keyint=60:repeat-headers=1','-f','h264',str(out)],600)
        result['bytes'] = out.stat().st_size
        result['bitrate_mbps_at_60fps'] = out.stat().st_size*8/10/1e6
        result['no_reordering'] = True
        report['decision'] = 'MediaCodec Surface prototype required' if result['fps']>=50 else 'Try next bounded software option'
        if result['fps'] >=50: break
except Exception:
    report['error'] = traceback.format_exc()
finally:
    if pipeline: pipeline.set_state(Gst.State.NULL)
    if session:
        try: call(session,dest+'.Session','Stop')
        except Exception: pass
    if original: S.run(['xrandr','--output',output,'--mode',original])
    data = json.dumps(report,indent=2).encode()
    (directory/'report.json').write_bytes(data)
    urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=data,method='POST'),timeout=15).read()
    print('ENCODE_REPORT_SAVED', flush=True)
