/* Text-only UCIP v1, identical to existing Android ClipboardWire / Linux4054.
 * No clipboard contents in logs. Windows Unicode conversion is strict. */
#define UCIP_MAX 262144u
#pragma pack(push,1)
typedef struct {char magic[4];unsigned char version,kind;unsigned short reserved;unsigned length;} UcipHeader;
#pragma pack(pop)
_Static_assert(sizeof(UcipHeader)==12,"UCIP shared ABI");
static bool ucip_header_valid(const UcipHeader *h) {
    return !memcmp(h->magic,"UCIP",4)&&h->version==1&&!h->reserved&&
        (h->kind==1||h->kind==2)&&h->length<=UCIP_MAX&&!(h->kind==2&&h->length);
}
static wchar_t *ucip_to_wide(const unsigned char *text,unsigned size,unsigned *count) {
    if(size>UCIP_MAX||(!text&&size))return NULL;
    // CF_UNICODETEXT cannot represent embedded NUL; reject instead of silently
    // truncating a message Android regards as complete.
    if(size&&memchr(text,0,size))return NULL;
    int n=size?MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,(const char*)text,(int)size,NULL,0):0;
    if(size&&!n)return NULL;
    wchar_t *wide=malloc(((size_t)n+1)*sizeof(wchar_t));if(!wide)return NULL;
    if(n&&MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,(const char*)text,(int)size,wide,n)!=n){free(wide);return NULL;}
    wide[n]=0;*count=(unsigned)n;return wide;
}
static unsigned char *ucip_from_wide(const wchar_t *wide,unsigned count,unsigned *size) {
    if(count>UCIP_MAX||(!wide&&count))return NULL;
    for(unsigned i=0;i<count;i++)if(!wide[i])return NULL;
    int n=count?WideCharToMultiByte(CP_UTF8,WC_ERR_INVALID_CHARS,wide,(int)count,NULL,0,NULL,NULL):0;
    if((count&&!n)||(unsigned)n>UCIP_MAX)return NULL;
    unsigned char *text=malloc(n?n:1);if(!text)return NULL;
    if(n&&WideCharToMultiByte(CP_UTF8,WC_ERR_INVALID_CHARS,wide,(int)count,(char*)text,n,NULL,NULL)!=n){free(text);return NULL;}
    *size=(unsigned)n;return text;
}
static bool ucip_read_exact(HANDLE input,void *bytes,DWORD count) {
    unsigned char *p=bytes;
    while(count){DWORD n=0;if(!ReadFile(input,p,count,&n,NULL)||!n)return false;p+=n;count-=n;}
    return true;
}
static bool ucip_write_text(HANDLE output,const unsigned char *text,unsigned size) {
    UcipHeader h={{'U','C','I','P'},1,1,0,size};
    if(size>UCIP_MAX)return false;
    const unsigned char *parts[2]={(const unsigned char*)&h,text};DWORD lengths[2]={sizeof(h),size};
    for(unsigned i=0;i<2;i++)while(lengths[i]) {
        DWORD n=0;if(!WriteFile(output,parts[i],lengths[i],&n,NULL)||!n)return false;
        parts[i]+=n;lengths[i]-=n;
    }
    return true;
}
