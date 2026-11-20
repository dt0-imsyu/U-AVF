#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stdio.h>
#include "windows-clipboard-retry.h"
static unsigned calls,pauses,budget;static bool enabled;
static bool allowed(void){return enabled;}
static int serve(void){calls++;if(calls==budget)enabled=false;return (int)calls+10;}
static void pause_mock(DWORD ms){if(ms!=1000){enabled=false;budget=0;}pauses++;}
#define CHECK(x) do{if(!(x)){fprintf(stderr,"FAIL line=%d\n",__LINE__);return 1;}checks++;}while(0)
int main(void) {
    unsigned checks=0;enabled=false;
    CHECK(clipboard_retry_loop(serve,allowed,pause_mock)==2);
    CHECK(calls==0&&pauses==0);
    enabled=true;budget=1;
    CHECK(clipboard_retry_loop(serve,allowed,pause_mock)==11);
    CHECK(calls==1&&pauses==0);
    calls=pauses=0;enabled=true;budget=4;
    CHECK(clipboard_retry_loop(serve,allowed,pause_mock)==14);
    CHECK(calls==4&&pauses==3&&budget==4);
    // Returning from lock never silently enables sync: caller must start a
    // new allowed session. A denied second entry must perform no new attempt.
    CHECK(clipboard_retry_loop(serve,allowed,pause_mock)==2);
    CHECK(calls==4&&pauses==3);
    printf("CLIPBOARD_RECOVERY_POLICY_CHECKS=%u PASS; HOST_CLIPBOARD_ACCESSES=0\n",checks);
    return 0;
}
