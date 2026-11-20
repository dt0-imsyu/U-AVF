#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <wchar.h>
#include "idd-captured-job.h"
int wmain(int argc,wchar_t **argv) {
    if(argc!=2)return 1;
    wchar_t system[MAX_PATH],command[512];
    if(!GetSystemDirectoryW(system,MAX_PATH))return 2;
    swprintf(command,512,L"\"%ls\\cmd.exe\" /d /c \"echo UAVF_OWN_CHILD_TEST&exit /b 37\"",system);
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);PROCESS_INFORMATION child={0};
    // Native child execution only, no UI automation or Windows authentication.
    if(!idd_start_captured_job(command,&startup,&child,argv[1]))return 3;
    DWORD code=0;
    if(WaitForSingleObject(child.hProcess,10000)!=WAIT_OBJECT_0 || !GetExitCodeProcess(child.hProcess,&code))return 4;
    CloseHandle(child.hThread);CloseHandle(child.hProcess);
    if(code!=37)return 5;
    FILE *log=_wfopen(argv[1],L"rb");char line[128]={0};
    if(!log)return 6;fread(line,1,127,log);fclose(log);
    if(!strstr(line,"UAVF_OWN_CHILD_TEST"))return 7;
    // Existing evidence must never be overwritten or executed a second time.
    swprintf(command,512,L"\"%ls\\cmd.exe\" /d /c exit /b 0",system);
    if(idd_start_captured_job(command,&startup,&child,argv[1]) || GetLastError()!=ERROR_FILE_EXISTS)return 8;
    puts("CAPTURED_CHILD_EXIT37_STDOUT_AND_EXISTING_LOG_GUARD_PASS; NOT_GUEST_OR_FPS_PASS");return 0;
}
