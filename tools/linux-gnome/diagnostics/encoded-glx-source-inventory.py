"""Read-only X11 capture-source inventory, no redirects or desktop changes.

Only allocate/free server snapshots; catch X errors instead of killing Xorg.
Does not prove GLX texture import, asynchronous readback, or performance.
"""
import ctypes as C
import json
import subprocess
import urllib.request
import zlib

x = C.CDLL('libX11.so.6')
composite = C.CDLL('libXcomposite.so.1')

class Attributes(C.Structure):
    _fields_ = [('x', C.c_int), ('y', C.c_int), ('width', C.c_int),
                ('height', C.c_int), ('border_width', C.c_int), ('depth', C.c_int),
                ('visual', C.c_void_p), ('root', C.c_ulong), ('class_', C.c_int),
                ('bit_gravity', C.c_int), ('win_gravity', C.c_int),
                ('backing_store', C.c_int), ('backing_planes', C.c_ulong),
                ('backing_pixel', C.c_ulong), ('save_under', C.c_int),
                ('colormap', C.c_ulong), ('map_installed', C.c_int),
                ('map_state', C.c_int), ('all_event_masks', C.c_long),
                ('your_event_mask', C.c_long), ('do_not_propagate_mask', C.c_long),
                ('override_redirect', C.c_int), ('screen', C.c_void_p)]

class Image(C.Structure):
    _fields_ = [('width', C.c_int), ('height', C.c_int), ('xoffset', C.c_int),
                ('format', C.c_int), ('data', C.c_void_p), ('byte_order', C.c_int),
                ('bitmap_unit', C.c_int), ('bitmap_bit_order', C.c_int),
                ('bitmap_pad', C.c_int), ('depth', C.c_int), ('stride', C.c_int),
                ('bpp', C.c_int), ('red_mask', C.c_ulong),
                ('green_mask', C.c_ulong), ('blue_mask', C.c_ulong)]

def bind(lib, name, result, args):
    f = getattr(lib, name); f.restype = result; f.argtypes = args; return f

ptr = C.c_void_p; ul = C.c_ulong; integer = C.c_int
open_display = bind(x, 'XOpenDisplay', ptr, [C.c_char_p])
root_window = bind(x, 'XDefaultRootWindow', ul, [ptr])
query = bind(x, 'XQueryTree', integer, [ptr, ul, C.POINTER(ul), C.POINTER(ul), C.POINTER(C.POINTER(ul)), C.POINTER(C.c_uint)])
attrs = bind(x, 'XGetWindowAttributes', integer, [ptr, ul, C.POINTER(Attributes)])
fetch_name = bind(x, 'XFetchName', integer, [ptr, ul, C.POINTER(C.c_char_p)])
sync = bind(x, 'XSync', integer, [ptr, integer])
free = bind(x, 'XFree', integer, [ptr])
get_image = bind(x, 'XGetImage', C.POINTER(Image), [ptr, ul, integer, integer, C.c_uint, C.c_uint, ul, integer])
destroy_image = bind(x, 'XDestroyImage', integer, [C.POINTER(Image)])
free_pixmap = bind(x, 'XFreePixmap', integer, [ptr, ul])
close = bind(x, 'XCloseDisplay', integer, [ptr])
get_overlay = bind(composite, 'XCompositeGetOverlayWindow', ul, [ptr, ul])
release_overlay = bind(composite, 'XCompositeReleaseOverlayWindow', None, [ptr, ul])
name_pixmap = bind(composite, 'XCompositeNameWindowPixmap', ul, [ptr, ul])

errors = []
@C.CFUNCTYPE(integer, ptr, ptr)
def on_error(display, event):
    errors.append('X protocol error'); return 0
bind(x, 'XSetErrorHandler', ptr, [ptr])(C.cast(on_error, ptr))

def children(display, window):
    r = ul(); p = ul(); items = C.POINTER(ul)(); n = C.c_uint()
    if not query(display, window, C.byref(r), C.byref(p), C.byref(items), C.byref(n)): return []
    try: return [int(items[i]) for i in range(n.value)]
    finally:
        if items: free(items)

def snapshot(display, drawable, width, height):
    # Sparse 64x64 sample from the actual drawable centre, no full-frame loop.
    before = len(errors)
    image = get_image(display, drawable, max(0, width // 2 - 32), max(0, height // 2 - 32),
                      min(64, width), min(64, height), ul(-1).value, 2)
    sync(display, 0)
    if not image or len(errors) != before: return {'error': 'XGetImage unavailable'}
    try:
        i = image.contents
        data = C.string_at(i.data, i.stride * i.height)
        colors = {data[o:o+3] for o in range(0, len(data)-3, max(1, i.bpp//8))}
        return {'crc32': zlib.crc32(data), 'sample_colors': len(colors),
                'nonzero_bytes': sum(v != 0 for v in data), 'bpp': i.bpp}
    finally: destroy_image(image)

report = {'kind': 'read-only-glx-compositor-source-inventory', 'windows': []}
display = open_display(None)
if not display: raise RuntimeError('No authenticated X display')
overlay = 0
try:
    root = root_window(display); overlay = get_overlay(display, root); sync(display, 0)
    gl = C.CDLL('libGL.so.1')
    screen = bind(x,'XDefaultScreen',integer,[ptr])(display)
    extensions = bind(gl,'glXQueryExtensionsString',C.c_char_p,[ptr,integer])(display,screen)
    report['glx_server_extensions'] = extensions.decode() if extensions else ''
    report.update(root=int(root), overlay=int(overlay))
    report['root_sample'] = snapshot(display, root, 1920, 1200)
    ids = list(dict.fromkeys(children(display, root) + ([overlay] if overlay else []) +
                            (children(display, overlay) if overlay else [])))
    for wid in ids:
        a = Attributes(); before = len(errors)
        ok = attrs(display, wid, C.byref(a)); sync(display, 0)
        if not ok or len(errors) != before or a.map_state != 2 or a.width < 800 or a.height < 600: continue
        name = C.c_char_p(); fetch_name(display, wid, C.byref(name))
        label = name.value.decode(errors='replace') if name else ''
        if name: free(C.cast(name, ptr))
        row = {'id': wid, 'name': label, 'geometry': [a.x,a.y,a.width,a.height],
               'depth': a.depth, 'window_sample': snapshot(display,wid,a.width,a.height)}
        before = len(errors); pixmap = name_pixmap(display,wid); sync(display,0)
        if len(errors) == before and pixmap:
            try: row['named_pixmap_sample'] = snapshot(display,pixmap,a.width,a.height)
            finally: free_pixmap(display,pixmap)
        else: row['named_pixmap_error'] = 'Window is not a nameable redirected drawable'
        report['windows'].append(row)
finally:
    if overlay: release_overlay(display,root)
    sync(display,0); close(display)
report['x_errors'] = len(errors)
for key,args in [('renderer',['glxinfo','-B']),('glx',['glxinfo'])]:
    try: result = subprocess.run(args,capture_output=True,text=True,timeout=15)
    except (OSError,subprocess.TimeoutExpired) as error:
        report[key] = {'error':str(error)}; continue
    report[key] = {'exit':result.returncode,'stderr':result.stderr[:2000],
                   'stdout': result.stdout[:3000] if key=='renderer' else
                   '\n'.join(l for l in result.stdout.splitlines() if any(s in l for s in
                       ('texture_from_pixmap','pixel_buffer_object','sync','GLX version','OpenGL version')))}
data = json.dumps(report,indent=2).encode()
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',data=data,method='POST'),timeout=15).read()
print('GLX_SOURCE_INVENTORY_SAVED',flush=True)
