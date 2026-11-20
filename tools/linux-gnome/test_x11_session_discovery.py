import importlib.util,io,unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('bridge',Path(__file__).parent/'guest/winavf-frame-bridge.py')
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
class DiscoveryTest(unittest.TestCase):
    def discover(self,compositor,uid=1000,launcher=True,xorg=True,runtime=None):
        env={'DISPLAY':':2','XAUTHORITY':'/run/user/'+str(uid)+'/gdm/Xauthority','XDG_RUNTIME_DIR':runtime or '/run/user/'+str(uid),'DBUS_SESSION_BUS_ADDRESS':'unix:path=/run/user/'+str(uid)+'/bus','XDG_SESSION_TYPE':'x11'}
        rows={'1':(compositor,uid,env,b'compositor\0')}
        if launcher:rows['2']=('gdm-x-session',uid,{},b'gdm\0')
        if xorg:rows['3']=('Xorg',uid,{},b'Xorg\0:2\0')
        def opening(path,*args,**kw):
            _,pid,kind=path.rsplit('/',2);comm,_,e,cmd=rows[pid]
            return io.BytesIO({'comm':comm.encode(),'environ':b'\0'.join((k+'='+v).encode() for k,v in e.items()),'cmdline':cmd}[kind])
        with patch.object(b.os,'listdir',return_value=list(rows)),patch.object(b.os,'stat',side_effect=lambda p:SimpleNamespace(st_uid=rows[p.rsplit('/',1)[1]][1])),patch('builtins.open',opening),patch.object(b.os.path,'exists',return_value=True),patch.object(b.os.path,'isfile',return_value=True),patch.object(b.os.path,'isdir',return_value=True),patch.object(b.os,'access',return_value=True):
            return b.find_display()
    def test_logged_in_compositors(self):
        for name in ('gnome-shell','kwin_x11'):self.assertEqual(self.discover(name)['DISPLAY'],':2')
    def test_no_greeter_or_unauthenticated_display(self):
        for name in ('gnome-shell','kwin_x11'):
            self.assertIsNone(self.discover(name,uid=119));self.assertIsNone(self.discover(name,uid=0))
            self.assertIsNone(self.discover(name,launcher=False));self.assertIsNone(self.discover(name,xorg=False))
            self.assertIsNone(self.discover(name,runtime='/run/user/999'))
if __name__=='__main__':unittest.main()
