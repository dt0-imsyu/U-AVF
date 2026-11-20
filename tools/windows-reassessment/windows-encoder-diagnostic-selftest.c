#define wmain encoder_real_wmain
#include "winpe-encode.c"
#undef wmain
#include <assert.h>
int wmain(void) {
    HANDLE read=NULL,write=NULL;assert(CreatePipe(&read,&write,NULL,4096));
    diagnostic_out=write;diagnostic_width=1920;diagnostic_height=1200;
    unsigned char text[1371];memset(text,'D',sizeof(text));
    assert(send_diagnostic(text,sizeof(text)));
    UvfHeader frame={{'U','V','F','1'},1,2,1,1,11,1920,1200,100000,1,0};
    assert(raw_write(write,&frame,sizeof(frame))&&raw_write(write,"F",1));
    CloseHandle(write);
    DWORD n=0;UvfHeader header;unsigned char bytes[1024];
    for(int i=0;i<2;i++) {
        assert(ReadFile(read,&header,40,&n,NULL)&&n==40);
        assert(header.kind==6&&header.codec==1&&header.sequence==0&&header.pts==0);
        assert(header.width==1920&&header.height==1200&&header.reserved==0);
        assert(header.length==(i==0?1024u:347u));
        assert(ReadFile(read,bytes,header.length,&n,NULL)&&n==header.length);
        for(DWORD j=0;j<n;j++)assert(bytes[j]=='D');
    }
    assert(ReadFile(read,&header,40,&n,NULL)&&n==40&&header.kind==2&&header.sequence==11);
    assert(ReadFile(read,bytes,1,&n,NULL)&&n==1&&bytes[0]=='F');
    CloseHandle(read);
    diagnostic_out=INVALID_HANDLE_VALUE;
    unsigned char packed[16]={0,0,255,0,0,255,0,0,255,0,0,0,255,255,255,0};
    unsigned char padded[24];memset(padded,0xcc,sizeof(padded));
    memcpy(padded,packed,8);memcpy(padded+12,packed+8,8);
    unsigned char reference[6],pitched[6];
    i420_pitch(packed,reference,2,2,8);i420_pitch(padded,pitched,2,2,12);
    assert(!memcmp(reference,pitched,6));
    assert(padded[8]==0xcc&&padded[20]==0xcc);
    puts("ACTUAL_UVF1_DIAGNOSTIC_CHUNKING_AND_FRAME_ORDER=PASS");
    puts("ACTUAL_BGRA_ROW_PITCH_TO_I420=PASS");
    return 0;
}
