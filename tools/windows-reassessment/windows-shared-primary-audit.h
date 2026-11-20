/* Documented GDI query only. Never creates/locks/maps/writes allocations. */
#include <stddef.h>
typedef struct {HDC dc;UINT adapter;LUID luid;UINT source;} UavfOpenAdapterHdc;
typedef struct {UINT adapter,source,shared;} UavfSharedPrimary;
typedef struct {UINT adapter;} UavfCloseAdapter;
_Static_assert(sizeof(UavfOpenAdapterHdc)==24,"64-bit SDK OpenAdapterFromHdc ABI");
_Static_assert(offsetof(UavfOpenAdapterHdc,adapter)==8&&offsetof(UavfOpenAdapterHdc,source)==20,"SDK adapter offsets");
_Static_assert(sizeof(UavfSharedPrimary)==12&&sizeof(UavfCloseAdapter)==4,"SDK query/close ABI");
typedef LONG (WINAPI *UavfOpenAdapterFn)(UavfOpenAdapterHdc*);
typedef LONG (WINAPI *UavfPrimaryFn)(UavfSharedPrimary*);
typedef LONG (WINAPI *UavfCloseAdapterFn)(const UavfCloseAdapter*);
typedef struct {LONG opened,queried,closed;UINT adapter,source,shared;LUID luid;} UavfPrimaryResult;
static UavfPrimaryResult shared_primary_query(HDC dc,UavfOpenAdapterFn open,UavfPrimaryFn query,UavfCloseAdapterFn close) {
    UavfPrimaryResult result={0};result.opened=result.queried=result.closed=(LONG)0xc00000bb;
    if(!dc||!open||!query||!close)return result;
    UavfOpenAdapterHdc adapter={0};adapter.dc=dc;
    result.opened=open(&adapter);
    if(result.opened<0)return result;
    result.adapter=adapter.adapter;result.luid=adapter.luid;result.source=adapter.source;
    UavfSharedPrimary primary={adapter.adapter,adapter.source,0};
    result.queried=query(&primary);if(result.queried>=0)result.shared=primary.shared;
    UavfCloseAdapter cleanup={adapter.adapter};result.closed=close(&cleanup);
    return result;
}
static void shared_primary_inventory(HANDLE report) {
    static int attempted;
    wchar_t desktop[128]={0};DWORD size=0;
    if(attempted||!GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,desktop,sizeof(desktop),&size)||wcscmp(desktop,L"Default"))return;
    attempted=1;
    HMODULE gdi=GetModuleHandleW(L"gdi32.dll");HDC dc=GetDC(NULL);
    UavfPrimaryResult result=shared_primary_query(dc,
        (UavfOpenAdapterFn)(void*)GetProcAddress(gdi,"D3DKMTOpenAdapterFromHdc"),
        (UavfPrimaryFn)(void*)GetProcAddress(gdi,"D3DKMTGetSharedPrimaryHandle"),
        (UavfCloseAdapterFn)(void*)GetProcAddress(gdi,"D3DKMTCloseAdapter"));
    if(dc)ReleaseDC(NULL,dc);
    char line[384];int length=snprintf(line,sizeof(line),
        "SHARED_PRIMARY_AUDIT desktop=Default open=%08lx query=%08lx close=%08lx adapter=%u source=%u shared=%u luid=%08lx:%08lx QUERY_ONLY_NO_ALLOCATION_MAP=1\r\n",
        (unsigned long)result.opened,(unsigned long)result.queried,(unsigned long)result.closed,
        result.adapter,result.source,result.shared,(unsigned long)result.luid.HighPart,(unsigned long)result.luid.LowPart);
    if(length>0&&length<(int)sizeof(line))write_all(report,line,(DWORD)length);
}
