"""Stage test-only tools on the newly assembled PC disk, never a device."""
import hashlib
import json
from pathlib import Path
from pyfatfs.PyFatFS import PyFatFS

folder = Path('E:/U-AVF/artifacts/idd-disposable-disk-20261013')
report = json.loads((folder / 'assembly.json').read_text())
assert report['root_readback'] == 'PASS' and report['device_writes'] == 0
assert not (folder / 'staging.json').exists(), 'Single staging pass'
tools = Path(__file__).resolve().parent
package = Path('E:/U-AVF/artifacts/idd-test-signed-package-20261013')
runtime = Path('E:/U-AVF/artifacts/idd-test-ms-runtime-20261013/VC_redist.arm64.exe')
helper = Path('E:/U-AVF/artifacts/idd-admission-helper-bounded-20261013/idd-probe-device.exe')
assert hashlib.sha256(runtime.read_bytes()).hexdigest().upper() == 'B70EF586669A620A0A30A1156969C05C6A3831DC8F8BC992DA75779D2A92F944'
assert hashlib.sha256(helper.read_bytes()).hexdigest().upper() == '7A7F2498995B323FA18FB0B084BF0FE49F2AA8B092347DA501A83B6BB26447EA'
staged = {}
with PyFatFS(str(folder / 'windows-idd-test.img'), offset=1048576) as fs:
    assert not fs.exists('/UAVFIDD')
    fs.makedir('/UAVFIDD'); fs.makedir('/UAVFIDD/package')
    def put(name, data):
        fs.writebytes('/UAVFIDD/' + name, data)
        assert fs.readbytes('/UAVFIDD/' + name) == data
        staged[name] = hashlib.sha256(data).hexdigest().upper()
    put('fresh-root-provenance.txt', ('NATIVE_WIM_APPLY_EXIT=0; STRICT_ACLS=1; IMAGE=3; ARM64=1\r\n'
        'ROOT_ALLOCATED_SHA256=' + report['allocated_root_sha256'] + '\r\n').encode('ascii'))
    for src, dest in (('provision-idd-fresh.cmd', 'provision.cmd'),
                      ('provision-installed-base.cmd', 'provision-base.cmd'),
                      ('prepare-installed-boot.cmd', 'prepare-boot.cmd'),
                      ('idd-disposable-unattend.xml', 'unattend.xml')):
        put(dest, (tools / src).read_bytes().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n'))
    for src in package.iterdir():
        if src.is_file() and src.suffix.lower() in ('.inf', '.dll', '.cat', '.cer'):
            put('package/' + src.name, src.read_bytes())
    put('VC_redist.arm64.exe', runtime.read_bytes())
    put('idd-probe-device.exe', helper.read_bytes())
(folder / 'staging.json').write_text(json.dumps(dict(staged=staged, device_writes=0,
    status='PC_ONLY_NOT_DRIVER_LOAD_OR_FPS_PASS'), indent=2))
print('DISPOSABLE_CARRIER_STAGED_PC_ONLY')
