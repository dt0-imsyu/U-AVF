#define GAS_TEST 1
#define GAS8 1
#define NO_RAW_DIAG 1
#include "uart-gas-shim.c"
int main(void) {
  UINT32 raw[20]={0xd2820001,0xd2807fa2,0x39400043,0x36280083,0xd2807f01,0x39000020,0xd65f03c0,0xf1000421,0x54ffff41,0x17fffffd};
  UINTN matches=0;
  if(raw_match((UINT8*)raw,sizeof(raw),&matches)!=(UINT8*)raw || matches!=1)return 5;
  copy(raw+10,raw,40);raw_match((UINT8*)raw,sizeof(raw),&matches);if(matches!=2)return 6;
  raw[1]^=1;raw[11]^=1;raw_match((UINT8*)raw,sizeof(raw),&matches);if(matches!=0)return 7;
  UINT8 rsdp[36]={0},xsdt[52]={0},dbg[84]={0},spcr[80]={0};
  EFI_SYSTEM_TABLE system={0}; EFI_CONFIGURATION_TABLE config={0};
  copy(rsdp,"RSD PTR ",8);rsdp[15]=2;
  UINT64 xp=(UINTN)xsdt;copy(rsdp+24,&xp,8);
  UINT8 sum=0;for(int i=0;i<20;i++)sum+=rsdp[i];rsdp[8]=(UINT8)-sum;
  copy(xsdt,"XSDT",4);xsdt[4]=52;UINT64 dp=(UINTN)dbg;copy(xsdt+36,&dp,8);dp=(UINTN)spcr;copy(xsdt+44,&dp,8);fixsum(xsdt,52);
  copy(spcr,"SPCR",4);spcr[4]=80;spcr[41]=32;spcr[43]=1;spcr[44]=0xf8;spcr[45]=3;fixsum(spcr,80);
  copy(dbg,"DBG2",4);dbg[4]=84;dbg[36]=44;dbg[40]=1;
  UINT8 *d=dbg+44;d[1]=40;d[3]=1;d[13]=0x80;d[14]=0x12;d[18]=22;
  UINT8 *g=d+22;g[1]=32;g[3]=1;g[4]=0xf8;g[5]=3;fixsum(dbg,84);
  config.VendorGuid=acpi_guid;config.VendorTable=rsdp;system.NumberOfTableEntries=1;system.ConfigurationTable=&config;st=&system;
  if(!tables() || g[1]!=8 || !checksum(dbg,84) || spcr[41]!=8 || !checksum(spcr,80))return 1;
  if(tables())return 2; // Reject a table not matching the intended BEFORE state.
  g[1]=32;g[4]=0;fixsum(dbg,84);if(tables())return 3; // Wrong base rejected.
  g[4]=0xf8;fixsum(dbg,84);dbg[9]++;if(tables())return 4; // Bad checksum rejected.
  return 0;
}
