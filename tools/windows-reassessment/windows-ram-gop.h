/* Offline Windows-only RAM framebuffer candidate. Not enabled in the launcher.
 * Standard UEFI GOP, reserved guest RAM, no host GPU/ACPI/CPU/security changes.
 * Never replace an existing GOP. The caller must retain the successful SMP
 * contract and validate Windows' actual framebuffer handoff before shipping. */
#ifndef UAVF_WINDOWS_RAM_GOP_H
#define UAVF_WINDOWS_RAM_GOP_H
#include <Uefi.h>
#include <Protocol/GraphicsOutput.h>
#include <Protocol/DevicePath.h>

#define WG_RAM_WIDTH 1920u
#define WG_RAM_HEIGHT 1200u
#define WG_RAM_BYTES ((UINTN)WG_RAM_WIDTH*WG_RAM_HEIGHT*4)
static EFI_SYSTEM_TABLE *wg_ram_system;
static EFI_GUID wg_ram_guid=EFI_GRAPHICS_OUTPUT_PROTOCOL_GUID;
static EFI_GUID wg_ram_path_guid=EFI_DEVICE_PATH_PROTOCOL_GUID;
/* Own display handle with standard vendor/display path; never mutate the
 * serial console handle, SystemTable pointers or ACPI tables. */
