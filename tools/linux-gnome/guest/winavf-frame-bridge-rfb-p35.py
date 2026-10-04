#!/usr/bin/env python3
"""Bounded-rate GNOME frames and input over one full-duplex vsock stream."""
import os
import struct
import subprocess
import time
import zlib
import ctypes
import ctypes.util
import shutil
import socket
import threading

def find_display(verbose=False):
    # The isolated software X server has a readable framebuffer.  Prefer it
    # only when its real Unix socket exists; leave the proven :0 path intact.
    if os.path.exists("/tmp/.X11-unix/X1"):
        env = os.environ.copy()
        env["DISPLAY"] = ":1"
        env.pop("XAUTHORITY", None)
        if verbose:
            print("SESSION_DISCOVERY=XVNC DISPLAY=:1", file=os.sys.stderr,
                  flush=True)
        return env
    # GDM's Xauthority is usually under /run/user/<uid>/gdm, not in the
    # live user's home directory. Read the running session's own environment
    # instead of guessing a display number or a drive-dependent path.
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            with open("/proc/" + pid + "/comm", "rb") as source:
                comm = source.read().strip().decode("ascii", "replace")
            if comm not in ("gnome-shell", "gdm-x-session", "Xorg"):
                continue
            with open("/proc/" + pid + "/environ", "rb") as source:
                entries = source.read().split(b"\0")
            session = dict(entry.decode("utf-8", "replace").split("=", 1)
                           for entry in entries if b"=" in entry)
            if session.get("DISPLAY") and session.get("XAUTHORITY") and os.path.isfile(session["XAUTHORITY"]):
                env = os.environ.copy()
                env.update(session)
                print("SESSION_DISCOVERY=PASS COMM=" + comm +
                      " TYPE=" + session.get("XDG_SESSION_TYPE", "unknown") +
                      " WAYLAND=" + session.get("WAYLAND_DISPLAY", "none") +
                      " DISPLAY=" + session["DISPLAY"] +
                      " XAUTHORITY=" + session["XAUTHORITY"], file=os.sys.stderr, flush=True)
                return env
            runtime = session.get("XDG_RUNTIME_DIR")
            wayland = session.get("WAYLAND_DISPLAY")
            if runtime and wayland and os.path.exists(os.path.join(runtime, wayland)):
                env = os.environ.copy()
                env.update(session)
                return env
        except (OSError, ValueError):
            continue
    for root in ("/run/user", "/run/user/1000"):
        if not os.path.isdir(root):
            continue
        for name in os.listdir(root):
            runtime = os.path.join(root, name)
            if not os.path.isdir(runtime):
                continue
            displays = [x for x in os.listdir(runtime) if x.startswith("wayland-")]
            if displays:
                env = os.environ.copy()
                env.update(XDG_RUNTIME_DIR=runtime, WAYLAND_DISPLAY=displays[0])
                return env
    # The current stock profile is X11 (gdm-x-session).  Keep this fallback
    # software-only; it does not require /dev/dri or a compositor API.
    if os.path.exists("/home/ubuntu/.Xauthority"):
        env = os.environ.copy()
        env.update(DISPLAY=":0", XAUTHORITY="/home/ubuntu/.Xauthority")
        return env
    return None


class XImage(ctypes.Structure):
    _fields_ = [("width", ctypes.c_int), ("height", ctypes.c_int),
                ("xoffset", ctypes.c_int), ("format", ctypes.c_int),
                ("data", ctypes.POINTER(ctypes.c_char)),
                ("byte_order", ctypes.c_int), ("bitmap_unit", ctypes.c_int),
                ("bitmap_bit_order", ctypes.c_int), ("bitmap_pad", ctypes.c_int),
                ("depth", ctypes.c_int), ("bytes_per_line", ctypes.c_int),
                ("bits_per_pixel", ctypes.c_int), ("red_mask", ctypes.c_ulong),
                ("green_mask", ctypes.c_ulong), ("blue_mask", ctypes.c_ulong),
                ("obdata", ctypes.c_void_p), ("funcs", ctypes.c_void_p)]


