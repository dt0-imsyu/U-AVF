/* Exactly two owned copies. No overwrite while pending/mapped, no busy Map wait. */
static void dxgi_async_poll_pause(DxgiCapture *c) {
    LARGE_INTEGER start,end,frequency;QueryPerformanceFrequency(&frequency);QueryPerformanceCounter(&start);
    int waited=0;
#ifdef UAVF_DXGI_HIGH_RES_POLL
    if(!c->poll_timer_attempted){
        c->poll_timer_attempted=1;
        c->poll_timer=CreateWaitableTimerExW(NULL,NULL,CREATE_WAITABLE_TIMER_HIGH_RESOLUTION,TIMER_MODIFY_STATE|SYNCHRONIZE);
        if(!c->poll_timer)c->poll_timer_error=GetLastError();
    }
    if(c->poll_timer) {
        int disable=0;
        LARGE_INTEGER due;due.QuadPart=-10000; /* Relative1ms, not calendar time. */
        if(SetWaitableTimer(c->poll_timer,&due,0,NULL,NULL,FALSE)) {
            DWORD status=WaitForSingleObject(c->poll_timer,20);
            if(status==WAIT_OBJECT_0){waited=1;c->high_res_waits++;c->poll_timer_error=0;}
            else {
                c->poll_timer_error=status==WAIT_FAILED?GetLastError():status;
                if(status==WAIT_TIMEOUT)c->poll_timer_timeouts++;
                else disable=1;
            }
        } else {c->poll_timer_error=GetLastError();disable=1;}
        if(!waited){
            CancelWaitableTimer(c->poll_timer);
            if(disable){CloseHandle(c->poll_timer);c->poll_timer=NULL;}
        }
    }
#endif
    if(!waited)Sleep(1);
    QueryPerformanceCounter(&end);double wall=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;
    c->poll_waits++;c->poll_wait_ms+=wall;if(wall>c->poll_wait_max_ms)c->poll_wait_max_ms=wall;
}
static HRESULT dxgi_async_map_frame(DxgiCapture *c,int w,DXGI_MAPPED_RECT *map,UINT *accumulated) {
    if(c->mapped||c->held){c->async_errors++;return DXGI_ERROR_INVALID_CALL;}
    LARGE_INTEGER frequency,start,end;QueryPerformanceFrequency(&frequency);
    int free_slot=-1;
    for(int i=0;i<2;i++)if(!c->slots[i].pending&&!(c->mapped&&c->mapped_slot==i)){free_slot=i;break;}
    if(free_slot>=0) {
        DXGI_OUTDUPL_FRAME_INFO info={0};IDXGIResource *resource=NULL;
        QueryPerformanceCounter(&start);uint64_t cpu_start=capture_thread_cpu();
        HRESULT hr=IDXGIOutputDuplication_AcquireNextFrame(c->dup,0,&info,&resource);
        QueryPerformanceCounter(&end);uint64_t cpu_end=capture_thread_cpu();
        c->acquire_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;c->acquire_calls++;
        if(cpu_start!=UINT64_MAX&&cpu_end!=UINT64_MAX&&cpu_end>=cpu_start){c->acquire_cpu_ms+=(cpu_end-cpu_start)/10000.0;c->cpu_samples++;}
        if(hr==DXGI_ERROR_WAIT_TIMEOUT)c->timeouts++;
        else if(FAILED(hr)){c->async_errors++;return hr;}
        else {
            c->held=1;c->successes++;c->accumulated+=info.AccumulatedFrames;
            if(info.AccumulatedFrames>1)c->coalesced+=info.AccumulatedFrames-1;
            LONGLONG stamp=info.LastPresentTime.QuadPart;
            if(!stamp)c->cursor_only++;
            else {
                if(stamp==c->last_present)c->present_repeated++;
                else if(c->last_present&&stamp<c->last_present)c->present_backwards++;
                else {c->present_unique++;if(!c->first_present)c->first_present=stamp;}
                c->last_present=stamp;
            }
            if(info.AccumulatedFrames&&stamp) {
                LARGE_INTEGER setup_start;QueryPerformanceCounter(&setup_start);
                ID3D11Texture2D *source=NULL;
                hr=resource?IDXGIResource_QueryInterface(resource,&IID_ID3D11Texture2D,(void**)&source):E_POINTER;
                if(SUCCEEDED(hr)) {
                    D3D11_TEXTURE2D_DESC desc={0};ID3D11Texture2D_GetDesc(source,&desc);
                    if(!c->texture_checked){c->texture_checked=1;c->texture_query_hr=hr;c->texture_desc=desc;}
                    if(desc.Width!=(UINT)w||desc.Format!=DXGI_FORMAT_B8G8R8A8_UNORM||desc.SampleDesc.Count!=1||desc.MipLevels!=1||desc.ArraySize!=1)hr=DXGI_ERROR_UNSUPPORTED;
                    if(SUCCEEDED(hr)&&!c->slots[free_slot].texture) {
                        desc.Usage=D3D11_USAGE_STAGING;desc.CPUAccessFlags=D3D11_CPU_ACCESS_READ;desc.BindFlags=0;desc.MiscFlags=0;
                        hr=ID3D11Device_CreateTexture2D(c->device,&desc,NULL,&c->slots[free_slot].texture);
                        if(SUCCEEDED(hr)&&!c->context)ID3D11Device_GetImmediateContext(c->device,&c->context);
                        if(SUCCEEDED(hr)&&!c->context)hr=E_POINTER;
                    }
                    if(SUCCEEDED(hr)) {
                        QueryPerformanceCounter(&end);c->setup_ms+=(end.QuadPart-setup_start.QuadPart)*1000.0/frequency.QuadPart;
                        QueryPerformanceCounter(&start);
                        ID3D11DeviceContext_CopyResource(c->context,(ID3D11Resource*)c->slots[free_slot].texture,(ID3D11Resource*)source);
                        ID3D11DeviceContext_Flush(c->context); /* Submit bounded queued copies without waiting for completion. */
                        QueryPerformanceCounter(&end);c->copy_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;
                        c->slots[free_slot].accumulated=info.AccumulatedFrames;c->slots[free_slot].present=stamp;
                        c->slots[free_slot].sequence=++c->submitted;c->slots[free_slot].pending=1;
                    }
                    QueryPerformanceCounter(&start);ID3D11Texture2D_Release(source);QueryPerformanceCounter(&end);
                    c->resource_release_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;
                }
            }
            if(resource){QueryPerformanceCounter(&start);IDXGIResource_Release(resource);QueryPerformanceCounter(&end);c->resource_release_ms+=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;}
            dxgi_release_frame(c); /* D3D command owns queued copy; no mapped pointer escapes. */
            if(FAILED(hr)){c->async_errors++;return hr;}
        }
    }
    int oldest=-1;
    for(int i=0;i<2;i++)if(c->slots[i].pending&&(oldest<0||c->slots[i].sequence<c->slots[oldest].sequence))oldest=i;
    if(oldest<0){dxgi_async_poll_pause(c);return DXGI_ERROR_WAIT_TIMEOUT;}
    D3D11_MAPPED_SUBRESOURCE pixels={0};QueryPerformanceCounter(&start);uint64_t cpu_start=capture_thread_cpu();
    HRESULT hr=ID3D11DeviceContext_Map(c->context,(ID3D11Resource*)c->slots[oldest].texture,0,D3D11_MAP_READ,D3D11_MAP_FLAG_DO_NOT_WAIT,&pixels);
    QueryPerformanceCounter(&end);uint64_t cpu_end=capture_thread_cpu();
    double wall=(end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart;c->map_ms+=wall;c->readmap_ms+=wall;
    if(cpu_start!=UINT64_MAX&&cpu_end!=UINT64_MAX&&cpu_end>=cpu_start)c->map_cpu_ms+=(cpu_end-cpu_start)/10000.0;
    if(hr==DXGI_ERROR_WAS_STILL_DRAWING){c->not_ready++;dxgi_async_poll_pause(c);return DXGI_ERROR_WAIT_TIMEOUT;}
    if(FAILED(hr)){c->async_errors++;return hr;}
    c->mapped=1;c->mapped_slot=oldest;c->slots[oldest].pending=0;
    map->pBits=pixels.pData;map->Pitch=(INT)pixels.RowPitch;
    if(!map->pBits||map->Pitch<w*4||map->Pitch>4096*4){dxgi_release_frame(c);return E_INVALIDARG;}
    *accumulated=c->slots[oldest].accumulated;c->ready++;
    c->ready_age_ms+=(end.QuadPart-c->slots[oldest].present)*1000.0/frequency.QuadPart;
    if(!c->memory_checked)c->memory_checked=VirtualQuery(map->pBits,&c->memory_desc,sizeof(c->memory_desc))==sizeof(c->memory_desc);
    return S_OK;
}
