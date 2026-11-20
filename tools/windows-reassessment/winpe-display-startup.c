/* Disposable WinPE only: keep Setup as foreground application; take three
 * bounded screenshots to a uniquely marked diagnostic FAT volume. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {HANDLE process;wchar_t report[1400];} ProducerExit;
static DWORD WINAPI record_producer_exit(void *opaque) {
    ProducerExit *state=opaque;
    DWORD wait=WaitForSingleObject(state->process,INFINITE),code=0;
    BOOL queried=GetExitCodeProcess(state->process,&code);
    HANDLE file=CreateFileW(state->report,GENERIC_WRITE,FILE_SHARE_READ,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_WRITE_THROUGH,NULL);
    if(file!=INVALID_HANDLE_VALUE){
        char text[192];DWORD bytes=0;
        int length=snprintf(text,sizeof(text),"PRODUCER_EXIT=0x%08lx\nWAIT=%lu\nQUERIED=%d\nNOT_SETUP_OR_VM_EXIT=1\n",code,wait,queried);
        if(length>0&&length<(int)sizeof(text))WriteFile(file,text,(DWORD)length,&bytes,NULL);
        FlushFileBuffers(file);CloseHandle(file);
    }
    CloseHandle(state->process);free(state);return 0;
}
static int start_producer(wchar_t *command,const wchar_t *directory) {
    STARTUPINFOW si={0};PROCESS_INFORMATION pi={0};si.cb=sizeof(si);
    /* Capture runtime-library diagnostics: exit 255 is not our return/deadline.
     * Only the producer inherits these dedicated handles; Setup is unchanged. */
    wchar_t diagnostic[1400];swprintf(diagnostic,1400,L"%ls\\vsock-stderr.txt",directory);
    SECURITY_ATTRIBUTES inherit={sizeof(inherit),NULL,TRUE};
    HANDLE errors=CreateFileW(diagnostic,GENERIC_WRITE,FILE_SHARE_READ,&inherit,
        CREATE_NEW,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_WRITE_THROUGH,NULL);
    HANDLE input=CreateFileW(L"NUL",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,
        &inherit,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,NULL);
    if(errors==INVALID_HANDLE_VALUE||input==INVALID_HANDLE_VALUE){
        if(errors!=INVALID_HANDLE_VALUE)CloseHandle(errors);
        if(input!=INVALID_HANDLE_VALUE)CloseHandle(input);return -4;
    }
    si.dwFlags=STARTF_USESTDHANDLES;si.hStdInput=input;si.hStdOutput=errors;si.hStdError=errors;
    BOOL created=CreateProcessW(NULL,command,NULL,NULL,TRUE,0,NULL,NULL,&si,&pi);
    CloseHandle(input);CloseHandle(errors);
    if(!created)return -1;
    CloseHandle(pi.hThread);
    ProducerExit *state=calloc(1,sizeof(*state));
    if(!state){CloseHandle(pi.hProcess);return -2;}
    state->process=pi.hProcess;
    swprintf(state->report,1400,L"%ls\\vsock-exit-report.txt",directory);
    HANDLE observer=CreateThread(NULL,0,record_producer_exit,state,0,NULL);
    if(!observer){CloseHandle(pi.hProcess);free(state);return -3;}
    CloseHandle(observer);return 0;
}

static int run(wchar_t *command, BOOL wait) {
    STARTUPINFOW si={0}; PROCESS_INFORMATION pi={0}; si.cb=sizeof(si);
    if(!CreateProcessW(NULL,command,NULL,NULL,FALSE,0,NULL,NULL,&si,&pi)) return -1;
    CloseHandle(pi.hThread);
    int result=0;
    if(wait) {
        DWORD state=WaitForSingleObject(pi.hProcess,60000), code=0;
        if(state==WAIT_OBJECT_0 && GetExitCodeProcess(pi.hProcess,&code)) result=(int)code;
        else result=-2; /* Never terminate the process or Setup. */
    }
    CloseHandle(pi.hProcess); return result;
}

static void status_process(const wchar_t *label) {
    wchar_t self[512],dir[512],target[1024],command[1200];
    if(!GetModuleFileNameW(NULL,self,512) || !GetTempPathW(512,dir)) return;
    swprintf(target,1024,L"%ls%ls.exe",dir,label);
    if(!CopyFileW(self,target,TRUE)) return;
    swprintf(command,1200,L"\"%ls\" --status",target);run(command,FALSE);
}

