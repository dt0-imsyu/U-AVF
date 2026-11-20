#include <windows.h>
#include <sddl.h>
#include <stdio.h>
#include "idd-frame-pipe.h"
struct TestReader {HANDLE pipe,go,done;unsigned char *packet;DWORD error;};
static DWORD WINAPI read_packet(void *context) {
    auto *r=static_cast<TestReader *>(context);
    if(WaitForSingleObject(r->go,5000)!=WAIT_OBJECT_0){r->error=1;return 1;}
    DWORD position=0,bytes=0,total=64+UAVF_IDD_FRAME_BYTES;
    while(position<total) {
        DWORD request=total-position;if(request>4093)request=4093; // Split header/pixels arbitrarily.
        if(!ReadFile(r->pipe,r->packet+position,request,&bytes,nullptr)||!bytes){r->error=2;break;}
        position+=bytes;
    }
    SetEvent(r->done);return r->error;
}
static bool check_packet(const unsigned char *packet,uint64_t sequence,unsigned char pixel) {
    UavfIddWireHeader h;memcpy(&h,packet,64);
    if(!uavf_idd_wire_valid(h)||h.sequence!=sequence)return false;
    for(unsigned i=0;i<UAVF_IDD_FRAME_BYTES;++i)if(packet[64+i]!=pixel)return false;
    return true;
}
int main() {
    wchar_t name[128];swprintf(name,128,L"\\\\.\\pipe\\UAVF-Idd-host-test-%lu",GetCurrentProcessId());
    PSECURITY_DESCRIPTOR descriptor=nullptr;
    if(!ConvertStringSecurityDescriptorToSecurityDescriptorW(
        L"D:P(A;;GA;;;OW)(A;;GA;;;SY)",SDDL_REVISION_1,&descriptor,nullptr))return 1;
    SECURITY_ATTRIBUTES security={sizeof(security),descriptor,FALSE};
    UavfIddFramePipe source;
    if(!source.open(name,&security))return 2;
    LocalFree(descriptor);
    auto *pixels=static_cast<unsigned char *>(_aligned_malloc(UAVF_IDD_FRAME_BYTES,16));
    auto *packet=static_cast<unsigned char *>(malloc(64+UAVF_IDD_FRAME_BYTES));
    if(!pixels||!packet)return 3;
    memset(pixels,0x31,UAVF_IDD_FRAME_BYTES);
    UavfIddFrame frame={pixels,1920,1200,7680,1,100};
    if(source.publish(frame,13000000))return 4; // No consumer: nonblocking drop.
    HANDLE client=CreateFileW(name,GENERIC_READ,0,nullptr,OPEN_EXISTING,0,nullptr);
    if(client==INVALID_HANDLE_VALUE)return 5;
    TestReader reader={client,CreateEventW(nullptr,TRUE,FALSE,nullptr),
        CreateEventW(nullptr,TRUE,FALSE,nullptr),packet,0};
    HANDLE thread=CreateThread(nullptr,0,read_packet,&reader,0,nullptr);
    if(!thread||!source.publish(frame,13000000))return 6;
    if(source.publish(frame,13000000))return 7; // Outstanding9MB write is bounded.
    memset(pixels,0x99,UAVF_IDD_FRAME_BYTES); // Borrowed input can be reused immediately.
    SetEvent(reader.go);
    if(WaitForSingleObject(reader.done,5000)!=WAIT_OBJECT_0)ExitProcess(8);
    WaitForSingleObject(thread,5000);CloseHandle(thread);
    if(reader.error||!check_packet(packet,2,0x31)||source.completed()!=1||source.dropped()!=2)return 9;
    ResetEvent(reader.done);ResetEvent(reader.go);
    thread=CreateThread(nullptr,0,read_packet,&reader,0,nullptr);
    if(!source.publish(frame,13000000))return 10;
    SetEvent(reader.go);
    if(WaitForSingleObject(reader.done,5000)!=WAIT_OBJECT_0)ExitProcess(11);
    WaitForSingleObject(thread,5000);CloseHandle(thread);
    if(reader.error||!check_packet(packet,4,0x99)||source.completed()!=2)return 12;
    // Close with an outstanding transfer and no reader: no borrowed memory lifetime leak.
    if(!source.publish(frame,13000000))return 13;
    source.close();CloseHandle(client);CloseHandle(reader.go);CloseHandle(reader.done);
    UavfIddWireHeader invalid={};if(uavf_idd_wire_valid(invalid))return 14;
    _aligned_free(pixels);free(packet);
    puts("PASS: actual named pipe, fragmented reads, full pixels, bounded backpressure, owned copy, cancellation");
    return 0;
}
