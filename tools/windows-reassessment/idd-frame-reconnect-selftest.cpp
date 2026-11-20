#include <windows.h>
#include <sddl.h>
#include <stdio.h>
#include "idd-frame-reader.h"
#include "idd-frame-pipe.h"
int main() {
    wchar_t name[128],image[1024];
    swprintf(name,128,L"\\\\.\\pipe\\UAVF-Idd-reconnect-test-%lu",GetCurrentProcessId());
    if(!GetModuleFileNameW(nullptr,image,1024))return 1;
    PSECURITY_DESCRIPTOR descriptor=nullptr;
    if(!ConvertStringSecurityDescriptorToSecurityDescriptorW(L"D:P(A;;GA;;;OW)(A;;GA;;;SY)",SDDL_REVISION_1,&descriptor,nullptr))return 2;
    SECURITY_ATTRIBUTES security={sizeof(security),descriptor,FALSE};
    UavfIddFramePipe writer;UavfIddFrameReader reader;
    if(!writer.open(name,&security))return 3;
    auto *source=static_cast<unsigned char *>(malloc(UAVF_IDD_FRAME_BYTES));
    auto *pixels=static_cast<unsigned char *>(malloc(UAVF_IDD_FRAME_BYTES));
    if(!source||!pixels)return 4;
    uint64_t last_sequence=0;
    for(unsigned cycle=0;cycle<4;cycle++) {
        bool connected=false;ULONGLONG deadline=GetTickCount64()+2000;
        while(GetTickCount64()<deadline&&!connected) {
            connected=reader.open(name,GetCurrentProcessId(),image);
            if(!connected)Sleep(10);
        }
        if(!connected){puts("RECONNECT_FAIL: same producer lost its endpoint after client disconnect");return 5;}
        memset(source,0x20+cycle,UAVF_IDD_FRAME_BYTES);
        UavfIddFrame frame={source,1920,1200,7680,cycle+1,100+cycle};UavfIddWireHeader header;
        if(!writer.publish(frame,13000000)||!reader.read(&header,pixels,UAVF_IDD_FRAME_BYTES,5000))return 6;
        if(header.sequence<=last_sequence||memcmp(source,pixels,UAVF_IDD_FRAME_BYTES))return 7;
        last_sequence=header.sequence;writer.completed();
        if(cycle==1) {
            // Disconnect with a 9MB write pending. Completion must precede reuse.
            if(!writer.publish(frame,13000000))return 8;
        }
        reader.close();
        if(cycle<3) {
            // The failed write detects peer loss; rearm, never destroy the endpoint.
            deadline=GetTickCount64()+2000;
            while(GetTickCount64()<deadline) {
                writer.publish(frame,13000000);Sleep(10);
                if(WaitNamedPipeW(name,10))break;
            }
        }
    }
    writer.close();LocalFree(descriptor);free(source);free(pixels);
    puts("PASS: same producer/server identity,4 reconnects,full pixels,new-frame sequence,pending-write cancellation; NOT_GUEST_PASS");
    return 0;
}