class XWindowAttributes(ctypes.Structure):
    _fields_ = [("x", ctypes.c_int), ("y", ctypes.c_int),
                ("width", ctypes.c_int), ("height", ctypes.c_int),
                ("border_width", ctypes.c_int), ("depth", ctypes.c_int),
                ("visual", ctypes.c_void_p), ("root", ctypes.c_ulong),
                ("window_class", ctypes.c_int), ("bit_gravity", ctypes.c_int),
                ("win_gravity", ctypes.c_int), ("backing_store", ctypes.c_int),
                ("backing_planes", ctypes.c_ulong), ("backing_pixel", ctypes.c_ulong),
                ("save_under", ctypes.c_int), ("colormap", ctypes.c_ulong),
                ("map_installed", ctypes.c_int), ("map_state", ctypes.c_int),
                ("all_event_masks", ctypes.c_long), ("your_event_mask", ctypes.c_long),
                ("do_not_propagate_mask", ctypes.c_long),
                ("override_redirect", ctypes.c_int), ("screen", ctypes.c_void_p)]


def capture_gnome_shell(env, sequence):
    """Ask GNOME's compositor for its actual output, not Xwayland's root."""
    gdbus = shutil.which("gdbus")
    runtime = env.get("XDG_RUNTIME_DIR")
    bus = env.get("DBUS_SESSION_BUS_ADDRESS")
    if not gdbus or not runtime or not bus:
        raise RuntimeError("GNOME_SCREENSHOT_PREREQUISITES_MISSING")
    owner = os.stat(runtime)
    filename = os.path.join(runtime, "winavf-frame-%d-%d.png" %
                            (os.getpid(), sequence))
    cmd = [gdbus, "call", "--session", "--dest", "org.gnome.Shell.Screenshot",
           "--object-path", "/org/gnome/Shell/Screenshot", "--method",
           "org.gnome.Shell.Screenshot.Screenshot", "false", "false", filename]
    try:
        result = subprocess.run(cmd, env=env, capture_output=True, text=True,
                                timeout=8, user=owner.st_uid, group=owner.st_gid)
        if result.returncode or not os.path.isfile(filename):
            detail = (result.stderr or result.stdout).strip().replace("\n", " ")[:160]
            raise RuntimeError("GNOME_SCREENSHOT_FAILED=" + detail)
        if not result.stdout.lstrip().startswith("(true,"):
            raise RuntimeError("GNOME_SCREENSHOT_REPLY=" + result.stdout.strip()[:120])
        gdk = ctypes.CDLL("libgdk_pixbuf-2.0.so.0")
        gdk.gdk_pixbuf_new_from_file.argtypes = [ctypes.c_char_p,
                                                 ctypes.POINTER(ctypes.c_void_p)]
        gdk.gdk_pixbuf_new_from_file.restype = ctypes.c_void_p
        error = ctypes.c_void_p()
        pixbuf = gdk.gdk_pixbuf_new_from_file(os.fsencode(filename),
                                               ctypes.byref(error))
        if not pixbuf:
            raise RuntimeError("GNOME_SCREENSHOT_PNG_DECODE_FAILED")
        try:
            for method in ("get_width", "get_height", "get_rowstride", "get_n_channels"):
                getattr(gdk, "gdk_pixbuf_" + method).argtypes = [ctypes.c_void_p]
                getattr(gdk, "gdk_pixbuf_" + method).restype = ctypes.c_int
            gdk.gdk_pixbuf_get_pixels.argtypes = [ctypes.c_void_p]
            gdk.gdk_pixbuf_get_pixels.restype = ctypes.c_void_p
            width = gdk.gdk_pixbuf_get_width(pixbuf)
            height = gdk.gdk_pixbuf_get_height(pixbuf)
            stride = gdk.gdk_pixbuf_get_rowstride(pixbuf)
            channels = gdk.gdk_pixbuf_get_n_channels(pixbuf)
            if not (0 < width <= 4096 and 0 < height <= 4096 and channels in (3, 4)):
                raise RuntimeError("GNOME_SCREENSHOT_UNSUPPORTED_FORMAT")
            raw = ctypes.string_at(gdk.gdk_pixbuf_get_pixels(pixbuf), stride * height)
            pixels = bytearray(width * height * 4)
            for y in range(height):
                for x in range(width):
                    src = y * stride + x * channels
                    dst = (y * width + x) * 4
                    pixels[dst:dst + 4] = bytes((raw[src + 2], raw[src + 1],
                                                  raw[src], 255))
            print("CAPTURE_BACKEND=GNOME_SHELL_SCREENSHOT WIDTH=%d HEIGHT=%d FORMAT=BGRA" %
                  (width, height), file=os.sys.stderr, flush=True)
            return width, height, pixels
        finally:
            gobject = ctypes.CDLL("libgobject-2.0.so.0")
            gobject.g_object_unref.argtypes = [ctypes.c_void_p]
            gobject.g_object_unref(pixbuf)
    finally:
        if os.path.exists(filename):
            os.unlink(filename)


