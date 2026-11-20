"""30-second UVF1/4055 H.264 -> Android Surface proof; fallback untouched.

Latest-wins applies only BEFORE encode. Never discard an encoded P-frame.
Pipe/backpressure is bounded. One-frame AUD assembly delay is measured in
capture-to-send latency; no claim of end-to-end latency without clock sync.
"""
import collections, importlib.util, json, os, re, resource, runpy, socket, struct, subprocess as S, sys, threading, time, traceback, urllib.request
from pathlib import Path
import gi
import base64
import zlib
gi.require_version('Gst','1.0')
from gi.repository import Gio, GLib, Gst

dest='org.gnome.Mutter.ScreenCast'
Gst.init(None)
bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)
loop=GLib.MainLoop()
condition=threading.Condition()
latest=None
stopping=False
pipeline=session=encoder=client=server=None
writer=reader=None
original=output=None
diagnostic_mode_created=diagnostic_mode_added=False
diagnostic_mode='uavf-1920x1200-90-diagnostic'
monitor90='--monitor90' in sys.argv
ximage='--ximage' in sys.argv
glx_i420='--glx-i420' in sys.argv
gpu_capture_thread=None
gpu_capture_times={}
verify_changes='--verify-changes' in sys.argv
full_frame='--full-frame' in sys.argv
last_signature=None
paused_pid=None
raw_socket_stopped=False
started=0
times=collections.deque()
source_times={}
events=[]
clock_offset=0
cadence='--cadence' in sys.argv
motion_hz=int(next((a.split('=',1)[1] for a in sys.argv if a.startswith('--motion-hz=')), '60'))
if not 30 <= motion_hz <= 120: raise ValueError('motion-hz must be 30..120')
source_fps=int(next((a.split('=',1)[1] for a in sys.argv if a.startswith('--source-fps=')), '90'))
if not 30 <= source_fps <= 120: raise ValueError('source-fps must be 30..120')
worker_threads=int(next((a.split('=',1)[1] for a in sys.argv if a.startswith('--worker-threads=')), '2'))
if not 1 <= worker_threads <= 4: raise ValueError('worker-threads must be 1..4')
cadence_frames=[]
reference='--reference' in sys.argv
reference_aus=[]
reference_sources={}
reference_index=300
matched_stop='--matched-stop' in sys.argv
motion_stop=threading.Event()
mover=None
profiler=None
cpu_previous={}

def cpu_snapshot():
    result={}
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit(): continue
        try:
            name=(proc/'comm').read_text().strip()
            if name not in ('gnome-shell','Xorg','pipewire','python3'): continue
            fields=(proc/'stat').read_text().rsplit(')',1)[1].split()
            result[proc.name]=(name,int(fields[11])+int(fields[12]))
        except (OSError,ValueError): pass
    return result
native_gst='--gstreamer' in sys.argv
stats={'source':0,'coalesced_before_encode':0,'submitted':0,'sent':0,'bytes':0,'encode_to_au_seconds':0.,'capture_to_send_seconds':0.,'write_seconds':0.}
report={'kind':'Mutter-BGRx-FFmpeg-x264-UVF1-MediaCodec-Surface','dimensions':[1920,1200], 'queue_depth':1, 'encoded_drops':0,'intervals':[]}
directory=Path('/var/tmp/uavf-software-encode-20261001')
directory.mkdir(exist_ok=True)
def call(path,interface,method,args=None):
    return bus.call_sync(dest,path,interface,method,args,None,Gio.DBusCallFlags.NONE,5000,None)
def sample(sink):
    global latest
    item=sink.emit('pull-sample')
    with condition:
        stats['source']+=1
        if latest is not None: stats['coalesced_before_encode']+=1
        latest=(item,time.monotonic_ns())
        condition.notify()
    return Gst.FlowReturn.OK
