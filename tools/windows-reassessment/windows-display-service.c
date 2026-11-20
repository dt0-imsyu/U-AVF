/* Isolated installed-Windows display broker. SCM service remains in Session0;
 * a windowless worker runs in the actual console session and starts the existing
 * GDI/OpenH264/vsock producer on the active desktop. No signed-image patches,
 * login bypass, desktop ACL changes, alternate codec, or Android root access. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <userenv.h>
#include <stdio.h>
#include <wchar.h>
#include <tlhelp32.h>

static unsigned long long filetime_value(FILETIME value) {
    ULARGE_INTEGER result;result.LowPart=value.dwLowDateTime;result.HighPart=value.dwHighDateTime;
    return result.QuadPart;
}

static int cpu_log_rotation_needed(unsigned sequence,long long bytes) {
    return sequence==0&&bytes>=512*1024;
}

/* Bounded read-only scheduling evidence. No command lines, user documents,
 * process termination, priority changes or privilege enabling. */
static void cpu_snapshot(unsigned sequence) {
    wchar_t folder[MAX_PATH],path[MAX_PATH];
    if(!GetEnvironmentVariableW(L"ProgramData",folder,MAX_PATH))return;
    if(swprintf(path,MAX_PATH,L"%ls\\UAVF\\runtime-cpu.log",folder)<0)return;
    HANDLE file=CreateFileW(path,FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,
        NULL,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(file==INVALID_HANDLE_VALUE)return;
    LARGE_INTEGER size;
    if(!GetFileSizeEx(file,&size)){CloseHandle(file);return;}
    if(size.QuadPart>=512*1024) {
        CloseHandle(file);
        if(!cpu_log_rotation_needed(sequence,size.QuadPart))return;
        // Preserve the last full OWN metadata log, then start bounded evidence
        // for this boot. Never touch Windows/user logs or increase the cap.
        wchar_t previous[MAX_PATH];
        if(swprintf(previous,MAX_PATH,L"%ls\\UAVF\\runtime-cpu.previous.log",folder)<0)return;
        if(!MoveFileExW(path,previous,MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH))return;
        file=CreateFileW(path,FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,
            NULL,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
        if(file==INVALID_HANDLE_VALUE)return;
    }
    FILETIME idle={0},kernel={0},user={0};LARGE_INTEGER counter,freq;
    BOOL system_ok=GetSystemTimes(&idle,&kernel,&user);
    QueryPerformanceCounter(&counter);QueryPerformanceFrequency(&freq);
    char line[512];DWORD written;
    int n=snprintf(line,sizeof(line),
        "CPU_SAMPLE seq=%u broker=%lu tick=%llu qpc=%llu freq=%llu system_ok=%d idle_100ns=%llu kernel_100ns=%llu user_100ns=%llu processors=%lu\r\n",
        sequence,GetCurrentProcessId(),(unsigned long long)GetTickCount64(),
        (unsigned long long)counter.QuadPart,(unsigned long long)freq.QuadPart,(int)system_ok,
        filetime_value(idle),filetime_value(kernel),filetime_value(user),
        (unsigned long)GetActiveProcessorCount(ALL_PROCESSOR_GROUPS));
    if(n>0&&n<(int)sizeof(line))WriteFile(file,line,(DWORD)n,&written,NULL);
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0);
    DWORD hot_pid=0;
    if(snapshot!=INVALID_HANDLE_VALUE) {
        PROCESSENTRY32W entry={0};entry.dwSize=sizeof(entry);unsigned count=0;
        for(BOOL found=Process32FirstW(snapshot,&entry);found&&count<256;
                found=Process32NextW(snapshot,&entry),count++) {
            FILETIME created={0},exited={0},pk={0},pu={0};
            if(!_wcsicmp(entry.szExeFile,L"lsass.exe"))hot_pid=entry.th32ProcessID;
            HANDLE process=OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,entry.th32ProcessID);
            BOOL ok=process&&GetProcessTimes(process,&created,&exited,&pk,&pu);
            DWORD error=ok?0:GetLastError();if(process)CloseHandle(process);
            n=snprintf(line,sizeof(line),
                "CPU_PROCESS seq=%u pid=%lu name=%ls threads=%lu ok=%d error=%lu created_100ns=%llu kernel_100ns=%llu user_100ns=%llu\r\n",
                sequence,entry.th32ProcessID,entry.szExeFile,entry.cntThreads,(int)ok,error,
                filetime_value(created),filetime_value(pk),filetime_value(pu));
            if(n>0&&n<(int)sizeof(line))WriteFile(file,line,(DWORD)n,&written,NULL);
        }
        CloseHandle(snapshot);
    }
    if(hot_pid) {
        typedef LONG (WINAPI *query_thread_fn)(HANDLE,ULONG,void*,ULONG,ULONG*);
        query_thread_fn query=(query_thread_fn)GetProcAddress(GetModuleHandleW(L"ntdll.dll"),"NtQueryInformationThread");
        HANDLE threads=CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD,0);
        if(threads!=INVALID_HANDLE_VALUE) {
            THREADENTRY32 entry={0};entry.dwSize=sizeof(entry);unsigned all=0,count=0;
            for(BOOL found=Thread32First(threads,&entry);found&&all<4096&&count<64;
                    found=Thread32Next(threads,&entry),all++) {
                if(entry.th32OwnerProcessID!=hot_pid)continue;
                count++;
                FILETIME created={0},exited={0},tk={0},tu={0};void *start=NULL;
                HANDLE thread=OpenThread(THREAD_QUERY_LIMITED_INFORMATION,FALSE,entry.th32ThreadID);
                BOOL ok=thread&&GetThreadTimes(thread,&created,&exited,&tk,&tu);
                DWORD error=ok?0:GetLastError();if(thread)CloseHandle(thread);
                LONG status=(LONG)0xc0000022;
                // Query metadata only. Never suspend/get context/read memory or
                // enable SeDebugPrivilege against the authentication process.
                thread=OpenThread(THREAD_QUERY_INFORMATION,FALSE,entry.th32ThreadID);
                if(thread&&query)status=query(thread,9,&start,sizeof(start),NULL);
                if(thread)CloseHandle(thread);
                n=snprintf(line,sizeof(line),
                    "CPU_HOT_THREAD seq=%u pid=%lu tid=%lu priority=%ld ok=%d error=%lu created_100ns=%llu kernel_100ns=%llu user_100ns=%llu start_status=%08lx start_address=%llx\r\n",
                    sequence,hot_pid,entry.th32ThreadID,entry.tpBasePri,(int)ok,error,
                    filetime_value(created),filetime_value(tk),filetime_value(tu),
                    (unsigned long)status,(unsigned long long)(ULONG_PTR)start);
                if(n>0&&n<(int)sizeof(line))WriteFile(file,line,(DWORD)n,&written,NULL);
            }
            CloseHandle(threads);
        }
    }
    CloseHandle(file);
}

