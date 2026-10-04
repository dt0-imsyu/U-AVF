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
import threading
import pwd
import socket

_CAPTURE_BACKEND_LOGGED = set()

def find_display(verbose=False):
    # Match a logged-in GNOME shell to its GDM Xorg launcher, not to a
    # guessed display number (or an independently started Xvnc desktop).
    processes = []
    for pid in os.listdir("/proc"):
        if not pid.isdigit():
            continue
        try:
            with open("/proc/" + pid + "/comm", "rb") as source:
                comm = source.read().strip().decode("ascii", "replace")
            if comm not in ("gnome-shell", "gdm-x-session", "Xorg"):
                continue
            uid = os.stat("/proc/" + pid).st_uid
            with open("/proc/" + pid + "/environ", "rb") as source:
                entries = source.read().split(b"\0")
            session = dict(entry.decode("utf-8", "replace").split("=", 1)
                           for entry in entries if b"=" in entry)
            with open("/proc/" + pid + "/cmdline", "rb") as source:
                command = source.read().split(b"\0")
            processes.append((comm, uid, session, command))
        except (OSError, ValueError):
            continue
    for comm, uid, session, _ in processes:
        if comm != "gnome-shell" or uid == 0 or session.get("XDG_SESSION_TYPE") != "x11":
            continue
        display = session.get("DISPLAY", "")
        authority = session.get("XAUTHORITY", "")
        runtime = session.get("XDG_RUNTIME_DIR", "")
        bus = session.get("DBUS_SESSION_BUS_ADDRESS", "")
        if (not display.startswith(":") or not display[1:].split(".", 1)[0].isdigit()
                or not os.path.exists("/tmp/.X11-unix/X" + display[1:].split(".", 1)[0])
                or not authority or not os.path.isfile(authority)
                or not os.access(authority, os.R_OK)
                or runtime != "/run/user/" + str(uid)
                or not os.path.isdir(runtime)
                or not bus.startswith("unix:")
                or not any(other_comm == "gdm-x-session" and other_uid == uid
                           for other_comm, other_uid, _, _ in processes)
                or not any(other_comm == "Xorg" and
                           (display.encode() in command or authority.encode() in command)
                           for other_comm, _, _, command in processes)):
            continue
        env = os.environ.copy()
        env.update(session)
        if verbose:
            print("SESSION_DISCOVERY=PASS COMM=" + comm +
                  " TYPE=x11 DISPLAY=" + display +
                  " UID=" + str(uid) + " XAUTHORITY=" + authority +
                  " XDG_RUNTIME_DIR=" + runtime + " DBUS_SESSION_BUS_ADDRESS=" + bus,
                  file=os.sys.stderr, flush=True)
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
                # Sparse RGB sampling distinguishes a real desktop surface
                # from the static X root background without a full-frame scan.
                nonblack = int(any(pixels[i] or pixels[i + 1] or pixels[i + 2]
                                   for i in range(0, len(pixels) - 2, 256)))
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
        if env.get("UAVF_CAPTURE_COMPOSITE_OVERLAY") == "1":
            try:
                composite_direct = ctypes.CDLL("libXcomposite.so.1")
                composite_direct.XCompositeGetOverlayWindow.argtypes = [
                    ctypes.c_void_p, ctypes.c_ulong]
                composite_direct.XCompositeGetOverlayWindow.restype = ctypes.c_ulong
                composite_direct.XCompositeReleaseOverlayWindow.argtypes = [
                    ctypes.c_void_p, ctypes.c_ulong]
                overlay_direct = composite_direct.XCompositeGetOverlayWindow(display, root)
                if overlay_direct:
                    try:
                        result = read_drawable(overlay_direct, width.value,
                                               height.value, "COMPOSITE_OVERLAY")
                        if result and result[3]:
                            if "X11_COMPOSITE_OVERLAY" not in _CAPTURE_BACKEND_LOGGED:
                                print("CAPTURE_BACKEND=X11_COMPOSITE_OVERLAY",
                                      file=os.sys.stderr, flush=True)
                                _CAPTURE_BACKEND_LOGGED.add("X11_COMPOSITE_OVERLAY")
                            return result[:3]
                        print("COMPOSITE_OVERLAY_NONBLACK=0",
                              file=os.sys.stderr, flush=True)
                    finally:
                        composite_direct.XCompositeReleaseOverlayWindow(display, root)
                else:
                    print("COMPOSITE_OVERLAY=UNAVAILABLE",
                          file=os.sys.stderr, flush=True)
            except OSError as error:
                print("COMPOSITE_OVERLAY_LIBRARY=UNAVAILABLE " + str(error),
                      file=os.sys.stderr, flush=True)
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


def launch_ubuntu_installer(env):
    """Launch only Canonical's fixed Live-session install/try entry point."""
    account = pwd.getpwnam("ubuntu")
    launch_env = env.copy()
    launch_env.update(HOME=account.pw_dir, USER=account.pw_name,
                      LOGNAME=account.pw_name,
                      BAMF_DESKTOP_FILE_HINT=(
                          "/var/lib/snapd/desktop/applications/"
                          "ubuntu-desktop-bootstrap_ubuntu-desktop-bootstrap.desktop"))
    process = subprocess.Popen(
        ["/snap/bin/ubuntu-desktop-bootstrap", "--try-or-install"],
        env=launch_env, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, close_fds=True, start_new_session=True,
        user=account.pw_uid, group=account.pw_gid,
        extra_groups=os.getgrouplist(account.pw_name, account.pw_gid))
    print("INPUT_INSTALLER_LAUNCH=SPAWNED PID=%d" % process.pid,
          file=os.sys.stderr, flush=True)
    return process.pid

