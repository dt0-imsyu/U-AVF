/* Read only our own numeric 80-byte events from an offline ETL. No device
 * creation, driver changes or image data. Capture is NOT Android present. */
#include <windows.h>
#include <evntrace.h>
#include <evntcons.h>
#include <stdio.h>
#include <string.h>
#ifdef UAVF_IDD_PIPE_READER
#include "idd-frame-receiver-probe.h"
#endif
static const GUID provider={0x43ea47ba,0x292b,0x4e83,{0xa8,0xca,0xc0,0xd4,0x68,0xea,0x31,0xae}};
static ULONG matches;
static void WINAPI record(EVENT_RECORD *r) {
    if(memcmp(&r->EventHeader.ProviderId,&provider,sizeof(GUID)) ||
       r->EventHeader.EventDescriptor.Version!=1)return;
    bool frames=r->EventHeader.EventDescriptor.Id==1 && r->UserDataLength==80;
    bool timing=r->EventHeader.EventDescriptor.Id==2 && r->UserDataLength==128;
    if(!frames&&!timing)return;
    const unsigned char *p=static_cast<const unsigned char *>(r->UserData);
    printf(frames?"IDD_PAYLOAD_HEX=":"IDD_TIMING_HEX=");
    for(unsigned i=0;i<r->UserDataLength;i++)printf("%02x",p[i]);
    puts("");++matches;
}
int wmain(int argc,wchar_t **argv) {
#ifdef UAVF_IDD_PIPE_READER
    if(argc==2 && !wcscmp(argv[1],L"--pipe-probe"))return idd_frame_receiver_probe();
#endif
    if(argc!=2){puts("Usage: idd-etw-read.exe own-trace.etl");return 2;}
    EVENT_TRACE_LOGFILEW log={};log.LogFileName=argv[1];
    log.ProcessTraceMode=PROCESS_TRACE_MODE_EVENT_RECORD;
    log.EventRecordCallback=record;
    TRACEHANDLE trace=OpenTraceW(&log);
    if(trace==INVALID_PROCESSTRACE_HANDLE){printf("OPEN_TRACE_ERROR=%lu\n",GetLastError());return 3;}
    ULONG error=ProcessTrace(&trace,1,nullptr,nullptr);CloseTrace(trace);
    printf("TRACE_STATUS=%lu OWN_RECORDS=%lu; NOT_ANDROID_PRESENT_PASS\n",error,matches);
    return error?4:matches?0:5;
}