static const wchar_t service_name[]=L"UAVFWindowsDisplay";
static HANDLE stop_event;
static SERVICE_STATUS_HANDLE status_handle;
static wchar_t self_path[MAX_PATH],system_path[MAX_PATH];

static void log_state(const char *event,DWORD value) {
    wchar_t folder[MAX_PATH],path[MAX_PATH];
    if(!GetEnvironmentVariableW(L"ProgramData",folder,MAX_PATH))return;
    if(swprintf(path,MAX_PATH,L"%ls\\UAVF",folder)<0)return;
    CreateDirectoryW(path,NULL);
    if(swprintf(path,MAX_PATH,L"%ls\\UAVF\\display-broker.log",folder)<0)return;
    HANDLE file=CreateFileW(path,FILE_APPEND_DATA,FILE_SHARE_READ|FILE_SHARE_WRITE,
                            NULL,OPEN_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if(file==INVALID_HANDLE_VALUE)return;
    LARGE_INTEGER size;
    // Bounded diagnostics; never fill storage because a desktop is unavailable.
    if(GetFileSizeEx(file,&size)&&size.QuadPart<1024*1024) {
        DWORD session=0,written=0;ProcessIdToSessionId(GetCurrentProcessId(),&session);
        char line[256];int count=snprintf(line,sizeof(line),
            "tick=%llu pid=%lu session=%lu event=%s value=%lu\r\n",
            (unsigned long long)GetTickCount64(),GetCurrentProcessId(),session,event,value);
        if(count>0&&count<(int)sizeof(line))WriteFile(file,line,(DWORD)count,&written,NULL);
    }
    CloseHandle(file);
}

#include "windows-cpu-profile.h"

static void retire_legacy_autostart(void) {
    // Only our exact superseded bootstrap. Preserve user/customized Run entries.
    static const wchar_t expected[]=L"cmd.exe /d /c start /min cmd.exe /d /c uavf-installed-display.cmd";
    HKEY key=NULL;wchar_t value[512]={0};DWORD type=0,size=sizeof(value);
    LONG result=RegOpenKeyExW(HKEY_LOCAL_MACHINE,
        L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",0,
        KEY_QUERY_VALUE|KEY_SET_VALUE|KEY_WOW64_64KEY,&key);
    if(result!=ERROR_SUCCESS){log_state("LEGACY_RUN_OPEN_FAILED",result);return;}
    result=RegQueryValueExW(key,L"UAVFDisplay",NULL,&type,(BYTE*)value,&size);
    if(result==ERROR_SUCCESS) {
        if(type==REG_SZ&&size==sizeof(expected)&&!memcmp(value,expected,sizeof(expected))) {
            result=RegDeleteValueW(key,L"UAVFDisplay");
            log_state("LEGACY_RUN_RETIRED",result);
        }else log_state("LEGACY_RUN_CUSTOM_VALUE_PRESERVED",0);
    }else if(result!=ERROR_FILE_NOT_FOUND)log_state("LEGACY_RUN_QUERY_FAILED",result);
    RegCloseKey(key);
}

static HANDLE child_job(void) {
    HANDLE job=CreateJobObjectW(NULL,NULL);
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
    limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(job&&!SetInformationJobObject(job,JobObjectExtendedLimitInformation,&limits,sizeof(limits))) {
        CloseHandle(job);job=NULL;
    }
    return job;
}

static BOOL own_child(HANDLE job,PROCESS_INFORMATION *process) {
    if(!job||!AssignProcessToJobObject(job,process->hProcess)) {
        DWORD error=GetLastError();TerminateProcess(process->hProcess,20);
        CloseHandle(process->hThread);CloseHandle(process->hProcess);
        log_state("OWN_CHILD_JOB_FAILED",error);return FALSE;
    }
    ResumeThread(process->hThread);CloseHandle(process->hThread);return TRUE;
}

static BOOL enable_privilege(HANDLE token,const wchar_t *name) {
    TOKEN_PRIVILEGES privilege={0};privilege.PrivilegeCount=1;
    if(!LookupPrivilegeValueW(NULL,name,&privilege.Privileges[0].Luid))return FALSE;
    privilege.Privileges[0].Attributes=SE_PRIVILEGE_ENABLED;
    SetLastError(ERROR_SUCCESS);
    return AdjustTokenPrivileges(token,FALSE,&privilege,0,NULL,NULL)&&GetLastError()==ERROR_SUCCESS;
}

static BOOL spawn_agent(DWORD session,HANDLE job,PROCESS_INFORMATION *process) {
    HANDLE token=NULL,primary=NULL;void *environment=NULL;BOOL ok=FALSE;
    DWORD error=ERROR_SUCCESS;
    if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY|TOKEN_DUPLICATE|TOKEN_ADJUST_PRIVILEGES,&token))goto done;
    if(!enable_privilege(token,SE_TCB_NAME)||!enable_privilege(token,SE_ASSIGNPRIMARYTOKEN_NAME)
            ||!enable_privilege(token,SE_INCREASE_QUOTA_NAME))goto done;
    if(!DuplicateTokenEx(token,TOKEN_ALL_ACCESS,NULL,SecurityImpersonation,TokenPrimary,&primary))goto done;
    if(!SetTokenInformation(primary,TokenSessionId,&session,sizeof(session)))goto done;
    if(!CreateEnvironmentBlock(&environment,primary,FALSE))goto done;
    wchar_t command[2*MAX_PATH];
    if(swprintf(command,2*MAX_PATH,L"\"%ls\" --agent",self_path)<0)goto done;
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);startup.lpDesktop=L"winsta0\\default";
    if(!CreateProcessAsUserW(primary,self_path,command,NULL,NULL,FALSE,
            CREATE_NO_WINDOW|CREATE_UNICODE_ENVIRONMENT|CREATE_SUSPENDED,
            environment,system_path,&startup,process))goto done;
    ok=own_child(job,process);
