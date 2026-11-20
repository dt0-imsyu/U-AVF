/* Prototype processing worker. Source gate only, no encoder/vsock/Surface
 * claim. Own ETW provider, no per-frame file IO or user pixel export. */
#include <evntprov.h>
#include "idd-system-frame-source.cpp"
#ifdef UAVF_IDD_FRAME_PIPE
#include <sddl.h>
#include "idd-frame-pipe.h"
#endif
static const GUID uavf_idd_provider = {0x43ea47ba,0x292b,0x4e83,{0xa8,0xca,0xc0,0xd4,0x68,0xea,0x31,0xae}};
extern "C" void uavf_idd_proof_copy(unsigned char *,const UavfIddFrame *);
struct UavfIddProofStats {
    UINT32 version, status;
    UINT64 frequency, elapsed_ticks, frames, unique_presentations, read_ticks;
    UINT32 last_presentation, width, height, pitch;
    bool seen;
    unsigned char *copy;
    UavfIddCallTiming calls;
    UINT64 wait_ticks, waits, wakes, timeouts, gaps, repeats, max_wait_ticks;
#ifdef UAVF_IDD_FRAME_PIPE
    UavfIddFramePipe *pipe;
#endif
};
static HRESULT uavf_idd_proof_read(const UavfIddFrame &frame, void *opaque) {
    auto *s = static_cast<UavfIddProofStats *>(opaque);
    LARGE_INTEGER a, b;
    QueryPerformanceCounter(&a);
#ifdef UAVF_IDD_FRAME_PIPE
    s->pipe->publish(frame,s->frequency); // Busy/disconnected consumer never holds the OS buffer.
#else
    uavf_idd_proof_copy(s->copy,&frame);
#endif
    QueryPerformanceCounter(&b);
    s->read_ticks += b.QuadPart-a.QuadPart;
    ++s->frames;
#ifndef UAVF_IDD_FRAME_PIPE
    if(s->seen){
        UINT32 delta=frame.presentation_number-s->last_presentation;
        if(!delta)++s->repeats;
        else if(delta<0x80000000u && delta>1)s->gaps+=delta-1;
    }
#endif
    if (!s->seen || s->last_presentation!=frame.presentation_number)
        ++s->unique_presentations;
    s->seen=true; s->last_presentation=frame.presentation_number;
    s->width=frame.width; s->height=frame.height; s->pitch=frame.pitch;
    return S_OK;
}
static void uavf_idd_emit(REGHANDLE trace, const UavfIddProofStats &s) {
    // Fixed-width fields only; never emit borrowed pointers or image data.
    UINT64 values[] = {1,s.status,s.frequency,s.elapsed_ticks,s.frames,
        s.unique_presentations,s.read_ticks,s.width,s.height,s.pitch};
    EVENT_DESCRIPTOR event={1,1,0,4,0,0,1};
    EVENT_DATA_DESCRIPTOR data;
    EventDataDescCreate(&data,values,sizeof(values));
    EventWrite(trace,&event,1,&data);
#ifndef UAVF_IDD_FRAME_PIPE
    UINT64 timing[]={2,s.status,s.frequency,s.elapsed_ticks,s.frames,
        s.calls.acquire_ticks,s.calls.finished_ticks,s.wait_ticks,s.waits,
        s.calls.pending_calls,s.wakes,s.timeouts,s.gaps,s.repeats,
        s.calls.max_acquire_ticks,s.max_wait_ticks};
    EVENT_DESCRIPTOR detail={2,1,0,4,0,0,1};
    EventDataDescCreate(&data,timing,sizeof(timing));EventWrite(trace,&detail,1,&data);
#endif
}
void SwapChainProcessor::RunCore() {
    REGHANDLE trace=0;
    if(EventRegister(&uavf_idd_provider,nullptr,nullptr,&trace)!=ERROR_SUCCESS)return;
    UavfIddProofStats stats={};
#ifdef UAVF_IDD_FRAME_PIPE
    UavfIddFramePipe pipe;stats.pipe=&pipe;
#endif
    stats.version=1;
    LARGE_INTEGER frequency,start,now;
    QueryPerformanceFrequency(&frequency);QueryPerformanceCounter(&start);
    stats.frequency=frequency.QuadPart;
    ComPtr<IDXGIDevice> dxgi;
    HRESULT hr=m_Device->Device.As(&dxgi);
    if(SUCCEEDED(hr)) {
        IDARG_IN_SWAPCHAINSETDEVICE device={};device.pDevice=dxgi.Get();
        hr=IddCxSwapChainSetDevice(m_hSwapChain,&device);
    }
    UavfIddSystemSource source={};
    if(SUCCEEDED(hr))hr=uavf_idd_admit_system_source(m_hSwapChain,&source);
    if(SUCCEEDED(hr)) {
#ifdef UAVF_IDD_FRAME_PIPE
        PSECURITY_DESCRIPTOR descriptor=nullptr;
        if(!ConvertStringSecurityDescriptorToSecurityDescriptorW(
            L"D:P(A;;GR;;;SY)",SDDL_REVISION_1,&descriptor,nullptr))hr=E_ACCESSDENIED;
        else {
            SECURITY_ATTRIBUTES security={sizeof(security),descriptor,FALSE};
            // Fixed endpoint only for the approved isolated Windows source probe.
            if(!pipe.open(L"\\\\.\\pipe\\UAVF-IddSourceProbe-3EF669B7",&security))hr=E_ACCESSDENIED;
            LocalFree(descriptor);
        }
#else
        stats.copy=static_cast<unsigned char *>(HeapAlloc(GetProcessHeap(),0,1920u*1200u*4u));
        if(!stats.copy)hr=E_OUTOFMEMORY;
#endif
    }
    while(SUCCEEDED(hr)) {
        if(WaitForSingleObject(m_hTerminateEvent.Get(),0)==WAIT_OBJECT_0)break;
#ifdef UAVF_IDD_FRAME_PIPE
        hr=uavf_idd_consume_system_frame(&source,uavf_idd_proof_read,&stats);
#else
        hr=uavf_idd_consume_system_frame(&source,uavf_idd_proof_read,&stats,&stats.calls);
#endif
        if(hr==E_PENDING) {
            HANDLE waits[]={m_hTerminateEvent.Get(),m_hAvailableBufferEvent};
#ifndef UAVF_IDD_FRAME_PIPE
            LARGE_INTEGER before,after;QueryPerformanceCounter(&before);
#endif
            DWORD wait=WaitForMultipleObjects(2,waits,FALSE,100);
#ifndef UAVF_IDD_FRAME_PIPE
            QueryPerformanceCounter(&after);UINT64 ticks=after.QuadPart-before.QuadPart;
            stats.wait_ticks+=ticks;++stats.waits;
            if(ticks>stats.max_wait_ticks)stats.max_wait_ticks=ticks;
            if(wait==WAIT_OBJECT_0+1)++stats.wakes;
            if(wait==WAIT_TIMEOUT)++stats.timeouts;
#endif
            if(wait==WAIT_OBJECT_0){hr=S_OK;break;} // Requested teardown, not an E_PENDING failure.
            if(wait!=WAIT_OBJECT_0+1 && wait!=WAIT_TIMEOUT){hr=HRESULT_FROM_WIN32(GetLastError());break;}
            hr=S_OK;
        }
        QueryPerformanceCounter(&now);
        stats.elapsed_ticks=now.QuadPart-start.QuadPart;
        if(stats.elapsed_ticks>=30*stats.frequency) {
            stats.status=static_cast<UINT32>(hr);uavf_idd_emit(trace,stats);
            start=now;stats.frames=stats.unique_presentations=stats.read_ticks=0;
            stats.calls={};stats.wait_ticks=stats.waits=stats.wakes=stats.timeouts=0;
            stats.gaps=stats.repeats=stats.max_wait_ticks=0;
            // Preserve last presentation across intervals; no false first unique.
        }
    }
    QueryPerformanceCounter(&now);stats.elapsed_ticks=now.QuadPart-start.QuadPart;
    stats.status=static_cast<UINT32>(hr);uavf_idd_emit(trace,stats);
    if(stats.copy)HeapFree(GetProcessHeap(),0,stats.copy);
    EventUnregister(trace);
}
