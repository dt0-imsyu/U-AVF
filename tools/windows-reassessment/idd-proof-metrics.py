"""Decode our 80-byte ETW EventID1/version1 payload, never an ETL guess.
Source gate candidate is NOT Android-present or full-image uniqueness proof.
"""
import struct

def decode_timing(payload):
    if len(payload)!=128:raise ValueError('Exact timing payload required')
    v,status,freq,elapsed,frames,acquire,finish,wait,waits,pending,wakes,timeouts,gaps,repeats,max_acquire,max_wait=struct.unpack('<16Q',payload)
    if v!=2 or not freq or status>0xffffffff or repeats>frames or wakes+timeouts>waits:
        raise ValueError('Malformed timing metrics')
    seconds=elapsed/freq
    if seconds>3600:raise ValueError('Invalid interval')
    per_frame=lambda ticks: ticks*1000/freq/frames if frames else None
    return dict(status=hex(status),seconds=seconds,frames=frames,
                acquire_ms_per_frame=per_frame(acquire),finish_ms_per_frame=per_frame(finish),
                wait_ms_per_frame=per_frame(wait),waiting_fraction=wait/elapsed if elapsed else None,
                max_acquire_ms=max_acquire*1000/freq,max_wait_ms=max_wait*1000/freq,
                waits=waits,pending_calls=pending,event_wakes=wakes,timeouts=timeouts,
                presentation_number_gaps=gaps,repeated_presentations=repeats,
                android_present_pass=False,whole_image_hash_verified=False)

def decode(payload):
    if len(payload)!=80:
        raise ValueError('Exact own ETW payload required')
    version,status,freq,elapsed,frames,unique,read_ticks,width,height,pitch=struct.unpack('<10Q',payload)
    if version!=1 or status>0xffffffff or unique>frames or not freq:
        raise ValueError('Malformed metrics')
    seconds=elapsed/freq
    if seconds<0 or seconds>3600:
        raise ValueError('Invalid interval')
    gate=(status==0 and 20<=seconds<=35 and width==1920 and height==1200
          and 7680<=pitch<=16384 and unique/seconds>=58)
    return dict(status=hex(status),seconds=seconds,frames=frames,
                unique_presentations=unique,
                frames_per_second=frames/seconds if seconds else None,
                unique_presentations_per_second=unique/seconds if seconds else None,
                read_ms_per_frame=read_ticks*1000/freq/frames if frames else None,
                width=width,height=height,pitch=pitch,source_gate_candidate=gate,
                android_present_pass=False,whole_image_hash_verified=False)

def selftest():
    def data(*args):return struct.pack('<10Q',*args)
    assert decode(data(1,0,1000,30000,1800,1800,1800,1920,1200,7680))['source_gate_candidate']
    assert not decode(data(1,0,1000,30000,1800,900,1800,1920,1200,7680))['source_gate_candidate']
    assert not decode(data(1,0,1000,1000,60,60,60,1920,1200,7680))['source_gate_candidate']
    assert not decode(data(1,0x80004002,1000,30000,0,0,0,0,0,0))['source_gate_candidate']
    assert decode(data(1,0,1000,0,0,0,0,0,0,0))['frames_per_second'] is None
    for invalid in (b'',data(2,0,1000,30000,1,1,0,1920,1200,7680),
                    data(1,0,0,30000,1,1,0,1920,1200,7680),
                    data(1,0,1000,30000,1,2,0,1920,1200,7680)):
        try:decode(invalid)
        except ValueError:pass
        else:raise AssertionError('Malformed payload accepted')
    print('IDD_ETW_METRIC_HOST_TESTS_PASS; NOT_REAL_CAPTURE_OR_FPS_PASS=1')
    t=decode_timing(struct.pack('<16Q',2,0,1000,30000,1500,1500,1500,27000,1500,1500,1500,0,40,0,2,30))
    assert t['acquire_ms_per_frame']==1 and t['waiting_fraction']==0.9
    assert t['presentation_number_gaps']==40 and not t['android_present_pass']
    for bad in (b'',struct.pack('<16Q',2,0,0,*([0]*13)),
                struct.pack('<16Q',2,0,1000,1,0,0,0,0,1,1,2,0,0,0,0,0)):
        try:decode_timing(bad)
        except ValueError:pass
        else:raise AssertionError('Malformed timing accepted')
    print('IDD_TIMING_METRIC_HOST_TESTS_PASS; NOT_RUNTIME_PASS=1')

if __name__=='__main__':
    import argparse,json
    p=argparse.ArgumentParser()
    p.add_argument('--payload-hex')
    p.add_argument('--timing-hex')
    a=p.parse_args()
    if a.timing_hex:
        if len(a.timing_hex)!=256:raise ValueError('Exact bounded timing only')
        print(json.dumps(decode_timing(bytes.fromhex(a.timing_hex)),indent=2))
    elif a.payload_hex:
        if len(a.payload_hex)!=160:raise ValueError('Exact bounded payload only')
        print(json.dumps(decode(bytes.fromhex(a.payload_hex)),indent=2))
    else:selftest()
