#!/usr/bin/python3
"""Production installed/Live/installer UVF1 AVC service; same UIN1 input.

No benchmark window, HTTP dependency, watchdog or user-data modification.
Bounded raw queue; encoded reference frames are never dropped.
"""
import collections
import ctypes
import importlib.util
import os
from pathlib import Path
import pwd
import runpy
import select
import signal
import socket
import struct
import subprocess
import sys
import threading
import time

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('frame_input',str(HERE/'winavf-frame-bridge.py'))
bridge=importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)

if '--session' not in sys.argv:
    # Discover the real GDM session while privileged; never create a second
    # system-service GNOME session (which breaks logind/Snap).
    env=None
    for _ in range(180):
        env=bridge.find_display()
        if env:
            candidate_uid=int(env['XDG_RUNTIME_DIR'].rsplit('/',1)[1])
            candidate_account=pwd.getpwuid(candidate_uid)
            if (1000 <= candidate_uid < 60000 or
                    (candidate_uid > 0 and candidate_account.pw_name == 'gdm')):
                break
        env=None
        time.sleep(1)
    if not env: raise RuntimeError('No GDM greeter or authenticated X11 session')
    uid=int(env['XDG_RUNTIME_DIR'].rsplit('/',1)[1])
    account=pwd.getpwuid(uid)
    if uid==0: raise RuntimeError('Refusing root desktop capture')
    units=subprocess.check_output(['systemctl','list-units','--type=service','--state=active','--plain','--no-legend'],text=True)
    raw=[line.split()[0] for line in units.splitlines() if line.split() and line.split()[0].startswith('winavf-frame@')]
    if raw: subprocess.run(['systemctl','stop',*raw],check=True)
    if os.geteuid()==0:
        os.initgroups(account.pw_name,account.pw_gid)
        os.setgid(account.pw_gid)
        os.setuid(uid)
    env['HOME']=account.pw_dir
    os.execve('/usr/bin/python3',['python3',str(Path(__file__).resolve()),'--session'],env)

import gi
gi.require_version('Gst','1.0')
from gi.repository import Gst, GLib
Gst.init(None)
stop=threading.Event()
for sig in (signal.SIGTERM,signal.SIGINT): signal.signal(sig,lambda *_:stop.set())
stream=socket.socket(fileno=os.dup(0))
stream.settimeout(2)
def header(kind,sequence,length=0,stamp=0):
    return struct.pack('<4sHHIIIHHQII',b'UVF1',1,kind,1,1,sequence,1920,1200,stamp,length,0)
stream.sendall(header(4,0))
reply=bytearray()
while len(reply)<8:
    part=stream.recv(8-len(reply))
    if not part: raise EOFError('No clock handshake')
    reply.extend(part)
# The handshake precedes input publication on Android: no UIN1 bytes can be
# mistaken for the clock response. Input keeps the proven blocking stdin reader;
# outgoing video uses MSG_DONTWAIT with a total 2-second deadline independently.
stream.settimeout(None)
def send_packet(packet):
    remaining=memoryview(packet)
    deadline=time.monotonic()+2
    while remaining:
        if stop.is_set() or time.monotonic()>deadline: raise TimeoutError('Encoded output backpressure')
        try:
            sent=stream.send(remaining,socket.MSG_DONTWAIT)
            if not sent: raise EOFError('Encoded socket closed')
            remaining=remaining[sent:]
        except BlockingIOError:
            select.select([],[stream],[],min(.1,max(0,deadline-time.monotonic())))
def read_input():
    try: bridge.input_loop()
    finally: stop.set()
if not ctypes.CDLL('libX11.so.6').XInitThreads():
    raise RuntimeError('Xlib thread initialization failed')
threading.Thread(target=read_input,daemon=True,name='uavf-input').start()

# Set only the authenticated session's display mode. No firmware/ISO change.
modes=subprocess.check_output(['xrandr','--current'],text=True)
output=next(line.split()[0] for line in modes.splitlines() if ' connected' in line)
subprocess.run(['xrandr','--output',output,'--mode','1920x1200'],check=True)
until=time.monotonic()+5
while 'current 1920 x 1200' not in subprocess.check_output(['xrandr','--current'],text=True).splitlines()[0]:
    if time.monotonic()>until: raise RuntimeError('1920x1200 mode not ready')
    time.sleep(.1)

