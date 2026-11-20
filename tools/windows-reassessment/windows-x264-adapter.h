/* Isolated encoder comparison only. Keeps existing I420/AVC/UVF1 interface.
 * No decoder, transport, desktop, or VM configuration change. */
/* Both public headers use unprefixed NAL enum names; namespace only x264's
 * declarations here. Numeric values and ABI are unchanged. */
#define NAL_UNKNOWN X264_NAL_UNKNOWN
#define NAL_SLICE X264_NAL_SLICE
#define NAL_SLICE_DPA X264_NAL_SLICE_DPA
#define NAL_SLICE_DPB X264_NAL_SLICE_DPB
#define NAL_SLICE_DPC X264_NAL_SLICE_DPC
#define NAL_SLICE_IDR X264_NAL_SLICE_IDR
#define NAL_SEI X264_NAL_SEI
#define NAL_SPS X264_NAL_SPS
#define NAL_PPS X264_NAL_PPS
#define NAL_PRIORITY_DISPOSABLE X264_NAL_PRIORITY_DISPOSABLE
#define NAL_PRIORITY_LOW X264_NAL_PRIORITY_LOW
#define NAL_PRIORITY_HIGH X264_NAL_PRIORITY_HIGH
#define NAL_PRIORITY_HIGHEST X264_NAL_PRIORITY_HIGHEST
#include "x264.h"
#undef NAL_UNKNOWN
#undef NAL_SLICE
#undef NAL_SLICE_DPA
#undef NAL_SLICE_DPB
#undef NAL_SLICE_DPC
#undef NAL_SLICE_IDR
#undef NAL_SEI
#undef NAL_SPS
#undef NAL_PPS
#undef NAL_PRIORITY_DISPOSABLE
#undef NAL_PRIORITY_LOW
#undef NAL_PRIORITY_HIGH
#undef NAL_PRIORITY_HIGHEST
typedef struct {
    ISVCEncoder table;
    x264_t *encoder;
    SEncParamExt effective;
    int lengths[128];
} X264Adapter;
static struct {
    int (*preset)(x264_param_t *,const char *,const char *);
    int (*profile)(x264_param_t *,const char *);
    x264_t *(*open)(x264_param_t *);
    void (*parameters)(x264_t *,x264_param_t *);
    int (*encode)(x264_t *,x264_nal_t **,int *,x264_picture_t *,x264_picture_t *);
    void (*close)(x264_t *);
} x264_api;
static int x264_adapter_default(ISVCEncoder *opaque,SEncParamExt *p) {
    (void)opaque;if(!p)return 1;memset(p,0,sizeof(*p));return 0;
}
static int x264_adapter_init(ISVCEncoder *opaque,const SEncParamExt *p) {
    X264Adapter *a=(X264Adapter *)opaque;x264_param_t q;
    if(!p||a->encoder||p->iPicWidth<16||p->iPicHeight<16||
       p->iPicWidth>4096||p->iPicHeight>4096||p->iPicWidth%2||p->iPicHeight%2||
       p->fMaxFrameRate<1||p->fMaxFrameRate>120||p->iTargetBitrate<=0)return 1;
    if(x264_api.preset(&q,"ultrafast","zerolatency"))return 1;
    /* Guest ISA has already been localized: never auto-select SVE paths. */
    q.cpu=X264_CPU_NEON;q.i_width=p->iPicWidth;q.i_height=p->iPicHeight;
    q.i_csp=X264_CSP_I420;q.i_bitdepth=8;
    q.i_threads=p->iMultipleThreadIdc;if(q.i_threads<1||q.i_threads>8)return 1;
    q.b_sliced_threads=1;q.i_slice_count=q.i_threads;
    q.i_fps_num=(uint32_t)p->fMaxFrameRate;q.i_fps_den=1;
    /* VFR needs the following frame to determine duration and adds a frame
     * of buffering. Preserve zerolatency's CFR contract; UVF1 keeps real PTS. */
    q.i_timebase_num=1;q.i_timebase_den=1000;q.b_vfr_input=0;
    q.i_bframe=0;q.i_sync_lookahead=0;q.rc.i_lookahead=0;
    q.b_annexb=1;q.b_repeat_headers=1;
    q.rc.i_rc_method=X264_RC_ABR;q.rc.i_bitrate=p->iTargetBitrate/1000;
    q.rc.i_vbv_max_bitrate=q.rc.i_bitrate;q.rc.i_vbv_buffer_size=q.rc.i_bitrate/2;
    q.i_log_level=X264_LOG_NONE;
    if(x264_api.profile(&q,"baseline"))return 1;
    a->encoder=x264_api.open(&q);if(!a->encoder)return 1;
    x264_api.parameters(a->encoder,&q);a->effective=*p;
    a->effective.iMultipleThreadIdc=(unsigned short)q.i_threads;
    a->effective.sSpatialLayers[0].sSliceArgument.uiSliceNum=(unsigned)q.i_slice_count;
    return 0;
}
static int x264_adapter_uninit(ISVCEncoder *opaque) {
    X264Adapter *a=(X264Adapter *)opaque;
    if(a->encoder)x264_api.close(a->encoder);a->encoder=NULL;return 0;
}
static int x264_adapter_encode(ISVCEncoder *opaque,const SSourcePicture *p,SFrameBSInfo *bs) {
    X264Adapter *a=(X264Adapter *)opaque;x264_picture_t in={0},out={0};
    x264_nal_t *nals=NULL;int count=0;
    if(!a->encoder||!p||!bs||p->iColorFormat!=videoFormatI420||
       p->iPicWidth!=a->effective.iPicWidth||p->iPicHeight!=a->effective.iPicHeight)return 1;
    in.img.i_csp=X264_CSP_I420;in.img.i_plane=3;in.i_pts=p->uiTimeStamp;
    for(int i=0;i<3;i++){in.img.plane[i]=p->pData[i];in.img.i_stride[i]=p->iStride[i];}
    int bytes=x264_api.encode(a->encoder,&nals,&count,&in,&out);
#ifdef X264_ADAPTER_TEST
    fprintf(stderr,"X264_TEST bytes=%d nals=%d\n",bytes,count);
#endif
    if(bytes<=0||bytes>4194304||count<1||count>128||!nals)return 1;
    size_t total=0;unsigned char *start=nals[0].p_payload;
    for(int i=0;i<count;i++) {
#ifdef X264_ADAPTER_TEST
        fprintf(stderr,"X264_TEST nal=%d bytes=%d contiguous=%d\n",i,nals[i].i_payload,nals[i].p_payload==start+total);
#endif
        if(nals[i].i_payload<=0||nals[i].p_payload!=start+total||
           (size_t)nals[i].i_payload>(size_t)bytes-total)return 1;
        a->lengths[i]=nals[i].i_payload;total+=(size_t)nals[i].i_payload;
    }
    if(total!=(size_t)bytes)return 1;
    memset(bs,0,sizeof(*bs));bs->iLayerNum=1;
    bs->sLayerInfo[0].iNalCount=count;bs->sLayerInfo[0].pNalLengthInByte=a->lengths;
    bs->sLayerInfo[0].pBsBuf=start;return 0;
}
static int x264_adapter_option(ISVCEncoder *opaque,ENCODER_OPTION option,void *out) {
    if(option!=ENCODER_OPTION_SVC_ENCODE_PARAM_EXT||!out)return 1;
    *(SEncParamExt *)out=((X264Adapter *)opaque)->effective;return 0;
}
static const ISVCEncoderVtbl x264_adapter_table={
    .InitializeExt=x264_adapter_init,.GetDefaultParams=x264_adapter_default,
    .Uninitialize=x264_adapter_uninit,.EncodeFrame=x264_adapter_encode,
    .GetOption=x264_adapter_option
};
static int x264_adapter_create(ISVCEncoder **out) {
    X264Adapter *a=calloc(1,sizeof(*a));if(!out||!a){free(a);return 1;}
    a->table=&x264_adapter_table;*out=(ISVCEncoder *)a;return 0;
}
static void x264_adapter_destroy(ISVCEncoder *opaque) {
    if(opaque){x264_adapter_uninit(opaque);free(opaque);}
}
static bool x264_adapter_bind(HMODULE module) {
    x264_api.preset=(void*)GetProcAddress(module,"x264_param_default_preset");
    x264_api.profile=(void*)GetProcAddress(module,"x264_param_apply_profile");
    x264_api.open=(void*)GetProcAddress(module,"x264_encoder_open_165");
    x264_api.parameters=(void*)GetProcAddress(module,"x264_encoder_parameters");
    x264_api.encode=(void*)GetProcAddress(module,"x264_encoder_encode");
    x264_api.close=(void*)GetProcAddress(module,"x264_encoder_close");
    return x264_api.preset&&x264_api.profile&&x264_api.open&&x264_api.parameters&&
           x264_api.encode&&x264_api.close;
}
