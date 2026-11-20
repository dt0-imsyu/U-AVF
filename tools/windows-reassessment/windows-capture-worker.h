#include "windows-frame-queue.h"
typedef struct {FrameQueue queue;DxgiCapture *direct;HANDLE thread;
    volatile LONG stop,error;int width,height;uint64_t started,updates;
    DxgiCapture metrics;} CaptureWorker;
static void capture_worker_stats(CaptureWorker *c) {
    EnterCriticalSection(&c->queue.lock);
    /* Only the capture thread touches live DXGI state. Consumers copy this
     * published snapshot, never dereference its COM pointers or timer. */
    c->metrics=*c->direct;
    LeaveCriticalSection(&c->queue.lock);
}
static DWORD WINAPI capture_worker_run(void *opaque) {
    CaptureWorker *c=opaque;LARGE_INTEGER frequency;QueryPerformanceFrequency(&frequency);
    while(!InterlockedCompareExchange(&c->stop,0,0)) {
        int slot=fq_reserve(&c->queue);if(slot<0){Sleep(1);continue;}
        LARGE_INTEGER start,convert,end;QueryPerformanceCounter(&start);
        DXGI_MAPPED_RECT map={0};UINT updates=0;
        HRESULT status=dxgi_map_frame(c->direct,c->width,&map,&updates);
        if(status==DXGI_ERROR_WAIT_TIMEOUT){fq_cancel(&c->queue,slot);capture_worker_stats(c);continue;}
        if(FAILED(status)){fq_cancel(&c->queue,slot);InterlockedExchange(&c->error,status);SetEvent(c->queue.ready);return 1;}
        if(!updates){dxgi_release_frame(c->direct);fq_cancel(&c->queue,slot);capture_worker_stats(c);continue;}
        QueryPerformanceCounter(&convert);
        memcpy(&c->queue.slots[slot].sampled_color,map.pBits+(size_t)(c->height/2)*map.Pitch+4*(c->width/2),4);
        c->queue.slots[slot].sampled_color&=0xffffff;
        i420_pitch(map.pBits,c->queue.slots[slot].pixels,c->width,c->height,map.Pitch);
        dxgi_release_frame(c->direct);QueryPerformanceCounter(&end);
        EnterCriticalSection(&c->queue.lock);c->updates+=updates;LeaveCriticalSection(&c->queue.lock);
        capture_worker_stats(c);
        fq_publish(&c->queue,slot,GetTickCount64()-c->started,
            (end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart,
            (end.QuadPart-convert.QuadPart)*1000.0/frequency.QuadPart);
    }
    return 0;
}
static bool capture_worker_start(CaptureWorker *c,DxgiCapture *direct,int w,int h,uint64_t started) {
    memset(c,0,sizeof(*c));c->direct=direct;c->width=w;c->height=h;c->started=started;
    if(!fq_init(&c->queue,(size_t)w*h*3/2)){fq_destroy(&c->queue);return false;}
    c->thread=CreateThread(NULL,0,capture_worker_run,c,0,NULL);
    if(!c->thread){fq_destroy(&c->queue);return false;}return true;
}
static void capture_worker_snapshot(CaptureWorker *c,DxgiCapture *stats,uint64_t *updates,uint64_t *published,uint64_t *dropped) {
    EnterCriticalSection(&c->queue.lock);
    *stats=c->metrics;
    *updates=c->updates;*published=c->queue.published;*dropped=c->queue.dropped;
    LeaveCriticalSection(&c->queue.lock);
}
static bool capture_worker_stop(CaptureWorker *c) {
    InterlockedExchange(&c->stop,1);
    if(WaitForSingleObject(c->thread,15000)!=WAIT_OBJECT_0)return false;
    CloseHandle(c->thread);c->thread=NULL;return true;
}
