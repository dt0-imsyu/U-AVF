"""Replace only the frame bridge in a verified P7-family hybrid initrd."""

import argparse
import hashlib
import importlib.machinery
import re
import subprocess
from pathlib import Path

from pyfatfs.PyFatFS import PyFatFS


# The live init-bottom hook copies this init-premount helper to /run and
# systemd executes that copy.  The similarly named /usr/local copy is only a
# fallback and is not the active source in the P7 Live profile.
BRIDGE_PATH = "scripts/init-premount/winavf-frame-bridge.py"
XVNC_HOOK_PATH = "scripts/init-bottom/99-winavf-vsock-systemd"


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


def parse_records(data):
    pos = 0
    records = []
    contents = {}
    while data[pos:pos + 6] == b"070701":
        header = data[pos:pos + 110]
        namesize = int(header[94:102], 16)
        filesize = int(header[54:62], 16)
        name_start = pos + 110
        name_raw = data[name_start:name_start + namesize]
        name = name_raw[:-1].decode("utf-8")
        content_start = (name_start + namesize + 3) & ~3
        content_end = content_start + filesize
        payload = data[content_start:content_end]
        records.append((header, name_raw, payload))
        contents[name] = payload
        pos = (content_end + 3) & ~3
        if name == "TRAILER!!!":
            return records, contents, pos
    raise RuntimeError("MAIN_CPIO_TRAILER_MISSING")


