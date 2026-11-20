/* Pure decisions only. No desktop opening, authentication, capture or mutation. */
#include <wchar.h>
enum UavfIddCaptureChoice { UAVF_IDD_REJECT=0, UAVF_IDD_BASELINE=1, UAVF_IDD_PRIMARY_PIPE=2 };
static enum UavfIddCaptureChoice idd_capture_choice(const wchar_t *desktop,int primary_is_idd) {
    if(!desktop)return UAVF_IDD_REJECT;
    if(!wcscmp(desktop,L"Winlogon"))return UAVF_IDD_BASELINE;
    if(wcscmp(desktop,L"Default"))return UAVF_IDD_REJECT;
    return primary_is_idd?UAVF_IDD_PRIMARY_PIPE:UAVF_IDD_BASELINE;
}
static int idd_capture_needs_restart(enum UavfIddCaptureChoice initial,enum UavfIddCaptureChoice now) {
    return now==UAVF_IDD_REJECT||initial!=now;
}
