/* Diagnostic Windows only: short circular-memory ETW code sampling.
 * No LSASS dump, thread suspension, security setting or signed-code changes.
 * Trace metadata stays private/local; never publish the ETL with a release. */
static const char cpu_profile_xml[]=
"<?xml version=\"1.0\" encoding=\"utf-8\"?>"
"<WindowsPerformanceRecorder Version=\"1.0\" Author=\"U-AVF\" Company=\"U-AVF\"><Profiles>"
"<SystemCollector Id=\"UAVFCPUCollector\" Name=\"U-AVF CPU instruction sample\">"
"<BufferSize Value=\"64\"/><Buffers Value=\"64\"/></SystemCollector>"
"<SystemProvider Id=\"UAVFCPUProvider\"><Keywords>"
"<Keyword Value=\"ProcessThread\"/><Keyword Value=\"Loader\"/><Keyword Value=\"SampledProfile\"/>"
"</Keywords></SystemProvider>"
"<Profile Id=\"UAVFCPUSample.Verbose.Memory\" Name=\"UAVFCPUSample\" Description=\"Bounded CPU instruction samples; no memory dump\" LoggingMode=\"Memory\" DetailLevel=\"Verbose\">"
"<Collectors><SystemCollectorId Value=\"UAVFCPUCollector\"><SystemProviderId Value=\"UAVFCPUProvider\"/>"
"</SystemCollectorId></Collectors></Profile></Profiles></WindowsPerformanceRecorder>";

static DWORD profile_command(const wchar_t *command,HANDLE output) {
    wchar_t copy[4*MAX_PATH];
    if(wcslen(command)>=4*MAX_PATH)return ERROR_BUFFER_OVERFLOW;
    wcscpy(copy,command);
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);
    startup.dwFlags=STARTF_USESTDHANDLES;
    startup.hStdOutput=startup.hStdError=output;
    SECURITY_ATTRIBUTES attributes={sizeof(attributes),NULL,TRUE};
    HANDLE input=CreateFileW(L"NUL",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,
        &attributes,OPEN_EXISTING,0,NULL);
    if(input==INVALID_HANDLE_VALUE)return GetLastError();
    startup.hStdInput=input;PROCESS_INFORMATION process={0};
    // Prior capture proved the normal-priority WPR child received almost no CPU
    // while LSASS saturated the one vCPU. Briefly prioritize ONLY our recorder
    // start/stop child; it exits before the steady CPU sample interval.
    BOOL created=CreateProcessW(NULL,copy,NULL,NULL,TRUE,CREATE_NO_WINDOW|HIGH_PRIORITY_CLASS,NULL,system_path,&startup,&process);
    DWORD error=GetLastError();CloseHandle(input);
    if(!created)return error;
    DWORD status=WaitForSingleObject(process.hProcess,60000),result=ERROR_TIMEOUT;
    if(status==WAIT_OBJECT_0)GetExitCodeProcess(process.hProcess,&result);
    else {
        // Only our own WPR child, never LSASS or another Windows process.
        TerminateProcess(process.hProcess,ERROR_TIMEOUT);
        WaitForSingleObject(process.hProcess,5000);
    }
    CloseHandle(process.hThread);CloseHandle(process.hProcess);return result;
}