static int find_source(wchar_t selected[512]) {
    for(int attempt=0;attempt<30 && !selected[0];attempt++) {
        wchar_t volume[512],marker[1024];
        HANDLE search=FindFirstVolumeW(volume,512);
        if(search!=INVALID_HANDLE_VALUE) {
            do {
                swprintf(marker,1024,L"%lsUAVF-DISPLAY.TAG",volume);
                DWORD attr=GetFileAttributesW(marker);
                if(attr!=INVALID_FILE_ATTRIBUTES && !(attr&FILE_ATTRIBUTE_DIRECTORY)) {
                    if(selected[0]) {FindVolumeClose(search);return -1;}
                    wcscpy(selected,volume);
                }
            } while(FindNextVolumeW(search,volume,512));
            FindVolumeClose(search);
        }
        if(!selected[0]) Sleep(1000);
    }
    return selected[0]?1:0;
}

#if UAVF_GUIDED_INSTALL
static BOOL approved_install(const wchar_t *volume) {
    static const char expected[]="UAVF_WINDOWS_INSTALL_APPROVED_V1\r\n";
    wchar_t path[1024],label[64],filesystem[32],mounted[512];
    swprintf(path,1024,L"%lsUAVF\\INSTALL.OK",volume);
    FILE *file=_wfopen(path,L"rb");
    if(!file)return FALSE;
    char bytes[sizeof(expected)];
    size_t length=fread(bytes,1,sizeof(bytes),file);
    fclose(file);
    if(length!=sizeof(expected)-1 || memcmp(bytes,expected,length)!=0)return FALSE;
    if(!GetVolumeInformationW(volume,label,64,NULL,NULL,NULL,filesystem,32)
            || _wcsicmp(label,L"WINSETUP") || _wcsicmp(filesystem,L"FAT32"))return FALSE;
    if(!GetVolumeNameForVolumeMountPointW(L"S:\\",mounted,512)) {
        if(GetLogicalDrives() & (1u<<('S'-'A')))return FALSE;
        if(!SetVolumeMountPointW(L"S:\\",volume)
                || !GetVolumeNameForVolumeMountPointW(L"S:\\",mounted,512))return FALSE;
    }
    return _wcsicmp(mounted,volume)==0;
}
#endif

