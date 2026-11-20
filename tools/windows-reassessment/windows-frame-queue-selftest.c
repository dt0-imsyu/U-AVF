#include <windows.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include "windows-frame-queue.h"
static FrameQueue q;static volatile LONG producer_done;
static DWORD WINAPI producer(void *unused) {
    (void)unused;
    for(uint32_t n=1;n<=2000;n++) {
        int i=fq_reserve(&q);assert(i>=0);
        memset(q.slots[i].pixels,n%256,4096);memcpy(q.slots[i].pixels,&n,sizeof(n));
        fq_publish(&q,i,n,1,0.5);
        if(n%10==0)Sleep(0);
    }
    InterlockedExchange(&producer_done,1);SetEvent(q.ready);return 0;
}
int main(void) {
    assert(fq_init(&q,4096));
    int reserved=fq_reserve(&q);assert(reserved>=0);fq_cancel(&q,reserved);
    HANDLE thread=CreateThread(NULL,0,producer,NULL,0,NULL);assert(thread);
    uint64_t previous=0;unsigned count=0;
    for(;;) {
        FrameSlot slot;int i=fq_borrow(&q,&slot);
        if(i<0){if(InterlockedCompareExchange(&producer_done,0,0))break;WaitForSingleObject(q.ready,1000);continue;}
        assert(slot.sequence>previous&&slot.pts==slot.sequence);previous=slot.sequence;
        uint32_t tag=0;memcpy(&tag,slot.pixels,4);assert(tag==slot.sequence);
        Sleep(1); /* Producer must never overwrite this borrowed frame. */
        memcpy(&tag,slot.pixels,4);assert(tag==slot.sequence);
        for(int x=4;x<4096;x++)assert(slot.pixels[x]==slot.sequence%256);
        fq_return(&q,i);count++;
    }
    assert(WaitForSingleObject(thread,1000)==WAIT_OBJECT_0);CloseHandle(thread);
    assert(previous==2000&&q.published==2000&&q.consumed==count&&q.dropped+q.consumed==q.published);
    for(int i=0;i<3;i++)assert(q.slots[i].state==FQ_FREE);
    fq_destroy(&q);printf("ACTUAL_THREADED_LATEST_FRAME_OWNERSHIP=PASS published=2000 consumed=%u\n",count);return 0;
}
