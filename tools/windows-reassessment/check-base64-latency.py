"""Observe stdin decode latency with stdin kept open (no VM involved)."""
import base64, queue, subprocess, threading, time, sys
command=sys.argv[1] if len(sys.argv)>1 else 'base64 -di'
p=subprocess.Popen(['adb','shell','-T',command],stdin=subprocess.PIPE,stdout=subprocess.PIPE)
q=queue.Queue()
threading.Thread(target=lambda:q.put(p.stdout.read(3)),daemon=True).start()
start=time.monotonic()
p.stdin.write(base64.b64encode(b'ABC'));p.stdin.flush()
received=False
try:
    data=q.get(timeout=2)
    received=True
    print('OPEN_STDIN_RESULT',repr(data),'MS',round(1000*(time.monotonic()-start)))
except queue.Empty:
    print('OPEN_STDIN_NO_OUTPUT_AFTER_2000MS')
p.stdin.close()
if not received:
    try: print('AFTER_EOF',repr(q.get(timeout=3)))
    except queue.Empty: print('NO_EOF_OUTPUT')
try:p.wait(timeout=3)
except subprocess.TimeoutExpired:p.kill()