def encode_records(records, trailing):
    output = bytearray()
    for header, name, payload in records:
        updated = bytearray(header)
        updated[54:62] = f"{len(payload):08x}".encode("ascii")
        output.extend(updated)
        output.extend(name)
        output.extend(b"\0" * ((-len(output)) & 3))
        output.extend(payload)
        output.extend(b"\0" * ((-len(output)) & 3))
    output.extend(trailing)
    return bytes(output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--prefix", required=True, type=Path)
    parser.add_argument("--expected-prefix-sha", required=True)
    parser.add_argument("--bridge", required=True, type=Path)
    parser.add_argument("--target-fps", type=int, default=120,
                        help="diagnostic frame cap embedded in this candidate")
    parser.add_argument("--x11-damage", action="store_true",
                        help="diagnostic: capture XDamage regions from Xvnc")
    parser.add_argument("--no-motion-pattern", action="store_true",
                        help="do not create the bridge's synthetic moving window")
    parser.add_argument("--x11-composite-overlay", action="store_true",
                        help="capture XDamage on XComposite's screen overlay drawable")
    parser.add_argument("--zstd", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit("REFUSING_TO_OVERWRITE_OUTPUT")
    if args.x11_composite_overlay and not args.x11_damage:
        raise SystemExit("X11_COMPOSITE_OVERLAY_REQUIRES_XDAMAGE")
    raw_prefix = args.prefix.read_bytes()
    if len(raw_prefix) != 128 * 1024 * 1024 or sha(raw_prefix) != args.expected_prefix_sha.upper():
        raise SystemExit("BASELINE_PREFIX_IDENTITY_MISMATCH")
    fs = PyFatFS(str(args.prefix), offset=1024 * 1024, preserve_case=True)
    try:
        with fs.openbin("/CASPER/INITRD", "r") as source:
            original_initrd = source.read()
    finally:
        fs.close()
    helper_path = Path(__file__).parents[1] / "generic-ubuntu" / "compare-p7-clean-p9.py"
    helper = importlib.machinery.SourceFileLoader("cpio_compare", str(helper_path)).load_module()
    early = helper.early_end(original_initrd)
    decoded = subprocess.run([str(args.zstd), "-d", "-q", "-c"],
                             input=original_initrd[early:], capture_output=True,
                             check=True).stdout
    records, before, trailer_end = parse_records(decoded)
    if BRIDGE_PATH not in before:
        raise SystemExit("BASELINE_FRAME_BRIDGE_MISSING")
    replacement = args.bridge.read_bytes()
    if args.target_fps < 30 or args.target_fps > 240:
        raise SystemExit("TARGET_FPS_OUT_OF_RANGE")
    try:
        bridge_text = replacement.decode("utf-8")
    except UnicodeDecodeError as error:
        raise SystemExit("BRIDGE_NOT_UTF8=" + str(error))
    default_marker = 'os.environ.get("UAVF_FRAME_TARGET_FPS", "60")'
    clamp_marker = "target_fps = max(30, min(60, target_fps))"
    if bridge_text.count(default_marker) != 1 or bridge_text.count(clamp_marker) != 1:
        raise SystemExit("UNEXPECTED_FRAME_CAP_IMPLEMENTATION")
    bridge_text = bridge_text.replace(
        default_marker,
        'os.environ.get("UAVF_FRAME_TARGET_FPS", "%d")' % args.target_fps)
    bridge_text = bridge_text.replace(
        clamp_marker,
        "target_fps = max(30, min(%d, target_fps))" % args.target_fps)
    motion_marker = 'os.environ.get("UAVF_FRAME_BENCHMARK_MOTION", "0")'
    if bridge_text.count(motion_marker) != 1:
        raise SystemExit("UNEXPECTED_BENCHMARK_MOTION_SWITCH")
    if args.target_fps > 60 and not args.no_motion_pattern:
        bridge_text = bridge_text.replace(
            motion_marker, 'os.environ.get("UAVF_FRAME_BENCHMARK_MOTION", "1")')
    replacement = bridge_text.encode("utf-8")
    if before[BRIDGE_PATH] == replacement:
        raise SystemExit("FRAME_BRIDGE_ALREADY_IDENTICAL")
    hook = before.get(XVNC_HOOK_PATH)
    if hook is None:
        raise SystemExit("XVNC_INIT_HOOK_MISSING")
    try:
        hook_text = hook.decode("utf-8")
    except UnicodeDecodeError as error:
        raise SystemExit("XVNC_HOOK_NOT_UTF8=" + str(error))
    xserver_lines = [line for line in hook_text.splitlines(keepends=True)
                     if line.startswith("/usr/local/sbin/winavf-Xtigervnc :1 $xvnc_render_args")
                     and "-geometry 1920x1080" in line]
    if len(xserver_lines) != 1:
        raise SystemExit("UNEXPECTED_XVNC_LAUNCH_LINE")
    xserver_line = xserver_lines[0]
    clean_xserver_line = re.sub(r" -FrameRate \d+", "", xserver_line)
    desired_xserver_line = clean_xserver_line.replace(
        " -geometry 1920x1080",
        " -FrameRate %d -geometry 1920x1080" % args.target_fps, 1)
    hook_replacement = hook_text.replace(xserver_line, desired_xserver_line, 1)
    frame_exec = "ExecStart=/usr/bin/python3 /run/winavf-frame-bridge.py"
    if hook_replacement.count(frame_exec) != 1:
        raise SystemExit("UNEXPECTED_FRAME_BRIDGE_EXEC_LINE")
    if args.x11_damage:
        # A controlled diagnostic: retain Xvnc/GNOME and the existing WAVF
        # transport, but read only regions reported by XDamage.
        capture_env = ("Environment=UAVF_CAPTURE_DIRECT_X11=1\n"
                       "Environment=UAVF_CAPTURE_X11_DAMAGE=1\n"
                       "Environment=UAVF_CAPTURE_X11_SHM=1\n")
        if args.x11_composite_overlay:
            capture_env += "Environment=UAVF_XDAMAGE_SOURCE=COMPOSITE_OVERLAY\n"
        if not args.no_motion_pattern:
            capture_env += "Environment=UAVF_FRAME_BENCHMARK_MOTION=1\n"
        # Source prefixes may already carry the diagnostic environment. Replace
        # it instead of accumulating duplicate systemd Environment assignments.
        for variable in ("UAVF_CAPTURE_DIRECT_X11", "UAVF_CAPTURE_X11_DAMAGE",
                        "UAVF_CAPTURE_X11_SHM", "UAVF_XDAMAGE_SOURCE",
                        "UAVF_FRAME_BENCHMARK_MOTION"):
            hook_replacement = re.sub(
                r"(?m)^Environment=%s=[^\r\n]*\r?\n" % variable,
                "", hook_replacement)
    else:
        # Keep the benchmark on Xvnc's real RFB framebuffer. Direct XGetImage
        # can select a static root/overlay backing layer on some sessions.
        capture_env = "Environment=UAVF_RFB_CONTINUOUS_UPDATES=0\n"
    hook_replacement = hook_replacement.replace(frame_exec,
                                                 capture_env + frame_exec)
    hook_replacement = hook_replacement.encode("utf-8")
    replacements = {BRIDGE_PATH: replacement, XVNC_HOOK_PATH: hook_replacement}
    records = [(header, name, replacements.get(name[:-1].decode("utf-8"), payload))
               for header, name, payload in records]
    rebuilt_cpio = encode_records(records, decoded[trailer_end:])
    compressed = subprocess.run([str(args.zstd), "-1", "-q", "-c"],
                                input=rebuilt_cpio, capture_output=True,
                                check=True).stdout
    candidate = original_initrd[:early] + compressed
    if candidate[:early] != original_initrd[:early]:
        raise SystemExit("EARLY_CPIO_CHANGED")
    candidate_decoded = subprocess.run([str(args.zstd), "-d", "-q", "-c"],
                                       input=candidate[early:], capture_output=True,
                                       check=True).stdout
    _, after, _ = parse_records(candidate_decoded)
    if set(before) != set(after):
        raise SystemExit("CPIO_FILE_LIST_CHANGED")
    changed = sorted(name for name in before if before[name] != after[name])
    expected_changed = sorted(replacements)
    if changed != expected_changed or any(after[name] != value
                                           for name, value in replacements.items()):
        raise SystemExit("UNEXPECTED_CPIO_DELTA=" + repr(changed))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("xb") as output:
        output.write(candidate)
    print("RESULT=PASS")
    print("BASELINE_PREFIX_SHA256=" + sha(raw_prefix))
    print("BASELINE_INITRD_SHA256=" + sha(original_initrd))
    print("OUTPUT_INITRD_BYTES=" + str(len(candidate)))
    print("OUTPUT_INITRD_SHA256=" + sha(candidate))
    print("EARLY_CPIO_BYTES=" + str(early))
    print("EARLY_CPIO_BYTE_IDENTICAL=PASS")
    print("DIFFERING_CPIO_FILES=" + ",".join(changed))
    print("BRIDGE_SHA256=" + sha(replacement))
    print("OUTPUT=" + str(args.output))


if __name__ == "__main__":
    main()
