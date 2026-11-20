#include <stdint.h>
#include <assert.h>
#include <stdio.h>
#include "windows-frame-pacer.h"
int main(void){
    WindowsFramePacer p={0};int64_t frequency=13000000;
    for(int i=0;i<10000;i++)assert(frame_pacer_deadline(&p,i,frequency,60)==i&&p.frames==0);
    frame_pacer_sent(&p,1000000);assert(p.frames==1);
    for(int i=0;i<10000;i++)assert(frame_pacer_deadline(&p,1000000+i,frequency,60)==1216667&&p.frames==1);
    int64_t after_idle=100000000;
    assert(frame_pacer_deadline(&p,after_idle,frequency,60)<after_idle);
    frame_pacer_sent(&p,after_idle);
    assert(frame_pacer_deadline(&p,after_idle,frequency,60)==after_idle+216667);
    assert(frame_pacer_deadline(&p,after_idle,frequency,120)==after_idle+108334);
    assert(frame_pacer_deadline(&p,1,frequency,60)==1); /* Counter reset cannot stall. */
    puts("ACTUAL_FRAME_PACER_POLL_INDEPENDENCE_IDLE_NO_CATCHUP_60_120=PASS");return 0;
}