def capture_x11(env, verbose=False):
    library = ctypes.util.find_library("X11") or "libX11.so.6"
    try:
        x11 = ctypes.CDLL(library)
    except OSError as error:
        raise RuntimeError("CAPTURE_BACKEND_NOT_FOUND=libX11:" + str(error))
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    # XOpenDisplay reads XAUTHORITY from this process, not from the local
    # dictionary passed to capture_x11().  Preserve the session's cookie.
    if "XAUTHORITY" in env:
        os.environ["XAUTHORITY"] = env["XAUTHORITY"]
    else:
        os.environ.pop("XAUTHORITY", None)
    display = x11.XOpenDisplay(env["DISPLAY"].encode())
    if not display:
        raise RuntimeError("CAPTURE_X11_OPEN_FAILED")
    x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
    x11.XDefaultRootWindow.restype = ctypes.c_ulong
    x11.XGetGeometry.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                ctypes.POINTER(ctypes.c_ulong),
                                ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int),
                                ctypes.POINTER(ctypes.c_uint), ctypes.POINTER(ctypes.c_uint),
                                ctypes.POINTER(ctypes.c_uint), ctypes.POINTER(ctypes.c_uint)]
    x11.XGetGeometry.restype = ctypes.c_int
    x11.XGetImage.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_int,
                              ctypes.c_uint, ctypes.c_uint, ctypes.c_ulong, ctypes.c_int]
    x11.XGetImage.restype = ctypes.POINTER(XImage)
    x11.XDestroyImage.argtypes = [ctypes.POINTER(XImage)]
    x11.XQueryTree.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                              ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_ulong),
                              ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)),
                              ctypes.POINTER(ctypes.c_uint)]
    x11.XQueryTree.restype = ctypes.c_int
    x11.XGetWindowAttributes.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                         ctypes.POINTER(XWindowAttributes)]
    x11.XGetWindowAttributes.restype = ctypes.c_int
    x11.XFree.argtypes = [ctypes.c_void_p]
    x11.XFreePixmap.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
    error_callback = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p, ctypes.c_void_p)
    x11.XSetErrorHandler.argtypes = [error_callback]
    x11.XSetErrorHandler.restype = ctypes.c_void_p
    error_handler = error_callback(lambda _display, _event: 0)
    x11.XSetErrorHandler(error_handler)
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    try:
        root = x11.XDefaultRootWindow(display)
        actual_root = ctypes.c_ulong()
        x = ctypes.c_int()
        y = ctypes.c_int()
        width = ctypes.c_uint()
        height = ctypes.c_uint()
        border = ctypes.c_uint()
        depth = ctypes.c_uint()
        if not x11.XGetGeometry(display, root, ctypes.byref(actual_root),
                               ctypes.byref(x), ctypes.byref(y), ctypes.byref(width),
                               ctypes.byref(height), ctypes.byref(border), ctypes.byref(depth)):
            raise RuntimeError("CAPTURE_X11_GEOMETRY_FAILED")
        def read_drawable(drawable, w, h, label):
            image = x11.XGetImage(display, drawable, 0, 0, w, h, 0xffffffff, 2)
            if not image:
                return None
            try:
                info = image.contents
                if info.bits_per_pixel != 32 or not info.data:
                    return None
                raw = ctypes.string_at(info.data, info.bytes_per_line * h)
                # Xvnc is already little-endian BGRX. Copy whole rows in C
                # rather than running 786,432 Python iterations per frame.
                if info.bytes_per_line == w * 4:
                    pixels = bytearray(raw)
                else:
                    pixels = bytearray().join(
                        raw[row * info.bytes_per_line:row * info.bytes_per_line + w * 4]
                        for row in range(h))
                # The isolated Xvnc :1 root is the already-proven output
                # source. Avoid scanning/copying three full 1080p color planes
                # on every capture; the Android receiver still checks pixels.
                nonblack = 1 if env.get("DISPLAY") == ":1" else int(
                    any(pixels[0::4]) or any(pixels[1::4]) or any(pixels[2::4]))
                pixels[3::4] = b"\xff" * (w * h)
                if verbose:
                    print("X11_DRAWABLE=%s WIDTH=%d HEIGHT=%d NONBLACK=%d" %
                          (label, w, h, nonblack), file=os.sys.stderr, flush=True)
                return (w, h, pixels, nonblack)
            finally:
                x11.XDestroyImage(image)

        root_image = read_drawable(root, width.value, height.value, "ROOT")
        if root_image is None:
            raise RuntimeError("CAPTURE_X11_GETIMAGE_FAILED")
        if root_image[3]:
            if verbose:
                print("CAPTURE_BACKEND=X11_ROOT", file=os.sys.stderr, flush=True)
            return root_image[:3]

        try:
            composite = ctypes.CDLL("libXcomposite.so.1")
            composite.XCompositeNameWindowPixmap.argtypes = [ctypes.c_void_p,
                                                               ctypes.c_ulong]
            composite.XCompositeNameWindowPixmap.restype = ctypes.c_ulong
            composite.XCompositeGetOverlayWindow.argtypes = [ctypes.c_void_p,
                                                               ctypes.c_ulong]
            composite.XCompositeGetOverlayWindow.restype = ctypes.c_ulong
            composite.XCompositeReleaseOverlayWindow.argtypes = [ctypes.c_void_p,
                                                                   ctypes.c_ulong]
            print("X11_COMPOSITE_LIBRARY=YES", file=os.sys.stderr, flush=True)
        except OSError:
            composite = None
            print("X11_COMPOSITE_LIBRARY=NO", file=os.sys.stderr, flush=True)

        actual_root = ctypes.c_ulong()
        parent = ctypes.c_ulong()
        children = ctypes.POINTER(ctypes.c_ulong)()
        count = ctypes.c_uint()
        if x11.XQueryTree(display, root, ctypes.byref(actual_root),
                          ctypes.byref(parent), ctypes.byref(children), ctypes.byref(count)):
            try:
                windows = []
                for i in range(min(count.value, 128)):
                    window = children[i]
                    attr = XWindowAttributes()
                    if (x11.XGetWindowAttributes(display, window, ctypes.byref(attr))
                            and attr.map_state == 2 and attr.width >= 128
                            and attr.height >= 128 and attr.width <= 4096
                            and attr.height <= 4096):
                        windows.append((attr.width * attr.height, window,
                                        attr.width, attr.height))
                print("X11_ROOT_CHILDREN=%d MAPPED_LARGE=%d" %
                      (count.value, len(windows)), file=os.sys.stderr, flush=True)
                for _, window, w, h in sorted(windows, reverse=True)[:12]:
                    print("X11_MAPPED_WINDOW=0x%x SIZE=%dx%d" % (window, w, h),
                          file=os.sys.stderr, flush=True)
                    result = read_drawable(window, w, h, "WINDOW_0x%x" % window)
                    if result and result[3]:
                        print("CAPTURE_BACKEND=X11_MAPPED_WINDOW", file=os.sys.stderr,
                              flush=True)
                        return result[:3]
                    if composite is not None:
                        pixmap = composite.XCompositeNameWindowPixmap(display, window)
                        if pixmap:
                            try:
                                result = read_drawable(pixmap, w, h,
                                                       "NAMED_PIXMAP_0x%x" % window)
                                if result and result[3]:
                                    print("CAPTURE_BACKEND=X11_NAMED_PIXMAP",
                                          file=os.sys.stderr, flush=True)
                                    return result[:3]
                            finally:
                                x11.XFreePixmap(display, pixmap)
            finally:
                if children:
                    x11.XFree(children)
        if composite is not None:
            overlay = composite.XCompositeGetOverlayWindow(display, root)
            if overlay:
                try:
                    overlay_root = ctypes.c_ulong()
                    overlay_x = ctypes.c_int()
                    overlay_y = ctypes.c_int()
                    overlay_w = ctypes.c_uint()
                    overlay_h = ctypes.c_uint()
                    overlay_border = ctypes.c_uint()
                    overlay_depth = ctypes.c_uint()
                    if x11.XGetGeometry(display, overlay, ctypes.byref(overlay_root),
                                        ctypes.byref(overlay_x), ctypes.byref(overlay_y),
                                        ctypes.byref(overlay_w), ctypes.byref(overlay_h),
                                        ctypes.byref(overlay_border),
                                        ctypes.byref(overlay_depth)):
                        print("X11_COMPOSITE_OVERLAY=0x%x SIZE=%dx%d" %
                              (overlay, overlay_w.value, overlay_h.value),
                              file=os.sys.stderr, flush=True)
                        if 0 < overlay_w.value <= 4096 and 0 < overlay_h.value <= 4096:
                            result = read_drawable(overlay, overlay_w.value,
                                                   overlay_h.value, "OVERLAY")
                            if result and result[3]:
                                print("CAPTURE_BACKEND=X11_COMPOSITE_OVERLAY",
                                      file=os.sys.stderr, flush=True)
                                return result[:3]
                finally:
                    composite.XCompositeReleaseOverlayWindow(display, root)
        print("CAPTURE_BACKEND=X11_ROOT_BLACK", file=os.sys.stderr, flush=True)
        return root_image[:3]
    finally:
        x11.XCloseDisplay(display)

