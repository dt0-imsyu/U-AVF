/* Isolated WinPE source/codec proof. Public GDI, official OpenH264 C ABI.
 * Records the existing UVF1 access-unit format; never changes a desktop. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#ifdef UAVF_IDD_PIPE_CAPTURE
#include "windows-idd-source.h"
#endif
#ifdef UAVF_IDD_DESKTOP_LIFECYCLE
#include "windows-idd-lifecycle-policy.h"
static enum UavfIddCaptureChoice idd_current_capture_choice(void) {
    wchar_t desktop[64]={0};DWORD used=0;
    if(!GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,desktop,sizeof(desktop),&used))return UAVF_IDD_REJECT;
    int primary_is_idd=0;
    for(DWORD index=0;index<16;index++) {
        DISPLAY_DEVICEW adapter={0};adapter.cb=sizeof(adapter);
        if(!EnumDisplayDevicesW(NULL,index,&adapter,0))break;
        if((adapter.StateFlags&DISPLAY_DEVICE_PRIMARY_DEVICE)&&(adapter.StateFlags&DISPLAY_DEVICE_ATTACHED_TO_DESKTOP)
           &&!wcscmp(adapter.DeviceString,L"U-AVF isolated source probe")){primary_is_idd=1;break;}
    }
    return idd_capture_choice(desktop,primary_is_idd);
}
#endif
#ifdef UAVF_FRAME_PACING_FIX
#include "windows-frame-pacer.h"
#endif
#ifdef UAVF_CAPTURE_CPU_AUDIT
#include <dwmapi.h>
#endif
#include "codec_api.h"
#if defined(UAVF_EXPERIMENTAL_EIGHT_CODEC) || defined(UAVF_EXPERIMENTAL_X264)
#include "windows-experimental-codec.h"
#ifdef UAVF_EXPERIMENTAL_X264
#ifdef UAVF_EXPERIMENTAL_EIGHT_CODEC
#error Select one isolated codec comparison
#endif
#include "windows-x264-adapter.h"
/* The unchanged fixture tests this encoder against the original decoder. */
static FARPROC x264_admission_api(HMODULE decoder,const char *name) {
    if(!strcmp(name,"WelsCreateSVCEncoder"))return (void*)x264_adapter_create;
    if(!strcmp(name,"WelsDestroySVCEncoder"))return (void*)x264_adapter_destroy;
    return GetProcAddress(decoder,name);
}
#define GetProcAddress(module,name) x264_admission_api(module,name)
#include "windows-eight-codec-selftest.h"
#undef GetProcAddress
#else
#include "windows-eight-codec-selftest.h"
#endif
#endif

static unsigned char clamp(int x) {return (unsigned char)(x<0?0:x>255?255:x);}
static void i420_pitch(const unsigned char *bgra,unsigned char *dst,int w,int h,int pitch) {
    unsigned char *u=dst+w*h,*v=u+w*h/4;
    for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
        const unsigned char *p=bgra+(size_t)y*pitch+4*x;
        dst[y*w+x]=clamp(((66*p[2]+129*p[1]+25*p[0]+128)>>8)+16);
    }
    for(int y=0;y<h;y+=2) for(int x=0;x<w;x+=2) {
        int r=0,g=0,b=0;
        for(int dy=0;dy<2;dy++) for(int dx=0;dx<2;dx++) {
            const unsigned char *p=bgra+(size_t)(y+dy)*pitch+4*(x+dx);r+=p[2];g+=p[1];b+=p[0];
        }
        r=(r+2)/4;g=(g+2)/4;b=(b+2)/4;
        u[(y/2)*(w/2)+x/2]=clamp(((-38*r-74*g+112*b+128)>>8)+128);
        v[(y/2)*(w/2)+x/2]=clamp(((112*r-94*g-18*b+128)>>8)+128);
    }
}
#pragma pack(push,1)
typedef struct {char magic[4];uint16_t version,kind;uint32_t codec,generation,sequence;
    uint16_t width,height;uint64_t pts;uint32_t length,reserved;} UvfHeader;
