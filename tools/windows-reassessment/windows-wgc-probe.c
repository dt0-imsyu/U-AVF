/* Isolated WGC feasibility; official WinRT ABI, no Desktop Duplication. */
#define WIN32_LEAN_AND_MEAN
#define COBJMACROS
#include <windows.h>
#include <roapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <d3d11.h>
#include <windows.graphics.capture.h>
#define Frame __x_ABI_CWindows_CGraphics_CCapture_CIDirect3D11CaptureFrame
#define Pool __x_ABI_CWindows_CGraphics_CCapture_CIDirect3D11CaptureFramePool
#define PoolStatics __x_ABI_CWindows_CGraphics_CCapture_CIDirect3D11CaptureFramePoolStatics2
#define Item __x_ABI_CWindows_CGraphics_CCapture_CIGraphicsCaptureItem
#define Session __x_ABI_CWindows_CGraphics_CCapture_CIGraphicsCaptureSession
#define Device __x_ABI_CWindows_CGraphics_CDirectX_CDirect3D11_CIDirect3DDevice
#define Surface __x_ABI_CWindows_CGraphics_CDirectX_CDirect3D11_CIDirect3DSurface
#define Handler __FITypedEventHandler_2_Windows__CGraphics__CCapture__CDirect3D11CaptureFramePool_IInspectable
static const IID item_iid={0x79c3f95b,0x31f7,0x4ec2,{0xa4,0x64,0x63,0x2e,0xf5,0xd3,0x07,0x60}};
static const IID pool_iid={0x589b103f,0x6bbc,0x5df5,{0xa9,0x91,0x02,0xe2,0x8b,0x3b,0x66,0xd5}};
static const IID interop_iid={0x3628e81b,0x3cac,0x4c60,{0xb7,0xf4,0x23,0xce,0x0e,0x0c,0x33,0x56}};
static const IID device_iid={0xa37624ab,0x8d5f,0x4650,{0x9d,0x3e,0x9e,0xae,0x3d,0x9b,0xc6,0x70}};
static const IID access_iid={0xa9b3d012,0x3df2,0x4ee3,{0xb8,0xd1,0x86,0x95,0xf4,0x57,0xd3,0xc1}};
static const IID handler_iid={0x51a947f7,0x79cf,0x5a3e,{0xa3,0xa5,0x12,0x89,0xcf,0xa6,0xdf,0xe8}};
static const IID agile_iid={0x94ea2b94,0xe9cc,0x49e0,{0xc0,0xff,0xee,0x64,0xca,0x8f,0x5b,0x90}};
static const IID close_iid={0x30d5a829,0x7fa4,0x4026,{0x83,0xbb,0xd7,0x5b,0xae,0x4e,0xa9,0x9e}};
typedef struct Interop Interop;
typedef struct {HRESULT(STDMETHODCALLTYPE *QueryInterface)(Interop*,REFIID,void**);ULONG(STDMETHODCALLTYPE *AddRef)(Interop*);ULONG(STDMETHODCALLTYPE *Release)(Interop*);HRESULT(STDMETHODCALLTYPE *Window)(Interop*,HWND,REFIID,void**);HRESULT(STDMETHODCALLTYPE *Monitor)(Interop*,HMONITOR,REFIID,void**);} InteropVtbl;
struct Interop {const InteropVtbl *lpVtbl;};
typedef struct Access Access;
typedef struct {HRESULT(STDMETHODCALLTYPE *QueryInterface)(Access*,REFIID,void**);ULONG(STDMETHODCALLTYPE *AddRef)(Access*);ULONG(STDMETHODCALLTYPE *Release)(Access*);HRESULT(STDMETHODCALLTYPE *GetInterface)(Access*,REFIID,void**);} AccessVtbl;
struct Access {const AccessVtbl *lpVtbl;};
static void closed(IUnknown *object) {
    __x_ABI_CWindows_CFoundation_CIClosable *close=NULL;
    if(object&&SUCCEEDED(IUnknown_QueryInterface(object,&close_iid,(void**)&close))){close->lpVtbl->Close(close);close->lpVtbl->Release(close);}
}
typedef struct {const __FITypedEventHandler_2_Windows__CGraphics__CCapture__CDirect3D11CaptureFramePool_IInspectableVtbl *lpVtbl;LONG refs;CRITICAL_SECTION lock;HANDLE ready;Frame *latest;LONG stopping;unsigned arrived,dropped,times;LONGLONG last_time,delta_sum,delta_min,delta_max;HRESULT error;} Inbox;
static ULONG STDMETHODCALLTYPE add(Handler *self){return (ULONG)InterlockedIncrement(&((Inbox*)self)->refs);}
static ULONG STDMETHODCALLTYPE release(Handler *self){Inbox *i=(Inbox*)self;LONG n=InterlockedDecrement(&i->refs);if(!n){if(i->latest){closed((IUnknown*)i->latest);i->latest->lpVtbl->Release(i->latest);}CloseHandle(i->ready);DeleteCriticalSection(&i->lock);free(i);}return (ULONG)n;}
static HRESULT STDMETHODCALLTYPE query(Handler *self,REFIID iid,void **out){if(!out)return E_POINTER;*out=NULL;if(IsEqualIID(iid,&IID_IUnknown)||IsEqualIID(iid,&handler_iid)||IsEqualIID(iid,&agile_iid)){*out=self;add(self);return S_OK;}return E_NOINTERFACE;}
static HRESULT STDMETHODCALLTYPE arrived(Handler *self,Pool *pool,IInspectable *args){
    (void)args;Inbox *i=(Inbox*)self;Frame *f=NULL;HRESULT hr=pool->lpVtbl->TryGetNextFrame(pool,&f);
    if(FAILED(hr)||!f){if(FAILED(hr))i->error=hr;return S_OK;}
    struct __x_ABI_CWindows_CFoundation_CTimeSpan stamp={0};f->lpVtbl->get_SystemRelativeTime(f,&stamp);
    EnterCriticalSection(&i->lock);i->arrived++;
    if(stamp.Duration>i->last_time){if(i->last_time){LONGLONG delta=stamp.Duration-i->last_time;i->delta_sum+=delta;if(!i->delta_min||delta<i->delta_min)i->delta_min=delta;if(delta>i->delta_max)i->delta_max=delta;}i->times++;i->last_time=stamp.Duration;}
    bool stop=i->stopping!=0;Frame *old=i->latest;i->latest=stop?NULL:f;if(old)i->dropped++;
    LeaveCriticalSection(&i->lock);if(old){closed((IUnknown*)old);old->lpVtbl->Release(old);}
    if(stop){closed((IUnknown*)f);f->lpVtbl->Release(f);}else SetEvent(i->ready);return S_OK;
}
static const __FITypedEventHandler_2_Windows__CGraphics__CCapture__CDirect3D11CaptureFramePool_IInspectableVtbl events={query,add,release,arrived};
static HRESULT activation(const wchar_t *text,REFIID iid,void **out){HSTRING name=NULL;HRESULT hr=WindowsCreateString(text,(UINT32)wcslen(text),&name);if(SUCCEEDED(hr))hr=RoGetActivationFactory(name,iid,out);WindowsDeleteString(name);return hr;}
#include "windows-capture-workload.h"
static HRESULT capture_probe(FILE *report){
    HRESULT hr=S_OK;const char *stage="D3D11";ID3D11Device *d3d=NULL;ID3D11DeviceContext *context=NULL;IDXGIDevice *dxgi=NULL;
    IInspectable *wrapped=NULL;Device *device=NULL;Interop *interop=NULL;Item *item=NULL;PoolStatics *factory=NULL;Pool *pool=NULL;Session *session=NULL;
    ID3D11Texture2D *staging=NULL;Inbox *inbox=NULL;EventRegistrationToken token={0};bool subscribed=false;unsigned frames=0,unique=0;uint32_t previous=0;double map_ms=0,copy_ms=0;LARGE_INTEGER freq;QueryPerformanceFrequency(&freq);
    unsigned char *pixels=NULL;ULONGLONG start=0;unsigned elapsed=0;
#define TRY(call) do{hr=(call);if(FAILED(hr))goto done;}while(0)
    hr=D3D11CreateDevice(NULL,D3D_DRIVER_TYPE_HARDWARE,NULL,D3D11_CREATE_DEVICE_BGRA_SUPPORT,NULL,0,D3D11_SDK_VERSION,&d3d,NULL,&context);
    fprintf(report,"WGC_D3D_HARDWARE_CREATE_HR=%08lx\n",(unsigned long)hr);
    if(FAILED(hr)){fprintf(report,"WGC_D3D_DEVICE=WARP; NOT_HARDWARE_ACCELERATION=1\n");TRY(D3D11CreateDevice(NULL,D3D_DRIVER_TYPE_WARP,NULL,D3D11_CREATE_DEVICE_BGRA_SUPPORT,NULL,0,D3D11_SDK_VERSION,&d3d,NULL,&context));}
    TRY(ID3D11Device_QueryInterface(d3d,&IID_IDXGIDevice,(void**)&dxgi));
    HRESULT(WINAPI *wrap)(IDXGIDevice*,IInspectable**)= (void*)GetProcAddress(GetModuleHandleW(L"d3d11.dll"),"CreateDirect3D11DeviceFromDXGIDevice");
    if(!wrap){hr=E_NOINTERFACE;goto done;}TRY(wrap(dxgi,&wrapped));TRY(wrapped->lpVtbl->QueryInterface(wrapped,&device_iid,(void**)&device));
    stage="CreateForMonitor";TRY(activation(L"Windows.Graphics.Capture.GraphicsCaptureItem",&interop_iid,(void**)&interop));
    TRY(interop->lpVtbl->Monitor(interop,MonitorFromPoint((POINT){0,0},MONITOR_DEFAULTTOPRIMARY),&item_iid,(void**)&item));
    struct __x_ABI_CWindows_CGraphics_CSizeInt32 size;TRY(item->lpVtbl->get_Size(item,&size));
    fprintf(report,"WGC_SIZE=%ldx%ld\n",(long)size.Width,(long)size.Height);fflush(report);
    if(size.Width!=1920||size.Height!=1200){hr=E_INVALIDARG;goto done;}
    stage="CreateFreeThreaded";TRY(activation(L"Windows.Graphics.Capture.Direct3D11CaptureFramePool",&pool_iid,(void**)&factory));
    TRY(factory->lpVtbl->CreateFreeThreaded(factory,device,87,3,size,&pool));
    inbox=calloc(1,sizeof(*inbox));if(!inbox){hr=E_OUTOFMEMORY;goto done;}inbox->lpVtbl=&events;inbox->refs=1;InitializeCriticalSection(&inbox->lock);inbox->ready=CreateEventW(NULL,FALSE,FALSE,NULL);
    if(!inbox->ready){hr=HRESULT_FROM_WIN32(GetLastError());goto done;}
    TRY(pool->lpVtbl->add_FrameArrived(pool,(Handler*)inbox,&token));subscribed=true;
    TRY(pool->lpVtbl->CreateCaptureSession(pool,item,&session));
    D3D11_TEXTURE2D_DESC desc={0};desc.Width=1920;desc.Height=1200;desc.MipLevels=desc.ArraySize=1;desc.Format=DXGI_FORMAT_B8G8R8A8_UNORM;desc.SampleDesc.Count=1;desc.Usage=D3D11_USAGE_STAGING;desc.CPUAccessFlags=D3D11_CPU_ACCESS_READ;
    TRY(ID3D11Device_CreateTexture2D(d3d,&desc,NULL,&staging));pixels=malloc(1920u*1200u*4u);if(!pixels){hr=E_OUTOFMEMORY;goto done;}
    stage="FrameArrived/readback";workload_start();TRY(session->lpVtbl->StartCapture(session));start=GetTickCount64();
    while(GetTickCount64()-start<30000){
        WaitForSingleObject(inbox->ready,100);EnterCriticalSection(&inbox->lock);Frame *frame=inbox->latest;inbox->latest=NULL;LeaveCriticalSection(&inbox->lock);if(!frame)continue;
        Surface *surface=NULL;Access *access=NULL;ID3D11Texture2D *texture=NULL;
        hr=frame->lpVtbl->get_Surface(frame,&surface);if(SUCCEEDED(hr))hr=surface->lpVtbl->QueryInterface(surface,&access_iid,(void**)&access);
        if(SUCCEEDED(hr))hr=access->lpVtbl->GetInterface(access,&IID_ID3D11Texture2D,(void**)&texture);
        if(SUCCEEDED(hr)){
            D3D11_TEXTURE2D_DESC actual;ID3D11Texture2D_GetDesc(texture,&actual);
            if(actual.Width!=1920||actual.Height!=1200||actual.Format!=DXGI_FORMAT_B8G8R8A8_UNORM)hr=E_INVALIDARG;
        }
        if(SUCCEEDED(hr)){
            ID3D11DeviceContext_CopyResource(context,(ID3D11Resource*)staging,(ID3D11Resource*)texture);
            LARGE_INTEGER a,b,c;QueryPerformanceCounter(&a);D3D11_MAPPED_SUBRESOURCE mapped;
            hr=ID3D11DeviceContext_Map(context,(ID3D11Resource*)staging,0,D3D11_MAP_READ,0,&mapped);QueryPerformanceCounter(&b);
            if(SUCCEEDED(hr)){for(unsigned y=0;y<1200;y++)memcpy(pixels+y*7680u,(unsigned char*)mapped.pData+y*mapped.RowPitch,7680);QueryPerformanceCounter(&c);ID3D11DeviceContext_Unmap(context,(ID3D11Resource*)staging,0);map_ms+=(b.QuadPart-a.QuadPart)*1000.0/freq.QuadPart;copy_ms+=(c.QuadPart-b.QuadPart)*1000.0/freq.QuadPart;uint32_t color;memcpy(&color,pixels+((600u*1920u+960u)*4u),4);if(!frames||color!=previous)unique++;previous=color;frames++;}
        }
        if(texture)ID3D11Texture2D_Release(texture);if(access)access->lpVtbl->Release(access);if(surface)surface->lpVtbl->Release(surface);closed((IUnknown*)frame);frame->lpVtbl->Release(frame);if(FAILED(hr))break;
    }
    elapsed=(unsigned)(GetTickCount64()-start);
done:
    if(inbox){EnterCriticalSection(&inbox->lock);inbox->stopping=1;LeaveCriticalSection(&inbox->lock);}
    if(session)closed((IUnknown*)session);if(pool&&subscribed)pool->lpVtbl->remove_FrameArrived(pool,token);if(pool)closed((IUnknown*)pool);workload_finish();
    fprintf(report,"WGC_RESULT stage=%s hr=%08lx elapsed_ms=%u arrived=%u distinct_source_times=%u read_frames=%u synthetic_color_changes=%u coalesced=%u read_fps=%.3f map_ms=%.3f copy_ms=%.3f; NOT_ANDROID_PRESENT_PASS=1\n",stage,(unsigned long)hr,elapsed,inbox?inbox->arrived:0,inbox?inbox->times:0,frames,unique,inbox?inbox->dropped:0,elapsed?frames*1000.0/elapsed:0,frames?map_ms/frames:0,frames?copy_ms/frames:0);fflush(report);
    if(inbox){EnterCriticalSection(&inbox->lock);fprintf(report,"WGC_SOURCE_DELTA mean_ms=%.3f min_ms=%.3f max_ms=%.3f synthetic_paints=%ld; PIXEL_SAMPLE_NOT_FULL_FRAME_HASH=1\n",inbox->times>1?inbox->delta_sum/(10000.0*(inbox->times-1)):0,inbox->delta_min/10000.0,inbox->delta_max/10000.0,(long)workload_paints);LeaveCriticalSection(&inbox->lock);fflush(report);}
    if(session)session->lpVtbl->Release(session);if(pool)pool->lpVtbl->Release(pool);if(inbox)release((Handler*)inbox);if(factory)factory->lpVtbl->Release(factory);if(item)item->lpVtbl->Release(item);if(interop)interop->lpVtbl->Release(interop);if(staging)ID3D11Texture2D_Release(staging);if(device)device->lpVtbl->Release(device);if(wrapped)wrapped->lpVtbl->Release(wrapped);if(dxgi)IDXGIDevice_Release(dxgi);if(context)ID3D11DeviceContext_Release(context);if(d3d)ID3D11Device_Release(d3d);free(pixels);return hr;
#undef TRY
}
/* SDK MIDL_INTERFACE 2224a540-5974-49aa-b232-0882536f4cb5. */
const IID IID___x_ABI_CWindows_CGraphics_CCapture_CIGraphicsCaptureSessionStatics={0x2224a540,0x5974,0x49aa,{0xb2,0x32,0x08,0x82,0x53,0x6f,0x4c,0xb5}};
static HRESULT wgc_benchmark(FILE *report) {
    HANDLE token=NULL;DWORD needed=0;TOKEN_STATISTICS stats={0};unsigned char user[256];
    if(OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token)) {
        bool statistics=GetTokenInformation(token,TokenStatistics,&stats,sizeof(stats),&needed)!=0;
        bool system=false;bool identity=GetTokenInformation(token,TokenUser,user,sizeof(user),&needed)!=0;
        if(identity)system=IsWellKnownSid(((TOKEN_USER*)user)->User.Sid,WinLocalSystemSid)!=0;
        fprintf(report,"WGC_CALLER identity_ok=%d local_system=%d statistics_ok=%d auth_luid=%08lx:%08lx\n",identity,system,statistics,(unsigned long)stats.AuthenticationId.HighPart,(unsigned long)stats.AuthenticationId.LowPart);CloseHandle(token);fflush(report);
    }
    HRESULT hr=RoInitialize(RO_INIT_MULTITHREADED);
    if(FAILED(hr))return hr;
    HSTRING name=NULL;
    const wchar_t text[]=L"Windows.Graphics.Capture.GraphicsCaptureSession";
    hr=WindowsCreateString(text,(UINT32)(sizeof(text)/sizeof(wchar_t)-1),&name);
    if(FAILED(hr)){RoUninitialize();return hr;}
    __x_ABI_CWindows_CGraphics_CCapture_CIGraphicsCaptureSessionStatics *factory=NULL;
    hr=RoGetActivationFactory(name,&IID___x_ABI_CWindows_CGraphics_CCapture_CIGraphicsCaptureSessionStatics,(void**)&factory);
    fprintf(report,"WGC_ACTIVATION_HR=%08lx\n",(unsigned long)hr);fflush(report);
    boolean supported=0;
    if(SUCCEEDED(hr))hr=factory->lpVtbl->IsSupported(factory,&supported);
    fprintf(report,"WGC_SUPPORT hr=%08lx supported=%d; NOT_FRAME_RATE_PASS=1\n",(unsigned long)hr,(int)supported);fflush(report);
    if(factory)factory->lpVtbl->Release(factory);
    if(SUCCEEDED(hr)&&supported)hr=capture_probe(report);
    else if(SUCCEEDED(hr))hr=HRESULT_FROM_WIN32(ERROR_NOT_SUPPORTED);
    WindowsDeleteString(name);RoUninitialize();return hr;
}
int wmain(void){return FAILED(wgc_benchmark(stdout))?4:0;}