def wavf_record(record_type, flags, width, height, sequence, payload):
    crc = zlib.crc32(payload) & 0xffffffff
    return struct.pack("<4sBBBBHHIIII", b"WAVF", 1, record_type, flags, 0,
                       width, height, sequence, len(payload), crc, 0) + payload


def wavf_damage_record(width, height, sequence, rectangles):
    """Encode (x, y, width, height, BGRA) updates as WAVF type-2 tiles."""
    payload = bytearray()
    for x, y, rect_width, rect_height, pixels in rectangles:
        if (x < 0 or y < 0 or rect_width <= 0 or rect_height <= 0
                or x + rect_width > width or y + rect_height > height
                or len(pixels) != rect_width * rect_height * 4):
            raise ValueError("DAMAGE_RECTANGLE_INVALID")
        compressed = zlib.compress(pixels, 1)
        data = compressed if len(compressed) < len(pixels) else pixels
        payload.extend(struct.pack("<HHHHI", x, y, rect_width,
                                   rect_height, len(data)))
        payload.extend(data)
    if not payload:
        return None
    return wavf_record(2, 0, width, height, sequence, payload)


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


class XRectangle(ctypes.Structure):
    _fields_ = [("x", ctypes.c_short), ("y", ctypes.c_short),
                ("width", ctypes.c_ushort), ("height", ctypes.c_ushort)]


class XShmSegmentInfo(ctypes.Structure):
    _fields_ = [("shmseg", ctypes.c_ulong), ("shmid", ctypes.c_int),
                ("shmaddr", ctypes.c_void_p), ("readOnly", ctypes.c_int)]


class XErrorEvent(ctypes.Structure):
    _fields_ = [("type", ctypes.c_int), ("display", ctypes.c_void_p),
                ("resourceid", ctypes.c_ulong), ("serial", ctypes.c_ulong),
                ("error_code", ctypes.c_ubyte),
                ("request_code", ctypes.c_ubyte),
                ("minor_code", ctypes.c_ubyte), ("pad", ctypes.c_ubyte)]


