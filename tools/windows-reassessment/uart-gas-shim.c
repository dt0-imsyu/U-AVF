// Isolated Windows diagnostic. No firmware build, CPU changes or Windows patch.
#include <Uefi.h>
#include <Protocol/LoadedImage.h>
#include <Protocol/SimpleFileSystem.h>
#include <Protocol/DevicePath.h>
#include <Guid/Acpi.h>
#include <Guid/FileInfo.h>
#include <Protocol/MemoryAttribute.h>
#ifndef WINDOWS_RAM_GOP
#define WINDOWS_RAM_GOP 0
#endif
#if WINDOWS_RAM_GOP
#include "windows-ram-gop.h"
#endif
#ifndef WINDOWS_CAM
#define WINDOWS_CAM 0
#endif
#if WINDOWS_CAM
#include <Protocol/AcpiTable.h>
extern const UINT8 cam_ssdt_begin[],cam_ssdt_end[];
#ifdef WINDOWS_WDDM_PNP
extern const UINT8 wddm_ssdt_begin[],wddm_ssdt_end[];
#endif
#endif
#ifndef GAS8
#define GAS8 0
#endif
#ifndef NO_RAW_DIAG
#define NO_RAW_DIAG 0
#endif
#ifndef GIC_AUDIT
#define GIC_AUDIT 0
#endif
#ifndef GIC_DS_PROBE
#define GIC_DS_PROBE 0
#endif
#ifndef GIC_DS_BOOT
#define GIC_DS_BOOT 0
#endif
#ifndef GIC_DS_EBS
#define GIC_DS_EBS 0
#endif
#ifndef GIC_DYNAMIC
#define GIC_DYNAMIC 0
#endif
#ifndef PSCI_ENTRY_RESERVE
#define PSCI_ENTRY_RESERVE 0
#endif
#ifndef PSCI_RUNTIME_RESERVE
#define PSCI_RUNTIME_RESERVE 0
#endif
#ifndef PSCI_RUNTIME_ALL_CPUS
#define PSCI_RUNTIME_ALL_CPUS 0
#endif
#ifndef PSCI_RUNTIME_ENTRY_MARKER
#define PSCI_RUNTIME_ENTRY_MARKER 0
#endif
#ifndef PSCI_RUNTIME_CACHE_PUBLISH
#define PSCI_RUNTIME_CACHE_PUBLISH 0
#endif
#ifndef GIC_FIXED_EIGHT
#define GIC_FIXED_EIGHT 0
#endif
#if PSCI_ENTRY_RESERVE && (!GIC_FIXED_EIGHT || GIC_DYNAMIC)
#error PSCI reserve requires isolated fixed eight CPU GIC profile
#endif
#if GIC_FIXED_EIGHT
#define LEGACY_GICR_BASE 0x3fef0000
#else
#define LEGACY_GICR_BASE 0x3ffd0000
#endif
static EFI_GUID loaded_guid = EFI_LOADED_IMAGE_PROTOCOL_GUID;
static EFI_GUID fs_guid = EFI_SIMPLE_FILE_SYSTEM_PROTOCOL_GUID;
static EFI_GUID dp_guid = EFI_DEVICE_PATH_PROTOCOL_GUID;
static EFI_GUID acpi_guid = EFI_ACPI_20_TABLE_GUID;
static EFI_GUID info_guid = EFI_FILE_INFO_ID;
static EFI_SYSTEM_TABLE *st;
static void out(const char *s) {
#ifdef GAS_TEST
  (void)s;
#else
  volatile UINT8 *uart = (volatile UINT8 *)(UINTN)0x3f8;
  while (*s) { UINTN n=1000000; while (!(uart[5]&0x20) && --n) {} uart[0]=(UINT8)*s++; }
#endif
}
static UINT32 u32(const UINT8 *p) { return p[0]|((UINT32)p[1]<<8)|((UINT32)p[2]<<16)|((UINT32)p[3]<<24); }
static UINT16 u16(const UINT8 *p) { return p[0]|((UINT16)p[1]<<8); }
static UINT64 u64(const UINT8 *p) { return u32(p)|((UINT64)u32(p+4)<<32); }
static int same(const void *a,const void *b,UINTN n) { const UINT8 *x=a,*y=b; while(n--) if(*x++!=*y++) return 0; return 1; }
static void copy(void *a,const void *b,UINTN n) { UINT8 *x=a; const UINT8 *y=b; while(n--) *x++=*y++; }
static int checksum(UINT8 *p,UINT32 n) { UINT8 s=0; while(n--) s+=*p++; return s==0; }
#if PSCI_ENTRY_RESERVE
static void hex(UINT64 v);
#include "psci-entry-reserve.h"
#endif
#if GIC_DYNAMIC
#include "windows-gic-layout.h"
#ifndef GAS_TEST
static int read_gic_typer(UINT64 address,UINT64 *value,void *context) {
  (void)context;*value=*(volatile UINT64*)(UINTN)address;return 1;
}
static int current_gic_layout(UINT64 mpidr,WG_GIC_LAYOUT *layout) {
  UINT8 *rsdp=0;
  for(UINTN i=0;i<st->NumberOfTableEntries;i++)
    if(same(&st->ConfigurationTable[i].VendorGuid,&acpi_guid,16)) {
      if(rsdp)return 0;rsdp=st->ConfigurationTable[i].VendorTable;
    }
  if(!rsdp||!same(rsdp,"RSD PTR ",8)||!checksum(rsdp,20)||rsdp[15]<2)return 0;
  UINT8 *xsdt=(void*)(UINTN)u64(rsdp+24);
  if(!xsdt||!same(xsdt,"XSDT",4))return 0;
  UINT32 n=u32(xsdt+4);
  if(n<36||n>4096||(n-36)%8||!checksum(xsdt,n))return 0;
  UINT8 *madt=0;
  for(UINT32 i=36;i<n;i+=8) {
    UINT8 *table=(void*)(UINTN)u64(xsdt+i);
    if(table&&same(table,"APIC",4)){if(madt)return 0;madt=table;}
  }
  return madt&&wg_gic_layout(madt,u32(madt+4),mpidr,read_gic_typer,0,layout);
}
#endif
#endif
static void fixsum(UINT8 *p,UINT32 n) { p[9]=0; UINT8 s=0; for(UINT32 i=0;i<n;i++)s+=p[i];p[9]=(UINT8)-s; }
#if WINDOWS_CAM
static int install_cam_tables(void) {
  EFI_GUID guid=EFI_ACPI_TABLE_PROTOCOL_GUID; EFI_ACPI_TABLE_PROTOCOL *acpi=0;
  EFI_STATUS status=st->BootServices->LocateProtocol(&guid,0,(void**)&acpi);
  if(EFI_ERROR(status)||!acpi)return 0;
  // Bound the experiment to the retained absent-MCFG platform contract.
  UINT8 *rsdp=0;
  for(UINTN i=0;i<st->NumberOfTableEntries;i++)
    if(same(&st->ConfigurationTable[i].VendorGuid,&acpi_guid,16))rsdp=st->ConfigurationTable[i].VendorTable;
  if(!rsdp)return 0;
  UINT8 *xsdt=(UINT8*)(UINTN)u64(rsdp+24);
  UINT32 length=u32(xsdt+4);
  if(length<36||length>4096||!checksum(xsdt,length))return 0;
  for(UINT32 i=36;i<length;i+=8) {
    UINT8 *table=(UINT8*)(UINTN)u64(xsdt+i);
    if(table&&same(table,"MCFG",4))return 0;
  }
  UINTN aml_size=(UINTN)(cam_ssdt_end-cam_ssdt_begin),key=0;
  if(aml_size!=298||!same(cam_ssdt_begin,"SSDT",4)||!checksum((UINT8*)cam_ssdt_begin,(UINT32)aml_size))return 0;
  UINT8 mcfg[60]={0};copy(mcfg,"MCFG",4);mcfg[4]=60;mcfg[8]=1;
  copy(mcfg+10,"UAVF  ",6);copy(mcfg+16,"WCAM0001",8);mcfg[24]=1;
  copy(mcfg+28,"UAVF",4);mcfg[32]=1;mcfg[47]=0x2e;fixsum(mcfg,60);
  status=acpi->InstallAcpiTable(acpi,mcfg,60,&key);if(EFI_ERROR(status))return 0;
  status=acpi->InstallAcpiTable(acpi,(void*)cam_ssdt_begin,aml_size,&key);if(EFI_ERROR(status))return 0;
#ifdef WINDOWS_WDDM_PNP
  UINTN wddm_size=(UINTN)(wddm_ssdt_end-wddm_ssdt_begin);
  if(wddm_size<36||wddm_size>4096||!same(wddm_ssdt_begin,"SSDT",4)||
     u32(wddm_ssdt_begin+4)!=wddm_size||!same(wddm_ssdt_begin+16,"MAVKGPU0",8)||
     !checksum((UINT8*)wddm_ssdt_begin,(UINT32)wddm_size)){
    out("WG:WDDM_PNP_SSDT=INVALID\r\n");return 0;
  }
  status=acpi->InstallAcpiTable(acpi,(void*)wddm_ssdt_begin,wddm_size,&key);
  if(EFI_ERROR(status)){out("WG:WDDM_PNP_SSDT=INSTALL_FAILED\r\n");return 0;}
  out("WG:WDDM_PNP_SSDT=INSTALLED\r\n");
#endif
  if(!PSCI_RUNTIME_RESERVE)out("WG:WINDOWS_CAM_ADAPTER=HAL_SHIFT_GUARD_REQUIRED\r\n");return 1;
}
#endif
#if GIC_DS_EBS && !defined(GAS_TEST)
#if WINDOWS_RAM_GOP
static void hex(UINT64 v);
static void audit_ram_gop_handoff(void) {
  /* Read-only at EBS: no allocations, BootServices calls or framebuffer writes.
   * Counts alone do not prove selection: Windows can read Mode directly. */
  out("WG:EBS_GOP_QUERY_CALLS=");hex(wg_ram_queries);
  out("WG:EBS_GOP_SET_CALLS=");hex(wg_ram_sets);
  out("WG:EBS_GOP_BLT_CALLS=");hex(wg_ram_blts);
  out("WG:EBS_GOP_BASE=");hex(wg_ram_mode.FrameBufferBase);
  out("WG:EBS_GOP_MODE=");hex(wg_ram_mode.Mode);
  out("WG:EBS_GOP_WIDTH=");hex(wg_ram_mode.Info->HorizontalResolution);
  out("WG:EBS_GOP_HEIGHT=");hex(wg_ram_mode.Info->VerticalResolution);
  UINT64 nonzero=0;
  volatile UINT32 *fb=(volatile UINT32*)(UINTN)wg_ram_mode.FrameBufferBase;
  if(fb)for(UINTN row=0;row<WG_RAM_HEIGHT;row+=75)
    for(UINTN x=0;x<WG_RAM_WIDTH;x+=120)if(fb[row*WG_RAM_WIDTH+x])nonzero++;
  out("WG:EBS_GOP_SAMPLED_NONZERO=");hex(nonzero);
}
#endif
static void EFIAPI restore_ds_after_gic_shutdown(EFI_EVENT event,void *context) {
  (void)event;(void)context;
#if WINDOWS_RAM_GOP
  audit_ram_gop_handoff();
#endif
  volatile UINT32 *dist=(volatile UINT32*)(UINTN)0x3fff0000;
  UINT32 value=dist[0];
  // Lower notify TPL than ArmGic's TPL_NOTIFY: its shutdown has already
  // disabled all sources and delivery. Never re-enable interrupts here.
  if((value&7) || (dist[1]&(1u<<10))){out("WG:EBS_DS=REFUSE_STATE\r\n");return;}
  dist[0]=value|0x40;__asm__ volatile("dsb sy":::"memory");
  out("WG:EBS_GICD_CTLR=");
  // Keep this EBS callback independent of Boot Services and hex() ordering.
  UINT32 after=dist[0];char b[11]="00000000\r\n";
  for(int i=7;i>=0;i--){b[i]="0123456789ABCDEF"[after&15];after>>=4;}out(b);
  out((dist[0]&0x40)?"WG:EBS_DS=PASS\r\n":"WG:EBS_DS=FAIL\r\n");
}
#endif
#if NO_RAW_DIAG
static void hex(UINT64 v) {
  char b[19]="0000000000000000\r\n";
  for(int i=15;i>=0;i--){b[i]="0123456789ABCDEF"[v&15];v>>=4;}out(b);
}
// Exact instruction stream reported in the validated KD access-violation
// packet, with the preceding address materialization and complete return/loop.
static UINT8 *raw_match(UINT8 *base,UINTN size,UINTN *matches) {
  UINT8 *found=0;*matches=0;
  static const UINT32 code[]={0xd2820001,0xd2807fa2,0x39400043,0x36280083,
    0xd2807f01,0x39000020,0xd65f03c0,0xf1000421,0x54ffff41,0x17fffffd};
  for(UINTN i=0;i+sizeof(code)<=size;i+=4) {
    if(same(base+i,code,sizeof(code))){found=base+i;(*matches)++;}
  }
  return found;
}
static int disable_raw_diagnostic(void) {
  EFI_GUID bds={0xb601f8c4,0x43b7,0x4784,{0x95,0xb1,0xf4,0x22,0x6c,0xb4,0x0c,0xee}};
  EFI_HANDLE *handles=0;UINTN count=0,matches=0;UINT8 *entry=0;
  EFI_STATUS status=st->BootServices->LocateHandleBuffer(ByProtocol,&loaded_guid,0,&count,&handles);
  if(EFI_ERROR(status))return 0;
  for(UINTN i=0;i<count;i++){
    EFI_LOADED_IMAGE_PROTOCOL *image=0;
    if(EFI_ERROR(st->BootServices->HandleProtocol(handles[i],&loaded_guid,(void**)&image)))continue;
    UINT8 *p=(UINT8*)image->FilePath;UINTN offset=0;int is_bds=0;
    while(p && p[0]!=0x7f && offset<256){
      UINT16 n=u16(p+2);if(n<4 || n>256)break;
      if(p[0]==4 && p[1]==6 && n==20 && same(p+4,&bds,16))is_bds=1;
      offset+=n;p+=n;
    }
    if(image->ImageSize>16*1024*1024)continue;
#if !GIC_DYNAMIC
    UINTN base=(UINTN)image->ImageBase;
    if(base<=0x17f3b00ac && 0x17f3b00ac+64<base+image->ImageSize){
      out("WG:FAULT_CONTAINER_BASE=");hex(base);
      out("WG:FAULT_CONTAINER_SIZE=");hex(image->ImageSize);
      for(UINTN a=0x17f3b00ac-16;a<0x17f3b00ac+64;a+=4){out("WG:FAULT_CODE=");hex(u32((void*)a));}
    }
#endif
    if(!is_bds)continue;
#if !GIC_DYNAMIC
    out("WG:RUNTIME_DXE_BASE=");hex(base);
#endif
    UINTN n=0;UINT8 *e=raw_match(image->ImageBase,image->ImageSize,&n);matches+=n;if(n)entry=e;
  }
  st->BootServices->FreePool(handles);
  if(matches!=1 || !entry){out("WG:RAW_DIAG_MATCH=FAIL\r\n");return 0;}
#if !GIC_DYNAMIC
  out("WG:RAW_DIAG_ENTRY=");hex((UINTN)entry);
#endif
  EFI_GUID attr_guid=EFI_MEMORY_ATTRIBUTE_PROTOCOL_GUID;
  EFI_MEMORY_ATTRIBUTE_PROTOCOL *attr=0;
  UINT64 original=0,after=0;UINTN page=(UINTN)entry&~(UINTN)4095;
  if(EFI_ERROR(st->BootServices->LocateProtocol(&attr_guid,0,(void**)&attr)) ||
     EFI_ERROR(attr->GetMemoryAttributes(attr,page,4096,&original)) ||
     (original&(EFI_MEMORY_RP|EFI_MEMORY_XP)))return 0;
#if !GIC_DYNAMIC && !PSCI_ENTRY_RESERVE
  out("WG:RAW_PAGE_ATTRIBUTES=");hex(original);
#endif
  if((original&EFI_MEMORY_RO) && EFI_ERROR(attr->ClearMemoryAttributes(attr,page,4096,EFI_MEMORY_RO)))return 0;
  // Only the exact bounded void WinAvfAuditPutc in retained r11 RuntimeDxe.
  // Verified offline FFS GUID + entire function, never Windows code,
  // CoreExitBootServices, firmware tables, or an on-disk firmware byte.
  *(volatile UINT32*)entry=0xd65f03c0;
#ifndef GAS_TEST
  __asm__ volatile("dc cvau, %0\n dsb ish\n ic ivau, %0\n dsb ish\n isb"::"r"(entry):"memory");
#endif
  if((original&EFI_MEMORY_RO) && EFI_ERROR(attr->SetMemoryAttributes(attr,page,4096,EFI_MEMORY_RO)))return 0;
  if(EFI_ERROR(attr->GetMemoryAttributes(attr,page,4096,&after)) || after!=original)return 0;
  if(u32(entry)!=0xd65f03c0)return 0;
  out("WG:RAW_DIAG_DISABLED=PASS\r\n");return 1;
}
#endif
static int tables(void) {
  UINT8 *rsdp=0,*xsdt; UINT32 count=0,spcr_count=0;
  for(UINTN i=0;i<st->NumberOfTableEntries;i++)
    if(same(&st->ConfigurationTable[i].VendorGuid,&acpi_guid,16))rsdp=st->ConfigurationTable[i].VendorTable;
  if(!rsdp || !same(rsdp,"RSD PTR ",8) || !checksum(rsdp,20) || rsdp[15]<2) return 0;
  xsdt=(UINT8 *)(UINTN)u64(rsdp+24);
  if(!xsdt || !same(xsdt,"XSDT",4))return 0;
  UINT32 length=u32(xsdt+4);
  if(length<36 || length>4096 || (length-36)%8 || !checksum(xsdt,length))return 0;
  for(UINT32 j=36;j<length;j+=8) {
    UINT8 *t=(UINT8 *)(UINTN)u64(xsdt+j);
    if(t && same(t,"SPCR",4)) {
      UINT32 n=u32(t+4);UINT8 *g=t+40;
      if(n<80 || n>4096 || !checksum(t,n) || g[0]!=0 || g[1]!=32 || g[2]!=0 || g[3]!=1 || u64(g+4)!=0x3f8)return 0;
      if(GAS8){g[1]=8;fixsum(t,n);}
      if(!checksum(t,n))return 0;
      if(!PSCI_RUNTIME_RESERVE)out(GAS8?"WG:SPCR:AFTER=8,CHECKSUM=PASS\r\n":"WG:SPCR:CONTROL=32,CHECKSUM=PASS\r\n");spcr_count++;
    }
    if(!t || !same(t,"DBG2",4))continue;
    UINT32 n=u32(t+4);
    if(n<44 || n>65536 || !checksum(t,n) || u32(t+40)!=1)return 0;
    UINT32 off=u32(t+36);
    if(off<44 || off+22>n)return 0;
    UINT8 *d=t+off; UINT16 len=u16(d+1), gas=u16(d+18);
    if(len<34 || off+len>n || d[3]!=1 || gas<22 || gas+12>len ||
       u16(d+12)!=0x8000 || u16(d+14)!=0x12)return 0;
    UINT8 *g=d+gas;
    if(g[0]!=0 || g[2]!=0 || g[3]!=1 || u64(g+4)!=0x3f8 || g[1]!=32)return 0;
    if(!PSCI_RUNTIME_RESERVE)out("\r\nWG:DBG2:BEFORE=32,ACCESS=1,BASE=3F8\r\n");
    if(GAS8) { g[1]=8; fixsum(t,n); }
    if(!checksum(t,n) || g[1]!=(GAS8?8:32))return 0;
    if(!PSCI_RUNTIME_RESERVE)out(GAS8?"WG:DBG2:AFTER=8,CHECKSUM=PASS\r\n":"WG:DBG2:CONTROL=32,CHECKSUM=PASS\r\n");
    count++;
  }
  return count==1 && spcr_count==1;
}
#ifdef WINDOWS_RTC_UTC
// Isolated clock A/B only. No runtime pointer hooks or counter/IRQ changes.
// Use the existing EFI RTC API and require its readback before Windows starts.
static int correct_invalid_rtc(void) {
  EFI_TIME before={0},after={0},desired=WINDOWS_RTC_UTC;
  EFI_STATUS status=st->RuntimeServices->GetTime(&before,0);
  out("WG:RTC_BEFORE_STATUS=");hex(status);
  if(EFI_ERROR(status))return 0;
  out("WG:RTC_BEFORE_YEAR=");hex(before.Year);
  if(before.Year>=2020 && before.Year<=2100 && before.Month>=1 && before.Month<=12 &&
     before.Day>=1 && before.Day<=31 && before.Hour<24 && before.Minute<60 && before.Second<60){
    out("WG:RTC_ALREADY_PLAUSIBLE=NO_CHANGE\r\n");return 1;
  }
  if(desired.Year<2020 || desired.Year>2100 || desired.TimeZone!=EFI_UNSPECIFIED_TIMEZONE)return 0;
  status=st->RuntimeServices->SetTime(&desired);
  out("WG:RTC_SET_STATUS=");hex(status);
  if(EFI_ERROR(status))return 0;
  status=st->RuntimeServices->GetTime(&after,0);
  out("WG:RTC_AFTER_STATUS=");hex(status);
  if(EFI_ERROR(status))return 0;
  out("WG:RTC_AFTER_YEAR=");hex(after.Year);
  if(after.Year!=desired.Year || after.Month!=desired.Month || after.Day!=desired.Day)return 0;
  out("WG:RTC_INVALID_DATE_CORRECTED=PASS\r\n");return 1;
}
#endif
EFI_STATUS EFIAPI efi_main(EFI_HANDLE image,EFI_SYSTEM_TABLE *system) {
  st=system; EFI_BOOT_SERVICES *bs=st->BootServices;
  EFI_LOADED_IMAGE_PROTOCOL *li=0; EFI_SIMPLE_FILE_SYSTEM_PROTOCOL *fs=0;
  EFI_FILE_PROTOCOL *root=0,*file=0; EFI_STATUS r; EFI_DEVICE_PATH_PROTOCOL *base=0;
  out(GAS8?"\r\nWG:MODE=GAS8\r\n":"\r\nWG:MODE=CONTROL\r\n");
  if(!tables()){out("WG:TABLE=FAIL\r\n");return EFI_ABORTED;}
#if WINDOWS_CAM
  if(!install_cam_tables()){out("WG:CAM_TABLE=FAIL\r\n");return EFI_ABORTED;}
#endif
#if NO_RAW_DIAG
  if(!disable_raw_diagnostic())return EFI_ABORTED;
#endif
#ifdef WINDOWS_RTC_UTC
  if(!correct_invalid_rtc()){out("WG:RTC_CORRECTION=FAIL\r\n");return EFI_ABORTED;}
#endif
#if GIC_AUDIT && !defined(GAS_TEST)
#if GIC_DYNAMIC
  UINT64 mpidr;__asm__ volatile("mrs %0,mpidr_el1":"=r"(mpidr));
  WG_GIC_LAYOUT layout;
  if(!current_gic_layout(mpidr,&layout)){out("WG:GIC_LAYOUT=FAIL\r\n");return EFI_ABORTED;}
  UINTN gicr=(UINTN)layout.boot;
  out("WG:GIC_LAYOUT_CPU_COUNT=");hex(layout.count);
  out("WG:GIC_BOOT_REDISTRIBUTOR=");hex(gicr);
#endif
  // Read only the known r11/one-vCPU GIC ranges. Never read IAR (acknowledges
  // interrupts), write controller state, or change the ACPI description.
#if !PSCI_ENTRY_RESERVE
  out("WG:GICD_CTLR=");hex(*(volatile UINT32*)(UINTN)0x3fff0000);
  out("WG:GICD_TYPER=");hex(*(volatile UINT32*)(UINTN)0x3fff0004);
#if !GIC_DYNAMIC
  out("WG:GICD_PIDR2=");hex(*(volatile UINT32*)(UINTN)0x3fffffe8);
#endif
#if GIC_DYNAMIC
  // Affinity was validated during discovery; compact variant logs its exact
  // selected base/count/MPIDR instead of redundant read-only register dumps.
#else
  out("WG:GICR_TYPER=");hex(*(volatile UINT64*)(UINTN)(LEGACY_GICR_BASE+8));
  out("WG:GICR_WAKER=");hex(*(volatile UINT32*)(UINTN)(LEGACY_GICR_BASE+0x14));
  out("WG:GICR_PIDR2=");hex(*(volatile UINT32*)(UINTN)(LEGACY_GICR_BASE+0xffe8));
  UINT64 mpidr;__asm__ volatile("mrs %0,mpidr_el1":"=r"(mpidr));
#endif
  out("WG:MPIDR_EL1=");hex(mpidr);
#endif
#if !GIC_DYNAMIC && !PSCI_ENTRY_RESERVE
  out("WG:GIC_READ_ONLY_AUDIT=COMPLETE\r\n");
#endif
#if GIC_DS_PROBE
  // Disposable VM only: no security extension is advertised. Refuse any
  // active interrupt, disable group delivery, then test DS and read it back.
  // This never touches the physical host GIC or production VM configuration.
  volatile UINT32 *dist=(volatile UINT32*)(UINTN)0x3fff0000;
  UINT32 original=dist[0],typer=dist[1],lines=((typer&31)+1)*32;
  if(typer&(1u<<10)){out(PSCI_ENTRY_RESERVE?"WG:DS_FAIL\r\n":"WG:DS_PROBE=REFUSE_SECURITY_EXTENSIONS\r\n");return EFI_ABORTED;}
  UINT64 daif;__asm__ volatile("mrs %0,daif\n msr daifset,#3":"=r"(daif)::"memory");
#if GIC_DYNAMIC
  for(UINT32 i=0;i<layout.count;i++)if(*(volatile UINT32*)(UINTN)(layout.frames[i]+0x10300)) {
    __asm__ volatile("msr daif,%0"::"r"(daif):"memory");out("WG:DS_PROBE=REFUSE_ACTIVE_PPI\r\n");return EFI_ABORTED;
  }
#else
  if(*(volatile UINT32*)(UINTN)(LEGACY_GICR_BASE+0x10300)){__asm__ volatile("msr daif,%0"::"r"(daif):"memory");out(PSCI_ENTRY_RESERVE?"WG:DS_FAIL\r\n":"WG:DS_PROBE=REFUSE_ACTIVE_PPI\r\n");return EFI_ABORTED;}
#endif
  for(UINT32 i=1;i<lines/32;i++)if(dist[0x300/4+i]){__asm__ volatile("msr daif,%0"::"r"(daif):"memory");out(PSCI_ENTRY_RESERVE?"WG:DS_FAIL\r\n":"WG:DS_PROBE=REFUSE_ACTIVE_SPI\r\n");return EFI_ABORTED;}
  dist[0]=original&~7u;__asm__ volatile("dsb sy":::"memory");
  UINTN wait=1000000;while((dist[0]&(1u<<31)) && --wait){}
  if(!wait){dist[0]=original;__asm__ volatile("dsb sy\n msr daif,%0"::"r"(daif):"memory");out(PSCI_ENTRY_RESERVE?"WG:DS_FAIL\r\n":"WG:DS_PROBE=RWP_TIMEOUT\r\n");return EFI_ABORTED;}
  dist[0]=(original&~7u)|0x40;__asm__ volatile("dsb sy":::"memory");
  // Runtime-map proof retains all DS guards/restoration and final PASS/FAIL.
  // Omit only redundant dumps to keep the own EFI file in its existing slot.
  UINT32 readback=dist[0];if(!PSCI_RUNTIME_RESERVE){out(PSCI_ENTRY_RESERVE?"WG:DS_READ=":"WG:DS_PROBE_READBACK=");hex(readback);}
  dist[0]=original|(readback&0x40);__asm__ volatile("dsb sy":::"memory");
  __asm__ volatile("msr daif,%0"::"r"(daif):"memory");
  if(!PSCI_RUNTIME_RESERVE){out(PSCI_ENTRY_RESERVE?"WG:DS_RESTORE=":"WG:DS_PROBE_RESTORED_CTLR=");hex(dist[0]);}
  out(PSCI_ENTRY_RESERVE?((readback&0x40)?"WG:DS_OK\r\n":"WG:DS_FAIL\r\n"):((readback&0x40)?"WG:DS_PROBE=WRITABLE\r\n":"WG:DS_PROBE=IGNORED\r\n"));
  if(!(readback&0x40))return EFI_ABORTED;
#endif
#if !GIC_DS_BOOT
  return EFI_ABORTED; // Deliberately no Windows boot in this read-only probe.
#endif
#endif
#if GIC_DS_EBS && !defined(GAS_TEST)
  EFI_EVENT ds_event=0;
  r=bs->CreateEvent(EVT_SIGNAL_EXIT_BOOT_SERVICES,TPL_CALLBACK,
    restore_ds_after_gic_shutdown,0,&ds_event);
  if(EFI_ERROR(r)){out("WG:EBS_DS_EVENT=FAIL\r\n");return r;}
  out("WG:EBS_DS_EVENT=REGISTERED\r\n");
#endif
#if PSCI_ENTRY_RESERVE && !defined(GAS_TEST)
  if(!reserve_psci_entry_pages()){out("WG:PSCI_OWNED_NVS_MAP=FAIL\r\n");return EFI_ABORTED;}
#endif
#if WINDOWS_RAM_GOP && !defined(GAS_TEST)
  // Isolated, opt-in native mode source. Never replace an existing GOP and
  // never modify ACPI/GIC/CPU state or touch an installed Windows file.
  r=wg_ram_gop_init(st);
  out("WG:RAM_GOP_STATUS=");hex(r);
  if(EFI_ERROR(r))return r;
  out("WG:RAM_GOP_1920_1200_BASE=");hex(wg_ram_mode.FrameBufferBase);
  out("WG:RAM_GOP_BYTES=");hex(wg_ram_mode.FrameBufferSize);
#endif
  r=bs->HandleProtocol(image,&loaded_guid,(void**)&li);if(EFI_ERROR(r))return r;
  r=bs->HandleProtocol(li->DeviceHandle,&fs_guid,(void**)&fs);if(EFI_ERROR(r))return r;
  r=fs->OpenVolume(fs,&root);if(EFI_ERROR(r))return r;
  r=root->Open(root,&file,L"\\EFI\\BOOT\\WINORIG.EFI",EFI_FILE_MODE_READ,0);if(EFI_ERROR(r))return r;
  UINTN info_size=0; file->GetInfo(file,&info_guid,&info_size,0);
  EFI_FILE_INFO *info=0; r=bs->AllocatePool(EfiLoaderData,info_size,(void**)&info);if(EFI_ERROR(r))return r;
  r=file->GetInfo(file,&info_guid,&info_size,info);if(EFI_ERROR(r))return r;
  UINTN size=(UINTN)info->FileSize;void *buffer=0;
  if(size<4096 || size>16*1024*1024)return EFI_ABORTED;
  r=bs->AllocatePool(EfiLoaderData,size,&buffer);if(EFI_ERROR(r))return r;
  UINTN got=size;r=file->Read(file,&got,buffer);file->Close(file);root->Close(root);bs->FreePool(info);
  if(EFI_ERROR(r)||got!=size)return EFI_ABORTED;
  r=bs->HandleProtocol(li->DeviceHandle,&dp_guid,(void**)&base);if(EFI_ERROR(r))return r;
  UINTN prefix=0;while(base->Type!=0x7f){UINT16 l=u16(base->Length);if(l<4 || prefix+l>4096)return EFI_ABORTED;prefix+=l;base=(void*)((UINT8*)base+l);}
  // Windows validates its on-disk image: point at the signed original, not shim.
  CHAR16 path[]=L"\\EFI\\BOOT\\WINORIG.EFI";
  UINTN node=4+sizeof(path),total=prefix+node+4;UINT8 *dp=0;
  r=bs->AllocatePool(EfiLoaderData,total,(void**)&dp);if(EFI_ERROR(r))return r;
  void *devpath=0;bs->HandleProtocol(li->DeviceHandle,&dp_guid,&devpath);copy(dp,devpath,prefix);
  dp[prefix]=4;dp[prefix+1]=4;dp[prefix+2]=(UINT8)node;dp[prefix+3]=(UINT8)(node>>8);copy(dp+prefix+4,path,sizeof(path));
  dp[total-4]=0x7f;dp[total-3]=0xff;dp[total-2]=4;dp[total-1]=0;
  EFI_HANDLE child=0;r=bs->LoadImage(FALSE,image,(void*)dp,buffer,size,&child);
  bs->FreePool(buffer);bs->FreePool(dp);
  if(EFI_ERROR(r)){out("WG:LOAD=FAIL\r\n");return r;}
  out("WG:LOAD=PASS,START_WINDOWS\r\n");
  r=bs->StartImage(child,0,0);out("WG:WINDOWS_RETURNED\r\n");return r;
}
