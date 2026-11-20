#define GAS_TEST 1
#define GAS8 1
#define NO_RAW_DIAG 1
#define GIC_DYNAMIC 1
#include "uart-gas-shim.c"
static UINT64 fake_base,typers[8];static UINT32 reads;
static int fake_read(UINT64 address,UINT64 *value,void *context) {
  (void)context;
  if(address<fake_base+8||(address-fake_base-8)%0x20000)return 0;
  UINT64 index=(address-fake_base-8)/0x20000;
  if(index>=8)return 0;
  *value=typers[index];reads++;return 1;
}
static void put32(UINT8 *p,UINT32 v){copy(p,&v,4);}
static void put64(UINT8 *p,UINT64 v){copy(p,&v,8);}
static UINT32 make_table(UINT8 *madt,UINT32 cpus) {
  for(UINT32 i=0;i<1024;i++)madt[i]=0;
  copy(madt,"APIC",4);madt[8]=6;
  UINT32 pos=44;
  for(UINT32 i=0;i<cpus;i++,pos+=80) {
    UINT8 *e=madt+pos;e[0]=11;e[1]=80;put32(e+12,1);put64(e+68,i);
  }
  UINT8 *e=madt+pos;e[0]=12;e[1]=24;put64(e+8,0x3fff0000);e[20]=3;pos+=24;
  fake_base=0x3fff0000-cpus*0x20000;
  e=madt+pos;e[0]=14;e[1]=16;put64(e+4,fake_base);put32(e+12,cpus*0x20000);pos+=16;
  put32(madt+4,pos);fixsum(madt,pos);
  for(UINT32 i=0;i<cpus;i++)typers[i]=((UINT64)i<<32)|(i==cpus-1?16:0);
  reads=0;return pos;
}
int main(void) {
  (void)hex; // Runtime logging is deliberately disabled in the pure host test.
  UINT8 table[1024];WG_GIC_LAYOUT layout;UINT32 n=make_table(table,1);
  if(!wg_gic_layout(table,n,0,fake_read,0,&layout)||layout.boot!=0x3ffd0000||reads!=1)return 1;
  n=make_table(table,8);
  if(!wg_gic_layout(table,n,0,fake_read,0,&layout)||layout.boot!=0x3fef0000||layout.count!=8||reads!=8)return 2;
  if(!wg_gic_layout(table,n,7,fake_read,0,&layout)||layout.boot!=0x3ffd0000)return 3;
  // Permuted discovery order must still select affinity, not frame index.
  typers[0]=(UINT64)7<<32;typers[7]=16;
  if(!wg_gic_layout(table,n,0,fake_read,0,&layout)||layout.boot!=0x3ffd0000)return 4;
  if(wg_gic_layout(table,n,9,fake_read,0,&layout))return 5;
  typers[1]=typers[0];if(wg_gic_layout(table,n,0,fake_read,0,&layout))return 6;
  n=make_table(table,8);typers[7]&=~16ULL;if(wg_gic_layout(table,n,0,fake_read,0,&layout))return 7;
  n=make_table(table,8);table[45]=0;fixsum(table,n);reads=0;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout)||reads)return 8;
  n=make_table(table,8);table[9]++;reads=0;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout)||reads)return 9;
  n=make_table(table,8);put64(table+44+8*80+24+4,0x40000000);fixsum(table,n);reads=0;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout)||reads)return 10;
  if(wg_affinity(0xab00123456ULL)!=0xab123456)return 11;
  n=make_table(table,8);put64(table+44+80+68,0);fixsum(table,n);reads=0;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout)||reads)return 12;
  n=make_table(table,8);typers[0]|=16;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout))return 13;
  n=make_table(table,1);typers[0]|=2;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout))return 14;
  n=make_table(table,1);table[44+80+20]=2;fixsum(table,n);reads=0;
  if(wg_gic_layout(table,n,0,fake_read,0,&layout)||reads)return 15;
  return 0;
}
