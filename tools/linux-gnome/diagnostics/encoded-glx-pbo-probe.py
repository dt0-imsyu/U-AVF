"""Bounded GPU-import/readback feasibility proof, not an FPS acceptance test.

Copies visible root into an OWNED X pixmap (no redirects, no server grabs),
imports via GLX_EXT_texture_from_pixmap, checks asynchronous PBO readback.
No changes to GNOME, services, display mode or installed files.
"""
import ctypes as C
import collections
import json
import sys
import subprocess as S
import time
import traceback
import urllib.request
import zlib

P=C.c_void_p; U=C.c_uint; L=C.c_ulong; I=C.c_int
class Image(C.Structure):
    _fields_=[('width',I),('height',I),('xoffset',I),('format',I),('data',P),
              ('byte_order',I),('bitmap_unit',I),('bitmap_bit_order',I),('bitmap_pad',I),
              ('depth',I),('stride',I),('bpp',I),('red',L),('green',L),('blue',L)]
x=C.CDLL('libX11.so.6'); gl=C.CDLL('libGL.so.1')
def bind(lib,name,result,args):
    f=getattr(lib,name); f.restype=result; f.argtypes=args; return f
def ints(*values): return (I*len(values))(*values)
errors=[]
@C.CFUNCTYPE(I,P,P)
def error_handler(display,event): errors.append('X protocol error'); return 0
set_error_handler=bind(x,'XSetErrorHandler',P,[P])
previous_error_handler=set_error_handler(C.cast(error_handler,P))
open_display=bind(x,'XOpenDisplay',P,[C.c_char_p])
sync=bind(x,'XSync',I,[P,I]); free=bind(x,'XFree',I,[P])
screen_of=bind(x,'XDefaultScreen',I,[P]); root_of=bind(x,'XDefaultRootWindow',L,[P])
geometry=bind(x,'XGetGeometry',I,[P,L,C.POINTER(L),C.POINTER(I),C.POINTER(I),C.POINTER(U),C.POINTER(U),C.POINTER(U),C.POINTER(U)])
create_pixmap=bind(x,'XCreatePixmap',L,[P,L,U,U,U])
create_gc=bind(x,'XCreateGC',P,[P,L,L,P])
copy_area=bind(x,'XCopyArea',I,[P,L,L,P,I,I,U,U,I,I])
get_image=bind(x,'XGetImage',C.POINTER(Image),[P,L,I,I,U,U,L,I])
destroy_image=bind(x,'XDestroyImage',I,[C.POINTER(Image)])
choose=bind(gl,'glXChooseFBConfig',C.POINTER(P),[P,I,C.POINTER(I),C.POINTER(I)])
get_visual=bind(gl,'glXGetVisualFromFBConfig',P,[P,P])
class Visual(C.Structure):
    _fields_=[('visual',P),('id',L),('screen',I),('depth',I),('class_',I),
              ('red',L),('green',L),('blue',L),('colormap',I),('bits',I)]
create_context=bind(gl,'glXCreateNewContext',P,[P,P,I,P,I])
create_pbuffer=bind(gl,'glXCreatePbuffer',L,[P,P,C.POINTER(I)])
make_current=bind(gl,'glXMakeContextCurrent',I,[P,L,L,P])
create_gl_pixmap=bind(gl,'glXCreatePixmap',L,[P,P,L,C.POINTER(I)])
proc_address=bind(gl,'glXGetProcAddressARB',P,[C.c_char_p])
def proc(name,result,args):
    address=proc_address(name.encode())
    if not address: raise RuntimeError('Missing GL procedure '+name)
    return C.CFUNCTYPE(result,*args)(address)

