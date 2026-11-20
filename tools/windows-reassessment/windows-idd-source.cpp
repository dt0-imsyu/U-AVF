#include <windows.h>
#include <stdio.h>
#include <new>
#define UAVF_IDD_AUTH_ONLY
#include "idd-frame-receiver-probe.h"
#include "windows-idd-source.h"
struct IddEncoderSource {
    UavfIddFrameReader reader;
    unsigned char *pixels=nullptr;
};
extern "C" void *uavf_idd_open(unsigned timeout_ms,unsigned long *error) {
    if(error)*error=ERROR_INVALID_PARAMETER;
    if(!timeout_ms || timeout_ms>50000)return nullptr;
    HANDLE token=nullptr;unsigned char user[256];DWORD size=0;
    if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token))return nullptr;
    bool system=GetTokenInformation(token,TokenUser,user,sizeof(user),&size) &&
        IsWellKnownSid(reinterpret_cast<TOKEN_USER *>(user)->User.Sid,WinLocalSystemSid);
    CloseHandle(token);
    if(!system){if(error)*error=ERROR_ACCESS_DENIED;return nullptr;}
    void *memory=HeapAlloc(GetProcessHeap(),0,sizeof(IddEncoderSource));
    auto *source=memory?new(memory) IddEncoderSource:nullptr;
    if(!source){if(error)*error=ERROR_NOT_ENOUGH_MEMORY;return nullptr;}
    ULONGLONG deadline=GetTickCount64()+timeout_ms;bool connected=false;
    do {
        DWORD pid=idd_probe_admitted_host();
        if(pid && source->reader.open(L"\\\\.\\pipe\\UAVF-IddSourceProbe-3EF669B7",pid,
            L"C:\\Windows\\System32\\WUDFHost.exe")){connected=true;break;}
        Sleep(100);
    }while(GetTickCount64()<deadline);
    if(connected)source->pixels=static_cast<unsigned char *>(HeapAlloc(GetProcessHeap(),0,UAVF_IDD_FRAME_BYTES));
    if(!source->pixels){source->~IddEncoderSource();HeapFree(GetProcessHeap(),0,source);if(error)*error=connected?ERROR_NOT_ENOUGH_MEMORY:ERROR_TIMEOUT;return nullptr;}
    if(error)*error=ERROR_SUCCESS;return source;
}
extern "C" int uavf_idd_next(void *opaque,const unsigned char **bgra,uint64_t *sequence,unsigned timeout_ms) {
    if(!opaque || !bgra || !sequence)return -1;
    *bgra=nullptr;
    auto *source=static_cast<IddEncoderSource *>(opaque);UavfIddWireHeader header;
    if(!source->reader.read(&header,source->pixels,UAVF_IDD_FRAME_BYTES,timeout_ms))
        return GetLastError()==ERROR_TIMEOUT && source->reader.connected()?0:-1;
    *bgra=source->pixels;*sequence=header.sequence;return 1;
}
extern "C" void uavf_idd_close(void *opaque) {
    auto *source=static_cast<IddEncoderSource *>(opaque);
    if(!source)return;
    source->reader.close();if(source->pixels)HeapFree(GetProcessHeap(),0,source->pixels);
    source->~IddEncoderSource();HeapFree(GetProcessHeap(),0,source);
}
