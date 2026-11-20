/* Real Windows Unicode/pipe APIs; NEVER opens or mutates host clipboard. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define wmain clipboard_worker_entry
#include "windows-clipboard-agent.c"
#undef wmain
#define CHECK(x) do {if(!(x)){fprintf(stderr,"FAIL line=%d\n",__LINE__);return 1;}checks++;}while(0)
typedef struct {HANDLE pipe;const unsigned char *data;unsigned size;} Fragment;
static DWORD WINAPI fragmented_write(void *opaque) {
    Fragment *f=opaque;for(unsigned i=0;i<f->size;i++) {
        DWORD n=0;if(!WriteFile(f->pipe,f->data+i,1,&n,NULL)||n!=1)return 1;
    }
    CloseHandle(f->pipe);return 0;
}
int wmain(int argc,wchar_t **argv) {
    unsigned checks=0;CHECK(argc==2);
    CHECK(clipboard_context_allowed(1,1,L"Default",L"Default"));
    CHECK(!clipboard_context_allowed(0,0,L"Default",L"Default"));
    CHECK(!clipboard_context_allowed(1,2,L"Default",L"Default"));
    CHECK(!clipboard_context_allowed(1,0xffffffff,L"Default",L"Default"));
    CHECK(!clipboard_context_allowed(1,1,L"Winlogon",L"Default"));
    CHECK(!clipboard_context_allowed(1,1,L"Default",L"Winlogon"));
    CHECK(!clipboard_context_allowed(1,1,NULL,L"Default"));
    wchar_t *no_arguments[]={L"worker"};CHECK(clipboard_worker_entry(1,no_arguments)==2);
    ClipboardInbox pending={0};InitializeCriticalSection(&pending.lock);
    CHECK(!clipboard_defer_for_pending_text(&pending,true)&&!pending.request);
    wchar_t fixture[]=L"synthetic queued write";pending.latest=fixture;
    CHECK(clipboard_defer_for_pending_text(&pending,false)&&!pending.request);
    CHECK(clipboard_defer_for_pending_text(&pending,true)&&pending.request);
    pending.latest=NULL;
    CHECK(!clipboard_defer_for_pending_text(&pending,false)&&pending.request);
    pending.request=false;
    CHECK(!clipboard_defer_for_pending_text(&pending,true)&&!pending.request);
    DeleteCriticalSection(&pending.lock);
    static const unsigned char text[]="Android \xe2\x86\x94 Windows\n\xd0\x9f\xd1\x80\xd0\xb8\xd0\xb2\xd0\xb5\xd1\x82\n\xf0\x9f\x98\x84\n";
    unsigned chars=0,size=0;wchar_t *wide=ucip_to_wide(text,sizeof(text)-1,&chars);CHECK(wide&&chars);
    unsigned char *back=ucip_from_wide(wide,chars,&size);CHECK(back&&size==sizeof(text)-1&&!memcmp(back,text,size));
    free(wide);free(back);
    const unsigned char malformed[][4]={{0xff},{0xc0,0x80},{0xed,0xa0,0x80},{0xf4,0x90,0x80,0x80}};
    unsigned lengths[]={1,2,3,4};
    for(unsigned i=0;i<4;i++)CHECK(!ucip_to_wide(malformed[i],lengths[i],&chars));
    CHECK(!ucip_to_wide((unsigned char*)"a\0b",3,&chars));
    CHECK(!ucip_to_wide(text,UCIP_MAX+1,&chars));
    const wchar_t bad_wide[]={0xd800};CHECK(!ucip_from_wide(bad_wide,1,&size));
    const wchar_t embedded[]={L'a',0,L'b'};CHECK(!ucip_from_wide(embedded,3,&size));
    wide=ucip_to_wide(NULL,0,&chars);CHECK(wide&&chars==0&&wide[0]==0);
    back=ucip_from_wide(wide,0,&size);CHECK(back&&size==0);free(wide);free(back);
    unsigned char *limit=malloc(UCIP_MAX);CHECK(limit);memset(limit,'x',UCIP_MAX);
    wide=ucip_to_wide(limit,UCIP_MAX,&chars);CHECK(wide&&chars==UCIP_MAX);
    back=ucip_from_wide(wide,chars,&size);CHECK(back&&size==UCIP_MAX&&!memcmp(back,limit,size));
    free(wide);free(back);free(limit);
    UcipHeader header={{'U','C','I','P'},1,1,0,sizeof(text)-1};CHECK(ucip_header_valid(&header));
    UcipHeader bad=header;bad.magic[0]='X';CHECK(!ucip_header_valid(&bad));
    bad=header;bad.version=2;CHECK(!ucip_header_valid(&bad));
    bad=header;bad.reserved=1;CHECK(!ucip_header_valid(&bad));
    bad=header;bad.kind=3;CHECK(!ucip_header_valid(&bad));
    bad=header;bad.length=0xffffffff;CHECK(!ucip_header_valid(&bad));
    bad=header;bad.kind=2;CHECK(!ucip_header_valid(&bad));bad.length=0;CHECK(ucip_header_valid(&bad));
    HANDLE vector=CreateFileW(argv[1],GENERIC_READ|GENERIC_WRITE,0,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    CHECK(vector!=INVALID_HANDLE_VALUE);CHECK(ucip_write_text(vector,text,sizeof(text)-1));
    LARGE_INTEGER zero={0};CHECK(SetFilePointerEx(vector,zero,NULL,FILE_BEGIN));
    UcipHeader received={0};CHECK(ucip_read_exact(vector,&received,sizeof(received)));
    CHECK(!memcmp(&received,&header,sizeof(header)));unsigned char buffer[sizeof(text)];
    CHECK(ucip_read_exact(vector,buffer,received.length)&&!memcmp(buffer,text,received.length));CloseHandle(vector);
    unsigned char packet[12+sizeof(text)-1];memcpy(packet,&header,12);memcpy(packet+12,text,sizeof(text)-1);
    HANDLE reader,writer;CHECK(CreatePipe(&reader,&writer,NULL,0));Fragment f={writer,packet,sizeof(packet)};
    HANDLE thread=CreateThread(NULL,0,fragmented_write,&f,0,NULL);CHECK(thread);
    CHECK(ucip_read_exact(reader,&received,12)&&!memcmp(&received,&header,12));
    CHECK(ucip_read_exact(reader,buffer,received.length)&&!memcmp(buffer,text,received.length));
    CHECK(!ucip_read_exact(reader,buffer,1));CHECK(WaitForSingleObject(thread,2000)==WAIT_OBJECT_0);
    CloseHandle(thread);CloseHandle(reader);
    CHECK(CreatePipe(&reader,&writer,NULL,0));DWORD n=0;CHECK(WriteFile(writer,packet,11,&n,NULL)&&n==11);CloseHandle(writer);
    CHECK(!ucip_read_exact(reader,&received,12));CloseHandle(reader);
    printf("ACTUAL_WINDOWS_UCIP_UNICODE_HEADER_BOUND_FRAGMENT_AND_EOF_CHECKS=%u PASS\nHOST_CLIPBOARD_ACCESSES=0\nNOT_GUEST_CLIPBOARD_RUNTIME_PASS=1\n",checks);
    return 0;
}
