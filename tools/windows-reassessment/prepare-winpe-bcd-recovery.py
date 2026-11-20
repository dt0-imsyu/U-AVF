"""Change only existing default-object GUID in a retained cold BCD extraction.

No privilege elevation, hive resizing, new key, boot flags or Windows image edits.
This is a temporary recovery selection, not an installed-boot success marker.
"""
import argparse
import hashlib
import io
import json
import struct
from Registry import Registry

BOOTMGR = '{9dea862c-5cdd-4e70-acc1-f32b344d4795}'
WINPE = '{7619dcc9-fafe-11d9-b411-000476eba25f}'
KEY = 'Objects\\' + BOOTMGR + '\\Elements\\23000003'

def inventory(registry):
    result = {}
    def walk(key, path):
        for value in key.values():
            result[path + '\\' + value.name()] = (value.value_type(), value.raw_data())
        for child in key.subkeys():
            walk(child, path + '\\' + child.name())
    walk(registry.root(), '')
    return result

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source')
    parser.add_argument('output')
    args = parser.parse_args()
    original = open(args.source, 'rb').read()
    assert original[:4] == b'regf'
    assert struct.unpack_from('<I', original, 4)[0] == struct.unpack_from('<I', original, 8)[0]
    registry = Registry.Registry(io.BytesIO(original))
    value = registry.open(KEY).value('Element')
    assert value.value_type() == Registry.RegSZ
    installed = value.value()
    assert installed != WINPE
    assert registry.open('Objects\\' + installed + '\\Elements\\12000004').value('Element').value() == 'Windows 11'
    assert registry.open('Objects\\' + installed + '\\Elements\\12000002').value('Element').value().lower() == '\\windows\\system32\\winload.efi'
    assert registry.open('Objects\\' + WINPE + '\\Elements\\12000004').value('Element').value() == 'Windows Setup'
    assert registry.open('Objects\\' + WINPE + '\\Elements\\26000022').value('Element').value() == b'\x01'
    raw = value.raw_data()
    replacement = (WINPE + '\0').encode('utf-16le')
    assert len(raw) == len(replacement) == 78
    offset = value._vkrecord.data_offset() + 4
    assert original[offset:offset + len(raw)] == raw
    changed = bytearray(original)
    changed[offset:offset + len(raw)] = replacement
    after = Registry.Registry(io.BytesIO(changed))
    before_values, after_values = inventory(registry), inventory(after)
    assert before_values.keys() == after_values.keys()
    changes = [key for key in before_values if before_values[key] != after_values[key]]
    assert changes == ['\\' + KEY + '\\Element']
    assert after.open(KEY).value('Element').value() == WINPE
    assert changed[:4096] == original[:4096], 'Hive header/checksum unchanged'
    with open(args.output, 'xb') as stream:
        stream.write(changed)
    print(json.dumps(dict(result='PASS', bytes=len(changed), previous_default=installed,
        recovery_default=WINPE, changed_values=changes, changed_byte_count=sum(a != b for a, b in zip(original, changed)),
        sha256=hashlib.sha256(changed).hexdigest().upper())))

if __name__ == '__main__':
    main()