def added(conn,sender,path,interface,signal,params,*unused):
    global pipeline
    if native_gst:
        source_description=('appsrc name=pw is-live=true format=time do-timestamp=true block=false max-buffers=1 leaky-type=downstream '
                            '! video/x-raw,format=I420,width=1920,height=1200,framerate=0/1,colorimetry=bt601 '
                            if glx_i420 else
                            'ximagesrc name=pw use-damage=false show-pointer=true endx=1919 endy=1199 '
                            f'! video/x-raw,framerate={source_fps}/1,format=BGRx '
                            if ximage else
                            'pipewiresrc name=pw path=%d do-timestamp=true ! video/x-raw,format=BGRx ' % params.unpack()[0])
        pipeline=Gst.parse_launch(
            source_description + '! identity name=source '
            '! queue max-size-buffers=1 max-size-bytes=0 max-size-time=0 leaky=downstream '+
            ('' if glx_i420 else f'! videoconvert n-threads={worker_threads} ! video/x-raw,format=I420 ')+
            f'! x264enc name=encoder tune=zerolatency speed-preset=ultrafast threads={worker_threads} '
            # In pass=qual bitrate is still the VBV maximum, not an unused CBR
            # setting. Default 2048 kbit/s destroys desktop P-frame quality.
            'sliced-threads=true byte-stream=true key-int-max=60 pass=qual quantizer=25 bitrate=32000 '
            'option-string="rc-lookahead=0:sync-lookahead=0:bframes=0" '
            '! video/x-h264,stream-format=byte-stream,alignment=au '
            '! appsink name=c emit-signals=true max-buffers=1 drop=false sync=false enable-last-sample=false')
        def source_probe(pad,info):
            global last_signature
            b=info.get_buffer()
            if b:
                stamp=time.monotonic_ns()
                stats['source']+=1; source_times[b.pts]=gpu_capture_times.pop(b.pts,stamp)
                events.append(('source',stamp,0))
                if verify_changes:
                    verification_started=time.monotonic_ns()
                    signature=0
                    # Conservative content-change evidence, not frame-rate
                    # synthesis: sampled full-width rows across the desktop.
                    for row in range(0,1200,50):
                        stride=1920 if glx_i420 else 1920*4
                        signature=zlib.crc32(b.extract_dup(row*stride,stride),signature)
                    if signature != last_signature:
                        events.append(('source_changed_sample',stamp,signature))
                    last_signature=signature
                    events.append(('verification_cost_ms',stamp,(time.monotonic_ns()-verification_started)/1e6))
                if cadence: cadence_frames.append([stamp,int(b.pts),int(b.duration),int(b.offset)])
                if 'source_caps' not in report:
                    report['source_caps']=pad.get_current_caps().to_string()
                    if reference:
                        (directory/'reference.bgr0').write_bytes(b.extract_dup(0,b.get_size()))
                if len(source_times)>120:
                    del source_times[min(source_times)]
                if reference and 295 <= stats['submitted'] <= reference_index:
                    reference_sources[b.pts]=b
                    while len(reference_sources)>8: del reference_sources[min(reference_sources)]
            return Gst.PadProbeReturn.OK
        def encoder_probe(pad,info):
            b=info.get_buffer()
            if b:
                if reference and stats['submitted']==reference_index:
                    source_buffer=reference_sources.get(b.pts)
                    if source_buffer:
                        (directory/'motion-source.bgr0').write_bytes(source_buffer.extract_dup(0,source_buffer.get_size()))
                    (directory/'motion-input.i420').write_bytes(b.extract_dup(0,b.get_size()))
                    report['reference_matched_pts']=int(b.pts)
                    report['reference_source_bytes']=source_buffer.get_size() if source_buffer else None
                    report['reference_i420_bytes']=b.get_size()
                captured=source_times.pop(b.pts,time.monotonic_ns())
                old=[key for key in source_times if key<b.pts]
                for key in old: del source_times[key]
                stats['coalesced_before_encode']+=len(old)
                times.append((captured,time.monotonic_ns())); stats['submitted']+=1
            return Gst.PadProbeReturn.OK
        pipeline.get_by_name('source').get_static_pad('src').add_probe(Gst.PadProbeType.BUFFER,source_probe)
        pipeline.get_by_name('encoder').get_static_pad('sink').add_probe(Gst.PadProbeType.BUFFER,encoder_probe)
        def encoded_sample(sink):
            item=sink.emit('pull-sample')
            b=item.get_buffer(); ok,mapped=b.map(Gst.MapFlags.READ)
            if ok:
                try: send_au(bytes(mapped.data))
                except Exception: fail(); return Gst.FlowReturn.ERROR
                finally: b.unmap(mapped)
            return Gst.FlowReturn.OK
        pipeline.get_by_name('c').connect('new-sample',encoded_sample)
        source=pipeline.get_by_name('pw')
        enc=pipeline.get_by_name('encoder')
        report['encoder_properties']={name:str(enc.get_property(name)) for name in
            ('pass','quantizer','bitrate','vbv-buf-capacity','speed-preset','tune','threads','sliced-threads')}
        report['pipewiresrc_properties']={p.name:str(source.get_property(p.name)) for p in source.list_properties() if p.name in ('always-copy','min-buffers','max-buffers','do-timestamp','keepalive-time','resend-last','stream-properties')}
        if not ximage and not glx_i420: pipeline.set_state(Gst.State.PLAYING)
        return
    pipeline=Gst.parse_launch('pipewiresrc path=%d do-timestamp=true ! video/x-raw,format=BGRx ! appsink name=c emit-signals=true max-buffers=1 drop=true sync=false enable-last-sample=false'%params.unpack()[0])
    pipeline.get_by_name('c').connect('new-sample',sample)
    pipeline.set_state(Gst.State.PLAYING)
