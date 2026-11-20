"""Isolated helper test: synthetic bytes to stdout, NEVER a VM descriptor."""
import argparse
import base64
import subprocess
import tempfile
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--binary',required=True);a=p.parse_args()
remote='/data/local/tmp/uavf-ingress-written-trace-test'
subprocess.run([a.adb,'push',a.binary,remote],check=True,capture_output=True)
subprocess.run([a.adb,'shell','chmod','755',remote],check=True,capture_output=True)
print(subprocess.check_output([a.adb,'shell',remote,'--pacing-selftest'],text=True).strip())
for paced in (False,True):
    payload=bytes(range(256))*(8 if paced else 1000)
    lengths=[1,2,13,1024,3072];encoded=[];at=0
    while at<len(payload):
        length=lengths[len(encoded)%len(lengths)]
        encoded.append(base64.b64encode(payload[at:at+length])+b'\r\n');at+=length
    written='/data/local/tmp/uavf-ingress-written-trace-test.bin'
    # SDK exec-out does not reliably deliver stdin EOF; use a closed fixture.
    with tempfile.TemporaryDirectory(prefix='uavf-uart-test-') as task_temp:
        fixture=Path(task_temp)/'synthetic.b64';fixture.write_bytes(b''.join(encoded))
        subprocess.run([a.adb,'push',str(fixture),remote+'.b64'],check=True,capture_output=True)
    command=remote+' --written-log '+written+(' --paced' if paced else '')+' < '+remote+'.b64 > '+remote+'.out 2> '+remote+'.report'
    proc=subprocess.run([a.adb,'shell',command],capture_output=True,timeout=45)
    assert proc.returncode==0,proc.stderr[-1000:]
    stdout=subprocess.check_output([a.adb,'exec-out','cat',remote+'.out'])
    assert stdout==payload,(len(stdout),len(payload))
    log=subprocess.check_output([a.adb,'exec-out','cat',written])
    assert log==payload,(len(log),len(payload))
    report=subprocess.check_output([a.adb,'exec-out','cat',remote+'.report'])
    assert b'UART_PIPE_ACCEPT' in report
    print('UART_HELPER_EXACT_SYNTHETIC_STDOUT_AND_POST_WRITE_LOG=PASS paced='+str(paced))