done:
    error=GetLastError();if(environment)DestroyEnvironmentBlock(environment);
    if(primary)CloseHandle(primary);if(token)CloseHandle(token);
    if(!ok)log_state("CONSOLE_AGENT_FAILED",error);
    return ok;
}

static int agent(void) {
    HANDLE mutex=CreateMutexW(NULL,TRUE,L"Local\\UAVFWindowsDisplayAgent");
    if(!mutex)return 21;
    if(GetLastError()==ERROR_ALREADY_EXISTS){CloseHandle(mutex);return 22;}
    HANDLE job=NULL,process=NULL;wchar_t previous[128]={0};
    DWORD last_error=0;
    log_state("CONSOLE_AGENT_STARTED",0);
    for(;;) {
        wchar_t name[128]={0};DWORD size=0;
        HDESK desktop=OpenInputDesktop(0,FALSE,DESKTOP_READOBJECTS);
        BOOL available=desktop&&GetUserObjectInformationW(desktop,UOI_NAME,name,sizeof(name),&size);
        DWORD error=available?0:GetLastError();if(desktop)CloseDesktop(desktop);
        if(!available) {
            if(error!=last_error){log_state("INPUT_DESKTOP_UNAVAILABLE",error);last_error=error;}
            Sleep(500);continue;
        }
        last_error=0;
        BOOL exited=process&&WaitForSingleObject(process,0)==WAIT_OBJECT_0;
        if(!process||exited||wcscmp(previous,name)) {
            if(job){CloseHandle(job);job=NULL;}
            if(process){CloseHandle(process);process=NULL;}
            wchar_t folder[MAX_PATH],command[4*MAX_PATH],desktop_path[160];
            if(!GetEnvironmentVariableW(L"ProgramData",folder,MAX_PATH))return 23;
            wchar_t output[MAX_PATH];
            if(swprintf(output,MAX_PATH,L"%ls\\UAVF\\WindowsDisplay",folder)<0)return 24;
            CreateDirectoryW(output,NULL);
            if(swprintf(command,4*MAX_PATH,L"\"%ls\\winpe-vsock.exe\" \"%ls\" --live",system_path,output)<0)return 25;
            if(swprintf(desktop_path,160,L"winsta0\\%ls",name)<0)return 26;
            SECURITY_ATTRIBUTES attributes={sizeof(attributes),NULL,TRUE};
            HANDLE null_io=CreateFileW(L"NUL",GENERIC_READ|GENERIC_WRITE,
                FILE_SHARE_READ|FILE_SHARE_WRITE,&attributes,OPEN_EXISTING,0,NULL);
            STARTUPINFOW startup={0};startup.cb=sizeof(startup);startup.lpDesktop=desktop_path;
            startup.dwFlags=STARTF_USESTDHANDLES;
            startup.hStdInput=startup.hStdOutput=startup.hStdError=null_io;
            PROCESS_INFORMATION child={0};job=child_job();
            BOOL created=job&&null_io!=INVALID_HANDLE_VALUE&&CreateProcessW(NULL,command,NULL,NULL,TRUE,
                CREATE_NO_WINDOW|CREATE_SUSPENDED,NULL,system_path,&startup,&child);
            error=GetLastError();if(null_io!=INVALID_HANDLE_VALUE)CloseHandle(null_io);
            if(created&&own_child(job,&child)) {
                process=child.hProcess;wcscpy(previous,name);
                log_state(!wcscmp(name,L"Winlogon")?"PRODUCER_WINLOGON_STARTED":"PRODUCER_DESKTOP_STARTED",child.dwProcessId);
            }else {
                if(job){CloseHandle(job);job=NULL;}
                log_state("PRODUCER_START_FAILED",error);Sleep(2000);
            }
        }
        Sleep(500);
    }
}

