/* Disposable WinPE bootstrap. No UI input, disk format, partition changes,
 * firmware changes or signed Windows code patches. Only exact labelled root. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
void *memset(void *p,int c,size_t n) {volatile unsigned char *s=p;while(n--)*s++=(unsigned char)c;return p;}
void *memcpy(void *d,const void *s,size_t n) {volatile unsigned char *a=d;const volatile unsigned char *b=s;while(n--)*a++=*b++;return d;}
static DWORD child(wchar_t *command,HANDLE log,DWORD seconds) {
    STARTUPINFOW si={0};PROCESS_INFORMATION pi={0};si.cb=sizeof(si);
    if(log!=INVALID_HANDLE_VALUE){si.dwFlags=STARTF_USESTDHANDLES;si.hStdOutput=si.hStdError=log;si.hStdInput=GetStdHandle(STD_INPUT_HANDLE);}
    if(!CreateProcessW(0,command,0,0,log!=INVALID_HANDLE_VALUE,CREATE_NO_WINDOW,0,0,&si,&pi))return GetLastError();
    CloseHandle(pi.hThread);DWORD code=ERROR_TIMEOUT;
    if(WaitForSingleObject(pi.hProcess,seconds*1000)==WAIT_OBJECT_0)GetExitCodeProcess(pi.hProcess,&code);
    // Never terminate DISM while it writes. A timeout fails without reboot.
    CloseHandle(pi.hProcess);return code;
}
static BOOL mount(const wchar_t *drive,const wchar_t *volume) {
    wchar_t current[512];
    if(GetVolumeNameForVolumeMountPointW(drive,current,512))return lstrcmpiW(current,volume)==0;
    return SetVolumeMountPointW(drive,volume);
}
void idd_start(void) {
    static wchar_t system[64],command[1200],source[512],root[512];
    if(!GetEnvironmentVariableW(L"SystemDrive",system,64)||lstrcmpiW(system,L"X:"))ExitProcess(10);
    lstrcpyW(command,L"X:\\Windows\\System32\\drvload.exe X:\\Windows\\System32\\uavf-drivers\\viostor\\viostor.inf");
    if(child(command,INVALID_HANDLE_VALUE,60))ExitProcess(11);
    DWORD sources=0,roots=0;
    for(DWORD attempt=0;attempt<30;attempt++) {
        static wchar_t volume[512],label[64],path[1200];sources=roots=0;source[0]=root[0]=0;
        HANDLE find=FindFirstVolumeW(volume,512);
        if(find!=INVALID_HANDLE_VALUE) {
            do {
                lstrcpyW(path,volume);lstrcatW(path,L"UAVFIDD\\fresh-root-provenance.txt");
                if(GetFileAttributesW(path)!=INVALID_FILE_ATTRIBUTES){sources++;lstrcpyW(source,volume);}
                if(GetVolumeInformationW(volume,label,64,0,0,0,0,0)&&!lstrcmpW(label,L"UAVF_IDD_TEST")){roots++;lstrcpyW(root,volume);}
            }while(FindNextVolumeW(find,volume,512));
            FindVolumeClose(find);
        }
        if(sources==1&&roots==1)break;
        if(sources>1||roots>1)ExitProcess(12);
        Sleep(1000);
    }
    if(sources!=1||roots!=1)ExitProcess(13);
    if(!mount(L"S:\\",source)||!mount(L"W:\\",root))ExitProcess(14);
    SECURITY_ATTRIBUTES inherit={sizeof(inherit),0,TRUE};
    HANDLE log=CreateFileW(L"S:\\UAVFIDD\\bootstrap-log.txt",GENERIC_WRITE,FILE_SHARE_READ,&inherit,CREATE_NEW,FILE_ATTRIBUTE_NORMAL|FILE_FLAG_WRITE_THROUGH,0);
    if(log==INVALID_HANDLE_VALUE)ExitProcess(15);
    const char begin[]="IDD_NATIVE_BOOTSTRAP_ENTERED; ONLY_LABELLED_DISPOSABLE_ROOT\r\n";DWORD written;
    WriteFile(log,begin,sizeof(begin)-1,&written,0);
    lstrcpyW(command,L"X:\\Windows\\System32\\cmd.exe /d /c X:\\UAVFIddJob\\provision.cmd");
    DWORD result=child(command,log,1200);
    FlushFileBuffers(log);CloseHandle(log);
    if(result)ExitProcess(result);
    lstrcpyW(command,L"X:\\Windows\\System32\\wpeutil.exe reboot");
    ExitProcess(child(command,INVALID_HANDLE_VALUE,60));
}