class X11DamageCapture:
    """Track root/top-level damage and emit visible root-pixel WAVF deltas."""
    def __init__(self, display_name, keyframe_interval=150):
        x11_name = ctypes.util.find_library("X11") or "libX11.so.6"
        damage_name = ctypes.util.find_library("Xdamage") or "libXdamage.so.1"
        fixes_name = ctypes.util.find_library("Xfixes") or "libXfixes.so.3"
        try:
            self.x11 = ctypes.CDLL(x11_name)
            self.damage = ctypes.CDLL(damage_name)
            self.fixes = ctypes.CDLL(fixes_name)
        except OSError as error:
            raise RuntimeError("X11_DAMAGE_EXTENSION_UNAVAILABLE=" + str(error))

        x11 = self.x11
        x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
        x11.XOpenDisplay.restype = ctypes.c_void_p
        x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
        x11.XDefaultRootWindow.restype = ctypes.c_ulong
        x11.XGetGeometry.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
            ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_int),
            ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_uint),
            ctypes.POINTER(ctypes.c_uint), ctypes.POINTER(ctypes.c_uint),
            ctypes.POINTER(ctypes.c_uint)]
        x11.XGetGeometry.restype = ctypes.c_int
        x11.XGetImage.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
            ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint,
            ctypes.c_ulong, ctypes.c_int]
        x11.XGetImage.restype = ctypes.POINTER(XImage)
        x11.XDestroyImage.argtypes = [ctypes.POINTER(XImage)]
        x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
        x11.XFree.argtypes = [ctypes.c_void_p]
        x11.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
        x11.XPending.argtypes = [ctypes.c_void_p]
        x11.XPending.restype = ctypes.c_int
        x11.XNextEvent.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        x11.XQueryTree.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
            ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.c_ulong),
            ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)),
            ctypes.POINTER(ctypes.c_uint)]
        x11.XQueryTree.restype = ctypes.c_int
        x11.XGetWindowAttributes.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong, ctypes.POINTER(XWindowAttributes)]
        x11.XGetWindowAttributes.restype = ctypes.c_int
        x11.XTranslateCoordinates.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong, ctypes.c_ulong, ctypes.c_int, ctypes.c_int,
            ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int),
            ctypes.POINTER(ctypes.c_ulong)]
        x11.XTranslateCoordinates.restype = ctypes.c_int
        x11.XDefaultScreen.argtypes = [ctypes.c_void_p]
        x11.XDefaultScreen.restype = ctypes.c_int
        x11.XDefaultVisual.argtypes = [ctypes.c_void_p, ctypes.c_int]
        x11.XDefaultVisual.restype = ctypes.c_void_p
        x11.XDefaultDepth.argtypes = [ctypes.c_void_p, ctypes.c_int]
        x11.XDefaultDepth.restype = ctypes.c_int

        self.damage.XDamageQueryExtension.argtypes = [ctypes.c_void_p,
            ctypes.POINTER(ctypes.c_int), ctypes.POINTER(ctypes.c_int)]
        self.damage.XDamageQueryExtension.restype = ctypes.c_int
        self.damage.XDamageCreate.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong, ctypes.c_int]
        self.damage.XDamageCreate.restype = ctypes.c_ulong
        self.damage.XDamageDestroy.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
        self.damage.XDamageSubtract.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong, ctypes.c_ulong, ctypes.c_ulong]
        self.fixes.XFixesCreateRegion.argtypes = [ctypes.c_void_p,
            ctypes.POINTER(XRectangle), ctypes.c_int]
        self.fixes.XFixesCreateRegion.restype = ctypes.c_ulong
        self.fixes.XFixesFetchRegion.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong, ctypes.POINTER(ctypes.c_int)]
        self.fixes.XFixesFetchRegion.restype = ctypes.POINTER(XRectangle)
        self.fixes.XFixesSetRegion.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong, ctypes.POINTER(XRectangle), ctypes.c_int]
        self.fixes.XFixesDestroyRegion.argtypes = [ctypes.c_void_p,
            ctypes.c_ulong]
        self.shm_ext = None

        if "XAUTHORITY" in os.environ:
            os.environ.pop("XAUTHORITY", None)
        self.display = x11.XOpenDisplay(display_name.encode())
        if not self.display:
            raise RuntimeError("X11_DAMAGE_OPEN_DISPLAY_FAILED=" + display_name)
        x11.XSetErrorHandler.argtypes = [ctypes.c_void_p]
        x11.XSetErrorHandler.restype = ctypes.c_void_p
        self.last_x_error = 0
        error_callback = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p,
                                          ctypes.c_void_p)
        def ignore_x_error(_display, event):
            if event:
                self.last_x_error = ctypes.cast(
                    event, ctypes.POINTER(XErrorEvent)).contents.error_code
            return 0
        self._x_error_callback = error_callback(ignore_x_error)
        x11.XSetErrorHandler(ctypes.cast(self._x_error_callback,
                                         ctypes.c_void_p))
        self.root = x11.XDefaultRootWindow(self.display)
        root_return = ctypes.c_ulong()
        root_x = ctypes.c_int()
        root_y = ctypes.c_int()
        root_width = ctypes.c_uint()
        root_height = ctypes.c_uint()
        border = ctypes.c_uint()
        depth = ctypes.c_uint()
        if not x11.XGetGeometry(self.display, self.root,
                ctypes.byref(root_return), ctypes.byref(root_x),
                ctypes.byref(root_y), ctypes.byref(root_width),
                ctypes.byref(root_height), ctypes.byref(border),
                ctypes.byref(depth)):
            self.close()
            raise RuntimeError("X11_DAMAGE_ROOT_GEOMETRY_FAILED")
        self.width, self.height = root_width.value, root_height.value
        if not (0 < self.width <= 4096 and 0 < self.height <= 4096):
            self.close()
            raise RuntimeError("X11_DAMAGE_ROOT_SIZE_INVALID")
        self.capture_drawable = self.root
        self.composite = None
        self.overlay_window = 0
        if os.environ.get("UAVF_XDAMAGE_SOURCE", "ROOT_TREE") == "COMPOSITE_OVERLAY":
            try:
                name = ctypes.util.find_library("Xcomposite") or "libXcomposite.so.1"
                self.composite = ctypes.CDLL(name)
                self.composite.XCompositeQueryExtension.argtypes = [
                    ctypes.c_void_p, ctypes.POINTER(ctypes.c_int),
                    ctypes.POINTER(ctypes.c_int)]
                self.composite.XCompositeQueryExtension.restype = ctypes.c_int
                self.composite.XCompositeGetOverlayWindow.argtypes = [
                    ctypes.c_void_p, ctypes.c_ulong]
                self.composite.XCompositeGetOverlayWindow.restype = ctypes.c_ulong
                self.composite.XCompositeReleaseOverlayWindow.argtypes = [
                    ctypes.c_void_p, ctypes.c_ulong]
                event_base = ctypes.c_int()
                error_base = ctypes.c_int()
                if not self.composite.XCompositeQueryExtension(
                        self.display, ctypes.byref(event_base),
                        ctypes.byref(error_base)):
                    raise RuntimeError("XCOMPOSITE_EXTENSION_NOT_PRESENT")
                self.overlay_window = int(self.composite.XCompositeGetOverlayWindow(
                    self.display, self.root))
                if not self.overlay_window:
                    raise RuntimeError("XCOMPOSITE_OVERLAY_WINDOW_UNAVAILABLE")
                self.capture_drawable = self.overlay_window
                x11.XSync(self.display, 0)
            except Exception as error:
                self.close()
                raise RuntimeError("X11_COMPOSITE_OVERLAY=" + str(error))
        event_base = ctypes.c_int()
        error_base = ctypes.c_int()
        if not self.damage.XDamageQueryExtension(self.display,
                ctypes.byref(event_base), ctypes.byref(error_base)):
            self.close()
            raise RuntimeError("X11_DAMAGE_EXTENSION_NOT_PRESENT")
        self.region = self.fixes.XFixesCreateRegion(self.display, None, 0)
        if not self.region:
            self.close()
            raise RuntimeError("X11_DAMAGE_REGION_CREATE_FAILED")
        self.damage_targets = {}
        self.last_target_refresh = 0.0
        self._refresh_damage_targets(force=True)
        if not self.damage_targets:
            self.close()
            raise RuntimeError("X11_DAMAGE_NO_TARGETS")
        self.keyframe_interval = max(1, keyframe_interval)
        self.captures = 0
        self.initialized = False
        self.shm_libc = None
        self.shm_info = None
        self.shm_image = None
        self.shm_attached = False
        if os.environ.get("UAVF_CAPTURE_X11_SHM", "0") == "1":
            self._attach_shm()
        # This private connection has no event consumer other than us. Drain
        # DamageNotify events after collecting each coalesced region so a
        # long-running session cannot accumulate an unbounded X event queue.
        self.event_buffer = ctypes.create_string_buffer(192)
        source = "COMPOSITE_OVERLAY" if self.overlay_window else "ROOT_TREE"
        print("CAPTURE_BACKEND=X11_DAMAGE_%s WIDTH=%d HEIGHT=%d TARGETS=%d" %
              (source, self.width, self.height, len(self.damage_targets)),
              file=os.sys.stderr, flush=True)
        if self.overlay_window:
            print("X11_DAMAGE_DRAWABLE=COMPOSITE_OVERLAY window_id=%d" %
                  self.overlay_window, file=os.sys.stderr, flush=True)

    def _refresh_damage_targets(self, force=False):
        now = time.monotonic()
        if not force and now - self.last_target_refresh < 1.0:
            return
        self.last_target_refresh = now
        actual_root = ctypes.c_ulong()
        parent = ctypes.c_ulong()
        children = ctypes.POINTER(ctypes.c_ulong)()
        count = ctypes.c_uint()
        if not self.x11.XQueryTree(self.display, self.root,
                ctypes.byref(actual_root), ctypes.byref(parent),
                ctypes.byref(children), ctypes.byref(count)):
            raise RuntimeError("X11_DAMAGE_QUERY_TREE_FAILED")
        if self.overlay_window:
            visible = {self.overlay_window}
            if children:
                self.x11.XFree(children)
        else:
            visible = {self.root}
            try:
                for index in range(min(count.value, 256)):
                    window = int(children[index])
                    attributes = XWindowAttributes()
                    if (self.x11.XGetWindowAttributes(self.display, window,
                            ctypes.byref(attributes)) and attributes.map_state == 2
                            and attributes.width > 0 and attributes.height > 0
                            and attributes.width <= 4096
                            and attributes.height <= 4096):
                        visible.add(window)
            finally:
                if children:
                    self.x11.XFree(children)
        previous = set(self.damage_targets)
        for window in previous - visible:
            self.damage.XDamageDestroy(self.display,
                                       self.damage_targets.pop(window))
        for window in visible - previous:
            damage_id = self.damage.XDamageCreate(self.display, window, 3)
            if damage_id:
                self.damage_targets[window] = damage_id
        self.x11.XSync(self.display, 0)
        if force or previous != visible:
            label = "COMPOSITE_OVERLAY" if self.overlay_window else "ROOT_AND_MAPPED_CHILDREN"
            print("X11_DAMAGE_TARGETS %s=%d" %
                  (label, len(self.damage_targets)), file=os.sys.stderr, flush=True)

    def _attach_shm(self):
        try:
            name = ctypes.util.find_library("Xext") or "libXext.so.6"
            self.shm_ext = ctypes.CDLL(name)
            self.shm_ext.XShmQueryExtension.argtypes = [ctypes.c_void_p]
            self.shm_ext.XShmQueryExtension.restype = ctypes.c_int
            if not self.shm_ext.XShmQueryExtension(self.display):
                raise RuntimeError("MIT_SHM_EXTENSION_NOT_PRESENT")
            self.shm_ext.XShmCreateImage.argtypes = [ctypes.c_void_p,
                ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_void_p,
                ctypes.POINTER(XShmSegmentInfo), ctypes.c_uint, ctypes.c_uint]
            self.shm_ext.XShmCreateImage.restype = ctypes.POINTER(XImage)
            self.shm_ext.XShmAttach.argtypes = [ctypes.c_void_p,
                ctypes.POINTER(XShmSegmentInfo)]
            self.shm_ext.XShmAttach.restype = ctypes.c_int
            self.shm_ext.XShmGetImage.argtypes = [ctypes.c_void_p,
                ctypes.c_ulong, ctypes.POINTER(XImage), ctypes.c_int,
                ctypes.c_int, ctypes.c_ulong]
            self.shm_ext.XShmGetImage.restype = ctypes.c_int
            self.shm_ext.XShmDetach.argtypes = [ctypes.c_void_p,
                ctypes.POINTER(XShmSegmentInfo)]

            libc = ctypes.CDLL(None)
            libc.shmget.argtypes = [ctypes.c_int, ctypes.c_size_t, ctypes.c_int]
            libc.shmget.restype = ctypes.c_int
            libc.shmat.argtypes = [ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
            libc.shmat.restype = ctypes.c_void_p
            libc.shmdt.argtypes = [ctypes.c_void_p]
            libc.shmctl.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_void_p]
            libc.shmctl.restype = ctypes.c_int
            self.shm_libc = libc
            screen = self.x11.XDefaultScreen(self.display)
            visual = self.x11.XDefaultVisual(self.display, screen)
            depth = self.x11.XDefaultDepth(self.display, screen)
            self.shm_info = XShmSegmentInfo()
            self.shm_info.shmid = libc.shmget(0,
                self.width * self.height * 4 + 4096, 0o1000 | 0o600)
            if self.shm_info.shmid < 0:
                raise RuntimeError("SYSV_SHMGET_FAILED")
            address = libc.shmat(self.shm_info.shmid, None, 0)
            if address in (None, ctypes.c_void_p(-1).value):
                raise RuntimeError("SYSV_SHMAT_FAILED")
            self.shm_info.shmaddr = address
            self.shm_info.readOnly = 0
            self.shm_image = self.shm_ext.XShmCreateImage(
                self.display, visual, depth, 2, address,
                ctypes.byref(self.shm_info), self.width, self.height)
            if not self.shm_image:
                raise RuntimeError("XSHM_CREATE_IMAGE_FAILED")
            image = self.shm_image.contents
            if image.bits_per_pixel != 32 or image.bytes_per_line * self.height > \
                    self.width * self.height * 4 + 4096:
                raise RuntimeError("XSHM_IMAGE_LAYOUT_UNSUPPORTED")
            self.shm_info.shmseg = 0
            if not self.shm_ext.XShmAttach(self.display,
                                            ctypes.byref(self.shm_info)):
                raise RuntimeError("XSHM_ATTACH_FAILED")
            self.shm_attached = True
            self.x11.XSync(self.display, 0)
            if self.last_x_error:
                raise RuntimeError("X11_MIT_SHM_ATTACH_REJECTED=%d" %
                                   self.last_x_error)
            print("X11_MIT_SHM=ENABLED STRIDE=%d" % image.bytes_per_line,
                  file=os.sys.stderr, flush=True)
        except Exception as error:
            self._detach_shm()
            print("X11_MIT_SHM=UNAVAILABLE REASON=" + str(error),
                  file=os.sys.stderr, flush=True)

    def _detach_shm(self):
        if self.shm_info is not None and self.shm_libc is not None:
            if self.shm_attached and self.display and self.shm_ext:
                self.shm_ext.XShmDetach(self.display,
                                        ctypes.byref(self.shm_info))
                self.x11.XSync(self.display, 0)
            if self.shm_image:
                self.x11.XDestroyImage(self.shm_image)
                self.shm_image = None
            if self.shm_info.shmaddr not in (None, ctypes.c_void_p(-1).value):
                self.shm_libc.shmdt(self.shm_info.shmaddr)
            if self.shm_info.shmid >= 0:
                self.shm_libc.shmctl(self.shm_info.shmid, 0, None)
        self.shm_info = None
        self.shm_attached = False
        self.shm_libc = None

    def close(self):
        if getattr(self, "display", None):
            self._detach_shm()
            for damage_id in getattr(self, "damage_targets", {}).values():
                self.damage.XDamageDestroy(self.display, damage_id)
            if hasattr(self, "damage_targets"):
                self.damage_targets.clear()
            if getattr(self, "region", 0):
                self.fixes.XFixesDestroyRegion(self.display, self.region)
                self.region = 0
            if getattr(self, "overlay_window", 0) and getattr(self, "composite", None):
                self.composite.XCompositeReleaseOverlayWindow(
                    self.display, self.overlay_window)
                self.overlay_window = 0
            self.x11.XCloseDisplay(self.display)
            self.display = None

    def _refresh_shm(self):
        if self.shm_image and not self.shm_ext.XShmGetImage(
                self.display, self.capture_drawable, self.shm_image,
                0, 0, 0xffffffff):
            raise RuntimeError("XSHM_GET_IMAGE_FAILED")

    def _read_rect(self, x, y, width, height):
        if self.shm_image:
            image = self.shm_image.contents
            base = ctypes.cast(image.data, ctypes.c_void_p).value
            row_bytes = width * 4
            start = y * image.bytes_per_line + x * 4
            if x == 0 and width == self.width:
                pixels = bytearray(ctypes.string_at(
                    base + start, image.bytes_per_line * height))
                if image.bytes_per_line != row_bytes:
                    pixels = bytearray().join(
                        pixels[row * image.bytes_per_line:
                               row * image.bytes_per_line + row_bytes]
                        for row in range(height))
            else:
                pixels = bytearray().join(
                    ctypes.string_at(base + (y + row) * image.bytes_per_line + x * 4,
                                     row_bytes)
                    for row in range(height))
            pixels[3::4] = b"\xff" * (width * height)
            return pixels
        image = self.x11.XGetImage(self.display, self.capture_drawable, x, y,
                                   width, height, 0xffffffff, 2)
        if not image:
            return None
        try:
            info = image.contents
            if info.bits_per_pixel != 32 or not info.data:
                raise RuntimeError("X11_DAMAGE_UNSUPPORTED_PIXEL_FORMAT")
            row_bytes = width * 4
            if info.bytes_per_line == row_bytes:
                pixels = bytearray(ctypes.string_at(info.data, row_bytes * height))
            else:
                raw = ctypes.string_at(info.data, info.bytes_per_line * height)
                pixels = bytearray().join(
                    raw[row * info.bytes_per_line:row * info.bytes_per_line + row_bytes]
                    for row in range(height))
            pixels[3::4] = b"\xff" * (width * height)
            return pixels
        finally:
            self.x11.XDestroyImage(image)

    def capture(self, sequence):
        self.captures += 1
        keyframe = (not self.initialized or
                    self.captures % self.keyframe_interval == 0)
        self._refresh_damage_targets()
        rects = []
        for window, damage_id in tuple(self.damage_targets.items()):
            self.fixes.XFixesSetRegion(self.display, self.region, None, 0)
            self.damage.XDamageSubtract(self.display, damage_id, 0, self.region)
            count = ctypes.c_int()
            rectangles = self.fixes.XFixesFetchRegion(
                self.display, self.region, ctypes.byref(count))
            if count.value <= 0 or not rectangles:
                if rectangles:
                    self.x11.XFree(rectangles)
                continue
            try:
                for index in range(min(count.value, 4096)):
                    rect = rectangles[index]
                    if window == self.root:
                        root_x, root_y = rect.x, rect.y
                    else:
                        translated_x = ctypes.c_int()
                        translated_y = ctypes.c_int()
                        child = ctypes.c_ulong()
                        if not self.x11.XTranslateCoordinates(self.display,
                                window, self.root, rect.x, rect.y,
                                ctypes.byref(translated_x),
                                ctypes.byref(translated_y), ctypes.byref(child)):
                            continue
                        root_x, root_y = translated_x.value, translated_y.value
                    x, y = max(0, root_x), max(0, root_y)
                    right = min(self.width, root_x + rect.width)
                    bottom = min(self.height, root_y + rect.height)
                    if right > x and bottom > y:
                        rects.append((x, y, right - x, bottom - y))
            finally:
                self.x11.XFree(rectangles)
        if keyframe:
            self._refresh_shm()
            pixels = self._read_rect(0, 0, self.width, self.height)
            if pixels is None:
                raise RuntimeError("X11_DAMAGE_KEYFRAME_READ_FAILED")
            self.initialized = True
            self._drain_events()
            return wavf_record(1, 1, self.width, self.height, sequence,
                               zlib.compress(pixels, 1))

        self._drain_events()
        if not rects:
            return None
        rects = list(dict.fromkeys(rects))
        area = sum(rect[2] * rect[3] for rect in rects)
        if not rects:
            return None
        if self.shm_image:
            self._refresh_shm()
        # XFixes returns a non-overlapping region. For highly fragmented or
        # almost-full-screen damage, one bounding capture costs less than many
        # XGetImage round-trips and rectangle headers.
        if len(rects) > 128 or area >= self.width * self.height * 3 // 5:
            left = min(rect[0] for rect in rects)
            top = min(rect[1] for rect in rects)
            right = max(rect[0] + rect[2] for rect in rects)
            bottom = max(rect[1] + rect[3] for rect in rects)
            rects = [(left, top, right - left, bottom - top)]
        dirty_rectangles = []
        total_pixels = 0
        for x, y, width, height in rects:
            pixels = self._read_rect(x, y, width, height)
            if pixels is None:
                continue
            dirty_rectangles.append((x, y, width, height, pixels))
            total_pixels += width * height
        if not dirty_rectangles:
            return None
        if self.captures <= 4 or self.captures % 150 == 0:
            print("X11_DAMAGE_UPDATE rects=%d pixels=%d coverage=%.1f%%" %
                  (len(rects), total_pixels,
                   100.0 * total_pixels / (self.width * self.height)),
                  file=os.sys.stderr, flush=True)
        return wavf_damage_record(self.width, self.height, sequence,
                                  dirty_rectangles)

    def _drain_events(self):
        pending = min(self.x11.XPending(self.display), 4096)
        for _ in range(pending):
            self.x11.XNextEvent(self.display, self.event_buffer)


