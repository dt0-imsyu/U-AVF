/* Explicit one-off guest job. Native UAC elevation, never UI command typing. */
#include <shellapi.h>
#include <shlobj.h>
static void idd_launch_note(const char *stage,DWORD value){
    HANDLE log=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-state.txt",FILE_APPEND_DATA,FILE_SHARE_READ,NULL,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(log==INVALID_HANDLE_VALUE)return;
    DWORD session=0,written=0;ProcessIdToSessionId(GetCurrentProcessId(),&session);
    char line[256];int n=snprintf(line,sizeof(line),"stage=%s value=%lu session=%lu tick=%llu\r\n",stage,(unsigned long)value,(unsigned long)session,(unsigned long long)GetTickCount64());
    if(n>0&&n<(int)sizeof(line)){WriteFile(log,line,(DWORD)n,&written,NULL);FlushFileBuffers(log);}
    CloseHandle(log);
}
#if defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT >= 24
#include "idd-captured-job.h"
static DWORD WINAPI idd_observe_child(void *process){
    DWORD wait=WaitForSingleObject((HANDLE)process,
#if UAVF_IDD_DESKTOP_ATTEMPT >= 28
        360000
#else
        180000
#endif
    ),code=STILL_ACTIVE;
    if(wait==WAIT_OBJECT_0 && GetExitCodeProcess((HANDLE)process,&code))idd_launch_note("BACKGROUND_JOB24_EXIT",code);
    else idd_launch_note("BACKGROUND_JOB24_WAIT",wait);
    CloseHandle((HANDLE)process);return 0;
}
#endif
static void idd_working_launch(void){
    DWORD serial=0;
    if(!GetVolumeInformationW(L"C:\\",NULL,0,&serial,NULL,NULL,NULL,0)||serial!=0x3ef669b7)return;
    wchar_t volume[512],selected[1024]={0},file[1024];
    HANDLE search=FindFirstVolumeW(volume,512);
    if(search==INVALID_HANDLE_VALUE)return;
    unsigned matches=0;
    do {
        if(swprintf(file,1024,L"%lsIDDWORK\\run.ps1",volume)<0)continue;
        if(GetFileAttributesW(file)!=INVALID_FILE_ATTRIBUTES){wcscpy(selected,file);matches++;}
    }while(FindNextVolumeW(search,volume,512));
    FindVolumeClose(search);if(matches!=1)return;
#if !defined(UAVF_IDD_ADMISSION_AUDIT_ONLY) || defined(UAVF_IDD_DESKTOP_SOURCE)
    wchar_t attached[64]={0};DWORD needed=0;
    if(!GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,attached,sizeof(attached),&needed)){
        idd_launch_note("ATTACHED_DESKTOP_QUERY_FAILED",GetLastError());return;
    }
    if(wcscmp(attached,L"Default")){idd_launch_note("NON_DEFAULT_PRODUCER_SKIPPED",0);return;}
    idd_launch_note("DESKTOP_WAIT_STARTED",0);
    /* Source test must run on an unlocked Default desktop, not a hidden
       Winlogon/secure desktop. Never unlock or enter credentials ourselves. */
    BOOL unlocked=FALSE;DWORD desktopError=0;ULONGLONG deadline=GetTickCount64()+300000;
    while(GetTickCount64()<deadline){
        HDESK desktop=OpenInputDesktop(0,FALSE,DESKTOP_READOBJECTS);
        if(desktop){wchar_t name[64]={0};DWORD needed=0;
            unlocked=GetUserObjectInformationW(desktop,UOI_NAME,name,sizeof(name),&needed)&&!wcscmp(name,L"Default");
            CloseDesktop(desktop);
        }else desktopError=GetLastError();
        if(unlocked)break;Sleep(500);
    }
    if(!unlocked){idd_launch_note("DEFAULT_DESKTOP_NOT_OBSERVED",desktopError);return;}
    idd_launch_note("DEFAULT_DESKTOP_OBSERVED",0);
    /* The display broker replaces its producer on Winlogon -> Default.
       Never consume the once-marker in the doomed Winlogon producer. */
#endif
#ifdef UAVF_IDD_ADMISSION_AUDIT_ONLY
    /* Finite read-only audit under the existing broker token; no login/UAC,
       trust installation or display-source test on the secure desktop. */
#ifdef UAVF_IDD_ADMISSION_LOAD_ONLY
#ifdef UAVF_IDD_RECONNECT_PROBE
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested23.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#else
#ifdef UAVF_IDD_DESKTOP_SOURCE
#if defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT == 29
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested29.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#elif defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT == 28
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested28.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#elif defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT == 27
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested27.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#elif defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT == 26
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested26.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#elif defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT == 25
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested25.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#elif defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT == 24
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested24.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#else
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested22.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#endif
#else
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested21.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#endif
#endif
#else
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested9.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#endif
#else
    HANDLE once=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-requested8.txt",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
#endif
    if(once==INVALID_HANDLE_VALUE){idd_launch_note("REQUEST_ALREADY_EXISTS_OR_FAILED",GetLastError());return;}
    CloseHandle(once);
    idd_launch_note("REQUEST_CREATED",0);
    wchar_t args[1500];
