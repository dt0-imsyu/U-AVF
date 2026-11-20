"""Byte-exact padded/fragmented ingress test; no VM or disk writes."""
import base64,queue,subprocess,threading,sys,time
adb=sys.argv[1];command=sys.argv[2]
payload=bytes(range(256))*16
wire=b''.join(base64.b64encode(payload[i:i+17])+b'\n' for i in range(0,len(payload),17))
p=subprocess.Popen([adb,'shell','-T',command+' | base64'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
# Windows adb raw stdout expands LF to CRLF; keep this return leg armored too.
q=queue.Queue();threading.Thread(target=lambda:q.put(p.stdout.read()),daemon=True).start()
start=time.monotonic()
for i in range(0,len(wire),113):p.stdin.write(wire[i:i+113]);p.stdin.flush()
p.stdin.close()
try:
    actual=base64.b64decode(q.get(timeout=5))
    if actual!=payload:
        first=next((i for i,(x,y) in enumerate(zip(actual,payload)) if x!=y),min(len(actual),len(payload)))
        print('LENGTH',len(actual),'FIRST_MISMATCH',first,'ACTUAL',actual[max(0,first-4):first+20].hex(),'EXPECTED',payload[max(0,first-4):first+20].hex())
    assert actual==payload,'decoded bytes differ'
    print('PADDED_FRAGMENTED_4096=PASS MS='+str(round(1000*(time.monotonic()-start))))
finally:
    try:p.wait(timeout=3)
    except subprocess.TimeoutExpired:p.kill()
    print('EXIT',p.returncode,'STDERR',p.stderr.read().decode(errors='replace'))
assert p.returncode==0,'decoder exit '+str(p.returncode)
