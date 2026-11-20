"""Two bounded KD RAM observations, not a capture backend or FPS measurement."""
import re
import struct
import time


def geometry(raw):
    text = raw.decode('ascii', errors='ignore')
    values = {}
    for name in ('BASE', 'MODE', 'WIDTH', 'HEIGHT'):
        found = re.findall(r'WG:EBS_GOP_' + name + r'=([0-9a-fA-F]{16})', text)
        assert len(found) == 1, 'One exact fresh EBS geometry required'
        values[name] = int(found[0], 16)
    assert values['MODE'] == 0 and values['WIDTH'] == 1920 and values['HEIGHT'] == 1200
    base = values['BASE']
    assert base % 4096 == 0 and 0x80000000 <= base <= 0x100000000 - 9216000
    return base


def active_workload(report):
    if not re.search(r'SOURCE_STAGE=\S+[^\r\n]*desktop=Default station=WinSta0', report):
        return False
    values = re.findall(r'SYNTHETIC_FULL_REPAINT paints=(\d+) elapsed_ms=(\d+) thread_exit=(\d+)', report)
    return bool(values and int(values[-1][0]) > 1 and int(values[-1][1]) < 25000 and int(values[-1][2]) == 259)


class Audit:
    def __init__(self):
        self.samples = []
        self.next_due = 0

    def observe(self, kd, raw):
        assert len(self.samples) < 2, 'Exactly two observations maximum'
        base = geometry(raw)
        if self.samples:
            assert self.samples[0]['base'] == hex(base), 'Same owner/framebuffer required'
        # Eight pixels away from the caption; exactly32 bytes/observation.
        points = ((120, 200), (960, 200), (1800, 200), (120, 600),
                  (960, 600), (1800, 600), (120, 1000), (1800, 1000))
        pixels = []
        for x, y in points:
            address = base + (y * 1920 + x) * 4
            assert base <= address <= base + 9216000 - 4
            read = kd.physical_sample(address)
            if read['status'] != '0x0' or len(read['data']) != 8:
                result = dict(base=hex(base), result='READ_UNAVAILABLE', sample=read,
                              not_a_fps_measurement=True)
                self.samples.append(result)
                return result
            pixels.append(dict(x=x, y=y, bgra=read['data']))
        result = dict(base=hex(base), result='PIXELS_READ', pixels=pixels,
                      observed_host_monotonic=time.monotonic(), not_a_fps_measurement=True)
        result['workload_color_matches'] = sum(
            bytes.fromhex(p['bgra'])[1] == 128 and
            bytes.fromhex(p['bgra'])[0] + bytes.fromhex(p['bgra'])[2] == 255
            for p in pixels)
        if self.samples:
            old = self.samples[0]
            assert old['result'] == 'PIXELS_READ'
            result['changed_pixels'] = sum(a['bgra'] != b['bgra'] for a, b in zip(old['pixels'], pixels))
        self.samples.append(result)
        self.next_due = time.monotonic() + 3
        return result


if __name__ == '__main__':
    marker = b'WG:EBS_GOP_BASE=00000000ff700000\nWG:EBS_GOP_MODE=0000000000000000\nWG:EBS_GOP_WIDTH=0000000000000780\nWG:EBS_GOP_HEIGHT=00000000000004b0\n'
    class Mock:
        def __init__(self): self.calls = []; self.value = 1
        def physical_sample(self, address):
            self.calls.append(address)
            return dict(address=hex(address), status='0x0', data=struct.pack('<I', self.value).hex())
    mock = Mock(); audit = Audit(); audit.observe(mock, marker); mock.value = 2
    assert audit.observe(mock, marker)['changed_pixels'] == 8 and len(mock.calls) == 16
    try: audit.observe(mock, marker); raise RuntimeError('Third sample accepted')
    except AssertionError: pass
    for bad in (marker + marker, marker.replace(b'ff700000', b'00001000'), marker.replace(b'00000780', b'00000500')):
        try: geometry(bad); raise RuntimeError('Invalid geometry accepted')
        except AssertionError: pass
    good='SOURCE_STAGE=ENCODER_READY pid=1 desktop=Default station=WinSta0\nSYNTHETIC_FULL_REPAINT paints=20 elapsed_ms=5016 thread_exit=259'
    assert active_workload(good)
    for bad in (good.replace('Default','Winlogon'),good.replace('5016','30000'),good.replace('259','0'),''):
        assert not active_workload(bad)
    class Unavailable:
        def physical_sample(self, address):
            return dict(address=hex(address),status='0xc0000001',data='')
    assert Audit().observe(Unavailable(),marker)['result']=='READ_UNAVAILABLE'
    print('ACTUAL_RAM_FRAMEBUFFER_FRESH_GEOMETRY_RANGE_TWO_SAMPLES_BOUND=PASS; NO_DEVICE_WRITES')
