/* PC-only IddCx source adapter, NOT a loadable/installed driver. Called by an
 * eventual source-owned UMDF MonitorAssignSwapChain worker. No D3D11 staging,
 * RAM mapping, sockets, PnP installation or production changes here. */
#include <windows.h>
#include <wdf.h>
#include <IddCx.h>
#include "idd-system-frame-policy.h"

using Consume = HRESULT (*)(const UavfIddFrame &, void *);
struct UavfIddSystemSource {
    IDDCX_SWAPCHAIN chain;
    bool admitted;
};
struct UavfIddCallTiming {
    UINT64 acquire_ticks, finished_ticks, ready_calls, pending_calls, max_acquire_ticks;
};

HRESULT uavf_idd_admit_system_source(IDDCX_SWAPCHAIN chain,
                                    UavfIddSystemSource *source) {
    if (!source || !chain) return E_INVALIDARG;
    *source = {};
    if (!IDD_IS_FUNCTION_AVAILABLE(IddCxSwapChainInSystemMemory) ||
        !IDD_IS_FUNCTION_AVAILABLE(IddCxSwapChainReleaseAndAcquireSystemBuffer))
        return E_NOINTERFACE;
    BOOL in_system_memory = FALSE;
    HRESULT hr = IddCxSwapChainInSystemMemory(chain, &in_system_memory);
    if (FAILED(hr)) return hr;
    if (!in_system_memory) return E_NOINTERFACE;
    /* Commit one acquire variant for this entire swapchain lifetime. */
    source->chain = chain;
    source->admitted = true;
    return S_OK;
}

HRESULT uavf_idd_consume_system_frame(UavfIddSystemSource *source,
                                      Consume consume, void *context,
                                      UavfIddCallTiming *timing = nullptr) {
    if (!source || !source->admitted || !consume) return E_INVALIDARG;
    IDDCX_METADATA metadata = {};
    metadata.Size = sizeof(metadata);
    IDDCX_SYSTEM_BUFFER_INFO buffer = {};
    buffer.Size = sizeof(buffer);
    IDARG_OUT_RELEASEANDACQUIRESYSTEMBUFFER out = { &metadata, &buffer };
    LARGE_INTEGER begin={}, end={};
    if(timing)QueryPerformanceCounter(&begin);
    HRESULT hr = IddCxSwapChainReleaseAndAcquireSystemBuffer(source->chain, &out);
    if(timing){
        QueryPerformanceCounter(&end);UINT64 elapsed=end.QuadPart-begin.QuadPart;
        timing->acquire_ticks+=elapsed;
        if(elapsed>timing->max_acquire_ticks)timing->max_acquire_ticks=elapsed;
        if(hr==S_OK)++timing->ready_calls;
        if(hr==E_PENDING)++timing->pending_calls;
    }
    if (hr != S_OK) return hr; // E_PENDING: worker waits on actual next-frame event.
    UavfIddFrame frame = { static_cast<const unsigned char *>(buffer.pBuffer),
                          buffer.Width, buffer.Height, buffer.Pitch,
                          metadata.PresentationFrameNumber, metadata.PresentDisplayQPCTime };
    hr = metadata.HwProtectedSurface ? E_ACCESSDENIED :
        (uavf_idd_valid(frame, buffer.Format) ? consume(frame, context) : E_INVALIDARG);
    /* OS owns pointer; callback has completed before any subsequent acquire.
     * Notify even on rejected geometry/consumer failure; caller then tears
     * down this worker. Never switch to the texture acquire variant. */
    if(timing)QueryPerformanceCounter(&begin);
    HRESULT finished = IddCxSwapChainFinishedProcessingFrame(source->chain);
    if(timing){QueryPerformanceCounter(&end);timing->finished_ticks+=end.QuadPart-begin.QuadPart;}
    return FAILED(hr) ? hr : finished;
}