class RfbIncrementalCapture:
    """Translate the shared GDM desktop's rectangles into the WAVF stream."""
    def __init__(self, address=("127.0.0.1", 5901), continuous_updates=None):
        self.sock = socket.create_connection(address, timeout=2)
        self.sock.settimeout(8)
        if continuous_updates is None:
            continuous_updates = os.environ.get(
                "UAVF_RFB_CONTINUOUS_UPDATES", "1").lower() not in (
                    "0", "false", "no", "off")
        self.continuous_updates = bool(continuous_updates)
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
            if 1 not in types:
                raise RuntimeError("RFB_SECURITY_NONE_UNAVAILABLE")
            self.sock.sendall(b"\x01")
            if struct.unpack(">I", self._read(4))[0] != 0:
                raise RuntimeError("RFB_SECURITY_REJECTED")
            self.sock.sendall(b"\x01")
            header = self._read(24)
            self.width, self.height = struct.unpack_from(">HH", header)
            if not (0 < self.width <= 4096 and 0 < self.height <= 4096
                    and self.width * self.height * 4 <= 16 * 1024 * 1024):
                raise RuntimeError("RFB_DIMENSIONS_INVALID")
            name_length = struct.unpack_from(">I", header, 20)[0]
            if name_length > 4096:
                raise RuntimeError("RFB_NAME_TOO_LONG")
            self._read(name_length)
            # BGRX little-endian, raw rectangles, guest loopback only.
            pixel_format = struct.pack(">BBBBHHHBBBxxx", 32, 24, 0, 1,
                                       255, 255, 255, 16, 8, 0)
            self.sock.sendall(b"\x00\x00\x00\x00" + pixel_format)
            # Declare support before sending EnableContinuousUpdates (message
            # 150). TigerVNC rejects that request unless both Fence (-312)
            # and ContinuousUpdates (-313) were advertised in SetEncodings.
            self.sock.sendall(struct.pack(">BBHiii", 2, 0, 3, 0, -312, -313))
            self.pixels = bytearray(self.width * self.height * 4)
            self.initialized = False
            self.updates = 0
            self.pending = False
            self.continuous_enabled = False
            self.force_keyframe_next = False
            print("CAPTURE_BACKEND=RFB_SHARE_EXISTING_DISPLAY WIDTH=%d HEIGHT=%d" %
                  (self.width, self.height), file=os.sys.stderr, flush=True)
            print("RFB_CONTINUOUS_UPDATES=%s" %
                  ("AVAILABLE" if self.continuous_updates else "DISABLED"),
                  file=os.sys.stderr, flush=True)
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
            if not self.continuous_enabled:
                # ContinuousUpdates is enabled only after the initial full
                # image has arrived. In pull mode, periodically request a
                # keyframe; in push mode TCP preserves the ordered deltas and
                # a reconnect obtains a fresh full image.
                self.pending_keyframe = (not self.initialized
                                         or self.force_keyframe_next
                                         or (not self.continuous_updates
                                             and (self.updates + 1) % 150 == 0))
                self.force_keyframe_next = False
                incremental = int(self.initialized and not self.pending_keyframe)
                self.sock.sendall(struct.pack(">BBHHHH", 3, incremental,
                                              0, 0, self.width, self.height))
                self.pending = True
        try:
            kind = self._read(1)[0]
            while kind in (150, 248):
                # TigerVNC acknowledges SetEncodings pseudo-extensions with
                # EndOfContinuousUpdates (150) and may send a Fence response
                # (248) before the first framebuffer update.
                if kind == 248:
                    # Both directions have three padding bytes. Fence
                    # requests are RTT/congestion pings, not acknowledgements
                    # to discard: TigerVNC stops updates without the pong.
                    fence = self._read(8)
                    flags = struct.unpack_from(">I", fence, 3)[0]
                    fence_length = fence[7]
                    if fence_length > 64:
                        raise RuntimeError("RFB_FENCE_PAYLOAD_TOO_LARGE")
                    payload = self._read(fence_length) if fence_length else b""
                    if flags & 0x80000000:
                        # Processing is synchronous: honor BlockBefore/After,
                        # clear Request and unsupported flags, echo payload.
                        self.sock.sendall(struct.pack(">B3xIB", 248, flags & 3,
                                                      fence_length) + payload)
                        if "RFB_FENCE_REPLY" not in _CAPTURE_BACKEND_LOGGED:
                            print("RFB_FENCE_REPLY=PASS", file=os.sys.stderr, flush=True)
                            _CAPTURE_BACKEND_LOGGED.add("RFB_FENCE_REPLY")
                kind = self._read(1)[0]
        except TimeoutError:
            # Some bundled Xvnc builds acknowledge ContinuousUpdates but do
            # not reliably push the first post-login damage. Do not leave the
            # one outstanding request latched forever: fall back to a fresh
            # non-incremental keyframe, then continue in pull mode.
            if self.continuous_enabled:
                self.sock.sendall(struct.pack(">BBHHHH", 150, 0, 0, 0,
                                              self.width, self.height))
                self.continuous_enabled = False
                self.continuous_updates = False
            self.pending = False
            self.force_keyframe_next = True
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
            if self.pending_keyframe:
                row_bytes = w * 4
                if x == 0 and w == self.width:
                    start = y * self.width * 4
                    self.pixels[start:start + len(raw)] = raw
                else:
                    for row in range(h):
                        start = ((y + row) * self.width + x) * 4
                        self.pixels[start:start + row_bytes] = raw[row * row_bytes:(row + 1) * row_bytes]
            if len(raw) < 4096:
                payload = raw
            else:
                compressed = zlib.compress(raw, 1)
                payload = compressed if len(compressed) < len(raw) else raw
            changed.extend(struct.pack("<HHHHI", x, y, w, h, len(payload)))
            changed.extend(payload)
            changed_area += w * h
        self.updates += 1
        if self.updates <= 3 or self.updates % 150 == 0:
            print("RFB_UPDATE rects=%d pixels=%d" % (count, changed_area),
                  file=os.sys.stderr, flush=True)
        if self.pending_keyframe:
            self.initialized = True
            packet = wavf_record(1, 1, self.width, self.height, sequence,
                                 zlib.compress(self.pixels, 1))
            self.pending_keyframe = False
            if self.continuous_updates and not self.continuous_enabled:
                # RFB message 150 is TigerVNC's EnableContinuousUpdates
                # extension. The server now pushes framebuffer updates as
                # damage occurs, avoiding a request/response round trip per
                # frame. This exact client/server pair is bundled by U-AVF.
                self.sock.sendall(struct.pack(">BBHHHH", 150, 1, 0, 0,
                                              self.width, self.height))
                self.continuous_enabled = True
                print("RFB_CONTINUOUS_UPDATES=ENABLED WIDTH=%d HEIGHT=%d" %
                      (self.width, self.height), file=os.sys.stderr, flush=True)
            return packet
        if changed:
            return wavf_record(2, 0, self.width, self.height, sequence, changed)
        return None