def wavf_record(record_type, flags, width, height, sequence, payload):
    crc = zlib.crc32(payload) & 0xffffffff
    return struct.pack("<4sBBBBHHIIII", b"WAVF", 1, record_type, flags, 0,
                       width, height, sequence, len(payload), crc, 0) + payload


class DeltaEncoder:
    """Keep WAVF v1; send compressed keyframes and changed row bands."""
    def __init__(self):
        self.previous = None
        self.width = self.height = 0
        self.captures = 0

    def encode(self, width, height, pixels, sequence):
        if len(pixels) != width * height * 4:
            raise ValueError("CAPTURE_SIZE_MISMATCH")
        self.captures += 1
        if (self.previous is None or width != self.width or height != self.height
                or self.captures % 150 == 0):
            payload = zlib.compress(pixels, 1)
            self.previous = bytes(pixels)
            self.width, self.height = width, height
            return wavf_record(1, 1, width, height, sequence, payload)
        if pixels == self.previous:
            return None
        old = memoryview(self.previous)
        current = memoryview(pixels)
        changed = bytearray()
        stride = width * 4
        for top in range(0, height, 64):
            band_height = min(64, height - top)
            start = top * stride
            end = (top + band_height) * stride
            if current[start:end] == old[start:end]:
                continue
            raw = bytes(current[start:end])
            compressed = zlib.compress(raw, 1)
            payload = compressed if len(compressed) < len(raw) else raw
            changed.extend(struct.pack("<HHHHI", 0, top, width,
                                       band_height, len(payload)))
            changed.extend(payload)
        self.previous = bytes(pixels)
        if not changed:
            return None
        return wavf_record(2, 0, width, height, sequence, changed)


