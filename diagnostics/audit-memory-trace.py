"""Small wire-format audit of one Perfetto capability capture, no dependencies.

Field IDs from google/perfetto trace_packet.proto and ftrace/*.proto.
This is a capability counter, not a general replacement for Trace Processor.
"""
import collections, json, pathlib, sys, zlib

def fields(data):
    pos=0
    def varint():
        nonlocal pos
        value=shift=0
        while True:
            b=data[pos]; pos+=1; value |= (b & 127) << shift
            if b < 128: return value
            shift+=7
            if shift>70: raise ValueError('invalid varint')
    while pos<len(data):
        key=varint(); number,wire=key>>3,key&7
        if wire==0: value=varint()
        elif wire==2:
            size=varint(); value=data[pos:pos+size]; pos+=size
        elif wire in (1,5):
            size=8 if wire==1 else 4; value=data[pos:pos+size]; pos+=size
        else: raise ValueError('unsupported wire type')
        yield number,value

counts=collections.Counter(); errors=[]; console=[]; compact_bundles=0
def packets(data):
    global compact_bundles
    for number,packet in fields(data):
        if number!=1: continue
        for kind,value in fields(packet):
            if kind==50: packets(zlib.decompress(value))
            elif kind==34:
                for f,v in fields(value):
                    if f in (5,6,7,13): errors.append({'field':f,'value':v.decode(errors='replace')})
            elif kind==1:
                for f,event in fields(value):
                    if f==4 and event: compact_bundles+=1
                    if f!=2: continue
                    for typ,payload in fields(event):
                        if typ in (4,327,329,330,408): counts[typ]+=1
                        if typ==408: console.append([(x,str(y)) for x,y in fields(payload)])
packets(pathlib.Path(sys.argv[1]).read_bytes())
print(json.dumps({'event_counts':dict(counts),'compact_sched_bundles':compact_bundles,'setup_errors':errors,
                  'console_samples':console[:5]},indent=2))
