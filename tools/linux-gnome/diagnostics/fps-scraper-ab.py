#!/usr/bin/env python3
"""One reversible existing-GDM scraper A/B; never changes boot or user files."""
import ctypes as C
import json
import math
import os
from pathlib import Path
import subprocess as S
import sys
import time
import urllib.request


def command(*args, env=None):
    return S.check_output(args, env=env, stderr=S.STDOUT, text=True)


def session():
    for p in Path('/proc').iterdir():
        try:
            if p.joinpath('comm').read_text().strip() != 'gnome-shell':
                continue
            e = dict(x.decode().split('=', 1) for x in p.joinpath('environ').read_bytes().split(b'\0') if b'=' in x)
            if e.get('XDG_SESSION_TYPE') == 'x11' and p.stat().st_uid != 0:
                return dict(os.environ, **e)
        except (OSError, UnicodeError, ValueError):
            pass
    raise RuntimeError('Real GNOME X11 session not found')


def motion(env, duration=30, trace=False, fit=False, stop_event=None, request_hz=60):
    # Move the existing active application with the WM protocol, not a synthetic drawable.
    os.environ.update({k: env[k] for k in ('DISPLAY', 'XAUTHORITY')})
    x = C.CDLL('libX11.so.6')
    x.XOpenDisplay.restype = C.c_void_p
    d = x.XOpenDisplay(None)
    if not d:
        raise RuntimeError('Cannot open real session display')
    x.XDefaultRootWindow.argtypes = [C.c_void_p]
    x.XDefaultRootWindow.restype = C.c_ulong
    root = x.XDefaultRootWindow(d)
    x.XInternAtom.argtypes = [C.c_void_p, C.c_char_p, C.c_int]
    x.XInternAtom.restype = C.c_ulong
    active = x.XInternAtom(d, b'_NET_ACTIVE_WINDOW', 0)
    actual = C.c_ulong(); fmt = C.c_int(); count = C.c_ulong(); remaining = C.c_ulong(); data = C.c_void_p()
    x.XGetWindowProperty.argtypes = [C.c_void_p, C.c_ulong, C.c_ulong, C.c_long, C.c_long, C.c_int, C.c_ulong, C.c_void_p, C.c_void_p, C.c_void_p, C.c_void_p, C.c_void_p]
    x.XGetWindowProperty(d, root, active, 0, 1, 0, 0, C.byref(actual), C.byref(fmt), C.byref(count), C.byref(remaining), C.byref(data))
    if count.value != 1 or fmt.value != 32 or not data.value:
        raise RuntimeError('No active ordinary application window')
    window = C.cast(data, C.POINTER(C.c_ulong))[0]
    x.XFree.argtypes = [C.c_void_p]; x.XFree(data)
    class Message(C.Structure):
        _fields_ = [('type', C.c_int), ('serial', C.c_ulong), ('send_event', C.c_int), ('display', C.c_void_p), ('window', C.c_ulong), ('message_type', C.c_ulong), ('format', C.c_int), ('data', C.c_long * 5)]
    class Event(C.Union):
        _fields_ = [('message', Message), ('pad', C.c_long * 24)]
    x.XSendEvent.argtypes = [C.c_void_p, C.c_ulong, C.c_int, C.c_long, C.c_void_p]
    x.XFlush.argtypes = [C.c_void_p]; x.XCloseDisplay.argtypes = [C.c_void_p]
    atom = x.XInternAtom(d, b'_NET_MOVERESIZE_WINDOW', 0)
    x.XTranslateCoordinates.argtypes = [C.c_void_p,C.c_ulong,C.c_ulong,C.c_int,C.c_int,C.c_void_p,C.c_void_p,C.c_void_p]
    observed=[]
    ox=C.c_int(); oy=C.c_int(); child=C.c_ulong()
    x.XGetGeometry.argtypes=[C.c_void_p,C.c_ulong,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p]
    gr=C.c_ulong(); gx=C.c_int(); gy=C.c_int(); gw=C.c_uint(); gh=C.c_uint(); border=C.c_uint(); depth=C.c_uint()
    x.XGetGeometry(d,window,C.byref(gr),C.byref(gx),C.byref(gy),C.byref(gw),C.byref(gh),C.byref(border),C.byref(depth))
    x.XTranslateCoordinates(d,window,root,0,0,C.byref(ox),C.byref(oy),C.byref(child))
    original_geometry=[ox.value,oy.value,gw.value,gh.value]
    if fit:
        event=Event(); event.message=Message(33,0,1,d,window,atom,32,
            (C.c_long*5)(1 | (15<<8) | (2<<12),180,140,1000,650))
        x.XSendEvent(d,root,0,(1<<20)|(1<<19),C.byref(event)); x.XFlush(d)
        time.sleep(.5)
    started = time.monotonic(); iterations = 0
    try:
        while time.monotonic() - started < duration and not (stop_event and stop_event.is_set()):
            t = time.monotonic() - started
            event = Event()
            event.message = Message(33, 0, 1, d, window, atom, 32,
                                    (C.c_long * 5)(1 | (1 << 8) | (1 << 9) | (2 << 12),
                                                 int(180 + 150 * math.sin(t * 3)),
                                                 int(140 + 100 * math.cos(t * 2)), 0, 0))
            x.XSendEvent(d, root, 0, (1 << 20) | (1 << 19), C.byref(event))
            x.XFlush(d); iterations += 1
            if trace:
                before=time.monotonic_ns()
                x.XTranslateCoordinates(d,window,root,0,0,C.byref(ox),C.byref(oy),C.byref(child))
                observed.append([time.monotonic_ns(),ox.value,oy.value,(time.monotonic_ns()-before)/1e6])
            time.sleep(max(0, started + iterations / request_hz - time.monotonic()))
    finally:
        if fit:
            event=Event(); event.message=Message(33,0,1,d,window,atom,32,
                (C.c_long*5)(1 | (15<<8) | (2<<12),*original_geometry))
            x.XSendEvent(d,root,0,(1<<20)|(1<<19),C.byref(event)); x.XFlush(d)
        x.XCloseDisplay(d)
    return {'window': window, 'movement_requests': iterations, 'seconds': time.monotonic() - started,
            'actual_positions':observed,'original_geometry':original_geometry,'fit_window':fit,'request_hz':request_hz}


