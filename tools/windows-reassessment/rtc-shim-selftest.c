#define GAS_TEST 1
#define GAS8 1
#define NO_RAW_DIAG 1
#define WINDOWS_RTC_UTC {2026,10,11,1,2,3,0,0,2047,0,0}
#include "uart-gas-shim.c"

static EFI_TIME current;
static unsigned gets,sets;
static int get_fail,set_fail,bad_readback;
static EFI_STATUS EFIAPI mock_get(EFI_TIME *time,EFI_TIME_CAPABILITIES *caps) {
  (void)caps;gets++;
  if(get_fail)return EFI_DEVICE_ERROR;
  *time=current;
  if(bad_readback && sets)time->Year=3053;
  return EFI_SUCCESS;
}
static EFI_STATUS EFIAPI mock_set(EFI_TIME *time) {
  sets++;
  if(set_fail)return EFI_DEVICE_ERROR;
  current=*time;return EFI_SUCCESS;
}
static void reset(unsigned year) {
  EFI_TIME value={2026,10,11,1,2,3,0,0,2047,0,0};current=value;current.Year=(UINT16)year;
  gets=sets=0;get_fail=set_fail=bad_readback=0;
}
int main(void) {
  EFI_RUNTIME_SERVICES rt={0};EFI_SYSTEM_TABLE system={0};
  rt.GetTime=mock_get;rt.SetTime=mock_set;system.RuntimeServices=&rt;st=&system;
  reset(3053);if(!correct_invalid_rtc() || sets!=1 || gets!=2 || current.Year!=2026)return 1;
  reset(2026);if(!correct_invalid_rtc() || sets || gets!=1)return 2;
  reset(3053);get_fail=1;if(correct_invalid_rtc() || sets)return 3;
  reset(3053);set_fail=1;if(correct_invalid_rtc() || sets!=1 || gets!=1)return 4;
  reset(3053);bad_readback=1;if(correct_invalid_rtc() || sets!=1 || gets!=2)return 5;
  reset(2026);current.Month=0;if(!correct_invalid_rtc() || sets!=1 || current.Month!=10)return 6;
  return 0;
}
