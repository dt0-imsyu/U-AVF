"""One bounded passive compositor DAMAGE audit, without capture/encode/readback."""
import ctypes as C
import importlib.util
import json
import os
from pathlib import Path
import subprocess as S
import threading
import time
import urllib.request
from gi.repository import Gio, GLib

report = {'kind': 'compositor-damage-without-capture', 'seconds': 20}
stop = threading.Event()
worker = None
display = None
handles = []
overlay = 0
mode = 'uavf-cadence-90-diagnostic'
added = created = stopped = False
try:
    S.run(['sudo', '-n', '-v'], check=True)
    spec = importlib.util.spec_from_file_location('motion', Path(__file__).with_name('fps-scraper-ab.py'))
    motion = importlib.util.module_from_spec(spec); spec.loader.exec_module(motion)
    env = motion.session(); os.environ.update(env)
    modes = S.check_output(['xrandr', '--current'], text=True)
    output = next(l.split()[0] for l in modes.splitlines() if ' connected' in l)
    original = next(l.split()[0] for l in modes.splitlines() if '*' in l and l.startswith(' '))
    S.run(['sudo', '-n', 'systemctl', 'stop', 'winavf-frame.socket'], check=True); stopped = True
    units = S.check_output(['systemctl', 'list-units', '--type=service', '--state=active', '--plain', '--no-legend'], text=True)
    instances = [l.split()[0] for l in units.splitlines() if l.split() and l.split()[0].startswith('winavf-frame@')]
    if instances: S.run(['sudo', '-n', 'systemctl', 'stop', *instances], check=True)
    S.run(['xrandr', '--output', output, '--mode', '1920x1200', '--fb', '1920x1200'], check=True)
    time.sleep(1)
    motion.full_frame_motion(env, prepare_only=True)
    S.run(['xrandr', '--newmode', mode, '231.192', '1920', '1968', '2000', '2080', '1200', '1203', '1209', '1235', '+hsync', '-vsync'], check=True); created = True
    S.run(['xrandr', '--addmode', output, mode], check=True); added = True
    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    def state():
        return bus.call_sync('org.gnome.Mutter.DisplayConfig','/org/gnome/Mutter/DisplayConfig',
            'org.gnome.Mutter.DisplayConfig','GetCurrentState',None,None,Gio.DBusCallFlags.NONE,5000,None).unpack()
    def apply(current, mode_id, scale):
        bus.call_sync('org.gnome.Mutter.DisplayConfig','/org/gnome/Mutter/DisplayConfig',
            'org.gnome.Mutter.DisplayConfig','ApplyMonitorsConfig',
            GLib.Variant('(uua(iiduba(ssa{sv}))a{sv})',(current[0],1,[(0,0,scale,0,True,[(output,mode_id,{})])],{})),
            None,Gio.DBusCallFlags.NONE,5000,None)
    current = state()
    old_mode = next(m[0] for monitor in current[1] if monitor[0][0]==output for m in monitor[1] if m[6].get('is-current'))
    old_scale = current[2][0][2]
    requested = next(m[0] for monitor in current[1] if monitor[0][0]==output for m in monitor[1] if m[1:3]==(1920,1200) and abs(m[3]-90)<.1)
    apply(current, requested, 1.0)
    report['temporary_mutter_mode'] = requested
    deadline = time.monotonic() + 3
    while True:
        report['xrandr'] = S.check_output(['xrandr', '--current'], text=True)
        if 'current 1920 x 1200' in report['xrandr'].splitlines()[0]: break
        if time.monotonic() >= deadline: raise RuntimeError('Root geometry did not settle at1920x1200')
        time.sleep(.1)
    report['mutter_packages'] = S.run(['dpkg-query', '-W', 'libmutter-14-0', 'gnome-shell'], capture_output=True, text=True).stdout
    report['mutter_display_state'] = S.run(['gdbus', 'call', '--session', '--dest', 'org.gnome.Mutter.DisplayConfig', '--object-path', '/org/gnome/Mutter/DisplayConfig', '--method', 'org.gnome.Mutter.DisplayConfig.GetCurrentState'], capture_output=True, text=True, timeout=5).stdout
    x = C.CDLL('libX11.so.6'); damage = C.CDLL('libXdamage.so.1'); composite = C.CDLL('libXcomposite.so.1')
    P = C.c_void_p; L = C.c_ulong; I = C.c_int; U = C.c_uint
    def bind(lib, name, result, args):
        f = getattr(lib, name); f.restype = result; f.argtypes = args; return f
    display = bind(x, 'XOpenDisplay', P, [C.c_char_p])(None)
    if not display: raise RuntimeError('Cannot open real X display')
    root = bind(x, 'XDefaultRootWindow', L, [P])(display)
    overlay = bind(composite, 'XCompositeGetOverlayWindow', L, [P, L])(display, root)
    rr = L(); parent = L(); items = C.POINTER(L)(); n = U()
    bind(x, 'XQueryTree', I, [P,L,C.POINTER(L),C.POINTER(L),C.POINTER(C.POINTER(L)),C.POINTER(U)])(display,overlay,C.byref(rr),C.byref(parent),C.byref(items),C.byref(n))
    drawables = [int(items[k]) for k in range(n.value)] or [int(overlay)]
    if items: bind(x, 'XFree', I, [P])(items)
    gl = C.CDLL('libGL.so.1')
    report['glx_extensions'] = bind(gl, 'glXQueryExtensionsString', C.c_char_p, [P,I])(display,0).decode()
    report['glx_msc_rates'] = []
    if 'GLX_OML_sync_control' in report['glx_extensions']:
        get_proc = bind(gl,'glXGetProcAddressARB',P,[C.c_char_p])
        address = get_proc(b'glXGetMscRateOML')
        if address:
            get_rate = C.CFUNCTYPE(I,P,L,C.POINTER(C.c_int32),C.POINTER(C.c_int32))(address)
            # Public read-only query. A foreign compositor drawable may be
            # rejected; record X errors instead of terminating the process.
            errors = []
            handler = C.CFUNCTYPE(I,P,P)(lambda d,e: errors.append('foreign_drawable_query_rejected') or 0)
            set_handler = bind(x,'XSetErrorHandler',P,[P]); previous = set_handler(C.cast(handler,P))
            try:
                for drawable in drawables:
                    numerator=C.c_int32(); denominator=C.c_int32()
                    ok=get_rate(display,drawable,C.byref(numerator),C.byref(denominator))
                    report['glx_msc_rates'].append({'drawable':drawable,'ok':ok,'numerator':numerator.value,'denominator':denominator.value})
                bind(x,'XSync',I,[P,I])(display,0)
            finally: set_handler(previous)
            report['glx_query_errors'] = errors
    base = I(); error = I()
    if not bind(damage, 'XDamageQueryExtension', I, [P,C.POINTER(I),C.POINTER(I)])(display,C.byref(base),C.byref(error)): raise RuntimeError('DAMAGE unavailable')
    for drawable in drawables: handles.append(bind(damage,'XDamageCreate',L,[P,L,I])(display,drawable,3))
    subtract = bind(damage,'XDamageSubtract',None,[P,L,L,L])
    pending = bind(x,'XPending',I,[P]); next_event = bind(x,'XNextEvent',I,[P,P]); flush = bind(x,'XFlush',I,[P])
    class Event(C.Structure):
        _fields_ = [('type',I),('serial',L),('send_event',I),('display',P),('drawable',L),('damage',L),('level',I),('more',I),('timestamp',L)]
    buf = C.create_string_buffer(192); ready = threading.Event()
    def animate():
        report['motion'] = motion.full_frame_motion(env, 25, stop_event=stop, request_hz=120, ready_event=ready, reuse_texture=True)
    worker = threading.Thread(target=animate); worker.start()
    if not ready.wait(5): raise RuntimeError('Fixture not ready')
    time.sleep(1); start = time.monotonic(); at = []
    while time.monotonic() - start < 20:
        while pending(display):
            next_event(display, buf); event = C.cast(buf,C.POINTER(Event)).contents
            if event.type == base.value and event.damage in handles:
                at.append(time.monotonic() - start); subtract(display,event.damage,0,0)
        flush(display); time.sleep(.0005)
    report.update(drawables=drawables, damage_fps=len(at)/20, per_second=[sum(i<=t<i+1 for t in at) for i in range(20)])
    report['xrandr_after'] = S.check_output(['xrandr', '--current'], text=True)
except Exception as exc:
    report['error'] = repr(exc)
finally:
    stop.set()
    if worker: worker.join(3)
    if display:
        for h in handles: bind(damage,'XDamageDestroy',None,[P,L])(display,h)
        if overlay: bind(composite,'XCompositeReleaseOverlayWindow',None,[P,L])(display,root)
        bind(x,'XCloseDisplay',I,[P])(display)
    if added:
        if 'old_mode' in globals():
            try: apply(state(), old_mode, old_scale)
            except Exception as exc: report['mutter_restore_error'] = repr(exc)
        S.run(['xrandr','--output',output,'--mode',original],check=False)
        S.run(['xrandr','--delmode',output,mode],check=False)
    if created: S.run(['xrandr','--rmmode',mode],check=False)
    if stopped: report['fallback_restore_exit'] = S.run(['sudo','-n','systemctl','start','winavf-frame.socket']).returncode
    urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=json.dumps(report).encode(),method='POST'),timeout=10).read()
    print('COMPOSITOR_CADENCE_SAVED', flush=True)
