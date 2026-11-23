import argparse
import importlib.util,io,unittest
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from types import ModuleType, SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent


def load_source(path):
    spec = importlib.util.spec_from_file_location(path.stem, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


if sys.platform == 'win32':
    # Linux account lookups are mocked by the discovery fixture.
    sys.modules.setdefault('pwd', SimpleNamespace(getpwuid=None))

b = load_source(ROOT / 'guest/winavf-frame-bridge.py')
class DiscoveryTest(unittest.TestCase):
    def discover(self,compositor,uid=1000,launcher=True,xorg=True,runtime=None,
                 allow_greeter=False, account='gdm'):
        env={'DISPLAY':':2','XAUTHORITY':'/run/user/'+str(uid)+'/gdm/Xauthority','XDG_RUNTIME_DIR':runtime or '/run/user/'+str(uid),'DBUS_SESSION_BUS_ADDRESS':'unix:path=/run/user/'+str(uid)+'/bus','XDG_SESSION_TYPE':'x11'}
        rows={'1':(compositor,uid,env,b'compositor\0')}
        if launcher:rows['2']=('gdm-x-session',uid,{},b'gdm\0')
        if xorg:rows['3']=('Xorg',uid,{},b'Xorg\0:2\0')
        def opening(path,*args,**kw):
            _,pid,kind=path.rsplit('/',2);comm,_,e,cmd=rows[pid]
            return io.BytesIO({'comm':comm.encode(),'environ':b'\0'.join((k+'='+v).encode() for k,v in e.items()),'cmdline':cmd}[kind])
        with patch.object(b.os,'listdir',return_value=list(rows)),patch.object(b.os,'stat',side_effect=lambda p:SimpleNamespace(st_uid=rows[p.rsplit('/',1)[1]][1])),patch('builtins.open',opening),patch.object(b.os.path,'exists',return_value=True),patch.object(b.os.path,'isfile',return_value=True),patch.object(b.os.path,'isdir',return_value=True),patch.object(b.os,'access',return_value=True):
            with patch.object(b.pwd,'getpwuid',return_value=SimpleNamespace(pw_name=account)):
                return b.find_display(allow_greeter=allow_greeter)
    def test_logged_in_compositors(self):
        for name in ('gnome-shell','kwin_x11'):self.assertEqual(self.discover(name)['DISPLAY'],':2')
    def test_no_greeter_or_unauthenticated_display(self):
        for name in ('gnome-shell','kwin_x11'):
            self.assertIsNone(self.discover(name,uid=119));self.assertIsNone(self.discover(name,uid=0))
            self.assertIsNone(self.discover(name,launcher=False));self.assertIsNone(self.discover(name,xorg=False))
            self.assertIsNone(self.discover(name,runtime='/run/user/999'))
    def test_opt_in_to_verified_gdm_greeter(self):
        self.assertEqual(self.discover('gnome-shell',uid=120,allow_greeter=True)['DISPLAY'],':2')
        self.assertIsNone(self.discover('gnome-shell',uid=120,allow_greeter=True,account='other'))
        self.assertIsNone(self.discover('gnome-shell',uid=120,allow_greeter=True,xorg=False))
        self.assertIsNone(self.discover('gnome-shell',uid=0,allow_greeter=True))

def verify_installer(apk, zstd):
    from pyfatfs.PyFatFS import PyFatFS

    global b
    cpio = load_source(ROOT.parent / 'generic-ubuntu/compare-p7-clean-p9.py')
    prefixes = (
        'uavf-install-platform-prefix-gdm-share.img',
        'uavf-install-platform-mavk-r2.img',
        'uavf-install-platform-plasma-r2.img',
    )
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as directory:
        prefix = Path(directory) / 'prefix.img'
        for asset in prefixes:
            with archive.open('assets/' + asset) as source, prefix.open('wb') as target:
                shutil.copyfileobj(source, target, 1024 * 1024)
            with PyFatFS(str(prefix), offset=1048576, read_only=True) as volume:
                initrd = volume.readbytes('/CASPER/INITRD')
            decoded = subprocess.run([zstd, '-d', '-q', '-c'],
                                     input=initrd[cpio.early_end(initrd):],
                                     capture_output=True, check=True).stdout
            files, _ = cpio.parse_cpio(decoded)
            for path in ('scripts/init-premount/winavf-frame-bridge.py', 'usr/local/sbin/winavf-frame-bridge'):
                b = ModuleType('packaged_bridge')
                exec(compile(files[path], path, 'exec'), vars(b))
                suite = unittest.defaultTestLoader.loadTestsFromTestCase(DiscoveryTest)
                if not unittest.TextTestRunner(verbosity=0).run(suite).wasSuccessful():
                    raise SystemExit(f'{asset}: {path} failed GDM discovery')
            producer = files['usr/local/sbin/uavf-encoded-display.py']
            compile(producer, 'uavf-encoded-display.py', 'exec')
            if not all(call in producer for call in (
                    b'bridge.find_display(allow_greeter=True)',
                    b'bridge.input_loop(allow_greeter=True)')):
                raise SystemExit(f'{asset}: encoded display does not enable the GDM greeter')
            print(f'PACKAGED_GDM_DISCOVERY=PASS {asset}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Test GDM X11 discovery in source or packaged installer payloads.')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--zstd', default='zstd')
    args = parser.parse_args()
    if args.apk:
        verify_installer(args.apk, args.zstd)
    else:
        unittest.main(argv=[sys.argv[0]])
