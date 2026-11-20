/* R&D only: exact separately staged ARM64 codec. Never searches by DLL name.
 * Public volume and BCrypt APIs; no drive letters, mount or trust changes. */
#include <bcrypt.h>
#ifdef UAVF_EXPERIMENTAL_X264
static const unsigned char eight_codec_sha[32]={
    0x04,0x63,0xf4,0xb9,0xd0,0x81,0xcd,0x6f,0x92,0x18,0x37,0xfb,0xe2,0x7e,0x9d,0x03,
    0xd2,0x9c,0xd8,0x48,0x98,0x1a,0x9c,0x36,0x54,0x11,0x36,0x21,0xef,0x55,0xd1,0x29};
#else
static const unsigned char eight_codec_sha[32]={
    0x53,0x3f,0x9c,0x10,0x8f,0xd7,0x18,0x28,0xe7,0xa2,0x09,0xa1,0x0f,0x02,0x02,0xfe,
    0x9d,0x83,0x12,0xb0,0x50,0x2b,0xd8,0x03,0x5c,0x9f,0x81,0x67,0xd7,0xcd,0x5e,0x04};
#endif
static bool codec_file_hash(HANDLE file,ULONGLONG expected_size,const unsigned char expected[32]) {
    LARGE_INTEGER size={0},zero={0};BCRYPT_ALG_HANDLE alg=NULL;BCRYPT_HASH_HANDLE hash=NULL;
    unsigned char *object=NULL,buffer[16384],actual[32];DWORD object_bytes=0,used=0,got=0;
    bool ok=false;
    if(!GetFileSizeEx(file,&size)||(ULONGLONG)size.QuadPart!=expected_size||
       !SetFilePointerEx(file,zero,NULL,FILE_BEGIN))goto done;
    if(BCryptOpenAlgorithmProvider(&alg,BCRYPT_SHA256_ALGORITHM,NULL,0)<0)goto done;
    if(BCryptGetProperty(alg,BCRYPT_OBJECT_LENGTH,(PUCHAR)&object_bytes,sizeof(object_bytes),&used,0)<0||
       used!=sizeof(object_bytes)||!object_bytes||object_bytes>65536)goto done;
    object=malloc(object_bytes);if(!object)goto done;
    if(BCryptCreateHash(alg,&hash,object,object_bytes,NULL,0,0)<0)goto done;
    ULONGLONG total=0;
    while(total<expected_size) {
        DWORD want=(DWORD)((expected_size-total)>sizeof(buffer)?sizeof(buffer):(expected_size-total));
        if(!ReadFile(file,buffer,want,&got,NULL)||got!=want||BCryptHashData(hash,buffer,got,0)<0)goto done;
        total+=got;
    }
    if(BCryptFinishHash(hash,actual,sizeof(actual),0)<0)goto done;
    ok=memcmp(actual,expected,32)==0;
done:
    if(hash)BCryptDestroyHash(hash);if(alg)BCryptCloseAlgorithmProvider(alg,0);
    free(object);return ok;
}
static HMODULE load_eight_codec(DWORD *last_error) {
    wchar_t volume[MAX_PATH],path[MAX_PATH+32],fs[32];HMODULE dll=NULL;
    HANDLE search=FindFirstVolumeW(volume,MAX_PATH);
    *last_error=ERROR_FILE_NOT_FOUND;
    if(search==INVALID_HANDLE_VALUE){*last_error=GetLastError();return NULL;}
    for(unsigned index=0;index<128;index++) {
        if(GetVolumeInformationW(volume,NULL,0,NULL,NULL,NULL,fs,32)&&!wcscmp(fs,L"FAT32")) {
            int count=swprintf(path,MAX_PATH+32,L"%lsUAVF8.DLL",volume);
            if(count>0&&count<MAX_PATH+32) {
                // No sharing for write/delete: keep the verified file locked
                // through LoadLibrary to prevent replacement between operations.
                HANDLE file=CreateFileW(path,GENERIC_READ,FILE_SHARE_READ,NULL,OPEN_EXISTING,
                    FILE_FLAG_SEQUENTIAL_SCAN|FILE_FLAG_OPEN_REPARSE_POINT,NULL);
                if(file!=INVALID_HANDLE_VALUE) {
                    BY_HANDLE_FILE_INFORMATION info={0};
                    bool regular=GetFileInformationByHandle(file,&info)&&
                        !(info.dwFileAttributes&(FILE_ATTRIBUTE_DIRECTORY|FILE_ATTRIBUTE_REPARSE_POINT));
                    if(regular&&codec_file_hash(file,843264,eight_codec_sha)) {
                        dll=LoadLibraryExW(path,NULL,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_SYSTEM32);
                        *last_error=dll?ERROR_SUCCESS:GetLastError();
                    } else {*last_error=ERROR_INVALID_DATA;}
                    CloseHandle(file);
                    if(dll)break;
                }
            }
        }
        if(!FindNextVolumeW(search,volume,MAX_PATH))break;
    }
    FindVolumeClose(search);return dll;
}
