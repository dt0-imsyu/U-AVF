#define UAVF_OVERLAPPED_CAPTURE 1
#define wmain encoder_real_wmain
#include "winpe-encode.c"
#undef wmain
#include <assert.h>
static unsigned char pixels[16];static LONG generation,fail_acquire;
static int held,mapped,released;
static HRESULT STDMETHODCALLTYPE acquire(IDXGIOutputDuplication *d,UINT timeout,DXGI_OUTDUPL_FRAME_INFO *info,IDXGIResource **r) {
    (void)d;assert(timeout==16&&!held&&!mapped);Sleep(1);*r=NULL;
    if(InterlockedCompareExchange(&fail_acquire,0,0))return DXGI_ERROR_ACCESS_LOST;
    unsigned char c=(unsigned char)InterlockedIncrement(&generation);
    for(int i=0;i<4;i++){pixels[i*4]=pixels[i*4+1]=pixels[i*4+2]=c;pixels[i*4+3]=255;}
    held=1;info->AccumulatedFrames=1;return S_OK;
}
static HRESULT STDMETHODCALLTYPE map(IDXGIOutputDuplication *d,DXGI_MAPPED_RECT *rect){(void)d;assert(held&&!mapped);mapped=1;rect->Pitch=8;rect->pBits=pixels;return S_OK;}
static HRESULT STDMETHODCALLTYPE unmap(IDXGIOutputDuplication *d){(void)d;assert(held&&mapped);mapped=0;return S_OK;}
static HRESULT STDMETHODCALLTYPE release_frame(IDXGIOutputDuplication *d){(void)d;assert(held&&!mapped);held=0;return S_OK;}
static ULONG STDMETHODCALLTYPE release_object(IDXGIOutputDuplication *d){(void)d;assert(!held&&!mapped);released++;return 0;}
int wmain(void) {
    IDXGIOutputDuplicationVtbl table={0};table.AcquireNextFrame=acquire;table.MapDesktopSurface=map;
    table.UnMapDesktopSurface=unmap;table.ReleaseFrame=release_frame;table.Release=release_object;
    IDXGIOutputDuplication object={&table};DxgiCapture direct={0};direct.dup=&object;
    CaptureWorker worker;assert(capture_worker_start(&worker,&direct,2,2,GetTickCount64()));
    uint64_t previous=0;
    for(int n=0;n<8;n++) {
        FrameSlot slot;int index;
        while((index=fq_borrow(&worker.queue,&slot))<0)assert(WaitForSingleObject(worker.queue.ready,1000)==WAIT_OBJECT_0);
        assert(slot.sequence>previous);previous=slot.sequence;
        unsigned char y=(unsigned char)(((220*(slot.sequence%256)+128)>>8)+16);
        Sleep(3);
        for(int i=0;i<4;i++)assert(slot.pixels[i]==y);
        assert(slot.pixels[4]==128&&slot.pixels[5]==128);fq_return(&worker.queue,index);
    }
    InterlockedExchange(&fail_acquire,1);
    ULONGLONG limit=GetTickCount64()+2000;
    while(!InterlockedCompareExchange(&worker.error,0,0)&&GetTickCount64()<limit)Sleep(1);
    assert(InterlockedCompareExchange(&worker.error,0,0)==DXGI_ERROR_ACCESS_LOST);
    assert(capture_worker_stop(&worker));DxgiCapture stats={0};uint64_t updates,published,dropped;
    capture_worker_snapshot(&worker,&stats,&updates,&published,&dropped);
    assert(published>=8&&updates==published&&stats.acquire_calls>=published);
    fq_destroy(&worker.queue);dxgi_close(&direct);assert(released==1&&!held&&!mapped);
    puts("ACTUAL_CAPTURE_WORKER_PIXELS_OWNERSHIP_ERROR_JOIN_TEARDOWN=PASS");return 0;
}