#pragma pack(pop)
_Static_assert(sizeof(UvfHeader)==40,"UVF1 ABI");
static HANDLE diagnostic_report=INVALID_HANDLE_VALUE,diagnostic_out=INVALID_HANDLE_VALUE;
static uint16_t diagnostic_width,diagnostic_height;
static unsigned char diagnostic_cache[32768];static DWORD diagnostic_cached;
static bool raw_write(HANDLE f,const void *p,DWORD n) {
    const unsigned char *bytes=p;
    while(n){DWORD done=0;if(!WriteFile(f,bytes,n,&done,NULL)||!done)return false;bytes+=done;n-=done;}
    return true;
}
static bool send_diagnostic(const void *p,DWORD n) {
    const unsigned char *text=p;
    while(n) {
        DWORD size=n>1024?1024:n;
        UvfHeader hdr={{'U','V','F','1'},1,6,1,1,0,diagnostic_width,diagnostic_height,0,size,0};
        if(!raw_write(diagnostic_out,&hdr,sizeof(hdr))||!raw_write(diagnostic_out,text,size))return false;
        text+=size;n-=size;
    }
    return true;
}
static bool write_all(HANDLE f,const void *p,DWORD n) {
    if(!raw_write(f,p,n))return false;
    if(f!=diagnostic_report)return true;
    if(diagnostic_out!=INVALID_HANDLE_VALUE)return send_diagnostic(p,n);
    /* Bound pre-stream inventory; never allocate an unbounded diagnostics queue. */
    if(n<=sizeof(diagnostic_cache)-diagnostic_cached) {
        memcpy(diagnostic_cache+diagnostic_cached,p,n);diagnostic_cached+=n;
    }
    return true;
}
#include "windows-dxgi-probe.h"
#include "windows-dxgi-capture.h"
#ifdef UAVF_OVERLAPPED_CAPTURE
#include "windows-capture-worker.h"
#endif
static BOOL CALLBACK inventory_window(HWND window,LPARAM opaque) {
    unsigned *count=(unsigned*)opaque;
    ++*count;
    (void)window;
    return *count<256;
}
#ifdef UAVF_SHARED_PRIMARY_AUDIT
#include "windows-shared-primary-audit.h"
#endif
static void source_inventory(HANDLE report,const char *stage) {
#ifdef UAVF_SHARED_PRIMARY_AUDIT
    shared_primary_inventory(report);
#endif
    wchar_t desktop[128]={0},station[128]={0};DWORD size=0,session=0;
    GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,desktop,sizeof(desktop),&size);
    GetUserObjectInformationW(GetProcessWindowStation(),UOI_NAME,station,sizeof(station),&size);
    ProcessIdToSessionId(GetCurrentProcessId(),&session);
    unsigned windows=0;EnumWindows(inventory_window,(LPARAM)&windows);
    LARGE_INTEGER freq,qpc;QueryPerformanceFrequency(&freq);QueryPerformanceCounter(&qpc);
    FILETIME created={0},exited={0},kernel={0},user={0},wall={0};
    BOOL cpu_ok=GetProcessTimes(GetCurrentProcess(),&created,&exited,&kernel,&user);
    GetSystemTimeAsFileTime(&wall);
    ULARGE_INTEGER kernel_time,user_time,wall_time;
    kernel_time.LowPart=kernel.dwLowDateTime;kernel_time.HighPart=kernel.dwHighDateTime;
    user_time.LowPart=user.dwLowDateTime;user_time.HighPart=user.dwHighDateTime;
    wall_time.LowPart=wall.dwLowDateTime;wall_time.HighPart=wall.dwHighDateTime;
    char line[768];DWORD written=0;
    int length=snprintf(line,sizeof(line),
        "SOURCE_STAGE=%s pid=%lu session=%lu desktop=%ls station=%ls windows=%u tick=%llu qpc_frequency=%llu qpc=%llu wall_100ns=%llu cpu_ok=%d kernel_100ns=%llu user_100ns=%llu\r\n",
        stage,GetCurrentProcessId(),session,desktop,station,windows,
        (unsigned long long)GetTickCount64(),(unsigned long long)freq.QuadPart,
        (unsigned long long)qpc.QuadPart,(unsigned long long)wall_time.QuadPart,
        (int)cpu_ok,(unsigned long long)kernel_time.QuadPart,(unsigned long long)user_time.QuadPart);
    (void)written;
    if(length>0&&length<(int)sizeof(line))write_all(report,line,(DWORD)length);
    FlushFileBuffers(report);
}
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
#include "windows-capture-workload.h"
#endif
#ifdef UAVF_WGC_SOURCE_PROBE
#define wmain isolated_wgc_standalone_entry
#include "windows-wgc-probe.c"
#undef wmain
#endif
int wmain(int argc,wchar_t **argv) {
#ifdef UAVF_WGC_SOURCE_PROBE
    if(argc==3&&!wcscmp(argv[2],L"--wgc-probe-only")) {
        // Explicit normal-user source-only test, no codec/pipe/UVF1 output.
        wchar_t desktop[128]={0},probe_path[1200];DWORD used=0,session=0;
        if(!ProcessIdToSessionId(GetCurrentProcessId(),&session)||!session||
           !GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,desktop,sizeof(desktop),&used)||wcscmp(desktop,L"Default"))return 21;
        int length=swprintf(probe_path,1200,L"%ls\\wgc-source-report.txt",argv[1]);
        if(length<=0||length>=1200||GetFileAttributesW(probe_path)!=INVALID_FILE_ATTRIBUTES)return 22;
        FILE *probe=_wfopen(probe_path,L"wb");if(!probe)return 23;
        HRESULT status=wgc_benchmark(probe);fclose(probe);return FAILED(status)?24:0;
    }
#endif
    bool live=argc==3 && !wcscmp(argv[2],L"--stdout");
    if(argc!=2 && !live)return 2;
    /* Windows display cap, not a promise of sustained presented FPS.
     * Keep the environment override for bounded regression measurements. */
    unsigned fps=60;
    wchar_t fps_text[16];
    DWORD fps_length=live?GetEnvironmentVariableW(L"UAVF_WINDOWS_FPS",fps_text,16):0;
    if(fps_length) {
        wchar_t *end=NULL;
        if(fps_length>=16)return 11;
        unsigned long parsed=wcstoul(fps_text,&end,10);
        if(!end||end==fps_text||*end||parsed<1||parsed>120)return 11;
        fps=(unsigned)parsed;
    }
    SetProcessDPIAware();
    int result=1,w=GetSystemMetrics(SM_CXSCREEN),h=GetSystemMetrics(SM_CYSCREEN);
    unsigned captured=0,decoded=0,encoded=0;uint64_t bytes=0,err=0,count=0;double capture_ms=0,encode_ms=0,write_ms=0;
    double gdi_ms=0,convert_ms=0,blit_ms=0,flush_ms=0;
    ULONGLONG started=0;
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
    uint64_t color_changes=0,last_color_changes=0;uint32_t previous_color=0;bool color_seen=false;
#endif
    if(w<=0||h<=0||w>4096||h>4096||(w&1)||(h&1))return 3;
    HMODULE dll=NULL;
#ifdef UAVF_EXPERIMENTAL_X264
    DWORD experimental_error=0;int experimental_test=-1;
    HMODULE x264_dll=load_eight_codec(&experimental_error);
    dll=LoadLibraryW(L"openh264.dll");if(!dll){if(x264_dll)FreeLibrary(x264_dll);return 4;}
    if(x264_dll&&x264_adapter_bind(x264_dll))experimental_test=eight_codec_selftest(dll);
    bool experimental_selected=x264_dll&&experimental_test==0;
#elif defined(UAVF_EXPERIMENTAL_EIGHT_CODEC)
    DWORD experimental_error=0;int experimental_test=-1;
    dll=load_eight_codec(&experimental_error);
    if(dll) {
        experimental_test=eight_codec_selftest(dll);
        if(experimental_test){FreeLibrary(dll);dll=NULL;}
    }
    bool experimental_selected=dll!=NULL;
#endif
    if(!dll)dll=LoadLibraryW(L"openh264.dll");if(!dll)return 4;
    int (*create)(ISVCEncoder**)= (void*)GetProcAddress(dll,"WelsCreateSVCEncoder");
    void (*destroy)(ISVCEncoder*)=(void*)GetProcAddress(dll,"WelsDestroySVCEncoder");
#ifdef UAVF_EXPERIMENTAL_X264
    if(experimental_selected){create=x264_adapter_create;destroy=x264_adapter_destroy;}
#endif
    long (*create_dec)(ISVCDecoder**)=(void*)GetProcAddress(dll,"WelsCreateDecoder");
    void (*destroy_dec)(ISVCDecoder*)=(void*)GetProcAddress(dll,"WelsDestroyDecoder");
    ISVCEncoder *enc=NULL;ISVCDecoder *dec=NULL;bool initialized=false,dec_ready=false;
    HDC screen=NULL,mem=NULL;HBITMAP bmp=NULL;HGDIOBJ old=NULL;void *pixels=NULL;
    DxgiCapture direct={0};bool use_direct=false;