#ifdef UAVF_IDD_ADMISSION_AUDIT_ONLY
#ifdef UAVF_IDD_ADMISSION_LOAD_ONLY
#ifdef UAVF_IDD_RECONNECT_PROBE
    swprintf(args,1500,L"\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"%ls\" -DriverAdmissionOnly -ReconnectProbe",selected);
#else
#ifdef UAVF_IDD_DESKTOP_SOURCE
#ifdef UAVF_IDD_RECONNECT_SOURCE
#if defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT >= 25
    swprintf(args,1500,L"\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"%ls\" -DriverAdmissionOnly -RealDesktop -ReconnectSource -RecoverInterruptedDesktop",selected);
#else
    swprintf(args,1500,L"\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"%ls\" -DriverAdmissionOnly -RealDesktop -ReconnectSource",selected);
#endif
#else
    swprintf(args,1500,L"\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"%ls\" -DriverAdmissionOnly -RealDesktop",selected);
#endif
#else
    swprintf(args,1500,L"\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"%ls\" -DriverAdmissionOnly",selected);
#endif
#endif
#else
    swprintf(args,1500,L"\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File \"%ls\" -AuditOnly",selected);
#endif
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);PROCESS_INFORMATION child={0};
#ifdef UAVF_IDD_DESKTOP_SOURCE
    startup.lpDesktop=L"winsta0\\Default";
#endif
#if defined(UAVF_IDD_DESKTOP_ATTEMPT) && UAVF_IDD_DESKTOP_ATTEMPT >= 24
    // Preserve the same token/desktop/arguments, but retain actual engine errors.
    // Only these two handles may be inherited, not UART/vsock/encoder handles.
#if UAVF_IDD_DESKTOP_ATTEMPT == 29
    BOOL launched=idd_start_captured_job(args,&startup,&child,L"C:\\ProgramData\\UAVF\\idd-working-job29.txt");
#elif UAVF_IDD_DESKTOP_ATTEMPT == 28
    BOOL launched=idd_start_captured_job(args,&startup,&child,L"C:\\ProgramData\\UAVF\\idd-working-job28.txt");
#elif UAVF_IDD_DESKTOP_ATTEMPT == 27
    BOOL launched=idd_start_captured_job(args,&startup,&child,L"C:\\ProgramData\\UAVF\\idd-working-job27.txt");
#elif UAVF_IDD_DESKTOP_ATTEMPT == 26
    BOOL launched=idd_start_captured_job(args,&startup,&child,L"C:\\ProgramData\\UAVF\\idd-working-job26.txt");
#elif UAVF_IDD_DESKTOP_ATTEMPT == 25
    BOOL launched=idd_start_captured_job(args,&startup,&child,L"C:\\ProgramData\\UAVF\\idd-working-job25.txt");
#else
    BOOL launched=idd_start_captured_job(args,&startup,&child,L"C:\\ProgramData\\UAVF\\idd-working-job24.txt");
#endif
    DWORD launchError=launched?0:GetLastError();
    if(!launched){idd_launch_note("BACKGROUND_JOB24_START_FAILED",launchError);return;}
    idd_launch_note("BACKGROUND_AUDIT_STARTED",child.dwProcessId);CloseHandle(child.hThread);
    HANDLE watcher=CreateThread(NULL,0,idd_observe_child,child.hProcess,0,NULL);
    if(watcher)CloseHandle(watcher);else{idd_launch_note("BACKGROUND_JOB24_WATCH_FAILED",GetLastError());CloseHandle(child.hProcess);}
#else
    if(!CreateProcessW(NULL,args,NULL,NULL,FALSE,CREATE_NO_WINDOW,NULL,NULL,&startup,&child)){
        idd_launch_note("BACKGROUND_AUDIT_START_FAILED",GetLastError());return;
    }
    idd_launch_note("BACKGROUND_AUDIT_STARTED",child.dwProcessId);
    CloseHandle(child.hThread);CloseHandle(child.hProcess);
#endif
#else
    swprintf(args,1500,L"-NoProfile -ExecutionPolicy Bypass -File \"%ls\"",selected);
    SHELLEXECUTEINFOW info={0};info.cbSize=sizeof(info);info.fMask=SEE_MASK_NOCLOSEPROCESS;
    info.lpVerb=IsUserAnAdmin()?L"open":L"runas";info.lpFile=L"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";info.lpParameters=args;info.nShow=SW_SHOW;
    if(!ShellExecuteExW(&info)){
        DWORD error=GetLastError(),written;
        idd_launch_note("NATIVE_PROCESS_START_FAILED",error);
        HANDLE log=CreateFileW(L"C:\\ProgramData\\UAVF\\idd-working-launch-error.bin",GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
        if(log!=INVALID_HANDLE_VALUE){WriteFile(log,&error,sizeof(error),&written,NULL);CloseHandle(log);}
        /* No automatic retry storm after a declined native security prompt. */
    }
    if(info.hProcess){idd_launch_note("NATIVE_PROCESS_STARTED",GetProcessId(info.hProcess));CloseHandle(info.hProcess);}
#endif
}
static DWORD WINAPI idd_working_launch_thread(void *unused){(void)unused;idd_working_launch();return 0;}
static void idd_working_request(void){
    HANDLE thread=CreateThread(NULL,0,idd_working_launch_thread,NULL,0,NULL);
    if(thread)CloseHandle(thread); /* Native elevation must not block display accept. */
}
