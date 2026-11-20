"""Read-only JDWP recovery of this diagnostic APK's own live owner token.

No VM suspend, code execution or field writes. Do not print the token.
"""
import re
import socket
import struct
import sys
from pathlib import Path

target=Path(sys.argv[1]);assert not target.exists()
sock=socket.create_connection(('127.0.0.1',39107),timeout=5);sock.settimeout(5)
def read(n):
    out=bytearray()
    while len(out)<n:
        part=sock.recv(n-len(out))
        if not part:raise EOFError()
        out.extend(part)
    return bytes(out)
sock.sendall(b'JDWP-Handshake');assert read(14)==b'JDWP-Handshake'
sequence=0
def call(command_set,command,payload=b''):
    global sequence
    sequence+=1
    sock.sendall(struct.pack('>IIBBB',11+len(payload),sequence,0,command_set,command)+payload)
    for _ in range(8):
        header=read(11);length,ident,flags=struct.unpack_from('>IIB',header)
        assert 11<=length<1048576
        body=read(length-11)
        if flags==0x80 and ident==sequence:
            error=struct.unpack_from('>H',header,9)[0]
            if error:raise RuntimeError(f'JDWP read {command_set}/{command} error={error}')
            return body
    raise RuntimeError('Reply bound')
def string(value):
    data=value.encode();return struct.pack('>I',len(data))+data
def take_string(data,offset):
    n=struct.unpack_from('>I',data,offset)[0];return data[offset+4:offset+4+n].decode(),offset+4+n
try:
    field_size,method_size,object_size,reference_size,frame_size=struct.unpack('>5I',call(1,7))
    assert all(n in (4,8) for n in (field_size,object_size,reference_size))
    types=call(1,2,string('Lcom/example/winavf/WindowsVmOwnerService;'))
    assert struct.unpack_from('>I',types)[0]==1
    clazz=types[5:5+reference_size]
    fields=call(2,4,clazz);count=struct.unpack_from('>I',fields)[0];offset=4;token_field=None
    for _ in range(count):
        ident=fields[offset:offset+field_size];offset+=field_size
        name,offset=take_string(fields,offset);signature,offset=take_string(fields,offset);offset+=4
        if name=='token' and signature=='Ljava/lang/String;':token_field=ident
    assert token_field is not None
    instances=call(2,16,clazz+struct.pack('>I',2));assert struct.unpack_from('>I',instances)[0]==1
    owner=instances[5:5+object_size]
    value=call(9,2,owner+struct.pack('>I',1)+token_field)
    assert struct.unpack_from('>I',value)[0]==1 and value[4]==ord('s')
    token,_=take_string(call(10,1,value[5:5+object_size]),0)
    assert re.fullmatch('[0-9a-f]{64}',token)
    target.write_text(token+'\n')
    print('OWN_DIAGNOSTIC_OWNER_TOKEN_RECOVERED_READ_ONLY; no VM restart')
finally:sock.close()
