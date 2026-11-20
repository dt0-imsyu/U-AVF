/* Finite real-desktop proof. Temporary CCD topology, no registry persistence,
 * synthetic window, authentication change, memory mapping, or capture API. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
void *memset(void *p,int c,size_t n){volatile unsigned char *s=p;while(n--)*s++=(unsigned char)c;return p;}
void *memcpy(void *p,const void *q,size_t n){volatile unsigned char *d=p;const volatile unsigned char *s=q;while(n--)*d++=*s++;return p;}
#include "idd-desktop-policy.h"
#ifndef UAVF_IDD_DESKTOP_OBSERVE_MS
#define UAVF_IDD_DESKTOP_OBSERVE_MS 30000
#endif
static DISPLAYCONFIG_PATH_INFO before_paths[IDD_DESKTOP_MAX_PATHS],all_paths[IDD_DESKTOP_MAX_PATHS];
static DISPLAYCONFIG_MODE_INFO before_modes[IDD_DESKTOP_MAX_MODES],all_modes[IDD_DESKTOP_MAX_MODES];
static UINT32 before_np,before_nm;
static HANDLE report=INVALID_HANDLE_VALUE;
static void note(const WCHAR *stage,LONG value) {
    WCHAR line[256];DWORD written;
    int n=wsprintfW(line,L"STAGE=%s RESULT=%ld TICK=%I64u\r\n",stage,value,GetTickCount64());
    if(report!=INVALID_HANDLE_VALUE&&n>0&&n<256){WriteFile(report,line,(DWORD)n*2,&written,0);FlushFileBuffers(report);}
}
static int on_user_desktop(void) {
    WCHAR attached[64]={0},input[64]={0};DWORD n=0,session=0;
    if(!ProcessIdToSessionId(GetCurrentProcessId(),&session)||session!=WTSGetActiveConsoleSessionId())return 0;
    if(!GetUserObjectInformationW(GetThreadDesktop(GetCurrentThreadId()),UOI_NAME,attached,sizeof(attached),&n)
       ||lstrcmpW(attached,L"Default"))return 0;
    HDESK d=OpenInputDesktop(0,FALSE,DESKTOP_READOBJECTS);
    if(!d)return 0;
    BOOL ok=GetUserObjectInformationW(d,UOI_NAME,input,sizeof(input),&n)&&!lstrcmpW(input,L"Default");
    CloseDesktop(d);return ok&&GetShellWindow()!=0;
}
static LONG query(UINT32 flags,UINT32 *np,DISPLAYCONFIG_PATH_INFO *p,UINT32 *nm,DISPLAYCONFIG_MODE_INFO *m) {
    for(int tries=0;tries<3;tries++) {
        LONG r=GetDisplayConfigBufferSizes(flags,np,nm);if(r)return r;
        if(*np>IDD_DESKTOP_MAX_PATHS||*nm>IDD_DESKTOP_MAX_MODES)return ERROR_INSUFFICIENT_BUFFER;
        r=QueryDisplayConfig(flags,np,p,nm,m,0);
        if(r!=ERROR_INSUFFICIENT_BUFFER)return r;
    }
    return ERROR_INSUFFICIENT_BUFFER;
}
#ifdef UAVF_IDD_ORDINARY_WINDOW_WORKLOAD
/* Same ordinary-window fixture as desktop-window-motion.ps1, synchronized to
 * the actual applied topology. Not a source/transport/input/auth mechanism.
 * Explorer must already be opened through existing authorized UIN1 input.
 * No synthetic drawable, application launch, registry change or user file IO.
 */
