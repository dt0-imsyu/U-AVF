"""Read only named time configuration from an offline SYSTEM hive.

Optional transaction recovery is in-memory only. No OS registry loading,
file/device writes, credentials or recursive enumeration.
"""
import argparse
from datetime import datetime, timedelta
import hashlib
import io
import json
import struct
from pathlib import Path
from Registry import Registry, RegistryLog

p = argparse.ArgumentParser()
p.add_argument('--system', type=Path, required=True)
p.add_argument('--log', type=Path, help='Single available non-empty SYSTEM.LOG1/2; recovery in memory only')
a = p.parse_args()
assert a.system.name.upper() == 'SYSTEM'
data = a.system.read_bytes()
sequence1, sequence2 = struct.unpack_from('<II', data, 4)
recovery = None
view = io.BytesIO(data)
if a.log:
    assert a.log.name.upper() in {'SYSTEM.LOG1', 'SYSTEM.LOG2'}
    assert a.log.parent.resolve() == a.system.parent.resolve()
    other = a.log.parent / ('SYSTEM.LOG1' if a.log.name.upper() == 'SYSTEM.LOG2' else 'SYSTEM.LOG2')
    assert not other.exists() or other.stat().st_size == 0, 'Dual non-empty logs require explicit ordered recovery'
    log_data = a.log.read_bytes()
    assert 512 <= len(log_data) <= 8 * 1024 * 1024
    log = RegistryLog.RegistryLog(view, io.BytesIO(log_data))
    assert log.is_eligible_log(), 'Stale transaction log refused'
    applied = log.recover_hive()
    recovered = view.getvalue()
    seq_after = struct.unpack_from('<II', recovered, 4)
    assert applied is not None and seq_after[0] == seq_after[1]
    recovery = {'log_sha256': hashlib.sha256(log_data).hexdigest(),
                'last_applied_sequence': applied, 'sequences_after': seq_after,
                'in_memory_only': True}
view.seek(0)
hive = Registry.Registry(view)
select = hive.open('Select')
current = select.value('Current').value()
assert isinstance(current, int) and 1 <= current <= 999
prefix = f'ControlSet{current:03d}'
paths = ['Control\\TimeZoneInformation',
         'Control\\Windows',
         'Services\\W32Time\\Config',
         'Services\\W32Time\\SecureTimeLimits',
         'Services\\W32Time\\Parameters']
allowed_windows = {'ShutdownTime'}
result = {'hive_sha256': hashlib.sha256(data).hexdigest(),
          'header_sequences': [sequence1, sequence2],
          'transaction_recovery_required': sequence1 != sequence2,
          'scope': 'Base hive only; unequal sequences require log recovery before exclusion claims',
          'recovery': recovery,
          'selected_control_set': prefix, 'read_only': True, 'keys': {}}
# Exact winload fallback is the LastWriteTime of this root subkey, not
# W32Time's SecureTime values. Read metadata only, never enumerate its contents.
try:
    override = hive.open('ControlSetOverride')
    result['loader_control_set_override'] = {'last_write': override.timestamp().isoformat(),
                                            'metadata_only': True}
except Registry.RegistryKeyNotFoundException:
    result['loader_control_set_override'] = {'missing': True, 'metadata_only': True}
for suffix in paths:
    path = prefix + '\\' + suffix
    try:
        key = hive.open(path)
    except Registry.RegistryKeyNotFoundException:
        result['keys'][path] = {'missing': True}
        continue
    values = {}
    for value in key.values():
        if suffix == 'Control\\Windows' and value.name() not in allowed_windows:
            continue
        raw = value.value()
        entry = {'type': value.value_type_str(),
                 'value': raw.hex() if isinstance(raw, bytes) else raw}
        if value.name() in {'ShutdownTime', 'SecureTimeEstimated',
                             'SecureTimeHigh', 'SecureTimeLow'}:
            ticks = int.from_bytes(raw, 'little') if isinstance(raw, bytes) and len(raw) == 8 else raw
            if isinstance(ticks, int):
                entry['filetime_100ns'] = ticks
                try:
                    entry['utc'] = (datetime(1601, 1, 1) + timedelta(microseconds=ticks // 10)).isoformat()
                except OverflowError:
                    entry['utc'] = 'outside datetime range'
        values[value.name()] = entry
    result['keys'][path] = {'last_write': key.timestamp().isoformat(), 'values': values}
print(json.dumps(result, indent=2))
