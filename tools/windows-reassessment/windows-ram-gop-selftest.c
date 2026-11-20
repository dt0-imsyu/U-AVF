#define WIN32_LEAN_AND_MEAN
#define GUID WIN_GUID
#define _LIST_ENTRY WIN_LIST_ENTRY_TAG
#define LIST_ENTRY WIN_LIST_ENTRY
#include <windows.h>
#undef GUID
#undef _LIST_ENTRY
#undef LIST_ENTRY
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <stdarg.h>
#undef TRUE
#undef FALSE
#undef NULL
#include "windows-ram-gop.h"
static int exists,fail_install,allocations,frees,installs;
static EFI_STATUS EFIAPI locate(EFI_GUID *g,VOID *r,VOID **p) {
  (void)r;assert(!memcmp(g,&wg_ram_guid,sizeof(*g)));
  if(exists){*p=&wg_ram_gop;return EFI_SUCCESS;}return EFI_NOT_FOUND;
}
static EFI_STATUS EFIAPI pages(EFI_ALLOCATE_TYPE kind,EFI_MEMORY_TYPE type,UINTN n,EFI_PHYSICAL_ADDRESS *p) {
  assert(kind==AllocateMaxAddress && type==EfiReservedMemoryType && n==2250 && *p==0xffffffff);
  VOID *memory=VirtualAlloc((VOID*)(UINTN)0x90000000,WG_RAM_BYTES,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);
  if(!memory)return EFI_OUT_OF_RESOURCES;
  allocations++;*p=(UINTN)memory;return EFI_SUCCESS;
}
static EFI_STATUS EFIAPI free_pages(EFI_PHYSICAL_ADDRESS p,UINTN n) {
  assert(n==2250);assert(VirtualFree((VOID*)(UINTN)p,0,MEM_RELEASE));frees++;return EFI_SUCCESS;
}
static EFI_STATUS EFIAPI pool(EFI_MEMORY_TYPE t,UINTN n,VOID **p) {
  assert(t==EfiBootServicesData);*p=malloc(n);return *p?EFI_SUCCESS:EFI_OUT_OF_RESOURCES;
}
static VOID EFIAPI zero(VOID *p,UINTN n,UINT8 v){memset(p,v,n);}
static EFI_STATUS EFIAPI install(EFI_HANDLE *h,...) {
  assert(!*h);
  va_list args;va_start(args,h);
  assert(va_arg(args,EFI_GUID*)==&wg_ram_path_guid);
  assert(va_arg(args,VOID*)==&wg_ram_path);
  assert(va_arg(args,EFI_GUID*)==&wg_ram_guid);
  assert(va_arg(args,VOID*)==&wg_ram_gop);
  assert(va_arg(args,VOID*)==NULL);va_end(args);
  assert(sizeof(wg_ram_path)==32);
  assert(wg_ram_path.vendor.Header.Type==HARDWARE_DEVICE_PATH);
  assert(wg_ram_path.display.Header.SubType==ACPI_ADR_DP);
  assert(wg_ram_path.end.Type==END_DEVICE_PATH_TYPE);
  installs++;
  if(fail_install)return EFI_DEVICE_ERROR;
  *h=(EFI_HANDLE)(UINTN)0x5678;return EFI_SUCCESS;
}
int main(void) {
  EFI_BOOT_SERVICES bs={0};bs.LocateProtocol=locate;bs.AllocatePages=pages;
  bs.FreePages=free_pages;bs.AllocatePool=pool;bs.SetMem=zero;bs.InstallMultipleProtocolInterfaces=install;
  EFI_SYSTEM_TABLE st={0};st.BootServices=&bs;st.ConsoleOutHandle=(EFI_HANDLE)(UINTN)0x1234;
  assert(wg_ram_gop_init(NULL)==EFI_INVALID_PARAMETER);
  exists=1;assert(wg_ram_gop_init(&st)==EFI_ALREADY_STARTED && !allocations && !installs);
  exists=0;fail_install=1;assert(wg_ram_gop_init(&st)==EFI_DEVICE_ERROR && frees==1 && !wg_ram_mode.FrameBufferBase);
  fail_install=0;assert(wg_ram_gop_init(&st)==EFI_SUCCESS);
  assert(wg_ram_handle==(EFI_HANDLE)(UINTN)0x5678 &&
      st.ConsoleOutHandle==(EFI_HANDLE)(UINTN)0x1234);
  assert(!wg_ram_queries && !wg_ram_sets && !wg_ram_blts);
  assert(wg_ram_mode.FrameBufferSize==9216000 && wg_ram_mode.MaxMode==1);
  EFI_GRAPHICS_OUTPUT_MODE_INFORMATION *info=NULL;UINTN size=0;
  assert(wg_ram_query(&wg_ram_gop,1,&size,&info)==EFI_INVALID_PARAMETER);
  assert(wg_ram_query(&wg_ram_gop,0,&size,&info)==EFI_SUCCESS);
  assert(info->HorizontalResolution==1920 && info->VerticalResolution==1200 &&
      info->PixelFormat==PixelBlueGreenRedReserved8BitPerColor);free(info);
  assert(wg_ram_set(&wg_ram_gop,1)==EFI_UNSUPPORTED);
  EFI_GRAPHICS_OUTPUT_BLT_PIXEL pixel={0x11,0x22,0x33,0},readback[2]={{0}};
  assert(wg_ram_blt(&wg_ram_gop,&pixel,EfiBltVideoFill,0,0,1,1,2,2,0)==EFI_SUCCESS);
  EFI_GRAPHICS_OUTPUT_BLT_PIXEL *fb=(VOID*)(UINTN)wg_ram_mode.FrameBufferBase;
  assert(!fb[0].Blue && fb[1921].Blue==0x11 && fb[1921].Red==0x33 && !fb[1923].Blue);
  assert(wg_ram_blt(&wg_ram_gop,readback,EfiBltVideoToBltBuffer,1,1,0,0,2,1,0)==EFI_SUCCESS);
  assert(!memcmp(readback,&fb[1921],sizeof(readback)));
  assert(wg_ram_blt(&wg_ram_gop,NULL,EfiBltVideoToVideo,1,1,2,1,2,1,0)==EFI_SUCCESS);
  assert(fb[1923].Red==0x33);
  assert(wg_ram_blt(&wg_ram_gop,&pixel,EfiBltVideoFill,0,0,1919,1199,2,1,0)==EFI_INVALID_PARAMETER);
  assert(wg_ram_blt(&wg_ram_gop,NULL,EfiBltVideoFill,0,0,0,0,1,1,0)==EFI_INVALID_PARAMETER);
  assert(wg_ram_blt(&wg_ram_gop,&pixel,(EFI_GRAPHICS_OUTPUT_BLT_OPERATION)-1,0,0,0,0,1,1,0)==EFI_INVALID_PARAMETER);
  assert(wg_ram_blt(&wg_ram_gop,&pixel,EfiBltBufferToVideo,0,0,0,0,1,1,3)==EFI_INVALID_PARAMETER);
  assert(wg_ram_blt(&wg_ram_gop,&pixel,EfiBltVideoFill,0,0,~(UINTN)0,0,1,1,0)==EFI_INVALID_PARAMETER);
  assert(wg_ram_gop_init(&st)==EFI_INVALID_PARAMETER); /* No second allocation. */
  free_pages(wg_ram_mode.FrameBufferBase,2250);
  assert(allocations==frees && installs==2);
  assert(wg_ram_queries==2 && wg_ram_sets==1 && wg_ram_blts==8);
  puts("OFFLINE_RAM_GOP_BOUNDS_LAYOUT_REFUSAL_ROLLBACK=PASS; NOT_WINDOWS_RUNTIME_PASS");
  return 0;
}