static void report_status(DWORD state,DWORD error) {
    SERVICE_STATUS status={0};status.dwServiceType=SERVICE_WIN32_OWN_PROCESS;
    status.dwCurrentState=state;status.dwWin32ExitCode=error;
    status.dwControlsAccepted=state==SERVICE_RUNNING?SERVICE_ACCEPT_STOP|SERVICE_ACCEPT_SHUTDOWN:0;
    status.dwWaitHint=state==SERVICE_START_PENDING||state==SERVICE_STOP_PENDING?5000:0;
    SetServiceStatus(status_handle,&status);
}

static DWORD WINAPI control(DWORD code,DWORD type,void *event,void *context) {
    (void)type;(void)event;(void)context;
    if(code==SERVICE_CONTROL_STOP||code==SERVICE_CONTROL_SHUTDOWN) {
        report_status(SERVICE_STOP_PENDING,0);SetEvent(stop_event);return NO_ERROR;
    }
    return code==SERVICE_CONTROL_INTERROGATE?NO_ERROR:ERROR_CALL_NOT_IMPLEMENTED;
}

static BOOL profile_on_start;

static void WINAPI service_main(DWORD argc,wchar_t **argv) {
    (void)argc;(void)argv;
    status_handle=RegisterServiceCtrlHandlerExW(service_name,control,NULL);
    if(!status_handle)return;
    report_status(SERVICE_START_PENDING,0);stop_event=CreateEventW(NULL,TRUE,FALSE,NULL);
    if(!stop_event){report_status(SERVICE_STOPPED,GetLastError());return;}
    report_status(SERVICE_RUNNING,0);log_state("SERVICE_STARTED",0);
    HANDLE profile_worker=NULL;
    if (profile_on_start) {
        retire_legacy_autostart();
        profile_worker=CreateThread(NULL,0,cpu_profile_worker,NULL,0,NULL);
        if(!profile_worker)log_state("CPU_PROFILE_WORKER_CREATE_FAILED",GetLastError());
    }
    ULONGLONG next_cpu_sample=GetTickCount64();unsigned cpu_samples=0;
    HANDLE job=NULL,process=NULL;DWORD previous=0xffffffff;
    while(WaitForSingleObject(stop_event,500)==WAIT_TIMEOUT) {
        if(profile_on_start&&cpu_samples<8&&GetTickCount64()>=next_cpu_sample) {
            cpu_snapshot(cpu_samples++);next_cpu_sample=GetTickCount64()+15000;
        }
        DWORD session=WTSGetActiveConsoleSessionId();
        if(session==0xffffffff||session==0)continue;
        if(session!=previous||!process||WaitForSingleObject(process,0)==WAIT_OBJECT_0) {
            if(job){CloseHandle(job);job=NULL;}
            if(process){CloseHandle(process);process=NULL;}
            PROCESS_INFORMATION child={0};job=child_job();
            if(job&&spawn_agent(session,job,&child)) {
                process=child.hProcess;previous=session;log_state("CONSOLE_AGENT_CREATED",session);
            }else {if(job){CloseHandle(job);job=NULL;}WaitForSingleObject(stop_event,2000);}
        }
    }
    if(job)CloseHandle(job);if(process)CloseHandle(process);
    if(profile_worker){WaitForSingleObject(profile_worker,130000);CloseHandle(profile_worker);}
    CloseHandle(stop_event);log_state("SERVICE_STOPPED",0);report_status(SERVICE_STOPPED,0);
}

int wmain(int argc,wchar_t **argv) {
    if(!GetModuleFileNameW(NULL,self_path,MAX_PATH)||!GetSystemDirectoryW(system_path,MAX_PATH))return 2;
    if(argc==2&&!wcscmp(argv[1],L"--agent"))return agent();
    if(argc==2&&!wcscmp(argv[1],L"--cpu-profile")) {
        // Explicit diagnostic entry point; permits later observation without reboot.
        stop_event=CreateEventW(NULL,TRUE,FALSE,NULL);if(!stop_event)return 4;
        DWORD result=cpu_profile_worker(NULL);CloseHandle(stop_event);return (int)result;
    }
    if(argc!=2)return 3;
    profile_on_start=!wcscmp(argv[1],L"--service");
    if(!profile_on_start&&wcscmp(argv[1],L"--display-service"))return 3;
    SERVICE_TABLE_ENTRYW table[]={{(wchar_t*)service_name,service_main},{NULL,NULL}};
    return StartServiceCtrlDispatcherW(table)?0:(int)GetLastError();
}
