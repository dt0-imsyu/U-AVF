/* Small actual codec test before admitting experimental streaming. */
static int eight_codec_selftest(HMODULE dll) {
    int result=1;bool enc_ready=false,dec_ready=false;
    ISVCEncoder *enc=NULL;ISVCDecoder *dec=NULL;unsigned char *yuv=NULL,*au=NULL;
    int (*create)(ISVCEncoder**)=(void*)GetProcAddress(dll,"WelsCreateSVCEncoder");
    void (*destroy)(ISVCEncoder*)=(void*)GetProcAddress(dll,"WelsDestroySVCEncoder");
    long (*create_dec)(ISVCDecoder**)=(void*)GetProcAddress(dll,"WelsCreateDecoder");
    void (*destroy_dec)(ISVCDecoder*)=(void*)GetProcAddress(dll,"WelsDestroyDecoder");
    void (*version)(OpenH264Version*)=(void*)GetProcAddress(dll,"WelsGetCodecVersionEx");
#define EIGHT_CHECK(test) do {if(!(test)){result=__LINE__;goto done;}}while(0)
    EIGHT_CHECK(create&&destroy&&create_dec&&destroy_dec&&version);
    OpenH264Version ver={0};version(&ver);EIGHT_CHECK(ver.uMajor==2&&ver.uMinor==5&&ver.uRevision==1);
    EIGHT_CHECK(!create(&enc)&&!create_dec(&dec));SEncParamExt params={0};
    EIGHT_CHECK(!(*enc)->GetDefaultParams(enc,&params));
    params.iUsageType=SCREEN_CONTENT_REAL_TIME;params.iPicWidth=1920;params.iPicHeight=1200;
    params.iMultipleThreadIdc=8;params.iComplexityMode=LOW_COMPLEXITY;params.fMaxFrameRate=60;
    params.iTargetBitrate=12000000;params.iRCMode=RC_BITRATE_MODE;params.bEnableFrameSkip=0;
    params.iSpatialLayerNum=1;params.iTemporalLayerNum=1;
    params.sSpatialLayers[0].iVideoWidth=1920;params.sSpatialLayers[0].iVideoHeight=1200;
    params.sSpatialLayers[0].fFrameRate=60;params.sSpatialLayers[0].iSpatialBitrate=12000000;
    params.sSpatialLayers[0].sSliceArgument.uiSliceMode=SM_FIXEDSLCNUM_SLICE;
    params.sSpatialLayers[0].sSliceArgument.uiSliceNum=8;
    EIGHT_CHECK(!(*enc)->InitializeExt(enc,&params));enc_ready=true;
    SEncParamExt effective={0};EIGHT_CHECK(!(*enc)->GetOption(enc,ENCODER_OPTION_SVC_ENCODE_PARAM_EXT,&effective));
    EIGHT_CHECK(effective.iMultipleThreadIdc==8&&effective.sSpatialLayers[0].sSliceArgument.uiSliceNum==8);
    SDecodingParam dp={0};dp.uiTargetDqLayer=255;dp.sVideoProperty.eVideoBsType=VIDEO_BITSTREAM_AVC;
    EIGHT_CHECK(!(*dec)->Initialize(dec,&dp));dec_ready=true;
    yuv=malloc(1920*1200*3/2);au=malloc(4*1024*1024);EIGHT_CHECK(yuv&&au);
    memset(yuv+1920*1200,128,1920*1200/2);
    for(unsigned frame=0;frame<12;frame++) {
        for(unsigned y=0;y<1200;y++)for(unsigned x=0;x<1920;x++)
            yuv[y*1920+x]=(unsigned char)(32+((x/32+y/32+frame)%8)*24);
        SSourcePicture pic={0};pic.iColorFormat=videoFormatI420;pic.iPicWidth=1920;pic.iPicHeight=1200;
        pic.iStride[0]=1920;pic.iStride[1]=pic.iStride[2]=960;
        pic.pData[0]=yuv;pic.pData[1]=yuv+1920*1200;pic.pData[2]=pic.pData[1]+1920*1200/4;
        pic.uiTimeStamp=frame*17;SFrameBSInfo bs={0};EIGHT_CHECK(!(*enc)->EncodeFrame(enc,&pic,&bs));
        size_t length=0;for(int layer=0;layer<bs.iLayerNum;layer++) {
            SLayerBSInfo *l=&bs.sLayerInfo[layer];size_t size=0;
            for(int n=0;n<l->iNalCount;n++){EIGHT_CHECK(l->pNalLengthInByte[n]>0);size+=(size_t)l->pNalLengthInByte[n];}
            EIGHT_CHECK(length+size<=4*1024*1024);memcpy(au+length,l->pBsBuf,size);length+=size;
        }
        EIGHT_CHECK(length);unsigned char *planes[3]={0};SBufferInfo info={0};
        EIGHT_CHECK((*dec)->DecodeFrameNoDelay(dec,au,(int)length,planes,&info)==dsErrorFree);
        EIGHT_CHECK(info.iBufferStatus==1&&planes[0]&&info.UsrData.sSystemBuffer.iWidth==1920&&info.UsrData.sSystemBuffer.iHeight==1200);
        int stride=info.UsrData.sSystemBuffer.iStride[0];EIGHT_CHECK(stride>=1920);
        unsigned long long error=0;
        for(int y=0;y<1200;y++)for(int x=0;x<1920;x++) {
            int delta=(int)yuv[y*1920+x]-planes[0][y*stride+x];error+=(unsigned)(delta<0?-delta:delta);
        }
        EIGHT_CHECK(error<12ULL*1920*1200);
    }
    result=0;
done:
    if(dec){if(dec_ready)(*dec)->Uninitialize(dec);destroy_dec(dec);}
    if(enc){if(enc_ready)(*enc)->Uninitialize(enc);destroy(enc);}
    free(yuv);free(au);return result;
#undef EIGHT_CHECK
}