class RfbIncrementalCapture:
    """Read Xvnc's changed rectangles; keep WAVF and Android unchanged."""
    def __init__(self, address=("127.0.0.1", 5901)):
        self.sock = socket.create_connection(address, timeout=2)
        self.sock.settimeout(3)
        try:
            version = self._read(12)
            if not version.startswith(b"RFB 003."):
                raise RuntimeError("RFB_VERSION_UNSUPPORTED")
            self.sock.sendall(b"RFB 003.008\n")
            count = self._read(1)[0]
            if count == 0:
                length = struct.unpack(">I", self._read(4))[0]
                raise RuntimeError("RFB_SECURITY=" + repr(self._read(min(length, 256))))
            types = self._read(count)
            if 1 not in types:  # SecurityType None, guest loopback only.
                raise RuntimeError("RFB_SECURITY_NONE_UNAVAILABLE")
            self.sock.sendall(b"\x01")
            if struct.unpack(">I", self._read(4))[0] != 0:
                raise RuntimeError("RFB_SECURITY_REJECTED")
            self.sock.sendall(b"\x01")  # shared ClientInit
            header = self._read(24)
            self.width, self.height = struct.unpack_from(">HH", header)
            if not (0 < self.width <= 4096 and 0 < self.height <= 4096
                    and self.width * self.height * 4 <= 16 * 1024 * 1024):
                raise RuntimeError("RFB_DIMENSIONS_INVALID")
            name_length = struct.unpack_from(">I", header, 20)[0]
            if name_length > 4096:
                raise RuntimeError("RFB_NAME_TOO_LONG")
            self._read(name_length)
            # Little-endian BGRX; request only raw rectangles. Xvnc decides
            # which regions changed, so no full-screen XGetImage is needed.
            pixel_format = struct.pack(">BBBBHHHBBBxxx", 32, 24, 0, 1,
                                       255, 255, 255, 16, 8, 0)
            self.sock.sendall(b"\x00\x00\x00\x00" + pixel_format)
            self.sock.sendall(struct.pack(">BBHi", 2, 0, 1, 0))
            self.pixels = bytearray(self.width * self.height * 4)
            self.initialized = False
            self.updates = 0
            # A static desktop may leave an incremental request pending. Use
            # a bounded read without treating an idle interval as a failure.
            self.pending = False
            self.sock.settimeout(8)
            print("CAPTURE_BACKEND=XVNC_RFB_INCREMENTAL WIDTH=%d HEIGHT=%d" %
                  (self.width, self.height), file=os.sys.stderr, flush=True)
        except Exception:
            self.sock.close()
            raise

    def _read(self, length):
        data = bytearray()
        while len(data) < length:
            part = self.sock.recv(length - len(data))
            if not part:
                raise RuntimeError("RFB_STREAM_CLOSED")
            data.extend(part)
        return bytes(data)

    def next_packet(self, sequence):
        if not self.pending:
            self.sock.sendall(struct.pack(">BBHHHH", 3, int(self.initialized),
                                          0, 0, self.width, self.height))
            self.pending = True
        try:
            kind = self._read(1)[0]
        except TimeoutError:
            # No update on an unchanged screen. Keep the outstanding request;
            # sending another would create an RFB request backlog.
            return None
        self.pending = False
        if kind != 0:
            raise RuntimeError("RFB_UNEXPECTED_MESSAGE=%d" % kind)
        count = struct.unpack(">H", self._read(3)[1:])[0]
        if count > 4096:
            raise RuntimeError("RFB_TOO_MANY_RECTS")
        changed = bytearray()
        changed_area = 0
        for _ in range(count):
            x, y, w, h, encoding = struct.unpack(">HHHHi", self._read(12))
            if (encoding != 0 or not w or not h or x + w > self.width
                    or y + h > self.height):
                raise RuntimeError("RFB_RECT_INVALID")
            raw = bytearray(self._read(w * h * 4))
            raw[3::4] = b"\xff" * (w * h)
            stride = w * 4
            for row in range(h):
                start = ((y + row) * self.width + x) * 4
                self.pixels[start:start + stride] = raw[row * stride:(row + 1) * stride]
            compressed = zlib.compress(raw, 1)
            payload = compressed if len(compressed) < len(raw) else raw
            changed.extend(struct.pack("<HHHHI", x, y, w, h, len(payload)))
            changed.extend(payload)
            changed_area += w * h
        self.updates += 1
        print("RFB_UPDATE rects=%d pixels=%d" % (count, changed_area),
              file=os.sys.stderr, flush=True)
        if not self.initialized or self.updates % 150 == 0:
            self.initialized = True
            return wavf_record(1, 1, self.width, self.height, sequence,
                               zlib.compress(self.pixels, 1))
        if changed:
            return wavf_record(2, 0, self.width, self.height, sequence, changed)
        return None


