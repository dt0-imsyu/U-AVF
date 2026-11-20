// Own guest EFI allocation only. Never guess a Windows/free physical page.
// CPU_ON deployment remains disabled until allocation/map/runtime proof.
#define PSCI_RESERVED_PAGES ((PSCI_RUNTIME_RESERVE&&!PSCI_RUNTIME_ALL_CPUS)?2:8)
#define PSCI_RESERVED_BYTES (PSCI_RESERVED_PAGES*4096)
static int psci_reservation_in_map(const UINT8 *map,UINTN bytes,UINTN stride,UINT64 page) {
  if(!map || stride<sizeof(EFI_MEMORY_DESCRIPTOR) || bytes%stride || page%4096 ||
     page<0x80000000ULL || page+PSCI_RESERVED_BYTES>0x100000000ULL)return 0;
  for(UINTN at=0;at<bytes;at+=stride) {
    const EFI_MEMORY_DESCRIPTOR *d=(const EFI_MEMORY_DESCRIPTOR*)(map+at);
    if(d->Type==(PSCI_RUNTIME_RESERVE?EfiRuntimeServicesCode:EfiACPIMemoryNVS) &&
       (!PSCI_RUNTIME_RESERVE || (d->Attribute&EFI_MEMORY_RUNTIME)) && d->NumberOfPages<=0x100000 &&
       d->PhysicalStart<=page && page+PSCI_RESERVED_BYTES<=d->PhysicalStart+d->NumberOfPages*4096 &&
       !(d->Attribute&(EFI_MEMORY_XP|EFI_MEMORY_RP|EFI_MEMORY_RO)))return 1;
  }
  return 0;
}
static void psci_initialize_reservation(UINT8 *pages,UINT64 physical) {
  for(UINTN i=0;i<PSCI_RESERVED_BYTES;i++)pages[i]=0;
  copy(pages,"UAVFPSCI",8);UINT64 header[]={PSCI_RUNTIME_ENTRY_MARKER?2:1,physical,PSCI_RESERVED_PAGES,4096};copy(pages+8,header,sizeof(header));
  for(UINTN cpu=1;cpu<PSCI_RESERVED_PAGES;cpu++) {
    // ldr x0,+16; ldr x16,+24; br x16; nop. Original context/entry literals
    // must be set/read back by the stopped observer before redirecting CPU_ON.
    if(PSCI_RUNTIME_ENTRY_MARKER) {
      // Only x0/x16 touched, exactly as the original trampoline's final ABI.
      // Store1 in our OWN aligned marker, barrier, restore original x0/entry.
      // No UART, Windows writes, exceptions, MMU/register-feature changes.
      UINT32 words[]={0x52800020,0x10000170,0xf9000200,0xd5033f9f,
                     0x58000080,0x580000b0,0xd61f0200,0xd503201f,
                     0,0,0,0,0,0,0xd503205f,0x17ffffff};
      UINT64 safe_wait=physical+cpu*4096+56;
      copy(pages+cpu*4096,words,sizeof(words));copy(pages+cpu*4096+40,&safe_wait,8);
    } else {
      UINT32 words[]={0x58000080,0x580000b0,0xd61f0200,0xd503201f,0,0,0,0,0xd503205f,0x17ffffff};
      UINT64 safe_wait=physical+cpu*4096+32;
      copy(pages+cpu*4096,words,sizeof(words));copy(pages+cpu*4096+24,&safe_wait,8);
    }
  }
  // First-CPU-only proof avoids debugger writes to NVS: CPU_ON context carries
  // the original entry PA in x0; this stub restores the observed Windows x0.
  // Admission MUST require original context exactly0x8000c000 and affinity1.
#if !PSCI_RUNTIME_RESERVE
  UINT32 first[]={0xaa0003f0,0x52980000,0x72b00000,0xd61f0200};
  copy(pages+4096,first,sizeof(first));
#endif
}
#ifndef GAS_TEST
static int reserve_psci_entry_pages(void) {
  UINT64 mpidr;__asm__ volatile("mrs %0,mpidr_el1":"=r"(mpidr));
  if((mpidr&0xff00ffffffULL)!=0)return 0;
  for(UINTN i=0;i<8;i++) {
    UINT64 typer=*(volatile UINT64*)(UINTN)(0x3fef0000+i*0x20000+8);
    if((typer>>32)!=i || !!(typer&16)!=(i==7) || (typer&2))return 0;
  }
  EFI_PHYSICAL_ADDRESS page=0xffffffffULL;
  EFI_BOOT_SERVICES *bs=st->BootServices;
  if(EFI_ERROR(bs->AllocatePages(AllocateMaxAddress,PSCI_RUNTIME_RESERVE?EfiRuntimeServicesCode:EfiACPIMemoryNVS,PSCI_RESERVED_PAGES,&page)))return 0;
  if(page<0x80000000ULL || page+PSCI_RESERVED_BYTES>0x100000000ULL){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  EFI_GUID guid=EFI_MEMORY_ATTRIBUTE_PROTOCOL_GUID;EFI_MEMORY_ATTRIBUTE_PROTOCOL *attr=0;
  UINT64 attributes=0;
  if(EFI_ERROR(bs->LocateProtocol(&guid,0,(void**)&attr)) ||
     EFI_ERROR(attr->ClearMemoryAttributes(attr,page,PSCI_RESERVED_BYTES,PSCI_RUNTIME_RESERVE?(EFI_MEMORY_XP|EFI_MEMORY_RO):EFI_MEMORY_XP)) ||
     EFI_ERROR(attr->GetMemoryAttributes(attr,page,PSCI_RESERVED_BYTES,&attributes)) ||
     (attributes&(EFI_MEMORY_XP|EFI_MEMORY_RP|EFI_MEMORY_RO))){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  psci_initialize_reservation((UINT8*)(UINTN)page,page);
#if PSCI_RUNTIME_RESERVE
  extern const UINT8 psci_callback_begin[],psci_callback_end[];
  UINTN callback_bytes=psci_callback_end-psci_callback_begin;
  if(callback_bytes!=68){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  copy((UINT8*)(UINTN)(page+256),psci_callback_begin,callback_bytes);
  *(UINT64*)(UINTN)(page+48)=(UINTN)st->RuntimeServices->ConvertPointer;
#if PSCI_RUNTIME_CACHE_PUBLISH
  extern const UINT8 psci_publish_begin[],psci_publish_end[];
  if(psci_publish_end-psci_publish_begin!=40){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  copy((UINT8*)(UINTN)(page+512),psci_publish_begin,40);
#endif
#endif
  for(UINTN at=0;at<PSCI_RESERVED_BYTES;at+=64) {
    UINTN address=(UINTN)page+at;
#if PSCI_RUNTIME_CACHE_PUBLISH
    // AP reset may read with caches/MMU off: PoU is not sufficient publication.
    __asm__ volatile("dc cvac,%0\n dsb sy\n ic ivau,%0"::"r"(address):"memory");
#else
    __asm__ volatile("dc cvau,%0\n dsb ish\n ic ivau,%0"::"r"(address):"memory");
#endif
  }
  __asm__ volatile("dsb ish\n isb":::"memory");
  UINTN bytes=0,key=0,stride=0;UINT32 version=0;EFI_MEMORY_DESCRIPTOR *map=0;
  EFI_STATUS status=bs->GetMemoryMap(&bytes,map,&key,&stride,&version);
  if(status!=EFI_BUFFER_TOO_SMALL || stride<sizeof(*map) || bytes>0x100000){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  bytes+=stride*16;
  if(EFI_ERROR(bs->AllocatePool(EfiLoaderData,bytes,(void**)&map))){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  status=bs->GetMemoryMap(&bytes,map,&key,&stride,&version);
  int valid=!EFI_ERROR(status)&&psci_reservation_in_map((UINT8*)map,bytes,stride,page);
  bs->FreePool(map);
  if(!valid){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
#if PSCI_RUNTIME_RESERVE
  EFI_EVENT virtual_event=0;
  if(EFI_ERROR(bs->CreateEvent(EVT_SIGNAL_VIRTUAL_ADDRESS_CHANGE,TPL_NOTIFY,
       (EFI_EVENT_NOTIFY)(UINTN)(page+256),(void*)(UINTN)page,&virtual_event))){bs->FreePages(page,PSCI_RESERVED_PAGES);return 0;}
  out("WG:PSCI_RT_PAGES=");hex(page);out("WG:PSCI_RT_MAP=PASS\r\n");
#else
  out("WG:PSCI_OWNED_NVS_PAGES=");hex(page);
  out("WG:PSCI_OWNED_NVS_MAP=PASS;NO_REDIRECT\r\n");
#endif
  return 1;
}
#endif
