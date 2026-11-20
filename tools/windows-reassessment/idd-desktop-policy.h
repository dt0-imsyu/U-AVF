/* Pure policy: no APIs/mutations. Guest helper and host negative tests share it. */
#define IDD_DESKTOP_MAX_PATHS 16u
#define IDD_DESKTOP_MAX_MODES 64u
static int idd_desktop_snapshot_valid(UINT32 np,const DISPLAYCONFIG_PATH_INFO *paths,
                                     UINT32 nm,const DISPLAYCONFIG_MODE_INFO *modes) {
    if(!np||np>IDD_DESKTOP_MAX_PATHS||!nm||nm>IDD_DESKTOP_MAX_MODES)return 0;
    for(UINT32 i=0;i<np;i++) {
        const DISPLAYCONFIG_PATH_INFO *p=&paths[i];
        if(!(p->flags&DISPLAYCONFIG_PATH_ACTIVE)||!p->targetInfo.targetAvailable)return 0;
        UINT32 si=p->sourceInfo.modeInfoIdx,ti=p->targetInfo.modeInfoIdx;
        if(si>=nm||ti>=nm||modes[si].infoType!=DISPLAYCONFIG_MODE_INFO_TYPE_SOURCE
           ||modes[ti].infoType!=DISPLAYCONFIG_MODE_INFO_TYPE_TARGET)return 0;
        if(modes[si].id!=p->sourceInfo.id||modes[ti].id!=p->targetInfo.id)return 0;
        if(modes[si].adapterId.LowPart!=p->sourceInfo.adapterId.LowPart
           ||modes[si].adapterId.HighPart!=p->sourceInfo.adapterId.HighPart
           ||modes[ti].adapterId.LowPart!=p->targetInfo.adapterId.LowPart
           ||modes[ti].adapterId.HighPart!=p->targetInfo.adapterId.HighPart)return 0;
    }
    return 1;
}
static void idd_desktop_candidate(DISPLAYCONFIG_PATH_INFO *path,DISPLAYCONFIG_MODE_INFO *mode) {
    path->flags=DISPLAYCONFIG_PATH_ACTIVE;
    path->sourceInfo.modeInfoIdx=0;
    path->targetInfo.modeInfoIdx=DISPLAYCONFIG_PATH_MODE_IDX_INVALID;
    path->targetInfo.refreshRate.Numerator=60;path->targetInfo.refreshRate.Denominator=1;
    path->targetInfo.rotation=DISPLAYCONFIG_ROTATION_IDENTITY;
    path->targetInfo.scaling=DISPLAYCONFIG_SCALING_IDENTITY;
    path->targetInfo.scanLineOrdering=DISPLAYCONFIG_SCANLINE_ORDERING_PROGRESSIVE;
    mode->infoType=DISPLAYCONFIG_MODE_INFO_TYPE_SOURCE;
    mode->id=path->sourceInfo.id;mode->adapterId=path->sourceInfo.adapterId;
    mode->sourceMode.width=1920;mode->sourceMode.height=1200;
    mode->sourceMode.pixelFormat=DISPLAYCONFIG_PIXELFORMAT_32BPP;
    mode->sourceMode.position.x=0;mode->sourceMode.position.y=0;
}
