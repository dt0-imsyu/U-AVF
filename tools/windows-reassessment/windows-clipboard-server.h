/* Same signed viosock provider as display; separate process/port4054. */
static HANDLE clipboard_status_file=INVALID_HANDLE_VALUE;
static unsigned clipboard_status_lines;
static void clipboard_status(unsigned event,DWORD a,DWORD b,DWORD c,DWORD d) {
    if(clipboard_status_file==INVALID_HANDLE_VALUE||clipboard_status_lines>=64)return;
    clipboard_status_lines++;
    char line[160];DWORD written=0;
    int n=snprintf(line,sizeof(line),"CLIPBOARD_STATUS event=%u a=%lu b=%lu c=%lu d=%lu\r\n",event,a,b,c,d);
    if(n>0&&n<(int)sizeof(line))WriteFile(clipboard_status_file,line,(DWORD)n,&written,NULL);
    FlushFileBuffers(clipboard_status_file);
}
static int clipboard_server_once(void) {
    if(!clipboard_desktop_allowed())return 2;
    wchar_t self[MAX_PATH];DWORD chars=GetModuleFileNameW(NULL,self,MAX_PATH);
    if(!chars||chars>=MAX_PATH)return 3;
    HANDLE dev=CreateFileW(L"\\??\\Viosock",GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,0,NULL);
    if(dev==INVALID_HANDLE_VALUE)return 4;
    DWORD af=0,received=0;
    BOOL ok=DeviceIoControl(dev,0x0801300C,NULL,0,&af,sizeof(af),&received,NULL);
    DWORD cid=0,cid_bytes=0;
    BOOL cid_ok=DeviceIoControl(dev,0x08013004,NULL,0,&cid,sizeof(cid),&cid_bytes,NULL);
    CloseHandle(dev);if(!ok||received!=4||!af||af>65535)return 5;
    if(cid_ok&&cid_bytes==4)clipboard_status(2,cid,af,0,0);
    // The display producer already initializes the installed signed provider.
    // Never install/register another provider from the optional clipboard path.
    HMODULE provider=LoadLibraryW(L"viosocklib.dll");if(!provider)return 6;
    WSADATA data;bool started=WSAStartup(MAKEWORD(2,2),&data)==0;int result=7;
    SOCKET listener=INVALID_SOCKET;
    if(!started)goto done;
    listener=socket((int)af,SOCK_STREAM,0);if(listener==INVALID_SOCKET)goto done;
    VsockAddress address={(ADDRESS_FAMILY)af,0,4054,UINT32_MAX};
    if(bind(listener,(struct sockaddr*)&address,sizeof(address))||listen(listener,1))goto done;
    u_long nonblocking=1;if(ioctlsocket(listener,FIONBIO,&nonblocking))goto done;
    result=0;
    while(clipboard_desktop_allowed()) {
        VsockAddress peer={0};int length=sizeof(peer);
        SOCKET client=accept(listener,(struct sockaddr*)&peer,&length);
        if(client==INVALID_SOCKET) {
            if(WSAGetLastError()!=WSAEWOULDBLOCK){result=8;break;}
            Sleep(100);continue;
        }
        nonblocking=0;
        if(length==sizeof(peer)&&peer.family==af&&peer.cid==2&&!ioctlsocket(client,FIONBIO,&nonblocking)) {
            ClipboardRelayOutcome observed;
            int relay_result=clipboard_relay(client,self,clipboard_desktop_allowed,&observed);
            clipboard_status(3,observed.stage,observed.error,observed.child_exited?observed.child_exit:STILL_ACTIVE,(DWORD)relay_result);
        }
        closesocket(client);
    }
done:
    if(listener!=INVALID_SOCKET)closesocket(listener);
    if(started)WSACleanup();FreeLibrary(provider);return result;
}
#include "windows-clipboard-retry.h"
static int clipboard_server_attempt(void) {
    int result=clipboard_server_once();clipboard_status(4,(DWORD)result,0,0,0);return result;
}
static int clipboard_server(const wchar_t *directory) {
    if(directory) {
        wchar_t path[2*MAX_PATH];int n=swprintf(path,2*MAX_PATH,L"%ls\\clipboard-status.txt",directory);
        if(n>0&&n<2*MAX_PATH)clipboard_status_file=CreateFileW(path,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_WRITE_THROUGH,NULL);
    }
    DWORD session=0;ProcessIdToSessionId(GetCurrentProcessId(),&session);
    clipboard_status(1,GetCurrentProcessId(),session,0,0);
    int result=clipboard_retry_loop(clipboard_server_attempt,clipboard_desktop_allowed,Sleep);
    if(clipboard_status_file!=INVALID_HANDLE_VALUE)CloseHandle(clipboard_status_file);
    return result;
}
static HANDLE start_clipboard_sidecar(DWORD *pid,const wchar_t *directory) {
    if(!clipboard_desktop_allowed())return NULL;
    wchar_t self[MAX_PATH],command[3*MAX_PATH];DWORD chars=GetModuleFileNameW(NULL,self,MAX_PATH);
    if(!chars||chars>=MAX_PATH)return NULL;
    if(!directory||wcschr(directory,L'"')||wcslen(directory)>=MAX_PATH)return NULL;
    int length=swprintf(command,3*MAX_PATH,L"\"%ls\" --clipboard-server \"%ls\"",self,directory);
    if(length<0||length>=3*MAX_PATH)return NULL;
    HANDLE job=clipboard_job();if(!job)return NULL;
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);PROCESS_INFORMATION child={0};
    if(!CreateProcessW(self,command,NULL,NULL,FALSE,CREATE_NO_WINDOW|CREATE_SUSPENDED,NULL,NULL,&startup,&child)) {
        CloseHandle(job);return NULL;
    }
    bool ok=AssignProcessToJobObject(job,child.hProcess)&&ResumeThread(child.hThread)!=(DWORD)-1;
    if(!ok)TerminateProcess(child.hProcess,20);else *pid=child.dwProcessId;
    CloseHandle(child.hThread);CloseHandle(child.hProcess);
    if(!ok){CloseHandle(job);return NULL;}return job;
}