static LONG ordinary_window_workload(void) {
    HWND window=FindWindowW(L"CabinetWClass",0);RECT original={0};DWORD pid=0,session=0;
    WCHAR image[1024]={0};DWORD size=1024;
    if(!window||!GetWindowRect(window,&original))return ERROR_NOT_FOUND;
    GetWindowThreadProcessId(window,&pid);
    HANDLE process=OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,pid);
    BOOL owned=process&&QueryFullProcessImageNameW(process,0,image,&size)
        &&!lstrcmpiW(image,L"C:\\Windows\\explorer.exe")
        &&ProcessIdToSessionId(pid,&session)&&session==WTSGetActiveConsoleSessionId();
    if(process)CloseHandle(process);
    if(!owned)return ERROR_ACCESS_DENIED;
    note(L"ORDINARY_EXPLORER_WORKLOAD_STARTED",(LONG)pid);
    ULONGLONG start=GetTickCount64();LONG result=0;
    while(GetTickCount64()-start<30000) {
        if(!on_user_desktop()||!IsWindow(window)){result=ERROR_INVALID_WINDOW_HANDLE;break;}
        ULONGLONG phase=(GetTickCount64()-start)%4000;
        int distance=(int)(phase<2000?phase:4000-phase);
        if(!SetWindowPos(window,0,100+distance/5,80+distance/20,1560,980,SWP_NOZORDER|SWP_NOACTIVATE)){
            result=(LONG)GetLastError();break;
        }
        Sleep(16);
    }
    note(L"ORDINARY_EXPLORER_WORKLOAD_ENDED",result);
    BOOL restored=SetWindowPos(window,0,original.left,original.top,original.right-original.left,
        original.bottom-original.top,SWP_NOZORDER|SWP_NOACTIVATE);
    note(L"ORDINARY_EXPLORER_POSITION_RESTORE",restored?0:(LONG)GetLastError());
    return result?result:(restored?0:ERROR_INVALID_WINDOW_HANDLE);
}
#endif
void idd_start(void) {
    DWORD serial=0;
    if(!GetVolumeInformationW(L"C:\\",0,0,&serial,0,0,0,0)||serial!=0x3ef669b7)ExitProcess(2);
    if(GetFileAttributesW(L"C:\\UAVF\\IddWorkingProbe\\approved-working-test.txt")==INVALID_FILE_ATTRIBUTES)ExitProcess(3);
    report=CreateFileW(L"C:\\UAVF\\IddWorkingProbe\\desktop-topology22.txt",GENERIC_WRITE,FILE_SHARE_READ,0,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,0);
    if(report==INVALID_HANDLE_VALUE)ExitProcess(4);
    if(!on_user_desktop()){note(L"USER_DESKTOP_REQUIRED_NO_MUTATIONS",0);ExitProcess(5);}
    LONG result=query(QDC_ONLY_ACTIVE_PATHS,&before_np,before_paths,&before_nm,before_modes);
    if(result||!idd_desktop_snapshot_valid(before_np,before_paths,before_nm,before_modes)){
        note(L"SNAPSHOT_REJECTED_NO_MUTATIONS",result);ExitProcess(6);
    }
    UINT32 np=0,nm=0;DISPLAYCONFIG_PATH_INFO selected={0};UINT32 matches=0;
    UINT32 ownId=0,ownDesc=0,ownFlags=0,ownAvailable=0,nameErrors=0;
    ULONGLONG deadline=GetTickCount64()+15000;
    do {
        matches=0;ownId=ownDesc=ownFlags=ownAvailable=nameErrors=0;result=query(QDC_ALL_PATHS,&np,all_paths,&nm,all_modes);
        if(result){note(L"ENUMERATION_FAILED_NO_MUTATIONS",result);ExitProcess(7);}
        for(UINT32 i=0;i<np;i++) {
            DISPLAYCONFIG_SOURCE_DEVICE_NAME name={0};name.header.type=DISPLAYCONFIG_DEVICE_INFO_GET_SOURCE_NAME;
            name.header.size=sizeof(name);name.header.adapterId=all_paths[i].sourceInfo.adapterId;name.header.id=all_paths[i].sourceInfo.id;
            if(DisplayConfigGetDeviceInfo(&name.header)){nameErrors++;continue;}
            DISPLAY_DEVICEW adapter={0};adapter.cb=sizeof(adapter);
            for(DWORD k=0;k<16;k++) {
                if(!EnumDisplayDevicesW(0,k,&adapter,0))break;
                if(!lstrcmpW(adapter.DeviceName,name.viewGdiDeviceName)) {
                    if(!lstrcmpW(adapter.DeviceID,L"UavfIddSourceProbe")){ownId++;ownFlags|=adapter.StateFlags;ownAvailable+=all_paths[i].targetInfo.targetAvailable;}
                    if(!lstrcmpW(adapter.DeviceString,L"U-AVF isolated source probe"))ownDesc++;
                }
                if(!lstrcmpW(adapter.DeviceName,name.viewGdiDeviceName)&&!lstrcmpW(adapter.DeviceString,L"U-AVF isolated source probe")
                   &&all_paths[i].targetInfo.targetAvailable){selected=all_paths[i];matches++;}
            }
        }
        if(!matches)Sleep(100);
    }while(!matches&&GetTickCount64()<deadline);
    if(matches!=1){
        note(L"Paths",np);note(L"OwnId",ownId);note(L"OwnDesc",ownDesc);
        note(L"OwnFlags",ownFlags);note(L"Available",ownAvailable);note(L"NameErrors",nameErrors);
        note(L"AMBIGUOUS_OR_ABSENT_PROBE_NO_MUTATIONS",matches);ExitProcess(8);
    }
    DISPLAYCONFIG_MODE_INFO mode={0};idd_desktop_candidate(&selected,&mode);
    result=SetDisplayConfig(1,&selected,1,&mode,SDC_VALIDATE|SDC_USE_SUPPLIED_DISPLAY_CONFIG|SDC_ALLOW_CHANGES);
    if(result){note(L"TOPOLOGY_VALIDATE_FAILED_NO_MUTATIONS",result);ExitProcess(9);}
    HANDLE snapshot=CreateFileW(L"C:\\UAVF\\IddWorkingProbe\\desktop-topology22-before.bin",GENERIC_WRITE,0,0,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,0);
    DWORD wrote=0;BOOL saved=snapshot!=INVALID_HANDLE_VALUE;
    if(saved)saved=WriteFile(snapshot,&before_np,4,&wrote,0)&&wrote==4&&WriteFile(snapshot,&before_nm,4,&wrote,0)&&wrote==4;
    if(saved)saved=WriteFile(snapshot,before_paths,before_np*sizeof(before_paths[0]),&wrote,0)&&wrote==before_np*sizeof(before_paths[0]);
    if(saved)saved=WriteFile(snapshot,before_modes,before_nm*sizeof(before_modes[0]),&wrote,0)&&wrote==before_nm*sizeof(before_modes[0]);
    if(snapshot!=INVALID_HANDLE_VALUE){if(saved)saved=FlushFileBuffers(snapshot);CloseHandle(snapshot);}
    if(!saved){note(L"JOURNAL_FAILED_NO_MUTATIONS",GetLastError());ExitProcess(10);}
    if(!on_user_desktop()){note(L"DESKTOP_CHANGED_NO_MUTATIONS",0);ExitProcess(13);}
    result=SetDisplayConfig(1,&selected,1,&mode,SDC_APPLY|SDC_USE_SUPPLIED_DISPLAY_CONFIG|SDC_ALLOW_CHANGES);
    note(L"TEMPORARY_USER_DESKTOP_APPLY",result);
    DWORD exit_code=result?11:0;
    if(!result) {
        DEVMODEW actual={0};DISPLAYCONFIG_SOURCE_DEVICE_NAME name={0};
        name.header.type=DISPLAYCONFIG_DEVICE_INFO_GET_SOURCE_NAME;name.header.size=sizeof(name);
        name.header.adapterId=selected.sourceInfo.adapterId;name.header.id=selected.sourceInfo.id;actual.dmSize=sizeof(actual);
        BOOL valid=!DisplayConfigGetDeviceInfo(&name.header)&&EnumDisplaySettingsW(name.viewGdiDeviceName,ENUM_CURRENT_SETTINGS,&actual)
          &&actual.dmPelsWidth==1920&&actual.dmPelsHeight==1200&&actual.dmPosition.x==0&&actual.dmPosition.y==0&&actual.dmDisplayFrequency==60;
        note(L"REAL_DESKTOP_PRIMARY_GEOMETRY",valid?0:ERROR_BAD_CONFIGURATION);
        if(!valid)exit_code=12;
        else {
            deadline=GetTickCount64()+UAVF_IDD_DESKTOP_OBSERVE_MS;
#ifdef UAVF_IDD_ORDINARY_WINDOW_WORKLOAD
            /* Leave20s for the independent read-only Android fence observer,
             * then measure30s of a real window, wholly inside this aperture. */
            ULONGLONG workload_at=GetTickCount64()+20000;
            while(GetTickCount64()<workload_at&&on_user_desktop())Sleep(100);
            LONG workload=ordinary_window_workload();
            if(workload){note(L"ORDINARY_WORKLOAD_FAILED_NO_FPS_CLAIM",workload);exit_code=14;}
#endif
            while(GetTickCount64()<deadline&&on_user_desktop())Sleep(250);
        }
    }
    /* Also restore after a failed apply: do not assume failure was atomic. */
    result=SetDisplayConfig(before_np,before_paths,before_nm,before_modes,SDC_APPLY|SDC_USE_SUPPLIED_DISPLAY_CONFIG);
    note(L"EXACT_TOPOLOGY_RESTORE",result);
    if(result)exit_code=30;
    else {
        result=query(QDC_ONLY_ACTIVE_PATHS,&np,all_paths,&nm,all_modes);
        if(result||np!=before_np||!idd_desktop_snapshot_valid(np,all_paths,nm,all_modes))exit_code=31;
        for(UINT32 i=0;!result&&i<before_np&&exit_code!=31;i++) {
            UINT32 found=0;
            for(UINT32 j=0;j<np;j++)if(all_paths[j].sourceInfo.id==before_paths[i].sourceInfo.id
                &&all_paths[j].targetInfo.id==before_paths[i].targetInfo.id
                &&all_paths[j].sourceInfo.adapterId.LowPart==before_paths[i].sourceInfo.adapterId.LowPart
                &&all_paths[j].sourceInfo.adapterId.HighPart==before_paths[i].sourceInfo.adapterId.HighPart
                &&all_paths[j].targetInfo.adapterId.LowPart==before_paths[i].targetInfo.adapterId.LowPart
                &&all_paths[j].targetInfo.adapterId.HighPart==before_paths[i].targetInfo.adapterId.HighPart) {
                const DISPLAYCONFIG_SOURCE_MODE *a=&before_modes[before_paths[i].sourceInfo.modeInfoIdx].sourceMode;
                const DISPLAYCONFIG_SOURCE_MODE *b=&all_modes[all_paths[j].sourceInfo.modeInfoIdx].sourceMode;
                const DISPLAYCONFIG_PATH_TARGET_INFO *ta=&before_paths[i].targetInfo,*tb=&all_paths[j].targetInfo;
                if(a->width==b->width&&a->height==b->height&&a->position.x==b->position.x&&a->position.y==b->position.y&&a->pixelFormat==b->pixelFormat
                   &&ta->rotation==tb->rotation&&ta->scaling==tb->scaling
                   &&ta->refreshRate.Numerator==tb->refreshRate.Numerator&&ta->refreshRate.Denominator==tb->refreshRate.Denominator)found++;
            }
            if(found!=1)exit_code=31;
        }
        note(L"TOPOLOGY_RESTORE_READBACK",exit_code==31?ERROR_BAD_CONFIGURATION:0);
    }
    CloseHandle(report);ExitProcess(exit_code);
}
