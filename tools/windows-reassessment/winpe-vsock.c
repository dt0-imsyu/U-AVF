/* Disposable WinPE: signed VirtIO socket driver + existing UVF1 proof recording.
 * Socket ABI reference: virtio-win viosock/inc/vio_sockets.h and lib/install.c.
 * Replay is a transport test, NOT live capture or Android display PASS. */
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include "winpe-input.h"
#ifdef UAVF_IDD_WORKING_LAUNCH
#include "idd-working-launch.h"
#endif
#ifndef UAVF_DIRECT_ACCEPT
#define UAVF_DIRECT_ACCEPT 0
#endif
typedef struct { HANDLE completed; FILE *report; } AcceptDeadline;
static HANDLE fault_report=INVALID_HANDLE_VALUE;
static LONG fault_count;
static LONG CALLBACK observe_fault(EXCEPTION_POINTERS *fault) {
    /* Own process only. Preserve original exception disposition and instructions.
     * Native unbuffered logging works even when CRT stderr was silent. */
    DWORD code=fault->ExceptionRecord->ExceptionCode;
    if(!(code&0x80000000)||fault_report==INVALID_HANDLE_VALUE||InterlockedIncrement(&fault_count)>16)
        return EXCEPTION_CONTINUE_SEARCH;
    char line[2300];wchar_t module[512]={0};HMODULE library=NULL;DWORD written=0;
    uintptr_t pc=(uintptr_t)fault->ExceptionRecord->ExceptionAddress;
    GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        (LPCWSTR)pc,&library);
    if(library)GetModuleFileNameW(library,module,512);
    char name[1600]={0};WideCharToMultiByte(CP_UTF8,0,module,-1,name,sizeof(name),NULL,NULL);
    unsigned char bytes[32]={0};SIZE_T count=0;
    BOOL readable=ReadProcessMemory(GetCurrentProcess(),(LPCVOID)pc,bytes,sizeof(bytes),&count);
    int length=snprintf(line,sizeof(line),"FIRST_CHANCE code=%08lx pc=%llx module=%s base=%llx rva=%llx thread=%lu flags=%lu parameters=%lu info0=%llx info1=%llx sp=%llx lr=%llx read=%d bytes=%llu\r\n",
        code,(unsigned long long)pc,name,(unsigned long long)(uintptr_t)library,
        (unsigned long long)(pc-(uintptr_t)library),GetCurrentThreadId(),fault->ExceptionRecord->ExceptionFlags,
        fault->ExceptionRecord->NumberParameters,(unsigned long long)fault->ExceptionRecord->ExceptionInformation[0],
        (unsigned long long)fault->ExceptionRecord->ExceptionInformation[1],
        (unsigned long long)fault->ContextRecord->Sp,(unsigned long long)fault->ContextRecord->Lr,
        readable,(unsigned long long)count);
    if(length>0&&length<(int)sizeof(line))WriteFile(fault_report,line,(DWORD)length,&written,NULL);
    if(readable){char hex[70];int position=0;for(SIZE_T i=0;i<count;i++)position+=snprintf(hex+position,sizeof(hex)-position,"%02x",bytes[i]);hex[position++]='\r';hex[position++]='\n';WriteFile(fault_report,hex,(DWORD)position,&written,NULL);}
    FlushFileBuffers(fault_report);
    return EXCEPTION_CONTINUE_SEARCH;
}
static DWORD WINAPI accept_deadline(void *opaque) {
    AcceptDeadline *deadline=opaque;
    if(WaitForSingleObject(deadline->completed,45000)==WAIT_TIMEOUT) {
        fprintf(deadline->report,"ACCEPT_DEADLINE=45000\n");fflush(deadline->report);
        /* Bound only this diagnostic producer; never terminate Setup or the VM. */
        ExitProcess(4);
    }
    return 0;
}
#pragma pack(push,1)
typedef struct {char magic[4];uint16_t version,kind;uint32_t codec,generation,sequence;
    uint16_t width,height;uint64_t pts;uint32_t length,reserved;} UvfHeader;
