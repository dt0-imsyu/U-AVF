/* Public system-memory Desktop Duplication. Map ownership never crosses encode. */
typedef struct {
    ID3D11Device *device;
    IDXGIOutputDuplication *dup;
    int held,mapped;
    double acquire_ms,map_ms;
    UINT64 acquire_calls,timeouts;
#ifdef UAVF_DXGI_STAGING_READBACK
    ID3D11DeviceContext *context;
    ID3D11Texture2D *staging;
    double copy_ms,readmap_ms;
#ifdef UAVF_DXGI_ASYNC_STAGING
    struct {ID3D11Texture2D *texture;UINT accumulated;LONGLONG present;UINT64 sequence;int pending;} slots[2];
    int mapped_slot;
    UINT64 submitted,ready,not_ready,async_errors;
    double ready_age_ms;
    UINT64 poll_waits;
    double poll_wait_ms,poll_wait_max_ms;
#ifdef UAVF_DXGI_HIGH_RES_POLL
    HANDLE poll_timer;
    int poll_timer_attempted;
    DWORD poll_timer_error;
    UINT64 high_res_waits;
    UINT64 poll_timer_timeouts;
#endif
#endif
#endif
#ifdef UAVF_CAPTURE_CPU_AUDIT
    UINT64 successes,present_unique,present_repeated,present_backwards,cursor_only,accumulated,coalesced;
    LONGLONG first_present,last_present;
    double acquire_cpu_ms,map_cpu_ms;
    double release_frame_ms,release_frame_cpu_ms,resource_release_ms,setup_ms;
    UINT64 release_frame_calls;
    UINT64 cpu_samples;
    int texture_checked,memory_checked;
    HRESULT texture_query_hr;
    D3D11_TEXTURE2D_DESC texture_desc;
    MEMORY_BASIC_INFORMATION memory_desc;
#endif
} DxgiCapture;
#ifdef UAVF_CAPTURE_CPU_AUDIT
static uint64_t capture_thread_cpu(void) {
    FILETIME created,exit,kernel,user;
    if(!GetThreadTimes(GetCurrentThread(),&created,&exit,&kernel,&user))return UINT64_MAX;
    return (((uint64_t)kernel.dwHighDateTime<<32)|kernel.dwLowDateTime)+
        (((uint64_t)user.dwHighDateTime<<32)|user.dwLowDateTime);
}
#endif
static void dxgi_unmap_frame(DxgiCapture *c) {
    if(c->mapped){
#ifdef UAVF_DXGI_STAGING_READBACK
#ifdef UAVF_DXGI_ASYNC_STAGING
        ID3D11DeviceContext_Unmap(c->context,(ID3D11Resource*)c->slots[c->mapped_slot].texture,0);
#else
        ID3D11DeviceContext_Unmap(c->context,(ID3D11Resource*)c->staging,0);
#endif
#else
        IDXGIOutputDuplication_UnMapDesktopSurface(c->dup);
#endif
        c->mapped=0;
    }
}
static void dxgi_release_frame(DxgiCapture *c) {
    dxgi_unmap_frame(c);
    if(c->held){
#ifdef UAVF_CAPTURE_CPU_AUDIT
        LARGE_INTEGER start,end,frequency;QueryPerformanceFrequency(&frequency);QueryPerformanceCounter(&start);
        uint64_t cpu_start=capture_thread_cpu();
#endif
        IDXGIOutputDuplication_ReleaseFrame(c->dup);c->held=0;
#ifdef UAVF_CAPTURE_CPU_AUDIT
        QueryPerformanceCounter(&end);uint64_t cpu_end=capture_thread_cpu();
        c->release_frame_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;c->release_frame_calls++;
        if(cpu_start!=UINT64_MAX&&cpu_end!=UINT64_MAX&&cpu_end>=cpu_start)c->release_frame_cpu_ms+=(cpu_end-cpu_start)/10000.0;
#endif
    }
}
static void dxgi_close(DxgiCapture *c) {
    dxgi_release_frame(c);
#ifdef UAVF_DXGI_HIGH_RES_POLL
    if(c->poll_timer){CancelWaitableTimer(c->poll_timer);CloseHandle(c->poll_timer);c->poll_timer=NULL;}
#endif
#ifdef UAVF_DXGI_STAGING_READBACK
#ifdef UAVF_DXGI_ASYNC_STAGING
    for(int i=0;i<2;i++){if(c->slots[i].texture)ID3D11Texture2D_Release(c->slots[i].texture);c->slots[i].texture=NULL;c->slots[i].pending=0;}
#endif
    if(c->staging)ID3D11Texture2D_Release(c->staging);
    if(c->context)ID3D11DeviceContext_Release(c->context);
    c->staging=NULL;c->context=NULL;
#endif
    if(c->dup)IDXGIOutputDuplication_Release(c->dup);
    if(c->device)ID3D11Device_Release(c->device);
    c->dup=NULL;c->device=NULL; /* Preserve monotonic timing totals across fallback. */
}
static HRESULT dxgi_open(DxgiCapture *c,int w,int h) {
    IDXGIFactory1 *factory=NULL;HRESULT status=CreateDXGIFactory1(&IID_IDXGIFactory1,(void**)&factory);
    if(FAILED(status))return status;
    for(UINT i=0;i<2&&!c->dup;i++) {
        IDXGIAdapter1 *adapter=NULL;if(FAILED(IDXGIFactory1_EnumAdapters1(factory,i,&adapter)))break;
        ID3D11Device *device=NULL;
        status=D3D11CreateDevice((IDXGIAdapter*)adapter,D3D_DRIVER_TYPE_UNKNOWN,NULL,
            D3D11_CREATE_DEVICE_BGRA_SUPPORT,NULL,0,D3D11_SDK_VERSION,&device,NULL,NULL);
        if(SUCCEEDED(status))for(UINT j=0;j<4&&!c->dup;j++) {
            IDXGIOutput *output=NULL;IDXGIOutput1 *output1=NULL;
            if(FAILED(IDXGIAdapter1_EnumOutputs(adapter,j,&output)))break;
            DXGI_OUTPUT_DESC desc={0};IDXGIOutput_GetDesc(output,&desc);
            if(desc.AttachedToDesktop && desc.DesktopCoordinates.left==0 && desc.DesktopCoordinates.top==0 &&
               desc.DesktopCoordinates.right==w && desc.DesktopCoordinates.bottom==h) {
                status=IDXGIOutput_QueryInterface(output,&IID_IDXGIOutput1,(void**)&output1);
                if(SUCCEEDED(status)) {
                    IDXGIOutputDuplication *dup=NULL;
                    status=IDXGIOutput1_DuplicateOutput(output1,(IUnknown*)device,&dup);
                    if(SUCCEEDED(status)) {
                        DXGI_OUTDUPL_DESC d={0};IDXGIOutputDuplication_GetDesc(dup,&d);
                        if(d.DesktopImageInSystemMemory && d.ModeDesc.Width==(UINT)w && d.ModeDesc.Height==(UINT)h && d.ModeDesc.Format==DXGI_FORMAT_B8G8R8A8_UNORM) {
                            c->device=device;c->dup=dup;device=NULL;
                        } else {IDXGIOutputDuplication_Release(dup);status=DXGI_ERROR_UNSUPPORTED;}
                    }
                }
            }
            if(output1)IDXGIOutput1_Release(output1);
            IDXGIOutput_Release(output);
        }
        if(device)ID3D11Device_Release(device);
        IDXGIAdapter1_Release(adapter);
    }
    IDXGIFactory1_Release(factory);
    return c->dup?S_OK:(FAILED(status)?status:DXGI_ERROR_UNSUPPORTED);
}
#ifdef UAVF_DXGI_ASYNC_STAGING
#include "windows-dxgi-async-staging.h"
#endif
static HRESULT dxgi_map_frame(DxgiCapture *c,int w,DXGI_MAPPED_RECT *map,UINT *accumulated) {
#ifdef UAVF_DXGI_ASYNC_STAGING
    return dxgi_async_map_frame(c,w,map,accumulated);
#else
#ifdef UAVF_DXGI_LATE_RELEASE
    /* Microsoft recommends releasing immediately before AcquireNextFrame:
     * no redundant desktop copies during encoder/pipe processing. The map
     * is still unmapped immediately after conversion; no pointer escapes. */
    dxgi_release_frame(c);
#endif
    DXGI_OUTDUPL_FRAME_INFO info={0};IDXGIResource *resource=NULL;
    LARGE_INTEGER frequency,start,end;QueryPerformanceFrequency(&frequency);QueryPerformanceCounter(&start);
#ifdef UAVF_CAPTURE_CPU_AUDIT
    uint64_t cpu_start=capture_thread_cpu();
#endif
    HRESULT status=IDXGIOutputDuplication_AcquireNextFrame(c->dup,16,&info,&resource);
    QueryPerformanceCounter(&end);c->acquire_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;c->acquire_calls++;
#ifdef UAVF_CAPTURE_CPU_AUDIT
    if(SUCCEEDED(status)) {
        c->successes++;c->accumulated+=info.AccumulatedFrames;
        if(info.AccumulatedFrames>1)c->coalesced+=info.AccumulatedFrames-1;
        if(!info.LastPresentTime.QuadPart)c->cursor_only++;
        else {
            LONGLONG stamp=info.LastPresentTime.QuadPart;
            if(stamp==c->last_present)c->present_repeated++;
            else if(c->last_present&&stamp<c->last_present)c->present_backwards++;
            else {c->present_unique++;if(!c->first_present)c->first_present=stamp;}
            c->last_present=stamp;
        }
    }
    uint64_t cpu_end=capture_thread_cpu();
    if(cpu_start!=UINT64_MAX&&cpu_end!=UINT64_MAX&&cpu_end>=cpu_start){c->acquire_cpu_ms+=(cpu_end-cpu_start)/10000.0;c->cpu_samples++;}
    if(SUCCEEDED(status)&&resource&&!c->texture_checked) {
        ID3D11Texture2D *texture=NULL;
        c->texture_query_hr=IDXGIResource_QueryInterface(resource,&IID_ID3D11Texture2D,(void**)&texture);
        if(SUCCEEDED(c->texture_query_hr)){ID3D11Texture2D_GetDesc(texture,&c->texture_desc);ID3D11Texture2D_Release(texture);}
        c->texture_checked=1;
    }
#endif
    if(status==DXGI_ERROR_WAIT_TIMEOUT)c->timeouts++;
    if(FAILED(status))return status;
    c->held=1;
#ifndef UAVF_DXGI_STAGING_READBACK
    if(resource)IDXGIResource_Release(resource);
#endif
    QueryPerformanceCounter(&start);
#ifdef UAVF_CAPTURE_CPU_AUDIT
    cpu_start=capture_thread_cpu();
#endif
#ifdef UAVF_DXGI_STAGING_READBACK
    ID3D11Texture2D *source=NULL;
    status=resource?IDXGIResource_QueryInterface(resource,&IID_ID3D11Texture2D,(void**)&source):E_POINTER;
    if(resource)IDXGIResource_Release(resource);
    if(SUCCEEDED(status)) {
        D3D11_TEXTURE2D_DESC desc={0};ID3D11Texture2D_GetDesc(source,&desc);
        if(desc.Width!=(UINT)w||desc.Format!=DXGI_FORMAT_B8G8R8A8_UNORM||desc.SampleDesc.Count!=1||desc.MipLevels!=1||desc.ArraySize!=1)status=DXGI_ERROR_UNSUPPORTED;
        if(SUCCEEDED(status)&&!c->staging) {
            desc.Usage=D3D11_USAGE_STAGING;desc.CPUAccessFlags=D3D11_CPU_ACCESS_READ;
            desc.BindFlags=0;desc.MiscFlags=0;
            status=ID3D11Device_CreateTexture2D(c->device,&desc,NULL,&c->staging);
            if(SUCCEEDED(status))ID3D11Device_GetImmediateContext(c->device,&c->context);
            if(SUCCEEDED(status)&&!c->context)status=E_POINTER;
        }
        if(SUCCEEDED(status)) {
            LARGE_INTEGER phase;QueryPerformanceCounter(&phase);
            ID3D11DeviceContext_CopyResource(c->context,(ID3D11Resource*)c->staging,(ID3D11Resource*)source);
            QueryPerformanceCounter(&end);c->copy_ms+=(end.QuadPart-phase.QuadPart)*1000.0/frequency.QuadPart;
            D3D11_MAPPED_SUBRESOURCE pixels={0};QueryPerformanceCounter(&phase);
            status=ID3D11DeviceContext_Map(c->context,(ID3D11Resource*)c->staging,0,D3D11_MAP_READ,0,&pixels);
            QueryPerformanceCounter(&end);c->readmap_ms+=(end.QuadPart-phase.QuadPart)*1000.0/frequency.QuadPart;
            if(SUCCEEDED(status)){map->pBits=pixels.pData;map->Pitch=(INT)pixels.RowPitch;}
        }
        ID3D11Texture2D_Release(source);
    }
#else
    status=IDXGIOutputDuplication_MapDesktopSurface(c->dup,map);
#endif
    QueryPerformanceCounter(&end);c->map_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;
#ifdef UAVF_CAPTURE_CPU_AUDIT
    cpu_end=capture_thread_cpu();
    if(cpu_start!=UINT64_MAX&&cpu_end!=UINT64_MAX&&cpu_end>=cpu_start)c->map_cpu_ms+=(cpu_end-cpu_start)/10000.0;
    if(SUCCEEDED(status)&&map->pBits&&!c->memory_checked) {
        c->memory_checked=VirtualQuery(map->pBits,&c->memory_desc,sizeof(c->memory_desc))==sizeof(c->memory_desc);
    }
#endif
    if(SUCCEEDED(status)) {
        c->mapped=1;
        if(!map->pBits || map->Pitch<w*4 || map->Pitch>4096*4)status=E_INVALIDARG;
    }
    if(FAILED(status))dxgi_release_frame(c);
    else *accumulated=info.AccumulatedFrames;
    return status;
#endif
}