int wmain(int argc,wchar_t **argv) {
    (void)argv;
    if(argc>1) {Sleep(120000);return 0;}
    wchar_t system[260], setup[520];
    if(!GetEnvironmentVariableW(L"SystemDrive",system,260)) return 2;
    wchar_t storage[1024];
    swprintf(storage,1024,L"\"%ls\\Windows\\System32\\drvload.exe\" \"%ls\\Windows\\System32\\uavf-drivers\\viostor\\viostor.inf\"",system,system);
    int load=run(storage,TRUE);
    wchar_t label[32];swprintf(label,32,L"drv%08x",(unsigned)load);status_process(label);
    wchar_t message[128];swprintf(message,128,L"UAVF_DISPLAY viostor_drvload=%d\n",load);
    OutputDebugStringW(message);
    HWND console=GetConsoleWindow();if(console) ShowWindow(console,SW_HIDE);
    wchar_t selected[512]={0};
    wchar_t directory[1024]={0};
    int source=0;
    DWORD flags=0;
#if UAVF_GUIDED_INSTALL
    source=find_source(selected);
    BOOL guided=source>0 && approved_install(selected);
    if(guided) {
        swprintf(setup,520,L"\"%ls\\Windows\\System32\\cmd.exe\" /d /c S:\\UAVF\\INSTALL.CMD --approved",system);
        flags=CREATE_NEW_CONSOLE;
    } else
#endif
        swprintf(setup,520,L"\"%ls\\setup.exe\"",system);
    STARTUPINFOW si={0}; PROCESS_INFORMATION pi={0}; si.cb=sizeof(si);
    if(!CreateProcessW(NULL,setup,NULL,NULL,FALSE,flags,NULL,NULL,&si,&pi)) return 3;
    CloseHandle(pi.hThread);
    if(!source)source=find_source(selected);
    if(source<0) {status_process(L"ambiguous");goto keep_setup;}
    if(!source) {status_process(L"nofat");goto keep_setup;}
    status_process(L"foundfat");
#if UAVF_GUIDED_INSTALL
    // Preview and approved install reuse one disk. Keep the transient capture
    // files on WinPE's RAM drive so an earlier run cannot block the producer.
    swprintf(directory,1024,L"%ls\\UAVF-DISPLAY",system);
#else
    swprintf(directory,1024,L"%lsUAVF-DISPLAY",selected);
#endif
    if(!CreateDirectoryW(directory,NULL)) goto keep_setup; /* Preserve evidence. */
    for(int i=0;i<3;i++) {
        Sleep(5000);
        wchar_t command[2048];
        swprintf(command,2048,L"\"%ls\\Windows\\System32\\winpe-screen.exe\" \"%ls\\screen-%d.bmp\"",system,directory,i);
        int capture=run(command,TRUE);
        swprintf(label,32,L"cap%d%08x",i,(unsigned)capture);status_process(label);
    }
    {
        wchar_t command[2048];
        swprintf(command,2048,L"\"%ls\\Windows\\System32\\winpe-encode.exe\" \"%ls\"",system,directory);
        int encode=run(command,TRUE);
        swprintf(label,32,L"enc%08x",(unsigned)encode);status_process(label);
        if(encode==0) {
            swprintf(command,2048,L"\"%ls\\Windows\\System32\\drvload.exe\" \"%ls\\Windows\\System32\\uavf-drivers\\viosock\\viosock.inf\"",system,system);
            int socket_driver=run(command,TRUE);
            swprintf(label,32,L"sockdrv%08x",(unsigned)socket_driver);status_process(label);
            if(socket_driver==0) {
#if UAVF_LIVE_DISPLAY
                swprintf(command,2048,L"\"%ls\\Windows\\System32\\winpe-vsock.exe\" \"%ls\" --live",system,directory);
#else
                swprintf(command,2048,L"\"%ls\\Windows\\System32\\winpe-vsock.exe\" \"%ls\"",system,directory);
#endif
                int server=start_producer(command,directory);
                swprintf(label,32,L"sockstart%08x",(unsigned)server);status_process(label);
            }
        }
    }
keep_setup:
    /* Setup.exe can be only a launcher. Its exit is not installation completion
     * and must not end winpeshl's shell lifetime while producer/SetupHost run. */
    WaitForSingleObject(pi.hProcess,INFINITE);
    DWORD setup_exit=0;GetExitCodeProcess(pi.hProcess,&setup_exit);
    CloseHandle(pi.hProcess);
#if UAVF_GUIDED_INSTALL
    if(guided) {
        wchar_t path[1024];
        swprintf(path,1024,L"%lsUAVF\\SETUP.LOG",selected);
        FILE *report=_wfopen(path,L"ab");
        if(report) {fprintf(report,"INSTALL_EXIT=%lu\r\n",setup_exit);fclose(report);}
    }
#endif
    if(directory[0]) {
        wchar_t destination[1400],source[1024];
        swprintf(destination,1400,L"%ls\\setup-exit-report.txt",directory);
        FILE *report=_wfopen(destination,L"wb");
        if(report){fprintf(report,"SETUP_LAUNCHER_EXIT=%lu\nSHELL_RETAINED=1\nNOT_INSTALL_COMPLETE=1\n",setup_exit);fclose(report);}
        const wchar_t *logs[]={L"Windows\\Panther\\setupact.log",L"Windows\\Panther\\setuperr.log",
            L"$WINDOWS.~BT\\Sources\\Panther\\setupact.log",L"$WINDOWS.~BT\\Sources\\Panther\\setuperr.log",
            L"Windows\\System32\\wpeinit.log"};
        for(unsigned i=0;i<sizeof(logs)/sizeof(logs[0]);i++) {
            swprintf(source,1024,L"%ls\\%ls",system,logs[i]);
            swprintf(destination,1400,L"%ls\\setup-log-%u.txt",directory,i);
            CopyFileW(source,destination,TRUE); /* Never replace retained evidence. */
        }
    }
    for(;;)Sleep(1000); /* AVF stop/reboot remains controlled by the owner/guest. */
}