pipeline=Gst.parse_launch(
    'appsrc name=source is-live=true format=time block=false max-buffers=1 leaky-type=downstream '
    '! video/x-raw,format=I420,width=1920,height=1200,framerate=0/1,colorimetry=bt601 '
    '! queue max-size-buffers=1 max-size-bytes=0 max-size-time=0 leaky=downstream '
    '! x264enc name=encoder tune=zerolatency speed-preset=ultrafast threads=4 '
    'sliced-threads=true byte-stream=true key-int-max=60 pass=qual quantizer=25 bitrate=32000 '
    'option-string="rc-lookahead=0:sync-lookahead=0:bframes=0" '
    '! video/x-h264,stream-format=byte-stream,alignment=au '
    '! appsink name=encoded emit-signals=true max-buffers=1 drop=false sync=false enable-last-sample=false')
origin=time.monotonic_ns()
pending=collections.deque(maxlen=120)
counts={'captured':0,'sent':0,'bytes':0}
times={}
def submitted(pad,info):
    b=info.get_buffer()
    if b:
        if len(pending)>=120:
            stop.set()
            return Gst.PadProbeReturn.DROP
        pending.append(times.pop(b.pts,time.monotonic_ns()))
    return Gst.PadProbeReturn.OK
pipeline.get_by_name('encoder').get_static_pad('sink').add_probe(Gst.PadProbeType.BUFFER,submitted)
def encoded(sink):
    sample=sink.emit('pull-sample')
    b=sample.get_buffer()
    ok,data=b.map(Gst.MapFlags.READ)
    if not ok: stop.set(); return Gst.FlowReturn.ERROR
    try:
        if stop.is_set(): return Gst.FlowReturn.FLUSHING
        if not pending: raise RuntimeError('AU without source stamp')
        stamp=pending.popleft()
        payload=bytes(data.data)
        if not payload or len(payload)>4*1024*1024: raise RuntimeError('Invalid AVC AU size')
        send_packet(header(2,counts['sent'],len(payload),stamp//1000)+payload)
        counts['sent']+=1; counts['bytes']+=len(payload)
        return Gst.FlowReturn.OK
    except Exception as error:
        print('ENCODED_STREAM_ERROR='+str(error),file=sys.stderr,flush=True)
        stop.set(); return Gst.FlowReturn.ERROR
    finally: b.unmap(data)
pipeline.get_by_name('encoded').connect('new-sample',encoded)
source=pipeline.get_by_name('source')
def captured(data,stamp):
    if stop.is_set(): return
    pts=stamp-origin
    times[pts]=stamp
    while len(times)>120: del times[min(times)]
    b=Gst.Buffer.new_allocate(None,len(data),None)
    b.fill(0,data); b.pts=pts; b.dts=pts
    counts['captured']+=1
    if source.emit('push-buffer',b)!=Gst.FlowReturn.OK: stop.set()
def capture():
    try:
        result=runpy.run_path(str(HERE/'uavf-encoded-capture.py'),init_globals={
            'frame_consumer':captured,'stream_stop_event':stop,
            'damage_sync':True,'async_readback':True,'persistent_stream':True})
        print('ENCODED_CAPTURE_END='+str(result.get('report')),file=sys.stderr,flush=True)
    finally: stop.set()

pipeline.set_state(Gst.State.PLAYING)
worker=threading.Thread(target=capture,name='uavf-gpu-capture')
worker.start()
print('ENCODED_RUNTIME_READY=GPU_I420_X264_UVF1_1920x1200_INPUT_UIN1',file=sys.stderr,flush=True)
try:
    previous=dict(counts); before=time.monotonic()
    while not stop.wait(5):
        now=time.monotonic(); dt=now-before
        print('ENCODED_PERF capture_fps=%.2f sent_fps=%.2f Mbps=%.2f pending=%d'%(
            (counts['captured']-previous['captured'])/dt,
            (counts['sent']-previous['sent'])/dt,
            (counts['bytes']-previous['bytes'])*8/dt/1e6,len(pending)),file=sys.stderr,flush=True)
        previous=dict(counts); before=now
        message=pipeline.get_bus().pop_filtered(Gst.MessageType.ERROR)
        if message: raise RuntimeError(str(message.parse_error()))
finally:
    stop.set()
    try: stream.shutdown(socket.SHUT_RDWR)
    except OSError: pass
    worker.join(3)
    pipeline.set_state(Gst.State.NULL)
    stream.close()