#ifdef UAVF_IDD_PIPE_CAPTURE
    void *idd_source=NULL;uint64_t idd_sequence=0;
#endif
#if defined(UAVF_FRAME_PACING_FIX) && defined(UAVF_OVERLAPPED_CAPTURE)
    DxgiCapture pacing_wait={0}; /* Main thread owns a separate private timer. */
#endif
#ifdef UAVF_OVERLAPPED_CAPTURE
    CaptureWorker worker={0};bool worker_active=false;int borrowed_slot=-1;
    FrameSlot borrowed={0};uint64_t last_published=0,last_dropped=0;
#endif
    ULONGLONG last_idle=0;UINT64 source_updates=0;
    HANDLE out=INVALID_HANDLE_VALUE,report=INVALID_HANDLE_VALUE;
    unsigned char *yuv=NULL,*au=NULL;
    wchar_t path[1200];swprintf(path,1200,L"%ls\\%ls",argv[1],live?L"live-encode-report.txt":L"encode-report.txt");
    report=CreateFileW(path,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    if(report==INVALID_HANDLE_VALUE)goto done;
    if(live)diagnostic_report=report;
#if defined(UAVF_EXPERIMENTAL_EIGHT_CODEC) || defined(UAVF_EXPERIMENTAL_X264)
    {
        char codec_line[256];int n=snprintf(codec_line,sizeof(codec_line),
            "EXPERIMENTAL_CODEC selected=%d load_error=%lu selftest=%d threads=8 test_frames=12 NOT_A_FPS_BENCHMARK=1\r\n",
            (int)experimental_selected,experimental_error,experimental_test);
        if(n>0&&n<(int)sizeof(codec_line))write_all(report,codec_line,(DWORD)n);
#ifdef UAVF_EXPERIMENTAL_X264
        const char *backend=experimental_selected?"CODEC_BACKEND=X264_ULTRAFAST_ZEROLATENCY ISA=NEON_ONLY\r\n":"CODEC_BACKEND=ORIGINAL_OPENH264_FALLBACK\r\n";
        write_all(report,backend,(DWORD)strlen(backend));
#ifdef UAVF_IDD_PIPE_CAPTURE
        if(!experimental_selected){result=15;goto done;} // Do not confound IDD/x264 proof with silent OpenH264 fallback.
#endif
#endif
    }
#endif
    source_inventory(report,"BEFORE_ENCODER_INIT");
    dxgi_probe(report);
    for(DWORD index=0;index<8;index++) {
        DISPLAY_DEVICEW device={0};device.cb=sizeof(device);
        if(!EnumDisplayDevicesW(NULL,index,&device,0))break;
        char line[768];
        int size=snprintf(line,sizeof(line),"DISPLAY_ADAPTER index=%lu name=%ls description=%ls id=%ls flags=%08lx\r\n",
            index,device.DeviceName,device.DeviceString,device.DeviceID,device.StateFlags);
        if(size>0&&size<(int)sizeof(line))write_all(report,line,(DWORD)size);
    }
    /* Record native modes, not an upscaled Android viewport. Bounded and
     * read-only: a missing 1200p mode must not silently become a fake success. */
    for(DWORD index=0;index<128;index++) {
        DEVMODEW mode={0};mode.dmSize=sizeof(mode);
        if(!EnumDisplaySettingsExW(NULL,index,&mode,0))break;
        char line[160];
        int size=snprintf(line,sizeof(line),"DISPLAY_MODE index=%lu width=%lu height=%lu bpp=%lu hz=%lu\r\n",
            index,mode.dmPelsWidth,mode.dmPelsHeight,mode.dmBitsPerPel,mode.dmDisplayFrequency);
        if(size>0&&size<(int)sizeof(line))write_all(report,line,(DWORD)size);
    }
    yuv=calloc((size_t)w*h*3/2,1);au=malloc(4*1024*1024);
    if(!create||!destroy||!create_dec||!destroy_dec||!yuv||!au||create(&enc))goto done;
    SEncParamExt params={0};
    if((*enc)->GetDefaultParams(enc,&params))goto done;
    params.iUsageType=SCREEN_CONTENT_REAL_TIME;
    params.iPicWidth=w;params.iPicHeight=h;
    params.iTargetBitrate=(w*h>=1920*1080)?12000000:4000000;
    params.iRCMode=RC_BITRATE_MODE;params.fMaxFrameRate=live?(float)fps:10;
    params.iComplexityMode=LOW_COMPLEXITY;
    params.iTemporalLayerNum=1;params.iSpatialLayerNum=1;
    SYSTEM_INFO system={0};GetSystemInfo(&system);
    unsigned threads=system.dwNumberOfProcessors;
    if(threads<1)threads=1;if(threads>8)threads=8;
    params.iMultipleThreadIdc=(unsigned short)threads;
    params.sSpatialLayers[0].iVideoWidth=w;params.sSpatialLayers[0].iVideoHeight=h;
    params.sSpatialLayers[0].fFrameRate=params.fMaxFrameRate;
    params.sSpatialLayers[0].iSpatialBitrate=params.iTargetBitrate;
    params.sSpatialLayers[0].sSliceArgument.uiSliceMode=SM_FIXEDSLCNUM_SLICE;
    params.sSpatialLayers[0].sSliceArgument.uiSliceNum=threads;
    if((*enc)->InitializeExt(enc,&params))goto done;initialized=true;
    char encoder_line[160];int encoder_n=snprintf(encoder_line,sizeof(encoder_line),
        "ENCODER_CONFIG threads=%u complexity=LOW slices=%u cap=%u\r\n",threads,threads,fps);
    if(encoder_n>0&&encoder_n<(int)sizeof(encoder_line))write_all(report,encoder_line,(DWORD)encoder_n);
    SEncParamExt effective={0};
    int effective_status=(*enc)->GetOption(enc,ENCODER_OPTION_SVC_ENCODE_PARAM_EXT,&effective);
    encoder_n=snprintf(encoder_line,sizeof(encoder_line),
        "ENCODER_EFFECTIVE status=%d threads=%u complexity=%d slices=%u fps=%.1f\r\n",
        effective_status,(unsigned)effective.iMultipleThreadIdc,(int)effective.iComplexityMode,
        effective.sSpatialLayers[0].sSliceArgument.uiSliceNum,effective.fMaxFrameRate);
    if(encoder_n>0&&encoder_n<(int)sizeof(encoder_line))write_all(report,encoder_line,(DWORD)encoder_n);
    source_inventory(report,"ENCODER_READY");
    if(!live) {
    if(create_dec(&dec))goto done;
    SDecodingParam dp={0};dp.uiTargetDqLayer=255;dp.sVideoProperty.eVideoBsType=VIDEO_BITSTREAM_AVC;
    if((*dec)->Initialize(dec,&dp))goto done;dec_ready=true;
    }
#ifdef UAVF_IDD_PIPE_CAPTURE
    bool idd_capture_required=live;
#ifdef UAVF_IDD_DESKTOP_LIFECYCLE
    enum UavfIddCaptureChoice initial_source_choice=UAVF_IDD_BASELINE;
    if(live) {
        /* Baseline remains visible before driver admission and after removal.
         * IDD is eligible only on the actual primary display of Default.
         * The broker recreates the producer/codec at a source transition;
         * never mix fallback metrics into an IDD performance result. */
        initial_source_choice=idd_current_capture_choice();
        if(initial_source_choice==UAVF_IDD_REJECT)goto done;
        idd_capture_required=initial_source_choice==UAVF_IDD_PRIMARY_PIPE;
    }
#endif
    if(idd_capture_required) {
        if(w!=1920||h!=1200)goto done;
        DWORD source_error=0;
#ifdef UAVF_IDD_DESKTOP_LIFECYCLE
        idd_source=uavf_idd_open(5000,&source_error);
#else
        idd_source=uavf_idd_open(50000,&source_error);
#endif
        char line[128];int size=snprintf(line,sizeof(line),"CAPTURE_SOURCE=IDD_LOCAL_PIPE open_error=%lu\r\n",source_error);
        if(size>0&&size<(int)sizeof(line))write_all(report,line,(DWORD)size);
        if(!idd_source)goto done;
    } else {
#endif
    BITMAPINFO bi={0};bi.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    bi.bmiHeader.biWidth=w;bi.bmiHeader.biHeight=-h;bi.bmiHeader.biPlanes=1;
    bi.bmiHeader.biBitCount=32;bi.bmiHeader.biCompression=BI_RGB;
    screen=GetDC(NULL);if(!screen)goto done;mem=CreateCompatibleDC(screen);if(!mem)goto done;
    bmp=CreateDIBSection(screen,&bi,DIB_RGB_COLORS,&pixels,NULL,0);if(!bmp)goto done;
    old=SelectObject(mem,bmp);if(!old||old==HGDI_ERROR)goto done;
    source_inventory(report,"GDI_READY");
    if(live) {
        HRESULT direct_status=dxgi_open(&direct,w,h);use_direct=SUCCEEDED(direct_status);
#ifdef UAVF_GDI_ONLY_CAPTURE
        dxgi_close(&direct);use_direct=false;source_inventory(report,"FORCED_GDI_AB");
#endif
        char line[160];int size=snprintf(line,sizeof(line),"CAPTURE_SOURCE=%s open_hr=%08lx\r\n",
            use_direct?"DXGI_SYSTEM_MEMORY":"GDI_FALLBACK",(unsigned long)direct_status);
        if(size>0&&size<(int)sizeof(line))write_all(report,line,(DWORD)size);
    }
#ifdef UAVF_IDD_PIPE_CAPTURE
    }
#endif
    swprintf(path,1200,L"%ls\\capture.uvf",argv[1]);
    out=live?GetStdHandle(STD_OUTPUT_HANDLE):CreateFileW(path,GENERIC_WRITE,0,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    if(out==INVALID_HANDLE_VALUE)goto done;
    if(live) {
        diagnostic_out=out;diagnostic_width=(uint16_t)w;diagnostic_height=(uint16_t)h;
        if(!send_diagnostic(diagnostic_cache,diagnostic_cached))goto done;
        diagnostic_cached=0;
    }
    LARGE_INTEGER freq,start,blit_end,gdi_end,cap_end,enc_end,write_end,convert_start;QueryPerformanceFrequency(&freq);
    started=GetTickCount64();
#ifdef UAVF_IDD_DESKTOP_LIFECYCLE
    ULONGLONG next_source_check=started+1000;
#endif
#ifdef UAVF_FRAME_PACING_FIX
    WindowsFramePacer frame_pacer={0};double frame_pacing_ms=0;
#endif
#ifdef UAVF_OVERLAPPED_CAPTURE
    if(live&&use_direct)worker_active=capture_worker_start(&worker,&direct,w,h,started);
#endif
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
    if(live)workload_start();
#endif
    ULONGLONG last_metrics=started;
    unsigned last_captured=0,last_encoded=0;
    uint64_t last_bytes=0;
    double last_capture_ms=0,last_encode_ms=0,last_write_ms=0;
    double last_gdi_ms=0,last_convert_ms=0,last_blit_ms=0,last_flush_ms=0;
    double last_acquire_ms=0,last_map_ms=0;UINT64 last_acquires=0,last_timeouts=0,last_updates=0;
    // Live lifetime follows the pipe/desktop, not the old 900-frame test limit.
    for(unsigned n=0;live||n<30;n++) {
#ifdef UAVF_IDD_DESKTOP_LIFECYCLE
        if(live&&GetTickCount64()>=next_source_check) {
            next_source_check=GetTickCount64()+1000;
            enum UavfIddCaptureChoice now=idd_current_capture_choice();
            if(idd_capture_needs_restart(initial_source_choice,now)) {
                char line[128];int size=snprintf(line,sizeof(line),"SOURCE_HANDOVER_REQUIRED from=%d to=%d; SUPERVISOR_RECONNECT=1\r\n",initial_source_choice,now);
                if(size>0&&size<(int)sizeof(line))write_all(report,line,(DWORD)size);
                result=16;goto done;
            }
        }
#endif
        if(live&&(GetSystemMetrics(SM_CXSCREEN)!=w||GetSystemMetrics(SM_CYSCREEN)!=h)) {
            result=10; // Supervisor reconnects/recreates source at the new dimensions.
            goto done;
        }
        QueryPerformanceCounter(&start);
        unsigned char *frame_yuv=yuv;
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
        uint32_t frame_color=0;bool color_sampled=false;
#endif
#ifdef UAVF_OVERLAPPED_CAPTURE
        if(worker_active) {
            if(InterlockedCompareExchange(&worker.error,0,0)) {
                if(!capture_worker_stop(&worker))ExitProcess(12);
                fq_destroy(&worker.queue);worker_active=false;
                dxgi_close(&direct);use_direct=false;
                continue; /* Existing GDI fallback remains available. */
            }
            borrowed_slot=fq_borrow(&worker.queue,&borrowed);
            if(borrowed_slot<0){WaitForSingleObject(worker.queue.ready,100);goto interval_metrics;}
            frame_yuv=borrowed.pixels;QueryPerformanceCounter(&cap_end);captured++;
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
            frame_color=borrowed.sampled_color;color_sampled=true;
#endif
            capture_ms+=borrowed.capture_ms;convert_ms+=borrowed.convert_ms;
        } else
#endif
#ifdef UAVF_IDD_PIPE_CAPTURE
        if(idd_source) {
            const unsigned char *bgra=NULL;uint64_t sequence=0;
            int ready=uavf_idd_next(idd_source,&bgra,&sequence,100);
            if(ready<0){result=14;goto done;}
            if(!ready)goto interval_metrics; // Static source remains connected; never fake frames.
            if(idd_sequence && sequence>idd_sequence)source_updates+=sequence-idd_sequence;
            else ++source_updates;
            idd_sequence=sequence;
            QueryPerformanceCounter(&convert_start);
            i420_pitch(bgra,yuv,w,h,w*4);QueryPerformanceCounter(&cap_end);++captured;
            convert_ms+=(cap_end.QuadPart-convert_start.QuadPart)*1000.0/freq.QuadPart;
        } else
#endif
        if(use_direct) {
            DXGI_MAPPED_RECT map={0};UINT accumulated=0;
            HRESULT status=dxgi_map_frame(&direct,w,&map,&accumulated);
            if(status==DXGI_ERROR_WAIT_TIMEOUT) {
                ULONGLONG now=GetTickCount64();
                if(now-last_idle>=5000){source_inventory(report,"DXGI_WAITING_FOR_DAMAGE");last_idle=now;}
                goto interval_metrics; /* Report idle liveness, never fabricate a frame. */
            }
            if(FAILED(status)) {
                dxgi_close(&direct);use_direct=false;
                char line[128];int size=snprintf(line,sizeof(line),"CAPTURE_FALLBACK hr=%08lx source=GDI\r\n",(unsigned long)status);
                if(size>0&&size<(int)sizeof(line))write_all(report,line,(DWORD)size);
                continue;
            }
            if(!accumulated) {dxgi_release_frame(&direct);goto interval_metrics;} /* Cursor-only is not desktop render FPS. */
            source_updates+=accumulated;
            QueryPerformanceCounter(&convert_start);
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
            memcpy(&frame_color,map.pBits+(size_t)(h/2)*map.Pitch+4*(w/2),4);frame_color&=0xffffff;color_sampled=true;
#endif
            i420_pitch(map.pBits,yuv,w,h,map.Pitch);
#ifdef UAVF_DXGI_LATE_RELEASE
            dxgi_unmap_frame(&direct);
#else
            dxgi_release_frame(&direct);
#endif
            QueryPerformanceCounter(&cap_end);captured++;
            convert_ms+=(cap_end.QuadPart-convert_start.QuadPart)*1000.0/freq.QuadPart;
        } else {
        /* Preserve layered-window capture: the isolated SRCCOPY-only trial
         * did not improve throughput and is not the production fix. */
        if(!BitBlt(mem,0,0,w,h,screen,0,0,SRCCOPY|CAPTUREBLT))goto done;
        QueryPerformanceCounter(&blit_end);
        if(!GdiFlush())goto done;
        QueryPerformanceCounter(&gdi_end);
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
        memcpy(&frame_color,(unsigned char*)pixels+(size_t)(h/2)*w*4+4*(w/2),4);frame_color&=0xffffff;color_sampled=true;
#endif
        blit_ms+=(blit_end.QuadPart-start.QuadPart)*1000.0/freq.QuadPart;
        flush_ms+=(gdi_end.QuadPart-blit_end.QuadPart)*1000.0/freq.QuadPart;
        i420_pitch(pixels,yuv,w,h,w*4);QueryPerformanceCounter(&cap_end);captured++;
        gdi_ms+=(gdi_end.QuadPart-start.QuadPart)*1000.0/freq.QuadPart;
        convert_ms+=(cap_end.QuadPart-gdi_end.QuadPart)*1000.0/freq.QuadPart;
        }
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
        /* The bounded workload repaints the full screen uniformly. This
         * single RGB sample detects repeated snapshots without8MB comparisons.
         * It is NOT a general desktop damage detector or production filter. */
        if(color_sampled&&workload_started&&GetTickCount64()-workload_started<30000) {
            if(!color_seen||frame_color!=previous_color)color_changes++;
            color_seen=true;previous_color=frame_color;
        }
#endif
        SSourcePicture pic={0};pic.iColorFormat=videoFormatI420;pic.iPicWidth=w;pic.iPicHeight=h;
        pic.iStride[0]=w;pic.iStride[1]=pic.iStride[2]=w/2;
        pic.pData[0]=frame_yuv;pic.pData[1]=frame_yuv+w*h;pic.pData[2]=frame_yuv+w*h*5/4;
        pic.uiTimeStamp=live?(long long)(GetTickCount64()-started):n*100;
#ifdef UAVF_OVERLAPPED_CAPTURE
        if(worker_active)pic.uiTimeStamp=(long long)borrowed.pts;
#endif
        SFrameBSInfo bs={0};if((*enc)->EncodeFrame(enc,&pic,&bs))goto done;
        QueryPerformanceCounter(&enc_end);
#ifdef UAVF_OVERLAPPED_CAPTURE
        if(!worker_active)
#endif
        capture_ms+=(cap_end.QuadPart-start.QuadPart)*1000.0/freq.QuadPart;
        encode_ms+=(enc_end.QuadPart-cap_end.QuadPart)*1000.0/freq.QuadPart;
        size_t length=0;
        for(int i=0;i<bs.iLayerNum;i++) {
            SLayerBSInfo *layer=&bs.sLayerInfo[i];size_t total=0;
            for(int j=0;j<layer->iNalCount;j++) {if(layer->pNalLengthInByte[j]<0)goto done;total+=(size_t)layer->pNalLengthInByte[j];}
            if(length+total>4*1024*1024)goto done;memcpy(au+length,layer->pBsBuf,total);length+=total;
        }
        if(length) {
#ifdef UAVF_FRAME_PACING_FIX
            LARGE_INTEGER pace_start,pace_now,send_start;QueryPerformanceCounter(&pace_start);pace_now=pace_start;
            if(live) {
                int64_t due=frame_pacer_deadline(&frame_pacer,pace_now.QuadPart,freq.QuadPart,fps);
                while(pace_now.QuadPart<due){
#ifdef UAVF_OVERLAPPED_CAPTURE
                    dxgi_async_poll_pause(&pacing_wait);
#else
                    dxgi_async_poll_pause(&direct);
#endif
                    QueryPerformanceCounter(&pace_now);
                }
                frame_pacing_ms+=(pace_now.QuadPart-pace_start.QuadPart)*1000.0/freq.QuadPart;
            }
            QueryPerformanceCounter(&send_start);
#endif
            UvfHeader hdr={{'U','V','F','1'},1,2,1,1,n+1,(uint16_t)w,(uint16_t)h,(uint64_t)pic.uiTimeStamp*1000,length,0};
#ifdef UAVF_IDD_PIPE_CAPTURE
            if(live)hdr.sequence=encoded+1; // Idle iterations are not transmitted-frame drops.
#endif
#ifdef UAVF_FRAME_PACING_FIX
            if(live)hdr.sequence=encoded+1;
#endif
            if(!write_all(out,&hdr,sizeof(hdr))||!write_all(out,au,(DWORD)length))goto done;
            QueryPerformanceCounter(&write_end);
#ifdef UAVF_FRAME_PACING_FIX
            write_ms+=(write_end.QuadPart-send_start.QuadPart)*1000.0/freq.QuadPart;
            if(live)frame_pacer_sent(&frame_pacer,write_end.QuadPart);
#else
            write_ms+=(write_end.QuadPart-enc_end.QuadPart)*1000.0/freq.QuadPart;
#endif
            encoded++;bytes+=length;
            if(!live) {
            unsigned char *planes[3]={0};SBufferInfo info={0};
            if((*dec)->DecodeFrameNoDelay(dec,au,(int)length,planes,&info)!=dsErrorFree)goto done;
            if(info.iBufferStatus==1) {
                if(info.UsrData.sSystemBuffer.iWidth!=w||info.UsrData.sSystemBuffer.iHeight!=h)goto done;
                decoded++;int stride=info.UsrData.sSystemBuffer.iStride[0];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {int d=(int)yuv[y*w+x]-planes[0][y*stride+x];err+=d<0?-d:d;count++;}
            }
            }
        }
#ifdef UAVF_OVERLAPPED_CAPTURE
        if(borrowed_slot>=0){fq_return(&worker.queue,borrowed_slot);borrowed_slot=-1;}
#endif
interval_metrics:
        if(live) {
            ULONGLONG now=GetTickCount64();
            if(now-last_metrics>=5000) {
                unsigned captures=captured-last_captured,outputs=encoded-last_encoded;
                double seconds=(now-last_metrics)/1000.0;
                char metrics[768];
                int size=snprintf(metrics,sizeof(metrics),
                    "LIVE_METRICS cap=%u width=%d height=%d interval_ms=%llu captured_fps=%.3f encoded_fps=%.3f bytes_per_sec=%.0f capture_convert_ms=%.3f encode_ms=%.3f pipe_write_ms=%.3f total=%u/%u gdi_ms=%.3f convert_ms=%.3f blit_ms=%.3f flush_ms=%.3f\r\n",
                    fps,w,h,(unsigned long long)(now-last_metrics),captures/seconds,outputs/seconds,
                    (bytes-last_bytes)/seconds,captures?(capture_ms-last_capture_ms)/captures:0,
                    captures?(encode_ms-last_encode_ms)/captures:0,
                    outputs?(write_ms-last_write_ms)/outputs:0,captured,encoded,
                    captures?(gdi_ms-last_gdi_ms)/captures:0,
                    captures?(convert_ms-last_convert_ms)/captures:0,
                    captures?(blit_ms-last_blit_ms)/captures:0,
                    captures?(flush_ms-last_flush_ms)/captures:0);
                if(size<=0||size>=(int)sizeof(metrics)||!write_all(report,metrics,(DWORD)size))goto done;
                source_inventory(report,"LIVE_INTERVAL");
#ifdef UAVF_FRAME_PACING_FIX
                char pace_line[256];int pace_length=snprintf(pace_line,sizeof(pace_line),
                    "FRAME_BASED_PACING sent=%llu wait_total_ms=%.3f cap=%u POLL_ATTEMPTS_DO_NOT_ADVANCE_CAP=1\r\n",
                    (unsigned long long)frame_pacer.frames,frame_pacing_ms,fps);
                if(pace_length>0&&pace_length<(int)sizeof(pace_line))write_all(report,pace_line,(DWORD)pace_length);
#endif
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
                char workload_line[256];DWORD exit_code=0;
                if(workload_thread)GetExitCodeThread(workload_thread,&exit_code);
                int workload_length=snprintf(workload_line,sizeof(workload_line),
                    "SYNTHETIC_FULL_REPAINT paints=%ld elapsed_ms=%llu thread_exit=%lu NOT_PRESENT_FENCES=1\r\n",
                    (long)InterlockedCompareExchange(&workload_paints,0,0),
                    (unsigned long long)(workload_started?GetTickCount64()-workload_started:0),exit_code);
                if(workload_length>0&&workload_length<(int)sizeof(workload_line))write_all(report,workload_line,(DWORD)workload_length);
                int color_length=snprintf(workload_line,sizeof(workload_line),
                    "SYNTHETIC_UNIQUE_CAPTURE_COLORS fps=%.3f total=%llu NOT_GENERAL_DESKTOP_DAMAGE=1\r\n",
                    (color_changes-last_color_changes)/seconds,(unsigned long long)color_changes);
                if(color_length>0&&color_length<(int)sizeof(workload_line))write_all(report,workload_line,(DWORD)color_length);
                last_color_changes=color_changes;
#endif
                DxgiCapture stats={0};
#ifdef UAVF_OVERLAPPED_CAPTURE
                if(worker_active) {
                    uint64_t published=0,dropped=0;
                    capture_worker_snapshot(&worker,&stats,&source_updates,&published,&dropped);
                    char worker_line[256];int worker_length=snprintf(worker_line,sizeof(worker_line),
                        "CAPTURE_WORKER published_fps=%.3f dropped_interval=%llu queue_ready_max=1 total=%llu/%llu\r\n",
                        (published-last_published)/seconds,(unsigned long long)(dropped-last_dropped),
                        (unsigned long long)published,(unsigned long long)dropped);
                    if(worker_length>0&&worker_length<(int)sizeof(worker_line))write_all(report,worker_line,(DWORD)worker_length);
                    last_published=published;last_dropped=dropped;
                } else
#endif
                stats=direct;
                UINT64 acquire_count=stats.acquire_calls-last_acquires;
                char source_line[384];int source_n=snprintf(source_line,sizeof(source_line),
                    "CAPTURE_BACKEND=%s source_accumulated_updates=%llu interval_updates=%llu acquire_calls=%llu timeouts=%llu acquire_ms_per_call=%.3f map_ms_per_capture=%.3f\r\n",
#ifdef UAVF_IDD_PIPE_CAPTURE
                    idd_source?"IDD_LOCAL_PIPE":use_direct?"DXGI_SYSTEM_MEMORY":"GDI",(unsigned long long)source_updates,
#else
                    use_direct?"DXGI_SYSTEM_MEMORY":"GDI",(unsigned long long)source_updates,
#endif
                    (unsigned long long)(source_updates-last_updates),(unsigned long long)acquire_count,
                    (unsigned long long)(stats.timeouts-last_timeouts),
                    acquire_count?(stats.acquire_ms-last_acquire_ms)/acquire_count:0,
                    captures?(stats.map_ms-last_map_ms)/captures:0);
                if(source_n>0&&source_n<(int)sizeof(source_line))write_all(report,source_line,(DWORD)source_n);
#ifdef UAVF_CAPTURE_CPU_AUDIT
                static DxgiCapture previous_audit;
                char cadence[768];int cadence_length=snprintf(cadence,sizeof(cadence),
                    "DXGI_CADENCE seconds=%.3f successful=%llu unique_present=%llu repeated_present=%llu backwards_present=%llu cursor_only=%llu accumulated=%llu coalesced=%llu last_present_qpc=%lld acquire_wall_ms=%.3f acquire_cpu_ms=%.3f map_wall_ms=%.3f map_cpu_ms=%.3f COUNTERS_NOT_PAINT_FPS=1\r\n",
                    seconds,(unsigned long long)(stats.successes-previous_audit.successes),
                    (unsigned long long)(stats.present_unique-previous_audit.present_unique),
                    (unsigned long long)(stats.present_repeated-previous_audit.present_repeated),
                    (unsigned long long)(stats.present_backwards-previous_audit.present_backwards),
                    (unsigned long long)(stats.cursor_only-previous_audit.cursor_only),
                    (unsigned long long)(stats.accumulated-previous_audit.accumulated),
                    (unsigned long long)(stats.coalesced-previous_audit.coalesced),(long long)stats.last_present,
                    stats.acquire_ms-previous_audit.acquire_ms,stats.acquire_cpu_ms-previous_audit.acquire_cpu_ms,
                    stats.map_ms-previous_audit.map_ms,stats.map_cpu_ms-previous_audit.map_cpu_ms);
                if(cadence_length>0&&cadence_length<(int)sizeof(cadence))write_all(report,cadence,(DWORD)cadence_length);
                previous_audit=stats;
#ifdef UAVF_DXGI_STAGING_READBACK
                char staging_line[256];int staging_length=snprintf(staging_line,sizeof(staging_line),
                    "DXGI_STAGING_READBACK copy_total_ms=%.3f read_map_total_ms=%.3f MAP_DESKTOP_SURFACE_USED=0\r\n",stats.copy_ms,stats.readmap_ms);
                if(staging_length>0&&staging_length<(int)sizeof(staging_line))write_all(report,staging_line,(DWORD)staging_length);
#ifdef UAVF_DXGI_ASYNC_STAGING
                char async_line[384];int async_length=snprintf(async_line,sizeof(async_line),
                    "DXGI_ASYNC_STAGING submitted=%llu ready=%llu not_ready=%llu errors=%llu pending=%d ready_age_mean_ms=%.3f BUFFER_LIMIT=2 DO_NOT_WAIT=1\r\n",
                    (unsigned long long)stats.submitted,(unsigned long long)stats.ready,(unsigned long long)stats.not_ready,
                    (unsigned long long)stats.async_errors,stats.slots[0].pending+stats.slots[1].pending,
                    stats.ready?stats.ready_age_ms/stats.ready:0);
                if(async_length>0&&async_length<(int)sizeof(async_line))write_all(report,async_line,(DWORD)async_length);
                int release_length=snprintf(async_line,sizeof(async_line),
                    "DXGI_ASYNC_RELEASE frame_calls=%llu frame_wall_total_ms=%.3f frame_cpu_total_ms=%.3f resource_wall_total_ms=%.3f setup_total_ms=%.3f SUBMISSION_IS_NOT_COMPLETION=1\r\n",
                    (unsigned long long)stats.release_frame_calls,stats.release_frame_ms,stats.release_frame_cpu_ms,stats.resource_release_ms,stats.setup_ms);
                if(release_length>0&&release_length<(int)sizeof(async_line))write_all(report,async_line,(DWORD)release_length);
                int poll_length=snprintf(async_line,sizeof(async_line),
                    "DXGI_ASYNC_POLL waits=%llu total_ms=%.3f mean_ms=%.3f max_ms=%.3f REQUESTED_SLEEP_MS=1 ACTUAL_WAKE_NOT_ASSUMED=1\r\n",
                    (unsigned long long)stats.poll_waits,stats.poll_wait_ms,
                    stats.poll_waits?stats.poll_wait_ms/stats.poll_waits:0,stats.poll_wait_max_ms);
                if(poll_length>0&&poll_length<(int)sizeof(async_line))write_all(report,async_line,(DWORD)poll_length);
#ifdef UAVF_DXGI_HIGH_RES_POLL
                int timer_length=snprintf(async_line,sizeof(async_line),
                    "DXGI_PRIVATE_POLL_TIMER active=%d attempted=%d error=%lu waits=%llu timeouts=%llu GLOBAL_TIMER_POLICY_CHANGED=0\r\n",
                    stats.poll_timer!=NULL,stats.poll_timer_attempted,(unsigned long)stats.poll_timer_error,(unsigned long long)stats.high_res_waits,(unsigned long long)stats.poll_timer_timeouts);
                if(timer_length>0&&timer_length<(int)sizeof(async_line))write_all(report,async_line,(DWORD)timer_length);
#endif
#endif
#endif
                DWM_TIMING_INFO dwm={0};dwm.cbSize=sizeof(dwm);
                HRESULT dwm_hr=DwmGetCompositionTimingInfo(NULL,&dwm);
                char dwm_line[768];int dwm_length=snprintf(dwm_line,sizeof(dwm_line),
                    "DWM_TIMING hr=%08lx refresh=%u/%u compose=%u/%u qpc_refresh_period=%llu qpc_vblank=%llu frame=%llu submitted=%llu confirmed=%llu displayed=%llu complete=%llu pending=%llu dropped=%llu missed=%llu REFRESH_NOT_ACTUAL_FPS=1\r\n",
                    (unsigned long)dwm_hr,dwm.rateRefresh.uiNumerator,dwm.rateRefresh.uiDenominator,
                    dwm.rateCompose.uiNumerator,dwm.rateCompose.uiDenominator,(unsigned long long)dwm.qpcRefreshPeriod,
                    (unsigned long long)dwm.qpcVBlank,(unsigned long long)dwm.cFrame,
                    (unsigned long long)dwm.cFrameSubmitted,(unsigned long long)dwm.cFrameConfirmed,
                    (unsigned long long)dwm.cFramesDisplayed,(unsigned long long)dwm.cFramesComplete,
                    (unsigned long long)dwm.cFramesPending,(unsigned long long)dwm.cFramesDropped,(unsigned long long)dwm.cFramesMissed);
                if(dwm_length>0&&dwm_length<(int)sizeof(dwm_line))write_all(report,dwm_line,(DWORD)dwm_length);
                char audit[640];int audit_length=snprintf(audit,sizeof(audit),
                    "DXGI_CPU_MEMORY_AUDIT acquire_cpu_total_ms=%.3f acquire_wall_total_ms=%.3f map_cpu_total_ms=%.3f map_wall_total_ms=%.3f cpu_samples=%llu texture_checked=%d texture_hr=%08lx texture=%ux%u usage=%u cpu_access=%08x bind=%08x misc=%08x memory_checked=%d map_protect=%08lx map_type=%08lx DIAGNOSTIC_NOT_PERFORMANCE_FIX=1\r\n",
                    stats.acquire_cpu_ms,stats.acquire_ms,stats.map_cpu_ms,stats.map_ms,(unsigned long long)stats.cpu_samples,
                    stats.texture_checked,(unsigned long)stats.texture_query_hr,stats.texture_desc.Width,stats.texture_desc.Height,
                    (unsigned)stats.texture_desc.Usage,stats.texture_desc.CPUAccessFlags,stats.texture_desc.BindFlags,stats.texture_desc.MiscFlags,
                    stats.memory_checked,(unsigned long)stats.memory_desc.Protect,(unsigned long)stats.memory_desc.Type);
                if(audit_length>0&&audit_length<(int)sizeof(audit))write_all(report,audit,(DWORD)audit_length);
#endif
                last_metrics=now;last_captured=captured;last_encoded=encoded;last_bytes=bytes;
                last_capture_ms=capture_ms;last_encode_ms=encode_ms;last_write_ms=write_ms;
                last_gdi_ms=gdi_ms;last_convert_ms=convert_ms;
                last_blit_ms=blit_ms;last_flush_ms=flush_ms;
                last_acquire_ms=stats.acquire_ms;last_map_ms=stats.map_ms;
                last_acquires=stats.acquire_calls;last_timeouts=stats.timeouts;last_updates=source_updates;
            }
#ifndef UAVF_FRAME_PACING_FIX
            ULONGLONG target=started+((uint64_t)n+1)*1000/fps;
            if(target>now)Sleep((DWORD)(target-now));
#endif
        }
        else Sleep(100);
    }
    char text[512];int len=snprintf(text,sizeof(text),"RESULT=PASS\r\nwidth=%d\r\nheight=%d\r\ncaptured=30\r\nencoded=%u\r\nlocally_decoded=%u\r\nbytes=%llu\r\ncapture_convert_ms=%.3f\r\nencode_ms=%.3f\r\nmean_luma_absolute_error=%.3f\r\nNOT_A_FPS_BENCHMARK=1\r\n",w,h,encoded,decoded,(unsigned long long)bytes,capture_ms/30,encode_ms/30,count?(double)err/count:-1);
    if((!live && (decoded!=encoded||!decoded))||!write_all(report,text,(DWORD)len))goto done;result=0;
done:
#ifdef UAVF_OVERLAPPED_CAPTURE
    if(worker_active) {
        if(borrowed_slot>=0)fq_return(&worker.queue,borrowed_slot);
        if(!capture_worker_stop(&worker))ExitProcess(12);
        fq_destroy(&worker.queue);worker_active=false;
    }
#endif
#ifdef UAVF_NATIVE_CAPTURE_WORKLOAD
    workload_finish();
#endif
    if(live&&report!=INVALID_HANDLE_VALUE) {
        char summary[512];int size=snprintf(summary,sizeof(summary),
            "LIVE_SOURCE=%s\r\nresult=%d\r\nwidth=%d\r\nheight=%d\r\ncaptured=%u\r\nencoded=%u\r\nbytes=%llu\r\nelapsed_ms=%llu\r\ncapture_convert_ms=%.3f\r\nencode_ms=%.3f\r\ncap_fps=%u\r\npipe_write_ms=%.3f\r\n",
#ifdef UAVF_IDD_PIPE_CAPTURE
            idd_source?"IDD_LOCAL_PIPE":use_direct?"DXGI_SYSTEM_MEMORY":"GDI",result,w,h,captured,encoded,(unsigned long long)bytes,
#else
            use_direct?"DXGI_SYSTEM_MEMORY":"GDI",result,w,h,captured,encoded,(unsigned long long)bytes,
#endif
            (unsigned long long)(started?GetTickCount64()-started:0),captured?capture_ms/captured:0,captured?encode_ms/captured:0,
            fps,encoded?write_ms/encoded:0);
        if(size>0&&size<(int)sizeof(summary))write_all(report,summary,(DWORD)size);
    }
    if(report!=INVALID_HANDLE_VALUE)CloseHandle(report);if(out!=INVALID_HANDLE_VALUE)CloseHandle(out);
    dxgi_close(&direct);
#ifdef UAVF_IDD_PIPE_CAPTURE
    uavf_idd_close(idd_source);
#endif
#if defined(UAVF_FRAME_PACING_FIX) && defined(UAVF_OVERLAPPED_CAPTURE)
    dxgi_close(&pacing_wait);
#endif
    if(old&&old!=HGDI_ERROR)SelectObject(mem,old);if(bmp)DeleteObject(bmp);
    if(mem)DeleteDC(mem);if(screen)ReleaseDC(NULL,screen);
    if(dec){if(dec_ready)(*dec)->Uninitialize(dec);destroy_dec(dec);}
    if(enc){if(initialized)(*enc)->Uninitialize(enc);destroy(enc);}
    free(yuv);free(au);FreeLibrary(dll);
#ifdef UAVF_EXPERIMENTAL_X264
    if(x264_dll)FreeLibrary(x264_dll);
#endif
    return result;
}
