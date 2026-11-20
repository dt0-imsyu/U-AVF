"""Bounded read-only KD GetVersion probe, without DbgEng auto-resets/breakpoints."""
import base64, json, queue, struct, subprocess, sys, threading, time
from pathlib import Path

adb, runner, directory = sys.argv[1:]
root = Path(directory)
root.mkdir(parents=True, exist_ok=True)
proc = subprocess.Popen([adb, 'shell', '-T', 'sh '+runner], stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
incoming = queue.Queue()
def reader():
    for line in proc.stdout:
        if line.startswith(b'O:'):
            incoming.put(base64.b64decode(line[2:]))
threading.Thread(target=reader, daemon=True).start()
rx, tx, buffer = bytearray(), bytearray(), bytearray()
events = []
gate = sent = False
read_stage = 0
module_list = None
def write(packet):
    tx.extend(packet)
    proc.stdin.write(base64.b64encode(packet)+b'\n')
    proc.stdin.flush()
def control(kind, pid=0):
    write(struct.pack('<IHHII', 0x69696969, kind, 0, pid, 0))
deadline = time.monotonic()+45
try:
    while time.monotonic()<deadline:
        try: data=incoming.get(timeout=.2)
        except queue.Empty: continue
        rx.extend(data)
        buffer.extend(data)
        if not gate:
            if b'WG:LOAD=PASS,START_WINDOWS' not in rx: continue
            gate=True
            control(6)
        while len(buffer)>=16:
            if buffer[:4] not in (b'0000', b'iiii'):
                del buffer[0]
                continue
            _,kind,n,pid,checksum=struct.unpack_from('<IHHII',buffer)
            length=16 if buffer[:4]==b'iiii' else 17+n
            if n>16384:
                del buffer[0]
                continue
            if len(buffer)<length: break
            body=bytes(buffer[16:16+n])
            if length>16 and (sum(body)!=checksum or buffer[length-1]!=0xaa):
                del buffer[0]
                continue
            del buffer[:length]
            event=dict(kind=kind,id=hex(pid),size=n,body=body.hex())
            events.append(event)
            print(json.dumps(event),flush=True)
            if length==16: continue
            control(4,pid & ~0x800)
            if kind==7 and not sent:
                # DBGKD_MANIPULATE_STATE64: GetVersion=0x3146, union at +16.
                request=struct.pack('<IHHI',0x3146,0,0,0)+bytes(44)
                write(struct.pack('<IHHII',0x30303030,2,56,0x80800000,sum(request))+request+b'\xaa')
                sent=True
            elif kind==2 and body[:4]==struct.pack('<I',0x3146):
                print('GET_VERSION_RESPONSE=PASS',flush=True)
                module_list=struct.unpack_from('<Q',body,40)[0]
                request=struct.pack('<IHHI',0x3130,0,0,0)+bytes(4)+struct.pack('<QII',module_list,1,0)+bytes(24)
                write(struct.pack('<IHHII',0x30303030,2,56,0x80800001,sum(request))+request+b'\xaa')
                read_stage=1
            elif kind==2 and body[:4]==struct.pack('<I',0x3130):
                print('READ_RESPONSE_STAGE',read_stage,'STATUS',hex(struct.unpack_from('<I',body,8)[0]),'BYTES',len(body)-56,flush=True)
                if read_stage==1:
                    request=struct.pack('<IHHI',0x3130,0,0,0)+bytes(4)+struct.pack('<QII',module_list,16,0)+bytes(24)
                    write(struct.pack('<IHHII',0x30303030,2,56,0x80800000,sum(request))+request+b'\xaa')
                    read_stage=2
                else: deadline=time.monotonic()
finally:
    proc.stdin.close()
    try:proc.wait(timeout=90)
    except subprocess.TimeoutExpired:proc.kill();proc.wait()
    (root/'rx.bin').write_bytes(rx)
    (root/'tx.bin').write_bytes(tx)
    (root/'events.json').write_text(json.dumps(events,indent=2))
    print('PROBE_EXIT',proc.returncode)
