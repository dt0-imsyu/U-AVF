#define UAVF_DXGI_LATE_RELEASE 1
#define wmain encoder_real_wmain
#include "winpe-encode.c"
#undef wmain
#include <assert.h>
static char calls[64];static unsigned call_count;
static HRESULT next_status=S_OK,map_status=S_OK;
static unsigned char fake_pixels[16];
#ifdef UAVF_CAPTURE_CPU_AUDIT
static unsigned audit_call;
#endif
static void record(char c){assert(call_count<sizeof(calls));calls[call_count++]=c;}
static HRESULT STDMETHODCALLTYPE fake_acquire(IDXGIOutputDuplication *d,UINT timeout,DXGI_OUTDUPL_FRAME_INFO *i,IDXGIResource **r){
    (void)d;assert(timeout==16);record('A');*r=NULL;i->AccumulatedFrames=1;
#ifdef UAVF_CAPTURE_CPU_AUDIT
    audit_call++;
    i->LastPresentTime.QuadPart=audit_call==1?100:200;
    if(audit_call==2)i->AccumulatedFrames=3;
    if(audit_call==5){i->LastPresentTime.QuadPart=0;i->AccumulatedFrames=0;}
#endif
    return next_status;
}
static HRESULT STDMETHODCALLTYPE fake_map(IDXGIOutputDuplication *d,DXGI_MAPPED_RECT *m){
    (void)d;record('M');m->Pitch=8;m->pBits=fake_pixels;return map_status;
}
static HRESULT STDMETHODCALLTYPE fake_unmap(IDXGIOutputDuplication *d){(void)d;record('U');return S_OK;}
static HRESULT STDMETHODCALLTYPE fake_frame_release(IDXGIOutputDuplication *d){(void)d;record('R');return S_OK;}
static ULONG STDMETHODCALLTYPE fake_object_release(IDXGIOutputDuplication *d){(void)d;record('D');return 0;}
int wmain(void){
    IDXGIOutputDuplicationVtbl table={0};table.AcquireNextFrame=fake_acquire;
    table.MapDesktopSurface=fake_map;table.UnMapDesktopSurface=fake_unmap;
    table.ReleaseFrame=fake_frame_release;table.Release=fake_object_release;
    IDXGIOutputDuplication object={&table};DxgiCapture c={0};c.dup=&object;
    DXGI_MAPPED_RECT m={0};UINT updates=0;
    assert(dxgi_map_frame(&c,2,&m,&updates)==S_OK&&c.held&&c.mapped&&updates==1);
    dxgi_unmap_frame(&c);assert(c.held&&!c.mapped);
    assert(call_count==3&&!memcmp(calls,"AMU",3)); /* Encode has no mapped pointer. */
    assert(dxgi_map_frame(&c,2,&m,&updates)==S_OK);
    assert(call_count==6&&!memcmp(calls,"AMURAM",6));
    dxgi_unmap_frame(&c);next_status=DXGI_ERROR_WAIT_TIMEOUT;
    assert(dxgi_map_frame(&c,2,&m,&updates)==DXGI_ERROR_WAIT_TIMEOUT&&!c.held&&!c.mapped);
    assert(call_count==9&&!memcmp(calls,"AMURAMURA",9));
    next_status=S_OK;map_status=DXGI_ERROR_ACCESS_LOST;
    assert(FAILED(dxgi_map_frame(&c,2,&m,&updates))&&!c.held&&!c.mapped);
    assert(call_count==12&&!memcmp(calls+9,"AMR",3));
    map_status=S_OK;assert(dxgi_map_frame(&c,2,&m,&updates)==S_OK);
    dxgi_close(&c);assert(!c.dup&&!c.mapped&&!c.held);
    assert(call_count==17&&!memcmp(calls+12,"AMURD",5));
#ifdef UAVF_CAPTURE_CPU_AUDIT
    assert(c.successes==4&&c.present_unique==2&&c.present_repeated==1&&c.cursor_only==1);
    assert(c.accumulated==5&&c.coalesced==2&&c.timeouts==1&&c.present_backwards==0);
    puts("ACTUAL_DXGI_AUDIT_COUNTERS_TIMEOUT_REPEATED_CURSOR_ACCUMULATION=PASS");
#endif
    puts("ACTUAL_DXGI_FRAME_OWNERSHIP_ORDER_TIMEOUT_ERROR_CLEANUP=PASS");return 0;
}
