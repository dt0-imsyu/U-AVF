"""Storage helper contract tests; no real disks or installed guest are opened."""
import importlib.util
import io
from pathlib import Path
import struct
import sys
import types
import unittest
from unittest.mock import patch

if sys.platform=='win32':
    # Only mock the Unix lock primitive for host-side contract tests.
    sys.modules['fcntl']=types.SimpleNamespace(LOCK_EX=2,LOCK_NB=4,flock=lambda *args:None)
spec=importlib.util.spec_from_file_location('control',Path(__file__).resolve().parents[1]/'guest/uavf-control.py')
control=importlib.util.module_from_spec(spec)
spec.loader.exec_module(control)

class StorageTests(unittest.TestCase):
    def run_case(self,filesystem_gib=32,partition_gib=40,mount='/dev/vda3 ext4',grow=True):
        sb=bytearray(2048)
        struct.pack_into('<H',sb,1024+56,0xef53)
        struct.pack_into('<I',sb,1024+24,2)
        struct.pack_into('<I',sb,1024+4,filesystem_gib*1024**3//4096)
        def opened(name,*args,**kwargs):
            if name=='/sys/class/block/vda3/size':return io.StringIO(str(partition_gib*1024**3//512))
            if name=='/dev/vda3':return io.BytesIO(sb)
            raise AssertionError('Unexpected file access: '+name)
        resized=[]
        def command(args,**kwargs):
            if args[0]=='findmnt':return type('Result',(),{'stdout':mount})()
            self.assertEqual(args,['/usr/sbin/resize2fs','/dev/vda3'])
            self.assertTrue(kwargs.get('capture_output'), 'resize output must never enter UCTL socket')
            self.assertTrue(kwargs.get('text'))
            resized.append(True)
            struct.pack_into('<I',sb,1024+4,partition_gib*1024**3//4096)
        with patch.object(control,'open',opened,create=True),patch.object(control.os,'geteuid',return_value=0,create=True),patch.object(control.os.path,'isfile',return_value=True),patch.object(control.subprocess,'run',side_effect=command),patch.object(control.fcntl,'flock'):
            result=control.storage_info(grow)
        return result,resized
    def test_growth(self):
        result,resized=self.run_case()
        self.assertEqual(len(resized),1)
        self.assertEqual(result['filesystem_bytes'],40*1024**3)
        self.assertEqual(result['growth_version'],1)
    def test_idempotent(self):
        self.assertFalse(self.run_case(filesystem_gib=40)[1])
    def test_query_does_not_resize(self):
        self.assertFalse(self.run_case(grow=False)[1])
    def test_reject_other_root(self):
        result,resized=self.run_case(mount='/dev/mapper/root ext4')
        self.assertFalse(result['growth_supported']);self.assertFalse(resized)
    def test_reject_shrunk_partition(self):
        with self.assertRaises(ValueError):self.run_case(filesystem_gib=48)

if __name__=='__main__':unittest.main()
