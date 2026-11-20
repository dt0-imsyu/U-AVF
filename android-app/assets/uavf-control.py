#!/usr/bin/env python3
"""UCTL v1 on systemd-accepted vsock4056, host CID2 only. No arbitrary RPC."""
import json
import os
import socket
import struct
import subprocess
import sys
import fcntl

HEADER=struct.Struct('<4sHHI')
MAX=262144
TYPES={'HELLO','PING','SESSION_INFO','STORAGE_INFO','SHUTDOWN','REBOOT','CLIPBOARD_SET','CLIPBOARD_GET'}

def storage_info(grow=False):
    mount=subprocess.run(['findmnt','-n','-o','SOURCE,FSTYPE','/'],capture_output=True,text=True,check=True,timeout=3).stdout.split()
    if mount!=['/dev/vda3','ext4']:
        return {'growth_supported':False,'reason':'Requires managed vda3 ext4 root'}
    if os.geteuid()!=0 or not os.path.isfile('/usr/sbin/resize2fs'):
        return {'growth_supported':False,'reason':'Root growth helper unavailable'}
    partition_bytes=int(open('/sys/class/block/vda3/size').read())*512
    with open('/dev/vda3','rb',buffering=0) as device:
        device.seek(1024);sb=device.read(1024)
    if len(sb)!=1024 or struct.unpack_from('<H',sb,56)[0]!=0xef53:
        raise ValueError('Root superblock is not ext4')
    blocks=struct.unpack_from('<I',sb,4)[0]
    if struct.unpack_from('<I',sb,96)[0]&0x80: blocks|=struct.unpack_from('<I',sb,336)[0]<<32
    log_block_size=struct.unpack_from('<I',sb,24)[0]
    if log_block_size>6 or blocks==0: raise ValueError('Invalid ext4 geometry')
    filesystem_bytes=blocks*(1024<<log_block_size)
    if filesystem_bytes>partition_bytes: raise ValueError('Filesystem exceeds root partition')
    if grow and partition_bytes-filesystem_bytes>=1024*1024:
        # Multiple socket clients cannot resize concurrently; no device/path comes from APK.
        with open('/dev/vda3','rb',buffering=0) as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            # stdout is the binary UCTL socket, never a command console.
            subprocess.run(['/usr/sbin/resize2fs','/dev/vda3'],check=True,timeout=300,
                           capture_output=True,text=True)
        return storage_info(False)
    return {'growth_supported':True,'growth_version':1,'partition_bytes':partition_bytes,'filesystem_bytes':filesystem_bytes}

def exact(stream,size):
    data=bytearray()
    while len(data)<size:
        chunk=stream.read(size-len(data))
        if not chunk: raise EOFError()
        data.extend(chunk)
    return bytes(data)

def read_message(stream):
    magic,version,flags,size=HEADER.unpack(exact(stream,HEADER.size))
    if magic!=b'UCTL' or version!=1 or flags or not 2<=size<=MAX: raise ValueError('header')
    request=json.loads(exact(stream,size).decode('utf-8'))
    if not isinstance(request,dict) or request.get('version')!=1 or type(request.get('id')) is not int or request.get('type') not in TYPES:
        raise ValueError('request')
    return request

def packet(response):
    data=json.dumps(response,ensure_ascii=False,separators=(',',':')).encode('utf-8')
    if len(data)>MAX: raise ValueError('response size')
    return HEADER.pack(b'UCTL',1,0,len(data))+data

def handle(request):
    kind=request['type']
    result={'version':1,'id':request['id'],'type':kind,'ok':True}
    if kind=='HELLO': result.update(agent='uavf-control',agent_version=2,capabilities=['PING','SESSION_INFO','STORAGE_INFO','SHUTDOWN','REBOOT'])
    elif kind=='PING': result['reply']='PONG'
    elif kind=='SESSION_INFO':
        sessions=subprocess.run(['loginctl','list-sessions','--no-legend'],capture_output=True,text=True,timeout=2)
        result.update(vcpu=os.cpu_count(),sessions=sessions.stdout[:8192],sessions_available=sessions.returncode==0)
        try: result.update(storage_info())
        except (OSError,ValueError,subprocess.SubprocessError): result.update(growth_supported=False)
    elif kind=='STORAGE_INFO': result.update(storage_info())
    elif kind in ('CLIPBOARD_SET','CLIPBOARD_GET'):
        # Preserve already proven UCIP4054 agent until its explicit migration.
        result.update(ok=False,error='Clipboard uses the separate UCIP4054 channel')
    elif kind in ('SHUTDOWN','REBOOT'):
        if os.geteuid()!=0: result.update(ok=False,error='Power agent requires root service')
    return result

def main():
    peer=socket.socket(fileno=os.dup(0))
    if peer.family!=socket.AF_VSOCK or peer.getpeername()[0]!=2: raise PermissionError('host CID2 required')
    peer.close()
    # New partition geometry is visible on a fresh boot. The app never reports
    # filesystem completion until SESSION_INFO confirms both sizes match.
    try: storage_info(True)
    except (OSError,ValueError,subprocess.SubprocessError) as error:
        print('UAVF_STORAGE_GROW_ERROR='+type(error).__name__,file=sys.stderr)
    source=os.fdopen(os.dup(0),'rb',buffering=0)
    sink=os.fdopen(os.dup(1),'wb',buffering=0)
    while True:
        request=read_message(source); response=handle(request)
        data=memoryview(packet(response))
        while data:
            n=sink.write(data)
            if not n: raise EOFError()
            data=data[n:]
        if request['type'] in ('SHUTDOWN','REBOOT') and response['ok']:
            # ACK is fully written before systemd starts the power transition.
            subprocess.run(['systemctl','--no-block','reboot' if request['type']=='REBOOT' else 'poweroff'],check=True,timeout=3)
            return

if __name__=='__main__':
    try: main()
    except EOFError: pass
