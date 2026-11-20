/* Tests the actual retirement function with mocked registry/environment APIs.
 * Never accesses host registry, files, SCM, desktop or process creation. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <wchar.h>
#include <string.h>
static unsigned deleted,closed;
static DWORD scenario;
static LONG WINAPI fake_open(HKEY root,LPCWSTR name,DWORD options,REGSAM access,PHKEY result) {
    (void)options;
    if(root!=HKEY_LOCAL_MACHINE||wcscmp(name,L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run")
       ||access!=(KEY_QUERY_VALUE|KEY_SET_VALUE|KEY_WOW64_64KEY))return ERROR_ACCESS_DENIED;
    *result=(HKEY)(UINT_PTR)1;return ERROR_SUCCESS;
}
static LONG WINAPI fake_query(HKEY key,LPCWSTR name,LPDWORD reserved,LPDWORD type,LPBYTE bytes,LPDWORD size) {
    (void)reserved;
    if(key!=(HKEY)(UINT_PTR)1||wcscmp(name,L"UAVFDisplay"))return ERROR_ACCESS_DENIED;
    if(scenario==1)return ERROR_FILE_NOT_FOUND;
    const wchar_t *value=scenario==2?L"user-custom-command":
        L"cmd.exe /d /c start /min cmd.exe /d /c uavf-installed-display.cmd";
    DWORD length=(DWORD)((wcslen(value)+1)*sizeof(wchar_t));
    if(*size<length)return ERROR_MORE_DATA;
    memcpy(bytes,value,length);*size=length;*type=scenario==3?REG_EXPAND_SZ:REG_SZ;
    if(scenario==4)(*size)-=sizeof(wchar_t); // Non-terminated value must survive.
    return ERROR_SUCCESS;
}
static LONG WINAPI fake_delete(HKEY key,LPCWSTR name) {
    if(key!=(HKEY)(UINT_PTR)1||wcscmp(name,L"UAVFDisplay"))return ERROR_ACCESS_DENIED;
    deleted++;return ERROR_SUCCESS;
}
static LONG WINAPI fake_close(HKEY key){(void)key;closed++;return ERROR_SUCCESS;}
static DWORD WINAPI fake_environment(LPCWSTR name,LPWSTR value,DWORD size){(void)name;(void)value;(void)size;return 0;}
#define RegOpenKeyExW fake_open
#define RegQueryValueExW fake_query
#define RegDeleteValueW fake_delete
#define RegCloseKey fake_close
#define GetEnvironmentVariableW fake_environment
#define wmain broker_original_wmain
#include "windows-display-service.c"
#undef wmain
int wmain(void) {
    if(cpu_log_rotation_needed(0,512*1024-1)||!cpu_log_rotation_needed(0,512*1024)
       ||cpu_log_rotation_needed(1,512*1024)||cpu_log_rotation_needed(7,1024*1024))return 20;
    for(scenario=0;scenario<5;scenario++) {
        deleted=closed=0;retire_legacy_autostart();
        if(deleted!=(scenario==0?1u:0u)||closed!=1)return 10+(int)scenario;
    }
    puts("LEGACY_RUN_EXACT_MATCH_ONLY_ABSENT_CUSTOM_TYPE_TRUNCATED_PRESERVED=PASS");
    puts("CPU_LOG_ROTATION_FIRST_SAMPLE_AT_CAP_ONLY=PASS");
    return 0;
}
