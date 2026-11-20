/* Guest-specific clipboard source, shared UCIP stdin/stdout worker.
 * Launch ONLY on authenticated console Default desktop, never Session0 or
 * Winlogon. No driver, injection, clipboard history or security override.
 * Stdio must be connected by an optional same-VM vsock4054 broker (not yet
 * admitted). Never connect this stream to the display UVF1 decoder.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "windows-clipboard-wire.h"
typedef struct {
    HANDLE input,event;CRITICAL_SECTION lock;volatile LONG stopped;
    wchar_t *latest;unsigned chars;bool request;
} ClipboardInbox;
static bool clipboard_context_allowed(DWORD session,DWORD console,const wchar_t *own,const wchar_t *active) {
    return session&&console!=0xffffffff&&session==console&&own&&active&&
        !wcscmp(own,L"Default")&&!wcscmp(active,L"Default");
}
static bool clipboard_desktop_allowed(void) {
    DWORD session=0;if(!ProcessIdToSessionId(GetCurrentProcessId(),&session)||!session||
        session!=WTSGetActiveConsoleSessionId())return false;
    wchar_t own[128]={0},active[128]={0};DWORD used=0;
    if(!GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,own,sizeof(own),&used)||
       wcscmp(own,L"Default"))return false;
    HDESK desktop=OpenInputDesktop(0,FALSE,DESKTOP_READOBJECTS);
    if(!desktop)return false;
    bool allowed=GetUserObjectInformationW(desktop,UOI_NAME,active,sizeof(active),&used)&&
        clipboard_context_allowed(session,WTSGetActiveConsoleSessionId(),own,active);
    CloseDesktop(desktop);return allowed;
}
static DWORD WINAPI clipboard_reader(void *opaque) {
    ClipboardInbox *inbox=opaque;
    while(!InterlockedCompareExchange(&inbox->stopped,0,0)) {
        UcipHeader header;
        if(!ucip_read_exact(inbox->input,&header,sizeof(header))||!ucip_header_valid(&header))break;
        unsigned char *text=malloc(header.length?header.length:1);if(!text)break;
        if(!ucip_read_exact(inbox->input,text,header.length)){free(text);break;}
        unsigned chars=0;wchar_t *wide=header.kind==1?ucip_to_wide(text,header.length,&chars):NULL;
        free(text);if(header.kind==1&&!wide)break;
        EnterCriticalSection(&inbox->lock);
        if(header.kind==1){free(inbox->latest);inbox->latest=wide;inbox->chars=chars;}
        else inbox->request=true;
        LeaveCriticalSection(&inbox->lock);SetEvent(inbox->event);
    }
    InterlockedExchange(&inbox->stopped,1);SetEvent(inbox->event);return 0;
}
static bool clipboard_defer_for_pending_text(ClipboardInbox *inbox,bool query) {
    EnterCriticalSection(&inbox->lock);
    bool pending=inbox->latest!=NULL;
    // A REQUEST following TEXT must not be satisfied with the previous value
    // if OpenClipboard contention deferred that TEXT. Preserve the request
    // until the newest queued write commits; do not read/log stale contents.
    if(pending&&query)inbox->request=true;
    LeaveCriticalSection(&inbox->lock);return pending;
}
static bool clipboard_set(HWND owner,const wchar_t *text,unsigned chars) {
    if(!clipboard_desktop_allowed())return false;
    HGLOBAL block=GlobalAlloc(GMEM_MOVEABLE,((size_t)chars+1)*sizeof(wchar_t));if(!block)return false;
    void *memory=GlobalLock(block);if(!memory){GlobalFree(block);return false;}
    memcpy(memory,text,((size_t)chars+1)*sizeof(wchar_t));GlobalUnlock(block);
    bool ok=false;
    if(OpenClipboard(owner)) {
        // A real owner HWND is mandatory: EmptyClipboard(NULL owner) would
        // prevent SetClipboardData from transferring this memory correctly.
        if(clipboard_desktop_allowed()&&EmptyClipboard()&&SetClipboardData(CF_UNICODETEXT,block))ok=true;
        CloseClipboard();
    }
    if(!ok)GlobalFree(block);return ok;
}
static unsigned char *clipboard_get(HWND owner,unsigned *size) {
    unsigned char *result=NULL;
    if(!clipboard_desktop_allowed()||!OpenClipboard(owner))return NULL;
    HGLOBAL block=GetClipboardData(CF_UNICODETEXT);
    SIZE_T bytes=block?GlobalSize(block):0;
    if(bytes>=sizeof(wchar_t)&&bytes<=(UCIP_MAX+1)*sizeof(wchar_t)&&!(bytes%sizeof(wchar_t))) {
        const wchar_t *text=GlobalLock(block);
        if(text) {
            unsigned chars=0,bound=(unsigned)(bytes/sizeof(wchar_t));
            while(chars<bound&&text[chars])chars++;
            if(chars<bound&&clipboard_desktop_allowed())result=ucip_from_wide(text,chars,size);
            GlobalUnlock(block);
        }
    }
    CloseClipboard();return result;
}
int wmain(int argc,wchar_t **argv) {
    if(argc!=2||wcscmp(argv[1],L"--stdio"))return 2;
    if(!clipboard_desktop_allowed())return 3;
    ClipboardInbox inbox={0};inbox.input=GetStdHandle(STD_INPUT_HANDLE);
    HANDLE output=GetStdHandle(STD_OUTPUT_HANDLE);
    if(inbox.input==INVALID_HANDLE_VALUE||output==INVALID_HANDLE_VALUE)return 4;
    HWND owner=CreateWindowExW(0,L"STATIC",L"U-AVF clipboard",0,0,0,0,0,HWND_MESSAGE,NULL,GetModuleHandleW(NULL),NULL);
    if(!owner)return 5;
    InitializeCriticalSection(&inbox.lock);inbox.event=CreateEventW(NULL,FALSE,FALSE,NULL);
    if(!inbox.event){DestroyWindow(owner);DeleteCriticalSection(&inbox.lock);return 6;}
    HANDLE reader=CreateThread(NULL,0,clipboard_reader,&inbox,0,NULL);
    if(!reader){CloseHandle(inbox.event);DestroyWindow(owner);DeleteCriticalSection(&inbox.lock);return 7;}
    DWORD previous=GetClipboardSequenceNumber();int result=0;
    while(!InterlockedCompareExchange(&inbox.stopped,0,0)) {
        if(!clipboard_desktop_allowed()){result=8;break;}
        MSG message;while(PeekMessageW(&message,NULL,0,0,PM_REMOVE)){TranslateMessage(&message);DispatchMessageW(&message);}
        EnterCriticalSection(&inbox.lock);
        wchar_t *text=inbox.latest;unsigned chars=inbox.chars;bool query=inbox.request;
        inbox.latest=NULL;inbox.request=false;LeaveCriticalSection(&inbox.lock);
        if(text) {
            if(clipboard_set(owner,text,chars)){previous=GetClipboardSequenceNumber();free(text);}
            else {
                // Temporary clipboard contention: retain only newest value.
                EnterCriticalSection(&inbox.lock);
                if(!inbox.latest){inbox.latest=text;inbox.chars=chars;text=NULL;}
                LeaveCriticalSection(&inbox.lock);free(text);
            }
        }
        DWORD sequence=GetClipboardSequenceNumber();
        if(!clipboard_defer_for_pending_text(&inbox,query)&&(query||sequence!=previous)) {
            unsigned size=0;unsigned char *local=clipboard_get(owner,&size);
            if(local) {
                if(!ucip_write_text(output,local,size)){free(local);result=9;break;}
                free(local);previous=sequence;
            }else if(query) {
                EnterCriticalSection(&inbox.lock);inbox.request=true;LeaveCriticalSection(&inbox.lock);
            }
        }
        WaitForSingleObject(inbox.event,200);
    }
    InterlockedExchange(&inbox.stopped,1);CancelSynchronousIo(reader);
    // Never free inbox while the reader can still touch it. Returning exits
    // only this owned process if pipe cancellation is unsupported.
    if(WaitForSingleObject(reader,2000)!=WAIT_OBJECT_0)return 10;
    CloseHandle(reader);CloseHandle(inbox.event);free(inbox.latest);
    DeleteCriticalSection(&inbox.lock);DestroyWindow(owner);return result;
}