#pragma pack(pop)
_Static_assert(sizeof(UvfHeader)==40,"Shared UVF1 ABI");
typedef struct {ADDRESS_FAMILY family;USHORT reserved;UINT port,cid;} VsockAddress;
_Static_assert(sizeof(VsockAddress)==12,"official sockaddr_vm layout");
#ifdef UAVF_WINDOWS_CLIPBOARD
#define wmain windows_clipboard_stdio_entry
#include "windows-clipboard-agent.c"
#undef wmain
#include "windows-clipboard-relay.h"
#include "windows-clipboard-server.h"
#endif
static int send_all(SOCKET s,const void *data,int n) {
    const char *p=data;while(n){int sent=send(s,p,n,0);if(sent<=0)return 0;p+=sent;n-=sent;}return 1;
}
int wmain(int argc,wchar_t **argv) {
#ifdef UAVF_WINDOWS_CLIPBOARD
    if(argc==2&&!wcscmp(argv[1],L"--clipboard-worker")) {
        wchar_t *worker_args[]={argv[0],L"--stdio"};return windows_clipboard_stdio_entry(2,worker_args);
    }
    if((argc==2||argc==3)&&!wcscmp(argv[1],L"--clipboard-server"))return clipboard_server(argc==3?argv[2]:NULL);
    HANDLE clipboard_sidecar=NULL;
#endif
    /* Route CRT fatal diagnostics to the bootstrap's dedicated stderr file,
     * never to a modal dialog. This does not change socket/driver behavior. */
    _set_error_mode(_OUT_TO_STDERR);
    int live=argc==3&&!wcscmp(argv[2],L"--live");
    if(argc!=2&&!live)return 2;
#ifdef UAVF_IDD_WORKING_LAUNCH
    if(live)idd_working_request();
#endif
    wchar_t live_directory[1200];
    if(live) {
        int length=swprintf(live_directory,1200,L"%ls\\LIVE-SERVER-%lu-%llu",argv[1],GetCurrentProcessId(),(unsigned long long)GetTickCount64());
        if(length<0||length>=1200||!CreateDirectoryW(live_directory,NULL))return 6;
        argv[1]=live_directory; // New helper process preserves previous evidence.
    }
    wchar_t fault_path[1200];swprintf(fault_path,1200,L"%ls\\vsock-fault-report.txt",argv[1]);
    fault_report=CreateFileW(fault_path,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,
        FILE_ATTRIBUTE_NORMAL|FILE_FLAG_WRITE_THROUGH,NULL);
    if(fault_report==INVALID_HANDLE_VALUE)return 5;
    void *fault_observer=AddVectoredExceptionHandler(1,observe_fault);
    wchar_t path[1200];swprintf(path,1200,L"%ls\\%ls",argv[1],live?L"vsock-live-report.txt":L"vsock-report.txt");
    if(GetFileAttributesW(path)!=INVALID_FILE_ATTRIBUTES)return 3;
    FILE *report=_wfopen(path,L"wb");if(!report)return 3;
    int result=1;WSADATA wd;int started=0;SOCKET listener=INVALID_SOCKET;
    HANDLE dev=INVALID_HANDLE_VALUE;HMODULE dll=NULL;
    fprintf(report,"REPLAY_ONLY=%d\n",!live);fflush(report);
    dev=CreateFileW(L"\\??\\Viosock",GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(dev==INVALID_HANDLE_VALUE){fprintf(report,"DEVICE_ERROR=%lu\n",GetLastError());goto done;}
    DWORD af=0,cid=0,n=0;
    if(!DeviceIoControl(dev,0x0801300C,NULL,0,&af,sizeof(af),&n,NULL)||n!=4||!af)goto done;
    if(!DeviceIoControl(dev,0x08013004,NULL,0,&cid,sizeof(cid),&n,NULL)||n!=4)goto done;
    fprintf(report,"AF=%lu\nCID=%lu\n",af,cid);fflush(report);
    dll=LoadLibraryW(L"viosocklib.dll");if(!dll){fprintf(report,"WSP_LOAD_ERROR=%lu\n",GetLastError());goto done;}
    if(WSAStartup(MAKEWORD(2,2),&wd))goto done;started=1;
    listener=socket((int)af,SOCK_STREAM,0);
    if(listener==INVALID_SOCKET){
        BOOL (*install)(void)=(void*)GetProcAddress(dll,"InstallProtocol");
        if(!install||!install()){fprintf(report,"WSP_INSTALL_ERROR=%lu\n",GetLastError());goto done;}
        WSACleanup();started=0;if(WSAStartup(MAKEWORD(2,2),&wd))goto done;started=1;
        listener=socket((int)af,SOCK_STREAM,0);
    }
    if(listener==INVALID_SOCKET){fprintf(report,"SOCKET_ERROR=%d\n",WSAGetLastError());goto done;}
    VsockAddress address={(ADDRESS_FAMILY)af,0,4055,UINT32_MAX};
    if(bind(listener,(struct sockaddr*)&address,sizeof(address))||listen(listener,2)){
        fprintf(report,"LISTEN_ERROR=%d\n",WSAGetLastError());goto done;
    }
    fprintf(report,"LISTEN_READY=4055\n");fflush(report);
#ifdef UAVF_WINDOWS_CLIPBOARD
    if(live) {
        DWORD pid=0;clipboard_sidecar=start_clipboard_sidecar(&pid,argv[1]);
        fprintf(report,"OPTIONAL_CLIPBOARD_SIDECAR_PID=%lu; NOT_CLIPBOARD_PASS=1\n",pid);fflush(report);
    }
#endif
    for(unsigned generation=1;generation&&(live||generation<=2);generation++) {
#if !UAVF_DIRECT_ACCEPT
        if(!live) {
        fd_set reads;FD_ZERO(&reads);FD_SET(listener,&reads);struct timeval timeout={45,0};
        fprintf(report,"SELECT_BEGIN GENERATION=%u\n",generation);fflush(report);
        int selected=select(0,&reads,NULL,NULL,&timeout);
        fprintf(report,"SELECT_END RESULT=%d WSA=%d\n",selected,WSAGetLastError());fflush(report);
        if(selected<=0){fprintf(report,"ACCEPT_TIMEOUT\n");break;}
        }
#endif
        AcceptDeadline deadline={NULL,report};HANDLE watchdog=NULL;
        if(!live) {
            deadline.completed=CreateEventW(NULL,TRUE,FALSE,NULL);
            if(!deadline.completed)goto done;
            watchdog=CreateThread(NULL,0,accept_deadline,&deadline,0,NULL);
            if(!watchdog){CloseHandle(deadline.completed);goto done;}
        }
        fprintf(report,"ACCEPT_BEGIN DIRECT=%d GENERATION=%u\n",live||UAVF_DIRECT_ACCEPT,generation);fflush(report);
        /* Shipped signed provider calls its GetPeerName unconditionally.
         * RVA31d0 dereferenced NULL addrlen with accept(NULL,NULL). Supply both
         * valid outputs; leave the signed DLL and socket protocol untouched. */
        VsockAddress peer={0};int peer_length=sizeof(peer);
        SOCKET client=accept(listener,(struct sockaddr*)&peer,&peer_length);int accept_error=WSAGetLastError();
        if(watchdog) {
            SetEvent(deadline.completed);WaitForSingleObject(watchdog,INFINITE);
            CloseHandle(watchdog);CloseHandle(deadline.completed);
        }
        fprintf(report,"ACCEPT_END VALID=%d WSA=%d\n",client!=INVALID_SOCKET,accept_error);fflush(report);
        fprintf(report,"PEER_LENGTH=%d PEER_CID=%lu PEER_PORT=%lu\n",peer_length,(unsigned long)peer.cid,(unsigned long)peer.port);fflush(report);
        if(client==INVALID_SOCKET)goto done;
        DWORD timeout_ms=5000;setsockopt(client,SOL_SOCKET,SO_SNDTIMEO,(char*)&timeout_ms,sizeof(timeout_ms));
        setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,(char*)&timeout_ms,sizeof(timeout_ms));
        int width=GetSystemMetrics(SM_CXSCREEN),height=GetSystemMetrics(SM_CYSCREEN);
        if(width<2||height<2||width>4096||height>4096){closesocket(client);goto done;}
        UvfHeader hello={{'U','V','F','1'},1,4,1,generation,0,(uint16_t)width,(uint16_t)height,0,0,0};
        uint64_t host_clock=0;int got=0;
        fprintf(report,"CLOCK_SEND_BEGIN\n");fflush(report);
        int hello_sent=send_all(client,&hello,40);
        fprintf(report,"CLOCK_SEND_END OK=%d WSA=%d\n",hello_sent,WSAGetLastError());fflush(report);
        if(hello_sent)while(got<8){int r=recv(client,((char*)&host_clock)+got,8-got,0);if(r<=0)break;got+=r;}
        fprintf(report,"CLOCK_ACK_BYTES=%d WSA=%d\n",got,WSAGetLastError());fflush(report);
        unsigned frames=0,last_sequence=0;ULONGLONG started=GetTickCount64();
        DWORD input_timeout=1000;setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,(char*)&input_timeout,sizeof(input_timeout));
        UinState input_state={0};input_state.client=client;
        HANDLE input_thread=got==8?CreateThread(NULL,0,input_worker,&input_state,0,NULL):NULL;
        FILE *input=NULL;
        if(!live){swprintf(path,1200,L"%ls\\capture.uvf",argv[1]);input=_wfopen(path,L"rb");}
        unsigned char *payload=malloc(4194304);
        if(!live&&got==8&&input&&payload){
            UvfHeader h;
            while(fread(&h,1,40,input)==40){
                if(memcmp(h.magic,"UVF1",4)||h.version!=1||h.kind!=2||!h.length||h.length>4194304)break;
                if(fread(payload,1,h.length,input)!=h.length)break;
                h.generation=generation;
                if(!send_all(client,&h,40)||!send_all(client,payload,(int)h.length))break;
                frames++;last_sequence=h.sequence;Sleep(100);
            }
        }
        if(live&&got==8&&payload) {
            wchar_t folder[1200],command[2400],system[260];
            swprintf(folder,1200,L"%ls\\LIVE-%u",argv[1],generation);
            if(CreateDirectoryW(folder,NULL)&&GetEnvironmentVariableW(L"SystemDrive",system,260)) {
                SECURITY_ATTRIBUTES sa={sizeof(sa),NULL,TRUE};HANDLE reader=NULL,writer=NULL;
                swprintf(path,1200,L"%ls\\encoder-stderr.txt",folder);
                HANDLE errors=CreateFileW(path,GENERIC_WRITE,0,&sa,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
                if(errors!=INVALID_HANDLE_VALUE&&CreatePipe(&reader,&writer,&sa,32768)) {
                    SetHandleInformation(reader,HANDLE_FLAG_INHERIT,0);
                    STARTUPINFOW si={0};PROCESS_INFORMATION pi={0};si.cb=sizeof(si);
                    si.dwFlags=STARTF_USESTDHANDLES;si.hStdInput=GetStdHandle(STD_INPUT_HANDLE);
                    si.hStdOutput=writer;si.hStdError=errors;
                    swprintf(command,2400,L"\"%ls\\Windows\\System32\\winpe-encode.exe\" \"%ls\" --stdout",system,folder);
                    if(CreateProcessW(NULL,command,NULL,NULL,TRUE,0,NULL,NULL,&si,&pi)) {
                        CloseHandle(pi.hThread);CloseHandle(writer);writer=NULL;
                        for(;;) {
                            UvfHeader h;DWORD n=0;unsigned have=0;
                            while(have<40&&ReadFile(reader,((char*)&h)+have,40-have,&n,NULL)&&n)have+=n;
                            if(have!=40||memcmp(h.magic,"UVF1",4)||h.version!=1||h.codec!=1||!h.length||h.length>4194304)break;
                            if(h.kind!=2 && !(h.kind==6 && h.length<=1024 && h.sequence==0 && h.pts==0 && h.reserved==0))break;
                            have=0;while(have<h.length&&ReadFile(reader,payload+have,h.length-have,&n,NULL)&&n)have+=n;
                            if(have!=h.length)break;
                            h.generation=generation;if(h.kind==2)h.pts+=host_clock/1000;
                            if(!send_all(client,&h,40)||!send_all(client,payload,(int)h.length))break;
                            if(h.kind==2){frames++;last_sequence=h.sequence;}
                        }
                        CloseHandle(reader);reader=NULL; // Broken pipe stops only this encoder helper.
                        if(WaitForSingleObject(pi.hProcess,5000)==WAIT_TIMEOUT) {
                            // Only our encoder child; never Setup or any other process.
                            TerminateProcess(pi.hProcess,10);WaitForSingleObject(pi.hProcess,5000);
                        }
                        CloseHandle(pi.hProcess);
                    }
                    if(reader)CloseHandle(reader);if(writer)CloseHandle(writer);
                }
                if(errors!=INVALID_HANDLE_VALUE)CloseHandle(errors);
            }
        }
        free(payload);if(input)fclose(input);
        UvfHeader end={{'U','V','F','1'},1,3,1,generation,last_sequence+1,(uint16_t)width,(uint16_t)height,frames*100000ULL,0,0};
        if(frames)send_all(client,&end,40);
        InterlockedExchange(&input_state.stopping,1);shutdown(client,SD_BOTH);closesocket(client);
        if(input_thread){WaitForSingleObject(input_thread,INFINITE);CloseHandle(input_thread);}
        fprintf(report,"GENERATION=%u ACK_BYTES=%d SENT_FRAMES=%u ELAPSED_MS=%llu INPUT_OK=%u INPUT_REJECTED=%u\n",generation,got,frames,(unsigned long long)(GetTickCount64()-started),input_state.accepted,input_state.rejected);fflush(report);
    }
    result=0;
done:
#ifdef UAVF_WINDOWS_CLIPBOARD
    if(clipboard_sidecar)CloseHandle(clipboard_sidecar);
#endif
    fprintf(report,"EXIT=%d WIN32=%lu WSA=%d\n",result,GetLastError(),started?WSAGetLastError():0);
    if(listener!=INVALID_SOCKET)closesocket(listener);if(started)WSACleanup();
    if(dll)FreeLibrary(dll);if(dev!=INVALID_HANDLE_VALUE)CloseHandle(dev);fclose(report);
    if(fault_observer)RemoveVectoredExceptionHandler(fault_observer);CloseHandle(fault_report);return result;
}
