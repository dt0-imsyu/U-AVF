"""Prepare isolated display-service registration with native Offline Registry.

Never opens the Android disk or the host's global registry. Canonical whole-hive
diff must show only our new service; no Setup/account/platform values may change.
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

EXPECTED_SYSTEM_SHA = "4623ab43639cbf8310c89c9a5e001e84d59d721e1d3dccf301a419cc88b5639e"
EXPECTED_SCREEN_SHA = "2bbf8193833df3d6b9e4f0b26859d38119637e4dfdd557650da4b43289cb418b"
SERVICE = r"ControlSet001\Services\UAVFWindowsDisplay"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def security_inventory(registry, data):
    result = {}
    def walk(key, path):
        record = key._nkrecord.sk_record()
        length = record.unpack_dword(0x10)
        assert 20 <= length <= 65536
        offset = record.offset() + 0x14
        result[path] = digest(data[offset:offset + length])
        for child in key.subkeys():
            walk(child, path + "\\" + child.name())
    walk(registry.root(), "")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-system-sha", default=EXPECTED_SYSTEM_SHA)
    parser.add_argument("--display-only", action="store_true",
                        help="Start the display broker without the diagnostic CPU sampler")
    parser.add_argument("--recover-retained-logs", action="store_true",
                        help="Recover only a retained scratch copy using both matching transaction logs")
    args = parser.parse_args()
    assert not args.output.exists(), "Preserve earlier preparation"
    original = (args.evidence / "Windows/System32/config/SYSTEM").read_bytes()
    screen = (args.evidence / "Windows/System32/winpe-screen.exe").read_bytes()
    assert digest(original) == args.expected_system_sha.lower() and digest(screen) == EXPECTED_SCREEN_SHA
    raw_original = original
    recovery = None
    assert original[:4] == b"regf"
    if original[4:8] != original[8:12]:
        assert args.recover_retained_logs, "Dirty hive requires explicit retained-log recovery"
        from Registry.RegistryLog import RegistryLog
        scratch = io.BytesIO(original)
        logs = []
        for suffix in (".LOG1", ".LOG2"):
            content = (args.evidence / ("Windows/System32/config/SYSTEM" + suffix)).read_bytes()
            if not content:
                continue
            scratch.seek(0)
            log = RegistryLog(scratch, io.BytesIO(content))
            assert log.is_eligible_log(), "Each nonempty log must belong to the current recovery generation"
            entries = [entry.sequence() for entry in log._regf.log_entries()]
            assert entries and entries == list(range(entries[0], entries[-1] + 1)), "Contiguous validated log records"
            logs.append((log, entries))
        assert logs and logs[0][1], "A dirty hive requires a valid recovery log"
        if len(logs) == 2:
            if not logs[0][0].is_starting_log(logs[1][0]):
                logs.reverse()
            assert logs[0][1][-1] + 1 == logs[1][1][0], "No gap between retained logs"
        last = logs[0][0].recover_hive()
        assert last == logs[0][1][-1]
        if len(logs) == 2:
            last = logs[1][0].recover_hive_continue(last + 1)
            assert last == logs[1][1][-1]
        original = scratch.getvalue()
        assert len(original) == len(raw_original), "Recovery cannot grow existing hive allocation"
        recovery = dict(raw_sequence=struct.unpack_from("<II", raw_original, 4),
                        final_sequence=last, recovered_sha256=digest(original),
                        scope="Retained scratch only; preserve native committed transactions")
    assert original[4:8] == original[8:12]
    registry = Registry.Registry(io.BytesIO(original))
    assert registry.open("Select").value("Current").value() == 1
    setup = registry.open("Setup")
    for name in ("SetupType", "OOBEInProgress", "SystemSetupInProgress"):
        assert setup.value(name).value() == 0, "Do not alter/restart native Setup"
    assert setup.value("CmdLine").value() == ""
    try:
        registry.open(SERVICE)
    except Registry.RegistryKeyNotFoundException:
        pass
    else:
        raise AssertionError("Own service already exists")
    binary = args.binary.read_bytes()
    assert binary[:2] == b"MZ" and 4096 <= len(binary) <= len(screen)
    pe = struct.unpack_from("<I", binary, 0x3c)[0]
    assert binary[pe:pe+4] == b"PE\0\0" and struct.unpack_from("<H", binary, pe+4)[0] == 0xaa64
    args.output.mkdir(parents=True)
    native_source = args.output / "SYSTEM-native-input.hiv"
    native_source.write_bytes(original)
    hive = args.output / "SYSTEM-display-service.hiv"
    # RegLoadAppKey requires uniform key security descriptors; a SYSTEM hive
    # does not meet that contract. Use Microsoft's Offline Registry library,
    # which never loads this hive into the active host registry.
    api = ctypes.WinDLL(r"C:\Windows\System32\offreg.dll", use_last_error=True)
    handle = ctypes.c_void_p
    status = ctypes.c_long
    api.OROpenHive.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(handle)]; api.OROpenHive.restype = status
    api.ORCreateKey.argtypes = [handle, ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_ulong,
                               ctypes.c_void_p, ctypes.POINTER(handle), ctypes.POINTER(ctypes.c_ulong)]
    api.ORCreateKey.restype = status
    api.ORSetValue.argtypes = [handle, ctypes.c_wchar_p, ctypes.c_ulong, ctypes.c_void_p, ctypes.c_ulong]
    api.ORSetValue.restype = status
    api.ORSaveHive.argtypes = [handle, ctypes.c_wchar_p, ctypes.c_ulong, ctypes.c_ulong]; api.ORSaveHive.restype = status
    api.ORCloseKey.argtypes = [handle]; api.ORCloseKey.restype = status
    api.ORCloseHive.argtypes = [handle]; api.ORCloseHive.restype = status
    def check(code, operation):
        if code:
            raise OSError(code, operation)
    root, child, disposition = handle(), handle(), ctypes.c_ulong()
    check(api.OROpenHive(str(native_source.resolve()), ctypes.byref(root)), "OROpenHive retained copy")
    mode = "--display-service" if args.display_only else "--service"
    values = {"Type": (4, 0x10), "Start": (4, 2), "ErrorControl": (4, 1),
              "ObjectName": (1, "LocalSystem"), "DisplayName": (1, "U-AVF Windows display"),
              "ImagePath": (2, rf'"%SystemRoot%\System32\winpe-screen.exe" {mode}')}
    try:
        check(api.ORCreateKey(root, SERVICE, None, 0, None, ctypes.byref(child), ctypes.byref(disposition)), "Create isolated service")
        assert disposition.value == 1
        try:
            for name, (kind, value) in values.items():
                data = struct.pack("<I", value) if kind == 4 else (value + "\0").encode("utf-16le")
                buffer = ctypes.create_string_buffer(data)
                check(api.ORSetValue(child, name, kind, buffer, len(data)), "Set own service value")
        finally:
            check(api.ORCloseKey(child), "Close own key")
        check(api.ORSaveHive(root, str(hive.resolve()), 10, 0), "Save isolated hive")
    finally:
        check(api.ORCloseHive(root), "Close offline hive")
    changed = hive.read_bytes()
    assert len(changed) <= len(original), "Existing NTFS hive capacity only"
    changed += bytes(len(original) - len(changed))
    assert changed[4:8] == changed[8:12], "Native clean checkpoint required"
    spec = importlib.util.spec_from_file_location("inventory", Path(__file__).with_name("prepare-winpe-bcd-recovery.py"))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    before = module.inventory(registry)
    parsed = Registry.Registry(io.BytesIO(changed))
    after = module.inventory(parsed)
    assert all(after.get(key) == value for key, value in before.items()), "Unrelated value mutation refused"
    added = sorted(set(after) - set(before))
    assert added == sorted("\\" + SERVICE + "\\" + name for name in values), "Only declared service values"
    before_security, after_security = security_inventory(registry, original), security_inventory(parsed, changed)
    assert all(after_security.get(key) == value for key, value in before_security.items()), "Existing key security mutation refused"
    assert set(after_security) - set(before_security) == {"\\" + SERVICE}, "Only one new key"
    # Existing owned diagnostic helper slot only; fixed file length/runlist.
    padded = binary + bytes(len(screen) - len(binary))
    (args.output / "SYSTEM-display-service-fixed.hiv").write_bytes(changed)
    (args.output / "winpe-screen-service-slot.exe").write_bytes(padded)
    report = dict(result="NATIVE_PRIVATE_HIVE_PREFLIGHT_PASS", original_system_sha256=digest(raw_original),
                  changed_system_sha256=digest(changed), original_helper_sha256=digest(screen),
                  changed_helper_sha256=digest(padded), hive_bytes=len(original), helper_bytes=len(screen),
                  semantic_added=added, unrelated_values_changed=0, existing_key_security_changed=0, guest_writes=0,
                  transaction_recovery=recovery,
                  scope="One owned service and existing owned unused screenshot helper; no Setup/account/EFI/ISO changes")
    (args.output / "report.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report))


if __name__ == "__main__":
    main()
