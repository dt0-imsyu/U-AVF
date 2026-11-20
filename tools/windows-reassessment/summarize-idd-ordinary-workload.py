"""Summarize an explicitly bounded real-window interval, not static updates.

Android render callbacks are deliberately not called physical presented FPS.
Read-only SurfaceFlinger history is retained separately; a short history cannot
prove the same 30-second sustained physical presentation interval.
"""
import argparse
import json
import re
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--report', type=Path, required=True)
p.add_argument('--pid', type=int, required=True)
p.add_argument('--start-tick', type=int, required=True)
p.add_argument('--end-tick', type=int, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
assert not a.output.exists() and a.end_tick > a.start_tick
text = a.report.read_text()
assert 'CAPTURE_SOURCE=IDD_LOCAL_PIPE open_error=0' in text
latest_metrics = None
points = []
positions = {}
for position, line in enumerate(text.splitlines()):
    if 'LIVE_METRICS ' in line:
        latest_metrics = dict(re.findall(r'(\w+)=([^ ]+)', line))
    if f'SOURCE_STAGE=LIVE_INTERVAL pid={a.pid} ' in line:
        tick = int(re.search(r' tick=(\d+)', line)[1])
        assert latest_metrics is not None
        points.append((tick, latest_metrics.copy()))
        positions[tick] = position
bounded = [(t, m) for t, m in points if a.start_tick < t <= a.end_tick]
assert len(bounded) >= 4 and bounded[-1][0] == a.end_tick
before = next(m for t, m in points if t == a.start_tick)
assert all(m['width'] == '1920' and m['height'] == '1200' for _, m in bounded)
seconds = sum(int(m['interval_ms']) for _, m in bounded) / 1000
assert 20 <= seconds <= 40
captured = int(bounded[-1][1]['total'].split('/')[0]) - int(before['total'].split('/')[0])
encoded = int(bounded[-1][1]['total'].split('/')[1]) - int(before['total'].split('/')[1])
lines = text.splitlines()[positions[a.start_tick]+1:positions[a.end_tick]+1]
android = [dict(re.findall(r'(\w+)=([^ ]+)', s)) for s in lines if s.startswith('H264 Surface ')]
assert len(android) >= 15 and all(s['codec'] == 'c2.mtk.avc.decoder' for s in android)
counts = [tuple(map(int, s['total'].split('/'))) for s in android]
assert all(all(y >= x for x, y in zip(old, new)) for old, new in zip(counts, counts[1:]))
assert all(c[2] <= c[1] <= c[0] and c[0]-c[2] <= 2 for c in counts)
android_seconds = sum((counts[i][0]-counts[i-1][0])/float(android[i]['receive']) for i in range(1,len(counts)) if float(android[i]['receive']) > 0)
assert android_seconds > 15
result = {
    'source': 'real Default Desktop / primary IddCx / ordinary Explorer dragging',
    'guest_pid': a.pid, 'start_tick': a.start_tick, 'end_tick': a.end_tick,
    'source_seconds': seconds, 'captured_frames': captured, 'encoded_frames': encoded,
    'captured_fps': captured/seconds, 'encoded_fps': encoded/seconds,
    'source_five_second_fps': [float(m['captured_fps']) for _,m in bounded],
    'android_samples': len(android), 'android_counter_interval_seconds': android_seconds,
    'android_received_fps': (counts[-1][0]-counts[0][0])/android_seconds,
    'android_decoded_fps': (counts[-1][1]-counts[0][1])/android_seconds,
    'android_render_callback_fps': (counts[-1][2]-counts[0][2])/android_seconds,
    'max_pending': max(int(s['pending']) for s in android),
    'receive_to_render_ms_mean': sum(float(s['receive_to_render_ms']) for s in android)/len(android),
    'physical_presented_30s_verified': False,
    'unique_pixel_frames_30s_verified': False,
    'claim': 'Real interactive source/callback progress only; not a sustained physical 60FPS PASS',
}
a.output.write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result))
