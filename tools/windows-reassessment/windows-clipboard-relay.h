/* Optional clipboard ONLY: socket <-> owned stdio child. No UVF1/frames.
 * The clipboard sidecar is a separate process so a provider/clipboard failure
 * cannot block the display producer. Callers own/close the accepted socket. */
typedef struct {SOCKET client;HANDLE pipe;volatile LONG stopping;} ClipboardUpload;
typedef struct {DWORD stage,error,child_exit;bool child_exited;} ClipboardRelayOutcome;
static DWORD WINAPI clipboard_upload(void *opaque) {
    ClipboardUpload *state=opaque;char buffer[8192];
    while(!InterlockedCompareExchange(&state->stopping,0,0)) {
        int n=recv(state->client,buffer,sizeof(buffer),0);
        if(n==SOCKET_ERROR&&WSAGetLastError()==WSAETIMEDOUT)continue;
        if(n<=0)break;
        for(int offset=0;offset<n;) {
            if(InterlockedCompareExchange(&state->stopping,0,0))return 0;
            DWORD written=0;
            if(!WriteFile(state->pipe,buffer+offset,(DWORD)(n-offset),&written,NULL)||!written)goto done;
            offset+=(int)written;
        }
    }
done:
    InterlockedExchange(&state->stopping,1);return 0;
}
static HANDLE clipboard_job(void) {
    HANDLE job=CreateJobObjectW(NULL,NULL);JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={0};
    limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(job&&!SetInformationJobObject(job,JobObjectExtendedLimitInformation,&limits,sizeof(limits))) {
        CloseHandle(job);job=NULL;
    }
    return job;
}
static int clipboard_relay(SOCKET client,const wchar_t *self,bool (*allowed)(void),ClipboardRelayOutcome *outcome) {
    ClipboardRelayOutcome observed={1,0,0,false};
    DWORD receive_ms=200,send_ms=1000;
    if(setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,(char*)&receive_ms,sizeof(receive_ms))||
       setsockopt(client,SOL_SOCKET,SO_SNDTIMEO,(char*)&send_ms,sizeof(send_ms))) {
        observed.error=(DWORD)WSAGetLastError();if(outcome)*outcome=observed;return 1;
    }
    SECURITY_ATTRIBUTES attrs={sizeof(attrs),NULL,TRUE};
    HANDLE in_read=NULL,in_write=NULL,out_read=NULL,out_write=NULL,nul=INVALID_HANDLE_VALUE;
    HANDLE job=NULL,upload=NULL;PROCESS_INFORMATION child={0};ClipboardUpload state={0};
    int result=2;bool fatal=false;
    observed.stage=2;
    if(!CreatePipe(&in_read,&in_write,&attrs,0)||!CreatePipe(&out_read,&out_write,&attrs,0))goto done;
    observed.stage=3;
    if(!SetHandleInformation(in_write,HANDLE_FLAG_INHERIT,0)||
       !SetHandleInformation(out_read,HANDLE_FLAG_INHERIT,0))goto done;
    observed.stage=4;
    nul=CreateFileW(L"NUL",GENERIC_READ|GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,&attrs,OPEN_EXISTING,0,NULL);
    if(nul==INVALID_HANDLE_VALUE)goto done;
    wchar_t command[2*MAX_PATH];
    int length=swprintf(command,2*MAX_PATH,L"\"%ls\" --clipboard-worker",self);
    if(length<0||length>=2*MAX_PATH)goto done;
    STARTUPINFOW startup={0};startup.cb=sizeof(startup);startup.dwFlags=STARTF_USESTDHANDLES;
    startup.hStdInput=in_read;startup.hStdOutput=out_write;startup.hStdError=nul;
    observed.stage=5;job=clipboard_job();if(!job)goto done;
    observed.stage=6;
    if(!CreateProcessW(self,command,NULL,NULL,TRUE,CREATE_NO_WINDOW|CREATE_SUSPENDED,NULL,NULL,&startup,&child))goto done;
    observed.stage=7;if(!AssignProcessToJobObject(job,child.hProcess))goto done;
    observed.stage=8;if(ResumeThread(child.hThread)==(DWORD)-1)goto done;
    CloseHandle(child.hThread);child.hThread=NULL;
    CloseHandle(in_read);in_read=NULL;CloseHandle(out_write);out_write=NULL;
    state.client=client;state.pipe=in_write;
    observed.stage=9;upload=CreateThread(NULL,0,clipboard_upload,&state,0,NULL);if(!upload)goto done;
    result=0;
    observed.stage=10;
    while(!InterlockedCompareExchange(&state.stopping,0,0)&&allowed()) {
        DWORD available=0;
        if(!PeekNamedPipe(out_read,NULL,0,NULL,&available,NULL)){observed.stage=11;observed.error=GetLastError();break;}
        if(!available){if(WaitForSingleObject(child.hProcess,0)==WAIT_OBJECT_0){observed.stage=12;break;}Sleep(20);continue;}
        char bytes[8192];DWORD count=0;
        if(!ReadFile(out_read,bytes,available>sizeof(bytes)?sizeof(bytes):available,&count,NULL)||!count){observed.stage=13;observed.error=GetLastError();break;}
        for(DWORD offset=0;offset<count;) {
            int sent=send(client,bytes+offset,(int)(count-offset),0);
            if(sent<=0){result=3;observed.stage=14;observed.error=(DWORD)WSAGetLastError();goto done;}offset+=(DWORD)sent;
        }
    }
done:
    if(result==2)observed.error=GetLastError();
    // Observe BEFORE cleanup kills our child; never report forced termination
    // as a natural worker exit. No clipboard data enters this structure.
    if(child.hProcess&&WaitForSingleObject(child.hProcess,0)==WAIT_OBJECT_0)
        observed.child_exited=GetExitCodeProcess(child.hProcess,&observed.child_exit)!=0;
    if(outcome)*outcome=observed;
    InterlockedExchange(&state.stopping,1);shutdown(client,SD_BOTH);
    if(upload)CancelSynchronousIo(upload);
    // Close our job first: kills ONLY our clipboard child and unblocks pipes.
    if(job){CloseHandle(job);job=NULL;}
    if(child.hProcess) {
        if(WaitForSingleObject(child.hProcess,1000)==WAIT_TIMEOUT) {
            TerminateProcess(child.hProcess,20);WaitForSingleObject(child.hProcess,1000);
        }
    }
    if(upload&&WaitForSingleObject(upload,2000)!=WAIT_OBJECT_0)fatal=true;
    // This routine belongs to a dedicated OPTIONAL sidecar. A stuck provider
    // must not leave a thread referencing this stack or impact display.
    if(fatal)ExitProcess(30);
    if(upload)CloseHandle(upload);if(child.hThread)CloseHandle(child.hThread);
    if(child.hProcess)CloseHandle(child.hProcess);
    if(in_read)CloseHandle(in_read);if(in_write)CloseHandle(in_write);
    if(out_read)CloseHandle(out_read);if(out_write)CloseHandle(out_write);
    if(nul!=INVALID_HANDLE_VALUE)CloseHandle(nul);return result;
}
