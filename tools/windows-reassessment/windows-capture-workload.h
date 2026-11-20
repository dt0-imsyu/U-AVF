/* Opt-in, bounded Windows GDI full repaint. No files, registry, timer-resolution
 * changes, or input injection. Paint count is NOT a compositor/present fence. */
static volatile LONG workload_paints,workload_stop;
static HANDLE workload_thread;static DWORD workload_thread_id;
static ULONGLONG workload_started;
static LRESULT CALLBACK workload_proc(HWND window,UINT message,WPARAM wp,LPARAM lp) {
    if(message==WM_TIMER) {
        if(InterlockedCompareExchange(&workload_stop,0,0)||GetTickCount64()-workload_started>=30000)DestroyWindow(window);
        else InvalidateRect(window,NULL,FALSE);
        return 0;
    }
    if(message==WM_PAINT) {
        PAINTSTRUCT paint;HDC dc=BeginPaint(window,&paint);RECT rect;GetClientRect(window,&rect);
        LONG count=InterlockedIncrement(&workload_paints);int color=count%256;
        HBRUSH brush=CreateSolidBrush(RGB(color,128,255-color));
        FillRect(dc,&rect,brush);DeleteObject(brush);
        wchar_t label[160];swprintf(label,160,L"U-AVF FULL REPAINT (synthetic) paints=%ld elapsed=%llu ms",count,(unsigned long long)(GetTickCount64()-workload_started));
        SetBkMode(dc,TRANSPARENT);SetTextColor(dc,RGB(255,255,255));
        TextOutW(dc,20,80,label,(int)wcslen(label));EndPaint(window,&paint);return 0;
    }
    if(message==WM_DESTROY){KillTimer(window,1);PostQuitMessage(0);return 0;}
    return DefWindowProcW(window,message,wp,lp);
}
static DWORD WINAPI workload_run(void *unused) {
    (void)unused;WNDCLASSW cls={0};cls.lpfnWndProc=workload_proc;
    cls.hInstance=GetModuleHandleW(NULL);cls.lpszClassName=L"UAVFBoundedCaptureWorkload";
    if(!RegisterClassW(&cls))return 1;
    workload_started=GetTickCount64();
    HWND window=CreateWindowW(cls.lpszClassName,L"U-AVF synthetic full repaint",WS_POPUP|WS_VISIBLE,
        0,0,GetSystemMetrics(SM_CXSCREEN),GetSystemMetrics(SM_CYSCREEN),NULL,NULL,cls.hInstance,NULL);
    if(!window)return 2;
    if(!SetTimer(window,1,1,NULL)){DestroyWindow(window);return 3;}
    MSG message;BOOL status;
    while((status=GetMessageW(&message,NULL,0,0))>0){TranslateMessage(&message);DispatchMessageW(&message);}
    if(IsWindow(window))DestroyWindow(window);
    UnregisterClassW(cls.lpszClassName,cls.hInstance);return status<0?4:0;
}
static void workload_start(void) {
    wchar_t name[128]={0};DWORD bytes=0;
    if(!GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,name,sizeof(name),&bytes)||wcscmp(name,L"Default"))return;
    workload_thread=CreateThread(NULL,0,workload_run,NULL,0,&workload_thread_id);
}
static void workload_finish(void) {
    if(!workload_thread)return;
    InterlockedExchange(&workload_stop,1);PostThreadMessageW(workload_thread_id,WM_QUIT,0,0);
    WaitForSingleObject(workload_thread,2000);CloseHandle(workload_thread);workload_thread=NULL;
}
