"""Read-only bounded SurfaceFlinger observation of the isolated Windows Surface.

No APK/VM mutation. Run concurrently with an observed ordinary moving window.
Never infer workload or unique pixel images merely from presentation fences.
"""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--seconds', type=int, choices=(30,40,45), default=40)
a = p.parse_args()
assert not a.output.exists()
owner = subprocess.check_output([a.adb, 'shell', '/apex/com.android.virt/bin/vm', 'list'], text=True)
assert owner.count('requesterUid:') == 1 and 'requesterUid: 10042' in owner
layers = subprocess.check_output([a.adb, 'shell', 'dumpsys SurfaceFlinger --list'], text=True)
names = []
for line in layers.splitlines():
    if 'SurfaceView[com.example.uavfwinpetest/com.example.winavf.WindowsDiagnosticActivity]' in line and '(BLAST)#' in line:
        match = re.fullmatch(r'RequestedLayerState\{(.+?) parentId=.*\}', line)
        names.append(match[1] if match else line.strip())
assert len(names) == 1 and not any(c in names[0] for c in ('"', '\\', '\n', '\r', '`', '$'))
layer = names[0]

def sample():
    # adb shell reparses its arguments; quote the observed name including spaces.
    raw = subprocess.check_output([a.adb, 'shell', f'dumpsys SurfaceFlinger --latency "{layer}"'], text=True, timeout=5)
    values = []
    for row in raw.splitlines()[1:]:
        if re.fullmatch(r'\d+\s+\d+\s+\d+', row):
            triple = tuple(map(int, row.split()))
            if all(0 < n < 2**63-1 for n in triple):
                values.append(triple)
    return raw, values

raw, initial = sample()
assert initial, 'No usable presentation fences; not a FPS result'
baseline = max(v[1] for v in initial)
a.output.mkdir(parents=True)
(a.output/'layer.txt').write_text(layer+'\n')
(a.output/'initial.txt').write_text(raw)
seen = {}
start = time.monotonic()
with (a.output/'samples.jsonl').open('w') as log:
    while time.monotonic()-start < a.seconds:
        raw, triples = sample()
        for v in triples:
            if v[1] > baseline:
                seen[v[1]] = v
        log.write(json.dumps({'elapsed': time.monotonic()-start, 'raw': raw})+'\n')
        log.flush()
        time.sleep(.3)
final_owner = subprocess.check_output([a.adb, 'shell', '/apex/com.android.virt/bin/vm', 'list'], text=True)
assert final_owner == owner, 'VM owner changed during observation'
timestamps = sorted(seen)
span = (timestamps[-1]-timestamps[0])/1e9 if len(timestamps)>1 else 0
# Predeclared idle discriminator, not a selected peak: ordinary continuous
# dragging should have no100ms presentation holes. Keep the full raw history
# and full-interval statistics too; this alone does not identify pixel content.
runs = []
current = []
for value in timestamps:
    if current and value-current[-1] > 100_000_000:
        runs.append(current)
        current = []
    current.append(value)
if current:
    runs.append(current)
longest = max(runs, key=lambda r: r[-1]-r[0], default=[])
continuous_span = (longest[-1]-longest[0])/1e9 if len(longest)>1 else 0
result = {
    'observed_layer': layer, 'host_observation_seconds': time.monotonic()-start,
    'new_actual_present_timestamps': len(timestamps), 'presentation_span_seconds': span,
    'present_timestamp_fps': (len(timestamps)-1)/span if span else None,
    'max_present_gap_ms': max(((b-c)/1e6 for c,b in zip(timestamps,timestamps[1:])), default=None),
    'predeclared_max_continuous_gap_ms': 100,
    'longest_continuous_present_span_seconds': continuous_span,
    'longest_continuous_present_count': len(longest),
    'continuous_present_timestamp_fps': (len(longest)-1)/continuous_span if continuous_span else None,
    'moving_workload_verified_by_this_tool': False, 'unique_pixel_images_verified': False,
    'claim': 'Presentation timestamps only; correlate motion interval and same source before claiming sustained FPS',
}
(a.output/'summary.json').write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result))
