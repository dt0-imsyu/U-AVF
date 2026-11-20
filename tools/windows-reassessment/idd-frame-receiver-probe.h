#include <tlhelp32.h>
#include <bcrypt.h>
#include "idd-frame-reader.h"
static bool idd_probe_module_hash(const wchar_t *path) {
#ifdef UAVF_EXPECTED_IDD_DRIVER_SHA_BYTES
    static const unsigned char expected[32]={UAVF_EXPECTED_IDD_DRIVER_SHA_BYTES};
#else
    static const unsigned char expected[32]={0xb1,0x5d,0x9f,0x4c,0x84,0x0f,0x3e,0xb8,
        0x56,0x9d,0xaa,0x61,0x81,0x13,0x6b,0xab,0x37,0xbb,0x3f,0x27,0x09,0x33,0x82,0x2e,
        0x83,0xf9,0x92,0x0b,0x4f,0x0a,0xff,0x5a};
#endif
    HANDLE file=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ,nullptr,OPEN_EXISTING,0,nullptr);
    if(file==INVALID_HANDLE_VALUE)return false;
    BCRYPT_ALG_HANDLE algorithm=nullptr;BCRYPT_HASH_HANDLE hash=nullptr;
    unsigned char bytes[4096],digest[32];DWORD got=0;bool good=false;
    if(BCryptOpenAlgorithmProvider(&algorithm,BCRYPT_SHA256_ALGORITHM,nullptr,0)>=0 &&
        BCryptCreateHash(algorithm,&hash,nullptr,0,nullptr,0,0)>=0) {
        good=true;
        for(;;) {
            if(!ReadFile(file,bytes,sizeof(bytes),&got,nullptr)){good=false;break;}
            if(!got)break;
            if(BCryptHashData(hash,bytes,got,0)<0){good=false;break;}
        }
        if(good)good=BCryptFinishHash(hash,digest,32,0)>=0 && !memcmp(digest,expected,32);
    }
    if(hash)BCryptDestroyHash(hash);
    if(algorithm)BCryptCloseAlgorithmProvider(algorithm,0);
    CloseHandle(file);return good;
}
static DWORD idd_probe_admitted_host() {
    DWORD result=0;HANDLE processes=CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0);
    if(processes==INVALID_HANDLE_VALUE)return 0;
    PROCESSENTRY32W process={};process.dwSize=sizeof(process);
    if(Process32FirstW(processes,&process))do {
        if(_wcsicmp(process.szExeFile,L"WUDFHost.exe"))continue;
        HANDLE modules=CreateToolhelp32Snapshot(TH32CS_SNAPMODULE,process.th32ProcessID);
        if(modules==INVALID_HANDLE_VALUE)continue;
        MODULEENTRY32W module={};module.dwSize=sizeof(module);
        if(Module32FirstW(modules,&module))do {
            if(!_wcsicmp(module.szModule,L"IddSampleDriver.dll") && idd_probe_module_hash(module.szExePath)) {
                if(result && result!=process.th32ProcessID){CloseHandle(modules);CloseHandle(processes);return 0;}
                result=process.th32ProcessID;
            }
        }while(Module32NextW(modules,&module));
        CloseHandle(modules);
    }while(Process32NextW(processes,&process));
    CloseHandle(processes);return result;
}
#ifndef UAVF_IDD_AUTH_ONLY
#ifdef UAVF_IDD_RECONNECT_PROBE
#include "idd-frame-reconnect-probe.h"
#else
static int idd_frame_receiver_probe() {
    HANDLE token=nullptr;unsigned char user[256];DWORD bytes=0;
    if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token))return 20;
    bool system=GetTokenInformation(token,TokenUser,user,sizeof(user),&bytes) &&
        IsWellKnownSid(reinterpret_cast<TOKEN_USER *>(user)->User.Sid,WinLocalSystemSid);
    CloseHandle(token);if(!system)return 21;
    UavfIddFrameReader reader;DWORD pid=0;ULONGLONG deadline=GetTickCount64()+50000;
    do {
        pid=idd_probe_admitted_host();
        if(pid && reader.open(L"\\\\.\\pipe\\UAVF-IddSourceProbe-3EF669B7",pid,L"C:\\Windows\\System32\\WUDFHost.exe"))break;
        Sleep(100);
    }while(GetTickCount64()<deadline);
    if(!pid || GetTickCount64()>=deadline){puts("PIPE_ADMISSION_FAILED; NOT_FPS_PASS");return 22;}
    auto *pixels=static_cast<unsigned char *>(HeapAlloc(GetProcessHeap(),0,UAVF_IDD_FRAME_BYTES));
    if(!pixels)return 23;
    LARGE_INTEGER frequency,start,end;QueryPerformanceFrequency(&frequency);QueryPerformanceCounter(&start);
    unsigned frames=0,sample_changes=0;uint64_t previous=0,previous_sample=0,gaps=0;
    int code=0;deadline=GetTickCount64()+30000;
    while(GetTickCount64()<deadline) {
        UavfIddWireHeader header;
        if(!reader.read(&header,pixels,UAVF_IDD_FRAME_BYTES,2000)){code=24;break;}
        if(previous && header.sequence>previous+1)gaps+=header.sequence-previous-1;
        uint64_t sample=1469598103934665603ull;
        for(unsigned i=0;i<UAVF_IDD_FRAME_BYTES;i+=4096)sample=(sample^pixels[i])*1099511628211ull;
        if(frames && sample!=previous_sample)++sample_changes;
        previous_sample=sample;previous=header.sequence;++frames;
    }
    QueryPerformanceCounter(&end);
    printf("PIPE_RECEIVED=%u SEQUENCE_GAPS=%llu SAMPLE_CHANGES=%u SERVER_PID=%lu ELAPSED_TICKS=%llu QPC_FREQ=%llu RESULT=%d; NOT_ANDROID_FPS; NOT_WHOLE_IMAGE_UNIQUENESS\n",
        frames,static_cast<unsigned long long>(gaps),sample_changes,pid,
        static_cast<unsigned long long>(end.QuadPart-start.QuadPart),
        static_cast<unsigned long long>(frequency.QuadPart),code);
    HeapFree(GetProcessHeap(),0,pixels);return code;
}
#endif
#endif
