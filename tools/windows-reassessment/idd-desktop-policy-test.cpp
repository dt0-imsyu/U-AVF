#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "idd-desktop-policy.h"
static int checks;
#define REQUIRE(x) do{checks++;if(!(x)){printf("FAIL line%d\n",__LINE__);return 1;}}while(0)
int main(){
    DISPLAYCONFIG_PATH_INFO p={};DISPLAYCONFIG_MODE_INFO m[2]={};
    p.flags=DISPLAYCONFIG_PATH_ACTIVE;p.targetInfo.targetAvailable=TRUE;
    p.sourceInfo.id=3;p.targetInfo.id=9;p.sourceInfo.adapterId.LowPart=22;p.targetInfo.adapterId.LowPart=44;
    p.sourceInfo.modeInfoIdx=0;p.targetInfo.modeInfoIdx=1;
    m[0].infoType=DISPLAYCONFIG_MODE_INFO_TYPE_SOURCE;m[0].id=3;m[0].adapterId=p.sourceInfo.adapterId;
    m[1].infoType=DISPLAYCONFIG_MODE_INFO_TYPE_TARGET;m[1].id=9;m[1].adapterId=p.targetInfo.adapterId;
    REQUIRE(idd_desktop_snapshot_valid(1,&p,2,m));
    REQUIRE(!idd_desktop_snapshot_valid(0,&p,2,m));
    REQUIRE(!idd_desktop_snapshot_valid(IDD_DESKTOP_MAX_PATHS+1,&p,2,m));
    REQUIRE(!idd_desktop_snapshot_valid(1,&p,0,m));
    REQUIRE(!idd_desktop_snapshot_valid(1,&p,IDD_DESKTOP_MAX_MODES+1,m));
    DISPLAYCONFIG_PATH_INFO bad=p;
    bad.flags=0;REQUIRE(!idd_desktop_snapshot_valid(1,&bad,2,m));
    bad=p;bad.targetInfo.targetAvailable=FALSE;REQUIRE(!idd_desktop_snapshot_valid(1,&bad,2,m));
    bad=p;bad.sourceInfo.modeInfoIdx=2;REQUIRE(!idd_desktop_snapshot_valid(1,&bad,2,m));
    bad=p;bad.targetInfo.modeInfoIdx=2;REQUIRE(!idd_desktop_snapshot_valid(1,&bad,2,m));
    for(int k=0;k<2;k++){
        DISPLAYCONFIG_MODE_INFO broken[2];memcpy(broken,m,sizeof(m));
        broken[k].infoType=DISPLAYCONFIG_MODE_INFO_TYPE_DESKTOP_IMAGE;REQUIRE(!idd_desktop_snapshot_valid(1,&p,2,broken));
        memcpy(broken,m,sizeof(m));broken[k].id++;REQUIRE(!idd_desktop_snapshot_valid(1,&p,2,broken));
        memcpy(broken,m,sizeof(m));broken[k].adapterId.LowPart++;REQUIRE(!idd_desktop_snapshot_valid(1,&p,2,broken));
        memcpy(broken,m,sizeof(m));broken[k].adapterId.HighPart++;REQUIRE(!idd_desktop_snapshot_valid(1,&p,2,broken));
    }
    DISPLAYCONFIG_PATH_INFO candidate=p;DISPLAYCONFIG_MODE_INFO source={};
    idd_desktop_candidate(&candidate,&source);
    REQUIRE(source.infoType==DISPLAYCONFIG_MODE_INFO_TYPE_SOURCE&&source.id==3);
    REQUIRE(source.sourceMode.width==1920&&source.sourceMode.height==1200);
    REQUIRE(source.sourceMode.position.x==0&&source.sourceMode.position.y==0);
    REQUIRE(candidate.sourceInfo.modeInfoIdx==0&&candidate.targetInfo.modeInfoIdx==DISPLAYCONFIG_PATH_MODE_IDX_INVALID);
    REQUIRE(candidate.targetInfo.id==9&&candidate.targetInfo.adapterId.LowPart==44);
    REQUIRE(candidate.targetInfo.refreshRate.Numerator==60&&candidate.targetInfo.refreshRate.Denominator==1);
    REQUIRE(candidate.flags==DISPLAYCONFIG_PATH_ACTIVE&&candidate.targetInfo.targetAvailable);
    REQUIRE(p.sourceInfo.modeInfoIdx==0&&p.targetInfo.modeInfoIdx==1); // Original restore snapshot unchanged.
    REQUIRE(idd_desktop_snapshot_valid(1,&p,2,m));
    printf("REAL_DESKTOP_POLICY_PASS checks=%d; NO_DISPLAY_API_CALLED; NOT_GUEST_DESKTOP_OR_FPS_PASS\n",checks);
    return 0;
}