display=None; context=None; pbuffer=pixmap=gl_pixmap=0; gc=None; texture=U(); packed_texture=U(); pbo=U(); fbo=U(); fence=None; bound=False; program=0; shaders=[]
damages=[]; overlay=0
original_resources=None; extra_slots=[]; pending_readback=None
report={'kind':'GLX-owned-root-copy-PBO-feasibility','not_visible_fps_acceptance':True}
consumer=globals().get('frame_consumer')
stream_stop=globals().get('stream_stop_event')
i420='--i420' in sys.argv or consumer is not None
report['read_format']='GPU_PACKED_I420' if i420 else ('RGBA' if '--rgba' in sys.argv else 'BGRA')
original_mode=output=None
try:
    if '--1200p' in sys.argv:
        modes=S.check_output(['xrandr','--current'],text=True)
        output=next(line.split()[0] for line in modes.splitlines() if ' connected' in line)
        original_mode=next(line.split()[0] for line in modes.splitlines() if '*' in line and line.startswith(' '))
        S.run(['xrandr','--output',output,'--mode','1920x1200'],check=True)
        time.sleep(1)
    display=open_display(None)
    if not display: raise RuntimeError('No authenticated X display')
    screen=screen_of(display); root=root_of(display)
    r=L(); xx=I(); yy=I(); width=U(); height=U(); border=U(); depth=U()
    if not geometry(display,root,C.byref(r),C.byref(xx),C.byref(yy),C.byref(width),C.byref(height),C.byref(border),C.byref(depth)):
        raise RuntimeError('Cannot read root geometry')
    report['dimensions']=[width.value,height.value]
    count=I()
    configs=choose(display,screen,ints(0x8010,6,0x8011,1,0x20D0,1,5,0,8,8,9,8,10,8,0),C.byref(count))
    if not configs: raise RuntimeError('No texture/pbuffer-compatible FBConfig')
    config=None
    try:
        for index in range(count.value):
            visual=get_visual(display,configs[index])
            if visual:
                info=C.cast(visual,C.POINTER(Visual)).contents
                matching=info.depth==depth.value
                visual_masks=(info.red,info.green,info.blue)
                free(visual)
                if matching:
                    config=configs[index]; report['visual_masks']=visual_masks; break
    finally: free(configs)
    if not config: raise RuntimeError('No FBConfig matching root depth')
    context=create_context(display,config,0x8014,None,1)
    pbuffer=create_pbuffer(display,config,ints(0x8041,1,0x8040,1,0))
    sync(display,0)
    if errors or not context or not pbuffer or not make_current(display,pbuffer,pbuffer,context):
        raise RuntimeError('Cannot create isolated GLX context: '+str(errors))
    get_string=proc('glGetString',C.c_char_p,[U])
    report['renderer']=get_string(0x1F01).decode()
    report['version']=get_string(0x1F02).decode()
    if 'virgl' not in report['renderer'].lower(): raise RuntimeError('Not VirGL; software GPU capture would not be an improvement')
    bind_texture=proc('glBindTexture',None,[U,U]); gen_textures=proc('glGenTextures',None,[I,C.POINTER(U)])
    bind_buffer=proc('glBindBuffer',None,[U,U]); gen_buffers=proc('glGenBuffers',None,[I,C.POINTER(U)])
    buffer_data=proc('glBufferData',None,[U,C.c_ssize_t,P,U])
    get_texture=proc('glGetTexImage',None,[U,I,U,U,P])
    gen_fbo=proc('glGenFramebuffersEXT',None,[I,C.POINTER(U)])
    bind_fbo=proc('glBindFramebufferEXT',None,[U,U])
    attach=proc('glFramebufferTexture2DEXT',None,[U,U,U,U,I])
    check_fbo=proc('glCheckFramebufferStatusEXT',U,[U])
    read_pixels=proc('glReadPixels',None,[I,I,I,I,U,U,P])
    fence_sync=proc('glFenceSync',P,[U,U]); wait_sync=proc('glClientWaitSync',U,[P,U,C.c_ulonglong])
    delete_sync=proc('glDeleteSync',None,[P]); flush=proc('glFlush',None,[])
    map_range=proc('glMapBufferRange',P,[U,C.c_ssize_t,C.c_ssize_t,U]); unmap=proc('glUnmapBuffer',U,[U])
    bind_image=proc('glXBindTexImageEXT',None,[P,L,I,C.POINTER(I)])
    release_image=proc('glXReleaseTexImageEXT',None,[P,L,I])
    gl_error=proc('glGetError',U,[])
    pixmap=create_pixmap(display,root,width.value,height.value,depth.value)
    gc=create_gc(display,pixmap,0,None)
    bind(x,'XSetSubwindowMode',I,[P,P,I])(display,gc,1)
    gl_pixmap=create_gl_pixmap(display,config,pixmap,ints(0x20D5,0x20D9,0x20D6,0x20DC,0))
    sync(display,0)
    if errors: raise RuntimeError('Pixmap import allocation failed: '+str(errors))
    gen_textures(1,C.byref(texture)); bind_texture(0x0DE1,texture.value)
    gen_fbo(1,C.byref(fbo)); bind_fbo(0x8D40,fbo.value)
    gen_buffers(1,C.byref(pbo)); bind_buffer(0x88EB,pbo.value)
    read_width=width.value//4 if i420 else width.value
    read_height=height.value*3//2 if i420 else height.value
    size=read_width*read_height*4
    report['readback_bytes']=size
    if i420:
        if width.value%8 or height.value%2: raise RuntimeError('GPU I420 requires width divisible8 and even height')
        gen_textures(1,C.byref(packed_texture)); bind_texture(0x0DE1,packed_texture.value)
        proc('glTexImage2D',None,[U,I,I,I,I,I,U,U,P])(0x0DE1,0,0x8058,read_width,read_height,0,0x1908,0x1401,None)
        parameter=proc('glTexParameteri',None,[U,U,I])
        parameter(0x0DE1,0x2801,0x2600); parameter(0x0DE1,0x2800,0x2600)
        create_shader=proc('glCreateShader',U,[U]); shader_source=proc('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),C.POINTER(I)])
        compile_shader=proc('glCompileShader',None,[U]); shader_status=proc('glGetShaderiv',None,[U,U,C.POINTER(I)])
        shader_log=proc('glGetShaderInfoLog',None,[U,I,C.POINTER(I),C.c_char_p])
        sources=[(0x8B31,b'#version 120\nvoid main(){ gl_Position=gl_Vertex; }'),(0x8B30,b'''#version 120
uniform sampler2D image;
uniform float w, h, flip;
vec3 rgb(float x,float y){
  float sy=flip>0.5 ? h-1.0-y : y;
  return texture2D(image,vec2((x+0.5)/w,(sy+0.5)/h)).rgb;
}
float channel(float n,float region){
  if(region<0.5){
    float y=floor(n/w), x=n-y*w;
    return dot(rgb(x,y),vec3(0.256788,0.504129,0.097906))+16.0/255.0;
  }
  float cw=w/2.0, y=floor(n/cw), x=n-y*cw;
  vec3 c=(rgb(x*2.0,y*2.0)+rgb(x*2.0+1.0,y*2.0)+rgb(x*2.0,y*2.0+1.0)+rgb(x*2.0+1.0,y*2.0+1.0))*0.25;
  return dot(c,region<1.5 ? vec3(-0.148223,-0.290993,0.439216) : vec3(0.439216,-0.367788,-0.071427))+128.0/255.0;
}
void main(){
  float px=floor(gl_FragCoord.x), py=floor(gl_FragCoord.y);
  float region=py<h ? 0.0 : (py<h*1.25 ? 1.0 : 2.0);
  float row=py-(region<0.5 ? 0.0 : (region<1.5 ? h : h*1.25));
  float n=row*w+px*4.0;
  gl_FragColor=vec4(channel(n,region),channel(n+1.0,region),channel(n+2.0,region),channel(n+3.0,region));
}''')]
        for kind,source in sources:
            shader=create_shader(kind); shaders.append(shader)
            source_array=(C.c_char_p*1)(source); shader_source(shader,1,source_array,None); compile_shader(shader)
            status=I(); shader_status(shader,0x8B81,C.byref(status))
            if not status.value:
                log=C.create_string_buffer(8192); shader_log(shader,8192,None,log)
                raise RuntimeError('GPU conversion shader: '+log.value.decode(errors='replace'))
        program=proc('glCreateProgram',U,[])()
        for shader in shaders: proc('glAttachShader',None,[U,U])(program,shader)
        proc('glLinkProgram',None,[U])(program)
        status=I(); proc('glGetProgramiv',None,[U,U,C.POINTER(I)])(program,0x8B82,C.byref(status))
        if not status.value: raise RuntimeError('GPU conversion program link failed')
        proc('glUseProgram',None,[U])(program)
        uniform=proc('glGetUniformLocation',I,[U,C.c_char_p]); uniform1f=proc('glUniform1f',None,[I,C.c_float])
        proc('glUniform1i',None,[I,I])(uniform(program,b'image'),0)
        inverted=I(); bind(gl,'glXGetFBConfigAttrib',I,[P,P,I,C.POINTER(I)])(display,config,0x20D4,C.byref(inverted))
        report['texture_y_inverted']=inverted.value
        for name,value in [(b'w',width.value),(b'h',height.value),(b'flip',inverted.value)]: uniform1f(uniform(program,name),value)
        proc('glViewport',None,[I,I,I,I])(0,0,read_width,read_height)
        begin=proc('glBegin',None,[U]); end=proc('glEnd',None,[]); vertex=proc('glVertex2f',None,[C.c_float,C.c_float])
        bind_texture(0x0DE1,texture.value)
        parameter(0x0DE1,0x2801,0x2600); parameter(0x0DE1,0x2800,0x2600)
        parameter(0x0DE1,0x2802,0x812F); parameter(0x0DE1,0x2803,0x812F)
    buffer_data(0x88EB,size,None,0x88E1)
    original_resources=(pixmap,gc,gl_pixmap,texture.value,pbo.value)
    slots=[{'pixmap':pixmap,'gc':gc,'gl_pixmap':gl_pixmap,'texture':texture.value,'pbo':pbo.value}]
    async_readback=consumer is not None and globals().get('async_readback',False)
    if async_readback:
        slot={'pixmap':create_pixmap(display,root,width.value,height.value,depth.value),
              'gc':None,'gl_pixmap':0,'texture':0,'pbo':0}
        extra_slots.append(slot)
        slot['gc']=create_gc(display,slot['pixmap'],0,None)
        bind(x,'XSetSubwindowMode',I,[P,P,I])(display,slot['gc'],1)
        slot['gl_pixmap']=create_gl_pixmap(display,config,slot['pixmap'],ints(0x20D5,0x20D9,0x20D6,0x20DC,0))
        new_texture=U(); gen_textures(1,C.byref(new_texture)); slot['texture']=new_texture.value
        bind_texture(0x0DE1,slot['texture'])
        for name,value in [(0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812F),(0x2803,0x812F)]: parameter(0x0DE1,name,value)
        new_pbo=U(); gen_buffers(1,C.byref(new_pbo)); slot['pbo']=new_pbo.value
        bind_buffer(0x88EB,slot['pbo']); buffer_data(0x88EB,size,None,0x88E1)
        sync(display,0)
        if errors: raise RuntimeError('Cannot allocate second isolated capture slot')
        slots.append(slot)
        report['async_readback_slots']=2
    persistent=globals().get('persistent_stream',False)
    measurements=collections.deque(maxlen=120) if persistent else []
    if consumer and globals().get('damage_sync'):
        damage=C.CDLL('libXdamage.so.1'); composite=C.CDLL('libXcomposite.so.1')
        event_base=I(); error_base=I()
        if not bind(damage,'XDamageQueryExtension',I,[P,C.POINTER(I),C.POINTER(I)])(display,C.byref(event_base),C.byref(error_base)):
            raise RuntimeError('No DAMAGE extension for source synchronization')
        overlay=bind(composite,'XCompositeGetOverlayWindow',L,[P,L])(display,root)
        query_tree=bind(x,'XQueryTree',I,[P,L,C.POINTER(L),C.POINTER(L),C.POINTER(C.POINTER(L)),C.POINTER(U)])
        rr=L(); parent=L(); items=C.POINTER(L)(); n=U()
        query_tree(display,overlay,C.byref(rr),C.byref(parent),C.byref(items),C.byref(n))
        try: drawables=[int(items[k]) for k in range(n.value)] or [int(overlay)]
        finally:
            if items: free(items)
        create_damage=bind(damage,'XDamageCreate',L,[P,L,I])
        subtract=bind(damage,'XDamageSubtract',None,[P,L,L,L])
        for drawable in drawables: damages.append(create_damage(display,drawable,3))
        sync(display,0)
        if errors: raise RuntimeError('Cannot watch compositor output: '+str(errors))
        report['damage_source_drawables']=drawables; report['damage_events']=0
        pending=bind(x,'XPending',I,[P]); next_event=bind(x,'XNextEvent',I,[P,P]); flush_x=bind(x,'XFlush',I,[P])
        class DamageEvent(C.Structure):
            _fields_=[('type',I),('serial',L),('send_event',I),('display',P),
                      ('drawable',L),('damage',L),('level',I),('more',I),('timestamp',L)]
        event=C.create_string_buffer(192)
        active_until=[0.0]
        report['idle_heartbeat_seconds']=.2
        report['post_damage_burst_seconds']=.35 if persistent else 0
        def wait_damage():
            # With two PBO slots the last composed frame is consumed on the
            # next capture. A 200ms idle wait therefore delays menu animation
            # tails. Briefly keep a frame-interval cadence after real damage,
            # then return to the low-cost static heartbeat. This is not an FPS
            # measurement and duplicate burst frames must not count as content.
            now=time.monotonic()
            heartbeat=now+(1/60 if persistent and now<active_until[0] else .2)
            while not stream_stop.is_set() and time.monotonic()<deadline:
                changed=False
                while pending(display):
                    next_event(display,event)
                    notification=C.cast(event,C.POINTER(DamageEvent)).contents
                    if notification.type==event_base.value and notification.damage in damages:
                        report['damage_events']+=1; changed=True
                        subtract(display,notification.damage,0,0)
                if changed:
                    if persistent: active_until[0]=time.monotonic()+.35
                    flush_x(display); return True
                if persistent and time.monotonic()>=heartbeat:
                    # Flush the last asynchronous PBO on a static desktop and
                    # keep cursor/session changes observable without a busy loop.
                    return True
                time.sleep(.001)
            return False
    def source_rgb(px,py):
        image=get_image(display,pixmap,px,py,1,1,L(-1).value,2)
        if not image: raise RuntimeError('No source pixel for colour validation')
        try:
            v=image.contents
            # XGetImage on a Pixmap has no associated visual and may report
            # zero RGB masks. Our owned pixmap uses the validated FBConfig.
            masks=(v.red,v.green,v.blue) if v.red else visual_masks
            if v.bpp!=32 or masks!=(0xff0000,0xff00,0xff):
                raise RuntimeError('Unexpected XImage layout in colour validation')
            pixel=int.from_bytes(C.string_at(v.data,4),'little' if v.byte_order==0 else 'big')
            return (pixel>>16)&255,(pixel>>8)&255,pixel&255
        finally: destroy_image(image)
    checks=[(width.value*i//7,height.value*j//5) for i,j in [(1,1),(2,2),(3,3),(4,1),(5,2),(6,4)]]
    if consumer and (width.value,height.value)!=(1920,1200): raise RuntimeError('Streaming requires exact1920x1200')
    deadline=float('inf') if persistent else time.monotonic()+35
    import itertools
    indices=itertools.count() if persistent else range(100000 if consumer else 12)
    for index in indices:
        if consumer and (stream_stop.is_set() or time.monotonic()>=deadline): break
        if damages and index>=2 and not wait_damage(): break
        slot=slots[index%2] if async_readback and index>=2 else slots[0]
        pixmap,gc,gl_pixmap=slot['pixmap'],slot['gc'],slot['gl_pixmap']
        texture=U(slot['texture']); pbo=U(slot['pbo'])
        bind_texture(0x0DE1,texture.value); bind_buffer(0x88EB,pbo.value)
        start=time.monotonic_ns()
        copy_area(display,root,pixmap,gc,0,0,width.value,height.value,0,0)
        sync(display,0)
        copied=time.monotonic_ns()
        bind_image(display,gl_pixmap,0x20DE,None); bound=True
        imported=time.monotonic_ns()
        attach(0x8D40,0x8CE0,0x0DE1,packed_texture.value if i420 else texture.value,0)
        status=check_fbo(0x8D40)
        if status!=0x8CD5: raise RuntimeError('Imported texture is not framebuffer-complete: '+hex(status))
        if i420:
            begin(7)
            for vx,vy in [(-1,-1),(1,-1),(1,1),(-1,1)]: vertex(vx,vy)
            end()
        read_pixels(0,0,read_width,read_height,0x1908 if i420 or '--rgba' in sys.argv else 0x80E1,0x1401,None)
        fence=fence_sync(0x9117,0); flush()
        issued=time.monotonic_ns()
        if async_readback and index>=2:
            release_image(display,gl_pixmap,0x20DE); bound=False
            previous=pending_readback
            pending_readback={'slot':slot,'fence':fence,'start':start,'copied':copied,'imported':imported,'issued':issued}
            fence=None
            if previous is None: continue
            old=previous['slot']
            pixmap,gc,gl_pixmap=old['pixmap'],old['gc'],old['gl_pixmap']
            pbo=U(old['pbo']); bind_buffer(0x88EB,pbo.value)
            fence=previous['fence']
            start,copied,imported,issued=[previous[name] for name in ('start','copied','imported','issued')]
        wait_started=time.monotonic_ns()
        status=wait_sync(fence,1,1_000_000_000)
        waited=time.monotonic_ns()
        if status not in (0x911A,0x911C): raise RuntimeError('PBO fence failed/timeout: '+hex(status))
        pointer=map_range(0x88EB,0,size,1)
        if not pointer: raise RuntimeError('Cannot map completed PBO')
        try:
            if i420 and index in (0,1):
                direct=[]; mirrored=[]
                for px,py in checks:
                    r,g,b=source_rgb(px,py); expected=16+.256788*r+.504129*g+.097906*b
                    direct.append(abs(C.c_ubyte.from_address(pointer+py*width.value+px).value-expected))
                    mirrored.append(abs(C.c_ubyte.from_address(pointer+(height.value-1-py)*width.value+px).value-expected))
                report['luma_validation']={'mean_error':sum(direct)/len(direct),'mirrored_error':sum(mirrored)/len(mirrored)}
                if index==0 and sum(mirrored)+3 < sum(direct):
                    inverted.value=0 if inverted.value>0 else 1
                    uniform1f(uniform(program,b'flip'),inverted.value)
                    report['orientation_corrected_from_pixels']=inverted.value
                elif index==1:
                    if sum(direct)/len(direct)>3: raise RuntimeError('GPU luma does not match source pixels')
                    px=width.value//6; py=height.value//6
                    rgb=[source_rgb(px*2+dx,py*2+dy) for dy in (0,1) for dx in (0,1)]
                    r,g,b=[sum(p[c] for p in rgb)/4 for c in range(3)]
                    uv_offset=py*(width.value//2)+px
                    actual_u=C.c_ubyte.from_address(pointer+width.value*height.value+uv_offset).value
                    actual_v=C.c_ubyte.from_address(pointer+width.value*height.value*5//4+uv_offset).value
                    u=128-.148223*r-.290993*g+.439216*b
                    v=128+.439216*r-.367788*g-.071427*b
                    report['chroma_validation']={'u_error':abs(actual_u-u),'v_error':abs(actual_v-v)}
                    if max(abs(actual_u-u),abs(actual_v-v))>3: raise RuntimeError('GPU chroma does not match source pixels')
            if index==0:
                sample=b''.join(C.string_at(pointer+row*read_width*4,read_width*4)
                                for row in range(0,read_height,max(1,read_height//24)))
                report['pixel_sample']={'crc32':zlib.crc32(sample),
                    'unique_rgb':len({sample[o:o+3] for o in range(0,len(sample)-3,4)}),
                    'nonzero_bytes':sum(v!=0 for v in sample)}
            frame=C.string_at(pointer,size) if consumer and index>=2 else None
        finally: unmap(0x88EB)
        mapped=time.monotonic_ns()
        delete_sync(fence); fence=None
        if bound:
            release_image(display,gl_pixmap,0x20DE); bound=False
        err=gl_error(); sync(display,0)
        if errors or err: raise RuntimeError('GPU capture error: '+str(errors)+' GL='+hex(err))
        measurements.append({'copy_sync_ms':(copied-start)/1e6,'import_ms':(imported-copied)/1e6,
                             'read_issue_ms':(issued-imported)/1e6,
                             'fence_wait_ms':(waited-wait_started)/1e6,'map_ms':(mapped-waited)/1e6})
        if frame is not None: consumer(frame,start)
    report['samples']=list(measurements)
    report['verdict']=('GPU_PIXMAP_IMPORT_AND_PBO_NONUNIFORM_READBACK_PASS'
                       if report['pixel_sample']['unique_rgb']>=32 else 'PIXEL_SOURCE_NOT_PROVEN')
except Exception:
    report['error']=traceback.format_exc(); report['verdict']='GPU_SOURCE_NOT_PROVEN'
finally:
    if display:
        for handle in damages: bind(damage,'XDamageDestroy',None,[P,L])(display,handle)
        if overlay: bind(composite,'XCompositeReleaseOverlayWindow',None,[P,L])(display,root)
        if bound: release_image(display,gl_pixmap,0x20DE)
        if fence: delete_sync(fence)
        if pending_readback: delete_sync(pending_readback['fence'])
        if original_resources:
            pixmap,gc,gl_pixmap,texture_id,pbo_id=original_resources
            texture=U(texture_id); pbo=U(pbo_id)
        if context:
            if program: proc('glDeleteProgram',None,[U])(program)
            for shader in shaders: proc('glDeleteShader',None,[U])(shader)
            if fbo.value: proc('glDeleteFramebuffersEXT',None,[I,C.POINTER(U)])(1,C.byref(fbo))
            if packed_texture.value: proc('glDeleteTextures',None,[I,C.POINTER(U)])(1,C.byref(packed_texture))
            if pbo.value: proc('glDeleteBuffers',None,[I,C.POINTER(U)])(1,C.byref(pbo))
            if texture.value: proc('glDeleteTextures',None,[I,C.POINTER(U)])(1,C.byref(texture))
            for slot in extra_slots:
                if slot['pbo']: proc('glDeleteBuffers',None,[I,C.POINTER(U)])(1,C.byref(U(slot['pbo'])))
                if slot['texture']: proc('glDeleteTextures',None,[I,C.POINTER(U)])(1,C.byref(U(slot['texture'])))
            make_current(display,0,0,None)
        if gl_pixmap: bind(gl,'glXDestroyPixmap',None,[P,L])(display,gl_pixmap)
        if pbuffer: bind(gl,'glXDestroyPbuffer',None,[P,L])(display,pbuffer)
        if context: bind(gl,'glXDestroyContext',None,[P,P])(display,context)
        if gc: bind(x,'XFreeGC',I,[P,P])(display,gc)
        if pixmap: bind(x,'XFreePixmap',I,[P,L])(display,pixmap)
        for slot in extra_slots:
            if slot['gl_pixmap']: bind(gl,'glXDestroyPixmap',None,[P,L])(display,slot['gl_pixmap'])
            if slot['gc']: bind(x,'XFreeGC',I,[P,P])(display,slot['gc'])
            if slot['pixmap']: bind(x,'XFreePixmap',I,[P,L])(display,slot['pixmap'])
        sync(display,0); bind(x,'XCloseDisplay',I,[P])(display)
    set_error_handler(previous_error_handler)
    if original_mode:
        restored=S.run(['xrandr','--output',output,'--mode',original_mode],capture_output=True,text=True)
        report['mode_restore']={'exit':restored.returncode,'mode':original_mode,'stderr':restored.stderr}
    report['x_errors']=len(errors)
    data=json.dumps(report,indent=2).encode()
    if consumer is None:
        urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=data,method='POST'),timeout=15).read()
        print('GLX_PBO_PROBE_SAVED',flush=True)
