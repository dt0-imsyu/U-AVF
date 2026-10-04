#!/usr/bin/python3
"""UAVF safe-JIT v1: scoped compatibility policy, not VM CPU masking."""
import ctypes, json, os, pathlib, platform, shutil, sys

POLICY = 'llvm20-nosve-v1'

def affected():
    if platform.machine() != 'aarch64':
        return False
    libc = ctypes.CDLL(None)
    libc.getauxval.argtypes = [ctypes.c_ulong]
    libc.getauxval.restype = ctypes.c_ulong
    if libc.getauxval(16) & (1 << 22):
        return False
    try:
        lib = ctypes.CDLL('libLLVM.so.20.1')
        lib.LLVMGetHostCPUName.restype = ctypes.c_void_p
        ptr = lib.LLVMGetHostCPUName()
        name = ctypes.string_at(ptr).decode()
        lib.LLVMDisposeMessage.argtypes = [ctypes.c_void_p]
        lib.LLVMDisposeMessage(ptr)
        return name == 'cortex-x925'
    except (OSError, AttributeError):
        return False

def environment(app):
    env = dict(os.environ)
    if not affected():
        return env
    home = pathlib.Path.home()
    # Each Snap has its own allowed namespace; never point it at another Snap.
    base = home / 'snap/snap-store/common/uavf-jit' if app == 'snap-store' else home / '.local/state/uavf/jit'
    base.mkdir(parents=True, exist_ok=True)
    flags = next(line for line in pathlib.Path('/proc/cpuinfo').read_text().splitlines()
                 if line.startswith('Features'))
    fixture = base / (POLICY + '.cpuinfo')
    contents = 'processor : 0\n' + flags + '\nCPU architecture : 8\n'
    if not fixture.exists() or fixture.read_text() != contents:
        tmp = fixture.with_suffix('.tmp.' + str(os.getpid()))
        tmp.write_text(contents); tmp.chmod(0o600); tmp.replace(fixture)
    cache = base / (POLICY + '-shader-cache')
    cache.mkdir(exist_ok=True)
    env['LLVM_CPUINFO'] = str(fixture)
    # Fresh versioned namespace: keep caches enabled, retain all old user data.
    env['MESA_SHADER_CACHE_DIR'] = str(cache)
    return env

def setup_desktop():
    if not affected():
        print('UAVF_SAFE_JIT=NOT_REQUIRED'); return
    source = pathlib.Path('/var/lib/snapd/desktop/applications/snap-store_snap-store.desktop')
    if not source.is_file():
        print('UAVF_SAFE_JIT=SNAP_STORE_NOT_INSTALLED'); return
    dest = pathlib.Path.home() / '.local/share/applications' / source.name
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and 'X-UAVF-Safe-JIT=' not in dest.read_text():
        print('UAVF_SAFE_JIT=USER_DESKTOP_OVERRIDE_PRESERVED'); return
    lines = source.read_text().splitlines()
    output = []
    for line in lines:
        # Preserve URI placeholders and explicit arguments from the official entry.
        if line.startswith('Exec='):
            import shlex
            words = shlex.split(line[5:])
            if '/snap/bin/snap-store' not in words:
                print('UAVF_SAFE_JIT=UNKNOWN_DESKTOP_EXEC_PRESERVED'); return
            args = words[words.index('/snap/bin/snap-store')+1:]
            line = 'Exec=/usr/local/bin/uavf-safe-jit snap-store ' + shlex.join(args)
        output.append(line)
    output.append('X-UAVF-Safe-JIT=' + POLICY)
    dest.write_text('\n'.join(output) + '\n')
    print('UAVF_SAFE_JIT=DESKTOP_CONFIGURED')

def main():
    if len(sys.argv) == 2 and sys.argv[1] == '--setup-desktop':
        setup_desktop(); return
    if len(sys.argv) == 3 and sys.argv[1] == '--probe':
        env = environment(sys.argv[2])
        print(json.dumps({k: env.get(k) for k in ('LLVM_CPUINFO','MESA_SHADER_CACHE_DIR')})); return
    if len(sys.argv) < 2 or sys.argv[1] not in ('snap-store','vkmark'):
        raise SystemExit('Supported compatibility targets: snap-store, vkmark')
    app = sys.argv[1]
    command = ['/usr/bin/snap', 'run', 'snap-store'] if app == 'snap-store' else ['/usr/bin/vkmark']
    os.execve(command[0], command + sys.argv[2:], environment(app))

if __name__ == '__main__':
    main()
