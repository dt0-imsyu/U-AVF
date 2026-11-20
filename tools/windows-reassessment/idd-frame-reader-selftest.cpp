#include <windows.h>
#include <sddl.h>
#include <stdio.h>
#include "idd-frame-reader.h"
#include "idd-frame-pipe.h"
int main() {
    wchar_t name[128],image[1024];
    swprintf(name,128,L"\\\\.\\pipe\\UAVF-Idd-reader-test-%lu",GetCurrentProcessId());
    if(!GetModuleFileNameW(nullptr,image,1024))return 1;
    PSECURITY_DESCRIPTOR descriptor=nullptr;
    if(!ConvertStringSecurityDescriptorToSecurityDescriptorW(
        L"D:P(A;;GA;;;OW)(A;;GA;;;SY)",SDDL_REVISION_1,&descriptor,nullptr))return 2;
    SECURITY_ATTRIBUTES security={sizeof(security),descriptor,FALSE};
    UavfIddFramePipe writer;UavfIddFrameReader reader;
    if(!writer.open(name,&security)||!reader.open(name,GetCurrentProcessId(),image))return 3;
    auto *source=static_cast<unsigned char *>(_aligned_malloc(UAVF_IDD_FRAME_BYTES,16));
    auto *pixels=static_cast<unsigned char *>(malloc(UAVF_IDD_FRAME_BYTES));
    if(!source||!pixels)return 4;
    memset(source,0x51,UAVF_IDD_FRAME_BYTES);
    UavfIddFrame frame={source,1920,1200,7680,1,100};UavfIddWireHeader header;
    if(!writer.publish(frame,13000000)||!reader.read(&header,pixels,UAVF_IDD_FRAME_BYTES,5000))return 5;
    if(header.sequence!=1||memcmp(source,pixels,UAVF_IDD_FRAME_BYTES)||writer.completed()!=1)return 6;
    ULONGLONG start=GetTickCount64();
    if(reader.read(&header,pixels,UAVF_IDD_FRAME_BYTES,50)||GetLastError()!=ERROR_TIMEOUT)return 7;
    if(GetTickCount64()-start>1500)return 8;
    if(!reader.connected())return 13;
    memset(source,0x63,UAVF_IDD_FRAME_BYTES);
    if(!writer.publish(frame,13000000)||!reader.read(&header,pixels,UAVF_IDD_FRAME_BYTES,5000) ||
        header.sequence!=2 || memcmp(source,pixels,UAVF_IDD_FRAME_BYTES))return 14;
    reader.close();
    writer.close();
    if(!writer.open(name,&security))return 9;
    if(reader.open(name,GetCurrentProcessId()+1,image)||GetLastError()!=ERROR_ACCESS_DENIED)return 10;
    writer.close();
    if(!writer.open(name,&security))return 11;
    if(reader.open(name,GetCurrentProcessId(),L"C:\\WrongImage.exe")||GetLastError()!=ERROR_ACCESS_DENIED)return 12;
    writer.close();LocalFree(descriptor);_aligned_free(source);free(pixels);
    puts("PASS: full frame, PID/image provenance, bounded timeout/cancellation, wrong-server rejection");return 0;
}
