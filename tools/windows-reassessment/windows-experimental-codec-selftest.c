/* Exercises the actual admission hash and reusable codec test, on host only. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "codec_api.h"
#include "windows-experimental-codec.h"
#include "windows-eight-codec-selftest.h"
#define CHECK(x) do {if(!(x)){fprintf(stderr,"FAIL line=%d error=%lu\n",__LINE__,GetLastError());return 1;}}while(0)
int wmain(int argc,wchar_t **argv) {
    CHECK(argc==3);
    HANDLE file=CreateFileW(argv[1],GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,FILE_FLAG_SEQUENTIAL_SCAN,NULL);
    CHECK(file!=INVALID_HANDLE_VALUE);
    CHECK(codec_file_hash(file,843264,eight_codec_sha));
    unsigned char wrong[32];memcpy(wrong,eight_codec_sha,32);wrong[0]^=1;
    CHECK(!codec_file_hash(file,843264,wrong));
    CHECK(!codec_file_hash(file,843263,eight_codec_sha));
    CHECK(codec_file_hash(file,843264,eight_codec_sha));
    HANDLE writer=CreateFileW(argv[1],GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_EXISTING,0,NULL);
    CHECK(writer==INVALID_HANDLE_VALUE&&GetLastError()==ERROR_SHARING_VIOLATION);
    CloseHandle(file);
    // The host sibling proves the SAME reusable test function, not ARM64 speed.
    HMODULE host=LoadLibraryExW(argv[2],NULL,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
    CHECK(host);int code=eight_codec_selftest(host);FreeLibrary(host);CHECK(!code);
    // Bind the actual volume loader into this binary; don't enumerate host volumes.
    CHECK(load_eight_codec!=NULL);
    puts("ACTUAL_ADMISSION_SHA_SIZE_POINTER_RESET_AND_FILE_LOCK=PASS\nACTUAL_REUSABLE_EIGHT_THREAD_12_FRAME_SELFTEST=PASS\nNOT_ARM64_RUNTIME_OR_FPS_PASS=1");
    return 0;
}
