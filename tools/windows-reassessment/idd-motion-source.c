/* Full repaint generator on ONLY the admitted U-AVF IddCx monitor.
 * Paint count is not capture/Android-present FPS. No screenshot API. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
void *memset(void *p,int c,size_t n){volatile unsigned char *s=p;while(n--)*s++=(unsigned char)c;return p;}
static ULONGLONG started;
static DWORD paints;
static ULONGLONG flush_ticks;
static LARGE_INTEGER flush_frequency;
static DWORD actual_hz;
static WCHAR monitor_name[32];
static DEVMODEW previous_mode;
static BOOL mode_changed;
static void finish(UINT code){
    if(mode_changed && ChangeDisplaySettingsExW(monitor_name,&previous_mode,0,0,0)!=DISP_CHANGE_SUCCESSFUL)
        code=30; /* Restoration failure is not a successful source test. */
    ExitProcess(code);
}
static LRESULT CALLBACK window_proc(HWND w,UINT m,WPARAM a,LPARAM b){
    if(m==WM_TIMER){if(GetTickCount64()-started>=30000)DestroyWindow(w);else InvalidateRect(w,0,FALSE);return 0;}
    if(m==WM_PAINT){PAINTSTRUCT p;HDC dc=BeginPaint(w,&p);RECT r;GetClientRect(w,&r);
#ifdef UAVF_D3D_FLIP_GENERATOR
        (void)dc;EndPaint(w,&p);return 0;
#else
        paints++;HBRUSH brush=CreateSolidBrush(RGB(paints%256,128,255-paints%256));
        FillRect(dc,&r,brush);DeleteObject(brush);EndPaint(w,&p);
        LARGE_INTEGER a,b;QueryPerformanceCounter(&a);BOOL flushed=GdiFlush();QueryPerformanceCounter(&b);
        flush_ticks+=b.QuadPart-a.QuadPart;if(!flushed)finish(17);return 0;}
#endif
#ifdef UAVF_D3D_FLIP_GENERATOR
    }
#endif
    if(m==WM_DESTROY){KillTimer(w,1);PostQuitMessage(0);return 0;}
    return DefWindowProcW(w,m,a,b);
}
#ifdef UAVF_D3D_FLIP_GENERATOR
#include "idd-d3d-flip-generator.inc"
#endif
void idd_start(void){
    QueryPerformanceFrequency(&flush_frequency);
    static DISPLAY_DEVICEW adapter;static DEVMODEW mode;
    BOOL found=FALSE;ULONGLONG deadline=GetTickCount64()+15000;
    while(GetTickCount64()<deadline&&!found){
        for(DWORD i=0;i<32;i++){
            memset(&adapter,0,sizeof(adapter));adapter.cb=sizeof(adapter);
            if(!EnumDisplayDevicesW(0,i,&adapter,0))break;
            if(!lstrcmpW(adapter.DeviceString,L"U-AVF isolated source probe")){found=TRUE;break;}
        }if(!found)Sleep(100);
    }
    if(!found)ExitProcess(10);
    mode.dmSize=sizeof(mode);mode.dmPelsWidth=1920;mode.dmPelsHeight=1200;
    mode.dmBitsPerPel=32;mode.dmDisplayFrequency=60;
    mode.dmPosition.x=GetSystemMetrics(SM_CXSCREEN);mode.dmPosition.y=0;
    mode.dmFields=DM_POSITION|DM_PELSWIDTH|DM_PELSHEIGHT|DM_BITSPERPEL|DM_DISPLAYFREQUENCY;
    lstrcpynW(monitor_name,adapter.DeviceName,32);
    previous_mode.dmSize=sizeof(previous_mode);
    if(!EnumDisplaySettingsW(adapter.DeviceName,ENUM_CURRENT_SETTINGS,&previous_mode)){
        memset(&previous_mode,0,sizeof(previous_mode));previous_mode.dmSize=sizeof(previous_mode);
        previous_mode.dmFields=DM_POSITION|DM_PELSWIDTH|DM_PELSHEIGHT; /* Previously detached monitor. */
    }
    if(ChangeDisplaySettingsExW(adapter.DeviceName,&mode,0,0,0)!=DISP_CHANGE_SUCCESSFUL)ExitProcess(11);
    mode_changed=TRUE;
    DEVMODEW actual={0};actual.dmSize=sizeof(actual);
    if(!EnumDisplaySettingsW(adapter.DeviceName,ENUM_CURRENT_SETTINGS,&actual)||actual.dmPelsWidth!=1920||actual.dmPelsHeight!=1200)finish(13);
    actual_hz=actual.dmDisplayFrequency;
    WNDCLASSW cls={0};cls.lpfnWndProc=window_proc;cls.hInstance=GetModuleHandleW(0);cls.lpszClassName=L"UavfIddFullRepaint";
    if(!RegisterClassW(&cls))finish(14);
    HWND w=CreateWindowExW(0,cls.lpszClassName,L"IddCx disposable source test",WS_POPUP|WS_VISIBLE,
        actual.dmPosition.x,actual.dmPosition.y,1920,1200,0,0,cls.hInstance,0);
    if(!w)finish(15);
    started=GetTickCount64();
#ifdef UAVF_D3D_FLIP_GENERATOR
    HRESULT render_result=idd_flip_render(w);
#else
    if(!SetTimer(w,1,1,0))finish(16);
    MSG message;while(GetMessageW(&message,0,0,0)>0){TranslateMessage(&message);DispatchMessageW(&message);}
#endif
#ifdef UAVF_D3D_FLIP_GENERATOR
    static wchar_t report[256];wsprintfW(report,L"GENERATOR_PRESENTS=%lu ELAPSED_MS=%I64u MODE_HZ=%lu RENDER_HRESULT=%08lx RENDERER=WARP FLIP_DISCARD=1 PRESENT_TICKS=%I64u QPC_FREQUENCY=%I64u; NOT_CAPTURE_OR_ANDROID_FPS\r\n",paints,GetTickCount64()-started,actual_hz,(unsigned long)render_result,flush_ticks,(ULONGLONG)flush_frequency.QuadPart);
#else
    static wchar_t report[256];wsprintfW(report,L"GENERATOR_PAINTS=%lu ELAPSED_MS=%I64u MODE_HZ=%lu GDI_FLUSH=1 FLUSH_TICKS=%I64u QPC_FREQUENCY=%I64u; NOT_CAPTURE_OR_ANDROID_FPS\r\n",paints,GetTickCount64()-started,actual_hz,flush_ticks,(ULONGLONG)flush_frequency.QuadPart);
#endif
    const WCHAR *report_path=GetFileAttributesW(L"C:\\UAVF\\IddWorkingProbe\\approved-working-test.txt")!=INVALID_FILE_ATTRIBUTES
        ? L"C:\\UAVF\\IddWorkingProbe\\motion-report.txt" : L"C:\\UAVF\\IddProbe\\motion-report.txt";
    HANDLE f=CreateFileW(report_path,GENERIC_WRITE,FILE_SHARE_READ,0,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,0);
    if(f!=INVALID_HANDLE_VALUE){DWORD n;WriteFile(f,report,(DWORD)(lstrlenW(report)*2),&n,0);CloseHandle(f);}
#ifdef UAVF_D3D_FLIP_GENERATOR
    finish(render_result!=S_OK?18:0);
#else
    finish(0);
#endif
}
