"""Temporary isolated bootstatuspolicy after observed repeated-boot winload stop.

Documented BCD integer250000e0=1 (IgnoreAllFailures), not a platform experiment.
Preserve complete original store and verify ONLY this element changes. Remove
the temporary policy after normal display/login/shutdown is proven.
"""
import argparse
import ctypes
import hashlib
import importlib.util
import io
import json
import struct
from pathlib import Path

from Registry import Registry

INSTALLED = "{86df57c0-9fb3-11f1-a311-83a31eab37aa}"
KEY = "Objects\\" + INSTALLED + "\\Elements\\250000e0"


def main():
    p = argparse.ArgumentParser(); p.add_argument("source", type=Path); p.add_argument("output", type=Path)
    p.add_argument('--test-signing',action='store_true',help='Explicit temporary signed IddCx admission in approved Windows guest, not bootstatus policy')
    a = p.parse_args(); assert not a.output.exists()
    key = "Objects\\" + INSTALLED + "\\Elements\\16000049" if a.test_signing else KEY
    wanted = b'\x01' if a.test_signing else struct.pack('<Q',1)
    original = a.source.read_bytes()
    assert len(original) == 32768 and original[:4] == b"regf" and original[4:8] == original[8:12]
    r = Registry.Registry(io.BytesIO(original))
    default = r.open(r"Objects\{9dea862c-5cdd-4e70-acc1-f32b344d4795}\Elements\23000003").value("Element").value()
    assert default.lower() == INSTALLED
    assert r.open("Objects\\" + INSTALLED + r"\Elements\12000002").value("Element").value().lower() == r"\windows\system32\winload.efi"
    assert r.open("Description").value("System").value() == 1
    try:
        previous = r.open(key).value("Element").raw_data()
    except Registry.RegistryKeyNotFoundException:
        previous = None
    assert previous != wanted, "Do not repeat policy change"
    api = ctypes.WinDLL(r"C:\Windows\System32\offreg.dll")
    handle = ctypes.c_void_p; status = ctypes.c_ulong
    api.OROpenHive.argtypes=[ctypes.c_wchar_p,ctypes.POINTER(handle)]; api.OROpenHive.restype=status
    api.ORCreateKey.argtypes=[handle,ctypes.c_wchar_p,ctypes.c_wchar_p,ctypes.c_ulong,ctypes.c_void_p,ctypes.POINTER(handle),ctypes.POINTER(ctypes.c_ulong)]; api.ORCreateKey.restype=status
    api.ORSetValue.argtypes=[handle,ctypes.c_wchar_p,ctypes.c_ulong,ctypes.c_void_p,ctypes.c_ulong]; api.ORSetValue.restype=status
    api.ORCloseKey.argtypes=[handle]; api.ORCloseKey.restype=status
    api.ORCloseHive.argtypes=[handle]; api.ORCloseHive.restype=status
    api.ORSaveHive.argtypes=[handle,ctypes.c_wchar_p,ctypes.c_ulong,ctypes.c_ulong]; api.ORSaveHive.restype=status
    def check(result):
        if result: raise OSError(result, "Native Offline BCD operation")
    root=handle(); child=handle(); disposition=ctypes.c_ulong()
    check(api.OROpenHive(str(a.source.resolve()),ctypes.byref(root)))
    try:
        check(api.ORCreateKey(root,key,None,0,None,ctypes.byref(child),ctypes.byref(disposition)))
        try:
            data=ctypes.create_string_buffer(wanted)
            check(api.ORSetValue(child,"Element",3,data,len(wanted)))
        finally: check(api.ORCloseKey(child))
        check(api.ORSaveHive(root,str(a.output.resolve()),10,0))
    finally: check(api.ORCloseHive(root))
    changed=a.output.read_bytes(); assert len(changed)<=len(original)
    changed+=bytes(len(original)-len(changed)); a.output.write_bytes(changed)
    folder=Path(__file__).parent
    def load(name,file):
        spec=importlib.util.spec_from_file_location(name,folder/file); m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m
    inventory=load("bcd_inventory","prepare-winpe-bcd-recovery.py").inventory
    security=load("bcd_security","prepare-windows-display-service.py").security_inventory
    parsed=Registry.Registry(io.BytesIO(changed)); before,after=inventory(r),inventory(parsed)
    element="\\"+key+"\\Element"
    assert {key for key in set(before)|set(after) if before.get(key)!=after.get(key)} == {element}
    old_security,new_security=security(r,original),security(parsed,changed)
    assert all(new_security.get(key)==value for key,value in old_security.items())
    report=dict(result="ONE_BCD_ELEMENT_PREFLIGHT_PASS", temporary=True,
                semantic_diff=[element], original_sha256=hashlib.sha256(original).hexdigest(),
                changed_sha256=hashlib.sha256(changed).hexdigest(),
                previous=None if previous is None else previous.hex(),
                reference="https://learn.microsoft.com/en-us/previous-versions/windows/desktop/bcd/bcdosloaderelementtypes",
                remove_after="Driver source test and cleanup" if a.test_signing else "Normal pre-login/display and clean shutdown proof",
                purpose="Approved guest test-driver admission only" if a.test_signing else "Temporary boot-status policy")
    a.output.with_suffix(".report.json").write_text(json.dumps(report,indent=2)); print(json.dumps(report))


if __name__ == "__main__": main()
