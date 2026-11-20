#define UAVF_DXGI_STAGING_READBACK 1
#ifdef UAVF_DXGI_HIGH_RES_POLL
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
static int inject_wait_timeout;
static DWORD timer_test_wait(HANDLE handle,DWORD timeout){
    if(inject_wait_timeout){inject_wait_timeout=0;return WAIT_TIMEOUT;}
    return WaitForSingleObject(handle,timeout);
}
#define WaitForSingleObject timer_test_wait
#endif
#define wmain encoder_real_wmain
#include "winpe-encode.c"
#undef wmain
#include <assert.h>
static ID3D11Texture2D source,staging;
#ifdef UAVF_DXGI_ASYNC_STAGING
static ID3D11Texture2D staging2;
static unsigned created,acquires,flushes;
#endif
static ID3D11DeviceContext context;
static unsigned copies,maps,unmaps,frame_releases,resource_releases,texture_releases;
static unsigned char pixels[16];
static HRESULT acquire_result=S_OK,map_result=S_OK;
static ULONG STDMETHODCALLTYPE resource_release(IDXGIResource *r){(void)r;resource_releases++;return 0;}
static HRESULT STDMETHODCALLTYPE query(IDXGIResource *r,REFIID iid,void **out){(void)r;assert(IsEqualGUID(iid,&IID_ID3D11Texture2D));*out=&source;return S_OK;}
static ULONG STDMETHODCALLTYPE texture_release(ID3D11Texture2D *t){
#ifdef UAVF_DXGI_ASYNC_STAGING
    assert(t==&source||t==&staging||t==&staging2);
#else
    assert(t==&source||t==&staging);
#endif
    texture_releases++;return 0;
}
static void STDMETHODCALLTYPE desc(ID3D11Texture2D *t,D3D11_TEXTURE2D_DESC *d){assert(t==&source);memset(d,0,sizeof(*d));d->Width=2;d->Height=2;d->MipLevels=1;d->ArraySize=1;d->SampleDesc.Count=1;d->Format=DXGI_FORMAT_B8G8R8A8_UNORM;d->BindFlags=D3D11_BIND_RENDER_TARGET;d->MiscFlags=0x2900;}
static HRESULT STDMETHODCALLTYPE create(ID3D11Device *d,const D3D11_TEXTURE2D_DESC *s,const D3D11_SUBRESOURCE_DATA *initial,ID3D11Texture2D **out){(void)d;assert(!initial&&s->Usage==D3D11_USAGE_STAGING&&s->CPUAccessFlags==D3D11_CPU_ACCESS_READ&&!s->BindFlags&&!s->MiscFlags);
#ifdef UAVF_DXGI_ASYNC_STAGING
    assert(created<2);*out=created++?&staging2:&staging;
#else
    *out=&staging;
#endif
    return S_OK;
}
static void STDMETHODCALLTYPE get_context(ID3D11Device *d,ID3D11DeviceContext **out){(void)d;*out=&context;}
static ULONG STDMETHODCALLTYPE device_release(ID3D11Device *d){(void)d;return 0;}
static ULONG STDMETHODCALLTYPE context_release(ID3D11DeviceContext *c){assert(c==&context);return 0;}
static void STDMETHODCALLTYPE copy(ID3D11DeviceContext *c,ID3D11Resource *dst,ID3D11Resource *src){assert(c==&context&&src==(ID3D11Resource*)&source);
#ifdef UAVF_DXGI_ASYNC_STAGING
    assert(dst==(ID3D11Resource*)&staging||dst==(ID3D11Resource*)&staging2);
#else
    assert(dst==(ID3D11Resource*)&staging);
#endif
    copies++;
}
static HRESULT STDMETHODCALLTYPE map(ID3D11DeviceContext *c,ID3D11Resource *r,UINT sub,D3D11_MAP type,UINT flags,D3D11_MAPPED_SUBRESOURCE *out){assert(c==&context&&!sub&&type==D3D11_MAP_READ);
#ifdef UAVF_DXGI_ASYNC_STAGING
    assert((r==(ID3D11Resource*)&staging||r==(ID3D11Resource*)&staging2)&&flags==D3D11_MAP_FLAG_DO_NOT_WAIT);
#else
    assert(r==(ID3D11Resource*)&staging&&!flags);
#endif
    maps++;out->pData=pixels;out->RowPitch=8;return map_result;
}
static void STDMETHODCALLTYPE unmap(ID3D11DeviceContext *c,ID3D11Resource *r,UINT sub){assert(c==&context&&!sub);
#ifdef UAVF_DXGI_ASYNC_STAGING
    assert(r==(ID3D11Resource*)&staging||r==(ID3D11Resource*)&staging2);
#else
    assert(r==(ID3D11Resource*)&staging);
#endif
    unmaps++;
}
#ifdef UAVF_DXGI_ASYNC_STAGING
static void STDMETHODCALLTYPE flush(ID3D11DeviceContext *c){assert(c==&context);flushes++;}
#endif
static IDXGIResource resource;
static HRESULT STDMETHODCALLTYPE acquire(IDXGIOutputDuplication *d,UINT timeout,DXGI_OUTDUPL_FRAME_INFO *info,IDXGIResource **out){(void)d;
#ifdef UAVF_DXGI_ASYNC_STAGING
    assert(!timeout);info->LastPresentTime.QuadPart=++acquires*100;
#else
    assert(timeout==16);
#endif
    info->AccumulatedFrames=1;*out=SUCCEEDED(acquire_result)?&resource:NULL;return acquire_result;}