def fail():
    report['error']=traceback.format_exc()
    GLib.idle_add(loop.quit)
def input_worker():
    global latest
    try:
        while not stopping:
            with condition:
                condition.wait_for(lambda: latest is not None or stopping,timeout=.2)
                if stopping: break
                if latest is None: continue
                item,captured=latest; latest=None
            b=item.get_buffer(); ok,mapped=b.map(Gst.MapFlags.READ)
            if not ok: raise RuntimeError('Cannot map capture')
            try:
                if len(mapped.data)!=1920*1200*4: raise RuntimeError('Unexpected frame stride')
                if len(times)>=8: raise RuntimeError('Encoder pipeline backlog >=8 frames')
                times.append((captured,time.monotonic_ns()))
                remaining=memoryview(mapped.data)
                while remaining and not stopping:
                    n=os.write(encoder.stdin.fileno(),remaining)
                    if n<=0: raise BrokenPipeError()
                    remaining=remaining[n:]
                stats['submitted']+=1
            finally: b.unmap(mapped)
    except Exception:
        if not stopping: fail()
    finally:
        try: encoder.stdin.close()
        except Exception: pass
def send_au(payload):
    # Freeze exactly this AU, including while the GLib quit callback is pending.
    if matched_stop and stats['sent']>reference_index: return
    if len(payload)>4*1024*1024: raise RuntimeError('AU too large')
    if not times: raise RuntimeError('AU without captured frame timestamp')
    captured,submitted=times.popleft()
    seq=stats['sent']
    if reference and seq<=reference_index: reference_aus.append(payload)
    header=struct.pack('<4sHHIIIHHQII',b'UVF1',1,2,1,1,seq,1920,1200,captured//1000,len(payload),0)
    before=time.monotonic_ns()
    events.append(('encoded',before,0))
    stats['encode_to_au_seconds']+=(before-submitted)/1e9
    client.sendall(header+payload)
    now=time.monotonic_ns()
    stats['write_seconds']+=(now-before)/1e9
    stats['capture_to_send_seconds']+=(now-captured)/1e9
    stats['sent']+=1; stats['bytes']+=len(payload)
    events.append(('sent',now,(now-captured)/1e6))
    if matched_stop and seq==reference_index:
        report['final_surface_sequence']=seq
        GLib.idle_add(lambda:(loop.quit(),False)[1])
def output_worker():
    try:
        pending=bytearray()
        aud=re.compile(b'\x00\x00(?:\x00)?\x01\x09')
        while not stopping:
            block=os.read(encoder.stdout.fileno(),65536)
            if not block: break
            pending.extend(block)
            while True:
                markers=list(aud.finditer(pending))
                if len(markers)<2: break
                boundary=markers[1].start()
                payload=bytes(pending[:boundary]); del pending[:boundary]
                send_au(payload)
            if len(pending)>4*1024*1024: raise RuntimeError('Missing AU boundary')
        if pending and not stopping: send_au(bytes(pending))
    except Exception:
        if not stopping: fail()
previous=dict(stats); previous_at=0
previous_cpu=0
def metrics():
    global previous,previous_at,previous_cpu,cpu_previous
    now=time.monotonic(); elapsed=now-previous_at
    delta={k:stats[k]-previous[k] for k in stats}
    sent=max(1,delta['sent'])
    interval={'elapsed':now-started,'source_fps':delta['source']/elapsed,
              'submitted_fps':delta['submitted']/elapsed,'sent_fps':delta['sent']/elapsed,
              'Mbps':delta['bytes']*8/elapsed/1e6,'input_coalesced':delta['coalesced_before_encode'],
              'encoder_pending':len(times),'encode_to_au_ms':delta['encode_to_au_seconds']*1000/sent,
              'capture_to_send_ms':delta['capture_to_send_seconds']*1000/sent,
              'send_ms':delta['write_seconds']*1000/sent}
    usage=resource.getrusage(resource.RUSAGE_SELF)
    cpu=usage.ru_utime+usage.ru_stime
    interval['capture_and_encode_cpu_cores']=(cpu-previous_cpu)/elapsed
    previous_cpu=cpu
    if cadence:
        current=cpu_snapshot()
        interval['process_cpu_cores']={pid+':'+v[0]:(v[1]-cpu_previous[pid][1])/os.sysconf('SC_CLK_TCK')/elapsed for pid,v in current.items() if pid in cpu_previous}
        cpu_previous=current
    if pipeline:
        error=pipeline.get_bus().pop_filtered(Gst.MessageType.ERROR)
        if error:
            report['pipeline_error']=str(error.parse_error()); loop.quit(); return False
    report['intervals'].append(interval)
    # Do not render the measurement log into the desktop being captured.
    with (directory/'stream-metrics.jsonl').open('a') as f: f.write(json.dumps(interval)+'\n')
    previous=dict(stats); previous_at=now
    return not stopping
try:
    # Resolve all local test dependencies before accepting Android or stopping
    # the stable display. /tmp may have been cleared by a guest restart.
    motion_path=Path(__file__).with_name('fps-scraper-ab.py')
    if not motion_path.is_file():
        raise RuntimeError('Missing test helper: '+str(motion_path)+'; fetch fps-scraper-ab.py alongside this script')
    spec=importlib.util.spec_from_file_location('motion',str(motion_path))
    motion=importlib.util.module_from_spec(spec); spec.loader.exec_module(motion)
    if '--profile-mutter' in sys.argv:
        profile_path=Path(__file__).with_name('encoded-mutter-profile.py')
        if not profile_path.is_file(): raise RuntimeError('Missing encoded-mutter-profile.py helper')
        profile_spec=importlib.util.spec_from_file_location('capture_profile',str(profile_path))
        profile_module=importlib.util.module_from_spec(profile_spec); profile_spec.loader.exec_module(profile_module)
        profiler=profile_module.Profile()
    if ximage and (not native_gst or Gst.ElementFactory.find('ximagesrc') is None):
        raise RuntimeError('ximagesrc native plugin required for this isolated source A/B')
    if glx_i420 and (not native_gst or ximage or not Path(__file__).with_name('encoded-glx-pbo-probe.py').is_file()):
        raise RuntimeError('Native GStreamer and GLX helper required; do not combine GLX with ximage')
    auth=S.run(['sudo','-n','-v'],capture_output=True,text=True)
    if auth.returncode:
        raise RuntimeError('Run sudo -v in this terminal before starting the diagnostic: '+auth.stderr.strip())
    server=socket.socket(socket.AF_VSOCK,socket.SOCK_STREAM)
    server.bind((socket.VMADDR_CID_ANY,4055)); server.listen(1); server.settimeout(90)
    print('UVF1_LISTENING_4055',flush=True)
    # A cancelled Android connect may complete after the next listener opens.
    # Reject only that pre-stream connection; no service or frame state changed.
    accept_deadline=time.monotonic()+90
    report['rejected_pre_stream_connections']=[]
    while True:
        remaining=accept_deadline-time.monotonic()
        if remaining<=0: raise TimeoutError('No valid Android clock handshake within 90 seconds')
        server.settimeout(remaining)
        client,_=server.accept(); client.settimeout(2)
        try:
            g1=time.monotonic_ns()
            client.sendall(struct.pack('<4sHHIIIHHQII',b'UVF1',1,4,1,1,0,1920,1200,0,0,0))
            reply=b''
            while len(reply)<8:
                block=client.recv(8-len(reply))
                if not block: raise EOFError('Clock reply disconnected')
                reply+=block
            g4=time.monotonic_ns()
            break
        except (EOFError,ConnectionResetError,BrokenPipeError,socket.timeout) as exc:
            report['rejected_pre_stream_connections'].append(str(exc))
            client.close(); client=None
            print('UVF1_WAITING_FOR_VALID_CLIENT',flush=True)
    clock_offset=struct.unpack('<Q',reply)[0]-(g1+g4)//2
    report['clock_uncertainty_ms']=(g4-g1)/2e6
    # EPIPE retries in the production bridge can reconnect RFB after the
    # Android client closes. Stop its socket and exact instances for this test.
    S.run(['sudo','-n','systemctl','stop','winavf-frame.socket'],check=True)
    raw_socket_stopped=True
    units=S.check_output(['systemctl','list-units','--type=service','--state=active','--plain','--no-legend'],text=True)
    instances=[line.split()[0] for line in units.splitlines() if line.split() and line.split()[0].startswith('winavf-frame@')]
    if instances: S.run(['sudo','-n','systemctl','stop',*instances],check=True)
    connections=S.check_output(['ss','-Htn','state','established'],text=True)
    report['raw_rfb_connections_after_stop']=[line for line in connections.splitlines() if ':5901' in line]
    if report['raw_rfb_connections_after_stop']: raise RuntimeError('RFB client still connected')
    report['raw_instances_stopped']=instances
    modes=S.check_output(['xrandr','--current'],text=True)
    output=next(l.split()[0] for l in modes.splitlines() if ' connected' in l)
    original=next(l.split()[0] for l in modes.splitlines() if '*' in l and l.startswith(' '))
    if monitor90:
        if full_frame:
            # Snapshot the known normal desktop before the temporary mode switch.
            # Mode-change black root pixmaps must not become a fake FPS fixture.
            S.run(['xrandr','--output',output,'--mode','1920x1200'],check=True)
            time.sleep(1)
            report['fullframe_fixture']=motion.full_frame_motion(dict(os.environ),prepare_only=True)
        # Valid reduced-blanking timing for a temporary virtual output only.
        # 231.192 MHz / (2080 * 1235) = 90 Hz. Never changes firmware/GPT.
        S.run(['xrandr','--newmode',diagnostic_mode,'231.192','1920','1968','2000','2080',
               '1200','1203','1209','1235','+hsync','-vsync'],check=True)
        diagnostic_mode_created=True
        S.run(['xrandr','--addmode',output,diagnostic_mode],check=True)
        diagnostic_mode_added=True
    S.run(['xrandr','--output',output,'--mode',diagnostic_mode if monitor90 else '1920x1200',
           '--fb','1920x1200'],check=True)
    # A custom CRTC mode can otherwise leave the virtual root at the old size.
    # Do not measure a cropped1280x800 root as if it were a1200p capture.
    geometry_deadline=time.monotonic()+3
    while True:
        root_modes=S.check_output(['xrandr','--current'],text=True)
        if 'current 1920 x 1200' in root_modes.splitlines()[0]: break
        if time.monotonic()>=geometry_deadline: raise RuntimeError('Root framebuffer did not become1920x1200: '+root_modes.splitlines()[0])
        time.sleep(.1)
    report['xrandr']=S.check_output(['xrandr','--current'],text=True)
    report['gnome_version']=S.check_output(['gnome-shell','--version'],text=True).strip()
    if '--pause-rfb' in sys.argv:
        pid=int(S.check_output(['systemctl','show','winavf-xvnc.service','-p','MainPID','--value'],text=True).strip())
        cmd=Path('/proc/%d/cmdline'%pid).read_bytes()
        if cmd.split(b'\0')[0]!=b'/usr/local/sbin/winavf-X0tigervnc':
            raise RuntimeError('Refusing to pause anything other than the proven scraper')
        S.run(['sudo','-n','kill','-STOP',str(pid)],check=True)
        paused_pid=pid; report['paused_scraper_pid']=pid
        # Independent guard also resumes this exact process if the test crashes.
        watchdog="import time,pathlib,subprocess; time.sleep(45); p=pathlib.Path('/proc/%d/cmdline'); subprocess.run(['sudo','-n','kill','-CONT','%d']) if p.exists() and p.read_bytes()==%r else None"%(pid,pid,cmd)
        S.Popen([sys.executable,'-c',watchdog],stdin=S.DEVNULL,stdout=S.DEVNULL,stderr=S.DEVNULL,start_new_session=True)
    args=['ffmpeg','-hide_banner','-nostdin','-loglevel','warning','-filter_threads','1',
          '-f','rawvideo','-pixel_format','bgr0','-video_size','1920x1200','-framerate','60','-i','pipe:0',
          '-an','-c:v','libx264','-preset','ultrafast','-tune','zerolatency','-threads','2',
          '-pix_fmt','yuv420p','-crf','25','-x264-params','aud=1:sliced-threads=1:rc-lookahead=0:sync-lookahead=0:bframes=0:keyint=60:repeat-headers=1',
          '-flush_packets','1','-f','h264','pipe:1']
    report['encoder_command']=args if not native_gst else 'Native GStreamer x264enc; no RGB Python map/pipe'
    if not native_gst:
        err=(directory/'stream-ffmpeg.stderr').open('wb')
        encoder=S.Popen(args,stdin=S.PIPE,stdout=S.PIPE,stderr=err,bufsize=0)
    if ximage or glx_i420:
        report['capture_method']='GLX-owned-root-GPU-I420-PBO' if glx_i420 else 'Xorg-root-XShm-ximagesrc'
        added(None,None,None,None,None,None,None)
    else:
        session=call('/org/gnome/Mutter/ScreenCast',dest,'CreateSession',GLib.Variant('(a{sv})',({},))).unpack()[0]
        if monitor90:
            stream=call(session,dest+'.Session','RecordMonitor',GLib.Variant('(sa{sv})',(output,{'cursor-mode':GLib.Variant('u',1)}))).unpack()[0]
        else:
            stream=call(session,dest+'.Session','RecordArea',GLib.Variant('(iiiia{sv})',(0,0,1920,1200,{'cursor-mode':GLib.Variant('u',1)}))).unpack()[0]
        report['capture_method']='RecordMonitor' if monitor90 else 'RecordArea'
        bus.signal_subscribe(dest,dest+'.Stream','PipeWireStreamAdded',stream,None,Gio.DBusSignalFlags.NONE,added,None)
    if not native_gst:
        writer=threading.Thread(target=input_worker); reader=threading.Thread(target=output_worker)
        writer.start(); reader.start()
    full_ready=threading.Event()
    def move():
        if not full_frame: time.sleep(3)
        try:
            report['motion']=(motion.full_frame_motion(dict(os.environ),35,stop_event=motion_stop,request_hz=motion_hz,ready_event=full_ready,reuse_texture=monitor90)
                             if full_frame else motion.motion(dict(os.environ),25,trace=cadence,fit=True,stop_event=motion_stop,request_hz=motion_hz))
        except Exception: report['motion_error']=traceback.format_exc()
        finally: full_ready.set()
    mover=threading.Thread(target=move,daemon=True)
    if full_frame:
        mover.start()
        if not full_ready.wait(12): raise RuntimeError('Fullscreen workload not prepared within 12s')
        if 'motion_error' in report: raise RuntimeError(report['motion_error'])
        report['fullscreen_prepared_before_measurement']=True
    if profiler: profiler.start()
    if session: call(session,dest+'.Session','Start')
    started=previous_at=time.monotonic()
    window_start=int(started*1e9)+5_000_000_000
    window_end=window_start+20_000_000_000
    payload=struct.pack('<QQ',window_start+clock_offset,window_end+clock_offset)
    client.sendall(struct.pack('<4sHHIIIHHQII',b'UVF1',1,5,1,1,0,1920,1200,0,len(payload),0)+payload)
    if ximage or glx_i420: pipeline.set_state(Gst.State.PLAYING)
    if glx_i420:
        appsrc=pipeline.get_by_name('pw')
        def consume_gpu_frame(data,capture_started):
            if motion_stop.is_set(): return
            buffer=Gst.Buffer.new_wrapped(data)
            buffer.pts=time.monotonic_ns()-int(started*1e9)
            gpu_capture_times[buffer.pts]=capture_started
            if len(gpu_capture_times)>120: del gpu_capture_times[min(gpu_capture_times)]
            result=appsrc.emit('push-buffer',buffer)
            if result not in (Gst.FlowReturn.OK,Gst.FlowReturn.FLUSHING): raise RuntimeError('GPU appsrc rejected frame '+str(result))
        def gpu_capture():
            try:
                values=runpy.run_path(str(Path(__file__).with_name('encoded-glx-pbo-probe.py')),
                    init_globals={'frame_consumer':consume_gpu_frame,'stream_stop_event':motion_stop,
                                  'damage_sync':'--damage-sync' in sys.argv,
                                  'async_readback':'--async-readback' in sys.argv})
                report['gpu_capture']=values['report']
                if values['report'].get('error'): raise RuntimeError(values['report']['error'])
            except Exception: fail()
        gpu_capture_thread=threading.Thread(target=gpu_capture,daemon=True)
        gpu_capture_thread.start()
    usage=resource.getrusage(resource.RUSAGE_SELF); previous_cpu=usage.ru_utime+usage.ru_stime
    cpu_previous=cpu_snapshot()
    if not full_frame: mover.start()
    GLib.timeout_add_seconds(1,metrics)
    GLib.timeout_add_seconds(30,lambda:(loop.quit(),False)[1])
    loop.run()
except Exception: report['error']=traceback.format_exc()
finally:
    stopping=True
    motion_stop.set()
    if gpu_capture_thread: gpu_capture_thread.join(timeout=3)
    if mover: mover.join(timeout=3)
    with condition: condition.notify_all()
    if pipeline: pipeline.set_state(Gst.State.NULL)
    if session:
        try: call(session,dest+'.Session','Stop')
        except Exception: pass
    if profiler and profiler.active:
        try: report['mutter_profile']=profiler.finish(window_start if 'window_start' in globals() else 0,
                                                     window_end if 'window_end' in globals() else time.monotonic_ns())
        except Exception: report['mutter_profile_error']=traceback.format_exc()
    if encoder:
        encoder.terminate()
        try: encoder.wait(timeout=2)
        except S.TimeoutExpired: encoder.kill(); encoder.wait()
        for t in (writer,reader):
            if t: t.join(timeout=3)
        err.close()
        report['encoder_error']=(directory/'stream-ffmpeg.stderr').read_text()[-3000:]
    if client:
        try: client.sendall(struct.pack('<4sHHIIIHHQII',b'UVF1',1,3,1,1,stats['sent'],1920,1200,0,0,0))
        except Exception: pass
        client.close()
    if server: server.close()
    if paused_pid: S.run(['sudo','-n','kill','-CONT',str(paused_pid)])
    if raw_socket_stopped: S.run(['sudo','-n','systemctl','start','winavf-frame.socket'],check=True)
    if original: S.run(['xrandr','--output',output,'--mode',original])
    if diagnostic_mode_added: S.run(['xrandr','--delmode',output,diagnostic_mode])
    if diagnostic_mode_created: S.run(['xrandr','--rmmode',diagnostic_mode])
    report['totals']=stats; report['rollback']='Production capture untouched; original mode restored'
    if reference and reference_aus:
        (directory/'reference.h264').write_bytes(b''.join(reference_aus))
        for name,args in (
            ('source',['-f','rawvideo','-pixel_format','bgr0','-video_size','1920x1200','-i',str(directory/'reference.bgr0')]),
            ('decoded',['-i',str(directory/'reference.h264')])):
            dest_image=directory/(name+'.png')
            S.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*args,'-frames:v','1',str(dest_image)],check=True)
            report[name+'_png_base64']=base64.b64encode(dest_image.read_bytes()).decode()
        report['h264_reference_bytes']=sum(map(len,reference_aus))
        motion_image=directory/'decoded-motion.png'
        S.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(directory/'reference.h264'),
            '-vf','select=eq(n\\,%d)'%reference_index,'-frames:v','1',str(motion_image)],check=True)
        if motion_image.exists(): report['decoded_motion_png_base64']=base64.b64encode(motion_image.read_bytes()).decode()
        for name,pixel in (('motion-source','bgr0'),('motion-input','yuv420p')):
            source_file=directory/(name+('.bgr0' if pixel=='bgr0' else '.i420'))
            if not source_file.exists(): continue
            image_file=directory/(name+'.png')
            S.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','rawvideo','-pixel_format',pixel,
                '-video_size','1920x1200','-i',str(source_file),'-frames:v','1',str(image_file)],check=True)
            report[name.replace('-','_')+'_png_base64']=base64.b64encode(image_file.read_bytes()).decode()
    if cadence:
        report['cadence_frames']=cadence_frames
        report['stage_events']=events
    if 'window_start' in globals():
        selected=[e for e in events if window_start<=e[1]<window_end]
        latencies=[e[2] for e in selected if e[0]=='sent']
        report['fixed_window']={'seconds':20,'android_start_ns':window_start+clock_offset,
            'android_end_ns':window_end+clock_offset,
            **{name+'_fps':sum(e[0]==name for e in selected)/20 for name in ('source','encoded','sent','source_changed_sample')},
            'capture_to_send_mean_ms':sum(latencies)/max(1,len(latencies)),
            'capture_to_send_p95_ms':sorted(latencies)[int(.95*(len(latencies)-1))] if latencies else None}
        verification_costs=[e[2] for e in selected if e[0]=='verification_cost_ms']
        report['fixed_window']['verification_mean_ms']=sum(verification_costs)/max(1,len(verification_costs))
    data=json.dumps(report,indent=2).encode()
    (directory/'stream-report.json').write_bytes(data)
    urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=data,method='POST'),timeout=15).read()
    print('STREAM_REPORT_SAVED',flush=True)
