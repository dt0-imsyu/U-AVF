#include <stdio.h>
#include <assert.h>
#include "windows-idd-lifecycle-policy.h"
int main(void) {
    unsigned checks=0;
    const wchar_t *names[]={NULL,L"",L"Winlogon",L"Default",L"Other",L"WinlogonX",L"DefaultX"};
    for(unsigned i=0;i<sizeof(names)/sizeof(names[0]);i++)for(int primary=0;primary<2;primary++) {
        enum UavfIddCaptureChoice expected=i==2?UAVF_IDD_BASELINE:i==3?(primary?UAVF_IDD_PRIMARY_PIPE:UAVF_IDD_BASELINE):UAVF_IDD_REJECT;
        enum UavfIddCaptureChoice actual=idd_capture_choice(names[i],primary);
        assert(actual==expected);checks++;
    }
    for(int initial=1;initial<=2;initial++)for(int now=0;now<=2;now++) {
        assert(idd_capture_needs_restart((enum UavfIddCaptureChoice)initial,(enum UavfIddCaptureChoice)now)==(now==0||initial!=now));checks++;
    }
    assert(idd_capture_choice(L"Default",0)==UAVF_IDD_BASELINE);checks++; // Before admission AND after removal.
    assert(idd_capture_needs_restart(UAVF_IDD_PRIMARY_PIPE,idd_capture_choice(L"Default",0)));checks++;
    printf("IDD_LIFECYCLE_POLICY_PASS checks=%u; NO_DISPLAY_API_CALLED; NOT_GUEST_RECONNECT_PASS\n",checks);
    return 0;
}