def full_frame_motion(env, duration=25, stop_event=None, request_hz=90, ready_event=None,
                      prepare_only=False, reuse_texture=False):
    """Bounded fullscreen desktop-texture pan, NOT normal GNOME workload proof.

    Snapshot once, then update every pixel of a temporary compositor-managed
    window using X server blits. Never touches user files or the root pixmap.
    XSync bounds outstanding work; finally destroys only our own resources.
    """
    os.environ.update({k: env[k] for k in ('DISPLAY', 'XAUTHORITY')})
    x=C.CDLL('libX11.so.6')
    ptr=C.c_void_p; ul=C.c_ulong; integer=C.c_int; uint=C.c_uint
    def bind(name,args,result=integer):
        f=getattr(x,name); f.argtypes=args; f.restype=result; return f
    bind('XOpenDisplay',[C.c_char_p],ptr)
    bind('XDefaultRootWindow',[ptr],ul); bind('XDefaultScreen',[ptr])
    bind('XDefaultDepth',[ptr,integer]); bind('XDisplayWidth',[ptr,integer]); bind('XDisplayHeight',[ptr,integer])
    bind('XGetImage',[ptr,ul,integer,integer,uint,uint,ul,integer],ptr)
    bind('XCreatePixmap',[ptr,ul,uint,uint,uint],ul)
    bind('XCreateSimpleWindow',[ptr,ul,integer,integer,uint,uint,uint,ul,ul],ul)
    bind('XCreateGC',[ptr,ul,ul,ptr],ptr)
    bind('XPutImage',[ptr,ul,ptr,ptr,integer,integer,integer,integer,uint,uint])
    bind('XCopyArea',[ptr,ul,ul,ptr,integer,integer,uint,uint,integer,integer])
    bind('XInternAtom',[ptr,C.c_char_p,integer],ul)
    bind('XChangeProperty',[ptr,ul,ul,ul,integer,integer,ptr,integer])
    bind('XStoreName',[ptr,ul,C.c_char_p]); bind('XMapRaised',[ptr,ul])
    bind('XSync',[ptr,integer]); bind('XDestroyWindow',[ptr,ul])
    bind('XGetGeometry',[ptr,ul,ptr,ptr,ptr,ptr,ptr,ptr,ptr])
    bind('XTranslateCoordinates',[ptr,ul,ul,integer,integer,ptr,ptr,ptr])
    bind('XFreePixmap',[ptr,ul]); bind('XFreeGC',[ptr,ptr])
    bind('XDestroyImage',[ptr]); bind('XCloseDisplay',[ptr])
    d=x.XOpenDisplay(None)
    if not d: raise RuntimeError('Cannot open fullscreen diagnostic display')
    image=gc=pixmap=window=None
    try:
        root=x.XDefaultRootWindow(d); screen=x.XDefaultScreen(d)
        w=x.XDisplayWidth(d,screen); h=x.XDisplayHeight(d,screen)
        if (w,h)!=(1920,1200): raise RuntimeError('Fullscreen test requires actual 1920x1200 output')
        image=x.XGetImage(d,root,0,0,w,h,ul(-1).value,2)
        if not image: raise RuntimeError('Desktop snapshot failed')
        class ImagePrefix(C.Structure):
            _fields_=[('width',integer),('height',integer),('xoffset',integer),('format',integer),
                      ('data',ptr),('byte_order',integer),('bitmap_unit',integer),
                      ('bitmap_bit_order',integer),('bitmap_pad',integer),('depth',integer),
                      ('bytes_per_line',integer),('bits_per_pixel',integer)]
        img=C.cast(image,C.POINTER(ImagePrefix)).contents
        if img.bytes_per_line!=w*4 or img.bits_per_pixel!=32:
            raise RuntimeError('Unexpected desktop texture pixel layout')
        fixture=Path('/tmp/uavf-fullframe-texture.bgrx')
        pixels=fixture.read_bytes() if reuse_texture else C.string_at(img.data,w*h*4)
        if len(pixels)!=w*h*4: raise RuntimeError('Unexpected texture size')
        sampled={pixels[i:i+3] for i in range(0,len(pixels)-4,887*4)}
        if len(sampled)<32: raise RuntimeError('Texture is uniform/blank: refusing an invalid fullscreen FPS run')
        texture_stats={'sampled_unique_colors':len(sampled),'bytes':len(pixels),'reused_fixture':reuse_texture}
        if prepare_only:
            fixture.write_bytes(pixels)
            return texture_stats
        if reuse_texture: C.memmove(img.data,pixels,len(pixels))
        pixmap=x.XCreatePixmap(d,root,w,h,x.XDefaultDepth(d,screen))
        gc=x.XCreateGC(d,pixmap,0,None)
        x.XPutImage(d,pixmap,gc,image,0,0,0,0,w,h)
        window=x.XCreateSimpleWindow(d,root,0,0,w,h,0,0,0)
        atom=lambda name:x.XInternAtom(d,name.encode(),0)
        fullscreen=ul(atom('_NET_WM_STATE_FULLSCREEN'))
        x.XChangeProperty(d,window,atom('_NET_WM_STATE'),4,32,0,C.byref(fullscreen),1)
        # EWMH 2 requests compositing; 0 only leaves the default policy.
        bypass=ul(2)
        x.XChangeProperty(d,window,atom('_NET_WM_BYPASS_COMPOSITOR'),6,32,0,C.byref(bypass),1)
        x.XStoreName(d,window,b'U-AVF full-frame captured desktop stress (25 seconds)')
        x.XMapRaised(d,window); x.XSync(d,0)
        if ready_event: ready_event.set()
        update_durations=[]
        started=time.monotonic(); iterations=0
        while time.monotonic()-started<duration and not (stop_event and stop_event.is_set()):
            before=time.monotonic_ns()
            dx=1+(iterations*7)%(w-1); dy=1+(iterations*11)%(h-1)
            for sx,sy,cw,ch,tx,ty in ((dx,dy,w-dx,h-dy,0,0),(0,dy,dx,h-dy,w-dx,0),
                                      (dx,0,w-dx,dy,0,h-dy),(0,0,dx,dy,w-dx,h-dy)):
                x.XCopyArea(d,pixmap,window,gc,sx,sy,cw,ch,tx,ty)
            x.XSync(d,0); iterations+=1
            update_durations.append((time.monotonic_ns()-before)/1e6)
            time.sleep(max(0,started+iterations/request_hz-time.monotonic()))
        gr=ul(); gx=integer(); gy=integer(); gw=uint(); gh=uint(); border=uint(); depth=uint(); child=ul()
        x.XGetGeometry(d,window,C.byref(gr),C.byref(gx),C.byref(gy),C.byref(gw),C.byref(gh),C.byref(border),C.byref(depth))
        x.XTranslateCoordinates(d,window,root,0,0,C.byref(gx),C.byref(gy),C.byref(child))
        geometry=[gx.value,gy.value,gw.value,gh.value]
        return {'kind':'fullscreen captured-desktop texture pan; diagnostic, not interactive GNOME acceptance',
                'actual_window_geometry':geometry,'fullscreen_geometry_verified':geometry==[0,0,w,h],
                'compositor_bypass_hint':2,
                'texture_statistics':texture_stats,
                'producer_blit_sync_mean_ms':sum(update_durations)/max(1,len(update_durations)),
                'producer_blit_sync_p95_ms':sorted(update_durations)[int(.95*(len(update_durations)-1))] if update_durations else None,
                'dimensions':[w,h], 'updated_pixel_area_per_iteration':w*h,
                'completed_updates':iterations,'seconds':time.monotonic()-started,'request_hz':request_hz}
    finally:
        if window: x.XDestroyWindow(d,window)
        if gc: x.XFreeGC(d,gc)
        if pixmap: x.XFreePixmap(d,pixmap)
        if image: x.XDestroyImage(image)
        x.XSync(d,0); x.XCloseDisplay(d)


