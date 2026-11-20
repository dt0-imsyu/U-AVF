/* Actual AVC encode->independent OpenH264 decode admission, not FPS evidence. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "codec_api.h"
#define X264_ADAPTER_TEST 1
#include "windows-x264-adapter.h"
static FARPROC test_api(HMODULE decoder,const char *name) {
    if(!strcmp(name,"WelsCreateSVCEncoder"))return (void*)x264_adapter_create;
    if(!strcmp(name,"WelsDestroySVCEncoder"))return (void*)x264_adapter_destroy;
    return GetProcAddress(decoder,name);
}
/* Reuse the exact existing12-frame/luma/stride admission fixture. Its version
 * check describes the independent decoder, NOT the encoder being compared. */
#define GetProcAddress(module,name) test_api(module,name)
#include "windows-eight-codec-selftest.h"
#undef GetProcAddress
int wmain(int argc,wchar_t **argv) {
    if(argc!=3)return 2;
    HMODULE encoder=LoadLibraryExW(argv[1],NULL,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
    HMODULE decoder=LoadLibraryExW(argv[2],NULL,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
    if(!encoder||!decoder||!x264_adapter_bind(encoder))return 3;
    int result=eight_codec_selftest(decoder);
    printf("X264_ULTRAFAST_ZEROLATENCY_ABI_DECODE_TEST=%d FRAME_COUNT=12 ISA=NEON_ONLY NOT_A_FPS_BENCHMARK=1\n",result);
    FreeLibrary(decoder);FreeLibrary(encoder);return result?1:0;
}
