"""Read-only post-experiment regression; does not start graphics workloads."""
import json, subprocess, urllib.request
report={}
for name,args in [
    ('removed_unit',['systemctl','is-enabled','uavf-vulkan-ring-probe.service']),
    ('encoded_journal',['sudo','-n','journalctl','-b','--no-pager','-u','uavf-encoded@*']),
    ('gnome',['pgrep','-a','gnome-shell'])]:
    p=subprocess.run(args,text=True,capture_output=True,timeout=20)
    report[name]={'exit':p.returncode,'stdout':p.stdout[-24000:],'stderr':p.stderr}
urllib.request.urlopen(urllib.request.Request('http://10.159.93.215:39054/report',
    data=json.dumps(report,indent=2).encode(),method='POST'),timeout=15).read()
print('ROLLBACK_REGRESSION_SAVED')