class X11MotionPattern:
    """Small real Xvnc window that changes each capture for a fair FPS test."""
    def __init__(self):
        self.x11 = ctypes.CDLL(ctypes.util.find_library("X11") or "libX11.so.6")
        x11 = self.x11
        x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
        x11.XOpenDisplay.restype = ctypes.c_void_p
        self.display = x11.XOpenDisplay(b":1")
        if not self.display:
            raise RuntimeError("MOTION_PATTERN_DISPLAY_UNAVAILABLE")
        x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
        x11.XDefaultRootWindow.restype = ctypes.c_ulong
        x11.XCreateSimpleWindow.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
            ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint,
            ctypes.c_uint, ctypes.c_ulong, ctypes.c_ulong]
        x11.XCreateSimpleWindow.restype = ctypes.c_ulong
        x11.XMapRaised.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        x11.XCreateGC.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                  ctypes.c_ulong, ctypes.c_void_p]
        x11.XCreateGC.restype = ctypes.c_void_p
        x11.XSetForeground.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_ulong]
        x11.XFillRectangle.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
            ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint]
        x11.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
        root = x11.XDefaultRootWindow(self.display)
        self.window = x11.XCreateSimpleWindow(self.display, root, 80, 80,
                                               512, 128, 0, 0, 0xffffff)
        if not self.window:
            raise RuntimeError("MOTION_PATTERN_WINDOW_FAILED")
        self.gc = x11.XCreateGC(self.display, self.window, 0, None)
        if not self.gc:
            raise RuntimeError("MOTION_PATTERN_GC_FAILED")
        x11.XMapRaised(self.display, self.window)
        x11.XSync(self.display, 0)
        print("MOTION_PATTERN_X11=PASS WIDTH=512 HEIGHT=128", file=os.sys.stderr,
              flush=True)

    def step(self, sequence):
        x11 = self.x11
        x11.XSetForeground(self.display, self.gc, 0xffffff)
        x11.XFillRectangle(self.display, self.window, self.gc, 0, 0, 512, 128)
        x11.XSetForeground(self.display, self.gc, 0x000000)
        x11.XFillRectangle(self.display, self.window, self.gc,
                            (sequence * 19) % 448, 16, 64, 96)
        x11.XSync(self.display, 0)


