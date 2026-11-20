// Isolated crosvm GICv3 discovery. Pure parser; caller supplies read-only MMIO.
// Never infer boot CPU from entry order, processor number or a fixed address.
#define WG_MAX_CPUS 8
typedef struct {
  UINT64 boot,frames[WG_MAX_CPUS];
  UINT32 count;
} WG_GIC_LAYOUT;
typedef int (*WG_READ_TYPER)(UINT64,UINT64*,void*);
static UINT32 wg_affinity(UINT64 mpidr) {
  return (UINT32)(mpidr&0xffffff)|((UINT32)(mpidr>>8)&0xff000000);
}
static int wg_gic_layout(const UINT8 *madt,UINT32 bytes,UINT64 mpidr,
                        WG_READ_TYPER read_typer,void *context,WG_GIC_LAYOUT *layout) {
  UINT32 affinities[WG_MAX_CPUS],enabled=0,dist_count=0,range_count=0;
  UINT64 base=0;UINT32 range=0;
  layout->boot=0;layout->count=0;
  if(bytes<44||bytes>65536||!same(madt,"APIC",4)||u32(madt+4)!=bytes||
     !checksum((UINT8*)madt,bytes))return 0;
  for(UINT32 pos=44;pos<bytes;) {
    if(bytes-pos<2)return 0;
    const UINT8 *entry=madt+pos;UINT32 size=entry[1];
    if(size<2||size>bytes-pos)return 0;
    if(entry[0]==11) {
      if(size<80)return 0;
      if(u32(entry+12)&1) {
        if(enabled==WG_MAX_CPUS)return 0;
        UINT32 affinity=wg_affinity(u64(entry+68));
        for(UINT32 i=0;i<enabled;i++)if(affinities[i]==affinity)return 0;
        affinities[enabled++]=affinity;
      }
    }else if(entry[0]==12) {
      if(size<24||++dist_count!=1||u64(entry+8)!=0x3fff0000||entry[20]!=3)return 0;
    }else if(entry[0]==14) {
      if(size<16||++range_count!=1)return 0;
      base=u64(entry+4);range=u32(entry+12);
    }
    pos+=size;
  }
  // Only this known crosvm address window; no speculative MMIO elsewhere.
  if(dist_count!=1||range_count!=1||!enabled||range<0x20000||range>0x100000||
     (base&0xffff)||(range&0xffff)||base<0x3fef0000||base>=0x3fff0000||
     base+range!=0x3fff0000)return 0;
  UINT32 matched=0;UINT64 pos=base,end=base+range;
  while(pos<end) {
    UINT64 typer;
    if(layout->count==WG_MAX_CPUS||end-pos<0x20000||!read_typer(pos+8,&typer,context))return 0;
    UINT32 affinity=(UINT32)(typer>>32),index=enabled;
    for(UINT32 i=0;i<enabled;i++)if(affinities[i]==affinity)index=i;
    if(index==enabled||(matched&(1u<<index)))return 0;
    matched|=1u<<index;layout->frames[layout->count++]=pos;
    if(affinity==wg_affinity(mpidr))layout->boot=pos;
    UINT32 stride=(typer&2)?0x40000:0x20000;
    if(end-pos<stride)return 0;
    pos+=stride;
    if(typer&16) {
      return pos==end&&layout->boot&&layout->count==enabled&&
        matched==((1u<<enabled)-1);
    }
  }
  return 0; // LAST missing, do not accept a partial topology.
}