static struct {
  VENDOR_DEVICE_PATH vendor;
  ACPI_ADR_DEVICE_PATH display;
  EFI_DEVICE_PATH_PROTOCOL end;
} wg_ram_path={
  {{HARDWARE_DEVICE_PATH,HW_VENDOR_DP,{sizeof(VENDOR_DEVICE_PATH),0}},
   {0x608a4b92,0x8013,0x4f9c,{0xa1,0x73,0x2d,0xe7,0x6b,0x19,0x33,0x08}}},
  {{ACPI_DEVICE_PATH,ACPI_ADR_DP,{sizeof(ACPI_ADR_DEVICE_PATH),0}},
   ACPI_DISPLAY_ADR(1,0,0,1,0,ACPI_ADR_DISPLAY_TYPE_EXTERNAL_DIGITAL,0,0)},
  {END_DEVICE_PATH_TYPE,END_ENTIRE_DEVICE_PATH_SUBTYPE,{sizeof(EFI_DEVICE_PATH_PROTOCOL),0}}
};
static EFI_HANDLE wg_ram_handle;
static volatile UINT64 wg_ram_queries,wg_ram_sets,wg_ram_blts;
static EFI_GRAPHICS_OUTPUT_MODE_INFORMATION wg_ram_info={
  0,WG_RAM_WIDTH,WG_RAM_HEIGHT,PixelBlueGreenRedReserved8BitPerColor,{0,0,0,0},WG_RAM_WIDTH
};
static EFI_GRAPHICS_OUTPUT_PROTOCOL_MODE wg_ram_mode={
  1,0,&wg_ram_info,sizeof(wg_ram_info),0,WG_RAM_BYTES
};
static int wg_ram_rect(UINTN x,UINTN y,UINTN width,UINTN height) {
  return width && height && x<WG_RAM_WIDTH && y<WG_RAM_HEIGHT &&
    width<=WG_RAM_WIDTH-x && height<=WG_RAM_HEIGHT-y;
}
static EFI_STATUS EFIAPI wg_ram_query(EFI_GRAPHICS_OUTPUT_PROTOCOL *gop,
    UINT32 mode,UINTN *size,EFI_GRAPHICS_OUTPUT_MODE_INFORMATION **info) {
  wg_ram_queries++;
  if(!gop || mode || !size || !info || !wg_ram_system)return EFI_INVALID_PARAMETER;
  EFI_STATUS status=wg_ram_system->BootServices->AllocatePool(EfiBootServicesData,
      sizeof(wg_ram_info),(VOID**)info);
  if(EFI_ERROR(status))return status;
  **info=wg_ram_info;*size=sizeof(wg_ram_info);return EFI_SUCCESS;
}
static EFI_STATUS EFIAPI wg_ram_set(EFI_GRAPHICS_OUTPUT_PROTOCOL *gop,UINT32 mode) {
  wg_ram_sets++;
  if(!gop || mode || !wg_ram_mode.FrameBufferBase)return EFI_UNSUPPORTED;
  wg_ram_system->BootServices->SetMem((VOID*)(UINTN)wg_ram_mode.FrameBufferBase,
      WG_RAM_BYTES,0);
  wg_ram_mode.Mode=0;return EFI_SUCCESS;
}
static EFI_STATUS EFIAPI wg_ram_blt(EFI_GRAPHICS_OUTPUT_PROTOCOL *gop,
    EFI_GRAPHICS_OUTPUT_BLT_PIXEL *buffer,EFI_GRAPHICS_OUTPUT_BLT_OPERATION op,
    UINTN sx,UINTN sy,UINTN dx,UINTN dy,UINTN width,UINTN height,UINTN delta) {
  wg_ram_blts++;
  if(!gop || !wg_ram_mode.FrameBufferBase || !width || !height ||
      width>WG_RAM_WIDTH || height>WG_RAM_HEIGHT || (UINTN)op>=EfiGraphicsOutputBltOperationMax)
    return EFI_INVALID_PARAMETER;
  if(op!=EfiBltVideoToVideo && !buffer)return EFI_INVALID_PARAMETER;
  if(op==EfiBltVideoToBltBuffer || op==EfiBltVideoToVideo) {
    if(!wg_ram_rect(sx,sy,width,height))return EFI_INVALID_PARAMETER;
  }
  if(op!=EfiBltVideoToBltBuffer && !wg_ram_rect(dx,dy,width,height))
    return EFI_INVALID_PARAMETER;
  UINTN stride=delta?delta/4:width;
  if(op==EfiBltBufferToVideo || op==EfiBltVideoToBltBuffer) {
    UINTN bx=op==EfiBltBufferToVideo?sx:dx,by=op==EfiBltBufferToVideo?sy:dy;
    /* GOP specifies caller-owned buffer capacity; reject malformed strides
     * and integer overflow before forming any buffer pointer. */
    if((delta&3) || !stride || bx>stride || width>stride-bx ||
        by>~(UINTN)0-height || by+height>~(UINTN)0/4/stride)
      return EFI_INVALID_PARAMETER;
  }
  EFI_GRAPHICS_OUTPUT_BLT_PIXEL *fb=(VOID*)(UINTN)wg_ram_mode.FrameBufferBase;
  int reverse=op==EfiBltVideoToVideo && (dy>sy || (dy==sy && dx>sx));
  for(UINTN row=0;row<height;row++)for(UINTN col=0;col<width;col++) {
    UINTN y=reverse?height-1-row:row,x=reverse?width-1-col:col;
    if(op==EfiBltVideoFill)fb[(dy+y)*WG_RAM_WIDTH+dx+x]=buffer[0];
    else if(op==EfiBltBufferToVideo)fb[(dy+y)*WG_RAM_WIDTH+dx+x]=buffer[(sy+y)*stride+sx+x];
    else if(op==EfiBltVideoToBltBuffer)buffer[(dy+y)*stride+dx+x]=fb[(sy+y)*WG_RAM_WIDTH+sx+x];
    else fb[(dy+y)*WG_RAM_WIDTH+dx+x]=fb[(sy+y)*WG_RAM_WIDTH+sx+x];
  }
  return EFI_SUCCESS;
}
static EFI_GRAPHICS_OUTPUT_PROTOCOL wg_ram_gop={wg_ram_query,wg_ram_set,wg_ram_blt,&wg_ram_mode};
static EFI_STATUS wg_ram_gop_init(EFI_SYSTEM_TABLE *system) {
  if(!system || !system->BootServices || !system->ConsoleOutHandle || wg_ram_mode.FrameBufferBase)
    return EFI_INVALID_PARAMETER;
  EFI_GRAPHICS_OUTPUT_PROTOCOL *existing=NULL;
  EFI_STATUS status=system->BootServices->LocateProtocol(&wg_ram_guid,NULL,(VOID**)&existing);
  if(!EFI_ERROR(status))return EFI_ALREADY_STARTED; /* Existing display wins. */
  if(status!=EFI_NOT_FOUND)return status;
  EFI_PHYSICAL_ADDRESS address=0xffffffff;
  UINTN pages=EFI_SIZE_TO_PAGES(WG_RAM_BYTES);
  status=system->BootServices->AllocatePages(AllocateMaxAddress,EfiReservedMemoryType,pages,&address);
  if(EFI_ERROR(status))return status;
  if((address&4095) || address<0x80000000 || address>0x100000000ULL-WG_RAM_BYTES) {
    system->BootServices->FreePages(address,pages);return EFI_UNSUPPORTED;
  }
  wg_ram_system=system;wg_ram_mode.FrameBufferBase=address;
  wg_ram_set(&wg_ram_gop,0);
  EFI_HANDLE handle=NULL;
  status=system->BootServices->InstallMultipleProtocolInterfaces(&handle,
      &wg_ram_path_guid,&wg_ram_path,&wg_ram_guid,&wg_ram_gop,NULL);
  if(EFI_ERROR(status)) {
    system->BootServices->FreePages(address,pages);wg_ram_mode.FrameBufferBase=0;wg_ram_system=NULL;
  } else {
    wg_ram_handle=handle;
    /* Exclude our own initialization clear from loader-use evidence. */
    wg_ram_queries=wg_ram_sets=wg_ram_blts=0;
  }
  return status;
}
#endif