def main():
    if os.geteuid() != 0:
        raise RuntimeError('Run this bounded diagnostic with sudo')
    url = sys.argv[1]
    env = session()
    report = {'start_epoch': time.time(), 'display': env['DISPLAY'], 'phases': []}
    wrapper = Path('/usr/local/sbin/uavf-gdm-display-share.sh')
    original = wrapper.read_text()
    needle = "'-AlwaysShared'], selected)"
    if original.count(needle) != 1:
        raise RuntimeError('Exact known scraper wrapper not found; refusing changes')
    test_wrapper = Path('/run/uavf-fps-scraper-ab.sh')
    dropin = Path('/run/systemd/system/winavf-xvnc.service.d/90-uavf-fps-ab.conf')
    if test_wrapper.exists() or dropin.exists():
        raise RuntimeError('Previous diagnostic files exist; refusing overwrite')
    report['help'] = S.run(['/usr/local/sbin/winavf-X0tigervnc', '-help'], capture_output=True, text=True).stderr
    report['original_mode'] = command('xrandr', env=env)
    output = next(line.split()[0] for line in report['original_mode'].splitlines() if ' connected' in line)
    current = next(line.split()[0] for line in report['original_mode'].splitlines() if '*' in line and line.startswith('   '))
    try:
        command('xrandr', '--output', output, '--mode', '1920x1200', env=env)
        time.sleep(5)
        for label in ('baseline', 'poll8_cpu100'):
            if label != 'baseline':
                test_wrapper.write_text(original.replace(needle, "'-AlwaysShared', '-PollingCycle', '8', '-MaxProcessorUsage', '100', '-FrameRate', '120'], selected)"))
                test_wrapper.chmod(0o700)
                dropin.parent.mkdir(parents=True, exist_ok=True)
                dropin.write_text('[Service]\nExecStart=\nExecStart=/bin/sh /run/uavf-fps-scraper-ab.sh\n')
                command('systemctl', 'daemon-reload')
                command('systemctl', 'restart', 'winavf-xvnc.service')
                time.sleep(8)
            phase = {'label': label, 'start_epoch': time.time(), 'mode': command('xrandr', env=env)}
            phase['motion'] = motion(env)
            phase['end_epoch'] = time.time()
            phase['processes'] = command('ps', '-eo', 'pid,pcpu,args')
            phase['journal'] = command('journalctl', '--since', '@' + str(int(phase['start_epoch'])), '--no-pager', '-o', 'short-unix')
            report['phases'].append(phase)
            print('FPS_AB_PHASE=' + label, flush=True)
    finally:
        if dropin.exists():
            dropin.unlink()
            command('systemctl', 'daemon-reload')
            command('systemctl', 'restart', 'winavf-xvnc.service')
        if test_wrapper.exists():
            test_wrapper.unlink()
        command('xrandr', '--output', output, '--mode', current, env=env)
        report['rollback'] = 'Original service and resolution restored'
        report['end_epoch'] = time.time()
        data = json.dumps(report).encode()
        Path('/tmp/uavf-fps-ab-report.json').write_bytes(data)
        req = urllib.request.Request(url + '/report', data=data, method='POST', headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=15) as response:
            print('FPS_AB_REPORT=' + str(response.status), flush=True)


if __name__ == '__main__':
    main()
