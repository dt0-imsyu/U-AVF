"""Summarize one common interval; actual presents are SF column two."""
import json, re, sys
from pathlib import Path

root=Path(sys.argv[1])
prefix=sys.argv[2] if len(sys.argv)>2 else 'surface-fixed'
guest=json.loads((root/(prefix+'-guest.json')).read_text())
window=guest['fixed_window']
android=(root/(prefix+'-android.txt')).read_text()
match=re.search(r'FIXED_WINDOW seconds=20 received=(\d+) decoded=(\d+) rendered_callbacks=(\d+)',android)
present=set()
frame_pairs=set()
layers=set()
for sample in json.loads((root/(prefix+'-sf.json')).read_text()):
    for name,data in sample['layers'].items():
        if '(BLAST)#' not in name: continue
        layers.add(name)
        for line in data.splitlines()[1:]:
            columns=line.split()
            if len(columns)!=3: continue
            desired,actual,ready=map(int,columns)
            if window['android_start_ns']<=actual<window['android_end_ns']:
                present.add(actual)
                frame_pairs.add((desired,actual))
result=dict(window)
seconds=int(window['seconds'])
start=window['android_start_ns']
present_per_second=[sum(start+i*1_000_000_000<=at<start+(i+1)*1_000_000_000
                        for at in present) for i in range(seconds)]
ordered=sorted(present)
gaps=sorted((b-a)/1e6 for a,b in zip(ordered,ordered[1:]))
result.update(android_received_fps=int(match[1])/20,mediacodec_decoded_fps=int(match[2])/20,
    surface_actual_present_fps=len(present)/20,
    surface_frame_records_fps=len(frame_pairs)/20,
    surface_layers=sorted(layers),
    raw_rfb_connections_after_stop=guest['raw_rfb_connections_after_stop'],
    raw_instances_stopped=guest['raw_instances_stopped'],
    clock_uncertainty_ms=guest['clock_uncertainty_ms'],
    actual_present_per_second=present_per_second,
    minimum_one_second_present_fps=min(present_per_second,default=0),
    present_gap_p95_ms=gaps[int(.95*(len(gaps)-1))] if gaps else None,
    present_gap_max_ms=max(gaps,default=0),
    full_screen_changing_workload_verified=bool(guest.get('motion',{}).get('fullscreen_geometry_verified'))
        and window.get('source_changed_sample_fps',0)>0,
    unique_content_present_fps_proven=False,
    rendered_callback_fixed_counter_valid=False,
    verdict='Not a full-screen unique-content sustained-60 acceptance; inspect one-second rates and content evidence')
(root/(prefix+'-summary.json')).write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
