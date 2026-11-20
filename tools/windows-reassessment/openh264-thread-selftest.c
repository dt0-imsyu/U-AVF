/* Actual codec ABI/thread/encode/decode test. Not a guest FPS benchmark. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "codec_api.h"
#define CHECK(x) do {if(!(x)){fprintf(stderr,"FAIL line=%d\n",__LINE__);return 1;}} while(0)
int wmain(int argc,wchar_t **argv) {
    CHECK(argc==2);HMODULE dll=LoadLibraryW(argv[1]);CHECK(dll);
    int (*create)(ISVCEncoder**)=(void*)GetProcAddress(dll,"WelsCreateSVCEncoder");
    void (*destroy)(ISVCEncoder*)=(void*)GetProcAddress(dll,"WelsDestroySVCEncoder");
    long (*create_dec)(ISVCDecoder**)=(void*)GetProcAddress(dll,"WelsCreateDecoder");
    void (*destroy_dec)(ISVCDecoder*)=(void*)GetProcAddress(dll,"WelsDestroyDecoder");
    void (*version)(OpenH264Version*)=(void*)GetProcAddress(dll,"WelsGetCodecVersionEx");
    CHECK(create&&destroy&&create_dec&&destroy_dec&&version);
    OpenH264Version ver={0};version(&ver);CHECK(ver.uMajor==2&&ver.uMinor==5&&ver.uRevision==1);
    ISVCEncoder *enc=NULL;ISVCDecoder *dec=NULL;CHECK(!create(&enc)&&!create_dec(&dec));
    SEncParamExt p={0};CHECK(!(*enc)->GetDefaultParams(enc,&p));
    p.iUsageType=SCREEN_CONTENT_REAL_TIME;p.iPicWidth=1920;p.iPicHeight=1200;
    p.iMultipleThreadIdc=8;p.iComplexityMode=LOW_COMPLEXITY;p.fMaxFrameRate=60;
    p.iTargetBitrate=12000000;p.iRCMode=RC_BITRATE_MODE;p.bEnableFrameSkip=0;
    p.iSpatialLayerNum=1;p.iTemporalLayerNum=1;
    p.sSpatialLayers[0].iVideoWidth=1920;p.sSpatialLayers[0].iVideoHeight=1200;
    p.sSpatialLayers[0].fFrameRate=60;p.sSpatialLayers[0].iSpatialBitrate=12000000;
    p.sSpatialLayers[0].sSliceArgument.uiSliceMode=SM_FIXEDSLCNUM_SLICE;
    p.sSpatialLayers[0].sSliceArgument.uiSliceNum=8;
    CHECK(!(*enc)->InitializeExt(enc,&p));SEncParamExt effective={0};
    CHECK(!(*enc)->GetOption(enc,ENCODER_OPTION_SVC_ENCODE_PARAM_EXT,&effective));
    CHECK(effective.iMultipleThreadIdc==8&&effective.sSpatialLayers[0].sSliceArgument.uiSliceNum==8);
    SDecodingParam dp={0};dp.uiTargetDqLayer=255;dp.sVideoProperty.eVideoBsType=VIDEO_BITSTREAM_AVC;
    CHECK(!(*dec)->Initialize(dec,&dp));
    unsigned char *yuv=malloc(1920*1200*3/2),*au=malloc(4*1024*1024);CHECK(yuv&&au);
    memset(yuv+1920*1200,128,1920*1200/2);unsigned decoded=0;
    for(unsigned frame=0;frame<12;frame++) {
        for(unsigned y=0;y<1200;y++)for(unsigned x=0;x<1920;x++)yuv[y*1920+x]=(unsigned char)(32+((x/32+y/32+frame)%8)*24);
        SSourcePicture pic={0};pic.iColorFormat=videoFormatI420;pic.iPicWidth=1920;pic.iPicHeight=1200;
        pic.iStride[0]=1920;pic.iStride[1]=pic.iStride[2]=960;
        pic.pData[0]=yuv;pic.pData[1]=yuv+1920*1200;pic.pData[2]=pic.pData[1]+1920*1200/4;
        pic.uiTimeStamp=frame*17;SFrameBSInfo bs={0};CHECK(!(*enc)->EncodeFrame(enc,&pic,&bs));
        size_t length=0;for(int layer=0;layer<bs.iLayerNum;layer++) {
            SLayerBSInfo *l=&bs.sLayerInfo[layer];size_t size=0;
            for(int n=0;n<l->iNalCount;n++){CHECK(l->pNalLengthInByte[n]>0);size+=(size_t)l->pNalLengthInByte[n];}
            CHECK(length+size<=4*1024*1024);memcpy(au+length,l->pBsBuf,size);length+=size;
        }
        CHECK(length);unsigned char *planes[3]={0};SBufferInfo info={0};
        CHECK((*dec)->DecodeFrameNoDelay(dec,au,(int)length,planes,&info)==dsErrorFree);
        CHECK(info.iBufferStatus==1&&planes[0]&&info.UsrData.sSystemBuffer.iWidth==1920&&info.UsrData.sSystemBuffer.iHeight==1200);
        int stride=info.UsrData.sSystemBuffer.iStride[0];CHECK(stride>=1920);
        unsigned long long error=0;
        for(int y=0;y<1200;y++)for(int x=0;x<1920;x++) {
            int delta=(int)yuv[y*1920+x]-planes[0][y*stride+x];error+=(unsigned)(delta<0?-delta:delta);
        }
        CHECK(error<12ULL*1920*1200);decoded++;
    }
    CHECK(decoded==12);(*dec)->Uninitialize(dec);destroy_dec(dec);(*enc)->Uninitialize(enc);destroy(enc);
    free(yuv);free(au);FreeLibrary(dll);
    puts("ACTUAL_CODEC_2_5_1_EIGHT_THREADS_EIGHT_SLICES_12_ENCODE_DECODE=PASS\nNOT_A_GUEST_FPS_BENCHMARK=1");return 0;
}
