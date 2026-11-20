"""Offline audit of our numeric ETW/reconnect logs; never a display FPS PASS."""
import argparse
import importlib.util
import json
import re
from pathlib import Path

spec = importlib.util.spec_from_file_location('metrics', Path(__file__).with_name('idd-proof-metrics.py'))
metrics = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metrics)
FRAME = re.compile(r'^IPC_FRAME connection=(\d+) sample=(\d+) pid=(\d+) sequence=(\d+) bytes=(\d+) crc32=([0-9a-f]{8}) display_qpc=(\d+)$')


def audit(source, pipe, nominal_hz=60):
    if not 1 <= nominal_hz <= 240:
        raise ValueError('Invalid nominal refresh')
    intervals = []
    frequencies = set()
    for line in source.splitlines():
        if line.startswith('IDD_PAYLOAD_HEX='):
            payload = bytes.fromhex(line.split('=', 1)[1])
            decoded = metrics.decode(payload)
            if decoded['status'] == '0x0' and decoded['frames']:
                frequencies.add(int.from_bytes(payload[16:24], 'little'))
                intervals.append(decoded)
    if len(frequencies) != 1:
        raise ValueError('Require one actual source QPC frequency')
    frequency = frequencies.pop()
    samples = []
    for line in pipe.splitlines():
        if line.startswith('IPC_FRAME'):
            match = FRAME.fullmatch(line)
            if not match:
                raise ValueError('Malformed owned frame record')
            c, sample, pid, sequence, size, crc, qpc = match.groups()
            if int(size) != 9216000:
                raise ValueError('Unexpected geometry')
            samples.append((int(c), int(sample), int(pid), int(sequence), crc, int(qpc)))
    if len(samples) < 2 or len({s[2] for s in samples}) != 1:
        raise ValueError('Missing frames or producer changed')
    gaps = []
    sequence_gaps = 0
    for previous, current in zip(samples, samples[1:]):
        if current[3] <= previous[3] or current[5] <= previous[5]:
            raise ValueError('Non-monotonic source metadata')
        # Reconnection deliberately creates a pause: never count it as cadence.
        if current[0] != previous[0]:
            continue
        if current[1] != previous[1] + 1:
            raise ValueError('Missing probe sample')
        delta = current[5] - previous[5]
        slots = round(delta * nominal_hz / frequency)
        residual = abs(delta * nominal_hz - slots * frequency) / nominal_hz
        gaps.append(dict(delta_ms=delta * 1000 / frequency, nominal_slots=slots,
                         grid_error_qpc_ticks=residual))
        sequence_gaps += current[3] - previous[3] - 1
    return dict(qpc_frequency=frequency, nominal_hz=nominal_hz,
                source_intervals=intervals, received_samples=len(samples),
                distinct_crc32=len({s[4] for s in samples}),
                within_connection_intervals=gaps,
                within_connection_sequence_gaps=sequence_gaps,
                nominal_grid_consistent=bool(gaps) and all(g['nominal_slots'] >= 1 and
                    g['grid_error_qpc_ticks'] <= 2 for g in gaps),
                sustained_capture_pass=False, actual_android_present_pass=False,
                caveat='Short reconnect samples; nominal timing grid is not proof of generated or presented 60FPS',
                publisher_cost_caveat='Reader disconnected for most of the 30s interval; read_ms includes disconnected/drop fast path, not sustained full-frame transfer cost')


def selftest():
    import struct
    source = 'IDD_PAYLOAD_HEX=' + struct.pack('<10Q', 1, 0, 13000000, 390000000,
        1200, 1200, 130000, 1920, 1200, 7680).hex()
    def frame(c, sample, sequence, qpc, pid=7):
        return f'IPC_FRAME connection={c} sample={sample} pid={pid} sequence={sequence} bytes=9216000 crc32=12345678 display_qpc={qpc}'
    lines = [frame(0, 0, 1, 13000000), frame(0, 1, 2, 13216667),
             frame(1, 0, 30, 26000000), frame(1, 1, 31, 26216667)]
    result = audit(source, '\n'.join(lines))
    assert result['nominal_grid_consistent'] and len(result['within_connection_intervals']) == 2
    assert result['within_connection_sequence_gaps'] == 0
    assert not result['actual_android_present_pass']
    for bad in ('', '\n'.join([lines[1], lines[0]]),
                '\n'.join([lines[0], frame(0, 1, 2, 13216667, 8)])):
        try:
            audit(source, bad)
        except ValueError:
            pass
        else:
            raise AssertionError('Invalid sample accepted')
    assert not audit(source, '\n'.join([lines[0], frame(0, 1, 2, 13200000)]))['nominal_grid_consistent']
    print('CADENCE_AUDIT_SELFTEST_PASS; NO_RUNTIME_FPS_CLAIM')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--selftest', action='store_true')
    args = parser.parse_args()
    if args.selftest:
        selftest()
    else:
        if not args.evidence or not args.output or args.output.exists():
            parser.error('Require own evidence and fresh output path')
        folder = args.evidence / 'UAVF/IddWorkingProbe'
        paths = [folder / 'source-report.txt', folder / 'pipe-reconnect23.txt']
        if any(p.stat().st_size > 262144 for p in paths):
            raise ValueError('Evidence exceeds bounded own log size')
        def read_owned(path):
            data = path.read_bytes()
            return data.decode('utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')
        result = audit(*(read_owned(p) for p in paths))
        args.output.write_text(json.dumps(result, indent=2), encoding='utf-8')
        print(json.dumps(result, indent=2))
