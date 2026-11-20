#define UAVF_SHARED_PRIMARY_AUDIT 1
#define wmain encoder_real_wmain
#include "winpe-encode.c"
#undef wmain
#include <assert.h>
static LONG open_status,query_status,close_status;
static unsigned opens,queries,closes,value;
static LONG WINAPI open_mock(UavfOpenAdapterHdc *p){assert(p->dc==(HDC)(uintptr_t)1);opens++;p->adapter=17;p->source=2;p->luid.LowPart=3;return open_status;}
static LONG WINAPI query_mock(UavfSharedPrimary *p){assert(p->adapter==17&&p->source==2);queries++;p->shared=value;return query_status;}
static LONG WINAPI close_mock(const UavfCloseAdapter *p){assert(p->adapter==17);closes++;return close_status;}
int wmain(void){
    HDC dc=(HDC)(uintptr_t)1;UavfPrimaryResult r;
    r=shared_primary_query(dc,NULL,query_mock,close_mock);assert(r.opened<0&&!opens&&!queries&&!closes);
    open_status=(LONG)0xc0000001;r=shared_primary_query(dc,open_mock,query_mock,close_mock);assert(r.opened<0&&opens==1&&!queries&&!closes);
    open_status=0;r=shared_primary_query(dc,open_mock,query_mock,close_mock);assert(!r.opened&&!r.queried&&!r.shared&&closes==1);
    value=42;r=shared_primary_query(dc,open_mock,query_mock,close_mock);assert(r.shared==42&&r.source==2&&closes==2);
    query_status=(LONG)0xc0000001;r=shared_primary_query(dc,open_mock,query_mock,close_mock);assert(r.queried<0&&!r.shared&&closes==3);
    query_status=0;close_status=(LONG)0xc0000001;r=shared_primary_query(dc,open_mock,query_mock,close_mock);assert(r.closed<0&&closes==4);
    puts("ACTUAL_SHARED_PRIMARY_QUERY_ONLY_ABI_NULL_OPEN_FAILURE_QUERY_FAILURE_CLEANUP=PASS");return 0;
}