class X11MotionPattern:
    """Continuously changing X drawable for an end-to-end FPS measurement."""
    def __init__(self, display_name):
        self.x11 = ctypes.CDLL(ctypes.util.find_library("X11") or "libX11.so.6")
        x11 = self.x11
        x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
        x11.XOpenDisplay.restype = ctypes.c_void_p
        self.display = x11.XOpenDisplay(display_name.encode())
        if not self.display:
            raise RuntimeError("MOTION_PATTERN_DISPLAY_UNAVAILABLE")
        self.xtst = ctypes.CDLL(ctypes.util.find_library("Xtst") or "libXtst.so.6")
        self.xtst.XTestFakeMotionEvent.argtypes = [ctypes.c_void_p,
            ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_ulong]
        self.xtst.XTestFakeMotionEvent.restype = ctypes.c_int
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
        x11.XStoreName.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                   ctypes.c_char_p]
        x11.XSetForeground.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_ulong]
        x11.XFillRectangle.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
            ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint]
        x11.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
        root = x11.XDefaultRootWindow(self.display)
        self.width, self.height = 512, 128
        self.window = x11.XCreateSimpleWindow(self.display, root, 80, 80,
            self.width, self.height, 0, 0, 0xffffff)
        if not self.window:
            raise RuntimeError("MOTION_PATTERN_WINDOW_FAILED")
        self.gc = x11.XCreateGC(self.display, self.window, 0, None)
        if not self.gc:
            raise RuntimeError("MOTION_PATTERN_GC_FAILED")
        # Use a normal WM-managed window. Override-redirect bypasses Mutter and
        # can update an X drawable without changing the composed Xvnc screen.
        x11.XStoreName(self.display, self.window,
                       b"U-AVF frame-rate diagnostic")
        x11.XMapRaised(self.display, self.window)
        x11.XSetForeground(self.display, self.gc, 0xffffff)
        x11.XFillRectangle(self.display, self.window, self.gc, 0, 0,
                           self.width, self.height)
        x11.XSync(self.display, 0)
        print("MOTION_PATTERN_X11=PASS WIDTH=%d HEIGHT=%d WM_MANAGED=1" %
              (self.width, self.height),
              file=os.sys.stderr,
              flush=True)

    def step(self, sequence):
        x11 = self.x11
        x = (sequence * 19) % max(1, self.width - 128)
        y = self.height // 2 - 48
        if getattr(self, "previous_xy", None) is not None:
            old_x, old_y = self.previous_xy
            x11.XSetForeground(self.display, self.gc, 0xffffff)
            x11.XFillRectangle(self.display, self.window, self.gc,
                                old_x, old_y, 128, 96)
        x11.XSetForeground(self.display, self.gc, 0x20a0ff)
        x11.XFillRectangle(self.display, self.window, self.gc,
                            x, y, 128, 96)
        self.previous_xy = (x, y)
        self.xtst.XTestFakeMotionEvent(self.display, 0,
                                       (sequence * 17) % 1920,
                                       (sequence * 11) % 1080, 0)
        x11.XSync(self.display, 0)

    def animate(self, target_fps):
        """Keep producing visible X damage while RFB waits for pushed updates."""
        interval = 1.0 / max(1, target_fps)
        deadline = time.monotonic()
        sequence = 0
        stats_started = deadline
        stats_frames = 0
        while True:
            sequence += 1
            self.step(sequence)
            stats_frames += 1
            deadline += interval
            now = time.monotonic()
            if now - stats_started >= 5.0:
                print("MOTION_PATTERN_FPS=%.1f" %
                      (stats_frames / (now - stats_started)),
                      file=os.sys.stderr, flush=True)
                stats_started = now
                stats_frames = 0
            if deadline < now - interval:
                deadline = now
            time.sleep(max(0.0, deadline - time.monotonic()))


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
            env = find_display()
            if env and env.get("DISPLAY"):
                if "XAUTHORITY" in env:
                    os.environ["XAUTHORITY"] = env["XAUTHORITY"]
                else:
                    os.environ.pop("XAUTHORITY", None)
                display = x11.XOpenDisplay(env["DISPLAY"].encode())
                if display:
                    print("INPUT_DISPLAY=" + env["DISPLAY"], file=os.sys.stderr, flush=True)
                    break
            time.sleep(1)
        if not display:
            raise RuntimeError("INPUT_DISPLAY_UNAVAILABLE")
        values = [ctypes.c_int() for _ in range(4)]
        if not xtst.XTestQueryExtension(display, *(ctypes.byref(v) for v in values)):
            raise RuntimeError("INPUT_XTEST_EXTENSION_UNAVAILABLE")
        print("INPUT_XTEST_READY", file=os.sys.stderr, flush=True)
        installer_launch_requested = False
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
            elif kind == 5 and value == 1 and x == 1 and y == 0:
                if installer_launch_requested:
                    print("INPUT_INSTALLER_LAUNCH=ALREADY_REQUESTED",
                          file=os.sys.stderr, flush=True)
                    result = 1
                else:
                    try:
                        launch_ubuntu_installer(env)
                        installer_launch_requested = True
                        result = 1
                    except Exception as error:
                        print("INPUT_INSTALLER_LAUNCH=FAILED " + str(error),
                              file=os.sys.stderr, flush=True)
                        result = 0
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
    try:
        target_fps = int(os.environ.get("UAVF_FRAME_TARGET_FPS", "60"))
    except ValueError:
        target_fps = 60
    target_fps = max(30, min(60, target_fps))
    motion_pattern_requested = (
        os.environ.get("UAVF_FRAME_BENCHMARK_MOTION", "0") == "1")
    direct_x11_capture = (
        os.environ.get("UAVF_CAPTURE_DIRECT_X11", "0") == "1")
    x11_damage_capture = (
        direct_x11_capture and
        os.environ.get("UAVF_CAPTURE_X11_DAMAGE", "0") == "1")
    motion_pattern = None
    direct_encoder = (DeltaEncoder() if direct_x11_capture and
                      not x11_damage_capture else None)
    damage_capture = None
    overlay_capture = (os.environ.get("UAVF_XDAMAGE_SOURCE", "ROOT_TREE") ==
                       "COMPOSITE_OVERLAY")
    capture_name = ("X11_XDAMAGE_COMPOSITE_OVERLAY" if
                    x11_damage_capture and overlay_capture else
                    "X11_XDAMAGE" if x11_damage_capture else
                    "X11_XGETIMAGE_DIRECT" if direct_x11_capture
                    else "X11_RFB_INCREMENTAL")
    print("FRAME_TARGET_FPS=%d CAPTURE=%s" % (target_fps, capture_name),
          file=os.sys.stderr, flush=True)
    if direct_x11_capture:
        print("DIRECT_X11_CAPTURE=ENABLED ACTIVE_SESSION_DISPLAY=DETECTED",
              file=os.sys.stderr, flush=True)
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
            if direct_x11_capture:
                env = find_display(sequence == 1)
                if env is None or not env.get("DISPLAY"):
                    raise RuntimeError("DISPLAY_RUNTIME_NOT_FOUND")
                if motion_pattern_requested and motion_pattern is None:
                    motion_pattern = X11MotionPattern(env["DISPLAY"])
                    threading.Thread(target=motion_pattern.animate,
                                     args=(target_fps,), daemon=True,
                                     name="uavf-frame-benchmark-motion").start()
                    print("FRAME_BENCHMARK_MOTION=ENABLED",
                          file=os.sys.stderr, flush=True)
                width, height, pixels = capture_x11(env)
                packet = direct_encoder.encode(width, height, pixels, sequence)
                captured = time.monotonic()
                computed = time.monotonic()
            else:
                if rfb is None:
                    env = find_display(sequence == 1)
                    if (env is None or not env.get("DISPLAY")
                            or not env.get("XAUTHORITY")
                            or not os.path.isfile(env["XAUTHORITY"])):
                        raise RuntimeError("AUTHENTICATED_DISPLAY_NOT_FOUND")
                    rfb = RfbIncrementalCapture()
                if motion_pattern_requested and motion_pattern is None:
                    motion_pattern = X11MotionPattern(env["DISPLAY"])
                    threading.Thread(target=motion_pattern.animate,
                                     args=(target_fps,), daemon=True,
                                     name="uavf-frame-benchmark-motion").start()
                    print("FRAME_BENCHMARK_MOTION=ENABLED",
                          file=os.sys.stderr, flush=True)
                packet = rfb.next_packet(sequence)
                captured = time.monotonic()
                computed = captured
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
