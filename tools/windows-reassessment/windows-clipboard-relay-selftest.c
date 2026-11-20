/* Actual localhost socket/stdio child relay; synthetic UCIP only.
 * NEVER calls OpenClipboard/GetClipboardData/SetClipboardData. */
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "windows-clipboard-wire.h"
#include "windows-clipboard-relay.h"
static bool test_allowed(void){return true;}
static bool test_disallowed(void){return false;}
typedef struct {SOCKET listener;wchar_t self[MAX_PATH];bool deny;int result;ClipboardRelayOutcome outcome;} RelayTest;
static DWORD WINAPI test_server(void *opaque) {
    RelayTest *state=opaque;SOCKET client=accept(state->listener,NULL,NULL);
    if(client==INVALID_SOCKET){state->result=100;return 1;}
    state->result=clipboard_relay(client,state->self,state->deny?test_disallowed:test_allowed,&state->outcome);
    closesocket(client);return 0;
}
static int fake_worker(void) {
    wchar_t flag[2];if(GetEnvironmentVariableW(L"UAVF_CLIPBOARD_SELFTEST_EXIT",flag,2))return 77;
    HANDLE input=GetStdHandle(STD_INPUT_HANDLE),output=GetStdHandle(STD_OUTPUT_HANDLE);
    for(;;) {
        UcipHeader header;if(!ucip_read_exact(input,&header,12))return 0;
        if(!ucip_header_valid(&header))return 2;
        unsigned char *text=malloc(header.length?header.length:1);if(!text)return 3;
        if(!ucip_read_exact(input,text,header.length)){free(text);return 4;}
        if(header.kind==1) {
            unsigned chars=0,size=0;wchar_t *wide=ucip_to_wide(text,header.length,&chars);
            if(!wide){free(text);return 6;}
            unsigned char *roundtrip=ucip_from_wide(wide,chars,&size);free(wide);
            bool same=roundtrip&&size==header.length&&!memcmp(roundtrip,text,size);
            free(roundtrip);if(!same){free(text);return 7;}
        }
        bool ok=header.kind==1?ucip_write_text(output,text,header.length):
            ucip_write_text(output,(unsigned char*)"RELAY_TEST",10);
        free(text);if(!ok)return 5;
    }
}
static bool socket_exact(SOCKET s,void *data,unsigned size) {
    unsigned char *p=data;while(size){int n=recv(s,(char*)p,(int)size,0);if(n<=0)return false;p+=n;size-=(unsigned)n;}return true;
}
#define CHECK(x) do{if(!(x)){fprintf(stderr,"FAIL line=%d WSA=%d\n",__LINE__,WSAGetLastError());return 1;}checks++;}while(0)
int wmain(int argc,wchar_t **argv) {
    if(argc==2&&!wcscmp(argv[1],L"--clipboard-worker"))return fake_worker();
    unsigned checks=0;WSADATA data;CHECK(!WSAStartup(MAKEWORD(2,2),&data));
    for(unsigned run=0;run<5;run++) {
        CHECK(SetEnvironmentVariableW(L"UAVF_CLIPBOARD_SELFTEST_EXIT",run==4?L"1":NULL));
        RelayTest state={0};state.deny=run==3;state.result=-1;
        CHECK(GetModuleFileNameW(NULL,state.self,MAX_PATH)>0);
        state.listener=socket(AF_INET,SOCK_STREAM,0);CHECK(state.listener!=INVALID_SOCKET);
        struct sockaddr_in address={0};address.sin_family=AF_INET;address.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
        CHECK(!bind(state.listener,(struct sockaddr*)&address,sizeof(address))&&!listen(state.listener,1));
        int address_bytes=sizeof(address);CHECK(!getsockname(state.listener,(struct sockaddr*)&address,&address_bytes));
        HANDLE server=CreateThread(NULL,0,test_server,&state,0,NULL);CHECK(server);
        SOCKET client=socket(AF_INET,SOCK_STREAM,0);CHECK(client!=INVALID_SOCKET);
        DWORD timeout=5000;CHECK(!setsockopt(client,SOL_SOCKET,SO_RCVTIMEO,(char*)&timeout,sizeof(timeout)));
        CHECK(!connect(client,(struct sockaddr*)&address,sizeof(address)));
        if(run==0) {
            const unsigned char payload[]={0xd0,0x9f,0xf0,0x9f,0x98,0x84};
            UcipHeader header={{'U','C','I','P'},1,1,0,sizeof(payload)};unsigned char packet[18];
            memcpy(packet,&header,12);memcpy(packet+12,payload,sizeof(payload));
            for(unsigned i=0;i<sizeof(packet);i++)CHECK(send(client,(char*)packet+i,1,0)==1);
            unsigned char received[18];CHECK(socket_exact(client,received,18)&&!memcmp(received,packet,18));
            header.kind=2;header.length=0;CHECK(send(client,(char*)&header,12,0)==12);
            CHECK(socket_exact(client,&header,12)&&ucip_header_valid(&header)&&header.kind==1&&header.length==10);
            unsigned char text[10];CHECK(socket_exact(client,text,10)&&!memcmp(text,"RELAY_TEST",10));
        }else if(run==1) {
            unsigned char *payload=malloc(UCIP_MAX);CHECK(payload);memset(payload,'x',UCIP_MAX);
            UcipHeader header={{'U','C','I','P'},1,1,0,UCIP_MAX};CHECK(send(client,(char*)&header,12,0)==12);
            for(unsigned offset=0;offset<UCIP_MAX;) {
                int n=send(client,(char*)payload+offset,(int)(UCIP_MAX-offset),0);CHECK(n>0);offset+=(unsigned)n;
            }
            CHECK(socket_exact(client,&header,12)&&ucip_header_valid(&header)&&header.length==UCIP_MAX);
            unsigned char *received=malloc(UCIP_MAX);CHECK(received);
            CHECK(socket_exact(client,received,UCIP_MAX)&&!memcmp(received,payload,UCIP_MAX));free(received);free(payload);
        }else if(run==2) {
            UcipHeader bad={{'U','C','I','P'},1,1,0,UCIP_MAX+1};CHECK(send(client,(char*)&bad,12,0)==12);
            char byte;CHECK(recv(client,&byte,1,0)<=0);
        }else {
            char byte;CHECK(recv(client,&byte,1,0)<=0);
        }
        shutdown(client,SD_BOTH);closesocket(client);
        CHECK(WaitForSingleObject(server,5000)==WAIT_OBJECT_0&&state.result==0);
        CHECK(state.outcome.stage>=10&&state.outcome.stage<=14);
        if(run==4)CHECK(state.outcome.child_exited&&state.outcome.child_exit==77);
        CloseHandle(server);closesocket(state.listener);
    }
    CHECK(SetEnvironmentVariableW(L"UAVF_CLIPBOARD_SELFTEST_EXIT",NULL));
    WSACleanup();printf("ACTUAL_SOCKET_PIPE_OWN_CHILD_RELAY_CHECKS=%u PASS\nHOST_CLIPBOARD_ACCESSES=0\nNOT_VIOSOCK_OR_GUEST_RUNTIME_PASS=1\n",checks);return 0;
}
