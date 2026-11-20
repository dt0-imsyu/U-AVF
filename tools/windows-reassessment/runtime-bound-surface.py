"""Pure diagnostic-retention gate; callbacks are not a physical FPS claim."""
import re


def snapshot(text):
    samples=re.findall(r'H264 Surface (\d+)x(\d+) codec=(\S+).*?pending=(\d+).*?total=(\d+)/(\d+)/(\d+)',text)
    return tuple(samples[-1]) if samples else None


def progressing(before,after):
    first,second=snapshot(before),snapshot(after)
    if not first or not second or first[:3]!=second[:3]:return False
    if int(first[3])>2 or int(second[3])>2:return False
    initial=list(map(int,first[4:]));final=list(map(int,second[4:]))
    # Reject stale screenshots, stalled decode, counter resets/reconnects and
    # backlog. At most two in-flight frames; normal streaming may have one.
    # New receive+decode+render callbacks must ALL progress.
    return (all(b-a>=2 for a,b in zip(initial,final)) and final[2]<=final[1]<=final[0]
            and final[0]-final[2]<=2 and initial[0]-initial[2]<=2)


if __name__=='__main__':
    def sample(values,pending=0,size='640x480'):
        return f'H264 Surface {size} codec=c2.mtk.avc.decoder receive=15 pending={pending} receive_to_render_ms=7 total={values}'
    assert progressing(sample('10/10/10'),sample('25/25/25'))
    assert progressing(sample('11/10/10',pending=1),sample('26/25/25',pending=1))
    for after in (sample('10/10/10'),sample('25/10/10'),sample('1/1/1'),
                  sample('25/25/25',pending=3),sample('25/25/25',size='1920x1200'),'connecting'):
        assert not progressing(sample('10/10/10'),after)
    print('RUNTIME_BOUND_FRESH_SURFACE_RETENTION_GUARDS=PASS')
