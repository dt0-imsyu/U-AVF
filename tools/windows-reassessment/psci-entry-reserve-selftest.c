#define GAS_TEST 1
#define GIC_FIXED_EIGHT 1
#define PSCI_ENTRY_RESERVE 1
#include "uart-gas-shim.c"
static UINT8 pages[0x8000];
int main(void) {
  EFI_MEMORY_DESCRIPTOR d={0};d.Type=PSCI_RUNTIME_RESERVE?EfiRuntimeServicesCode:EfiACPIMemoryNVS;
  d.Attribute=PSCI_RUNTIME_RESERVE?EFI_MEMORY_RUNTIME:0;d.PhysicalStart=0x90000000;d.NumberOfPages=PSCI_RESERVED_PAGES;
  if(!psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d),d.PhysicalStart))return 1;
  d.Type=EfiConventionalMemory;if(psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d),d.PhysicalStart))return 2;
  d.Type=PSCI_RUNTIME_RESERVE?EfiRuntimeServicesCode:EfiACPIMemoryNVS;d.NumberOfPages=PSCI_RESERVED_PAGES-1;if(psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d),d.PhysicalStart))return 3;
  d.NumberOfPages=PSCI_RESERVED_PAGES;d.Attribute=EFI_MEMORY_XP;if(psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d),d.PhysicalStart))return 4;
  d.Attribute=EFI_MEMORY_RO;if(psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d),d.PhysicalStart))return 5;
  d.Attribute=PSCI_RUNTIME_RESERVE?EFI_MEMORY_RUNTIME:0;if(psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d),d.PhysicalStart+16))return 6;
  if(psci_reservation_in_map((UINT8*)&d,sizeof(d),sizeof(d)-8,d.PhysicalStart))return 7;
  psci_initialize_reservation(pages,d.PhysicalStart);
  if(!same(pages,"UAVFPSCI",8) || u64(pages+16)!=d.PhysicalStart || u64(pages+24)!=PSCI_RESERVED_PAGES)return 8;
#if !PSCI_RUNTIME_RESERVE
  if(u32(pages+4096)!=0xaa0003f0 || u32(pages+4100)!=0x52980000 || u32(pages+4104)!=0x72b00000 || u32(pages+4108)!=0xd61f0200)return 11;
#endif
  for(UINTN cpu=PSCI_RUNTIME_RESERVE?1:2;cpu<PSCI_RESERVED_PAGES;cpu++) {
    UINT8 *p=pages+cpu*4096;
#if PSCI_RUNTIME_ENTRY_MARKER
    if(u32(p)!=0x52800020 || u32(p+4)!=0x10000170 || u32(p+8)!=0xf9000200 ||
       u32(p+12)!=0xd5033f9f || u32(p+16)!=0x58000080 || u32(p+20)!=0x580000b0 ||
       u32(p+24)!=0xd61f0200 || u64(p+32)!=0 || u64(p+40)!=d.PhysicalStart+cpu*4096+56 ||
       u64(p+48)!=0 || u32(p+56)!=0xd503205f || u32(p+60)!=0x17ffffff)return 12;
    for(UINTN i=64;i<4096;i++)if(p[i])return 13;
#else
    if(u32(p)!=0x58000080 || u32(p+4)!=0x580000b0 || u32(p+8)!=0xd61f0200 ||
       u64(p+16)!=0 || u64(p+24)!=d.PhysicalStart+cpu*4096+32 || u32(p+32)!=0xd503205f)return 9;
    for(UINTN i=40;i<4096;i++)if(p[i])return 10;
#endif
  }
  return 0;
}