static HRESULT STDMETHODCALLTYPE frame_release(IDXGIOutputDuplication *d){(void)d;frame_releases++;return S_OK;}
static ULONG STDMETHODCALLTYPE dup_release(IDXGIOutputDuplication *d){(void)d;return 0;}
int wmain(void){
    IDXGIResourceVtbl rv={0};rv.QueryInterface=query;rv.Release=resource_release;resource.lpVtbl=&rv;
    ID3D11Texture2DVtbl tv={0};tv.GetDesc=desc;tv.Release=texture_release;source.lpVtbl=&tv;staging.lpVtbl=&tv;
    ID3D11DeviceContextVtbl cv={0};cv.CopyResource=copy;cv.Map=map;cv.Unmap=unmap;cv.Release=context_release;context.lpVtbl=&cv;
#ifdef UAVF_DXGI_ASYNC_STAGING
    staging2.lpVtbl=&tv;cv.Flush=flush;
#endif
    ID3D11DeviceVtbl dv={0};dv.CreateTexture2D=create;dv.GetImmediateContext=get_context;dv.Release=device_release;ID3D11Device device={&dv};
    IDXGIOutputDuplicationVtbl ov={0};ov.AcquireNextFrame=acquire;ov.ReleaseFrame=frame_release;ov.Release=dup_release;IDXGIOutputDuplication dup={&ov};
    DxgiCapture c={0};c.device=&device;c.dup=&dup;DXGI_MAPPED_RECT out={0};UINT accumulated=0;
#ifdef UAVF_DXGI_ASYNC_STAGING
    map_result=DXGI_ERROR_WAS_STILL_DRAWING;
    assert(dxgi_map_frame(&c,2,&out,&accumulated)==DXGI_ERROR_WAIT_TIMEOUT&&!c.held&&!c.mapped);
#ifdef UAVF_DXGI_HIGH_RES_POLL
    HANDLE first_timer=c.poll_timer;assert(c.poll_timer_attempted);
    assert(first_timer||c.poll_timer_error);
    if(first_timer)inject_wait_timeout=1;
#endif
    assert(dxgi_map_frame(&c,2,&out,&accumulated)==DXGI_ERROR_WAIT_TIMEOUT&&!c.held&&!c.mapped);
#ifdef UAVF_DXGI_HIGH_RES_POLL
    if(first_timer){
        assert(c.poll_timer==first_timer&&c.high_res_waits==1&&c.poll_timer_timeouts==1);
        dxgi_async_poll_pause(&c);
        assert(c.poll_timer==first_timer&&c.high_res_waits==2&&c.poll_timer_error==0);
    }
#endif
    assert(copies==2&&created==2&&acquires==2&&flushes==2&&c.not_ready==2);
    map_result=S_OK;
    assert(dxgi_map_frame(&c,2,&out,&accumulated)==S_OK&&c.mapped&&!c.held&&c.ready==1&&acquires==2);
    assert(dxgi_map_frame(&c,2,&out,&accumulated)==DXGI_ERROR_INVALID_CALL&&copies==2);
    dxgi_release_frame(&c);acquire_result=DXGI_ERROR_WAIT_TIMEOUT;
    assert(dxgi_map_frame(&c,2,&out,&accumulated)==S_OK&&c.ready==2&&copies==2);
    dxgi_release_frame(&c);acquire_result=S_OK;map_result=E_FAIL;
    assert(FAILED(dxgi_map_frame(&c,2,&out,&accumulated))&&!c.mapped&&!c.held&&created==2);
    dxgi_close(&c);assert(!c.context&&!c.dup&&!c.slots[0].texture&&!c.slots[1].texture&&unmaps==2&&frame_releases==3);
    assert(maps==5&&resource_releases==3&&texture_releases==5);
#ifdef UAVF_DXGI_HIGH_RES_POLL
    DWORD handle_flags=0;
    if(first_timer)assert(!GetHandleInformation(first_timer,&handle_flags));
    DxgiCapture fallback={0};fallback.poll_timer_attempted=1;
    fallback.poll_timer=CreateEventW(NULL,FALSE,FALSE,NULL);assert(fallback.poll_timer);
    HANDLE wrong_type=fallback.poll_timer;dxgi_async_poll_pause(&fallback);
    assert(!fallback.poll_timer&&fallback.poll_timer_error&&fallback.high_res_waits==0&&fallback.poll_waits==1);
    assert(!GetHandleInformation(wrong_type,&handle_flags));
    puts("ACTUAL_PRIVATE_TIMER_REUSE_CLOSE_INVALID_TYPE_FALLBACK=PASS");
#endif
    puts("ACTUAL_ASYNC_TWO_SLOT_NO_OVERWRITE_DO_NOT_WAIT_READY_TIMEOUT_ERROR_CLEANUP=PASS");return 0;
#else
    assert(dxgi_map_frame(&c,2,&out,&accumulated)==S_OK&&c.held&&c.mapped&&out.pBits==pixels&&out.Pitch==8&&accumulated==1);
    assert(copies==1&&maps==1&&resource_releases==1&&texture_releases==1);
    dxgi_release_frame(&c);assert(unmaps==1&&frame_releases==1&&!c.held&&!c.mapped);
    map_result=E_FAIL;assert(FAILED(dxgi_map_frame(&c,2,&out,&accumulated))&&!c.held&&!c.mapped);
    assert(copies==2&&maps==2&&unmaps==1&&frame_releases==2);
    acquire_result=DXGI_ERROR_WAIT_TIMEOUT;assert(dxgi_map_frame(&c,2,&out,&accumulated)==DXGI_ERROR_WAIT_TIMEOUT&&copies==2);
    dxgi_close(&c);assert(!c.context&&!c.staging&&!c.device&&!c.dup&&texture_releases==3);
    puts("ACTUAL_DXGI_STAGING_DESCRIPTOR_REUSE_MAP_TIMEOUT_ERROR_OWNERSHIP=PASS");return 0;
#endif
}
