/* Diagnostic only: capture the current Windows desktop through public GDI.
 * No driver patches, input injection, or desktop switching. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdint.h>

int wmain(int argc, wchar_t **argv) {
    if (argc != 2) { fwprintf(stderr,L"Usage: winpe-screen.exe output.bmp\n"); return 2; }
    int result=1, w=GetSystemMetrics(SM_CXSCREEN), h=GetSystemMetrics(SM_CYSCREEN);
    HDC screen=NULL, memory=NULL; HBITMAP bitmap=NULL; HGDIOBJ old=NULL;
    HANDLE file=INVALID_HANDLE_VALUE; void *pixels=NULL;
    if(w<=0 || h<=0 || w>8192 || h>8192) { fprintf(stderr,"Invalid display %dx%d\n",w,h); goto done; }
    DWORD size=(DWORD)((uint64_t)w*h*4);
    BITMAPINFO info={0}; info.bmiHeader.biSize=sizeof(BITMAPINFOHEADER);
    info.bmiHeader.biWidth=w; info.bmiHeader.biHeight=-h;
    info.bmiHeader.biPlanes=1; info.bmiHeader.biBitCount=32;
    info.bmiHeader.biCompression=BI_RGB; info.bmiHeader.biSizeImage=size;
    screen=GetDC(NULL); if(!screen) goto done;
    memory=CreateCompatibleDC(screen); if(!memory) goto done;
    bitmap=CreateDIBSection(screen,&info,DIB_RGB_COLORS,&pixels,NULL,0); if(!bitmap) goto done;
    old=SelectObject(memory,bitmap); if(!old || old==HGDI_ERROR) goto done;
    if(!BitBlt(memory,0,0,w,h,screen,0,0,SRCCOPY|CAPTUREBLT) || !GdiFlush()) goto done;
    uint64_t nonblack=0, sum=0;
    for(DWORD i=0;i<size;i+=4) {
        unsigned char *p=(unsigned char*)pixels+i;
        if(p[0] || p[1] || p[2]) nonblack++;
        sum+=p[0]+p[1]+p[2];
    }
    file=CreateFileW(argv[1],GENERIC_WRITE,0,NULL,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,NULL);
    if(file==INVALID_HANDLE_VALUE) goto done;
    BITMAPFILEHEADER header={0}; header.bfType=0x4d42;
    header.bfOffBits=sizeof(header)+sizeof(info.bmiHeader); header.bfSize=header.bfOffBits+size;
    DWORD written=0;
    if(!WriteFile(file,&header,sizeof(header),&written,NULL) || written!=sizeof(header)) goto done;
    if(!WriteFile(file,&info.bmiHeader,sizeof(info.bmiHeader),&written,NULL) || written!=sizeof(info.bmiHeader)) goto done;
    if(!WriteFile(file,pixels,size,&written,NULL) || written!=size) goto done;
    printf("CAPTURE_OK width=%d height=%d nonblack=%llu mean_rgb=%.3f\n",w,h,
        (unsigned long long)nonblack,(double)sum/((double)w*h*3)); result=0;
done:
    if(result) fprintf(stderr,"CAPTURE_FAILED win32_error=%lu\n",GetLastError());
    if(file!=INVALID_HANDLE_VALUE) CloseHandle(file);
    if(old && old!=HGDI_ERROR) SelectObject(memory,old);
    if(bitmap) DeleteObject(bitmap);
    if(memory) DeleteDC(memory);
    if(screen) ReleaseDC(NULL,screen);
    return result;
}