def frame(sequence, encoder):
    verbose = sequence == 1
    env = find_display(verbose)
    if env is None:
        raise RuntimeError("DISPLAY_RUNTIME_NOT_FOUND")
    # P14 proved GNOME Shell's D-Bus screenshot endpoint denies this caller.
    # Inspect the live Xorg window hierarchy without waiting on that timeout.
    width, height, bgra = capture_x11(env, verbose)
    captured = time.monotonic()
    if len(bgra) != width * height * 4:
        raise RuntimeError("CAPTURE_SIZE_MISMATCH")
    packet = encoder.encode(width, height, bgra, sequence)
    return packet, captured

INPUT_PACKET = struct.Struct("<4sBBHii")


def input_loop():
    """Read only host-owned fixed-size input records from the socket stdin."""
    try:
        x11 = ctypes.CDLL(ctypes.util.find_library("X11") or "libX11.so.6")
        xtst = ctypes.CDLL(ctypes.util.find_library("Xtst") or "libXtst.so.6")
        x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
        x11.XOpenDisplay.restype = ctypes.c_void_p
        x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        x11.XKeysymToKeycode.restype = ctypes.c_uint
        x11.XFlush.argtypes = [ctypes.c_void_p]
        xtst.XTestQueryExtension.argtypes = [ctypes.c_void_p] + [ctypes.POINTER(ctypes.c_int)] * 4
        xtst.XTestFakeMotionEvent.argtypes = [ctypes.c_void_p, ctypes.c_int,
                                               ctypes.c_int, ctypes.c_int, ctypes.c_ulong]
        xtst.XTestFakeButtonEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint,
                                               ctypes.c_int, ctypes.c_ulong]
        xtst.XTestFakeKeyEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint,
                                            ctypes.c_int, ctypes.c_ulong]
        display = None
        for _ in range(180):
            display = x11.XOpenDisplay(b":1")
            if display:
                break
            time.sleep(1)
        if not display:
            raise RuntimeError("INPUT_DISPLAY_UNAVAILABLE")
        values = [ctypes.c_int() for _ in range(4)]
        if not xtst.XTestQueryExtension(display, *(ctypes.byref(v) for v in values)):
            raise RuntimeError("INPUT_XTEST_EXTENSION_UNAVAILABLE")
        print("INPUT_XTEST_READY", file=os.sys.stderr, flush=True)
        while True:
            packet = bytearray()
            while len(packet) < INPUT_PACKET.size:
                part = os.read(0, INPUT_PACKET.size - len(packet))
                if not part:
                    return
                packet.extend(part)
            magic, kind, value, _reserved, x, y = INPUT_PACKET.unpack(packet)
            if magic != b"UIN1":
                raise RuntimeError("INPUT_BAD_MAGIC")
            if kind == 1 and 0 <= x < 4096 and 0 <= y < 4096:
                result = xtst.XTestFakeMotionEvent(display, -1, x, y, 0)
            elif kind == 2 and value in (0, 1) and x in (0, 1, 2, 3):
                # x=0 preserves older touch packets; 1/2/3 are X11 mouse buttons.
                result = xtst.XTestFakeButtonEvent(display, x or 1, value, 0)
            elif kind == 3 and value in (0, 1):
                keycode = x11.XKeysymToKeycode(display, x)
                result = xtst.XTestFakeKeyEvent(display, keycode, value, 0) if keycode else 0
            elif kind == 4 and -32 <= x <= 32 and -32 <= y <= 32:
                # XTest wheel events are button press/release pairs.
                result = 1
                for button, steps in ((6 if x > 0 else 7, abs(x)),
                                      (4 if y > 0 else 5, abs(y))):
                    for _ in range(steps):
                        result &= xtst.XTestFakeButtonEvent(display, button, 1, 0)
                        result &= xtst.XTestFakeButtonEvent(display, button, 0, 0)
            else:
                print("INPUT_INVALID_RECORD", file=os.sys.stderr, flush=True)
                continue
            x11.XFlush(display)
            print("INPUT_EVENT kind=%d accepted=%d" % (kind, result),
                  file=os.sys.stderr, flush=True)
    except Exception as error:
        print("INPUT_ERROR=" + str(error), file=os.sys.stderr, flush=True)


