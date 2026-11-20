/* Explicit own diagnostic child: separate log, no parent UART/socket inheritance. */
static BOOL idd_start_captured_job(wchar_t *command,const STARTUPINFOW *startup,
                                  PROCESS_INFORMATION *child,const wchar_t *logPath) {
    SECURITY_ATTRIBUTES security={sizeof(security),NULL,TRUE};
    HANDLE logFile=CreateFileW(logPath,GENERIC_WRITE,FILE_SHARE_READ,&security,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    if(logFile==INVALID_HANDLE_VALUE)return FALSE;
    HANDLE nullInput=CreateFileW(L"NUL",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,&security,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    DWORD error=GetLastError();
    if(nullInput==INVALID_HANDLE_VALUE){CloseHandle(logFile);SetLastError(error);return FALSE;}
    SIZE_T bytes=0;InitializeProcThreadAttributeList(NULL,1,0,&bytes);
    LPPROC_THREAD_ATTRIBUTE_LIST attributes=(LPPROC_THREAD_ATTRIBUTE_LIST)HeapAlloc(GetProcessHeap(),0,bytes);
    if(!attributes){CloseHandle(logFile);CloseHandle(nullInput);SetLastError(ERROR_NOT_ENOUGH_MEMORY);return FALSE;}
    BOOL initialized=InitializeProcThreadAttributeList(attributes,1,0,&bytes);
    HANDLE handles[]={logFile,nullInput};
    BOOL ready=initialized&&UpdateProcThreadAttribute(attributes,0,PROC_THREAD_ATTRIBUTE_HANDLE_LIST,handles,sizeof(handles),NULL,NULL);
    STARTUPINFOEXW extended={0};extended.StartupInfo=*startup;
    extended.StartupInfo.cb=sizeof(extended);extended.StartupInfo.dwFlags|=STARTF_USESTDHANDLES;
    extended.StartupInfo.hStdInput=nullInput;extended.StartupInfo.hStdOutput=logFile;extended.StartupInfo.hStdError=logFile;
    extended.lpAttributeList=attributes;
    BOOL launched=ready&&CreateProcessW(NULL,command,NULL,NULL,TRUE,CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT,NULL,NULL,&extended.StartupInfo,child);
    error=launched?ERROR_SUCCESS:GetLastError();
    if(initialized)DeleteProcThreadAttributeList(attributes);
    HeapFree(GetProcessHeap(),0,attributes);CloseHandle(logFile);CloseHandle(nullInput);
    SetLastError(error);return launched;
}
