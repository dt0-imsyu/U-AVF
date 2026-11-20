#pragma once
#include <windows.h>
#include <string.h>
#include "idd-frame-wire.h"
#include "idd-system-frame-policy.h"

/* One owned packet, one asynchronous write: never wait on the compositor
 * worker, never retain IddCx's borrowed pointer. Busy consumer => drop.
 * Caller supplies a restrictive DACL. No remote clients, inherited handles,
 * physical mapping or global shared-memory objects. */
class UavfIddFramePipe {
    HANDLE pipe_=INVALID_HANDLE_VALUE;
    OVERLAPPED operation_={};
    unsigned char *packet_=nullptr;
    enum Phase {Closed,Connecting,Ready,Writing} phase_=Closed;
    uint64_t sequence_=0, dropped_=0, completed_=0;
    bool connect() {
        HANDLE event=operation_.hEvent;operation_={};operation_.hEvent=event;
        ResetEvent(event);
        if(ConnectNamedPipe(pipe_,&operation_))phase_=Ready;
        else if(GetLastError()==ERROR_PIPE_CONNECTED)phase_=Ready;
        else if(GetLastError()==ERROR_IO_PENDING)phase_=Connecting;
        else {close();return false;}
        return true;
    }
    bool peer_lost(DWORD error) {
        if(error!=ERROR_BROKEN_PIPE && error!=ERROR_PIPE_NOT_CONNECTED && error!=ERROR_NO_DATA) {
            close();return false; // Unknown protocol/security error still fails closed.
        }
        // Called only after a terminal overlapped result or synchronous error.
        // No live write may reference packet_ when the connection is recycled.
        if(!DisconnectNamedPipe(pipe_) && GetLastError()!=ERROR_PIPE_NOT_CONNECTED) {
            close();return false;
        }
        return connect(); // Same first-instance handle/DACL/server PID, not a new owner.
    }
    bool settle() {
        if(phase_!=Connecting && phase_!=Writing)return phase_==Ready;
        DWORD bytes=0;
        if(!GetOverlappedResult(pipe_,&operation_,&bytes,FALSE)) {
            DWORD error=GetLastError();
            if(error==ERROR_IO_INCOMPLETE)return false;
            if(phase_==Writing)++dropped_;
            if(!peer_lost(error))return false;
            return phase_==Ready;
        }
        if(phase_==Writing) {
            if(bytes!=sizeof(UavfIddWireHeader)+UAVF_IDD_FRAME_BYTES){close();return false;}
            ++completed_;
        }
        phase_=Ready;ResetEvent(operation_.hEvent);return true;
    }
public:
    UavfIddFramePipe()=default;
    UavfIddFramePipe(const UavfIddFramePipe &)=delete;
    UavfIddFramePipe &operator=(const UavfIddFramePipe &)=delete;
    ~UavfIddFramePipe(){close();}
    bool open(const wchar_t *name,SECURITY_ATTRIBUTES *security) {
        if(!security || !security->lpSecurityDescriptor || security->bInheritHandle)return false;
        close();
        packet_=static_cast<unsigned char *>(HeapAlloc(GetProcessHeap(),0,
            sizeof(UavfIddWireHeader)+UAVF_IDD_FRAME_BYTES));
        operation_.hEvent=CreateEventW(nullptr,TRUE,FALSE,nullptr);
        if(!packet_ || !operation_.hEvent){close();return false;}
        pipe_=CreateNamedPipeW(name,PIPE_ACCESS_OUTBOUND|FILE_FLAG_OVERLAPPED|
            FILE_FLAG_FIRST_PIPE_INSTANCE,PIPE_TYPE_BYTE|PIPE_READMODE_BYTE|
            PIPE_WAIT|PIPE_REJECT_REMOTE_CLIENTS,1,65536,0,0,security);
        if(pipe_==INVALID_HANDLE_VALUE){close();return false;}
        return connect();
    }
    bool publish(const UavfIddFrame &frame,uint64_t frequency) {
        ++sequence_;
        if(!frequency || !uavf_idd_valid(frame,87) || !settle()) {++dropped_;return false;}
        UavfIddWireHeader header={UAVF_IDD_WIRE_MAGIC,1,64,87,1920,1200,7680,
            UAVF_IDD_FRAME_BYTES,sequence_,frame.display_qpc,frequency,dropped_};
        memcpy(packet_,&header,sizeof(header));
        for(unsigned y=0;y<1200;++y)
            memcpy(packet_+sizeof(header)+y*7680,frame.pixels+(size_t)y*frame.pitch,7680);
        DWORD bytes=0;ResetEvent(operation_.hEvent);
        if(WriteFile(pipe_,packet_,sizeof(header)+UAVF_IDD_FRAME_BYTES,&bytes,&operation_)) {
            if(bytes!=sizeof(header)+UAVF_IDD_FRAME_BYTES){close();return false;}
            ++completed_;return true;
        }
        DWORD error=GetLastError();
        if(error==ERROR_IO_PENDING){phase_=Writing;return true;}
        ++dropped_;peer_lost(error);return false;
    }
    uint64_t dropped()const{return dropped_;}
    uint64_t completed(){settle();return completed_;}
    void close() {
        if(pipe_!=INVALID_HANDLE_VALUE) {
            /* Cancellation completion precedes freeing any referenced packet. */
            CancelIoEx(pipe_,&operation_);
            if(phase_==Connecting || phase_==Writing) {
                DWORD bytes=0;GetOverlappedResult(pipe_,&operation_,&bytes,TRUE);
            }
            CloseHandle(pipe_);pipe_=INVALID_HANDLE_VALUE;
        }
        if(operation_.hEvent)CloseHandle(operation_.hEvent);
        operation_={};
        if(packet_)HeapFree(GetProcessHeap(),0,packet_);
        packet_=nullptr;phase_=Closed;
    }
};
