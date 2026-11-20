#pragma once
#include <windows.h>
#include <string.h>
#include "idd-frame-wire.h"

/* Separate encoder process only. A trusted launcher supplies the admitted
 * WUDFHost PID and exact image path; fixed endpoint name alone is not trust. */
class UavfIddFrameReader {
    HANDLE pipe_=INVALID_HANDLE_VALUE,event_=nullptr;
    uint64_t last_sequence_=0,frequency_=0;
    bool exact(void *buffer,DWORD bytes,ULONGLONG deadline,bool *idle=nullptr) {
        auto *cursor=static_cast<unsigned char *>(buffer);
        DWORD consumed=0;if(idle)*idle=false;
        while(bytes) {
            OVERLAPPED operation={};operation.hEvent=event_;ResetEvent(event_);
            DWORD got=0;
            if(!ReadFile(pipe_,cursor,bytes,&got,&operation)) {
                if(GetLastError()!=ERROR_IO_PENDING)return false;
                ULONGLONG now=GetTickCount64();
                DWORD remaining=now<deadline?static_cast<DWORD>(deadline-now):0;
                if(WaitForSingleObject(event_,remaining)!=WAIT_OBJECT_0) {
                    CancelIoEx(pipe_,&operation);
                    // Never destroy a live OVERLAPPED or its referenced storage.
                    if(WaitForSingleObject(event_,1000)!=WAIT_OBJECT_0)ExitProcess(90);
                    BOOL completed=GetOverlappedResult(pipe_,&operation,&got,FALSE);
                    DWORD error=completed?ERROR_SUCCESS:GetLastError();
                    if(!completed) {
                        if(idle && !consumed && !got && error==ERROR_OPERATION_ABORTED)*idle=true;
                        SetLastError(ERROR_TIMEOUT);return false;
                    }
                    // Completion raced cancellation: consume actual bytes, never discard a header.
                } else {
                    if(!GetOverlappedResult(pipe_,&operation,&got,FALSE))return false;
                }
            }
            if(!got || got>bytes){SetLastError(ERROR_INVALID_DATA);return false;}
            cursor+=got;bytes-=got;consumed+=got;
        }
        return true;
    }
public:
    UavfIddFrameReader()=default;
    UavfIddFrameReader(const UavfIddFrameReader &)=delete;
    UavfIddFrameReader &operator=(const UavfIddFrameReader &)=delete;
    ~UavfIddFrameReader(){close();}
    bool open(const wchar_t *name,DWORD admitted_pid,const wchar_t *image) {
        close();if(!admitted_pid || !image || !*image)return false;
        pipe_=CreateFileW(name,GENERIC_READ,0,nullptr,OPEN_EXISTING,
            FILE_FLAG_OVERLAPPED|SECURITY_SQOS_PRESENT|SECURITY_IDENTIFICATION,nullptr);
        if(pipe_==INVALID_HANDLE_VALUE)return false;
        ULONG pid=0;bool accepted=false;
        if(GetNamedPipeServerProcessId(pipe_,&pid) && pid==admitted_pid) {
            HANDLE process=OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,pid);
            if(process) {
                wchar_t path[1024];DWORD count=1024;
                accepted=QueryFullProcessImageNameW(process,0,path,&count) && !_wcsicmp(path,image);
                CloseHandle(process);
            }
        }
        if(!accepted){close();SetLastError(ERROR_ACCESS_DENIED);return false;}
        event_=CreateEventW(nullptr,TRUE,FALSE,nullptr);
        if(!event_){close();return false;}
        return true;
    }
    bool read(UavfIddWireHeader *header,unsigned char *pixels,DWORD capacity,DWORD timeout_ms) {
        if(pipe_==INVALID_HANDLE_VALUE || !event_ || !header || !pixels ||
            capacity!=UAVF_IDD_FRAME_BYTES || !timeout_ms || timeout_ms>5000)return false;
        ULONGLONG deadline=GetTickCount64()+timeout_ms;
        bool idle=false;
        if(!exact(header,64,deadline,&idle)) {
            if(idle){SetLastError(ERROR_TIMEOUT);return false;} // Clean packet boundary: retry safely.
            DWORD error=GetLastError();close();SetLastError(error?error:ERROR_INVALID_DATA);return false;
        }
        if(!uavf_idd_wire_valid(*header) || header->sequence<=last_sequence_ ||
            (frequency_ && frequency_!=header->qpc_frequency)) {
            close();SetLastError(ERROR_INVALID_DATA);return false;
        }
        if(!exact(pixels,header->pixel_bytes,deadline)) {
            DWORD error=GetLastError();close();SetLastError(error?error:ERROR_INVALID_DATA);return false;
        }
        last_sequence_=header->sequence;frequency_=header->qpc_frequency;return true;
    }
    bool connected()const{return pipe_!=INVALID_HANDLE_VALUE;}
    void close() {
        if(pipe_!=INVALID_HANDLE_VALUE)CloseHandle(pipe_);
        if(event_)CloseHandle(event_);
        pipe_=INVALID_HANDLE_VALUE;event_=nullptr;last_sequence_=frequency_=0;
    }
};
