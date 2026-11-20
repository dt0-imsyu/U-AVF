import importlib.util
import io
import json
from pathlib import Path
import struct
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('control',Path(__file__).parent/'guest/uavf-control.py')
control=importlib.util.module_from_spec(spec); spec.loader.exec_module(control)

class ControlWireTest(unittest.TestCase):
    def test_mavk_requires_addon(self):
        with patch.object(control.os.path,'isfile',return_value=False):
            self.assertNotIn('MAVK_MODE',control.handle({'type':'HELLO','id':1})['capabilities'])
            self.assertFalse(control.handle({'type':'MAVK_ENABLE','id':2})['ok'])
        with patch.object(control.os.path,'isfile',return_value=True):
            self.assertIn('MAVK_MODE',control.handle({'type':'HELLO','id':3})['capabilities'])
    def test_roundtrip(self):
        message={'version':1,'id':123,'type':'PING'}
        self.assertEqual(control.read_message(io.BytesIO(control.packet(message))),message)
        self.assertEqual(control.handle(message)['reply'],'PONG')
    def test_rejected(self):
        for value in (b'BAD!'+bytes(8),struct.pack('<4sHHI',b'UCTL',1,0,262145),control.packet({'version':1,'id':1,'type':'EXEC'})):
            with self.assertRaises(ValueError): control.read_message(io.BytesIO(value))
    def test_truncated(self):
        with self.assertRaises(EOFError): control.read_message(io.BytesIO(b'UCTL'))
    def test_no_false_clipboard_capability(self):
        response=control.handle({'version':1,'id':1,'type':'CLIPBOARD_GET'})
        self.assertFalse(response['ok'])
        hello=control.handle({'version':1,'id':2,'type':'HELLO'})
        self.assertNotIn('CLIPBOARD_GET',hello['capabilities'])
    def test_plasma_fixed_unit_and_power_guard(self):
        request = {'type':'PLASMA_INSTALL','id':1,'unit':'ignored.service'}
        with patch.object(control.os,'geteuid',return_value=1000), patch.object(
                control,'plasma_state',return_value='available'):
            self.assertFalse(control.handle(request)['ok'])
        with patch.object(control.os,'geteuid',return_value=0), patch.object(
                control,'plasma_state',return_value='available'), patch.object(
                control.subprocess,'run') as run:
            self.assertEqual(control.handle(request)['plasma_x11'],'installing')
            self.assertEqual(run.call_args.args[0],
                             ['systemctl','--no-block','start','uavf-plasma-install.service'])
        with patch.object(control.os,'geteuid',return_value=0), patch.object(
                control,'plasma_state',return_value='installing'):
            self.assertFalse(control.handle({'type':'REBOOT','id':2})['ok'])

if __name__=='__main__': unittest.main()