static DWORD WINAPI cpu_profile_worker(void *unused) {
    (void)unused;
    // Allow normal display/session startup first. One recording per boot.
    if(WaitForSingleObject(stop_event,15000)!=WAIT_TIMEOUT)return 0;
    wchar_t folder[MAX_PATH],profile[MAX_PATH],trace[MAX_PATH],log[MAX_PATH],wpr[MAX_PATH],command[4*MAX_PATH];
    if(!GetEnvironmentVariableW(L"ProgramData",folder,MAX_PATH))return 1;
    if(swprintf(profile,MAX_PATH,L"%ls\\UAVF\\cpu-sample-v1.wprp",folder)<0||
       swprintf(trace,MAX_PATH,L"%ls\\UAVF\\cpu-sample-%lu.etl",folder,GetCurrentProcessId())<0||
       swprintf(log,MAX_PATH,L"%ls\\UAVF\\cpu-profile-%lu.log",folder,GetCurrentProcessId())<0||
       swprintf(wpr,MAX_PATH,L"%ls\\wpr.exe",system_path)<0)return 2;
    if(GetFileAttributesW(wpr)==INVALID_FILE_ATTRIBUTES){log_state("CPU_PROFILE_WPR_UNAVAILABLE",GetLastError());return 3;}
    if(GetFileAttributesW(trace)!=INVALID_FILE_ATTRIBUTES){log_state("CPU_PROFILE_EXISTING_TRACE_PRESERVED",0);return 4;}
    HANDLE file=CreateFileW(profile,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    DWORD written=0;
    if(file!=INVALID_HANDLE_VALUE) {
        BOOL ok=WriteFile(file,cpu_profile_xml,sizeof(cpu_profile_xml)-1,&written,NULL);
        CloseHandle(file);
        if(!ok||written!=sizeof(cpu_profile_xml)-1){log_state("CPU_PROFILE_XML_WRITE_FAILED",GetLastError());return 5;}
    } else {
        if(GetLastError()!=ERROR_FILE_EXISTS){log_state("CPU_PROFILE_XML_CREATE_FAILED",GetLastError());return 6;}
        char existing[sizeof(cpu_profile_xml)];
        file=CreateFileW(profile,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
        BOOL ok=file!=INVALID_HANDLE_VALUE&&ReadFile(file,existing,sizeof(existing),&written,NULL);
        if(file!=INVALID_HANDLE_VALUE)CloseHandle(file);
        if(!ok||written!=sizeof(cpu_profile_xml)-1||memcmp(existing,cpu_profile_xml,written)) {
            log_state("CPU_PROFILE_CUSTOM_XML_PRESERVED",0);return 7;
        }
    }
    SECURITY_ATTRIBUTES attributes={sizeof(attributes),NULL,TRUE};
    HANDLE output=CreateFileW(log,GENERIC_WRITE,FILE_SHARE_READ,&attributes,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    if(output==INVALID_HANDLE_VALUE){log_state("CPU_PROFILE_LOG_CREATE_FAILED",GetLastError());return 8;}
    if(swprintf(command,4*MAX_PATH,L"\"%ls\" -start \"%ls!UAVFCPUSample\" -instancename UAVFCPU%lu",wpr,profile,GetCurrentProcessId())<0){CloseHandle(output);return 9;}
    DWORD result=profile_command(command,output);log_state("CPU_PROFILE_START_RESULT",result);
    if(result==ERROR_TIMEOUT) {
        // Start may have partially configured our named session before timeout.
        // Cancel only that exact instance, never another WPR user's recording.
        if(swprintf(command,4*MAX_PATH,L"\"%ls\" -cancel -instancename UAVFCPU%lu",wpr,GetCurrentProcessId())>=0)
            log_state("CPU_PROFILE_OWN_START_TIMEOUT_CANCEL_RESULT",profile_command(command,output));
    } else if(result==0) {
        // 4MiB circular buffers, not an unbounded file-mode recording.
        WaitForSingleObject(stop_event,20000);
        if(swprintf(command,4*MAX_PATH,L"\"%ls\" -stop \"%ls\" -skipPdbGen -instancename UAVFCPU%lu",wpr,trace,GetCurrentProcessId())>=0) {
            result=profile_command(command,output);log_state("CPU_PROFILE_STOP_RESULT",result);
            if(result!=0&&swprintf(command,4*MAX_PATH,L"\"%ls\" -cancel -instancename UAVFCPU%lu",wpr,GetCurrentProcessId())>=0)
                log_state("CPU_PROFILE_OWN_CANCEL_RESULT",profile_command(command,output));
        }
    }
    // A failed start must never cancel somebody else's recording.
    FlushFileBuffers(output);CloseHandle(output);return result;
}