if __name__ == "__main__":
    threading.Thread(target=input_loop, name="winavf-input", daemon=True).start()
    target_fps = 30
    print("FRAME_TARGET_FPS=%d DELTA_TILES=64" % target_fps,
          file=os.sys.stderr, flush=True)
    encoder = DeltaEncoder()
    rfb = None
    sequence = 1
    started = time.monotonic()
    next_frame = started
    last_error = None
    stats_started = started
    stats_captures = stats_packets = stats_bytes = 0
    stats_compute = stats_write = stats_capture = stats_encode = 0.0
    while True:
        try:
            begin = time.monotonic()
            if rfb is None:
                rfb = RfbIncrementalCapture()
            packet = rfb.next_packet(sequence)
            captured = time.monotonic()
            computed = time.monotonic()
            stats_captures += 1
            stats_compute += computed - begin
            stats_capture += captured - begin
            stats_encode += computed - captured
            if packet:
                offset = 0
                while offset < len(packet):
                    offset += os.write(1, packet[offset:])
                stats_packets += 1
                stats_bytes += len(packet)
                stats_write += time.monotonic() - computed
                sequence += 1
            last_error = None
        except Exception as error:
            if rfb is not None:
                rfb.sock.close()
                rfb = None
            # The vsock socket starts before GDM/GNOME creates its display. Keep
            # this one connection alive while the graphical session comes up.
            if str(error) != last_error:
                print("WINAVF_FRAME_BRIDGE=" + str(error), file=os.sys.stderr, flush=True)
                last_error = str(error)
            if sequence == 1 and time.monotonic() - started > 180:
                raise
        now = time.monotonic()
        if now - stats_started >= 5.0:
            elapsed = now - stats_started
            print("FRAME_STATS capture_fps=%.2f packet_fps=%.2f bytes_per_s=%.0f compute_ms=%.2f write_ms=%.2f capture_ms=%.2f encode_ms=%.2f" %
                  (stats_captures / elapsed, stats_packets / elapsed,
                   stats_bytes / elapsed, 1000 * stats_compute / max(1, stats_captures),
                   1000 * stats_write / max(1, stats_packets),
                   1000 * stats_capture / max(1, stats_captures),
                   1000 * stats_encode / max(1, stats_captures)),
                  file=os.sys.stderr, flush=True)
            stats_started = now
            stats_captures = stats_packets = stats_bytes = 0
            stats_compute = stats_write = stats_capture = stats_encode = 0.0
        next_frame = max(next_frame + 1.0 / target_fps, time.monotonic())
        time.sleep(max(0.0, next_frame - time.monotonic()))
